#!/bin/bash
# Launches the manual-test triage app in the background, from wherever this script is run.
#
#   bash docs/manual-tests/launch-triage.sh
#
# or, from inside docs/manual-tests, once it's executable (chmod +x, done already):
#
#   ./launch-triage.sh
#
# Does not check whether a copy is already running - triage.py has no problem with two windows
# open on the same files, so this always just starts one more.

set -e

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

py -3 "$HERE/triage.py" > /dev/null 2>&1 &
disown

echo "triage.py launched (pid $!)."
