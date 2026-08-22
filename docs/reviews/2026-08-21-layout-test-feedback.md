# What the layout testing found - 2026-08-21

**Prefix for citing this document: `LT`.**

Adam ran the 41 hands-on tests in `2026-08-20-tests-to-run.md` against `cb0074ec` and wrote his results
into that file. This is the work list drawn from them: what to inspect, what to change, and what each
one is waiting on.

Numbered by severity to the convention in [README.md](README.md). A is wrong behaviour or lost work, B
is wrong in one flow, C is cosmetic or a small refinement. Feature requests that are not defects are
listed separately at the end, because they are not findings.

Every entry names the test it came from, so his words can be found in context.

---

## Status

| # | What | From | Status |
|---|---|---|---|
| LT-A1 | Ctrl+X / Ctrl+V over a diagram square does nothing - the keys act on the locomotive buttons instead | 21 | Fixed - it was the station LABEL, which resolves to no Point |
| LT-A2 | A tile moved off the graph loses its station AND its locomotive, and cannot be made a station again | 1 | Fixed - the capture pruned by Point, not by tile |
| LT-A3 | Dragging a selection LEFT removes the locomotive | 2 | Believed fixed with A2 - same prune; needs a re-run to confirm |
| LT-A4 | A locomotive's direction changes when its tile is moved to valid connected track | 1 | Reported, not corrected - a new check names the square |
| LT-A5 | Feedback events do not capture into CONDITIONS; switches do | 10 | Fixed - sensors reached the view through nothing at all |
| LT-B1 | Editing a route teleports the user to the Track Diagram tab after the sync | 6 | Fixed - the tab is put back rather than the culprit hunted |
| LT-B2 | Signal auto-detection by address does not work in conditions, only in commands | 10 | Fixed |
| LT-B3 | A paired, in-use link is drawn greyed out as if autonomy ignored it | 19 | Open - could not reproduce by reading; needs one detail |
| LT-B4 | An unnamed station is a warning; it should be an error | 24 | Fixed - the javadoc always said blocking |
| LT-B5 | The route editor still syncs with the Central Station on close even with no CS routes | 4 | Fixed |
| LT-B6 | No confirmation when closing the route editor with unsaved changes | 5 | Fixed |
| LT-C1 | The signal picker window closes and reopens when a signal is removed | 23 | Fixed |
| LT-C2 | The autonomy editor's banner has an odd border and a grey artifact on its right | 18 | Fixed |
| LT-C3 | The autonomy diagram needs about one more row of scrollable height | 14 | Fixed |
| LT-C4 | Boolean-operator rows in the conditions table are greyed, which reads as disabled | 3 | Fixed |
| LT-C5 | The drag-target group is light red; it should be blue, with the selection staying red | 13 | Fixed |
| LT-A6 | Cutting a locomotive threw its protecting signals - real ironwork moved from a setup gesture | 21 re-run | Fixed |
| LT-A7 | Pasting worked over the platform but not over the station's name beside it | 21 re-run | Fixed |

## Menu work, all from tests 22 and 23

| # | What | Status |
|---|---|---|
| LT-M1 | Track diagram deep menu only: hide Show a Station Name Here, Clear This Square, the locomotive settings item, Signal Protecting This Station, and all three locomotive entries (Add to Autonomy, Move to This Station, Remove from This Square) | Fixed |
| LT-M2 | Home appears in both the track diagram's own menu and the deep menu - remove it from the top one | Fixed |
| LT-M3 | Move "{loc} Is Facing..." out of the deep menu and up to the track diagram's own menu | Fixed |
| LT-M4 | Hide "Make a One-Way Run from Here..." in the deep menu; it stays in the autonomy editor | Fixed |
| LT-M5 | Rename "Connections and Direction" to "Trains May Depart...", and move "Trains May Arrive" beside it | Fixed |
| LT-M6 | Move the link options out of Connections and into the menu itself | Fixed |
| LT-M7 | Give every right-click group of three or more a semantic heading | Fixed - station, turning, arrivals, departures and links all headed |
| LT-M8 | Selection menu: rename "Pick" to "Select", make the existing item a Deselect, and deselect automatically once a move completes | Fixed |

## What LT-B3 needs from Adam

A link that is paired and in use should not be shaded, and reading the code says it is not: shading is
`isDimmed`, which is a component plus `isIgnored`, and `isIgnored` is false for a LINK or TUNNEL unless
its PAGE is excluded from autonomy.  Neither disqualified nor transparent covers them.

Two states would explain what was seen, and they are different bugs:

- the link's PARTNER is on a page excluded from autonomy, in which case shading the far end is correct
  and shading this one is not;
- the link was shaded while the pairing list was open, in which case something in that flow is greying
  what it should be highlighting.

Which one it was decides the fix, so it is left open rather than guessed at.

## Not defects - feature requests, recorded not started

| # | What | From |
|---|---|---|
| LT-F1 | Double-clicking a locomotive label on the track diagram opens the placement view, when autonomy is not running | 1 |
| LT-F2 | The autonomy editor and the track diagram editor as two tabs of one window | 19 |

## Documentation

| # | What |
|---|---|
| LT-D1 | Bring tests 26-41 into the manual list itself rather than pointing at two other documents - **done**, all 41 are in one file |

---

## Confirmed clean

Tests 7, 8, 9, 12, 16, 17, 20, 41 and the second half of 10 came back with no defect. Test 11 was
verified synthetically earlier and not re-run; test 25 needs no run, since nothing is deployed yet.
Tests 23 and 24 pass apart from the items above. Test 41 - the one written because an automated test
could not see the tile regression - passed.
