# Hooks

Two hooks enforce the standing rules that used to live only in instructions. Read this when a hook needs changing or has to be set up again on another machine; nothing else points here.

## What is in this folder

| File | What it is |
|---|---|
| `claude_guard.py` | The Claude Code `PreToolUse` hook. It judges every Bash, PowerShell, Edit, Write and NotebookEdit call that touches this repository, and every delete and every move of this repository's master wherever it runs, and answers deny, ask, a note, or nothing. |
| `pre-commit` | git's pre-commit hook. It runs `precommit.py` and lets the commit through, with a note, when there is no Python. |
| `precommit.py` | The pre-commit checks. They read what is staged, never the working tree. |
| `test_hooks.py` | 411 cases for both hooks. Run it after any change: `python docs/tools/hooks/test_hooks.py`. |

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

* a delete outside the scratch folders, in any session and any folder. Scratch is everything under the temp folder (`TEMP`, `TMP`, `LOCALAPPDATA\Temp`; Claude's scratchpads and worktrees are there), compared without case across `C:\`, `C:/` and `/c/` and after `..` is resolved; Git Bash's `/tmp` is the temp folder. The deletes are bash `rm`, `rmdir`, `unlink`, `shred` and `find` with `-delete` or `-exec rm`; PowerShell `Remove-Item` (`ri`, `rm`, `del`, `erase`, `rd`, `rmdir`) and `Clear-Content` (`clc`); cmd's `del`, `erase`, `rd` and `rmdir` through `cmd /c`; and `git worktree remove`. A relative path is judged from every folder the call may run in (the session's folder and any `cd`), except in a delete joined to the `cd`s before it by `&&` alone (`cd <scratch> && rm -rf x`, also through `Set-Location`, `Push-Location` or `pushd`): it runs only if they succeeded, so it is judged only where they took it, and a `cd` there that the guard cannot read asks. A `;`, `||`, `|`, newline, brace or subshell between them brings back every folder, so `cd <scratch>; rm -rf x` from elsewhere asks. PowerShell 5.1 has no `&&`, and `Set-Location <scratch>; if ($?) { Remove-Item x }` is not read as one: there, name the path in full. `$TEMP`, `$TMP`, `%TEMP%`, `$env:TEMP`, `$LOCALAPPDATA`, `$USERPROFILE`, `~` and `$HOME` are read, and so is a variable the same call set earlier (`S=/c/...;`, `S="..." &&`, `export S=...`, PowerShell `$S = '...'`), with `S=$(mktemp ...)`, `New-TemporaryFile` and `GetTempPath` as scratch. A pattern is judged by its literal leading folder, which may be a scratch folder itself (`rm -rf "$TEMP"/*`); a plain delete of the temp folder itself asks. A path still unreadable after that asks, and so does a delete fed its paths by `xargs` or a pipe, except `Get-ChildItem <folder> | Remove-Item`, judged by the folder. `git rm`, `>` redirections, `mv`, `cp` and the other writes do not count as deletes, and PowerShell's `Env:`, `Variable:`, `Function:` and `Alias:` drives are not files
* a move of this repository's master, from this checkout or any of its worktrees: a push to master (or `git push --all` or `--mirror`), `git branch` with `-f`/`--force` setting master, `-d`/`-D`/`-m`/`-M` naming it or `-c`/`-C` copying onto it, `git update-ref` of `refs/heads/master`, `git checkout -B master`, `git switch -C master`, and a fetch into master (`git fetch . release:master`). `docs/RELEASE.md`'s `git branch -f master release` asks
* a merge, rebase, cherry-pick or commit while the checkout is on master
* `git checkout -- <path>`, a checkout naming a file or folder that exists, one fed paths by `xargs`, or `git restore` of named files
* a shell command that writes or removes `LocDB.data`, `UIState.data` or `autonomy.json` (a redirection into it, a copy onto it, a move or removal of it)

An "ask" is a permission prompt for Adam in every mode, auto mode included: Claude Code's own check never decides it. So the guard asks only where Adam's rule says (deletes outside scratch, master, his live state, his uncommitted edits), and a call it may have read wrong asks rather than being refused.

It tells, without stopping the call, about a `.java` edit in `src/` or `test/` while a battery runs: the edit goes ahead with a `systemMessage` for Adam and the same text as `additionalContext` for Claude. `battery.sh` writes `%TEMP%\traincontrol-battery.lock.battery` with its pid and removes it on the way out, and a marker whose process is gone is ignored.

Other calls outside this repository pass untouched, apart from the Bypass rule. A shell command counts as inside it when its working folder is in the repository or the command names `TrainControl`. Any error inside the script lets the call through: a broken guard must never stop work.

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

3. **Check both**: run `python docs/tools/hooks/test_hooks.py` from the main checkout, with no battery running (411 ok, 0 missed; in a checkout whose folder is not named `TrainControl` the four cases that reach it through `~` or `$HOME` miss, and while a battery runs its cases are skipped and "Edit a .java, no battery" misses). Then, in a new session, ask Claude to run `git stash push`. It should be refused with "TrainControl guard: no git stash". Then ask it to run `rm -rf C:/guard-check-nothing-here`: Adam should get a permission prompt naming `C:\guard-check-nothing-here` as outside the scratch folders.

## Changing a rule

Edit `claude_guard.py` or `precommit.py`, add a case for the change to `test_hooks.py`, and run it. A rule that may be wrong in a case nobody foresaw should ask rather than deny: a guard with no way past gets turned off.
