# Routing checks in the editor

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 7. Routing checks in the editor

**Test a path and Why not Moving? answer for both tiers at once** (FR-221; Adam: *"I do think we can retire it, with
the missing details clearly shown in the output.  also, the output should include a table of destinations, rather than
just text."*). The Auto Path / Manual Path radios under Path Debugging are gone, and nothing remembered them. The two
tiers differ **only** in where a train may be sent: an inactive square stops both, a barred arrival stops both, a
compulsory turn turns both.

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
Return Home say that trains may not arrive facing its way and what to do: drive it off by hand, turn it around, or open
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

**Test a path draws autonomy's route each way**, yellow there and amber back: the search autonomy runs, turning only
where the train stands and never on the way, and never to a station it is told to leave alone
(`AutonomySession.testPathForAutonomy`). **Where autonomy has none and a hand send has one, that leg is drawn purple**
(`AutonomySession.testPath`), and the message strip says what purple is and why autonomy would not take it: the station
is one autonomy leaves alone (on every known railway the only cause, D184). The route is still drawn, because the track
is passable. `core.testManualOnlyPathsAreADifferentColour.testTheStripSaysWhichLegOnlyAHandSendTakesAndWhy`.

**Neither search passes another copy of either end, nor throws a switch two ways**, as `Layout.bfsBySettings` and
`isPathClear` route: a kickback, whose only way turns on a spur and comes back through the switch set the other way, is
no way at all. On Adam's frozen railway and every test railway no hand path turns round on the way, so the strip's note
for that was removed (D184).
`core.testTestAPathStartsWhereTheFindingsDo.testAKickbackThatThrowsItsSwitchBothWaysIsNoWay`,
`core.testAutonomyDiagramReducer.testNoWalkPassesAnotherCopyOfEitherEnd`. The findings use the same search (FR-238):
`testAStationReachedOnlyPastAnotherCopyOfItsDestinationGetsItsWarning`, `testTheFindingsAgreeWithTestAPathOnTheFrozenRailway`.

**Why Not Moving? gives one answer for both tiers, as a table of destinations** (MT-434; Adam, asked which tool: *"Why
Not Moving?"*): one row per station, saying whether the train **can go now** (green: autonomy may send it there), **by
hand only** (purple: a hand send may, autonomy will not, with autonomy's reason, and "Return Home only" where it is the
train's home by `HomeStaging.atHome`; D177), or **not right now** (gray: neither may, with what stops a hand send, and
autonomy's reason as well where it differs). A station shows the best of its copies, and the headline counts the green
ones only (D175); the diagram marks each square as its row says, and draws autonomy's route to a green one and the hand
send's to a purple one. Rows run by answer, then stations before berths (FR-080), then by page and name (MT-429). By
hand the reasons are the ones the right-click menu meets: autonomy's standing bars (not to be chosen, a reversing square
on the way or at the end) are not reasons there. **What the menu itself leaves out is still a reason by hand**: a
station that excludes the train, and a terminus autonomy may choose for a train that cannot reverse (OB-205), asked
through `Layout.isOfferableToOperator`, so the list and its explanation cannot disagree. A switched-off station is still
refused by hand and says it is switched off. Where only autonomy will not start the train (paused, say), the answer says
so over the table and no station can go now; where nothing may move it, that one reason is the answer.
`gui.autonomy.DestinationTable`, over `Layout.explainDestinationsGrouped(Locomotive, boolean)` asked for each tier;
`regression.testPathTypeRedrawsTheTestInTheEditor.testWhyNotMovingShowsBothTiersInATable`.

**The locomotive list answers the same way**: its "No available paths" tooltip (the first twelve rows) and its
why-window (every row) show the same table, under where the train stands.
`regression.testTheDestinationDoorsAgree.testEveryWhyNamesItsTier` holds every why-window to name its tier, and both
doors to `DestinationTable`.

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

**And the drawn route says it too, in its color.** A tested path is drawn yellow on the way out and amber on the way
back; a leg only a hand send takes, to a station autonomy will never choose **and a person still may** (a parking
berth), is drawn in purple, whichever direction it runs.

**Two squares are deliberately not purple.** A **compulsory turn** is chosen like any other station (OB-195) when *May
choose this station* is on. A square **switched out of service** is not a manual-only point: nothing may be sent
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
refuses it too. A train turns around at a reversing square or a terminus on its way.
`core.testARouteIsFoundPastATerminus.testNoRoundTripBackToTheTrainsOwnSquare`.

---

**May depart sets the arrows on the station's own square** (OB-472; Adam: *"have may depart just control the arrows on the station, which can be set except when a one direction run is forced, such as a terminus"*). It offers `Both Ways` and `One Way, Toward` each side, no `Closed - No Trains` (D144; Adam: *"A: Drop Closed"*), and writes nothing outside the station's square (`AutonomySession.setRunDirection`, an s88 square being a run of its own). Where the track on one side runs out before another s88 square (`AutonomySession.deadEndSide`, the terminus icon's rule) it is grayed, shows the one way out, and its (i) says why; an old arrow shutting that one way leaves it live, so it can be changed. Elsewhere it stays live: a pick that leaves trains no way out is said by the setup check (`checkStationReachesNothing`, or the ERROR `checkStationAtTheEndOfALine` at a line's end trains may not turn at) and the picture's can't-leave notice. An arrow on an s88 square is no error, and a road an older version saved Closed keeps working as it did, shown as no pick. A click on an s88 square opens its Station Settings (read-only during a run) and changes no arrow; armed tools take the click first, and a dragged train's release makes no click. The station picture draws on every square the red and green arrows the track diagram draws there, under the diagram's own arrow options (OB-511; Adam: *"for arrows just make them consistent between the track diagram and what's shown in the station editor popup"*); its next squares keep their green whatever the options, as they are its controls: `ui.testStationSettingsWindow.testThePictureDrawsTheDiagramsArrows`. A click on the station in the picture steps May depart to its list's next answer, round to the first, through the list itself (Adam: *"when clicking the arrows on the station in the station editor, can we get it to cycle the "can depart" dropdown options?"*), under a hand pointer; where the list is grayed it changes nothing and its tooltip gives the (i)'s reason: `testAClickOnTheStationStepsMayDepart`, `testAGrayedMayDepartIsNotSteppedByAClick`. **Two s88 squares with no track between them** (a link's or tunnel's jump counts as none) are INFO, one finding per pair. Test a Path never runs through a stop-only station (`AutonomySession.testPath`). `core.testTheEditorWarnsOfARailwayThatCannotWork.testAnArrowOnAnS88SquareIsNoError`, `testTwoS88SquaresWithNoTrackBetweenAreSaidOnce`, `testTestAPathStopsAtAStopOnlyStation`; `ui.testStationSettingsWindow.testMayDepartSetsOnlyTheStationsSquare`, `testMayDepartIsGrayAtATrackEndAndShowsTheOnlyWayOut`, `testMayDepartIsLiveEverywhereButATrackEnd`, `testAMayDepartPickThatStrandsTrainsIsSaid`.

---

**The diagram's right-click menu offers a standing train its destinations while another train is out**, as the
train's Auto tab card does (FR-187; this ends the known limit OB-164 recorded). The menu and the hand send ask one
question, `RunGates.mayBeSentByHandNow`: no destinations while autonomy, a timetable or Return Home runs or plans, nor
for a train that is out itself; a train sent by hand elsewhere, or a Graceful Stop waiting for trains, does not stop
them. The send asks it again at the click, so a destination offered before Start or Return Home began sends nothing
once they have, and says to wait for the trains. No setup edit is offered during any run; Is Active pauses the train
alone while a run holds the setup, as the card's button does. `ui.testAStandingTrainIsSentWhileAnotherRuns`.

---

**A sensor announces itself however it changed.** A module changes state two ways, a message arriving over the wire and
`setState` (clicking an s88 tile on the track diagram, the simulation's own announce and clear, the restore at
start-up), and both tell the listeners, so the route editor's capture sees a sensor the operator has just clicked. The
announcement is outside the debug check: a capture that depended on a logging setting would be a defect reachable by a
preference.
