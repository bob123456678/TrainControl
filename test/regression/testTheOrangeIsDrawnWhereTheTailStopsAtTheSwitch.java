package regression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A train whose tail stops at the switch behind its platform is still drawn in orange where it lies (OB-290).
 *
 * Adam, 2026-09-24: *"in the CURRENT setup, 75 407 DB gets no orange line at bottommaina.  it did earlier"* - and, on
 * MT-543, *"With not known, there is no orange tail.  The tiles up to the switch are greyed out, as expected."*
 *
 * A train with no road into its platform - placed by hand, answered Not known, or re-stood after a restart or an edit,
 * none of which keeps the road it drove - has its tail decided by the side it came in by.  Where more than one rail
 * comes in by that side and they part behind the platform, the walk claims the squares they share, up to the switch,
 * and stops (MT-477).  It claims them as PLACES, which is what the grey is drawn from, and covers no EDGE - and the
 * orange line was drawn only from covered edges.  So the grey was right and the train itself was drawn nowhere.
 *
 * At BottomMainA on the frozen `live-snapshot`, where rails from the west part behind the platform, with this class's
 * own four-unit train (75 407 DB's length) standing there, having come in from the west.
 *
 * MUTATION: take the claimed-places drawing out of `AutonomySession.routesCoveredByStandingTrains` and the first claim
 * fails; draw every road seen on a square rather than the one they agree on, and the second does.
 *
 * @author Adam
 */
public class testTheOrangeIsDrawnWhereTheTailStopsAtTheSwitch
{
    /** BottomMainA's square. */
    private static final TileKey PLATFORM = new TileKey("1 - Main", 20, 12);

    private static final String OUR_TRAIN = "OB-290 probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    private static MarklinLocomotive train;
    private static Point platform;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        train = model.newMM2Locomotive(OUR_TRAIN, 2395);

        assertNotNull(train, "could not create this class's train");

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        // Nothing of his standing about, so everything drawn is this class's train.
        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }

        platform = theCopyWhereTheRailsFromTheWestPart();

        // AS 75 407 DB STANDS THERE after a restart: in from the west, four units, and no road it drove.
        platform.setLocomotive(train);
        platform.setArrivedFrom("W");

        train.setTrainLength(4);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (platform != null) platform.setLocomotive(null);

            if (model != null) model.deleteLoc(OUR_TRAIN);
        }
        catch (Exception alreadyGone)
        {
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The train is drawn where it lies: its own square, and the squares its tail claims behind it.
     */
    @Test
    public void testTheTrainIsDrawnWhereItsTailStops()
    {
        Set<String> claimed = claimedByOurTrain();

        assertTrue(claimed.size() >= 2, "precondition: the train claims no track behind its platform, so there is"
            + " nothing for the orange to be drawn over: " + claimed);

        for (Map.Entry<Edge, Locomotive> covered : layout.edgesCoveredByStandingTrains().entrySet())
        {
            assertFalse(covered.getValue() == train, "precondition: the tail covers the edge "
                + covered.getKey().getName() + ", so it did not stop where the rails part and this is not the case"
                + " OB-290 is about");
        }

        Map<TileKey, Set<RouteId>> orange = session.routesCoveredByStandingTrains(layout);

        assertTrue(orange.containsKey(PLATFORM) && !orange.get(PLATFORM).isEmpty(), "a four-unit train standing at"
            + " BottomMainA, in from the west with no road, is not drawn on its own square - OB-290, \"75 407 DB gets no"
            + " orange line at bottommaina\".  Claimed: " + claimed + ", orange: " + orange);

        int behind = 0;

        for (Map.Entry<TileKey, Set<RouteId>> square : orange.entrySet())
        {
            if (!square.getKey().equals(PLATFORM) && !square.getValue().isEmpty()) behind++;
        }

        assertTrue(behind >= 1, "the train is drawn on its own square and on nothing behind it, although its tail claims "
            + claimed + " - MT-543, \"there is no orange tail\".  Orange: " + orange);

        for (String place : claimed)
        {
            TileKey square = squareOf(place);

            assertTrue(square == null || orange.containsKey(square), "the tail claims " + place + " and the orange"
                + " says nothing about that square: " + orange);
        }
    }

    /**
     * Never both legs of the switch: where the rails through a square run different roads, the train's is not known.
     */
    @Test
    public void testNoSquareIsDrawnAlongTwoRoads()
    {
        // LONG ENOUGH TO REACH THE SWITCH where the rails part, which four units at BottomMainA is not.
        train.setTrainLength(12);

        try
        {
            Map<TileKey, Set<RouteId>> orange = session.routesCoveredByStandingTrains(layout);

            boolean unnamed = false;

            for (Map.Entry<TileKey, Set<RouteId>> square : orange.entrySet())
            {
                assertTrue(square.getValue().size() <= 1, "the square " + square.getKey() + " is drawn along "
                    + square.getValue() + " - the train is on one of them at most, and which is not known.  Orange: "
                    + orange);

                if (square.getValue().isEmpty()) unnamed = true;
            }

            assertTrue(unnamed, "precondition: a twelve-unit tail drawn with a road on every square, so it never reached"
                + " the switch where the rails part and this asks nothing about it.  Orange: " + orange);
        }
        finally
        {
            train.setTrainLength(4);
        }
    }

    /** The passing loop's squares that only its upper road runs over (OB-239). */
    private static final Set<TileKey> UPPER_ROAD = new LinkedHashSet<>(java.util.Arrays.asList(
        new TileKey("main", 4, 1), new TileKey("main", 5, 1), new TileKey("main", 6, 1), new TileKey("main", 7, 1)));

    /** And the squares that only its lower road runs over. */
    private static final Set<TileKey> LOWER_ROAD = new LinkedHashSet<>(java.util.Arrays.asList(
        new TileKey("main", 5, 2), new TileKey("main", 6, 2)));

    /** The passing loop's train (OB-239). */
    private static final String LOOP_TRAIN = "OB-239 probe";

    /**
     * The orange is drawn along the road the railway says the train covers, of two between one pair of sensors
     * (OB-239).
     *
     * The walk kept only the two squares at the ends of each covered edge and stepped between them along the FIRST
     * reduced edge joining the pair, either way round - so where two roads join the same two sensors it drew whichever
     * the reduction listed first, and a train on the other was drawn on track it is not on: track the railway would let
     * another train onto.  Built here, since no railway in `test/layouts` has two roads between one pair of sensors: a
     * passing loop between A and B, its upper road one-way east and its lower road one-way west, so the edge from A to
     * B runs over the upper and the edge from B to A over the lower.  A four-unit train stands at each end in turn,
     * come along the road that ends there - and whichever road the reduction lists first, one of the two is the other
     * road.
     *
     * MUTATION: take the first reduced edge joining a covered edge's two squares, either way round, as `pathBetween`
     * did, and this fails at one end or the other.
     *
     * @throws Exception from the build
     */
    @Test
    public void testTheOrangeFollowsTheRoadTheTrainCovers() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-ob239").toFile();

        try
        {
            AutonomySession loop = new AutonomySession(folder);

            loop.open(java.util.Arrays.asList(passingLoop()));
            loop.initialize("OB-239");

            TileKey westSwitch = new TileKey("main", 4, 2);
            TileKey eastSwitch = new TileKey("main", 7, 2);

            // THE UPPER ROAD EAST ONLY AND THE LOWER WEST ONLY, set at the two switches: out of the west switch's
            // branch and into the east switch's toe; and westward through both straights.
            runs(loop, westSwitch, true, Side.N);
            runs(loop, westSwitch, false, Side.W);
            runs(loop, eastSwitch, true, Side.E);
            runs(loop, eastSwitch, false, Side.W);

            // ONE UNIT A SQUARE, so a four-unit train lies on its own square and three behind it
            for (TileKey square : new ArrayList<>(loop.getGraph().getTiles().keySet())) loop.setTileLength(square, 1);

            loop.rebuild();

            TileKey a = new TileKey("main", 3, 2);
            TileKey b = new TileKey("main", 8, 2);

            assertTrue(runsOver(loop, a, b, UPPER_ROAD) && runsOver(loop, b, a, LOWER_ROAD), "precondition: the passing"
                + " loop does not run A to B over its upper road and B to A over its lower, so there is no second road"
                + " between the two sensors to draw");

            Layout built = Layout.fromJSON(loop.buildConfiguration(), model);

            assertNotNull(built, "precondition: the passing loop did not build");

            Edge toB = null;
            Edge toA = null;

            for (Edge edge : built.getEdges())
            {
                TileKey from = loop.getStationIndex().squareOf(edge.getStart().getName());
                TileKey to = loop.getStationIndex().squareOf(edge.getEnd().getName());

                if (a.equals(from) && b.equals(to)) toB = edge;
                if (b.equals(from) && a.equals(to)) toA = edge;
            }

            assertTrue(toB != null && toA != null && !toB.getPlaceIds().isEmpty() && !toA.getPlaceIds().isEmpty(),
                "precondition: the built railway (valid: " + built.isValid() + ") has no rail each way between A and"
                + " B, or no places on one");

            MarklinLocomotive probe = model.newMM2Locomotive(LOOP_TRAIN, 2319);

            assertNotNull(probe, "precondition: could not create the passing loop's train");

            try
            {
                probe.setTrainLength(4);

                for (Edge rail : new Edge[] {toB, toA})
                {
                    String road = rail == toB ? "upper" : "lower";

                    Set<TileKey> its = rail == toB ? UPPER_ROAD : LOWER_ROAD;
                    Set<TileKey> other = rail == toB ? LOWER_ROAD : UPPER_ROAD;

                    Point end = rail.getEnd();

                    end.setLocomotive(probe);
                    end.setArrivedFrom(built.entrySideOf(rail, end));
                    end.setArrivedAlong(java.util.Arrays.asList(rail));

                    try
                    {
                        assertTrue(built.edgesCoveredByStandingTrains().get(rail) == probe, "precondition: the train"
                            + " standing at the end of the " + road + " road does not cover it");

                        Map<TileKey, Set<RouteId>> orange = loop.routesCoveredByStandingTrains(built);

                        for (TileKey square : other)
                        {
                            assertFalse(orange.containsKey(square), "a train come along the " + road + " road is drawn"
                                + " on " + square + ", on the other road between the same two sensors - track the"
                                + " railway would let another train onto (OB-239).  Orange: " + orange);
                        }

                        boolean onItsOwn = false;

                        for (TileKey square : its) onItsOwn |= orange.containsKey(square);

                        assertTrue(onItsOwn, "a train come along the " + road + " road is not drawn on it at all, so"
                            + " the claim above says nothing.  Orange: " + orange);
                    }
                    finally
                    {
                        end.setLocomotive(null);
                    }
                }
            }
            finally
            {
                model.deleteLoc(LOOP_TRAIN);
            }
        }
        finally
        {
            deleteQuietly(folder);
        }
    }

    /**
     * A passing loop on a page of its own (OB-239): C 1,2 - 2,2 - A 3,2 - switch 4,2 - 5,2 - 6,2 - switch 7,2 - B 8,2 -
     * 9,2 - D 10,2, both switches branching north onto an upper road 4,1 - 5,1 - 6,1 - 7,1.  The switches are
     * addressed, as a railway's are; C and D give A and B a way in from both sides.
     *
     * @return the page
     * @throws java.io.IOException from the diagram
     */
    private static LayoutDiagram passingLoop() throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 12, 4, null, null);

        lay(page, componentType.FEEDBACK, 1, 2, 0, 71);
        lay(page, componentType.STRAIGHT, 2, 2, 0, 0);
        lay(page, componentType.FEEDBACK, 3, 2, 0, 72);
        lay(page, componentType.SWITCH_LEFT, 4, 2, 3, 7);
        lay(page, componentType.STRAIGHT, 5, 2, 0, 0);
        lay(page, componentType.STRAIGHT, 6, 2, 0, 0);
        lay(page, componentType.SWITCH_RIGHT, 7, 2, 1, 8);
        lay(page, componentType.FEEDBACK, 8, 2, 0, 73);
        lay(page, componentType.STRAIGHT, 9, 2, 0, 0);
        lay(page, componentType.FEEDBACK, 10, 2, 0, 74);

        // THE UPPER ROAD: up from the west switch's branch, along, and down into the east switch's
        lay(page, componentType.CURVE, 4, 1, 0, 0);
        lay(page, componentType.STRAIGHT, 5, 1, 0, 0);
        lay(page, componentType.STRAIGHT, 6, 1, 0, 0);
        lay(page, componentType.CURVE, 7, 1, 3, 0);

        page.setPageId("1");

        return page;
    }

    /**
     * Puts a tile down, and gives a switch the accessory a railway's switch has.
     *
     * @param page the page
     * @param type the tile
     * @param x its column
     * @param y its row
     * @param orientation its orientation
     * @param address a sensor's or a switch's address, 0 for plain track
     * @throws java.io.IOException from the diagram
     */
    private static void lay(LayoutDiagram page, componentType type, int x, int y, int orientation, int address)
        throws java.io.IOException
    {
        page.addComponent(type, x, y, orientation, 0, address, address, accessoryDecoderType.MM2, null);

        if (type == componentType.SWITCH_LEFT || type == componentType.SWITCH_RIGHT)
        {
            page.getComponent(x, y).setAccessory(new org.traincontrol.marklin.MarklinAccessory(null, address,
                org.traincontrol.base.Accessory.accessoryType.SWITCH, accessoryDecoderType.MM2, "Switch " + address,
                false, 0));
        }
    }

    /**
     * Sets one road of a switch running one way only: its branch, or its straight road.
     *
     * @param on the setup
     * @param square the switch
     * @param branch true for the road that leaves by the north, false for the other
     * @param toward the side trains run toward on it
     */
    private static void runs(AutonomySession on, TileKey square, boolean branch, Side toward)
    {
        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> road : on.getRoutes(square).entrySet())
        {
            if (road.getValue().touches(Side.N) != branch) continue;

            on.setDirection(square, road.getKey(), road.getValue().getA() == toward ? Direction.TOWARD_A
                : Direction.TOWARD_B);
        }
    }

    /**
     * Whether the reduction's edge from one square to another runs over every one of these squares.
     *
     * @param on the setup
     * @param from where the edge starts
     * @param to where it ends
     * @param squares the squares
     * @return true when such an edge exists and runs over them all
     */
    private static boolean runsOver(AutonomySession on, TileKey from, TileKey to, Set<TileKey> squares)
    {
        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : on.getReducer().getEdges())
        {
            if (!from.equals(edge.getStart()) || !to.equals(edge.getEnd())) continue;

            Set<TileKey> over = new LinkedHashSet<>();

            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath()) over.add(step.getTile());

            return over.containsAll(squares);
        }

        return false;
    }

    /**
     * Deletes a folder and everything in it, as far as it can.
     *
     * @param file the folder
     */
    private static void deleteQuietly(java.io.File file)
    {
        java.io.File[] inside = file.listFiles();

        if (inside != null) for (java.io.File each : inside) deleteQuietly(each);

        if (!file.delete()) file.deleteOnExit();
    }

    /** The places the runtime says this class's train lies over. */
    private static Set<String> claimedByOurTrain()
    {
        Set<String> out = new LinkedHashSet<>();

        for (Map.Entry<String, Locomotive> claim : layout.placesCoveredByStandingTrains().entrySet())
        {
            if (claim.getValue() == train) out.add(claim.getKey());
        }

        return out;
    }

    /** A place id's square: the id up to a road suffix, when it names one. */
    private static TileKey squareOf(String place)
    {
        String square = place.indexOf('/') >= 0 ? place.substring(0, place.indexOf('/')) : place;

        for (TileKey key : session.routesCoveredByStandingTrains(layout).keySet())
        {
            if (key.toString().equals(square)) return key;
        }

        int colon = square.lastIndexOf(':');
        int comma = square.lastIndexOf(',');

        if (colon < 0 || comma < colon) return null;

        return new TileKey(square.substring(0, colon), Integer.parseInt(square.substring(colon + 1, comma)),
            Integer.parseInt(square.substring(comma + 1)));
    }

    /**
     * The copy of BottomMainA that rails from the west arrive at, more than one of them, over different squares.
     */
    private static Point theCopyWhereTheRailsFromTheWestPart()
    {
        assertEquals(session.getStationIndex().nameOf(PLATFORM), "BottomMainA",
            "the snapshot no longer calls " + PLATFORM + " BottomMainA, so this class is about a railway that has moved");

        for (Point point : layout.getPoints())
        {
            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square == null || !square.equals(PLATFORM)) continue;

            Set<List<String>> roads = new LinkedHashSet<>();

            for (Edge edge : layout.getEdges())
            {
                if (edge.getEnd() == point && "W".equalsIgnoreCase(layout.entrySideOf(edge, point)))
                {
                    roads.add(new ArrayList<>(edge.getPlaceIds()));
                }
            }

            if (roads.size() >= 2) return point;
        }

        throw new AssertionError("no copy of BottomMainA has two rails from the west over different squares, so the"
            + " tail there no longer stops where rails part and this class is about a railway that has moved");
    }
}
