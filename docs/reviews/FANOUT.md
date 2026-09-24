# Reviewer fanout

How a multi-lane review is run, iterated, closed and reported in one session. The rules for a single review
document - status lines, prefixes, the A/B/C/D grades, dispositions, cataloguing - are in [README.md](README.md);
this is the procedure around them.

Adam, 2026-09-23, asking for the first one run this way: *"let's do a full Opus 5.5 reviewer fanout per our reporting
SOP.  One to look at today's work only, and 4 (autonomy, UI, regression, documentation) looking at all code changes
since 2.8.1.  Iterate on each reviewer's findings up 4 times (fixes/re-review with an opus validator).  Make a report
with any remaining open items and decisions that need my input when finished."* And on the report it produced: *"I like
this HTML report ... this is an example of what I'd like to review at the end of the session."*
[examples/session-report-2026-09-23.html](examples/session-report-2026-09-23.html) is that report.

## The lanes

Five reviewers, one per lane, each writing one document. The prefix is the lane, and each round adds its number, so a
finding's id says which lane and which round raised it: `AUT-C2` (autonomy, round 1), `GUI4-C3` (UI, round 4).

| Lane | Prefix | Reads |
|---|---|---|
| Today | `TDY` | the session's own commits, and nothing older |
| Autonomy | `AUT` | every change to `automation/` and `automationui/` since the last release tag |
| UI | `GUI` | every change to `gui/` and the message bundles since the last release |
| Regression | `REG` | the same range, asking only what a user upgrading from the last release sees change |
| Documentation | `DCN` | `docs/`, the root documents, comments, the manual tests and the catalogue, against the code |

Check the prefixes are free before launching: `docs/tools/catalog-findings.py` owns the catalogue, and a prefix is
free only if no row in `docs/manual-tests/findings.tsv` uses it (the folder is empty by design, so looking there
proves nothing).

## What every reviewer is told

- **Read-only.** No tests, no compile, no JVM - the battery shares the machine with the application, and two JVMs at
  once have thrown points under a train. No git state changes. No helper scripts on disk.
- **One file.** The reviewer writes its own report and nothing else, to a folder in the session's scratch area, never
  into the repository.
- **Never read or write `cs2_sample_layout/`.** It is Adam's live railway.
- **The report's shape is README.md's:** `**Status:**`, `**Prefix:**`, a method paragraph saying what was read and how,
  then findings under `### PREFIX-A1 - title` with a `| **Disposition** | ... |` table. D findings (checked and clean)
  are headings too - a bullet is not catalogued. No summary table whose first cell looks like an id: the catalogue
  reads it as a finding.
- **Every finding that needs execution says so**, with a verification request: the fixture, what result proves it,
  what refutes it. Reading finds about half of what running finds, and the request is what lets the fixer run it.
- **Grade by consequence on the railway**, and say what mitigates it.

## The round

A round is: reviewers (or, from round 2, validators) → fixes → records. Up to four rounds, stopping earlier when a
round finds nothing new above C.

1. **Launch** the five reviewers in parallel, in the background.
2. **Triage** each report as it lands. Settle every finding one of four ways: fix it; Adam's decision (write the
   question, the options and a recommendation); follow-up (older than the range, or out of scope - say why); or not a
   defect (say what was checked). Findings that restate an earlier one are dispositioned with it.
3. **Claims first.** Every fix ships with a test that was seen failing on the unfixed code, for the reason the finding
   gives. Commit the red claims on their own, then the fixes. A claim whose fixture could produce the asserted state
   another way is a control, not a claim - set the other inputs to disagree with it.
4. **Run the classes the change touches**, gated (no `java.exe`/`javaw.exe` running - checked immediately before every
   run, not once per session). No edits to `src/`, `test/` or `docs/` while a battery runs.
5. **Records.** Copy the round's documents into `docs/reviews-<date>/`, write the dispositions (commit hashes, claim
   hashes, "red first"), catalogue with `python docs/tools/catalog-findings.py --add docs/reviews-<date>`, set each new
   row's status in the store (Closed / Open / "Open - Adam's decision"), render the mirror, update the counts in
   behaviour.md and open-questions.md. Tracker entries for anything Adam has to see on the railway: appended to
   `tests.md` with their anchors, proved with `triagedb.py check --tag MT-###` (not `show`, which prints only verdicts).
6. **The next round's validators** each get their lane's documents so far and the round's fix commits, and are asked
   two things: is each disposition true (verify, don't trust), and what did the fixes break or leave behind. Same rules
   as the reviewers.

## Before closing

- **A full battery** on the final HEAD. Bookkeeping reds (a ratchet lowered by a fix, a citation of something that is
  not a finding) are fixed and re-run; anything else is a finding.
- **A mutation run** over every fix of every round: one mutation per fix, each undoing it, run against the classes that
  claim it (`mutate.py`, which restores from git - commit first). Every survivor is either a claim strengthened until it
  fails, or recorded as equivalent with the reason (the state it needs is one the rules never produce). A spec must
  name a class whose fixture reaches the mutated line.

## Closing the round

1. Each document's `**Status:**` from the store: `closed` where every row is Closed, otherwise `open` with the open ids
   named under it.
2. Commit the documents in that final state, then delete them in the commit that closes the round - the catalogue
   carries every finding, and the parent commit carries the prose.
3. Say in README.md that the round happened, without changing the counted totals of earlier deletions.

## The report

An HTML page, published as an Artifact, for Adam to review at the end of the session. Write it for someone who was
away: what needs him, what was decided without him, what is left. The example is
[examples/session-report-2026-09-23.html](examples/session-report-2026-09-23.html).

- **The strip at the top:** findings raised, closed, waiting on his decision, left for later; the battery and the
  mutation run in one sentence each, with the counts.
- **Needs your decision:** one card per question, grouped by theme. The ids as chips; what happens today, in railway
  terms; the options; a recommendation where there is one. None of it should need the review documents to understand.
- **Decided while you were away:** every call made on his behalf, and the rule it followed, so any of them can be
  reversed.
- **Left for a later session:** a table - the id and what is left, and why it waits.  Adam, 2026-09-24: *"work the
  'for a later session' items listed, or file them if deferral makes more sense."*  So the next session starts there:
  each item is worked - claim first, as any fix - or filed in `issues.md` with the reason it waits, and the next report
  says which.
- **Waiting on the railway:** the manual tests he has to run, with anything he must do first (note which routes are
  armed; the file to import is provided).
- **The footer:** the commits, and what was not touched (his live layout).

Plain words throughout: the square's name rather than its key, the menu item's label rather than the method behind it.
Numbers as counts. No finding is described only by its id.

**Published to the same page each time.** The report is republished to the same Artifact as the session goes on, so the
link Adam has always shows the latest; a new session's report is a new page.  He reads it on his phone as often as at
the desk, so it has to work at phone width.
