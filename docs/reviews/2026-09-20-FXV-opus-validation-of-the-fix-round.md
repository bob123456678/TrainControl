# Opus validation of the fix round, 2026-09-20

**Status:** open

**Prefix: `FXV`.** Cite findings from this document as `FXV-B1`, `FXV-C4` and so on. `OPV`, `OP2` and
`OP3` are the three earlier Opus validations; this is the fourth, and both a letter and a digit suffix
on `OP` are taken, so it takes a name of its own.

**What was reviewed.** Branch `autonomy-diagram-r0` at `fde1b7fb` ("The rest of the OP3 and batch 4
items", 2026-09-20 07:29). Ten commits, newest first: `fde1b7fb` (OP2-C2/C6/C8/C13, SVB-C3, OP2-C5
declined), `a4a2f484` (OP2-C9), `7229505c` (OP3-C7), `c07d889d` (AUR-C3), `a4da36d5` (the four battery
registries and nine OP3/OP2 items), `5d1e23b8` (OP3-C3), `49de68b7` (OP3-B1), `d2541437` (UIX-C3 and
OP3-B2), `4e2d02b5` and `7ac40714` (the tooltip shortening). Most of these are
[OP3](2026-09-19-OP3-opus-validation-of-the-c-sweep.md)'s own findings being closed, so the question
asked of each was not "is this a reasonable change" but "does it close the finding it names".

**Method.** Re-derived the dead message-key set independently, twice - once under the guard's own new
`BUILDERS` rule and once under a rule of my own that looks at every file type in `src/` and `test/`,
not only `.java`. Diffed the key sets of all eight bundles at five revisions. Checked every changed
tooltip value in all eight bundles for placeholder drift, straight apostrophes and non-ASCII. Traced
every caller and every sibling door of the two gated methods. Read every new and changed test against
the SOP's vacuity rules, and every new comment against the code it describes. **No test was run and
nothing was built** - the harness rule stands, and what that costs is written out in the last section.
No file outside this document was written, and nothing under `cs2_sample_layout/` was touched.

---

## Summary

| | count |
|---|---|
| A | 0 |
| B | 4 |
| C | 15 |
| D | 19 |

The three that need Adam first:

- **FXV-B1** - `testEveryMessageKeyIsAskedFor` is **red at `fde1b7fb`**. It reports two dead keys
  against `EXPECTED = 0`, because OP2-C2's new `bulkClearWarning` builder was not added to the
  `BUILDERS` list that the same day's OP3-B1 fix installed. The class comment names this exact failure
  mode; it arrived one commit later, in the same push.
- **FXV-B2** - OP2-C9's new Save branch writes `nameField`'s text onto the renamed route, which renames
  it **back**. The comment above it says the save is "onto the route under its new name" and the dialog
  says only that the number is kept. Somebody else's rename is silently undone.
- **FXV-B4** - OP3-B2's sweep stopped at one door. Three more `getRoute`-then-dereference sites remain,
  and one of them is on the Start Autonomy worker thread, where the failure is a button that does
  nothing and says nothing.

---

## A - high

None. Nothing in this round produces wrong behaviour on the layout or loses data on it. FXV-B2 loses a
NAME, under an operator's own Yes, which is why it is B and not A: the route, its id, its commands and
every tile bound to it survive.

---

## B - medium

| id | status | where |
|---|---|---|
| FXV-B1 | fixed 2026-09-20 (7a978ebf, before this review landed) - the builder is declared in BUILDERS | `AutonomyEditorPanel.bulkClearWarning` (`:9883-9886`) against `testEveryMessageKeyIsAskedFor.BUILDERS` (`:74-86`) and `EXPECTED` (`:65`) |
| FXV-B2 | fixed 2026-09-20 - nameToSaveAs keeps the other party's name, and the sentence says so | `RouteEditorFrame.onSave` (`:2923-2946`), `route.ui.confirmRouteRenamed` in all eight bundles |
| FXV-B3 | fixed 2026-09-20 - the floor asks TestNG what passed in this class, not a counter at one gate | `test/regression/testTheWindowTakesTheKeyboard.java:459` against its seven `@Test` methods and the `@AfterClass` at `:1039-1049` |
| FXV-B4 | fixed 2026-09-20 - all three doors guarded, and the double lookup made one | `TrainControlUI.java:21283`, `:22760`, `:25130` against the guard added at `:21503-21512` |

### FXV-B1: the new bulk-clear builder is not in `BUILDERS`, so the dead-key guard is red at HEAD

`49de68b7` replaced the guard's "any literal that is a prefix shields a key" rule with a list of the
five real builders, and wrote the risk down in the class comment:

> *"The narrow rule has the opposite risk - a builder written in future and not added here reports its
> keys as dead - and that is the direction to fail in."*

`fde1b7fb`, three commits later, wrote a sixth builder:

```java
private String bulkClearWarning(String key, int count)
{
    return I18n.f(page == null ? key + "AtOnce" : key, count);
}
```

`autolayout.ui.confirmClearAllTrackLengthsAtOnce` and
`autolayout.ui.confirmClearAllMaxTrainLengthsAtOnce` are now spelled nowhere. I re-ran the class's own
rule in Python against the working tree - its exact `LITERAL` regex, its `MYSELF` exclusion, its
`keysOf` line splitting, its `BUILDERS` list - over 387 `.java` files, 21,332 distinct literals and
1,797 keys. Two keys come back dead, and both are those. `EXPECTED` is 0, so
`testNoKeyIsLeftBehindByADeletedWindow` fails, naming two keys that are perfectly live.

Two things make this worth a B rather than a C. The first is that a battery reported "260 green with
four classes failing" at `a4da36d5` and this went in afterwards, so the round ends redder than it
started. The second is that the guard is the only thing standing behind a 237-key deletion: a class
that fails for a reason nobody believes is a class somebody raises `EXPECTED` to silence, and the next
real dead key goes in under the same number.

**The codebase has already decided how to answer this**, in `ArrivalSidePrompt.keyFor`
(`:352-372`), whose javadoc says:

> *"WHOLE KEYS, not a prefix plus a letter. The bundle check reads the source for the keys it must
> find, and a concatenated one reads as `autolayout.ui.side` - which is in no bundle, so it reports a
> missing message that is not missing."*

`bulkClearWarning` is that rule broken one method later. A `switch` or a ternary that spells both keys
out - `page == null ? "…AtOnce" : "…"` chosen once, in one place - keeps everything OP2-C2 asked for
(one builder, one choice, four call sites reduced to one rule) and keeps the two literals visible.
Adding the two stems to `BUILDERS` also works, but it shields the non-`AtOnce` siblings as a side
effect, which is a small step back toward the prefix rule OP3-B1 removed.

### FXV-B2: the renamed-route Save renames it back, and neither the comment nor the dialog says so

`RouteEditorFrame.onSave`, `:2940`:

```java
if (!parent.getModel().editRoute(nowCalled, name, built, s88, trigger,
    enabledBox.isSelected(), expression))
```

`name` is `nameField.getText().trim()`, read at `:2880`. This window is not modal, it does not refresh,
and `originalName` is final - so unless the operator has typed in the name field themselves, `name` is
still the name the window opened with. `editRoute(nowCalled, name, …)` therefore renames the route from
whatever the other party called it back to what this window remembers.

The comment above it says the opposite:

> `// RENAMED IS NOT DELETED (OP2-C9).  Saving onto the route under its new name keeps its id…`

and so does the commit message - *"offers to save onto that route under its new name"*. It does not
save under the new name; it saves under this window's name, which is the old one.

The dialog is the operator's only warning, and it does not warn:

> `{0} has been renamed to {1} since you opened it here.  Saving now writes what is in this window onto
> that route, keeping its number - so the buttons on your track diagram and any autonomy setting go on
> pointing at it.  Save onto {1}?`

Every clause is true and the sentence still misleads, because the one thing it does not mention is the
thing the operator will notice: the route goes back to being called `{0}`. "Save onto `{1}`?" reads as
"leave it as `{1}`".

Two honest answers, and they are different features. If "your window wins" is the intended semantic -
which is what the `"changed"` branch does - then the sentence needs a clause saying the name goes back
too. If the rename is meant to survive, the call is `editRoute(nowCalled, nowCalled, …)` when the name
field is untouched, and the comment is already correct.

**While the file is open, two more things `loadedId` now makes answerable and does not answer:**

- `howTheRouteMoved` only asks by id when the name lookup fails. If the route was renamed AND a new
  route took the old name, `getRoute(originalName)` returns the impostor, the signature differs, the
  answer is `"changed"`, and Save writes this window's route over a route it never opened. Pre-existing
  - but `loadedId` is exactly the thing that can now tell them apart, and the `"changed"` branch does
  not consult it.
- `nameNowHeldById()` is called twice, once inside `howTheRouteMoved` and once at `:2929`. Between
  them the route can go, and then `nowCalled` is null: the dialog says "renamed to null" and
  `editRoute(null, …)` is asked. One local, read once, removes it.

### FXV-B3: OP2-C8's floor in `testTheWindowTakesTheKeyboard` is wired to one of its seven claims

The commit says *"both focus-dependent classes have a floor, so a desktop that never gives the keyboard
fails them instead of reporting green"*. In `testTheKeyMapReachesTheWholeWindow` that is what happened:
`FOCUSED.incrementAndGet()` sits in the shared helper every claim goes through (`:345`).

In `testTheWindowTakesTheKeyboard` it does not. The class has seven `@Test` methods and eight
`SkipException` sites, and `FOCUSED.incrementAndGet()` was added at exactly one of them - `:459`,
inside `testTheKeyboardComesBackWhenTheWindowGainsFocus`, past that test's own inner skip about taking
the keyboard off a tabbed pane. The other six tests have their own skip gates at `:361`, `:431`,
`:557`, `:657`, `:741` and `:925`, and `testDisplayDoesNotUndoTheTopmostTrickItDependsOn` (`:835`) has
no skip at all and always runs.

So the `@AfterClass` does not assert "some claim in this class was asked". It asserts "this one tabbed-
pane focus transfer worked", and it fails the whole class when that one gesture is unavailable even
though six claims ran and passed. That is a new red on some desktops for a reason that has nothing to
do with the code - the SOP's *"a regression test that only sometimes catches the regression is worse
than none"* with the sign flipped.

The fix is the shape the sibling class already has: increment where each claim establishes that it has
the keyboard, or count the tests that did not skip rather than the focus transfers that succeeded.

### FXV-B4: three more `getRoute`-then-dereference doors, one on a worker with nothing to catch it

`d2541437` put MKR-C4's guard into `enableOrDisableRoute`, which closes OP3-B2 as written, and OP3's
two named neighbours (today `:21059` and `:21382`) are both already guarded - I checked. But a sweep of
every `= this.model.getRoute(` in the file finds three more of the same shape still open:

- **`:25130`**, inside `startAutonomyActionPerformed`'s worker thread:
  ```java
  for (String routeName : this.model.getRouteList())
  {
      Route r = this.model.getRoute(routeName);

      if (r.isEnabled())
  ```
  `getRouteList()` is a copy of the names; `getRoute` is a live lookup; `editRoute` and the Central
  Station's re-read of a station route are both delete-then-re-add. The thread has a `finally` that
  gives the Start button back, so the button does not stick - but the run never starts, nothing is
  logged, and the operator sees a press that did nothing. This is the door OP3-B2 was written about,
  one method over.
- **`:22760`**, the same loop in the timetable's start handler, on the EDT.
- **`:21283`**, `editRoute(String)`'s worker: `if (getRoute(routeName) != null) { Route currentRoute =
  getRoute(routeName); … currentRoute.getId() }`. Two lookups, and only the first is checked - the
  classic time-of-check shape, on a thread with no handler.

The README calls this the July cycle's most repeated mistake and says the search is one command. It is,
and it was not run this time either.

---

## C - low

| id | status | where |
|---|---|---|
| FXV-C1 | fixed 2026-09-20 - the constant sits above the javadoc | `test/core/testLayoutTiles.java:268-286`, and `testJavadocsAreAttached` which only reads `src/` |
| FXV-C2 | open | `TrainControlUI.openAutonomyEditorIfItCan` (`:8444-8460`) against `AutonomyMenu.java:410-413` |
| FXV-C3 | open | `TrainControlUI.openAutonomyPagesMenu` (`:8463-8475`) |
| FXV-C4 | open | `HomeStaging.tailKey` (`:2635-2643`) |
| FXV-C5 | open | `Layout.restoreVisitHistory` (`:480-487`) against `MarklinControlStation.parseAuto` (`:1076-1097`) and `TrainControlUI.java:23916` |
| FXV-C6 | open | `AutonomyEditorPanel.buildArrivedFromMenu:3371-3381` against `TrainControlUI.rebuildRunningLayoutFromSetup:6439-6446` |
| FXV-C7 | open | `test/regression/testTheBulkClearSaysWhatCancelDoes.java:253-301` |
| FXV-C8 | open | the OP3-C11 and OP3-C9 status rows against `DiagramExport.java:187-202` and `HomeStaging.java:2674-2688` |
| FXV-C9 | open | the OP3-B1 / OP3-B2 rows in `2026-09-19-OP3-…` and the UIX-C4 row in `2026-09-19-UIX-…` |
| FXV-C10 | open | `cs2_sample_layout/config/autonomy/configuration-Main_bak.json` against `5d1e23b8`'s message |
| FXV-C11 | open | `test/core/testAutonomyDiagramReducer.java`, `locksItsTwoRoads` (`:2007-2060`) |
| FXV-C12 | open | `fde1b7fb`'s message against `a4da36d5`'s content |
| FXV-C13 | open | `ui.main.toolbar.tooltip.pathIntegrityValidation` against `Layout.java:56-60` |
| FXV-C14 | open | `test/regression/testTheTailCanBeGivenInTheEditor.java`, `Fixture.open` (`:574-635`) |
| FXV-C15 | open | `test/core/testLockEdgesSurviveTheFile.java:334-341`, the precondition asserted after the call it qualifies |

### FXV-C1: the OP2-C6 constant was inserted between a javadoc and the method it documents

`fde1b7fb` added `HIGHLIGHT_HOLD_MS` to `testLayoutTiles` immediately after the javadoc of
`testASecondHighlightStopsTheFirstsRestore` and immediately before its `@Test`:

```
268  /**
269   * A second accessory highlight stops the first, so only one restore ever fires (VC2-C3).
...
277   */
278  /**
279   * How long an accessory highlight is held for, as `LayoutLabel` holds it.
280   *
281   * Named here because the test above turns on the second drive landing inside it (OP2-C6).
282   */
283  private static final long HIGHLIGHT_HOLD_MS = 2250;
284
285  @Test
286  public void testASecondHighlightStopsTheFirstsRestore() throws Exception
```

Only the last doc comment before a declaration attaches. So the field is documented by the test's
paragraph, and `testASecondHighlightStopsTheFirstsRestore` is now documented by "How long an accessory
highlight is held for". The field's own sentence is wrong as well: it says "the test **above**", and
the test is below it.

This is the defect `testJavadocsAreAttached` exists for, and the same commit series has already been
caught by it twice - `a4da36d5` lists *"a javadoc I split from its method again"* among the four
classes the battery failed on, and repaired one that `49de68b7` had created in `TrainControlUI`
(`openAutonomyPagesMenu`'s javadoc, orphaned above `openAutonomyEditorIfItCan`; that repair is right,
see FXV-D17).

**Why the ratchet did not catch this one:** `testNoNewOrphanedJavadocs` starts at `new File("src")` and
never looks at `test/`. The guard knows only what it lists. Extending it to `test/` needs its own
`ALLOWED` count and per-file list, which is a day's arithmetic - but it is the only thing that will
stop the third occurrence in three days from becoming the fourth.

### FXV-C2: "the same question the menu item asks" is most of the question, not the question

`openAutonomyEditorIfItCan`'s javadoc:

> *"It asks the same question the menu item asks - `whyAutonomyEditorCannotOpen`, which is the guard
> `AutonomyMenu` enables Edit by - so a click cannot reach a door the menu would have shown greyed
> out."*

`AutonomyMenu.java:413`:

```java
edit.setEnabled(editRefusal == null && pagesAvailable);
```

`pagesAvailable` is `edit.getItemCount() > 0`, and the new method does not ask it. The gesture this
serves is the excluded-page label, so the case is not hypothetical: on a configuration where every page
is left out of autonomy the submenu is empty, the menu greys Edit, and a click on the label calls
`openAutonomyEditor(null)` - which is `openLayoutEditor(null, TRUE, null)` and opens the editor on
whatever page the main window is showing. Nothing crashes; a door the menu says is shut opens.

Either ask both halves, or say in the comment that it asks the refusal only and why that is enough.
The sentence as written is the kind of claim the README's *"verify the layer you are actually claiming
about"* is for - the delegation is right, the enumeration of what it delegates is not.

### FXV-C3: `openAutonomyPagesMenu` now has no callers, and its javadoc says where it is called from

`49de68b7` repointed `AutonomyOverlayToggle`'s click at the new method. That was its only caller:
`grep -rn "openAutonomyPagesMenu" src/ test/` returns the declaration and nothing else. The javadoc
still reads *"Reached from the diagram itself, because that is where the user finds out the page is
left out"*, which is now a statement about a caller that no longer exists.

Dead public method plus a comment that describes a removed call site. Delete both, or say it is kept
for the menu's own use and name that use.

### FXV-C4: the widened `tailKey` does close OP3-C2, and "at no cost" is the one word in its comment that is not measured

The fix is right about the fact it keys. `turnedByThePlan` reads two things out of `movedAlong`:
`containsKey(l)` and `turnedOnTheWay(get(l))`. Every value in `movedAlong` is a route from
`firstClearRoute` between two different squares, so none is ever null or empty, so the `if (road ==
null || road.isEmpty()) continue` above the append never fires in practice and the `turned` list is
exactly `movedAlong`'s key set with a status word on each name. Both facts are now in the key. OP3-C2
is closed - see FXV-D9.

What is not measured is the price. The comment says:

> *"every moved train is named whether it turned or not - which is what keys the first fact, at no cost
> over keying the second alone."*

There is a cost, and it is precisely the extra discrimination the fix is for: under the old rule two
states with the same arrangement, the same tails and the same turns shared a key whatever set of trains
had been moved to get there; under the new one they do not. More distinct keys means more entries
pushed into `open` and more closed, and `SEARCH_LIMIT` is 50,000 - `NO_PLAN_FOUND` is a statement about
that budget.

I expect the growth to be small, and the reason is worth writing down so nobody re-opens it: for two
paths to the same arrangement to differ in WHICH trains moved, some train must have been moved and
ended where it started, which costs at least two extra moves, and `misplaced` is consistent, so those
states lose to the cheaper one. But "I expect" is not "994 of 83,881", and the figure the comment
quotes is explicitly about the turn answer alone - it says so, honestly, which is why this is a C about
one clause rather than about the paragraph.

**The separator note from OP3-C9 is still unaddressed**, and is now worse: `/` was already the
separator inside `firstClearRoute`'s own visited key, and it is now the separator in a comma-joined
list of user-chosen locomotive names appended to a `#`/`:`/`,`/`|`-joined list of user-chosen point
names. Two different states can spell one key if a name carries one of those characters. Vanishingly
unlikely and pre-existing for four of the five - but this file writes that sort of thing down, which is
why OP3-C9 raised it and why closing OP3-C9 on the other half leaves it unrecorded (see FXV-C8).

### FXV-C5: the visit history now survives a rebuild, including a rebuild onto a different railway

`restoreVisitHistory` is `putAll` into the new layout's `lastArrival`, and the key is
`recencyKeyOf(point)` - the block name, or the unique id. The comment says:

> *"a key naming a point this configuration no longer has simply never matches, which is the same
> answer as never having been there."*

True, and it answers the case it was written for. It does not answer the other two:

- **A different railway.** `parseAuto` is reached from `TrainControlUI.java:23916` -
  `this.model.parseAuto(this.autonomyJSON.getText())` - which takes any configuration the operator
  loads or pastes, not only a rebuild of the one in front of them. Load railway B after railway A and
  every block name the two happen to share arrives pre-visited. `LEAST_RECENTLY_VISITED` then ranks B's
  first few journeys off A's evening. This is small - the effect decays as B records its own arrivals -
  but it is the opposite of what the field's own javadoc promises ("a layout switched on tomorrow has
  not been anywhere yet").
- **Nothing is ever pruned.** Keys for points no configuration has any more are carried forward by
  every subsequent `putAll`, for the life of the session. Harmless in size; not harmless if a station
  is removed and later re-added under its old block name, which then arrives with a timestamp from
  before it existed.

And one ordering note rather than a defect: `putAll` overwrites unconditionally, so if anything could
record an arrival on the new layout between `Layout.fromJSON` returning and `restoreVisitHistory`
running, the restore would move that timestamp backwards. I could find no path that does - `fromJSON`
starts nothing - so this is a trap for the next author rather than a live bug, and `merge(…, Math::max)`
closes it for one word.

The narrow fix for the first case is to carry the history only when the incoming configuration is the
same one - which `AutonomyCompanionStore`'s active-configuration name already knows.

### FXV-C6: the arrival-side gate closes the only door for a square the running layout has not caught up with

The gate itself is right, and it is the answer OP3-C7 itself proposed. `appendTailCrossed` (`:3676`)
and `armTailPick` (`:3755`) both already had `point.getCurrentLocomotive() == null` and
`buildArrivedFromMenu` was the odd one out, so this is a sibling swept rather than a rule invented -
see FXV-D7.

What it costs is worth recording before somebody rediscovers it as a bug report. `setupChanged()` calls
`rebuildRunningLayoutSoon()`, so ordinarily the running layout catches up within the gesture and the
menu comes back. It does not catch up in two states:

- **While autonomy is busy.** `rebuildRunningLayoutFromSetup` returns without rebuilding when
  `isAutonomyBusy()` (`:6439-6446`), logging `autosetup.log.setupEditNotApplied`. Every edit made
  during a run leaves the two stores apart until the run ends.
- **When the setup will not build.** The editor is explicitly usable with outstanding problems - that
  is what the "Things to look at" list is for - and a configuration that does not load leaves the old
  running layout in place.

In both, a square the setup assigns a train to has no train on the running layout, and the arrival side
can no longer be set at all - not even into the setup, which is the durable store and which the same
click still writes. Before this commit the operator could set it and only the running-layout half was
wasted.

Adam's standing preference (`guards need a way past`) is for no check rather than an over-strict one.
The middle answer, if this turns out to bite: keep the item when the SETUP names a train and write only
`session.setArrivedFrom`, skipping the running-layout half that has nowhere to go - which is the same
decision `toJSON` already makes.

### FXV-C7: the new bulk-clear test asserts a precondition about a set the menu does not use

`testTheLengthClearsCarryTheEditorsSentence` establishes three preconditions. Two are the sets the menu
items read. The third is not:

```java
assertFalse(session.tilesWithALocomotive().isEmpty(),
    "precondition: no square holds a locomotive, so that clear has nothing to warn about");
```

`clearLocs` is gated on `session.placementsAutonomyWillWrite().size()`, and the comment eleven lines
above it in `AutonomyEditorPanel` (`:2214-2215`) says the two disagree:

> *"The same set the bulk home door writes (SEV-C3): `tilesWithALocomotive` reads every page and the
> door skips excluded ones, so the two disagreed on a layout with a page left out."*

`tilesWithALocomotive` walks the configuration file's `points`; `placementsAutonomyWillWrite` is
`placedLocomotives()`. On a snapshot whose only placements are on an excluded page the precondition
passes, `clearLocs` shows its "nothing to clear" notice, `namingCancel` is 2, and the test fails saying
one of the three clears is showing the track diagram's sentence - which is not what happened. That is
`guard-and-affordance-same-question` in a test: assert the question the item is greyed by.

Two smaller things in the same method:

- The comment says *"A pair of keys swapped in any of the four places that choose between the two
  sentences takes one of these away"*. After OP2-C2 there is **one** place that chooses -
  `bulkClearWarning` - and of the four call sites, two are dialogs (`:9740`, `:9779`) this test cannot
  see, because it reads tooltips. The commit message repeats the claim. What the test really holds is
  the two tooltip call sites, which is worth having and is not what it says.
- The loop only ever offers a length to the FIRST tile `getTiles().keySet()` yields, and marks
  `measured` non-null whether or not the set took. Whether the class passes therefore depends on map
  iteration order landing on a tile that can hold a length. The precondition turns that into a loud
  failure rather than a silent pass, which is the right side to fail on - but a loop that keeps trying
  until one takes costs one line.

The count itself is exact, and that is the good half of this test: only three values in the whole bulk
menu can contain the word Cancel, so `>= 3` is `== 3` - see FXV-D14.

### FXV-C8: two findings are marked fixed with half of their own text untouched

This is OP3-C8's shape, twice, in the round that closed OP3-C8.

**OP3-C11** named two things: the grid build outside the `try`, and the `finally`'s own
`invokeAndWait`, which *"throws `InterruptedException` if the calling thread's interrupt flag is set -
which is precisely the case the commit message names… the interrupt replaces the original exception on
the way out."* The build was moved inside the bracket, correctly. `DiagramExport.java:196-201` still
reads:

```java
finally
{
    javax.swing.SwingUtilities.invokeAndWait(() ->
    {
        if (grid[0] != null) grid[0].discard();
    });
}
```

with no catch. The status row says *"fixed 2026-09-20 - the grid build is inside the bracket too"*,
which is accurate about what was done and reads as closing the finding.

**OP3-C9** named the comment's overclaim and, in the same entry, the separator becoming load-bearing in
a string of user-chosen names. The comment was fixed; the separator is unmentioned anywhere in the
tree. Row: *"fixed 2026-09-20 - the comment says what the key actually spells"*.

Neither leftover is worth much on its own. What is worth something is that a reader counting open items
from the tables now misses both, which is exactly what the SOP's "one status, one location" is
protecting.

### FXV-C9: three status rows disagree with the tree

- **OP3-B1** is still `open` in its own table. `49de68b7` removed the other 119 keys and rewrote the
  rule, which is the whole of what OP3-B1 asked for.
- **OP3-B2** is still `open`. `d2541437` added the guard, and its commit message says so by name.
- **UIX-C4**'s row still reads *"fixed 2026-09-19 - 118 keys out of all eight bundles"*. It is 237 now,
  and that row was named in OP3-B1's own `where` as part of what had to change.

Safe direction to be wrong in, and still wrong. The `C` table in the same OP3 document was updated in
`fde1b7fb`; the `B` table above it was not.

### FXV-C10: one of the seven untracked files is not on disk, against the commit's own promise

`5d1e23b8` says:

> *"Removed from the index only; every byte is still on disk, and nothing under `cs2_sample_layout`
> has been written, moved or deleted."*

Six of the seven are there. `cs2_sample_layout/config/autonomy/configuration-Main_bak.json` (261 lines
in the commit that removed it) is not:

```
Main_bak.json              17440  Sep 13 22:36
configuration-Main.json     7583  Sep 20 05:31
setup.json                  6836  Sep 20 05:31
```

I cannot say the untracking deleted it - the directory's mtime is 05:31 on 2026-09-20, hours after the
commit, and a test run rewrote the two live files at the same moment, so something else in the morning
is at least as likely. What I can say is that the claim does not hold today, and that this file is the
one of the seven that `AutonomyCompanionStore` would actually have enumerated: it carries the
`configuration-` prefix that OP3-C3 pointed out `Main_bak.json` lacks. So the blessed fixture had one
more autonomy configuration in it than it has now.

Worth one look at whether anything in the suite counts the sample layout's configurations, and worth
correcting the commit's sentence in whatever record cites it. `git status` is clean and the `.gitignore`
narrowing is genuinely narrow - see FXV-D19.

### FXV-C11: the crossing test is now deterministic and still has no floor on what it found

The OP3-C10 rewrite is right and is a real strengthening: every ordered pair across the two groups is
asked, the answer must be uniform, and a tile that locked some pairs and not others now fails loudly
instead of answering differently on different runs.

Two gaps remain, both of the "property tests need a floor" kind, and both matter because the javadoc's
claim got BIGGER in the same commit (*"It carries FOUR roads rather than two… so eight directed legs
cross it"*):

- Nothing asserts how many legs were found. `assertFalse(eastWest.isEmpty())` and the same for
  `northSouth` are satisfied by one leg each - so a reducer that stopped emitting the diagonals would
  leave this green while the javadoc goes on claiming eight.
- The classification is now `if (y == 2) … else if (x == 3) …`. A leg whose start is neither on row 2
  nor on column 3 is silently dropped rather than counted, and so is a leg starting on the crossing
  itself, which the old independent `if`s would have put in both groups. On this fixture the four
  sensors are all on row 2 or column 3, so nothing is dropped today; the filter is what would have to
  change if the fixture ever grew.

One `assertEquals(eastWest.size() + northSouth.size(), expected)` per tile type - 4 for an overpass and
a crossing, 8 for a double slip - turns the javadoc's new sentence into something the class holds.

### FXV-C12: `fde1b7fb` claims OP2-C13, which is in `a4da36d5`

`fde1b7fb`'s message lists *"OP2-C13 - the route re-bind logs the tile that lost its route"*. Its
`src/` diff is two files - `AutonomyEditorPanel` (the bulk-clear builder) and `MarklinRoute` (SVB-C3's
comment). `rebindRouteTiles`' new `logf` is at `MarklinControlStation.java:3618-3625`, and
`git log -S` puts it in `a4da36d5`, whose own message does not mention it.

The fix itself is correct and I checked it: `bound == null && c.getRoute() != null` logs once, before
`setRoute(null)` makes the condition false, so a repeated rebind is silent rather than chattering. Only
the provenance is wrong, and provenance is what a commit message is for in a repository whose comments
cite commits.

### FXV-C13: the shortened path-integrity tooltip describes a bounded check as an unbounded wait

Nineteen of the twenty shortened values still say what the code does. This one changed its meaning:

> OLD: *"Confirms via the Central Station that a path's switches and signals reached the commanded
> position before the train departs; otherwise the train is held. Recommended."*
>
> NEW: *"Holds the train until the Central Station confirms every switch and signal on its path is set.
> Recommended."*

`Layout.java:56-60`:

> *"When true, `configureAndLockPath` verifies (via the CS echo) that every accessory on the path
> actually reached its commanded state before releasing the locomotive; **on a persistent mismatch it
> stops that locomotive and releases its locks** rather than letting it depart onto an unset path."*

and the deadline is `PATH_VALIDATION_MS * (accessories + 1)`, not forever. "Holds until it confirms"
promises a wait that ends in departure. What actually happens when confirmation does not come is that
the move is abandoned, the locks go back, and - past
`PATH_VALIDATION_ALERT_THRESHOLD` - the operator gets a popup. The old sentence's "otherwise the train
is held" was clumsy and at least gestured at the failure case; the new one dropped it.

Ten words put it back: *"Holds the train until the Central Station confirms every switch and signal on
its path is set, and stops it if they never are."*

### FXV-C14: the OP3-C7 test can only run on a snapshot that has a tail-crossing junction

`testTheArrivalSideIsNotOfferedForAnEmptySquare` uses `Fixture.open()`, which searches the running
layout for a standing train whose square passes `TailCrossedPrompt.wouldAsk(running, point,
point.getArrivedFrom(), LONG)` and throws `SkipException` when it finds none.

That condition - a junction behind the train with two roads back and a sensor on at least one of them -
is what the other tests in the class are about. It has nothing to do with OP3-C7, whose claim needs
only a square with a train on it. So this test skips on any snapshot without a tail-crossing junction,
and a skip reads as green (`test-green-is-not-no-failures`).

The fixture it needs is two lines: find any occupied Point whose square the station index knows, and
open the editor on its page.

### FXV-C15: the OP3-C4 control's precondition is asserted after the call it qualifies

`testLockEdgesSurviveTheFile`, `:331-341`:

```java
reserve.invoke(stand, loc());

// THE SAME INSTANCE BOTH TIMES…
assertSame(loc(), loc(), "precondition: loc() no longer returns one instance…");

assertEquals(stand.getArrivedFrom(), "LE Junction", …);
```

The precondition is true whenever it is true, so asserting it afterwards is sound - but it reads as if
it guarded the `reserve` above it and it does not, and the note OP3-C4 asked for was about the call.
Moving it one statement up costs nothing and makes the order say what the comment says.

The rest of the OP3-C4 fix is right, and is the best single change in the round: setting the side on
the empty square before serialising makes the assertion a test of `Point.toJSON`'s gate instead of a
test of a field that was already null. `assertEquals(stand.getArrivedFrom(), "LE Junction", …)`'s
reversed `(actual, expected)` argument order, which OP3-C4 also mentioned, is still reversed - failure
message only.

---

## D - not defects, and checks that came back clean

| id | what was checked |
|---|---|
| FXV-D1 | none of the 119 newly-removed keys is reachable at runtime by any route I could construct |
| FXV-D2 | all eight bundles hold identical key sets at every revision in this round, with no duplicates |
| FXV-D3 | 20 tooltip values changed in all eight bundles: no placeholder lost or renumbered, no straight apostrophe, all ASCII |
| FXV-D4 | `route.ui.confirmRouteRenamed` landed in all eight bundles with `{0}`, `{1}`, `{1}` in each |
| FXV-D5 | `candidate.getId() == loadedId` is a value comparison, and the null case returns before the unboxing |
| FXV-D6 | the OP2-C9 test's closing `"gone"` assertion is the control that separates the new branch from the old |
| FXV-D7 | OP3-C7's gate matches its two siblings, and every other door that writes an arrival side already has it |
| FXV-D8 | the AUR-C3 test is not tautological: a real `parseAuto`, two preconditions, and a red without the fix |
| FXV-D9 | the widened `tailKey` really does key `movedAlong.containsKey(l)` - no moved train escapes the list |
| FXV-D10 | OP3-B2's two named neighbours are both guarded |
| FXV-D11 | UIX-C2's corrected comment about the key post-processor is accurate |
| FXV-D12 | OP3-C1's replacement sentence is true: `ui.main.toolbar.functions` is in no bundle |
| FXV-D13 | every citation added in this round resolves in `findings.tsv`, and no new test class was added |
| FXV-D14 | the bulk menu really does have exactly three sentences that can name Cancel |
| FXV-D15 | `testMessageBundles`' duplicate-key example cites a key that has gone, and is past tense about it |
| FXV-D16 | the other 19 shortened tooltips still describe what the code does |
| FXV-D17 | the javadoc `49de68b7` orphaned in `TrainControlUI` was repaired by `a4da36d5`, onto the right method |
| FXV-D18 | every test class touched here owns its sandbox, so the new mutations cannot reach another class |
| FXV-D19 | the `.gitignore` narrowing is narrow, and `git status` is clean |

**FXV-D1.** Four routes, for each of the 119. (1) Exact literal in any `.java` in `src/` or `test/`:
none. (2) Any occurrence at all, in any file type, anywhere under `src/` or `test/` including the
`.form` files: two hits, both benign - `autolayout.ui.errorAddEdge` inside a past-tense javadoc
sentence in `testMessageBundles` (FXV-D15), and `autosetup.ui.errorCannotBuild` as a substring of the
live `autosetup.ui.errorCannotBuildDetail` and `…DetailOne`. (3) Concatenation: every
`I18n.t|f|getString("literal" + …)` in `src/` is one of the five builders plus `bulkClearWarning`'s
`key + "AtOnce"` (FXV-B1), and no removed key begins with any of the six. (4) Keys passed as a
variable: 25 sites, and each one is fed by literals spelled out in the same file - `ArrivalSidePrompt.
keyFor`'s `switch` is the pattern, and its javadoc says why. Nothing shields a removed key by a route
the guard cannot see.

**FXV-D2.** Key sets at `104d3172^`, `104d3172`, `4e2d02b5`, `49de68b7` and `fde1b7fb`: 2033, 1915,
1915, 1796, 1797 - identical across all eight bundles at every one of the five, with zero duplicates.
The `+1` is `route.ui.confirmRouteRenamed`. This also corrects `49de68b7`'s message, which says *"All
eight bundles are back to equal key counts - the first pass silently skipped one line per
translation"*: they were already equal at `104d3172`, so the skipped line must have been in an
uncommitted working state. Harmless, and the end state is right, but a reader looking for the
disparity in the history will not find it.

**FXV-D3.** All 20 values, in all eight files, compared before `7ac40714` and after `4e2d02b5`: the set
of `{n}` indices is unchanged in every one; no value contains a straight `'` (which `MessageFormat`
would eat); no value contains a character above `~` (`testMessageBundles` would catch that, and the
translations use plain-ASCII substitutions - "omdoebt", "geoeffnet", "abrio aqui" - deliberately); and
no translation was left at its old value while the English changed.

**FXV-D5.** `Route.getId()` is declared `abstract public int getId()` and `MarklinRoute` returns `int`,
so `candidate.getId() == loadedId` unboxes `loadedId` and compares values - not the reference trap that
an `Integer`-returning getter would have set for ids above 127. `nameNowHeldById` returns before the
comparison when `loadedId` is null, so the unboxing cannot throw. `loadedId = route == null ? null :
route.getId()` types as `Integer` and does not unbox in the null arm.

**FXV-D7.** `appendTailCrossed` (`:3676`) and `armTailPick` (`:3755`) already carried
`point.getCurrentLocomotive() == null` before this commit, so the new gate makes the odd one out match
its own family rather than inventing a rule. And every other writer of an arrival side writes only past
an occupant: `GraphLocAssign:261` (`if (point.getCurrentLocomotive() != null)` around the whole block),
`TrainControlUI:7899` (same), `LayoutRightclickAutonomyMenu:1209` (after a successful `moveLocomotive`).
No door was left ungated and none was closed twice.

**FXV-D8.** The test notes an arrival, copies the history, runs a real `parseAuto`, asserts the layout
object actually changed (`assertNotSame`), and compares the rebuilt history against the copy. It uses
the same accessor on both sides, which is the shape worth suspecting - but the two objects are
different `Layout`s and the rebuilt one's map is populated only by `restoreVisitHistory`, so without
the fix it is empty and the assertion is red. The `assertFalse(history.isEmpty())` precondition is the
one that makes it mean something, and it is there. Its `finally` restores the shared setup by
re-running `parseAuto(was)`, and `toJSON`/`fromJSON` do carry `activateRoutes` and `activateRouteIDs`
(`Layout.java:10308-10309`, `:11485-11514`), so `testAutoRoute`'s state round-trips whichever order the
two run in. The one residue is that the restored layout keeps the noted arrival - the fix carries it
across the restoring `parseAuto` too - and nothing else in the class reads `lastArrival` on the shared
layout, so it does not reach anything today.

**FXV-D9.** `movedAlong` is written in exactly two places: `HomeStaging.java:821`, after
`firstClearRoute` returned non-null for a train not at home, and `:1026`, with a `path` between two
different squares. Neither can be null or empty, so `if (road == null || road.isEmpty()) continue`
never skips a real entry and `turned` is `movedAlong.keySet()` with a status word. OP3-C2's remaining
half is genuinely closed.

**FXV-D10.** OP3-B2's *"two neighbours worth the same glance"* were `:21037` and `:21360` at the time;
they are `:21059` (`if (r != null && r.commandsDrive(value))`) and `:21382` (`if (currentRoute != null
&& …)`) now, and both guard. The doors that do not are in FXV-B4, and none of them was named by OP3.

**FXV-D11.** The corrected Control+M comment claims the main window's `KeyEventPostProcessor` cannot
reach `LayoutEditor` because it returns unless the focus owner `isDescendingFrom` the main window and a
top-level frame's parent is null. `TrainControlUI.java:9498` is `if (owner == null ||
!SwingUtilities.isDescendingFrom(owner, this)) return false;` and `LayoutEditor extends
PositionAwareJFrame`, which is a `JFrame` - an unowned top-level window whose `getParent()` is null, not
a `JDialog` whose parent would be its owner. The claim holds, and it is the kind that would have been
false had the editor been a dialog.

**FXV-D13.** `AUR-C3`, `OP3-C1`, `OP3-C2`, `OP3-C4`..`OP3-C11`, `OP3-B1`, `OP3-B2`, `OP2-C2`, `OP2-C5`,
`OP2-C6`..`OP2-C13`, `SVB-C3`, `UIX-C2`, `UIX-C3` - all 24 resolve to exactly one row in
`docs/manual-tests/findings.tsv`. `testEveryTestIsInTheBattery` reads `build.xml` per CLASS and no new
test class went in, so the battery list is unaffected. `openAutonomyEditorIfItCan` was added to
`testTheRefusalsAreAskedAtTheDoors` (`:96`), which is the *"refusal door added without being declared"*
`a4da36d5` names.

**FXV-D14.** Of everything the bulk menu can put in a tooltip, exactly three English values contain the
word Cancel: `autolayout.ui.confirmClearLocomotives`, `…confirmClearAllTrackLengths` and
`…confirmClearAllMaxTrainLengths`. `confirmClearAllHomeLocomotives`, `confirmHomeEveryTrainWhereItStands`,
the mass-assign tooltips and all five "nothing to clear" notices do not. So
`assertTrue(namingCancel >= 3)` cannot be satisfied by an unrelated item, and a swap at either tooltip
call site takes the count to 2. The assertion is tighter than its `>=` suggests.

**FXV-D16.** Checked each of the other 19 against the code or the model it describes.
`BALANCED_PRIORITY`'s new text ("weighs priority against the track needed to reach it… without
priorities, the same as Over the Shortest Track") matches `ratioOf` and the enum's own javadoc,
including the default-priority case. `LEAST_RECENTLY_VISITED`'s keeps "station priority still applies
first", which is the band gate. The two length rules' "with no lengths set… this is At Random" is the
same claim as before, said once instead of twice, which is what `4e2d02b5`'s message says it did. The
losses are all reasons rather than rules - `BALANCED_PRIORITY` no longer says it is the only preference
that sees past the top priority band, and `tooltipPathType` no longer says why Auto and Manual differ -
which is the editorial line `7ac40714` declared and stuck to.

**FXV-D18.** `testTheTailCanBeGivenInTheEditor`, `testTheBulkClearSaysWhatCancelDoes` and
`testTheWindowTakesTheKeyboard` each open their own `support.LayoutSandbox` and their own
`MarklinControlStation` in `@BeforeClass` and dispose both in `@AfterClass`. So the OP3-C7 test's
`copy.setLocomotive(null)` on the running layout - which `Fixture.close()` does not undo, since it
restores the setup only - and the bulk test's unrestored `setTileLength`/`assignMaxTrainLength` cannot
reach another class in a one-JVM run. Within their own classes the next `Fixture.open()` rebuilds the
running layout from the setup, which repairs the first; the second is only read by assertions that do
not care.

**FXV-D19.** The three new `.gitignore` entries are `/random_test_layout/`, `bash.exe.stackdump` and
`/cs2_sample_layout/config/autonomy/*_bak.json`. The third is the one worth checking and it is narrow:
it hides only `*_bak.json` inside one directory, so a real change to `configuration-Main.json` or
`setup.json` still shows up. `git status --porcelain` is empty at `fde1b7fb`, and both live files were
rewritten by a test run on 2026-09-20 05:31 with identical content.

---

## What this pass did not cover

**Nothing was run.** No test, no build, no application. Every claim above about a test is a claim about
its source. Where that matters most:

- **FXV-B1 is the one thing I would most like to have run**, and it is also the one I am most confident
  about: it is arithmetic over files, I reimplemented the class's own rule line by line in Python, and
  the two keys it names are visibly built by concatenation at `AutonomyEditorPanel.java:9885`. But the
  claim "this class is red at HEAD" is still a re-derivation of the class, not the class.
- **The A\* cost of the widened `tailKey` is unmeasured** (FXV-C4). `core.testReturnHomeOnRealLayout`
  and the planner battery are what would settle whether the extra keys are the handful I argue for or
  something that moves `NO_PLAN_FOUND`. The blessed baseline is the reader to watch.
- **The crossing claim's uniformity is asserted, not verified.** OP3-C10's rewrite fails loudly if a
  double slip locks some pairs and not others; whether it does is a question for `GraphReducer`, and I
  read the fixture rather than reducing it.
- **Every "this test would be red without the fix" is reasoned.** That includes the AUR-C3 test, the
  OP3-C7 test and the OP2-C9 assertions.

**Not looked at:** the `triage.db` rewrites in `a4da36d5` and `fde1b7fb`, and the 26 rows added to
`findings.tsv` beyond checking that the ids this round cites resolve. The manual-test record was not
read.

**Not looked at in depth:** OP2-C10 (the injected page removed again) and the
`testSwitchingToACentralStationLayout` / `testAdvancedRoutes` / `testTheRefusalsAreAskedAtTheDoors`
registry edits in `a4da36d5`. I read the hunks and they do what the message says; I did not trace their
callers.

**Deliberately not done:** I did not re-derive the dead key set a third time by hand. The two
derivations here agree with each other and with `49de68b7`'s own arithmetic (118 + 119 = 237 = UIX-C4's
original number), and the second one deliberately looked at every file type in `src/` and `test/`
rather than only `.java`, which is the direction the guard itself is blind in.

**One thing I could not settle:** whether `configuration-Main_bak.json` was removed by the untracking
or by the test run that rewrote the two live files four hours later (FXV-C10). The file is gone either
way and the commit's sentence is wrong either way; who did it decides only whether there is a second
defect behind it.
