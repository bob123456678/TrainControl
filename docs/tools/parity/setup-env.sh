#!/bin/sh
# Builds the two-engine parity environment, outside the repository.
#
# Adam: "Make a new folder with the traincontrol 2_8_1 jar, 3_0_0 beta jar from our current work,
# current locdb, current uistate, and cs2_sample_layout."
#
# SEPARATE FOLDERS PER ENGINE, AND THAT IS LOAD-BEARING RATHER THAN TIDY. The layout-override
# preference key is namespaced by a hash of the working directory, so two engines run from two folders
# keep two sets of preferences and cannot read each other's layout override, locomotive database or
# window state. Run them from one folder and the second run inherits whatever the first left behind -
# which is the same mechanism that made TrainControl show different locomotives when launched from the
# Triage app rather than from NetBeans.
#
# THE LIVE LAYOUT IS COPIED, NEVER USED IN PLACE. cs2_sample_layout is Adam's real railway and is not
# recoverable; autonomy writes to it every time a train moves. Each engine gets its own copy so that
# neither the experiment nor the other engine can touch the original, and so the two runs cannot
# contaminate each other through a shared placement file.
#
# Usage: sh docs/tools/parity/setup-env.sh [targetFolder]

set -e

# THREE LEVELS, NOT TWO (TSX-C16).
#
# These scripts moved from `tools/parity/` to `docs/tools/parity/` in fb3722f5, and this line
# did not move with them: `../..` from here is `docs/`, not the repository.  Everything reached
# through $REPO was therefore one folder short - the jar at $REPO/dist, the sample layout, the
# four copied files, and `cd "$REPO"` - and the harness could not be set up at all.
#
# The three driver paths below were the exception, and that is what made it hard to see: they
# still read `$REPO/tools/parity`, which resolved correctly BECAUSE $REPO was wrong.  Both
# halves have to be right at once or the fix is invisible.
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
TARGET=${1:-"$REPO/../traincontrol-parity"}

JAR_URL="https://github.com/bob123456678/TrainControl/releases/download/v2_8_1/TrainControl.jar"

JDK=${TC_JDK:-"/c/Program Files/Java/jdk1.8.0_361"}
JAVAC="$JDK/bin/javac.exe"

echo "repo:   $REPO"
echo "target: $TARGET"

mkdir -p "$TARGET/v2_8_1" "$TARGET/v3_0_0" "$TARGET/out"

# ==================================================================== the two jars
if [ ! -f "$TARGET/v2_8_1/TrainControl.jar" ]
then
    echo "downloading 2.8.1 from $JAR_URL"
    curl -fsSL -o "$TARGET/v2_8_1/TrainControl.jar" "$JAR_URL"
else
    echo "2.8.1 jar already present"
fi

if [ ! -f "$REPO/dist/TrainControl.jar" ]
then
    echo "*** NO dist/TrainControl.jar - build it in NetBeans first (Clean and Build) ***"
    exit 2
fi

cp "$REPO/dist/TrainControl.jar" "$TARGET/v3_0_0/TrainControl.jar"

# AND ITS lib FOLDER, WHICH IS NOT OPTIONAL.
#
# The 2.8.1 release is a fat jar and carries org.json inside it; the jar NetBeans builds is thin and
# names its dependencies in a manifest Class-Path of "lib/...", resolved relative to the jar. Copying
# the jar alone produced a run that printed "Restoring state..." and exited 1 with no stack trace,
# which reads exactly like a hang and is not one.
rm -rf "$TARGET/v3_0_0/lib"
cp -r "$REPO/dist/lib" "$TARGET/v3_0_0/lib"

# The jar is whatever NetBeans last produced, which is not necessarily the working tree.  Said out
# loud, because a parity run against a stale 3.0.0 jar would look like a regression in 3.0.0.
echo "3.0.0 jar copied from dist, dated: $(date -r "$REPO/dist/TrainControl.jar" '+%Y-%m-%d %H:%M')"
echo "  (if that is older than your last change, rebuild in NetBeans and run this again)"

# ==================================================================== state and layout, one copy each
for ENGINE in v2_8_1 v3_0_0
do
    for FILE in LocDB.data UIState.data
    do
        if [ -f "$REPO/$FILE" ]
        then
            cp "$REPO/$FILE" "$TARGET/$ENGINE/$FILE"
        else
            echo "*** $FILE not found in $REPO ***"
            exit 2
        fi
    done

    rm -rf "$TARGET/$ENGINE/cs2_sample_layout"
    cp -r "$REPO/cs2_sample_layout" "$TARGET/$ENGINE/cs2_sample_layout"
done

# ==================================================================== the old engine reads the old file
#
# "Into the 2_8_1 copy, load autonomy.json from autonomy_legacy."  2.8.1 has no notion of the
# diagram-derived setup, so the hand-built graph is the only thing it can run - and it is also the
# thing 3.0.0 is being asked to be a superset of.
LEGACY="$TARGET/v2_8_1/cs2_sample_layout/config/autonomy_legacy/autonomy.json"

if [ ! -f "$LEGACY" ]
then
    echo "*** no legacy autonomy.json at $LEGACY ***"
    exit 2
fi

cp "$LEGACY" "$TARGET/v2_8_1/autonomy.json"

echo "legacy graph staged at $TARGET/v2_8_1/autonomy.json"

# ==================================================================== the drivers, compiled per engine
#
# ParityDriver is compiled TWICE, once against each jar, and uses nothing newer than 2.8.1.  If it
# fails to compile against the old jar, an API it depends on did not exist then and the comparison
# would not have been like for like.
for ENGINE in v2_8_1 v3_0_0
do
    mkdir -p "$TARGET/$ENGINE/classes"

    "$JAVAC" -nowarn -encoding UTF-8 \
        -cp "$TARGET/$ENGINE/TrainControl.jar" \
        -d "$TARGET/$ENGINE/classes" \
        "$REPO/docs/tools/parity/ParityDriver.java"

    "$JAVAC" -nowarn -encoding UTF-8 \
        -cp "$TARGET/$ENGINE/TrainControl.jar" \
        -d "$TARGET/$ENGINE/classes" \
        "$REPO/docs/tools/parity/ConditionParityDriver.java"

    echo "compiled ConditionParityDriver against $ENGINE"

    echo "compiled ParityDriver against $ENGINE"
done

# The diagram-to-JSON step only exists in the new build.
"$JAVAC" -nowarn -encoding UTF-8 \
    -cp "$TARGET/v3_0_0/TrainControl.jar" \
    -d "$TARGET/v3_0_0/classes" \
    "$REPO/docs/tools/parity/BuildDiagramSetup.java"

echo "compiled BuildDiagramSetup against v3_0_0"

# The routing-logic preference exists only in 3.0.0, so its probe does too.
"$JAVAC" -nowarn -encoding UTF-8 \
    -cp "$TARGET/v3_0_0/TrainControl.jar" \
    -d "$TARGET/v3_0_0/classes" \
    "$REPO/docs/tools/parity/PathPreferenceProbe.java"

echo "compiled PathPreferenceProbe against v3_0_0"
echo ""
echo "environment ready: $TARGET"
echo "next: sh docs/tools/parity/run.sh \"$TARGET\""
