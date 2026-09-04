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

# THE SAME TAKE, TOO, WORD FOR WORD (REL-B2).
#
# battery.sh was hardened on 2026-09-03 and this file was not, on the same lock: the take was still
# test-then-write, the fallback still wrote a bare MSYS pid, and this reader could not parse the
# "msys:NNN" the other file had started writing - so a one.sh could read a LIVE battery's lock as
# clear and overwrite it.  Three legs of one defect, and the third was created by fixing the first
# somewhere else.  This project's most repeated mistake, in the file that documents it.
#
# What the lock covers is the COMPILE.  The JVM probe above sees test JVMs; two runs that overlap in
# javac own no JVM at all, which is exactly where the 2026-09-01 double-run overlapped.
LOCK_PID=$(cat /proc/$$/winpid 2>/dev/null | tr -d '\r\n ')

# THE NAMESPACE, WRITTEN WITH THE NUMBER (SV2-C2).
#
# A bare integer that is an MSYS pid is one `Get-Process` is guaranteed to call dead, so a lock
# written from a shell with no /proc could be cleared while its run was still going.
case "$LOCK_PID" in
    ''|*[!0-9]*) LOCK_PID="msys:$$" ;;
esac

# Whether the holder is alive, dead, or a number this shell cannot resolve.
lock_holder_state()
{
    held="$1"

    case "$held" in
        '') echo "unknown"; return;;
        msys:*) held_msys=${held#msys:}; held_win="";;
        *[!0-9]*) echo "unknown"; return;;
        *) held_msys="$held"; held_win="$held";;
    esac

    state="unknown"

    if [ -n "$held_win" ]
    then
        state=$(powershell.exe -NoProfile -Command \
            "if (Get-Process -Id $held_win -ErrorAction SilentlyContinue) { 'yes' } else { 'no' }" \
            2>/dev/null | tr -d '\r\n ')
    fi

    # Only ever ADDS a yes: two tests that can say "still running" cannot combine into a false stale,
    # which is the only direction that costs anything here.
    if [ "$state" != "yes" ] && [ -n "$held_msys" ] && kill -0 "$held_msys" 2>/dev/null
    then
        state="yes"
    fi

    # A number Windows was never asked about is unknown rather than dead: this may simply be a
    # different MSYS runtime.
    if [ "$state" != "yes" ] && [ -z "$held_win" ]
    then
        state="unknown"
    fi

    echo "$state"
}

# TAKEN, OR NOT, IN ONE STEP (SV2-C3, and REL-C9's correction of it).
#
# `set -o noclobber` with `: >` makes "create it only if it does not exist" one operation the
# filesystem decides.  A lock whose holder is gone, or whose number nobody can resolve, is taken OVER
# with `mv` - which is also one operation, so two shells racing past the same stale lock cannot both
# win it.  The pid is written to a temp file and moved into place, so the lock is never present and
# empty for a reader to find (REL-C9's second half).
take_the_lock()
{
    if ( set -o noclobber; : > "$LOCK" ) 2>/dev/null
    then
        printf "%s\n" "$LOCK_PID" > "$LOCK.mine.$$" && mv -f "$LOCK.mine.$$" "$LOCK"

        return 0
    fi

    return 1
}

if [ -f "$LOCK" ]
then
    HELD=$(cat "$LOCK" 2>/dev/null | tr -d '\r\n ')

    case "$(lock_holder_state "$HELD")" in
        yes)
            echo "*** A BATTERY OR ANOTHER RUN IS ALREADY GOING (pid $HELD) - nothing was run ***"
            exit 2
            ;;
        no)
            echo "(clearing a stale lock from pid $HELD, which is no longer running)"
            echo ""

            mv "$LOCK" "$LOCK.stale.$$" 2>/dev/null && rm -f "$LOCK.stale.$$"
            ;;
        *)
            # WARNS AND PROCEEDS, which is what the unknown arm is for (REL-C10).
            #
            # A lock nobody can resolve is possibly days old, and refusing here would leave deleting
            # the file by hand as the only way past - the learned behaviour the probe's own comment
            # forbids.  Taken over with `mv`, so two shells arriving together cannot both take it.
            echo "*** WARNING: could not tell whether the run holding this lock (pid $HELD) is still"
            echo "    going - that check DID NOT RUN.  Make sure nothing else is running tests."
            echo ""

            mv "$LOCK" "$LOCK.stale.$$" 2>/dev/null && rm -f "$LOCK.stale.$$"
            ;;
    esac
fi

if ! take_the_lock
then
    echo "*** ANOTHER RUN TOOK THE LOCK IN THE SAME INSTANT - nothing was run ***"
    echo ""
    echo "Two started within a few milliseconds of each other.  Wait for the other one and try again."

    exit 2
fi

BUILD="$S/build/one-$$"

# ------------------------------------------------------------------------------------------------
# THIS RUN'S OWN NAME, AND THE REAPER THAT USES IT (V33-C2).
#
# battery.sh has had both since 2026-08-25 and this runner had neither, which is the gap: a class that
# hangs, or one whose window never closes, leaves a JVM behind, and the NEXT run's start-of-run probe
# then refuses to start with a message saying the check clears itself.  It does not.  Somebody has to
# find the process by hand, and the message tells them not to bother looking.
#
# `one-$$` rather than `battery-$$`, and reap.ps1 matches the id WHOLE - so a one.sh run and a battery
# can never reap each other, which was the bug that made the id whole-matched in the first place.
RUN_ID="one-$$"

# FROM THE SCRIPT'S OWN DIRECTORY, which is the correction TS3-A1 made to battery.sh: reading it from
# `pwd` meant that moving the folder silently broke it, for four days, with the error going to
# /dev/null.  Said out loud here for the same reason - a reaper that is not there is exactly the
# condition nobody notices.
REAPER="$(cd "$(dirname "$0")" && pwd)/reap.ps1"

if [ ! -f "$REAPER" ]
then
    echo "*** WARNING: no reaper at $REAPER - leftover test JVMs will NOT be cleaned up ***"
    echo ""
    echo "A JVM left behind by one class poisons every class after it, and trips the next run's"
    echo "start-of-run probe with a message that says the check clears itself.  It will not."
    echo ""

    REAPER=""
fi

reap()
{
    if [ -n "$REAPER" ]
    then
        powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$REAPER" \
            -RunId "$RUN_ID" >/dev/null 2>&1
    fi
}

# ------------------------------------------------------------------------------------------------
# ON THE WAY OUT, WHICHEVER WAY (TSX-C14).
#
# Three things the traps did not do, all of which matter most on the path they are for - a run that
# was stopped rather than waited for.
#
# THE LOCK IS RELEASED ONLY IF IT IS STILL OURS.  `rm -f "$LOCK"` never asked.  Since REL-C10 the
# unknown arm deliberately takes a live-but-unresolvable lock OVER, so two runs holding the "same"
# lock is a state this is designed for - and the first of them to finish was deleting the second's
# lock and leaving the machine unlocked with a battery still going.  REL-C9 named this sequence for
# the `mv` and the trap was not changed with it.
#
# THE REAPER RUNS.  A run stopped part way leaves the class it was on going, and reap.ps1 matches the
# run id whole while that id embeds this shell's pid - so once the shell is gone nothing can ever
# match it again.  That is the permanence argument battery.sh already writes out for its post-loop
# reap, arriving at the trap.
#
# AND THE LAYOUT IS CHECKED.  one.sh's own header states the rule: "a guard that only runs on the
# slow path is a guard that is not running."  The run most likely to have written to
# cs2_sample_layout is the one that was killed because a class hung, and that was the one run neither
# script looked at.  Only when the run did NOT reach its own report, which does this properly.
release_the_lock()
{
    if [ "$(cat "${LOCK:-}" 2>/dev/null | tr -d '\r\n ')" = "${LOCK_PID:-}" ]
    then
        rm -f "${LOCK:-}"
    fi
}

REPORTED=""

# ONCE, NOT TWICE (VD9-B2).
#
# `trap '...; exit 130' INT TERM` runs its body and then exits - and exiting fires the EXIT trap,
# which runs the same body again.  Measured, not assumed: a throwaway script with this exact pair
# prints its handler twice.  So a killed run reaped twice and, worse, printed the "this run was
# stopped and the layout changed" warning twice, which reads as two events.
DONE=""

on_the_way_out()
{
    if [ -n "${DONE:-}" ]; then return; fi

    DONE=1

    # BEFORE the fingerprint, because a leftover JVM is by definition one that is still running and
    # deferred work landing after everybody stopped watching is the whole subject of LayoutSandbox.
    #
    # Written out rather than calling reap(), and every variable defaulted - genuinely every one
    # now (VD9-C7): $LOCK, $REPORTED and $DONE were not, and under `set -u` an unbound one aborts
    # the trap at that line and takes `rm -rf "$BUILD"` with it, which is the exact failure this
    # comment claimed to be preventing.
    #
    # It could not happen, because all three are set before the trap is armed - but that is safety
    # by arming order, and this body is meant to survive being moved.
    if [ -n "${REAPER:-}" ]
    then
        powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$REAPER"             -RunId "${RUN_ID:-}" >/dev/null 2>&1
    fi

    if [ -z "${REPORTED:-}" ] && [ -n "${live_before:-}" ]
    then
        if [ "$live_before" != "$(fingerprint)" ]
        then
            echo ""
            echo "*** THIS RUN WAS STOPPED, AND ${LIVE:-the layout} CHANGED WHILE IT RAN ***"
            echo ""
            echo "That folder is Adam's real railway.  Check it before trusting anything above, and"
            echo "check whether TrainControl was open - a running railway rewrites it as trains move."
            echo ""
        fi
    fi

    release_the_lock

    rm -rf "${BUILD:-}"
}

trap 'on_the_way_out; exit 130' INT TERM
trap 'on_the_way_out' EXIT

# ------------------------------------------------------------------------------------------------

CP="src;resources/flatlaf-3.7.2.jar;resources/json-20260814.jar;resources_test/jcommander-1.69.jar;resources_test/testng-6.14.3.jar;test"

rm -rf "$BUILD"; mkdir -p "$BUILD"

find src test -name "*.java" > "$S/one-files.txt"

# THE COMPILE HAS TO HAVE WORKED (V33-C3, TS3-C5).
#
# This was `javac ... 2>&1 | grep -i error | head -8`, which is wrong twice over.  The pipeline's
# status is `head`'s, so the compile could fail and the tests ran anyway - against the LAST build that
# worked, which is the most misleading thing a runner can do: it reports on code that is not in the
# tree.  And it is the same `| grep | head` shape this file's own comment forty lines down warns
# about, where SIGPIPE reached back and swallowed a whole class.
#
# battery.sh has done it this way since it was written; this is that code, not a new idea.
if ! "${TC_JAVAC:-/c/Program Files/Java/jdk1.8.0_361/bin/javac}" -nowarn -encoding UTF-8 \
    -d "$BUILD" -cp "$CP" @"$S/one-files.txt" 2>"$S/one-javac.log"
then
    echo "*** THE WORKING TREE DOES NOT COMPILE - nothing was run ***"
    echo ""
    grep -i "error" "$S/one-javac.log" | head -20

    exit 2
fi

JAVA="${TC_JAVA:-/c/Program Files/Java/jdk1.8.0_361/bin/java}"

# THE SAME FLAGS battery.sh RUNS WITH, INCLUDING THE HEAP BOUND (TSX-C15).
#
# This file already carries battery.sh's DIAGNOSTIC for an unbounded heap - the "DID NOT RUN - no
# heap (machine busy, rerun)" branch below - and had never been given the bound that stops it
# happening.  A default-heap JVM reserves a fraction of physical RAM up front, so with NetBeans open
# and Adam running his own tests three classes of battery34 died before TestNG loaded, reported in
# the same words as a class that crashed.  All three pass in 512m and the heaviest class peaks
# nowhere near it.
#
# Overridable by the same two variables, because the number is a guess about this machine rather
# than a property of the tests, and a runner you cannot tune is one people stop using.
JAVA_FLAGS="${TC_JAVA_FLAGS:--Dtraincontrol.anyReceivePort=true}"

JAVA_FLAGS="$JAVA_FLAGS ${TC_JAVA_HEAP:--Xmx512m}"

# CAPTURED TO A FILE, NOT PIPED INTO head.
#
# `... | grep | head -30` closes the pipe as soon as head has its thirty lines, and the SIGPIPE that
# follows reached back far enough to swallow the NEXT class in the loop: two classes in one invocation
# reliably printed a bare "--- second.class" with no summary, while the same class alone reported
# normally.  Read as "nothing failed", that is a class that ran nothing.
#
# Nothing is truncated now, and a missing summary is called out rather than left blank - a class that
# reported nothing is not a class that passed.

# WHAT WENT WRONG, counted (V33-C4).
#
# Whatever the tests did, this used to exit 0 - so a caller that chained on it, or a person reading
# only the exit status, could not tell a green run from one where every class failed.  battery.sh has
# reported this since it was written.  (The live-layout, lock and probe branches have always had exit
# statuses of their own; it is the TESTS' verdict that was thrown away.)
BAD=0

# AND A SKIP IS NOT A FAILURE, but it is not a pass either (V34-C3).
#
# Counted apart, as battery.sh does: it exits 1 for failures and 2 for skips, and says why - "a class
# that tested nothing counts".  This file's own comment claimed to count them apart while adding them
# to one number and exiting 1 for everything, so a class that skips because it needs a display made the
# runner report a failure.
SKIPPED=0

for T in "$@"
do
    echo "--- $T"

    # Only THIS RUN's leftovers, before the class and again after the last one (V33-C1, V33-C2).
    reap

    "$JAVA" $JAVA_FLAGS -Dtraincontrol.batteryRun="$RUN_ID" \
        -cp "$BUILD;$CP" org.testng.TestNG -testclass "$T" -d "$S/oneout" > "$S/one-run.txt" 2>&1

    grep -E "Total tests run|Configuration Failures|FAILED|java.lang.Assertion|at regression|at core" \
        "$S/one-run.txt"

    if ! grep -q "Total tests run" "$S/one-run.txt"
    then
        # THE MACHINE, OR THE CLASS (V34-C3, from battery.sh).
        #
        # A JVM that could not get its heap is the machine being busy and the answer is to run it
        # again; anything else is the class, and the answer is to go and read it.  Reading them
        # identically cost battery.sh a round of hunting for a fault in three classes that were fine.
        #
        # Both wordings, because a 64-bit JDK 8 says "Unable to allocate NNN bitmaps ... for the
        # requested NNNKB heap" as well as "Could not reserve enough space for %I64uKB object heap".
        if grep -qE "Could not reserve enough space|Unable to allocate.*heap" "$S/one-run.txt"
        then
            echo "*** $T DID NOT RUN - no heap (machine busy, rerun)"
        else
            echo "*** $T PRINTED NO SUMMARY - it did not run.  Last lines:"
            tail -5 "$S/one-run.txt" | sed "s/^/    /"
        fi

        BAD=$((BAD+1))

        continue
    fi

    summary=$(grep 'Total tests run' "$S/one-run.txt" | tail -1)

    # A TEARDOWN THAT THREW (V33-A1).
    #
    # TestNG's summary is TWO lines and this runner read one of them:
    #
    #     Total tests run: 1, Failures: 0, Skips: 0
    #     Configuration Failures: 1, Skips: 0
    #
    # battery.sh was repaired for exactly this on 2026-08-25 and said why: "the teardowns in this
    # suite are load-bearing: testBothProtectingSignalsAreThrown puts two of Adam's real signals back
    # to GREEN in its teardown and says why, and testARouteDoesNotThrowSwitchesUnderATrain clears the
    # auto layout in its own.  A teardown that threw would leave the railway changed and be reported
    # as a clean run."  That sentence was still true here.
    config=$(grep 'Configuration Failures' "$S/one-run.txt" | tail -1)

    if [ -n "$config" ] && ! echo "$config" | grep -q "Configuration Failures: 0"
    then
        echo "*** $T HAD A CONFIGURATION FAILURE - a @Before or @After threw ***"
        echo "    $config"
        echo "    The teardowns in this suite put Adam's signals back and clear the auto layout."

        BAD=$((BAD+1))

        continue
    fi

    # GREEN IS NOT "no failures" (the other half of the same omission, V33-B1).
    #
    # A class whose @BeforeClass throws reports every test SKIPPED and none failed, so "Failures: 0"
    # is true of a class that tested nothing at all.
    #
    # FAILURES ASKED FIRST, and skips counted apart (V34-C3).  Both corrections are battery.sh's: a
    # class that fails AND skips was headlined "SKIPPED TESTS", with the failures visible only in the
    # raw grep above, and a class that skips legitimately - several need a display and say so - was
    # added to the same number as a failure and exited 1.
    if ! echo "$summary" | grep -q "Failures: 0"
    then
        BAD=$((BAD+1))
    elif echo "$summary" | grep -qE "Total tests run: 0"
    then
        echo "*** $T RAN NOTHING - $summary"

        SKIPPED=$((SKIPPED+1))
    elif ! echo "$summary" | grep -q "Skips: 0"
    then
        echo "*** $T SKIPPED TESTS - $summary"
        echo "    A skipped class is not a green class."

        SKIPPED=$((SKIPPED+1))
    fi
done

# AND AFTER THE LAST CLASS (V33-C1, REL-C3).
#
# The reap in the loop runs at the TOP of it, so every class is cleaned up before the next one starts
# and the last one is not cleaned up at all.  A run that ends on a class which left a JVM behind leaves
# it behind for good: `reap.ps1` matches the run id whole, and the id embeds this shell's pid, so no
# later run reaps it either - the next run's start-of-run probe then refuses, with a message saying the
# check clears itself.
#
# `battery.sh` got this on 2026-09-02 and the comment in the loop has claimed it ever since - "and again
# after the last one" - which is worse than its absence, because the claim is what a reader checks
# against.  Fourth drift of this sibling pair, second one repaired in a day.
reap

live_after=$(fingerprint)

# THE TRAP'S COPY OF THIS CHECK STANDS DOWN NOW (TSX-C14).
#
# What follows reports the comparison properly, with the diff and the question about whether
# TrainControl was open.  The trap's version exists for the run that never gets here.
REPORTED=1

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

if [ "$BAD" -gt 0 ]
then
    echo ""
    echo "*** $BAD of the classes above did not come back clean ***"

    exit 1
fi

# TWO, LIKE battery.sh, and for its reason: a class that tested nothing counts.  It is not a failure,
# but it is not a pass either, and a caller that chains on this can tell the two apart.
if [ "$SKIPPED" -gt 0 ]
then
    echo ""
    echo "*** $SKIPPED of the classes above tested nothing - no failures, but not a pass ***"

    exit 2
fi

exit 0
