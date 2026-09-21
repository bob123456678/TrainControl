package core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
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
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A train part-way through a run has ONE tail, behind where it is now (MT-438).
 *
 * **What Adam photographed.** MT-438, 2026-09-21: an orange line along a road his train never drove,
 * left over after it departed - *"coloring switch 99 and 100 is still an error regardless"*, and
 * *"why is the tail from bottommainapre to bottommaina not orange.  it's almost as if you are shifting
 * the location of the train"*.
 *
 * **Why a running train has several tails.** Locking a path RESERVES every point on it for the
 * locomotive - `Point.reserve`, which deliberately does not sweep, because the reservation is what
 * holds a junction behind the train against a second train reaching it another way. `walkStandingTrains`
 * then walks every Point that holds the locomotive, so each reserved point claims a tail of its own,
 * each guessed from whatever arrival side that point happens to carry. A point the train passed through
 * ten minutes ago claims track it is nowhere near.
 *
 * **His ruling, 2026-09-21:** *"There is no ambiguity - the tail is certain at departure and shouldn't
 * change. You also know which way the train went when it started running. Just unlock the rest of the
 * diagram once the tail by length is far enough away."*  Which is `tailHasProvablyPassed` - `behind >=
 * trainLength` - the rule this railway already uses to hand an edge back as the head pulls away from it.
 *
 * So: one tail per train, anchored where the train IS, along the road it drove, and everything further
 * back released.
 *
 * The run is real - `executePath` with the sensors thrown by hand - because the state this is about only
 * exists while a path is locked.
 *
 * MUTATION: walk every Point that holds the locomotive again - drop the `alreadyWalked` guard and the
 * head anchor in `walkStandingTrains` - and two of the three claims below go red: the leg behind the
 * departure square and the leg ahead of the train are both claimed as its tail.
 *
 * @author Adam
 */
public class testARunningTrainHasOneTail
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static Layout layout;

    private static Locomotive loc;

    private static Integer lengthWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
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

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Part-way through a run, one tail is claimed and it is behind the train.
     *
     * @throws Exception from the run
     */
    @Test
    public void testOnlyOneTailIsClaimedMidRun() throws Exception
    {
        layout = aRunThroughAJunction(2710);

        final Point start = layout.getPoint("TR_A");
        final Point junction = layout.getPoint("TR_J");
        final Point platform = layout.getPoint("TR_S");

        assertTrue(layout.moveLocomotive(loc.getName(), start.getName(), false),
            "could not stand the train at the start of its route");

        // ONE UNIT TO DEPART WITH, because the room rule judges the destination before the run is
        // allowed: three units into a one-unit approach is refused, and the first draft of this test
        // never set off at all.  The length is raised once the train is at the junction, which is
        // where the claim below is asked.
        loc.setTrainLength(1);

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge("TR_A", "TR_J"));
        path.add(layout.getEdge("TR_J", "TR_S"));

        model.setFeedbackState(start.getS88(), true);

        final Layout running = layout;

        Thread run = new Thread(() -> running.executePath(path, loc, 20, null));

        run.setDaemon(true);
        run.start();

        try
        {
            assertTrue(waitFor(() -> running.isRunning() && loc.getSpeed() > 0, 15000),
                "the train never set off, so there is no run for this claim to be about");

            // THE TRAIN IS AT THE JUNCTION NOW: its own sensor on, the one it came from off.
            model.setFeedbackState(junction.getS88(), true);
            model.setFeedbackState(start.getS88(), false);

            // REACHED, NOT RESERVED.  `junction.getCurrentLocomotive()` is true the moment the path is
            // LOCKED - the lock reserves every point on it - so waiting on that asks about a train
            // still standing at the start.  The milestones are what the run reports having reached.
            assertTrue(waitFor(() -> reached(junction), 15000),
                "the train never reached the junction, so this is not about a run in progress."
                + "  Milestones: " + names(running.getReachedMilestones(loc)));

            // ONE UNIT, AND THAT IS THE WHOLE DISCRIMINATOR.  Each leg measures one, so a one-unit
            // train at the junction lies on the leg it came in along and can reach nothing further
            // back - `TR_B>TR_A` is a leg it cannot be on.  A claim there can only come from a SECOND
            // anchor: the Point it departed from, still holding the locomotive because the lock
            // reserved it.  With three units the same claim would be honest, which is why the first
            // draft of this test could not tell the two apart.
            Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

            Set<String> mine = new LinkedHashSet<>();

            for (Map.Entry<Edge, Locomotive> claim : covered.entrySet())
            {
                if (loc.equals(claim.getValue()))
                {
                    mine.add(claim.getKey().getStart().getName() + ">" + claim.getKey().getEnd().getName());
                }
            }

            // ONE TAIL, and it is the road it drove in on.  Adam: "the tail is certain at departure and
            // shouldn't change."
            assertTrue(mine.contains("TR_A>TR_J"),
                "the tail of a train standing at the junction is not claimed on the road it drove in"
                + " along.  Claimed: " + mine);

            assertFalse(mine.contains("TR_C>TR_J"),
                "the tail was claimed along TR_C>TR_J, a road the train has never been on - which is"
                + " what Adam photographed at switches 99 and 100.  Claimed: " + mine);

            assertFalse(mine.contains("TR_B>TR_A"),
                "a ONE-unit train at the junction is claimed to be lying on TR_B>TR_A, two legs back."
                + "  That claim can only come from a second anchor - the square it departed from,"
                + " still holding the locomotive because the lock reserved it and `Point.reserve`"
                + " deliberately does not sweep.  A train has one body in one place.  Claimed: "
                + mine);

            assertFalse(mine.contains("TR_J>TR_S"),
                "the leg AHEAD of the train is claimed as its tail, which is the destination's own"
                + " reservation being walked as though the train were standing there - Adam's \"it is"
                + " almost as if you are shifting the location of the train\".  Claimed: " + mine);
        }
        finally
        {
            layout.stopLocomotives();

            run.interrupt();

            model.setFeedbackState(junction.getS88(), false);
        }
    }

    /**
     * Whether the run has reported reaching a point.
     *
     * @param where the point
     * @return whether it is among the milestones
     */
    private static boolean reached(Point where)
    {
        List<Point> milestones = layout.getReachedMilestones(loc);

        return milestones != null && milestones.contains(where);
    }

    /**
     * The milestones' names, for a failure message.
     *
     * @param milestones what the run has reached
     * @return their names
     */
    private static String names(List<Point> milestones)
    {
        if (milestones == null) return "(none)";

        List<String> out = new ArrayList<>();

        for (Point p : milestones) out.add(p.getName());

        return out.toString();
    }

    /**
     * A run of three points with a second road into the junction and a leg behind the start.
     *
     * `TR_B -> TR_A -> TR_J -> TR_S`, plus `TR_C -> TR_J`.  The leg behind the start is what makes a
     * phantom claim visible: it is the one a point the train has left would claim.
     *
     * @param s88 the first of five feedback addresses
     * @return the railway
     * @throws Exception from the model
     */
    private static Layout aRunThroughAJunction(int s88) throws Exception
    {
        Layout built = new Layout(model);

        built.createPoint("TR_B", true, model.newFeedback(s88, null).getName());
        built.createPoint("TR_A", true, model.newFeedback(s88 + 1, null).getName());
        built.createPoint("TR_C", true, model.newFeedback(s88 + 2, null).getName());
        built.createPoint("TR_J", false, model.newFeedback(s88 + 3, null).getName());
        built.createPoint("TR_S", true, model.newFeedback(s88 + 4, null).getName());

        built.createEdge("TR_B", "TR_A");
        built.createEdge("TR_A", "TR_J");
        built.createEdge("TR_C", "TR_J");
        built.createEdge("TR_J", "TR_S");

        built.getEdge("TR_B", "TR_A").setLength(1);
        built.getEdge("TR_A", "TR_J").setLength(1);
        built.getEdge("TR_C", "TR_J").setLength(1);
        built.getEdge("TR_J", "TR_S").setLength(1);

        built.getEdge("TR_A", "TR_J").setEntrySide("W");
        built.getEdge("TR_C", "TR_J").setEntrySide("W");
        built.getEdge("TR_J", "TR_S").setEntrySide("W");
        built.getEdge("TR_B", "TR_A").setEntrySide("W");

        return built;
    }

    /**
     * Waits for a condition, polling.
     *
     * @param until what to wait for
     * @param millis how long at most
     * @return whether it came true
     */
    private static boolean waitFor(BooleanSupplier until, long millis)
    {
        long deadline = System.currentTimeMillis() + millis;

        while (System.currentTimeMillis() < deadline)
        {
            if (until.getAsBoolean()) return true;

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException stopped)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return until.getAsBoolean();
    }
}
