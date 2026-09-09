# D3 - three-day review (commits 2026-09-06 .. 2026-09-09)

**Prefix for citing these findings elsewhere:** `D3`

> **Operational note, resolved but worth keeping.** Mid-review, `git diff` showed a live mutation
> from the seven-day review in the shared working tree: `src/org/traincontrol/automation/Layout.java`
> ~line 2526 read `lyingAcross = null; // W7B MUTATION: rule disabled, pinned strings intact` -
> the shared-metal fouling rule (Adam's 2026-09-07 EN57-203-over-a-blocked-switch fix) switched
> off. It was that reviewer's red-check and they restored it before this review finished; the tree
> is clean now. It is recorded because three reviews were mutating one working tree concurrently,
> and a build made in such a window ships with an anti-collision rule off. **`git diff src/` must
> be empty before Adam builds**, every time.

## Scope and method

**Read:** every commit since 2026-09-06 (`git log --since="3 days ago"` - about 160 commits), with
full diffs of the ones that change behaviour: the OB-192 pair (`e4f8f577`, `49a3aee4`), W7-A2
(`759aabda`), D2-A1/W7-A1 (`74015c16`), MT-334 (`26375f83`), MT-247 (`c22c9d90`), MT-262
(`716cf3f5`), MT-309/blocked-track (`6bb0a0fc`, `e2d6e4de`, `e6f4649c`), REV9-B3 (`0016fc18`),
FR-065 (`d9876d69`, `1345220c`), OB-191 (`4a63a0a7`), the occupancy-tier ruling (`06d00a82`), the
turn-at-destination chain (`41913ecd`, `41848af2`, `e23f26d1`), and the tripwire/census commits
(`b9010b57`, `458ca15a`, `a6ea72d4`). On top of the diffs I swept the current source for the one
rule the two OB-192 commits state - *no event-thread call may queue on the `Layout` monitor* - by
enumerating every `synchronized` method of `Layout` and walking every `gui/` call site of each.

**Ran** (via `docs/tools/one.sh`, sharing the harness with two other reviewers):

- `regression.testTheGraphIsToldWhenARunEnds` - green, 0 skips.
- `regression.testAnEditedPlacementSurvivesTheRebuild` - green, 0 skips.

**Mutated, saw red, restored** (both restores verified by `git diff`):

- Removed `announceRunFinished()` from `Layout.executeTimetableInternal` (the W7-A2 addition,
  `Layout.java:5365`) -> `testTheGraphIsToldWhenARunEnds` fails 1 of 3. The test is real.
- Forced `putTheTrainsBack` to ignore `placementsJustEdited` (`TrainControlUI.java:5955`, i.e. the
  railway always wins) -> `testAnEditedPlacementSurvivesTheRebuild` fails 1 of 3, and goes green
  again on restore. The D2-A1 provenance fix is genuinely load-bearing and genuinely tested.

Nothing under `cs2_sample_layout/` was touched. My working-tree changes are fully reverted; the
only residual diff is the W7B mutation flagged above, which is not mine.

## A - high

### A1 - The diagram's right-click menu still takes the railway's monitor on the event thread

**Files:** `src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:287` (`getPossiblePaths`),
`:341` (`isOfferableToOperator`), `:354` (`isChoosableByAutonomy`); built on the EDT by
`showFor`'s `invokeLater` at `:67`.

OB-192 (`e4f8f577`) established, with a measured stack, that the event thread must never queue on
the `Layout` monitor: a dispatch holds it for the whole of `configureAndLockPath` - a
`CONFIGURE_SLEEP` (150ms) per edge and again per accessory, "several seconds on a six-edge path"
(`Layout.java:923`) - and `AutoLocomotiveStatus.findPaths` holds it for a whole-graph search on
every panel refresh *with nothing running at all* (the second round's own measurement,
`49a3aee4`). The second round fixed the return-home item **of this very popup**
(`HomeLocomotiveMenu.java`, called at `LayoutRightclickAutonomyMenu.java:237`) by making it read
the button's cached answer. Three lines further down the same constructor, the path-listing
section still calls `Layout.getPossiblePaths` - `synchronized`, and itself a whole-graph search -
plus `isOfferableToOperator` and `isChoosableByAutonomy` per candidate path, all on the EDT.

The guard at `:284` (`!getActiveLocomotives().containsKey(locomotive)`) only checks that *this*
locomotive is idle; it says nothing about any other holder of the monitor.

**Concrete scenario:** full autonomy is running with two trains; one is mid-dispatch inside
`configureAndLockPath`. The operator right-clicks a station where an idle third train stands - the
routine way to see its options during a run. The popup's constructor parks the event thread on
the `Layout` monitor: no repaints, no controls, no popup, for the length of someone else's
switch-throwing, and again at the next dispatch. This is the freeze of OB-192 by the next door
along - bounded rather than a deadlock (this path holds no window monitor), but on a
high-frequency gesture, and exactly the shape Adam has now reported twice.

**Why no tripwire caught it:** `testNothingOnTheEventThreadAsksWhetherAnythingIsAwayFromHome`
(`test/regression/testTheDiagramRefreshDoesNotWaitOnTheRailway.java:395`) greps `gui/` for
`triageReturnToHome` only. The rule its failure message states is general ("a user interface
class is on the event thread unless it has arranged not to be"); the guard enforces one method
name. It reports clean about every door it never heard of.

## B - medium

### B1 - Caption visibility asks the railway per station, on the event thread, at three doors

**Files:** `src/org/traincontrol/gui/TrainControlUI.java:1425`
(`captionIsActive` -> `Layout.isChoosableByAutonomy`, `synchronized` at `Layout.java:4352`),
reached from: `addLayoutStation` (`TrainControlUI.java:1482`, called per captioned square from
`LayoutGrid.java:1274` while the grid is built on the EDT inside `repaintLayout`'s `invokeLater`);
the overlay-toggle loop (`TrainControlUI.java:5061`); and `refreshCaptionVisibility`
(`TrainControlUI.java:10263`, an explicit `invokeLater` loop over every registered station).

Same mechanism as A1, lower amplitude per call: `isChoosableByAutonomy` is cheap inside, but the
EDT still queues behind whoever holds the monitor, once per captioned station.

**Concrete scenario:** autonomy is running; the operator switches diagram pages (or toggles Show
Inactive Labels). The grid rebuild walks its captions on the EDT, and the first
`isChoosableByAutonomy` lands while a dispatch is inside `configureAndLockPath`: the page change
freezes for the remainder of the configuration. `updateVisiblePoints` was fixed for exactly this
(its own comment now says the old "takes no Layout monitor" sentence "surveyed one of the two
things this method does"); the caption path is the thing it did not survey either.

### B2 - `isReturnHomeOffered` readers aside, the whole EDT/monitor rule has no general guard

This is the pattern finding over A1, B1, C1 and C4, recorded separately because it is the fix
worth making once. The rule is stated in three places now (`behaviour.md` §5c and the return-home
paragraph, `Layout.getEdges`'s comment, `refreshCoveredTrack`'s javadoc), and each of its three
enforcement points pins only the door that was last caught: the ASKS grep pins
`triageReturnToHome`; `testTheDiagramRefreshDoesNotWaitOnTheRailway` measures
`updateVisiblePoints` and the return-home button; `AutoLocomotiveStatus` moved `findPaths` and
both tooltip walks off the EDT and documented it. `Layout` has ~24 `synchronized` methods and
`gui/` calls at least six of them from the event thread today. A tripwire the same shape as the
ASKS grep, but keyed on the *list of synchronized `Layout` methods* with an allowlist of
off-thread spans, would have caught A1, B1, C1 and C4 before this review did - and will catch the
next door, which the per-method grep provably will not (it has already missed four).

## C - low

### C1 - The editor's "why is it not moving" tool walks the whole graph on the EDT

**File:** `src/org/traincontrol/gui/AutonomyEditorPanel.java:6291`
(`layout.explainDestinations(standing)`, `synchronized` at `Layout.java:4503`).

The editor cannot be open while autonomy runs (OB-047), so no dispatch can hold the monitor here -
but `49a3aee4` measured that `AutoLocomotiveStatus.findPaths` holds it for a whole-graph search on
every placement refresh with nothing running, and the why-tool's click handler runs on the EDT.
The identical question was moved off the EDT twice in `AutoLocomotiveStatus` (OB-079: "hovering
this label while a train was being dispatched froze the whole window"; and `whyNotReport`'s
`new Thread`). The editor's copy of the question was not swept. Stall is a graph search, not a
dispatch - fractions of a second on today's railway - hence C.

### C2 - Deleting a timetable row: guard, then modal dialog, then an unsynchronized remove

**File:** `src/org/traincontrol/gui/TrainControlUI.java:25481-25514`;
`Layout.getTimetable()` (`Layout.java:848`) returns the live `LinkedList` (`Layout.java:782`).

The handler checks `isAutonomyBusy()`, then holds a modal confirmation open for as long as the
operator leaves it, then calls `getTimetable().remove(index)` - a structural mutation of a plain
`LinkedList`, with no lock, while `addTimetableEntry` (`synchronized`, `Layout.java:809`) appends
from locomotive threads whenever capture is on. `repaintTimetable`'s own comment (`:26980`)
already names this exact window as real: "Three of those callers hold a modal dialog open between
the check and the snapshot, so the window is as wide as the operator leaves it" - that fix moved
the *snapshot* off the EDT and left the *remove* unguarded.

**Concrete scenario:** operator right-clicks a row to delete it; while the confirm sits open,
they (or a route trigger) dispatch a locomotive with capture on; entries append; Yes now removes
against a stale index, and in the worst interleaving corrupts the list a running timetable is
reading.

### C3 - `refreshReturnHomeButton` materialises the Layout its sibling swore not to bring into being

**Files:** `src/org/traincontrol/gui/TrainControlUI.java:23148`
(`this.model.getAutoLayout()`, unconditional) vs `refreshCoveredTrack` (`:6906`), which
deliberately asks `hasAutoLayout()` first, with a comment: "a question about what is covered must
not bring a railway into being to answer it." `MarklinControlStation.getAutoLayout()`
(`MarklinControlStation.java:908`) creates an empty `Layout` when there is none, and
`MarklinControlStation.java:3336` warns about exactly this. `refreshReturnHomeButton` runs at
window construction (`TrainControlUI.java:4144`), so `hasAutoLayout()` is true for the entire
session and the careful guard beside it is dead code from startup. Consequences today are benign
(an empty Layout answers with empty sets); recorded because the two methods were written the same
evening, in the same commit pair, and one of them already breaks the other's stated rule.

### C4 - `moveLocomotive` and friends on the EDT at the placement doors

**Files:** `src/org/traincontrol/gui/TrainControlUI.java:6552, 6567` (paste/cut),
`LayoutRightclickAutonomyMenu.java:628, 978`, `GraphLocAssign.java:335` - all call
`Layout.moveLocomotive` (`synchronized`, `Layout.java:6991`) from click handlers.
`TrainControlUI.java:22557` (`toJSON`, `synchronized`, on the legacy JSON reload door) is the same
family. Each is a quick call that only hurts under contention: a Ctrl+V while a dispatch is
configuring stalls the UI for the remainder of the hold. Lesser siblings of A1; listed so the B2
sweep has its full inventory.

### C5 - Pending destination turns do not survive a setup rebuild

**Files:** `Layout.java:686` (`reversedOnArrival` lives on the Layout object);
`TrainControlUI.rebuildRunningLayoutFromSetup` (`:6051-6165`) carries placements and
`arrivedFrom` across the rebuild (`whereTheTrainsAre`/`putTheTrainsBack`) but not the un-drained
reversals; the viewer's `load` replaces the Layout wholesale and the map dies with it.

RGD-C7 went to some trouble to make an unwritable turn survive ("NOT WRITTEN, SO NOT FORGOTTEN",
`TrainControlUI.java:6693`) - restored into the map, retried at every idle refresh. A rebuild
defeats that: any right-click setup gesture on the viewer (set a home, a direction) between the
turn being recorded and its successful write discards it silently. The window is small in the
happy path (W7-A2 makes runs announce, and the drain follows within one refresh), but it is
exactly the retry case RGD-C7 preserved the map for - a turn that *declined* to write once now has
its retries destroyed by the next unrelated edit. The next dispatch is then offered paths for the
wrong heading, which is the symptom of OB-189/W7-A2 back through a third door. This is the
`new-field-needs-the-copy-constructor` shape: `arrivedFrom` was added to the carry in OB-183's
fix; `reversedOnArrival` was not.

## D - minor

### D1 - The findings store rows for the three new reviews are placeholders

`docs/manual-tests/findings.tsv` gained `D2-*`, `W7-*`, and `IND9X-*` rows with status `-`
(commit `759aabda`). Deliberate (the reviews are open), but note the IND9X rows cite
`IND-independent-review.md` while the prefix registered is `IND9X` - a reader grepping `IND-`
against the store will not find them. Worth one line in the catalogue conventions.

### D2 - `stationLabel` reads the session off the field while `updateStationLabels` builds it

`updateStationLabels` (`TrainControlUI.java:5500`) calls `getAutonomySession()` - the lazy
builder that "parses every page, can write to disk and can raise a dialog" (SV-B2, quoted in
`stationLabel`'s javadoc at `:26866`, which deliberately reads the field instead) - from inside
`updateVisiblePoints`, i.e. on the EDT while holding the window's monitor. When the session
already exists this is free; on the one path where it does not, the diagram refresh can parse
pages and raise a dialog from inside a `synchronized` window method. Same inconsistent-sibling
shape as C3.

## Why defects are being reintroduced

The three days answer Adam's worry with unusual precision, because one of them contains the whole
cycle inside a single day. At 13:35 (`759aabda`) a refresh call was added to the window's
callback with a comment *proving* it safe: "`getPoints()` is deliberately unsynchronized, so this
takes no Layout monitor." That sentence was false when it was written - the method's other half
reached `edgesCoveredByStandingTrains` - and at 23:18 (`e4f8f577`) it came back as Adam's
every-run UI freeze. The corrected commit then swept three more doors (23:45), and this review,
looking the next morning with the same flashlight, found four more (A1, B1, C1, C4) - including
one *in the same popup menu the second round fixed*. Nothing about that sequence is carelessness;
every commit in it is measured, tested, and documented beyond most codebases' standards. The
mechanism is structural, and it is two-fold:

**First: rules here live at call sites, so every rule has as many copies as there are doors, and
a fix is a sweep that stops when the reviewer's list runs out.** The monitor rule, the
tier-of-enforcement rule, the who-wins-a-rebuild rule, the tell-the-graph-on-idle rule - each
exists only as the set of places that honour it. The codebase knows this about itself: this
window's fixes explicitly reached for one-mechanism designs (`announceRunFinished` instead of a
fourth pasted refresh; `placementsJustEdited` as a *required parameter* so the compiler visits
every placement door; the popup's single private constructor). Where a seam like that was built,
the regressions have actually stopped - nobody has re-broken run-end announcement, rebuild
provenance, or the covered marks since. Where the rule is still per-door prose - the EDT/monitor
rule above all - it regresses on schedule, because the next door is written by someone reading a
different file.

**Second: the tripwires pin instances, not rules, so green is quietly narrower than it reads.**
The ASKS grep pins one method name against a rule its own failure message states generally; the
editor-surface guard had to be taught `annotationsChanged` the day the light door became legal;
`b9010b57` is the extreme case - a headline number ("every square builds to one copy") cited in
three documents, guarded by a test that did not exist, and false by 30 squares of 58 when finally
measured, because the fixture it was measured on was silently unwired. The countervailing force
is also visible and is the most encouraging thing in the window: the tripwire that *disagreed
with the number it was cited for* got committed anyway, the census that disagreed with
behaviour.md's published figure got committed as a battery test, and a proposed narrowing was
measured against the real railway and REVERTED UNSHIPPED with the counter-examples kept as a
test (`e23f26d1`). That is convergence behaviour, applied to numbers and rules one at a time.

So my honest read: the codebase **is** converging, at the rate seams are built, and no faster.
Roughly a third of these ~160 commits are corrections to the previous two days, and five are
corrections of corrections - but almost all of them terminate in a mechanism (an announcement, a
parameter, a committed census, a ratchet with an exact count) rather than in prose, and those
mechanisms have held. The place it is *not* converging is wherever a rule still has to be
remembered per door, and the biggest such rule is the EDT/monitor one: three findings in this
review are the same sentence at different line numbers. One general tripwire (B2) converts that
whole class from "swept when reported" to "refused at commit time", the same way
`placementChanged`'s parameter did for placements. I would spend the next tooling hour there, and
not on another per-door fix.

## What this pass could not check

- **Live behaviour.** Nothing here drove a real window over a dispatch; A1/B1/C4 freezes are
  established from the same measured mechanism as OB-192 (monitor identity, hold durations from
  `Layout.java`'s own comments), not re-measured by me. A red-first test for A1 would hold the
  monitor from a helper thread and build the popup on the EDT, exactly as
  `testTheReturnHomeMenuDoesNotWaitOnTheRailway` does for the item beside it - I wrote no new
  tests, per this review's constraints.
- **The full battery.** The harness was contended by two other reviews throughout; I ran two
  classes and two mutations. The battery figure (159 green, `b1795351`) is theirs, not verified
  here.
- **Commits before 2026-09-06**, the translations touched by `c22c9d90`/`cac9e29f` (eight
  bundles, unchecked for `\uXXXX` escaping), the binary `triage.db` changes, and the checked-in
  layout library's fidelity to Adam's railway (`ba86e434`).
- **Concurrent-review interference in general** - the W7B incident at the top was caught because
  I happened to run `git diff` at the right moment; my own two test runs compiled a tree that
  briefly contained another review's mutation (my results were re-verified clean afterwards), and
  there is no mechanism that would have caught a third review's mutation landing during a battery.
