# Usage: python docs/tools/hooks/test_hooks.py
#
# Feeds claude_guard.py the calls it must refuse, ask about and let through, and checks each answer; then runs
# precommit.py against a throwaway repository for each thing it must refuse.  Prints one line per case and exits 1 on
# any miss.  Touches nothing in this repository.
import json, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
GUARD = os.path.join(HERE, 'claude_guard.py')
PRECOMMIT = os.path.join(HERE, 'precommit.py')
R = ROOT.replace('\\', '/')
MSYS = '/' + R[0].lower() + R[2:]
misses = 0


def guard(tool, ti, cwd=R):
    out = subprocess.run([sys.executable, GUARD], input=json.dumps({'tool_name': tool, 'cwd': cwd, 'tool_input': ti}),
                         capture_output=True, text=True).stdout.strip()
    return json.loads(out)['hookSpecificOutput']['permissionDecision'] if out else 'pass'


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

out = subprocess.run([sys.executable, GUARD], input='not json', capture_output=True, text=True)
check('garbage on stdin', 'pass' if not out.stdout.strip() and out.returncode == 0 else 'error', 'pass')

marker = os.path.join(os.environ.get('TEMP') or os.environ.get('TMP') or '/tmp', 'traincontrol-battery.lock.battery')
if not os.path.exists(marker):
    open(marker, 'w').write('%d\n' % os.getpid())
    check('Edit a .java while a battery runs', edit(R + '/src/a/B.java', 'b'), 'ask')
    check('Edit a .md while a battery runs', edit(R + '/docs/x.md', 'b'), 'pass')
    open(marker, 'w').write('999999\n')
    check('Edit a .java, stale battery marker', edit(R + '/src/a/B.java', 'b'), 'pass')
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
