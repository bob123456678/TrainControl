# AUT — Autonomy as it ships: the graph, the locking, the tiers, and what tests can see

**Status:** closed

*Every finding in this document was worked in the rounds of 2026-09-09 and 2026-09-10; a finding's state lives in `docs/manual-tests/triage.db` and its mirror `docs/manual-tests/findings.tsv`, which is where it has lived since the reviews folder was retired. Added 2026-09-10 (E8V-C4): `docs/reviews/README.md` says a document with no status line is open, and these seven were relying on that default.*

**Prefix: `AUT9`** (confirmed free in `docs/manual-tests/findings.tsv` — no `AUT9*` and no `AUT*` rows exist).

Independent acceptance review of the autonomy feature on branch `autonomy-diagram-r0`, 2026-09-09.
Scoped to autonomy **as it now stands**, not to any diff. Two other reviewers are reading the
last-3-day and last-week diffs; the reversal-mechanics reviewer (REV) and the whole-application
reviewer (IND) filed the same day. Where my reading overlaps theirs I say so and do not re-file.

---

## Scope and method

**Read in full:** `docs/reference/behaviour.md` (all 864 lines), `docs/reference/open-questions.md`,
`test/README.md`, `test/support/Scenario.java`, and the autonomy core — `Layout.isPathClear`,
`configureAndLockPath`, `executePath`/`executePathInternal`, `pickPath`, `unlockPath`,
`shouldReverseAt`, `edgesCoveredByStandingTrains`, `measuredRoomAtTheBerth`, `whyTooLongForTheBerth`,
`isSendableDestination`, `barredFromAutonomy`/`isChoosableByAutonomy`/`isOfferableToOperator`,
`isFullAutonomyRunning`, `HomeStaging` (snapshot, plan, canEnter, firstClearRoute), and the surfaces
that configure/show autonomy — `LayoutRightclickAutonomyMenu`, `HomeLocomotiveMenu`,
`AutoLocomotiveStatus`, `TrainControlUI.refreshReturnHomeButton`, `ManualReversalPrompt`.

**Ran** (via `docs/tools/one.sh`, one at a time, waiting out the concurrency guard):
- `regression.testTheDiagramRefreshDoesNotWaitOnTheRailway` — the OB-192 deadlock suite. **5/5 green.**
- `core.testATrainCoversTheTrackBehindIt` (15), `core.testTwoRoutesShareOneSwitch` (4),
  `core.testTheLengthGuardsOnTheRealLayout` (7), `core.testAutoLayout` (29) — see the mutation below.

**Mutated** (then reverted exactly; `git diff` clean of my edits — see the hazard note):
- Disabled the **shared-metal covered-track refusal** in `Layout.isPathClear` (the
  `lyingAcross == null` lock-edge scan, ~`Layout.java:2498`) by gating it off.
  `testATrainCoversTheTrackBehindIt` and `testTwoRoutesShareOneSwitch` **stayed green** — see B2.
  (`testTheLengthGuardsOnTheRealLayout`'s one failure is **independent** of this mutation; its cause
  is B1.)

**A live hazard I hit, worth recording — now closed by ruling:** at least one other review agent was
mutating the **same shared working tree** during my pass (`W7B` set `lyingAcross = null` on the very
rule I was mutating; a `D3` `if (true)` appeared and vanished in `TrainControlUI`), while Adam builds
from this tree in NetBeans. The coordinator has since directed that **no reviewer edits `src/` or
`test/` again, even temporarily**; my one mutation predates that directive, was reverted within
minutes, and no further source edits were made after it. Under the new protocol, a suspected-vacuous
test is to be argued by **describing** the exact mutation (file, line, change) for Adam to run in a
controlled window — B2 below is written that way. My shared-metal result is still valid despite the
overlap: both agents' mutations disabled the same rule, so "the dedicated tests stayed green" holds
either way.

**Final state, confirmed as directed:** `git status --short src test` is **empty** — no file under
`src/` or `test/` differs from HEAD as this report is finished.

**Deliberately did NOT check** (covered elsewhere or out of scope): the painted diagram — the orange
line, the grey wash, the banner height (IND A1/D7, REV; and I cannot see pixels the suite cannot). The
reversal idle-drain / `reversedOnArrival` mechanics (REV A1/B1/B3 own these in depth). Route-conflict
`execRoute` skipping (IND B2). I read these for consistency but re-filing would duplicate.

---

## A - high

**None.** The safety-critical autonomy paths I examined are sound and, with one exception (B2), tested:
the OB-192 deadlock is genuinely fixed and pinned by a measured bounded-latency test plus a source
tripwire; two trains cannot be routed into one block (occupancy, whole-block, lock-held, covered-track
and shared-metal all refuse, and `configureAndLockPath` claims atomically under the monitor); locks are
released on every failure path including the lock-phase throw; the length guards run at every
destination in every tier. I could not make autonomy move a train unsafely or silently lose an
operator's setup in the time I had. The items below are real but none is a live collision or data-loss.

---

## B - medium

### B1 — A battery test reads the operator's LIVE railway and is RED right now because a train moved

`test/core/testTheLengthGuardsOnTheRealLayout.java:62` opens `new File("cs2_sample_layout")` — Adam's
**real, mutable** railway (through a `LayoutSandbox` copy, so nothing is written to it, but its current
*state* is read). `testBottomMainCIsBlockedByTheTailAtBottomMainB` (`:593`) then hard-asserts at
`:617`:

```
assertTrue(platform.getName().startsWith("BottomMainB"),
    "the 2-8-4 is at " + platform.getName() + " rather than BottomMainB");
```

i.e. it requires "2-8-4 3505 SP" to be **standing at BottomMainB** in the live setup. It is not:
`cs2_sample_layout/config/autonomy/setup.json` (shown modified in `git status` at session start) now
places it at **BottomMainA (eastbound)**, so the test fails its own precondition — I reproduced it
**twice**, in a 4-class run and in isolation, 1 failure of 7 each time. The failure is upstream of any
coverage computation, so it is **not** caused by the concurrent W7B mutation on
`edgesCoveredByStandingTrains`.

**Mechanism/impact.** The tail-blocking behaviour the test means to prove still works — the test simply
can't see it because its ground truth moved under it. But the battery is Adam's acceptance gate, and
this makes "green" a function of **where his trains happen to be parked**: it flips red/green as he
operates. `test/README.md`'s own fixture survey (MON-C17–C21) and the new `test/layouts/live-snapshot`
— *"the ground truth doesn't move underneath us… the base for mutations"* — exist precisely to end
this, and the sibling classes (`testATrainCoversTheTrackBehindIt`, `testTwoRoutesShareOneSwitch`) have
already migrated. This class (and its `setUp` opening `cs2_sample_layout`) is the one that did not.

**Fix I'd insist on:** rebuild this class on `Scenario.open("live-snapshot")` (frozen) and **place the
2-8-4 itself** (`point.setLocomotive`) exactly as the doctrine says, instead of asserting where the
live railway left it. This is the single concrete red I found, and it is red today.

### B2 — The shared-metal covered-track refusal is a live anti-collision rule with no red-on-mutation test

`Layout.isPathClear` refuses a path not only over an edge a standing train's tail directly covers, but
over any edge that **shares metal** (a lock edge, symmetric) with a covered one — the `lyingAcross ==
null` fallback at ~`Layout.java:2498–2530`. Its own comment records the real case it exists for:
EN57-203 *"allowed to traverse a blocked/shaded switch (60)… even though it should not be possible"* —
a train lying across one pair of a switch's arms must foul the other pair, which is a **different Edge**
never in the covered set.

I disabled that branch and ran the two dedicated covered-track classes: **both stayed fully green**
(`testATrainCoversTheTrackBehindIt` 15/15, `testTwoRoutesShareOneSwitch` 4/4).
`testAPathOverCoveredTrackIsRefused` only exercises the **direct** hit (`coveredTrack.get(e) != null`),
and `testTwoRoutesShareOneSwitch` exercises lock edges via a **route hold** (`isLockHeld`), not via a
**standing train's tail on shared metal**. So the specific branch that fixed the switch-60 collision is
covered by nothing that turns red when it is removed — on exactly the shape (`single-switch`) that now
exists to test it.

*Caveat:* W7B was mutating this same rule during my pass, so W7B may already be writing this test. If
so, this is independent corroboration; if not, it is the one untested safety rule I found. Either way
the gap is real as of the tree I ran against.

**The mutation to re-run in a controlled window** (per the no-tree-edits ruling — I have already run
it once and reverted; re-running confirms on a pristine tree):

- File: `src/org/traincontrol/automation/Layout.java`, in `isPathClear`, at the line
  `if (lyingAcross == null)` immediately above the `for (Edge sharing : e.getLockEdges())` loop
  (~line 2498).
- Change: `if (lyingAcross == null)` → `if (lyingAcross == null && Boolean.parseBoolean("false"))`
  (a compile-proof way to make the branch dead; a plain `false` triggers javac's unreachable-code
  pruning warnings in some shapes, the parse call does not).
- Expectation if the gap is real: `core.testATrainCoversTheTrackBehindIt` (15 tests),
  `core.testTwoRoutesShareOneSwitch` (4) and `core.testAutoLayout` (29) all stay green — which is
  what I measured. A test that closes the gap should go red under exactly this change.
- Revert by restoring the original line verbatim.

---

## C - low

### C1 — `maxActiveTrains` (the concurrency cap) is enforced only under `isAutoRunning`, and is in no reference doc

`Layout.isPathClear:2253` caps concurrent trains only when `this.isAutoRunning()`. A **hand** dispatch
(right-click menu, commands tab) is not subject to the cap. That may well be intended — the cap is an
autonomy-shaping tool, like the FR-001 occupancy restriction that §1 deliberately fences to full
autonomy — but unlike FR-001 it is written **nowhere** in `behaviour.md`, so there is no statement for
Adam to accept or reject. The tier treatment (and whether it should be `isFullAutonomyRunning` like
FR-001, not `isAutoRunning`) is an open question the document does not pose. One sentence in §1's tier
table would close it.

### C2 — The full-autonomy vs timetable tier split is correct and well tested; `behaviour.md` §1 documents it, but the enforcement point is easy to regress

Positive finding, logged so the next reader doesn't re-open it: `isFullAutonomyRunning()` (`= running
&& !timetableExecuting`) correctly puts Return Home / timetables on the Manual side of the FR-001
occupancy restriction, matching §1, and `testHomeStaging.testAStagingRunIsNotRefusedByTheOccupancyRestriction`
pins **both** directions (timetable passes, full autonomy refuses) by reflection on the two flags. I
verified HomeStaging itself never consults `heldBackBy`/`blockedBy`, consistent with §6's *"does not
read the occupancy restrictions… neither when planning, nor when the run executes."* Code and document
agree here.

---

## D - minor

### D1 — `firstClearRoute` and `isPathClear` agree on lock edges only "by coincidence"; the comment says so but no test guards the coincidence

`HomeStaging.canEnter` (`:1229`) and its comment state plainly that the planner models occupancy
per-Point and *"the shared-sensor rule below happens to catch the same pairs… They agree by coincidence
rather than by construction, which is worth knowing before either rule is changed."* `auditAgainstRuntime`
compares the route **search** against `getPossiblePaths`, but nothing asserts the **lock-edge** halves
stay aligned if the runtime's lock model changes. Low because the planner is the stricter side (it only
ever over-refuses), so a drift here yields a NO_PLAN_FOUND, never an unsafe move — but the comment is
carrying the whole guarantee.

### D2 — `isOfferableToOperator` drops the terminus/reversibility question that `barredFromAutonomy` keeps; both are right, but the asymmetry is documented only in code

`isOfferableToOperator(end, loc)` (`:4393`) deliberately does **not** refuse a non-reversible loco a
terminus (the operator may back in by hand), while `barredFromAutonomy` (`:4436`) does for the
auto tier. This is correct per §3/§7 and the javadoc explains it at length, but `behaviour.md` §7's
"Path Type" section does not mention that the manual offer is *wider* than the auto check on exactly
this point. A reader reconciling §7 with the menu would not find it. Cosmetic/doc.

---

## Is autonomy acceptable?

**Close, with one must-fix before the battery can be called a clean acceptance gate.**

The engine is in good shape. The deadlock that froze every run (OB-192) is fixed and has the best test
in the suite behind it — bounded, measured against a free-cost baseline, with a source tripwire for the
next door. The collision defenses are layered and, for the paths I could drive, sound. The tier rules
(autonomy / manual / Return Home) are consistently enforced and, unusually for this area, the
full-autonomy-vs-timetable split is pinned in both directions. `behaviour.md` is an exceptionally
honest document and I found **no case where it actively contradicts the code** on an autonomy rule — the
disagreements other reviewers found are about the drawn diagram, which I did not audit.

What I would insist on first:

1. **B1** — it is red *now*. An acceptance battery that flips with the operator's train positions is not
   an acceptance battery. Migrate `testTheLengthGuardsOnTheRealLayout` to the frozen `live-snapshot` and
   place its loco itself. This is both the fix and a repeat of a rule the project already wrote down.
2. **B2** — add (or confirm W7B is adding) a `single-switch` test where a standing train's tail on one
   arm refuses a route over the *other* arm, so the switch-60 anti-collision rule fails loudly if it is
   ever removed.
3. The **one-tree, many-mutating-agents** hazard (method note) has already been closed by the
   coordinator's ruling — no reviewer edits `src/` or `test/` any more; described mutations are run
   by Adam in a controlled window. Nothing further to do beyond keeping that rule.

None of these is a live railway-safety bug. On the question Adam actually asked — *"we can't have
trivial bugs… the behavior needs to be documented and tested"* — autonomy is documented well and tested
well, with one currently-failing test and one untested safety branch standing between it and a green,
meaningful battery.

---

## What this pass could not check

- **Anything drawn.** The orange train line, the grey blocked-track wash, the banner's height floor/cap
  — I read the code and the comments but cannot see the pixels, and the suite's ability to see them is
  itself new and narrow (IND A1/D7 own this).
- **A real multi-train run on hardware.** Every run I exercised was simulated/headless or a monitor-race
  reproduction. Contention behaviour under real feedback latency (the `maxActiveTrains` race the cap
  closes, the non-atomic early-unlock) I verified only by reading and by the existing race test.
- **The reversal idle-drain end to end** (REV A1/B1). I confirmed the destination-turn write and the
  tier gating but did not re-derive the parity/`reconcileFacingWhenIdle` argument REV is auditing.
- **A clean-tree mutation baseline.** Because the working tree carried other agents' live mutations
  throughout, I could establish "the dedicated covered-track tests do not cover the shared-metal branch"
  (valid regardless) but could NOT get a pristine-tree confirmation that B1 is red on unmodified code —
  though B1's failure is provably upstream of every concurrent mutation.
- **Page-crossing reductions and reversible-locomotive-specific paths** — `test/README.md`'s C18/C19
  gaps are still open; no fixture exercises a portal link or a reversible-only reversal, so the thin
  side of `flipFacing`/turning-copy selection remains untested by anything I could run.
