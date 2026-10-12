package core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A standing train's tail follows the route it arrived along, past a junction.
 *
 * Adam, MT-333/MT-335, 2026-09-13: *"With 75 407 DB at bottommaina, length 5, segment length 1+1 between
 * bottommaina and bottommainapre, and 1 between tunnel and bottommainapre past the previous station,
 * EN57-947 may still be manually sent to botommainb, and the orange blocked track is not extended to the
 * segment between tunnel and bottommaina pre."*
 *
 * **Why it stopped.**  Beyond the first edge the tail walk has only the graph to go on, and at a junction
 * several roads lead back - so on 2026-09-07 the rule became *"end locking at the switch and call it a
 * day"*.  Five units of train with two measured behind the platform reach past BottomMainAPre, which is
 * a junction, and nothing claimed the road beyond it.
 *
 * **His ruling, 2026-09-13:** a tail that runs past a junction follows the route the train actually
 * arrived on.  A train placed by hand has no route, and still stops at the fork.
 *
 * **The fixture is that shape and nothing else:** a train drives A -> J -> S, and J is a junction because
 * a second road C -> J also arrives there.  The platform's own approach is one unit and the train is
 * three, so two units lie back past J - along A -> J, which is where it came from, and not along C -> J.
 *
 * The run is real - `executePath`, with the destination's sensor thrown - because what has to hold is
 * that an ARRIVAL leaves the route behind for the walk to read.  A claim that set it by hand would pass
 * with the arrival forgetting to.
 *
 * @author Adam
 */
public class testATailFollowsTheRouteItCameIn
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Layout layout;
    private static Locomotive loc;
    private static Integer lengthWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference and would otherwise
        // open Adam's real railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        loc = model.getLocByName(model.getLocList().get(0));

        lengthWas = loc.getTrainLength();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (loc != null) loc.setTrainLength(lengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Driven there, the tail claims the road it came in on, beyond the junction.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testADrivenTrainsTailFollowsItsRoutePastTheJunction() throws Exception
    {
        layout = aJunctionBehindAPlatform(2240);

        Point a = layout.getPoint("TF_A");
        Point s = layout.getPoint("TF_S");

        assertTrue(layout.moveLocomotive(loc.getName(), a.getName(), false),
            "could not stand the train at the start of its route");

        List<Edge> path = new ArrayList<>();
        path.add(layout.getEdge("TF_A", "TF_J"));
        path.add(layout.getEdge("TF_J", "TF_S"));

        model.setFeedbackState(a.getS88(), true);

        final Layout running = layout;

        Thread run = new Thread(() -> running.executePath(path, loc, 20, null));

        run.setDaemon(true);
        run.start();

        try
        {
            assertTrue(waitFor(() -> running.isRunning() && loc.getSpeed() > 0, 15000),
                "the train never set off, so there was no arrival for this claim to be about");

            // THROUGH THE JUNCTION'S OWN SENSOR FIRST.  The route passes J, and the run waits for each
            // point's sensor in turn - so a claim that threw only the platform's waited at J for ever.
            model.setFeedbackState(layout.getPoint("TF_J").getS88(), true);
            model.setFeedbackState(a.getS88(), false);

            waitFor(() -> false, 500);

            model.setFeedbackState(s.getS88(), true);
            model.setFeedbackState(layout.getPoint("TF_J").getS88(), false);

            assertTrue(waitFor(() -> !run.isAlive(), 30000),
                "the run never finished after the destination's sensor went on");
        }
        finally
        {
            layout.stopLocomotives();

            run.interrupt();
        }

        assertTrue(s.getCurrentLocomotive() == loc,
            "precondition: the train did not arrive at TF_S, so nothing here is about its tail");

        loc.setTrainLength(3);

        java.util.Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(layout.getEdge("TF_J", "TF_S")),
            "precondition: the platform's own approach is not covered, so the walk did not start");

        assertTrue(covered.containsKey(layout.getEdge("TF_A", "TF_J")),
            "a three-unit train drove A -> J -> S and stands at S behind a one-unit approach, so two units"
            + " lie back past the junction J - along A -> J, which is the road it came in on - and that"
            + " road is not claimed. Adam, MT-335: \"EN57-947 may still be manually sent to bottommainb,"
            + " and the orange blocked track is not extended\". His ruling: past a junction the tail"
            + " follows the route the train arrived on. Covered: " + covered.keySet());

        assertFalse(covered.containsKey(layout.getEdge("TF_C", "TF_J")),
            "the tail was claimed along C -> J as well, which is a road the train never used - that is"
            + " the \"claim every road back\" rule, which is not the one Adam chose");
    }

    /**
     * Placed there by hand, it still stops at the junction: there is no route to follow.
     *
     * The control, and the half of the ruling that keeps the fork rule: *"A train placed by hand (no
     * route) still stops at the fork, as now."*
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAPlacedTrainStillStopsAtTheJunction() throws Exception
    {
        Layout placed = aJunctionBehindAPlatform(2250);

        Point s = placed.getPoint("TF_S");

        assertTrue(placed.moveLocomotive(loc.getName(), s.getName(), false),
            "could not place the train at TF_S");

        s.setArrivedFrom(placed.entrySideOf(placed.getEdge("TF_J", "TF_S"), s));

        loc.setTrainLength(3);

        java.util.Map<Edge, Locomotive> covered = placed.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(placed.getEdge("TF_J", "TF_S")),
            "precondition: a placed train with a recorded arrival side covers nothing at all, so this"
            + " control is about a walk that never started");

        assertFalse(covered.containsKey(placed.getEdge("TF_A", "TF_J")),
            "a train placed by hand, with no route, claimed a road beyond the junction. Nothing says it"
            + " came that way - the fork rule still decides for a placed train");
    }

    /**
     * A -> J -> S, with C -> J making J a junction; every edge measured, the platform's approach at one.
     *
     * @param s88 the first of four sensor numbers this fixture uses
     * @return the graph
     */
    private static Layout aJunctionBehindAPlatform(int s88) throws Exception
    {
        Layout built = new Layout(model);

        built.createPoint("TF_A", true, model.newFeedback(s88, null).getName());
        built.createPoint("TF_C", true, model.newFeedback(s88 + 1, null).getName());
        built.createPoint("TF_J", false, model.newFeedback(s88 + 2, null).getName());
        built.createPoint("TF_S", true, model.newFeedback(s88 + 3, null).getName());

        built.createEdge("TF_A", "TF_J");
        built.createEdge("TF_C", "TF_J");
        built.createEdge("TF_J", "TF_S");

        built.getEdge("TF_A", "TF_J").setLength(3);
        built.getEdge("TF_C", "TF_J").setLength(3);
        built.getEdge("TF_J", "TF_S").setLength(1);

        // Which side the train comes in by at the platform, so a placed train has a tail to walk.
        built.getEdge("TF_J", "TF_S").setEntrySide("W");

        return built;
    }

    private static boolean waitFor(BooleanSupplier until, long ms)
    {
        long deadline = System.currentTimeMillis() + ms;

        while (System.currentTimeMillis() < deadline)
        {
            if (until.getAsBoolean()) return true;

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return until.getAsBoolean();
    }
}
