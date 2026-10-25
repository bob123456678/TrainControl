package regression;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A train is refused for standing across the points only where it stops (MT-333).  Adam: "75 407 DB (len 2) can no
 * longer go from tunnel to bottommaina because of the 1+1 length split around the switch between buttommainapre and
 * bottommaina.  this SHOULD be allowed per the standing rule that this switch blocking should only affect berthes."
 *
 * It was refused one square earlier, because a square the train only PASSES was judged as though it stood there; the
 * standing rule ("make a rule that parking berths cant block any other edges, but not make that check for active
 * stations") was built at the destination only.  Still refusing: the destination (the room past its switch, or its
 * measured route in at a station autonomy may choose (FR-087), and the berth rule at one it may not) and a square the
 * train turns at.  And the protrusion: "if we made 75 407 DB have len 3 and it is parked at bottommaina, other trains
 * should be blocked from going tunnel->bottommainb and c" - a driven train's tail follows the road it came in on
 * (MT-335).
 *
 * Each claim runs on its own sandbox copy of the frozen `live-snapshot`, with Adam's measurements set on it: `1 - Main`
 * 7,9 = 1, 10,10 = 2, 13,12 = 1, 19,12 = 1, and nothing else.  Two trains of its own.
 *
 * @author Adam
 */
public class testAPassingTrainMayStandAcrossThePoints
{
    private support.Scenario scenario;

    private MarklinLocomotive standing;
    private MarklinLocomotive other;

    private static final String STANDING = "overhang probe standing";
    private static final String OTHER = "overhang probe other";

    /**
     * A TRACK DIAGRAM OF ITS OWN FOR EVERY SCENARIO (Adam: "Make sure tests for the different scenarios use independent
     * track diagrams- otherwise all bets will be off"): each claim opens its own sandbox copy, sets its own lengths,
     * makes its own two trains and takes them away again.
     */
    @BeforeMethod
    public void openAFreshDiagram() throws Exception
    {
        scenario = support.Scenario.open("live-snapshot");

        scenario.getModel().stop();

        standing = scenario.getModel().newMM2Locomotive(STANDING, 2331);
        other = scenario.getModel().newMM2Locomotive(OTHER, 2332);

        assertNotNull(standing, "could not create the standing train");
        assertNotNull(other, "could not create the other train");

        // ADAM'S MEASUREMENTS, and nothing else.
        for (TileKey tile : new java.util.ArrayList<>(scenario.getSession().tilesWithALength()))
        {
            scenario.getSession().setTileLength(tile, 0);
        }

        scenario.getSession().setTileLength(scenario.tile("1 - Main", 7, 9), 1);
        scenario.getSession().setTileLength(scenario.tile("1 - Main", 10, 10), 2);
        scenario.getSession().setTileLength(scenario.tile("1 - Main", 13, 12), 1);
        scenario.getSession().setTileLength(scenario.tile("1 - Main", 19, 12), 1);
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheDiagram()
    {
        if (scenario == null) return;

        for (String name : new String[] { STANDING, OTHER })
        {
            try
            {
                scenario.getModel().deleteLoc(name);
            }
            catch (Exception alreadyGone)
            {
            }
        }

        scenario.close();

        scenario = null;
    }

    /** 75 407 DB's journey: two units, Tunnel to BottomMainA, past one measured unit after a switch. */
    @Test
    public void testATwoUnitTrainIsOfferedTunnelToBottomMainA() throws Exception
    {
        Layout built = emptied();

        standing.setTrainLength(2);

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = arrivalFrom(built, tunnel, "BottomMainA");

        List<Edge> route = built.bfs(tunnel, mainA, new LinkedList<List<Edge>>());

        assertNotNull(route, "precondition: there is no route from " + tunnel.getName() + " to BottomMainA on the snapshot");

        assertTrue(route.size() >= 2 && route.get(0).crossesASwitch() && route.get(0).getLength() == 1,
            "precondition: the first leg of the route is not a switch-crossing run measuring one unit, so the square"
            + " after it would never have been refused and this claim tests nothing: " + describe(route));

        assertTrue(built.moveLocomotive(STANDING, tunnel.getName(), false), "could not stand the train at " + tunnel.getName());

        assertNull(Layout.whyTooLongForThisRoute(route, standing),
            "a two-unit train is refused Tunnel -> BottomMainA: '" + Layout.whyTooLongForThisRoute(route, standing)
            + "'.  It only PASSES the square after the one-unit run and stops at BottomMainA, whose approach holds"
            + " it.  Adam, MT-333: 'this SHOULD be allowed per the standing rule that this switch blocking should"
            + " only affect berthes'");

        assertTrue(endsOf(built.getPossiblePaths(standing, false)).contains(mainA.getName()),
            "BottomMainA is not offered to the two-unit train at " + tunnel.getName() + " - the railway still refuses"
            + " it somewhere.  Offered: " + endsOf(built.getPossiblePaths(standing, false)));
    }

    /**
     * Three units are taken at BottomMainA because the measured route in holds them, and four are not (FR-087; Adam,
     * MT-431: "didn't we say that BottomMainA should be allowed at length 3, since it is not a parking spot, the
     * station allows the length, and the length would be tracked?").  At a station autonomy may choose, the train is
     * accepted if the measured track of the route it drives in on holds it back from the destination: Tunnel to
     * BottomMainA measures one unit and then two.
     */
    @Test
    public void testTheRouteInHoldsAThreeUnitTrainAndNotAFourUnitOne() throws Exception
    {
        Layout built = emptied();

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = arrivalFrom(built, tunnel, "BottomMainA");

        List<Edge> route = built.bfs(tunnel, mainA, new LinkedList<List<Edge>>());

        assertNotNull(route, "precondition: no route from Tunnel to BottomMainA");

        int measured = 0;

        for (Edge leg : route) measured += Math.max(0, leg.getLength());

        assertEquals(measured, 3, "precondition: the route from Tunnel to BottomMainA does not measure three units, so this"
            + " claim is not about the train it names: " + describe(route));

        standing.setTrainLength(3);

        assertNull(Layout.whyTooLongForThisRoute(route, standing),
            "three units are refused BottomMainA, a station autonomy may choose, though the route in measures three and the"
            + " tail lying on it is tracked.  Adam, MT-431: 'BottomMainA should be allowed at length 3'.  Refused: "
            + Layout.whyTooLongForThisRoute(route, standing));

        standing.setTrainLength(4);

        String why = Layout.whyTooLongForThisRoute(route, standing);

        assertNotNull(why, "four units are accepted at BottomMainA, and the route in measures three - the tail would lie on"
            + " track nothing has measured");

        assertTrue(why.contains("BottomMainA"), "the four-unit refusal does not name BottomMainA: " + why);

        // AND THE NUMBER THAT REFUSED IT: three units were admitted over this route, so a refusal quoting the room past
        // the switch would say the square holds less than it just held.
        assertTrue(why.contains(org.traincontrol.util.I18n.f("autolayout.errorTrainTooLongForBerth", standing.getName(),
            "BottomMainA", 3, 4).replaceAll(".*which measures ", "which measures ").replaceAll(" - .*", "")),
            "the four-unit refusal does not say the route in measures three - it quotes another bound: " + why);
    }

    /** The protrusion: driven to BottomMainA at two units and made three, it closes Tunnel to B and C. */
    @Test
    public void testThreeUnitsAtBottomMainAClosesTunnelToBAndC() throws Exception
    {
        Layout built = emptied();

        other.setTrainLength(null);

        // Reversible, so a turning copy of B or C is no reason to refuse it: the one thing left in its way is the tail.
        other.setReversible(true);

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = arrivalFrom(built, tunnel, "BottomMainA");

        List<Edge> route = built.bfs(tunnel, mainA, new LinkedList<List<Edge>>());

        assertNotNull(route, "precondition: no route from Tunnel to BottomMainA");

        // Standing at BottomMainA as a DRIVEN train is: the side it came in by, and the road it came along.
        standing.setTrainLength(2);

        assertTrue(built.moveLocomotive(STANDING, mainA.getName(), false), "could not stand the train at BottomMainA");

        mainA.setArrivedFrom(built.entrySideOf(route.get(route.size() - 1), mainA));
        mainA.setArrivedAlong(route);

        assertTrue(built.moveLocomotive(OTHER, tunnel.getName(), false), "could not stand the other train at Tunnel");

        Map<String, String> atTwo = built.explainDestinations(other);

        for (String road : new String[] { "BottomMainB", "BottomMainC" })
        {
            assertNull(reasonFor(atTwo, road),
                "precondition: with two units at BottomMainA - exactly its approach - the other train is already"
                + " refused " + road + ", so the three units below are not what closes it: " + reasonFor(atTwo, road));
        }

        standing.setTrainLength(3);

        Map<String, String> atThree = built.explainDestinations(other);

        for (String road : new String[] { "BottomMainB", "BottomMainC" })
        {
            String why = reasonFor(atThree, road);

            assertNotNull(why, "with 75 407 DB's three units at BottomMainA, one more than its approach, the other"
                + " train at Tunnel is still offered " + road + ".  Adam, MT-333: 'other trains should be blocked"
                + " from going tunnel->bottommainb and c'.  Reasons: " + atThree);

            assertTrue(why.contains(STANDING), road + " is refused, but not because of the train at BottomMainA: '"
                + why + "'");
        }
    }

    /**
     * And after a rebuild the three units still close B and C: the road comes back with the train
     * (`TrainControlUI.putTheTrainsBack` once carried the side and not the road).  Adam: "Then state will always be
     * fully consistent."
     */
    @Test
    public void testTheRoadIsKeptAcrossARebuild() throws Exception
    {
        Layout built = aThreeUnitTrainDrivenToBottomMainA();

        Map<String, String[]> standing = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(built);

        Layout rebuilt = emptied();

        org.traincontrol.gui.TrainControlUI.putTheTrainsBack(rebuilt, standing, null);

        assertBAndCAreClosed(rebuilt, "after a rebuild");
    }

    /** And written to the setup, so a restart (a build from the file alone) still closes B and C. */
    @Test
    public void testTheRoadIsWrittenToTheSetup() throws Exception
    {
        Layout built = aThreeUnitTrainDrivenToBottomMainA();

        String active = scenario.getSession().getStore().getActiveConfiguration();

        assertNotNull(active, "precondition: the scenario has no active configuration to capture into");

        scenario.getSession().captureFromLayout(built.toJSON(), active);

        Layout restarted = scenario.build();

        assertBAndCAreClosed(restarted, "after the running layout was captured to the setup and built again from it");
    }

    /**
     * Turned where it stands, the train still covers the road it came in on: a facing change stands it on the other
     * lane's copy and carries its side and road across (`AutonomySession.moveOntoFacingCopy`), and the walk that
     * follows a road "either way round" once matched the platform's own edge from the far end and turned back on
     * itself.
     */
    @Test
    public void testTheRoadIsFollowedAfterTheTrainIsTurned() throws Exception
    {
        // BOTH LANES OF BOTTOMMAINA ONES A TRAIN MAY STAND ON: the turn stands the train on the other lane's copy, and
        // with arrivals from the east barred that copy is no station, so a turn that can happen is needed.
        TileKey mainASquare = scenario.tile("1 - Main", 20, 12);

        assertEquals(scenario.getSession().getStore().getPointName(mainASquare), "BottomMainA",
            "precondition: 1 - Main 20,12 is not BottomMainA on this snapshot");

        scenario.getSession().setBarredArrivals(mainASquare,
            java.util.Collections.<org.traincontrol.automationui.TilePorts.Side>emptySet());

        Layout built = aThreeUnitTrainDrivenToBottomMainA();

        Point mainA = null;

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() == standing) mainA = point;
        }

        TileKey square = scenario.getSession().getStationIndex().squareOf(mainA);

        assertNotNull(square, "precondition: BottomMainA is not a square of the setup");

        Map<String, org.traincontrol.automationui.TilePorts.Side> copies = scenario.getSession().facingsFor(square);

        org.traincontrol.automationui.TilePorts.Side now = copies.get(mainA.getName());
        org.traincontrol.automationui.TilePorts.Side other = null;

        for (org.traincontrol.automationui.TilePorts.Side side : copies.values())
        {
            if (side != null && side != now) other = side;
        }

        if (other == null) throw new AssertionError("BottomMainA is not split into two lanes on the snapshot: " + copies);

        final java.lang.reflect.Method turn = org.traincontrol.automationui.AutonomySession.class.getDeclaredMethod(
            "moveOntoFacingCopy", Layout.class, String.class, TileKey.class, org.traincontrol.automationui.TilePorts.Side.class);

        turn.setAccessible(true);

        turn.invoke(scenario.getSession(), built, STANDING, square, other);

        Point turned = null;

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() == standing) turned = point;
        }

        assertNotNull(turned, "precondition: the turn put the train nowhere");
        assertNotEquals(turned.getName(), mainA.getName(), "precondition: the turn left the train on the same copy");
        assertNotNull(turned.getArrivedAlong(), "precondition: the turn did not carry the road across (TDR-B2)");

        assertBAndCAreClosed(built, "after the train was turned at BottomMainA");
    }

    /**
     * A train that leaves takes its side and road out of the setup, whichever copy they were read from: the build
     * writes them onto every copy, a leaving train clears only the copy it stood on, and the capture merge put the tail
     * back.
     */
    @Test
    public void testALeavingTrainTakesItsTailOutOfTheSetup() throws Exception
    {
        Layout built = aThreeUnitTrainDrivenToBottomMainA();

        String active = scenario.getSession().getStore().getActiveConfiguration();

        assertNotNull(active, "precondition: no active configuration");

        scenario.getSession().captureFromLayout(built.toJSON(), active);

        Layout rebuilt = scenario.build();

        Point mainA = null;

        for (Point point : rebuilt.getPoints())
        {
            if (point.getCurrentLocomotive() == standing) mainA = point;
        }

        assertNotNull(mainA, "precondition: the rebuild did not stand the train at BottomMainA");

        TileKey square = scenario.getSession().getStationIndex().squareOf(mainA);

        if (scenario.getSession().facingsFor(square).size() < 2)
        {
            throw new AssertionError("BottomMainA is not split on the snapshot, so it has no other copy to hold a stale tail");
        }

        assertNotNull(scenario.getSession().getArrivedFrom(square), "precondition: the capture did not record the side");

        assertTrue(rebuilt.moveLocomotive(null, mainA.getName(), true), "could not take the train off BottomMainA");

        scenario.getSession().captureFromLayout(rebuilt.toJSON(), active);

        assertNull(scenario.getSession().getArrivedFrom(square), "the train left BottomMainA and the setup still records"
            + " the side it came in by - read back from another copy of the square, so a restart gives it to the next train");

        assertNull(scenario.getSession().getArrivedAlong(square), "the train left BottomMainA and the setup still records"
            + " its road - read back from another copy of the square");
    }

    /** Three units standing at BottomMainA as a train driven there from Tunnel stands, and the other train at Tunnel. */
    private Layout aThreeUnitTrainDrivenToBottomMainA() throws Exception
    {
        Layout built = emptied();

        other.setTrainLength(null);
        other.setReversible(true);

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = arrivalFrom(built, tunnel, "BottomMainA");

        List<Edge> route = built.bfs(tunnel, mainA, new LinkedList<List<Edge>>());

        assertNotNull(route, "precondition: no route from Tunnel to BottomMainA");

        standing.setTrainLength(3);

        assertTrue(built.moveLocomotive(STANDING, mainA.getName(), false), "could not stand the train at BottomMainA");

        mainA.setArrivedFrom(built.entrySideOf(route.get(route.size() - 1), mainA));
        mainA.setArrivedAlong(route);

        assertTrue(built.moveLocomotive(OTHER, tunnel.getName(), false), "could not stand the other train at Tunnel");

        assertBAndCAreClosed(built, "before anything was rebuilt (precondition)");

        return built;
    }

    /** B and C are refused to the other train, because of the standing one. */
    private void assertBAndCAreClosed(Layout built, String when)
    {
        Map<String, String> reasons = built.explainDestinations(other);

        for (String road : new String[] { "BottomMainB", "BottomMainC" })
        {
            String why = reasonFor(reasons, road);

            assertNotNull(why, when + ", the other train at Tunnel is offered " + road + " with three units standing at"
                + " BottomMainA - the tail lying back along the Tunnel run is no longer followed past BottomMainAPre, so"
                + " the road it came in on was lost (WK7-B1).  Reasons: " + reasons);

            assertTrue(why.contains(STANDING), when + ", " + road + " is refused, but not because of the train at"
                + " BottomMainA: '" + why + "'");
        }
    }

    // ---------------------------------------------------------------- the fixture

    /** The railway built afresh, with every train taken off it. */
    private Layout emptied() throws Exception
    {
        Layout built = scenario.build();

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null) built.moveLocomotive(null, point.getName(), true);
        }

        return built;
    }

    /** The copy of a square a train can be sent to: the plain one where there are several. */
    private static Point copy(Layout built, String square, boolean destination)
    {
        Point found = null;

        for (Point point : built.getPoints())
        {
            String name = point.getName();

            if (!name.equals(square) && !name.startsWith(square + " (")) continue;

            if (destination && !point.isDestination()) continue;

            if (name.endsWith(", reverse)")) continue;

            if (found == null || name.length() < found.getName().length()) found = point;
        }

        if (found == null) throw new AssertionError("the snapshot has no " + square);

        return found;
    }

    /**
     * The plain copy of a square that a journey from here arrives on, by ROUTE and not by name: both plain copies of
     * BottomMainA are destinations now, and the shorter-named westbound one is not reached from Tunnel.
     */
    private static Point arrivalFrom(Layout built, Point from, String square) throws Exception
    {
        List<String> tried = new LinkedList<>();

        for (Point point : built.getPoints())
        {
            String name = point.getName();

            if (!name.equals(square) && !name.startsWith(square + " (")) continue;

            if (!point.isDestination() || name.endsWith(", reverse)")) continue;

            if (built.bfs(from, point, new LinkedList<List<Edge>>()) != null) return point;

            tried.add(name);
        }

        throw new AssertionError("precondition: no route from " + from.getName() + " reaches a plain copy of " + square
            + " (tried " + tried + "), so every claim in this class is about a journey the railway no longer has");
    }

    /** The first reason given for any copy of a square, or null when one of them is available. */
    private static String reasonFor(Map<String, String> reasons, String square)
    {
        String why = null;

        for (Map.Entry<String, String> entry : reasons.entrySet())
        {
            String name = entry.getKey();

            if (!name.equals(square) && !name.startsWith(square + " (")) continue;

            if (entry.getValue() == null) return null;

            if (why == null) why = entry.getValue();
        }

        return why;
    }

    private static java.util.Set<String> endsOf(List<List<Edge>> paths)
    {
        java.util.Set<String> out = new java.util.TreeSet<>();

        if (paths == null) return out;

        for (List<Edge> path : paths)
        {
            if (!path.isEmpty()) out.add(path.get(path.size() - 1).getEnd().getName());
        }

        return out;
    }

    private static String describe(List<Edge> route)
    {
        StringBuilder out = new StringBuilder();

        for (Edge edge : route)
        {
            out.append(" [").append(edge.getName()).append(" length=").append(edge.getLength())
                .append(" switch=").append(edge.crossesASwitch()).append("]");
        }

        return out.toString();
    }
}
