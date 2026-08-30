#!/bin/sh
# Runs both engines over the same four trains and compares what each will do.
#
# Usage: sh tools/parity/run.sh [targetFolder] [runSeconds]
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

REPO=$(cd "$(dirname "$0")/../.." && pwd)
TARGET=${1:-"$REPO/../traincontrol-parity"}
RUN_SECONDS=${2:-0}

JDK=${TC_JDK:-"/c/Program Files/Java/jdk1.8.0_361"}
JAVA="$JDK/bin/java.exe"

if [ ! -d "$TARGET/v2_8_1" ]
then
    echo "*** no environment at $TARGET - run tools/parity/setup-env.sh first ***"
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

cd "$REPO"

# compare.py exits non-zero when the superset claim fails, which is a result rather than an error.
set +e

python tools/parity/compare.py "$TARGET/out/v2_8_1.tsv" "$TARGET/out/v3_0_0.tsv" \
    "$TARGET/out/report.md"

STATUS=$?

set -e

echo ""
echo "report: $TARGET/out/report.md"
echo "logs:   $TARGET/out/"

exit $STATUS
