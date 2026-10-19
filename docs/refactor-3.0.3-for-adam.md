# FR-136: calls made without Adam, for his review

Adam, 2026-10-19: *"continue autonomously through all remaining phases, flagging issues for my attention for later
review.  If unsure, make the call and ensure it is noted and reversible, or skip that step if not."*

Every call below says what was decided, why, and how to undo it.  **Skipped** items needed Adam and could not be
made reversibly; they wait for him.  Newest last.  The plan is [refactor-3.0.3.md](refactor-3.0.3.md).

## Calls made

| # | Phase | The call | Why | To undo |
|---|---|---|---|---|
| 1 | 0 | `layoutFolderIs`'s javadoc now says the control station's fall-back to the Central Station writes the layout preference itself, instead of changing the code to go through it. | The direct write is `""`, which never touches the last-local-folder preference, so the effect is the same; changing working code in a refactor-only release was the larger risk. | Revert the comment in `5b04438d`, or route `MarklinControlStation`'s fall-back through a package-visible `layoutFolderIs`. |
| 2 | 0 | The editor's shortcut floor in `testNoTwoShortcutsShareAKey` rose from 14 to 17. | The per-window scan finds 20; the floor was meant to sit a little below the real count. | Set `EDITOR_FLOOR` back to 14 (`5b04438d`). |
| 3 | 1 | `testTheRecordsCountTheStore` runs `python` for its nine new claims about `catalog-findings.py --next`. | The tool is Python; asking it beats copying its rules into Java.  A missing `python` makes the class fail, never skip. | Delete the nine claims (`ce8a0b70`). |
| 4 | 1 | The "pre-3.0.0" manual-test archive ended at 2.8.2 (2026-09-26). | The brief gave no lower bound for 3.0.0.  **Moot once 1c lands**: the archives are deleted with the markdown. | Nothing to undo after 1c. |
| 5 | 1 | Phase 1's archive (`2ff1eecd`, `54ff433c`) is superseded by Phase 1c the same day. | Adam chose a database with a text dump; the archive treated a symptom. | The archive stays in git history. |
| 6 | 2 | AutonomySession's comment trim stopped at 19% (6,180 to 4,988 comment lines), below the plan's 30-40%. | What is left is live rules, the ids that keep claims traceable and your quoted requirements; the comment rule forbids cutting those to reach a number.  The other three giants cut 31-32%. | A second pass can be run on AutonomySession at any time; nothing to undo. |
| 7 | 2 | Long javadoc contracts were left whole (Layout's `whyItWouldMeetItsOwnTail` is 45 lines, AutonomySession has 58 blocks over 15 lines). | The rule allows a block over 15 lines when it is a javadoc contract. | A later pass can shorten them. |

## Skipped, waiting for Adam

| # | Phase | What | Why it waits |
|---|---|---|---|
| 1 | 1b | `core.testRouteInventory`'s three skipped claims (hollow-scan H0387 to H0389) never check anything outside your main checkout: they read `tc_backup/Autonomy 1b.json`, `1d` and `1e`, which only your machine has. | Making them real means putting your bundles (or made-up stand-ins shaped like them) into `test/`.  Your data stays out of the repository unless you say so. |

## Issues found, for later

| # | Found in | What | Filed |
|---|---|---|---|
| 1 | Phase 0 | `findings.tsv` reports "not what the store renders" after a checkout, but only its line endings differ. The mirror check is line-ending sensitive. | Not filed. Phase 1c touches the mirror. |
