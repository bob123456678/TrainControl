package regression;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A train is refused for standing across the points only where it stops (Adam, MT-333, 2026-09-14).
 *
 * *"75 407 DB (len 2) can no longer go from tunnel to bottommaina because of the 1+1 length split around the
 * switch between buttommainapre and bottommaina.  this SHOULD be allowed per the standing rule that this switch
 * blocking should only affect berthes."*
 *
 * **What refused it.**  Not BottomMainA: its approach measures 1 + 1 and a length-2 train fits it, which is the
 * relaxation of 2026-09-12.  It was refused ONE SQUARE EARLIER - "does not fit at BottomMainAPre on the way to
 * BottomMainA, where the track measures 1" - because the run from Tunnel to BottomMainAPre crosses a switch with
 * one measured unit on it, and since 2026-09-10 (`5948a88a`, the ruling of 2026-09-09) a square the train only
 * PASSES was judged as though the train stood there.  It never does: it stops at the destination, and at a square
 * it turns round at.  The standing rule of 2026-09-12 - *"make a rule that parking berths cant block any other
 * edges, but not make that check for active stations"* - was built at the destination only, and the pass-through
 * check went on refusing overhangs nobody stops in.  It showed on his railway when those tiles were measured.
 *
 * **What still refuses.**  The destination (the room past its switch, or its whole approach at a station
 * autonomy may choose, and the berth rule at one it may not) and a square the train turns at (2026-09-11).
 *
 * **And the protrusion he asked to have re-filed**: *"if we made 75 407 DB have len 3 and it is parked at
 * bottommaina, other trains should be blocked from going tunnel->bottommainb and c."*  A driven train's tail
 * follows the road it came in on (MT-335), so three units behind a two-unit approach reach back over the run from
 * Tunnel, which the roads to B and C share.
 *
 * Each claim on its own sandbox copy of the frozen `live-snapshot`, with Adam's measurements of 2026-09-14 set on it:
 * `1 - Main` 7,9 = 1, 10,10 = 2, 13,12 = 1, 19,12 = 1, and nothing else.  Two trains of its own.
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
     * A TRACK DIAGRAM OF ITS OWN FOR EVERY SCENARIO (Adam, 2026-09-14: "Make sure tests for the different
     * scenarios use independent track diagrams- otherwise all bets will be off").
     *
     * Each claim below opens its own sandbox copy of the snapshot, sets its own lengths on it, makes its own two
     * trains and takes them away again, so no claim can pass or fail on a length, a placement or a remembered
     * route another one left behind.
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

        // ADAM'S MEASUREMENTS AS THEY STAND ON 2026-09-14, and nothing else.
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

    /**
     * 75 407 DB's journey: two units, Tunnel to BottomMainA, past one measured unit after a switch.
     */
    @Test
    public void testATwoUnitTrainIsOfferedTunnelToBottomMainA() throws Exception
    {
        Layout built = emptied();

        standing.setTrainLength(2);

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = copy(built, "BottomMainA", true);

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
     * The control: the destination is still judged.  Three units do not fit the two-unit approach.
     */
    @Test
    public void testAThreeUnitTrainIsStillRefusedTheTwoUnitPlatform() throws Exception
    {
        Layout built = emptied();

        standing.setTrainLength(3);

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = copy(built, "BottomMainA", true);

        List<Edge> route = built.bfs(tunnel, mainA, new LinkedList<List<Edge>>());

        assertNotNull(route, "precondition: no route from Tunnel to BottomMainA");

        String why = Layout.whyTooLongForThisRoute(route, standing);

        assertNotNull(why, "a three-unit train is admitted to BottomMainA, whose approach measures two - the"
            + " destination is no longer judged at all, which is not what MT-333 asked for");

        assertTrue(why.contains("BottomMainA"), "the refusal is '" + why + "', which does not name BottomMainA -"
            + " it is refused somewhere it only passes");
    }

    /**
     * The protrusion: driven to BottomMainA at two units and made three, it closes Tunnel to B and C.
     */
    @Test
    public void testThreeUnitsAtBottomMainAClosesTunnelToBAndC() throws Exception
    {
        Layout built = emptied();

        other.setTrainLength(null);

        // Reversible, so a turning copy of B or C is no reason to refuse it: the one thing left in its way is the tail.
        other.setReversible(true);

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = copy(built, "BottomMainA", true);

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
     * And after a rebuild the three units still close B and C: the road comes back with the train (WK7-B1).
     *
     * Every setup gesture and every close of the autonomy editor regenerates the running layout from the setup and
     * puts the trains back where they stood (`TrainControlUI.putTheTrainsBack`).  That carried the side and not the
     * road, so the tail stopped at BottomMainAPre and B and C were offered again over a tail still lying on the
     * Tunnel run.  Adam, 2026-09-14: *"Then state will always be fully consistent."*
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

    /**
     * And written to the setup, so a restart - a build from the file alone - still closes B and C (WK7-B1).
     */
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

    /** Three units standing at BottomMainA as a train driven there from Tunnel stands, and the other train at Tunnel. */
    private Layout aThreeUnitTrainDrivenToBottomMainA() throws Exception
    {
        Layout built = emptied();

        other.setTrainLength(null);
        other.setReversible(true);

        Point tunnel = copy(built, "Tunnel", true);
        Point mainA = copy(built, "BottomMainA", true);

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

        if (found == null) throw new SkipException("the snapshot has no " + square);

        return found;
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
