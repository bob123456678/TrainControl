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
#
# -Serve reads run ids from its input, one a line, reaps each the same way and answers "reaped <n>", until the input
# ends: a battery lane reaps before every class, and one PowerShell a lane pays PowerShell's start-up once (FR-167).

param
(
    [Parameter(Mandatory = $true, ParameterSetName = 'Once')]
    [string] $RunId,

    [Parameter(Mandatory = $true, ParameterSetName = 'Serve')]
    [switch] $Serve
)

# The id is matched WHOLE, not as a prefix (found 2026-08-25, by review).
#
# This was `...=$RunId*`, and RUN_ID is "battery-$$" - a process id.  The trailing wildcard made
# battery-777 match battery-7777, so one battery killed another battery's JVMs.  Three- and
# four-digit pids sharing a prefix are ordinary on Windows.
#
# The file used to claim "two batteries can run at once and neither touches the other - proven rather
# than assumed"; the proof used three ids, none of which was a prefix of another.  Review ran it
# again with battery-777, battery-7777 and battery-888 and watched battery-7777 die.
#
# The flag is the last thing on the command line in this harness, but not necessarily: the match
# anchors on what follows the id instead - a space or the end of the string.
#
# Only an id of letters, digits, '.', '_' and '-' is matched: -like reads * ? [ ] as wildcards, and an empty id would
# match every test JVM.
function Stop-Run([string] $Id)
{
    if ($Id -notmatch '^[A-Za-z0-9._-]+$') { return 0 }

    $mine = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object {
            $_.CommandLine -like "*traincontrol.batteryRun=$Id" -or
            $_.CommandLine -like "*traincontrol.batteryRun=$Id *"
        })

    foreach ($p in $mine)
    {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }

    return $mine.Count
}

if ($Serve)
{
    # Trimmed: an id that kept a carriage return would match nothing.
    while ($null -ne ($line = [Console]::In.ReadLine()))
    {
        $n = Stop-Run $line.Trim()

        [Console]::Out.WriteLine("reaped $n")
        [Console]::Out.Flush()
    }
}
else
{
    Stop-Run $RunId | Out-Null
}
