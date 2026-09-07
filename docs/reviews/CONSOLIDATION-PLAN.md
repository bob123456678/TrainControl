# Plan: replacing 135 review documents with one source of truth

**Proposal only — nothing here has been done.** Adam asked for a plan and said he would give
directional feedback before any of it runs.

---

# Reviewed 2026-09-07, against two days of evidence

The plan below was written before the package sweeps were triaged and before two more review rounds
ran. Those produced measurements that settle three of its four open questions and overturn one of its
stages. **Read this section first; the original is kept underneath because the parts that held up are
still the plan.**

## What held up

**The central claim, and it is now measured rather than argued.** "243 open rows is not 243 open
problems." Of the twenty-nine package-sweep findings triaged on 2026-09-07, **twelve were already
gone** — and eleven of those twelve had been fixed in the intervening five weeks by people who did not
know the finding existed. Three of them carried comments naming the exact mechanism the sweep had
reported. That is category 2 at 41% of one bundle, which is the strongest possible argument for
triage-before-writing.

## What was wrong: Stage 2 is not a cost

The plan calls Stage 2 "the only expensive stage" and treats its size as something to minimise.
That is backwards. Triaging those twenty-nine rows **produced fourteen fixes and found the largest
defect of the week** — C13, filed as "a route condition registers a phantom accessory" and actually a
call in the keyboard paint loop that had put 2048 phantom switches into the live database.

Reading a finding against current code does not *cost* a fix; it *is* how the fix gets found. So this
is not a backlog-clearing exercise with a documentation deliverable. It is **a review pass that leaves
a paper trail**, and it should be scheduled and valued as review work.

## Question 4 — grandfathering: the evidence says no

The plan offered to mark everything before a cut-off date superseded without reading, and guessed it
would "drop some real findings". It would have dropped the biggest one. **C13 was three weeks old.**
Under a 2026-08-01 cut-off it survives; under a 2026-09-01 one it is deleted unread, and the 2048
phantoms stay.

The plan's other proxy fails too. "Rows in untouched files are likelier still true" — C13 lived in
`MarklinControlStation.java` and `TrainControlUI.java`, two of the most-edited files in the project,
and survived every edit. **Neither age nor churn predicted staleness.** The only thing that did was
reading the mechanism against the code, which is Stage 2 with no shortcut in it.

## Question 3 — the ledger: the premise was wrong

The plan calls `verify-ledger` "the only automated check that dispositions do not drift" and implies
it covers the corpus. It does not: it holds **27 rows**, all from the manual-test flow. That is about
11% of the open rows and none of the review bundles.

Worse, it does not check the thing that actually drifted. `REG8-C6` found `REG7`'s own status table
stale **within three days** — three closed findings still shown as open — and the ledger passed clean
throughout, because a status table inside a document is not what it parses. So: retiring it with the
reviews costs almost nothing, and repointing it at a new document is **new work, not a redirect**. If
anything is worth automating it is the check that was missing: that a finding's stated disposition
agrees with its own body.

## Stage 4 is not safe as written — 871 reasons

The plan ends with `git rm docs/reviews/*.md`, on the grounds that git history is what Adam asked for.
But **the code cites these documents 871 times** — 441 in `src/`, 430 in `test/` — by finding id:
`DR-B10` fifteen times, `VD10-B2` and `LE-A1` ten each, and so on down a long tail. Those citations
are load-bearing: they are how a comment explains *why* a line is the way it is, and they are the
first thing anyone follows when they want to change it.

`git rm` turns every one of them into a reference retrievable only by someone who knows to go looking
in history for a file that no longer exists. That is a real loss and the plan does not mention it.

**The cheap fix, and it should come first rather than last:** an id → one-line index. What the finding
was, and what happened to it. A few hundred lines, mechanically generated from the documents that
already exist, and it is what makes deleting them safe. Build it in Stage 1, beside the inventory.

## The shape that actually emerged is not the shape proposed

The plan proposed **one** document organised **by feature**. What exists in `docs/reference/` after
two days is **four**, organised **by kind**:

| | what it is |
|---|---|
| `behaviour.md` | what the railway should do — the source of truth, in Adam's words |
| `open-questions.md` | the delta from it: what is broken, what was decided |
| `package-sweeps.md` | one bundle, triaged finding by finding |
| `two-copies-evaluation.md` | one question, answered at length |

This is better and the plan should adopt it. A single by-feature document would have to hold both
"this is the rule" and "this is where we do not follow it", and those are read by different people at
different times: the rule is read to settle an argument, the delta is read to decide what to work on.
`behaviour.md` earned its place this week by being the thing two reviewers checked the code against.

## Revised recommendation

**Do not run this as a project.** Run it the way the last two days actually worked:

1. **Stage 1 as written, plus the id index.** Mechanical, cheap, and the index is what makes Stage 4
   reversible.
2. **One bundle at a time, triaged as review work** — the way `C1`–`C29` was done. Each pass closes
   what is stale, fixes what is live, and appends its rulings to `open-questions.md`. No cut-off dates.
3. **Let the consolidated document accrete** rather than being written in one sitting. It is already
   accreting.
4. **Stage 4 last, and only after the index exists** — and it is worth asking whether it is needed at
   all. Once every live finding has been lifted into `docs/reference/`, the reviews are inert; deleting
   inert files buys tidiness and costs 871 working references.

## The one question still worth Adam's answer

The plan's question 2 — **how much reason a decision should carry** — is unanswered and is the only
one measurement cannot settle. The evidence leans hard toward "all of it": every rule re-litigated
this week was one recorded without its reason, and `behaviour.md`'s quoted rulings are the part both
reviewers used. But it makes the document long, and long documents go unread, which is its own failure
mode.

---

# The original plan, as written 2026-09-06

## What is there now

- **135 documents**, 4.8 MB, from 2026-07-26 to 2026-09-06.
- **243 rows still marked Open**, across roughly 50 citation prefixes (`AR`, `CONF`, `DR`, `FR`,
  `SPEC`, `TCX`, `V35`, …).
- One ledger (`docs/manual-tests/triage.py verify-ledger`) that already checks dispositions have not
  drifted from the documents.

Adam's goal, in his words: *"all the reviewers replaced with a single summary that is also a source of
truth, with reviews then just living in git history."*

## The thing to be careful about

**243 open rows is not 243 open problems.** From the last two days' work, the categories are very
different and must not be merged:

1. **Actually still broken.** Small, and the whole point of the exercise.
2. **Fixed, disposition never updated.** A finding fixed in passing by a later change.
3. **Ruled on and rejected** — Adam decided the current behaviour is correct.
4. **Superseded.** The code it describes no longer exists, or a later ruling reversed the premise.
   `CONF-A2` is the clearest case: correct when written, and its subject was deleted the same day.
5. **Never true.** A reviewer's misreading. Several this week were mine.

A consolidation that treats all 243 as work items produces a backlog nobody will ever finish and
which is mostly noise. **The first pass is triage, not writing.**

## Proposed shape

One document, `docs/reference/open-questions.md`, beside the behaviour reference written today. It
holds only categories 1 and 3 — what is broken, and what was decided. Everything else goes.

Structure it **by feature, matching the sections of `behaviour.md`**, rather than by review or by
date:

```
## Reversals
  Open      — what is still wrong, with a repro
  Decided   — "asked and answered: X, because Y" (Adam's rulings, so they stop being re-litigated)
  Limits    — deliberate under-claims, so they are not reported as bugs
## Length and blocking
## Routing tiers
...
```

The **Decided** and **Limits** sections are the ones that pay for the exercise. Most of the churn in
recent sessions came from rules being re-argued, not from bugs.

## How to get there

**Stage 1 — mechanical inventory (no judgement).** Script over `docs/reviews/*.md`: every row with
its id, severity, disposition, and the document it lives in. Output CSV. Cheap, and it makes the real
size visible before anyone commits to reading 135 files.

**Stage 2 — triage into the five categories.** The only expensive stage. Each open row needs the code
read as it stands today. Proposed split:

- Rows whose file/line no longer exists → **superseded**, mechanically.
- Rows in files touched since the finding → need reading.
- Rows in untouched files → likelier still true; read those first.

I would do this in batches by feature area, not by document, so that one area is settled at a time
and the output can be checked against `behaviour.md` while the area is fresh.

**Stage 3 — write the consolidated document,** feature by feature, in the shape above.

**Stage 4 — retire the reviews.** `git rm docs/reviews/*.md` in one commit whose message lists what
moved where. They stay in history, which is what Adam asked for. Three things must move out first:

- The **citation-prefix registry** (which prefixes are taken) — small, and needed by any future
  review.
- The **protocol** in `docs/reviews/README.md` — how a review is run and graded. It is a working
  process document, not a finding.
- Whatever the **ledger** reads. `verify-ledger` parses these documents; it either moves to the new
  document or is retired with them. **This is the one hard dependency and should be settled first.**

## What I would want from you before starting

1. **Is a 5-category triage the right cut**, or do you want simply "still broken / everything else"?
   The finer cut costs more and buys the Decided section.
2. **How much history should the new document carry?** My instinct is that a decision is worth
   keeping only with its *reason* — a bare ruling gets reversed by the next person who finds it
   inconvenient. That makes it longer but load-bearing.
3. **The ledger.** Retire it with the reviews, or repoint it at the new document? It is the only
   automated check that dispositions do not drift, and today it passes clean.
4. **Grandfathering.** Everything before a date — say 2026-08-01 — could go straight to superseded
   without reading, on the grounds that the code has moved. Faster, and it will drop some real
   findings. Your call on the trade.

## Estimate

Stage 1 is minutes. Stage 3 is a day's writing. **Stage 2 is the whole cost**, and its size depends
entirely on question 4: reading 243 rows against current code is a large job, and grandfathering
could cut it by more than half.

I would not start Stage 4 until you have read the Stage 3 document, because deleting the reviews is
the irreversible half and the new document is the only thing that would justify it.
