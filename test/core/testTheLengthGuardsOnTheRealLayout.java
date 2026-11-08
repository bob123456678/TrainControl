package core;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
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
 * **ON THE FROZEN RAILWAY, AND WITH THE TRAIN PUT WHERE IT IS WANTED**, in a `LayoutSandbox` copy of
 * `test/layouts/live-snapshot` (OB-111): where Adam's trains stand is not the subject.
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
     * The train this class asks with, made here (Adam: *"generate trains programmatically in the tests, and give them
     * semantic names."*); deleted in `tearDown`, since `init` opens his real locomotive database.
     */
    private static final String OUR_TRAIN = "length guard probe";

    /**
     * Its address, chosen so a stray left by a crash is identifiable: addresses are not unique, so it only has to be
     * nobody else's.
     */
    private static final int OUR_ADDRESS = 61;

    private static MarklinLocomotive ourTrain;

    /**
     * The square this class starts its train from: `1 - Main:20,13` is BottomMainB, the one platform whose measured
     * approach the snapshot carries a length on.
     */
    private static final TileKey BOTTOM_MAIN_B = new TileKey(MAIN, 20, 13);

    /** BottomMainA, where the claim about his two one-unit segments stands 75 407 DB. */
    private static final TileKey BOTTOM_MAIN_A = new TileKey(MAIN, 20, 12);

    /**
     * The tile Adam's ruling turns on: `1 - Main:22,7`, the last unswitched square before BottomMainPost and one of
     * only three the snapshot measures.  Adam: *"I see no tracks with a defined length except for 22,7."*
     */
    private static final TileKey TWENTY_TWO_SEVEN = new TileKey(MAIN, 22, 7);

    @BeforeClass
    public static void setUp() throws Exception
    {
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111): `live-snapshot` is the same railway with the clock
        // stopped, taken from `git show HEAD:` so it cannot move under a test.  The train this class needs is placed in
        // code below, not read off the fixture.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

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

        // AND NO STATION'S SIZE.  Every station has a maximum train length, and `whyTooLongForThisRoute` asks that
        // first, so a nine-unit train would be refused BottomMainPost, which holds 3, before any track was looked at,
        // and every claim below would be a claim about the number typed on the station.  This class is about the TRACK:
        // the lengths it sets are the only thing that may decide.
        session.clearEveryMaxTrainLength();

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
     * Borrowed from Adam's railway by NAME (`75 407 DB`, `2-8-4 3505 SP`, each behind an `assertNotNull`); the lengths
     * are put back by `giveTheLengthsBack`.  `roomTheGuardSees` can report less than the guard uses (MT-333).
     */

    /**
     * ONE TILE, THREE MEASUREMENTS: unmeasured is offered, 22,7 at one refuses a nine-unit train, at nine it is
     * offered.
     *
     * Adam: **"Make sure you add a test case to confirm this pathing IS possible if no
     * lengths are set anywhere, and if those lengths are set to 9."**  `1 - Main:22,7` is the last unswitched square
     * before BottomMainPost, so its measure is the room a train has coming to rest there.
     *
     * MUTATION: `>=` for `>` at `Layout.whyTooLongForThisRoute` fails the third case; widening the comparison by any
     * constant fails the second.
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
     * Turn the measurements up and the same long train becomes welcome again.  The whole-railway form of the control,
     * but **not the same claim**: measuring every TILE at one unit does not make every SECTION one unit (the run into
     * RampDown is eighteen tiles), so this asserts it of the berth whose approach really is short;
     * `core.testALongTrainIsOfferedNothingOnAOneUnitRailway` makes the every-section claim on `single-switch`.
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
     * A nine-unit train is offered RampDown, past the one unit at 22,7 that it only drives through (MT-333): a square a
     * train only passes is not judged for room.  The route comes from `bfs`, and edge counts are asserted rather than
     * names, since the road taken varies with what is measured.
     *
     * MUTATION: removing `if (!comesToRest) continue;` from `Layout.whyTooLongForThisRoute` refuses RampDown and
     * fails the offer assertion.
     */
    @Test
    public void testRampDownIsOfferedPastTheOneUnitItOnlyPasses() throws Exception
    {
        ourTrain.setTrainLength(9);

        measureOnlyTheSnapshotsThreeAs(1);

        Layout built = rebuild();

        Point from = onlyOurTrainAtBottomMainB(built);

        assertNotNull(from,
            "the nine-unit train is not standing alone at BottomMainB, so what follows is about some"
            + " other journey");

        assertTrue(reaches(destinationsFromBottomMainB(), "RampDown"),
            "RampDown is refused to a nine-unit train standing at BottomMainB with 22,7 measured at one unit."
            + "  The route only PASSES BottomMainPost, where that unit is, and stops at RampDown, which nothing"
            + " bounds.  Adam, MT-333, 2026-09-14: 'this switch blocking should only affect berthes'");

        // THE ROUTE IS STILL THERE, and still has the shape the reasoning above describes - it is the
        // LENGTH RULE that decides it, not the graph having changed underneath this test.
        List<Edge> route = null;

        for (Point candidate : built.getPoints())
        {
            if (!candidate.getName().startsWith("RampDown")) continue;

            route = built.bfs(from, candidate, new LinkedList<List<Edge>>());

            if (route != null) break;
        }

        assertNotNull(route,
            "there is no route at all from BottomMainB to RampDown any more, so this test is recording"
            + " an offer that the geometry makes moot and the reasoning above is about track that no"
            + " longer connects");

        assertEquals(route.get(0).getEnd().getName().split(" ")[0], "BottomMainPost",
            "the route from BottomMainB to RampDown no longer starts by running to BottomMainPost, so"
            + " the tile at 22,7 is somewhere else on it and the reasoning above does not hold. It"
            + " runs: " + namesOf(route));

        assertEquals(route.get(0).getRoomAtTheEnd(), 1,
            "the first edge of the route measures " + route.get(0).getRoomAtTheEnd() + " units after"
            + " its last switch rather than the one unit at 22,7, so that tile is not where this test"
            + " believes it is");

        // AND THE BERTH'S OWN APPROACH STILL HAS NOTHING TO BOUND IT WITH, which is what makes this a
        // test of the route rather than of the destination.
        Edge last = route.get(route.size() - 1);

        assertFalse(last.crossesASwitch(),
            "the last edge into RampDown crosses a switch now, so it DOES bound where the train may"
            + " come to rest and the offer above is a question about the berth rather than about 22,7."
            + " That is a different railway from the one this test was measured on");

        assertEquals(last.getLength(), 0,
            "the last edge into RampDown is measured at " + last.getLength() + " units, so the snapshot"
            + " has grown a measurement on it and the \"then nothing\" in Adam's ruling is no longer"
            + " true of it");

        // SWITCHES IN BETWEEN: this is why the berth-only walk could never reach 22,7.
        int switchesBetween = 0;

        for (int i = 1; i < route.size(); i++)
        {
            if (route.get(i).crossesASwitch()) switchesBetween++;
        }

        assertTrue(switchesBetween > 0,
            "there is no switch anywhere between BottomMainPost and RampDown, so the berth walk would"
            + " have run all the way back and 22,7 would have bound the berth even under the old rule."
            + " It runs: " + namesOf(route));

        assertNull(Layout.measuredRoomAtTheEndOf(route, ourTrain),
            "the berth walk now measures " + Layout.measuredRoomAtTheEndOf(route, ourTrain) + " units"
            + " at RampDown where it used to decline to judge, so the offer above depends on the"
            + " destination and this test would no longer be about the square the train only passes");

        // AND THE RULE ITSELF GIVES NO REASON, so the offer above is the room rule's answer and not luck.
        assertNull(Layout.whyTooLongForThisRoute(route, ourTrain),
            "the room rule refuses the route to RampDown: '" + Layout.whyTooLongForThisRoute(route, ourTrain)
            + "' - it is still judging BottomMainPost, a square the train only passes");

        // AND ADAM'S TWO CASES: unmeasured and at nine, the route is offered again.
        measureOnlyTheSnapshotsThreeAs(0);

        assertTrue(reaches(destinationsFromBottomMainB(), "RampDown"),
            "with no lengths set anywhere, RampDown is not offered - Adam asked for this case by name,"
            + " and his ruling of 2026-09-09 is explicit that the route rule applies \"only if lengths"
            + " are specified\"");

        measureOnlyTheSnapshotsThreeAs(9);

        assertTrue(reaches(destinationsFromBottomMainB(), "RampDown"),
            "with the snapshot's three tiles measured at nine, RampDown is not offered - Adam asked for"
            + " this case by name, and nine units of room holds a nine-unit train");
    }

    /**
     * Clears every measurement and gives the three tiles `live-snapshot` measures (`19,12`, `14,13`, `22,7`) the same
     * length.
     */
    private void measureOnlyTheSnapshotsThreeAs(int units)
    {
        measureEverythingAs(0);

        session.setTileLength(new TileKey(MAIN, 19, 12), units);
        session.setTileLength(new TileKey(MAIN, 14, 13), units);
        session.setTileLength(TWENTY_TWO_SEVEN, units);
    }

    /**
     * Stands this class's train at BottomMainB, alone so that only the measurements decide, and returns the
     * destinations offered.
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

    /** The route the railway offers this train to a named station, or null when it offers none. */
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
     * Whether any offered name is a copy of the station: it is emitted as several Points (one per arrival side, plus a
     * turning copy), so `BottomMainPost` is offered as `BottomMainPost (northbound)` and the bare name never matches.
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
     * TunnelLongPark refuses this train at every measurement (two units of room or eight), so the refusal is not the
     *
     * length: it is authored as a place trains must turn (MT-309).  If this ever passes, find the rule that stopped
     * applying.
     *
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
     * EXACTLY FITS IS ADMITTED, and one unit more is not, on a berth this railway really offers.
     *
     * Adam: **"if we made both lengths 4, would it then be accepted?  That should be the minimum
     * The guard agrees in code (`>` refuses, so four into four is admitted); this is the behavioral half.
     *
     * It names no tile, room or berth, each of which goes stale: it searches for a destination the railway offers once
     * measured wide, reads the room back, and asserts the boundary there.
     *
     * MUTATION: `>=` in place of `>` at the guard leaves no berth where exactly-fits is offered.
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

            // A BERTH WHERE THE GUARD ACTUALLY BINDS.  Being offered is not enough: `measuredRoomAtTheEndOf` declines
            // to judge a run in nobody has measured, so on most of the railway there is no refusal to sit at the edge
            // of.  So the test for "does the guard bind here" is the refusal itself: one unit too long must be refused.
            // Then, and only then, is exactly-fits worth asserting.
            String berth = null;
            int room = 0;

            for (String candidate : offeredDestinations(train))
            {
                // A PARKING BERTH - Can Be Chosen In Full Autonomy off - since FR-087 (MT-431): the room rule binds at
                // a berth, so a berth is taken only where exactly-fits is offered too.  What the search cannot supply
                // is that the boundary sits at the room the guard is measured to see.
                Point asked = rebuild().getPoint(candidate);

                if (asked == null || asked.isAutoDestination()) continue;

                int here = roomTheGuardSees(train, candidate);

                if (here <= 1) continue;

                train.setTrainLength(here + 1);

                if (offers(train, candidate)) continue;

                train.setTrainLength(here);

                if (!offers(train, candidate)) continue;

                berth = candidate;
                room = here;

                break;
            }

            assertNotNull(berth,
                "no parking berth on this railway admits a train exactly as long as the room the guard"
                + " measures there and refuses one unit more. That is a railway with no berth measured, a"
                + " guard that has stopped refusing, or one that refuses exactly-fits - `>=` where `>` is");

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

    /** Every destination the railway will currently offer this locomotive, by name. */
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
     * The largest train the guard lets into a station from where this one stands: tightest measured stretch where the
     * train comes to rest on the best route, largest across routes (MT-333).
     *
     * @return the units the guard counts, or -1 when nothing measurable leads there
     */
    private int roomTheGuardSees(Locomotive loc, String station) throws Exception
    {
        Layout built = rebuild();

        Point to = built.getPoint(station);

        if (to == null) return -1;

        Point from = null;

        for (Point point : built.getPoints())
        {
            if (loc.equals(point.getCurrentLocomotive())) from = point;
        }

        if (from == null) return -1;

        int best = -1;

        List<List<Edge>> seen = new LinkedList<>();

        while (true)
        {
            List<Edge> path = built.bfs(from, to, seen);

            if (path == null) break;

            seen.add(path);

            int tightest = Integer.MAX_VALUE;

            for (int i = 0; i < path.size(); i++)
            {
                // WHERE THE TRAIN COMES TO REST, as the guard asks (MT-333): the destination and a
                // square it turns at, not one it only passes.
                if (i < path.size() - 1 && !path.get(i).getEnd().isReversing()) continue;

                Integer room = Layout.measuredRoomAtTheEndOf(path.subList(0, i + 1), loc);

                if (room != null) tightest = Math.min(tightest, room);
            }

            if (tightest != Integer.MAX_VALUE) best = Math.max(best, tightest);
        }

        return best;
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

        // AND THEY ARE ON TRACK THE GUARD COUNTS.  Two named tiles are a measurement only while they lie on a stretch
        // the room walk reaches, else both halves of a boundary read as refusals.  Asked of the walk, not the geometry:
        // `measuredRoomAtTheEndOf` is what the guard counts with, so a small answer on the route to this berth means
        // the two lengths above landed where meant, and a large one, or none, that they did not.

        Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "75 407 DB is not on this railway any more");

        // PUT BACK, like the other two sites that borrow this engine.  This one kept the length.
        Integer was = train.getTrainLength();

        // ALONE, AND STANDING WHERE THIS CLAIM SAYS (FR-136): at BottomMainA, facing east, in from the west.  Where his
        // trains stand is working state, and a train standing on its way blocks a route this claim needs.  Put back after.
        Map<TileKey, Object[]> standing = liftEveryTrain();

        session.placeLocomotive(BOTTOM_MAIN_A, "75 407 DB");
        session.setPointProperty(BOTTOM_MAIN_A, "facing", "E");
        session.setPointProperty(BOTTOM_MAIN_A, "arrivedFrom", "W");

        try
        {
            train.setTrainLength(4);

            // THE FLOOR.  `offers` answers false when the railway offers this train nothing
            // at all - an unplaced locomotive, a configuration that drops its Point - so without this
            // the refusal below is satisfied by a railway that has closed down entirely.
            assertFalse(offeredDestinations(train).isEmpty(),
                "this railway offers 75 407 DB nowhere at all, so the refusal below says nothing"
                + " about TunnelLongPark - it says the fixture is broken");

            assertFalse(offers(train, "TunnelLongPark"),
                "with only the two one-unit segments he measured, a four-unit train is still offered"
                + " TunnelLongPark - so the guard is wrong and the stale lengths were not the cause");

            // AND THE LENGTH IS WHAT REFUSES IT, which is the claim this test's NAME makes.
            //
            // `testTunnelLongParkIsRefusedForReasonsOtherThanLength` asserts this berth refuses this locomotive with
            // every tile measured at eight, so something other than length closes it and the refusal above holds with
            // the room rule switched off.  So the rule is asked directly, of a route to that berth.
            List<Edge> route = theRouteOverTheTrackTo("TunnelLongPark");

            assertNotNull(route,
                "there is no route at all from where 75 407 DB stands to TunnelLongPark, so the room"
                + " rule has nothing to judge and the claim below is about track that does not"
                + " connect");

            // WHY THE TRACK AND NOT THE RAILWAY'S SEARCH (OB-229), said as a precondition so that a wrong reading
            // of it shows: the route over the track passes a terminus, which is why `bfs` no longer returns it.
            assertTrue(passesATerminus(route),
                "precondition: the route over the track to TunnelLongPark passes no terminus, so the railway's own"
                + " search should still find it - this claim's reason for searching the track itself is wrong");

            Integer room = Layout.measuredRoomAtTheEndOf(route, train);

            assertNotNull(room,
                "the room walk declines to judge the route to TunnelLongPark, so neither of the two"
                + " lengths set above is on a stretch it counts - which is the staleness this file's"
                + " own note at the RampDown claim is about");

            assertTrue(room < 4,
                "the walk measures " + room + " units on the way to TunnelLongPark with only Adam's"
                + " two one-unit tiles set, so it is counting track those two numbers are not on");

            String why = Layout.whyTooLongForThisRoute(route, train);

            assertNotNull(why,
                "the room rule admits a four-unit train over the two one-unit segments Adam named, so"
                + " the refusal above is somebody else's and this test does not test the length rule"
                + " at all - which is what it was doing until E8-B4 found it");

            assertTrue(why.contains("4"),
                "the refusal is \"" + why + "\" and does not carry the train's length, so it is not"
                + " the room rule's sentence");
        }
        finally
        {
            train.setTrainLength(was == null ? 0 : was);

            putTheTrainsBack(standing);
        }
    }

    /**
     * A route from where the borrowed engine stands to a named station, over the track alone: `Layout.bfs` extends no
     * route through a terminus that is not its end (Adam: *"Search past termini"*), and the shortest route to
     * TunnelLongPark passes one.
     * @return the edges in order, or null when nothing connects
     */
    private List<Edge> theRouteOverTheTrackTo(String station) throws Exception
    {
        Layout built = rebuild();

        Locomotive train = model.getLocByName("75 407 DB");

        Point from = null;

        for (Point point : built.getPoints())
        {
            if (train != null && train.equals(point.getCurrentLocomotive())) from = point;
        }

        if (from == null) return null;

        for (Point to : built.getPoints())
        {
            if (!to.getName().startsWith(station)) continue;

            List<Edge> route = aRouteOverTheTrackAlone(built, from, to);

            if (route != null) return route;
        }

        return null;
    }

    /** Whether some edge after the first starts at a terminus - the route `isPathClear` refuses on that ground. */
    private static boolean passesATerminus(List<Edge> route)
    {
        for (int i = 1; i < route.size(); i++)
        {
            if (route.get(i).getStart().isTerminus()) return true;
        }

        return false;
    }

    /**
     * The shortest route over the track, through anything: `Layout.bfs` without its exclusions and its check that the
     * end is a destination.  Null when none.
     */
    @SuppressWarnings("unchecked")
    private static List<Edge> aRouteOverTheTrackAlone(Layout built, Point start, Point end)
    {
        java.util.Set<Point> visited = new java.util.HashSet<>();
        java.util.Queue<Object[]> queue = new LinkedList<>();

        queue.add(new Object[] { start, new LinkedList<Edge>() });

        while (!queue.isEmpty())
        {
            Object[] current = queue.remove();
            Point point = (Point) current[0];
            List<Edge> path = (List<Edge>) current[1];

            visited.add(point);

            for (Edge next : built.getNeighbors(point))
            {
                if (next.getEnd().equals(end))
                {
                    List<Edge> found = new LinkedList<>(path);

                    found.add(next);

                    return found;
                }

                if (!visited.contains(next.getEnd()))
                {
                    List<Edge> longer = new LinkedList<>(path);

                    longer.add(next);
                    queue.add(new Object[] { next.getEnd(), longer });
                }
            }
        }

        return null;
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
        // Walking the Points for whichever one has the 2-8-4 on it would assert about where Adam last left a train.
        // What the test is about is a tail lying across the throat behind a platform, and the platform is named here.
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
     * **Every page, not just the main one.**  A berth on another page would arrive UNMEASURED, which the guard cannot
     * judge, so a nine-unit train would be offered `LowerFront` on a layout this method had just declared measured at
     * one unit throughout.
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
     * Puts a locomotive on a named square and returns the copy it went onto: the first that is a destination, as only
     * some copies are places a train may stand.  Every other copy is cleared first, since one train recorded on two
     * copies of a platform would block twice the track.  Null when no copy can hold it.
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

    /**
     * Takes every train off the setup, and hands back where each stood, for `putTheTrainsBack`.
     */
    private static Map<TileKey, Object[]> liftEveryTrain()
    {
        Map<TileKey, Object[]> standing = new java.util.LinkedHashMap<>();

        for (TileKey tile : session.tilesWithALocomotive())
        {
            standing.put(tile, new Object[] {session.getPointProperty(tile, "loc"), session.getPointProperty(tile, "facing"),
                session.getPointProperty(tile, "arrivedFrom"), session.getPointProperty(tile, "arrivedAlong")});
        }

        session.clearEveryPlacement();

        return standing;
    }

    /**
     * Takes every train off the setup and stands again those `liftEveryTrain` took off, as they stood.
     */
    private static void putTheTrainsBack(Map<TileKey, Object[]> standing)
    {
        session.clearEveryPlacement();

        for (Map.Entry<TileKey, Object[]> at : standing.entrySet())
        {
            session.setPointProperty(at.getKey(), "loc", at.getValue()[0]);
            session.setPointProperty(at.getKey(), "facing", at.getValue()[1]);
            session.setPointProperty(at.getKey(), "arrivedFrom", at.getValue()[2]);
            session.setPointProperty(at.getKey(), "arrivedAlong", at.getValue()[3]);
        }
    }

    private Layout rebuild() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build");

        return built;
    }

    /**
     * The lengths this class has changed on Adam's own locomotives, and what they were: `MarklinControlStation.init`
     * opens his real locomotive database, so a length set on a borrowed train outlives the run.  A map, as how many are
     * borrowed depends on the snapshot.
     */
    private static final java.util.Map<org.traincontrol.base.Locomotive, Integer> LENGTHS_WE_CHANGED =
        new java.util.LinkedHashMap<>();

    /**
     * Remembers a borrowed train's length before this class writes over it; FIRST VALUE WINS, so a train measured twice
     * goes back to its first.
     */
    private static void borrowTheLengthOf(org.traincontrol.base.Locomotive loc)
    {
        if (loc == null || LENGTHS_WE_CHANGED.containsKey(loc)) return;

        LENGTHS_WE_CHANGED.put(loc, loc.getTrainLength());
    }

    /**
     * Puts every borrowed length back, each on its own so one failure keeps no other borrowed: a length left behind is
     * silent.
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


}
