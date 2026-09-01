#!/bin/sh
#
# Runs a few named test classes, one JVM each, against the working tree.
#
# Usage, from the project root:
#
#     TC_SCRATCH=/path/to/scratch sh docs/tools/one.sh core.testSomething [more...]
#
# IT LIVES IN THE REPOSITORY NOW, and that is the point of this commit (TV2-A1, 2026-09-01).
#
# It lived in an agent's scratch directory, so `find . -name one.sh` over the project found nothing and
# no review could see it.  On 2026-09-01 its sibling battery.sh had its concurrency guard corrected
# FIVE times in one day - the probe widened, the failure modes separated, the lock's liveness test
# rewritten twice, the lock moved out of per-session scope - and not one of those corrections reached
# this file, which the header below correctly says is the one used all day.  So the runner most likely
# to cause the hazard was the one still carrying every version of the bug.
#
# battery.sh was moved into the repository on 2026-08-25 for exactly this reason, with the note "a
# harness nobody can read is a harness nobody can review".  The same sentence applies here and it took
# a third validation pass to notice that it had not been acted on.
#
# THE LIVE-LAYOUT GUARD IS THE POINT OF THE TOP AND BOTTOM OF THIS FILE.
#
# battery.sh fingerprints cs2_sample_layout around a whole run and shouts if the suite wrote to it,
# because that folder is Adam's real railway and is not recoverable.  This runner had no such check, so
# a class that wrote there did it silently.  That gap was found on 2026-08-25 when three files in that
# folder turned up modified and nothing in the tooling could say whether a test or the application had
# done it.
#
# A guard that only runs on the slow path is a guard that is not running.

set -u

S="${TC_SCRATCH:-}"

if [ -z "$S" ] || [ ! -d "$S" ]
then
    echo "TC_SCRATCH must name a scratch directory." >&2
    exit 2
fi

R="$(pwd)"
LIVE="$R/cs2_sample_layout"

if [ ! -d "$LIVE" ]
then
    echo "Run this from the project root - $LIVE is not there." >&2
    exit 2
fi

# Carriage returns stripped before hashing, for the reason battery.sh gives: git checks these files
# out with CRLF and the application writes LF, so an identical rewrite differs byte for byte and a
# guard that always fires is one nobody reads.
fingerprint()
{
    if [ -d "$LIVE" ]
    then
        find "$LIVE" -type f -print0 2>/dev/null | sort -z | while IFS= read -r -d "" f
        do
            printf '%s %s\n' "$(tr -d '\r' < "$f" | md5sum | cut -d' ' -f1)" "${f#$LIVE/}"
        done
    fi
}

live_before=$(fingerprint)

# ------------------------------------------------------------------------------------------------
# NOT WHILE SOMETHING ELSE IS TESTING.  The same guard battery.sh carries, kept identical on purpose.
#
# Test JVMs share the Java Preferences store that LayoutSandbox redirects, and two runs redirecting it
# at once is how the real railway was damaged on 2026-08-30.
#
# THE PROBE MATCHES OUR COMPILES BY THEIR ARGFILE (TV2-C1).  battery.sh first tried
# `javac.exe ... '*TrainControl*'`, which cannot match either runner's compile in a session whose
# cp.txt holds relative paths - and this session's does.  What is always on the command line is the
# argfile, `one-files.txt` or `battery-files.txt`, and the testng jar on the classpath.  A compile is
# the window that matters: both runners own no JVM at all while javac is running, which is exactly
# where the two runs of 2026-09-01 overlapped.  Measured, that window is a few seconds rather than
# the minute an earlier comment claimed - short, not absent.
PROBE="(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javac.exe'\" | Where-Object { \$_.CommandLine -like '*anyReceivePort*' -or \$_.CommandLine -like '*testng*' -or \$_.CommandLine -like '*TestNG*' -or \$_.CommandLine -like '*one-files*' -or \$_.CommandLine -like '*battery-files*' } | Measure-Object).Count"

RUNNING_JVMS=$(powershell.exe -NoProfile -Command "$PROBE" 2>/dev/null | tr -d '\r\n ')

# The arms test the WHOLE answer, not its first character: "12abc" matched [0-9]* before, the -gt then
# failed with its diagnostic discarded, and the run proceeded without the warning meant for that case.
case "$RUNNING_JVMS" in
    ''|*[!0-9]*)
        echo "*** WARNING: could not count running test JVMs - this check DID NOT RUN ***"
        echo ""
        echo "PowerShell returned: '$RUNNING_JVMS'.  Make sure nothing else is running tests."
        echo ""
        ;;
    *)
        if [ "$RUNNING_JVMS" -gt 0 ]
        then
            echo "*** TEST JVMS ARE ALREADY RUNNING ($RUNNING_JVMS of them) - nothing was run ***"
            echo ""
            echo "A battery, an ant run, or another one.sh is going.  Wait for it, or stop it."
            echo "Nothing needs deleting: this check clears itself when those processes exit."
            exit 2
        fi
        ;;
esac

# AND THE SAME LOCK battery.sh takes, at the same user-wide path.
#
# Per-session was the whole defect: the lock used to live under TC_SCRATCH, so two sessions took two
# different files and neither ever saw the other.  TEMP is one directory for the user, which is the
# scope the hazard has - the Preferences store these runs fight over is per user, not per session.
LOCK="${TC_LOCK:-${TEMP:-${TMP:-/tmp}}/traincontrol-battery.lock}"

LOCK_PID=$(cat /proc/$$/winpid 2>/dev/null | tr -d '\r\n ')

case "$LOCK_PID" in
    ''|*[!0-9]*) LOCK_PID=$$ ;;
esac

if [ -f "$LOCK" ]
then
    HELD=$(cat "$LOCK" 2>/dev/null | tr -d '\r\n ')

    ALIVE=$(powershell.exe -NoProfile -Command \
        "if (Get-Process -Id $HELD -ErrorAction SilentlyContinue) { 'yes' } else { 'no' }" \
        2>/dev/null | tr -d '\r\n ')

    if [ "$ALIVE" != "yes" ] && kill -0 "$HELD" 2>/dev/null
    then
        ALIVE="yes"
    fi

    if [ "$ALIVE" = "yes" ]
    then
        echo "*** A BATTERY OR ANOTHER RUN IS ALREADY GOING (pid $HELD) - nothing was run ***"
        exit 2
    fi
fi

echo "$LOCK_PID" > "$LOCK"

BUILD="$S/build/one-$$"

trap 'rm -f "$LOCK"; rm -rf "$BUILD"; exit 130' INT TERM
trap 'rm -f "$LOCK"; rm -rf "$BUILD"' EXIT

# ------------------------------------------------------------------------------------------------

CP="src;resources/flatlaf-3.7.2.jar;resources/json-20260814.jar;resources_test/jcommander-1.69.jar;resources_test/testng-6.14.3.jar;test"

rm -rf "$BUILD"; mkdir -p "$BUILD"

find src test -name "*.java" > "$S/one-files.txt"

"${TC_JAVAC:-/c/Program Files/Java/jdk1.8.0_361/bin/javac}" -nowarn -encoding UTF-8 \
    -d "$BUILD" -cp "$CP" @"$S/one-files.txt" 2>&1 | grep -i "error" | head -8

JAVA="${TC_JAVA:-/c/Program Files/Java/jdk1.8.0_361/bin/java}"

# CAPTURED TO A FILE, NOT PIPED INTO head.
#
# `... | grep | head -30` closes the pipe as soon as head has its thirty lines, and the SIGPIPE that
# follows reached back far enough to swallow the NEXT class in the loop: two classes in one invocation
# reliably printed a bare "--- second.class" with no summary, while the same class alone reported
# normally.  Read as "nothing failed", that is a class that ran nothing.
#
# Nothing is truncated now, and a missing summary is called out rather than left blank - a class that
# reported nothing is not a class that passed.
for T in "$@"
do
    echo "--- $T"

    "$JAVA" -Dtraincontrol.anyReceivePort=true \
        -cp "$BUILD;$CP" org.testng.TestNG -testclass "$T" -d "$S/oneout" > "$S/one-run.txt" 2>&1

    grep -E "Total tests run|FAILED|java.lang.Assertion|at regression|at core" "$S/one-run.txt"

    if ! grep -q "Total tests run" "$S/one-run.txt"
    then
        echo "*** $T PRINTED NO SUMMARY - it did not run.  Last lines:"
        tail -5 "$S/one-run.txt" | sed "s/^/    /"
    fi
done

live_after=$(fingerprint)

if [ "$live_before" != "$live_after" ]
then
    echo ""
    echo "*** THESE TESTS WROTE TO $LIVE ***"
    echo ""
    echo "That folder is Adam's real railway and holds his accumulated autonomy setup, which is"
    echo "not recoverable.  Find the class that did it before trusting anything above."
    echo ""
    echo "BUT FIRST: was TrainControl open while this ran?  The application writes to that folder"
    echo "too - every train that moves rewrites its placement - and this check hashes the folder"
    echo "before and after the whole run, so it cannot tell the two apart.  Changes to 'loc' and"
    echo "'facing' in configuration-*.json are what a running railway looks like; changes to"
    echo "setup.json, or a file appearing or disappearing, are not.  Look at the diff before"
    echo "hunting for a class."
    echo ""
    diff <(echo "$live_before") <(echo "$live_after") | grep '^[<>]' | sed 's/^/  /'
    exit 1
fi

exit 0
