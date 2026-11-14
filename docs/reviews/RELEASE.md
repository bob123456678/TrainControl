# How a release is built, reviewed and checked

Adam approved this structure on 2026-11-14 (D136): "I'd rather move away from arbitrary rounds and follow a structure, while leaving room to see if the structure is sufficient." It replaces the file and area scoped "final review" rounds. Each step looks at something the step before it cannot see.

## 1. Building: every change

The validators review each change on its way to dev (`FANOUT.md`). Builders follow these on top of their brief:

- **Twin doors.** A fix to a door that reads or writes the user's data lists its sibling doors (Save and Download, Open Layout and Sync, Backup and the update's backup) and fixes each, or says why not. OB-465 was Save fixed and Download left.
- **Round trips.** A feature that writes data gets a claim that writes it, reads it back and compares every field: save and reopen, download and reopen, export and import.
- **Look at it.** A change to a window gets pictures of that window in English and German, at its minimum and its default size, lettered as Adam's screenshots are. The orchestrator looks at them before the merge. The language fit test measures; a person still looks.
- **The MT runs before Adam does.** The orchestrator runs each fix's MT steps against the simulator before the MT reaches Adam. Steps that need a real Central Station stay his.
- **Validators ask one more question:** who else reads or writes this data, and does that still hold?
- **Iteration:** a batch's reviews repeat until a round finds no new A or B, at most three rounds. C fixes go alongside but don't hold convergence.

## 2. Automatic gates: every merge to dev

The merge guards, plus:
- SpotBugs against its baseline (`docs/tools/spotbugs.sh`);
- the language fit test over every window;
- the layout read comparison against the last release tag;
- the last release's updater taking the new jar.

A gate not yet built is listed on the plan page until it is.

## 3. Release reviews: once, at feature freeze

Each runs from its standing brief in `release/`, with the placeholders filled in.

| Review | Brief | Model | Scope |
|---|---|---|---|
| Cross-feature | `release/cross-feature.md` | one Opus and one Fable, independently | follow one piece of data, and one window, across features |
| Data safety | `release/data-safety.md` | Opus | every door that writes the user's data; after the first use, the doors changed or new since the last tag |
| Concurrency | `release/concurrency.md` | Opus | locks, threads, timers and gates added or changed since the last tag |
| Upgrade path | `release/upgrade-path.md` | Sonnet | first run, self-update, old files in the new version and new files in the old |
| Translation wording | `release/translations.md` | Sonnet | strings added or changed since the last tag |

Findings are catalogued (`docs/tools/catalog-findings.py`) and fixed as usual. Rulings in `docs/reference/behaviour.md` are not defects.

## 4. One fix review

One Opus and one Fable reviewer read the fixes from step 3 together, repeating until a round finds no new A or B, at most three rounds. Then a battery and the merge to dev.

## 5. Readiness, Adam's MTs, master

The readiness page (`triagedb.py ledger`, `issues`, `check`, open findings, code size against the last tag) says what stands between dev and the release. Adam runs his MTs. Master only on his word.

The release itself: `IS_PRE_RELEASE` matches the kind of release, the GitHub release name carries `v{RELEASE}`, and the jar built by `ant package-for-store` is its first asset (the updater takes asset 0).

## Is the structure enough?

- **Escape log** (`release/escapes.tsv`): every bug found after the freeze, by Adam, a user or anyone outside these steps, gets a row naming the step that should have caught it and what changed in that step's brief.
- **Yield record** (`release/yield.tsv`): each review's A and B count per release. A review that finds no A or B two releases running gets lighter, except upgrade path and concurrency, where a miss costs too much.
- **Rework:** per release, the share of fix lines that rework the release's own code, and the share of bugs Adam found rather than the steps above. Both should fall.
- **3.1.0 baseline** (measured 2026-11-14 by blaming each fix's changed src lines): 51% of fix lines reworked code written in the release, a median of 3 hours after it was written, so mostly the validator loop on fresh branches. Of the 32 bugs filed during the cycle that could be classified, 18 were regressions in 3.1.0 code; Adam found 9 of those, reviews, validators and builders 8.
- **After each release**, read the three and change this file.
