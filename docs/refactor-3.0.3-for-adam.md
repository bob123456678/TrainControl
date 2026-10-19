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
| 8 | 1a | The laned battery stays opt-in (`TC_LANES=auto`); `battery.sh` alone still runs one class at a time.  My own batteries from here on run laned. | The proof matched claim for claim (3,246 each side, one clipboard skip that the desktop caused, not the lanes), but nothing is lost by leaving the default where you know it. | Set `LANES="${TC_LANES:-auto}"` in battery.sh to make lanes the default, or drop `TC_LANES` to go back to serial. |
| 9 | 1a | Tests point the station sync of `init()` at an address that fails in 20 ms (`support.TestStationAddress.withNoStation()`), instead of 127.0.0.1, which Windows refuses only after 2 s. | It cost about 4 s per init, about 30 minutes across the battery; the program is unchanged. | Remove the wrapper calls in `af897da2`. |
| 10 | 1c | `docs/manual-tests/README.md` step 4 used to add a new finding's tag to an entry's **From:** line; it now adds a comment naming the finding. | No command rewrites an entry's body, which keeps MT entries append-only.  If the From line itself must change, `triagedb.py` needs a command for it. | Add that command and restore the step. |
| 11 | 1c | `findings.tsv` stays as the Java-readable mirror of the findings, beside the new dump's `findings.txt`. | The citation guards read it; replacing their reader was not needed for the cut-over. | Point the guards at `dump/findings.txt` and delete `findings.tsv`. |

## Skipped, waiting for Adam

| # | Phase | What | Why it waits |
|---|---|---|---|
| 1 | 1b | `core.testRouteInventory`'s three skipped claims (hollow-scan H0387 to H0389) never check anything outside your main checkout: they read `tc_backup/Autonomy 1b.json`, `1d` and `1e`, which only your machine has. | Making them real means putting your bundles (or made-up stand-ins shaped like them) into `test/`.  Your data stays out of the repository unless you say so. |
| 2 | 1a | Production speed-ups for tests: skip the start-up station sync when no station is configured or in simulation (about 4 s per init); make the fixed simulation pauses settable like `TIMETABLE_STUCK_MS` (`CONFIGURE_SLEEP` 150 ms, `FEEDBACK_DURATION_THRESHOLD`, `COMPLETION_POLL`, `NO_PATH_IDLE_MS`, the 1 s after a turn, `STAGING_RETRY_PAUSE`); a quick refusal in `HomeStaging` when a plan is plainly impossible (its 15 s budget). | They change production code, which you said needs your OK (plan, 1a). |
| 3 | 1a | `core.testACompulsoryTurnIsNotAQuestion` could zero its builder delays (about 40 s), as other tests do. | It changes the scenario's timing, which may be what the test is about. |
| 4 | 6 | A JaCoCo coverage run of the battery (a test-only jar in `resources_test`). | Downloading a file needs your OK.  The Phase 6 inventory is built from the code instead (which actions and events a test drives the way a user or the railway would); coverage figures can be added once JaCoCo is approved. |

## Issues found, for later

| # | Found in | What | Filed |
|---|---|---|---|
| 1 | Phase 0 | `findings.tsv` reports "not what the store renders" after a checkout, but only its line endings differ. The mirror check is line-ending sensitive. | Not filed. Phase 1c touches the mirror. |
| 2 | 1b (H0029) | On the frozen snapshot, 3 of your 11 "may run at once" pairs cannot run at once: every combination of copies shares rail.  BottomSecondary -> BottomMainC while BottomInner -> BottomInnerOtherside runs; the same while BottomInnerOtherside -> BottomInner runs; TopMainR2Pre -> TopMainR2 while BottomMainA -> TopMainR1 runs.  `testALockReachesTheRailBeingRunOver` now floors at 8 of 11 and prints the 3. | Not filed: either the list or the layout needs your decision. |
| 3 | 1b (H0130) | `getAutoLayout()` creates a Layout when there is none, so test guards of the form `if (model.getAutoLayout() == null) skip` can never fire. | Not filed; Phase 1b's second scan pass can look for the rest. |
| 4 | 1b (H0467) | `TailCrossedPrompt.isATurningCopyForTests` is a production hook that no test uses any more. | Not removed: refactor-only, and dead-code removal is listed rather than done mid-phase. |
| 5 | 1b | Twelve core claims whose feature, when broken, turns them into a skip rather than a red were counted as not hollow (the brief's rule).  The battery lists any class with a skip as "tested nothing", so such a defect is visible, but not as a failure. | Not filed. |
