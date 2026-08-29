# Kept for reference

Source that is no longer part of the build, kept because somebody may want to read it.

Renamed to `.txt` so the compiler ignores it: a file in `src/` either compiles or breaks the build,
and these cannot compile now that the window they belonged to is gone.

## The graph window (removed 2026-08-21)

TrainControl's autonomy setup used to be a GraphStream graph in a window of its own, drawn from a
hand-authored JSON file. The track diagram replaced it: the diagram IS the graph now, derived from
the pages the Central Station already holds, and a second view of the same railway was an invitation
to edit the wrong one.

`GraphViewer` is deleted outright. The three GraphStream jars (`resources/gs-algo-2.0.jar`,
`gs-core-2.0.jar`, `gs-ui-swing-2.0.jar`) are still on disk but off the classpath -
`nbproject/project.properties` no longer lists them. These four source files are kept:

| File | What it was |
| --- | --- |
| `GraphRightClickPointMenu.java.txt` | Everything that could be set on a point, and the model for the autonomy editor's own tile menu |
| `GraphRightClickGeneralMenu.java.txt` | The graph's background menu |
| `GraphEdgeEdit.java.txt` / `.form.txt` | Editing an edge's lock commands - the accessories a path must set. **This has no equivalent**: the diagram derives those from the switches along an edge, so there is nothing to hand-author |
| `GraphLocExclude.java.txt` / `.form.txt` | Which locomotives were barred from a point. Replaced by "Excluded locomotives" under Advanced Parameters on the tile menu |

`GraphLocAssign` is NOT here - it is still live, opened from the track diagram's right-click menu and
from the autonomy editor's tile menu, and is the only place a locomotive's arrival and departure
functions can be set.

They will not compile as they stand: they take a `GraphViewer` and call methods on it that no longer
exist (`updatePoint(Point, Graph)`, `highlightLockedEdges`, `addEdge`). What they are useful for is
the behaviour, not the wiring.
