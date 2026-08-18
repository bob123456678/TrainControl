# Manual test plan, 18 August 2026

For the autonomy-diagram work on `autonomy-diagram-r0`. Work top to bottom; later tiers assume earlier
ones passed.

**Before starting:** copy `LocDB.data`, `UIState.data`, `autonomy.json` and your `config/autonomy/`
folder somewhere safe.

**When something fails, the useful things to capture:** the tier and step number, the log lines around
it, your exported `autonomy-derived.json`, and a screenshot for anything visual. The arrival chevrons
in particular have never been seen rendered by anyone.

---

## Tier 1 - diagram and editor, autonomy not running

1. **Arrival marks look right.** Autonomy editor, visibility dropdown set to **Station Arrivals**.
   Every station with two or more ways in shows small yellow inward chevrons at its edges. Are they
   legible at your tile size, and clearly not overlapping the red/green direction arrows?
2. **Arrivals menu placement.** Right-click a station with two ways in. **"Trains may arrive…"** is on
   the top level of the menu, beside the usage choice, not inside it. Untick one side; the last
   remaining side should refuse to be unticked.
3. **Arrival marks in the viewer.** Close the editor. A restricted station shows its marks on the
   running diagram; an unrestricted one shows nothing. (Deliberate - no clutter for the default.)
4. **Switched-off link.** Switch a link off. It is greyed on the main diagram, not only in the editor.
5. **Remove a locomotive from a non-station.** Right-click a point holding a loco that is not a
   station. **Remove** is present.

## Tier 2 - data safety

6. **Page switching keeps captions live.** Note a caption on page A. Go to page B, then C, then back to
   A. A's captions still update.
7. **Popup diagram captions.** Pop out a page window, then repaint the main window. The popup's
   captions still update.
8. **Cancel in the track diagram editor.** Delete two sensor squares that carry names, lengths or
   arrival settings, then press **Cancel**. The track comes back AND those squares keep their autonomy
   settings.
9. **Undo covers captions.** Delete a captioned sensor, Ctrl+Z: tile and name both return. Drag a
   captioned tile, Ctrl+Z: the caption follows it back.
10. **Export / import round trip.** Export the autonomy JSON, re-import it. It loads, and Tier 4 step
    19 still holds afterwards. (This was broken until 18 August - the block field was not written.)
11. **Page files.** After a save, `config/gleisbilder/` holds a one-time `.bak` beside a rewritten
    page, and nothing is corrupted.

## Tier 3 - autonomy in simulation, one train

12. **Running path drawing.** The route is a line along the track - red ahead of the train, green
    behind - with black arrowheads for direction. The train marker sits on the tile it has actually
    reached, not one ahead.
13. **Caption direction arrow.** The `>` `<` `^` `v` arrow appears consistently, both for a train you
    placed by hand and for one autonomy drove there.
14. **Barred arrival is honoured.** Bar one side of a two-ended station, reload, run. Trains only pull
    in from the allowed side, and the station is still reachable.
15. **Barred terminus loads.** Mark a terminus "trains may turn round here", bar one of its sides,
    reload. It loads - no "configuration is invalid and must be reloaded".

## Tier 4 - the routing comparison (the one that matters most)

**Adam's framing, and the primary check: what matters is the ROUTING, not the graph. A route that was
impossible before must be impossible now.**

Every automated check written so far is structural - which squares connect, which are usable as
through-points. Those can all pass while the router still offers a journey no train can make. This tier
tests the thing itself. **Ignore parking spaces throughout** - they are excluded destinations in the
new model and were modelled as reversing stations in the old, so they are not comparable.

16. **Collect what the new model offers.** Load the derived configuration. For each of a sample of
    stations - pick ones with a reversing point, a double curve, a one-way section, and a busy junction
    - place a locomotive there and write down every destination offered (the locomotive panel's path
    list, or the station's right-click menu).
17. **Collect what the old model offered.** Load the v2.8.1 hand-authored `autonomy.json`. Place the
    same locomotive at the same station. Write down the destinations offered.
18. **Compare, and scrutinise the NEW-ONLY entries.** A destination the new model offers and the old
    one did not is the dangerous direction - it may be a journey no train can physically make. For each
    one, ask: does the route reverse at a square where a train cannot reverse? Does it change track
    mid-square at a double curve? If yes, that is a routing bug and the most valuable thing you can
    report.
    - Old-only entries (offered before, not now) matter less, but note them: they are lost capability
      rather than an unsafe move.
19. **Run a new-only route in simulation.** Pick one and execute it. Watch the train: does it do
    anything physically impossible? This is the strongest single test in the plan.

## Tier 5 - autonomy in simulation, several trains

20. **Two trains, shared junction.** Run two trains whose routes cross a junction. They never receive
    conflicting routes through it; the second waits.
21. **Collision refusal.** Try to get autonomy to send a second train to an occupied platform,
    including when the occupant arrived from the other direction (the split-copy case, which was
    broken).
22. **Manual displacement still works.** Right-click-place a train onto an occupied station: it
    displaces the previous occupant. This is intended - you are telling the model where a train is.
23. **Long run.** Three or four trains for twenty minutes or more. No train silently stops and stays
    stopped; no gradual gridlock; CPU stays low when a train is boxed in.

## Tier 6 - real hardware, optional

24. **Path-integrity failure.** Let an accessory fail to confirm. The train stops, its track is
    released, and it resumes on the next cycle rather than dropping out of autonomy until a reload.

---

## Note for whoever automates Tier 4

Tier 4 is the check that should have been built first, and it can be automated. The shape:

- load the hand-authored v2.8.1 graph and the derived graph;
- for each station, ask each model for the set of destinations it would offer
  (`getPossiblePaths(loc, true)`, minus parking);
- assert the derived set is a SUBSET of the hand-authored set, modulo stations that only exist in one.

Subset, not equality: the derived model may legitimately offer fewer routes (arrival restrictions, a
station not yet configured), but it must never offer MORE - an extra route is a journey the
hand-authored railway said was impossible.

That is a stronger oracle than the reachability set currently pinned in
`test/autonomy_formats/v2_8_1-station-paths.txt`, which compares which pairs connect and is blind to
whether the path between them is physically runnable.
