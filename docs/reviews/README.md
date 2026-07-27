# Code review discipline

How reviews in this folder are run and recorded. Every rule below exists because it was broken during
the July 2026 review; the example after each one is what it cost.

The documents already in this folder predate the A/B/C/D convention below and use several ad-hoc
letters. They are left as they are - their identifiers are referenced from commits and from each other,
and renumbering would break that for no gain. The convention applies to new reviews.

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
