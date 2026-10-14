package regression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A train standing with no road is drawn in orange over the switches its tail lies across (OB-348).
 *
 * Adam, 2026-10-14: *"when the tail reaches bottommainbcpre, it is not drawn over the thrown arm of the switch and 7,8,
 * even though I see orange before and after."*
 *
 * A train with no road into its platform - placed by hand, answered Not known, or re-stood - has its tail claimed up to
 * where the rails arriving by its side part (MT-477), and those claims are drawn along the road the rails agree on on
 * each square (OB-290).  The roads were asked of every edge of the railway through the square, so a switch behind the
 * platform whose other leg carries rails that never reach the platform - 7,8, whose straight leg runs from Tunnel to
 * BottomMainBCPre, and the three-way switch at 9,7 - was seen run along two roads and drawn along none.
 *
 * At TunnelLeftPark on the frozen `live-snapshot`, as Adam's 02 0314-1 DDR stands there: in from the south with no road,
 * and this class's own train long enough to reach back to where the rails part.
 *
 * MUTATION: let every edge through a claimed square say which road it runs along again, as
 * `AutonomySession.drawTheTrainsThatCoverNoEdge` did, and this fails at 9,7 and 7,8.
 *
 * @author Adam
 */
public class testTheOrangeRunsOverTheSwitchesBehindTheTrain
{
    private static final String PAGE = "1 - Main";

    /** TunnelLeftPark's square. */
    private static final TileKey PLATFORM = new TileKey(PAGE, 8, 6);

    /** The two squares Adam names: the switch whose thrown arm the tail lies on, and 7,8. */
    private static final TileKey THREE_WAY = new TileKey(PAGE, 9, 7);
    private static final TileKey SEVEN_EIGHT = new TileKey(PAGE, 7, 8);

    private static final String OUR_TRAIN = "OB-348 probe";

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

        train = model.newMM2Locomotive(OUR_TRAIN, 2397);

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

        List<String> copies = session.getStationIndex().pointNamesAt(PLATFORM);

        if (copies.size() != 1) throw new SkipException("TunnelLeftPark is not one Point on this snapshot: " + copies);

        platform = layout.getPoint(copies.get(0));

        assertNotNull(platform, "precondition: no Point for TunnelLeftPark");

        // AS 02 0314-1 DDR STANDS THERE: in from the south, and no road it drove
        platform.setLocomotive(train);
        platform.setArrivedFrom("S");
        platform.setArrivedAlong(null);

        train.setTrainLength(12);
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
     * Every square the tail claims behind the switches is drawn along the road the rails into the platform take there.
     */
    @Test
    public void testTheTailIsDrawnOverTheThrownArms()
    {
        for (Map.Entry<Edge, Locomotive> covered : layout.edgesCoveredByStandingTrains().entrySet())
        {
            assertFalse(covered.getValue() == train, "precondition: the tail covers the edge "
                + covered.getKey().getName() + ", so the railway knows its road and this is not the case OB-348 is about");
        }

        Map<TileKey, Set<RouteId>> roads = roadsOfTheRailsIntoThePlatform();

        assertTrue(roads.containsKey(THREE_WAY) && roads.containsKey(SEVEN_EIGHT), "precondition: the tail does not"
            + " reach back over 9,7 and 7,8, so this asks nothing about them.  Claimed squares: " + roads.keySet());

        assertEquals(roads.get(THREE_WAY).size(), 1, "precondition: the rails into TunnelLeftPark do not agree on one"
            + " road over the switch at 9,7: " + roads.get(THREE_WAY));

        assertEquals(roads.get(SEVEN_EIGHT).size(), 1, "precondition: the rails into TunnelLeftPark do not agree on one"
            + " road over 7,8: " + roads.get(SEVEN_EIGHT));

        Map<TileKey, Set<RouteId>> orange = session.routesCoveredByStandingTrains(layout);

        for (TileKey square : new TileKey[] {THREE_WAY, SEVEN_EIGHT})
        {
            assertEquals(orange.get(square), roads.get(square), "the tail of a train standing at TunnelLeftPark is not"
                + " drawn along its road over " + square + " - Adam, OB-348: \"it is not drawn over the thrown arm of the"
                + " switch and 7,8, even though I see orange before and after\".  Orange: " + orange);
        }

        // AND EVERY OTHER SQUARE THE RAILS AGREE ON, as before
        for (Map.Entry<TileKey, Set<RouteId>> square : roads.entrySet())
        {
            if (square.getValue().size() != 1) continue;

            assertEquals(orange.get(square.getKey()), square.getValue(), "the tail is not drawn along its road over "
                + square.getKey() + ".  Orange: " + orange);
        }
    }

    /**
     * Where the rails into the platform part, the switch is still drawn along no road: which leg is not known (OB-290).
     */
    @Test
    public void testWhereTheRailsPartNoRoadIsDrawn()
    {
        Map<TileKey, Set<RouteId>> roads = roadsOfTheRailsIntoThePlatform();

        Map<TileKey, Set<RouteId>> orange = session.routesCoveredByStandingTrains(layout);

        boolean parted = false;

        for (Map.Entry<TileKey, Set<RouteId>> square : roads.entrySet())
        {
            if (square.getValue().size() < 2) continue;

            parted = true;

            assertTrue(orange.containsKey(square.getKey()) && orange.get(square.getKey()).isEmpty(), "the switch at "
                + square.getKey() + ", where the rails into TunnelLeftPark part, is not listed with no road: the train"
                + " is on it, and on which leg is not known.  Orange: " + orange);
        }

        assertTrue(parted, "precondition: the tail never reaches the switch where the rails into TunnelLeftPark part."
            + "  Claimed squares: " + roads);
    }

    /**
     * Each square this class's train claims behind TunnelLeftPark, with the roads over it of the rails into the platform
     * by the south that run over every one of its claims - which is the railway's own question (`Layout`'s walk claims
     * what those rails share, up to where they part).
     *
     * @return claimed square to the roads those rails take over it
     */
    private static Map<TileKey, Set<RouteId>> roadsOfTheRailsIntoThePlatform()
    {
        Set<String> claims = new LinkedHashSet<>();

        for (Map.Entry<String, Locomotive> claim : layout.placesCoveredByStandingTrains().entrySet())
        {
            if (claim.getValue() == train) claims.add(claim.getKey());
        }

        assertFalse(claims.isEmpty(), "precondition: the train claims nothing");

        Map<TileKey, Set<RouteId>> out = new LinkedHashMap<>();

        List<GraphReducer.ReducedEdge> rails = new ArrayList<>();

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            if (!PLATFORM.equals(edge.getEnd())) continue;

            List<String> ids = new ArrayList<>();

            for (GraphReducer.Place place : session.getReducer().placesAlong(edge)) ids.add(place.getId());

            if (ids.containsAll(claims)) rails.add(edge);
        }

        assertTrue(rails.size() >= 2, "precondition: fewer than two rails into TunnelLeftPark run over the tail's"
            + " claims, so they part nowhere: " + rails);

        for (GraphReducer.ReducedEdge rail : rails)
        {
            List<GraphReducer.Place> places = session.getReducer().placesAlong(rail);

            List<GraphReducer.TileStep> path = rail.getPath();

            for (int at = 0; at < path.size() && at < places.size(); at++)
            {
                if (!claims.contains(places.get(at).getId())) continue;

                Set<RouteId> over = out.get(path.get(at).getTile());

                if (over == null)
                {
                    over = new LinkedHashSet<>();

                    out.put(path.get(at).getTile(), over);
                }

                if (path.get(at).getRouteId() != null) over.add(path.get(at).getRouteId());
            }
        }

        return out;
    }
}
