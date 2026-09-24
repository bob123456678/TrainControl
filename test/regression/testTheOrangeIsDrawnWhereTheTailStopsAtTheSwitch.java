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
