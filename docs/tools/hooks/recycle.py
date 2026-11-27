# Usage: python docs/tools/hooks/recycle.py PATH...
#
# Moves files and folders to the Windows Recycle Bin, so a delete outside the scratch folders can be undone and needs no
# permission prompt: claude_guard.py sends such deletes here.  A path that is not there is skipped, as rm -f would.  It
# refuses, and recycles nothing, when a path is a drive root, a share, the home or temp folder or a folder above one,
# a whole git checkout, or when the paths come to more than LIMIT bytes (a Recycle Bin can be too small, and Windows
# then deletes for good): those wait for Adam's go.  A pattern (*, ?) is expanded here.
import ctypes, glob, os, re, sys
from ctypes import wintypes

LIMIT = 1 << 30          # bytes in one call
MAX_ENTRIES = 20000      # files and folders walked before the size is taken as too big to judge


def full(path):
    """An absolute Windows path from C:\\x, C:/x or Git Bash's /c/x."""
    p = path.replace('\\', '/')
    m = re.match(r'^/([a-zA-Z])(?:/(.*))?$', p)
    if m:
        p = m.group(1) + ':/' + (m.group(2) or '')
    return os.path.normpath(os.path.abspath(p))


def _env(name):
    v = os.environ.get(name) or ''
    return os.path.normcase(full(v)) if re.match(r'^([A-Za-z]:[\\/]|/[a-zA-Z]/)', v) else ''


def protected():
    """The folders that are never recycled, nor any folder above them."""
    out = {_env(n) for n in ('TEMP', 'TMP', 'USERPROFILE', 'HOME', 'LOCALAPPDATA', 'APPDATA', 'SystemRoot',
                             'ProgramFiles', 'ProgramFiles(x86)', 'ProgramData')}
    out.add(os.path.normcase(full(os.path.expanduser('~'))))
    return {p for p in out if p}


def size_of(path, budget):
    """Bytes under path, or None once more than budget entries have been walked."""
    if not os.path.isdir(path) or os.path.islink(path):
        return os.path.getsize(path) if os.path.lexists(path) else 0
    total, seen = 0, 0
    for top, dirs, files in os.walk(path):
        seen += len(dirs) + len(files)
        if seen > budget:
            return None
        for f in files:
            try:
                total += os.path.getsize(os.path.join(top, f))
            except OSError:
                pass
    return total


def refusal(paths):
    """Why these paths must not go to the Recycle Bin without Adam's go; None when they may."""
    keep = protected()
    total = 0
    for raw in paths:
        if raw[:2] in ('\\\\', '//'):
            return raw + ' is on a share, which has no Recycle Bin'
        p = full(raw)
        c = os.path.normcase(p)
        drive, rest = os.path.splitdrive(p)
        if not drive or rest in ('', '\\', '/'):
            return p + ' is a drive root'
        for k in keep:
            if c == k or k.startswith(c.rstrip('\\') + '\\'):
                return p + ' is the home, temp or a system folder, or holds one'
        if os.path.isdir(p) and os.path.exists(os.path.join(p, '.git')):
            return p + ' is a whole git checkout'
        n = size_of(p, MAX_ENTRIES)
        if n is None:
            return p + ' holds more than %d files and folders' % MAX_ENTRIES
        total += n
        if total > LIMIT:
            return 'the paths come to more than %d MB, more than a Recycle Bin may take' % (LIMIT >> 20)
    return None


class SHFILEOPSTRUCTW(ctypes.Structure):
    _fields_ = [('hwnd', wintypes.HWND), ('wFunc', wintypes.UINT), ('pFrom', wintypes.LPCWSTR),
                ('pTo', wintypes.LPCWSTR), ('fFlags', ctypes.c_uint16), ('fAnyOperationsAborted', wintypes.BOOL),
                ('hNameMappings', ctypes.c_void_p), ('lpszProgressTitle', wintypes.LPCWSTR)]


FO_DELETE = 3
# Undo, no prompts, no progress, no error boxes; but a warning box if Windows would delete for good after all
FLAGS = 0x0040 | 0x0010 | 0x0004 | 0x0400 | 0x4000


def recycle(paths):
    op = SHFILEOPSTRUCTW(None, FO_DELETE, '\0'.join(paths) + '\0\0', None, FLAGS, False, None, None)
    code = ctypes.windll.shell32.SHFileOperationW(ctypes.byref(op))
    return code, bool(op.fAnyOperationsAborted)


def main(argv):
    if not argv:
        print('usage: python recycle.py PATH...')
        return 2
    paths = []
    for a in argv:
        hits = glob.glob(full(a)) if re.search(r'[*?\[]', a) else [full(a)]
        paths += [h for h in hits if os.path.lexists(h)]
    skipped = [a for a in argv if not re.search(r'[*?\[]', a) and not os.path.lexists(full(a))]
    for a in skipped:
        print('not there, skipped: ' + full(a))
    if not paths:
        return 0
    why = refusal(paths)
    if why:
        print('recycled nothing: ' + why + '.  This delete waits for Adam\'s go: ask him in chat first.')
        return 1
    code, aborted = recycle(paths)
    gone = [p for p in paths if not os.path.lexists(p)]
    for p in gone:
        print('recycled: ' + p)
    if code or aborted or len(gone) != len(paths):
        print('Windows could not recycle all of them (code %d); still there: %s' %
              (code, ', '.join(p for p in paths if os.path.lexists(p))))
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
