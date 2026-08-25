# Stops the test battery's OWN leftover JVMs, and nothing else.
#
# WHY THIS IS NOT "kill every java.exe", which is what it used to be:
#
# NetBeans runs TrainControl as java.exe. So the battery, reaping between test classes, was killing
# the application every time it moved on - roughly twenty seconds in, with no dialog and no stack
# trace, because the process was signalled rather than failing. It looked exactly like a startup
# crash, and an hour went into hunting one that did not exist. Adam found it from the outside:
# "I do think it is being killed."
#
# The harness notes had already said to match "only java.exe whose command line names this session's
# scratch directory". The blunt version got written anyway, which is the whole lesson: a destructive
# command needs its blast radius decided before it is convenient, not after it misfires.
#
# The marker is THIS RUN's id, not "a test JVM".
#
# It was `-Dtraincontrol.anyReceivePort` until 2026-08-25, which is carried by every test JVM on the
# machine rather than by this battery's. So a battery running in the background killed any test run by
# hand alongside it: the run died at exit 127, printed nothing, and read exactly like a test that
# hangs. That is the same mistake as the java.exe version above, one notch narrower - "mine" was still
# being decided by what a process IS rather than by who started it.
#
# battery.sh generates the id from its own shell PID and passes it both to the JVMs it starts and to
# this script, so two batteries can run at once and neither touches the other.
#
# Proven rather than assumed: three JVMs side by side - one with this run's id, one with another run's
# id, one with neither. After this runs, only the first is gone.

param
(
    [Parameter(Mandatory = $true)]
    [string] $RunId
)

$mine = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like "*traincontrol.batteryRun=$RunId*" }

foreach ($p in $mine)
{
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
