# Reviewer fanout

How a multi-lane review is run, iterated, closed and reported in one session.  The rules for a single review document
(status lines, prefixes, the A/B/C/D grades, dispositions, cataloguing) are in [README.md](README.md); this is the
procedure around them.  Adam asked for reviewers' findings to be iterated *"up 4 times (fixes/re-review with an opus
validator)"*, ending in a report of open items and the decisions that need him;
[examples/session-report-2026-09-23.html](examples/session-report-2026-09-23.html) is the report he wants to see.

## The lanes

Five Opus reviewers, one per lane, each writing one document.  The prefix is the lane, and each round adds its number:
`AUT-C2` (autonomy, round 1), `GUI4-C3` (UI, round 4).

| Lane | Prefix | Reads |
|---|---|---|
| Today | `TDY` | the session's own commits, and nothing older |
| Autonomy | `AUT` | every change to `automation/` and `automationui/` since the last release tag |
| UI | `GUI` | every change to `gui/` and the message bundles since the last release |
| Regression | `REG` | the same range, asking only what a user upgrading from the last release sees change |
| Documentation | `DCN` | `docs/`, the root documents, comments, the manual tests and the catalogue, against the code |

Take each lane's round from the store before launching: `python docs/tools/catalog-findings.py --next AUT` prints the
next unused round of that lane (AUT6 after AUT5) and refuses a prefix the store has never used (`--new` declares a new
lane).  The store owns the numbers; the folder is empty by design.

## What every reviewer is told

- **Read-only:** no tests, no compile, no JVM (the battery shares the machine with the application), no git state
  changes, no helper scripts on disk.
- **One file:** its own report, in a folder in the session's scratch area, never in the repository.
- **Never read or write `cs2_sample_layout/`** (Adam's live railway).
- **The report's shape is README.md's:** `**Status:**`, `**Prefix:**`, a method paragraph (what was read and how),
  then findings under `### PREFIX-A1 - title` with a `| **Disposition** | ... |` table.  D findings (checked and
  clean) are headings too: a bullet is not catalogued.  No summary table whose first cell looks like an id: the
  catalogue reads it as a finding.
- **A finding that needs execution says so**, with a verification request: the fixture, what result proves it, what
  refutes it, so the fixer can run it.
- **Grade by consequence on the railway**, and say what mitigates it.  A lost timetable entry is B, not A (Adam:
  *"B IS OK."*); runs come first, so a corner case at rest while the setup is mid-edit (a link left unpaired, then a
  door of the Layouts menu) is C at most.
- **Comments are reviewed against the comment rule in `CLAUDE.md`:** a new or changed comment that tells history
  (what the code used to do, a review round, a date), runs past 15 lines outside a javadoc contract, or repeats the
  code is a C finding.

## The round

Reviewers (from round 2, validators), then fixes, then records.  Up to four rounds, stopping earlier when a round finds
nothing new above C.

1. **Launch** the five reviewers in parallel, in the background.
2. **Triage** each report as it lands.  Settle every finding one way: fix it; Adam's decision (the question, the
   options and a recommendation); follow-up (older than the range, or out of scope: say why); or not a defect (say
   what was checked).  A finding that restates an earlier one is dispositioned with it.
3. **Claims first.**  Every fix ships with a test seen failing on the unfixed code, for the reason the finding gives.
   Commit the red claims on their own, then the fixes.  A claim whose fixture could produce the asserted state
   another way is a control: set the other inputs to disagree with it.
4. **Run the classes the change touches**, gated: no `java.exe`/`javaw.exe` running, checked immediately before every
   run.  No edits to `src/`, `test/` or `docs/` while a battery runs.
5. **Records.**  Keep the round's documents in a working folder in the scratch area, never in the repository.  Write
   the dispositions (commit hashes, claim hashes, "red first"); catalogue with
   `python docs/tools/catalog-findings.py --add <the working folder>`; set each new row's status in the store
   (Closed / Open / "Open - Adam's decision"); render the mirror; update the counts in behaviour.md
   (`triagedb.py counts`).  Anything Adam has to see on the railway gets a tracker entry with `triagedb.py add-test`,
   proved with `triagedb.py show MT-###`.
6. **The next round's validators** each get their lane's documents so far and the round's fix commits, and are asked:
   is each disposition true (verify, don't trust), and what did the fixes break or leave behind.  Same rules as the
   reviewers.

## Before closing

- **A full battery** on the final HEAD.  Bookkeeping reds (a ratchet lowered by a fix, a citation of something that is
  not a finding) are fixed and re-run; anything else is a finding.
- **A mutation run:** one mutation per fix of every round, each undoing it, run against the classes that claim it
  (`mutate.py`, which restores from git: commit first).  Every survivor is a claim strengthened until it fails, or
  recorded as equivalent with the reason (the state it needs is one the rules never produce).  A spec must name a
  class whose fixture reaches the mutated line.

## Closing the round

1. Each document's `**Status:**` from the store: `closed` where every row is Closed, otherwise `open` with the open ids
   named under it.
2. Catalogue the documents in that final state (`--add <the working folder> --check` exits 0 when the store holds
   every finding in them), then delete the working folder.
3. Say in README.md that the round happened (the prefixes, the rounds and the date), without changing the counted
   totals of earlier deletions.

## The report

An HTML page, published as an Artifact, for Adam to review at the end of the session, written for someone who was
away.  The example is [examples/session-report-2026-09-23.html](examples/session-report-2026-09-23.html).

- **The strip at the top:** findings raised, closed, waiting on his decision, left for later; the battery and the
  mutation run in one sentence each, with the counts.
- **Needs your decision:** one card per question, grouped by theme: the ids as chips; what happens today, in railway
  terms; the options; a recommendation where there is one.  None of it should need the review documents.
- **Decided while you were away:** every call made on his behalf, and the rule it followed, so any can be reversed.
- **Left for a later session:** a table of the id, what is left and why it waits.  The next session starts there
  (Adam: *"work the 'for a later session' items listed, or file them if deferral makes more sense."*): each item is
  worked, claim first like any fix, or filed into the Inbox (`triagedb.py file`) with the reason it waits, and the
  next report says which.
- **Waiting on the railway:** the manual tests he has to run, with anything he must do first (which routes are armed;
  the file to import is provided).
- **The footer:** the commits, and what was not touched (his live layout).

Plain words: the square's name rather than its key, the menu item's label rather than the method behind it.  Numbers
as counts.  No finding is described only by its id.

**Republished to the same Artifact** as the session goes on, so Adam's link always shows the latest; a new session's
report is a new page.  It must work at phone width.
