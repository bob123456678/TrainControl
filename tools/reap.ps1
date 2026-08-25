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
# -Dtraincontrol.anyReceivePort=true is the marker. Every JVM the battery starts carries it and
# nothing else on the machine does - the application must never set it, because binding anywhere but
# the Marklin port would leave it transmitting perfectly and hearing nothing.
#
# Proven rather than assumed: two JVMs started side by side, one with the flag and one without; after
# this runs, the one without is still alive and the one with is gone.

$mine = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*traincontrol.anyReceivePort*' }

foreach ($p in $mine)
{
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
