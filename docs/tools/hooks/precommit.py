# The git pre-commit check, run by `pre-commit` beside it: README.md says how it is installed and what it checks.
#
# Reads what is staged (never the working tree), prints every problem it finds and exits 1 on any, so the commit
# stops.  Only fast text checks live here; the full guards are the regression classes the battery runs.
import re, subprocess, sys


def git(*args):
    return subprocess.run(['git'] + list(args), capture_output=True, text=True, encoding='utf-8',
                          errors='replace').stdout


def staged_text(path):
    return subprocess.run(['git', 'show', ':' + path], capture_output=True).stdout


def dashed(line):
    """An em dash, an en dash or a spaced hyphen in prose.  Code spans, image alt text, a bracketed date range such as
    "[11/2025 - 3/2026]" and a table cell holding only keys such as "+ and -" are not prose.  Same as claude_guard.py's."""
    prose = re.sub(r'`[^`]*`', '', line)
    prose = re.sub(r'!\[[^\]]*\]', '', prose)
    prose = re.sub(r'\[\d[\d/.]* - [^\]]*\]', '', prose)
    cells = prose.strip().strip('|').split('|') if prose.lstrip().startswith('|') else [prose]
    return any('—' in c or '–' in c or re.search(r'\S - \S', c) for c in cells)


def orphans(source):
    """testJavadocsAreAttached's count: a javadoc with nothing but whitespace before the next one."""
    found, at = 0, source.find('/**')
    while at >= 0:
        ends = source.find('*/', at + 3)
        if ends < 0:
            break
        nxt = source.find('/**', ends + 2)
        if nxt < 0:
            break
        if not source[ends + 2:nxt].strip():
            found += 1
        at = source.find('/**', at + 3)
    return found


problems = []
staged = [p for p in git('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z').split('\0') if p]

for path in staged:
    if re.match(r'^(LocDB[^/]*\.data|UIState[^/]*\.data|autonomy\.json)$', path, re.I):
        problems.append(path + ' is Adam\'s live state and never goes into the repository; unstage it')
    elif path.endswith('.properties'):
        raw = staged_text(path)
        for n, line in enumerate(raw.split(b'\n'), 1):
            if any(b > 127 for b in line):
                problems.append('%s:%d is not ASCII; write \\uXXXX escapes' % (path, n))
                break
    elif path.endswith('.java'):
        n = orphans(staged_text(path).decode('utf-8', 'replace'))
        if n:
            problems.append('%s has %d javadoc%s straight above another, documenting nothing' % (path, n, 's' * (n > 1)))
    elif path.lower() in ('readme.md', 'automation.md'):
        # Added lines only, so a line already there is never judged
        for line in git('diff', '--cached', '-U0', '--', path).splitlines():
            if line.startswith('+') and not line.startswith('+++') and dashed(line[1:]):
                problems.append('%s: no em dashes, en dashes or spaced hyphens: "%s"' % (path, line[1:].strip()[:80]))

# Not a refusal: the live layout's own changes go into the commits, and a commit that leaves them out is usually a slip
if git('diff', '--name-only', '--', 'cs2_sample_layout').strip():
    print('pre-commit: note: cs2_sample_layout has unstaged changes; they belong in the commit')

if problems:
    print('pre-commit: the commit is stopped (docs/tools/hooks/README.md):')
    for p in problems:
        print('  ' + p)
    sys.exit(1)
