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
ROUTES="${TC_ROUTES:-$REPO/cs2_sample_layout/config/gleisbilder/routes.json}"

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
        "$TARGET/out/conditions-v2_8_1.tsv" "$TARGET/out/conditions-v3_0_0.tsv"

    CONDITION_STATUS=$?

    set -e
else
    echo "*** no routes.json at $ROUTES - the condition comparison was skipped ***"
fi

echo ""

cd "$REPO"

# compare.py exits non-zero when the superset claim fails, which is a result rather than an error.
set +e

python docs/tools/parity/compare.py "$TARGET/out/v2_8_1.tsv" "$TARGET/out/v3_0_0.tsv" \
    "$TARGET/out/report.md"

STATUS=$?

# A condition that changed meaning between the two engines is a parity failure like any other.
if [ "${CONDITION_STATUS:-0}" -ne 0 ]
then
    STATUS=1
fi

set -e

echo ""
echo "report: $TARGET/out/report.md"
echo "logs:   $TARGET/out/"

exit $STATUS
