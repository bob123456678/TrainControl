# VAL9 - validation of the fixes made against IND9 and REG8

**Status:** open

**Prefix:** `VAL9`. **Reviewed:** working tree at `1bd81f53` (branch `autonomy-diagram-r0`), 2026-09-07.
**Scope:** the five commits `95c09ac4`, `dd3fb5d4`, `8b4531ec`, `51a6a971`, `1bd81f53`, checked against
the defects as filed in `IND9-independent-2026-09-07.md` and `REG8-regressions-2026-09-07.md`, and
against `docs/reference/behaviour.md`.

**Method.** Reading and static reasoning only. No build, no test run, nothing under
`cs2_sample_layout/` opened or written, and no file edited except this one. Every claim below is a read
of the enforcing line at HEAD; where a claim needs an execution I could not do, the Confidence row says
so and names the probe.

## Verdict on each of the six fixes

| Fix | Verdict |
|---|---|
| IND9-A1 / REG8-A1 - `arrivedFrom` | **Correct.** Both halves. The ordering is right for every path into `parseAuto` and the clearing sweep is complete. One of its three sites has no test (VAL9-C1). |
| IND9-B4 - a turn at the destination | **Wrong for autonomy, correct for the manual case it was filed about.** The record is taken on the shared arrival path, so it fires for every autonomy and Return Home arrival at a reversing point, and a `Set` keyed by name collapses several reversals in one session into one flip. See VAL9-A1. |
| REG8-B2 - `GraphLocAssign.commitAndRecord` | **Correct as an extraction** - line-for-line equivalent, nothing lost (VAL9-D4). **Incomplete as a fix**: the diagram door writes the setup in memory and nothing persists it, so the filed symptom survives a restart. See VAL9-B2. |
| IND9-B5 - `ArrivalSidePrompt.wouldAsk` | **Correct** for the defect as filed. `wouldAsk` is exactly the condition under which `forPlacement` reaches `ask` (VAL9-D3). The third null - "could not ask" - still abandons the paste, against the commit message's own criterion (VAL9-C4). |
| IND9-C2 - `Util.parseReleaseVersion` | **Incomplete.** Two plausible names still give the wrong answer, one in each direction. See VAL9-C5. |
| C23 / C26 catches | **Correct.** Neither swallows anything reachable that is not logged (VAL9-D8). |

**Leading with the one that is worse than what it replaced:** `8b4531ec` widened a fix filed about a
manual send into every arrival the railway makes, and put a second writer of the facing on a path whose
sibling deliberately excludes itself from exactly that. It is VAL9-A1.

---

## A - wrong behaviour on the layout

### VAL9-A1 - the destination-turn record fires for autonomy too, and a Set loses the parity

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High that the record is taken on every autonomy arrival at a reversing point and that several reversals collapse to one (both are reads of the enforcing lines). Medium that `flipFacing` then takes rather than declining - that is the one link I cannot settle without running. Grade drops to D if it always declines on this railway. |

`8b4531ec` put the record inside the arrival block of `executePathInternal`
(`src/org/traincontrol/automation/Layout.java:6566-6573`), under
`if (arrived.isTerminus() || shouldReverseAt(arrived, arrived, loc, reversals))`. That block is not the
manual door. It is the single arrival path, and `shouldReverseAt` answers `current.isReversing()` for
**both** of the callers that are not a manual send (`Layout.java:5310`: `reversals == null ||
reversals == ALWAYS_REVERSE` - autonomy's own loop and Return Home). So every autonomy arrival at a
terminus or reversing point, and every staged arrival, now adds its locomotive to `reversedOnArrival`.

Three consequences, in order of how sure I am of them:

1. **The parity is lost.** `reversedOnArrival` is a `Set<String>`
   (`Layout.java:667-668`), and `reconcileFacingWhenIdle` returns before draining it while
   `built.isRunning()` (`TrainControlUI.java:6141`). `isRunning()` is
   `running || activeLocomotives || locomotiveThreads` (`Layout.java:1686-1690`) and `running` is true
   from `runLocomotives` until `stopLocomotives` - so the drain cannot happen at all during an autonomy
   session. A train that shuttles between two termini turns **twice** in one session and the set holds
   its name **once**. When autonomy stops, one flip is applied for two physical reversals, and the
   recorded facing is left exactly one flip wrong. Every even number of turns in one session is wrong;
   every odd number happens to come out right. A two-terminus shuttle is the ordinary case.
2. **It is the second writer the sibling refuses to be.** `followDirectionChanges`'s own javadoc
   (`TrainControlUI.java:10176-10179`): *"Not while autonomy is running. A run turns its own trains at
   reversing points and knows what it did; the setup is reconciled from the run's own capture.
   Following the echo as well would have two writers for one fact, and the loser would be whichever
   finished second."* The new path is that second writer, moved from the echo to a queue and applied
   after the run instead of during it. The reconcile from the run's own capture
   (`captureFromLayout`, which derives the facing from the copy the train is standing on) has not gone
   away; it now competes with a flip that assumes it did not happen.
3. **A race with the surviving echo path can double-flip.** Between `isRunning()` going false and the
   next `updateVisiblePoints`, `followDirectionChanges` reaches line 10222 with the pre-run baseline,
   sees a difference, and posts a `flipFacing` (this is IND9-B6 / REG8-B1 window 2, unchanged). The
   reconcile then drains the same name and flips again. Two flips for one reversal, and a spurious
   `autosetup.infoFacingFollowedDirection` in the log. Before `8b4531ec` that window produced one flip,
   which for a destination turn was the right answer by accident. The window is small; it is the same
   window both reviewers already filed, now with a worse outcome.

**What makes the grade uncertain, honestly.** `flipFacing` declines when the arrival square has no
recorded facing or does not offer exactly two facing choices (`AutonomySession.java:1573` - `recorded == null || choices.size() != 2`), and
after a run the arrival square is precisely where the setup is most likely to have no recorded facing
yet. If it always declines on this railway, none of the above bites - and neither does the fix.
`two-copies-evaluation.md` says every square here builds to one copy because the may-reverse squares
are dead ends; `facingChoices` on a turn-around square adds the arrival sides back
(`AutonomySession.java:4807`, the `isTurnAround` clause), which is what could make it two. The probe
that settles it in one run: after an autonomy session that turned one train twice, assert
`session.getFacing(square)` against the facing before the session.

**Fix shape.** Two independent changes, and the first alone would do:

- Record only what the manual door meant: take the record inside a branch that knows the policy came
  from a person (`reversals != null && reversals != ALWAYS_REVERSE`), or record it where
  `ManualReversalPrompt` answered rather than where the train arrived.
- If it is meant to cover autonomy as well, then it cannot be a set of names: it has to be a count, or
  a flip has to be applied at each arrival rather than accumulated. And the drain and
  `followDirectionChanges` need to agree on who consumes a name, so the two cannot both act on it.

---

## B - real, but narrow, latent, or incomplete

### VAL9-B1 - the whole window half of IND9-B4 is unpinned; three mutations survive

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - the two new tests are in front of me and neither reads the graph |

`testATurnAtTheDestinationIsRecordedThere` (`test/core/testAutonomySimulationSanity.java:152-247`)
asserts only the `Layout` side: the name goes into the set and the set drains. It never asks whether
the facing changed. `testTheBaselineIsLevelledWhenTheRailwayGoesIdle`
(`test/ui/testADirectionChangeIsNotSwallowed.java:113-137`) is a source-shape check on file offsets.
Between them, these mutations are green:

- delete `session.flipFacing(turned, built)` from inside the loop in `reconcileFacingWhenIdle`
  (`TrainControlUI.java:6157`), keeping the loop. IND9-B4 is fully back;
- change it to `session.flipFacing(turned, null)`. The setup moves and the running layout does not,
  which is the DIR-B3 defect `flipFacing`'s javadoc spends fifteen lines on;
- the file-offset assertion `writes < levels` reads the **raw** source, not `codeOnly` - so any comment
  anywhere above containing the literal `takeReversalsOnArrival()` makes it vacuous, and it compares
  positions in the whole file rather than within `reconcileFacingWhenIdle`.

The javadoc of the simulation test claims *"MUTATION: removing the `reversedOnArrival.add` at the
arrival fails the first assertion"* - true - but the commit's headline claim is that the turn reaches
the graph, and nothing asserts that. This is the same shape the same commit set fixed in `dd3fb5d4`:
asserting the layer above the defect and the layer below it.

**Cheap strengthening:** the simulation test already has a real arrival. Give it a session (or a stub)
and assert `getFacing(square)` flipped once and only once across two refreshes.

### VAL9-B2 - the diagram assignment reaches the setup in memory and nothing writes it out

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High on the omission (a read of the two adjacent methods); medium that no other path saves before the process exits |

REG8-B2's stated symptom is *"Restart the application, or load a configuration: the OLD locomotive is
back."* `commitAndRecord` calls `session.placeLocomotive` and `session.setFacing`, both of which reach
`writePointProperty` (`AutonomySession.java:5486`) - an in-memory write into the store's JSON tree - and
`touched()`, which sets `dirty` and rebuilds. Nothing in that path calls `store.save()`.

The diagram door then does `ui.updateVisiblePoints(); ui.repaintAutoLocList(false);`
(`LayoutRightclickAutonomyMenu.java:685-686`) and stops. Compare the two doors it should be level with:

- `placeFacing`, forty lines below in the same file, ends with `AutonomyReport.show(ui, session.save())`
  (`LayoutRightclickAutonomyMenu.java:997`) and the comment explaining why;
- `TrainControlUI.rememberPlacement` ends with `noteIfTheSetupWasNotTidied(session.save())`
  (`TrainControlUI.java:6329`).

The editor's twin does not save either - but the editor has a Save gesture and an unsaved-changes
prompt (`LayoutEditor.java:5625`, `:5748`) reading `autonomyPanel.isDirty()`. The track diagram has
neither, and `isDirty` has no reader outside the editor. So an assignment made from the diagram is
`dirty` in a session nobody will ask, and is written out only if the operator happens to open an editor
(`captureRunningLayout` at `TrainControlUI.java:4780`) or rename a page (`:23457`) before quitting -
which is the same "only if you open the editor first" caveat REG8-B2 filed against the old code.

The fix is a real improvement in memory (`forgetPlacementsElsewhere` now runs, which is the
two-placement invalidation `placeFacing`'s comment warns about), but it achieved parity with the wrong
twin. **Fix shape:** end `commitAndRecord` - or the diagram caller - the way `placeFacing` ends.

---

## C - minor

### VAL9-C1 - the occupant-change clear is the one `arrivedFrom` site with no test, and REG8-B2 now rests on it

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - grepped the whole `test/` tree for `arrivedFrom` |

`95c09ac4`'s message names three sites: the single door, the bulk clear, and a change of occupant.
`testAnArrivalSideDoesNotOutliveItsTrain` covers the first two. Nothing anywhere places a **different**
train over an existing one and asserts the side cleared, and nothing places the **same** train back and
asserts the side kept - which is the entire reason `nameOfPlacedLocomotive` exists
(`AutonomySession.java:4688-4700`). Deleting the whole
`if (!name.equals(nameOfPlacedLocomotive(existing)))` block leaves the battery green and IND9-A1's third
site restored.

That matters more than an ordinary coverage gap because `GraphLocAssign.commitAndRecord`'s javadoc now
delegates to it explicitly: *"The arrival side is not written and does not need to be: `placeLocomotive`
clears it when the occupant changes."* Both assignment doors are correct only if that branch is.

Two small ways the branch can answer wrongly, both from a hand-edited or older `autonomy.json`:

- `nameOfPlacedLocomotive` returns null for a **bare-string** placement, which `getLocomotiveNameAt`
  explicitly supports (`AutonomySession.java:4566-4571`, "an older autonomy.json may hold one"). Putting
  the same train back onto such a square reads as a change of occupant and throws away a true side;
- `getLocomotiveNameAt` trims and `nameOfPlacedLocomotive` does not, so a stored name with whitespace
  compares unequal to itself.

Neither is worth code on its own; a test over the branch would catch both.

### VAL9-C2 - `reconcileFacingWhenIdle` drains without a session, and edits the stored setup in silence

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

`TrainControlUI.java:6154-6158`: the drain is unconditional and the guard is inside the loop -

```java
for (String turned : built.takeReversalsOnArrival())
{
    if (session != null) session.flipFacing(turned, built);
}
```

With no session, every name is taken and discarded. `if (session == null) return;` before the loop
costs nothing and keeps the record for the refresh after the session exists. (The set also dies with
its `Layout` on any rebuild, which is defensible but undocumented.)

Separately, the twin call in `followDirectionChanges` follows its `flipFacing` with
`this.model.logf("autosetup.infoFacingFollowedDirection", ...)` and `autonomySetupChanged()`, under the
comment *"Said out loud: the setup has been changed by something the operator did to a train, and a
silent edit to a stored configuration is the thing this project keeps filing against itself"*
(`TrainControlUI.java:10254-10258`). The new path does neither. It is a silent edit to a stored
configuration made from a repaint.

### VAL9-C3 - `wouldAsk` is pinned only by a `contains`, and the obvious wrong implementation is green

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

The only reference to `wouldAsk` anywhere in `test/` is
`testEditorSurfaceRules.java:1643`, `between.contains("ArrivalSidePrompt.wouldAsk(")`. Nothing calls it.
`public static boolean wouldAsk(...) { return mayReverse; }` restores IND9-B5 in full with the battery
green - and that is the exact predicate the old code had inline, so it is the mutation a future edit is
most likely to make. `wouldAsk` is a static method over a `Layout` and a `Point`; three assertions
(empty sides, one side, two sides) need no dialog and no window.

### VAL9-C4 - the third null still abandons the paste, against the commit's own criterion

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

`1bd81f53`'s message: *"forPlacement answers null when the operator declined, when the square offers no
side to choose between, and when the dialog could not be shown; **only the first is a refusal to
place**."* `wouldAsk` separates the second and not the third: when `ask`'s `invokeAndWait` throws,
`forPlacement` returns null and `wouldAsk` still answers true, so the paste is abandoned. That is
unchanged from before the fix, so it is not a regression - but the criterion the commit states is not
the criterion the code implements, and the ordinary way to hit it is a headless run, where a paste onto
a may-turn square with two sides is now silently refused. Either narrow the claim in the comment or
have `ask` distinguish "dismissed" from "could not be shown".

### VAL9-C5 - two plausible release names still defeat `parseReleaseVersion`, one in each direction

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High on behaviour; low on likelihood, same as IND9-C2 |

`Util.java:665-668` prefers `[vV]([0-9]+(?:[.][0-9]+)*)` - the **first** such match, with no left
boundary and no requirement that the number be version-shaped. Two names of the kind the fix's own
comment warns about:

- `"Marklin CS3 v2.6 compatibility - TrainControl v3.0.0"` -> `"2.6"`. Compares older, so a real
  release is hidden in silence. That is the C5 failure mode the fix exists to close, reached by naming
  somebody else's version first - which is exactly what a compatibility release is called.
- `"Nov2026 build - TrainControl v3.0.0"` -> `"2026"`, because the `v` of "Nov" is a `v`. Compares newer
  than anything, so the menu offers an update that does not exist.

The fallback has the same shape at one remove: `"TrainControl 2026.09.07 build 3.0.0"` gives
`"2026.09.07"`, because longest wins and a datestamp is longer than a version.

**Fix shape** - a left boundary, and longest among the anchored matches rather than first:
`(?<![A-Za-z0-9])[vV]([0-9]+(?:[.][0-9]+)*)`, take the longest group. That answers all three, keeps the
three rows just added, and keeps `"v3.0"` working.

### VAL9-C6 - both strengthened source guards still have a concrete surviving mutation

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - the mutations are written out below |

Both are genuinely stronger than what IND9-C5 complained about. Neither is proof.

- `testAFailedSyncGivesTheMenusBack` (`testEditorSurfaceRules.java:1520-1530`) now asserts
  `syncResult = -1` between the call and the re-enable. Surviving mutation:
  `catch (RuntimeException e) { this.model.log(e); syncResult = -1; return; }` - green, and C23 fully
  back, menus greyed for the session. The `substring(0, call).contains("try")` half is unchanged and
  still satisfied by any unrelated earlier `try`.
- `testTheDragPutsTheNameOnTheClipboardSafely` (`:1466-1478`) now requires a
  `catch (IllegalStateException` **after** the write, which kills the mutation IND9-C5 named. Surviving
  mutation: move the clipboard write above the try entirely and leave any
  `try { ... } catch (IllegalStateException e) { }` later in the method - green, C26's deletion hazard
  back.

IND9-C5's own suggestion - assert the ordered quadruple and that no `}` closing the try body
intervenes - is still the one that binds.

### VAL9-C7 - the `commitChanges` guard knows only the two files it lists

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

`testEditorSurfaceRules.java:290-306` loops over `AutonomyEditorPanel.java` and
`LayoutRightclickAutonomyMenu.java` and forbids the literal `edit.commitChanges()`. Two mutations
survive: a third door in a third file (the same blind spot REG8-B2 filed against this test's
`AutonomyEditorPanel`-only scope), and a door in one of the two files that names its dialog anything
other than `edit` - the file still contains one `GraphLocAssign.commitAndRecord(`, so the control
assertion passes too. `filesWriting("commitChanges()")` against a declared list, in this file's own best
style, is the strong form and is one line.

### VAL9-C8 - REG7's table audit is broken markdown and still says Open

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - the file is in front of me |

`8b4531ec` answered REG8-C6 by putting the audit paragraph **inside the table's header row**
(`docs/reviews/REG7-regressions-2026-09-07.md:29`): the line begins `| # > **AUDITED 2026-09-07...` and
is followed by a four-column header (`| Finding | Severity | Confidence | Disposition |`), a
five-column separator, and five-column data rows. It will not render as a table.

More important than the rendering: the disposition cells for `REG7-A1`, `A2` and `B1` still read
**Open**, contradicting the note directly above them. `docs/reviews/README.md` - *"One status, one
location. A finding's disposition belongs in exactly one place - the status table at the head of its
section"* - is the rule, and REG8-C6 asked for the table. The correction as made leaves the same reader
with the same two answers, which is `audit-the-bodies-not-the-index` with the index and the body
swapped.

### VAL9-C9 - REG8-C5 is two thirds done, and one test javadoc describes a mechanism it does not use

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

- `dd3fb5d4` attached `layout.ui.tooltipGrowDiagram` and `tooltipShrinkDiagram`. The third orphan
  REG8-C5 named, `layout.ui.errorNoStationsInGraph`, is still present in all eight bundles and still
  read by no `.java` in `src/` or `test/`.
- `testTheKeyboardDrag`'s new javadoc says `giveTheClipboardBack` is *"Called from a finally in every
  test"*. It is an `@AfterMethod(alwaysRun = true)`, which is better, and no test has a finally for it.
  The comment describes an implementation that was not written - the same class of sentence C4 was
  filed about.

---

## D - checked and clean

Recorded so this ground need not be re-ploughed. These are the fixes I would stop thinking about.

**D1 - the `parseAuto` ordering is right for every path in.** The deferred loop
(`Layout.java:9203-9219`) is the last thing before `rebuildHomeStations`, and I searched
`Layout.java:8050-9230` for anything that could place or displace a locomotive after the point loop:
there is exactly one, `clearBlockExcept(placeOn)` immediately followed by `placeOn.setLocomotive(l)`
(`:8783-8786`), and both are *inside* the point loop, so both precede the deferred application. The
`clearBlockExcept` interaction is benign: a point it empties gets the file's `arrivedFrom` re-applied
while standing empty, and `edgesCoveredByStandingTrains` skips any point with no locomotive
(`Layout.java:5418`), so a side on an empty point
blocks nothing. Nothing between the collector and the application reads `arrivedFrom`: `blockersByPoint`
writes `setBlockedBy`, and `rebuildHomeStations` is about home claims. There is no second entry point -
`fromJSON` is the only caller of this loop.

**D2 - the clearing sweep is complete, and the door IND9-A1 asked about is covered by a better fix.**
Every session-side door that empties a square goes through `placeLocomotive(tile, null)` or
`clearEveryPlacement`: `AutonomyEditorPanel.java:1133`, `LayoutRightclickAutonomyMenu.java:638`,
`GraphLocAssign.commitAndRecord`, and `TrainControlUI.rememberPlacement`. IND9-A1 asked for
`rememberPlacement`'s `setArrivedFrom` to be moved out of the `getCurrentLocomotive() != null` guard;
it was not, and it does not need to be - the cut branch reaches `placeLocomotive(tile, null)` first
(`TrainControlUI.java:6260`), which now clears the side. That is the smaller fix and it covers doors
`rememberPlacement` does not.

**D3 - `wouldAsk` is exactly `forPlacement`'s dialog condition.** Branch by branch:
`forPlacement` reaches `ask` iff `layout != null && at != null && !sides.isEmpty() && sides.size() != 1
&& mayReverse`; `wouldAsk` returns true iff `mayReverse && layout != null && at != null &&
sidesOf(...).size() > 1`. Identical, and `sidesOf` is a pure read of the layout called back-to-back at
one call site with the same three arguments, so nothing can move between them. The only divergence is
the exception path (VAL9-C4).

**D4 - the `commitAndRecord` extraction is equivalent, and the two things that look like losses are
not.** Diffed against the old inline block line by line: the heading is still read from `edit.getLoc()`
**before** `edit.commitChanges()` (CONF-B3 kept), the `point.getCurrentLocomotive() != null` guard is
kept with its comment, `facingAfterAPaste` is called with the same three arguments, and the editor door
still fires `setupChanged()` afterwards. Two apparent regressions, both checked and cleared:

- the old code used `layout` from `layoutSource.get()` and the new call passes `runningLayout.get()`.
  These are two different suppliers, and in the borrowed diagram panel only one of them is set. It does
  not matter: this door is behind `if (!menuOnly)` (`AutonomyEditorPanel.java:1126`) and the borrowed
  panel is `setMenuOnly(true)`, so the door is editor-only, and `LayoutEditor.java:1607` and `:1621`
  set both suppliers to the same lambda.
- the old code passed the caller's `target`; the new method re-derives the tile from
  `getStationIndex().squareOf(point.getName())`. The round trip is exact, not lossy:
  `point` came from `pointOnTheLayout(layout, target)`, which iterates
  `getStationIndex().pointNamesAt(target)`, and `pointsBySquare` is built by inverting `squareByPoint`
  in the index's constructor (`StationIndex.java:82-95`). `tile == target` always.

**D5 - the diagram door does nothing it should not.** `commitAndRecord` writes only when the assignment
took; `forgetPlacementsElsewhere` inside `placeLocomotive` is precisely the two-placements repair
`placeFacing`'s comment warns about; no rebuild is fired, correctly, because `commitChanges` has already
moved the running layout. The setup-write half is the half that is missing its save (VAL9-B2), not a
half that does too much.

**D6 - GUI-builder hygiene.** No `.form` file changed and no `GEN-BEGIN`/`GEN-END` content appears
anywhere in the cumulative diff `95c09ac4~1..HEAD`. The `LayoutEditor` tooltips are set in ordinary
code beside the existing `diagramSize` tooltip, null-guarded, on fields the generated block declares -
which is the permitted pattern.

**D7 - bundles.** No bundle file was touched in this window, so IND9-D1's byte-level verification still
stands. The two keys the new tooltip code names are present in all eight files. The one inconsistency
left is the third orphan (VAL9-C9), which is a leftover from before this window rather than something
these commits created.

**D8 - the two catches.** `syncWithCS2()` declares no checked exception (it is called from a lambda
that declares none), so `catch (RuntimeException)` cannot be masking a checked failure, and what it
catches becomes `syncResult = -1` - the failure code `doSync` already knows how to report - with the
throwable logged. `Error` still propagates. On the clipboard path, `getSystemClipboard()` throws
`HeadlessException` and `setContents` throws `IllegalStateException`; `StringSelection`'s constructor
throws nothing, and `copyTarget` is null-checked two lines up, so no NPE is being hidden. Neither catch
swallows anything the operator could act on that is not in the log. One asymmetry worth a glance some
day: the clipboard catch guards `this.model != null` and the sync catch does not.

**D9 - no new field needs a copy constructor or a serialisation sweep.** `reversedOnArrival` is
per-`Layout` runtime state that deliberately dies with its layout; `tailAtTheLanding` and
`facingAtTheLanding` are UI-transient; `priorClipboard` is test-only. Nothing new goes through
`toJSON`/`fromJSON`, a snapshot, or an undo path. `arrivedFrom` predates the window and round-trips
correctly now that D1 holds.

**D10 - `testWhatTheSetupRecordsIsWhatTheRailwayGets` is the strongest test in the batch.** It asserts
at the consumer, in both directions, over three properties rather than the one that failed; it asserts
its own precondition (`built.getCurrentLocomotive() != null` - "without it this test cannot fail"),
which is the assertion that makes it bind; and it searches for a station that actually offers a side
rather than taking the first. Both A's fail it. I could not construct a mutation of either fix that
survives it.

**D11 - the new `flipFacing` call does not reintroduce DIR-B4's off-EDT store write.** Every caller of
`updateVisiblePoints` I could reach arrives on the event thread - `DiagramMonitorDriver:278` is inside
`SwingUtilities.invokeLater` (`:255`), and the rest are menu handlers, the paste handler, the rename
dialog and page-switch code. Medium confidence: an exhaustive proof needs a run, and it is the kind of
property that is true until somebody adds a caller.

**D12 - `behaviour.md` now matches the code on everything it was contradicting itself about.** The
mid-run bullet is reconciled with the paragraph below it; the "set or cleared" bullet says set; the
refusal that `d45d7951` removed is gone and replaced with the reason it cannot arise. The one place the
document now over-claims is the new destination paragraph - *"the window applies it to the graph the
next time the railway is idle"* - which is true only when `flipFacing` does not decline, and which says
nothing about the autonomy case VAL9-A1 is about.

---

## Ranked summary

| Finding | One line | Grade |
|---|---|---|
| VAL9-A1 | The destination-turn record fires for autonomy and Return Home too, and a Set of names collapses several reversals in one session into one flip | A |
| VAL9-B1 | Nothing asserts the turn reaches the graph; deleting the `flipFacing` call leaves the battery green and IND9-B4 fully back | B |
| VAL9-B2 | The diagram assignment now reaches the setup in memory and nothing saves it, so REG8-B2's restart symptom survives | B |
| VAL9-C1 | The occupant-change clear has no test, and both assignment doors now depend on it | C |
| VAL9-C2 | The drain discards its record when there is no session, and edits the stored setup without a word | C |
| VAL9-C3 | `wouldAsk` is pinned by a `contains` only; `return mayReverse;` restores IND9-B5 with everything green | C |
| VAL9-C4 | "Could not ask" still abandons the paste, against the commit message's own criterion | C |
| VAL9-C5 | `"Marklin CS3 v2.6 ... v3.0.0"` hides a release; `"Nov2026 ... v3.0.0"` invents one | C |
| VAL9-C6 | Both strengthened source guards still have one concrete surviving mutation each | C |
| VAL9-C7 | The `commitChanges` guard reads two named files and no others | C |
| VAL9-C8 | REG7's audit broke the table and left three dispositions saying Open under a note saying they are closed | C |
| VAL9-C9 | The third orphan key is still orphaned; a new test javadoc describes a `finally` that is an `@AfterMethod` | C |
| VAL9-D1..D12 | The `arrivedFrom` ordering, the clearing sweep, `wouldAsk`'s equivalence, the `commitAndRecord` extraction, GUI hygiene, the bundles, both catches, and the new consumer-side test - all clean | D |
