#!/bin/sh
#
# Sets up a worker's worktree so one.sh and battery.sh run in it, and prints the line to run them with.
#
#     sh docs/tools/env.sh <worktree> [cp.txt [scratch-dir]]
#
# It makes (each only when missing, so running it again changes nothing):
#   - <worktree>/LocDB.data, a copy of test/LocDB3_0_0.data (the file is git-ignored and never Adam's real one);
#   - the scratch folder, <worktree>-scratch unless one is named, holding cp.txt: a copy of the file given, else the
#     classpath one.sh compiles with, read from one.sh itself so the two cannot drift.
# The last line is TC_SCRATCH=... in the C:/ form the runners need, never /c/.

set -eu

W="${1:-}"

if [ -z "$W" ] || [ ! -f "$W/build.xml" ] || [ ! -f "$W/test/LocDB3_0_0.data" ]
then
    echo "usage: sh docs/tools/env.sh <worktree> [cp.txt [scratch-dir]]  (a worktree has build.xml and test/LocDB3_0_0.data)" >&2
    exit 2
fi

W=$(cd "$W" && pwd)

SCRATCH="${3:-$W-scratch}"

mkdir -p "$SCRATCH"

SCRATCH=$(cd "$SCRATCH" && pwd)

if [ ! -f "$W/LocDB.data" ]
then
    cp "$W/test/LocDB3_0_0.data" "$W/LocDB.data"
    echo "created $(cygpath -m "$W")/LocDB.data"
fi

if [ ! -f "$SCRATCH/cp.txt" ]
then
    if [ -n "${2:-}" ]
    then
        cp "$2" "$SCRATCH/cp.txt"
    else
        sed -n 's/^CP="\(.*\)"$/\1/p' "$W/docs/tools/one.sh" | head -1 | tr -d '\n' > "$SCRATCH/cp.txt"
    fi

    echo "created $(cygpath -m "$SCRATCH")/cp.txt"
fi

echo "TC_SCRATCH=$(cygpath -m "$SCRATCH")"
