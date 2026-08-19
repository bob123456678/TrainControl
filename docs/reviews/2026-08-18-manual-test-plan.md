# Manual test plan, 18 August 2026

For the autonomy-diagram work on `autonomy-diagram-r0`. Work top to bottom; later tiers assume earlier
ones passed.

**Before starting:** copy `LocDB.data`, `UIState.data`, `autonomy.json` and your `config/autonomy/`
folder somewhere safe.

**When something fails, the useful things to capture:** the tier and step number, the log lines around
it, your exported `autonomy-derived.json`, and a screenshot for anything visual. The arrival chevrons
in particular have never been seen rendered by anyone.

---

Priority: In Autonomy 1b.json (the currently loaded config), trains are stuck and nothing moves, even though there are plenty of apparently valid routes.  Diagnose on priority.

Bugs unrelated to what's explicitly in this review:

Unrelated: path tracing doesn't follow switch angles, but rather right angles.  Make it follow switches and curves correctly.

Unrelated: in station (yes/no), make it say yes, terminus if the MUST option is set, or reversing if the may option is set.

Unrelated: diagram viewer right-click autonomy menus are active when the autonomy editor is open.  either ensure consistency, or (preferred) disable rightclickautonomymenu during that time. (with a warning)

Unrelated: move "changing direction" just below station yes/no in the autonomy editor right click menu

Unrelated: home locomotive option disappears from the track viewer right click menu if I edit the station type while there is a loc there

Unrelated: "a train reaching {s88} from at least one direction could not go on" move to notices for non-stations

Unrelated: starting autonomy from the right click menu on the track diagram does not properly activate the autonomy running view.  graceful stop button/option not activated.  regression.


## Tier 1 - diagram and editor, autonomy not running

1. **Arrival marks look right.** Autonomy editor, visibility dropdown set to **Station Arrivals**.
   Every station with two or more ways in shows small yellow inward chevrons at its edges. Are they
   legible at your tile size, and clearly not overlapping the red/green direction arrows?

Icons are OK but the offset is odd.  Also, [---] station labels are propagating into some of the stations in the editor, which overlaps.  

Idea: make station shapes semantic.  A triangle that points in the way it accepts arrivals.  We just need a way to differentiate "can reverse" and "must reverse" then.


Bug: clicking on the arrows to cycle in the editor affects an unrelated tile.  Changing in menu works.

2. **Arrivals menu placement.** Right-click a station with two ways in. **"Trains may arrive…"** is on
   the top level of the menu, beside the usage choice, not inside it. Untick one side; the last
   remaining side should refuse to be unticked.

Works

3. **Arrival marks in the viewer.** Close the editor. A restricted station shows its marks on the
   running diagram; an unrestricted one shows nothing. (Deliberate - no clutter for the default.)

Works, but overlap with the labels makes it suboptimal.  station icon may fix this.  Side requirement: left clicking a station icon should propagate the click to the s88 and back.

4. **Switched-off link.** Switch a link off. It is greyed on the main diagram, not only in the editor.

Looks right in the track diagram.  But not greyed out in the editor.  Also, move the "use this link" option out of the submenu into the top level.

5. **Remove a locomotive from a non-station.** Right-click a point holding a loco that is not a
   station. **Remove** is present.

Works.  For the 3 type options (trains can stop, trains can pass through, neither, prefix with "Yes, No, No".  Out of service -> nothing can pass.

## Tier 2 - data safety

6. **Page switching keeps captions live.** Note a caption on page A. Go to page B, then C, then back to
   A. A's captions still update.

They do- but I didn't test running with autonomy.

7. **Popup diagram captions.** Pop out a page window, then repaint the main window. The popup's
   captions still update.

Works, but I noticed that some locomotives get a V > suffix, not just V or >.  Also, when moving a locomotive from one point to the other, it would be ideal if its natural direction could be preserved, compatible with the entrance direction to the station.

8. **Cancel in the track diagram editor.** Delete two sensor squares that carry names, lengths or
   arrival settings, then press **Cancel**. The track comes back AND those squares keep their autonomy
   settings.

Labels disappear, stations stay.  Bug!  Confirmed the labels stay gone after reload.

Also, the confirm dialog in the diagram editor says 'are you sure you want to exit without saving', but the autonomy is 'save before existing?'  make the latter consistent.

9. **Undo covers captions.** Delete a captioned sensor, Ctrl+Z: tile and name both return. Drag a
   captioned tile, Ctrl+Z: the caption follows it back.

Bug- caption says, but content changes from the name itself to [---].  

Also: still don't see a way to move labels in the layout editor.

10. **Export / import round trip.** Export the autonomy JSON, re-import it. It loads, and Tier 4 step
    19 still holds afterwards. (This was broken until 18 August - the block field was not written.)

Seems fine.  Not sure what the block field is.

11. **Page files.** After a save, `config/gleisbilder/` holds a one-time `.bak` beside a rewritten
    page, and nothing is corrupted.

I don't see the .bak, but check on your end.

## Tier 3 - autonomy in simulation, one train

12. **Running path drawing.** The route is a line along the track - red ahead of the train, green
    behind - with black arrowheads for direction. The train marker sits on the tile it has actually
    reached, not one ahead.

Looks OK for now, couldn't test much.

13. **Caption direction arrow.** The `>` `<` `^` `v` arrow appears consistently, both for a train you
    placed by hand and for one autonomy drove there.

No, see above.  The arrow is sometimes duplicated.

14. **Barred arrival is honoured.** Bar one side of a two-ended station, reload, run. Trains only pull
    in from the allowed side, and the station is still reachable.

Honored.

15. **Barred terminus loads.** Mark a terminus "trains may turn round here", bar one of its sides,
    reload. It loads - no "configuration is invalid and must be reloaded".

Correct. And reversible locomotives are enforced.

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

Help me collect this programmatically.  You can add code and run 3.0.0 and 2.8.1.  I will then validate.

Sample 5 locs, some reversing, and connect only stations to each other.  Activate all points except reversing points in the sim.

17. **Collect what the old model offered.** Load the v2.8.1 hand-authored `autonomy.json`. Place the
    same locomotive at the same station. Write down the destinations offered.

Help me collect this programmatically.  You can add code and run 3.0.0 and 2.8.1.  I will then validate.

Sample 5 locs, some reversing, and connect only stations to each other.  Activate all points except reversing points in the sim.

18. **Compare, and scrutinise the NEW-ONLY entries.** A destination the new model offers and the old
    one did not is the dangerous direction - it may be a journey no train can physically make. For each
    one, ask: does the route reverse at a square where a train cannot reverse? Does it change track
    mid-square at a double curve? If yes, that is a routing bug and the most valuable thing you can
    report.
    - Old-only entries (offered before, not now) matter less, but note them: they are lost capability
      rather than an unsafe move.
18b. **The known-bad journey.** Specifically check whether the new model offers
    **BottomMainA -> BottomSecondary** directly. Adam: it should NOT - a red signal after the end
    requires a stop at TopMainR1 or TopMainR2, a constraint that lived in the hand-authored edge
    config commands and that the derivation cannot currently express.  If it is offered, that is the
    clearest example of the gap, and worth reporting first.

19. **Run a new-only route in simulation.** Pick one and execute it. Watch the train: does it do
    anything physically impossible? This is the strongest single test in the plan.

## Tier 5 - autonomy in simulation, several trains

Deferred due to priority issue above.

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

List them for me (start, end, intermediate) so I can test top examples here.

**How weak that pinned file is, precisely:** on the sample layout every one of the 240 station pairs is
reachable in the hand-authored graph, so pair reachability cannot distinguish a good model from a
broken one there at all.  And reachability cannot see a constraint that lives in EDGE CONFIGURATION -
BottomMainA reaches BottomSecondary in the graph and must not be routed there, because of a signal.
The pinned file is still worth keeping as a change-detector; it is not a correctness oracle.

---

## KNOWN GOOD: autonomy works at `41b10ac`

Adam, 18 August, after testing: "Autonomy works well now."

That is the first build on this branch where trains actually run on the diagram-derived model.  The
commit that made the difference is `b3c86cc` - lock edges asking about the throat rather than the
platform beyond it - which undid a regression of mine that had refused every route out of a pair of
converging platforms whenever either had a train on it.

If autonomy stops working again, `41b10ac` is the state to diff against.

## Feature backlog (Adam, 18 August)

Not scheduled - recorded so they are not lost.

- **Multiple select in the diagram editor.** Select a range of tiles and act on them at once.
- **+/- buttons to add rows and columns** to a diagram, rather than editing its size numerically.
- **Build a new track diagram from chosen linked pages**, joined together into one - so a layout split
  across pages for drawing convenience can be viewed and worked on as a single diagram.
- **Semantic station shapes** (deferred 18 August): a triangle pointing the way a station accepts
  arrivals, instead of the current badge plus arrival chevrons. Needs a way to tell "can reverse" from
  "must reverse" first. The current yellow arrival chevrons are fine, so this is polish, not a fix.
- **The station-label rebuild is roughly cubic on the feedback path.** `AutonomyBuilder` and
  `uniqueNames()` are rebuilt per point per feedback event. Slow, not wrong, and it has not been felt
  on a layout this size - but it is on the wrong side of the curve, so it wants doing before somebody
  brings a bigger one. (Deferred out of the disposition audit's C6.)
- **A configurable path-choosing rule.** Autonomy picks a path from those available by one fixed rule
  today. Offer the choice: prefer the shortest, always the shortest, random, or another rule. "Prefer"
  and "always" are different railways - always-shortest will queue trains behind one another on the
  short way round rather than send the second train the long way, which is what somebody wants for a
  timetable and not what they want for a layout that should look busy.

## Manual review items - closed 18 August

All five carried since the manual pass are fixed:

- Path tracing followed right angles rather than the track. Both highlights - the editor's tested path
  and the running one - now draw the chord the rail is drawn as. `4589c6a`
- The diagram's right-click autonomy menu was live while an editor held the diagram. Shut, with the
  reason on it, exactly as the Autonomy menu already was. `fb0bd4b`
- "Changing direction" moved up under the station group. `fb0bd4b`
- The home item vanished from the viewer menu when a train was standing on a square whose type had
  just been edited. It asks a copy that speaks for the square now. `fb0bd4b`
- The three usage options read Yes / No / No, and "neither - out of service" says "nothing can pass".
  `fb0bd4b`

Length propagation is fixed too (`53e2327`): a length set on a platform never reached the graph,
because the sum covered only the track strictly BETWEEN two sensors and a station is an endpoint. All
eleven lengths on the test layout summed to nothing. The tile an edge arrives on is counted now, which
gives every tile along a route exactly once.

## The stuck trains, diagnosed 18 August

`Layout.debugPath` reports every candidate route with the reason it was refused, which is what made
this findable. "No paths" is the same words for traffic and for a bug.

**The cause.** Every route out of BottomSecondary was refused with *lock edge
`1 - Main 12,7 -> BottomInnerOtherside` occupied* - because a train stood at BottomInnerOtherside. The
two trains that could have moved both needed BottomSecondary, where that train was. A three-way cycle
with nothing to break it.

A lock edge is track kept clear so two routes cannot take one throat at once. A train standing at the
Point one leads to is not on that track and cannot be: reduction cuts an edge at every sensor, so a
Point's tile is an endpoint of the edges meeting there and an intermediate step of none of them - 54
endpoint tiles and 259 intermediate tiles on this layout, none in both. Fixed in `2ab59d4`; BR 628
went from nothing to seven destinations, matching what Adam said should be possible.

**What is left, and is correct.** The other two trains are still held, now by a train genuinely
standing on the platform they both want. That resolves itself the moment BR 628 moves.

**Still open: `LowerBack` reaches nothing.** Zero destinations by BFS on an empty railway, so this is
structural rather than traffic. `LowerFront` reaches only ParkingTrack12. Worth a look at page 2's
connections.

**Not a bug: `BottomMainC`.** Ten destinations reachable, four clear. The six refusals are all
legitimate - three are out-and-back routes that reverse at BottomMainPost and then need the same
switch in two positions, three pass back through a terminus.

## KNOWN GOOD: `95a91f6`, tagged `autonomy-clean-2026-08-18`

The state to fall back to. What is true of it:

- 43 test classes clean. `testAutoDetect` fails 3/3 for want of a Central Station at 192.168.50.25,
  which is this machine and not a regression.
- Trains move. The lock-edge deadlock is gone; BR 628 is offered seven destinations from
  BottomSecondary, and the two trains still held are held by a train genuinely standing where they
  both want to go.
- Edge lengths reach the graph: 26 of 99 edges carry one.
- `LocDB.data`, `UIState.data` and `setup.json` byte-identical after the runs.

Not covered: no hardware, and no Swing rendering seen rather than reasoned about.
