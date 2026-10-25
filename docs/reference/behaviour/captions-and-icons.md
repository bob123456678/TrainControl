# Station captions and icons

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 4a. Station captions: a readout, not a name plate

Adam: *"nothing seems to happen when I say 'show a different station here', is it meant to
reset the label?  we should make it clear what this does."*

**A caption is a square that shows the live state of a station somewhere else on the diagram.** It is not a label
naming the square it sits on:

| what the pill shows | what it means |
|---|---|
| a locomotive's name | that train is standing at the station |
| an em dash | the station is empty |
| an arrow | a train is passing through without stopping, pointing the way it is going |
| three bullets | passing through, and the graph cannot say which way: an unsplit station, or a copy with no recorded facing |
| a dark fill behind the name | a home locomotive that is NOT at its home |

**A station square always shows its own station**, and the menu says so rather than offering a choice: its item is
present but disabled, with the reason on its tooltip, because an item that vanishes answers "what does this do" by
refusing to say.

**One caption per station per page.** Pointing a square at a station clears the station's other caption on the same
page, so a page never names the same station twice; on a page where the station has no caption it ADDS one, and the
captions on other pages stay. Showing a station name on a square moves the caption within its page and adds one on
another; dragging a caption moves it within its page only (a drop on another page is refused); "Stop Showing" removes
only the caption of the square clicked. The automatic placement, an import and the migration of old `Point:` labels
each caption a station only where it has none on that page. It is per page for the setup check (Adam: *"The duplicate caption
check should be per page.  It is OK to show on another page what is going on on another, unless you think this breaks
something major."*) and for the editor alike.

A setup edited by hand, or written by an older version, can still carry more than one on a page. A station captioned
on its own square and beside it on that page is drawn once, the beside one, and is no error; one on each of two pages is
drawn on both. Two captions beside it on one page are two names, and the setup check calls that an error (OB-341),
naming the station and those squares, filed on the first of them. What counts is what the diagram draws: a caption on a
page left out of autonomy, or on a page that is gone, neither counts toward the duplicate check nor makes a station's own
caption give way, but it still counts as the station being shown, so the unlabeled-station error stays quiet. A station
that is unnamed and switched off is drawn nowhere and not reported; a station the railway built no Point for is drawn
all the same, and is.

**A caption may sit on blank space, and over your own text it hides it.** The text on that square is not shown, though
it is kept; the editor asks before it puts a caption over text, naming the text, and the setup check warns about each
such square. A caption goes only on a square the running diagram draws (the part of the page its components cover, not
the blank margin the editor shows): Show a Station Name Here is grayed there, with the reason on its tooltip, Control+N
does nothing and a dragged label is refused. Deleting the square takes its caption with it, and deleting the STATION
takes every caption naming it.

**A station with no train reads as an em dash, not as its name**: the caption is about the station's state, and
"empty" is a state. So an unlabeled square captioning an empty station does not say which station it is (FR-014); the
menu names it in the "Stop Showing" item, and the tooltip explains the rest.

## 4b. A station's icon: what the shape says

**Circles, diamonds and squares** (Adam: *"The icons are hard to work with in reality, because
they add complexity.  Keep the work on the new icons so we can revisit later, and revert roughly to what we had before"*).
`TileAnnotation.StationIcons.CLASSIC` is the default; FR-118's icons, below, are kept whole behind
`TileAnnotation.setStationIcons(StationIcons.SHAPES)`.

| | trains do not turn | trains may turn | trains must turn, or its track runs out |
|---|---|---|---|
| a station | big circle | big diamond | big square |
| a sensor that is no station | small circle | small diamond | small square |

Blue where autonomy uses it, gray a berth it does not choose; filled when named, white ringed in the color when not;
the same in the autonomy editor as on the track diagram (*"same appearance in editor as viewer"*). A station's mark is
14 pixels across at the small size and 28 at the large, its diamond 17 and 36, covering the sensor's contact with the
track drawn back across it. A sensor's mark is 10 and 20, its diamond 13 and 26. A square out of service is an orange
sign, with no arrows on its square: on a station a solid orange circle with a white X, its mark's size (*"give the station out of service
a solid orange fill, with a white X inside instead"*); on a sensor the white circle ringed in orange with an orange X, 12
and 24. The red and green arrows are drawn over the mark (MT-710). A mark stays inside its square, on the middle of its
track, on a curve too. On a curve a station's diamond is 15 and 29, a pixel in from the middle of its rails (Adam:
*"make sure that the diamonds are correctly
aligned on curves"*): `testAClassicStationsDiamondOnACurveIsOnItsRails`. `TileAnnotation.classicBox`;
`core.testAutonomyDiagramMonitor`: `testTheClassicIconsAreTheGridAgain`, `testTheClassicIconsAreTheSizesAdamAskedFor`,
`testTheClassicOutOfServiceSignIsAStationsOrASensors`, `testTheClassicIconsAreTheSameInTheEditor`,
`testAStationOnACurveIsWhollyInsideItsSquare`; FR-118's own claims are marked `@DrawsFR118Icons` and drawn with its icons.

**With FR-118's icons**: every station's icon is no taller across its track than the sensor's contact under it (13
pixels at the small size, 26 at the large) and longer along the track, so a caption stood on end beside it is full size
(OB-327; *"the small font on the
vertical labels is really hard to read"*).

| the icon | the station |
|---|---|
| a rounded block | a station |
| a hexagon pointed at both ends | trains may turn round there |
| two arrow-shaped halves back to back, pointing away from each other, white bars in them, the track's black line between them | trains must turn round there |
| a hexagon flat at one end, with a bar at that end | a terminus: its track runs out on that side |
| a white circle with an orange ring and an orange X in it, whatever the square otherwise is | out of service: nothing may pass or stop there |

Blue where autonomy uses the station, gray a berth it does not choose, a color on whatever shape the station has
(*"shouldn't parking have the same shape as terminus?"*); on the track diagram filled when named, white ringed in the
color when not; in the autonomy editor every icon is white ringed in its color, so the direction arrows over it read
clearly (Adam: *"Let's make all of the icons white with a blue border in the editor.
That way, arrows can be seen clearly."*). The icon covers the sensor's own contact with white, the track drawn back
across it. A passing point (a sensor that is no station) keeps its small mark; where trains turn there, a little bigger,
over its contact too. A square out of service, of any kind, is one sign: the white circle with the orange ring and X,
the contact's size, inside the red arrows the sensor shows while it reads occupied, and no arrows on its square (Adam:
*"regardless of the type of station or point it otherwise is"*).

**A terminus is a station whose track runs out on one side** (at a buffer stop, or where the drawn track stops) before
it reaches another sensor (Adam, asked *"a station with only one way out becomes a terminus,
correct?"*: yes). It is drawn flat against that side whatever its turning setting. The TRACK is asked, not the setup: a
station trains can leave only one way because of a one-way run or a barred arrival keeps its own shape. With the
classic icons it is the big square. `AutonomySession.deadEndSide`; `core.testATrainIsPlacedOnceAndKeepsItsFacing.testAStationWhoseTrackRunsOutIsATerminus`.

On a curve the icon lies along the line between the curve's two ends, on its rails and its full size; what falls outside
its square is drawn over the squares beside it, under any caption there, and their arrows, arrival marks and outline are
drawn again over it (Adam: *"can we instead make the stations spill over onto adjacent tiles?"*), in the editor as on
the track diagram. On a double-curve sensor it is on the curve the art draws the contact on, and a terminus there faces
where that curve's track runs out (MT-712). The red and green arrows are drawn over the icon, on the diagram and in the
editor (MT-710: *"We need to
render red arrows on top of stations"*). A may-turn hexagon is 24 pixels long at the small size and 47 at the large, a
terminus 24 and 48, and the must-turn pair no longer than the may-turn (MT-710). The icons:
`core.testAutonomyDiagramMonitor`: `testAStationIsARoundedBlock`, `testWhereTrainsMayTurnIsTheHexagon`,
`testWhereTrainsMustTurnIsTwoTerminiBackToBack`, `testAStationAtADeadEndIsATerminusFacingIt`,
`testParkingIsGreyInItsOwnShape`, `testASquareOutOfServiceIsAnOrangeRingedCircle`, `testAStationCoversTheSensorsContact`,
`testTheEditorsIconsAreWhiteWithTheirColourRound`, `testTheEditorsIconOnACurveIsOnItsRails`,
`testTheTurningIconsAreTheLengthsAdamAskedFor`, `testAStationOnACurveIsWhollyInsideItsSquare`,
`testTheArrowsAreDrawnOverAStationsIcon`; the caption, `ui.testDiagramLooksRight.testAPillOnEndIsFullSize` and
`testAPillOnEndClearsTheStationsBesideIt`.

**A train standing still is drawn with the run's locomotive icon** on its station, facing the way it stands, whether it
is parked with no path or holds one (FR-027, OB-317; Adam: *"same icon as when a run is started"*). **On a curve the icon
is never clipped to its tile** (MT-642; Adam: *"there should be no clip"*): it runs along the rail at a straight's size,
on the train's own road of a double curve, its front the way the train goes and its roof never pointing down, and
reaches onto the next tiles. `core.testAutonomyDiagramMonitor.testTheIconRunsAlongEveryCurveAndDoubleCurveOnItsOwnRoad`.
