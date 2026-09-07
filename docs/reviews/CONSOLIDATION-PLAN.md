# Plan: replacing 135 review documents with one source of truth

**Proposal only — nothing here has been done.** Adam asked for a plan and said he would give
directional feedback before any of it runs.

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
