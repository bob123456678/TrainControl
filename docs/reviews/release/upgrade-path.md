# Upgrade-path review of TrainControl {RELEASE}: first run, self-update, and old files meeting new code

Model: Sonnet. Read-only plus headless probes. Prefix `{PREFIX}`. Worktree `{WORKTREE}` (detached at `{DEV}`); scratch is the same path plus `-scratch`. Add a second worktree at `{LAST_TAG}` for the old side.

## Shared rules

- **Repository:** C:/Users/adamo/OneDrive/Documents/NetBeansProjects/TrainControl, a Java 8 Swing app that drives a Marklin model railway through a Central Station 2/3 and runs trains automatically.
- **Review** dev at `{DEV}` against `{LAST_TAG}`. Read with `git show {DEV}:<path>`, `git show {LAST_TAG}:<path>`, `git diff {LAST_TAG} {DEV} -- <path>`, `git grep`, `git log`. Builders change the main checkout, so don't rely on its working files.
- **Read first:** `CLAUDE.md`; `docs/reviews/README.md`; `docs/reference/behaviour.md`; `docs/reviews/RELEASE.md`; the orchestrator's `builder-rules.md`.
- **Never** read or write `cs2_sample_layout/`, LocDB.data, UIState.data or the owner's preferences. Never connect to a Central Station. No window tests unless the orchestrator grants a slot. Probes: your own detached worktree, headless, each run under about 4 minutes. Don't commit, merge or push.
- **Known items are not new:** `py -3 docs/manual-tests/triagedb.py issues`, `... findings <ref>`, and the earlier reports the orchestrator names. When the orchestrator says a calibration run is on, don't read tracker items filed or commented on after `{FREEZE}`.
- **Grades:** A = wrong behavior on the layout, or data silently lost; B = incorrect results, crashes or hangs in specific configurations; C = narrow; D = checked and clean.

## 1. First run

Follow start-up (`src/TrainControl.java` and what it calls) with no preferences, no layout folder, no locomotive database and no station reachable. Start from default preferences the way the tests do (D74; the `support` helpers), so the owner's are never touched: `-Dtraincontrol.dataDir` and `-Dtraincontrol.preferences` naming a fresh folder and node, and `-Dtraincontrol.anyReceivePort=true`, address 127.0.0.1. What does the user meet: questions and their order, a blank or stuck window, an exception, a wait with no sign of work? Compare `{LAST_TAG}`'s first run, built the same way. Probe headless up to the point a window would open; read the rest. Cover the first-launch demo layout, the address question and its Cancel, a saved address that doesn't answer, and the Resync or Exit question.

## 2. Self-update

Follow `JarUpdater`, `SelfUpdate` and `UpdateFlow` as a `{LAST_TAG}` user updating to `{RELEASE}`. Read the old side from `git show {LAST_TAG}:<path>`. Check how the new version is found, downloaded and checked, swapped in, and restarted; and what happens on a failed or partial download, a jar in use, a folder without write access, and a non-jar build (run from NetBeans). Check:
- `{RELEASE}`'s own updater can update to a later version;
- nothing changed since `{LAST_TAG}` breaks the old updater taking the new jar: file names, URL, manifest entries (`Main-Class`), jar layout, bundled libraries (no duplicates), Java target, the environment variable and preference keys. Diff `build.xml`, `manifest.mf`, `nbproject/` and `resources/`; read the `package-for-store` target;
- build a store-style jar from both trees by hand and run the old `JarUpdater` on real files in the sandbox: the swap, the rollback when the start marker is missing, and the failure note;
- the release checklist: the release name carries `v{RELEASE}`, asset 0 is the jar, the pre-release flag matches what `docs/reviews/RELEASE.md` says. Add what `docs/reviews/RELEASE.md` doesn't list.

## 3. Old files in the new version, new files in the old

This check becomes an automatic gate: a headless harness reads every layout in the repository with both builds, `{RELEASE}` converts and saves each, and `{LAST_TAG}` reads the saved copy back. Don't rebuild it. Instead:
- confirm the gate ran on `{DEV}` and was clean, and read its output file, not just its exit status;
- judge each difference it reports: intended by a named commit or changelog line, or a regression (layout, page, square, both values, likely code);
- compare the changelog's compatibility promises (what `{LAST_TAG}` can still open after `{RELEASE}` saves) with what the gate covers, and name what it doesn't (routes from the CS3's JSON, the autonomy setup JSON, window or wiring behavior). Probe those by hand where cheap.

If the gate did not run or its output is missing, that is a B and `Converged: no`.

## Report

To the file the orchestrator names: a title; `**Status:** open`; `**Prefix:** {PREFIX}`; `**Reviewed:** dev {DEV} against {LAST_TAG}, <date>`; a method paragraph with the probes run; a short list of what a user meets on a first run; each finding as `### {PREFIX}-A1 - <title>` then `| **Disposition** | open |`, with file:line, scenario, consequence, the commit that brought it, and a probe result or verification request. D headings for clean areas. Last time these were not reached: the start marker on a real window, the real GitHub release (asset order, digest), virus-scanner timing, OneDrive. Say which still stand.

End with the report line: counts by grade; each A and B in one line; what wasn't reached; `Converged: yes` or `Converged: no`.
