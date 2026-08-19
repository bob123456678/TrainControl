# How long the test suite takes, and a lite battery

Measured on 2026-08-19, one class per JVM, on Adam's machine. Sixty classes.

## The shape of it

Ten classes account for 606 seconds. The other fifty run in under ten seconds each.

| Class | Seconds | Why |
| --- | --- | --- |
| `testAutoDetect` | 207 | Waits on network timeouts. Needs a real Central Station on the network; without one it fails, which is what it does today |
| `testAutonomySimulationSanity` | 142 | Drives simulated trains |
| `testReturnHomeOnRealLayout` | 113 | Plans and runs a full return-home on the sample layout |
| `testTimetableOnDerivedGraph` | 34 | Captures and replays a timetable, driving trains twice |
| `testAutonomyPathValidation` | 26 | Needs a display; walks the graph |
| `testLayoutTiles` | 19 | Needs a display; builds real tiles |
| `testAdvancedRoutes` | 17 | |
| `testLocomotive` | 12 | |
| `testMockCentralStation` | 11 | HTTP, several fetches |

`testAutoDetect` is a third of the entire suite on its own, and it is the one class whose result says
nothing about the code — it is measuring whether a Central Station is answering.

## The lite battery

`runlite.sh` runs everything except those. Fifty classes.

It is written as an **exclusion** list rather than a fast list, and that is the point worth recording:
a fast list has to be updated whenever a class is added, and forgetting means the new class silently
never runs in the battery people use most. With an exclusion list a new class is included by default,
and the only maintenance is adding one that turns out to be slow — which announces itself by making
the lite battery slow.

The harness lives in the session scratchpad rather than the repository, alongside `runeach.sh`.

## A flake that was the test's own fault

`testTimetableOnDerivedGraph` failed one full battery in its SETUP, not its assertions: "could not put
BR 628 2 back at TopMainR1Inter (southbound) before the replay".

The cause is worth recording because it is not obvious. The test restores every locomotive to where it
began, and `moveLocomotive` refuses a point that is not a destination - rightly, since placing a train
by hand is a person saying where it is, and "halfway along the approach" is not somewhere a train is
put. But a configuration's own saved placements are not all stations. So the test had a precondition it
could not satisfy, and whether it hit one depended on which locomotives the configuration happened to
carry.

It now restores the ones standing on stations and takes the rest OFF the graph, because a train left
where the run finished would sit on track a captured route needs - and the replay would then fail for
a reason that has nothing to do with what is being tested.

## Worth knowing

- **`testAutoDetect` currently fails all three of its tests**, and has throughout this session. It
  asks a Central Station on the network to answer, and nothing is answering. Not a code failure.
- **Two classes need a display** and are retried automatically with one when the headless run reports
  skips or configuration failures. A class that never ran is not a class that passed, which is the
  reason that retry exists.
- **The battery writes `LocDB.data`** if it is not stopped from doing so: two classes bring up the real
  interface, whose window-close handler saves state. The runner snapshots the file before and restores
  it after.
