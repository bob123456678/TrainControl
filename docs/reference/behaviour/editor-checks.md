# Routing checks in the editor

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 7. Routing checks in the editor

**Path Type (Auto / Manual)** decides which tier the check answers for. The two differ **only** in
where a train may be sent: an inactive square stops both, a barred arrival stops both, a compulsory
turn turns both.

**A BARRED ARRIVAL STOPS BEING SENT THERE, AND NOTHING ELSE ABOUT THE TRACK** (Adam, of RampDown and
BottomMainPost: *"may need station restrictions but not pass through restrictions, since they are meant
for shunting only"*, with *"make sure trains can still go through there"*). The restriction is spent
entirely on one flag: `AutonomyBuilder.arrivalAllowed` makes that copy of the square `station: false`.
The copy is still built, still carries its edges and is still crossed by any route that runs over it -
so a square can be closed as a destination and remain open as track, which is exactly the shunting
siding he describes. The tile-wide copy a never-split square is emitted as is never barred at all,
because there is no arrival side to bar and refusing it would make the station unreachable rather than
restricted.

**A train may still come in the barred way and turn there** (TDA-C8; Adam: *"Arrivals THAT STOP
THERE should only be allowed from the configured side(s).  Turning shouldn't need to factor this in, since the
former would govern the behavior."*). The turn-round copy of a barred side is still built, so the notice asking
for the length behind a turn asks about that side as it does any other; the two berth notices, about a train
that stops, leave it out (MT-552).

**A train can still stand facing the barred way, and is not started there** (OB-284). The copy a train is on is its
direction (section 3), so a train reversed on the throttle, turned by the Facing menu, standing when the side was
barred, or built from a setup that records that facing stands on the barred copy. Autonomy does not start it there,
and Why not Moving? and Return Home say that trains may not arrive facing its way and what to do: drive it off by
hand, turn it round, or open the side (GUI3-C1, TDY4-C3). The rebuild's put-back stands it there again where no
station copy faces its way (TDY3-A1, TDY4-B1); a placement keeps it where the train can leave that way (OB-284); a
home does not save such a facing (GUI3-C2); and an import of an old
autonomy.json takes each train's facing from the side its point's one-way edges leave by, guessing one trains may
arrive in where they cannot say and the square records none - a facing the square records stays (REG3-C1, REG4-A1,
REG4-C1, RLA3-C4; counting it and preferring a way trains may arrive are OB-304). **The old file's directions are
carried onto the diagram first** (Adam: *"Carry the old file's directions onto the diagram - yes, to the extent
possible."*): each one-way edge sets the track it ran over running its way, as the editor's one-way run does; track run
both ways stays both; and the log says how much was carried. **Only onto a diagram nobody has set a direction on** -
the fresh upgrade the ruling was for. A default is stored as nothing, so on a tuned diagram the import cannot tell
track left running both ways on purpose from track nobody has looked at: on Adam's own railway it would make 176
pieces one-way, 170 of them plain track he runs both ways, and directions are shared by every configuration. There the
diagram is left as it is and the log counts what the file ran differently. Where the diagram cannot hold the file's
facing, no impossible facing is saved (OB-270): the train stands the way the square allows, and the import's log names
the square, since that is the other way round from how it drives.

**The flag is decided once.** It is emitted as both `station` and the terminus, and if it were read twice the
turn-round copy of a barred side would come out as a terminus that is not a destination - a pair `Point.setTerminus`
refuses and `parseAuto` answers by invalidating the whole layout. Restricting a terminus platform, the most natural use
this setting has, must not make a setup unloadable. Both of his squares are `canReverse`, so both are that shape.

**Barring every way in is a different thing and is reported, not enforced.** `AutonomySession.shutStations`
names a station whose every arrival side is barred, because such a square is no longer a destination by
any road and that is worth saying out loud rather than discovering from an empty menu.

On **Auto**, a station autonomy is told to leave alone is reported as reachable **and never chosen** -
which is the state somebody opens the panel to explain. The route is still drawn, because the track
is passable and reporting "no path" would be a lie about the railway to make a point about the
settings.

**Why Not Moving? follows Path Type as well** (MT-434; Adam, asked which tool: *"Why Not
Moving?"*). On **Auto** it answers for autonomy: the stations it could choose but cannot right now, and the
stations it will never choose, each with autonomy's reason. On **Manual** it answers for a train sent by hand, the
way the right-click menu decides what to offer: autonomy's standing bars - not to be chosen, a reversing square on
the way or at the end - are not reasons there, so those stations are listed as reachable when a route is clear, and
every refusal is under *Stations the train cannot be sent to right now*.  **What the menu itself leaves out is still
a reason by hand** (AMR-B2): a station that excludes the train, and a terminus autonomy may choose for a train that
cannot reverse (OB-205) - `Layout.isOfferableToOperator`'s rule, asked through the same method, so the list and
its explanation cannot disagree.  A switched-off station
is still refused by hand, as the first paragraph of this section says of both tiers: nothing may be sent to one,
and the menu's rule is asked first, so it is listed there saying it is switched off (MFV-C5; AMV-C6). Switching the
radio asks the last square again. `Layout.explainDestinations(Locomotive, boolean)`;
`regression.testPathTypeRedrawsTheTestInTheEditor.testWhyNotMovingFollowsPathType`.

**The locomotive list answers the same way** (REG4): its "No available paths" tooltip and its
why-window have no Path Type of their own, so they answer for a train sent by hand whenever autonomy is not running
- which is when the list offers routes picked by hand - and for autonomy while it runs. Otherwise a train on a copy
that is no station would be told it could not be sent anywhere while the list offered it routes.
`regression.testTheDestinationDoorsAgree.testEveryWhyNamesItsTier` holds every why-window under `gui/` to a tier.

**And where the only way there is a lap, it says so** (AMR-B1). No route in any tier passes
another copy of the square it starts at or the square it ends at: a square drawn as several Points is one piece of
track, so such a route goes round a loop to where the train already was, or drives through its destination to reach
it. Adam, shown the 22 such routes on his railway and the 14 the right-click menu was offering: *"We need to refuse
both. A copy makes a cycle."* **The railway's search applies it; Return Home's does not**: applying it
there cost Return Home plans it used to find, and Return Home is manual operation (§1), so he chose *"menu and
autonomy only"*. So a lap is available to the planner when it is the only way a train
can get home, and `HomeStaging.auditAgainstRuntime` exempts such a destination as a deliberate tier difference rather
than a planner defect — the runtime agrees with the looser answer, because `isPathClear` exempts an intermediate copy
of the train's own square as occupied by that train. A station left unreachable by
it is reported with a reason of its own - *"The only track route there doubles back through a square this journey
already uses."* - rather than as missing track, which is the same distinction PTR-B1 drew
for the terminus: the question the window puts to the TRACK may walk a lap, so the sentence is chosen from the route
that question found. `core.testARouteIsFoundPastATerminus.testNoRoutePassesAnotherCopyOfTheTrainsOwnSquare`,
`testNoRoutePassesAnotherCopyOfItsDestination` and `testWhyNotMovingSaysTheOnlyWayThereIsALap`.

**And the drawn route says it too, in its colour.** A tested path is drawn yellow
on the way out and orange on the way back; a leg whose **destination** is a station autonomy will
never choose **and a person still may** - a parking berth - is drawn in magenta instead, whichever
direction it runs.

**Two squares are deliberately not magenta.** A **compulsory turn** is chosen like any other station
(OB-195) when *Can Be Chosen in Full Autonomy* is on. And a square **switched out of service** is
not a manual-only point at all: nothing may be sent there by any tier, so colouring the leg as though
a hand-driven run were the remedy would point at a shut door. The routing check says so in its own
sentence instead. The colour asks `AutonomySession.manualOnlyStations`, which is the runtime
rule less the shut squares. Adam: *"just use a different color going to manual-only points.
yellow is currently forward, and orange is backwards — path, not the chevron arrows."* The colour is
about the destination, so every square of that leg carries it; the chevrons still say the direction.

**A route is found past a terminus, never through one** (OB-229; Adam: *"Search past termini"*). A route may
end at a terminus and may not pass through one - the route check refuses that on every tier. The route search does not
extend a route through a terminus: it spends each square on the first way it reaches it, so a shorter
way through a terminus would hide a longer clear way past it (from Tunnel, 75 407 DB must be offered RampDown and
BottomSecondary by the loop over the top level). The right-click
menu, autonomy's choice and Why Not Moving? all use that search. `core.testARouteIsFoundPastATerminus`.

**No tier sends a train round a loop to another copy of its own square** (Adam: *"we should never do a round trip
just to change direction"*). The right-click menu and autonomy
never offer one - a copy of the square a train stands on reads as occupied by that train - and Why Not Moving? does not
list the train's own square; Return Home's planner refuses it too. A train turns round at a reversing square or a
terminus on its way, never by lapping the railway back onto where it started.
`core.testARouteIsFoundPastATerminus.testNoRoundTripBackToTheTrainsOwnSquare`.

---

**Known limit: in non-atomic mode the diagram's right-click menu can offer a train no destinations**
where the Locomotive commands panel offers several (OB-164, MT-087). The two ask different questions -
the menu gates on the one locomotive being on a run, the panel on the whole railway running - and the
state that separates them was never reproduced. Adam: *"The user can rely on full autonomy
or the panels to send trains more clearly."* Accepted as it stands, and written here so the next reader
who notices the two surfaces disagree finds the answer rather than reopening it.

---

**A sensor announces itself however it changed** (W21-B1). A module changes state two ways - a
message arriving over the wire, and `setState`, which is everything else: clicking an s88 tile on the
track diagram, the simulation's own announce and clear, the restore at start-up. Both tell the listeners, so the
route editor's capture sees a sensor the operator has just clicked. The
capture cannot tell a clicked sensor from a wired one and should not: a route being recorded is about
what the railway did, not about which code path said so. The announcement is outside the debug check -
a capture that depends on a logging setting is the same defect reachable by a preference.
