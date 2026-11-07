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
# prints the first differences.  Exit 0 only when every file is the same, what each build writes on a load and save with
# no change among them, and at least one Return Home plan was compared.  A Return Home plan's moves are compared too when
# both builds plan by route work (OB-413); against an older build, 3.0.8 included, whose wall clock picks among plans run
# to run, they are printed and do not fail (see old-setups.py).
# The 3.0.8 build is `dev-3.0.9`.  Later builders: also the commit your branch started from.  TC_KEEP=1 keeps the base
# worktree.
#
# TC_OLD_SETUPS_LOCOS, _ROUTES and _TRIPS size the sample.  Routine use is 6 trips a setup, over 10 minutes a build, so
# schedule it with whoever owns the test slots; TC_OLD_SETUPS_TRIPS=30 is the full sample, about 50 minutes a build.
# TC_OLD_SETUPS_ONLY=<part of a setup's name> dumps only the setups it names, and is the way to look again at one.
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

TC_OLD_SETUPS_TRIPS="${TC_OLD_SETUPS_TRIPS:-6}"
export TC_OLD_SETUPS_TRIPS

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

# Without a locomotive database one build sees no trains, and every answer about a train reads as a difference.
if [ ! -f LocDB.data ]
then
    echo "old-setups: this worktree has no LocDB.data, so the comparison cannot mean anything; nothing was run.  Run: sh docs/tools/env.sh ." >&2
    exit 2
fi

git worktree remove --force "$OTHER" 2>/dev/null
git worktree add --detach "$OTHER" "$COMMIT" >/dev/null || exit 2

OTHER_SCRATCH="$(sh "$TOOLS/env.sh" "$OTHER" | sed -n 's/^TC_SCRATCH=//p')"

if [ ! -f "$OTHER/LocDB.data" ]
then
    echo "old-setups: the worktree of $COMMIT has no LocDB.data after env.sh; nothing was run." >&2
    exit 2
fi

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
