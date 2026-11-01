# The Claude Code PreToolUse hook for this repository: README.md beside it says how it is installed and what it checks.
#
# Reads the hook's JSON on stdin and answers with a permissionDecision of "deny" or "ask", or with nothing (the tool
# goes ahead).  Only calls that touch this repository are judged; anything else passes untouched.  It must never
# stop a call because it broke itself, so any error inside it lets the call through.
import glob, json, os, re, subprocess, sys

ROOT = os.path.normcase(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..')))
NAME = os.path.basename(ROOT)
SLOT_BASE = os.path.join(os.environ.get('TEMP') or os.environ.get('TMP') or '/tmp', 'traincontrol-battery.lock')


def norm(path):
    """A path as Windows sees it, lower case, so C:/x, C:\\x and /c/x compare equal."""
    if not path:
        return ''
    p = path.replace('\\', '/')
    m = re.match(r'^/([a-zA-Z])/(.*)$', p)
    if m:
        p = m.group(1) + ':/' + m.group(2)
    return os.path.normcase(os.path.abspath(p))


def in_repo(path, cwd):
    if not path:
        return False
    p = path if os.path.isabs(path) or re.match(r'^/[a-zA-Z]/', path) else os.path.join(cwd or '', path)
    p = norm(p)
    return p == ROOT or p.startswith(ROOT + os.sep)


def rel(path, cwd):
    p = path if os.path.isabs(path) or re.match(r'^/[a-zA-Z]/', path) else os.path.join(cwd or '', path)
    return os.path.relpath(norm(p), ROOT).replace('\\', '/')


def answer(decision, why):
    print(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'permissionDecision': decision,
                                             'permissionDecisionReason': 'TrainControl guard: ' + why}}))
    sys.exit(0)


def battery_running():
    """The pid in battery.sh's marker, while that process is alive; None otherwise."""
    try:
        pid = open(SLOT_BASE + '.battery').read().strip()
    except OSError:
        return None
    if not pid.isdigit():
        return pid or None
    try:
        out = subprocess.run(['tasklist', '/FI', 'PID eq ' + pid, '/NH'], capture_output=True, text=True,
                             timeout=5).stdout
    except Exception:
        return pid
    return pid if pid in out else None


def dashed(line):
    """An em dash, an en dash or a spaced hyphen in prose; code spans are left out."""
    prose = re.sub(r'`[^`]*`', '', line)
    return '—' in prose or '–' in prose or re.search(r'\S - \S', prose) is not None


# Root files that are Adam's live state, and the live layout folder
LIVE_FILES = re.compile(r'^(LocDB[^/]*\.data|UIState[^/]*\.data|autonomy\.json)$', re.I)
LIVE_DIR = 'cs2_sample_layout/'


def judge_write(tool, ti, cwd):
    path = ti.get('file_path') or ti.get('notebook_path') or ''
    if not in_repo(path, cwd):
        return
    r = rel(path, cwd)
    if LIVE_FILES.match(r):
        answer('deny', r + ' is Adam\'s live state; it is never written by a session.')
    if r.lower().startswith(LIVE_DIR):
        answer('deny', r + ' is in the live layout, which is never written or reverted by a session.')
    new = ti.get('content') if tool == 'Write' else ti.get('new_string') if tool == 'Edit' else ti.get('new_source')
    new = new or ''
    for e in ti.get('edits') or []:
        new += '\n' + (e.get('new_string') or '')
    if r.endswith('.properties') and any(ord(c) > 127 for c in new):
        bad = sorted(set(c for c in new if ord(c) > 127))
        answer('deny', 'bundles are ASCII: write ' + ', '.join('\\u%04X' % ord(c) for c in bad)
               + ' instead of the characters themselves.')
    if r.lower() in ('readme.md', 'automation.md'):
        # Only lines this call adds: the files keep a few of Adam's, such as the changelog's "[11/2025 - 3/2026]"
        if tool == 'Write':
            try:
                before = open(path, encoding='utf-8').read()
            except OSError:
                before = ''
        else:
            before = (ti.get('old_string') or '') + '\n' + '\n'.join(e.get('old_string') or ''
                                                                    for e in ti.get('edits') or [])
        had = set(before.splitlines())
        for line in new.splitlines():
            if line not in had and dashed(line):
                answer('deny', 'no em dashes, en dashes or spaced hyphens in ' + r + ': "' + line.strip()[:80] + '"')
    if r.endswith('.java') and (r.startswith('src/') or r.startswith('test/')):
        pid = battery_running()
        if pid:
            answer('ask', 'a battery is running (pid ' + pid + '); a .java edit now changes what it measures.')


GIT = r'(?:^|[;&|(`]\s*|\s)git(?:\s+-C\s+\S+)?\s+'


def judge_shell(cmd, cwd):
    scoped = in_repo(cwd, '') or NAME.lower() in cmd.lower()
    if re.search(r'-ExecutionPolicy\s+Bypass', cmd, re.I):
        answer('deny', '-ExecutionPolicy Bypass is never used; run the script another way.')
    if not scoped:
        return
    if re.search(GIT + r'add\b[^;&|]*(\s-A\b|\s--all\b|\s-u\b|\s--update\b|\s\.(\s|$)|\s\*)', cmd):
        answer('deny', 'stage files by name: no git add -A, --all, -u or "."')
    if re.search(GIT + r'commit\b[^;&|]*\s(-a\b|-am\b|--all\b)', cmd):
        answer('deny', 'stage files by name: no git commit -a')
    if re.search(GIT + r'stash\b(?!\s+(list|show)\b)', cmd):
        answer('deny', 'no git stash: it can take Adam\'s uncommitted edits with it.')
    if re.search(GIT + r'clean\b[^;&|]*\s-\w*f', cmd):
        answer('deny', 'no git clean -f: it deletes untracked files outright.')
    if re.search(r'--no-verify\b', cmd) and re.search(GIT + r'(commit|push)\b', cmd):
        answer('deny', 'hooks are not skipped; fix what the hook found.')
    if LIVE_DIR.rstrip('/') in cmd and re.search(GIT + r'(checkout|restore|reset|stash|clean)\b', cmd):
        answer('deny', 'the live layout is never reverted; commit its changes instead.')
    if re.search(GIT + r'push\b[^;&|]*\b(master|main)\b', cmd):
        answer('ask', 'a push to master waits for Adam\'s go.')
    if re.search(GIT + r'(merge|rebase|cherry-pick|commit)\b', cmd):
        try:
            branch = subprocess.run(['git', '-C', ROOT, 'rev-parse', '--abbrev-ref', 'HEAD'], capture_output=True,
                                    text=True, timeout=5).stdout.strip()
        except Exception:
            branch = ''
        if branch == 'master':
            answer('ask', 'this checkout is on master; changes to master wait for Adam\'s go.')
    if re.search(GIT + r'(reset\s+--hard|checkout\s+--\s|checkout\s+\.\s*$|restore\b)', cmd):
        answer('ask', 'this can throw away uncommitted edits, which may be Adam\'s.')
    if re.search(r'(taskkill\b[^;&|]*\bjava|Stop-Process\b[^;&|]*-Name\s+["\']?javaw?\b|\bkill(all)?\s+[^;&|]*\bjava)',
                 cmd, re.I):
        answer('deny', 'never kill java by name: Adam\'s TrainControl may be one of them.  Use the run\'s reaper.')
    if re.search(r'(^|[\s/\\"\'])(LocDB|UIState)\.data\b|(^|[\s/\\"\'])autonomy\.json\b', cmd) and \
            re.search(r'(>|\b(cp|mv|rm|del|Copy-Item|Move-Item|Remove-Item|Set-Content|Out-File)\b)', cmd):
        answer('ask', 'this may write or remove Adam\'s live LocDB.data, UIState.data or autonomy.json.')


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return
    tool = data.get('tool_name') or ''
    ti = data.get('tool_input') or {}
    cwd = data.get('cwd') or os.getcwd()
    if tool in ('Write', 'Edit', 'NotebookEdit'):
        judge_write(tool, ti, cwd)
    elif tool in ('Bash', 'PowerShell'):
        judge_shell(ti.get('command') or '', cwd)


if __name__ == '__main__':
    try:
        main()
    except SystemExit:
        raise
    except Exception:
        pass
