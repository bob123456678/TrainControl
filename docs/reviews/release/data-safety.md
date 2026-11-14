# Data-safety review of TrainControl {RELEASE}: what happens when a write fails

Model: Opus. Read-only plus headless probes. Prefix `{PREFIX}`. Worktree `{WORKTREE}` (detached at `{DEV}`); scratch is the same path plus `-scratch`.

## Shared rules

- **Repository:** C:/Users/adamo/OneDrive/Documents/NetBeansProjects/TrainControl, a Java 8 Swing app that drives a Marklin model railway through a Central Station 2/3 and runs trains automatically.
- **Review** dev at `{DEV}` against `{LAST_TAG}`. Read with `git show {DEV}:<path>`, `git diff {LAST_TAG} {DEV} -- <path>`, `git grep <pattern> {DEV}`, `git log`, `git blame`. Builders change the main checkout, so don't rely on its working files.
- **Read first:** `CLAUDE.md`; `docs/reviews/README.md` (grades, "Before calling something a finding"); `docs/reference/behaviour.md` (owner rulings); the orchestrator's `builder-rules.md`.
- **Never** read or write `cs2_sample_layout/`, LocDB.data, UIState.data or preferences. Never connect to a Central Station. No window tests unless the orchestrator grants a slot. Probes: your own detached worktree, headless, each run under about 4 minutes. Don't commit, merge or push.
- **Known items are not new:** `py -3 docs/manual-tests/triagedb.py issues`, `... findings <ref>`, and the earlier reports the orchestrator names. When the orchestrator says a calibration run is on, don't read tracker items filed or commented on after `{FREEZE}`.
- **Helpers:** Sonnet subagents may read for you; re-read what you keep.
- **Grades:** A = wrong behavior on the layout, or data silently lost; B = incorrect results, crashes or hangs in specific configurations; C = narrow; D = checked and clean. Grade by consequence and say what mitigates it.

## Scope: every door that writes the user's data

On the first run of this brief, cover every door. Afterwards, cover the doors changed or new since `{LAST_TAG}`, plus any door whose caller changed. List them:
- `git grep -n -E "FileOutputStream|Files\.(write|move|copy|delete)|renameTo|writeAtomically|zipInto|downloadFile|FileWriter|PrintWriter" {DEV} -- src`, and the same at `{LAST_TAG}`; diff the two lists;
- `git diff --stat {LAST_TAG} {DEV}` for files near those calls;
- `git log {LAST_TAG}..{DEV} --oneline -S"<write call>"` for each call name, to find who added or moved one.

Doors to expect: a layout's page files and index; the autonomy setup and configurations; the locomotive database; UI state; backups and Restore from Backup; Download Central Station Layout Files; one-time conversions of the user's files; the exports (CSV, log, counts, JSON); self-update's jar; any crash or edit note. Say how each writes: in place, temp then rename, backup first, forced to disk or not.

## Method

For each door, judge what the user is left with if the write fails part way: disk full, a read-only folder, a file locked by OneDrive or another program, the process killed mid-write. Name one outcome: old file intact, half-written file, nothing, or a mix across files that must agree (a layout's pages and its setup). Also judge:
- is the user told, and where (a dialog, or only the log);
- does a later save repair it, or make it permanent;
- does a backup taken after a failure report itself complete;
- for a one-time conversion, does the previous release still read the result;
- for a door that changed, how did `{LAST_TAG}` behave.

Probe headless where cheap: a read-only target folder; a file held open with an exclusive lock; a byte-range lock; a write that throws part way (a stub, or a target made unwritable after the first file). Earlier rounds probed `Util.writeAtomically` (held target, denied staging file, throwing body), `zipInto`, a page rename with the original held, and a backup read by Restore. Reuse the idea, not the files.

**Checks that found things before:**
- a silent failure at exit (the last save of the session);
- files rewritten in place on a hot path, where a torn write turns off a later safety;
- a backup or archive that keeps an empty entry for a file it could not read, and Restore writing it back as an empty file;
- closing the app during a backup;
- a staged write that never forces bytes to disk before the rename;
- a conversion that marks a layout converted after skipping an unreadable page.

## Report

To the file the orchestrator names: a title; `**Status:** open`; `**Prefix:** {PREFIX}`; `**Reviewed:** dev {DEV} against {LAST_TAG}, <date>`; a method paragraph (doors listed, how, probes run); each finding as `### {PREFIX}-A1 - <title>` then `| **Disposition** | open |`, with file:line, scenario, consequence, the commit that brought it, and a probe result or verification request. One D heading per clean door. Data silently lost or corrupted is A.

End with the report line: counts by grade; each A and B in one line; what wasn't reached; `Converged: yes` or `Converged: no`.
