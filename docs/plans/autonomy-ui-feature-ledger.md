# Autonomy UI feature ledger: graph window vs track diagram

Tracks whether every capability of the old GraphStream window has a home in the new diagram-based UI,
so that "the graph window can go" becomes a checkable claim rather than an impression.

Sources: `GraphRightClickPointMenu`, `GraphRightClickGeneralMenu`, `GraphEdgeEdit`, `GraphLocAssign`,
`GraphLocExclude`, `GraphViewer`, and the `Load Autonomy Configuration` / `Autonomy Settings` tabs.

**Status key** — DONE: reachable in the new UI. PARTIAL: possible but awkward or incomplete.
TODO: no way to do it yet. N/A: deliberately dropped, with the reason.

Last updated 2026-08-24. Rows marked **(corrected)** were wrong before the 2026-08-16 three-way
review: this ledger claimed DONE for things that were broken or absent, which is worse than
having no ledger. What follows is what the review verified, not what was intended. The old
`GraphViewer` window itself was deleted on 2026-08-21; this ledger is kept open because it is
still the only inventory of what that window could do that the new diagram-based UI does not yet
match.

## Points

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Create a point | general right-click → Create point | Automatic: every s88 tile is a point | DONE |
| Delete a point | point right-click → Delete | - | N/A **(corrected)** - deleting the physical sensor is a different act.  The equivalent is un-station plus parking |
| Rename a point | point right-click → Rename | Right-click → Name, or "Name everything" | DONE |
| Mark as station | point right-click checkbox | Right-click -> Station -> a three-way radio: trains can stop / can only pass through / neither.  Replaces the station checkbox and Active together, which between them said the same three things in four states | DONE |
| Terminus station | point right-click checkbox | Retired as a user-facing word.  Station + "Trains can turn round here" COMPILES to terminus (author ruling 2026-08-16) | DONE |
| Warn on stations missing a diagram label | - | Configuration errors and warnings -> "not shown anywhere on the track diagram" | DONE (new) |
| Put a station name on the diagram | layout editor only (`editTextWithDropdown`), text squares only | Automatic on naming a station: written on the connected plain track BESIDE it - below for a north-south platform, left for an east-west one.  Manual placement stays on the right-click menu | DONE (new) |
| Warn on default-named stations | - | Configuration errors and warnings -> "still named after the square it sits on" | DONE (new) |
| Reversing point | point right-click checkbox | Retired as a user-facing word.  A non-station that turns round compiles to reversing; so does a parking station | DONE |
| Active / inactive | point right-click checkbox | The third option of that radio.  Not a switch of its own any more: "not a station" and "inactive" were two spellings of one idea | DONE |
| Max train length | point right-click → advanced | Right-click → Advanced Parameters | DONE |
| Speed multiplier | point right-click → speed multiplier | Right-click, in percent | DONE **(corrected)** - had been storing a raw percentage where the model wants a factor, so any real value made the configuration refuse to load |
| Station priority | point right-click → advanced | Right-click → Advanced Parameters | DONE |
| Excluded locomotives | point right-click → GraphLocExclude | Right-click → excluded list | PARTIAL **(corrected)** - the list works; GraphLocExclude also explained station-vs-non-station semantics and warned when an exclusion would strand that point's home locomotive |
| Home locomotives | point right-click | Right-click → Home for | DONE **(corrected)** - had been a multi-select writing a JSON array where the model reads one string, so a home became the literal text ["BR 111"] |
| Test connection from a point | point right-click → test connection | Test tool, reports both directions | PARTIAL **(corrected)** - the old test took a LOCOMOTIVE and listed each rejected path with its reason.  The new one is pure topology and cannot answer "can THIS train get there" |
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
| Place a locomotive | point right-click → Add/assign | Editor right-click, at stations only | DONE **(corrected)** - the Auto tab button only NAVIGATES to the run tab, which has no placement control |
| Remove from a point | point right-click | Editor right-click | DONE |
| Clear all locomotives | general right-click | - | **TODO (corrected)** - claimed DONE; nothing anywhere does this |
| See where locomotives are | on the graph | Roster list, and the diagram overlay | DONE |
| Show home locomotives | display option | **Layer exists in spec, not drawn yet** | TODO |

## Running and monitoring

| Old capability | Where it was | New home | Status |
|---|---|---|---|
| Start autonomy | general right-click / button | Locomotive Commands tab, unchanged | DONE |
| Graceful stop | general right-click / button | Unchanged | DONE |
| Watch trains move | graph node/edge colouring | Diagram overlay, same colours | DONE |
| See which paths are locked | graph edge colouring | Diagram overlay (locked state) | DONE |
| Train icon on the diagram | - | Drawn on the diagram overlay | DONE |

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
| Import a configuration | Load JSON from file | Manage → Import | PARTIAL **(corrected)** - imports the STORE format, not a legacy autonomy.json.  The 2026-08-16 ruling kept legacy import/export for code-first users; not implemented |
| Export a configuration | Export current graph | Manage → Export | PARTIAL **(corrected)** - same: store format, not autonomy.json |
| Several named configurations | - | New in the diagram UI | DONE |
| Autosave on exit | checkbox | Always on, hidden | DONE - and it no longer writes the derived graph over the legacy autonomy.json, which it did on every exit |
| Global settings (pace, speeds) | Autonomy Settings tab | Unchanged | DONE |
| Timetables | Timetable tab | Unchanged | DONE |
| Read the graph as a graph | the window itself | Debug-only export to file | PARTIAL |

## Capabilities this ledger previously omitted

Found by review; none has a new home and none has a ruling.

| Old capability | Where it was | Status |
|---|---|---|
| Clear all home locomotives | general right-click -> HomeLocomotiveMenu | **Built 2026-09-02**, in the editor's Bulk Tools submenu rather than on the right-click menu (`R28-C1`) |
| Seven keyboard shortcuts: Ctrl+V place, Del remove, Ctrl+X cut, Ctrl+E/U exclude, Ctrl+H home, Ctrl+S s88 | GraphViewer | TODO - the whole cut/paste move-a-locomotive idiom is gone |
| Double-click a node to edit its locomotive | GraphViewer | TODO |
| Hover a point to log its excluded locomotives | GraphViewer | TODO - exclusions are also not drawn on the diagram |
| Edge editor's "Test" - actually fires the accessories | GraphEdgeEdit | TODO - on a derived-commands design this is the one check that catches a port-map error before a train does |
| Edge editor's "Highlight" - light the accessories a path commands | GraphEdgeEdit | TODO |
| Refusing a duplicate point name | point right-click -> Rename | TODO - names are silently disambiguated instead |
| Tooltips on exclusions and speed multiplier | point right-click | TODO - the other four carried over |
| Display filters: hide inactive, hide reversing stations, hide reversing edges | general right-click | TODO |
| Busy gate on point properties while autonomy runs | GraphViewer / point menu | DONE - added after review |
| Edit s88 address | GraphViewer Ctrl+S | N/A - the tile IS the sensor |

## Open gaps, in priority order

1. **Show home locomotives** - a layer the spec calls for and nothing draws.
2. **Test connection does not say WHY** - the old menu listed invalid paths and the reason each was
   rejected. The new tool says reachable or not. The reasons come from `Layout`'s own validation, so
   this is a matter of surfacing them rather than of computing anything new.
3. **Display filters** - hide inactive points, reversing stations, reversing edges. Less pressing on a
   diagram than on a graph, where they existed to fight clutter, but "hide inactive" is a genuine want
   on a layout with many parking berths.
4. **Exclusions are not drawn** - a point with excluded locomotives looks like any other.
5. **Reading the derived graph** - a file export in debug mode only. The old `GraphViewer` window
   this could have opened in is gone (deleted 2026-08-21); the remaining option is a table.
