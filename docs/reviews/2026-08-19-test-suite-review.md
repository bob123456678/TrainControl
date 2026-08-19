# Test-suite review: semantic coverage of train operations - 2026-08-19

**Prefix for citing this document: `TS`.**

**Version reviewed: `7e9b299` (`autonomy-diagram-r0` HEAD), 2026-08-19**, from a detached worktree.
**Scope:** the whole test corpus - 51 classes, ~600 tests, ~22,000 lines - read for two commissioned
questions: does the suite cover the **semantics of train operations** (autonomy, routing,
multi-units - what a train actually does, not what a structure contains), and are there
**self-fulfilling tests** among the generated ones - tests whose oracle is the code under test,
whose corpus is narrower than their claim, or which cannot fail. Method: corpus-wide sweeps
(randomness, assert density, silent catches, file oracles, pinned constants), an
operation-surface-to-test map built from the model, and line-by-line reads of the suites the map or
a sweep flagged - with the newest, never-audited classes (`testRouteCommandParity`,
`testCommandRow`, `testConditionRows`, `testMockCentralStation`, `testBusyDialogInteraction`,
`testTimetableOnDerivedGraph`) read in full, since every earlier test audit predates them. Nothing
was compiled or run.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---|---|---|
| TS-B1 | The route-command parity suite claims "every constructor RouteCommand offers is exercised"; it exercises **7 of 12** kinds, carries **no delay-bearing command**, and its oracle - `line -> parse -> line` stability - is structurally blind to any field `toLine` fails to write. `testCommandRow`'s round-trip shares the hand-built corpus and the delay omission. The new route editor "rests on" this parity by the suite's own words | B | Open |
| TS-C1 | No automated test drives a train through the runtime reversal mechanics - the mid-path stop-flip-resume at a reversing point, or a terminus arrival with the direction flip asserted - and the one end-to-end that could (`testTimetableOnDerivedGraph`) excludes termini and reversing points by design. The same class of gap covers pre-arrival speed reduction, speed multipliers in motion, the `maxActiveTrains` cap, non-atomic progressive unlocking, departure/arrival function hooks, and a multi-unit consist under dispatch - all exercised only at rest, by parse, or manually | C | Open |
| TS-C2 | Three randomised suites use an unseeded `Random` - `testRoutes`, `testReturnHomeOnRealLayout`, `testTimetableOnDerivedGraph` - against the README's own rule ("fixed seeds and the seed in the failure message"), so their rare failures are unreproducible. `testLayoutBfsEquivalence` shows the compliant shape in the same corpus | C | Open |
| TS-C3 | Six of `testRouteInventory`'s eight tests silently `return` (green) when their input bundle is absent, so a pass can mean "did not run"; the suite's own neighbours use `SkipException`, which reports honestly | C | Open |
| TS-D1 | Clean checks: the external-oracle and differential suites verified methodologically sound, the discriminating fixtures confirmed discriminating, and the coverage that is strong enumerated as such | - | Recorded |
| TS-D2 | Reconciliation with the prior test audits, and what this pass adds | - | Recorded |

No A findings. The corpus is, on the whole, unusually honest for its size - the two
characterisation suites declare themselves, the differential suite compares against a frozen
transcription rather than against itself, and the discriminating fixtures genuinely discriminate.
The B is in the newest, never-audited material, which is exactly where such a thing would survive.

---

## B - Medium

### TS-B1. The parity the new editor rests on is narrower than it says

[testRouteCommandParity.java](../../test/testRouteCommandParity.java) - the header: *"Every
constructor RouteCommand offers is exercised, so a kind added later without a matching parse is
caught here rather than in somebody's timetable"*; and *"this is the parity the new route editor
rests on"*. Against [RouteCommand.java](../../src/org/traincontrol/base/RouteCommand.java), which
offers twelve factory constructors.

Three defects in one suite, each of the self-fulfilling class this review was asked to hunt:

1. **The corpus is 7 of 12 kinds.** Missing: `RouteCommandRoute` (a route triggering a route),
   `RouteCommandAutoLocomotive` (the term every route **condition** is built from - and conditions
   are exactly what the new editor's `ConditionRows` manipulates), `RouteCommandAutonomyLightsOn`,
   `RouteCommandLightsOn`. The suite's own `kindOf` helper names route and autoloc kinds it never
   feeds through.
2. **No command carries a delay.** Delays are serialized by at least four distinct per-kind paths
   in `toLine` (accessory, feedback, speed, function), each with its own parse counterpart
   (`parts[3]`, `parts[4]`, `parts[2]` - the indices differ per kind), and none of them is
   exercised. The delay is precisely the optional trailing field the July `dedupKeyOf` work
   documented as the reason "everything but the last field" cannot identify a line.
3. **The oracle is one-sided.** `parsed.toLine(null)` equal to `original.toLine(null)` proves the
   text form is *stable*, not that it is *faithful*: a field `toLine` silently drops never appears
   in either string, and the comparison passes while the editor loses it - the exact failure the
   header promises to catch ("a route that changes when somebody opens it and presses Save").
   Comparing rebuilt commands field-wise against the originals - or asserting `equals`, which the
   delay-canonicalisation comment at `setDelay` shows was designed for this - closes the blindness.

`testCommandRow.testEveryEditableKindRoundTrips` shares the hand-built corpus and the delay
omission - though it earns credit for the sibling test that pins refusal of the kinds the editor
has no controls for, which is the correct half of that contract. `UH-B7` is this same class ("a
test asserts two fields, not an invariant") and was graded B; so is this. **Fix shape:** derive the
corpus from the kinds rather than by hand (the `kindOf` chain is already an enumeration begging to
be one), add a delayed variant of every kind that can carry one, and assert field equality rather
than line stability.

## C - Low

### TS-C1. The runtime is tested at rest; the reversal is tested nowhere automated

The operation-surface map against the corpus. What a train **does** while driving is thin
precisely where 3.0.0 is newest:

- **The reversal flip** ([Layout.java](../../src/org/traincontrol/automation/Layout.java) -
  stop, `switchDirection`, pause, resume at an intermediate reversing point; and the
  terminus/reversing flip on arrival) has no automated execution anywhere: `switchDirection` in
  tests is unit-level locomotive state, `testAutonomyDiagramReversal` is graph shape,
  and [testTimetableOnDerivedGraph.java](../../test/testTimetableOnDerivedGraph.java) excludes
  termini and reversing points from placement by recorded design. Backing into a berth is the
  headline operational novelty of this release, it is simulable with the machinery this same suite
  already uses, and today its only coverage is the manual test plan (which, to its credit, says so
  and lists the scenarios).
- **Also exercised only at rest or by parse:** `preArrivalSpeedReduction` and `maxActiveTrains`
  (input validation only - the runtime slowdown and the dispatch cap are never hit),
  `speedMultiplier` (store round-trips only - never applied in motion), non-atomic
  `atomicRoutes=false` progressive unlocking with train lengths (two uses, both about lock-edge
  release on a skipped edge), departure/arrival function hooks during a drive, and a multi-unit
  consist dispatched by autonomy (MU coverage is strong at the database and conflict level, and
  routes assert fan-out to members; a consist *driven* through a path is untested).

One end-to-end simulation that places a reversible locomotive, drives it through a may-turn square
into a must-turn berth, and asserts the arrival copy, the direction flips, and the departure back
out would cover the top of this list in a single test, on the fixtures
`testTimetableOnDerivedGraph` already builds.

### TS-C2. Three suites roll dice nobody can re-roll

`testRoutes` (route generator), `testReturnHomeOnRealLayout` (random placements and run lengths),
and `testTimetableOnDerivedGraph` (random placements) all use `new Random()` with no seed and no
seed in any failure message - against the README's own rule, written after this cycle deleted
exactly such a failure. `testLayoutBfsEquivalence` is the in-corpus counterexample: 120 fixed
seeds, deterministic properties only. The fix is mechanical: a seed chosen once per run, printed,
and settable from the environment.

### TS-C3. Green that means "did not run"

Six of [testRouteInventory.java](../../test/testRouteInventory.java)'s eight tests begin
`if (!bundle.isFile()) return;` - a silent pass on a missing input. The suite is an honest,
self-declared report harness ("a REPORT, not an assertion... What the routes ought to be is Adam's
to say") and that is a legitimate thing to keep in the tree; the defect is only that absence reads
as success. `SkipException`, which its own neighbours use for the same situation, reports the
truth in the runner's summary.

---

## TS-D1. Clean checks: where the suite is strong, verified rather than assumed

- **The external oracle is real.** `testAutonomyDiagramSampleLayout` compares the derived graph
  against the hand-authored 2.8.1 configuration - an artifact written by a person against a
  physical railway, independent of the code under test - with non-equality expectations stated and
  the right assertions behind them: every legacy sensor derived, every legacy connection still
  reachable, no two routes sharing physical track unlocked, arrival-side rules on real geometry,
  build determinism. Its header undersells it ("the assertions here only catch the reduction
  collapsing"); the lock-coverage and reachability gates are substantive.
- **The characterisation suites declare themselves.** `testAutonomyGroundTruth` pins the real
  layout's station-pair reachability and says in so many words that it detects change rather than
  proves correctness; the pinned file lives beside the configuration it describes. This is the
  honest form of the shape TS-B1 fails at.
- **The differential suite compares against a frozen past, not against itself.**
  `testLayoutBfsEquivalence` transcribes the pre-change `bfs` from the prior commit, compares only
  the deterministic properties (existence, length, sampled route sets), and documents why exact
  routes cannot be compared - the July control-experiment lesson, applied.
- **The discriminating fixtures discriminate.** `testEachRuleMeasuresWhatItSaysItMeasures` builds a
  graph where shortest-length and fewest-stations disagree, and shuts the via-station so the rules
  cannot be confused with nearer-destination preference - read in full, genuinely discriminating.
  `testAHomeOnASplitSquareIsEmittedOnce` guards its own vacuity by asserting the split happened.
- **The planner-runtime agreement is enforced, not hoped.** `HomeStaging.auditAgainstRuntime` is
  asserted zero in two staging tests, which is the teeth behind the model comment that the two
  halves "agree by coincidence rather than by construction".
- **Autonomy selection semantics are thoroughly covered**: 21 `pickPath` tests spanning priority
  banding, occupancy, exclusion, inactive, paused, reversing-station refusal (end and through),
  manual-vs-autonomous tiering, terminus drive-through refusal; block/split occupancy, reservation,
  protecting signals (incl. two stations sharing one signal), two-trains-one-square repair;
  timetable capture conventions, retry/abandon/graceful-stop; the reload fence; staging's 58 tests;
  and `testTimetableOnDerivedGraph`'s replay compares routes **edge by edge** precisely so a
  same-station-different-arrival-side substitution fails.
- **The pinned magic numbers are fixture-derived, not code-pinned**: parse counts (127 accessories,
  136 locomotives) characterise checked-in input files that do not change when the code does; the
  ground-truth counts belong to the declared characterisation suite.
- **Sweep residue that came back clean:** the low assert-density files are the report harness, the
  atomic-write suite (whose guard tests carry the assertions), and parse smoke tests;
  `testRubbishIsRefused`'s catch-and-ignore is correct by the `AssertionError`-vs-`Exception`
  hierarchy (an unexpected parse fails the test through the catch); the widespread `Thread.sleep`
  usage is simulation pacing with stated margins, and the one timing-sensitive runtime test
  (`testARedundantPowerOnKeepsTheRunningTime`) documents why its margins hold.

## TS-D2. Reconciliation

The `AD` cycle ran a test-audit pass ("does each test establish what its name claims") over the
diagram suites, and the post-fix round's `B7` (the parseAuto field test asserting two fields) is
this review's TS-B1 class, found earlier in older material - it remains open there and is carried,
not re-filed. This pass therefore concentrated on what no audit had read: the five test classes
added since the known-good tag, where TS-B1 was found, and the corpus-wide sweeps no prior pass
had run, which produced TS-C2 and TS-C3. The semantic-coverage map (TS-C1) is new work; no prior
document asked which *operations* the suite exercises in motion.

---

## Verdict

The suite's architecture is better than most of its size: real external oracles, declared
characterisation, frozen-past differentials, and discriminating fixtures - the self-fulfilling
shapes this review hunted are largely *absent* where the record already audited. What it found is
concentrated where nothing had looked: the newest parity suites overclaim their corpus behind a
one-sided oracle (TS-B1), and the runtime's motion semantics - above all the reversal flip that
defines this release - are covered manually but not by a single automated drive (TS-C1). TS-B1 is
worth fixing before the new route editor grows further on top of it; TS-C1's first test is cheap
on fixtures that already exist; TS-C2 and TS-C3 are mechanical. None of this blocks a build - but
the reversal drive test belongs in the suite before 3.0.0 calls the berth workflow done.
