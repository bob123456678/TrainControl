# Code review discipline

How reviews in this folder are run and recorded. Every rule below exists because it was broken during
the July 2026 review; the example after each one is what it cost.

The documents already in this folder predate the A/B/C/D convention below and use several ad-hoc
letters. They are left as they are - their identifiers are referenced from commits and from each other,
and renumbering would break that for no gain. The convention applies to new reviews.

[2026-07-cycle-summary.md](2026-07-cycle-summary.md) indexes the July 2026 cycle: which pass covered
what, the patterns that recurred across passes, and the consolidated reviewer-error tally. Start there
rather than with any single document.

---

## The document

**Findings are lettered A, B, C, D by severity - never by topic, phase, or reviewer.**

| | |
|---|---|
| **A** | High. Wrong behaviour on the layout, or data silently lost. |
| **B** | Medium. Incorrect results, or crashes in specific configurations. |
| **C** | Low. Cosmetic, dead code, or narrow edge cases. |
| **D** | Not defects. Things that look wrong and are not, findings withdrawn as mistaken, and checks that came back clean. |

Number within the letter (A1, A2, ...). A finding keeps its identifier for life: if its severity is
revised, say so in its entry rather than renumbering it, because the old identifier is already
referenced from commits, changelog notes and other reviews.

**Declare a short prefix at the top of each document, and cite other documents' findings with it.**
Identifiers are only unique within a document, and a cycle of any size will collide. *The July 2026
cycle produced 92 findings across five documents, in which `B1` names three unrelated things and `D1`
another three - so "see B1" was ambiguous in exactly the place a reader needs precision. `INT-A1` and
`FCR-B1` are not.* Keep numbering per-document; the prefix is for crossing between them.

**Do not merge documents to solve that.** Each one records a pass - its scope, its method, and what it
missed - and merging destroys who found what and when. That is the calibration data, and it is worth
more than the tidiness. Write an index instead.

**D is not a bin for things you didn't fix.** It is for things that turned out not to be defects. An
open C item is still a C. A withdrawn A becomes a D, with the original severity and the reason it was
wrong both recorded - that transition is the single most useful thing in a review for calibrating how
much to trust the rest of it.

*Cost: the July cycle produced findings under A, B, C, SR, FR, P, N, M and T. Six of those letters
carried no meaning beyond which pass or which reviewer produced them, so nothing could be sorted or
counted by severity across documents, and the same defect class landed under different letters in
different files.*

**One status, one location.** A finding's disposition belongs in exactly one place - the status table at
the head of its section. The prose under a finding is a historical record of what was believed when it
was written, and says so. *Cost: that review's summary and body disagreed three times, and the reviewer
misread his own C table as showing a dozen open items that were already fixed.*

**Withdrawn findings stay in the record, marked withdrawn.** Deleting a mistake hides that the reviewer
was wrong about it, which is exactly what a later reader needs to calibrate the rest. *Three findings
were withdrawn in July 2026; two of them had already been "fixed" before the mistake surfaced.*

**Say what version was reviewed and when.** A review header that still claims "no code was changed"
after twenty commits is worse than no header.

---

## Before calling something a finding

**Verify the layer you are actually claiming about.** Read the method that enforces the rule, not the
one that looks like it should. *Twice in one review: `Layout.createPoint` appeared to allow a
destination with no feedback - the check is in `Point`'s constructor. And `bfs` appeared deterministic -
the shuffle is in `getNeighbors`.*

This applies to library code too, and a method name is not a specification. *A fix used
`Collection.removeIf` to delete a map key whose hash had drifted, with a comment explaining that it
iterated rather than looking up. It does not: `removeIf` calls `Iterator.remove()`, which the
hash-based collections implement by recomputing the hash from the key's current state - so it searched
the wrong bucket and removed nothing, in exactly the case it was written for. The comment described the
API's shape, not its implementation.* A related habit that caught two separate errors the same day:
when a claim rests on a method whose name reads like another method's (`isAutonomyRunning` against
`isAutoRunning`), open it. Both times, the delegation was the opposite of what the name suggested.

**"Wrong code" is not "wrong behaviour".** Trace whether any caller can actually reach the defect before
assigning severity. *B3, C7 and C15 were all structurally real and all unreachable: every caller already
guarded. They are worth fixing as traps for the next caller, not worth a changelog entry.*

**Check whether the surrounding machinery already compensates.** A defect in one call can be irrelevant
inside the loop that calls it. *The "intermittent no free paths" concern was raised from measuring a
single `bfs` exclusion sequence; `pickPath` enumerates every route to every destination until exhausted,
which makes the ordering irrelevant. Retracted after measuring the whole function - 2,680 invocations,
zero spurious failures.*

**Distinguish "this could happen" from "this does happen".** Where a claim depends on real data, check
the real data. *B8 predicted an import failure on sparse function lists; 243 locomotive blocks across
the actual fixtures have no gaps. The fix stayed as a guard, the changelog entry was withdrawn.*

---

## Fixing

**One finding at a time, failing test first, confirmed failing before the fix.** And check it fails for
the *right reason* - a test that fails because its own setup is wrong proves nothing.

**Prove the guard actually guards.** A regression test that only sometimes catches the regression is
worse than none, because it reads as protection. *The guard against re-applying a BFS optimisation was
measured at 247 catches in 500 runs - a coin toss - because the neighbour shuffle sometimes hid the
regression. It now repeats twenty times.*

**Re-read every caller before changing a signature or a semantic.** *Changing `RouteCommand.fromLine` to
wrap unchecked exceptions was safe only because all three callers propagate `throws Exception`; one that
caught `NumberFormatException` would have broken silently.*

**Prefer the smaller fix when the larger one changes behaviour.** *C17 suggested two changes. The
container swap was pure cost; marking `visited` on enqueue would have broken the alternative-route
search that every caller depends on. Only the first was applied, with the reason recorded at the call
site.*

**Leave the reasoning where the next person will trip over it** - in the code, not only in the review.
Comments cannot drift out of sync with the code the way a separate document can.

**Check the shape of what you changed, not just that it still parses.** A structural check that cannot
tell "correct" from "badly broken" is not a check. *An edit that replaced `equals` and `hashCode`
computed its start by searching backwards for the preceding javadoc; `equals` had none, so the search
ran past it and silently deleted two unrelated methods. Braces still balanced - deleting whole methods
keeps them balanced - and the non-ASCII check passed too. Both "verifications" were satisfied by a file
missing two methods. Diffing the method names against HEAD takes one command and catches it
immediately.*

**Before mutating shared state, find out who else reads it and under which lock.** Verifying the
function you are changing is not enough; the hazard lives in its call graph. *A fix that unlinked a
deleted locomotive from every consist did exactly the right thing to the data - and did it by mutating
a plain LinkedHashMap that `setSpeed` iterates under the locomotive's own lock, from a UI thread that
did not hold it. The change was correct and the concurrency was not. This is the sibling of "verify the
layer you are actually claiming about": that one catches a missing guard above you, this one catches a
reader beside you.*

---

## Testing

**Simulate the code, not your model of the code.** *A simulation "proved" two BFS implementations
identical across 6,874 comparisons. It modelled the reviewer's mental picture, not `getNeighbors`, which
shuffles. The real test failed immediately.*

**Run the control experiment before concluding two things differ.** Compare the thing against itself
first. *Current vs previous implementation: 57 disagreements. Current vs current: 62. The divergence was
the shuffle, and the differential test that "found" it was unsound.*

**Randomised tests need fixed seeds and the seed in the failure message.** A failure nobody can reproduce
gets deleted rather than fixed.

**Property tests need a floor on how much they exercised.** Assert a minimum number of meaningful cases,
or the suite can quietly degenerate into testing nothing while still passing. *Every generator-driven
test in `testLayoutBfs` asserts one - e.g. "at least 300 pairs actually had a route".*

**Assert only what is deterministic.** Where the code is deliberately random, assert on invariants -
length, existence, set membership over repeats - never on one specific outcome. *Exact-route assertions
are safe in these suites only where the shortest route is unique, and the file header says so.*

**Assert the precondition that makes a test meaningful.** Otherwise the test can pass for reasons that
have nothing to do with what it claims. *A test for a cleanup that had to find a drifted hash key first
asserted that the lookup genuinely failed. That assert is the only reason it caught the fix being wrong
- the fix used `removeIf`, which cannot remove a drifted key, and without the precondition the test
would have passed while exercising only the cases that never needed fixing.*

**When a root fix lands, expect tests of the old bug to fail at their preconditions - that is
confirmation, not regression.** *After locomotive hashing became identity-based, the test that
manufactured a drifted hash could no longer manufacture one. Inverting it into a guard on the new
invariant is usually better than deleting it: the scenario is gone, but the property that replaced it
is exactly what a future author might undo.*

---

## The changelog

**Only defects a user could actually have hit.** Everything else is noise to a non-technical reader and
a false claim to a technical one. *Two entries were withdrawn in July 2026 (A5, B8) once it was clear no
real file could trigger either.*

---

## When challenged

**Verify - do not concede, and do not defend.** Both are guesses. *"MFX addresses are unique" was
plausible and wrong; the correction came with a reason (duplicated locomotives drive one decoder) that
was checkable, and checking it also surfaced that an earlier fix could have evicted a duplicated
locomotive from the name map. Neither reflexive agreement nor argument would have found that.*

**After a correction, re-check what else rested on the wrong assumption.** A retracted premise usually
propped up more than one conclusion.
