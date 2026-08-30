# Release candidate: five passes over v3_0_0_rc1

**Status:** open

**Prefix for citing these findings elsewhere:** `RC`

**Reviewed:** the release candidate `0af7bab5`, from five directions at once - the comments in every
file touched since `3017f719`, the last two days of commits, every commit since `v2_7_4c` read for
regressions, the autonomy model on its own terms, and the test suite. Every finding below was
re-derived by hand before anything was touched, and several reported findings were dropped on that
reading.

**Why this pass happened.** Adam tagged the commit as a release candidate and asked for in-depth review
before manual testing.

**What the round is really about.** Nine of the twelve comment findings, and two of the five defects,
were introduced by THIS WEEK’S OWN FIXES. RC-A1 is the shape: LE-A6 fixed a real defect by standing a
flag down in the one place every edit passes through, which was sound reasoning about the wrong
granularity, and it cost the fix it was protecting. RC-A2 is the same story one method over - LE-B5
fixed the success path of a migration and left the branch above it asking a question of the wrong
object. **A fix is a change like any other and deserves the same suspicion**, which is the argument for
having run this pass at all rather than shipping on green tests.

**Adam's own comments were left alone**, on his instruction. Every attributed quote in the files below
was read as context and never edited; the checker that applies these edits refuses any anchor
containing `Adam`.

**The finding that matters most is C3.** `Layout.fromJSON` said blockedBy names are never resolved,
three lines above a comment saying they are resolved after the loop, and forty lines above the code
that resolves them. A reader trusting the first one writes a lookup that already exists, or "fixes" a
name-matching rule that was never the mechanism.

**Nine of the twelve comments were written or last edited during the week under review**, and three of
those (C10, C11, and half of C9) were written by this round's own fixes and were false the day they
landed. That is the pattern worth carrying forward: a comment describing a guard is written while the
guard is being added, and the next commit in the same round removes the guard.

---

## A - high

### A1 - a cut whose paste was not the very next gesture lost the setup

| | |
|---|---|
| **Disposition** | fixed, `regression.testLayoutEditorBulkEdits` (two tests) |
| **Manual test** | [MT-228](../manual-tests/tests.md#mt-228) |

`clipboardWasCut` is the only thing that carries a group cut's setup to where it lands: `cutSelection`
calls `deleteSelection(false)`, which deliberately tells autonomy nothing, because the paste is the
other half of the gesture (LE2-B7).

LE-A6 stood that flag down inside `snapshotLayout`, reasoning that any edit falsifies it and that
listing the doors would leave the next one unswept. **Every edit snapshots.** So cut a set-up yard,
press "+" to make room, paste it back, and the move had silently become a copy - the station flags,
names, lengths, facings, barred arrivals, signal pairings, portal partners and placed locomotives all
left keyed to the squares the cut emptied, where the next reconciling save prunes them. Rotating a
tile, dropping one from the palette, shifting a row, or switching page and coming back did the same.

**The flag was standing in for a per-square fact** - "the square this came from is empty now" - and
asserting it for the whole clipboard gave it up for all of them. `cutMoves` now asks the question
itself, per origin: is this square still empty, and is it on the page being pasted onto? That needs no
list of doors either, and it does not punish nineteen innocent squares for the one that was refilled.
Leaving a page and coming back now keeps the cut, which the flag could not do.

**The two tests drive a real `LayoutEditor`**, because a source scan cannot see this: the flag is read
in the right place and written in the right place, and what was wrong is when the write happens
relative to the read. That is LE-A7's lesson applied to LE-A6's fix.

### A2 - the routing migration deleted its own source, by the branch LE-B5 did not fix

| | |
|---|---|
| **Disposition** | fixed, `regression.testTheRoutingChoiceSurvivesTheUpgrade` (new class, two tests) |
| **Manual test** | [MT-229](../manual-tests/tests.md#mt-229) |

LE-B5 fixed the success path: the pre-3.0.0 preference key goes only once `persistPathPreference` says
the configuration has it. The branch above it was left asking the LIVE layout whether the migration was
still needed - and the live layout carries what this same method wrote into it in memory on the
previous call. The first call stored nothing and correctly kept the key; the second read its own
writing, concluded the configuration had answered, and deleted it.

The user's routing rule was then gone, and RANDOM took its place, for every future session, silently.
The second call is one autonomy checkbox away: `loadAutoLayoutSettings` has fourteen callers. It is
deterministic for anyone whose autonomy comes from `autonomy.json` rather than a diagram configuration,
because `setGlobal` cannot succeed for them at all.

Asked of the store instead, which is the thing the question is about. **That needed a `getGlobal` to go
with `setGlobal`** - the missing half is what made the wrong source the easy one to reach for.

### A3 - an unreadable layout page deleted the sensors that only lived on it

| | |
|---|---|
| **Disposition** | fixed, `regression.testLayoutFolderRobustness` (three tests) |
| **Manual test** | [MT-230](../manual-tests/tests.md#mt-230) |

`syncLayouts` deletes every s88 in the database that no LOADED page mentions. That was safe while one
bad page took the whole parse down with it. The per-page tolerance - right in itself, and what
`testLayoutFolderRobustness` exists for - made it unsafe: a five-page folder whose third page is
truncated or half-copied loads four pages, and the loop reads four pages as "the railway".

Every sensor whose only appearance was on the third page is then deleted, permanently, taking the
autonomy points that watched them, and the deletions go into `LocDB.data` on exit. The existing
`!feedbackAddresses.isEmpty()` guard covers only TOTAL failure, which is exactly why the total case was
safe and the likely case was not.

Pruning now happens only when nothing failed to read, and says so when it declines.

### A4 - a folder where every page fails no longer fell back to the Central Station

| | |
|---|---|
| **Disposition** | fixed, `regression.testLayoutFolderRobustness` |
| **Manual test** | [MT-230](../manual-tests/tests.md#mt-230) |

The revert lives in a `catch` around `syncLayouts`, so it needs a throw. One bad page used to provide
one; with the per-page guard an all-bad folder returns an empty list instead. `clearLayouts` has
already run, so the user gets an empty diagram, no message, and the override preference kept - the same
nothing at every launch.

It throws again, and **throws before clearing**, so the diagram on screen survives the attempt. An
empty folder is unaffected, because it fails no pages.

### A5 - the warning that explains both was debug-only

| | |
|---|---|
| **Disposition** | fixed |

`layout.warningPageCouldNotBeRead` was logged with `debugOnly = true`, so the one thing that explains an
incomplete railway was invisible unless debug mode happened to be on. A page that will not read is a
fault the user can act on - re-copy the file, check the folder - which is what separates a warning worth
showing from noise. It is also now translated into all eight languages, having been English-only on the
strength of being a debug line.

---

## B - medium

### B1 - undo after shrinking a page put a station's name back outside it

| | |
|---|---|
| **Disposition** | fixed, `regression.testLayoutEditorBulkEdits` |
| **Manual test** | [MT-227](../manual-tests/tests.md#mt-227) |

`shrinkEdges` snapshots for undo *before* it drops the captions on the row and column it is about to
remove - which is LE-B1's fix, and correct on its own path. Undo then restores the components and the
captions, but the page **size** is not part of an undo snapshot, and a shrink is only offered when the
trimmed edge holds no track, so no restored component pins the size back either.

So Ctrl+Z after "-" gives the caption back and not the row it stood on: a station name that is present
in the setup, never drawn, and with no square left to click to remove it. That is precisely the state
LE-B1 was raised for, reached through undo instead of through the shrink - LE-B1 guarded the shrink
only.

**Filtered on the way back in, rather than by teaching undo about page size.** Restoring the size would
mean snapshotting it, which changes what an undo entry *is*, on the path every edit in this editor goes
through. The invariant is narrower and provable: a caption outside the page is never valid, whatever
put it there. `forgetCaptionsOutsideThePage`, called where captions are restored, covers this and
anything else that ever restores one onto a page that has since shrunk.

---

### B2 - `BALANCED_PRIORITY` inverted below zero

| | |
|---|---|
| **Disposition** | fixed, `core.testRoutePicking` (two tests) |
| **Manual test** | [MT-231](../manual-tests/tests.md#mt-231) |

`ratioOf` was `((priority + 1) * 1000) / length`, and the `+1` baseline was reasoned about only from a
default of zero. Negative priorities are supported and meant: `Point.setPriority` takes them, and
`AutonomyEditorPanel` says in as many words that this is a field "where negatives are perfectly valid".

At priority -1 the numerator is 0, so every route to that station scores 0 and distance stops mattering.
At -2 and below the numerator is negative, and **dividing a negative by a larger length makes it
larger** - so among de-prioritised stations the rule picked the most distant route it could find. "Go
here less" was read as "go here the long way round". Measured: -500 at length 2 against -55 at length 18.

Below zero a station is now worth `1000 / (1 - priority)` - 500 at -1, 333 at -2, 166 at -5: always
positive, always less than the 1000 an ordinary station gets, still ordered among themselves. Nothing at
or above zero changes, so a railway that has never used a negative priority behaves exactly as before.

**Shrinking rather than clamping at 1**, because a clamp stops the inversion and then ties every
de-prioritised station with an ordinary one, which is a different wrong answer.

The existing test could not catch this and could not catch much else: with the far station at priority 5
the ratio and plain `1/distance` give the same answer, so replacing the numerator with a constant left
it green. Both new tests are built to disagree with `1/distance`.

### B3 - Add Route stopped proposing a name

| | |
|---|---|
| **Disposition** | fixed |
| **Manual test** | [MT-232](../manual-tests/tests.md#mt-232) |

Against 2.7.4c. The next free name is still worked out - `final String newName = String.format(...)` -
and then never used: the editor is constructed with null. Press Add Route, add a command, press Save,
and it is refused for having no name. The dead variable is what makes this an oversight rather than a
decision.

**Not fixed by passing `newName` as the constructor's `routeName`.** That argument becomes
`originalName`, and a non-empty `originalName` is what makes Save an EDIT of an existing route rather
than an add - so passing it would make Add Route try to rename a route nobody has created. Only the box
is filled, and the unchanged-signature is re-taken afterwards so that closing straight away is still
"nothing to throw away".

### B4 - the route editor is the one child window that does not stay on top

| | |
|---|---|
| **Disposition** | fixed |
| **Manual test** | [MT-232](../manual-tests/tests.md#mt-232) |

`AddLocomotive`, `LayoutEditor`, `LayoutPopupUI`, `LocomotiveSelector` and `UsageHistogram` all call
`setAlwaysOnTop(parent.isAlwaysOnTop())`, and so did the text editor this replaced. It is missed exactly
where it matters: capture is ticked in this window and the switches are thrown on the layout window, so
clicking over there hid the window being watched.

### B5 - Start could leave the layout "running" with nothing running

| | |
|---|---|
| **Disposition** | fixed, `core.testRoutePicking` (two tests) |
| **Manual test** | [MT-233](../manual-tests/tests.md#mt-233) |

`runLocomotives` sets `running` before it looks at a single locomotive, and both of its skips - start
point inactive, preferred speed outside 1 to 100 - return without starting a thread. Skip every
locomotive and the flag is set with nothing that can ever clear it, because `announceRunFinished` is
only reached from a thread decrement that never happens.

Until Stop is pressed: `moveLocomotive`, `renamePoint` and `setSimulate` all refuse, every protecting
signal on the layout has been commanded, and `isPathClear` applies the autonomy-only rules to hand
dispatches. Recoverable, which is why it is a B - and nothing tells the user how.

`executeTimetableInternal` guards exactly this and says so in as many words; `runLocomotives` did not
inherit it. It cannot ask up front the way the timetable does, because the skips are decided one
locomotive at a time, so it asks afterwards and says why nothing started. The window's own guard covers
an empty run LIST, not a list where every entry was skipped. A preferred speed of 0 is the state of any
locomotive placed on the graph without the speed dialog ever being opened.

## C - low

All twelve are comments, except C12, which is a line of code that disagreed with the comment above it.
None changes behaviour.

### C1 - "no second dialog results" was made false by OB-140

`TrainControlUI:18397` reasoned that `BusyDialog` runs its work off the event thread, and off it the
wrapper is a plain guarded call. OB-140 gave `syncWithCS2` its own `BusyDialog.showUntilClosed` on
exactly that branch, so the condition the comment rests on is now the one that guarantees the opposite.

The nesting is left as it is, deliberately, and the comment now says so: the two dialogs do not
deadlock, and suppressing a nested spinner would mean `BusyDialog` deciding not to show - a mistake in
which hides *every* spinner, a worse failure than seeing two.

### C2 - the overlay strip called itself the scroll pane's column header

Three places in `AutonomyOverlayToggle`. OB-148 took it out of the scroll pane; it is a sibling above
the diagram now, which is the point of having moved it. The height reasoning still holds; the reason
given for it did not.

### C3 - `fromJSON` said blockedBy names are never resolved, and they are

`Layout.java:6649` said the names are read verbatim and that "nothing resolves them at load - the rule
asks by name at the moment it is applied". Three lines below, inside the same `if`, a second comment
says "Kept as names for now and resolved after the loop." The second is right: `Layout.java:7433` calls
`held.setBlockedBy(watching)` after resolving each name through `layout.getPoint`, logging and dropping
what matches nothing.

The tolerance the first comment describes is real, so that half was kept - it just happens at the
resolution site, by dropping, not by never looking.

### C4 - `AutonomySession.save()` promised pruning that DR-B10 made conditional

The headline is two sentences and says the method "forgets what the diagram no longer has", full stop.
DR-B10 - written for a real data-loss incident - makes that conditional on `pagesSafeToJudge()`: with a
page unloaded or numbering caught mid-renumber, nothing is pruned and a declined `Reconciliation` comes
back instead. All of that reasoning is present inline, sixty lines down. The two-sentence summary at the
top is what a reader trusts.

### C5 - `sideTowardNeighbour` claimed it "asks the graph"

Contrasted against the static `gridSideTowards` as if the two answered different questions. It calls
the private static `neighbour`, which is the same coordinate arithmetic, so the two answer identically
for any pair of squares on one page. The next paragraph of the same javadoc - portals are not reachable
here - contradicted the claim on its own. The rename is still worth having; DD-C9's reason for it, the
one-letter collision, is the true one.

### C6 - the fourth copy of a zoom claim DOC-C24 fixed three times

`LocIconCropDialog.setZoomFraction`'s `@param` said fraction 0 is "whole window filled". `MIN_ZOOM` is
0.5 and `getMinScale()` is `fitScale() * MIN_ZOOM`, so 0 is half size with white on every side. DOC-C24
corrected this exact sentence in the `zoomFraction` field doc, in `ZoomObserver.zoomChanged` and in
`getMinScale` - and missed this one. Fix one site, sweep the siblings.

### C7 - the menu separator described an order Adam changed

`AutonomyMenu` split itself into "everything above chooses which setup is in force and everything below
is housekeeping on the file that holds it". Adam moved `manageMenu` - the housekeeping - above the
separator on 2026-08-28, to sit directly under the configuration it manages. Both halves of the
sentence became false at once.

### C8 - `homeLoc` is a reference, and its javadoc said "by name"

`getHomeLoc` and `setHomeLoc` both said the assignment is made by name; the field is a `Locomotive` and
the setter takes one. The name is what gets written out and matched back on load, which is why the
trimming rules in the body matter - so the distinction is the whole point of that paragraph, and the
first line contradicted it. `setBlockedBy`'s `@param pointNames` also named an argument the method does
not have.

### C9 - two comments in one file disagreed about whether an item is disabled

`RightClickFunctionMenu`'s constructor said the departure/arrival slot item "is disabled with a note
when a DIFFERENT function holds the slot, rather than silently taking it". `autonomySlot`'s own javadoc
says the item still shows, unticked, names the function that holds the slot, and moves it when chosen.
There is no `setEnabled(false)` anywhere in the method. The code follows the javadoc.

### C10 - a guard OB-091 removed, described as still there

`restingBorder` explained that no border swap can shift the artwork because `receiveMoveEvent` returns
immediately in autonomy mode. OB-091, in this same round, gave the blue outline to the autonomy editor,
so it does not return and the border *is* swapped. The conclusion survives for a different reason -
`overlayLine` is sized to the room the resting border takes - which the three-argument javadoc directly
below already stated correctly.

### C11 - "not reachable from the menu today", passed to `setEnabled` twice

`canShiftDown` and `canShiftRight` each said they were a trap for a future caller rather than a live
predicate. `LayoutEditorRightclickMenu:476` and `:479` pass both to `addShift`, which hands the value
straight to `item.setEnabled`. They are live, and nearly always true - which is not the same as never
asked, and is what made the wrong claim plausible.

### C12 - "resolved ONCE", asked twice more three lines later

`RightClickMenuListener` introduced `subject` with a paragraph explaining that the locomotive is
resolved once as the menu is built, rather than asked again inside every listener. The line testing for
a local icon then called `ui.getButtonLocomotive(source)` twice.

**Fixed in the code, not the comment.** The comment has the intent right and every other line in the
block already follows it; this one did not. No behaviour changes - all three calls happen at build time
and return the same locomotive - so this is a C.

---

## What was checked and found sound

Recorded because a later reader should not have to re-derive it:

- `AutonomyCompanionStore`'s "eleven kept collections" counts, traced through fully - consistent.
- `AutonomyReport`'s "six doors" javadoc, against all six real `session.save()` call sites.
- Page-name versus page-ID keying claims across `AutonomySession`, `AutonomyCompanionStore` and
  `LayoutPageEdit`.
- `CSDetect`'s "only reachable hosts ever get here" retry gating, at both call sites.
- `MarklinRoute.locomotiveRenamed`'s removal - the logic really did move into `Route.namesLocomotives`
  and `locomotiveDeleted`, covering both commands and conditions.
- `NetworkProxy.ANY_RECEIVE_PORT`'s two-call-site claim, the startup latch ordering in
  `setViewListener`, and `AutonomyEditorPanel.placementChanged`, which deletes two of its own sentences
  under NR-7 for this same reason.

## Carried forward - for Adam

Everything below was reported by one of the five passes and is **not fixed**. Each was read against the
code before being written down here, and the ones I could not settle say so. They are in the order I
would take them.

### Not fixed, and I think they should be, but not by me alone

**1. `Edge.occupied` is a flag, not a count, and with `atomicRoutes` OFF one path's completion can
clear a lock another running path is holding.** `Edge.java:397-443`, released at `Layout.java:5013` and
`:2981`. The sequence needs an *asymmetric* lock relation, which is what the editor writes: train A
locks edge L, releases it early once its tail has passed, train B locks M and re-sets L as its
protection, A finishes and `unlockPath` clears L while B is crossing it - and train C is then dispatched
over L. **Not live for you today**, because your `autonomy.json` is `atomicRoutes: true` with all 892
relations symmetric, and I traced the atomic path and it is sound. But `atomicRoutes` is a checkbox on
the main window and `Readme.md:1357` advertises it. The existing note in `2026-07-code-review.md` scopes
this hazard to a hand-edited JSON; the reviewer showed it needs no hand-editing, and found the enabling
shape in your own `backup2026-07-24` configuration.

*Why I did not fix it:* the fix is a reference count on `Edge.occupied`, which is the single most
load-bearing field in the locking model, and it wants your judgement on whether `atomicRoutes` off is a
mode you still want at all.

**2. The route guard is blind for the seconds a path is being locked.** `Layout.getActiveAccs` iterates
`activeLocomotives` only, and a dispatching locomotive is in `takingPath` but not yet in
`activeLocomotives` for the whole of `configureAndLockPath` - 150 ms per edge and per accessory, plus
validation. With **Enhanced path validation** on (the default) this is caught and the dispatch is
refused: degraded, not dangerous. With it off, or in simulation, the train departs onto a path an
s88-triggered route has just altered. This is AU-A2's defect from the other end - the dispatch is
invisible again, this time because it has not registered yet.

**3. A mid-run failure strands a train on locked track and drops that track's route protection at the
same moment.** `Layout.java:4562-4567`. The `RuntimeException` handler deliberately leaves the path
locked, which is right - the locomotive may be standing on those edges - and in the same block removes
`loc` from `activeLocomotives`, which is the only thing `getActiveAccs` reads. From that instant no
route is refused for the track the stranded train is on. If it was the only thread, `isRunning()` also
goes false and the platform signal stops being maintained.

*The narrow fix is an "abandoned but still locked" set, not unlocking* - the comment is right that
unlocking would be worse.

### Return-to-home planner, five findings I confirmed and did not fix

All in `HomeStaging.java`; the planner is a large enough surface that I did not want to change it
between a release candidate and your manual testing.

1. **Two homes on one sensor are proved IMPOSSIBLE even when both trains are already standing on
them** (`:392-406`). The exemption its own sibling scan carries twelve lines below (`:467-469`) is
missing here. On your graph `BottomMainC` / `BottomMainCTerm` share feedback 4, so with both trains home
and a third away, Return Home refuses the whole run and names the two that need nothing doing.
**This is the one most likely to bite you in manual testing.**
2. **A plan from a non-station origin is refused at execution and abandons the run** (`:837` vs
`isPathClear` at `Layout.java:2016`). HP-C2 taught `firstClearRoute` the inactive-origin half of that
rule and not the non-station half beside it.
3. **A locomotive left reserved on several points by a failed path makes the shadow map double-count
it**, and `misplaced == 0` becomes unreachable (`:135`, `:1459-1492`).
4. **The mover is exempt from its own sensor everywhere in the plan; `isPathClear` grants no such
exemption** when it reads the live feedback (`:1031-1080` vs `Layout.java:2024`). Hardware-conditional -
on pulsed feedback it never fires.
5. **A locomotive with no preferred speed abandons the entire staging run**, where `runLocomotives`
skips it and keeps everything else going (`Layout.java:4278`, `:4655`). Same shape as RC-B5, one method
over.

### The test suite - thirteen A-severity gaps

The suite pass found thirteen mutations that leave everything green. I did not work through them because
each is a test to write rather than a defect to fix, and the list wants prioritising with you. The ones
I would do first, because they cover machinery nothing executes today:

- **A101** - `grep -rn getLockEdges test/` returns nothing. Deleting the `addLockEdge` call in
  `Layout.java:7284` leaves the suite green, and every file-loaded configuration would then load with
  zero lock edges: the crossing protection silently absent.
- **A100** - the lock-multiplicity test asserts referential integrity, not multiplicity. Truncating
  `AutonomyBuilder`'s lock loop to the first copy of a split edge is green, and second and later copies
  of a conflicting edge then run unlocked.
- **A105** - `captureRunningLayout` - where every train is standing - is executed by no test. Your own
  report: run a train, open the diagram editor, save, and the train is back where it started.
- **A102** - the automatic route door (`execRoute(true)`) is exercised once, always with a conflict.
- **A113** - locomotive exclusions are never asked about a SECOND locomotive, so excluding A from a
  siding could bar everybody and no test would notice.

The full list, with the exact mutation for each, is in the suite reviewer's report; I have not
transcribed it here because it is long and I would rather you saw it whole.

**Also worth an hour, and cheap:** `tools/battery.sh` can never exit 0 - the three `test/support/`
fixtures always report `Total tests run: 0` and are always counted as skips - so its exit code carries
no information. And `@Test(enabled = false)` is invisible to all three layers that are supposed to
notice a test that does not run.

### Regressions against 2.7.4c that are decisions, not defects

Reported and confirmed; each needs you to say whether it matters:

- **Two stations may now be given the same name, silently.** 2.7.4c refused outright.
  `AutonomyBuilder` disambiguates to `Hauptbahnhof (2)` and its own javadoc says the user "is told at
  authoring time". Nothing tells them. (Ledger row 115.)
- **An edge's accessory commands can no longer be dry-fired from the desk.** `GraphEdgeEdit` had a Test
  button; commands are now derived from tile geometry, so a wrong port map produces a plausible path and
  the first thing that discovers it is a train.
- **`Readme.md:441-442` still says "The older text editor is still there".** It was deleted in
  `28bdfcc8`. Two documents make claims conditioned on its survival.
- **`LayoutEditor.editTextWithDropdown` has zero callers**, so the "station labels moved to autonomy"
  notice - translated into all eight languages - can never appear. The ledger says manual placement
  stays on the right-click menu; it does not.
- Smaller: the route editor forgets its position and size; commands can only be appended at the end; a
  station's maximum train length is no longer shown outside its own menu; excluded locomotives are not
  drawn; Ctrl+E / Ctrl+U are gone; no bulk "clear locomotives".

### Reported and left alone on purpose

- **`TileGraph.transparentRoutes`**: a contiguous panel of route buttons can conduct track, because each
  button counts its neighbour button as a facing port. Nine interior buttons on your layout already
  infer a through route backed by no track at either end. It is latent - the through-pair rule resolves
  every real case correctly today - and the remedy is a new warning, which is a change to what the
  checker says and wants your eye.
- **`GraphReducer.hasAnyConnection`** asks about the neighbour's ports, not the sensor's own, so the
  isolated-sensor guard passes for a sensor with no track of its own. One clause to fix; no instance in
  any of the three sample layouts.
- **`AutonomyBuilder` emits `mustReverse`** into the generated JSON although its sibling `canReverse` is
  filtered out. Harmless today - `parseAuto` ignores what it does not know - but it puts an
  authoring-only key into a generated file.
- **`sanitizeMultiUnits` re-reads the occupant after null-checking it** (`Layout.java:5220-5228`), which
  is the read-once pattern `Edge.isOccupied` documents a fix for. Unreachable today. Line 5227 is also
  the last hard-coded English log string in `automation`, typo included.
- **`StationCaption.onPill`** says a comment in `LayoutGrid` asserts the opposite of what the code
  draws. `LayoutGrid` was in a different agent's scope and I did not chase it. Nothing is at risk; it is
  a comment.

### What I could not settle

- **Whether `BALANCED_PRIORITY` should consider de-prioritised stations at all.** B2 stops the
  inversion and keeps them ordered below ordinary stations, which is defensible and conservative. Saying
  "a station at or below -1 is not a destination for this rule" would be a design decision, and it is
  yours.
- **Autonomy runtime parity with 2.7.4c** - whether a train picks the same routes and holds the same
  locks. `tools/parity/` exists for exactly this and its README records one still-open loss
  (`BottomInner -> Tunnel` has lost its alternative via `BottomCrossover`/`TunnelPre`). It needs the
  2.8.1 jar and a copy of the layout, and I did not re-run it this round.
- **`RouteEditorFrame`'s new save-time refusals** could lock somebody out of editing an existing route.
  On the 135 routes and 1284 commands in this repository's fixtures they cannot: every command is an
  accessory or a stop, every address in range. The reachable case is "route A calls route B, B was
  deleted" - **worth five minutes against your own routes before release.**

