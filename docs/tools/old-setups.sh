#!/bin/sh
#
# Proves old setups mean what they meant (FR-111): the same files, read by an older build and by this working tree.
#
#     TC_SCRATCH=<scratch> sh docs/tools/old-setups.sh <base-commit>          # side by side, and the comparison
#     TC_SCRATCH=<scratch> sh docs/tools/old-setups.sh --opens-in <commit>    # the new features, opened by an older build
#
# Run from the project root, as one background command; each one.sh waits for its own test slot.
#
# Side by side: a detached worktree of <base-commit> under $TC_SCRATCH gets a copy of support.SetupDump and
# core.testSetupDumpComparesBuilds; both builds dump every test layout, every autonomy format fixture and one read-only
# copy of cs2_sample_layout; docs/tools/old-setups.py compares them file by file, says how many answers it compared and
# prints the first differences.  Exit 0 only when every file is the same.  TC_OLD_SETUPS_LOCOS, _ROUTES and _TRIPS size
# the sample.  Later builders: use the commit your branch started from, and keep the base worktree with TC_KEEP=1.
#
# --opens-in: the same worktree trick for core.testASetupFromANewerBuildOpens over test/layouts/saved-by-3-1-0, then the
# files that build saved are read back by this one.  The 3.0.8 build is `dev-3.0.9` (it is only renumbered).

set -u

S="${TC_SCRATCH:-}"
W="$(pwd)"
TOOLS="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$S" ] || [ ! -d "$S" ] || [ ! -f build.xml ] || [ $# -lt 1 ]
then
    echo "usage: TC_SCRATCH=<dir> sh docs/tools/old-setups.sh (<base-commit> | --opens-in <commit>)" >&2
    exit 2
fi

OUT="$S/old-setups"
OTHER="$OUT/other-build"

mkdir -p "$OUT"

# The older build in a worktree of its own, its data folder and scratch set up by env.sh.
if [ "$1" = "--opens-in" ]
then
    COMMIT="$2"
else
    COMMIT="$1"
fi

git worktree remove --force "$OTHER" 2>/dev/null
git worktree add --detach "$OTHER" "$COMMIT" >/dev/null || exit 2

OTHER_SCRATCH="$(sh "$TOOLS/env.sh" "$OTHER" | sed -n 's/^TC_SCRATCH=//p')"

copy_in()
{
    cp "test/$1" "$OTHER/test/$1" || exit 2
}

status=0

if [ "$1" = "--opens-in" ]
then
    copy_in support/Scenario.java
    copy_in core/testASetupFromANewerBuildOpens.java

    rm -rf "$OTHER/test/layouts/saved-by-3-1-0"
    cp -r test/layouts/saved-by-3-1-0 "$OTHER/test/layouts/saved-by-3-1-0" || exit 2

    rm -rf "$OUT/older-saved"
    cp -r test/layouts/saved-by-3-1-0 "$OUT/older-saved" || exit 2

    (cd "$OTHER" && TC_OLDER_SAVES_TO="$OUT/older-saved" TC_SCRATCH="$OTHER_SCRATCH" \
        sh docs/tools/one.sh core.testASetupFromANewerBuildOpens) || status=1

    TC_OLDER_SAVED="$OUT/older-saved" TC_SCRATCH="$S" sh docs/tools/one.sh core.testASetupFromANewerBuildOpens || status=1
else
    copy_in support/Scenario.java
    copy_in support/SetupDump.java
    copy_in core/testSetupDumpComparesBuilds.java

    # One copy, so both read the same railway whatever the live one does meanwhile; LayoutSandbox copies it again.
    rm -rf "$OUT/base" "$OUT/new" "$OUT/live-copy"
    cp -r cs2_sample_layout "$OUT/live-copy" || exit 2

    (cd "$OTHER" && TC_OLD_SETUPS_OUT="$OUT/base" TC_OLD_SETUPS_LIVE="$OUT/live-copy" TC_SCRATCH="$OTHER_SCRATCH" \
        sh docs/tools/one.sh core.testSetupDumpComparesBuilds) || status=1

    TC_OLD_SETUPS_OUT="$OUT/new" TC_OLD_SETUPS_LIVE="$OUT/live-copy" TC_SCRATCH="$S" \
        sh docs/tools/one.sh core.testSetupDumpComparesBuilds || status=1

    py -3 "$TOOLS/old-setups.py" "$OUT/base" "$OUT/new" || status=1
fi

[ -n "${TC_KEEP:-}" ] || git worktree remove --force "$OTHER"

exit $status
