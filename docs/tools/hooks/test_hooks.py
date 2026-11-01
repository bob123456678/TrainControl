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
check('git reset --hard', sh('git reset --hard HEAD'), 'ask')
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
check('Edit a .java, no battery', edit(R + '/src/a/B.java', 'b'), 'pass')
check('Write LocDB.data in another folder', guard('Write', {'file_path': 'C:/elsewhere/LocDB.data', 'content': 'x'},
                                                  'C:/elsewhere'), 'pass')
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
