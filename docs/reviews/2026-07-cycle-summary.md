# July 2026 review cycle - index

The entry point for a cycle that ran across eight documents and produced 116 findings - 52 in the main
review, then 64 more across seven later passes.

Nothing here is authoritative: **each finding's status lives in the status table at the head of its own
section**, per the "one status, one location" rule in [README.md](README.md). This page exists to say
what was reviewed, by whom, what came of it, and what the cycle taught - none of which any single
document holds.

Written 2026-07-27. The cycle's review work is finished, but this is not a frozen snapshot: two findings
are open by the author's decision, two arrived after the reviews had closed, from writing tests
rather than from reading, and seven more were added later the same day by `PV` - five by the pass that
reviewed the fix commits the cycle ended on, and two by the validation of *its* fixes. All counts above
include them. Anything that reopens or adds a finding needs this page
updated with it.

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
| `FP` | [2026-07-27-fresh-perspective-review.md](2026-07-27-fresh-perspective-review.md) | B1-B3, C1-C6, D1 |
| `PV` | [2026-07-27-post-cycle-verification.md](2026-07-27-post-cycle-verification.md) | B1, C1-C6, D1 |

So `INT-A1` is the mutable-hash-key finding, `FCR-B1` is the charset bug, and `IND-B1` is a deliberate
behaviour change - three unrelated things that would all be "B1" without the prefix.

The letter normally encodes severity, but identifiers are fixed when first assigned and the documents
that cite them are not rewritten. Two findings diverge, and by coincidence both are `C4`: `FP-C4` was
raised to B once its cause was understood, and `PV-C4` when its "benign" conclusion proved wrong.
Both keep their identifiers. Each document's own status table carries the real severity.

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

**`RR` - the regression review.** An independent read of the v2_7_4..HEAD range, 72 commits and roughly
2,700 inserted source lines, hunk by hunk, asking what had regressed since v2_7_2. No A findings and no
B findings: nothing had regressed into wrong behaviour or data loss. But **all five C findings were
introduced by this cycle's own changes** - the sharpest evidence for the signature error below, and the
reason a cycle this size needs a pass that reviews the fixes rather than the code.

**`FP` - the fresh-perspective review.** Deliberately not another correctness sweep: it asked resource
lifecycle, cost per operation, unbounded growth, locale sensitivity, and what the database keys actually
are. The last question produced `FP-B1`. Nine findings and one clean-checks record; seven fixed, two
recorded and deferred. Two of the nine were found later and by a different route - writing the
invalid-input tests, not reading.

**`PV` - the post-cycle verification.** The fixes for `RR`'s and `FP`'s findings landed *after* `RR`
froze at `5e80c41`, so the cycle ended on four source commits nobody but their author had read. This
pass verified all twelve fixes against their writeups in the enforcing methods, traced the
identity-hash change across the serialization boundary (a question no earlier pass had asked), and
re-verified a sample of `RR-D1`. All twelve fixes verified correct. Five new findings, four of them in
or beside the cycle's own fixes: the one wrapped-Thread site the `FP-C3` matcher could not see
(`PV-C1`), a third entrance to the layout rebuild outside the `RR-C2` lock (`PV-C2`, the pattern's
sixth instance), the stale hash-drift comment the `9c5727e` cleanup missed (`PV-C3`), and the
Central-Station-side mirror of `FP-B1`'s precondition (`PV-C4`, benign end state - traced, not
assumed - and wrongly: see the error tally).

`PV-C1`..`C5` are now fixed, `PV-C4` at severity B after a second reader traced its delete branch
one step further. The pass also produced `PV-C5`, found while verifying the `PV` document itself: the
javadoc explaining the identity-hash fix - the cycle's most-cited comment - had lost its `*` prefix on
22 lines and nothing had noticed, because it still compiles.

The validation of those fixes (commit `3391cb9`) confirmed all five correct - including a deadlock
audit of the new `clearLayouts` lock scope - and produced two more findings. `PV-C6` is the residue of
`PV-C5`'s reformat, enumerated by the scan that writeup called for: sentence run-ons, nothing
structural - four of them, one more than the enumeration recorded. `PV-B1` is the one that matters: the
`FP-B1` and `PV-C4` refusals are each correct alone, but the proposals they let through still interact
*with each other* - a name swap performed on the Central Station generates two individually-valid
proposals whose sequential application deletes one locomotive of the pair. Severity B, and it turns the
family's three findings into one root statement: the rename list is generated against a database that
applying the list mutates.

The fix is the one place in the cycle where a finding's own proposed shape was improved on rather than
implemented. It suggested declining anything whose target was another proposal's source. Ordering them
instead costs no more code and saves the chain case entirely - the contested name is freed before it is
wanted, so nothing is deleted at all - leaving only true cycles to refuse.

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

### The same mistake, eight times: fixing one of several identical entrances

This is the cycle's signature error. A defect is found at one call site, fixed there, and the identical
call site next to it is left alone - so the defect survives with its report marked closed.

| # | The fix | The entrance it missed |
|---|---|---|
| 1 | `CR-C12` corrected the always-true `getAutoLayout() != null` in `deleteRoute` | `changeRouteId` and seven UI sites kept it - found later as `FCR-C1` |
| 2 | `INT-A1` wired the consist re-key into `renameLoc` | `changeLocAddress` re-keys identically and was not wired - found in the same finding's amendment |
| 3 | The route editor's edit branch dropped its `syncWithCS2` call | The same sync survived inside `layoutEditingComplete` - found much later as `FCR-B3` |
| 4 | The `FCR-B3` follow-up serialised `refreshLayouts` on a lock | `syncWithCS2` reaches the same clear-and-repopulate unlocked - `RR-C2` |
| 5 | `INT-D1` decided `setLinkedLocomotives` could stay unsynchronised | That decision was scoped to the multi-unit dialog; `INT-A2` then called it from an automatic sync - `RR-C1` |
| 6 | The `RR-C2` fix locked `syncLayoutsFromConfiguredSource` "so every entrance inherits it" | `switchCSLayout` clears the same database directly, on the EDT, without the lock - `PV-C2` |
| 7 | `FP-B1` indexed the LOCAL side by UID and refused ambiguous addresses | The parsed side went on being iterated ungrouped, so the Central Station's own duplicates still produced two proposals - `PV-C4` |
| 8 | `PV-C4` grouped the parsed side per address, symmetrically | The pairs still interact across addresses through the *names*: a swap or chain of renames on the Central Station deletes a locomotive - `PV-B1` |

Instances 4 and 5 were produced *while fixing earlier instances of the same pattern*. Number 4 was
introduced the day after number 3 was diagnosed. Number 6 is the older shape of instances 1-3 - the
entrance predates the fix, and the fix's own comment ("so every entrance inherits it") claims a
coverage it does not have; what made the missed entrance *matter* is `FCR-B3` moving refreshes off the
EDT, which removed the serialisation that had made it safe.

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
| `FP` | `C4`: called a method a pure validator when its own first line of javadoc says it creates accessories. The author said so at the time and was right |
| `FP` | A new test assumed accessory addresses 291-293 were free. They are not: `init` restores the real database, and `testAccessory`'s helper had already documented why |
| `FP` | `C3`'s verification reported 69 as an independently counted number of *started* threads. It was a count of `new Thread(` occurrences, and one of the 69 is never started - found as `PV-C1` |
| `PV` | `C4`: traced the phantom rename as far as `renameLoc`'s guard and stopped, calling the end state benign. The delete one step earlier destroys an unrelated locomotive and then renames nothing |
| `PV` | `C6` called its enumeration complete - "that is the whole residue" - having found three run-ons. An independent rescan before fixing found a fourth, in `I18n`'s own javadoc |

Three of these are the same mistake: **assuming what a method does instead of reading what it says**.
Twice from the name (`IND-M2`, `IND-D6`), and once - `FP-C4` - from the body, against a javadoc that
stated the behaviour in its first sentence. It is now a rule in [README.md](README.md).

The last row is the same shape one level out: the information needed was in a helper whose call sites
were read and whose comment was not. Both new test files were written by consulting existing tests for
*idiom* while skipping what those tests had written down about the *environment*.

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

**Two items are open by decision; nothing is open by oversight.** `FP-B3` (a route import that parses
can still destroy routes) and `FP-C6` (the load error dialog shows the last problem rather than the
first) were both found after the cycle's findings had closed, while writing tests for invalid input.
The author recorded and deferred both, judging neither likely to be met in practice. They are written
up so that a later change to the import flow is made knowing the delete has already run.

`PV-C1`..`PV-C5` were added by the post-cycle verification pass and are all fixed, the fixes validated
at `3391cb9`. One of them, `PV-C4`, was filed as C and benign; verification found the benign conclusion
wrong and it closed as a B - data loss. Its shape is the cycle's most common: the finding was right,
and the reasoning about what it *led to* stopped one call too early.

The validation added `PV-B1` (severity B: rename proposals interact through the name space, and a
Central Station name swap deletes a locomotive - the third face of the `FP-B1` family, and the argument
that the family's root defect is the precomputed list itself) and `PV-C6` (cosmetic: the javadoc
run-ons left by the `PV-C5` reformat). Both are fixed; `PV-C6` turned out to be four run-ons rather
than the three its enumeration recorded.

Everything across the eight documents is now fixed, withdrawn as mistaken, closed by decision, or
informational. The last item to close was `FP-C4`, on 2026-07-27, having been reported wrongly,
re-diagnosed, and then fixed more severely than first proposed. It was written up as a block that
validates nothing; in fact the call creates the accessory as a side effect, exactly as the author said
when the finding was raised. What was really wrong was that its failure was silent - so a configuration
that could never be actuated loaded as valid, and failed later at a point that named nothing about the
cause. It is now fatal at load, and the severity was raised from C to B.

Worth recording for its own sake: **that fix was reached by writing the error message.** Three wordings
were drafted, and each forced a sharper question about what the code actually did - the second being
provably false the moment it was checked against `addConfigCommand`. Having to state plainly what
happens to the user is a cheap and unusually effective way to find out whether you know.

Test classes added during the cycle: `testLayoutBfs`, `testLayoutBfsEquivalence`, `testLayoutPickPath`,
`testLayoutTimetable`, `testMessageBundles`, `testMultiUnitMembership`, `testLayoutReloadFence`,
`testLayoutRenameKeys`, `testImportRename`, `testRouteRoundTrip`, `testAdvancedRoutes`,
`testInvalidInput`.

The last two were added after the findings closed, covering combinations rather than defects:
conditional routes acting on locomotives with multi-units inside them, and the rejection paths for
input the user entered incorrectly - `Layout.fromJSON`'s roughly forty validations, none of which had
been asserted before.

Two areas remain untested and are named here so the gap is not mistaken for coverage: the editor flows
(`FCR-B3`, verified manually), and the charset round trip (`FCR-B1`, which would pass vacuously on a
UTF-8 JVM and needs a byte-level assertion).
