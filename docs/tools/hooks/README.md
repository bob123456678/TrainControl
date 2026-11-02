# Hooks

Two hooks enforce the standing rules that used to live only in instructions. Read this when a hook needs changing or has to be set up again on another machine; nothing else points here.

## What is in this folder

| File | What it is |
|---|---|
| `claude_guard.py` | The Claude Code `PreToolUse` hook. It judges every Bash, PowerShell, Edit, Write and NotebookEdit call that touches this repository, and answers deny, ask, or nothing. |
| `pre-commit` | git's pre-commit hook. It runs `precommit.py` and lets the commit through, with a note, when there is no Python. |
| `precommit.py` | The pre-commit checks. They read what is staged, never the working tree. |
| `test_hooks.py` | 221 cases for both hooks. Run it after any change: `python docs/tools/hooks/test_hooks.py`. |

## What they check

It reads a shell command as the simple commands it is made of (split at `;`, `&&`, `|`, newlines and subshells), each as words with their quotes taken off. So text inside a quoted argument, a heredoc or a PowerShell here-string (a commit message, a grep pattern) is never read as a command, while `bash -c "..."`, `sh -c`, `cmd /c`, `powershell -Command`, `eval` and `Invoke-Expression` have their script judged as commands of their own. git's global options (`-C`, `-c k=v`, `--git-dir=...`) and a quoted or full-path `git` are seen through. A `cd` earlier in the call only adds a folder the rest may run in, as a `cd` can fail and a subshell ends: a command is judged if any of those folders is this checkout. git's `-C`, which git itself obeys, is the one way to run a whole-tree revert in a scratch clone from here (`git -C <clone> reset --hard`, the clone named literally). A folder the guard cannot read literally (a variable, `~`, `%VAR%`, an 8.3 short name), whether from a `cd`, `Push-Location`, `env -C`, `env --chdir` or `git -C`, is a folder it cannot place: a git command there is judged as if it ran here, and so is one that climbs out of it with `-C ..`; one that names `cs2_sample_layout` stands for the layout. A write of a path the guard cannot read that names `cs2_sample_layout`, or of a relative path after a `cd` into the layout or into a folder it cannot place, counts as a write into the layout. What it does not read: a folder made by `$(...)` or `Join-Path`, and `GIT_DIR` set in an earlier statement (`export`, `$env:`); the guard catches slips, and these are not ones a session types by accident. Every command in a call is judged before the answer: a deny anywhere wins over an ask before it.

`claude_guard.py` refuses:

* `git add` with `-A`, `--all`, `-u`, `--update` (also in a cluster such as `-Av`), `.`, `*` or `:/`; `git commit` with `-a` or `--all` (also `-am`, `-av`); `git stash` (apart from `list` and `show`); `git clean -f` or `--force`
* `--no-verify`, `git commit -n`, and `git -c core.hooksPath=...`
* a whole-tree revert, which reverts `cs2_sample_layout` without naming it: `git checkout` of `.` or `:/` (`git checkout -- .`, `git checkout HEAD .`), `git checkout -f`, `git switch -f` or `--discard-changes`, `git restore .` (other than `--staged` alone), `git reset --hard`
* any git checkout, restore, reset, stash, clean, switch, rm or mv that names `cs2_sample_layout`, or runs inside it
* a shell write into `cs2_sample_layout`: a `>` or `>>` into it, `cp`, `Copy-Item` and the like with it as destination, `mv`, `rm`, `Set-Content`, `Out-File`, `tee` or `sed -i` naming it, or any of these with a relative path after a `cd` into it. Reads (`cat`, `grep`, `git diff`, `git show ... > elsewhere`) pass
* a powershell or pwsh started with `-ExecutionPolicy Bypass` (or `-ep bypass`), or `Set-ExecutionPolicy Bypass`, in any folder. A search for the text passes
* killing or waiting on java by name, by any common spelling: a command that names `java` or `javaw` as an argument and kills anywhere in it, across pipes (`Get-Process java | Stop-Process`, `gps java | kill`, `spps`, `pkill`, `killall`, `taskkill /IM java.exe`, `wmic ... terminate`, `.Kill()`, `Wait-Process`). `Get-Process java` alone passes, and so does a `java` that is itself the command being run
* a Write or Edit of the root `LocDB*.data`, `UIState*.data` or `autonomy.json`, or of anything in `cs2_sample_layout/`
* non-ASCII characters written into a `.properties` file
* an em dash, an en dash or a spaced hyphen in a line added to `Readme.md` or `Automation.md`. Lines already there, code spans, image alt text, a bracketed date range such as `[11/2025 - 3/2026]` and a table cell holding only keys such as `+ and -` are left alone

Commands such as `grep`, `rg`, `Select-String`, `echo` and `cat` are readers: a rule's text among their words never counts as a call.

It asks first about:

* a push to master, or `git push --all` or `--mirror`
* a merge, rebase, cherry-pick or commit while the checkout is on master
* `git checkout -- <path>`, a checkout naming a file or folder that exists, one fed paths by `xargs`, or `git restore` of named files
* a shell command that writes or removes `LocDB.data`, `UIState.data` or `autonomy.json` (a redirection into it, a copy onto it, a move or removal of it)
* a `.java` edit in `src/` or `test/` while a battery runs: `battery.sh` writes `%TEMP%\traincontrol-battery.lock.battery` with its pid and removes it on the way out, and a marker whose process is gone is ignored

An "ask" reaches you only in a session that prompts for permission. In auto mode, Claude Code's own check decides it instead, so treat ask as a speed bump and deny as the guard.

Calls outside this repository pass untouched, apart from the Bypass rule. A shell command counts as inside it when its working folder is in the repository or the command names `TrainControl`. Any error inside the script lets the call through: a broken guard must never stop work.

The guard catches slips, not intent: a script, an alias or a variable can still hide a command. It judges only the tools its matcher names (below); a tool outside it, such as the terminal tool `mcp__terminal__run_in_terminal`, is not judged at all.

`precommit.py` stops a commit that stages:

* the root `LocDB*.data`, `UIState*.data` or `autonomy.json`
* a `.properties` file with a non-ASCII byte
* a `.java` file with a javadoc straight above another (`testJavadocsAreAttached`'s count, pinned at zero)
* an added line in `Readme.md` or `Automation.md` with an em dash, an en dash or a spaced hyphen, judged as the Claude hook judges it (dates in brackets, key table cells and image alt text are left alone)

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

3. **Check both**: run `python docs/tools/hooks/test_hooks.py` (221 ok, 0 missed). Then, in a new session, ask Claude to run `git stash push`. It should be refused with "TrainControl guard: no git stash".

## Changing a rule

Edit `claude_guard.py` or `precommit.py`, add a case for the change to `test_hooks.py`, and run it. A rule that may be wrong in a case nobody foresaw should ask rather than deny: a guard with no way past gets turned off.
