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
import org.traincontrol.automationui.TilePorts.Side;
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
 * `AutonomySession.drawTheTrainsThatCoverNoEdge` did, and the first claim fails at 9,7 and 7,8.
 *
 * And at Tunnel, where a rail autonomy does not drive - against a one-way arrow - runs through the other leg of the switch
 * at 11,3 behind the platform (V348-C3): a train placed there by hand may have come along it, so which leg is not known.
 *
 * MUTATION: ask only the rails the railway drives, the ones that run over every place the train claims, and the third
 * claim fails at 11,3.
 *
 * And nothing past such a switch (V348B-C1; Adam, 2026-10-14: "stop at unknown.  but if it's not thrown and the train
 * must pass through the thrown arm, then do draw it"): beyond it the tail may lie on either road.
 *
 * MUTATION: draw every claimed square the rails agree on, wherever it lies, and the third and fourth claims fail.
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

        // ASKED OF THE TRACK, not of the rails (V348-C4): the leg of 9,7 between its west and south sides, which 8,7's
        // curve leads into, and the leg of 7,8 between its east and south sides, which 8,8 leads into
        assertEquals(roads.get(THREE_WAY), legBetween(THREE_WAY, Side.W, Side.S), "precondition: the rails into"
            + " TunnelLeftPark do not run over 9,7's leg from 8,7");

        assertEquals(roads.get(SEVEN_EIGHT), legBetween(SEVEN_EIGHT, Side.E, Side.S), "precondition: the rails into"
            + " TunnelLeftPark do not run over 7,8's leg from 8,8");

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
     * Where a rail autonomy does not drive runs through a switch behind the platform on the other leg, the switch is drawn
     * along no road: the train may have come along it (V348-C3).
     */
    @Test
    public void testASwitchAnUndrivenRailCrossesIsDrawnAlongNoRoad()
    {
        TileKey tunnel = new TileKey(PAGE, 7, 7);
        TileKey fork = new TileKey(PAGE, 11, 3);

        Point southbound = null;

        for (Map.Entry<String, Side> copy : session.getStationIndex().facingsAt(tunnel).entrySet())
        {
            if (copy.getValue() == Side.S) southbound = layout.getPoint(copy.getKey());
        }

        if (southbound == null) throw new SkipException("Tunnel has no southbound copy on this snapshot");

        int length = train.getTrainLength();

        platform.setLocomotive(null);

        try
        {
            // PLACED BY HAND: in from the north, and no road
            southbound.setLocomotive(train);
            southbound.setArrivedFrom("N");
            southbound.setArrivedAlong(null);

            train.setTrainLength(40);

            for (Map.Entry<Edge, Locomotive> covered : layout.edgesCoveredByStandingTrains().entrySet())
            {
                assertFalse(covered.getValue() == train, "precondition: the tail covers the edge "
                    + covered.getKey().getName() + ", so this is not a train with no road");
            }

            Set<String> claims = claimsOfOurTrain();

            assertTrue(claims.contains(placeOf(fork)), "precondition: the tail does not reach back over 11,3: " + claims);

            // THE RAILS INTO TUNNEL BY THE NORTH OVER 11,3: those the railway drives run over all the claims, and one at
            // least, which it does not, over the other leg
            Set<RouteId> driven = new LinkedHashSet<>();
            Set<RouteId> undriven = new LinkedHashSet<>();

            for (GraphReducer.ReducedEdge rail : session.getReducer().getEdges())
            {
                if (!tunnel.equals(rail.getEnd()) || rail.getEntrySide() != Side.N) continue;

                List<String> ids = new ArrayList<>();

                for (GraphReducer.Place place : session.getReducer().placesAlong(rail)) ids.add(place.getId());

                for (GraphReducer.TileStep step : rail.getPath())
                {
                    if (!fork.equals(step.getTile()) || step.getRouteId() == null) continue;

                    (ids.containsAll(claims) ? driven : undriven).add(step.getRouteId());
                }
            }

            undriven.removeAll(driven);

            assertTrue(driven.size() == 1 && !undriven.isEmpty(), "precondition: no rail into Tunnel by the north runs over"
                + " 11,3 on a leg the railway's rails do not take: driven " + driven + ", other " + undriven);

            Map<TileKey, Set<RouteId>> orange = session.routesCoveredByStandingTrains(layout);

            assertTrue(orange.containsKey(fork) && orange.get(fork).isEmpty(), "the switch at 11,3, which a rail into"
                + " Tunnel autonomy does not drive crosses on its other leg, is drawn along " + orange.get(fork) + " for a"
                + " train placed at Tunnel by hand - it may have come along that rail (V348-C3).  Orange: " + orange);

            TileKey beyond = new TileKey(PAGE, 11, 4);

            assertTrue(claims.contains(placeOf(beyond)), "precondition: the tail does not reach back past 11,3 to 11,4: "
                + claims);

            assertTrue(orange.get(beyond) == null || orange.get(beyond).isEmpty(), "11,4, past the switch at 11,3 whose leg"
                + " is not known, is drawn along " + orange.get(beyond) + " - Adam: \"stop at unknown\" (V348B-C1).  Orange: "
                + orange);
        }
        finally
        {
            southbound.setLocomotive(null);

            train.setTrainLength(length);

            platform.setLocomotive(train);
            platform.setArrivedFrom("S");
            platform.setArrivedAlong(null);
        }
    }

    /**
     * Nowhere on the railway is a tail drawn farther back than a square it lies on whose road is not known (V348B-C1).
     *
     * Every Point of the snapshot, in by every side a rail reaches it by, at three lengths, with no road: past a square the
     * orange lists with no road - the switch where the rails into the platform part - nothing is drawn along one.  How far
     * back is asked of the rails into the platform by that side that run over every place the train claims.
     */
    @Test
    public void testNothingIsDrawnPastASwitchWhoseLegIsNotKnown()
    {
        int length = train.getTrainLength();

        platform.setLocomotive(null);

        int asked = 0;
        int blanks = 0;

        try
        {
            for (Point at : new ArrayList<>(layout.getPoints()))
            {
                TileKey square = session.getStationIndex().squareOf(at.getName());

                if (square == null || at.getCurrentLocomotive() != null) continue;

                Set<Side> sides = new LinkedHashSet<>();

                for (GraphReducer.ReducedEdge rail : session.getReducer().getEdges())
                {
                    if (square.equals(rail.getEnd()) && rail.getEntrySide() != null) sides.add(rail.getEntrySide());
                }

                for (Side side : sides)
                {
                    for (int units : new int[] {4, 12, 40})
                    {
                        at.setLocomotive(train);
                        at.setArrivedFrom(side.name());
                        at.setArrivedAlong(null);

                        train.setTrainLength(units);

                        try
                        {
                            boolean covers = false;

                            for (Locomotive coverer : layout.edgesCoveredByStandingTrains().values())
                            {
                                covers |= coverer == train;
                            }

                            if (covers) continue;

                            Set<String> claims = claimsOfOurTrain();

                            Map<TileKey, Integer> back = new LinkedHashMap<>();

                            for (GraphReducer.ReducedEdge rail : session.getReducer().getEdges())
                            {
                                if (!square.equals(rail.getEnd()) || rail.getEntrySide() != side) continue;

                                List<GraphReducer.Place> places = session.getReducer().placesAlong(rail);

                                List<String> ids = new ArrayList<>();

                                for (GraphReducer.Place place : places) ids.add(place.getId());

                                if (!ids.containsAll(claims)) continue;

                                for (int i = 0; i < rail.getPath().size() && i < places.size(); i++)
                                {
                                    if (!claims.contains(ids.get(i))) continue;

                                    TileKey tile = rail.getPath().get(i).getTile();

                                    Integer was = back.get(tile);

                                    if (was == null || places.size() - 1 - i < was) back.put(tile, places.size() - 1 - i);
                                }
                            }

                            Map<TileKey, Set<RouteId>> orange = session.routesCoveredByStandingTrains(layout);

                            asked++;

                            for (Map.Entry<TileKey, Integer> blank : back.entrySet())
                            {
                                Set<RouteId> drawn = orange.get(blank.getKey());

                                if (drawn == null || !drawn.isEmpty()) continue;

                                blanks++;

                                for (Map.Entry<TileKey, Integer> farther : back.entrySet())
                                {
                                    if (farther.getValue() <= blank.getValue()) continue;

                                    Set<RouteId> past = orange.get(farther.getKey());

                                    assertTrue(past == null || past.isEmpty(), "a train of " + units + " units standing at "
                                        + at.getName() + ", in by " + side + " with no road, is drawn along " + past + " at "
                                        + farther.getKey() + ", past " + blank.getKey() + " whose road is not known - Adam:"
                                        + " \"stop at unknown\" (V348B-C1).  Orange: " + orange);
                                }
                            }
                        }
                        finally
                        {
                            at.setLocomotive(null);
                        }
                    }
                }
            }
        }
        finally
        {
            train.setTrainLength(length);

            platform.setLocomotive(train);
            platform.setArrivedFrom("S");
            platform.setArrivedAlong(null);
        }

        assertTrue(asked > 0 && blanks > 0, "precondition: the sweep asked " + asked + " placements and found " + blanks
            + " squares left without a road, so it says nothing");
    }

    /**
     * The one road of a square that runs between the two sides given.
     *
     * @param square the square
     * @param a one side
     * @param b the other
     * @return the road, as a set of one, or an empty set
     */
    private static Set<RouteId> legBetween(TileKey square, Side a, Side b)
    {
        Set<RouteId> out = new LinkedHashSet<>();

        for (Map.Entry<RouteId, org.traincontrol.automationui.TilePorts.Route> road : session.getRoutes(square).entrySet())
        {
            if (road.getValue().touches(a) && road.getValue().touches(b)) out.add(road.getKey());
        }

        return out;
    }

    /**
     * The places this class's train claims.
     *
     * @return the place ids
     */
    private static Set<String> claimsOfOurTrain()
    {
        Set<String> claims = new LinkedHashSet<>();

        for (Map.Entry<String, Locomotive> claim : layout.placesCoveredByStandingTrains().entrySet())
        {
            if (claim.getValue() == train) claims.add(claim.getKey());
        }

        return claims;
    }

    /**
     * The place id the reduction gives a whole square, as a switch's is.
     *
     * @param square the square
     * @return the id, or null where no edge names the square as a whole
     */
    private static String placeOf(TileKey square)
    {
        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            List<GraphReducer.Place> places = session.getReducer().placesAlong(edge);

            for (int at = 0; at < edge.getPath().size() && at < places.size(); at++)
            {
                if (square.equals(edge.getPath().get(at).getTile())) return places.get(at).getId();
            }
        }

        return null;
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
