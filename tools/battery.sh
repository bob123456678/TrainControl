#!/bin/bash
#
# Runs every test class, one JVM each, and reports which ones are not green.
#
# Lived in a session scratch directory until 2026-08-25, which is why the bug below could be made
# twice: a harness nobody can read is a harness nobody can review. It is here now.
#
# Usage, from the project root:
#
#     TC_SCRATCH=/path/to/scratch bash tools/battery.sh
#
# TC_SCRATCH holds cp.txt (the classpath, one line) and receives the TestNG output. See
# docs/manual-tests/README.md for how it is built.

set -u

S="${TC_SCRATCH:-}"

if [ -z "$S" ] || [ ! -f "$S/cp.txt" ]
then
    echo "TC_SCRATCH must name a directory containing cp.txt (the classpath)." >&2
    exit 2
fi

# NOT a stale compile directory first.  A leftover tree once shadowed the real one, so the battery
# silently tested old code and a method added minutes earlier came back as NoSuchMethodError.
CP="$(cat "$S/cp.txt")"

JAVA="${TC_JAVA:-/c/Program Files/Java/jdk1.8.0_361/bin/java}"

# Any free receive port instead of the Marklin one.
#
# Every class that builds a MarklinControlStation used to bind UDP 15730, so the battery could not run
# while TrainControl was open, an orphaned JVM poisoned every class after it, and two classes could
# never overlap. None of them receives anything from a Central Station - there is none - so the port
# was pure contention.
#
# The failure it removes is a nasty one to read: a bind failure comes out of @BeforeClass as
# "Total tests run: 16, Failures: 0, Skips: 16" - zero failures, having tested nothing.
JAVA_FLAGS="${TC_JAVA_FLAGS:--Dtraincontrol.anyReceivePort=true}"

pass=0
fail=0
failed=""

for f in $(find test -name "*.java" | sort)
do
    cls=$(echo "$f" | sed 's|^test/||; s|\.java$||; s|/|.|g')

    case "$cls" in *.*) ;; *) continue;; esac

    # Skipped for the reason build.xml gives: it "probes the network for a real Central Station at a
    # hardcoded address" and blocks until that answers. It hung this runner for fourteen minutes.
    case "$cls" in *testAutoDetect) echo "SKIP $cls (needs a Central Station)"; continue;; esac

    # Only OUR OWN leftover JVMs - reap.ps1 says why this is not "every java.exe".
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(pwd)/tools/reap.ps1" >/dev/null 2>&1

    out=$("$JAVA" $JAVA_FLAGS -cp "$CP" org.testng.TestNG -testclass "$cls" -d "$S/tng-run/$cls" 2>&1 | tail -4)

    if echo "$out" | grep -q "Failures: 0"
    then
        pass=$((pass+1))
    elif echo "$out" | grep -q "Total tests run"
    then
        fail=$((fail+1)); failed="$failed\n  $cls: $(echo "$out" | grep 'Total tests run')"
    else
        # A class that produced no summary did not run.  Reported as a failure on purpose: a runner
        # that reads only "Failures:" calls a class that never started clean.
        fail=$((fail+1)); failed="$failed\n  $cls: DID NOT RUN"
    fi
done

echo "classes green: $pass   classes with failures: $fail"
echo -e "$failed"
