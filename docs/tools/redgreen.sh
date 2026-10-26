#!/bin/sh
#
# Shows a claim red on the old code, then green on the working tree, with both result lines.
#
#     TC_SCRATCH=<scratch> sh docs/tools/redgreen.sh --base <commit> [--keep] <claim>...
#     TC_SCRATCH=<scratch> sh docs/tools/redgreen.sh --patch <file>  [--keep] <claim>...
#
# A claim is pkg.Class (the whole class) or pkg.Class.method.  Run from the project root, with the claims' test files
# in the working tree, committed or not.
#
#   --base   the red run uses the working tree's tests over the src/ of <commit>: for a claim written with its fix.
#   --patch  the red run uses the working tree with a unified diff applied (a mutation, from a MUTATION: note): `git diff
#            src > mutation.patch` after making the change, then undo it.  The working tree itself is never touched.
#
# Both runs are docs/tools/one.sh (the red one in a copy of the tree under $TC_SCRATCH/redgreen-tree, removed afterwards
# unless --keep).  Exit 0 when the red run failed and the green one is clean; 1 when it was not red (the claim proves
# nothing) or not green; 2 on a usage error or when the red tree does not compile.

set -u

BASE=""
PATCH=""
KEEP=""
CLAIMS=""

while [ $# -gt 0 ]
do
    case "$1" in
        --base) BASE="${2:-}"; shift;;
        --patch) PATCH="${2:-}"; shift;;
        --keep) KEEP=1;;
        -*) echo "unknown option $1" >&2; exit 2;;
        *) CLAIMS="$CLAIMS $1";;
    esac

    shift
done

S="${TC_SCRATCH:-}"

if [ -z "$S" ] || [ ! -d "$S" ] || [ ! -f build.xml ] || [ -z "$CLAIMS" ] || { [ -z "$BASE" ] && [ -z "$PATCH" ]; } \
    || { [ -n "$BASE" ] && [ -n "$PATCH" ]; } || { [ -n "$PATCH" ] && [ ! -f "$PATCH" ]; }
then
    echo "usage: TC_SCRATCH=<dir> sh docs/tools/redgreen.sh (--base <commit> | --patch <file>) [--keep] pkg.Class[.method]..." >&2
    exit 2
fi

W="$(pwd)"
TOOLS="$(cd "$(dirname "$0")" && pwd)"
TREE="$S/redgreen-tree"
RED_S="$S/redgreen-red"

[ -n "$PATCH" ] && PATCH="$(cd "$(dirname "$PATCH")" && pwd)/$(basename "$PATCH")"

# One.sh takes pkg.Class, or label:pkg.Class.method for one method.
ARGS=""

for c in $CLAIMS
do
    case "$c" in
        *.*.*) cls="${c%.*}"; ARGS="$ARGS ${cls#*.}:$c";;
        *) ARGS="$ARGS $c";;
    esac
done

rm -rf "$TREE" "$RED_S"
mkdir -p "$TREE" "$RED_S"

# The tree the red run uses: HEAD's files, the working tree's tests, and the src/ the mode asks for.
git archive HEAD | tar -x -C "$TREE" || exit 2

rm -rf "$TREE/test" "$TREE/src"
cp -r test "$TREE/test"
cp build.xml "$TREE/build.xml"

[ -f LocDB.data ] && cp LocDB.data "$TREE/LocDB.data"

if [ -n "$BASE" ]
then
    git archive "$BASE" src | tar -x -C "$TREE" || exit 2
    WHAT="src/ of $BASE"
else
    cp -r src "$TREE/src"
    (cd "$TREE" && git apply --ignore-whitespace "$PATCH") || { echo "the patch does not apply to the working tree" >&2; exit 2; }
    WHAT="$(basename "$PATCH") applied"
fi

(cd "$TREE" && TC_SCRATCH="$RED_S" bash "$TOOLS/one.sh" $ARGS) > "$S/redgreen-red.txt" 2>&1
RED_STATUS=$?

TC_SCRATCH="$S" bash "$TOOLS/one.sh" $ARGS > "$S/redgreen-green.txt" 2>&1
GREEN_STATUS=$?

show()
{
    grep -E '^(---|Total tests run|Configuration Failures: [1-9]|\*\*\*)' "$1" | sed "s/^/    /"
}

echo "RED (the working tree's tests on $WHAT):"
show "$S/redgreen-red.txt"

FIRST=$(tr '\n' ' ' < "$RED_S"/oneout/junitreports/TEST-*.xml 2>/dev/null | grep -o '<failure[^>]*' | head -1 | cut -c1-300)

[ -n "$FIRST" ] && echo "    first failure: $FIRST"

echo "GREEN (the working tree):"
show "$S/redgreen-green.txt"

[ -z "$KEEP" ] && rm -rf "$TREE"

if grep -q "DOES NOT COMPILE" "$S/redgreen-red.txt"
then
    echo "redgreen: the red tree does not compile (the claim needs the new code to build); use --patch with a mutation instead"

    exit 2
fi

if ! grep -qE "Failures: [1-9]" "$S/redgreen-red.txt" && [ "$RED_STATUS" -eq 0 ]
then
    echo "redgreen: NOT RED - the claim passed without the change, so it proves nothing"

    exit 1
fi

if [ "$GREEN_STATUS" -ne 0 ]
then
    echo "redgreen: red, but NOT GREEN on the working tree"

    exit 1
fi

echo "redgreen: red without the change, green with it"
