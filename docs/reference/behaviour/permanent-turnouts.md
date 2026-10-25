# Permanently-set turnouts

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

### 5e. Permanently-set turnouts: fork to base only, and they still bound a berth

> *"They are trailable, so it's about intent and documentation.  They just can't go from the base to
> the fork since we don't know which way they will end up.  Fork to base is OK."* - Adam

A `CUSTOM_PERM_*` tile declares a turnout with no address that cannot be thrown. It is supported, not an error: the
setup loads with a warning, and `TilePorts` gives each of the four a port map of its own.

**Autonomy never makes a facing move over one.** With the blades in a position nobody has recorded, a move from the
base out to the fork cannot choose a leg, so that move does not exist in the port map. Fork to base does: the turnouts
are trailable, so a train merges safely whichever way the blades lie, and both legs may trail in. The restriction is the
hardware's and lives in `TilePorts`; a direction the operator authors on the tile ANDs with it and can only narrow it.

**A scissors crossing is refused outright** (`CUSTOM_SCISSORS` and `CUSTOM_PERM_SCISSORS`): it is two tiles depicting
one double slip, whose topology cannot be expressed per tile. An **undeclared** address-less switch is refused too:
routing over it would trust it to lie the right way, which is what `CUSTOM_PERM_*` exists to declare.

**It bounds a berth like any other switch** (Adam: *"treat them the same as regular
switches for the purposes of the check"*). The room walk of 5a stops at a permanent turnout, so a berth beyond one is
measured from the points. The two questions differ:

- **routing** asks which road a train takes, and over a permanent turnout there is only one, so it is not a switch for
  that purpose;
- **the berth check** asks where a train comes to REST: a train too long for the berth hangs back across the toe and
  blocks the other leg's merge whichever way the blades lie.

**A crossing does not stop the walk.** Nothing merges at a crossing, so a train standing across one fouls another
route's track, which is the tail walk's business in 5c.

**And the editor does not offer an arrow for the road that is not there** (OB-233; Adam: *"green arrows in the
direction that can't be chosen shouldn't be offered"*). An authored direction ANDs with the hardware's restriction, so
on these tiles "toward the fork" and "both ways" restrict nothing, and a green arrow for them would be untrue. The menu
offers what `TileGraph.directionIsPossible` allows, which is **not** the walk's question: `directionAllows` and it
disagree about `NONE` (always offerable, never passable), and whether a train MOVES is `isPassable`. Where only one way
is left, that one is ticked, because the stored answer is `BOTH` by default and `BOTH` is not on offer. Shutting a route
is always offered.

**The arrows are the whole story on every square whose roads share an end** (OB-351; Adam: *"into
spur should simply require S to be green, and out of spur should require W to be green.  Align it so that the arrows
tell the full story.   If we need more granular control, we can just prevent movement on the track attached to s"*,
and *"3-way switches work similarly but have 4 settings"*). A move through a switch, three-way switch or double slip is
allowed exactly when the side it leaves by is open; each road stores the answer of its two sides, narrowed by the
hardware, so closing one road of a permanent turnout closes its toe and with it the other road. The per-road items are
not offered there; crossings, double curves and two-square crossings keep them, since each of their roads has ends of
its own. A setup holding a road setting its arrows cannot show is read as its arrows show, logged once and saved.
`core.testTheArrowsTellTheWholeStory`.

Held by `core.testAutonomyDiagramReducer.testTheRoomWalkStopsAtASwitchAndAPermanentTurnoutButNotACrossing` and
`core.testAutonomyDiagramPorts.testOnlyThePossibleDirectionsAreOffered`.

### 5f. Two permanent turnouts drawn toe to toe are one crossing

> *"Lets support two permanent Ys as a crossing, if possible, same as the current crossing by effectively rotated 45
> degrees, just connecting differently and spread across 2 tiles."* - Adam (OB-320)

The diagram has no diagonal crossing, so one is drawn as two `CUSTOM_PERM_Y` tiles whose toes face each other: the four
legs are the crossing's four ends, and each track runs from a leg of one square, over the toes, to the **opposite** leg
of the other, never round the reverse curve to the leg beside it. Read as turnouts, nothing could pass them.

`TileGraph.crossingPartner` finds the pairs: two permanent turnouts toe to toe, beside each other on one page, every
leg of one facing the opposite leg of the other: two Ys, or two lefts or two rights turned half round from each other
(Adam, of the two permanent rights at 15,13 and 16,13 on TC3Sandbox_layout: *"Treat those two adjacent perma switches at 15,13 and 16,13
as one logical crossing."*). **Only that.** A permanent turnout on its own, toe to a leg, side by side, back to back, a
left with a right, a three-way, or toe to toe with a switch that has an address, is the turnout of 5e (Adam: *"there won't always be two adjacent Y to form a logical crossing.  sometimes it could just be one
perma Y"*; and *"those without another adjacent to form a crossing are treated as switches only crossable in one
direction by autonomy"*). The leg rule is what makes the pair **look** like a crossing, which is the test Adam set:
*"make sure that the pairing is visually a crossing.  if not, we
treat the tracks verbatim."*

- **Both ways, both tracks.** A pair's roads are not directed into the toe; it defaults to both ways as a crossing does,
  and is not warned about as a turnout trains may only trail.
- **One track at a time.** The two tracks share the side the squares meet at, so a square entered by it is told which
  track the train came along (the square before and its road there, `TileGraph.legAcross`) by every walk that crosses
  it: the reduction's edge walk and the undirected walks of the One-Way tool and an old file's import. A walk is
  remembered by the track too, so a run that crosses on one track and comes back over the other is not taken for going
  in circles.
- **One direction per track.** A direction set on one square's half of a track is set on the other's, the same way along
  the track, whichever door it comes through (`AutonomySession.record`). Halves that disagree (a setup saved while the
  squares were turnouts, or a turnout given its partner since) are read as a turnout's, the same on both
  (`TileGraph.settleCrossingHalves`): towards the toe is both ways; towards a leg is closed.
- **The click** steps both tracks through both ways, each track one way with the other both ways, the four ways they can
  each run one way, and closed (Adam: *"Pretend these are two curved tracks on one tile, so we need to cycle
  through both possible directions on both (4 combos)"*; and on MT-688: *"when cycling, there is no option for red in
  one direction and green in the other 3"*), counted on the north or west square so either square carries on from the
  same place.
- **A crossing for the berth too**: the room walk of 5a does not stop at it, and the berth rule refuses at it only where
  another road runs over it. Locking is unchanged: both tracks run over both squares, so two trains cannot be on the
  crossing at once.

Held by `core.testTwoYsMakeACrossing`.
