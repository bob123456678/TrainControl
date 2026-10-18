package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Route;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train reversed on the throttle turns round in autonomy, from the throttle's call to the setup's facing (MT-488).
 *
 * Adam, 2026-09-24, running TrainControl in simulation: *"reversing it on the throttle has no effect on autonomy.  when no
 * autonomy or manual path is running, have the throttle change its autonomy direction."*  The window follows a turn
 * from the direction MESSAGE (`followDirectionChanges`), which a Central Station sends back for every command - and a
 * simulation sends back nothing unless it echoes packets, which it did not.  So the railway was right and the
 * simulation was mute; Adam, asked: *"You don't need to add that workaround - we can just require the test to echo
 * packets."*
 *
 * What was never claimed is the whole path: `core.testATrainIsPutOnlyWhereItCanStart` calls `flipFacing` itself.  This
 * reverses the train as the throttle does, with packets echoed as a station would, and asks the setup.
 *
 * On the frozen `live-snapshot`, at BottomMainA, with this class's own train standing there facing east.
 *
 * A reversal made while a route runs with autonomy idle is not written then (no setup edit during a run), and is followed
 * once the route has finished, through the same `followTheTurn`; two reversals during the route leave the facing as it was,
 * which is what the last direction says (Adam, on finding C8: *"it should reverse, but be careful to avoid duplication in
 * code mechanisms on how this is done"*).
 *
 * MUTATION: take `followDirectionChanges` out of `TrainControlUI.repaintLoc`, and this fails.  Take the `routeFinished`
 * call out of `MarklinRoute`, or `turnsHeldByARoute` out of `followTheTurn`, and the route tests fail.
 *
 * @author Adam
 */
public class testAThrottleReversalIsFollowedFromItsEcho
{
    private static final String PROBE = "throttle follow probe";

    /** BottomMainA's square. */
    private static final TileKey MAIN_A = new TileKey("1 - Main", 20, 12);

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static boolean echoWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the turn is followed by the window, and the window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // Simulating, with a window, in debug - the echo below answers only in debug.
        model = init(null, true, true, false, true);

        model.setNetworkCommState(false);

        // ECHOED, as a Central Station answers every command.
        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so nothing follows a turn");

        settle();

        assertNotNull(model.newMM2Locomotive(PROBE, 2397), "could not create this class's train");

        SwingUtilities.invokeAndWait(() -> session = ui.getAutonomySession());

        assertNotNull(session, "the sandbox copy holds no autonomy setup");

        assertEquals(session.getStationIndex().nameOf(MAIN_A), "BottomMainA",
            "the snapshot no longer calls " + MAIN_A + " BottomMainA, so this class is about a railway that has moved");

        // STANDING AT BOTTOMMAINA FACING EAST, on the running layout the window's own session built.
        SwingUtilities.invokeAndWait(() ->
        {
            session.placeLocomotive(MAIN_A, PROBE);
            session.setFacing(MAIN_A, Side.E);

            model.parseAuto(session.buildConfiguration());
        });

        settle();

        assertFalse(model.getAutoLayout().isRunning(), "precondition: something is running, and a turn is not followed"
            + " while it is");

        // What the window has seen, as a refresh at idle leaves it - otherwise the reversal below is a first sighting.
        SwingUtilities.invokeAndWait(() -> ui.reconcileFacingWhenIdle());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

        try
        {
            if (model != null) model.deleteLoc(PROBE);
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
     * Reversed as the throttle reverses it, and the setup follows.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheThrottleTurnsTheTrainRoundInAutonomy() throws Exception
    {
        assertEquals(session.getFacing(MAIN_A), Side.E, "precondition: the train is not recorded facing east");

        Locomotive train = model.getLocByName(PROBE);

        // THE THROTTLE'S OWN CALL - `TrainControlUI.switchDirection` stops the locomotive and sets the other direction.
        train.stop().setDirection(train.goingForward()
            ? Locomotive.locDirection.DIR_BACKWARD : Locomotive.locDirection.DIR_FORWARD);

        Side facing = session.getFacing(MAIN_A);

        for (long until = System.currentTimeMillis() + 10000; System.currentTimeMillis() < until
            && facing != Side.W; )
        {
            settle();

            Thread.sleep(100);

            facing = session.getFacing(MAIN_A);
        }

        assertEquals(facing, Side.W, "reversed on the throttle at BottomMainA, with its command echoed as a Central"
            + " Station echoes it, the train is still recorded facing east - Adam, 2026-09-24: \"reversing it on the"
            + " throttle has no effect on autonomy\"");
    }

    /**
     * Reversed while a route runs, the train is not turned in the setup then, and is once the route has finished.  The route
     * is marked running as `execRoute` marks it, and ended as its `finally` ends it.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAReversalDuringARouteIsFollowedWhenItEnds() throws Exception
    {
        standEast();

        Route hold = aHeldRoute();

        try
        {
            reverse();

            waitFor(() -> held().contains(PROBE), "the reversal was not seen by the window while a route runs");

            settle();

            assertEquals(session.getFacing(MAIN_A), Side.E, "the setup was turned while a route runs - Adam: \"There"
                + " should be no setup edit possible during a run\"");

            endTheRoute(hold);

            waitFor(() -> session.getFacing(MAIN_A) == Side.W, "the reversal made during a route was never followed: the"
                + " setup still has the train facing " + session.getFacing(MAIN_A) + " after the route ended");

            assertFalse(held().contains(PROBE), "the turn is still held after it was followed");
        }
        finally
        {
            endTheRoute(hold);

            standEast();
        }
    }

    /**
     * Two reversals while the route runs leave the setup as it was: it ends facing the way the last direction says, not
     * turned twice and not once.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTwoReversalsDuringARouteLeaveTheFacingAsTheLastOneSays() throws Exception
    {
        standEast();

        Route hold = aHeldRoute();

        try
        {
            reverse();

            waitFor(() -> held().contains(PROBE), "the first reversal was not seen by the window while a route runs");

            reverse();

            waitFor(() -> !held().contains(PROBE), "the second reversal did not cancel the first");

            endTheRoute(hold);

            settle();

            Thread.sleep(500);

            settle();

            assertEquals(session.getFacing(MAIN_A), Side.E, "reversed twice during a route, the train runs the way it did,"
                + " and the setup turned it");
        }
        finally
        {
            endTheRoute(hold);

            standEast();
        }
    }

    /**
     * A route's own locomotive direction command is followed, with the route running on for a second after it: the whole
     * path, from `MarklinRoute` to the setup.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARoutesOwnDirectionCommandIsFollowedWhenItEnds() throws Exception
    {
        standEast();

        final String name = "reversing route";

        java.util.List<org.traincontrol.base.RouteCommand> commands = new java.util.ArrayList<>();

        commands.add(org.traincontrol.base.RouteCommand.RouteCommandLocomotiveDirection(PROBE,
            model.getLocByName(PROBE).goingForward() ? Locomotive.locDirection.DIR_BACKWARD
            : Locomotive.locDirection.DIR_FORWARD));

        for (int i = 0; i < 8; i++)
        {
            commands.add(org.traincontrol.base.RouteCommand.RouteCommandAccessory(39,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, i % 2 == 0));
        }

        try
        {
            assertTrue(model.newRoute(name, commands, 0, Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "could not make the route");

            model.execRoute(name);

            waitFor(() -> model.getRoute(name).isExecuting(), "the route did not start");

            waitFor(() -> !model.getRoute(name).isExecuting(), "the route did not finish");

            waitFor(() -> session.getFacing(MAIN_A) == Side.W, "the route turned the train and the setup still has it"
                + " facing " + session.getFacing(MAIN_A) + " after the route ended");
        }
        finally
        {
            if (model.getRoute(name) != null)
            {
                model.getRoute(name).stopExecuting();

                model.deleteRoute(name);
            }

            standEast();
        }
    }

    private static final String HOLD = "held route";

    /** A route marked running as `execRoute` marks it, with nothing sent. */
    private static Route aHeldRoute()
    {
        java.util.List<org.traincontrol.base.RouteCommand> commands = new java.util.ArrayList<>();

        commands.add(org.traincontrol.base.RouteCommand.RouteCommandAccessory(39,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true));

        assertTrue(model.newRoute(HOLD, commands, 0, Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "could not make the route");

        assertTrue(model.getRoute(HOLD).setExecuting(), "the route would not run");

        assertFalse(model.isAutonomyRunning(), "precondition: autonomy is running, so this is not a route alone");

        return model.getRoute(HOLD);
    }

    /** The route's `finally`: no longer executing, and the window told; then the route is deleted. */
    private static void endTheRoute(Route route)
    {
        if (route.stopExecuting()) ui.routeFinished(route);

        if (model.getRoute(HOLD) != null) model.deleteRoute(HOLD);
    }

    /** The train, standing at BottomMainA facing east on the running layout, with what the window has seen levelled. */
    private static void standEast() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            session.setFacing(MAIN_A, Side.E);

            model.parseAuto(session.buildConfiguration());
        });

        settle();

        SwingUtilities.invokeAndWait(() -> ui.reconcileFacingWhenIdle());
    }

    /** The throttle's own call. */
    private static void reverse()
    {
        Locomotive train = model.getLocByName(PROBE);

        train.stop().setDirection(train.goingForward()
            ? Locomotive.locDirection.DIR_BACKWARD : Locomotive.locDirection.DIR_FORWARD);
    }

    /** The turns the window is holding for a route to end. */
    @SuppressWarnings("unchecked")
    private static java.util.Set<String> held()
    {
        try
        {
            java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("turnsHeldByARoute");

            field.setAccessible(true);

            return (java.util.Set<String>) field.get(ui);
        }
        catch (ReflectiveOperationException failed)
        {
            throw new IllegalStateException(failed);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition, String otherwise) throws Exception
    {
        for (long until = System.currentTimeMillis() + 10000; System.currentTimeMillis() < until; )
        {
            settle();

            if (condition.getAsBoolean()) return;

            Thread.sleep(100);
        }

        assertTrue(condition.getAsBoolean(), otherwise);
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 4; pass++) SwingUtilities.invokeAndWait(() -> { });
    }
}
