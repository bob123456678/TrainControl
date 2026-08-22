package core;

import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The limit on how many trains may be out at once is a limit, not a suggestion.
 *
 * The cap was counted from the locomotives the layout had REGISTERED as running - and a locomotive is
 * not registered until after its path has been locked, its accessories thrown, and the wait for those
 * accessories to confirm has finished. That wait deliberately holds no lock and takes up to a second
 * per accessory, so the gap between "this train has claimed its route" and "this train is counted" is
 * seconds wide. Two trains taking routes that do not touch could both cross it: each checked the cap,
 * each saw the other uncounted, and both went.
 *
 * The track was never at risk - the edges are exclusively locked either way - so this was never a
 * collision. It is a cap that is usually set for something the model cannot see, like what a booster
 * will carry or how much the operator wants to watch at once, and quietly running more trains than
 * asked for is not a small thing to get wrong.
 *
 * The race is made deterministic here rather than provoked with threads: a train that has locked its
 * path but is not yet registered is exactly the state the gap consists of, and it can be arranged
 * exactly. A timing test for this would pass on a fast machine while the bug was still there.
 */
public class testMaxActiveTrains
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        for (int sensor = 190; sensor <= 193; sensor++)
        {
            model.newFeedback(sensor, null);
        }
    }

    /**
     * A second train is refused while the first has locked a route but has not yet set off.
     *
     * The two routes do not touch, so nothing else refuses it: the edge locks are satisfied, the
     * sensors are clear, and the only thing that should say no is the cap.
     */
    @Test
    public void testATrainThatHasClaimedARouteCounts() throws Exception
    {
        Layout layout = twoSeparateRoutes();

        layout.setMaxActiveTrains(1);

        Locomotive first = model.getLocByName(model.getLocList().get(0));
        Locomotive second = model.getLocByName(model.getLocList().get(1));

        // running, with nothing to run: the cap is only enforced during autonomous operation, and
        // this layout has no locomotives assigned, so no threads start
        layout.runLocomotives();

        try
        {
            assertTrue(layout.configureAndLockPath(routeOne(layout), first),
                "the first train's route should lock - if this fails the rest proves nothing");

            assertFalse(layout.isPathClear(routeTwo(layout), second, false),
                "a second train was allowed out under a cap of one, because the first had locked its "
                + "route and was still waiting for its accessories to confirm - which is seconds, not "
                + "microseconds.  The cap counted only trains already registered as running");
        }
        finally
        {
            layout.stopLocomotives();
        }
    }

    /**
     * And a cap of two still lets the second train out.
     *
     * The same arrangement with room for both. A fix that simply refused the second train would pass
     * the test above and halve the railway.
     */
    @Test
    public void testTheCapStillAllowsWhatItAllows() throws Exception
    {
        Layout layout = twoSeparateRoutes();

        layout.setMaxActiveTrains(2);

        Locomotive first = model.getLocByName(model.getLocList().get(0));
        Locomotive second = model.getLocByName(model.getLocList().get(1));

        layout.runLocomotives();

        try
        {
            assertTrue(layout.configureAndLockPath(routeOne(layout), first),
                "the first train's route should lock");

            assertTrue(layout.isPathClear(routeTwo(layout), second, false),
                "with room for two, the second train must still be allowed out on a route that does "
                + "not touch the first");
        }
        finally
        {
            layout.stopLocomotives();
        }
    }

    /**
     * A claim that comes to nothing gives its place back.
     *
     * The failure mode of the fix itself, and the one worth guarding: a claim left behind lowers the
     * cap for the rest of the session, so the railway gets quieter and quieter with nothing to say
     * why. Releasing the path is one of the two ways a claim ends.
     */
    @Test
    public void testAReleasedRouteGivesTheSlotBack() throws Exception
    {
        Layout layout = twoSeparateRoutes();

        layout.setMaxActiveTrains(1);

        Locomotive first = model.getLocByName(model.getLocList().get(0));
        Locomotive second = model.getLocByName(model.getLocList().get(1));

        layout.runLocomotives();

        try
        {
            assertTrue(layout.configureAndLockPath(routeOne(layout), first), "the route should lock");

            layout.unlockPath(routeOne(layout), first);

            assertTrue(layout.isPathClear(routeTwo(layout), second, false),
                "the first train gave up its route, so its place under the cap should have gone with "
                + "it.  A claim that outlives its path lowers the limit permanently");
        }
        finally
        {
            layout.stopLocomotives();
        }
    }

    /**
     * Four points and two edges that share nothing.
     */
    private static Layout twoSeparateRoutes() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("CAP_a", true, "190");
        layout.createPoint("CAP_b", true, "191");
        layout.createPoint("CAP_c", true, "192");
        layout.createPoint("CAP_d", true, "193");

        layout.createEdge("CAP_a", "CAP_b");
        layout.createEdge("CAP_c", "CAP_d");

        return layout;
    }

    private static List<Edge> routeOne(Layout layout)
    {
        return Arrays.asList(layout.getEdge("CAP_a", "CAP_b"));
    }

    private static List<Edge> routeTwo(Layout layout)
    {
        return Arrays.asList(layout.getEdge("CAP_c", "CAP_d"));
    }
}
