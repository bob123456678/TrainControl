# Usage: python docs/tools/hooks/test_hooks.py
#
# Feeds claude_guard.py the calls it must refuse, ask about and let through, and checks each answer; then runs
# precommit.py against a throwaway repository for each thing it must refuse.  Prints one line per case and exits 1 on
# any miss.  Touches nothing in this repository.
import json, os, shutil, subprocess, sys, tempfile, time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
GUARD = os.path.join(HERE, 'claude_guard.py')
PRECOMMIT = os.path.join(HERE, 'precommit.py')
R = ROOT.replace('\\', '/')
MSYS = '/' + R[0].lower() + R[2:]
misses = 0


def answer(tool, ti, cwd=R):
    out = subprocess.run([sys.executable, GUARD], input=json.dumps({'tool_name': tool, 'cwd': cwd, 'tool_input': ti}),
                         capture_output=True, text=True).stdout.strip()
    return json.loads(out) if out else {}


def guard(tool, ti, cwd=R):
    """deny, ask, note (no decision, a message for Adam and Claude) or pass."""
    out = answer(tool, ti, cwd)
    return (out.get('hookSpecificOutput') or {}).get('permissionDecision') or ('note' if out else 'pass')


def check(name, got, want):
    global misses
    ok = got == want
    misses += not ok
    print('%-4s %-44s %s%s' % ('ok' if ok else 'MISS', name, got, '' if ok else '  (wanted ' + want + ')'))


def sh(cmd, cwd=R):
    return guard('Bash', {'command': cmd}, cwd)


def edit(path, new, tool='Edit'):
    ti = {'file_path': path, 'old_string': 'a', 'new_string': new} if tool == 'Edit' else {'file_path': path,
                                                                                            'content': new}
    return guard(tool, ti)


check('git add -A', sh('git add -A'), 'deny')
check('git add . after a cd', sh('cd ' + MSYS + ' && git add .', cwd='C:/elsewhere'), 'deny')
check('git add -u', sh('git add -u'), 'deny')
check('git add by name', sh('git add src/a/B.java test/c/D.java'), 'pass')
check('git add -A in another repo', sh('git add -A', cwd='C:/elsewhere'), 'pass')
check('git commit -am', sh('git commit -am x'), 'deny')
check('git commit -F (on dev)', sh('git commit -q -F msg.txt'), 'pass')
check('git stash push', sh('git stash push'), 'deny')
check('git stash list', sh('git stash list'), 'pass')
check('git clean -fd', sh('git clean -fd'), 'deny')
check('git commit --no-verify', sh('git commit --no-verify -m x'), 'deny')
check('git checkout the live layout', sh('git checkout -- cs2_sample_layout/gleisbild.cs2'), 'deny')
check('git push origin dev', sh('git push origin dev'), 'pass')
check('git push origin master', sh('git push origin master'), 'ask')
check('git push origin dev:master', sh('git push origin dev:master'), 'ask')
check('git reset --hard', sh('git reset --hard HEAD'), 'deny')
check('git restore a file', sh('git restore src/a/B.java'), 'ask')
check('-ExecutionPolicy Bypass, anywhere', guard('PowerShell', {'command': 'powershell -ExecutionPolicy Bypass -File a.ps1'},
                                                 'C:/elsewhere'), 'deny')
check('taskkill /IM java.exe', guard('PowerShell', {'command': 'taskkill /F /IM java.exe'}), 'deny')
check('Stop-Process -Name java', guard('PowerShell', {'command': 'Stop-Process -Name java -Force'}), 'deny')
check('kill $JVM', sh('kill $JVM'), 'pass')
check('cp over LocDB.data', sh('cp test/LocDB.data LocDB.data'), 'ask')
check('ls LocDB.data', sh('ls -l LocDB.data'), 'pass')
check('Write the root LocDB.data', edit(R + '/LocDB.data', 'x', 'Write'), 'deny')
check('Write autonomy.json', edit(R + '/autonomy.json', 'x', 'Write'), 'deny')
check('Write test/LocDB.data', edit(R + '/test/LocDB.data', 'x', 'Write'), 'pass')
check('Edit the live layout', edit(R + '/cs2_sample_layout/gleisbild.cs2', 'b'), 'deny')
check('Edit a bundle, non-ASCII', edit(R + '/src/b/messages_de.properties', 'x=Gr\u00fc\u00dfe'), 'deny')
check('Edit a bundle, escaped', edit(R + '/src/b/messages_de.properties', 'x=Gr\\u00FCn'), 'pass')
check('Readme em dash', edit(R + '/Readme.md', 'Trains \u2014 fast'), 'deny')
check('Readme spaced hyphen', edit(R + '/Readme.md', 'Trains - fast'), 'deny')
check('Readme list items', edit(R + '/Readme.md', '- a list item\n  - nested'), 'pass')
check('Readme dash in a code span', edit(R + '/Readme.md', 'the `1 - Main` page'), 'pass')
kept = '* v2.6.x [11/2025 - 3/2026]'
check('Readme, one of Adam\'s lines kept', guard('Edit', {'file_path': R + '/Readme.md', 'old_string': kept,
                                                          'new_string': kept + '\n* a new line'}), 'pass')
check('Readme, a new date range line', edit(R + '/Readme.md', '* v3.0.x [3/2026 - 11/2026]'), 'pass')
check('Automation key table row', edit(R + '/Automation.md', '| + and - | Next page |\n| - | none |'), 'pass')
check('Automation image alt text', edit(R + '/Automation.md', '![Yes - Trains Can Stop Here](assets/a.png)'), 'pass')
check('Automation table cell, prose dash', edit(R + '/Automation.md', '| x | Trains - fast |'), 'deny')
check('Edit a .java, no battery', edit(R + '/src/a/B.java', 'b'), 'pass')
check('Write LocDB.data in another folder', guard('Write', {'file_path': 'C:/elsewhere/LocDB.data', 'content': 'x'},
                                                  'C:/elsewhere'), 'pass')

def ps(cmd, cwd=R):
    return guard('PowerShell', {'command': cmd}, cwd)


# Whole-tree reverts, which revert the live layout without naming it
for cmd in ['git checkout HEAD -- .', 'git checkout HEAD .', 'git checkout . && git status', 'git checkout -- .',
            'git checkout -f', 'git checkout -f dev', 'git switch --discard-changes dev', 'git switch -f dev',
            'git restore .', 'git restore --staged --worktree .', 'git reset -q --hard', 'git reset --hard',
            'git clean --force', 'git clean -f src', 'git clean -xdf', 'git checkout -- :/']:
    check(cmd, sh(cmd), 'deny')
check('git checkout -- a named file', sh('git checkout -- src/a/B.java'), 'ask')
check('git checkout HEAD -- a named file', sh('git checkout HEAD -- src/a/B.java'), 'ask')
check('git checkout a branch', sh('git checkout dev'), 'pass')
check('git checkout -b', sh('git checkout -b fix-1'), 'pass')
check('git switch a branch', sh('git switch dev'), 'pass')
check('git restore --staged .', sh('git restore --staged .'), 'ask')
check('git reset, mixed', sh('git reset src/a/B.java'), 'pass')
check('git clean -n', sh('git clean -nd'), 'pass')
check('branch switch after a layout log', sh('git log -- cs2_sample_layout && git checkout dev'), 'pass')
check('git add the live layout', sh('git add cs2_sample_layout/gleisbild.cs2 src/a/B.java'), 'pass')

# Shell writes into the live layout, and reads of it
for cmd in ['cp /tmp/x.cs2 cs2_sample_layout/gleisbild.cs2', 'echo x > cs2_sample_layout/gleisbild.cs2',
            'git show HEAD:cs2_sample_layout/a.cs2 > cs2_sample_layout/a.cs2', 'cat a >> "cs2_sample_layout/b c.cs2"',
            'mv cs2_sample_layout/a.cs2 /tmp/', 'sed -i s/a/b/ cs2_sample_layout/a.cs2', 'cat a | tee cs2_sample_layout/a',
            'cp -t cs2_sample_layout a.cs2', 'rm -rf cs2_sample_layout/fahrstrassen']:
    check(cmd, sh(cmd), 'deny')
for cmd in ['Copy-Item C:/tmp/a.cs2 -Destination cs2_sample_layout/a.cs2', 'Set-Content cs2_sample_layout/a.cs2 x',
            'Get-Content a | Out-File -FilePath cs2_sample_layout/a.cs2', 'Move-Item cs2_sample_layout/a.cs2 C:/tmp']:
    check(cmd, ps(cmd), 'deny')
for cmd in ['cat cs2_sample_layout/gleisbild.cs2', 'grep -rn x cs2_sample_layout', 'git diff -- cs2_sample_layout',
            'git log --oneline -3 -- cs2_sample_layout', 'git show HEAD:cs2_sample_layout/a.cs2 > /tmp/a.cs2',
            'cp -r cs2_sample_layout /tmp/backup', 'sed -n 1,5p cs2_sample_layout/a.cs2',
            'git diff cs2_sample_layout 2>/dev/null | head']:
    check(cmd, sh(cmd), 'pass')
check('Copy-Item out of the layout', ps('Copy-Item cs2_sample_layout/a.cs2 -Destination C:/tmp/a.cs2'), 'pass')

# Killing java by any common spelling; reading the process list passes
for cmd in ['Get-Process java | Stop-Process', 'Get-Process -Name javaw | Stop-Process -Force', 'gps java | kill',
            'spps -Name java', 'Stop-Process -ProcessName java', 'Stop-Process -Id (Get-Process java).Id',
            '(Get-Process javaw).Kill()', 'Get-Process java | Wait-Process',
            'wmic process where "name=\'java.exe\'" call terminate', 'taskkill /F /IM javaw.exe']:
    check(cmd, ps(cmd), 'deny')
for cmd in ['pkill java', 'killall java', 'pkill -f java', 'kill -9 $(pgrep java)',
            'wmic process where "name like \'%java%\'" delete']:
    check(cmd, sh(cmd), 'deny')
check('Get-Process java', ps('Get-Process java'), 'pass')
check('tasklist | findstr java', ps('tasklist | findstr java'), 'pass')
check('java run, then kill $!', sh('"$JAVA" -cp build Main & JVM=$!; sleep 5; kill $JVM'), 'pass')
check('kill beside a java folder', sh('ls src/main/java; kill $JVM'), 'pass')
check('grep for a kill line', sh('grep -n "taskkill /IM java.exe" docs/tools/battery.sh'), 'pass')

# Rule text that is only data: commit messages, heredocs, searches, later lines
check('commit -m naming git stash', sh('git commit -m "the guard now refuses git stash"'), 'pass')
check('commit -m naming git add -A', sh('git commit -m "no git add -A or --no-verify; -a is out"'), 'pass')
check('commit, heredoc message', sh("git commit -F - <<'EOF'\nRefuse git stash, git add -A and --no-verify\n"
                                    "* git add .\nEOF"), 'pass')
check('commit, $(cat heredoc) message', sh('git commit -m "$(cat <<\'EOF\'\nno git stash -u .\n* bullet\nEOF\n)"'),
      'pass')
check('commit, PowerShell here-string', ps("git commit -m @'\nno git stash; git add -A\n'@"), 'pass')
check('git add by name, later lines', sh('git add src/A.java\ngit commit -F msg.txt\n* a bullet -u .'), 'pass')
check('grep for ExecutionPolicy Bypass', sh('grep -n "ExecutionPolicy Bypass" docs/tools/battery.sh'), 'pass')
check('Select-String for it', ps('Select-String -Path docs/tools/battery.sh -Pattern "-ExecutionPolicy Bypass"'),
      'pass')
check('rg for powershell -ExecutionPolicy Bypass', sh('rg "powershell -ExecutionPolicy Bypass" docs'), 'pass')
check('read LocDB.data, stderr redirected', sh('grep -c x LocDB.data 2>/dev/null'), 'pass')
check('copy LocDB.data out', sh('cp LocDB.data /tmp/LocDB.backup'), 'pass')
check('LocDB.data > redirect', sh('cat test/LocDB.data > LocDB.data'), 'ask')
check('Remove-Item LocDB.data', ps('Remove-Item LocDB.data'), 'ask')
check('pwsh -ep bypass', sh('pwsh -NoProfile -ep bypass -File x.ps1', 'C:/elsewhere'), 'deny')
check('Set-ExecutionPolicy Bypass', ps('Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process', 'C:/elsewhere'),
      'deny')
check('cmd /c powershell Bypass', sh('cmd //c powershell.exe -ExecutionPolicy Bypass -File x.ps1'), 'deny')

# Ways round: wrappers, global options and short forms
for cmd in ['git -c core.x=y stash', 'git --git-dir=.git stash', '"git" stash', 'bash -c "git stash"',
            "sh -c 'git stash push'", 'cd x && (git stash)', 'git commit -n -m x', 'git commit -nm x',
            'git -c core.hooksPath=/dev/null commit -m x', 'git add -Av', 'git add -vA', 'git add :/', 'git add --all',
            'git commit -a -m x', 'git commit -av', 'git commit --all -m x', 'git push --no-verify origin dev',
            'eval "git stash"', '/usr/bin/git stash', 'git -C . stash']:
    check(cmd, sh(cmd), 'deny')
check('& "git" stash', ps('& "git" stash'), 'deny')
check('powershell -Command git stash', ps('powershell -NoProfile -Command "git stash"'), 'deny')
check('git push --all', sh('git push --all origin'), 'ask')
check('git push --mirror', sh('git push --mirror origin'), 'ask')
check('git commit -m "-a thing"', sh('git commit -m "-a and -n are refused"'), 'pass')
check('git commit -S key', sh('git commit -Sabc -F msg.txt'), 'pass')
check('git commit -C ref', sh('git commit -C HEAD~1'), 'pass')

# A deny later in the call wins over an ask before it
for cmd in ['git checkout -- src/a/B.java && git clean -fd', 'git restore x; git reset --hard',
            'git restore x && echo y > cs2_sample_layout/a.cs2', 'git restore x; pkill java']:
    check(cmd, sh(cmd), 'deny')
check('git restore x; Get-Process java | Stop-Process', ps('git restore x; Get-Process java | Stop-Process'), 'deny')
check('git restore one file', sh('git restore src/x.java'), 'ask')

# File reverts by other spellings; a branch switch passes
check('git checkout <existing file>', sh('git checkout Readme.md'), 'ask')
check('xargs git checkout --', sh('git diff --name-only | xargs git checkout --'), 'ask')
check('timeout git reset --hard', sh('timeout 60 git reset --hard'), 'deny')
check('git checkout <branch>', sh('git checkout dev'), 'pass')

# A cd only adds folders a command may run in: it can fail, a subshell ends.  git -C is where git really runs.
for cmd in ['git -C /tmp/clone reset --hard', 'cp x.cs2 /tmp/cs2_sample_layout_backup/x.cs2',
            'echo x > /tmp/cs2_sample_layout/a.cs2', 'git checkout dev']:
    check(cmd, sh(cmd), 'pass')
for cmd in ['cd /tmp/clone && git reset --hard', 'cd /tmp/clone; git clean -fd', '(cd /tmp/x && make); git reset --hard',
            'pushd /tmp/x; popd; git reset --hard', 'cd -; git reset --hard', 'cd; git stash',
            'echo x > $PWD/cs2_sample_layout/a.cs2',
            'cp x.cs2 ~/TrainControl/cs2_sample_layout/a.cs2']:
    check(cmd, sh(cmd), 'deny')
check('Set-Location then ; reset --hard', ps('Set-Location C:/scratch/clnoe; git reset --hard'), 'deny')
check('cd into the repo, then reset --hard', sh('cd /tmp && cd ' + MSYS + ' && git reset --hard', cwd='C:/elsewhere'),
      'deny')
check('cmd /c cd /d into the repo', ps('cmd /c "cd /d ' + R + ' && git reset --hard"', cwd='C:/elsewhere'), 'deny')

# From a session outside the checkout: a path the guard cannot read, Push-Location, a bare drive, env -C
HOME_REPO = '~/OneDrive/Documents/NetBeansProjects/TrainControl'
for cmd in ['git -C ' + HOME_REPO + ' reset --hard', 'cd ' + HOME_REPO + ' && git reset --hard',
            'cd $HOME/OneDrive/Documents/NetBeansProjects/TrainControl && git stash',
            'cd ' + HOME_REPO + ' && echo x > cs2_sample_layout/a.cs2',
            'cd /c && cd ' + R[3:] + ' && git reset --hard', 'env -C ' + MSYS + ' git reset --hard']:
    check(cmd, sh(cmd, cwd='C:/elsewhere'), 'deny')
check('Push-Location into the repo', ps('Push-Location ' + R + '; git reset --hard', cwd='C:/elsewhere'), 'deny')
check('Push-Location src; git -C ..', ps('Push-Location src; git -C .. reset --hard'), 'deny')
check('cd cs2_sample_layout; checkout a file', sh('cd cs2_sample_layout && git checkout gleisbild.cs2'), 'deny')
check('cd src; checkout an existing file', sh('cd src && git checkout org'), 'ask')
check('the layout folder by its 8.3 name', sh('echo x > CS2_SA~1/a.cs2'), 'deny')
check('a scratch clone through -C outside', sh('git -C C:/tc-scratch/TrainControl-clone reset --hard',
                                               cwd='C:/elsewhere'), 'pass')

# A folder the guard cannot place stays unplaced: -C .. from it is still judged here
check('cd "$PWD/test"; git -C .. clean', sh('cd "$PWD/test" && git -C .. clean -fdx'), 'deny')
check('cd $X; git checkout <path>', sh('cd "$SCRATCH" && git checkout Readme.md'), 'ask')

# Inside the layout folder, by name or through a variable, a write or revert is denied
for cmd in ['cd cs2_sample_layout && rm -f *.cs2', 'cd cs2_sample_layout && sed -i s/a/b/ gleisbild.cs2',
            'cd cs2_sample_layout && cp /tmp/x.cs2 .', 'cd "$PWD/cs2_sample_layout" && git checkout gleisbild.cs2']:
    check(cmd, sh(cmd), 'deny')
check('cd cs2_sample_layout; read it', sh('cd cs2_sample_layout && ls && cat gleisbild.cs2'), 'pass')

# A Git Bash path in -C, and env --chdir
check('git -C /c/... checkout <file> outside', sh('git -C ' + MSYS + ' checkout Readme.md', cwd='C:/elsewhere'), 'ask')
check('env --chdir=<repo> elsewhere', sh('env --chdir=' + MSYS + ' git reset --hard', cwd='C:/elsewhere'), 'deny')

# Forty relative cds in one call are followed quickly, and still judged
many = ' && '.join(['cd a%d' % k for k in range(40)])
started = time.time()
got = sh(many + ' && git reset --hard')
check('forty relative cds, judged in time', got if time.time() - started < 5 else 'slow', 'deny')
check('git -C <repo> reset --hard elsewhere', sh('git -C ' + MSYS + ' reset --hard', cwd='C:/elsewhere'), 'deny')
check('--work-tree <repo> elsewhere', sh('git --work-tree=' + MSYS + ' reset --hard', cwd='C:/elsewhere'), 'deny')

# checkout of a named folder, several paths, or paths from xargs
for cmd in ['git checkout src', 'git checkout HEAD src test', 'git diff --name-only | xargs git checkout']:
    check(cmd, sh(cmd), 'ask')

# Deletes outside the scratch folders ask, in any session; inside them, and writes that are not deletes, pass.
# OUT is outside both the scratch folders and this checkout; SCR is a folder in scratch.
TEMP = (os.environ.get('TEMP') or os.environ.get('TMP')).replace('\\', '/')
TMSYS = '/' + TEMP[0].lower() + TEMP[2:]
TWIN = TEMP.replace('/', '\\')
OUT, SCR = 'C:/elsewhere', TEMP + '/tc-guard-test'
for cmd in ['rm -rf C:/elsewhere/build', 'rm -rf build', 'rm -f /c/Users/x/a.txt', 'rmdir /c/x/y', 'unlink C:/x/a',
            'shred -u -n 3 C:/x/a', "find C:/x -name '*.class' -delete", 'find /c/x -type f -exec rm -f {} \\;',
            'rm -rf $TEMP/../x', 'rm -rf ~/x', 'rm -rf $HOME/x', 'rm -rf "$FOO/x"', 'rm -rf $(cat list)',
            'S=/c/x; rm -rf "$S/y"', 'S="$FOO" && rm -rf "$S"', 'ls | xargs rm -f', 'ls | xargs -I {} rm -rf {}',
            'rm -rf C:/Users/x/*', 'rm -rf /tmp/*/../../x', 'rm -rf $TEMP', 'rm -rf "' + TEMP + '"',
            'bash -c "rm -rf /c/x"', 'git worktree remove C:/elsewhere/wt', 'git worktree remove --force ../wt',
            'git -C C:/x worktree remove wt', 'cd ~ && rm -rf x', 'env -C /c/x rm -rf y',
            'cmd //c "rd /s /q C:\\x"', 'rm -rf /usr/bin']:
    check(cmd, sh(cmd, cwd=OUT), 'ask')
for cmd in ['cd /c/x; rm -rf y', 'cd /tmp/x && cd ..\\..\\.. && rm -rf y', 'cd; rm -rf x', 'cd $FOO && rm -rf y',
            'rm -rf ../../x']:
    check(cmd + ', from scratch', sh(cmd, cwd=SCR), 'ask')
# A delete joined by && alone to the cds before it runs only where they took it; any other boundary, every folder
for cmd in ['cd /tmp/x && rm -rf build', 'cd ' + TEMP + '/a && cd b && rm -rf x', 'cd /tmp/a && cd ../b && rm -rf x',
            'cd "$TEMP/run" && make && rm -rf out', 'cd /tmp/x &&\nrm -rf y', 'S=/tmp/q && cd "$S" && rm -rf y',
            'bash -c "cd /tmp/x && rm -rf y"', 'cmd //c "cd /d ' + TWIN + '\\x && rd /s /q y"',
            'cd C:/elsewhere && cd /tmp/x && rm -rf y']:
    check('&& chain: ' + cmd, sh(cmd, cwd=OUT), 'pass')
for cmd in ['cd /tmp/x; rm -rf build', 'cd C:/x && rm y', '(cd /tmp/x) && rm y', 'cd /tmp && cd .. && rm -rf x',
            'cd $UNKNOWN && rm y', 'cd /tmp/x || rm -rf y', 'cd /tmp/x & rm -rf y', 'cd /tmp/x && ls | xargs rm',
            'cd /tmp/x && rm -rf y; rm -rf z', 'cd /tmp/x\nrm -rf y', 'cd /tmp/x && { rm -rf y; }',
            'cd /tmp/x && popd && rm -rf y', 'cd $UNKNOWN && cd sub && rm y', 'cd /tmp/x && rm -rf ../../y',
            'cd C:/x && rm -rf /tmp/ok && rm -rf y']:
    check('&& chain: ' + cmd, sh(cmd, cwd=OUT), 'ask')
check('&& chain: Set-Location; if ($?) is not read', ps('Set-Location $env:TEMP\\x; if ($?) { Remove-Item y }',
                                                        cwd=OUT), 'ask')
# The repository rules keep "a cd only adds folders"
check('&& chain: cd elsewhere && git reset --hard', sh('cd /tmp/clone && git reset --hard'), 'deny')
for cmd in ['Remove-Item -Recurse -Force C:\\x\\y', 'ri C:\\x', 'del C:\\x\\a.txt', 'erase C:\\x\\a.txt', 'rd C:\\x',
            'rmdir C:\\x', 'rm -r -fo C:\\x', 'Clear-Content C:\\x\\log.txt', "Remove-Item -LiteralPath 'C:\\x' -Recurse",
            'Remove-Item -Path:C:\\x', 'Remove-Item $env:USERPROFILE\\x', "$S = 'C:\\x'; Remove-Item -Recurse $S",
            '$S = Get-Location; Remove-Item $S', 'Get-ChildItem C:\\x -Recurse | Remove-Item',
            'Get-Content list.txt | Remove-Item', 'Remove-Item a.txt -ErrorAction SilentlyContinue',
            'Remove-Item HKCU:\\Software\\x', 'Remove-Item "$env:TEMP\\a",C:\\x', 'cmd /c del /f /q C:\\x\\a.txt',
            'cmd /c "rd /s /q %USERPROFILE%\\x"', 'powershell -Command "Remove-Item C:\\x"']:
    check('PowerShell: ' + cmd, ps(cmd, cwd=OUT), 'ask')
for cmd in ['rm -rf /tmp/x', 'rm -rf ' + TEMP + '/x', "rm -rf '" + TWIN.upper() + "\\x'", 'rm -rf ' + TMSYS.lower() + '/x',
            'rm -rf $TEMP/x', 'rm -rf "$TMP/x"', 'rm -rf ${TEMP}/x', 'rm -rf ~/AppData/Local/Temp/x',
            'rm -rf $HOME/AppData/Local/Temp/x', 'S=/tmp/run1; rm -rf "$S"', 'S="$TEMP/run" && rm -rf "$S/out"',
            'export S=/tmp/a; rm -rf $S', 'T=$(mktemp -d); rm -rf "$T"', 'T="$(mktemp)" && rm -f "$T"',
            'rm -rf /tmp/foo*', 'rm -rf "$TEMP"/*', 'rm -rf /tmp/x{1,2}', "find /tmp/x -name '*.class' -delete",
            "find C:/x -name '*.class'", 'find C:/x -exec grep -l y {} +', 'echo x > C:/elsewhere/a.txt',
            'mv C:/x/a C:/x/b', 'cp a C:/x/b', 'git rm src/a.txt', 'git rm -r --cached x', 'grep -rn "rm -rf" C:/x',
            'echo rm -rf C:/x', 'git commit -m "rm -rf C:/x and Remove-Item C:/y"', 'git worktree list',
            'git worktree add C:/x/wt', 'git worktree remove ' + TEMP + '/tc-x', 'git worktree remove --force /tmp/tc-x',
            'rm -rf /tmp/x 2>/dev/null', 'bash -c "rm -rf /tmp/x"', 'cmd //c "rd /s /q %TEMP%\\x"',
            'rm']:
    check(cmd, sh(cmd, cwd=OUT), 'pass')
for cmd in ['rm -rf build', 'find . -type f -delete', 'cd /tmp && rm -rf x', 'rm -rf ./out/*',
            'cd /tmp/x && rm -rf ../y']:
    check(cmd + ', from scratch', sh(cmd, cwd=SCR), 'pass')
for cmd in ['Remove-Item -Recurse -Force $env:TEMP\\x', 'Remove-Item "$env:TMP\\x"', 'ri ' + TWIN + '\\x',
            '$S = "$env:TEMP\\run"; Remove-Item -Recurse $S', "$S='" + TWIN + "\\run'; Remove-Item $S",
            '$t = New-TemporaryFile; Remove-Item $t.FullName', 'Remove-Item Env:FOO', 'Remove-Item Variable:x',
            'Get-ChildItem $env:TEMP\\x | Remove-Item', 'Remove-Item -Path $env:TEMP\\a,$env:TEMP\\b',
            'Clear-Content $env:TEMP\\log.txt', 'cmd /c "del /q ' + TWIN + '\\x\\*"', 'Set-Content C:\\x\\a.txt y',
            'Move-Item C:\\x\\a C:\\x\\b', 'Copy-Item a C:\\x\\b', 'Get-ChildItem C:\\x -Recurse',
            'Select-String -Path C:\\x\\a -Pattern "Remove-Item"']:
    check('PowerShell: ' + cmd, ps(cmd, cwd=OUT), 'pass')
check('PowerShell: Remove-Item a.txt -ErrorAction ..., from scratch',
      ps('Remove-Item a.txt -ErrorAction SilentlyContinue', cwd=SCR), 'pass')
check('PowerShell: Get-ChildItem | Remove-Item, from scratch', ps('Get-ChildItem *.log | Remove-Item', cwd=SCR), 'pass')
# The existing denies still win, and a live-state write keeps its own message
check('a heredoc naming rm -rf', sh("cat <<'EOF'\nrm -rf C:/x\nEOF", cwd=OUT), 'pass')
check('rm outside, then git clean -fd', sh('rm -rf C:/x && git clean -fd'), 'deny')
check('rm outside, then pkill java', sh('rm -rf C:/x; pkill java'), 'deny')
check('rm -rf the live layout', sh('rm -rf ' + R + '/cs2_sample_layout'), 'deny')
why = (answer('Bash', {'command': 'rm -rf C:/x'}, OUT).get('hookSpecificOutput') or {}).get('permissionDecisionReason')
check('the message names the path', 'yes' if 'C:\\x' in (why or '') and 'outside the scratch folders' in why else why,
      'yes')
why = (answer('Bash', {'command': 'rm ' + R + '/LocDB.data'}).get('hookSpecificOutput') or {}).get(
    'permissionDecisionReason')
check('rm LocDB.data asks about the live state', 'yes' if 'LocDB' in (why or '') else why, 'yes')

# Moving master in this repository asks, by any spelling the guard reads
for cmd in ['git branch -f master release', 'git branch --force master release', 'git branch -D master',
            'git branch -d master', 'git branch -M master', 'git branch -m master old-master',
            'git branch -M release master', 'git branch -C release master', 'git branch -fq master release',
            'git update-ref refs/heads/master HEAD', 'git update-ref -d refs/heads/master',
            'git update-ref -m "x" refs/heads/master abc', '"git" branch -f master release',
            '/mingw64/bin/git branch -f master release', 'git checkout -B master release',
            'git switch -C master release', 'git fetch . release:master', 'git fetch origin +master:master',
            'bash -c "git branch -f master release"']:
    check(cmd, sh(cmd), 'ask')
check('git -C <repo> branch -f master, from elsewhere', sh('git -C ' + MSYS + ' branch -f master release', cwd=OUT),
      'ask')
check('PowerShell: git branch -f master', ps('git branch -f master release'), 'ask')
for cmd in ['git branch -f dev master', 'git branch release master', 'git branch --list master', 'git branch -d old',
            'git branch -c master copy', 'git update-ref refs/heads/dev HEAD',
            'git update-ref refs/heads/master-backup HEAD', 'git log master', 'git checkout -b master-fix',
            'git fetch origin master', 'git branch -m old new', 'git switch -c fix master', 'git checkout master',
            'git commit -m "git branch -f master release"', 'git branch -f fix --contains master']:
    check(cmd, sh(cmd), 'pass')
check('git branch -f master in another repository', sh('git branch -f master release', cwd=OUT), 'pass')
# A worktree of this repository is this repository; a repository of its own is not
fake = tempfile.mkdtemp()
try:
    common = subprocess.run(['git', '-C', ROOT, 'rev-parse', '--git-common-dir'], capture_output=True,
                            text=True).stdout.strip()
    common = os.path.abspath(os.path.join(ROOT, common))
    os.makedirs(os.path.join(fake, 'gd'))
    os.makedirs(os.path.join(fake, 'wt', 'src'))
    os.makedirs(os.path.join(fake, 'other', '.git'))
    open(os.path.join(fake, 'gd', 'commondir'), 'w').write(common + '\n')
    open(os.path.join(fake, 'wt', '.git'), 'w').write('gitdir: ' + os.path.join(fake, 'gd') + '\n')
    wt = os.path.join(fake, 'wt', 'src').replace('\\', '/')
    check('a worktree: git branch -f master', sh('git branch -f master release', cwd=wt), 'ask')
    check('a worktree: git push origin master', sh('git push origin master', cwd=wt), 'ask')
    check('a worktree: git branch -f dev', sh('git branch -f dev release', cwd=wt), 'pass')
    check('another repository: git branch -f master', sh('git branch -f master release',
                                                         cwd=os.path.join(fake, 'other').replace('\\', '/')), 'pass')
    check('another repository: git push origin master', sh('git push origin master',
                                                           cwd=os.path.join(fake, 'other').replace('\\', '/')), 'pass')
finally:
    shutil.rmtree(fake, ignore_errors=True)

# Shell keywords open the command after them: a delete or git command in a loop or an if body is judged
for cmd in ['for d in build dist; do rm -rf "$d"; done', 'if [ -d C:/x ]; then rm -rf C:/x; fi',
            'if true; then rm -rf C:/x; else echo no; fi', 'if [ -e X ]; then echo; else rm -rf C:/x; fi',
            'while read f; do rm -f "$f"; done < list.txt', 'until true; do rm -rf C:/x; done',
            'if false; then :; elif true; then rm -rf C:/x; fi', 'ls *.log | while read f; do rm "$f"; done',
            'for d in /tmp/a C:/x; do rm -rf "$d"; done', 'for d in $(ls); do rm -rf "$d"; done']:
    check('keywords: ' + cmd, sh(cmd, cwd=OUT), 'ask')
for cmd in ['for d in /tmp/a /tmp/b; do rm -rf "$d"; done', 'for d in "$TEMP"/x*; do rm -rf "$d"; done',
            'if [ -d /tmp/x ]; then rm -rf /tmp/x; fi', 'for f in *.log; do echo "$f"; done',
            'if true; then echo rm -rf C:/x; fi', 'if true; then mv C:/x/a C:/x/b; fi']:
    check('keywords: ' + cmd, sh(cmd, cwd=OUT), 'pass')
for cmd in ['if true; then git reset --hard; fi', 'if true; then git stash; fi', 'while true; do git stash; done',
            'for f in a; do git checkout -- cs2_sample_layout/x; done', 'if true; then git add -A; fi']:
    check('keywords: ' + cmd, sh(cmd), 'deny')
check('keywords: if ...; then git push origin master', sh('if git diff --quiet; then git push origin master; fi'),
      'ask')
check('keywords: if ...; then git push origin dev', sh('if git diff --quiet; then git push origin dev; fi'), 'pass')

# git clean -f deletes untracked files: it asks outside scratch in any repository, passes in scratch, and is refused
# in this checkout
for cmd in ['git clean -fdx', 'git clean -f', 'git clean --force -d', 'git -C C:/x clean -fd', 'cd C:/x && git clean -f',
            'git clean -fd ../other']:
    check('clean: ' + cmd, sh(cmd, cwd=OUT), 'ask')
check('clean: git clean -f ../../.. from scratch', sh('git clean -f ../../..', cwd=SCR), 'ask')
for cmd in ['git clean -n', 'git clean -nd', 'cd /tmp/x && git clean -fdx', 'git -C /tmp/x clean -fd',
            'git -C "$TEMP/x" clean -fdx']:
    check('clean: ' + cmd, sh(cmd, cwd=OUT), 'pass')
check('clean: git clean -fdx in scratch', sh('git clean -fdx', cwd=SCR), 'pass')
check('clean: git clean -fd in this checkout', sh('git clean -fd'), 'deny')

# cd X || exit and cd X || return place what follows at that level in X; ${VAR:-default}, ${VAR:?}, mktemp -p
for cmd in ['cd /tmp/wt || exit 1; rm -rf build', 'cd /tmp/wt || exit; rm -rf build', 'cd /tmp/wt || return; rm -rf b',
            'cd /tmp/wt || exit 1\nrm -rf build', 'cd /tmp/wt || exit 1; make; rm -rf build',
            'cd /tmp/wt || exit 1; cd sub && rm -rf x', 'S=/tmp/s; rm -rf "${S:?}/x"', 'rm -rf "${TMPDIR:-/tmp}/x"',
            'rm -rf "${NOPE:=$TEMP}/x"', 'rm -rf "${TEMP:-C:/x}/y"', 'T=$(mktemp -d -p "$TEMP"); rm -rf "$T"',
            'T="$(mktemp -d -p "$TEMP")" && rm -rf "$T"', 'T=$(mktemp -d --tmpdir=/tmp); rm -rf "$T"',
            'T=$(mktemp /tmp/fooXXXX); rm -f "$T"', 'T=$(mktemp -p /tmp/x); rm -f "$T"']:
    check('C1: ' + cmd, sh(cmd, cwd=OUT), 'pass')
for cmd in ['cd /tmp/wt || echo no; rm -rf build', 'cd C:/x || exit 1; rm -rf y', '(cd /tmp/wt || exit 1); rm -rf b',
            'cd /tmp/wt || exit 1; cd C:/x; rm -rf y', 'cd $NOPE || exit 1; rm -rf y', 'cd /tmp/wt | exit; rm -rf b',
            'rm -rf "${NOPE:?}/x"', 'rm -rf "${NOPE:-C:/x}/y"', 'S=/tmp/s; rm -rf "${S:+C:/x}"',
            'S=/tmp/s; rm -rf "${S/tmp/c}"', 'T=$(mktemp -d -p C:/x); rm -rf "$T"', 'T=$(mktemp C:/x/fooXXXX); rm "$T"',
            'T=$(mktemp -d -p "$NOPE"); rm -rf "$T"']:
    check('C1: ' + cmd, sh(cmd, cwd=OUT), 'ask')

# A repository's .claude/worktrees, where the Agent tool's isolated worktrees are, is scratch
AGENT = 'C:/elsewhere/.claude/worktrees/agent-x'
for cmd, cwd in [('rm -rf build', AGENT), ('git worktree remove --force .claude/worktrees/agent-a0', OUT),
                 ('rm -rf C:/elsewhere/.claude/worktrees', OUT), ('git clean -fdx', AGENT),
                 ('rm -rf ' + R + '/.claude/worktrees/agent-y/build', OUT)]:
    check('C2: ' + cmd + ' in ' + cwd[-24:], sh(cmd, cwd=cwd), 'pass')
for cmd in ['rm -rf C:/elsewhere/.claude', 'rm -rf C:/elsewhere/.claude/worktrees-old/x',
            'rm -rf C:/elsewhere/.claude/worktrees/../x', 'rm -rf C:/elsewhere/claude/worktrees/x']:
    check('C2: ' + cmd, sh(cmd, cwd=OUT), 'ask')

# cd A || cd S && rm is not placed in S; S=x cmd sets S for cmd alone
many = 'cd /tmp/wt || exit 1; ' + '; '.join(['cd a%d' % k for k in range(60)]) + '; rm -rf C:/x'
started = time.time()
got = sh(many, cwd=OUT)
check('C1: sixty cds after cd || exit, judged in time', got if time.time() - started < 5 else 'slow', 'ask')
check('C5: cd C:/x || cd /tmp/s && rm',sh('cd C:/x || cd /tmp/s && rm -rf y', cwd=OUT), 'ask')
check('C5: cd /tmp/a || cd /tmp/s && rm, from scratch', sh('cd /tmp/a || cd /tmp/s && rm -rf y', cwd=SCR), 'pass')
check('C5: S=x true; rm "$S"', sh('S=/tmp/x true; rm -rf "$S/y"', cwd=OUT), 'ask')
check('C5: S=x; true; rm "$S"', sh('S=/tmp/x; true; rm -rf "$S/y"', cwd=OUT), 'pass')

# Master moved in one call: switched to and then changed, or changed in a worktree that is on master
for cmd in ['git checkout master && git merge --ff-only release',
            'git switch master && git merge --ff-only release && git push origin',
            'git checkout master && git merge --ff-only release && git push',
            'git checkout master; git merge --ff-only release; git checkout dev', 'git checkout master && git commit -m x',
            'git switch master && git reset HEAD~1', 'git checkout master && git cherry-pick abc',
            'git checkout master && git pull', 'git checkout -q master && git revert abc', 'git rebase dev master',
            'git rebase --onto dev x master', 'git worktree add -B master /tmp/m release',
            'git worktree add -b master /tmp/m', 'git switch master\ngit merge release']:
    check('B2: ' + cmd, sh(cmd), 'ask')
check('B2: PowerShell checkout master; merge',
      ps('git checkout master; git merge --ff-only release; git push; git checkout dev'), 'ask')
for cmd in ['git checkout master', 'git push origin dev', 'git merge dev', 'git push', 'git push origin',
            'git checkout -b fix master && git commit -m x', 'git switch -c fix master && git merge dev',
            'git switch --detach master && git commit -m x', 'git checkout --detach master && git merge x',
            'git checkout master && git log -3', 'git checkout master && git diff dev', 'git rebase master',
            'git worktree add -b fix /tmp/m master', 'git worktree add --detach /tmp/m master',
            'git checkout master && git merge --abort']:
    check('B2: ' + cmd, sh(cmd), 'pass')
fake = tempfile.mkdtemp()
try:
    common = os.path.abspath(os.path.join(ROOT, subprocess.run(
        ['git', '-C', ROOT, 'rev-parse', '--git-common-dir'], capture_output=True, text=True).stdout.strip()))

    def worktree(name, branch):
        gd = os.path.join(fake, 'gd-' + name)
        os.makedirs(gd)
        os.makedirs(os.path.join(fake, name, 'src'))
        open(os.path.join(gd, 'commondir'), 'w').write(common + '\n')
        open(os.path.join(gd, 'HEAD'), 'w').write('ref: refs/heads/' + branch + '\n' if branch else 'abc123\n')
        open(os.path.join(fake, name, '.git'), 'w').write('gitdir: ' + gd + '\n')
        return os.path.join(fake, name).replace('\\', '/')
    on, off, bare = worktree('m', 'master'), worktree('d', 'dev'), worktree('x', '')
    for cmd, cwd in [('git -C ' + on + ' merge --ff-only release', R), ('cd ' + on + ' && git merge x && git push', R),
                     ('git push', on), ('git push origin', on), ('git push -u origin', on), ('git push origin HEAD', on),
                     ('git commit -m x', on), ('git reset HEAD~1', on), ('git merge --continue', on),
                     ('git pull --ff-only', on), ('git -C ' + on + ' cherry-pick abc', OUT), ('git rebase dev', on)]:
        check('B2 on master: ' + cmd.replace(on, '<m>'), sh(cmd, cwd=cwd), 'ask')
    for cmd, cwd in [('git push origin dev', on), ('git push --tags', on), ('git reset src', on),
                     ('git merge --abort', on), ('git log', on), ('git push origin HEAD:dev', on),
                     ('git merge x', off), ('git push', off), ('git -C ' + off + ' commit -m x', R),
                     ('git commit -m x', bare), ('git push origin', bare), ('cd ' + off + ' && git merge x', R)]:
        check('B2 off master: ' + cmd.replace(off, '<d>'), sh(cmd, cwd=cwd), 'pass')

    # The asks about uncommitted edits, and the live-state ask, follow the delete check's placement: work in a
    # scratch worktree never asks, the same work in this checkout still does, and the denies stay strict
    wt = off
    for cmd in ['cd ' + wt + ' && git checkout -- src/a.java', 'cd ' + wt + ' && git restore src/a.java',
                'git -C ' + wt + ' restore src/a.java', 'cd ' + wt + ' && git checkout src',
                'cd ' + wt + ' || exit 1; git restore src/a.java', 'cp test/LocDB.data ' + wt + '/LocDB.data',
                'cd ' + wt + ' && cp test/LocDB.data LocDB.data', 'cd ' + wt + ' && rm -f UIState.data',
                'cd .claude/worktrees/agent-x && git checkout -- src/a.java',
                'git -C .claude/worktrees/agent-x restore src/a.java']:
        check('7 silent: ' + cmd.replace(wt, '<wt>'), sh(cmd), 'pass')
    check('7 silent: git restore in a scratch cwd', sh('git restore src/a.java', cwd=wt), 'pass')
    for cmd in ['cd ' + wt + '; git checkout -- src/a.java', 'cd ' + wt + '\ngit restore src/a.java',
                'git restore src/a.java', 'cd $NOPE && git restore x', 'cd ' + wt + '; cp test/LocDB.data LocDB.data',
                'cp test/LocDB.data LocDB.data', 'cd ' + wt + ' || cd src && git checkout org']:
        check('7 asks: ' + cmd.replace(wt, '<wt>'), sh(cmd), 'ask')
    check('7 asks: PowerShell Set-Location; git restore', ps('Set-Location ' + wt + '; git restore src/a.java'), 'ask')
    for cmd in ['cd ' + wt + ' && git reset --hard', 'cd ' + wt + ' && git stash', 'cd ' + wt + ' && git checkout .',
                'cd ' + wt + ' && echo x > ' + R + '/cs2_sample_layout/a.cs2']:
        check('7 denies stay: ' + cmd.replace(wt, '<wt>'), sh(cmd), 'deny')
finally:
    shutil.rmtree(fake, ignore_errors=True)

out = subprocess.run([sys.executable, GUARD], input='not json', capture_output=True, text=True)
check('garbage on stdin', 'pass' if not out.stdout.strip() and out.returncode == 0 else 'error', 'pass')

marker = os.path.join(os.environ.get('TEMP') or os.environ.get('TMP') or '/tmp', 'traincontrol-battery.lock.battery')
if not os.path.exists(marker):
    try:
        open(marker, 'w').write('%d\n' % os.getpid())
        # The edit goes ahead: Adam sees the systemMessage, Claude the same text as additionalContext
        check('Edit a .java while a battery runs', edit(R + '/src/a/B.java', 'b'), 'note')
        out = answer('Edit', {'file_path': R + '/src/a/B.java', 'old_string': 'a', 'new_string': 'b'})
        told = out.get('systemMessage') or ''
        check('battery note: both told, same text', 'yes' if 'battery is running' in told and
              out['hookSpecificOutput'].get('additionalContext') == told else 'no', 'yes')
        check('Edit a .md while a battery runs', edit(R + '/docs/x.md', 'b'), 'pass')
        open(marker, 'w').write('999999\n')
        check('Edit a .java, stale battery marker', edit(R + '/src/a/B.java', 'b'), 'pass')
    finally:
        os.remove(marker)
else:
    print('skip the battery cases: a battery marker is already there (%s)' % marker)

# precommit.py, in a scratch repository: each case stages one file and expects a refusal or not
for name, path, text, want in [
        ('a clean .java', 'src/A.java', '/** Doc. */\nclass A {}\n', 0),
        ('an orphaned javadoc', 'src/A.java', '/** One. */\n/** Two. */\nclass A {}\n', 1),
        ('a non-ASCII bundle', 'src/m_de.properties', 'x=Gr\u00fc\u00dfe\n', 1),
        ('an escaped bundle', 'src/m_de.properties', 'x=Gr\\u00FCn\n', 0),
        ('a Readme em dash', 'Readme.md', 'Trains \u2014 fast\n', 1),
        ('a Readme spaced hyphen', 'Readme.md', 'Trains - fast\n', 1),
        ('a Readme list', 'Readme.md', '- one\n- two\n', 0),
        ('a Readme date range', 'Readme.md', '* v3.0.x [3/2026 - 11/2026]\n', 0),
        ('a key table row', 'Automation.md', '| + and - | Next and previous page |\n| - | none |\n', 0),
        ('image alt text', 'Automation.md', '![Yes - Trains Can Stop Here](assets/a.png)\n', 0),
        ('a table cell in prose', 'Automation.md', '| x | Trains - fast |\n', 1),
        ('a link in prose', 'Readme.md', 'See [the page](x) - here\n', 1),
        ('the root LocDB.data', 'LocDB.data', 'x', 1),
        ('test/LocDB.data', 'test/LocDB.data', 'x', 0)]:
    scratch = tempfile.mkdtemp()
    try:
        subprocess.run(['git', 'init', '-q', scratch], check=True)
        full = os.path.join(scratch, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        open(full, 'w', encoding='utf-8', newline='\n').write(text)
        subprocess.run(['git', '-c', 'core.autocrlf=false', '-C', scratch, 'add', '-f', path], check=True)
        got = subprocess.run([sys.executable, PRECOMMIT], cwd=scratch, capture_output=True, text=True).returncode
        check('pre-commit: ' + name, 'refused' if got else 'allowed', 'refused' if want else 'allowed')
    finally:
        shutil.rmtree(scratch, ignore_errors=True)

print('%d missed' % misses)
sys.exit(1 if misses else 0)
