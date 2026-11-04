# The Claude Code PreToolUse hook for this repository: README.md beside it says how it is installed and what it checks.
#
# Reads the hook's JSON on stdin and answers with a permissionDecision of "deny" or "ask", with a note and no decision,
# or with nothing (the tool goes ahead).  Calls that touch this repository are judged; elsewhere only deletes outside
# the scratch folders, moves of this repository's master and -ExecutionPolicy Bypass are.  It must never stop a call
# because it broke itself, so any error inside it lets the call through.
import glob, json, os, re, subprocess, sys

ROOT = os.path.normcase(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..')))
NAME = os.path.basename(ROOT)
SLOT_BASE = os.path.join(os.environ.get('TEMP') or os.environ.get('TMP') or '/tmp', 'traincontrol-battery.lock')


def norm(path):
    """A path as Windows sees it, lower case, so C:/x, C:\\x and /c/x compare equal."""
    if not path:
        return ''
    p = path.replace('\\', '/')
    m = re.match(r'^/([a-zA-Z])(?:/(.*))?$', p)
    if m:
        p = m.group(1) + ':/' + (m.group(2) or '')
    return os.path.normcase(os.path.abspath(p))


def in_repo(path, cwd):
    if not path:
        return False
    p = path if os.path.isabs(path) or re.match(r'^/[a-zA-Z](/|$)', path) else os.path.join(cwd or '', path)
    p = norm(p)
    return p == ROOT or p.startswith(ROOT + os.sep)


def rel(path, cwd):
    p = path if os.path.isabs(path) or re.match(r'^/[a-zA-Z](/|$)', path) else os.path.join(cwd or '', path)
    return os.path.relpath(norm(p), ROOT).replace('\\', '/')


# The first ask, kept until everything has been judged, so a deny later in the same call still wins; and notes, which
# tell Adam and Claude something without stopping the call
ASKED = []
NOTES = []


def answer(decision, why):
    if decision == 'ask':
        if not ASKED:
            ASKED.append(why)
        return
    print(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'permissionDecision': decision,
                                             'permissionDecisionReason': 'TrainControl guard: ' + why}}))
    sys.exit(0)


def note(why):
    NOTES.append('TrainControl guard: ' + why)


def finish():
    out = {}
    if ASKED:
        out['hookSpecificOutput'] = {'hookEventName': 'PreToolUse', 'permissionDecision': 'ask',
                                     'permissionDecisionReason': 'TrainControl guard: ' + ASKED[0]}
    if NOTES:
        text = '\n'.join(NOTES)
        out['systemMessage'] = text
        out.setdefault('hookSpecificOutput', {'hookEventName': 'PreToolUse'})['additionalContext'] = text
    if out:
        print(json.dumps(out))


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
    """An em dash, an en dash or a spaced hyphen in prose.  Code spans, image alt text, a bracketed date range such as
    "[11/2025 - 3/2026]" and a table cell holding only keys such as "+ and -" are not prose.  Same as precommit.py's."""
    prose = re.sub(r'`[^`]*`', '', line)
    prose = re.sub(r'!\[[^\]]*\]', '', prose)
    prose = re.sub(r'\[\d[\d/.]* - [^\]]*\]', '', prose)
    cells = prose.strip().strip('|').split('|') if prose.lstrip().startswith('|') else [prose]
    return any('—' in c or '–' in c or re.search(r'\S - \S', c) for c in cells)


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
        # Only lines this call adds, so a line it keeps is never judged
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
            note('a battery is running (pid ' + pid + '); a .java edit now changes what it measures.')


# A shell command is judged as the simple commands it is made of, each a list of words with its quotes taken off, so
# text inside a quoted argument (a commit message, a grep pattern) is never read as a command.  This catches slips,
# not intent: a script, an alias or a variable can still hide a command.

def drop_heredocs(cmd):
    """cmd without the bodies of its heredocs and PowerShell here-strings, which are data."""
    cmd = re.sub(r'@([\'"])[ \t]*\r?\n.*?\r?\n\1@', "''", cmd, flags=re.S)
    out, ends = [], []
    for line in cmd.split('\n'):
        if ends:
            if line.strip() == ends[0]:
                ends.pop(0)
            continue
        out.append(line)
        ends += [m.group(2) for m in re.finditer(r'(?<!<)<<-?\s*([\'"]?)([A-Za-z_][\w.-]*)\1', line)]
    return '\n'.join(out)


def lex(cmd, ps):
    """The simple commands in cmd as (words, output redirection targets, the separators before it, such as "&&" or
    ";"): split at unquoted ; & | ( ) newlines, backticks in bash, and { } at a word's edge.
    ps: PowerShell quoting, where the backtick escapes."""
    segs, words, targets, between = [], [], [], []
    cur, has, redirect = [], False, None
    esc = '`' if ps else '\\'
    i, n = 0, len(cmd)

    def flush():
        nonlocal cur, has, redirect
        if has:
            w = ''.join(cur)
            if redirect == 'out':
                targets.append(w)
            elif redirect is None:
                words.append(w)
            redirect = None
        cur, has = [], False

    def cut(sep=''):
        nonlocal words, targets, redirect
        flush()
        if words or targets:
            segs.append((words, targets, ''.join(between)))
            between[:] = []
        between.append(sep)
        words, targets, redirect = [], [], None

    while i < n:
        c = cmd[i]
        if c == esc and i + 1 < n:
            nxt = cmd[i + 1]
            if nxt in '\r\n':
                i += 3 if cmd[i + 1:i + 3] == '\r\n' else 2
            elif ps or nxt in ' \t"\'\\$`;&|<>(){}':
                cur.append(nxt)
                has = True
                i += 2
            else:
                cur.append(c)
                has = True
                i += 1
            continue
        if c == "'":
            j = cmd.find("'", i + 1)
            j = n if j < 0 else j
            cur.append(cmd[i + 1:j])
            has, i = True, j + 1
            continue
        if c == '"':
            j, buf = i + 1, []
            while j < n and cmd[j] != '"':
                if cmd[j] == esc and j + 1 < n and (ps or cmd[j + 1] in '"\\$`\n'):
                    buf.append(cmd[j + 1])
                    j += 2
                else:
                    buf.append(cmd[j])
                    j += 1
            cur.append(''.join(buf))
            has, i = True, j + 1
            continue
        if c in ' \t\r':
            flush()
        elif c == '#' and not has:
            while i < n and cmd[i] != '\n':
                i += 1
            continue
        elif c in ';|\n()' or (c == '`' and not ps) or (c in '{}' and not has):
            cut(c)
        elif c == '&':
            if cmd[i + 1:i + 2] == '>':
                flush()
                i += 3 if cmd[i + 2:i + 3] == '>' else 2
                redirect = 'out'
                continue
            cut(c)
        elif c == '>':
            if has and re.match(r'^[0-9*]$', ''.join(cur)):
                cur, has = [], False
            flush()
            i += 1
            if i < n and cmd[i] in '>|':
                i += 1
            if i < n and cmd[i] == '&':
                i += 1
                while i < n and (cmd[i].isdigit() or cmd[i] == '-'):
                    i += 1
            else:
                redirect = 'out'
            continue
        elif c == '<':
            flush()
            while i < n and cmd[i] == '<':
                i += 1
            redirect = 'in'
            continue
        else:
            cur.append(c)
            has = True
        i += 1
    cut()
    return segs


def base(word):
    """A command word's name: no folder, no .exe, lower case."""
    b = re.split(r'[/\\]', word)[-1].lower()
    return b[:-4] if b.endswith('.exe') else b


PREFIXES = {'env', 'command', 'exec', 'time', 'nice', 'nohup', 'sudo', 'builtin', 'xargs', 'timeout', '!'}
# Shell keywords in front of the command they open: `if true; then rm x; fi` runs rm
KEYWORDS = {'if', 'then', 'else', 'elif', 'while', 'until', 'do', '{'}


def command_at(words):
    """The index of the word naming the command, past variable assignments and prefixes such as env or xargs."""
    i = 0
    while i < len(words):
        w = words[i]
        if re.match(r'^[A-Za-z_]\w*=', w):
            i += 1
        elif w.startswith('$') and words[i + 1:i + 2] == ['=']:
            i += 2
        elif w.lower() in KEYWORDS:
            i += 1
        elif base(w) in PREFIXES:
            i += 1
            while i < len(words) and words[i].startswith('-'):
                i += 2 if words[i] in ('-n', '-I', '-L', '-P', '-d', '-s', '-E', '-a', '-u', '-k', '-C') else 1
            # timeout's duration comes before the command it runs
            if base(w) == 'timeout' and i < len(words) and re.match(r'^\d+(\.\d+)?[smhd]?$', words[i]):
                i += 1
        else:
            return i
    return i


def segments(cmd, dialect, depth=0):
    """Every simple command in cmd as (words, redirection targets, dialect, the separators before it), with the script
    given to bash -c, sh -c, cmd /c, powershell -Command, eval or Invoke-Expression added as commands of its own, each
    such script starting afresh.  dialect: bash, ps or cmd."""
    segs = [(w, t, dialect, sep) for w, t, sep in lex(drop_heredocs(cmd), dialect != 'bash')]
    for words, _, _, _ in list(segs):
        ci = command_at(words)
        if depth > 3 or ci >= len(words):
            continue
        name, rest = base(words[ci]), words[ci + 1:]
        inner = None
        if name in ('bash', 'sh', 'zsh', 'dash', 'ksh'):
            at = [k for k, w in enumerate(rest) if re.match(r'^-[a-zA-Z]*c[a-zA-Z]*$', w)]
            if at and at[0] + 1 < len(rest):
                inner = (rest[at[0] + 1], 'bash')
        elif name == 'cmd':
            at = [k for k, w in enumerate(rest) if w.lower() in ('/c', '/k', '//c', '//k')]
            if at:
                inner = (' '.join(rest[at[0] + 1:]), 'cmd')
        elif name in ('powershell', 'pwsh'):
            at = [k for k, w in enumerate(rest) if len(w) > 1 and '-command'.startswith(w.lower())]
            if at:
                inner = (' '.join(rest[at[0] + 1:]), 'ps')
        elif name in ('eval', 'iex', 'invoke-expression'):
            inner = (' '.join(rest), dialect)
        if inner:
            segs += segments(inner[0], inner[1], depth + 1)
    return segs


def options(args, takes_next='', takes_rest='', long_takes=()):
    """args as (flags, positionals).  A short letter in takes_next takes the rest of its cluster or the next word as its
    value, one in takes_rest only the rest of its cluster; a long name in long_takes takes the next word unless it has
    an =.  Everything after -- is positional."""
    flags, pos, i = set(), [], 0
    while i < len(args):
        a = args[i]
        if a == '--':
            pos += args[i + 1:]
            break
        if a.startswith('--'):
            name = a[2:].split('=', 1)[0]
            flags.add('--' + name)
            if '=' not in a and name in long_takes:
                i += 1
        elif a.startswith('-') and len(a) > 1:
            for k, ch in enumerate(a[1:]):
                flags.add('-' + ch)
                if ch in takes_next or ch in takes_rest:
                    if ch in takes_next and k == len(a) - 2:
                        i += 1
                    break
        else:
            pos.append(a)
        i += 1
    return flags, pos


# Pathspecs that mean the whole tree
WHOLE = {'.', './', '.\\', '*', ':', ':/', ':/.', ':/*', ':.', ':*', ':(top)'}


def judge_git(glob_opts, sub, args, text, cwds=(ROOT,), ask_cwds=(ROOT,)):
    """Deny or ask about a git command that may run in this checkout: cwds are where it may run, read strictly, for
    the denies; ask_cwds where the delete check places it, for the asks."""
    if any('core.hookspath' in g.lower() for g in glob_opts):
        answer('deny', 'hooks are not skipped; fix what the hook found.')
    if sub in ('checkout', 'restore', 'reset', 'stash', 'clean', 'switch', 'rm', 'mv') and \
            (LIVE_DIR.rstrip('/') in text.lower() or any(in_live_layout(c, [ROOT]) for c in cwds)):
        answer('deny', 'the live layout is never reverted; commit its changes instead.')
    if sub == 'add':
        f, pos = options(args, long_takes=('pathspec-from-file',))
        if f & {'-A', '-u', '--all', '--update'} or any(p in WHOLE or p.startswith('*') for p in pos):
            answer('deny', 'stage files by name: no git add -A, --all, -u, "." or ":/"')
    elif sub == 'commit':
        f, _ = options(args, 'mFCct', 'Su', ('message', 'file', 'author', 'date', 'reuse-message', 'reedit-message',
                                             'fixup', 'squash', 'template', 'cleanup', 'trailer', 'pathspec-from-file'))
        if f & {'-a', '--all'}:
            answer('deny', 'stage files by name: no git commit -a')
        if '-n' in f or any(x.startswith('--no-veri') for x in f):
            answer('deny', 'hooks are not skipped; fix what the hook found.')
    elif '--no-verify' in args:
        answer('deny', 'hooks are not skipped; fix what the hook found.')
    if sub == 'stash' and (not args or args[0] not in ('list', 'show')):
        answer('deny', 'no git stash: it can take Adam\'s uncommitted edits with it.')
    revert = 'this reverts the whole tree, the live layout and Adam\'s uncommitted edits with it.'
    if sub == 'clean':
        f, _ = options(args, 'e', long_takes=('exclude',))
        if f & {'-f', '--force'}:
            answer('deny', 'no git clean -f: it deletes untracked files outright.')
    if sub == 'reset' and '--hard' in args:
        answer('deny', 'no git reset --hard: ' + revert)
    if sub == 'checkout':
        f, pos = options(args, 'bB', long_takes=('orphan', 'conflict', 'pathspec-from-file'))
        if f & {'-f', '--force'} or any(p in WHOLE for p in pos):
            answer('deny', revert)
        # Paths after `--`, from xargs, or a named file or folder that exists: each reverts what it names
        if ask_cwds and ('--' in args or (pos and UNREAD in ask_cwds) or
                         any(os.path.exists(os.path.join(norm(c), p)) for c in ask_cwds if c != UNREAD for p in pos)):
            answer('ask', 'this can throw away uncommitted edits, which may be Adam\'s.')
    if sub == 'switch':
        f, _ = options(args, 'cC', long_takes=('create', 'force-create', 'orphan', 'conflict'))
        if f & {'-f', '--force', '--discard-changes'}:
            answer('deny', revert)
    if sub == 'restore':
        f, pos = options(args, 's', long_takes=('source', 'pathspec-from-file', 'conflict'))
        index_only = f & {'-S', '--staged'} and not f & {'-W', '--worktree'}
        if any(p in WHOLE for p in pos) and not index_only:
            answer('deny', revert)
        if ask_cwds:
            answer('ask', 'this can throw away uncommitted edits, which may be Adam\'s.')


def moves_master(sub, args):
    """Why this git command moves master, when it does: a push to it, or a local branch, ref or fetch that sets,
    renames, copies over or deletes it; None otherwise."""
    moves = False
    if sub == 'push':
        f, pos = options(args, 'o', long_takes=('repo', 'push-option', 'receive-pack', 'exec'))
        if f & {'--all', '--mirror'} or re.search(r'\b(master|main)\b', ' '.join(pos)):
            return 'a push to master waits for Adam\'s go.'
    elif sub == 'branch':
        f, pos = options(args, 'u', long_takes=('set-upstream-to', 'contains', 'no-contains', 'merged', 'no-merged',
                                                'points-at', 'sort', 'format'))
        moves = (f & {'-f', '--force'} and pos[:1] == ['master']) or \
            (f & {'-d', '-D', '--delete', '-m', '-M', '--move'} and 'master' in pos) or \
            (f & {'-c', '-C', '--copy'} and pos[-1:] == ['master'])
    elif sub == 'update-ref':
        _, pos = options(args, 'm')
        moves = any(p in ('refs/heads/master', 'heads/master') for p in pos)
    elif sub in ('checkout', 'switch'):
        # checkout -B and switch -C reset the branch they name to the start point
        for k, a in enumerate(args):
            if re.match(r'^-[A-Za-z]*' + ('B' if sub == 'checkout' else 'C') + '$', a) or a == '--force-create':
                moves = moves or args[k + 1:k + 2] == ['master']
            moves = moves or a in ('-Bmaster' if sub == 'checkout' else '-Cmaster', '--force-create=master')
    elif sub == 'fetch':
        _, pos = options(args, 'oj', long_takes=('depth', 'deepen', 'shallow-since', 'shallow-exclude', 'refmap',
                                                 'server-option', 'upload-pack', 'jobs'))
        moves = any(':' in p and p.rsplit(':', 1)[1] in ('master', 'refs/heads/master') for p in pos)
    elif sub == 'rebase':
        # git rebase <upstream> master checks master out and rebases it
        _, pos = options(args, 'sXxC', long_takes=('onto', 'strategy', 'strategy-option', 'exec', 'empty'))
        moves = pos[1:2] == ['master']
    elif sub == 'worktree' and args[:1] == ['add']:
        moves = any((re.match(r'^-[A-Za-z]*[bB]$', a) and args[k + 1:k + 2] == ['master']) or
                    a in ('-bmaster', '-Bmaster') for k, a in enumerate(args))
    return 'this moves master; changes to master wait for Adam\'s go.' if moves else None


def git_dir_of(folder):
    """The common git folder of the repository holding folder, read from its .git (a worktree's .git is a file naming
    its folder under the main .git); '' when there is none."""
    f = norm(folder)
    for _ in range(64):
        dot = os.path.join(f, '.git')
        if os.path.isdir(dot):
            return os.path.normcase(dot)
        if os.path.isfile(dot):
            try:
                line = open(dot).read().strip()
            except OSError:
                return ''
            if not line.startswith('gitdir:'):
                return ''
            gd = norm(os.path.join(f, line[7:].strip()))
            try:
                gd = norm(os.path.join(gd, open(os.path.join(gd, 'commondir')).read().strip()))
            except OSError:
                pass
            return gd
        up = os.path.dirname(f)
        if up == f:
            return ''
        f = up
    return ''


REPO_GIT = []


def this_repository(place, gated):
    """Whether git run in place works on this repository: this checkout, one of its worktrees, or (in a call that
    touches this checkout) a folder the guard cannot place."""
    if place == UNREAD:
        return gated
    if in_repo(place, ''):
        return True
    if not REPO_GIT:
        REPO_GIT.append(git_dir_of(ROOT))
    return bool(REPO_GIT[0]) and git_dir_of(place) == REPO_GIT[0]


def git_call(words):
    """(global options, subcommand, its arguments) when the command is git; None otherwise."""
    ci = command_at(words)
    if ci >= len(words) or base(words[ci]) != 'git':
        return None
    i, opts = ci + 1, []
    while i < len(words) and words[i].startswith('-'):
        step = 2 if words[i] in ('-C', '-c', '--git-dir', '--work-tree', '--namespace', '--config-env') else 1
        opts += words[i:i + step]
        i += step
    return (opts, words[i], words[i + 1:]) if i < len(words) else None


# Commands that write every path they name, commands whose destination is the one they write, and in-place editors
ANY_ARG = {'mv', 'move', 'move-item', 'mi', 'ren', 'rename', 'rename-item', 'rni', 'rm', 'rmdir', 'rd', 'del',
           'erase', 'remove-item', 'ri', 'set-content', 'sc', 'add-content', 'ac', 'out-file', 'clear-content', 'clc',
           'new-item', 'ni', 'touch', 'truncate', 'tee', 'tee-object', 'mkdir', 'md', 'unlink', 'shred'}
COPIERS = {'cp', 'copy', 'copy-item', 'cpi', 'install', 'rsync', 'scp', 'ln', 'xcopy', 'robocopy'}
IN_PLACE = {'sed', 'perl', 'gawk'}


def writes(words, targets, hit):
    """Whether this simple command writes or removes a path that hit() picks out."""
    if any(hit(t) for t in targets):
        return True
    ci = command_at(words)
    if ci >= len(words):
        return False
    name, args = base(words[ci]), words[ci + 1:]
    if name in ANY_ARG:
        return any(hit(a) for a in args)
    if name in IN_PLACE:
        return any(re.match(r'^(-[A-Za-z]*i|--in-place|inplace$)', a) for a in args) and any(hit(a) for a in args)
    if name == 'find':
        acts = '-delete' in args or any(a in ('-exec', '-execdir') and k + 1 < len(args) and
                                        base(args[k + 1]) in ANY_ARG | COPIERS for k, a in enumerate(args))
        return acts and any(hit(a) for a in args)
    if name in COPIERS:
        for k, a in enumerate(args):
            low = a.lower()
            if low in ('-t', '--target-directory') or (len(low) >= 3 and '-destination'.startswith(low)):
                return k + 1 < len(args) and hit(args[k + 1])
            if low.startswith('--target-directory=') or low.startswith('-destination:'):
                return hit(a)
        pos = [a for a in args if not a.startswith('-')]
        if name in ('xcopy', 'robocopy'):
            return len(pos) > 1 and hit(pos[1])
        return len(pos) > 1 and hit(pos[-1])
    return False


# Commands whose words are only read or printed: a rule's text in them is never a call
READERS = {'grep', 'egrep', 'fgrep', 'rg', 'ag', 'findstr', 'select-string', 'sls', 'echo', 'printf', 'write-host',
           'write-output', 'write-error', 'cat', 'type', 'get-content', 'gc', 'head', 'tail', 'less', 'more', 'wc',
           'git'}
JAVA = re.compile(r'(?<![\w./\\-])javaw?(?:\.exe)?(?![\w.\\/-])', re.I)
KILL = re.compile(r'(?<![\w.-])(p?kill(all)?|taskkill|tskill|stop-process|spps|wait-process)(?![\w-])|\.kill\b'
                  r'|\bterminate\b', re.I)


def acting(segs):
    """(command name, words from the command on) of each simple command that is not a reader."""
    for words, _ in segs:
        ci = command_at(words)
        if ci < len(words) and base(words[ci]) not in READERS:
            yield base(words[ci]), words[ci:]


# A folder the guard cannot place: one named with a variable, a home folder or an 8.3 short name, or one reached from
# such a folder.  It is judged as this checkout.
UNREAD = '<unread>'

# How many folders a call is followed through; past it, the rest of the call counts as anywhere
MAX_PLACES = 32


def absolute(path, cwd):
    if os.path.isabs(path) or re.match(r'^/[a-zA-Z](/|$)', path):
        return path
    return UNREAD if cwd == UNREAD else os.path.join(cwd or '', path)


# A path the guard cannot read literally: a variable, a home folder, an 8.3 short name
UNRESOLVED = re.compile(r'[$~%]')


def reached(path, places):
    """The folders a path may name from any of these.  One the guard cannot read is a folder it cannot place, unless
    it names the live layout, which it then stands for."""
    if UNRESOLVED.search(path):
        return [os.path.join(ROOT, LIVE_DIR.rstrip('/'))] if LIVE_DIR.rstrip('/') in path.lower() else [UNREAD]
    return [absolute(path, p) for p in places]


def inside_checkout(place):
    return place == UNREAD or in_repo(place, '')


def moves_to(words, places):
    """The folders a cd, pushd, Push-Location or Set-Location may reach, when this simple command is one; None
    otherwise."""
    ci = command_at(words)
    if ci < len(words) and base(words[ci]) in ('cd', 'pushd', 'chdir', 'set-location', 'sl', 'push-location'):
        args = [a for a in words[ci + 1:] if not a.startswith('-')]
        # cmd's `cd /d <folder>`: a lone /x with a folder after it is a switch; alone, /c is the drive
        if len(args) > 1 and re.match(r'^/[A-Za-z]$', args[0]):
            args = args[1:]
        if args:
            return reached(args[0], places)
    return None


def git_places(words, glob_opts, places):
    """The folders a git command may work in.  -C is where git really runs, and env -C where it starts; --git-dir,
    --work-tree and GIT_DIR only add folders, as they can name this checkout from anywhere."""
    out = list(places)
    pre = words[:command_at(words)]
    for k, w in enumerate(pre):
        if w in ('-C', '--chdir') and k > 0 and k + 1 < len(pre):
            out = reached(pre[k + 1], out)
        elif w.startswith('--chdir='):
            out = reached(w.split('=', 1)[1], out)
    for k, o in enumerate(glob_opts):
        if o == '-C' and k + 1 < len(glob_opts):
            out = reached(glob_opts[k + 1], out)
    named = [o.split('=', 1)[1] for o in glob_opts if o.startswith(('--git-dir=', '--work-tree=')) and '=' in o]
    named += [glob_opts[k + 1] for k, o in enumerate(glob_opts) if o in ('--git-dir', '--work-tree')
              and k + 1 < len(glob_opts)]
    named += [w.split('=', 1)[1] for w in words[:command_at(words)] if re.match(r'^GIT_(DIR|WORK_TREE)=', w)]
    for n in named:
        out += reached(n, places)
    return out


def in_live_layout(word, places):
    """Whether a word may name the repository's live layout folder or a path inside it: one the guard cannot read
    literally counts, and a relative one counts if it lands there from any folder the command may run in."""
    low = word.lower()
    if re.search(r'cs2_sa~\d', low):
        return True
    if LIVE_DIR.rstrip('/') not in low:
        return False
    if UNRESOLVED.search(word) or (UNREAD in places and absolute(word, UNREAD) == UNREAD):
        return True
    for p in places:
        if p != UNREAD and in_repo(word, p):
            r = rel(word, p).lower()
            if r == LIVE_DIR.rstrip('/') or r.startswith(LIVE_DIR):
                return True
    return False


# Deletes outside the scratch folders ask, in every session.  Scratch is anything under the temp folder, Claude's
# scratchpads and worktrees with it, spelled from the environment.

def env_dir(name):
    v = os.environ.get(name) or ''
    return v if re.match(r'^([A-Za-z]:[\\/]|/[a-zA-Z]/)', v) else ''


TEMP_DIR = env_dir('TEMP') or env_dir('TMP') or (os.path.join(env_dir('LOCALAPPDATA'), 'Temp')
                                                 if env_dir('LOCALAPPDATA') else '')
SCRATCH = sorted(set(norm(d) for d in (env_dir('TEMP'), env_dir('TMP'), os.path.join(env_dir('LOCALAPPDATA'), 'Temp')
                                        if env_dir('LOCALAPPDATA') else '') if d))
HOME_DIR = env_dir('HOME') or env_dir('USERPROFILE') or os.path.expanduser('~')
KNOWN = dict((k.lower(), env_dir(k)) for k in ('TEMP', 'TMP', 'LOCALAPPDATA', 'USERPROFILE') if env_dir(k))
KNOWN['home'] = HOME_DIR
# A stand-in for a folder made by mktemp, New-TemporaryFile or GetTempPath, which land in the temp folder
MADE_IN_TEMP = os.path.join(TEMP_DIR, 'tmp.made') if TEMP_DIR else None
VAR = re.compile(r'\$\{(\w+)(:?[-=?+])?([^}]*)\}|\$env:(\w+)|\$(\w+)|%(\w+)%', re.I)


def expand(word, names):
    """word with ~, $NAME, ${NAME}, ${NAME:-default} (and :=, :?), $env:NAME and %NAME% put in, from the names this
    call assigned and the environment's temp and home folders; None when some part of it cannot be read."""
    if word == '~' or word[:2] in ('~/', '~\\'):
        word = '$HOME' + word[1:]
    missing = []

    def put(m):
        key = next(g for g in (m.group(1), m.group(4), m.group(5), m.group(6)) if g).lower()
        known = key in names or key in KNOWN
        v = names[key] if key in names else KNOWN.get(key)
        op, rest = m.group(2) or '', m.group(3) or ''
        if m.group(1) and (op.endswith('+') or (rest and not op)):
            v = None  # ${S:+x} and ${S/a/b} are not read
        elif op[-1:] in ('-', '=') and not known:
            v = expand(rest, names)
        if v is None:
            missing.append(key)
        return v or ''
    out = VAR.sub(put, word)
    return None if missing or re.search(r'[$%`~]', VAR.sub('', word)) else out


def full_path(w, place, dialect):
    """An expanded path as an absolute one, from place when it is relative; None when place cannot be read."""
    if dialect == 'bash' and re.match(r'^/tmp([/\\]|$)', w) and TEMP_DIR:
        w = TEMP_DIR + w[4:]  # Git Bash mounts /tmp on the temp folder
    if re.match(r'^[A-Za-z]{2,}:', w) or w[:2] in ('\\\\', '//'):
        return w  # a PowerShell drive such as HKCU:, or a share: never scratch
    if not (w[:1] in '/\\' or re.match(r'^[A-Za-z]:', w)):
        if place == UNREAD:
            return None
        w = os.path.join(place, w)
    w = w.replace('\\', '/')
    m = re.match(r'^/([a-zA-Z])(?:/(.*))?$', w)
    return os.path.abspath(m.group(1) + ':/' + (m.group(2) or '') if m else w)


WORKTREES = os.sep + os.path.join('.claude', 'worktrees')


def in_scratch(path, root_too):
    """Under the temp folder (or it, with root_too), or in a repository's .claude/worktrees, where the Agent tool's
    isolated worktrees are."""
    c = os.path.normcase(path)
    return any((root_too and c == r) or c.startswith(r.rstrip(os.sep) + os.sep) for r in SCRATCH) or \
        c.endswith(WORKTREES) or (WORKTREES + os.sep) in c


def outside(word, place, names, dialect, root_too):
    """Why deleting word from place may reach outside the scratch folders; None when it stays inside.  A pattern is
    judged by its literal leading folder, and root_too lets that folder (or a find's) be a scratch folder itself."""
    w = expand(word, names)
    if w is None:
        return word + ', a path the guard cannot read,', False
    parts = re.split(r'[/\\]', w)
    at = next((k for k, s in enumerate(parts) if re.search(r'[*?\[{]' if dialect == 'bash' else r'[*?\[]', s)), None)
    if at is not None:
        if '..' in parts[at + 1:]:
            return word + ', a pattern that climbs out with "..",', False
        w = '/'.join(parts[:at]) or ('/' if w[:1] in '/\\' else '.')
        w, root_too = w + '/' if w.endswith(':') else w, True
    p = full_path(w, place, dialect)
    if p is None:
        return word + ' in a folder the guard cannot place,', False
    return None if in_scratch(p, root_too) else (word + ' (' + p + ')', True)


CDS = {'cd', 'pushd', 'chdir', 'set-location', 'sl', 'push-location'}
BASH_DELETES = {'rm', 'rmdir', 'unlink', 'shred'}
PS_DELETES = {'remove-item', 'ri', 'rm', 'del', 'erase', 'rd', 'rmdir', 'clear-content', 'clc'}
CMD_DELETES = {'del', 'erase', 'rd', 'rmdir'}
LISTERS = {'get-childitem', 'gci', 'ls', 'dir', 'get-item', 'gi'}
PS_VALUES = ('filter', 'include', 'exclude', 'credential', 'stream', 'erroraction', 'warningaction',
             'informationaction', 'errorvariable', 'warningvariable', 'informationvariable', 'outvariable', 'outbuffer',
             'pipelinevariable', 'progressaction', 'depth', 'attributes')
PS_SHORT = {'ea', 'wa', 'infa', 'ev', 'wv', 'iv', 'ov', 'ob', 'pv'}


def ps_paths(args):
    """The paths a PowerShell cmdlet's words name: its positionals and its -Path and -LiteralPath values."""
    out, i = [], 0
    while i < len(args):
        a = args[i]
        if a.startswith('-') and len(a) > 1:
            key, colon, val = a[1:].partition(':')
            key = key.lower()
            is_path = key in ('lp', 'pspath') or (len(key) > 1 and 'literalpath'.startswith(key)) or \
                'path'.startswith(key)
            if not colon and (is_path or key in PS_SHORT or (len(key) > 1 and any(v.startswith(key)
                                                                                  for v in PS_VALUES))):
                val = args[i + 1] if i + 1 < len(args) else ''
                i += 1
            if is_path and val:
                out.append(val)
        else:
            out.append(a)
        i += 1
    return [p for w in out for p in w.split(',') if p]


def deleted(name, args, dialect, prev, via_xargs):
    """[(path word, root_too)] that this simple command deletes; None when it deletes nothing; 'input' when it deletes
    paths it reads from its input."""
    if dialect == 'bash' and name in BASH_DELETES:
        _, pos = options(args, 'ns' if name == 'shred' else '', long_takes=('iterations', 'size', 'random-source'))
        return [(p, False) for p in pos] or ('input' if via_xargs else None)
    if dialect == 'bash' and name == 'find':
        acts = '-delete' in args or any(a in ('-exec', '-execdir', '-ok', '-okdir') and k + 1 < len(args) and
                                        base(args[k + 1]) in BASH_DELETES for k, a in enumerate(args))
        if not acts:
            return None
        starts = []
        for a in args:
            if a.startswith('-') or a in ('(', ')', '!'):
                break
            starts.append(a)
        return [(p, True) for p in starts or ['.']]
    if dialect == 'ps' and name in PS_DELETES:
        paths = ps_paths(args)
        if paths:
            return [(p, False) for p in paths]
        # Get-ChildItem x | Remove-Item deletes what the listing names
        if prev and prev[0] in LISTERS:
            return [(p, True) for p in ps_paths(prev[1]) or ['.']]
        return 'input'
    if dialect == 'cmd' and name in CMD_DELETES:
        return [(a, False) for a in args if not re.match(r'^/[A-Za-z?](:\S*)?$', a)] or None
    return None


def assigned(words, ci, dialect, after):
    """{name: value} this simple command assigns, values expanded later; after is the next simple command, which an
    unquoted S=$(mktemp) splits off."""
    out = {}
    if dialect == 'ps':
        m = re.match(r'^\$(?:env:)?(\w+)(=(.*))?$', words[0], re.I) if words else None
        if m and (m.group(2) is not None or words[1:2] == ['=']):
            v = m.group(3) if m.group(2) is not None and len(words) == 1 else \
                words[2] if m.group(2) is None and len(words) == 3 else None
            if v is not None and re.match(r'^(New-TemporaryFile|\[(System\.)?IO\.Path\]::GetTemp\w*)$', v, re.I):
                v = MADE_IN_TEMP
            elif v is not None and not re.search(r'[/\\:$%~.]', v):
                v = None  # a command's output, not a path
            out[m.group(1).lower()] = v
        return out
    # S=x cmd sets S for cmd alone, so only a command of assignments, or export and the like, sets names
    pairs = list(words[:ci]) if ci >= len(words) else []
    if ci < len(words) and base(words[ci]) in ('export', 'declare', 'local', 'readonly', 'typeset'):
        pairs += words[ci + 1:]
    for w in pairs:
        m = re.match(r'^([A-Za-z_]\w*)=(.*)$', w, re.S)
        if not m:
            continue
        v = m.group(2)
        made = re.match(r'^\$\(\s*mktemp\b([^)]*)\)$', v)
        margs = made.group(1).split() if made else after[1:] if v == '$' and after and base(after[0]) == 'mktemp' \
            else None
        if margs is not None:
            v = made_by_mktemp(margs)
        out[m.group(1).lower()] = v
    return out


def made_by_mktemp(args):
    """Where mktemp with these arguments makes its file or folder, as a path to expand: in its -p or --tmpdir folder,
    at a template that names a folder, or in the temp folder."""
    folder, template, i = None, None, 0
    while i < len(args):
        a = args[i]
        if a in ('-p', '--tmpdir'):
            folder, i = (args[i + 1] if i + 1 < len(args) else None), i + 1
        elif a.startswith('--tmpdir='):
            folder = a.split('=', 1)[1]
        elif a.startswith('-p') and len(a) > 2:
            folder = a[2:]
        elif not a.startswith('-'):
            template = a
        i += 1
    if template and re.search(r'[/\\]', template):
        return os.path.join(folder, template) if folder else template
    return os.path.join(folder, 'tmp.made') if folder else MADE_IN_TEMP


def place(word, folder, names, dialect):
    """The folder a cd, -C or --chdir to word reaches from folder; UNREAD when the guard cannot read it."""
    x = expand(word, names)
    return (full_path(x, folder, dialect) if x is not None else None) or UNREAD


def cd_target(name, args, dialect):
    """(word, or None) a cd-like command moves to: None for a bare cd to home, '-' for cd -, '' for no move."""
    rest = [a for a in args if not a.startswith('-')]
    # cmd's `cd /d <folder>`: a lone /x with a folder after it is a switch; alone, /c is the drive
    if len(rest) > 1 and re.match(r'^/[A-Za-z]$', rest[0]):
        rest = rest[1:]
    return rest[0] if rest else '-' if '-' in args else None if name == 'cd' and dialect == 'bash' else ''


# The separators that keep a cd's folder: && always; ; and newlines too after `cd X || exit`
AND = re.compile(r'^&&\n*$')
SURE = re.compile(r'^(;|\n|&&)*$')


def follow(segs, cwd):
    """The call as the delete check reads it: (why its first delete that may reach outside the scratch folders asks,
    or None; for each simple command, (the folders it may run in, the names assigned before it)).  A relative path is
    judged from every folder the call may run in; but a command joined by && alone to the cds before it, or following
    `cd X || exit` at the same level, runs only where they took it."""
    places, names, prev, xargs_open, chain, sure, why = [cwd], {}, None, False, None, False, None
    out, last_cd, loops = [], False, {}
    for k, (words, _, dialect, sep) in enumerate(segs):
        ci = command_at(words)
        name = base(words[ci]) if ci < len(words) else ''
        if sep == '||' and last_cd and chain is not None and name in ('exit', 'return'):
            sure = True  # cd X || exit: the rest of this level runs in X
        elif not (AND.match(sep) or (sure and SURE.match(sep))):
            chain, sure = None, False
        last_cd = False
        # for d in a b; do rm -rf "$d"; done: d is each of the listed words in turn
        if dialect == 'bash' and len(words) > 2 and words[0] == 'for' and words[2] == 'in' and \
                re.match(r'^[A-Za-z_]\w*$', words[1]):
            loops[words[1].lower()] = words[3:]
            names.pop(words[1].lower(), None)
        sets = assigned(words, ci, dialect, segs[k + 1][0] if k + 1 < len(segs) else None)
        for n, v in sets.items():
            names[n] = expand(v, names) if v is not None else None
        here = list(chain if chain is not None else places)
        # xargs -I {} rm {}: the braces end the xargs words, so the rm after them reads its input too
        via_xargs, xargs_open = xargs_open or any(base(w) == 'xargs' for w in words[:ci]), \
            ci >= len(words) and any(base(w) == 'xargs' for w in words)
        if ci >= len(words) or (dialect == 'ps' and sets):
            out.append((here, dict(names)))
            continue
        args = words[ci + 1:]
        if name in CDS:
            to = cd_target(name, args, dialect)
            if to != '':
                def reach(froms):
                    return [UNREAD] if to == '-' else [HOME_DIR] if to is None else \
                        [place(to, p, names, dialect) for p in froms]
                new = reach(places)
                # cd A || cd S: either may have run, so the cd after || starts no chain; after `cd X || exit;` a
                # further cd may fail, so its folder joins X's
                if '||' in sep:
                    chain = None
                elif chain is not None:
                    chain = reach(chain) if AND.match(sep) else chain + [m for m in reach(chain) if m not in chain]
                else:
                    chain = new
                if chain and len(chain) > MAX_PLACES:
                    chain = [p for p in chain[:MAX_PLACES] if p != UNREAD] + [UNREAD]
                last_cd = chain is not None
                places += [m for m in new if m not in places]
                if len(places) > MAX_PLACES:
                    places = [p for p in places[:MAX_PLACES] if p != UNREAD] + [UNREAD]
            out.append((here, dict(names)))
            continue
        if name in ('popd', 'pop-location'):
            chain = None
        # env -C and git -C move the one command they run
        for j, w in enumerate(words[:ci]):
            if w in ('-C', '--chdir') and j > 0 and j + 1 < ci:
                here = [place(words[j + 1], p, names, dialect) for p in here]
            elif w.startswith('--chdir='):
                here = [place(w.split('=', 1)[1], p, names, dialect) for p in here]
        out.append((here, dict(names)))
        if why:
            continue
        what, verb = deleted(name, args, dialect, prev, via_xargs), 'deletes '
        g = git_call(words)
        if g and g[1] in ('worktree', 'clean'):
            for j, o in enumerate(g[0]):
                if o == '-C' and j + 1 < len(g[0]):
                    here = [place(g[0][j + 1], p, names, dialect) for p in here]
            if g[1] == 'worktree' and g[2][:1] == ['remove']:
                _, pos = options(g[2][1:])
                what, verb = [(p, False) for p in pos[:1]] or None, 'removes the worktree '
            elif g[1] == 'clean':
                f, pos = options(g[2], 'e', long_takes=('exclude',))
                if f & {'-f', '--force'}:
                    what, verb = [(p, True) for p in pos or ['.']], 'deletes the untracked files in '
        prev = (name, args)
        if what == 'input':
            why = 'this deletes paths it reads from its input, which may be outside the scratch folders (under ' + \
                TEMP_DIR + '); a delete there waits for Adam\'s go.'
        for word, root_too in (what if isinstance(what, list) else []):
            if why or (dialect == 'ps' and re.match(r'^(env|variable|function|alias):', word, re.I)):
                continue  # a session's variable or function, not a file
            for p, value in [(p, v) for p in here for v in each_value(word, loops, names)]:
                bad = outside(word, p, value, dialect, root_too)
                if bad:
                    why = 'this ' + verb + bad[0] + (', which is outside' if bad[1] else ' so it may be outside') + \
                        ' the scratch folders (under ' + TEMP_DIR + '); a delete there waits for Adam\'s go.'
                    break
    return why, out


def each_value(word, loops, names):
    """The names to judge word with: one set for each value a for loop's variable in word takes (none listed or
    one this call reassigned is left unread)."""
    sets = [names]
    for var, values in loops.items():
        if var not in names and re.search(r'\$\{?' + var + r'\b', word, re.I):
            sets = [dict(s, **{var: expand(v, s) if '$' in v or '~' in v else v}) for s in sets for v in values] or \
                [dict(s, **{var: None}) for s in sets]
            if len(sets) > 4 * MAX_PLACES:
                return [dict(names, **{var: None})]  # too many to follow: unread, so it asks
    return sets


def judge_deletes(segs, cwd):
    return follow(segs, cwd)[0]


def git_places_loose(words, glob_opts, seen, dialect):
    """The folders a git command may work in, as the delete check places it (seen: follow()'s entry for it), with -C
    and --git-dir or --work-tree read as git reads them."""
    here, names = seen
    for k, o in enumerate(glob_opts):
        if o == '-C' and k + 1 < len(glob_opts):
            here = [place(glob_opts[k + 1], p, names, dialect) for p in here]
    named = [o.split('=', 1)[1] for o in glob_opts if o.startswith(('--git-dir=', '--work-tree='))]
    named += [glob_opts[k + 1] for k, o in enumerate(glob_opts) if o in ('--git-dir', '--work-tree')
              and k + 1 < len(glob_opts)]
    named += [w.split('=', 1)[1] for w in words[:command_at(words)] if re.match(r'^GIT_(DIR|WORK_TREE)=', w)]
    return here + [place(n, p, names, dialect) for n in named for p in seen[0]]


def asks_here(folder):
    """Whether a repository ask about uncommitted edits applies in folder: this checkout, but not an isolated agent's
    worktree inside it (.claude/worktrees), and a folder the guard cannot place."""
    if folder == UNREAD:
        return True
    return in_repo(folder, '') and not rel(folder, '').lower().startswith('.claude/worktrees')


LIVE_STATE = re.compile(r'(^|[/\\])(LocDB\.data|UIState\.data|autonomy\.json)$', re.I)


def live_state(word, seen, dialect):
    """Whether word may be this checkout's own LocDB.data, UIState.data or autonomy.json, from where the delete check
    places the command (seen); a worktree's copy is not.  Without seen, any path ending in one of those names."""
    if not LIVE_STATE.search(word):
        return False
    x = expand(word, seen[1]) if seen else None
    if x is None:
        return True
    for p in seen[0]:
        full = full_path(x, p, dialect)
        if full is None or os.path.normcase(os.path.dirname(full)) == ROOT:
            return True
    return False


def head_of(folder):
    """(the worktree's own git folder, the branch its HEAD is on, '' when detached), read from its .git files; None
    when folder is in no repository."""
    f = norm(folder)
    for _ in range(64):
        dot = os.path.join(f, '.git')
        gd = None
        if os.path.isdir(dot):
            gd = dot
        elif os.path.isfile(dot):
            try:
                line = open(dot).read().strip()
            except OSError:
                return None
            gd = os.path.join(f, line[7:].strip()) if line.startswith('gitdir:') else None
            if not gd:
                return None
        if gd:
            try:
                head = open(os.path.join(gd, 'HEAD')).read().strip()
            except OSError:
                head = ''
            return norm(gd), head[16:] if head.startswith('ref: refs/heads/') else ''
        up = os.path.dirname(f)
        if up == f:
            return None
        f = up
    return None


# The git commands whose folder matters to master: they may move it by name, by switching to it, or on it
MASTER_SUBS = {'push', 'branch', 'update-ref', 'checkout', 'switch', 'fetch', 'rebase', 'worktree', 'merge',
               'cherry-pick', 'commit', 'reset', 'pull', 'revert', 'am'}
ON_BRANCH = {'merge', 'rebase', 'cherry-pick', 'commit', 'reset', 'pull', 'revert', 'am', 'push'}


def switches_to_master(sub, args):
    if sub == 'checkout' and '--' not in args:
        f, pos = options(args, 'bB', long_takes=('orphan', 'conflict', 'pathspec-from-file'))
        return not f & {'-b', '-B', '--orphan', '--detach'} and pos == ['master']
    if sub == 'switch':
        f, pos = options(args, 'cC', long_takes=('create', 'force-create', 'orphan', 'conflict'))
        return not f & {'-c', '-C', '--create', '--force-create', '--orphan', '-d', '--detach'} and \
            pos[:1] == ['master']
    return False


def moves_current(sub, args, folders):
    """Whether this git command, run on a branch, moves that branch."""
    if sub in ('merge', 'rebase', 'cherry-pick', 'revert', 'am'):
        return not set(args) & {'--abort', '--quit', '--skip', '--show-current-patch'}
    if sub == 'push':
        f, pos = options(args, 'o', long_takes=('repo', 'push-option', 'receive-pack', 'exec'))
        if f & {'--tags', '--delete', '-d'}:
            return False
        # No refspec pushes the current branch (push.default simple), and so does HEAD
        return len(pos) < 2 or any(r.lstrip('+') == 'HEAD' for r in pos[1:])
    if sub == 'reset':
        before = args[:args.index('--')] if '--' in args else args
        _, pos = options(before)
        # A commit moves the branch; a path only resets the index
        return any(not any(os.path.exists(os.path.join(norm(p), r)) for p in folders if p != UNREAD) for r in pos)
    return True


def judge_master(sub, args, folders, on_master):
    """Ask before a git command in this repository's folders that moves master: by name (moves_master), or run on
    master, because the worktree's HEAD is there or this call switched it there (on_master keeps those)."""
    if not folders:
        return
    why = moves_master(sub, args)
    if why:
        answer('ask', why)
    heads = [(p, head_of(ROOT if p == UNREAD else p)) for p in folders]
    keys = [h[0] if h else p for p, h in heads]
    if switches_to_master(sub, args):
        on_master.update(keys)
    elif sub in ON_BRANCH and any((h and h[1] == 'master') or key in on_master for (p, h), key in zip(heads, keys)) \
            and moves_current(sub, args, folders):
        answer('ask', 'this ' + sub + ' runs on master; changes to master wait for Adam\'s go.')


def judge_shell(cmd, cwd, ps):
    segs3 = segments(cmd, 'ps' if ps else 'bash')
    segs = [(w, t) for w, t, _, _ in segs3]
    for name, words in acting(segs):
        text = ' '.join(words)
        if (re.search(r'(?<![\w-])(powershell|pwsh)(\.exe)?(?![\w-])', text, re.I) and
                re.search(r'(?<![\w-])-(ep|ex\w*)[\s:]+bypass\b', text, re.I)) or \
                re.search(r'\bset-executionpolicy\b.*\bbypass\b', text, re.I):
            answer('deny', '-ExecutionPolicy Bypass is never used; run the script another way.')
    try:
        removal, soft = follow(segs3, cwd)
    except Exception:
        removal, soft = None, None
    gated = in_repo(cwd, '') or NAME.lower() in cmd.lower()
    # A cd earlier in the call adds a folder the rest may run in, and never takes one away: a cd can fail, a subshell
    # ends, and the shell is then still where it was.  Only git -C, which git itself obeys, moves a git command.  That
    # strict placement is the denies'; the asks use the delete check's, so work in a scratch worktree never asks.
    places, on_master = [cwd], set()
    for k, (words, targets) in enumerate(segs):
        moved = moves_to(words, places)
        if moved:
            places += [m for m in moved if m not in places]
            if len(places) > MAX_PLACES:
                places = [p for p in places[:MAX_PLACES] if p != UNREAD] + [UNREAD]
            continue
        g = git_call(words)
        if g:
            at = git_places(words, g[0], places)
            loose = git_places_loose(words, g[0], soft[k], segs3[k][2]) if soft else at
            judge_master(g[1], g[2], [p for p in loose if this_repository(p, gated)] if g[1] in MASTER_SUBS else [],
                         on_master)
            inside = [p for p in at if inside_checkout(p)] if gated else []
            if inside:
                ci = command_at(words)
                via_xargs = any(base(w) == 'xargs' for w in words[:ci])
                judge_git(g[0], g[1], g[2] + (['--'] if via_xargs and g[1] in ('checkout', 'restore') else []),
                          ' '.join(words), inside, [p for p in loose if asks_here(p)])
        # Run from inside the layout folder, a write of a relative path is a write into it
        in_layout = gated and any(p != UNREAD and in_live_layout(p, [ROOT]) for p in places)
        if gated and writes(words, targets, lambda w: in_live_layout(w, places)
                            or (in_layout and absolute(w, ROOT) != w)):
            answer('deny', 'the live layout is never written or reverted by a session; its changes are committed.')
    if gated:
        # Across pipes: Get-Process java | Stop-Process names java in one command and kills in the next.  A java that
        # is itself the command being run is not a name to kill by.
        named = killed = False
        for name, words in acting(segs):
            named = named or any(JAVA.search(w) for w in words[1:])
            killed = killed or KILL.search(' '.join(words)) is not None or \
                (name == 'wmic' and re.search(r'\b(delete|terminate)\b', ' '.join(words), re.I) is not None)
        if named and killed:
            answer('deny', 'never kill or wait on java by name: Adam\'s TrainControl may be one of them.  Use the '
                           'run\'s reaper.')
        if any(writes(words, targets, lambda w: live_state(w, soft[k] if soft else None, segs3[k][2]))
               for k, (words, targets) in enumerate(segs)):
            answer('ask', 'this may write or remove Adam\'s live LocDB.data, UIState.data or autonomy.json.')
    if removal:
        answer('ask', removal)


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
        judge_shell(ti.get('command') or '', cwd, tool == 'PowerShell')
    finish()


if __name__ == '__main__':
    try:
        main()
    except SystemExit:
        raise
    except Exception:
        pass
