# July 2026 review cycle - index

The entry point for a cycle that ran across seven documents and produced 105 findings - 52 in the main
review, then 53 more across six later passes.

Nothing here is authoritative: **each finding's status lives in the status table at the head of its own
section**, per the "one status, one location" rule in [README.md](README.md). This page exists to say
what was reviewed, by whom, what came of it, and what the cycle taught - none of which any single
document holds.

Written 2026-07-27, when the last open finding closed. The cycle is finished, so this is a snapshot that
should not need maintaining.

---

## Cross-document identifiers

Identifiers are per-document and several collide: `B1` names three different findings, `D1` names three,
`C1`-`C4` two each. Cite findings from other documents with the document prefix.

| Prefix | Document | Findings |
|---|---|---|
| `CR` | [2026-07-code-review.md](2026-07-code-review.md) | A1-A8, B1-B19, C1-C20, SR1-SR2, FR1-FR3 |
| `PC` | [2026-07-post-change-review.md](2026-07-post-change-review.md) | P1-P5 |
| `IND` | [2026-07-26-independent-review.md](2026-07-26-independent-review.md) | N1-N4, B1-B4, M1-M4, T1-T4, D1-D6 |
| `INT` | [2026-07-26-integration-review.md](2026-07-26-integration-review.md) | A1-A2, B1, D1-D2 |
| `FCR` | [2026-07-26-full-codebase-review.md](2026-07-26-full-codebase-review.md) | B1-B3, C1-C4, D1 |
| `RR` | [2026-07-27-regression-review.md](2026-07-27-regression-review.md) | C1-C5, D1 |
| `FP` | [2026-07-27-fresh-perspective-review.md](2026-07-27-fresh-perspective-review.md) | B1-B2, C1-C4, D1 |

So `INT-A1` is the mutable-hash-key finding, `FCR-B1` is the charset bug, and `IND-B1` is a deliberate
behaviour change - three unrelated things that would all be "B1" without the prefix.

---

## The passes, in order

**`CR` - the July code review.** The main pass, 47 findings (A8 / B19 / C20) plus two source-review and
three follow-up items. Broad: CAN protocol, CS2/CS3 parsing, routes, accessories, the autonomy graph,
the UI. All resolved. Three findings were withdrawn as mistaken, two of them after being "fixed".

**`PC` - the post-change review.** A self-review of everything `CR` had touched, looking for side
effects rather than new defects. Five findings, **none requiring action** - the most useful outcome a
review of one's own work can have, and worth recording precisely because it produced nothing.

**`IND` - the independent review.** An external pass over v2_7_2..HEAD, then three evaluation passes
re-verifying the fixes. Produced the N (new-code), B (deliberate behaviour), M (multi-unit), T
(timetable) and D (deep-dive) series. Also the cycle's most valuable artefact: a reviewer-error tally in
which the reviewer records their own wrong findings.

**`INT` - the integration review.** Asked what no per-finding pass could: now that every fix is in, does
any pair interact? Prompted by `IND-M2` turning out to be unreachable - if a finding could be wrong
about what the surrounding code permits, so could a fix. Found `INT-A1`, the root cause behind six
findings.

**`FCR` - the full codebase review.** A fresh pass over the parts the cycle had not deep-dived. Seven
findings, all fixed.

---

## What the cycle actually taught

Four patterns recurred often enough to be worth more than the individual fixes.

### A mutable hash key produced six findings before anyone fixed the cause

`MarklinLocomotive.hashCode` was built from name, address and decoder type - all mutable in place. Every
hash container holding a locomotive was one rename away from silently losing it.

`IND-M4` (delete), `INT-A1` (rename), `INT-A1` amended (address change), `IND-D6` (exclusions and run
list), `INT-A2` (Central Station sync), `INT-B1` (delete leaves exclusions) - each was found by someone
tripping over a *different* symptom, and each was fixed where it surfaced. Twice the new repair was
itself incomplete. Two of the six surfaced only because a test asserted its own precondition.

The root fix - identity `equals`/`hashCode` - was five lines, and made 181 lines of accumulated repairs
dead. It is recorded under "Standing item" in `IND`.

### The same mistake, five times: fixing one of several identical entrances

This is the cycle's signature error. A defect is found at one call site, fixed there, and the identical
call site next to it is left alone - so the defect survives with its report marked closed.

| # | The fix | The entrance it missed |
|---|---|---|
| 1 | `CR-C12` corrected the always-true `getAutoLayout() != null` in `deleteRoute` | `changeRouteId` and seven UI sites kept it - found later as `FCR-C1` |
| 2 | `INT-A1` wired the consist re-key into `renameLoc` | `changeLocAddress` re-keys identically and was not wired - found in the same finding's amendment |
| 3 | The route editor's edit branch dropped its `syncWithCS2` call | The same sync survived inside `layoutEditingComplete` - found much later as `FCR-B3` |
| 4 | The `FCR-B3` follow-up serialised `refreshLayouts` on a lock | `syncWithCS2` reaches the same clear-and-repopulate unlocked - `RR-C2` |
| 5 | `INT-D1` decided `setLinkedLocomotives` could stay unsynchronised | That decision was scoped to the multi-unit dialog; `INT-A2` then called it from an automatic sync - `RR-C1` |

Instances 4 and 5 were produced *while fixing earlier instances of the same pattern*. Number 4 was
introduced the day after number 3 was diagnosed.

A related shape, twice: a finding withdrawn because a guard made it unreachable, where another path
bypasses the guard. `IND-M2` was withdrawn on a UI guard, and `IND-D6` showed a rename defeats it;
`IND-D6` trigger B was withdrawn because renames are guarded, and `INT-A2` reached the same state
through an unguarded sync.

The rule this produced: verifying the function is not enough; the hazard lives in its call graph. The
count suggests the rule is necessary but not sufficient - what actually caught instances 1, 3 and 4 was
someone reading the fix afterwards, not the person writing it.

### Making something faster removed a guarantee nobody had written down

`FCR-B3` moved a Central Station sync off the EDT because it froze the UI for seconds. Correct fix -
but while it ran on the EDT it was accidentally mutually exclusive with every repaint, and nothing had
ever recorded that, because it was never a decision. Two defects followed: a repaint could land while
the layout database was empty mid-rebuild, and two rebuilds could overlap. Both were found only by a
pass that asked specifically what the single thread had been serialising for free.

### Reading found less than using and testing did

`INT-A1` was found while writing test coverage, not while reviewing. `FCR-B3` was found by the author
using the application. `IND-D5`'s recorded escape route was disproved by reading the guard it depended
on. Two defects were caught only because a test asserted the precondition that made it meaningful - a
`removeIf` that cannot remove a drifted key, and a fix wired into one re-keying path but not the other.

---

## Reviewer errors, consolidated

The cycle's calibration data. Both reviewers were wrong repeatedly, and in *the same way twice*.

| Where | The error |
|---|---|
| `CR` | Three findings withdrawn as mistaken, two after being "fixed" (`CR-B3`, `CR-C5`, `CR-C9`) |
| `CR` | An MFX-address assumption asserted confidently and corrected by the author; re-checking it surfaced a real eviction bug |
| `IND` | `M2`: inferred that no membership back-reference existed, from reading one class. It exists in the UI layer, computed rather than stored |
| `IND` | `D6` trigger B: inferred a predicate's meaning from its *name* (`isAutonomyRunning` vs `isAutoRunning`). Same error as `M2`, same document |
| `IND` | `D6` JSON bullet: claimed export carried a stale name; `toJSON` iterates, so it never did |
| `IND` | `N4`, `T1`, `T2`: right defect or right fix, wrong stated mechanism or trigger |
| `INT` | `A1`'s first fix repaired one of two re-keying paths, and used `removeIf`, which cannot remove a drifted key |
| `INT` | `D5`'s note recorded a recovery route (delete the locomotive) that the delete guard makes unreachable |

Two of these are the same mistake: **believing a method does what its name suggests**. It is now a rule
in [README.md](README.md).

---

## Decisions taken, not defects

Four items were closed by the author's decision rather than fixed, each with reasoning recorded:

- `CR-B6` - read-only lookups create accessories. Accepted as tolerable. *Later partly reversed:*
  `FCR-C2` removed the creation from display paths, leaving it only where creating on demand is
  intended.
- `CR-C6` - dead code with no callers, left as-is.
- `CR-C9` - the `lastStartTime` rename would change hardware command emission in the base class for no
  user-visible benefit.
- `IND-M3` - direction re-assert after a power cycle, accepted as-is.

---

## State

**Two items are open**, both in `FP` and both deliberately left: `FP-C3`, a 100-site mechanical cleanup,
and `FP-C4`, a dead validation block whose removal is a judgement about intent. Everything else across
the seven documents is fixed, withdrawn as mistaken, closed by decision, or informational.

Test classes added during the cycle: `testLayoutBfs`, `testLayoutBfsEquivalence`, `testLayoutPickPath`,
`testLayoutTimetable`, `testMessageBundles`, `testMultiUnitMembership`, `testLayoutReloadFence`,
`testLayoutRenameKeys`, `testImportRename`, `testRouteRoundTrip`.

Two areas remain untested and are named here so the gap is not mistaken for coverage: the editor flows
(`FCR-B3`, verified manually), and the charset round trip (`FCR-B1`, which would pass vacuously on a
UTF-8 JVM and needs a byte-level assertion).
