# Code review discipline

How a review round is run and recorded.

**This folder holds no reviews, and that is deliberate.** It is the convention, not the home. Two hundred and eight
review documents were written between 26 July and 20 September 2026 and all were deleted, 143 on 2026-09-08 and 65 on
2026-09-21, alongside two for-Adam notes that were never reviews, once the catalog carried what they found. Later
rounds went the same way and are counted apart from those two deletions: twenty documents in
`docs/reviews-2026-09-23/`, fifteen in `docs/reviews-2026-09-24/`, and 101 files in `docs/reviews-2026-09-25/` (RSA 1
to 75, RLV6 to RLV13, RLA, RLD and RLU 1 to 5 each, BCR, BPV and E2E; 1,199 findings, each compared with the store
before the folder was deleted on 2026-10-19). **How a multi-lane review is run, closed and reported is in
[FANOUT.md](FANOUT.md)**, with an example report in
[examples/session-report-2026-09-23.html](examples/session-report-2026-09-23.html).

**Where those numbers come from:** `git log --diff-filter=D` counts **210 files** deleted from the review folders,
145 on 2026-09-08 and 65 on 2026-09-21 (44 in `docs/reviews/` itself and 21 in three dated folders beside it). The two
for-Adam notes, `2026-08-19-for-adam.md` and `2026-09-03-questions-for-adam.md`, are the difference between 145 files
and 143 reviews. Which of the rest counted as a review is a judgment no test can make;
`regression.testTheRecordsCountTheStore` checks only that the halves quoted here add up to the totals quoted here.

What survives a round:

| | |
|---|---|
| every finding | the `finding` table in `docs/manual-tests/triage.db`: ref, severity, what it was about, the evidence, the commit, which source files cite it, and a `status` set deliberately |
| the same, greppable | `docs/manual-tests/findings.tsv`, rendered from the store: `grep "^MKR-B1" docs/manual-tests/findings.tsv` |
| the rules it settled | [../reference/behaviour.md](../reference/behaviour.md) |
| the questions it left | the findings whose status is "Open - Adam's decision", in the same store |
| the work it left | the Inbox, in the same store: `py -3 docs/manual-tests/triagedb.py issues` |
| the tests it asked for | the manual tests, in the same store, tagged `MT-###`: `triagedb.py show MT-###` |

Adam: *"I don't want more reviews living in the repo"*, and *"everything relevant, that needs to be in prose, will live
in behaviour.md."*

**So a round's documents are written, cataloged and deleted in the same round, and the store hands out the numbers.**
The finding store (`docs/manual-tests/triage.db`, with `findings.tsv` as its mirror) is the index; a round never looks
in a folder to see what has been used:

1. **Take the round's number from the store.** `python docs/tools/catalog-findings.py --next RSA` prints the next
   unused round (RSA76 once RSA75 is in). It refuses a prefix the store has never used (a prefix is declared by
   querying the store) unless `--new` says it is a new one. `--check-unused RSA76-A1` refuses a ref, or a whole round
   (`--check-unused RSA76`), that is already there, so a number is never used twice.
2. **Write the round's record in a working folder** in the scratch area, not the repository, and write it while the
   round is acted on.
3. **Catalog it:** `python docs/tools/catalog-findings.py --add <the working folder>`. `--add <folder> --check` first
   says what that would do and writes nothing; it exits 0 only when every finding in the folder is already in the
   store.
4. **Delete the folder.** The store carries every finding.

`--add` is authoritative for the documents in the folder and nothing else: it adds and updates, and removes only rows
of a document IN the folder that the document no longer makes. It refuses a document named as a deleted round's
document that makes a different round, because that would remove the old round's rows. The full rebuild, which
re-reads `docs/reviews/`, refuses to run unless the folder holds every document the store holds (never true once a
round is deleted) and has no override, so no mode can drop the rows of deleted rounds.
`regression.testTheRecordsCountTheStore` holds each of these.

Findings keep their old identifiers, including early ad hoc letters that predate the A/B/C/D convention: they are
cited from commits, comments and each other.

**Hands-on tests live elsewhere.** Anything that needs the real railway or a display is a manual test in
`docs/manual-tests/triage.db`, under the rules in [../manual-tests/README.md](../manual-tests/README.md): append-only,
tagged `MT-###`, each with a disposition saying whether its last result still means anything. A finding is still
lettered by severity and dispositioned here; its TEST is written there and referenced by tag. When a round produces
work for the layout, add entries (`triagedb.py add-test`) and put their tags beside the findings.

---

## While the round is running

### Every document carries an overall status

A line near the top, under the title:

```
**Status:** open
```

Three values, and no others:

| | |
|---|---|
| **open** | At least one finding in it is not closed, or it is a plan that has not been carried out. |
| **closed** | Every finding has a disposition that is finished, and nothing outstanding cites it. |
| **reopened** | It was closed and something came back: a regression, or a finding that turned out not to be fixed. Say underneath WHICH finding and why. |

**A document with no status line is open**, because forgetting the line is likelier than writing the wrong one.

### What the status line is for, now that nothing is archived

It is the one line a fix commit has to change, and it makes the end of a round checkable: a folder in which every
document reads `closed` can be deleted; one in which any reads `open` is a round not finished. Keep it true; a
document's status table left behind by its own fixes reads "open" for finished work.

Nothing is renamed, ever: identifiers are cited from commits, comments and other documents, and a rename breaks those
silently.

### Three kinds of document, not one

1. **Reviews**: findings with severities and dispositions. Closed when every finding is.
2. **Plans and proposals**: a decision written down before the work. Closed when the work is done or the plan is
   abandoned; say which in the status line.
3. **Generated reports**: harness output for a person to read. No findings, no dispositions, regenerable; they do not
   belong in this folder. Fold the conclusion into a review and delete the dump, or have the harness write to a
   temporary directory, as `testRouteInventory` does.

### What this is not

Not a way of tidying up work nobody wants to look at. A finding is closed when it is fixed, withdrawn, or explicitly
declined by Adam; never because it has been open a long time, and never because a later document covers the same area.

---

## The document

**Findings are lettered A, B, C, D by severity, never by topic, phase, or reviewer.**

| | |
|---|---|
| **A** | High. Wrong behavior on the layout, or data silently lost. |
| **B** | Medium. Incorrect results, or crashes in specific configurations. |
| **C** | Low. Cosmetic, dead code, or narrow edge cases. |
| **D** | Not defects. Things that look wrong and are not, findings withdrawn as mistaken, and checks that came back clean. |

Number within the letter (A1, A2, ...). A finding keeps its identifier for life: if its severity is revised, say so in
its entry rather than renumbering, because the identifier is referenced from commits, changelog notes and other
reviews.

**Declare a short prefix at the top of each document, and cite other documents' findings with it** (`INT-A1`, not
"see B1"): identifiers are unique only within a document. Keep numbering per document.

**Do not merge documents.** Each records a pass (its scope, its method, what it missed), and merging destroys who found
what and when, which is the calibration data. Write an index instead.

**D is not a bin for things you didn't fix.** An open C item is still a C. A withdrawn A becomes a D with the original
severity and the reason it was wrong both recorded; that transition is the most useful calibration in a review.

**One status, one location.** A finding's disposition belongs only in the status table at the head of its section. The
prose under a finding is a historical record of what was believed when it was written, and says so.

**Withdrawn findings stay in the record, marked withdrawn**, so a later reader can see where the reviewer was wrong.

**Say what version was reviewed and when.** A header that still claims "no code was changed" after twenty commits is
worse than none.

---

## Before calling something a finding

**Verify the layer you are actually claiming about.** Read the method that enforces the rule, not the one that looks
like it should (the check may be in a constructor, the shuffle in a helper).

This applies to library code too: a method name is not a specification. (`removeIf` on a hash-based collection
recomputes the hash from the key's current state, so it cannot remove a key whose hash has drifted.) When a method's
name reads like another's (`isAutonomyRunning` against `isAutoRunning`), open it.

The documentation is part of the method: read the javadoc before contradicting it.

**"Wrong code" is not "wrong behavior".** Trace whether any caller can reach the defect before assigning severity. An
unreachable defect is worth fixing as a trap for the next caller, not worth a changelog entry.

**Check whether the surrounding machinery already compensates.** A defect in one call can be irrelevant inside the
loop that calls it. Measure the whole function, on the data the users have.

**Distinguish "this could happen" from "this does happen".** Where a claim depends on real data, check the real data.

---

## Fixing

**One finding at a time, failing test first, confirmed failing before the fix**, and failing for the *right reason*: a
test that fails because its own setup is wrong proves nothing.

**Prove the guard actually guards.** A regression test that only sometimes catches the regression is worse than none,
because it reads as protection. Measure how often it catches; the BFS guard repeats twenty times for that reason.

**Re-read every caller before changing a signature or a semantic.**

**When you fix a call site, grep for its twins before closing the finding.** The search is one command.

**Prefer the smaller fix when the larger one changes behavior**, with the reason recorded at the call site.

**Leave the reasoning where the next person will trip over it**: in the code, not only in the review.

**Check the shape of what you changed, not just that it still parses.** Balanced braces survive deleting whole
methods; diff the method names against HEAD.

**Moving work off a single thread removes an exclusion nobody wrote down.** Before making something asynchronous,
enumerate what that thread was serializing for free, and reintroduce each guarantee explicitly.

**Before mutating shared state, find out who else reads it and under which lock.** The hazard lives in the call graph,
not in the function you are changing.

---

## Comments: authoritative and self-contained

Adam, 2026-09-07: **"We should make comments in the code to strive to be authoritative and
self-contained, with the behaviour document being the documented intended functionality."**

The rules divide the work between the code and [`behaviour.md`](../reference/behaviour.md):

**A comment must stand on its own.** A reader who has never seen a review document must understand from the comment
alone what the code does and why. Write the rule out; name the case that forced it. If the comment only makes sense
once you have found `DR-B10` and read it, it has outsourced its job.

**A finding id is provenance, not the explanation.** `(DR-B10)` after a rule stated in full says where the argument was
had; citing it *instead of* the rule makes the comment depend on a document. Every citation should be the first kind.
`regression.testEveryCitationResolves` walks every citation in `src/` and `test/`; the count moves with every commit, so
it is not written here.

**The behavior document is the intended functionality, not the code.** A comment says why THIS code is the way it is;
`behaviour.md` says what the railway is supposed to do. When the two disagree, one is a bug and Adam decides which.

The test, applied when writing: **if this file were the only thing a reader had, would the comment be enough?** If the
answer needs a second document that is not `behaviour.md`, write more.

**Amended 2026-10-18: the current rule, not its history.** Adam approved FR-136's comment rule (*"I approve the plan
and the change to the comment rule"*; its standing rules are in `docs/refactoring.md`):

- A comment states the **current** rule and why the code meets it.  It must make sense on its own,
  ideally in 8 lines or fewer.
- History belongs in the finding row its id resolves to, or in git: what the code used to do, which
  round found it, who chose which option on which date.
- One id per claim; quote Adam only where his wording is the rule.
- A block over 15 lines is either a javadoc contract or moves to `behaviour.md`.
- New code follows the rule at once.  Existing comments are trimmed in comment-only commits, proven to
  change no code by comparing the compiled classes.

## Testing

**Simulate the code, not your model of the code.**

**Run the control experiment before concluding two things differ.** Compare the thing against itself first.

**Randomized tests need fixed seeds and the seed in the failure message.** A failure nobody can reproduce gets deleted
rather than fixed.

**Property tests need a floor on how much they exercised.** Assert a minimum number of meaningful cases, as every
generator-driven test in `testLayoutBfs` does ("at least 300 pairs actually had a route").

**Assert only what is deterministic.** Where the code is deliberately random, assert invariants (length, existence, set
membership over repeats), never one specific outcome; exact-route assertions only where the shortest route is unique,
and the file header says so.

**Assert the precondition that makes a test meaningful**, or it can pass for reasons unrelated to its claim.

**When a root fix lands, expect tests of the old bug to fail at their preconditions: that is confirmation, not
regression.** Inverting such a test into a guard on the new invariant is usually better than deleting it.

---

## The changelog

**Only defects a user could actually have hit.** Everything else is noise to a non-technical reader and a false claim
to a technical one.

---

## When challenged

**Verify; do not concede, and do not defend.** Both are guesses; check the reason given.

**After a correction, re-check what else rested on the wrong assumption.** A retracted premise usually propped up more
than one conclusion.
