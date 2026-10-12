package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A station reached by a longer route is offered when the shorter one passes a terminus (OB-229).
 *
 * Found through Return Home's agreement check on Adam's MT-335 run of 2026-09-15: *"planner allows 75 407 DB ->
 * BottomSecondary, but the layout would refuse it"*, and RampDown twice.  The planner was right - the route it found,
 * the loop from Tunnel over the top level down RampDown, passes `isPathClear` - and the railway's own route search
 * never produced it.  `Layout.bfs` marks a square visited once it is taken off the queue and excludes only whole paths,
 * so where the SHORTEST way to a square runs through a terminus - which `isPathClear` refuses in the middle of a route,
 * every time - that square is spent on a path that can never be used, and the longer way through it is never extended.
 * The right-click menu, autonomy and Why Not Moving? all ask that search.
 *
 * Adam chose, asked how to fix it: the search does not extend a route through a terminus that is not its end.
 *
 * **The shape, on sensors of its own.**  From S two ways reach Q and then E: S -> X -> T -> Y -> Q, through T, a
 * terminus; and S -> A -> B -> C -> D -> F -> Q, through none.  The longer way is TWO squares longer on purpose:
 * `Layout.getNeighbors` shuffles, and a way only one square longer reaches D at the same depth as the shorter way
 * reaches Q, so on some runs D is taken off the queue first and Q is queued twice - the first version of this claim
 * was red on one assertion and green on the other for exactly that reason.  Nothing is measured.
 *
 * @author Adam
 */
public class testARouteIsFoundPastATerminus
{
    private static MarklinControlStation model;

    /** A throwaway copy of the fixture layout, opened BEFORE the model: `init` reads the layout preference (OB-111). */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * E is offered to a train at S, by the longer route, and the autonomy answer has no reason against it.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheLongerRouteIsOffered() throws Exception
    {
        Layout layout = theTwoWays("RT9", 3200, true);
        Locomotive loc = aTrainAt(layout, "RT9_S");

        List<Edge> longer = route(layout, "RT9_S", "RT9_A", "RT9_B", "RT9_C", "RT9_D", "RT9_F", "RT9_Q", "RT9_E");

        assertTrue(layout.isPathClear(longer, loc, false),
            "precondition: the railway refuses the longer route itself - " + Layout.getLastError()
            + " - so this claim is not about finding it");

        assertTrue(ends(layout.getPossiblePaths(loc, true)).contains("RT9_E"),
            "RT9_E is not offered to a train at RT9_S, though the route through A, B, C, D and F is clear: the shortest way to"
            + " RT9_Q runs through the terminus RT9_T, which the railway refuses, and the search never went on past RT9_Q"
            + " the longer way (OB-229).  Offered: " + ends(layout.getPossiblePaths(loc, true)));

        String why = layout.explainDestinations(loc).get("RT9_E");

        assertNull(why, "Why Not Moving? says RT9_E cannot be reached - '" + why + "' - by a clear route (OB-229)");
    }

    /**
     * THE CONTROL: without the longer way, E is still refused - the terminus is not let through.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testThroughTheTerminusAloneItIsStillRefused() throws Exception
    {
        Layout layout = theTwoWays("RU9", 3220, false);
        Locomotive loc = aTrainAt(layout, "RU9_S");

        assertFalse(ends(layout.getPossiblePaths(loc, true)).contains("RU9_E"),
            "RU9_E is offered where the only route runs through the terminus RU9_T - a route the railway refuses");

        String why = layout.explainDestinations(loc).get("RU9_E");

        // AND IT SAYS WHY (PTR-B1).  The track does connect, through the terminus, so "No track route leads there" would
        // send the operator looking for rails that are there.  The reason is the route check's own sentence.
        assertEquals(why, org.traincontrol.util.I18n.t("autolayout.errorIntermediateTerminusStation"),
            "Why Not Moving? says '" + why + "' of RU9_E, whose only route runs through the terminus RU9_T - the track"
            + " connects, so the reason is the terminus in the way, not missing track (PTR-B1)");

        // AND A STATION NO TRACK REACHES STILL SAYS SO: the question put to the track through termini must not turn
        // every missing route into a terminus.
        MarklinFeedback sensor = model.newFeedback(3235, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint("RU9_Z", true, sensor.getName());

        assertEquals(layout.explainDestinations(loc).get("RU9_Z"), org.traincontrol.util.I18n.t("autolayout.why.noRoute"),
            "RU9_Z is joined to nothing, and Why Not Moving? does not say no track route leads there");
    }

    /**
     * No tier sends a train round a loop to another copy of the square it is standing on (Adam, 2026-09-15).
     *
     * Reading the routes the terminus fix opens on his railway, Adam: *"These paths all make sense to me, except for the
     * circular ones like RampDown (northbound, reverse) -> RampDown (southbound, reverse).  These are the same point so
     * we should never do a round trip just to change direction."*  The right-click menu and autonomy already never
     * offer one - a copy of the square a train stands on reads as occupied by that train - but Return Home's planner
     * skipped only the exact copy, so it could plan the loop, and its agreement check would call that a disagreement.
     *
     * A square split into two copies, P_a and P_b, one block; a loop from P_a out and back into P_b through no terminus.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testNoRoundTripBackToTheTrainsOwnSquare() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "RR9_Pa", "RR9_Pb", "RR9_K", "RR9_L", "RR9_M" };
        boolean[] stations = { true, true, false, false, false };

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback sensor = model.newFeedback(3240 + i, null);

            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(names[i], stations[i], sensor.getName());
        }

        layout.getPoint("RR9_Pa").setBlock("RR9_P-square");
        layout.getPoint("RR9_Pb").setBlock("RR9_P-square");

        String[][] rails = { { "RR9_Pa", "RR9_K" }, { "RR9_K", "RR9_L" }, { "RR9_L", "RR9_M" }, { "RR9_M", "RR9_Pb" } };

        for (String[] rail : rails) layout.createEdge(rail[0], rail[1]);

        Locomotive loc = aTrainAt(layout, "RR9_Pa");

        assertTrue(layout.bfs(layout.getPoint("RR9_Pa"), layout.getPoint("RR9_Pb"), null) != null,
            "precondition: there is no loop from RR9_Pa back to RR9_Pb, so nothing here could be a round trip");

        // THE CONTROL: the menu never offered it.
        assertFalse(ends(layout.getPossiblePaths(loc, true)).contains("RR9_Pb"),
            "precondition: the right-click menu offers RR9_Pb, the other copy of the square the train stands on");

        // AND THE PLANNER AGREES.
        assertEquals(org.traincontrol.automation.HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "Return Home's planner would send a train at RR9_Pa round the loop to RR9_Pb - the same square, a round trip"
            + " just to change direction, which Adam ruled out and the menu never offers");
    }

    /**
     * No route passes another copy of the square the train is standing on (Adam, 2026-09-15).
     *
     * Reading what the terminus fix opened, Adam ruled out a round trip to another copy of a train's own square;
     * `testNoRoundTripBackToTheTrainsOwnSquare` above is that square as a DESTINATION.  Asked about routes that pass
     * one on the way - 22 of them on his railway, 14 offered by the right-click menu - he ruled: **"We need to refuse
     * both.  A copy makes a cycle."**
     *
     * P is one square drawn as two Points, Pa and Pb; a loop leaves Pa, comes back into Pb, and goes on to E.  The
     * only way to E passes the train's own square, so E is not somewhere it may be sent.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testNoRoutePassesAnotherCopyOfTheTrainsOwnSquare() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "RC9_Pa", "RC9_Pb", "RC9_K", "RC9_L", "RC9_E" };
        boolean[] stations = { true, true, false, false, true };

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback sensor = model.newFeedback(3260 + i, null);

            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(names[i], stations[i], sensor.getName());
        }

        layout.getPoint("RC9_Pa").setBlock("RC9_P-square");
        layout.getPoint("RC9_Pb").setBlock("RC9_P-square");

        String[][] rails = { { "RC9_Pa", "RC9_K" }, { "RC9_K", "RC9_L" }, { "RC9_L", "RC9_Pb" }, { "RC9_Pb", "RC9_E" } };

        for (String[] rail : rails) layout.createEdge(rail[0], rail[1]);

        Locomotive loc = aTrainAt(layout, "RC9_Pa");

        // The fixture, asserted without asking the search: the only track to E runs through the other copy.
        assertNotNull(layout.getEdge("RC9_Pb", "RC9_E"), "precondition: the fixture has no rail from RC9_Pb to RC9_E");
        assertTrue(layout.getPoint("RC9_Pa").isSamePlaceAs(layout.getPoint("RC9_Pb")),
            "precondition: the two copies are not one square, so nothing below is about a copy");

        assertFalse(ends(layout.getPossiblePaths(loc, true)).contains("RC9_E"),
            "RC9_E is offered to a train at RC9_Pa, and the only way there goes round a loop and back through RC9_Pb -"
            + " the same square the train is standing on.  Adam: a copy makes a cycle");

        assertEquals(org.traincontrol.automation.HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "Return Home's planner would use a route the railway refuses");
    }

    /**
     * Nor another copy of the square it is being sent to (Adam, 2026-09-15: *"a copy makes a cycle"*).
     *
     * The other half of the same ruling: S reaches Qa only by passing Qb, which is the same platform, so the train
     * would drive through its destination to reach it.  Qb itself is still somewhere to go, which is the control.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testNoRoutePassesAnotherCopyOfItsDestination() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "RD9_S", "RD9_Qa", "RD9_Qb", "RD9_M" };
        boolean[] stations = { true, true, true, false };

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback sensor = model.newFeedback(3270 + i, null);

            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(names[i], stations[i], sensor.getName());
        }

        layout.getPoint("RD9_Qa").setBlock("RD9_Q-square");
        layout.getPoint("RD9_Qb").setBlock("RD9_Q-square");

        String[][] rails = { { "RD9_S", "RD9_Qb" }, { "RD9_Qb", "RD9_M" }, { "RD9_M", "RD9_Qa" } };

        for (String[] rail : rails) layout.createEdge(rail[0], rail[1]);

        Locomotive loc = aTrainAt(layout, "RD9_S");

        assertTrue(ends(layout.getPossiblePaths(loc, true)).contains("RD9_Qb"),
            "control: RD9_Qb is not offered at all, so the refusal below would not be about the copy");

        assertFalse(ends(layout.getPossiblePaths(loc, true)).contains("RD9_Qa"),
            "RD9_Qa is offered, and the only way there passes RD9_Qb - the same platform - so the train drives through"
            + " its destination to reach it.  Adam: a copy makes a cycle");
    }

    /**
     * And Why Not Moving? says a lap is the only way, rather than blaming the track or a terminus (AMR-B1).
     *
     * Since the copy rule the search finds nothing for such a square, and "No track route leads there" sends the
     * operator looking for rails that are there - the same defect PTR-B1 fixed for a terminus in the way.  Worse, the
     * question the fallback puts to the TRACK may walk a lap, so a route round one would have been reported as a
     * terminus in the way when no terminus is involved.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testWhyNotMovingSaysTheOnlyWayThereIsALap() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "RL9_Pa", "RL9_Pb", "RL9_K", "RL9_L", "RL9_E", "RL9_Z" };
        boolean[] stations = { true, true, false, false, true, true };

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback sensor = model.newFeedback(3280 + i, null);

            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(names[i], stations[i], sensor.getName());
        }

        layout.getPoint("RL9_Pa").setBlock("RL9_P-square");
        layout.getPoint("RL9_Pb").setBlock("RL9_P-square");

        String[][] rails = { { "RL9_Pa", "RL9_K" }, { "RL9_K", "RL9_L" }, { "RL9_L", "RL9_Pb" }, { "RL9_Pb", "RL9_E" } };

        for (String[] rail : rails) layout.createEdge(rail[0], rail[1]);

        Locomotive loc = aTrainAt(layout, "RL9_Pa");

        String why = layout.explainDestinations(loc, true).get("RL9_E");

        assertEquals(why, org.traincontrol.util.I18n.t("autolayout.why.onlyALapLeadsThere"),
            "Why Not Moving? says '" + why + "' of RL9_E, whose only route doubles back through the other copy of the"
            + " square the train is standing on - the track is there and no terminus is in the way, so neither of those"
            + " two answers is true (AMR-B1)");

        // AND A STATION NO TRACK REACHES STILL SAYS SO: the lap answer must not swallow the missing-track one.
        assertEquals(layout.explainDestinations(loc, true).get("RL9_Z"),
            org.traincontrol.util.I18n.t("autolayout.why.noRoute"),
            "RL9_Z is joined to nothing, and Why Not Moving? does not say no track route leads there");
    }

    /** S -> X -> T -> Y -> Q -> E, T a terminus; and, when asked for, S -> A -> B -> C -> D -> F -> Q. */
    private static Layout theTwoWays(String tag, int s88, boolean withTheLongerWay) throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "S", "X", "T", "Y", "Q", "E", "A", "B", "C", "D", "F" };
        boolean[] stations = { true, false, true, false, false, true, false, false, false, false, false };

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback sensor = model.newFeedback(s88 + i, null);

            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(tag + "_" + names[i], stations[i], sensor.getName());
        }

        layout.getPoint(tag + "_T").setTerminus(true);

        String[][] rails = { { "S", "X" }, { "X", "T" }, { "T", "Y" }, { "Y", "Q" }, { "Q", "E" } };

        for (String[] rail : rails) layout.createEdge(tag + "_" + rail[0], tag + "_" + rail[1]);

        if (withTheLongerWay)
        {
            String[][] longer = { { "S", "A" }, { "A", "B" }, { "B", "C" }, { "C", "D" }, { "D", "F" }, { "F", "Q" } };

            for (String[] rail : longer) layout.createEdge(tag + "_" + rail[0], tag + "_" + rail[1]);
        }

        return layout;
    }

    private static Locomotive aTrainAt(Layout layout, String point) throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), point, false), "could not stand the train at " + point);

        return loc;
    }

    private static List<Edge> route(Layout layout, String... points)
    {
        List<Edge> out = new ArrayList<>();

        for (int i = 0; i + 1 < points.length; i++) out.add(layout.getEdge(points[i], points[i + 1]));

        return out;
    }

    private static List<String> ends(List<List<Edge>> paths)
    {
        List<String> out = new ArrayList<>();

        for (List<Edge> path : paths) out.add(path.get(path.size() - 1).getEnd().getName());

        return out;
    }
}
