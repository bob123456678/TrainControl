# Hooks

Two hooks enforce the standing rules that used to live only in instructions. Read this when a hook needs changing or has to be set up again on another machine; nothing else points here.

## What is in this folder

| File | What it is |
|---|---|
| `claude_guard.py` | The Claude Code `PreToolUse` hook. It judges every Bash, PowerShell, Edit, Write and NotebookEdit call that touches this repository, and answers deny, ask, or nothing. |
| `pre-commit` | git's pre-commit hook. It runs `precommit.py` and lets the commit through, with a note, when there is no Python. |
| `precommit.py` | The pre-commit checks. They read what is staged, never the working tree. |
| `test_hooks.py` | 49 cases for both hooks. Run it after any change: `python docs/tools/hooks/test_hooks.py`. |

## What they check

`claude_guard.py` refuses:

* `git add -A`, `--all`, `-u`, `.` or `*`; `git commit -a`; `git stash` (apart from `list` and `show`); `git clean -f`; `--no-verify`
* any git checkout, restore, reset, stash or clean of `cs2_sample_layout`
* `-ExecutionPolicy Bypass`, in any folder
* killing java by name (`taskkill /IM java.exe`, `Stop-Process -Name java`)
* a Write or Edit of the root `LocDB*.data`, `UIState*.data` or `autonomy.json`, or of anything in `cs2_sample_layout/`
* non-ASCII characters written into a `.properties` file
* an em dash, an en dash or a spaced hyphen in a line added to `Readme.md` or `Automation.md` (code spans and lines already there are left alone)

It asks first about:

* a push to master
* a merge, rebase, cherry-pick or commit while the checkout is on master
* `git reset --hard`, `git checkout -- <path>` or `git restore`
* a shell command that may write or remove the root `LocDB.data`, `UIState.data` or `autonomy.json`
* a `.java` edit in `src/` or `test/` while a battery runs: `battery.sh` writes `%TEMP%\traincontrol-battery.lock.battery` with its pid and removes it on the way out, and a marker whose process is gone is ignored

Calls outside this repository pass untouched, apart from the Bypass rule. A shell command counts as inside it when its working folder is in the repository or the command names `TrainControl`. Any error inside the script lets the call through: a broken guard must never stop work.

`precommit.py` stops a commit that stages:

* the root `LocDB*.data`, `UIState*.data` or `autonomy.json`
* a `.properties` file with a non-ASCII byte
* a `.java` file with a javadoc straight above another (`testJavadocsAreAttached`'s count, pinned at zero)
* an added line in `Readme.md` or `Automation.md` with an em dash, an en dash or a spaced hyphen

It also prints a note, without stopping the commit, when `cs2_sample_layout` has unstaged changes. The slower guards (`testCommentsStayLean` and the rest) stay in the battery.

## Setting them up again

1. **The Claude Code hook** is in the user settings, `C:\Users\adamo\.claude\settings.json`, so every session sees it whatever folder it starts in:

   ```json
   "hooks": {
     "PreToolUse": [
       {
         "matcher": "Bash|PowerShell|Edit|Write|NotebookEdit",
         "hooks": [
           {
             "type": "command",
             "command": "f=\"C:/Users/adamo/OneDrive/Documents/NetBeansProjects/TrainControl/docs/tools/hooks/claude_guard.py\"; if [ -f \"$f\" ]; then python \"$f\"; fi; exit 0",
             "timeout": 15
           }
         ]
       }
     ]
   }
   ```

   The `if [ -f ]` and `exit 0` matter. A checkout without this folder, such as master, must not turn every tool call into an error: Python exits 2 on a missing script, and exit 2 blocks the call. A settings change reaches a session only when that session starts.

2. **The git hook** needs one setting in the repository: `git config core.hooksPath docs/tools/hooks`. Worktrees share it.

3. **Check both**: run `python docs/tools/hooks/test_hooks.py` (49 ok, 0 missed). Then, in a new session, ask Claude to run `git stash push`. It should be refused with "TrainControl guard: no git stash".

## Changing a rule

Edit `claude_guard.py` or `precommit.py`, add a case for the change to `test_hooks.py`, and run it. A rule that may be wrong in a case nobody foresaw should ask rather than deny: a guard with no way past gets turned off.
