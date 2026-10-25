# Sourced at the top of one.sh and battery.sh: a run refused because the machine is busy ("nothing was run") waits
# and retries by itself, so a caller never writes a retry loop.  A tree that does not compile is not retried.
#
#     TC_WAIT=0          refuse at once, as before
#     TC_WAIT_MAX=<s>    give up after this many seconds (default 21600, six hours)
#
# The output is printed once the run has happened, so run it as one background command and wait for it to finish.

if [ -z "${TC_IN_WAIT:-}" ] && [ "${TC_WAIT:-1}" != 0 ]
then
    TC_IN_WAIT=1
    export TC_IN_WAIT

    waited=0
    told=""

    while :
    do
        out="$("${BASH:-sh}" "$0" "$@" 2>&1)"
        rc=$?

        case "$out" in
            *"DOES NOT COMPILE"*) ;;
            *"nothing was run"*)
                if [ "$waited" -lt "${TC_WAIT_MAX:-21600}" ]
                then
                    if [ -z "$told" ]
                    then
                        echo "(the test slots are busy: waiting, and retrying every 30 s)" >&2
                        told=1
                    fi

                    sleep 30
                    waited=$((waited + 30))
                    continue
                fi ;;
        esac

        [ -n "$told" ] && echo "(waited $waited s for a test slot)"
        printf '%s\n' "$out"
        exit $rc
    done
fi
