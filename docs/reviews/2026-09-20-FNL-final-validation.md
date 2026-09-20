# Final validation of the 19-20 September work, 2026-09-20

**Status:** open

**Prefix: `FNL`.** Cite findings from this document as `FNL-C1`, `FNL-D4` and so on. Every three-letter prefix already used in this folder was checked first; `FNL` is fresh.

**What was reviewed.** Branch `autonomy-diagram-r0` at `821dedfd` ("FXV-C5: the carried visit history was copied but not readable", 2026-09-20). The scope is everything committed on 2026-09-19 and 2026-09-20 - 43 commits from `0c3911d8` (SET-B1/B3) to `821dedfd` - which is the C-severity sweep of AUR, AUS, MKR, GUX and their neighbours, the two Opus validations of it ([OP3](2026-09-19-OP3-opus-validation-of-the-c-sweep.md) and [FXV](2026-09-20-FXV-opus-validation-of-the-fix-round.md)), and the three commits that answered FXV (`7a978ebf`, `1b06620a`, `821dedfd`). 74 files, 10,822 insertions, 2,345 deletions. The two validation documents were read as claims to check, not as settled findings; where this pass agrees with one of them it says so, and where it does not (FNL-C1, FNL-D18) it says why.

**Method.** Read the current source of every area the brief named - `HomeStaging.tailKey` and the A\* loop around it, `Layout.getVisitHistory`/`restoreVisitHistory` and `MarklinControlStation.parseAuto`, `RouteEditorFrame`'s rename branch and its three new helpers, `AutonomyEditorPanel.buildArrivedFromMenu` and `bulkClearWarning`, `Point.assign`/`toJSON` and `Layout.fromJSON`'s arrival-side pass, `DiagramExport.render` - and traced each through its callers rather than its diff. Re-derived the dead-key set independently under the guard's own rule (its literal grammar, its `MYSELF` exclusion, its line splitting, its six `BUILDERS`), then again with comments stripped and again with `test/` excluded, to measure what the rule cannot see. Checked all eight bundles for key-set parity, duplicates, non-ASCII, and per-key placeholder parity across languages. Read the diff of every test class touched in the two days (twenty of them) against the SOP's vacuity rules. Checked every comment the fixes wrote against the code beside it, every "fixed" status row in OP3 and FXV against the tree, and the four `behaviour.md` additions against the code they describe.

**Nothing was run, built or compiled.** No test, no `javac`, no `ant`. No git command that writes was used. Nothing under `cs2_sample_layout/` was touched. This document is the only file written. What that costs is in the last section.

**The tree moved under this pass.** `git status` was clean at `821dedfd` when the pass began; by the time this document was written, twenty files carried uncommitted edits from another session - a `try`/`catch` round the export's discard (FXV-C8's second half), the FXV-C6 cost written beside the arrival-side gate, `openAutonomyPagesMenu` deleted (FXV-C3), the path-integrity tooltip restored in all eight bundles (FXV-C13), the bulk-clear test's precondition moved to `placementsAutonomyWillWrite` (FXV-C7), and status rows in eight review documents. Every line number and every claim below is about the committed tree at `821dedfd`, not those edits. Read against them anyway: the new `catch` in `DiagramExport.render` still discards only `grid[0]`, so FNL-C1 is unchanged by it, and none of the other in-flight edits touches a finding here.

---

## Summary

| | count |
|---|---|
| A | 0 |
| B | 0 |
| C | 6 |
| D | 24 |

**Nothing is broken.** The seven areas the brief named all hold up under tracing (FNL-D6 to FNL-D12), the 237 removed keys are referenced nowhere in the tree in any file type (FNL-D2), all eight bundles agree with each other to the key and to the placeholder (FNL-D3), and every "fixed" row in OP3 and FXV describes something that is actually in the tree (FNL-D20). The tests earn their green with two qualifications (FNL-C2, FNL-C6), neither of which reaches the railway.

The three worth Adam's attention first, all C:

- **FNL-C1** - OP3-C11 proposed moving the grid build inside the `try` so that a `LayoutGrid` constructor throwing part-way would still be discarded. The build was moved, FXV called it correct, and the new comment in `DiagramExport.render` says the leak by that door is closed. It is not: a constructor that throws never assigns `grid[0]`, and the `finally` discards only `grid[0]`. Three passes agreed on a fix that does nothing for the case it names. The case is unreachable today, so the cost is one wrong comment - but it is a wrong comment written with unusual confidence.
- **FNL-C2** - the dead-key guard reads string literals inside comments as "asked for". Two keys, `loc.ui.menuCopyToNextPage` and `loc.ui.menuCopyToPreviousPage`, are spelled only inside a `/* ... */` block that has said "we no longer need these" for some time, and are in all eight bundles. The guard reports zero dead; measured with comments stripped it reports these two. The 237-key sweep is otherwise complete.
- **FNL-C6** - the toolbar test's discriminating half depends on a fixed 500 ms sleep on the side that has to be slow to fail. On a quick machine it catches the toggle-instead-of-force regression; on a loaded one the second Control press reads the state the first press left and passes against the defect it was written for. Its own `SETTLE` javadoc records that this is exactly how its first draft passed.

---

## A - high

None.

---

## B - medium

None. I looked for one hardest in `tailKey` (a finer key cannot lose a plan; it can only spend budget - FNL-D6), in the visit-history carry (a name-keyed carry cannot move a timestamp onto the wrong square of the same railway - FNL-D7), and in the rename branch (the operator's own Yes stands between the window and the database, and the name it now writes is the one on the database - FNL-D8).

---

## C - low

| id | status | where |
|---|---|---|
| FNL-C1 | fixed 2026-09-20 - the export retires by the panel, which the constructor registers before it builds | `DiagramExport.java:128-146` (the comment) against `:196-201` (the `finally`) and `LayoutGrid.java:709-733` |
| FNL-C2 | fixed 2026-09-20 - comments are stripped before the literal scan; the two keys and the dead code are gone | `RightClickMenuListener.java:121-133`; `TrainControlUI.java:10374`, `:10388`; `testEveryMessageKeyIsAskedFor.LITERAL` and `collect` |
| FNL-C3 | fixed 2026-09-20 - the line citation is out | `HomeStaging.java:2637` against `testEveryCitationResolves.java:232-233` |
| FNL-C4 | fixed 2026-09-20 - the javadoc describes the name-keyed carry | `Layout.java:489-493`, the `restoreVisitHistory` javadoc |
| FNL-C5 | fixed 2026-09-20 - the null second lookup and the impostor are both handled, and pinned | `RouteEditorFrame.java:2942-2950`; the FXV-B2 status row |
| FNL-C6 | fixed 2026-09-20 - the discriminating press waits for the direction command | `test/regression/testTheToolbarButtonsHonourTheirTooltips.java`, `SETTLE` and the second press in `testControlOnADirectionButtonForcesThatDirection` |

### FNL-C1: the export's `try` was widened for a case the `finally` still cannot reach

OP3-C11 said: *"If it throws part-way - after registering some and before returning - `grid[0]` is never assigned, the `try` is never entered, and those captions stay in `layoutStations` for the session. Moving the build inside the `try` costs nothing, since the `finally` already null-checks."* The build was moved. FXV-C8: *"The build was moved inside the bracket, correctly."* And the comment now in `render`:

> `THE BUILD IS INSIDE THE BRACKET TOO. LayoutGrid's constructor is what registers the captions, so a constructor that threw part-way - after registering some and before returning - left grid[0] unassigned and the finally unentered: the same leak by the one door the first version of this fix did not cover. The discard already null-checks.`

Read the `finally`:

```java
javax.swing.SwingUtilities.invokeAndWait(() ->
{
    if (grid[0] != null) grid[0].discard();
});
```

A constructor that throws part-way still never assigns `grid[0]`, so `grid[0]` is null, so the null-check skips the discard, so the captions the partial constructor registered stay in the table. Entering the `finally` changes nothing about what it can discard. The comment describes the leak accurately and then claims a fix that does not touch it.

The mechanism that could close it is already in `LayoutGrid`: the constructor's first act is `LIVE.put(parent, new WeakReference<>(this))` (`LayoutGrid.java:911`), and `discard()` itself says a grid that never reached its panel *"registers itself against that panel before it builds, so a failure part way through leaves one here to be discarded by the next grid over the same panel"*. There is no next grid over an export's `host`. A `LayoutGrid.retire(JPanel)` that looks the panel up in `LIVE` and discards whatever it finds, called from the `finally` instead of `grid[0].discard()`, would cover both doors with the same null-check.

Graded C because `discard()`'s own comment says *"Nothing on that path throws today"*, so this is a wrong comment about an unreachable case rather than a live leak. It is first in the summary because it is the one place in the round where the reviewer, the fixer and the validator all agreed and were all wrong - the SOP's *"verify the layer you are actually claiming about"*, applied to a `finally`.

The second half of OP3-C11 - the `finally`'s own `invokeAndWait` throwing `InterruptedException` over the original cause - is still unaddressed and is already recorded as open in FXV-C8. Not repeated here.

### FNL-C2: two keys are kept alive by commented-out code, and the guard cannot tell

`testEveryMessageKeyIsAskedFor` collects every string literal in every `.java` file under `src/` and `test/` with `LITERAL` over the raw file text. A literal inside a comment matches exactly as one in code does. Re-derived with comments stripped (a `//`-to-end-of-line and `/* */` stripper that skips string literals, so a `"//"` inside a string cannot truncate a line), the only change in the whole tree is two keys:

```java
// We no longer need these since users can just drag or copy entire pages
/*addSeparator();

menuItem = new JMenuItem(
    I18n.t("loc.ui.menuCopyToNextPage")
);
menuItem.addActionListener(event -> ui.copyToNextPage(source));
...
menuItem = new JMenuItem(
    I18n.t("loc.ui.menuCopyToPreviousPage")
);
menuItem.addActionListener(event -> ui.copyToPrevPage(source));
add(menuItem);*/
```

`RightClickMenuListener.java:121-133`. Both keys are in all eight bundles; both `TrainControlUI.copyToNextPage` (`:10374`) and `copyToPrevPage` (`:10388`) are public methods whose only callers are inside that block. So the day's sweep left the bundles with two dead lines in eight languages and the source with two dead public methods, and the guard that certifies the sweep reports zero.

This is the same shape as OP3-B1 at a fortieth of the size: a rule that shields too generously. It is C rather than B because the count is two, nothing reaches the railway, and the class comment does not claim comments are excluded - but it is the exact question the brief asked, *"a guard that reads source and would pass against the unfixed file"*, and the answer is yes for any key whose window was commented out rather than deleted.

Two remedies, and the first is the one to take: delete the block, the two methods and the two keys in eight bundles, and lower nothing (`EXPECTED` is already 0). The second is to strip comments in `collect` before matching, which also makes the class stop counting the 2 keys the next commented-out window leaves. Measured before recommending it: with comments stripped, no live key loses its shield - the two above are the whole delta - and excluding `test/` as well changes nothing either (no key is spelled in test code only). So neither narrowing costs a false positive today.

### FNL-C3: `tailKey`'s comment carries a bare line-number citation the citation guard cannot see

`HomeStaging.java:2637`:

> `Written <name>/turned or <name>/straight, which is the spelling firstClearRoute uses for its own visited set (:1362) - a word after the slash in both cases`

Line 1362 is the visited-key line today, so the citation is accurate this afternoon. It is the shape `33a09886` ("A comment that read as a line-number citation says the shape instead") removed elsewhere on the same day, and `7a978ebf` removed from `MarklinRoute` for the same reason - and it survives because `testEveryCitationResolves`'s `byLine` pattern requires a capitalised name before the colon (`[A-Z][A-Za-z0-9_]*(?:\.java)?:\d{2,5}`), which `(:1362)` does not have. The guard knows only what it lists. The next edit above line 1362 makes the citation wrong, silently. Say "the `next.getUniqueId() + (turned ? "/turned" : "/straight")` key in `firstClearRoute`" and drop the number.

### FNL-C4: `restoreVisitHistory`'s javadoc describes the key `821dedfd` replaced

`Layout.java:489-493`:

> `Keyed by block or unique id, which survives a rebuild of the same railway; a key naming a point this configuration no longer has simply never matches, which is the same answer as never having been there.`

The commit that fixed FXV-C5 established the opposite - `getVisitHistory`'s javadoc four methods up: *"unique ids come from a global allocator that hands a new one to every Point built, so a rebuilt railway's squares have different ids. Carried over as they stood, every entry for a point with no block was dead weight the rule could never match"* - and rewrote the body to translate point NAMES back into this layout's keys. The body comment (`:501-506`) says so. The javadoc above it was not updated, so the method now documents itself as doing what the same commit's message calls "a bigger hole". A reader who trusts javadocs over bodies re-learns FXV-C5 from scratch.

### FNL-C5: the FXV-B2 row says fixed, and its two asides are neither fixed nor recorded anywhere

The finding proper - `nameToSaveAs` and the sentence - is fixed, tested both ways and pinned at the call site (FNL-D8). FXV-B2 also raised, under *"while the file is open"*, two things `loadedId` makes answerable:

- `nameNowHeldById()` is still called twice - once inside `howTheRouteMoved` (`:525`) and once at `:2950` - and the route can go between them. Then `nowCalled` is null, the dialog reads "renamed to null", `nameToSaveAs(name, null)` returns the typed name, and `editRoute(null, ...)` is asked. That call is safe - `RemoteDeviceCollection.getByName(null)` is a map lookup that returns null, so `editRoute` logs and returns false - and the operator gets `errorEditRouteFailed` naming the wrong route. One local, read once, is still the fix.
- The impostor case - renamed AND a new route took the old name - still answers `"changed"`, and the `"changed"` branch still does not consult `loadedId`.

Neither is in FXV's C table, and the B row reads simply "fixed". This is FXV-C8's own shape ("two findings are marked fixed with half of their own text untouched") in the document that named it. Either give them ids or say in the row that they were declined.

### FNL-C6: the toolbar test's second press is a sleep, on the side that has to be slow to fail

`testControlOnADirectionButtonForcesThatDirection` presses Control+reverse twice. OP3-D9 is right that the second press is the one that separates force from toggle. The check after it is `awaitDirection(loc, false)`, which is `Thread.sleep(SETTLE)` and then a poll for up to four seconds until `goingForward() == false`. The state before the second press is already reversed. If the door's worker - `backwardLoc` starts `new Thread(...)` (`TrainControlUI.java:12865`) - has not run within 500 ms, the poll's first sample sees the state the FIRST press left, which is reversed, and returns true at once. A regression back to `switchDirection()` is then invisible. The `SETTLE` javadoc says this is exactly how the first draft passed against the toggle; 500 ms made it pass for the right reason on that machine on that day.

The README's rule is *"a regression test that only sometimes catches the regression is worse than none"*, and the fix is one of the shapes the suite already uses: `model.setSentMessageObserver` (as `testAdvancedRoutes.testARouteFinishesWhenItsLocomotiveIsDeletedMidRun` does) can count direction commands actually sent, so the assertion becomes "a second direction command went out and it said reverse" instead of "the locomotive still says reverse some time later". Alternatively the test can wait for the worker by polling something the worker sets that the previous press did not - the `Backward.isSelected()` toggle is written on the same thread, but it too is already true after the first press. Counting the sent command is the one that discriminates.

The speed test does not have this problem: every press there changes the number, so a stale read fails rather than passes.

---

## D - not defects, and checks that came back clean

| id | what was checked |
|---|---|
| FNL-D1 | the dead-key guard's rule, re-derived: 0 dead, 43 keys resting on `BUILDERS`, all 43 live, both floors hold with margin |
| FNL-D2 | 237 keys removed by key-set arithmetic (2,022 - 237 + 12 added = 1,797), none referenced in any file type under `src/` or `test/` |
| FNL-D3 | all eight bundles: identical 1,797-key sets, no duplicate keys, no non-ASCII, identical placeholder sets per key, no straight apostrophe beside a placeholder |
| FNL-D4 | the 20 shortened tooltips: 20 values changed in each bundle, placeholders intact; FXV-C13's text is as FXV describes and remains open |
| FNL-D5 | FXV-B1: `autolayout.ui.confirmClearAll` is in `BUILDERS`; the two `AtOnce` keys carry `{0}` like their siblings |
| FNL-D6 | `tailKey` keys both facts `turnedByThePlan` reads, a finer key cannot lose a plan, and the RTX-C1 test discriminates and has a control |
| FNL-D7 | the visit history: carried by name, translated back, restored before anything can record; the AUR-C3 test is red without the restore half |
| FNL-D8 | the rename branch: `nameToSaveAs` is right, the sentence landed in all eight bundles, the test pins both directions and the call site |
| FNL-D9 | `buildArrivedFromMenu`'s gate matches its two siblings and `bulkClearWarning` is what its four call sites use |
| FNL-D10 | the arrival side has one writer door, and the load, the save and the reservation all agree it belongs beside an occupant |
| FNL-D11 | `DiagramExport.render`'s seam is safe to read across threads, and the export test discriminates for both doors it drives |
| FNL-D12 | FXV-B4: all ten `getRoute`-then-dereference sites in `TrainControlUI` are guarded, not only the three named |
| FNL-D13 | FXV-B3: both `@AfterClass` floors ask TestNG per class, and both runners read the second summary line, so their failure is red |
| FNL-D14 | OP3-C4's test is now a test of `Point.toJSON`'s gate; the reserve door and its control are sound |
| FNL-D15 | OP3-C6: the cancel test asserts the triggers; OP3-C5: the export test asserts it had captions to lose |
| FNL-D16 | OP3-C10: the crossing test asks every ordered pair and fails loudly on a non-uniform answer |
| FNL-D17 | the SET-B1 load refusal, the SET-B2 crossing cut and the SET-B3 run rule are each pinned by a test that measures rather than reads |
| FNL-D18 | FXV-C10 was declined correctly, and FXV had the path wrong: the untracked file was in `random_test_layout/`, and all seven are on disk |
| FNL-D19 | `behaviour.md`'s four additions name things that exist and say what the code does |
| FNL-D20 | every "fixed" row in OP3 and FXV describes something in the tree; the rows FXV-C9 names are still as FXV describes |
| FNL-D21 | the three comments the fix commits rewrote are true: OP3-C1, FXV-C2, SVB-C3 |
| FNL-D22 | the battery registries: four new classes in `build.xml`, the window-building pin is 49 for the right three |
| FNL-D23 | `git status` is clean, and `.gitignore` hides only what `5d1e23b8` said |
| FNL-D24 | the July-cycle "twin site" rule was followed everywhere this round touched a guard |

**FNL-D1.** Reimplemented the class's rule over the working tree: 387 `.java` files, 21,337 distinct literals, 1,797 keys, 0 duplicates. Dead under the six-entry `BUILDERS` rule: none. 43 keys are not spelled anywhere and rest on a builder prefix - 18 `pathPreference*`/`tooltip.pathPreference*`, 13 `route.kind.*`, 8 `side*`/`facing*`, 2 `confirmClearAll*AtOnce` - and each is a value of the enum or the list its builder iterates. The floors are 1,700 keys and 15,000 literals against 1,797 and 21,337; the key floor has about 5% of room, which is enough for the two keys FNL-C2 would remove and not for another window-sized sweep - OP3-D3's note still applies. The `autolayout.ui.confirmClearAll` entry FXV-B1 warned would shield the non-`AtOnce` siblings does shield `confirmClearAllHomeLocomotives`, which is spelled at `AutonomyEditorPanel.java:2237` anyway.

**FNL-D2.** Key set at `0c3911d8^` minus key set at `821dedfd`: 237, which is UIX-C4's number. Twelve keys were added over the two days (the four route-moved sentences, the two `AtOnce` siblings, `checkHalfMeasuredApproach`, `crossingAtSquare`, `sensorAtSquare`, `errorMaxTrainLengthNegative`, `promptMassAssignCrossings`, `errorLocomotiveDrivenByARunningRoute`), so 2,022 - 237 + 12 = 1,797. Each of the 237, grepped as a whole word across every file type under `src/` and `test/` - `.java`, `.form`, `.xml`, `.json`, everything - hits twice: `ui.main.toolbar.functions` in the OP3-C1 comment, past tense, and `autolayout.ui.errorAddEdge` in `testMessageBundles`' past-tense javadoc (FXV-D15). Nothing else.

**FNL-D3.** All eight bundles parsed with the guard's own line rule: 1,797 keys each, the same set, none repeated. No value contains a character above `~`. For every key, the set of `{n}` placeholder indices is identical in all eight files - a check run over the whole bundle rather than the 20 changed values, so a translation that lost a `{1}` anywhere would have shown. No value in any bundle contains a straight `'` beside a `{`, which is what `MessageFormat` would silently eat.

**FNL-D4.** `git diff 7ac40714^..4e2d02b5` changes exactly 20 values in `messages.properties`, and the parity check above covers the other seven. `ui.main.toolbar.tooltip.pathIntegrityValidation` still reads *"Holds the train until the Central Station confirms every switch and signal on its path is set. Recommended."*, so FXV-C13 is as described and correctly still open.

**FNL-D5.** `BUILDERS` carries `autolayout.ui.confirmClearAll` with a comment naming the builder. `confirmClearAllTrackLengthsAtOnce` and `confirmClearAllMaxTrainLengthsAtOnce` carry `{0}` like the keys they are built from, and `bulkClearWarning` passes one argument, so the pair cannot disagree about arity.

**FNL-D6.** `turnedByThePlan` (`HomeStaging.java:980-983`) reads `movedAlong.containsKey(l)` and `turnedOnTheWay(movedAlong.get(l))`. `tailKey` now appends `<name>/turned` or `<name>/straight` for every entry in `routes` before the still-standing check, so both facts are in the key; the `if (road == null || road.isEmpty()) continue` above it never fires because `movedAlong` is written only at `:821` and `:1026` with a non-empty route from `firstClearRoute`. `this.movedAlong` is replaced from `routesOf.get(currentKey)` at `:925` on every poll, so what the key carries is what the expansion reads. A finer key splits states; it cannot merge them, so no plan reachable before is unreachable now - the only cost is `SEARCH_LIMIT`, which FXV-C4 already records as unmeasured. `testARoadThatTurnedIsNotTheSameStateAsOneThatDidNot` drives `tailKey` by reflection with a real `Layout`, over unmeasured edges so both tails are empty, asserts the reversing flag as a precondition, and runs the same road twice as a control before asserting the two roads differ: without the append both keys are `key(state)` and it is red.

**FNL-D7.** `parseAuto` reads `getVisitHistory()` off the old layout before `invalidate()`, builds the new one, and calls `restoreVisitHistory` before `applyAutonomyRouteActivations` - and `Layout.fromJSON` starts nothing, so nothing can record an arrival on the new layout in between (FXV-C5's ordering note stays a trap for the next author, not a live hazard). `getVisitHistory` walks `getPoints()` and emits a name only where `lastArrival.get(recencyKeyOf(point))` hits, so it is the rule's own view projected onto names; `restoreVisitHistory` looks each name up with `getPoint` and writes under `recencyKeyOf(here)`. A split square with a block yields one entry per copy under the same block key and restores to the same key - idempotent. `getPoints()` is the live `points.values()`; nothing adds points to a built layout, so the walk is safe. The AUR-C3 test compares `getVisitHistory()` before and after a real `parseAuto`, with `assertNotSame` on the layout and a non-empty precondition. Reverting `restoreVisitHistory` to `putAll(history)` leaves name-keyed entries the projection cannot see, so `rebuilt.getVisitHistory()` is empty and the test is red. What it does not catch is reverting BOTH methods to raw copies of the map - then both sides are the same bytes and it passes as the first version did; the projection is what makes the assertion mean "readable", and a comment in the test says so.

**FNL-D8.** `nameToSaveAs(typed, nowCalled)` returns `nowCalled` when the field still holds `originalName` and `typed` otherwise, and `onSave` calls `editRoute(nowCalled, saveAs, ...)`. `editRoute` refuses a rename onto a name another route holds (`MarklinControlStation.java:2036-2041`), so a typed name that collides is refused rather than deleting the route. `route.ui.confirmRouteRenamed` landed in all eight bundles with the new clause and the same three placeholders. `testTheRouteEditorNoticesItsRouteMoving` asserts both branches of `nameToSaveAs` and pins the call site with `source.contains("editRoute(nowCalled, saveAs,")`, which is the string in the file; the pin is honest about being a source read and the rule beside it is driven.

**FNL-D9.** `buildArrivedFromMenu` returns null when `point.getCurrentLocomotive() == null` after `pointOnTheLayout`, which is the gate `appendTailCrossed` (`:3676`) and `armTailPick` (`:3755`) already had. Its click action still writes to `pointOnTheLayout(now, target)`, which prefers the occupied copy. `bulkClearWarning` is `I18n.f(page == null ? key + "AtOnce" : key, count)` and all four call sites (`:2261`, `:2276`, `:9740`, `:9779`) go through it. `testTheArrivalSideIsNotOfferedForAnEmptySquare` takes every copy's train off the running layout, asserts the setup still names one, and asserts the menu is now null - red without the gate, since `pointOnTheLayout` falls back to `first` and the sides list is non-empty on a square that just offered the menu.

**FNL-D10.** `currentLoc` is written in one place outside the constructor - `assign`, `synchronized`, which clears `arrivedFrom` and `arrivedAlong` on a change of occupant; `setLocomotive` and `reserve` both go through it (the second clear in `setLocomotive` is now redundant and harmless). `toJSON` writes `arrivedFrom` and `arrivedAlong` only when `currentLoc != null` (`:1268`, `:1282`). `Layout.fromJSON` applies both only onto a point whose `getCurrentLocomotive() != null` (`:11618-11636`). The runtime's own writers - `Layout.java:8261-8265` after a move, `:3375-3379` for a sibling re-stand, `AutonomySession`'s re-stand - all write past an occupant. Three doors, one rule.

**FNL-D11.** `stumbleForTest` is a plain public static; the test sets it and calls `render` on the same thread, so no visibility question arises, and the `finally` in the test nulls it. In `render`, the seam runs after the build and before `awaitTiles`, so a throw there leaves `grid[0]` assigned and the `finally` discards it - which is the case the test drives, and the case the fix is for. `testAnInterruptedExportStillHandsBackItsCaptions` asserts `before > 0` (OP3-C5), asserts the render threw, and compares the settled count. Without the `finally` the captions stay - `addLayoutStation` prunes only on a successor with the same owner and the owner is a local - so it is red. `testAnExportThatWorksStillHandsThemBack` is the control.

**FNL-D12.** Every `this.model.getRoute(` in `TrainControlUI` (`:19417`, `:19699`, `:20087`, `:21062`, `:21283`, `:21389`, `:21469`, `:21510`, `:22767`, `:25142`): each is followed by a null check, an `instanceof`, or a `continue`. `editRoute(String)` now looks up once into `asked` and dereferences only inside `if (asked != null)`. This is the sweep FXV-B4 said had not been run; it has now, and it is complete.

**FNL-D13.** Both `testSomethingWasActuallyAsked` methods are `@AfterClass(alwaysRun = true)` taking `ITestContext`, count `getPassedTests()` filtered to the class, and throw when zero. `getPassedTests()` holds only `@Test` results, populated as each finishes, so the count is of claims and not of configurations. A throw from `@AfterClass` is a configuration failure, which TestNG prints on the SECOND summary line with `Failures: 0` on the first - and both `docs/tools/battery.sh` (`:629-657`) and `docs/tools/one.sh` (`:469-479`) read that second line and count the class red. So the floor is visible to the harness, which is the thing that would have made it a floor in name only. No `FOCUSED` counter remains in either class.

**FNL-D14.** `testADroppedPlacementLeavesNoTailBehind` now sets a side on the empty square before serialising, so `assertFalse(toJSON().contains("arrivedFrom"))` is red the moment `Point.toJSON`'s `&& this.currentLoc != null` is reverted - there is no other `arrivedFrom` in that fixture's output to confuse it. `reserve` is invoked by reflection with `loc()`, which is `model.getLocByName(LOC)` and returns one instance, so the control's same-occupant case is real; `assertSame(loc(), loc())` pins that, one statement later than it should be (FXV-C15, open). The reversed `(actual, expected)` at the end is still reversed and still affects only the message.

**FNL-D15.** `testACancelledCopyLeavesTheFunctionsAlone` copies both arrays before the press, asserts the copy changed the types and set the flag, and asserts types, triggers and flag after the undo. The export test's `assertTrue(before > 0)` is the OP3-C5 precondition.

**FNL-D16.** `locksItsTwoRoads` collects every leg whose path steps on the middle tile, splits by start row or column, skips a leg against its own reverse, and asserts every remaining ordered pair gives the same answer as the first. On the four-road double slip a diagonal starting on row 2 lands in `eastWest` and one starting on column 3 in `northSouth`, so the pairs asked include diagonal-against-straight - which is what "every pair of different roads locks" needs. FXV-C11's floor on how many legs were found is still missing and still open.

**FNL-D17.** `testANegativeMaximumStopsTheConfigurationLoading` asserts the loader's error contains "length" - `autolayout.errorMaxTrainLengthInvalid` is *"{0} maxTrainLength must be >= 0"*, and `Layout.fromJSON` invalidates on `< 0` at `:10826` - and loads the same fixture without the negative as a control. `testACrossingIsCutOutOfEveryPieceAndCountsOnBothRoads` asserts the piece count, no square in two pieces, and the two legs' lengths after typing known numbers. `testTheSingleDoorSpeaksForTheWholeRunAfterAWalk` measures the run and asserts what the dialog opens on. `testALegThatCrossesItsOwnSquareTwiceIsCutAtIt` asserts its own precondition (a leg over the crossing twice) before the claim, and its arithmetic note records a measured value OP2-C5 had assumed was zero.

**FNL-D18.** `git show --name-status 5d1e23b8` lists seven deletions from the index: `bash.exe.stackdump`, `cs2_sample_layout/config/autonomy/Main_bak.json`, and five under `random_test_layout/` - among them `random_test_layout/config/autonomy/configuration-Main_bak.json`. There was never a `cs2_sample_layout/config/autonomy/configuration-Main_bak.json` in that commit; FXV-C10 read the wrong directory. All seven are on disk. The declined row is right, and its reason ("the scratch layout's file was rewritten by a later run") is about the file FXV actually meant. Nothing in the suite counts the sample layout's configurations against a number this could have changed.

**FNL-D19.** Control+B: `LayoutEditor.java:7340` asks `hoveredSquare()` and calls `autonomyPanel.promptMaxTrainLengthFor(over)`, which is the station menu's prompt. `Layout.simAnnounce` (`:1720`) and `simClearBehind` (`:1733`) exist and are what the section names. `testEveryCopyOfAMayReverseSquareIsAskedAbout` and `testKeepDirectionIsHonouredAtAMayTurnSquare` exist in the classes named. The negative maximum: `Layout.fromJSON` invalidates the whole configuration (`:10809-10829`), and `Point.setMaxTrainLength` (`:1024-1027`) clamps a negative to 0 - a third layer the document does not mention and which the file door makes unreachable from a saved configuration; consistent with the text as written. The CS3-B2 "known limitation" paragraph and the crossing-in-no-piece paragraph were read against `testMassAssignLengths` and the reducer test rather than the wiring code, which is listed under not covered.

**FNL-D20.** OP3's C table: C1 (comment past tense - `TrainControlUI.java:918-921`), C2 (`tailKey`), C3 (untracked), C4, C5, C6, C7 (the gate), C8 (`AutonomyBanner.java:218` is `Font.BOLD, 12`, as the row records Adam ruled), C9 (the comment now says a word after the slash in both cases), C10, C11 (the build IS inside the bracket; the row says what was done and only the code comment overclaims - FNL-C1). FXV's B and C tables: B1 to B4 fixed as described (FNL-D5, D8, D13, D12), C1 (`HIGHLIGHT_HOLD_MS` sits between its own javadoc and the test's, in that order), C2 (the javadoc names `pagesAvailable` and says why it is not asked), C5 (FNL-D7), C10 (FNL-D18). The rows FXV-C9 names - OP3-B1 and OP3-B2 `open`, UIX-C4 "118 keys" - are still as FXV describes, and FXV-C9 is still open, so the record is consistent with itself about being stale there.

**FNL-D21.** OP3-C1's replacement says the key "has since gone", which FNL-D2 confirms. FXV-C2's replacement says the method asks `whyAutonomyEditorCannotOpen` and not `pagesAvailable`, which `openAutonomyEditorIfItCan` (`:8456`) and `AutonomyMenu.java:410-413` bear out. SVB-C3's rewritten sentence says the reader takes a copy in `MarklinSimpleComponent`'s constructor, and `:94` is `new java.util.ArrayList<>(r.getRoute())`.

**FNL-D22.** `build.xml` lists `testTheToolbarButtonsHonourTheirTooltips`, `testEveryMessageKeyIsAskedFor`, `testCancelUndoesACustomizationCopy` and `testTheExportRetiresItsGrid`. `testSwitchingToACentralStationLayout`'s pin moved 46 to 49 and names the three that build a window; `testEveryMessageKeyIsAskedFor` does not, and is rightly not counted.

**FNL-D23.** `git status --porcelain` is empty at `821dedfd`. The three `.gitignore` lines are the ones FXV-D19 describes.

**FNL-D24.** Every guard added this round was checked for a twin: the `getRoute` sweep (FNL-D12), the arrival-side gate against its two siblings (FNL-D9), the three arrival-side doors (FNL-D10), the two `@AfterClass` floors (FNL-D13), and the four `bulkClearWarning` call sites (FNL-D9). The one place a twin was missed is FNL-C1, and it is a twin of a mechanism rather than of a call site.

---

## What this pass did not cover

**Nothing was run.** Every "red without the fix" above is reached by reading the code under test and the code it tests, not by reverting anything and watching. The claims that most want a run:

- **The A\* budget under the widened `tailKey`** (FXV-C4, still open). `core.testReturnHomeOnRealLayout` and the blessed planner baseline are the readers; nothing here measured them.
- **The two `@AfterClass` floors on a desktop that gives no focus.** I have read that the harness counts a configuration failure as red; I have not seen one counted.
- **The uniformity `locksItsTwoRoads` asserts** on the double slip. The test fails loudly if the reducer answers differently for different pairs; whether it does is a question for `GraphReducer`, which I did not reduce.
- **`testJavadocsAreAttached`'s `ALLOWED = 90` and its per-file list.** I did not re-derive the orphan count; the two decrements and their reasons read correctly and the FXV-C1 field is now attached, but the number is the class's to hold.

**Not read at all:** `docs/manual-tests/tests.md` MT-465 to MT-469 and the `findings.tsv`/`triage.db` rewrites, beyond confirming that the ids this round cites resolve there is FXV-D13's job and I did not repeat it. `AutonomySession`'s 207 changed lines, `AutonomyChecks`, `GraphReducer`'s 19, `LayoutLabel`'s 111 and `MarklinControlStation`'s 235 outside `parseAuto` and `editRoute` - read where a named area led into them, not as a whole. The CS3-B2 wiring claim in `behaviour.md` ("each wiring pass also re-creates the accessory twice") was not traced.

**Not re-derived:** the 20 tooltip meanings against the code, one by one. FXV-D16 did that; I checked their placeholders and parity and the one FXV-C13 names.

**One thing I deliberately measured rather than reasoned about:** how many keys the dead-key guard shields through comments (FNL-C2). The answer is two, and it was found by running the guard's rule with comments stripped and diffing - the same method OP3-B1 used to find 119, applied to the blind spot OP3-B1 did not name.
