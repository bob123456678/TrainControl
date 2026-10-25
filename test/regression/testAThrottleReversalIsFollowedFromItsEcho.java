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
 * A followed turn turns the train's icon on the diagram too, to the facing the caption's arrow shows (MT-759; Adam: "make
 * sure the icon matches the label").  While autonomy or Return Home runs, a direction command from the throttle or the
 * Central Station changes neither, then or after.
 *
 * MUTATION: take `followDirectionChanges` out of `TrainControlUI.repaintLoc`, and this fails.  Take the `routeFinished`
 * call out of `MarklinRoute`, or `turnsHeldByARoute` out of `followTheTurn`, and the route tests fail.  Take the
 * `refreshUI` out of `AutonomySession.moveOntoFacingCopy`, and the icon is left facing east in three.
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
        model = support.TestStation.simulatedWithWindowInDebug();

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

            // AND THE DIAGRAM WATCHING THE RAILWAY JUST BUILT, as the window's own loads leave it (`autonomyLoadedFromDiagram`)
            ui.getDiagramMonitorDriver().bind(session);
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
        standEast();

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

        assertTheDiagramShows(Side.W);
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

            assertEquals(iconFacing(), Side.E, "the diagram turned the train while a route runs");

            endTheRoute(hold);

            waitFor(() -> session.getFacing(MAIN_A) == Side.W, "the reversal made during a route was never followed: the"
                + " setup still has the train facing " + session.getFacing(MAIN_A) + " after the route ended");

            assertFalse(held().contains(PROBE), "the turn is still held after it was followed");

            assertTheDiagramShows(Side.W);
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

            assertTheDiagramShows(Side.W);
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

    /**
     * While autonomy runs, a direction command from the throttle or from the Central Station changes nothing: not the setup,
     * not the copy the train stands on (which the caption reads) and not the icon, then or after the run (Adam, on MT-759:
     * "while autonomy is running, make sure manual/central station direction commands don't alter the config, since at
     * that point it would be the user correcting an invalid facing").  A hand send runs the railway as autonomy does
     * (`Layout.isRunning`), so this is its guard too.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testADirectionCommandWhileAutonomyRunsChangesNothing() throws Exception
    {
        final java.lang.reflect.Field running = org.traincontrol.automation.Layout.class.getDeclaredField("running");

        running.setAccessible(true);

        ignoredWhile(() -> set(running, model.getAutoLayout(), true), () -> set(running, model.getAutoLayout(), false),
            "autonomy runs");
    }

    /**
     * And while Return Home stages the trains, the same.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testADirectionCommandWhileReturnHomeRunsChangesNothing() throws Exception
    {
        final java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        staging.setAccessible(true);

        ignoredWhile(() -> set(staging, ui, true), () -> set(staging, ui, false), "Return Home runs");
    }

    /**
     * Reverses the train from the throttle and then from the Central Station while `begin` holds the railway, and asks the
     * setup, the railway and the diagram, then and once `end` has let it go and another message has come in.
     */
    private static void ignoredWhile(Runnable begin, Runnable end, String what) throws Exception
    {
        standEast();

        Locomotive train = model.getLocByName(PROBE);

        final boolean forwardWas = train.goingForward();

        SwingUtilities.invokeAndWait(begin);

        try
        {
            // THE THROTTLE, echoed; THE CENTRAL STATION'S OWN COMMAND, turning it back; and the throttle again, so that it
            // ends turned round and a turn left over would show after the run
            reverse();

            settle();

            Thread.sleep(500);

            fromTheCentralStation(train, forwardWas);

            settle();

            Thread.sleep(500);

            assertEquals(train.goingForward(), forwardWas, "precondition: the Central Station's command was not read");

            reverse();

            settle();

            Thread.sleep(500);

            settle();

            assertNothingTurned("a direction command while " + what);
        }
        finally
        {
            SwingUtilities.invokeAndWait(end);
        }

        // AND NOTHING LEFT TO FOLLOW AFTERWARDS: the idle refresh, then another message about the train
        SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

        train.setSpeed(0);

        settle();

        Thread.sleep(500);

        settle();

        assertNothingTurned("a direction command made while " + what + ", once it was over,");

        assertFalse(held().contains(PROBE), "a direction command while " + what + " is held for later");

        standEast();
    }

    /** The setup, the copy the train stands on and the icon, all still facing east. */
    private static void assertNothingTurned(String what) throws Exception
    {
        assertEquals(session.getFacing(MAIN_A), Side.E, what + " turned the train in the setup");

        assertEquals(session.facingOnTheRailway(MAIN_A, model.getAutoLayout()), Side.E, what + " turned the train on the"
            + " railway, which the caption's arrow reads");

        assertEquals(iconFacing(), Side.E, what + " turned the train's icon on the diagram");
    }

    /** A direction command as a Central Station sends one, read as every message from it is. */
    private static void fromTheCentralStation(Locomotive train, boolean forward)
    {
        int uid = ((org.traincontrol.marklin.MarklinLocomotive) train).getIntUID();

        org.traincontrol.marklin.udp.CS2Message command = new org.traincontrol.marklin.udp.CS2Message(
            org.traincontrol.marklin.udp.CS2Message.CMD_LOCO_DIRECTION,
            new byte[] {(byte) (uid >> 24), (byte) (uid >> 16), (byte) (uid >> 8), (byte) uid, (byte) (forward ? 1 : 2)});

        model.receiveMessage(new org.traincontrol.marklin.udp.CS2Message(command.getCommand(), command.getHash(), true,
            command.getData()));
    }

    private static void set(java.lang.reflect.Field field, Object on, boolean value)
    {
        try
        {
            field.setBoolean(on, value);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The facing the diagram's icon, the station caption's arrow and the setup show for the train at BottomMainA, all one
     * (MT-759; Adam: "make sure the icon matches the label").
     */
    private static void assertTheDiagramShows(Side facing) throws Exception
    {
        // WAITED FOR, NOT READ AT ONCE: `flipFacing` writes the setup and then, a statement later on the event thread,
        // stands the train on the other copy, and a caller that has just seen the setup turn can read the railway between
        // the two - which a loaded machine makes likely.
        waitFor(() -> railwayFacing() == facing, "the railway, which the caption's arrow reads, does not have the train"
            + " facing " + facing + ": it has it facing " + railwayFacing());

        assertEquals(railwayFacing(), facing, "the railway, which the caption's arrow reads, does not have the train"
            + " facing " + facing);

        waitFor(() -> iconFacing() == facing, "the train was turned to face " + facing + " and its icon on the diagram"
            + " still faces " + iconFacing() + " - Adam, on MT-759: \"the label's arrow direction changes, but it does not"
            + " match the locomotive icon direction on the track diagram\"");
    }

    /** The way the railway has the train at BottomMainA facing (the caption's arrow), read on the event thread. */
    private static Side railwayFacing()
    {
        final Side[] read = new Side[1];

        try
        {
            SwingUtilities.invokeAndWait(() -> read[0] = session.facingOnTheRailway(MAIN_A, model.getAutoLayout()));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        return read[0];
    }

    /** Which way the diagram draws the train standing at BottomMainA, as its squares were last told, or null. */
    private static Side iconFacing()
    {
        final org.traincontrol.automationui.TileOverlay[] drawn = new org.traincontrol.automationui.TileOverlay[1];

        try
        {
            SwingUtilities.invokeAndWait(() -> drawn[0] = ui.getDiagramTileRegistry().overlayAt(MAIN_A));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        return drawn[0] == null || !drawn[0].isParked() ? null : drawn[0].getFacing();
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

    /** The train, standing at BottomMainA facing east on the running layout, with what the window has seen leveled. */
    private static void standEast() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            session.setFacing(MAIN_A, Side.E);

            model.parseAuto(session.buildConfiguration());

            // AND THE DIAGRAM WATCHING THE RAILWAY JUST BUILT, as the window's own loads leave it (`autonomyLoadedFromDiagram`)
            ui.getDiagramMonitorDriver().bind(session);
        });

        settle();

        SwingUtilities.invokeAndWait(() -> ui.reconcileFacingWhenIdle());

        // DRAWN FROM THE RAILWAY JUST BUILT, and nothing left for the diagram to look at: a look still owed would redraw the
        // icon after the turn whether or not the turn asked for it
        waitFor(() -> !theDiagramOwesALook(), "the diagram never looked at the railway just built");

        waitFor(() -> iconFacing() == Side.E, "the train stood facing east is drawn facing " + iconFacing());
    }

    /** Whether the diagram has been told something moved and has not yet looked (`DiagramMonitor.markDirty`). */
    private static boolean theDiagramOwesALook()
    {
        try
        {
            Object driver = ui.getDiagramMonitorDriver();

            java.lang.reflect.Field monitor = driver.getClass().getDeclaredField("monitor");

            monitor.setAccessible(true);

            Object watching = monitor.get(driver);

            java.lang.reflect.Field dirty = watching.getClass().getDeclaredField("dirty");

            dirty.setAccessible(true);

            return ((java.util.concurrent.atomic.AtomicBoolean) dirty.get(watching)).get();
        }
        catch (ReflectiveOperationException failed)
        {
            throw new IllegalStateException(failed);
        }
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
