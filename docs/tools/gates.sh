#!/bin/sh
#
# The automatic gates for a merge to dev (RELEASE.md section 2), one PASS or FAIL line each.
#
#     TC_SCRATCH=<C:/ dir> sh docs/tools/gates.sh <base> [--layouts | --full]
#
# Run from the project root, as one background command; each run waits for its own test slot.  <base> is what the
# branch is merged onto (dev).  The gates, in order:
#
#   guards    the standing guard classes plus `affected.py --guards <base>`, through one.sh, at most 15 a run
#   spotbugs  no SpotBugs finding beyond docs/tools/spotbugs-baseline.txt (spotbugs.sh, about a minute)
#   fit       ui.testEveryLanguageFits: every window in every language; it opens windows, so it takes a window slot
#   updater   core.testTheLastReleaseUpdatesToThisBuild: the last release's updater finds, takes and starts this build
#   layouts   old-setups.sh --no-live against the last release tag: what both builds read from the test setups; only
#             with --layouts (2 trips a setup, over 20 minutes: once a batch) or --full (the routine 6, before a release)
#
# The last release tag is the highest v<n>_<n>... (no beta, none of the 1.x tags without an underscore).  Nothing here
# reads cs2_sample_layout, Adam's live railway.  TC_GATES="spotbugs fit" runs only the gates it names.
# Exit 0 only when every gate run passed; the logs are under $TC_SCRATCH/gates.

set -u

S="${TC_SCRATCH:-}"
BASE="${1:-}"
TOOLS="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$S" ] || [ ! -d "$S" ] || [ -z "$BASE" ] || [ ! -f build.xml ]
then
    echo "usage: TC_SCRATCH=<dir> sh docs/tools/gates.sh <base> [--layouts | --full]" >&2
    exit 2
fi

GATES="guards spotbugs fit updater"
TRIPS=2

case "${2:-}" in
    --layouts) GATES="$GATES layouts";;
    --full) GATES="$GATES layouts"; TRIPS=6;;
esac

GATES="${TC_GATES:-$GATES}"
LOGS="$S/gates"
mkdir -p "$LOGS"

TC_LAST_RELEASE="$(git tag --list 'v*' | grep -E '^v[0-9]+(_[0-9]+)+$' | sort -t_ -k1.2,1n -k2,2n -k3,3n -k4,4n | tail -1)"
export TC_LAST_RELEASE

if [ -z "$TC_LAST_RELEASE" ]; then echo "gates: no release tag found" >&2; exit 2; fi

STANDING="regression.testEveryTestIsInTheBattery regression.testNoNewCopies regression.testCommentsStayLean
regression.testTestSleepsOnlyGoDown regression.testJavadocsAreAttached core.testMessageBundles
regression.testEveryMessageKeyIsAskedFor regression.testEveryScenarioIsUsedAndSaysSo"

RESULTS=""
failed=0

# say <gate> <status> <detail>
say()
{
    if [ "$2" = 0 ]; then line="PASS $1"; else line="FAIL $1"; failed=1; fi
    RESULTS="$RESULTS$line  $3
"
    echo "$line  $3"
}

run_classes()
{
    TC_SCRATCH="$S" sh "$TOOLS/one.sh" "$@"
}

for gate in $GATES
do
    log="$LOGS/$gate.log"
    : > "$log"

    case "$gate" in
        guards)
            # The fit test is its own gate below, so it is not run twice
            py -3 "$TOOLS/affected.py" "$BASE" --guards 2>/dev/null > "$LOGS/affected.txt"
            classes="$(printf '%s\n' $STANDING $(cat "$LOGS/affected.txt") | tr -d '\r' | grep -v '^ui.testEveryLanguageFits$' \
                | sort -u)"
            status=0
            set -- $classes
            while [ $# -gt 0 ]
            do
                chunk=""
                n=0
                while [ $# -gt 0 ] && [ $n -lt 15 ]; do chunk="$chunk $1"; n=$((n + 1)); shift; done
                run_classes $chunk >> "$log" 2>&1 || status=1
            done
            say guards $status "$(echo $classes | wc -w) classes; $(grep -c '^Total tests run' "$log") reported"
            ;;
        spotbugs)
            TC_SCRATCH="$S" bash "$TOOLS/spotbugs.sh" > "$log" 2>&1
            status=$?
            say spotbugs $status "$(grep -m1 'findings' "$log")"
            ;;
        fit)
            run_classes ui.testEveryLanguageFits > "$log" 2>&1
            status=$?
            say fit $status "$(grep -m1 '^Total tests run' "$log")"
            ;;
        updater)
            run_classes core.testTheLastReleaseUpdatesToThisBuild > "$log" 2>&1
            status=$?
            say updater $status "$TC_LAST_RELEASE; $(grep -m1 '^Total tests run' "$log")"
            ;;
        layouts)
            started=$(date +%s)
            TC_OLD_SETUPS_TRIPS=$TRIPS TC_SCRATCH="$S" sh "$TOOLS/old-setups.sh" --no-live "$TC_LAST_RELEASE" > "$log" 2>&1
            status=$?
            say layouts $status "against $TC_LAST_RELEASE, $TRIPS trips, $(( ($(date +%s) - started) / 60 )) min; \
$(grep -m1 '^files compared' "$log")"
            ;;
        *)
            say "$gate" 1 "no such gate"
            ;;
    esac
done

echo ""
echo "gates against $BASE (last release $TC_LAST_RELEASE):"
printf '%s' "$RESULTS"

exit $failed
