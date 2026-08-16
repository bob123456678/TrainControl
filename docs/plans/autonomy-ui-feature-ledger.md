# Autonomy UI feature ledger: graph window vs track diagram

Tracks whether every capability of the old GraphStream window has a home in the new diagram-based UI,
so that "the graph window can go" becomes a checkable claim rather than an impression.

Sources: `GraphRightClickPointMenu`, `GraphRightClickGeneralMenu`, `GraphEdgeEdit`, `GraphLocAssign`,
`GraphLocExclude`, `GraphViewer`, and the `Load Autonomy Configuration` / `Autonomy Settings` tabs.

**Status key** — DONE: reachable in the new UI. PARTIAL: possible but awkward or incomplete.
TODO: no way to do it yet. N/A: deliberately dropped, with the reason.

Last updated 2026-08-16.

## Points

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Create a point | general right-click → Create point | Automatic: every s88 tile is a point | DONE |
| Delete a point | point right-click → Delete | Delete the s88 tile in the diagram editor | DONE |
| Rename a point | point right-click → Rename | Right-click → Name, or "Name everything" | DONE |
| Mark as station | point right-click checkbox | Right-click → Point properties → station | DONE |
| Terminus station | point right-click checkbox | Point properties → terminus | DONE |
| Reversing point | point right-click checkbox | Point properties → reversing | DONE |
| Active / inactive | point right-click checkbox | Point properties → parking (same flag) | DONE |
| Max train length | point right-click → advanced | Point properties | DONE |
| Speed multiplier | point right-click → speed multiplier | Point properties | DONE |
| Station priority | point right-click → advanced | Point properties | DONE |
| Excluded locomotives | point right-click → GraphLocExclude | Point properties → excluded list | DONE |
| Home locomotives | point right-click | Point properties → home list | DONE |
| Test connection from a point | point right-click → test connection | Test tool, reports both directions | DONE |
| See invalid paths and the reason | point right-click → test connection | **Only reachable/not reachable is reported** | PARTIAL |

## Edges

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Create an edge | point right-click → Connect to | Derived from the diagram | N/A - the diagram IS the edge list |
| Delete an edge | point right-click → Delete edge | Close the track between two points | DONE |
| Edit an edge (commands) | GraphEdgeEdit | Derived from the tiles crossed | N/A - see below |
| Copy an outgoing edge | point right-click → Copy edge | - | N/A - authoring edges is gone |
| Lock edges | GraphEdgeEdit | Derived from shared tiles | N/A - and provably complete |
| Edge length | GraphEdgeEdit | Lengths tool, per tile, summed | DONE |
| Capture accessory commands | GraphEdgeEdit | Derived from the switches crossed | N/A - author ruling, 2026-08-16 |
| One-way running | edge direction in JSON | One-way run tool, or per-tile direction | DONE |

**Why the edge editor is N/A rather than TODO**: an edge's commands, locks and path are all derived
from the tiles it crosses. Hand-editing them was only necessary because the graph had no idea what
track it described. Anything that cannot be expressed by the diagram is a gap in the DIAGRAM, and
should be reported as such rather than patched by hand here.

## Locomotives

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Place a locomotive | point right-click → Add/assign | Auto tab → Place locomotives (existing tab) | DONE |
| Remove from a point | point right-click | Same tab | DONE |
| Clear all locomotives | general right-click | Same tab | DONE |
| See where locomotives are | on the graph | Roster list, and the diagram overlay | DONE |
| Show home locomotives | display option | **Layer exists in spec, not drawn yet** | TODO |

## Running and monitoring

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Start autonomy | general right-click / button | Locomotive Commands tab, unchanged | DONE |
| Graceful stop | general right-click / button | Unchanged | DONE |
| Watch trains move | graph node/edge colouring | Diagram overlay, same colours | DONE |
| See which paths are locked | graph edge colouring | Diagram overlay (locked state) | DONE |
| Train icon on the diagram | - | **Wanted, comes last** | TODO |

## Display options

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Hide inactive points | display options | - | TODO |
| Hide reversing stations | display options | - | TODO |
| Hide reversing edges | display options | - | TODO |
| Show lengths and exclusions | display options | Lengths toggle in the editor | PARTIAL - exclusions not drawn |
| Show home locomotives | display options | - | TODO |

## Configuration as a whole

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Load a configuration | Validate and open graph UI | Auto tab → Check and enable | DONE |
| Edit the JSON by hand | text area | - | N/A - author ruling: the diagram is the source |
| Import a configuration | Load JSON from file | Manage → Import | DONE |
| Export a configuration | Export current graph | Manage → Export | DONE |
| Several named configurations | - | New in the diagram UI | DONE |
| Autosave on exit | checkbox | Always on, hidden | DONE |
| Global settings (pace, speeds) | Autonomy Settings tab | Unchanged | DONE |
| Timetables | Timetable tab | Unchanged | DONE |
| Read the graph as a graph | the window itself | Debug-only export to file | PARTIAL |

## Open gaps, in priority order

1. **Show home locomotives** - a layer the spec calls for and nothing draws.
2. **Test connection does not say WHY** - the old menu listed invalid paths and the reason each was
   rejected. The new tool says reachable or not. The reasons come from `Layout`'s own validation, so
   this is a matter of surfacing them rather than of computing anything new.
3. **Display filters** - hide inactive points, reversing stations, reversing edges. Less pressing on a
   diagram than on a graph, where they existed to fight clutter, but "hide inactive" is a genuine want
   on a layout with many parking berths.
4. **Exclusions are not drawn** - a point with excluded locomotives looks like any other.
5. **Train icon on the diagram** - author has said this comes last.
6. **Reading the derived graph** - a file export in debug mode only. May want to open in the old
   viewer before that window is deleted, or become a table.
