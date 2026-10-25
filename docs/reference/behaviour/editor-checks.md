# Routing checks in the editor

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 7. Routing checks in the editor

**Path Type (Auto / Manual)** decides which tier the check answers for. The two differ **only** in where a train may be
sent: an inactive square stops both, a barred arrival stops both, a compulsory turn turns both.

**A BARRED ARRIVAL STOPS BEING SENT THERE, AND NOTHING ELSE ABOUT THE TRACK** (Adam, of RampDown and
BottomMainPost: *"may need station restrictions but not pass through restrictions, since they are meant
for shunting only"*, with *"make sure trains can still go through there"*). `AutonomyBuilder.arrivalAllowed` makes that
copy of the square `station: false`; the copy is still built, carries its edges and is crossed by any route over it, so
a square can be closed as a destination and open as track. The tile-wide copy of a never-split square is never barred:
there is no arrival side to bar, and refusing it would make the station unreachable.

**A train may still come in the barred way and turn there** (Adam: *"Arrivals THAT STOP
THERE should only be allowed from the configured side(s).  Turning shouldn't need to factor this in, since the
former would govern the behavior."*). The turn-round copy of a barred side is still built, so the notice asking for the
length behind a turn asks about that side; the two berth notices, about a train that stops, leave it out (MT-552).

**A train can still stand facing the barred way, and is not started there** (OB-284). The copy is its direction
(section 3), so a train reversed on the throttle, turned by the Facing menu, standing when the side was barred, or built
from a setup recording that facing stands on the barred copy. Autonomy does not start it there, and Why not Moving? and
Return Home say that trains may not arrive facing its way and what to do: drive it off by hand, turn it round, or open
the side. The rebuild's put-back stands it there again where no station copy faces its way; a placement keeps it where
the train can leave that way (OB-284); a home does not save such a facing. An import of an old autonomy.json takes each
train's facing from the side its point's one-way edges leave by, guessing one trains may arrive in where they cannot
say and the square records none; a facing the square records stays (counting it and preferring a way trains may arrive
are OB-304). **The old file's directions are carried onto the diagram first** (Adam: *"Carry the old file's directions onto the diagram - yes, to the extent
possible."*): each one-way edge sets the track it ran over running its way, track run both ways stays both, and the log
says how much was carried. **Only onto a diagram nobody has set a direction on**: a default is stored as nothing, so on a
tuned diagram the import cannot tell track left both ways on purpose from track nobody has looked at (on Adam's railway
it would make 176 pieces one-way, 170 of them plain track he runs both ways, and directions are shared by every
configuration). There the diagram is left as it is and the log counts what the file ran differently. Where the diagram
cannot hold the file's facing, no impossible facing is saved (OB-270): the train stands the way the square allows, and
the log names the square.

**The flag is decided once.** It is emitted as both `station` and the terminus; read twice, the turn-round copy of a
barred side would come out as a terminus that is not a destination, which `Point.setTerminus` refuses and `parseAuto`
answers by invalidating the whole layout. Restricting a terminus platform must not make a setup unloadable; both of his
squares are `canReverse`, so both are that shape.

**Barring every way in is reported, not enforced.** `AutonomySession.shutStations` names a station whose every arrival
side is barred, since it is then no longer a destination by any road.

On **Auto**, a station autonomy is told to leave alone is reported as reachable **and never chosen**. The route is still
drawn, because the track is passable.

**Why Not Moving? follows Path Type as well** (MT-434; Adam, asked which tool: *"Why Not
Moving?"*). On **Auto** it answers for autonomy: the stations it could choose but cannot right now, and those it will
never choose, each with autonomy's reason. On **Manual** it answers for a train sent by hand, as the right-click menu
decides what to offer: autonomy's standing bars (not to be chosen, a reversing square on the way or at the end) are not
reasons there, so those stations are listed as reachable when a route is clear, and every refusal is under *Stations the
train cannot be sent to right now*. **What the menu itself leaves out is still a reason by hand**: a station that
excludes the train, and a terminus autonomy may choose for a train that cannot reverse (OB-205), asked through
`Layout.isOfferableToOperator`, so the list and its explanation cannot disagree. A switched-off station is still refused
by hand and listed there saying it is switched off. Switching the radio asks the last square again.
`Layout.explainDestinations(Locomotive, boolean)`;
`regression.testPathTypeRedrawsTheTestInTheEditor.testWhyNotMovingFollowsPathType`.

**The locomotive list answers the same way**: its "No available paths" tooltip and its why-window answer for a train
sent by hand whenever autonomy is not running, and for autonomy while it runs, or a train on a copy that is no station
would be told it could go nowhere while the list offered it routes.
`regression.testTheDestinationDoorsAgree.testEveryWhyNamesItsTier` holds every why-window under `gui/` to a tier.

**And where the only way there is a lap, it says so.** No route in any tier passes another copy of the square it starts
at or the square it ends at: such a route goes round a loop to where the train already was, or drives through its
destination to reach it. Adam, shown the 22 such routes on his railway and the 14 the right-click menu was offering:
*"We need to refuse
both. A copy makes a cycle."* **The railway's search applies it; Return Home's does not**: it cost Return Home plans, and
Return Home is manual operation (§1), so he chose *"menu and
autonomy only"*. A lap is available to the planner when it is the only way home, and
`HomeStaging.auditAgainstRuntime` exempts such a destination as a deliberate tier difference: `isPathClear` exempts an
intermediate copy of the train's own square as occupied by that train. A station left unreachable by it is reported with
a reason of its own, *"The only track route there doubles back through a square this journey
already uses."*, rather than as missing track, chosen from the route the track question found.
`core.testARouteIsFoundPastATerminus.testNoRoutePassesAnotherCopyOfTheTrainsOwnSquare`,
`testNoRoutePassesAnotherCopyOfItsDestination` and `testWhyNotMovingSaysTheOnlyWayThereIsALap`.

**And the drawn route says it too, in its color.** A tested path is drawn yellow on the way out and orange on the way
back; a leg whose **destination** is a station autonomy will never choose **and a person still may** (a parking berth)
is drawn in magenta, whichever direction it runs.

**Two squares are deliberately not magenta.** A **compulsory turn** is chosen like any other station (OB-195) when *Can
Be Chosen in Full Autonomy* is on. A square **switched out of service** is not a manual-only point: nothing may be sent
there by any tier, and the routing check says so in its own sentence. The color asks
`AutonomySession.manualOnlyStations`, the runtime rule less the shut squares. Adam: *"just use a different color going to manual-only points.
yellow is currently forward, and orange is backwards — path, not the chevron arrows."* Every square of that leg carries
the color; the chevrons still say the direction.

**A route is found past a terminus, never through one** (OB-229; Adam: *"Search past termini"*). A route may end at a
terminus and may not pass through one, on every tier. The route search spends each square on the first way it reaches
it, so a shorter way through a terminus must not hide a longer clear way past it (from Tunnel, 75 407 DB must be offered
RampDown and BottomSecondary by the loop over the top level). The right-click menu, autonomy's choice and Why Not Moving?
all use that search. `core.testARouteIsFoundPastATerminus`.

**No tier sends a train round a loop to another copy of its own square** (Adam: *"we should never do a round trip
just to change direction"*). The right-click menu and autonomy never offer one (a copy of the square a train stands on
reads as occupied by that train), Why Not Moving? does not list the train's own square, and Return Home's planner
refuses it too. A train turns round at a reversing square or a terminus on its way.
`core.testARouteIsFoundPastATerminus.testNoRoundTripBackToTheTrainsOwnSquare`.

---

**Known limit: in non-atomic mode the diagram's right-click menu can offer a train no destinations**
where the Locomotive commands panel offers several (OB-164, MT-087). The menu gates on the one locomotive being on a
run, the panel on the whole railway running, and the state that separates them was never reproduced. Adam: *"The user
can rely on full autonomy
or the panels to send trains more clearly."* Accepted as it stands.

---

**A sensor announces itself however it changed.** A module changes state two ways, a message arriving over the wire and
`setState` (clicking an s88 tile on the track diagram, the simulation's own announce and clear, the restore at
start-up), and both tell the listeners, so the route editor's capture sees a sensor the operator has just clicked. The
announcement is outside the debug check: a capture that depended on a logging setting would be a defect reachable by a
preference.
