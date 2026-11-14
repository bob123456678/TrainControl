# Concurrency review of TrainControl {RELEASE}: locks, threads and gates across features

Model: Opus. Read-only; a headless probe only for a claim that reading cannot settle. Prefix `{PREFIX}`.

## Shared rules

- **Repository:** C:/Users/adamo/OneDrive/Documents/NetBeansProjects/TrainControl, a Java 8 Swing app that drives a Marklin model railway through a Central Station 2/3 and runs trains automatically.
- **Review** dev at `{DEV}` against `{LAST_TAG}`. Read with `git show {DEV}:<path>`, `git diff {LAST_TAG} {DEV} -- <path>`, `git grep <pattern> {DEV}`, `git log`, `git blame`. Builders change the main checkout, so don't rely on its working files.
- **Read first:** `CLAUDE.md`; `docs/reviews/README.md` (grades, "Before calling something a finding"); `docs/reference/behaviour.md` (owner rulings); the orchestrator's `builder-rules.md`.
- **Never** read or write `cs2_sample_layout/`, LocDB.data, UIState.data or preferences. Never connect to a Central Station. No window tests unless the orchestrator grants a slot. Probes: your own detached worktree, headless, each run under about 4 minutes. Don't commit, merge or push.
- **Known items are not new:** `py -3 docs/manual-tests/triagedb.py issues`, `... findings <ref>`, and the earlier reports the orchestrator names. When the orchestrator says a calibration run is on, don't read tracker items filed or commented on after `{FREEZE}`.
- **Helpers:** Sonnet subagents may read for you; re-read every site you keep, and say so.
- **Grades:** A = wrong behavior on the layout, or data silently lost; B = incorrect results, crashes or hangs in specific configurations; C = narrow; D = checked and clean. Grade by consequence and say what mitigates it.

## Scope: what changed since `{LAST_TAG}`

Cover every lock, monitor, thread, timer, `SwingWorker`, executor, latch, atomic and `volatile` gate that was added, changed, moved or newly taken since `{LAST_TAG}`. Find them by grepping the primitives at both revisions and diffing the two lists:
`git grep -n -E "synchronized|ReentrantLock|CountDownLatch|Atomic|volatile|invokeAndWait|invokeLater|\.wait\(|notify|Executor|Timer|SwingWorker|CompletableFuture|new Thread" <rev> -- src`.
Also read `git diff --stat {LAST_TAG} {DEV}` for bodies moved between classes: a moved body keeps its lock state only if every caller does.

## Method

**Build a map** at `{DEV}` of each primitive in scope: the railway's monitor (`Layout` and `synchronized` on it; the repo has a guard listing the callers allowed to take it, `ALLOWED`: find it); `MarklinControlStation`'s; the proxy's; `AutonomySession`'s; locks and latches; gates such as `editorOnItsWay` and `syncInFlight`. For each, record which threads take it: event thread, sync worker, network readers, autonomy thread, timers, workers.

**Then find:**
- lock orders that can invert across features (a reader holds the proxy's lock and calls into the model while the event thread holds the model's lock and calls into the proxy);
- the event thread waiting on a worker that waits on the event thread (`invokeAndWait` under a lock);
- Swing touched off the event thread;
- state made shared without a guard: fields read and written on two threads;
- gates that can stay up forever after an exception, leaving a door permanently refused;
- the event thread taking a monitor that a thread doing network I/O can hold, so a blocked write freezes the window.

**Trace call by call. Don't stop at grep.** Round 1 of the last review grepped, gave a clean verdict and listed what it had not reached. Round 2 read those bodies and found two more: a timer on the event thread polling a locomotive's monitor, and a renderer thread reading combo boxes. So:
- include window bodies (panels, tabs, station windows, editors) and follow each call they make into the model and the railway;
- for a class that was moved or extracted, read its callers' lock state, not just its own;
- a class that was only grepped goes under "Not reached", and that makes `Converged: no`.

Prioritize new paths: sync and the owed sync; any editor gate; page-window and diagram redraws and their caches; sensor passes; station-file reads with retries; autonomy's Cancel and restore; anything on a timer.

## Report

To the file the orchestrator names: a title; `**Status:** open`; `**Prefix:** {PREFIX}`; `**Reviewed:** dev {DEV} against {LAST_TAG}, <date>`; a method paragraph; the lock and gate map as a table (its first cell must not look like a finding id); each finding as `### {PREFIX}-A1 - <title>` then `| **Disposition** | open |`, with file:line, who holds what, scenario, consequence, mitigation, the commit that brought it, and a verification request (a claim that holds the monitor on another thread and asserts the other side doesn't block, or a single-flag fix). D headings for clean areas, a "Not reached" list, and a Round 2 section if you read more.

End with the report line: counts by grade; each A and B in one line; what wasn't reached; `Converged: yes` or `Converged: no`.
