# W7B — the week in the long view (2026-09-02 .. 2026-09-09)

**Status:** closed

*Every finding in this document was worked in the rounds of 2026-09-09 and 2026-09-10; a finding's state lives in `docs/manual-tests/triage.db` and its mirror `docs/manual-tests/findings.tsv`, which is where it has lived since the reviews folder was retired. Added 2026-09-10 (E8V-C4): `docs/reviews/README.md` says a document with no status line is open, and these seven were relying on that default.*

**Prefix for citing these findings elsewhere:** `W7B` (confirmed absent from `docs/manual-tests/findings.tsv` before writing).

Reviewer scope per the brief: all 307 commits of the past week, read by theme, with the last three days
and autonomy owned in depth by other reviewers. This pass looks for rules changed early and assumed
late, fixes that undid fixes, and decisions made twice differently.

---

## Scope and method

**Themes read deeply:**

1. **Reversal and direction policy across the week** — `ca0265f4` (09-06, deferred-until-idle) →
   `bc6120f1` (09-07, ignored-not-queued) → `41848af2`/`759aabda` (09-08, arrival turns and run-end
   doors), including `followDirectionChanges`, `reconcileFacingWhenIdle` and
   `test/ui/testADirectionChangeIsNotSwallowed.java` as they stand today.
2. **Run-end and announcement doors** — OB-189 (`41913ecd`), W7-A2 (`759aabda`), OB-192 both rounds
   (`e4f8f577`, `49a3aee4`); verified the current single-funnel shape in source.
3. **Length, covered track and tier fences** — the whole of `Layout.isPathClear` (lines ~2252-2660)
   as it stands after MT-262 (`716cf3f5`), the shared-metal clause (`d1934608`), the
   `isFullAutonomyRunning` split (`06d00a82`), and `measuredRoomAtTheBerth` (~7563).
4. **Placement vs rebuild** — MT-337 (`1728986e`) → D2-A1/W7-A1 (`74015c16`); enumerated every
   `placementChanged(...)` and `session.placeLocomotive(...)` call site myself and read the two
   diagram-menu doors in `LayoutRightclickAutonomyMenu.java`.
5. **Test-suite integrity** — the live-layout fixture migration (`ba86e434`, `e4f8f577`), the two
   moving ratchets (`ebcafcd5`, `1345220c`), and one mutation probe (details under A2).

**What I RAN** (via `one.sh`, serialized with the other reviewers' runs):
`core.testTheLengthGuardsOnTheRealLayout` (RED, see A1), `core.testAPastedTrainKeepsItsDirection`,
`core.testTheRoomRuleCensusOnTheRealLayout`, `regression.testTheTurnAtTheDestinationReachesTheDiagram`,
`regression.testTheHomeLabelIsDrawnOnce`, `core.testACompulsoryTurnIsNotAQuestion` (all green), and —
under the one mutation I made — `core.testATrainCoversTheTrackBehindIt`,
`core.testTwoRoutesShareOneSwitch`, `core.testAutonomyPathValidation` (all green, which is the
finding).

**What I mutated:** exactly one site, `src/org/traincontrol/automation/Layout.java:2526`
(`lyingAcross = onShared;` → `lyingAcross = null;`), restored exactly afterwards. `git status --short
src test` is empty as of writing, verified. Per the coordinator's instruction mid-review, no further
source or test mutations were made after that one; A2 below describes the follow-up mutation to run in
a controlled window instead.

**What I did NOT read:** the eight message bundles beyond spot checks; the OB-170 splash/foreground
thread (09-03, settled by experiment); FR-057/FR-061 window-stacking and caption-dropdown polish; the
triage tooling internals (`triagedb`, `catalog-findings.py`); route-editor internals — FR3-B1
(`015366b3`) and AC2-A1 (`2cef4211`) were verified from their commit messages and tests' existence
only; `LayoutEditor` drawing internals; `build.xml` beyond the battery entries. The 09-06 arrivedFrom
mechanics and 09-09 REV9/FR-065 work were skimmed for cross-week interaction only, since the
three-day and autonomy reviewers own them in depth.

One caveat on my mutation runs: another reviewer was using the harness (and, at one point, the tree)
concurrently. All three classes I ran under the mutation reported `Failures: 0, Skips: 0` with the
expected test counts, so I consider the result sound, but the controlled re-run A2 asks for would
remove any doubt.

---

## A - high

### W7B-A1 — The acceptance battery is red on a clean tree, because one live-fixture class was left behind by its own fix

**File:** `test/core/testTheLengthGuardsOnTheRealLayout.java:62` (opens
`support.LayoutSandbox.open(new File("cs2_sample_layout"))`) and `:617` (the assertion that fails).

**Mechanism.** The class pins where the 2-8-4 3505 SP is standing: `assertTrue(platform.getName()
.startsWith("BottomMainB"), ...)`. Its fixture is Adam's live railway folder — a sandbox COPY, so
nothing is written back, but a copy of the *working tree*, which drifts as he operates. His current
uncommitted changes move the 2-8-4 from tile `1 - Main:20,13` (BottomMainB, arrivedFrom W) to
`1 - Main:20,12` (BottomMainA, facing E) — verified by diffing
`cs2_sample_layout/config/autonomy/configuration-Main.json` against HEAD.

**Failure, observed not predicted.** Ran today on the unmodified tree:
`testBottomMainCIsBlockedByTheTailAtBottomMainB` fails with *"the 2-8-4 is at BottomMainA (eastbound)
rather than BottomMainB"* — 7 tests, 1 failure. Six siblings in the class pass.

**Why this is the long-view finding and not just a flaky test.** This exact failure mode was
diagnosed and fixed *this week*: `ba86e434` (09-09) created `test/layouts/live-snapshot` — a frozen
copy of the railway taken from commit `e6f4649c` — and its README says why: *"On 2026-09-09 three
broke in one morning."* `e4f8f577` then moved **seven** classes onto the snapshot. But **fourteen**
test classes still construct their sandbox from the live `cs2_sample_layout` folder
(`testTheLengthGuardsOnTheRealLayout`, `testTheRoomRuleCensusOnTheRealLayout`,
`testACompulsoryTurnIsNotAQuestion`, `testAPastedTrainKeepsItsDirection`,
`testEverySquareBuildsToTheCopiesTheSetupImplies`, `testTheAutoTierScopeMatchesTheRuntime`,
`testControlSNamesOnlyASensor`, `testOneChangeSticks`, `testTheDiagramIsNotRebuiltForAnArrow`,
`testTheGoldenLayoutHoldsTogether`, `testTheHomeLabelIsDrawnOnce`,
`testTheTurnAtTheDestinationReachesTheDiagram`, `testTheTurnRuleDoesNotChangeTheRealRailway`,
`testEveryLanguageFits`). Most of them place their own trains and survive (I ran five; green). This
one pins a *placement* it does not make, so it broke the first time Adam moved that train — which is
what operating a railway is.

**Concrete user scenario.** Adam finishes an operating session, runs the battery before accepting
v3.0.0, and gets a red class with no code defect behind it. A battery that is red for environmental
reasons trains everyone to read red as noise — the opposite of an acceptance test.

**Fix shape.** Either move the class to `Scenario.folderFor("live-snapshot")` like its seven
siblings (its lengths and `arrivedFrom` are already set by the test; only the placement is trusted),
or have it *place* the 2-8-4 at BottomMainB itself the way `testAPastedTrainKeepsItsDirection`
survives. And sweep the other thirteen: any of them that asserts against live *operating state*
(placements, homes, facings) rather than geometry is the same defect waiting for Adam's next session.

### W7B-A2 — The shared-metal fouling rule (an anti-collision rule, from Adam's own incident) has no behavioural test; its three pins are source greps that a rule-disabling mutation walks past

**File:** `src/org/traincontrol/automation/Layout.java:2497-2530` (the `for (Edge sharing :
e.getLockEdges())` sweep inside `isPathClear`); pins at `test/core/testHomeStaging.java:238,250` and
`test/regression/testEditorSurfaceRules.java:1508`.

**Mechanism.** On 09-07 Adam reported: EN57-203 *"is allowed to traverse a blocked/shaded switch (60)
... even though it should not be possible"*. The fix (`d1934608`, RGD-B2) refuses a path whose edge
shares metal — symmetric lock-edge partners — with an edge a standing train's tail covers. Coverage
is per EDGE and a switch's other arm is a different Edge, so without this sweep the covered-track rule
does not protect the very case Adam hit. This is collision-avoidance on the physical railway.

Every test that names this rule pins it as **source text**: `testHomeStaging` asserts
`layout.contains("if (!sharing.getLockEdges().contains(e)) continue;")`, `testEditorSurfaceRules`
asserts `layout.contains("for (Edge sharing : e.getLockEdges())")`. `testHomeStaging`'s own comment
explains why there is no behavioural pin: *"no hand-built fixture in this suite has a switch in it."*

**Proof, executed.** I changed line 2526 from `lyingAcross = onShared;` to `lyingAcross = null;` —
the rule fully disabled, every pinned string intact — and ran the three plausible catchers:
`core.testATrainCoversTheTrackBehindIt` (15 tests), `core.testTwoRoutesShareOneSwitch` (4),
`core.testAutonomyPathValidation` (15). **All green.** Mutation restored exactly; tree verified clean.

**Follow-up mutation for a controlled window** (per the coordinator's instruction I did not re-run
it): apply the same one-line change — `Layout.java:2526`, `lyingAcross = onShared;` →
`lyingAcross = null;` — and run the full battery. My prediction from the three targeted runs plus the
fixture-monoculture note is that nothing behavioural goes red; only a test that drives a train at a
switch's other arm while a long train's tail covers the switch can.

**Concrete user scenario.** Anyone "fixing" an over-refusal report by adjusting this sweep — the
symmetry clause has a documented false-positive case (two stations holding each other back), so such
a report is plausible — can break the fouling rule entirely and ship green. The regression reaches
Adam as EN57-203 crossing switch 60 again, discovered on the physical layout.

**The long view.** `open-questions.md` §2b already names this exact cause and remedy — *"One fixture
with a curve and a switch would have caught two of the six outright. That is the next piece of test
work worth doing, ahead of anything left in this section"* (written 09-07). Since that sentence,
three more switch-dependent rules landed (MT-262's unfenced room rule, `06d00a82`'s tier fence,
`e6f4649c`'s grey) and the fixture still does not exist. This is the highest-leverage single test
artifact the project can build before acceptance.

---

## B - medium

### W7B-B1 — At idle, a manual send is refused over covered track that the diagram shows as free; the ruling that bounded the grey was only ever asked about the running case

**Files:** `src/org/traincontrol/automation/Layout.java:2481` (the covered-track sweep in
`isPathClear` — unfenced, applies in every tier at every time) vs
`src/org/traincontrol/gui/TrainControlUI.java` / `LayoutLabel.paintCoveredMark` (the grey wash,
painted **only while autonomy is running** per `e6f4649c` and behaviour.md §5c).

**Mechanism.** The refusal and the drawing answer the same question on different conditions. With
nothing running: a long train's tail covers the edge behind it; a right-click manual send across that
edge is refused (`errorTrackCoveredByStandingTrain` — the sweep has no `isRunning` fence, correctly,
since it is the anti-collision rule §5 says every tier obeys); but the diagram paints no grey,
because the grey is fenced on `isAutonomyRunning`. The square looks exactly like free track — which
is the very complaint (*"a square the railway would not let a train onto looked exactly like free
track"*) that brought the grey back on 09-09 for the running case.

**Why I file it as a question, not a defect.** The bound is Adam's own sentence — *"can we just grey
out the tiles just like blocked edges **while autonomy is running**?"* — and behaviour.md §5c records
it faithfully. But that ruling was given while diagnosing a running-autonomy complaint; nobody has
asked him about the idle manual send, and the guard-and-affordance doctrine this codebase enforces
everywhere else (OB-057/OB-090) says the two should ask one question. The why-window does explain the
refusal after the fact, so the operator is not blind — only surprised.

**Concrete user scenario.** Railway idle. Adam right-clicks a locomotive, picks a destination two
squares past a long-parked train's tail. The menu offers it (destination filtering is tiered, not
covered-track-aware), the send is refused, and the diagram at the moment of the click showed nothing
grey anywhere. **Suggested disposition:** one question to Adam — "should the grey also show while
nothing is running?" — and one sentence in behaviour.md either way.

---

## C - low

### W7B-C1 — open-questions.md has not absorbed the week's two newest rulings, in the sections built to stop them being re-argued

**File:** `docs/reference/open-questions.md` — "Reversals / **Decided**" (~line 88) and "Routing
tiers / **Decided**" (~line 126).

**Mechanism.** The document's stated job is to record what Adam ruled *"so it stops being
re-argued"*. Two rulings that each **reversed** an implemented behaviour are in behaviour.md but not
here:

- *Reversals during a run are ignored, not queued* (Adam, 09-07, `bc6120f1`) — this inverted the
  09-06 fix `ca0265f4`, which had made mid-run reversals deferred-until-idle. Behaviour.md §3 has it;
  the Reversals Decided list does not. A future reader of `ca0265f4`'s commit message (which argues
  eloquently for deferral) with only open-questions.md open re-argues it from the losing side.
- *Occupancy restrictions are full autonomy's alone* (Adam, 09-09, `06d00a82`) — this question was
  answered **three different ways by three tiers** during the week before the ruling unified it
  (isPathClear fenced on `isAutoRunning`, the planner unfenced, Return Home caught in between).
  Behaviour.md §1 has it; the Routing-tiers Decided list stops at 09-07.

The document's own footer invites this filing: *"If this document and the code disagree, the code
wins and this is stale — say so."* Said.

---

## D - minor

### W7B-D1 — one.sh's shared output files let a reviewer read another reviewer's run as their own

**Files:** `docs/tools/one.sh` (writes `$TC_SCRATCH/one-run.txt`, `oneout/junitreports/`).

While diagnosing A1 I tailed `one-run.txt` after my `testTheLengthGuardsOnTheRealLayout` run and got
a transcript of `testTheGraphIsToldWhenARunEnds` fixtures ("W7A2 hand dispatch") — another agent's
run, or a stale file; `oneout/junitreports/` also accumulates reports (including `probe*` classes)
across runs and reviewers indefinitely. The lock serializes execution but not interpretation: a
reviewer grepping the log for their class's failure can find, or miss, someone else's. The junit XML
named for the class is the reliable artifact; a line in one.sh's usage text saying so — or a
per-invocation subfolder — would prevent the misread. (This is the `report-about-the-tool` shape:
I lost ten minutes to it today.)

### W7B-D2 — mixed line endings in Layout.java remain a live source of false guard failures

**File:** `src/org/traincontrol/automation/Layout.java` (git warns `LF will be replaced by CRLF` on
every touch; same for the cs2 layout files).

`d1934608` (09-07) records a guard — `testTheGreyEditButtonSaysWhyItIsGrey` — failing **falsely**
because an edit converted a region to CRLF and a `\n`-anchored source search matched nothing. The
underlying condition is unchanged: the file still has mixed endings, and the suite has since *grown*
its population of source-text pins (see A2 — they are the only pins some rules have). Every such pin
is one CRLF conversion away from a false red, or — worse, the A2 direction — a pattern that never
matched anything to begin with. A `.gitattributes` line or a one-off normalization would retire the
class of failure.

---

## Why defects are being reintroduced

Adam's worry is directionally right about the mechanism but, on this week's evidence, wrong about the
trend. The codebase **is** converging — behaviour.md sampled true against the code at every one of
the six rules I checked it on; open-questions.md audits its own drift honestly; the ratchets I
examined (window count 23→25→26, javadoc count, the sandbox census, the citation ratchet) each moved
only with a documented cause, which is ratchets *working*, and D2-A1's fix made the compiler enumerate
all seven call sites instead of trusting a hand sweep — the one mechanical door-census of the week,
and it held. But the *loop* that produces convergence is expensive, and it has two structural inputs
that keep manufacturing defects faster than any review round can retire them. First: **rulings arrive
incrementally and invert** — mid-run reversals went from swallowed-by-bug (pre-09-06) to
deferred-by-design (`ca0265f4`) to ignored-by-ruling (`bc6120f1`) in under 48 hours; the wash became
a line became line-plus-grey in three days; the occupancy fence moved twice. Code written faithfully
against Monday's rule is a defect by Friday with nobody touching it, and every inversion leaves
comments, tests and open-questions entries arguing for the losing side unless each is hunted down
(C1 is one that got away). Second: **the door census is manual.** The week's recurring A-shape — a
rule enforced at one door of several — appeared on 09-02 (SVN-B7/B10/B16), 09-06 (inactive
endpoints), 09-07/08 (OB-189's two-of-four run-end doors), 09-08/09 (OB-192's doors, twice), and
09-09 (the live-snapshot migration reaching 7 of 18 classes — my A1, found *in the fix for the
previous instance*). Each fix's sweep is a grep and a promise. The convergent moves this week were
the ones that removed the census instead of redoing it: a required parameter the compiler checks, one
predicate both sides ask (`measuredRoomAtTheBerth`, `heldBackBy`), one funnel every door passes
through. The divergent moves were seventeen-line comments pasted at two of four sites. The
highest-leverage investments before acceptance, in order: the switch-and-curve fixture that
open-questions.md has been asking for since 09-07 (A2 is what its absence costs — the anti-collision
rules are pinned by grep); finishing the live-snapshot migration (A1); and a bias, wherever a rule
gains a door, toward making the compiler or a single funnel do the enumeration.

---

## What this pass could not check

- **The full battery.** Forbidden by the shared-harness constraint; I ran nine classes. Other
  live-folder classes beyond the five I ran (A1's list) may also be red on Adam's current tree.
- **The A2 mutation against the whole suite.** I proved three candidate catchers blind to it; a
  battery-wide run under the described mutation (to be run by the coordinator in a controlled window)
  would make the vacuity claim exhaustive.
- **Anything requiring the physical railway or the CS2** — the OB-192 deadlock fix, the reversal
  wire-watching (`e23f26d1`), and the s88-driven paths are asserted by tests that simulate feedback;
  the manual-test checklist owns the rest.
- **The 09-06 arrivedFrom capture/restore mechanics and 09-09 REV9/FR-065 work in depth** — deferred
  to the three-day and autonomy reviewers by design; I checked only their cross-week joints.
- **Route-editor internals** (FR3-B1's or/and repair, AC2-A1's autonomy flag) — verified from commit
  messages and test names only.
- **The i18n bundles** — 8 files × ~50 changes this week; spot checks only, no key-by-key audit.
- **Concurrent-reviewer interference** — one other agent held the harness and briefly modified
  `Layout.java` during my window; my green results under mutation showed the expected test counts,
  but I cannot fully exclude cross-talk (see the one-run.txt note, D1).
