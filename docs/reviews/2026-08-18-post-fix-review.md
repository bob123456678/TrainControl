# Two reviews of the 18 August fixes, and their dispositions

Two independent reviewers were run against the day's work: one auditing whether each fix does what its
commit message claims, one hunting for regressions with a bias toward graph-dependent features.

Every load-bearing claim below was re-verified by hand before being acted on. Three were checked with a
command rather than by reading, and all three held.

---

## A - fixed on 18 August, after the reviews

| | Finding | Disposition |
|---|---|---|
| A1 | Return Home still applies the lock rule `2ab59d4` removed | **Fixed** |
| A2 | A protecting signal can be left GREEN with a train on the platform | **Fixed** |
| A3 | `LoadingSpinner.getPreferredSize` overrode both of its callers | **Fixed** |
| A4 | `clearBlockExcept` takes a train off the graph in silence | **Fixed** |
| A5 | `BusyDialog.run` hangs the application if ever called off the EDT | **Fixed** |

### A1. The planner kept a rule the runtime dropped

`HomeStaging.lockEdgesFree` refused an edge when the point one of its lock edges led to held another
locomotive - character for character the rule `2ab59d4` removed from `isPathClear`, and stated as such
in its own javadoc. So the planner became the **stricter** of the two halves, which is the worst way
round for them to disagree: Return Home refused arrangements the runtime would have driven, and said so
only as `NO_PLAN_FOUND` after exhausting its full 15-second budget. On this layout 54 of 92 edges carry
lock edges, so it is not a corner case.

Nothing replaces it. The runtime rule is "is another route holding this track", and a staging plan runs
**one train at a time**, so during a staging move no other route is running and the answer is always no.
A check that cannot fire is worse than no check, because it invites someone to make it fire.

This is the third time in one day a fix has been applied to one of two matching places - after DX6's
sibling copies and after `block`/`protectingSignal` in the JSON. The lesson is written down in
[[shared-primitive-has-many-contracts]] and was still not enough; what would have caught all three is
searching for the *rule* rather than the *symptom* before calling any of them done.

### A2. A signal left green under a standing train

`signalClaimed` was a field on `Point`, while `claimed` was computed from the whole square. A refresh on
one copy that saw the square claimed **through another copy** wrote `true` into its own memo while
standing empty, and nothing ever wrote `false` back, because the clearing transition was recorded on the
other copy. The next real arrival there matched its stale memo and sent no command at all.

The memo now hangs on the accessory, which is the thing being commanded - one signal, one aspect. That
also closes a case nothing handled before: the pairing UI lets two stations pick the same signal, and a
signal can only show one aspect, so it now stays red while **either** platform is occupied.

### A4. A displaced train now says so

The sweep is right, but a train taken off may now be nowhere on the graph - it keeps its place in the
run list and its home claim, while staging skips it and `pickPath` never dispatches it. That is the
correct outcome when somebody has just said a different train is standing there, and the wrong thing to
do in silence, because the copy written to is not always the copy the user was looking at.

---

## B - open, and accepted as accurate

| | Finding | Disposition |
|---|---|---|
| B1 | "Locks are symmetric, nothing is given up" is false for hand-authored graphs | **Open** - claim withdrawn, fix stands |
| B2 | The tile-settled counter is global, so one diagram waits on another's decodes | **Open** |
| B3 | `parseAuto` is a second, unguarded door to two trains on one square | **Open** |
| B4 | B6's twin `repaintAutoLocListFull` still searches on the EDT | **Open** |
| B5 | A4 of the arrivals review: only the tooltip landed | **Open**, row corrected |
| B6 | B4 of the audit: a third surface still buckets INFO as a warning | **Open** |
| B7 | `testEveryFieldParseAutoReadsIsAlsoWritten` asserts two fields, not an invariant | **Open** |
| B8 | Non-default route preferences enumerate every path to every station | **Open**, measure first |

**B1 is the one to read.** The commit message for `2ab59d4` asserts that nothing is given up because the
reducer locks every pair of edges sharing a tile in both directions. That is true of derived graphs and
false of everything else: `GraphEdgeEdit.applyLockEdges` writes one direction only. Measured on the
files that ship - the sample layout has **104 asymmetric lock relations of 118**, and the legacy graph
**290 of 312**. The fix itself is still right, and the old check was only a start-time guard rather than
a guarantee, but the justification does not cover hand-authored graphs and should not be relied on.

**B3** means the D4 fix does not repair state that is already wrong: a configuration saved while two
trains shared a square reinstates them on load.

---

## C - record corrections

Two commit messages describe work they do not contain, which is worth stating plainly rather than
quietly amending:

- **`68d637a`** claims the Start-button binding fix. That commit contains **one file**,
  `test/testRouteInventory.java`. The fix is real and is in the tree, but it landed in **`adf68bf`**,
  whose message does not mention it. Its other claim - "read the live setup instead of an exported
  bundle" - is also not in that diff.
- **`6be8999`** describes the test-runner change. The runner lives in a scratchpad outside the repo, so
  no commit contains it.

Also corrected: the `KNOWN GOOD: 43 test classes clean` block in the manual test plan was written before
it was discovered that two classes needing a display had never run at all.

---

## D - checked and clean

Worth recording, because these were the parts most likely to have broken and did not:

- **`TileGraph.findUndirectedPath`**. No legitimate path the rewrite fails to find. The walk state
  `(tile, entry side)` is strictly finer than `(tile)`, so it explores a superset; `getRoutes` unions
  across all switch states, so three-way turnouts, double slips and scissors offer every leg; the portal
  branch re-enters with no entry side, which reopens every route on the partner. The one behaviour
  change is that a route button entered from a side its inferred route does not carry is now a dead end
  where it previously leaked into the perpendicular track - which is the fix, not a regression.
- **`pickPath` banding**. Priority is genuinely respected - `ends` is shuffled then *stably* sorted, so
  bands are contiguous, and the band is updated for filtered ends too. RANDOM is behaviourally identical
  to the old code. The enumeration terminates.
- **`Point.toJSON` against `parseAuto`**. Every key the reader understands is written, on points, edges
  and at the top level. See B7 for why the test does not prove this.
- **The tile counter's arithmetic**. Every increment is paired with a decrement in a `finally`.
  See B2 for why the *scope* is still wrong.
- **`clearBlockExcept` and staging**. `moveLocomotive` refuses while the layout is running, and staging
  sets running before dispatching, so no sweep can land mid-plan.
