# The test slots: how many runs may have test JVMs going at once, sourced by one.sh and battery.sh (FR-136 Phase 1a).
#
# One lock file used to mean one run at a time on the whole machine.  Since 2026-09-23 every run has its own data
# folder and its own preference node, and since Phase 1a its own temp folder, so two runs no longer share anything
# but the screen.  The lock is now a count: SLOTS lock files, each held by one run.  A run of named classes takes
# one; a battery's lanes take one each and leave at least one free, so a worker's one.sh still gets in during a
# battery.  What needs the machine to itself takes every slot: a class in serial-lane.txt (keyboard focus, the
# clipboard) and a battery run without lanes.
#
# Slot 1 is the old lock file itself, at the old path, so a runner from before this change still finds a holder
# there - and it still refuses while any test JVM runs, so it never overlaps a run of this one.
#
# One copy of this code for both runners: the take, the liveness test and the probe were corrected five times in
# one.sh and battery.sh separately, and drifted every time (see one.sh's header).  Sourced, so it is read whole
# when the runner starts and an edit during a run cannot shift it.
#
# A RUN THAT NEEDS EVERY SLOT MUST NOT STARVE (2026-10-19).  It can start only at an instant when no other run holds
# one, and with workers starting single-slot one.sh runs all day that instant did not come: a serial-lane class waited
# over forty minutes and a laned battery over an hour, each finding a slot taken at every 30 s retry.  So a refused
# every-slot run leaves a short-lived "waiting for every slot" marker beside the slots (leave_waiting_marker), and while
# a fresh one stands a single-slot run refuses, saying "nothing was run", even with a slot free (take_one_slot).  Runs
# already going are never stopped, so the slots drain as they finish; the waiting run takes them all on a retry and
# removes its marker (take_every_slot, and the runners' exit traps).  A run that itself needs every slot ignores the
# markers: it competes as it always did.
#
# Every refusal prints a line with "nothing was run" in it: workers' retry loops look for exactly that, and a
# refusal worded any other way was once read as an empty result.

SLOT_BASE="${TC_LOCK:-${TEMP:-${TMP:-/tmp}}/traincontrol-battery.lock}"

# How many slots there are: one per four logical processors, from two to six.  From the processor count only, so
# every runner on the machine counts the same number whenever it asks; TC_SLOTS overrides it.
slot_count()
{
    n="${TC_SLOTS:-}"

    case "$n" in
        ''|*[!0-9]*)
            n=$(( $(nproc 2>/dev/null || echo 4) / 4 ))
            ;;
    esac

    [ "$n" -lt 2 ] && n=2
    [ "$n" -gt 6 ] && [ -z "${TC_SLOTS:-}" ] && n=6

    echo "$n"
}

SLOTS=$(slot_count)

slot_file()
{
    if [ "$1" -eq 1 ]; then echo "$SLOT_BASE"; else echo "$SLOT_BASE.$1"; fi
}

# THE LOCK MUST HOLD A PID WINDOWS HAS HEARD OF (FV2-A1), and a bare MSYS pid is written "msys:NNN" (SV2-C2).
SLOT_PID=$(cat /proc/$$/winpid 2>/dev/null | tr -d '\r\n ')

case "$SLOT_PID" in
    ''|*[!0-9]*) SLOT_PID="msys:$$" ;;
esac

# Whether a holder is alive, dead, or a number this shell cannot resolve (moved here from one.sh).
#
# Each number is asked of the tool that can answer it: Windows for a winpid, `kill -0` for an MSYS pid, and only
# ever ADDING a yes - two tests that can say "still running" cannot combine into a false stale, which is the only
# direction that costs anything here.  A number Windows was never asked about is unknown rather than dead.
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

    if [ "$state" != "yes" ] && [ -n "$held_msys" ] && kill -0 "$held_msys" 2>/dev/null
    then
        state="yes"
    fi

    if [ "$state" != "yes" ] && [ -z "$held_win" ]
    then
        state="unknown"
    fi

    echo "$state"
}

# A lock file's holder, read twice when empty: a take creates the file empty and moves the pid in a moment later,
# and an empty file read in that moment is a live run, not an unresolvable one.
read_holder()
{
    held=$(cat "$1" 2>/dev/null | tr -d '\r\n ')

    if [ -z "$held" ] && [ -f "$1" ]
    then
        sleep 1

        held=$(cat "$1" 2>/dev/null | tr -d '\r\n ')
    fi

    echo "$held"
}

# Frees a lock file whose holder is gone, or that nobody can resolve (REL-C10: warned about and taken over, since
# refusing would leave deleting it by hand as the only way past).  Returns 1, with LOCK_HELD_BY set, when a live run
# holds it.
#
# Taken over with `mv`, one operation (REL-C9) - and the moved file is read again, because another shell may have
# taken the slot over between this one's read and its move: what was moved aside is then that shell's live lock,
# and it goes back.
clear_if_stale()
{
    f="$1"

    [ -f "$f" ] || return 0

    held=$(read_holder "$f")

    case "$(lock_holder_state "$held")" in
        yes)
            LOCK_HELD_BY="$held"

            return 1
            ;;
        no)
            echo "(clearing a stale lock $f from pid $held, which is no longer running)"
            ;;
        *)
            echo "*** WARNING: could not tell whether the run holding $f (pid $held) is still"
            echo "    going - that check DID NOT RUN.  Make sure nothing else is running tests."
            ;;
    esac

    if mv "$f" "$f.stale.$$" 2>/dev/null
    then
        if [ "$(cat "$f.stale.$$" 2>/dev/null | tr -d '\r\n ')" != "$held" ]
        then
            mv -n "$f.stale.$$" "$f" 2>/dev/null

            LOCK_HELD_BY="another run"

            return 1
        fi

        rm -f "$f.stale.$$"
    fi

    return 0
}

# TAKEN IN ONE STEP (SV2-C3): `set -o noclobber` with `: >` makes "create it only if it does not exist" one
# operation the filesystem decides.  The pid goes through a temp file and a move.
take_lock_file()
{
    if ( set -o noclobber; : > "$1" ) 2>/dev/null
    then
        printf "%s\n" "$SLOT_PID" > "$1.mine.$$" && mv -f "$1.mine.$$" "$1"

        return 0
    fi

    return 1
}

# RELEASED ONLY IF IT IS STILL OURS (TSX-C14): since REL-C10 a lock can be taken over, and the first of two runs
# to finish was deleting the second's.
release_lock_file()
{
    if [ "$(cat "${1:-}" 2>/dev/null | tr -d '\r\n ')" = "${SLOT_PID:-}" ]
    then
        rm -f "${1:-}"
    fi
}

# The slots this run holds, as a space-separated list of numbers.
HELD_SLOTS=""

release_slot()
{
    release_lock_file "$(slot_file "$1")"

    HELD_SLOTS=$(echo " $HELD_SLOTS " | sed "s/ $1 / /; s/^ *//; s/ *$//")
}

release_all_slots()
{
    for k in ${HELD_SLOTS:-}
    do
        release_lock_file "$(slot_file "$k")"
    done

    HELD_SLOTS=""
}

# THE "WAITING FOR EVERY SLOT" MARKER.  One file per waiting runner and scratch directory, beside the slots:
# "$SLOT_BASE.waiting.<key>", holding the waiter's pid and the time (seconds since 1970) it last asked.  The key is
# the scratch directory and the runner's name, so the retries of one caller write one file over and over - the run
# that refused has exited by the time its caller tries again - and a battery and a one.sh of the same session do
# not share one.  Written whole through a temp file and a move, so a reader never sees half of it.
waiting_marker_file()
{
    echo "$SLOT_BASE.waiting.$(printf '%s %s' "${TC_SCRATCH:-$$}" "$(basename "$0")" | md5sum | cut -c1-16)"
}

# How long a marker counts, in seconds (TC_WAITING_TTL).  A waiting run asks again every 30 s, so a marker this old
# belongs to a caller that gave up or was stopped.
WAITING_TTL="${TC_WAITING_TTL:-600}"

# Leaves the marker.  Called by an every-slot run just before it exits 2 on a refusal.
leave_waiting_marker()
{
    m=$(waiting_marker_file)

    if printf "%s %s\n" "$SLOT_PID" "$(date +%s)" > "$m.tmp.$$" 2>/dev/null
    then
        mv -f "$m.tmp.$$" "$m" 2>/dev/null || rm -f "$m.tmp.$$"
    fi

    echo ""
    echo "This run is now marked as waiting for every slot: runs that need only one hold off (and say so) until it"
    echo "has run, or the mark is $WAITING_TTL s old.  Runs already going are not stopped."
}

# Removes this runner's own marker: when the waiting run gets its slots, and again from the exit traps.
clear_waiting_marker()
{
    rm -f "$(waiting_marker_file)"
}

# Whether any marker is fresh; returns 0 with WAITING_PID and WAITING_AGE set to the freshest, or 1.
#
# STALE, and so ignored: older than WAITING_TTL with no live pid behind it - or older than six times that whatever the
# pid says, because a recycled pid must never hold every single-slot run off for good (a guard needs a way past).  An
# unreadable marker is stale.  A stale marker is deleted on the way, which touches no slot or lock file.
waiting_marker_fresh()
{
    WAITING_PID=""
    WAITING_AGE=""

    now=$(date +%s)

    for m in "$SLOT_BASE".waiting.*
    do
        [ -f "$m" ] || continue

        case "$m" in *.tmp.*) continue;; esac

        w_pid=""
        w_time=""

        read -r w_pid w_time < "$m" 2>/dev/null

        case "${w_time:-}" in
            ''|*[!0-9]*) rm -f "$m"; continue;;
        esac

        age=$((now - w_time))

        [ "$age" -lt 0 ] && age=0

        if [ "$age" -gt "$WAITING_TTL" ]
        then
            if [ "$age" -gt $((WAITING_TTL * 6)) ] || [ "$(lock_holder_state "$w_pid")" != "yes" ]
            then
                rm -f "$m"

                continue
            fi
        fi

        if [ -z "$WAITING_AGE" ] || [ "$age" -lt "$WAITING_AGE" ]
        then
            WAITING_PID="$w_pid"
            WAITING_AGE="$age"
        fi
    done

    [ -n "$WAITING_AGE" ]
}

# The test JVMs running now.  "all" counts every one, as the probe always did: the runner, our compiles by their
# argfile (TV2-C1), and the receive-port flag.  "unisolated" counts only test JVMs without a preference node of
# their own - an IDE or ant run, or a runner from before 2026-09-23 - which share the real node that LayoutSandbox
# redirects; a run with its own node and folder can run beside another.  Prints the count, or something that is
# not a number when the probe itself failed.
count_test_jvms()
{
    if [ "$1" = "all" ]
    then
        probe="(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javac.exe'\" | Where-Object { \$_.CommandLine -like '*anyReceivePort*' -or \$_.CommandLine -like '*testng*' -or \$_.CommandLine -like '*TestNG*' -or \$_.CommandLine -like '*one-files*' -or \$_.CommandLine -like '*battery-files*' } | Measure-Object).Count"
    else
        probe="(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { (\$_.CommandLine -like '*anyReceivePort*' -or \$_.CommandLine -like '*testng*' -or \$_.CommandLine -like '*TestNG*') -and \$_.CommandLine -notlike '*traincontrol.preferences=*' } | Measure-Object).Count"
    fi

    powershell.exe -NoProfile -Command "$probe" 2>/dev/null | tr -d '\r\n '
}

# Refuses, and exits 2, when test JVMs that this run may not overlap are going.  $1 is "all" or "unisolated".
#
# The arms test the WHOLE answer (FV2-C5), and a probe that failed says so rather than reading as "none"; the run
# still goes ahead then, because the slots are still standing.
refuse_if_test_jvms()
{
    running=$(count_test_jvms "$1")

    case "$running" in
        ''|*[!0-9]*)
            echo "*** WARNING: could not count running test JVMs - this check DID NOT RUN ***"
            echo ""
            echo "PowerShell returned: '$running'.  Make sure nothing else is running tests."
            echo ""
            ;;
        *)
            if [ "$running" -gt 0 ]
            then
                if [ "$1" = "all" ]
                then
                    echo "*** TEST JVMS ARE ALREADY RUNNING ($running of them) - nothing was run ***"
                    echo ""
                    echo "This run needs the machine to itself, and a battery, an ant run, or another one.sh is"
                    echo "going.  Wait for it, or stop it.  Nothing needs deleting: this check clears itself"
                    echo "when those processes exit."

                    leave_waiting_marker
                else
                    echo "*** TEST JVMS WITHOUT A STATE OF THEIR OWN ARE RUNNING ($running of them) - nothing was run ***"
                    echo ""
                    echo "An IDE or ant run, or a runner from before 2026-09-23, uses the real preference node that"
                    echo "LayoutSandbox redirects; two runs redirecting it at once is how the real railway was"
                    echo "damaged on 2026-08-30.  Wait for it, or stop it."
                fi

                exit 2
            fi
            ;;
    esac
}

# Takes one free slot, the lowest first (so a runner from before the slots sees this run in slot 1 whenever it can).
# Refuses, and exits 2, when every slot is held - or when a run that needs every slot is waiting for them (see the
# header), whether or not one is free: the free ones are what it is waiting to see all at once.
take_one_slot()
{
    if waiting_marker_fresh
    then
        echo "*** A RUN IS WAITING FOR EVERY TEST SLOT (asked $WAITING_AGE s ago, pid $WAITING_PID) - nothing was run ***"
        echo ""
        echo "A battery, or a class that needs the machine to itself, has been finding a slot taken at every try, so"
        echo "new single-slot runs hold off until it has run.  Try again shortly; the runs already going are not stopped."

        exit 2
    fi

    holders=""

    k=1

    while [ "$k" -le "$SLOTS" ]
    do
        f=$(slot_file "$k")

        LOCK_HELD_BY=""

        if clear_if_stale "$f" && take_lock_file "$f"
        then
            HELD_SLOTS="$k"

            return 0
        fi

        holders="$holders ${LOCK_HELD_BY:-another run}"

        k=$((k + 1))
    done

    echo "*** ALL $SLOTS TEST SLOTS ARE IN USE (pids$holders) - nothing was run ***"
    echo ""
    echo "A battery's lanes, or other runs, hold them.  Wait for one to finish and try again."

    exit 2
}

# Takes every slot, for a run that needs the machine to itself.  Refuses, and exits 2, when any is held - having
# given back the ones it took, so two runs racing for all of them cannot each end up holding half - and leaves the
# waiting marker.  It looks at no one else's marker: two runs that both need every slot compete as they always did.
take_every_slot()
{
    k=1

    while [ "$k" -le "$SLOTS" ]
    do
        if ! clear_if_stale "$(slot_file "$k")"
        then
            echo "*** A BATTERY OR ANOTHER RUN IS ALREADY GOING (pid $LOCK_HELD_BY) - nothing was run ***"
            echo ""
            echo "This run needs every test slot to itself.  Wait for that one and try again."

            leave_waiting_marker

            exit 2
        fi

        k=$((k + 1))
    done

    k=1

    while [ "$k" -le "$SLOTS" ]
    do
        if ! take_lock_file "$(slot_file "$k")"
        then
            release_all_slots

            echo "*** ANOTHER RUN TOOK THE LOCK IN THE SAME INSTANT - nothing was run ***"
            echo ""
            echo "Two started within a few milliseconds of each other.  Wait for the other one and try again."

            leave_waiting_marker

            exit 2
        fi

        HELD_SLOTS="$HELD_SLOTS $k"

        k=$((k + 1))
    done

    HELD_SLOTS=$(echo $HELD_SLOTS)

    # It has them all: it is no longer waiting.
    clear_waiting_marker
}

# The classes that need the machine to themselves, from serial-lane.txt in $TOOLS (the runner's own folder): the
# class names, without comments or blank lines.
serial_lane_classes()
{
    sed 's/#.*//' "$TOOLS/serial-lane.txt" 2>/dev/null | tr -d '\r' | awk 'NF { print $1 }'
}
