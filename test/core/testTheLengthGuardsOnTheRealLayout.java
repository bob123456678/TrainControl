package core;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The length guards, on the real railway, with lengths set deliberately small.
 *
 * Adam, 2026-09-06: **"For testing, it should be 1 between BottomMainA and BottomMainPost, and 1
 * between BottomMainA and TunnelLongPark.  Therefore, the route should be refused.  In your tests,
 * these are the types of setups I want you to set up.  It may not be realistic, but it will allow us
 * whether the guards work correctly."**
 *
 * **This is a different instrument from the generated railways beside it.**  Those check the rules
 * against the spec on shapes nobody drew; this checks them on the shape Adam actually operates, with
 * the measurements turned down until the guard has to fire.  A rule can be perfect on a chain of three
 * synthetic points and never fire on his layout - which is exactly what was found the day this was
 * written: with four fifty-unit trains standing on it, not one edge was covered, because almost
 * nothing carried a length.
 *
 * So the lengths are set here rather than assumed.  Unrealistic on purpose: one unit between stations
 * is not a railway anybody would build, and it is the only way to be sure the refusal comes from the
 * measurement rather than from the guard never being reached.
 *
 * **ON THE FROZEN RAILWAY, AND WITH THE TRAIN PUT WHERE IT IS WANTED.**  The shape is what this class
 * is about - a real throat, real platforms, real switches - and that shape is the same in
 * `test/layouts/live-snapshot` as it is on the layout Adam operates.  Where his trains happen to be
 * standing is not part of the subject, and reading it off the live folder made this class say
 * something different every time he ran a train: the 2-8-4 was at BottomMainB when this was written
 * and is at BottomMainA now, so the class was red for a reason that has nothing to do with any guard.
 *
 * Everything happens in a `LayoutSandbox` copy of that snapshot (OB-111).  His railway is neither read
 * nor written.
 */
public class testTheLengthGuardsOnTheRealLayout
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    private static int edgesAtOpen;

    /** The page his main railway is on, which the setup names rather than numbers. */
    private static final String MAIN = "1 - Main";

    /**
     * The train this class asks with, made here rather than borrowed.
     *
     * Adam, 2026-09-09: *"generate trains programmatically in the tests, and give them semantic
     * names."*  Every claim that used to ask `anyPlacedLocomotive` - "whatever is standing" - was
     * asserting about ONE square, chosen by the order the layout enumerates its Points in, while
     * reading as an assertion about the whole railway.  Two of them were false from a different square
     * and green from that one.
     *
     * Deleted in `tearDown`.  `MarklinControlStation.init` opens Adam's real locomotive database rather
     * than an empty one - the layout sandbox copies the layout folder and nothing else - so a train
     * left behind here is a train on his railway.
     */
    private static final String OUR_TRAIN = "length guard probe";

    /**
     * Its address, chosen only so that a stray one left behind by a crash is identifiable.
     *
     * Addresses are not unique in this database and several test classes already share one, so what
     * this needs to be is nobody else's rather than free.
     */
    private static final int OUR_ADDRESS = 61;

    private static MarklinLocomotive ourTrain;

    /**
     * The square this class starts its train from, and what it is called.
     *
     * `1 - Main:20,13` is BottomMainB - the platform Adam names in the ruling this class was rewritten
     * for, and the one square whose measured approach the snapshot actually carries a length on.
     */
    private static final TileKey BOTTOM_MAIN_B = new TileKey(MAIN, 20, 13);

    /**
     * The tile the whole of Adam's 2026-09-09 ruling turns on.
     *
     * `1 - Main:22,7` is the last unswitched square before BottomMainPost, and one of only three tiles
     * the snapshot measures at all.  Adam: *"I see no tracks with a defined length except for 22,7."*
     */
    private static final TileKey TWENTY_TWO_SEVEN = new TileKey(MAIN, 22, 7);

    @BeforeClass
    public static void setUp() throws Exception
    {
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // This opened `cs2_sample_layout` - his real railway, in the shape it is in right now.  The
        // shape is the same; where the trains are standing is not, and this class asserted that the
        // 2-8-4 stood at BottomMainB.  It is at BottomMainA today, so the class was RED, and it would
        // have gone red again on its own at the next shunt whatever anybody did to the code.
        //
        // `live-snapshot` is the same railway with the clock stopped, taken from `git show HEAD:`
        // rather than from the working tree, so it cannot move under a test.  And the placement this
        // class needs is made in code below rather than read off the fixture, so it does not depend on
        // where the snapshot's trains stand either.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, false);

        // THIS CLASS'S OWN TRAIN, before anything is measured or placed.
        ourTrain = model.newMM2Locomotive(OUR_TRAIN, OUR_ADDRESS);

        assertNotNull(ourTrain, "the class could not create its own train, so every claim below would"
            + " be about whichever of Adam's engines the snapshot happens to have standing first");

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        // THE SWITCHES HAVE TO BE WIRED BEFORE THE DIAGRAM IS REDUCED, and forgetting it is why every
        // probe written against this layout today reported "no path".
        //
        // `parseLayout` reads the drawing; the accessory behind each switch is attached separately,
        // by the application, from the addresses on the tiles.  A switch with no accessory is a tile
        // the reducer cannot trace through - so the graph falls from fifty-odd edges to eighteen, the
        // railway comes apart into fragments, and every station is unreachable from every other.
        //
        // Measured here rather than assumed: `edgesAtOpen` was 18 without this and the assertion below
        // now holds it above 50.  `testTheCheckerAgreesWithTheBuild` has done this all along, which is
        // why that test sees a connected railway and four probes written today did not.
        wireAccessories(pages);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        edgesAtOpen = session.getReducer().getEdges().size();

        assertTrue(edgesAtOpen > 50,
            "only " + edgesAtOpen + " edges were derived from the sample diagram. The railway is in"
            + " fragments and nothing below can be routed anywhere - which reads as \"the guard"
            + " refused it\" and is nothing of the kind");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDown() throws Exception
    {
        giveTheLengthsBack();

        // Each on its own, so a failure to remove the train still puts the preference back.
        if (model != null)
        {
            try
            {
                model.deleteLoc(OUR_TRAIN);
            }
            catch (Exception alreadyGone)
            {
            }
        }

        if (sandbox != null) sandbox.close();
    }

    /*
     * WHAT THIS CLASS BORROWS FROM ADAM'S RAILWAY, AND WHAT IT STOPPED BORROWING ON 2026-09-09.
     *
     * Adam: *"generate trains programmatically in the tests, and give them semantic names."*
     *
     * It borrows by NAME - `75 407 DB` at three sites and `2-8-4 3505 SP` at one, each with an
     * `assertNotNull` saying the engine "is not on this railway any more" - and those sites stay, with
     * `borrowTheLengthOf` and `giveTheLengthsBack` putting every length back.
     *
     * **What it no longer borrows is "whatever is standing".**  `anyPlacedLocomotive` returned the
     * first occupied Point the layout enumerates, and three claims asked it for a train.  That made
     * every one of them an assertion about ONE START SQUARE while reading as an assertion about the
     * whole railway - and the note that used to be here recorded two questions about it, both of which
     * are now answered by measurement rather than left open.
     *
     * **THE FIRST QUESTION: is the guard refusing from some start squares and not others?**  No.  It
     * refuses on the room behind the BERTH, which is a property of the berth and of the last part of
     * the approach to it, so different starts reach different berths and get different answers.  That
     * is the rule working, not a hole in it: `testTheOneUnitAtTwentyTwoSevenDecidesBottomMainPost`
     * pins it in three measurements at one berth, and `testWhyRampDownIsOffered` pins why a berth eight
     * edges away is not governed by the same tile.
     *
     * **THE SECOND QUESTION: does `roomTheGuardSees` answer a different question from
     * `Layout.measuredRoomAtTheBerth`?**  Yes, and it is the helper that is loose.  It takes the
     * LARGEST `getRoomAtTheEnd` of any edge ending at a station of that name, over every copy of it and
     * every approach; the guard uses the room on the path in hand.  Where a station is reached by more
     * than one approach the two are different numbers, and the note that used to be here reported "the
     * room at that berth is ONE" for RampDown on a path whose last edge crosses no switch at all and
     * has no such number.  It is still used - by `testExactlyFitsIsAdmittedAndOneMoreIsNot`, which
     * searches for a berth where the guard binds and then verifies the refusal itself, so a loose
     * number there costs a candidate rather than a wrong verdict.
     */

    /**
     * ONE TILE, THREE MEASUREMENTS, AND THE BERTH IT ACTUALLY GOVERNS.
     *
     * Adam, 2026-09-09: **"Make sure you add a test case to confirm this pathing IS possible if no
     * lengths are set anywhere, and if those lengths are set to 9."**  Both of his cases are here, with
     * the case that separates them in the middle.
     *
     * `1 - Main:22,7` is the last unswitched square before BottomMainPost, so it is the room a train
     * coming to rest there has:
     *
     *   - nothing measured anywhere - BottomMainPost is offered.  Adam, on an unmeasured run in:
     *     *"generally, allow it."*  Unmeasured is unknown, not zero.
     *   - 22,7 measured at ONE - a nine-unit train is refused.  One unit of berth cannot hold nine
     *     units of train, and the train would come to rest lying across the switch behind it.
     *   - 22,7 measured at NINE - offered again, and exactly-fits is admitted.  Adam: *"if we made both
     *     lengths 4, would it then be accepted?  That should be the minimum acceptable length."*
     *
     * **Nothing but that one number changes between the three.**  Same railway, same train, same start
     * square, opposite answers - which is the shape the claim this replaced could not have, because it
     * asserted about every berth at once on a fixture that measures three tiles.
     *
     * MUTATION, run: `>=` for `>` at `Layout.whyTooLongForTheBerth` fails the third case (nine into
     * nine is refused); widening the comparison by any constant fails the second.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheOneUnitAtTwentyTwoSevenDecidesBottomMainPost() throws Exception
    {
        ourTrain.setTrainLength(9);

        measureOnlyTheSnapshotsThreeAs(0);

        Set<String> unmeasured = destinationsFromBottomMainB();

        measureOnlyTheSnapshotsThreeAs(1);

        Set<String> atOne = destinationsFromBottomMainB();

        measureOnlyTheSnapshotsThreeAs(9);

        Set<String> atNine = destinationsFromBottomMainB();

        assertTrue(reaches(unmeasured, "BottomMainPost"),
            "with nothing measured anywhere, BottomMainPost is not offered to a nine-unit train - so"
            + " the guard is refusing on the ABSENCE of a measurement, which makes every unmeasured"
            + " layout unusable and is what Adam's \"generally, allow it\" forbids. Offered: "
            + unmeasured);

        assertFalse(reaches(atOne, "BottomMainPost"),
            "one unit of room at BottomMainPost still admits a nine-unit train. It comes to rest eight"
            + " units across the switch behind it, which is the state Adam's covered-track rule already"
            + " refuses to route anything else over. Offered: " + atOne);

        assertTrue(reaches(atNine, "BottomMainPost"),
            "nine units of room REFUSED a nine-unit train, so exactly-fits is being refused and every"
            + " berth measured to the train that lives in it becomes unusable - Adam: \"that should be"
            + " the minimum acceptable length\". Offered: " + atNine);
    }

    /**
     * And the control: the same one unit of room, a train that fits, is still welcome.
     *
     * Without this the refusal above passes on a railway that offers BottomMainPost to nobody - and the
     * claim this file used to make had no control of that kind at the berth it was about, which is how
     * it stayed green while being false.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAOneUnitTrainIsStillAdmittedToTheOneUnitBerth() throws Exception
    {
        measureOnlyTheSnapshotsThreeAs(1);

        ourTrain.setTrainLength(1);

        Set<String> offered = destinationsFromBottomMainB();

        assertTrue(reaches(offered, "BottomMainPost"),
            "a ONE-unit train is refused a berth measured at one unit, so the refusal in the test above"
            + " says nothing about lengths - it says this berth is closed to everybody, which is a"
            + " different fault and would hide the guard entirely. Offered: " + offered);
    }

    /**
     * Turn the measurements up and the same long train becomes welcome again.
     *
     * The whole-railway form of the control: not one tile but every one of them, which is the closest
     * this fixture can come to the claim it used to make.  **It is not the same claim**: measuring
     * every TILE at one unit does not make every SECTION one unit, because a section here is many
     * tiles - the run into RampDown is eighteen of them.  So this asserts it of the berth whose
     * approach really is short, and `core.testALongTrainIsOfferedNothingOnAOneUnitRailway` makes the
     * every-section claim on `single-switch`, where a section is two to four tiles and the claim is
     * true from every square.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheSameTrainIsAdmittedOnceThereIsRoom() throws Exception
    {
        ourTrain.setTrainLength(9);

        measureEverythingAs(1);

        Set<String> whenTight = destinationsFromBottomMainB();

        measureEverythingAs(50);

        Set<String> whenRoomy = destinationsFromBottomMainB();

        assertFalse(reaches(whenTight, "BottomMainPost"),
            "the nine-unit train was admitted to BottomMainPost with every tile on its approach"
            + " measured at one unit. Offered: " + whenTight);

        assertTrue(reaches(whenRoomy, "BottomMainPost"),
            "the same train is refused BottomMainPost even with fifty units in every tile, so the guard"
            + " is refusing for a reason that has nothing to do with length and the test above passes"
            + " for that reason too. Offered: " + whenRoomy);
    }

    /**
     * WHY RampDown IS OFFERED, AND WHY 22,7 CANNOT BE THE REASON IT IS NOT.
     *
     * Adam, 2026-09-09, on being shown that a nine-unit train standing at BottomMainB is offered
     * `RampDown (southbound, reverse)`: **"technically incorrect to say there is a path since we pass
     * the track of length 1 at 22,7 to get there, then nothing."**
     *
     * **The first half of that is right and the conclusion does not follow, and this test is the
     * measurement that says so.**  The route really does cross 22,7 - it is the first edge of it - and
     * after that nothing on the way to RampDown is measured.  But 22,7 is not on RampDown's approach in
     * the sense the room rule uses, and the geometry is the whole answer:
     *
     *   - RampDown is at `1 - Main:21,6`, BottomMainPost at `22,6`.  They are adjacent squares on the
     *     drawing and there is NO edge between them: the reduction connects RampDown only to
     *     TopMainPost at `7,2`.
     *   - So the route from BottomMainB runs BottomMainB - BottomMainPost - `7,1` - RampUp - down the
     *     ramp - TopMainR2 - TopMainPost - RampDown: NINE edges, the long way round, with three
     *     switch-crossing edges between 22,7 and the berth.
     *   - The last of those nine crosses no switch at all and is eighteen tiles long.  `roomAtTheEnd`
     *     is what bounds a berth, and this edge has none to bound it with.
     *
     * `Layout.measuredRoomAtTheBerth` walks BACKWARDS from the berth and stops at the last switch -
     * Adam's own ruling of 2026-09-02, *"between the switch and the station, the length must be >=
     * length of the train"* - so it stops seven edges short of 22,7 and could not reach it.  The one
     * unit at 22,7 measures the room at BOTTOMMAINPOST, which the train passes through and does not
     * stop at, and `testTheOneUnitAtTwentyTwoSevenDecidesBottomMainPost` is that tile doing exactly its
     * job.
     *
     * **This is a pin, not a complaint.**  If somebody later decides a route must also fit between the
     * switches at the points it merely passes through, this test is the one that will go red, and its
     * message says which claim was traded for which.  `configureAndLockPath` locks the whole route
     * before the train moves, so nothing stops it at BottomMainPost as things stand.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testWhyRampDownIsOffered() throws Exception
    {
        ourTrain.setTrainLength(9);

        measureOnlyTheSnapshotsThreeAs(1);

        Layout built = rebuild();

        List<Edge> route = onlyOurTrainAtBottomMainB(built) == null ? null : theRouteTo(built,
            "RampDown");

        assertNotNull(route,
            "a nine-unit train standing at BottomMainB is offered no route to RampDown at all with the"
            + " snapshot's three tiles measured at one unit. Adam's ruling is about that route being"
            + " offered, so if it has gone the ruling has been overtaken and this test is the record of"
            + " what it used to say");

        // THE TILE IS ON THE FIRST EDGE, MEASURING THE ROOM AT BOTTOMMAINPOST.
        assertEquals(route.get(0).getEnd().getName().split(" ")[0], "BottomMainPost",
            "the route from BottomMainB to RampDown no longer starts by running to BottomMainPost, so"
            + " the tile at 22,7 is somewhere else on it and the reasoning below does not hold. It runs:"
            + " " + namesOf(route));

        assertEquals(route.get(0).getRoomAtTheEnd(), 1,
            "the first edge of the route measures " + route.get(0).getRoomAtTheEnd() + " units after"
            + " its last switch rather than the one unit at 22,7, so that tile is not where this test"
            + " believes it is");

        // AND THE BERTH'S OWN APPROACH HAS NOTHING TO BOUND IT WITH.
        Edge last = route.get(route.size() - 1);

        assertFalse(last.crossesASwitch(),
            "the last edge into RampDown crosses a switch now, so it DOES bound where the train may"
            + " come to rest and the guard has a number to judge with. That is a different railway from"
            + " the one this test was measured on");

        assertEquals(last.getLength(), 0,
            "the last edge into RampDown is measured at " + last.getLength() + " units, so the snapshot"
            + " has grown a measurement on it and the \"then nothing\" in Adam's ruling is no longer"
            + " true of it");

        // SWITCHES IN BETWEEN, which is what stops the walk long before 22,7.
        int switchesBetween = 0;

        for (int i = 1; i < route.size(); i++)
        {
            if (route.get(i).crossesASwitch()) switchesBetween++;
        }

        assertTrue(switchesBetween > 0,
            "there is no switch anywhere between BottomMainPost and RampDown, so the room walk would"
            + " run all the way back and 22,7 WOULD bound the berth. It runs: " + namesOf(route));

        assertEquals(Layout.measuredRoomAtTheBerth(route, ourTrain), null,
            "the guard now measures " + Layout.measuredRoomAtTheBerth(route, ourTrain) + " units at"
            + " RampDown where it used to decline to judge, so something has been given a length or the"
            + " walk has been changed");

        // AND ADAM'S TWO CASES: unmeasured and at nine, the route is offered either way.
        measureOnlyTheSnapshotsThreeAs(0);

        assertTrue(reaches(destinationsFromBottomMainB(), "RampDown"),
            "with no lengths set anywhere, RampDown is not offered - Adam asked for this case by name");

        measureOnlyTheSnapshotsThreeAs(9);

        assertTrue(reaches(destinationsFromBottomMainB(), "RampDown"),
            "with the snapshot's three tiles measured at nine, RampDown is not offered - Adam asked for"
            + " this case by name");
    }

    /**
     * Clears every measurement and gives the three tiles the snapshot carries the same length.
     *
     * Those three - `19,12`, `14,13` and `22,7` - are the whole of what `live-snapshot` measures, and
     * Adam's ruling is stated in terms of them: *"we only gave 3 tracks artificially low lengths."*
     * Setting them here rather than reading them keeps the two halves of every claim below symmetrical,
     * and follows his rule for the scenario library - hand-authored for topology, lengths in code.
     *
     * @param units the length to give each of the three
     */
    private void measureOnlyTheSnapshotsThreeAs(int units)
    {
        measureEverythingAs(0);

        session.setTileLength(new TileKey(MAIN, 19, 12), units);
        session.setTileLength(new TileKey(MAIN, 14, 13), units);
        session.setTileLength(TWENTY_TWO_SEVEN, units);
    }

    /**
     * Stands this class's train at BottomMainB, alone, and answers where the railway will send it.
     *
     * **Alone on purpose.**  Every other train is taken off first, so what is offered depends on the
     * measurements and on nothing else - where the snapshot's engines happen to stand is not part of
     * the subject, and reading it off the fixture is what made three claims in this file say something
     * different depending on which square was enumerated first.
     *
     * @return the names of every destination offered
     * @throws Exception on a failure to build
     */
    private Set<String> destinationsFromBottomMainB() throws Exception
    {
        Layout built = rebuild();

        assertNotNull(onlyOurTrainAtBottomMainB(built),
            "there is no copy of 1 - Main:20,13 a train may stand on, so nothing was asked");

        Set<String> out = new LinkedHashSet<>();

        List<List<Edge>> paths = built.getPossiblePaths(ourTrain, true);

        if (paths == null) return out;

        for (List<Edge> path : paths)
        {
            if (!path.isEmpty()) out.add(path.get(path.size() - 1).getEnd().getName());
        }

        return out;
    }

    /**
     * Empties the railway and puts this class's train on BottomMainB.
     *
     * @param built the running layout
     * @return the copy it is standing on, or null when the square has no copy that can hold it
     */
    private Point onlyOurTrainAtBottomMainB(Layout built)
    {
        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null)
            {
                built.moveLocomotive(null, point.getName(), true);
            }
        }

        Point at = placeAt(built, ourTrain, BOTTOM_MAIN_B);

        if (at != null)
        {
            assertEquals(session.getStationIndex().baseNameOf(at.getName()), "BottomMainB",
                "1 - Main:20,13 is called " + at.getName() + " on this railway rather than BottomMainB,"
                + " so the start square these claims are written about is not the one they name");
        }

        return at;
    }

    /**
     * The route the railway offers this train to a named station, or null when it offers none.
     *
     * @param built the running layout
     * @param station the destination's base name
     * @return the edges in order, or null
     */
    private List<Edge> theRouteTo(Layout built, String station)
    {
        List<List<Edge>> paths = built.getPossiblePaths(ourTrain, true);

        if (paths == null) return null;

        for (List<Edge> path : paths)
        {
            if (path.isEmpty()) continue;

            if (path.get(path.size() - 1).getEnd().getName().startsWith(station)) return path;
        }

        return null;
    }

    /**
     * Whether any of these destination names is a copy of the named station.
     *
     * A station is emitted as several Points - one per side a train can arrive by, and a turning copy
     * beside each - so `BottomMainPost` is offered as `BottomMainPost (northbound)` or
     * `BottomMainPost (northbound, reverse)`, and a claim that asked for the bare name would never
     * match.
     *
     * @param offered what the railway offered
     * @param station the station's base name
     * @return true when at least one copy of it was offered
     */
    private boolean reaches(Set<String> offered, String station)
    {
        for (String name : offered)
        {
            if (name.startsWith(station)) return true;
        }

        return false;
    }

    /**
     * @param route the edges
     * @return their names, for a failure message
     */
    private String namesOf(List<Edge> route)
    {
        StringBuilder out = new StringBuilder();

        for (Edge edge : route) out.append(" ").append(edge.getName());

        return out.toString();
    }

    /**
     * Attaches an accessory to every switch and signal drawn with an address.
     *
     * The application does this on load; a test that reads the diagram itself has to do it too, or the
     * reducer cannot trace through a single switch.  Copied from
     * `testTheCheckerAgreesWithTheBuild`, which has had it since it was written.
     *
     * @param pages the parsed diagram
     */
    private static void wireAccessories(List<LayoutDiagram> pages)
    {
        for (LayoutDiagram page : pages)
        {
            for (org.traincontrol.base.LayoutDiagramComponent component : page.getAll())
            {
                if (!component.isSwitch() && !component.isSignal()) continue;

                // the application skips tiles drawn without a digital address, and so does this
                if (component.getAddress() <= 0) continue;

                org.traincontrol.base.Accessory.accessoryType type = component.isSignal()
                    ? org.traincontrol.base.Accessory.accessoryType.SIGNAL
                    : org.traincontrol.base.Accessory.accessoryType.SWITCH;

                component.setAccessory(accessory(component.getAddress(), type,
                    component.getProtocol()));

                if (component.isThreeWay())
                {
                    component.setAccessory2(accessory(component.getAddress() + 1,
                        org.traincontrol.base.Accessory.accessoryType.SWITCH,
                        component.getProtocol()));
                }
            }
        }
    }

    private static org.traincontrol.marklin.MarklinAccessory accessory(int logicalAddress,
        org.traincontrol.base.Accessory.accessoryType type,
        org.traincontrol.base.Accessory.accessoryDecoderType protocol)
    {
        return new org.traincontrol.marklin.MarklinAccessory(null, logicalAddress - 1, type, protocol,
            org.traincontrol.marklin.MarklinAccessory.getNameWithProtocol(logicalAddress, type,
                protocol), false, 0);
    }

    /**
     * TunnelLongPark refuses this train at every measurement - which is worth pinning as a FACT.
     *
     * This test used to assert that measuring the approach wide enough would get `75 407 DB` into
     * TunnelLongPark. Measured 2026-09-08: it is refused with two units of room and refused with
     * eight, so whatever is refusing there is not the length.
     *
     * **The rule the test was written for has moved to `testExactlyFitsIsAdmittedAndOneMoreIsNot`,**
     * which finds a berth the railway will offer and asserts the boundary there. What is left here is
     * the observation itself, because it is the kind of thing that is worth noticing if it changes:
     * TunnelLongPark is authored as a place trains must turn, and the reasons a berth like that
     * refuses a particular locomotive are the subject of MT-309 rather than of the length guard.
     *
     * If this ever starts passing, TunnelLongPark has become reachable for this train and somebody
     * should find out which rule stopped applying.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTunnelLongParkIsRefusedForReasonsOtherThanLength() throws Exception
    {
        Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "75 407 DB is not on this railway any more");

        Integer was = train.getTrainLength();

        try
        {
            train.setTrainLength(4);

            // AMPLE ROOM EVERYWHERE.  If length were the reason, this would admit it.
            measureEverythingAs(8);

            assertFalse(offers(train, "TunnelLongPark"),
                "TunnelLongPark now admits " + train.getName() + " with every tile measured at eight."
                + " It refused at two and at eight when this was measured, so something other than"
                + " the length was refusing - find out which rule stopped applying before deleting"
                + " this line");
        }
        finally
        {
            train.setTrainLength(was == null ? 0 : was);
        }
    }
    /**
     * Whether this train is offered a destination whose name starts with the given text.
     *
     * @param loc the train
     * @param destination the name to look for
     * @return true when it is offered
     * @throws Exception on a failure to build
     */
    private boolean offers(Locomotive loc, String destination) throws Exception
    {
        Layout built = rebuild();

        for (List<Edge> path : built.getPossiblePaths(loc, true))
        {
            if (path.isEmpty()) continue;

            if (path.get(path.size() - 1).getEnd().getName().startsWith(destination)) return true;
        }

        return false;
    }

    /**
     * EXACTLY FITS IS ADMITTED, and one unit more is not - on a berth this railway really offers.
     *
     * Adam: **"if we made both lengths 4, would it then be accepted?  That should be the minimum
     * acceptable length."** The guard agrees in code - `loc.getTrainLength() > room` refuses, so four
     * into four is admitted - and this is the behavioural half of it.
     *
     * **Three things this test has now stopped naming**, each of which went stale in turn:
     *
     * 1. The TILES to measure. It set `1 - Main:10,9` and `10,10`, which were the approach to
     *    TunnelLongPark on a railway of eighteen edges - the graph this suite built before its
     *    fixture wired the accessories. On the real reduction those numbers landed on track the guard
     *    never counts.
     * 2. The ROOM those measurements produce. `roomAtTheEnd` stops at the last switch, so how many of
     *    the units written reach the count depends on where the switches are. It is read back now.
     * 3. The BERTH itself. TunnelLongPark refuses this locomotive at eight units of room as firmly as
     *    at two, so whatever is refusing there is not the length - and a boundary test that cannot
     *    get its train admitted at any measurement is testing nothing.
     *
     * So it searches for a destination the railway will offer once it is measured wide, and asserts
     * the boundary there. What is being tested is the RULE, and the rule is not about one berth.
     *
     * MUTATION: `>=` in place of `>` at the guard fails the first assertion.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testExactlyFitsIsAdmittedAndOneMoreIsNot() throws Exception
    {
        Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "75 407 DB is not on this railway any more");

        Integer was = train.getTrainLength();

        try
        {
            // A SHORT TRAIN AND A WIDE RAILWAY, so that whatever is offered is offered because it
            // fits rather than because nothing was measured.
            measureEverythingAs(8);

            train.setTrainLength(1);

            // A BERTH WHERE THE GUARD ACTUALLY BINDS.
            //
            // Being offered is not enough: the rule is `measuredRoomAtTheBerth`, and it declines to
            // judge a run in nobody has measured - so on most of a real railway there is no refusal
            // to sit at the edge of.  (It applied only where a train had to BACK IN until MT-262,
            // which is a narrower version of the same point.)
            // The first version of this search took the first berth with room and found
            // `BottomMainPost (northbound)` at 32 units, which admitted a 33-unit train quite happily -
            // the boundary assertion passed and meant nothing. The control caught it, which is what a
            // control is for.
            //
            // So the test for "does the guard bind here" is the refusal itself: one unit too long must
            // be refused. Then, and only then, is exactly-fits worth asserting.
            String berth = null;
            int room = 0;

            for (String candidate : offeredDestinations(train))
            {
                int here = roomTheGuardSees(candidate);

                if (here <= 1) continue;

                train.setTrainLength(here + 1);

                if (offers(train, candidate)) continue;

                berth = candidate;
                room = here;

                break;
            }

            assertNotNull(berth,
                "no destination on this railway refuses a train one unit too long for it, so the length"
                + " guard binds nowhere and there is no boundary to test. That is either a railway with"
                + " no reversing berths measured, or a guard that has stopped refusing");

            // EXACTLY FITS.
            train.setTrainLength(room);

            assertTrue(offers(train, berth),
                room + " units of room at " + berth + " REFUSED a " + room + "-unit train."
                + " Exactly-fits must be admitted or every berth measured to the train that lives in"
                + " it becomes unusable - Adam: \"that should be the minimum acceptable length\"");

            // The one-too-long refusal is what SELECTED this berth, so it is already established -
            // asserted again here so that the pair reads as a boundary rather than as a search.
            train.setTrainLength(room + 1);

            assertFalse(offers(train, berth),
                "a train one unit longer than the " + room + " units at " + berth + " was still"
                + " admitted, so the guard is not measuring and the boundary above means nothing");
        }
        finally
        {
            train.setTrainLength(was == null ? 0 : was);
        }
    }

    /**
     * Every destination the railway will currently offer this locomotive, by name.
     *
     * @param loc the train
     * @return the destination names
     * @throws Exception on a failure to build
     */
    private java.util.List<String> offeredDestinations(Locomotive loc) throws Exception
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (List<Edge> path : rebuild().getPossiblePaths(loc, true))
        {
            if (!path.isEmpty()) out.add(path.get(path.size() - 1).getEnd().getName());
        }

        return out;
    }

    /**
     * The room the guard will actually count on the way into a station.
     *
     * **Read from the built railway rather than worked out here.** `roomAtTheEnd` is the part of the
     * approach AFTER the last switch, because a train reversing in can only use the stretch it can
     * see - and which tiles fall inside that stretch depends on where the switches are. A test that
     * decides for itself how many units it has just measured is a test that has to know the geometry,
     * and this one used to: it named two tiles, they stopped being the run in when the fixture began
     * building the whole railway, and both halves of the boundary read as refusals.
     *
     * @param station the destination
     * @return the units the guard counts, or -1 when nothing leads there
     * @throws Exception on a failure to build
     */
    private int roomTheGuardSees(String station) throws Exception
    {
        Layout built = rebuild();

        int most = -1;

        for (Edge edge : built.getEdges())
        {
            if (edge.getEnd() == null) continue;

            if (!station.equals(session.getStationIndex().baseNameOf(edge.getEnd().getName()))
                && !station.equals(edge.getEnd().getName()))
            {
                continue;
            }

            most = Math.max(most, edge.getRoomAtTheEnd());
        }

        return most;
    }

    /**
     * Sets the two tiles that bind the run into TunnelLongPark and asks whether it is offered.
     *
     * @param first 1 - Main:10,9
     * @param second 1 - Main:10,10
     * @param train the locomotive
     * @return whether TunnelLongPark is offered
     * @throws Exception on a failure to build
     */
    private boolean roomOf(int first, int second, Locomotive train) throws Exception
    {
        java.util.List<TileKey> runIn = theRunInto("TunnelLongPark");

        assertTrue(runIn.size() >= 2,
            "the run into TunnelLongPark is " + runIn.size() + " tiles, so a two-number measurement"
            + " cannot be spread across it and every case below would be measuring the same thing:"
            + " " + runIn);

        // FOUND, NOT NAMED (2026-09-08).  This used to set `1 - Main:10,9` and `10,10` by hand, which
        // were the run into TunnelLongPark on the railway this suite used to build - eighteen edges,
        // because the fixture parsed its pages without wiring their accessories. On the real reduction
        // the run is elsewhere, so the two numbers were being written onto track the guard never
        // consulted, and both halves of the boundary read as refusals.
        //
        // The LAST two tiles of the run, because the guard measures backwards from the berth and stops
        // at the last switch: those are the ones inside the stretch it counts.
        session.setTileLength(runIn.get(runIn.size() - 2), first);
        session.setTileLength(runIn.get(runIn.size() - 1), second);

        return offers(train, "TunnelLongPark");
    }

    /**
     * The tiles a train crosses on its way into a station, in the order it crosses them.
     *
     * Read off the reduction rather than written down, so it follows the railway instead of a snapshot
     * of it: this test hard-coded two tiles and they stopped being the answer the moment the fixture
     * started building the whole layout.
     *
     * @param station the destination
     * @return its approach tiles, empty when nothing leads there
     */
    private java.util.List<TileKey> theRunInto(String station)
    {
        TileKey berth = session.getStationIndex().squareOf(station);

        if (berth == null) return java.util.Collections.emptyList();

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : session.getReducer()
            .getEdges())
        {
            if (!berth.equals(edge.getEnd())) continue;

            java.util.List<TileKey> tiles = new java.util.ArrayList<>();

            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) tiles.add(step.getTile());
            }

            if (tiles.size() >= 2) return tiles;
        }

        return java.util.Collections.emptyList();
    }

    /**
     * The railway as Adam describes it: two numbered segments, both one unit - and the route refused.
     *
     * **"There are only two numbered track segments, both of length 1.  Direction should not matter."**
     *
     * His saved setup does not say that, and the difference is mine.  When his layout was damaged I
     * restored `tileLengths` from the last commit, which put SIX older measurements back - including
     * `1 - Main:1,10 = 4` and `1 - Main:0,11 = 4`.  Those two are the 8 the guard was reading, and
     * they are the reason a four-unit train was admitted to a berth he had measured at one.
     *
     * So this clears everything and sets only the two he named.  If it refuses, the guard is correct
     * and the disagreement was entirely stale data in his file.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTwoOneUnitSegmentsRefuseTheFourUnitTrain() throws Exception
    {
        measureEverythingAs(0);

        // The two he named, and nothing else.
        session.setTileLength(new TileKey(MAIN, 19, 12), 1);
        session.setTileLength(new TileKey(MAIN, 22, 7), 1);

        Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "75 407 DB is not on this railway any more");

        // PUT BACK, like the other two sites that borrow this engine.  This one kept the length.
        Integer was = train.getTrainLength();

        try
        {
            train.setTrainLength(4);

            assertFalse(offers(train, "TunnelLongPark"),
                "with only the two one-unit segments he measured, a four-unit train is still offered"
                + " TunnelLongPark - so the guard is wrong and the stale lengths were not the cause");
        }
        finally
        {
            train.setTrainLength(was == null ? 0 : was);
        }
    }

    /**
     * BottomMainC is blocked while the 2-8-4 lies across the switch behind BottomMainB.
     *
     * Adam, 2026-09-07: **"I turned the 2-8-4 train around, so it is now facing east.  Assume it
     * arrived from the west.  We set arrivedFrom for 2-8-4 at BottomMainB to west.  Now, you should be
     * able to block the switch that leads to BottomMainC."**
     *
     * **Why facing could not have told us this.**  A train faces the way it will leave; its tail is
     * behind it, and once it has been turned round the two point the same way.  Before `arrivedFrom`
     * the walk looked up the track this train is about to depart along - which is clear - and reported
     * nothing blocked.  The rail it is actually lying across is the one it came in by, and only the
     * train knows that.
     *
     * The tail has to be longer than the run into the platform for any of this to matter, so the lead
     * in is measured at one and the train at four: three units of it are past the switch.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testBottomMainCIsBlockedByTheTailAtBottomMainB() throws Exception
    {
        measureEverythingAs(0);

        // One unit of platform road, so a four-unit train is three units past the switch.
        session.setTileLength(new TileKey(MAIN, 19, 13), 1);

        Layout built = rebuild();

        Locomotive engine = model.getLocByName("2-8-4 3505 SP");

        assertNotNull(engine, "2-8-4 3505 SP is not on this railway any more");

        // BORROWED, AND GIVEN BACK in tearDown.  This site kept the length; the rest of the method
        // moves the train about, so a try/finally round it would have to be round the whole claim.
        borrowTheLengthOf(engine);

        engine.setTrainLength(4);

        // PUT THERE, NOT FOUND THERE.
        //
        // This used to walk the Points for whichever one had the 2-8-4 on it and then assert that it
        // was BottomMainB.  That is an assertion about where Adam last left a train, and it went red
        // the first time he shunted it - it is at BottomMainA today.  What the test is about is a tail
        // lying across the throat behind a platform, and the platform is named here.
        Point platform = placeAt(built, engine, new TileKey(MAIN, 20, 13));

        assertNotNull(platform, "there is no platform at 1 - Main:20,13 to put the 2-8-4 on");

        assertEquals(session.getStationIndex().baseNameOf(platform.getName()), "BottomMainB",
            "1 - Main:20,13 is called " + platform.getName() + " on this railway rather than"
            + " BottomMainB, so the throat this test walks is not the one it was written about");

        // NOTHING RECORDED YET: the walk cannot tell which way the tail lies, and says so by
        // blocking nothing.  This is the control - without it the assertion below could pass on a
        // rule that blocks the whole railway.
        platform.setArrivedFrom(null);

        assertTrue(built.edgesCoveredByStandingTrains().isEmpty(),
            "track is being blocked with no arrival side recorded, so the rule is guessing which way"
            + " the tail lies rather than being told");

        // AND NOW HIS CASE: it came in from the west.
        platform.setArrivedFrom("W");

        java.util.Map<Edge, Locomotive> covered = built.edgesCoveredByStandingTrains();

        StringBuilder sides = new StringBuilder();
        for (Edge e : built.getNeighborsAndIncoming(platform))
        {
            Point other = e.getStart() == platform ? e.getEnd() : e.getStart();
            sides.append(" ").append(built.sideTowards(platform, other)).append("->")
                 .append(other.getName()).append("(len=").append(e.getLength()).append(")");
        }

        assertFalse(covered.isEmpty(),
            "with the arrival side recorded as west, the tail covers nothing. Copy " + platform.getName()
            + " offers:" + sides);

        StringBuilder what = new StringBuilder();

        for (Edge e : covered.keySet()) what.append(" ").append(e.getName());

        boolean touchesTheThroat = what.toString().contains("BottomMainBCPre")
            || what.toString().contains("BottomMainPost");

        assertTrue(touchesTheThroat,
            "the tail covers" + what + ", none of which is the track behind BottomMainB - so whatever"
            + " it found is not the rail the 2-8-4 is lying across");
    }

    // ---------------------------------------------------------------- the setups Adam asked for

    /**
     * Sets EVERY tile the reduction knows to one length, which is what makes the guard testable here.
     *
     * **Every page, not just the main one, since 2026-09-08.** It filtered to `1 - Main` and its name
     * still said "everything", which was true of the railway this suite used to build: the fixture
     * parsed its pages without wiring their accessories, so the graph was eighteen edges and never left
     * that page. Wired, it is the whole railway - and a berth on another page arrived UNMEASURED, which
     * the guard cannot judge, so a nine-unit train was offered `LowerFront` on a layout this method had
     * just declared measured at one unit throughout.
     *
     * Two of the four failures in this class were that, and they are the good kind: the test said
     * something about the whole railway while looking at a fifth of it.
     *
     * @param units the length to give every tile
     */
    private void measureEverythingAs(int units)
    {
        // COLLECTED FIRST, THEN WRITTEN.  setTileLength rebuilds the reducer, so walking its own
        // collections while writing to it re-derives the graph under the iterator - which quietly
        // dropped it from 50-odd edges to 18, and every route on the railway with them.  The first
        // version of this test did exactly that and then reported that nothing was reachable.
        Set<TileKey> everyTile = new LinkedHashSet<>();

        everyTile.addAll(session.getReducer().getPoints().keySet());

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : session.getReducer()
            .getEdges())
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) everyTile.add(step.getTile());
            }
        }

        for (TileKey tile : everyTile) session.setTileLength(tile, units);
    }

    /**
     * Puts a locomotive on a named square, and answers the copy it went onto.
     *
     * A square is several Points - one per side a train can arrive by - and only some of them are
     * places a train may stand, so this takes the first copy that is a destination.  Every other copy
     * of the square is cleared first: a locomotive is one train, and leaving it recorded on two copies
     * of one platform would block twice as much track as it can lie across.
     *
     * @param built the running layout
     * @param engine the train
     * @param square the platform to put it on
     * @return the copy it is standing on, or null when the square has no copy that can hold it
     */
    private Point placeAt(Layout built, Locomotive engine, TileKey square)
    {
        // OFF WHEREVER IT WAS FIRST.  The snapshot has it standing somewhere already, and moveLocomotive
        // fills the target without emptying the old square when the two are different Points.
        for (Point p : built.getPoints())
        {
            if (engine.equals(p.getCurrentLocomotive())) built.moveLocomotive(null, p.getName(), true);
        }

        for (String name : session.getStationIndex().pointNamesAt(square))
        {
            Point copy = built.getPoint(name);

            if (copy == null || !copy.isDestination()) continue;

            built.moveLocomotive(engine.getName(), copy.getName(), false);

            return copy;
        }

        return null;
    }

    private Layout rebuild() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build");

        return built;
    }

    /**
     * The lengths this class has changed on Adam's own locomotives, and what they were.
     *
     * `MarklinControlStation.init` opens his real locomotive database rather than an empty one, and
     * the layout sandbox does not freeze it - it copies the layout folder and nothing else. So a
     * length set on a borrowed train is a change to his railway that outlives the run.
     *
     * A map rather than a field per site, because what gets borrowed here is "whatever is standing",
     * and how many that is depends on the snapshot.
     */
    private static final java.util.Map<org.traincontrol.base.Locomotive, Integer> LENGTHS_WE_CHANGED =
        new java.util.LinkedHashMap<>();

    /**
     * Remembers a borrowed train's length before this class writes over it.
     *
     * FIRST VALUE WINS: a train measured twice in one run must go back to what it was before the
     * FIRST change, not to what the previous claim left on it.
     *
     * @param loc the borrowed train
     */
    private static void borrowTheLengthOf(org.traincontrol.base.Locomotive loc)
    {
        if (loc == null || LENGTHS_WE_CHANGED.containsKey(loc)) return;

        LENGTHS_WE_CHANGED.put(loc, loc.getTrainLength());
    }

    /**
     * Puts every borrowed length back.
     *
     * Each on its own, so one failure does not keep the others borrowed. A length left behind is
     * silent: nothing on screen says a train is measured at fifty, and the next thing to read it is
     * the anti-collision rule.
     */
    private static void giveTheLengthsBack()
    {
        for (java.util.Map.Entry<org.traincontrol.base.Locomotive, Integer> was
             : LENGTHS_WE_CHANGED.entrySet())
        {
            try
            {
                was.getKey().setTrainLength(was.getValue() == null ? 0 : was.getValue());
            }
            catch (Exception cannotPutItBack)
            {
            }
        }

        LENGTHS_WE_CHANGED.clear();
    }


    /**
     * The berths this train is offered that it would have to come to rest inside - terminus or
     * reversing, which is where the room rule applies.
     *
     * @param built the railway
     * @param loc the train
     * @return the names of those destinations
     */
    private Set<String> berthsOfferedTo(Layout built, Locomotive loc)
    {
        Set<String> out = new LinkedHashSet<>();

        List<List<Edge>> paths = built.getPossiblePaths(loc, true);

        if (paths == null) return out;

        for (List<Edge> path : paths)
        {
            if (path.isEmpty()) continue;

            Point end = path.get(path.size() - 1).getEnd();

            if (end.isTerminus() || end.isReversing()) out.add(end.getName());
        }

        return out;
    }
}
