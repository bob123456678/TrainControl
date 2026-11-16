#!/bin/sh
#
# Runs named test classes from test/, one JVM each, each with a data folder and a preference node of
# its own, so no test reads or writes the owner's settings or the working folder's LocDB.data and
# UIState.data (Util.dataPath and Util.preferencesFor, D149).
#
# Usage, from the project root:
#
#     TC_SCRATCH=C:/some/scratch/dir sh docs/tools/one.sh testSomething [testOther...]
#
# A class starts with no locomotive database, as on a first launch, except the classes in NEEDS_LOCDB,
# which build on the locomotives a real one holds: they get the fixture test/LocDB2_8_0.data, or the
# file TC_LOCDB names.  Each class's log stays in TC_SCRATCH as one-<class>.log.
# Never run tests through ant, NetBeans or a bare java command.

set -u

# Windows programs get their arguments as written: path conversion breaks a ";" class path
export MSYS_NO_PATHCONV=1

S="${TC_SCRATCH:-}"
if [ -z "$S" ] || [ ! -d "$S" ]; then echo "TC_SCRATCH must name an existing scratch directory." >&2; exit 2; fi
if [ ! -d src ] || [ ! -d test ]; then echo "Run this from the project root." >&2; exit 2; fi
if [ $# -eq 0 ]; then echo "Name at least one test class." >&2; exit 2; fi

S="$(cygpath -m "$S")"
JDK="${TC_JDK:-C:/Program Files/Java/jdk1.8.0_361}"
RUN_ID="one-$$"
BUILD="$S/build-$RUN_ID"
STATE="$S/state-$$"
NODE_KEY="HKCU\\Software\\JavaSoft\\Prefs\\traincontrol-test-runs\\$RUN_ID"
NEEDS_LOCDB="testRoutes"

# Classes that build the window, which asks in a modal dialog to create a track diagram when it has no
# layout: they get a copy of test/layout, named in the run's node under the key TrainControlUI uses
NEEDS_LAYOUT="testAutonomyPathValidation testLayoutTiles"

forget()
{
    # Only this run's own subtree of the test-run root, never the application's node
    reg.exe delete "$NODE_KEY" /f >/dev/null 2>&1
    rm -rf "$STATE"
}

# 2.8.x's control station binds the fixed UDP port 15730, so two classes from different runs on this
# machine cannot run at once: each class takes a machine-wide lock first (a lock older than 15 minutes
# is a dead run's and is taken over)
LOCK="${TEMP:-/tmp}/traincontrol-283-udp.lock"
OWN_LOCK=""

lock()
{
    # The holder writes its pid into the lock; a lock is taken over only when that holder is gone, or when it names
    # no holder and is older than 15 minutes (a run killed between mkdir and the write).
    while ! mkdir "$LOCK" 2>/dev/null
    do
        holder=$(cat "$LOCK/pid" 2>/dev/null)
        if [ -n "$holder" ]; then
            kill -0 "$holder" 2>/dev/null || { rm -rf "$LOCK"; continue; }
        elif [ -n "$(find "$LOCK" -maxdepth 0 -mmin +15 2>/dev/null)" ]; then
            rm -rf "$LOCK"; continue
        fi
        sleep 2
    done
    echo $$ > "$LOCK/pid"
    OWN_LOCK=1
}

unlock()
{
    if [ -n "$OWN_LOCK" ]; then rm -rf "$LOCK"; OWN_LOCK=""; fi
}

trap 'unlock; forget; rm -rf "$BUILD"' EXIT
trap 'exit 130' INT TERM

# The test jars are not in 2.8.x's tree; these are dev's resources_test blobs, read from the repository
mkdir -p "$BUILD/classes" "$BUILD/lib"
git cat-file blob 5af78d84af79773de5ff223de4969046d2f4b700 > "$BUILD/lib/testng-6.14.3.jar" &&
git cat-file blob b74498a8e74a44ebc07a1a906436f1f8d456f8b7 > "$BUILD/lib/jcommander-1.69.jar" ||
    { echo "Could not read the TestNG jars from the repository." >&2; exit 2; }

CP="src;test"
for J in resources/*.jar "$BUILD"/lib/*.jar; do CP="$CP;$J"; done

find src test -name "*.java" > "$BUILD/files.txt"
if ! "$JDK/bin/javac" -nowarn -encoding UTF-8 -source 1.8 -target 1.8 -d "$BUILD/classes" -cp "$CP" \
    @"$BUILD/files.txt" > "$S/one-javac.log" 2>&1
then
    echo "*** The working tree does not compile; nothing was run (see $S/one-javac.log) ***"
    grep -i "error" "$S/one-javac.log" | head -20
    exit 2
fi

cat > "$BUILD/seed.js" <<'EOF'
var node = Java.type("org.traincontrol.util.Util").preferencesFor(Java.type("org.traincontrol.gui.PositionAwareJFrame").class);
node.put("LayoutOverridePath" + Java.type("org.traincontrol.util.Conversion").getFolderHash(10), arguments[0]);
node.flush();
EOF

layout_before=$(git status --porcelain -- cs2_sample_layout)

for T in "$@"
do
    forget
    mkdir -p "$STATE"
    case " $NEEDS_LOCDB " in *" $T "*) cp "${TC_LOCDB:-test/LocDB2_8_0.data}" "$STATE/LocDB.data";; esac
    case " $NEEDS_LAYOUT " in *" $T "*)
        cp -r test/layout "$STATE/layout"
        "$JDK/bin/jjs" -J-Dtraincontrol.preferences="traincontrol-test-runs/$RUN_ID" -J-Djava.class.path="$BUILD/classes;$CP" \
            "$BUILD/seed.js" -- "$STATE/layout";;
    esac

    LOG="$S/one-$T.log"
    lock
    "$JDK/bin/java" -ea -Dtraincontrol.dataDir="$STATE" -Dtraincontrol.preferences="traincontrol-test-runs/$RUN_ID" \
        -cp "$BUILD/classes;$CP" org.testng.TestNG -verbose 2 -testclass "$T" -d "$BUILD/out" > "$LOG" 2>&1
    unlock

    echo "--- $T"
    grep -E "^(FAILED|SKIPPED)( CONFIGURATION)?:" "$LOG" | cut -c1-160
    if grep -q "Total tests run" "$LOG"
    then
        grep "Total tests run" "$LOG" | tail -1
    else
        echo "*** $T printed no summary; last lines of $LOG:"
        tail -5 "$LOG" | sed "s/^/    /"
    fi
done

if [ "$layout_before" != "$(git status --porcelain -- cs2_sample_layout)" ]
then
    echo "*** cs2_sample_layout changed during this run; check it before trusting the results ***"
fi
