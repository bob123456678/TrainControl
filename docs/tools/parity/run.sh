#!/bin/sh
# Runs both engines over the same four trains and compares what each will do.
#
# Usage: sh docs/tools/parity/run.sh [targetFolder] [runSeconds]
#
# runSeconds drives the optional timed autonomy run.  Zero - the default - records only what each
# engine would CONSIDER, which is what the superset question actually asks.  On a simulated station a
# timed run records nothing at all, because simulate mode does not move trains; see ParityDriver's
# header.
#
# Each engine runs FROM ITS OWN FOLDER.  The layout-override preference is namespaced by a hash of the
# working directory, so this is what keeps the two runs from reading each other's layout, locomotive
# database and window state.

set -e

# THREE LEVELS, NOT TWO (TSX-C16).
#
# These scripts moved from `tools/parity/` to `docs/tools/parity/` in fb3722f5, and this line did not
# move with them: `../..` from here is `docs/`, not the repository.  setup-env.sh carries the full
# account, including the three driver paths that resolved correctly BECAUSE this was wrong.
#
# What it costs HERE is smaller and worth naming on its own (VD9-C10): this file uses $REPO twice, for
# TARGET's default and for the `cd` before compare.py.  So the run used to cd into `docs/` and look for
# `docs/docs/tools/parity/compare.py`.
#
# It also moves TARGET's default.  With REPO wrong, `$REPO/../traincontrol-parity` landed INSIDE the
# repository; it now lands beside it, which is what the name always meant.  An environment built by
# the old version is still at the old path and is not found - pass it as $1, or let setup-env.sh build
# a new one.
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
TARGET=${1:-"$REPO/../traincontrol-parity"}
RUN_SECONDS=${2:-0}

JDK=${TC_JDK:-"/c/Program Files/Java/jdk1.8.0_361"}
JAVA="$JDK/bin/java.exe"

if [ ! -d "$TARGET/v2_8_1" ]
then
    echo "*** no environment at $TARGET - run docs/tools/parity/setup-env.sh first ***"
    exit 2
fi

mkdir -p "$TARGET/out"

# LAST RUN'S REPORTS GO FIRST (VD11-C4).
#
# Everything below runs under `set -e`, and the condition drivers sit before `compare.py`.  So a
# driver that crashes takes the whole script with it BEFORE the autonomy report is regenerated, and
# leaves the previous run's `report.md` sitting there with its own date on it - a stale answer that
# reads exactly like a fresh one.
#
# Deleting rather than stamping: a report that is not there cannot be misread, and there is no marker
# to remember to look for.
rm -f "$TARGET/out/report.md" "$TARGET/out/conditions.md"

# Debug mode dumps every CAN packet, which is worth keeping and useless on a terminal.
echo "building the 3.0.0 configuration from the track diagram..."

cd "$TARGET/v3_0_0"

"$JAVA" -Dtraincontrol.anyReceivePort=true \
    -cp "classes;TrainControl.jar" BuildDiagramSetup \
    "$TARGET/v3_0_0/cs2_sample_layout" "$TARGET/v3_0_0/autonomy.json" \
    > "$TARGET/out/build-v3_0_0.log" 2>&1

tail -1 "$TARGET/out/build-v3_0_0.log"

echo "recording 2.8.1..."

cd "$TARGET/v2_8_1"

"$JAVA" -Dtraincontrol.anyReceivePort=true \
    -cp "classes;TrainControl.jar" ParityDriver \
    "$TARGET/v2_8_1/cs2_sample_layout" \
    "$TARGET/v2_8_1/autonomy.json" \
    "$TARGET/out/v2_8_1.tsv" \
    2.8.1 "$RUN_SECONDS" > "$TARGET/out/run-v2_8_1.log" 2>&1

tail -1 "$TARGET/out/run-v2_8_1.log"

echo "recording 3.0.0..."

cd "$TARGET/v3_0_0"

"$JAVA" -Dtraincontrol.anyReceivePort=true \
    -cp "classes;TrainControl.jar" ParityDriver \
    "$TARGET/v3_0_0/cs2_sample_layout" \
    "$TARGET/v3_0_0/autonomy.json" \
    "$TARGET/out/v3_0_0.tsv" \
    3.0.0 "$RUN_SECONDS" > "$TARGET/out/run-v3_0_0.log" 2>&1

tail -1 "$TARGET/out/run-v3_0_0.log"

echo "probing the routing-logic preference..."

"$JAVA" -Dtraincontrol.anyReceivePort=true \
    -cp "classes;TrainControl.jar" PathPreferenceProbe \
    "$TARGET/v3_0_0/cs2_sample_layout" \
    "$TARGET/v3_0_0/autonomy.json" \
    "$TARGET/out/pathpref.tsv" > "$TARGET/out/pathpref.log" 2>&1

tail -1 "$TARGET/out/pathpref.log"

# ==================================================================== the route conditions
#
# Adam, 2026-09-03: "we would want to parse the JSON into a NodeExpression in the old one, and see if
# 3.0.0 has logically equivalent expressions in those routes."
#
# Separate from the sections above because it asks about ROUTES rather than about autonomy, and
# because it needs neither a model nor a layout: NodeExpression.fromJSON is a static function of a
# JSONObject, so this binds no port and opens no window.  That is also why it can read the operator's
# own routes.json where it lies - nothing here writes to that folder.
#
# It is worth its own section because a structural reading of that file answers the wrong question:
# fromJSON runs normalize, which INSERTS a NodeGroup around a cross-operator left child, so the tree
# each engine holds is not the tree in the file.  compare-conditions.py compares by truth table for
# the same reason - two trees that bracket differently and mean the same thing are not a difference.
# THE FROZEN COPY, not the live folder (VD10-C11).
#
# `testConditionOutline` reads `test/operator_layout` for a stated reason - "cs2_sample_layout is the
# live one and moves under a test's feet" - and this read the live one, so the number quoted into the
# report came from the file that argument calls unstable.  They are identical today, which is exactly
# how that goes unnoticed.
#
# TC_ROUTES still points it anywhere, including at the live file when that is the question being asked.
ROUTES="${TC_ROUTES:-$REPO/test/operator_layout/config/gleisbilder/routes.json}"

CONDITION_STATUS=0

if [ -f "$ROUTES" ]
then
    for ENGINE in v2_8_1 v3_0_0
    do
        (cd "$TARGET/$ENGINE" && "$JAVA" -cp "classes;TrainControl.jar" ConditionParityDriver \
            "$ROUTES" "$TARGET/out/conditions-$ENGINE.tsv" "$ENGINE")
    done

    echo ""
    echo "route conditions, 2.8.1 against 3.0.0:"

    set +e

    python "$REPO/docs/tools/parity/compare-conditions.py" \
        "$TARGET/out/conditions-v2_8_1.tsv" "$TARGET/out/conditions-v3_0_0.tsv" \
        "$TARGET/out/conditions.md"

    CONDITION_STATUS=$?

    set -e
else
    echo "*** NO routes.json AT $ROUTES - THE CONDITION COMPARISON DID NOT RUN ***"
    echo ""
    echo "This section has not been checked.  Point TC_ROUTES at a routes file, or accept that the"
    echo "run says nothing about whether 2.8.1 and 3.0.0 read your conditions the same way."

    # A section that did not run is not a section that passed (VD10-C11).
    CONDITION_STATUS=2
fi

echo ""

cd "$REPO"

# compare.py exits non-zero when the superset claim fails, which is a result rather than an error.
set +e

python docs/tools/parity/compare.py "$TARGET/out/v2_8_1.tsv" "$TARGET/out/v3_0_0.tsv" \
    "$TARGET/out/report.md"

STATUS=$?

# A condition that changed meaning between the two engines is a parity failure like any other - and a
# comparison that DID NOT RUN is a third thing, which this used to flatten into the second (VD11-C4).
#
# 1 means something is wrong with the railway.  2 means this run does not know, because the routes
# file was missing or too small to be evidence.  Collapsing them told a reader who checked the exit
# code that a skipped section had failed - which is the same shape as the finding that put the floor
# in, arriving at the code that reports it.
#
# A real difference wins over a skip, because it is the more serious of the two.
if [ "${CONDITION_STATUS:-0}" -eq 1 ]
then
    STATUS=1
elif [ "${CONDITION_STATUS:-0}" -ne 0 ] && [ "$STATUS" -eq 0 ]
then
    STATUS="$CONDITION_STATUS"
fi

set -e

echo ""
echo "report: $TARGET/out/report.md"
echo "        $TARGET/out/conditions.md"
echo "logs:   $TARGET/out/"

exit $STATUS
