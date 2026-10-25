#!/bin/bash
#
# Self-test of the "waiting for every slot" marker in slots.sh (FR-136 Phase 1a, 2026-10-19).
#
# Usage:   bash docs/tools/test-slot-waiting.sh
#
# Touches NO real slot and NO real lock file: every run here is a small process that sources slots.sh against a lock
# of its own (TC_LOCK in a temp folder, three slots) and takes and releases slots through the normal functions.  The
# real test JVM probe is replaced by a count the test names, so the result does not depend on what else is running.
# Needs about a minute: every liveness check asks Windows about a pid.
#
# It shows, in three scenes:
#   1. a single-slot holder; a run that needs every slot is refused and leaves its marker; a second single-slot run is
#      refused BECAUSE OF THE MARKER though a slot is free; the holder (never stopped) is released; the waiter gets
#      every slot and takes its marker down; single-slot runs are admitted again.
#   2. the stale marker: old with a dead pid is ignored and deleted; fresh with a dead pid counts (the waiting run has
#      exited between its retries); old with a live pid counts; older than six times the limit is ignored whatever.
#   3. two runs that both need every slot: neither is refused because of the other's marker.
#
# Exits 0 when every check passes, 1 otherwise.

set -u

HERE="$(cd "$(dirname "$0")" && pwd)"

# ------------------------------------------------------------------------------------------------
# A ROLE: one small process of the scene, started by the driver below as `bash test-slot-waiting.sh role <name> ...`.
if [ "${1:-}" = "role" ]
then
    ROLE="$2"
    shift 2

    TOOLS="$HERE"

    . "$HERE/slots.sh"

    count_test_jvms()
    {
        echo "${FAKE_JVMS:-0}"
    }

    case "$ROLE" in
        holder)
            # a run that holds one slot until told to let go: $1 is touched when it has the slot, $2 is waited for
            take_one_slot
            echo "holder holds slot $HELD_SLOTS"
            touch "$1"

            while [ ! -f "$2" ]; do sleep 1; done

            release_all_slots
            echo "holder released"
            ;;
        single)
            take_one_slot
            echo "single-slot run got slot $HELD_SLOTS"
            release_all_slots
            ;;
        every)
            # the order both runners use: the probe, then the take; the exit trap as the runners arm it
            refuse_if_test_jvms all
            take_every_slot
            trap 'release_all_slots; clear_waiting_marker' EXIT
            echo "every-slot run got every slot: $HELD_SLOTS"

            if [ -n "${HOLD_UNTIL:-}" ]
            then
                touch "$1"

                while [ ! -f "$HOLD_UNTIL" ]; do sleep 1; done
            fi
            ;;
    esac

    exit 0
fi

# ------------------------------------------------------------------------------------------------
# THE DRIVER

T=$(mktemp -d)

export TC_LOCK="$T/lock"
export TC_SLOTS=3

mkdir -p "$T/scratch-a" "$T/scratch-b" "$T/scratch-c"

PASS=0
FAIL=0
HOLDERS=""

cleanup()
{
    for p in $HOLDERS; do kill "$p" 2>/dev/null; done

    rm -rf "$T"
}

trap cleanup EXIT

check()
{
    if [ "$2" = "yes" ]
    then
        PASS=$((PASS + 1))
        echo "  PASS  $1"
    else
        FAIL=$((FAIL + 1))
        echo "  FAIL  $1"
    fi
}

contains()
{
    case "$1" in *"$2"*) echo yes;; *) echo no;; esac
}

# Runs a role with its own scratch directory (a: one waiting runner, b: another, c: workers), keeping output and status.
OUT=""
RC=0

run()
{
    scratch="$1"
    shift

    OUT=$(TC_SCRATCH="$T/scratch-$scratch" bash "$HERE/test-slot-waiting.sh" role "$@" 2>&1)
    RC=$?

    echo "$OUT" | sed 's/^/        | /'
}

markers()
{
    ls "$TC_LOCK".waiting.* 2>/dev/null | grep -vc '\.tmp\.' || true
}

now()
{
    date +%s
}

echo "=== 1. a waiter behind a single-slot holder: it leaves its marker, the next single-slot run holds off, the waiter gets in"

echo "--- control: with no marker a single-slot run takes a slot"
run c single
check "single-slot run is admitted with no marker" "$(contains "$OUT" "single-slot run got slot 1")"

echo "--- a fake single-slot holder takes a slot and keeps it"
bash "$HERE/test-slot-waiting.sh" role holder "$T/h1.ready" "$T/h1.release" > "$T/h1.out" 2>&1 &
H1=$!
HOLDERS="$HOLDERS $H1"

for i in $(seq 1 60); do [ -f "$T/h1.ready" ] && break; sleep 1; done

check "holder has a slot" "$([ -f "$T/h1.ready" ] && echo yes || echo no)"

echo "--- a run that needs every slot is refused, and leaves its marker"
run a every
check "the waiter is refused with 'nothing was run'" "$([ "$RC" = 2 ] && contains "$OUT" "nothing was run")"
check "the existing wording is kept" "$(contains "$OUT" "A BATTERY OR ANOTHER RUN IS ALREADY GOING")"
check "one marker stands" "$([ "$(markers)" = 1 ] && echo yes || echo no)"
check "the marker holds a pid and a timestamp" "$(grep -Eq '^[^ ]+ [0-9]{9,}$' "$TC_LOCK".waiting.* && echo yes || echo no)"

echo "--- a second single-slot run: a slot is free (3 slots, 1 held), but the marker refuses it"
run c single
check "refused with 'nothing was run'" "$([ "$RC" = 2 ] && contains "$OUT" "nothing was run")"
check "refused because of the marker" "$(contains "$OUT" "A RUN IS WAITING FOR EVERY TEST SLOT")"
check "it was not the full-house refusal" "$([ "$(contains "$OUT" "TEST SLOTS ARE IN USE")" = no ] && echo yes || echo no)"
check "it took no slot" "$([ ! -f "$TC_LOCK.2" ] && echo yes || echo no)"

echo "--- the waiter asks again while the holder still runs: still refused, still one marker (rewritten, not added)"
run a every
check "refused again" "$([ "$RC" = 2 ] && echo yes || echo no)"
check "still one marker" "$([ "$(markers)" = 1 ] && echo yes || echo no)"

echo "--- the holder was never stopped by any of this"
check "holder process is alive" "$(kill -0 "$H1" 2>/dev/null && echo yes || echo no)"
check "holder's lock file is still there" "$([ -f "$TC_LOCK" ] && echo yes || echo no)"

echo "--- release the holder; the waiter's next try gets every slot and takes its marker down"
touch "$T/h1.release"
wait "$H1" 2>/dev/null
check "holder released its slot" "$(contains "$(cat "$T/h1.out")" "holder released")"

run a every
check "the waiter got every slot" "$(contains "$OUT" "every-slot run got every slot: 1 2 3")"
check "its marker is gone" "$([ "$(markers)" = 0 ] && echo yes || echo no)"
check "its slots are released at exit" "$([ ! -f "$TC_LOCK" ] && [ ! -f "$TC_LOCK.2" ] && [ ! -f "$TC_LOCK.3" ] && echo yes || echo no)"

run c single
check "single-slot runs are admitted again" "$(contains "$OUT" "single-slot run got slot 1")"

echo "--- the probe's refusal leaves a marker too (a test JVM is running), and a finished run clears it"
FAKE_JVMS=2 run a every
check "refused by the probe with 'nothing was run'" "$([ "$RC" = 2 ] && contains "$OUT" "TEST JVMS ARE ALREADY RUNNING")"
check "marker left by the probe's refusal" "$([ "$(markers)" = 1 ] && echo yes || echo no)"
run c single
check "single-slot run holds off" "$(contains "$OUT" "A RUN IS WAITING FOR EVERY TEST SLOT")"
run a every
check "the waiter, with no JVMs now, runs" "$(contains "$OUT" "every-slot run got every slot")"
check "marker gone" "$([ "$(markers)" = 0 ] && echo yes || echo no)"

echo "=== 2. the stale marker"

# A marker written by hand, in the format the script writes: "<pid> <seconds since 1970>".
# 999999 is a pid no process has; $$ here is a live MSYS shell, and its winpid is what a run would write.
LIVE=$(cat /proc/$$/winpid 2>/dev/null | tr -d '\r\n ')
[ -z "$LIVE" ] && LIVE="msys:$$"

put()
{
    rm -f "$TC_LOCK".waiting.*
    printf '%s %s\n' "$1" "$(( $(now) - $2 ))" > "$TC_LOCK.waiting.fake"
}

echo "--- 11 minutes old, its pid dead: stale, ignored, deleted"
put 999999 660
run c single
check "single-slot run is admitted" "$(contains "$OUT" "single-slot run got slot 1")"
check "the stale marker was deleted" "$([ "$(markers)" = 0 ] && echo yes || echo no)"

echo "--- 30 s old, its pid dead (the waiter exited between its retries): fresh, counts"
put 999999 30
run c single
check "single-slot run holds off" "$(contains "$OUT" "A RUN IS WAITING FOR EVERY TEST SLOT")"
check "the marker stays" "$([ "$(markers)" = 1 ] && echo yes || echo no)"

echo "--- 11 minutes old, its pid live: not stale, counts"
put "$LIVE" 660
run c single
check "single-slot run holds off" "$(contains "$OUT" "A RUN IS WAITING FOR EVERY TEST SLOT")"

echo "--- an hour and a half old, its pid live (a recycled pid): ignored whatever the pid says"
put "$LIVE" 5400
run c single
check "single-slot run is admitted" "$(contains "$OUT" "single-slot run got slot 1")"
check "the marker was deleted" "$([ "$(markers)" = 0 ] && echo yes || echo no)"

echo "--- an unreadable marker is stale"
echo "garbage" > "$TC_LOCK.waiting.fake"
run c single
check "single-slot run is admitted" "$(contains "$OUT" "single-slot run got slot 1")"
check "the marker was deleted" "$([ "$(markers)" = 0 ] && echo yes || echo no)"

echo "--- TC_WAITING_TTL shortens it: 60 s old with a 30 s limit and a dead pid is stale"
put 999999 60
TC_WAITING_TTL=30 run c single
check "single-slot run is admitted" "$(contains "$OUT" "single-slot run got slot 1")"

rm -f "$TC_LOCK".waiting.*

echo "=== 3. two runs that both need every slot"

bash "$HERE/test-slot-waiting.sh" role holder "$T/h2.ready" "$T/h2.release" > "$T/h2.out" 2>&1 &
H2=$!
HOLDERS="$HOLDERS $H2"

for i in $(seq 1 60); do [ -f "$T/h2.ready" ] && break; sleep 1; done

run a every
check "waiter A refused behind the holder" "$([ "$RC" = 2 ] && echo yes || echo no)"
run b every
check "waiter B refused behind the holder" "$([ "$RC" = 2 ] && echo yes || echo no)"
check "B was refused for the holder, not for A's marker" "$(contains "$OUT" "A BATTERY OR ANOTHER RUN IS ALREADY GOING")"
check "two markers stand, one each" "$([ "$(markers)" = 2 ] && echo yes || echo no)"

touch "$T/h2.release"
wait "$H2" 2>/dev/null

echo "--- holder gone, both markers standing: A (needs every slot) is not refused by B's marker"
TC_SCRATCH="$T/scratch-a" HOLD_UNTIL="$T/a.release" bash "$HERE/test-slot-waiting.sh" role every "$T/a.ready" > "$T/a.out" 2>&1 &
A=$!
HOLDERS="$HOLDERS $A"

for i in $(seq 1 60); do [ -f "$T/a.ready" ] && break; sleep 1; done

check "A got every slot with B's marker standing" "$(contains "$(cat "$T/a.out")" "every-slot run got every slot: 1 2 3")"
check "A took its own marker down, and only its own" "$([ "$(markers)" = 1 ] && echo yes || echo no)"

echo "--- B tries while A holds every slot: refused, as before, and a single-slot run is refused too"
run b every
check "B refused while A runs" "$([ "$RC" = 2 ] && contains "$OUT" "nothing was run")"
run c single
check "single-slot run refused while A runs" "$([ "$RC" = 2 ] && contains "$OUT" "nothing was run")"

touch "$T/a.release"
wait "$A" 2>/dev/null

echo "--- A has finished: its exit trap took its slots; B is next and gets them"
run b every
check "B got every slot" "$(contains "$OUT" "every-slot run got every slot: 1 2 3")"
check "all markers gone" "$([ "$(markers)" = 0 ] && echo yes || echo no)"

echo ""
echo "$PASS checks passed, $FAIL failed"

[ "$FAIL" -eq 0 ]
