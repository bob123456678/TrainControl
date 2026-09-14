# The repairs to the two validation rounds of 2026-09-13, and one defect they turned up

**Status:** closed 2026-09-13

**Prefix:** SVZ

**Not SV2, SV3 or anything near them.** `SV2` was taken by the second validation of 2026-09-01, and
the round-two document of today declared it anyway - three of its numbers landed on findings already
cited from `HomeStaging.java` and `docs/manual-tests/README.md`. That document is now
[`2026-09-13-SVX-fix-validation-round-two.md`](2026-09-13-SVX-fix-validation-round-two.md) and its
header says so. The catalogue answers the question in one query:
`SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`.

**Covers** the repairs made to [`2026-09-13-SVV-fix-validation.md`](2026-09-13-SVV-fix-validation.md)
and [`2026-09-13-SVX-fix-validation-round-two.md`](2026-09-13-SVX-fix-validation-round-two.md), and the
one defect found while building a fixture those rounds had asked for. Adam capped the validation at two
rounds, so **nothing here has been validated by a reviewer** - it is held by its own claims, each seen
red first, and by the battery.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| SVZ-B1 | fixed | `Layout.walkStandingTrains` - the first hop took whichever copy of the rail sorted first |

### SVZ-B1 - the square a train is standing on is claimed at some stations and not at others

**Found by building the fixture SVV-C3 asked for.** A probe over every point on the frozen railway,
looking for a standing square with a genuine second hop behind it, showed TopMainR0Park claiming
`[4,4 4,3]` for a train standing on `4,5` - the two tiles behind it, and not the tile it is on.
`testTheAllowanceDoesNotAbsorbTheTrain` asks for exactly that square among the claims at TunnelLongPark
and passes.

**Why the two differ.** A piece of rail is two `Edge` objects, one per direction, and at a berth both
can report the same entry side. `getNeighborsAndIncoming` lists outgoing edges before incoming ones, so
where an outgoing copy exists it won the first hop - and an edge's places are the path plus the square
it ARRIVES at, so that copy's places are the track behind and never the berth. TunnelLongPark has no
outgoing copy answering to its arrival side, which is the only reason the claim there was true.

**The consequence.** A square with a train on it that nobody has claimed is a square another train can
be routed over: `isPathClear` narrows a shared edge to the places the tail actually covers, and the one
under the train was not among them. It also made the allowance rule invisible at those squares - the
skip only applies to a copy that arrives here - so the two budgets were correct and reading the wrong
edge.

**The fix.** The first hop prefers the copy that ends at the standing square, which is the one the
train arrived along and the one `arrivedFrom` records. The other copy is still taken when there is no
arriving one - a square a train has been turned on - because a tail fouls a rail whichever way traffic
runs, and half an answer beats none.

**Pinned by** `core.testAStationsSizeIsAnAllowance.testTheSquareUnderTheTrainIsClaimedWhereverItStands`,
written against the unfixed walk and red there. Eight classes covering the tail walk, the length guards
and the shading were run against the fix and are green.

---

## C - narrow, latent, or a claim that cannot fail

| id | status | where |
|---|---|---|
| SVZ-C1 | open | `Layout.walkStandingTrains` and `whyABerthCannotHoldIt` still read "the last place" positionally |

### SVZ-C1 - two rules take the last place as the arrival square and neither checks it

SVX-C9 closed the way a malformed file could shift that element, by refusing a places list with a hole
in it. What it did not do is make either rule ask whether the last id really is the square the edge
ends at - `Edge` carries places as ids and `Point` does not know its tile key, so there is nothing to
compare against inside `Layout`. Recorded rather than fixed: the loader is now the only way a list can
be short, and it refuses those.

---

## What this round repaired

From [`SVV`](2026-09-13-SVV-fix-validation.md):

- **SVV-C2** - `ManualReversalPrompt.forJourney` names the may-turn dead end, where the two halves of
  the rule disagree and the outcome is still right.
- **SVV-C3** - `testTheTailReachesPastTheFirstEdgeBehindIt`, on TopMainR1. The first attempt stood a
  long train at TunnelLongPark and was red for a reason about the railway: the run into it ends where
  three roads meet, and the fork rule stops the tail there whatever the budget says.
- **SVV-C4** - recorded as unpinnable. `hasAWayThrough` is private, both callers pass a copy they have
  already established is a turning copy, and no behaviour can tell the change from its predecessor.
- **SVV-C8** - the berth javadoc and `Automation.md`, under SVX-C1.
- **SVV-C9** - points 2 and 3, under SVX-C6.

From [`SVX`](2026-09-13-SVX-fix-validation-round-two.md): B1, B2, C1, C2, C3, C4, C5, C7, C8, C9 fixed;
C6 partly - `waitForDialog` still matches any showing dialog.

## What this pass did NOT cover

- **Nobody reviewed these repairs.** Two validation rounds were the instruction and both are spent. The
  first-hop change above is the one to look at hardest: it changes which edge the guard walks first at
  every square where both directions of a rail report the same entry side.
- **The berth rule's own first hop** was not given the same treatment. `whyABerthCannotHoldIt` is
  handed a path rather than choosing an edge, so it cannot pick the wrong copy - but its caller can,
  and the caller is a route the operator asked for rather than a walk.
- **`waitForDialog`** in the mapping-page class still disposes the first showing dialog it finds,
  whatever that dialog is.
- **The eight new bundle sentences** of this round and the thirteen before it are machine-written in
  seven languages Adam does not read back. MT-291's caveat stands.
