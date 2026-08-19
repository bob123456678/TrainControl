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

- **Prettify the route editor.** Users should not have to type strings. An overlay table with an oval
  per setting, clicked through rather than typed, would say the same thing far more legibly - and the
  route editor is one of the few places left where the interface asks somebody to get syntax right.

  Sits on top of the existing window: no change under the hood beyond the UI interactions and a
  translation each way between the ovals and the text format the editor already reads and writes. That
  boundary is the thing to hold to - the text form stays the source of truth, so a route the new
  interface cannot express is still editable, and nothing that reads routes has to learn anything new.

  **Decided 18 August: commands AND conditions both get ovals.**

  Commands are the easy half - a flat list of seven kinds (accessory, feedback, function, locomotive
  speed, locomotive direction, stop, functions-off), each a row of two or three ovals with an add and a
  delete. That part is a table.

  Conditions are not a list. `MarklinRoute` holds them as a `NodeExpression` **tree**, normalized to
  right-nested form, with AND and OR mixed - so a flat row of ovals joined by "AND" would misrepresent
  any route whose conditions are not a single conjunction, and silently change what the route means the
  moment somebody edited it. Three ways out, in order of how much they promise:

  1. Render the tree as indented groups, each with its own operator. Honest, and the only shape that can
     round-trip everything.
  2. Offer ovals only for a flat conjunction, and fall back to the text field for anything else - with
     the reason shown, not just a disabled control.
  3. Normalize everything to a conjunction on save. Do NOT do this: it changes routes people already
     have.

  Either 1 or 2 is fine; 2 first is defensible if the tree work looks large. What must not happen is a
  widget that shows a nested condition as though it were flat.


- **One-way running through a link.** A link cannot carry a direction today: its route is a stub - the
  same side twice - so "toward A" and "toward B" name the same place, and the editor does not offer the
  setting rather than appear to accept one that does nothing. Expressing it needs the JUMP itself to
  carry a direction, which is a change to how a portal is traversed rather than a new menu.

- **Multiple select in the diagram editor.** Select a range of tiles and act on them at once.

  **Decided 18 August: paint, erase, rotate and copy/paste all act on the selection.**
  Rotate turns each selected tile about its own centre rather than rotating the shape of the block.
  Copy takes the bounding rectangle of the selection, including squares inside it that were not
  selected, and paste drops it at the cursor.

  Still to settle when the work starts: what paste does to squares it lands on that are not empty
  (overwrite, or skip and report), and whether a paste that would run off the edge of the diagram grows
  it or is refused.

- **+/- buttons to add rows and columns** to a diagram, rather than editing its size numerically.

  **Decided 18 August: removing a row or column that still carries track is ALLOWED**, with no refusal
  and no confirmation. The editor has an undo clipboard and nothing is written until the user confirms
  in the editor, so the cost of getting it wrong is one undo. Note the "+" half already exists on a
  keyboard shortcut and a right-click item and only wants buttons; the "-" half does not exist at all -
  `LayoutDiagram.addRowsAndColumns` clamps negatives to zero.

  Still to settle: whether the minus button removes the LAST row/column or the one at the cursor.

- **Build a new track diagram from chosen linked pages**, joined together into one - so a layout split
  across pages for drawing convenience can be viewed and worked on as a single diagram.

  **Decided 18 August: position the pages from the portal pairs, and report disagreements** rather than
  silently picking one offset. Each pair of paired portals implies an offset between two pages; where
  several pairs join the same two pages and disagree, use one and say which pair is out and by how much.

  Still to settle: whether the result is a new saved page or a read-only view, and what happens when two
  pages want the same square. The second is the one that decides the feature - a layout drawn as
  overlapping pages cannot be joined without either moving track or refusing.
- **Semantic station shapes** (deferred 18 August): a triangle pointing the way a station accepts
  arrivals, instead of the current badge plus arrival chevrons. Needs a way to tell "can reverse" from
  "must reverse" first. The current yellow arrival chevrons are fine, so this is polish, not a fix.
- **The station-label rebuild is roughly cubic on the feedback path.** `AutonomyBuilder` and
  `uniqueNames()` are rebuilt per point per feedback event. Slow, not wrong, and it has not been felt
  on a layout this size - but it is on the wrong side of the curve, so it wants doing before somebody
  brings a bigger one. (Deferred out of the disposition audit's C6.)
- ~~**A configurable path-choosing rule.**~~ Done: **Autonomy > Route Choice**, offering At Random
  (the default), Past the Fewest or the Most Stations, Over the Shortest or the Longest Track, Across
  the Fewest or the Most Sensors, and Least Recently Visited. An application preference rather than a
  per-configuration one - it is how the user wants their trains to behave, not a fact about a railway,
  and it survives a reload.

  Two corrections to what this entry said when it was written: the menu is under Autonomy rather than
  Preferences, and "fewest sensors" now counts SENSORS. It counted hops of the running graph, and on a
  derived graph a square is several Points - so two routes over exactly the same s88s could come out
  with different numbers.

  The default is deliberately the OLD behaviour. A preference that changed how existing railways run
  the moment their owner upgraded would be a regression with a switch beside it, and the people most
  affected drive from scripts and would never see the menu. It is also the cheapest option, so the
  upgrade costs nothing either: the ranked choices enumerate the alternatives before comparing them,
  and this one stops at the first route that works.

  Station priority still wins. Ranking happens between stations of EQUAL priority, so a station
  somebody marked important is not beaten by a shorter route to an ordinary one.

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

- 43 test classes reported clean - but that number was produced by a runner that read only the
  failure count. Two classes, `testAutonomyPathValidation` and `testLayoutTiles`, ask init() for the
  UI and had silently never run under it. Both pass when given a display. `testAutoDetect` fails 3/3
  for want of a Central Station at 192.168.50.25, which is this machine and not a regression.
- Trains move. The lock-edge deadlock is gone; BR 628 is offered seven destinations from
  BottomSecondary, and the two trains still held are held by a train genuinely standing where they
  both want to go.
- Edge lengths reach the graph: 26 of 99 edges carry one.
- `LocDB.data`, `UIState.data` and `setup.json` byte-identical after the runs.

Not covered: no hardware, and no Swing rendering seen rather than reasoned about.
