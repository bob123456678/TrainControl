# Opus validation of the 2026-09-19 rulings

**Status:** open

**Prefix:** OP2, **not** OPV. The brief asked for `OPV` and said it was checked free. It is not:
`docs/reviews/archive/2026-09-04-opus-validation.md` declares it and `docs/manual-tests/findings.tsv:1370-1376`
carries `OPV-B1` and `OPV-C1` to `OPV-C6`, all closed. Renumbering would break citations that already exist,
which is the SOP's own rule, so this document takes `OP2` instead - checked free on 2026-09-19: no document in
`docs/reviews/` declares it, and `grep -rl "OP2-[ABCD][0-9]" docs/ src/ test/` is empty. The filename keeps the
`OPV` the brief asked for so that the path it named resolves; **cite findings as `OP2-xx`.**

**Covers**, on `autonomy-diagram-r0`, read against the working tree at HEAD:

| commit | claim |
|---|---|
| `00e9a1aa` | RTX-C2: `AutonomySession.stationsWithAHalfMeasuredApproach` + `AutonomyChecks.HALF_MEASURED_APPROACH` |
| `fe6c32bb` | RTX-C4: `behaviour.md` section 8 records the detection hardware; AMR-D1 corrected in place |
| `20d6ca90` | AUS-C2: the track-length and maximum clears choose a sentence by `page == null` |
| `ffba40a3` | MKR-B1: `MarklinControlStation.rebindRouteTiles` on every route-database write |
| `19c2f85a` | AUR-B1: a double curve keyed per route in `GraphReducer.locationsOf` |
| `6d2fef9a` | GUX-B1: the route editor's `signatureOf` / `howTheRouteMoved` |
| `4e464649` | AUS-B1: `autonomyTileMenus.refresh()` before `buildTileMenu` |
| `b7b1bfca` | VC2-C3 restore counter, VC2-C5 figure-of-eight arithmetic, VB2-C3 rename through `renameLoc`, VB2-C6 both names |
| `ef672f15` | SVT-C1 the load refusal, VC2-C6 two focus preconditions turned into skips |

**Read-only.** Nothing was built, run or edited. No test, `javac`, `ant` or application was started, no
writing git command was issued, and nothing under `src/`, `test/`, `cs2_sample_layout/`, `docs/manual-tests/`
or the tracker was touched. This document is the only file created. Every "red" and "green" below is argued
from the code, under the SOP's warning about simulating one's model of it rather than the code; where a claim
rests on timing or on a desktop's behaviour it says so.

**The questions asked**, from the brief: is RTX-C2's premise true and does its detector read the squares the
walks read; is what `behaviour.md` now says about the sensor true of the code and does anything else in `docs/`
contradict it; do the two length-clear sentences match their surfaces and can a tooltip and its dialog come
apart; is every route-database door covered by the re-bind, and can the re-bind itself break a tile or run on
the wrong thread; what else reads the reduction's location ids and is a `FEEDBACK_DOUBLE_CURVE` affected; does
the route editor's signature cover what Save writes and can its questions fire spuriously; is one refresh per
right-click enough, what it costs, and whether it can interleave with a walk; and for each hardening item,
does it pin what it says, can it pass for another reason, is it deterministic.

---

## A - wrong behaviour on the layout, or data silently lost

| id | status | where |
|---|---|---|
| - | - | none found |

Nothing reached A. The shapes looked for and why each was discharged: a command sent wrongly (no change here
touches a command path - MKR-B1's `setRoute(null)` is null-checked at every reader, OP2-D4); a place-id change
altering a Point's identity (it cannot - a Point tile is never a path step, OP2-D6); the diagram menu's new
save committing an editor's abandoned work (`buildAutonomyTileMenu` refuses while a layout editor is open,
`TrainControlUI.java:4683`, OP2-D8); and a rename onto a taken name losing the route (`editRoute` checks
before it deletes, `MarklinControlStation.java:1976-1980`, OP2-D5). `OP2-B1` and `OP2-B5` are the two that
came closest and the reasons they are B rather than A are stated in their entries.

---

## B - incorrect results, a fix that guards nothing, or a claim in a commit that is not true of the code

| id | status | where |
|---|---|---|
| OP2-B1 | fixed - refreshRunLeaders announces nothing | AUS-B1: the refresh at `TrainControlUI.java:4765` reaches `onChanged`, so every right-click on the track diagram now writes the setup to disk - twice - and runs the whole findings pass twice. "One cheap derivation per right-click" is not what it costs |
| OP2-B2 | fixed - the warning skips a station autonomy chooses | RTX-C2: `stationsWithAHalfMeasuredApproach` filters on `isStation` and never asks `isAutoDestination`, while `whyABerthCannotHoldIt` exempts every auto-destination - so the warning fires on ordinary platforms where the rule it quotes never runs, which is the default state of every station |
| OP2-B3 | fixed - one approach at a time | RTX-C2: "half measured" is pooled over every arriving edge; the rule decides per approach edge. A station with one measured approach and one wholly unmeasured one warns, and neither approach is half measured |
| OP2-B4 | fixed - under layoutRefreshLock | MKR-B1: `rebindRouteTiles` reads `layoutDB` through `getLayoutList()`/`getLayout()` without `layoutRefreshLock`, which `clearLayouts` and `syncLayoutsFromConfiguredSource` both take because those maps are plain `HashMap`s written from two threads |
| OP2-B5 | fixed - the conditions are read by content, with a test | GUX-B1: `signatureOf` compares a route's CONDITIONS by object identity, so a locomotive rename - which rewrites condition commands in place - is invisible to the new question, and Save puts the old name back |

### OP2-B1 - the diagram's autonomy menu now saves the setup on every right-click

**Where.** `src/org/traincontrol/gui/TrainControlUI.java:4765` (`autonomyTileMenus.refresh()`), reaching
`src/org/traincontrol/gui/AutonomyEditorPanel.java:9138` (`if (onChanged != null) onChanged.run();`, the last
statement of `refresh()`).

**The caller path.** A right-click on a tile of the running track diagram builds
`LayoutRightclickAutonomyMenu`. That class reaches `buildAutonomyTileMenu` **twice** for one click:
`LayoutRightclickAutonomyMenu.java:822` calls `ui.buildAutonomyFacingMenu(here)`, whose first statement is
`if (buildAutonomyTileMenu(tile) == null) return null;` (`TrainControlUI.java:4642`) - it builds the whole
popup only to throw it away and check the panel exists - and then `:829` calls `addSetupMenu()`, which calls
`ui.buildAutonomyTileMenu(here)` again at `:903`. Both are inside the `if (!ui.isAutonomyBusy())` branch that
opens at `:397` and closes at `:830`, so this is the ordinary right-click on an idle railway.

On this surface `onChanged` is the runnable wired at `TrainControlUI.java:4701-4721`, whose body is
`noteIfTheSetupWasNotTidied(session.save())` followed by `refreshStaticAutonomyLayer()` and
`updateVisiblePoints()`. `AutonomySession.save()` (`AutonomySession.java:7973`) is the reconciliation that
walks every tile of every page and prunes entries whose square no longer exists - the method whose own comment
at `:7992-8003` records "Adam lost 19 point names and 14 stations to this on 2026-08-23". `refresh()` itself
(`AutonomyEditorPanel.java:8964`) calls `session.flowMarks()`, `session.runLeaders()` and then
`session.check()` at `:9012`, and `check()`'s own comment (`AutonomySession.java:5259-5262`) describes the
paths it calls as "four full walks of the railway on the event thread, every time somebody right-clicks a
station". `00e9a1aa` added a fifth walk to it (`stationsWithAHalfMeasuredApproach`, O(stations x edges x path)).

So one right-click is now: two full findings passes, two full page-and-tile reconciliations, and two writes of
the setup file, all on the EDT. Before AUS-B1 the panel refreshed only after its own actions (`item()` calls
`refresh()`), so opening the menu and pressing Escape wrote nothing at all.

**Why this is a fix that half-guards.** The `final boolean[] listening = { false }` at `:4699` exists for
exactly this: its comment says "The panel refreshes itself in its own constructor and the refresh is what
reports a change - so an unguarded runnable saved the setup the first time anybody right-clicked a square,
before they had chosen anything. Writing on open is how an edit somebody abandoned elsewhere gets committed by
a menu they only looked at." AUS-B1 re-creates that write one step later, with `listening[0]` already true.
The commit message's "One cheap derivation per right-click, and it cannot go stale" is true of the
`runLeaders` derivation it was written for and untrue of what `refresh()` does.

**Not A**, because the save writes what the session already holds and the prune path is fenced by the renumber
guard at `:7992`; the loss is potential rather than demonstrated. It is B because the commit states a cost
that is not the cost, and because a right-click is now a disk write.

**The smaller fix.** AUS-B1 needs `runLeaders` and `flowMarks` current, not the findings list and not a save.
A `refreshDerivationsOnly()` that sets those two fields and nulls `unmeasuredSquares` - the first four lines of
`refresh()` - does everything the defect needed. Failing that, `buildAutonomyFacingMenu` should stop building a
popup it discards.

**How to prove it.** In `regression.testEditorSurfaceRules`, a runtime test: open the track diagram on a
sandboxed layout, record the setup file's last-modified time (or count calls by wrapping the session), call
`ui.buildAutonomyTileMenu(tile)` once, and assert the file was not written. Red today, twice over. A source
rule is not enough - the existing `testTheDiagramMenuRefreshesBeforeItBinds` reads
`TrainControlUI.java` and can only see the ORDER of two identifiers, never what `refresh()` does.

### OP2-B2 - the half-measured warning fires on stations the berth rule exempts

**Where.** `src/org/traincontrol/automationui/AutonomySession.java:8258` -
`if (!store.isStation(square)) continue;` is the whole of the filter.

**What the rule it quotes actually does.** `Layout.whyABerthCannotHoldIt` (`Layout.java:9037`) has, at
`:9048`, `if (berth == null || berth.isAutoDestination()) return null;` under the heading "STATIONS ARE
EXEMPT, which is the whole of his ruling". The rule is about PARKING BERTHS and nothing else - Adam's ruling
of 2026-09-12, quoted in its own javadoc: *"make a rule that parking berths cant block any other edges, but
not make that check for active stations."*

**And that exemption is the default.** `AutonomySession.isAutoDestination` (`:4366-4369`) is
`return !isParking(tile);`, and `setAutoDestination` writes `AutonomyBuilder.AUTO_DESTINATION` only to turn it
OFF. A square marked a station with nothing else set is an auto-destination, so the berth rule never runs on
it. `store.isStation` (`AutonomyCompanionStore.java:991`) is `stations.contains(tile)` - the station flag,
which berths and platforms both carry.

So on a railway with a part-measured approach to an ordinary platform, the operator is shown a WARNING reading
*"{0} takes no train at all while part of its approach is measured and part is not"* about a square where
nothing refuses anything. The neighbour check `runInsShorterThanTheBerth` (`:8312`) is about stations and is
right not to filter; this one is about berths and copied the wrong neighbour's filter.

**Why the test cannot see it.** `core.testMassAssignLengths.testAHalfMeasuredApproachIsWarnedAbout` uses
`openBerthBehindASwitch`, which at `test/core/testMassAssignLengths.java:1109` calls
`session.setAutoDestination(berth, false)`. The fixture is a berth, so the test passes whether or not the
detector filters on it - the "assert the precondition that makes a test meaningful" rule, missing.

**How to prove it.** In the same class, a second test: `openBerthBehindASwitch(key(5,1))`, then
`session.setAutoDestination(key(5,1), true)` - making it an ordinary platform - then
`session.setTileLength(key(3,1), 1)` as the existing test does, and assert
`session.stationsWithAHalfMeasuredApproach().isEmpty()`. Red today, because the detector still finds it; and
the assertion is exactly the claim the message makes, since `Layout.whyABerthCannotHoldIt` returns null for
that square by construction.

### OP2-B3 - "half measured" is judged across every approach at once, and the rule judges one

**Where.** `AutonomySession.java:8260-8276`. `anyMeasured` and `unmeasured` are declared once per STATION
and then accumulated inside `for (GraphReducer.ReducedEdge arriving : reducer.getEdges())`, so one measured
square anywhere on any arriving leg satisfies `anyMeasured` for all of them.

**What the rule does instead.** `whyABerthCannotHoldIt` is handed ONE path and takes
`Edge approach = path.get(path.size() - 1)` (`Layout.java:9042`). Its `anyMeasured` scan at `:9085-9091` runs
over that single edge's `getPlaceLengths()`. A different approach to the same berth is a different call with
its own answer.

**The false positive this produces.** A station reached by two legs, leg A fully measured and leg B wholly
unmeasured. The detector: `anyMeasured` true from A, `unmeasured > 0` from B, so it warns. The rule: on A it
judges and the berth holds a train that fits; on B `anyMeasured` is false, it returns null and declines to
judge - PRW-B1's own doctrine, which the detector's javadoc quotes correctly and then does not implement. No
train is refused anywhere, and the operator is told the berth takes nothing.

**And the number is wrong too.** The message says "{1} squares leading to it still have no length". The count
sums unmeasured steps over every arriving edge, so a square two legs both run over is counted twice, and a
berth on a three-way throat reports three times the squares there are to measure.

**How to prove it.** A fixture with two legs into one berth - the existing `openBerthBehindASwitch` already
has a branch at `3,0` - measure every square of one leg and none of the other, and assert
`stationsWithAHalfMeasuredApproach()` is empty. Red today. For the count: a fixture where two arriving legs
share an unmeasured square, asserting the value is the number of distinct squares.

### OP2-B4 - the re-bind is a new unguarded reader of `layoutDB`

**Where.** `src/org/traincontrol/marklin/MarklinControlStation.java:3537-3553`. `rebindRouteTiles` walks
`this.getLayoutList()` (`:3854`, `layoutDB.getItemNames()` then `Collections.sort`) and `this.getLayout(page)`
(`:3868`, `layoutDB.getByName`). Neither takes a lock.

**The exclusion it is missing.** `syncLayoutsFromConfiguredSource` takes `layoutRefreshLock` at `:544`, and
its comment says why: "layoutDB is backed by plain HashMaps". `clearLayouts` takes it at `:1656` with a
comment that is this finding written out in advance - "Guarding only the two callers of that method left this
one able to delete from layoutDB while a background diagram-save refresh was repopulating it - plain HashMaps,
modified structurally from two threads. The exclusion existed for free until FCR-B3 moved refreshes off the
EDT."

**The caller path that collides.** `rebindRouteTiles` is now called from five route-database doors:
`newRoute(MarklinRoute)` `:2149`, `newRoute(String,int,...)` `:2188`, `newRoute(String,List,...)` `:2227`,
`deleteRoute` `:3584`, `changeRouteId` `:3627`. The route editor's Save reaches `editRoute` -> `deleteRoute`
and `newRoute` on the EDT (`RouteEditorFrame.java:2903`, and `importRoutes` at `:4036-4050` from the same
thread). A diagram-save refresh calls `refreshLayouts()` -> `syncLayoutsFromConfiguredSource()` from a
background thread since FCR-B3. So a Save or an import can be walking `layoutDB` while `syncLayouts()` is
clearing and repopulating it: a `ConcurrentModificationException` out of `getItemNames()`, or a half-rebuilt
page set that silently leaves some route tiles bound to nothing.

This is the SOP's single most repeated mistake, named in its own words: "a lock put on `refreshLayouts` and
not on the method `syncWithCS2` calls". The fix is one line - wrap the body of `rebindRouteTiles` in
`synchronized (this.layoutRefreshLock)`; the lock is reentrant, so `syncWithCS2`, which already holds it when
it re-imports routes, is unaffected.

**Also worth pricing while it is open.** `importRoutes` (`:4036-4050`) deletes N routes and adds M, each of
which now sweeps every tile of every page. That is (N+M) full sweeps for one import. Correct, but it belongs
in the caller: one re-bind after the loop.

**How to prove it.** A test that holds `layoutRefreshLock` on one thread (or drives `clearLayouts()` in a
loop) while another calls `model.deleteRoute(...)`, asserting no exception over some hundreds of iterations.
Red today, intermittently - which is the honest shape for this defect, and the reason the guard is worth
having even where a red is hard to produce on demand.

### OP2-B5 - the signature does not cover a condition rewritten in place

**Where.** `src/org/traincontrol/gui/RouteEditorFrame.java:461-478`, and specifically `:476`:

```java
out.append(String.valueOf(route.getConditions()));
```

**Why that is identity, not value.** `NodeExpression` (`src/org/traincontrol/base/NodeExpression.java:15`)
declares no `toString()`, and neither does any of its four subclasses - `NodeAnd`, `NodeOr`, `NodeGroup`,
`NodeRouteCommand` all override `equals` and none overrides `toString`. So `String.valueOf` yields
`org.traincontrol.base.NodeAnd@1f2a3b`: the object's identity. (`MarklinControlStation.syncWithCS2` at
`:1467` compares the same field with `Objects.equals`, which is the value comparison that exists.)

**The case it misses.** `Route.locomotiveRenamed` (`src/org/traincontrol/base/Route.java:152-159`) walks
`namesLocomotives()` and calls `rc.setName(newName)`. `namesLocomotives` (`:222-240`) deliberately includes
the CONDITIONS - its javadoc at `:210-220` says so and names this exact hazard: "NodeExpression.toList hands
back the objects themselves rather than copies, so changing one here changes the condition... A renamed
locomotive left in a condition is the worst of the shapes this can take: the condition cannot be satisfied, so
the route stops firing, and nothing anywhere says that a rename did it."

So: open the route editor on a route whose CONDITION names locomotive `Big Boy` and whose commands do not.
Rename `Big Boy` from the locomotive window. `MarklinControlStation.renameLoc` (`:3411`) rewrites the
`RouteCommand` inside the condition tree in place - the `NodeExpression` object is the same object, so
`String.valueOf(getConditions())` is the same string, and every other term of the signature is unchanged too.
`howTheRouteMoved()` (`:490`) returns null, no question is asked, and Save writes the window's expression -
putting `Big Boy` back into a condition for a locomotive that no longer exists. The route then never fires
again, silently, which is the outcome `Route.java:216-218` was written to stop.

**Not A**, because Save has always overwritten and GUX-B1 did not make this worse. It is B because GUX-B1's
stated purpose is that Save asks before overwriting a version somebody else changed, and this is a version
somebody else changed that it cannot see.

**And the other direction.** The same identity comparison over-fires: a door that replaces the route object
with identical content (another editor saving unchanged, a bulk enable that writes the state it already had)
yields a new `NodeExpression` and therefore a "changed" question about nothing. Narrower, and in the asking
direction, so it is priced here rather than filed separately.

**The fix.** Build the conditions term from `NodeExpression.toList(route.getConditions())`, appending each
`RouteCommand`'s `toString()` - which IS value-based (`RouteCommand.java:431-481`) - exactly as the commands
loop above it already does. That makes both directions right with one change.

**How to prove it.** In `core.testLayoutTiles`, beside `testTheRouteEditorNoticesItsRouteMoving`: build a
route whose CONDITION names a locomotive, open an editor on it, call `model.renameLoc(...)`, and assert
`editor.howTheRouteMoved()` equals `"changed"`. Red today - it returns null. Note that the existing test
cannot catch this: it builds the route with `conditions` = `null` and passes the SAME `commands` list to
`editRoute`, so neither the conditions term nor the commands term is exercised as a value comparison at all.

---

## C - narrow edge cases, stale or contradicted comments, and tests that pass for the wrong reason

| id | status | where |
|---|---|---|
| OP2-C1 | open | RTX-C4: `behaviour.md` says `HomeStaging.snapshot` pulses the feedback. It reads it; the pulse is `Layout.simAnnounce` / `simClearBehind` |
| OP2-C2 | open | AUS-C2: the sentence is chosen by a ternary duplicated in four places, against the one-builder rule the locomotive clear's own comment states - and no test runs either sentence |
| OP2-C3 | open | AUR-B1: `deriveLocks`' javadoc still says "The one exception is an overpass". There are three |
| OP2-C4 | open | AUR-B1 contradicts `sharedSquaresALengthRuleReads`, written the same day, about whether a double curve's two roads share metal |
| OP2-C5 | open | VC2-C5: the figure-of-eight arithmetic's `- getTileLength(leg.getStart())` term is 0 in its fixture, so the convention the failure message names is not tested |
| OP2-C6 | open | VC2-C3: the restore-counter test turns on wall-clock timing, which is the hazard VC2-C6 removed from two other tests in the next commit |
| OP2-C7 | open | SVT-C1: `assertFalse(built != null && built.isValid())` passes for any invalidation, and `Layout.getLastError()` is never asserted |
| OP2-C8 | open | VC2-C6: nothing records that the two focus checks ever ran, so a desktop that never gives focus turns both classes green while asking nothing |
| OP2-C9 | open | GUX-B1: a RENAMED route is reported "gone", and save-as-new takes a new id that no route tile follows |
| OP2-C10 | open | MKR-B1's test injects a page into `layoutDB` and never removes it |
| OP2-C11 | open | VB2-C3: the rename test has no `finally`, so a failure leaves "HS renamed" in the shared locomotive database |
| OP2-C12 | open | RTX-C2's message says "takes no train at all", which is true only where a road shares the approach, and never for a train with no length |
| OP2-C13 | open | `rebindRouteTiles` is silent where `wireComponents` logs `layout.routeButtonMissingRoute` |

### OP2-C1 - the behaviour document names the wrong site for the pulse

**Where.** `docs/reference/behaviour.md`, section 8, the bullet added by `fe6c32bb`: *"**The simulation is the
odd one out**: `HomeStaging.snapshot` pulses its feedback"*. The same sentence is repeated in the corrected
AMR-D1 row at `docs/reviews/2026-09-15-AMR-autonomy-runtime-review.md:141` ("what pulses is the simulated
feedback in `HomeStaging.snapshot`").

`HomeStaging.snapshot` (`src/org/traincontrol/automation/HomeStaging.java:192-266`) does not pulse anything.
At `:219` it READS: `if (layout.isFeedbackOccupied(p.getS88())) sensorsSet.add(p.getS88());`, and the comment
above it at `:213-218` says why it reads rather than infers - *"in simulation the feedback is pulsed and
clears again behind the train"*. The pulse itself is `Layout.simAnnounce` (`Layout.java:1660-1670`), which
sets the sensor, and `Layout.simClearBehind` (`:1673-1695`), spawned detached from the dispatch loop at
`:7810` and `:8078` under `if (this.simulate)`.

Everything else the bullet asserts is true of the code and was checked:

- *"`isPathClear` refuses an edge whose end reports a set sensor"* - `Layout.java:2367-2373`,
  `if (control.getFeedbackState(e.getEnd().getS88()) != false)`, inside the per-edge loop, so it is every
  edge's end and not only the last.
- *"the staging planner refuses a point whose sensor sibling holds a train"* - `HomeStaging.canEnter`,
  `:1682-1701`, whose own comment at `:1690-1700` records that the mover is not exempt and that the behaviour
  is hardware-conditional.
- *"both refuse the SIBLING square"* - both read the s88 address rather than the Point, and `pointsBySensor`
  (`:207-212`) is built precisely because one address carries several Points.

**Nothing else in `docs/` contradicts it.** The only other live occurrence of the old reading is
`docs/reviews/2026-09-19-RTX-autonomy-runtime-delta-review.md:95`, which QUOTES the old AMR-D1 as the evidence
for RTX-C2 - correct as a record of what was said. (`docs/manual-tests/.triage-backups/` carries stale copies
of `tests.md`, but they are backups by name and are not part of the record.)

**How to prove it.** This is a document, so the check is a reader's: the sentence as written sends somebody
looking for a `setFeedbackState` in `HomeStaging` that is not there. Fix is two words -
`Layout.simAnnounce` / `simClearBehind` in simulation, which `HomeStaging.snapshot` then reads.

### OP2-C2 - two sentences, four copies of the choice, and no test that runs either

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:2261`, `:2277`, `:9699`, `:9739`. Each is its
own `I18n.f(page == null ? "...AtOnce" : "...", count)`.

**Both sentences are TRUE of their surface**, which was the thing to check first and it holds - see OP2-D3.
The finding is about how the pair is built. The sibling this fix cites, `clearLocomotivesWarning`
(`:9857-9877`), is a METHOD, and its javadoc at `:9846-9848` states the rule: "One builder, used by the
confirmation AND by the menu item's tooltip, so the warning cannot be shown in one place and not the other."
The comment ten lines above the track-length tooltip (`:2249-2251`) says the same thing and records that this
pair "had already drifted once". AUS-C2 chose the copy-the-ternary shape instead, in four places, for two
menu items. Nothing in the build stops the fifth copy, or a swapped pair of keys.

**And no test runs either sentence.** The commit says
`regression.testCancelUndoesAutonomyEdits.testCancelPutsTheLengthsBack` "runs it". It does not: the test calls
`session.clearEveryTileLength()` directly (`test/regression/testCancelUndoesAutonomyEdits.java:245`) and never
touches `clearAllTileLengths()` or the dialog. It is a good test of the UNDO - the mechanism the editor's
sentence promises - and it is silent about the sentence and about the other surface entirely. The locomotive
clear has `regression.testTheBulkClearSaysWhatCancelDoes`, which "asks both doors"; its two neighbours now do
not.

**How to prove it.** Make a `clearTrackLengthsWarning()` and a `clearMaximaWarning()` on the panel, the way
`clearLocomotivesWarning()` is, and extend `testTheBulkClearSaysWhatCancelDoes` to ask all three on both
surfaces. As a pure regression: assert that the editor-surface warning contains the Cancel sentence and the
diagram-surface one does not. Red if the two keys are ever swapped - which is the mutation this has no
defence against today.

### OP2-C3 - `deriveLocks` still says there is one exception

**Where.** `src/org/traincontrol/automationui/GraphReducer.java:1337`, the javadoc immediately above
`deriveLocks` at `:1370`: "The one exception is an overpass, where the two tracks are at different heights".

AUR-B1 added two more (`DOUBLE_CURVE`, `FEEDBACK_DOUBLE_CURVE`) at `:1440-1442` and wrote them up thoroughly
at `locationsOf`'s own javadoc - but the method whose BEHAVIOUR changed still describes the old rule. The SOP
is explicit that a comment must stand on its own; a reader who opens `deriveLocks` to find out why two roads
do not lock is told the answer is "an overpass" and it is not.

**How to prove it.** No test; a reader's check. One sentence at `:1337` naming all three and the reason
(different heights for an overpass, no rail between the corners for a double curve, and that a crossing is
deliberately not one).

### OP2-C4 - two comments written the same day disagree about a double curve

**Where.** `src/org/traincontrol/automationui/AutonomySession.java:3207-3209`, in
`sharedSquaresALengthRuleReads`' javadoc (the method at `:3220`, added by `6d4aefee` SET-B2 earlier the same
day):

> What makes a crossing different is its geometry: `TilePorts` gives it two separate roads (`CROSSING` runs
> north-south and east-west, a `DOUBLE_CURVE` two unconnected curves), so **a train on one road passes over
> the other's rail**.

And `GraphReducer.java:1418-1421`, AUR-B1's own:

> `TilePorts` gives a double curve `route(N, W)` and `route(E, S)`: two curves in opposite corners of the
> square, **with no rail between them**.

Both cannot be right, and AUR-B1's is the one that was measured (its commit names 20,10, 21,10 and 11,11 on
1 - Main). The arithmetic `sharedSquaresALengthRuleReads` performs is unaffected - a double curve's length
does belong on each road that crosses the square, whether or not the roads touch - so this is a C rather than
a B. What it costs is the next decision: somebody asking "should these two roads lock?" finds the older
sentence first, because it is the one attached to the rule about that square.

**How to prove it.** A reader's check. Correcting `:3208` to say the two are grouped by the arithmetic they
need (a length counted once per road) rather than by shared metal, and citing AUR-B1, settles it - and is the
"fix one site, sweep the siblings" step that AUR-B1 did not take.

### OP2-C5 - the figure-of-eight's endpoint claim cannot fail

**Where.** `test/core/testMassAssignLengths.java`, in `testALegThatCrossesItsOwnSquareTwiceIsCutAtIt`:

```java
int expected = 4 * pieces + 3 * over - session.getStore().getTileLength(leg.getStart());
```

with the message "and the reduction does not count the square it starts on".

The fixture (`:1325-1341`) is two `FEEDBACK` squares at `1,2` and `3,4` and plain track between. The test
assigns 4 to every piece and 3 to the crossing; a sensor square is in no piece (`sharedSquaresALengthRuleReads`'
own javadoc, `:3210-3212`: "a Point tile is an edge endpoint, never an intermediate square") and is not a
switch or a crossing, so nothing ever gives `1,2` or `3,4` a length. `getTileLength(leg.getStart())` is
therefore 0 and the subtraction is a no-op. By the same token `lengthOf(edge.getEnd())`, the other half of the
convention `placesAlong` documents at `GraphReducer.java:1504-1507` ("The far endpoint is included and the
near one is not"), contributes 0 as well. So of the three claims the message makes, the test exercises one -
pieces once each, crossing once per crossing - and neither endpoint convention. That is the memory's "assert
the variable, not the control": the term that cannot vary is not a check.

**How to prove it.** Add `session.setTileLength(key(1,2), 5)` and `session.setTileLength(key(3,4), 7)` before
the rebuild, and make `expected` `4 * pieces + 3 * over + 7`. Both endpoint conventions then discriminate:
counting the start would be 5 out, dropping the end 7 out.

Two smaller things in the same block, worth a line each while it is open: `pieces` is
`stretchesALengthRuleReads()` counted layout-wide while the arithmetic assumes every piece lies on THIS leg -
true of this single-page fixture and an unstated precondition; and `assignStretchLength(piece, 4)` over a
seven-square piece relies on the share-out summing back to 4 exactly, which is the property under test's
neighbour and is not asserted.

### OP2-C6 - the restore counter is pinned by a wall clock

**Where.** `test/core/testLayoutTiles.java`, `testASecondHighlightStopsTheFirstsRestore`.

**It does pin what it says.** The counter increments inside the restore branch at
`src/org/traincontrol/gui/LayoutLabel.java:1061`, which is reached only when the timer fires AND the click
window has passed - so it counts restores that actually repainted, which is the thing that distinguishes a
stopped timer from a superseded one left armed. With the VC2-C3 fix reverted (remove `endAccessoryHighlight()`
at `:1037`) the first timer fires part-way through the second highlight and the count is 2. Good.

**It is not deterministic.** `HIGHLIGHT_DURATION` is 2250 ms. The test samples `before`, then drives a second
highlight, then `Thread.sleep(3200)`. If more than 2250 ms elapse between the sample and the second drive -
`awaitHighlight` plus two `invokeAndWait` hops on a machine running the whole battery - the FIRST restore
fires inside that window, is counted, and the assertion sees 2 with the fix in place. That is the shape
`ef672f15` removed from `testTheKeyMapReachesTheWholeWindow` and `testTheWindowTakesTheKeyboard` one commit
later: an intermittent failure that says nothing about the code under test.

**How to prove it.** Record `System.currentTimeMillis()` at the sample and again after the second
`awaitHighlight`, and skip (or retry) if the gap is not comfortably under `HIGHLIGHT_DURATION` -
`LayoutLabel` can expose the constant. That keeps the pin and takes the flake out, and it is the same remedy
`ef672f15` chose.

### OP2-C7 - the negative maximum test passes for any invalidation

**Where.** `test/core/testHomeStaging.java`, `testANegativeMaximumStopsTheConfigurationLoading`:

```java
assertFalse(built != null && built.isValid(), ...)
```

**The claim is true of the code**: `Layout.java:10748-10769` accepts `maxTrainLength` only when it is an
`Integer` and `>= 0`, and otherwise calls `layout.invalidate(I18n.f("autolayout.errorMaxTrainLengthInvalid",
...))`. So the finding is about the test, not the fix.

Two ways it can pass without showing that: `built == null`, which is any failure to produce a layout at all;
and any OTHER invalidation of the same configuration. The control below it - the plain fixture loads - rules
out "the fixture never loads", which is the most likely of them, and that is worth having. It does not rule
out "the -3 broke something else".

There is also a latent trap in the fixture: `station("HS B", 1, null).replace("}", ", 'maxTrainLength': -3}")`
replaces EVERY `}`. With `loc == null` that string has exactly one (`:297-301`), so it is well formed today -
but `station(name, offset, loc)` emits a nested `{'name': ...}` when `loc` is non-null, and this file's own
`ringAssigning` (`:315-319`) uses the safe idiom, `raw.substring(0, raw.length() - 1) + ", ... }"`, for
exactly this reason.

**How to prove it.** Assert the reason, not only the refusal:
`assertTrue(Layout.getLastError().contains("HS B"))`, or match the translated
`autolayout.errorMaxTrainLengthInvalid`. Red if the configuration is rejected for anything else, which is the
whole point of the test. And switch the injection to the `substring` idiom the file already uses.

### OP2-C8 - the two skips have no floor

**Where.** `test/regression/testTheKeyMapReachesTheWholeWindow.java:336-343` and
`test/regression/testTheWindowTakesTheKeyboard.java:451-458`.

Turning the assertion into a `SkipException` is right, and both messages say exactly what could not be set up
- this is the better half of `ef672f15`. What neither does is record that the check was ever made. If the
desktop never gives the window the keyboard, every such test skips and both classes report green while asking
nothing about the key map or about OB-170 - "green is not no failures", and the SOP's own rule that a test
which can quietly degenerate into testing nothing needs a floor on how much it exercised.

**How to prove it.** A class-level counter incremented past each precondition and an `@AfterClass` that fails
when it is zero ("this desktop could not focus a window at all, so nothing in this class was asked"), or a
single `@Test` that establishes focus once and fails loudly if it cannot - so the skip means "this one case"
rather than "possibly all of them".

### OP2-C9 - "gone" also means renamed, and save-as-new takes a new id

**Where.** `src/org/traincontrol/gui/RouteEditorFrame.java:490-503`. `howTheRouteMoved` looks the route up by
`parent.getModel().getRoute(originalName)` - by NAME. If somebody renamed the route under the window it is not
deleted; the lookup still fails and the answer is `"gone"`. The message then says
*"{0} is no longer in the route list - it has been deleted, or replaced by an import"*
(`route.ui.confirmRouteGoneSaveAsNew`), naming the two causes that did not happen, and the remedy it offers -
save as new - creates a second route alongside the renamed one.

Second half: the save-as-new branch (`:2889`) calls `newRoute(name, built, ...)`, which allocates a fresh id
from `ROUTE_STARTING_ID` upwards (`MarklinControlStation.java:2212-2217`). The route's old id is gone, so a
diagram route tile bound to it - tiles bind by address, which is the id, as `rebindRouteTiles`' own javadoc
says - stays dead, and `restoreRouteActivation` is not called, so an autonomy selection on the old id is not
carried over either. Both are defensible for something the dialog calls "a new route"; neither is said.

**How to prove it.** In `core.testLayoutTiles`, beside the existing test: rename the route through
`model.editRoute(name, other, ...)` and assert `editor.howTheRouteMoved()` - red today at whatever the third
answer should be ("renamed"), which is the point. For the id: assert `model.getRoute(name).getId()` after a
save-as-new, and decide whether keeping the old id when it is free is worth doing.

### OP2-C10 - the route-tile test leaks a page into the model

**Where.** `test/core/testAdvancedRoutes.java`, `testARouteTileFollowsTheDatabase`. The test reflects into
`layoutDB` and calls `layouts.add(page, page.getName(), page.getName())` - correctly, because the re-bind
walks the model's own page list. Its `finally` deletes the ROUTE and leaves the page. Every later test in that
JVM sees a page called "tiles" in `getLayoutList()`, every later `rebindRouteTiles` walks it, and anything
counting pages or reconciling against them counts one that no diagram has.

**How to prove it.** Add `layouts.delete(page.getName())` (or the collection's equivalent) to the `finally`,
and - the check that makes it stick - assert `model.getLayoutList()` has the same size before and after.

### OP2-C11 - the rename test can leave the locomotive renamed

**Where.** `test/core/testHomeStaging.java`, in the record test that `b7b1bfca` moved onto `renameLoc`. The
two assertions between `renameLoc(LOC_A, "HS renamed")` and `renameLoc("HS renamed", LOC_A)` are not inside a
`try`/`finally`. Going through the operator's door is right - VB2-C3's point stands - but that door writes the
real locomotive database, and a failure of either assertion leaves `HS renamed` there for every later test in
the class. The second call's own message ("the name was not put back for the rest of the class") shows the
author knew the hazard and then guarded only the case where the rename itself is refused.

**How to prove it.** Wrap the two assertions in `try` with the restoring `renameLoc` in `finally`. The test's
own behaviour does not change; what changes is what a red one costs.

### OP2-C12 - "takes no train at all" is broader than the code

**Where.** `src/org/traincontrol/resources/messages.properties`,
`autosetup.ui.checkHalfMeasuredApproach`.

The premise was checked and is right in shape: with the approach half measured, the walk at
`Layout.java:9096-9118` claims each place before spending on it and an unmeasured place spends nothing, so a
one-unit train claims the same places a nine-unit one does. Two qualifications the sentence does not carry:

- The claim only becomes a refusal where a LOCK EDGE shares one of those places (`:9120-9134`). A berth on a
  branch nothing else touches is half measured and takes every train. On that railway the warning is a
  statement about nothing.
- `whyABerthCannotHoldIt` returns null at `:9040` when the locomotive has no train length. A train with no
  length is admitted to a half-measured berth, so "no train at all" is not literally true even where a road
  does share.

The first of these is worth putting in the detector rather than the sentence - asking whether the approach has
a lock edge sharing a claimed place would make the warning say something that is true of the railway in front
of the operator.

### OP2-C13 - the re-bind says nothing where the wiring logs

**Where.** `MarklinControlStation.java:3549` sets `c.setRoute(this.routeDB.getById(c.getAddress()))` with no
report. `wireComponents`, doing the same lookup at `:758-765`, logs `layout.routeButtonMissingRoute` with the
tile's coordinates when the route is not there. After `changeRouteId` the old tile's address matches nothing
and the tile goes quietly dead - which is exactly the case that log line exists for, and it is the case
MKR-B1's own comment names ("a route whose id changes leaves its old tile pointing at it and its new tile
pointing at nothing"). A delete is a deliberate act and needs no log; an id change is not.

**How to prove it.** Log the same key from the re-bind when a tile that previously HELD a route now resolves
to null. A test can assert the model logged it after `changeRouteId`, or simply that the tile's route is null
and the old address is reported - the second is the cheaper pin.

---

## D - checked and clean, or claimed and true

| id | what |
|---|---|
| OP2-D1 | The half-measured detector does NOT fire on a page with no lengths at all, nor on a station whose only measured square is its own, and its skip lines up exactly with the rule's |
| OP2-D2 | RTX-C4's two refusals are true of the code, and nothing else in `docs/` states the opposite |
| OP2-D3 | Both AUS-C2 sentences are true of their surface: the editor's `onChanged` does not save, the diagram's does |
| OP2-D4 | Every route-database write door is covered by the re-bind, and a null route is safe at every reader |
| OP2-D5 | `editRoute` refuses a rename onto a taken name BEFORE deleting, so "the name was taken in the meantime" cannot lose the route |
| OP2-D6 | AUR-B1 cannot change a `FEEDBACK_DOUBLE_CURVE` Point's identity: a Point tile is never a path step |
| OP2-D7 | Place ids are persisted, and the persisted key is stable and only ever compared within one build |
| OP2-D8 | AUS-B1's refresh cannot interleave with a walk of that panel, and cannot commit a layout editor's abandoned work |
| OP2-D9 | VB2-C6 is right: the Central Station proposal really does delete a locomotive of the target name first |
| OP2-D10 | The MKR-B1 test would go red without the fix |
| OP2-D11 | Every new message line in all nine property files is ASCII |

**OP2-D1.** The brief's two "does it fire where nothing is wrong" cases both come back clean.
*A page with no lengths at all*: `anyMeasured` (`AutonomySession.java:8271`) is only ever set from
`store.getTileLength(step.getTile()) > 0`, so it stays false and `:8276` never records the station. (Note it
reaches that answer without `runInsShorterThanTheBerth`'s explicit `if (!store.measuresAnyTrack()) return
out;` at `:8317` - the same outcome by a different route, and worth knowing if the loop is ever rewritten.)
*A station whose only measured square is its own*: the detector skips `step.getTile().equals(square)` at
`:8268`, and the rule's `anyMeasured` scan skips the LAST span at `:9085` (`n < spans.size() - 1`). Those two
skips are the same square, and the alignment is exact rather than lucky: `ReducedEdge.getPath()` contains
neither endpoint - the walk starts with an empty list (`GraphReducer.java:1000`, "Empty, deliberately") and
terminates when it reaches a Point without adding it (`:1044`) - while `placesAlong` (`:1510-1524`) appends
the end tile as the last place. So `ids[0 .. size-2]` is precisely the detector's step set. SVV-B1's fix and
this detector read the same squares.

**OP2-D2.** Checked directly, and both bite as the document now says. `isPathClear`:
`Layout.java:2367-2373`, inside the per-edge loop. The staging planner: `HomeStaging.canEnter`, `:1682-1701`.
Both read the s88 address rather than the Point, so both catch the sibling square - `pointsBySensor`
(`:207-212`) exists for that. The one thing the document gets wrong is where the pulse lives (OP2-C1). A
`grep` of `docs/` for the old reading finds only the RTX-C4 finding itself, which quotes it as evidence.

**OP2-D3.** This was the load-bearing claim and it holds. The two constructions of the panel are
`LayoutEditor.java:1675` (page = `layout.getName()`, non-null) and `TrainControlUI.java:4701` (page = null).
The diagram's runnable is `noteIfTheSetupWasNotTidied(session.save())` (`:4711`) and `refresh()` runs it at
`AutonomyEditorPanel.java:9138` - and `item()` calls `refresh()` after every menu action - so a clear made
there really is on disk before the menu closes. The editor's runnable (`LayoutEditor.java:1676-1686`) is
`refreshAutonomyAnnotations()` and `parent.refreshStaticAutonomyLayer()`: no save, so Cancel's snapshot
restore is what the lengths come back from. Both panels write through the same door
(`session.clearEveryTileLength()` at `AutonomyEditorPanel.java:9708`), which is the door
`testCancelPutsTheLengthsBack` exercises - so the undo half is genuinely pinned even though the sentence half
is not (OP2-C2).

**OP2-D4.** Every write to `routeDB` in `src/` is one of six statements - `:2145`, `:2184`, `:2223`, `:3580`,
`:3620`, `:3623` - and each is followed by a `rebindRouteTiles()` (`:2149`, `:2188`, `:2227`, `:3584`, and
`:3627` covering the delete-then-add pair in `changeRouteId`). The sync and the import reach the database only
through these (`:476`, `:1484`, `:4046`), so both are covered. A tile whose address matches nothing gets
`setRoute(null)`, and every reader guards: `execSwitching` `LayoutDiagramComponent.java:179-183`,
`getImageName` `:363-368`, the tooltip `:743`. `changeRouteId` is the one that makes it happen, and it is
`:3627` that repairs the new tile in the same breath.

**OP2-D5.** `MarklinControlStation.editRoute:1976-1980` refuses a rename onto an existing name before
`deleteRoute`, with a comment recording the loss it was written after. So GUX-B1's "changed" branch, which
calls `editRoute`, cannot lose the route if the name was taken in the meantime - the window stays open with
the typing in it. The "gone" branch calls `newRoute`, which refuses a taken name outright (`:2221`) before
adding anything.

**OP2-D6.** The brief's sharpest question, and the answer is that the change cannot reach a Point.
`locationsOf` is called only from `deriveLocks` (`GraphReducer.java:1380`) and `placesAlong` (`:1518-1521`), both
over `edge.getPath()` - and a Point tile is never in a path, because `continueWalk` stops at
`points.containsKey(tile)` (`:1044`) without adding it. `placesAlong` gives the end Point its place id from
`edge.getEnd().toString()` (`:1524`), unrouted, and that is the only way a Point's id is made. So a
`FEEDBACK_DOUBLE_CURVE` that carries a sensor keeps exactly the identity it had; one that does not is always
an intermediate and now always carries the routed key. There is no half-and-half case and therefore no
asymmetry between an edge ending at such a tile and an edge crossing it - which is what would have been the
dangerous outcome, since it would have let a route pass under a standing train.

**OP2-D7.** Place ids ARE written to the configuration: `Edge.toJSON` emits them as `places[].at`
(`Edge.java:713-727`), and `Layout.fromJSON` reads them back at `:11267-11290`. Two things make that safe.
`RouteId` is `state + "#" + index` (`TileGraph.java:231-234`), derived from `TilePorts` for the tile's type
and orientation, so the same railway produces the same key on every build. And every comparison of place ids -
the lock relation, `whyABerthCannotHoldIt`'s `claimed` set, `placesCoveredByStandingTrains`,
`HomeStaging.placesCoveredAtStart` - is between edges of ONE configuration, so an old file and a new build
never meet. A configuration written before today and reloaded today keeps the answers it had until it is
rebuilt, which is the same guarantee `Edge.java:364-367` already promises for a pre-3.0.0 file. No test
fixture and no layout under `test/layouts/` carries a hand-written `places` entry for a double curve.

**OP2-D8.** Two hazards checked, both closed. *A walk mid-flight*: `massAssignLengths`
(`AutonomyEditorPanel.java:9341`) and its two siblings are synchronous loops of modal `JOptionPane`s on the
EDT, and a modal dialog blocks mouse events to the diagram behind it, so a right-click cannot reach
`buildAutonomyTileMenu` while one is up. `refresh()` also does not clear the gesture fields (`oneWayFrom`,
`signalFor`, `tailFor`, `tailPicks`), so a waiting-for-a-click gesture survives one. *An abandoned edit
committed*: `buildAutonomyTileMenu` returns null while a layout editor is open (`TrainControlUI.java:4683`,
OB-076), so the save at `:4711` cannot reach a setup the editor's Cancel is about to revert. That guard is
what keeps OP2-B1 at B.

**OP2-D9.** `TrainControlUI.java:26700-26706`: when a locomotive of the proposed name exists, the door calls
`deleteLoc(newName)` before `renameLoc`. So asking `refuseWhileARouteDrivesIt(this, newName)` is asking about
a locomotive this door may delete, which is what CS3-B1 is for. It also refuses when a running route names
`newName` and no such locomotive exists yet - which is right rather than over-strict, since after the rename
that route would be driving it.

**OP2-D10.** `testARouteTileFollowsTheDatabase` goes red without `ffba40a3`: `editRoute` is a delete and a
re-add, so `model.getRoute(id)` is a different object and `assertSame` fails on the first of the two
assertions; the second (`assertNull` after a delete) fails on its own too. The fixture is the real case - the
page is put into `layoutDB` so the re-bind walks it (OP2-C10 is about cleaning it up afterwards, not about
whether the test is sound).

**OP2-D11.** `git show` of the three message-bearing commits, filtered to added lines, has no byte outside
`0x20-0x7E` in any of the nine `messages*.properties` files - so the Java 8 ISO-8859-1 reader cannot mojibake
them, and every locale received every new key (one line each for `00e9a1aa` and `6d2fef9a`, six for
`20d6ca90`).

---

## What this pass did not cover

- **Nothing was run.** No test, no build, no application, per the brief. Every red and green above is argued
  from the source. Where an argument turns on timing (OP2-C6), on a desktop's focus policy (OP2-C8) or on a
  thread interleaving (OP2-B4), it is a reading of the code and not a measurement, and it says so.
- **The railway itself.** OP2-B2 and OP2-B3 predict that the new warning fires on Adam's own layout on
  squares where nothing refuses a train. That is a claim about `1 - Main`'s station flags and measured
  squares, and the SOP's "distinguish this could happen from this does happen" applies: the frozen snapshot
  under `test/layouts/live-snapshot/` would settle how many, and was not opened.
- **The other locales.** The nine property files were checked for the keys' presence and for ASCII. Whether
  the Danish, German, Spanish, French, Italian, Dutch and Polish texts SAY what the English says was not
  checked.
- **The rest of `refresh()`.** OP2-B1 names `session.check()` and `session.save()` as the cost. The banner,
  the findings list and the three `JList` models it also rebuilds were not priced, and neither was what
  rebuilding a list model on a panel that is never added to a container costs.
- **`HomeStaging` beyond the two refusals.** RTX-C4's sentence was checked against `isPathClear` and
  `canEnter`. Whether the latching sensor changes the reading of MT-440 and MT-450, which the RTX finding
  raised, is a question for the layout and is not answered here.
- **Everything before 2026-09-19.** Only the nine commits in the Covers table were read. The earlier fixes of
  the same day (`dad874e6`, `0a12d732`, `6b4321a8`, `6d4aefee`, `0c3911d8`) were opened only where a finding
  above reaches into them - OP2-C4 is the one place that happened.
- **Whether `OPV` should have been reused.** This document takes `OP2` and says why at the top. If Adam
  would rather the archived `OPV` document be renumbered instead, that is his call and this one should be
  renamed with it.
