# IND9 - independent review of the 2026-09-06/07 commits

**Status:** open

**Prefix:** `IND9`. **Reviewed:** working tree at `8ea1ff15` (branch `autonomy-diagram-r0`),
2026-09-07. **Scope:** the eight commits `d45d7951` .. `8ea1ff15`, reviewed against
`docs/reference/behaviour.md` and `docs/reference/package-sweeps.md`.

**Method:** reading and static reasoning only. No build was run, no test was run, nothing outside this
document was edited, and nothing under `cs2_sample_layout/` was touched. Where a finding claims a test
would fail or pass, that is a traced prediction, not an observed run - the Confidence row says how far
I trust it.

**Overall verdict first, because the author asked for honesty over volume:** the work is largely
sound. The C13 sweep is complete and its model test is the strongest in the batch; the C3 guard is
correctly placed with a real driving test; the bundles are byte-clean; nothing touched a GEN block or
a `.form` file. The real defects I found cluster where the author said they would: a placement door
whose sibling was swept for `facing` and not for `arrivedFrom` (A1), a ruling pinned at the layer
below the one that decides (B1), and a reference document that contradicts itself about the one rule
that changed twice in a day (B3). Section D lists what came back clean so it need not be re-read.

---

## A - wrong behaviour on the layout

### IND9-A1 - a stale `arrivedFrom` outlives its train, and the assign door never writes one

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High on the mechanism (every link verified in source); medium-high that the full sequence reproduces without a step I have not seen |

The tail-blocking chain is: setup `arrivedFrom` -> `AutonomyBuilder` emits it on every copy
(it is not in the excluded-keys list, `AutonomyBuilder.java:941-986`) -> `parseAuto` writes it onto
the built `Point` (`Layout.java:8336-8340`) -> the tail walk greys and blocks track behind whatever
locomotive stands there.

Four writers manage that setup value, and only two of them do the whole job:

1. `AutonomySession.placeLocomotive` (`AutonomySession.java:4590-4623`) clears `loc` **and** `FACING`
   on the null path - with a comment saying exactly why: *"the next locomotive placed here inherits
   the last one's direction without being asked"* - and does **not** clear `arrivedFrom`, on either
   path. The identical argument applies word for word: the arrival side described the train that was
   standing there, not the square.
2. `AutonomySession.clearEveryPlacement` (`AutonomySession.java:4637-4655`) - same two clears, same
   omission, on every square at once.
3. The editor's **Place/Edit Locomotive At...** door (`AutonomyEditorPanel.java:4055-4110`,
   `GraphLocAssign`) writes the placement and - since REG6-B5 - the facing, and never touches
   `arrivedFrom` at all. Its own comment four lines up cites `fix-one-site-sweep-the-siblings` about
   the facing; the arrival side is the sibling that was not swept this time.
4. `TrainControlUI.rememberPlacement` (`TrainControlUI.java:6265-6301`) is correct on a paste, but its
   `session.setArrivedFrom(tile, tail)` sits inside `if (point.getCurrentLocomotive() != null)` - so
   the **cut/clear** branch (which reaches `rememberPlacement` with the square emptied) never clears
   the setup value either.

`Point.setLocomotive` clears the **layout** side on an occupant change (`Point.java:534`), so the
running railway is briefly right - and then the next rebuild reads the setup, which is wrong, and
puts the stale side back. `captureFromLayout` cannot heal it: `arrivedFrom` is not in
`POINT_OPERATIONAL_KEYS` (`AutonomySession.java:2451-2453`).

**Reproduction (all by hand, no autonomy needed):**

1. Paste train A onto a may-reverse square S from the diagram; answer "from the north" ->
   setup `arrivedFrom=N` for S.
2. Ctrl-X cut A off S (or clear it from the editor). Setup still holds `arrivedFrom=N`; nothing on
   screen says so.
3. In the editor, use **Place Locomotive At...** on S to assign train B (a train that was never
   driven there).
4. `setupChanged()` rebuilds. B's built Point carries `arrivedFrom=N`; the tail walk greys the track
   north of B and refuses routes across it, based on where **A** came from. If B's actual tail lies
   south, the fouled track is not blocked and the clear track is.

The interaction with `8ea1ff15` makes it worse, not better: with the "Not known" menu option removed,
the operator has **no way to clear** the stale side - only to overwrite it with another positive
claim. Adam's ruling ("clearing should not be possible, only setting") rests on the premise that
*"every train that reaches a square now has an answer"* (comment in
`AutonomyEditorPanel.java:2947-2959`, `behaviour.md` section 4) - and the assign door is a placement
door that produces trains with no answer while inheriting somebody else's. The premise is broken by
this finding, not by the ruling.

**Fix shape:** clear `arrivedFrom` wherever `FACING` is cleared (both `placeLocomotive` paths,
`clearEveryPlacement`), move `rememberPlacement`'s `setArrivedFrom` out of the non-null guard so a
clear writes null, and have the `GraphLocAssign` door either ask (it is the same "hand placement"
`behaviour.md` section 4 describes) or write null. A test can pin it entirely at the model/session
layer: place, record a side, clear, re-place via `placeLocomotive`, rebuild, assert the built Point's
`getArrivedFrom()` is null.

---

## B - real, but narrow, latent, or a claim a document cannot support

### IND9-B1 - reverting the d45d7951 fix leaves every test green

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - traced against each test that touches the area |

"Only the destination is asked about" is decided in `ManualReversalPrompt.forJourney`
(`ManualReversalPrompt.java:93-158`): the scoping to `path.get(size-1).getEnd()`, and the
`at == asked` clauses in the returned policy. No test calls `forJourney` and observes that scoping:

- `testOnlyTheDestinationIsAskedAboutAndIntermediatesTurnAsRequired`
  (`testNonReversibleTrains.java:401-471`) builds its **own** policy with the door's intended shape
  and drives `Layout.shouldReverseAt` with it. It verifies the layer below the door - exactly the
  weakness the author asked me to probe. Restore the pre-d45d7951 `forJourney` (destination first,
  falling back to the first may-turn square on the route, answer applied path-wide) and this test
  still passes, because it never consults `forJourney`.
- `testTheJourneyPolicyAnswersAsksAboutIndependentlyOfTheAnswer`
  (`testNonReversibleTrains.java:593-626`) is a source-shape check that only forbids the word `turn`
  inside `asksAbout`. The reverted code contains no such word; green.
- The REG7-A1 stranding refusal was removed as dead code, so nothing else fails either.

So the ruling Adam gave on 2026-09-07 - and the REG7-A1 defect it dissolved - can silently regress:
put the path-scanning fallback back and a manual journey through an intermediate may-turn square
prompts again, with the whole battery green.

**A test is feasible without a dialog:** `forJourney(session, null, path, loc)` for a path whose
*intermediate* is a may-turn square and whose destination is not asks nothing (nothing qualifies) and
returns `KEEP_DIRECTION` today; assert that, plus `policy.asksAbout(intermediate) == false` and
`policy.shouldReverse(loc, intermediate) == false` on the regressed shape's own terms. In a headless
run the dialog path cannot open anyway (`ask` catches and answers keep), so the assertions bind in
both environments. A session stub that answers `mayTurnTiles()` for the intermediate square is the
only fixture work.

### IND9-B2 - package-sweeps.md claims every behavioural fix was proved by a failing test; five were not

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - searched the whole `test/` tree per fix |

`docs/reference/package-sweeps.md:10-11`: *"Every behavioural fix below was proved by a test seen
failing first."* Of the fourteen rows in "Fixed":

- **C6** (atomic id, `Point.java:127-167`) - no test mentions the allocator. Defensible for a
  concurrency fix, but then the sentence should say so.
- **C7** (received speed rounded, `MarklinLocomotive.java:596`) - no test anywhere exercises the
  divide-by-ten path or `Math.round`. The commit message elevates this fix ("what the app believes
  about a train is what the length and blocking rules run on") and it shipped unpinned; a revert to
  `speed /= 10` is invisible to the battery.
- **C19b** (power-state guard, `Locomotive.java:467`) - no test.
- **C22** (`doClearCurrentPage`, `TrainControlUI.java:17714-17738`) - no test; `testTheKeyboardDrag`
  covers the four drag gestures and never touches page clearing.
- **C24** (cancelled label edit, `LayoutEditor.java:3659-3670`) - no test.

Separately, the `bc6120f1` fix (reversals during a run ignored - `reconcileFacingWhenIdle`,
`TrainControlUI.java:6125-6154`) shipped with **no test at all**, and it is the most
behaviour-changing edit in the window. The five fixes above look correct on reading (see D5-D9), but
the project's own rule is that a test written after the fix agrees with the bug, and a claim of
red-before-green that is false for five of fourteen rows is the kind of claim this review was asked
to check the sweeps file for.

### IND9-B3 - behaviour.md contradicts itself about mid-run reversals

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - both sentences are in the current file, and the code was checked against each |

`docs/reference/behaviour.md`, section 3, "Direction commands":

- Line 153-154: *"a reversal made **during** a run is followed once the run ends, not discarded."*
- Line 159-163: *"A direction command arriving while a run is under way is **ignored, not queued**.
  The baseline is brought up to date as it arrives ... Reversals are counted only when nothing is
  running, and there is no backlog."*

These are mutually exclusive, and the code does the second: `reconcileFacingWhenIdle` levels
`lastSeenDirection` to the current state at every idle refresh (`TrainControlUI.java:6125-6154`,
called at `:25520`), so nothing arriving mid-run is ever followed. The first sentence is the
pre-`bc6120f1` behaviour, left standing when `8ea1ff15` revised the document the same night. The
document exists to be read against the code; on this rule it licenses both answers, which is the one
thing it must not do. One sentence should go, with Adam's 2026-09-07 quote deciding which (it decides
for the second).

### IND9-B4 - a manual turn at a may-reverse destination is executed on the track and never recorded on the graph

| | |
|---|---|
| **Disposition** | Open - and worth a hands-on check before treating as confirmed |
| **Confidence** | Medium - every code link traced, but this is layered behaviour and a probe should confirm it |

Sequence: manual send to the **plain** copy of a may-reverse station; the departure prompt appears
(the plain copy is not a terminus); the operator answers **No** (turn). At arrival,
`shouldReverseAt(arrived, arrived, ...)` consults the policy (`Layout.java:6520`), which answers yes
(`turn && at == asked`), and `loc.switchDirection()` runs. The direction echo arrives inside the
run's own `delay(1000)`, while `isRunning()` is still true - so `followDirectionChanges` takes the
baseline-only branch (`TrainControlUI.java:10185-10196`). When the run ends,
`reconcileFacingWhenIdle` **levels** the baseline instead of following. Nothing else writes the
facing: `executePathInternal` records `arrivedFrom` at arrival and nothing more, and
`captureFromLayout` reads the placement copy, which is still the plain (un-turned) copy.

Net: the physical train is reversed, the graph and setup say it is not, and the next dispatch offers
paths for the wrong heading. Before `bc6120f1` the post-run echo followed the change (late, and
misattributed - the defect that commit fixes), so for this one case the old backlog happened to
record the truth. `behaviour.md:152` - *"a direction command from the track DOES update the graph, if
it disagrees"* - is not true of the command the run itself issued at the destination, and
`behaviour.md`'s account of the arrival ("the arrival writes the graph") covers `arrivedFrom` only,
not facing.

If this is judged acceptable ("the operator turned it, the operator can see it"), it should be a
stated limit in behaviour.md section 3; today the document reads as though the graph follows.

### IND9-B5 - the dismissed-paste guard reads three different nulls as one

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High on the code paths; medium on how often the bad ones occur on real layouts |

`TrainControlUI.java:6037-6042` abandons the paste when `forPlacement` answered null on a may-turn
square, on the theory that null there means dismissed. `ArrivalSidePrompt.forPlacement`
(`ArrivalSidePrompt.java:62-80`) returns null on a may-turn square in three distinct cases:

1. the operator dismissed the dialog - the case the guard is for;
2. `sides.isEmpty()` - the chosen copy has no compass-resolvable neighbour at all. `sideTowards`
   answers null for a point without coordinates and for a zero delta (`Layout.java:5512-5527`), so a
   may-turn square whose `speakerAt` copy connects only through neighbours the grid cannot place
   resolves to no sides. No dialog is ever shown, and the paste is refused **silently, every time,
   with no log line** - the operator cannot paste onto that square and nothing says why. (Contrast
   the way-out refusal directly above it, which logs `autolayout.warnNoWayOutOfPoint`.)
3. the `invokeAndWait` threw (`cannotAsk`) - a headless or interrupted EDT now abandons the paste
   rather than placing with nothing recorded.

Case 2 is the "guard knows only what it lists" hole: `forPlacement`'s javadoc even documents the two
meanings of null ("cannot be worked out **and was not answered**") and the guard collapses them. Fix
shape: let `forPlacement` distinguish "no question to ask" (empty/one side) from "asked and
dismissed" - a sentinel, a small result object, or asking `sidesOf().size() > 1` at the guard - and
log the refusal it does make.

### IND9-B6 - the baseline levelling has a window in which the backlog still fires

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | Medium-high - the ordering is real; the window is small and needs an unlucky echo |

`reconcileFacingWhenIdle` runs only when a refresh runs. At run end the sequence is:
`locomotiveThreads.decrementAndGet() == 0` (driving thread, `Layout.java:5833`) ->
`announceRunFinished` -> callback -> `invokeLater(onPathEvent)`
(`AutonomyRefreshCallback.java:67-78`) -> EDT eventually runs `updateVisiblePoints` ->
`reconcileFacingWhenIdle` (`TrainControlUI.java:25520`).

Between the decrement and that EDT refresh, `isRunning()` is already false, and the baseline is still
stale for any locomotive the run turned (the mid-run echo hit the `putIfAbsent` branch,
`TrainControlUI.java:10193`). Any locomotive message processed on `locMessageProcessor` in that gap -
a late echo of the run's own final commands, a CS2 broadcast, the operator touching the throttle -
reaches `followDirectionChanges` line 10198 with the stale baseline and **follows the run's own
reversal**, which is precisely the backlog `bc6120f1` says is gone. The run's terminal
`switchDirection().delay(1000)` makes the run's own echo unlikely to land late, but the max-latency
setting this project supports says late echoes are a case it plans for.

Fix shape: level the baseline where the run ends rather than where the next repaint happens - e.g.
inside `announceRunFinished`'s caller before the count reaches zero is observable, or have
`followDirectionChanges` treat "first message after a run ended" as baseline-only. A test can pin the
ordering: with the layout just stopped and a stale baseline planted, deliver one locomotive update
and assert no `flipFacing` occurred.

---

## C - minor

### IND9-C1 - behaviour.md describes a refusal that does not exist

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

`behaviour.md:135-139`: *"A journey to a may-reverse **destination** is still refused if the operator
declines the turn it needs, because that is the square the question was about."* No refusal exists
anywhere: `whereTheJourneyWouldStrand`, both door checks and the message were all removed in
`d45d7951`, and both doors now dispatch unconditionally after the prompt
(`AutoLocomotiveStatus.java:1038-1060`, `LayoutRightclickAutonomyMenu.java:1044-1077`). Nor can the
case arise: a journey ending at the **turning** copy of a may-reverse station ends at a terminus
(`AutonomyBuilder.java:988-995` - a turning station copy is emitted as a TERMINUS) and is never
asked; a journey ending at the **plain** copy needs no turn to complete, so there is nothing to
"need" and decline. The sentence is a leftover from the refused-journey design that the same day's
ruling dissolved. C rather than B only because no reachable behaviour is wrong - but this is the
ground-truth file, and a nonexistent rule recorded there will eventually be "restored".

### IND9-C2 - parseReleaseVersion takes the first number, which is not always the version

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High on behaviour; low on likelihood - it needs a hand-typed name with a leading number |

`Util.java:657-665` matches the first `[0-9]+(?:[.][0-9]+)*` in the release name. Names that break
it, all of the "typed by hand at tag time" kind the fix's own comment warns about:

- `"TrainControl 2026 Edition v3.0.0"` -> `"2026"` -> compares newer than anything -> the menu
  offers "v2026 Update Info".
- `"Build 1 v3.0.0"` / `"Java 8 build v3.0.0"` -> `"1"` / `"8"` - the first hides the release
  **silently**, which is the exact C5 failure mode the fix exists to close.

The old code anchored on the `v`; the new code anchored on digits; the release names carry both. A
version-shaped match anchored on `v` first, falling back to the bare number
(`[vV]([0-9]+(?:[.][0-9]+)*)` before the current pattern), closes it, and
`testAReleaseVersionIsReadOutOfAnyName` (`testInvalidInput.java:173-207`) is the obvious place for
the two new rows.

### IND9-C3 - the C19b comment claims the sibling it sits sixty lines below does not exist

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

`Locomotive.java:462-466`: *"power going off DURING a run still counts the whole stretch as running
time, because lastStartTime is stamped once and nothing revisits it."* `notifyOfPowerStateChange`
(`Locomotive.java:390-443`) revisits it in both directions: power-off closes the interval and zeroes
the stamp; power-on re-stamps. So the stretch after a power-off is *not* counted, and the "different
question - not one to settle inside a guard" the comment defers was already settled, correctly,
sixty lines up. A comment asserting the comfortable version of what the code does is the failure mode
`ManualReversalPrompt`'s own javadoc (line 278-294) warns about by name.

### IND9-C4 - the drag tests overwrite the runner's clipboard while explaining why they must not

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High |

`testTheKeyboardDrag.java:38-40`: *"asserting it for real would mean overwriting whatever the person
running the battery had copied, and a test that costs its runner their clipboard is not worth what it
proves."* Every one of the five tests then calls `ui.setCopyTarget(...)` on a populated key, which
runs the very clipboard write being discussed (`TrainControlUI.java:8496-8506`) - so the battery
costs Adam his clipboard anyway ("KD one" lands on it), and only the *assertion* was spared. Either
the cost is acceptable (then the comment is wrong and the assertion might as well exist) or it is not
(then the fixture should stub or skip the write). Cosmetic, but it is a false sentence inside a test
that exists to stop reviewers misreading this exact code.

### IND9-C5 - two of the new source-shape assertions prove less than their failure messages claim

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | High - the surviving mutations are concrete |

Both in `testEditorSurfaceRules.java`:

- `testAFailedSyncGivesTheMenusBack` (`:1457-1490`) asserts `substring(0, call).contains("try")` and
  `substring(call, back).contains("catch")`. Neither proves the call is *inside* the try, nor that
  the catch *continues* to the re-enable. Surviving mutation:
  `catch (RuntimeException e) { this.model.log(e); return; }` - test green, C23 fully back (menus
  greyed for the session). Any future unrelated `try` earlier in `doSync` also makes the first
  assertion permanently vacuous. Today the method's only `try` is the right one (verified), so the
  guard binds *now* - the weakness is what it will still be guarding after the next edit.
- `testTheDragPutsTheNameOnTheClipboardSafely` (`:1413-1445`) - same shape:
  `drag.substring(0, wrote).contains("try")` is satisfied by any try anywhere above the write.
  Surviving mutation: wrap the earlier `if (button != null ...)` bookkeeping in its own try/catch and
  move the clipboard write below/outside - test green, C26's deletion hazard back.

Cheap strengthening for both: assert on the ordered triple (`try` index < call index < `catch`
index < re-enable index) *and* that no `}` closing the try body intervenes - or, simpler and in this
file's own best style (`testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning`), assert what the catch
*does*: `sync.substring(call, back).contains("syncResult = -1")`.

---

## D - checked and clean

Recorded so the next pass need not re-plough these.

- **D1 - message bundles.** All eight bundles carry identical key sets (1,916 keys each, verified by
  script), zero non-ASCII bytes in any of them. The four removed keys (`arrivedFromUnknown`,
  `journeyNeedsTheTurn` and its two continuation lines) are gone from all eight, and no source or
  test still references either key (the one remaining mention is historical prose in
  `VAL8-validation-2026-09-07.md:299`, which is a record, not a reference).
- **D2 - GUI builder hygiene.** No `.form` file and no `GEN-BEGIN`/`GEN-END` content changed anywhere
  in the window (checked across the full cumulative diff). Edits inside `//GEN-FIRST` handler bodies
  are the permitted kind.
- **D3 - the C13 sweep is complete.** Every remaining `getAccessoryState(` caller is a command path
  or inert: the abstract pair (`Locomotive.java:569`, `MarklinLocomotive.java:503`),
  `waitForAccessoryState` (`Locomotive.java:618` - no production caller; only `testLocomotive` calls
  it, as `2026-08-21-review-dispositions.md:269` already established), and the two example files.
  `Edge.java:165` and `NodeExpression.java:178` already use the IfPresent lookup.
  `getAccessoryStateIfPresent` gives the identical answer to the miss branch (created-unswitched
  reads false; absent reads false). The model test
  (`testAdvancedRoutes.testEvaluatingAConditionDoesNotInventTheAccessory`) asserts its own
  precondition, both polarities, and absence after each - it fails on a revert - and the whole-file
  absence assertions in `testEditorSurfaceRules.java:1503-1543` catch any *new* paint-path caller,
  which is the strong form. This is the best-executed fix in the window.
- **D4 - the C3 guard.** `isRunning()` covers manual dispatches and thread coast-down
  (`running || activeLocomotives || locomotiveThreads`, `Layout.java:1663-1667`), staging is
  included, and the guard sits in the model where every door must pass. The unguarded siblings I
  went looking for are not reachable hazards: `createPoint`/`createEdge` have no UI caller (examples
  and the build path only), and the setup-rebuild door is separately guarded by `isAutonomyBusy`
  (`rebuildRunningLayoutFromSetup`, `TrainControlUI.java:5616-5663`, resting on OB-047's
  editor-cannot-open-while-running). The test
  (`testHomeStaging.testTheGraphCannotBeEditedWhileTheRailwayIsUsingIt`) drives the real methods,
  keeps `renamePoint` as a control, and proves the way past with the same arguments the refusals
  used - so a refusal for the wrong reason cannot hide.
- **D5 - C19b is compensated, not lossy.** The scenario the guard could have broken - power off,
  speed commanded, power returns, train runs - is caught by `notifyOfPowerStateChange`'s power-on
  branch, which stamps `lastStartTime` when `speed > 0` on a real transition
  (`Locomotive.java:417-427`). No running time is lost and none is double-counted in any ordering I
  could construct. (The stale comment above the guard is IND9-C3.)
- **D6 - C6.** The allocator had no resetter and exactly one increment site before the change;
  `AtomicInteger.incrementAndGet` preserves the 1-based sequence. Nothing else touches
  `ID_ALLOCATOR`.
- **D7 - C7.** `MarklinLocomotive.java:596` is the only receive-side scaling site (the send side at
  `:820` is exact multiplication), rounding halves the worst-case round-trip error and removes the
  one-directional bias; values above 1000 would now scale past 100 and be dropped by `_setSpeed`'s
  range check instead of clamping, which only a malformed datagram can produce.
- **D8 - C22.** The page test is asked of the page's own mapping, `equals` on interned model
  locomotives is safe here, and a locomotive mapped to several pages loses activeness only when the
  cleared page holds it - defensible either way, and the same answer the old code gave.
- **D9 - C24.** The `newText != null` guard matches `JOptionPane` cancel semantics, and leaving the
  re-add unconditional is the right trade for exactly the reason the comment gives.
- **D10 - the sweeps file's C4 row is true.** `HomeStaging.blockedSensors`
  (`HomeStaging.java:1309-1326`) does ignore its parameter and read `this.start`, and that *is* the
  correct behaviour for unknown-occupancy-as-start-fact; the caller at `:768` passing `state` gets
  the right answer for the wrong-looking reason, exactly as filed.
- **D11 - the d45d7951 semantics at the layer that decides.** With the real door's policy,
  `shouldReverseAt` forces the turn at an intermediate turning copy (`asksAbout` false there ->
  `Layout.java:5317` returns true), leaves the plain copy of an intermediate may-turn square alone
  (`mayReverseAt` true -> falls through to `shouldReverse` -> `turn && at == asked` false), and
  obeys the answer only at the destination. The `at == asked` identity comparison is sound because
  the policy and the run share the same `Point` objects from the same path list. The removal of the
  stranding refusal is consistent with the ruling: with no intermediate ever asked, no journey can
  depend on a declined answer. (What the doc says about it is IND9-C1; what happens to the *record*
  of a destination turn is IND9-B4.)
- **D12 - the ec5dde63 tests.** The drift test has a non-empty floor, three builds (the toggle
  argument is right), and captures placements by built-Point name, which is what a copy-choice drift
  would move. The terminus-coupling test pins the setter three files away rather than asserting a
  behaviour of an unreachable state - the honest shape for "cannot happen".
- **D13 - testTheKeyboardDrag fixture hygiene** (apart from IND9-C4): `LayoutSandbox.open()` before
  the window per OB-111, MM2 addresses 240+ clear of the diagram, real-LocDB teardown, victims
  planted on the wrong page so a mis-paged clear has something to delete, keys sorted for
  determinism. The mutation claims I traced (cut block, clearCopyTarget, copyTargetPage) all hold.
- **D14 - dismissal polarity.** `reverseFor(chose) == (chose == NO)` still means Escape (-1), X, and
  cannot-ask all keep direction; the paste door's dismissal answers null, not a side. Both dialogs
  use the application's own option arrays.
- **D15 - clipboard siblings.** The other two `setContents` sites
  (`TrainControlUI.java:22612-22617, 22689-22694`, the JSON/route exports) were already inside
  `catch (Exception)` blocks; no unguarded sibling remains. (Their catch shows an "export failed"
  dialog for a clipboard-only failure after a successful export - pre-existing, outside this window,
  noted here so it is not re-found.)
- **D16 - no new model fields needing copy-constructor or serialisation sweeps.** The window's new
  fields are UI-transient (`tailAtTheLanding`) or static (`ID_ALLOCATOR`); `arrivedFrom` predates
  the window and round-trips through `Point.toJSON`/`parseAuto`.

---

## Ranked summary

| Finding | One line | Grade |

**This table has no disposition column, which the reviews README forbids: one status, in one place.**
Adding one now would be inventing a second home for something `docs/reviews/README.md` says lives on
the finding.  So it is said here instead, checked against HEAD on 2026-09-08: **A1, B1, B3, B4, B5 and
C1 are fixed**; **B2 is a process observation rather than a defect**; **B6 is the late-echo race, open,**
and is the same finding as REG8-B1.  Each finding's own body carries its disposition and is the
authority.
|---|---|---|
| IND9-A1 | Stale `arrivedFrom` survives clears and the assign door, and blocks the wrong track for the next train | A |
| IND9-B1 | The destination-only ruling is pinned below the door; reverting `forJourney` leaves the battery green | B |
| IND9-B2 | "Every behavioural fix proved by a failing test" is false for C6, C7, C19b, C22, C24 - and bc6120f1 | B |
| IND9-B3 | behaviour.md states both the old and new mid-run-reversal rules | B |
| IND9-B4 | A manual destination turn is executed on the track and never recorded on the graph | B |
| IND9-B5 | The dismissed-paste guard reads "no sides" and "cannot ask" as "operator said no" | B |
| IND9-B6 | An echo between run-end and the next refresh still fires the backlog | B |
| IND9-C1 | behaviour.md's may-reverse-destination refusal does not exist and cannot arise | C |
| IND9-C2 | Release names with a leading number defeat the C5 fix in its own failure mode | C |
| IND9-C3 | The C19b comment denies the power handler that sits sixty lines above it | C |
| IND9-C4 | The drag tests spend the runner's clipboard while explaining why they must not | C |
| IND9-C5 | Two "try appears before the index" assertions survive concrete regressions | C |
