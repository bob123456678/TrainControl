package ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A route that would switch track a train is on, fired at each of its doors on the real window: by its sensor (MT-506),
 * and from the route list and from its tile on the track diagram (MT-507) - Adam, 2026-09-25: automated tests supersede
 * the MTs they answer.
 *
 * The train is on switch A with its path locked by a dispatch, as `regression.testARouteDoesNotThrowSwitchesUnderATrain`
 * builds it, and the route is fired from inside that dispatch; commands are echoed back as the Central Station would
 * (`DEBUG_SIMULATE_PACKETS`, put back after), so a switch reads thrown only when its command went out.  The tile is one
 * of the route tiles on his own diagram, given this route for the length of the claim.
 *
 * A route carrying an emergency stop is never asked about - Adam, 2026-09-01: *"Emergency stop should never conflict or
 * prompt."* - so the question MT-507 describes is asked of the same route without its stop, and the route with its stop
 * is fired too, to show what each door does with it.
 *
 * @author Adam
 */
public class testARouteOverATrainAtItsDoors
{
    private static final long POWER_PATIENCE_MS = 15000;
    private static final int SWITCH_A = 84;
    private static final int SWITCH_B = 93;
    private static final String S88 = "48401";
    private static final String ROUTE_S88 = "48409";
    private static final String ROUTE = "MT-507 route";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static boolean echoWas;

    private static final List<String> logged = Collections.synchronizedList(new ArrayList<>());
    private static Handler tap;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the doors belong to a window, and a window needs a display");
        }

        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        tap = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                logged.add(String.valueOf(record.getMessage()));
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            if (tap != null) Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

            if (model != null) model.clearAutoLayout();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void takeDownDialogs() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()) window.dispose();
            }
        });

        if (model.getRoute(ROUTE) != null) model.deleteRoute(ROUTE);
    }

    /**
     * Fired by its sensor while a train holds switch A: nothing is asked, switch A is not thrown, switch B is, the power
     * goes off, and the log names switch A as held back (MT-506).
     *
     * MUTATION: have the automatic door ask, refuse the whole route, or throw the held switch, and this fails.
     *
     * @throws Exception from the model or the window
     */
    @Test
    public void testFiredByItsSensorItSkipsOnlyTheSwitchUnderTheTrain() throws Exception
    {
        final boolean[] seen = new boolean[4];
        final String[] asked = new String[1];

        if (!model.isFeedbackSet(ROUTE_S88)) model.newFeedback(Integer.parseInt(ROUTE_S88), null);

        model.setFeedbackState(ROUTE_S88, false);

        int from = logged.size();

        onARouteOverATrain((underTheTrain, otherSwitch) ->
        {
            // STEP 2: the route, with an s88 trigger, armed.
            MarklinRoute route = new MarklinRoute(model, ROUTE, 84907, commands(true), Integer.parseInt(ROUTE_S88),
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

            assertTrue(model.newRoute(route), "precondition: the route could not be added to the route list");

            try
            {
                Thread.sleep(500);

                // STEP 3: the sensor set.
                model.setFeedbackState(ROUTE_S88, true);

                long until = System.currentTimeMillis() + POWER_PATIENCE_MS;

                while (System.currentTimeMillis() < until && model.getPowerState())
                {
                    asked[0] = asked[0] != null ? asked[0] : questionShowing();

                    Thread.sleep(100);
                }

                Thread.sleep(600);

                asked[0] = asked[0] != null ? asked[0] : questionShowing();

                seen[0] = underTheTrain.isSwitched();
                seen[1] = otherSwitch.isSwitched();
                seen[2] = model.getPowerState();
            }
            finally
            {
                route.disable();

                model.setFeedbackState(ROUTE_S88, false);
            }
        });

        assertNull(asked[0], "a route fired by its sensor put a question up (MT-506): " + asked[0]);

        assertFalse(seen[0], "the route fired by its sensor threw switch A, under the train (MT-506)");
        assertTrue(seen[1], "the route fired by its sensor did not throw switch B (MT-506)");
        assertFalse(seen[2], "the route fired by its sensor did not cut the power (MT-506)");

        String held = I18n.f("route.refusedAccessoryOnActivePath", ROUTE, accessoryName(SWITCH_A));

        assertTrue(new ArrayList<>(logged.subList(from, logged.size())).contains(held), "the log does not name switch A as"
            + " held back (MT-506): " + logged.subList(from, logged.size()));
    }

    /**
     * From the route list and from its tile, the route over the train is asked about with one question - that it would
     * switch track a train is on, run anyway? - and Cancel runs none of it: no switch thrown, the power still on (MT-507).
     * The same route with its emergency stop, as the entry describes it, is asked about at neither door and runs guarded.
     *
     * MUTATION: have either door fire on Cancel, or ask a route with a stop, and this fails.
     *
     * @throws Exception from the model or the window
     */
    @Test
    public void testCancelAtEitherDoorRunsNothing() throws Exception
    {
        final LayoutLabel tile = aRouteTile();

        assertNotNull(tile, "precondition: his diagram has no route tile to fire the route from");

        final org.traincontrol.base.Route tileWas = tile.getComponent().getRoute();

        final List<String[]> questions = new ArrayList<>();
        final List<boolean[]> after = new ArrayList<>();
        final String[] withAStop = new String[2];
        final boolean[][] guarded = new boolean[2][];

        try
        {
            // WITHOUT THE STOP: the question, and Cancel.
            onARouteOverATrain((underTheTrain, otherSwitch) ->
            {
                MarklinRoute route = new MarklinRoute(model, ROUTE, 84907, commands(false), 0,
                    MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

                assertTrue(model.newRoute(route), "precondition: the route could not be added to the route list");

                tile.getComponent().setRoute(route);

                for (int door = 0; door < 2; door++)
                {
                    fire(door, tile);

                    javax.swing.JDialog question = awaitQuestion();

                    assertNotNull(question, "the " + doorName(door) + " did not ask about the route over the train (MT-507)");

                    questions.add(new String[] {textOf(question), String.valueOf(initialValueOf(question))});

                    press(question, I18n.t("ui.cancel"));

                    Thread.sleep(1000);

                    after.add(new boolean[] {underTheTrain.isSwitched(), otherSwitch.isSwitched(), model.getPowerState()});
                }
            });

            // WITH THE STOP, AS THE ENTRY'S ROUTE HAS IT: no question at either door, and it runs guarded.
            for (int door = 0; door < 2; door++)
            {
                final int which = door;

                onARouteOverATrain((underTheTrain, otherSwitch) ->
                {
                    MarklinRoute route = new MarklinRoute(model, ROUTE, 84907, commands(true), 0,
                        MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

                    assertTrue(model.newRoute(route), "precondition: the route could not be added to the route list");

                    tile.getComponent().setRoute(route);

                    fire(which, tile);

                    long until = System.currentTimeMillis() + POWER_PATIENCE_MS;

                    while (System.currentTimeMillis() < until && model.getPowerState())
                    {
                        withAStop[which] = withAStop[which] != null ? withAStop[which] : questionShowing();

                        Thread.sleep(100);
                    }

                    Thread.sleep(600);

                    withAStop[which] = withAStop[which] != null ? withAStop[which] : questionShowing();

                    guarded[which] = new boolean[] {underTheTrain.isSwitched(), otherSwitch.isSwitched(),
                        model.getPowerState()};

                    model.deleteRoute(ROUTE);
                });
            }
        }
        finally
        {
            tile.getComponent().setRoute(tileWas);
        }

        String expected = I18n.f("layout.ui.confirmRouteActiveRoute", ROUTE, accessoryName(SWITCH_A));

        for (int door = 0; door < 2; door++)
        {
            assertEquals(questions.get(door)[0], expected, "the " + doorName(door) + " does not ask whether to run a route"
                + " that would switch track a train is on (MT-507)");

            assertEquals(questions.get(door)[1], I18n.t("ui.cancel"), "the " + doorName(door) + "'s question does not"
                + " start on Cancel");

            assertFalse(after.get(door)[0], "after Cancel at the " + doorName(door) + ", switch A was thrown (MT-507)");
            assertFalse(after.get(door)[1], "after Cancel at the " + doorName(door) + ", switch B was thrown (MT-507)");
            assertTrue(after.get(door)[2], "after Cancel at the " + doorName(door) + ", the power is off (MT-507)");

            assertNull(withAStop[door], "the " + doorName(door) + " asked about a route carrying an emergency stop - Adam,"
                + " 2026-09-01: \"Emergency stop should never conflict or prompt.\": " + withAStop[door]);

            assertNotNull(guarded[door], "precondition: the route with a stop was not fired at the " + doorName(door));

            assertFalse(guarded[door][0], "the route with a stop, fired at the " + doorName(door) + ", threw switch A under"
                + " the train");
            assertTrue(guarded[door][1], "the route with a stop, fired at the " + doorName(door) + ", did not throw switch B");
            assertFalse(guarded[door][2], "the route with a stop, fired at the " + doorName(door) + ", did not cut the"
                + " power");
        }
    }

    // ---------------------------------------------------------------- the fixture

    /** What is done with the route, from inside the dispatch that holds switch A. */
    private interface WithTheTrainOnA
    {
        void run(MarklinAccessory underTheTrain, MarklinAccessory otherSwitch) throws Exception;
    }

    /**
     * A train dispatched over switch A, and inside that dispatch `probe`.  The power is on before, and put back after;
     * both switches straight before.
     */
    private static void onARouteOverATrain(WithTheTrainOnA probe) throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("RD_A", false, null);
        layout.createPoint("RD_B", true, S88);

        Edge ab = layout.createEdge("RD_A", "RD_B");

        final MarklinAccessory underTheTrain = switchAt(SWITCH_A);
        final MarklinAccessory otherSwitch = switchAt(SWITCH_B);

        underTheTrain.setSwitched(false);
        otherSwitch.setSwitched(false);

        ab.addConfigCommand(underTheTrain.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("RD_A").setLocomotive(loc);

        model.go();

        assertTrue(model.waitForPowerState(true, POWER_PATIENCE_MS), "precondition: the power has to be ON, or the"
            + " route's stop has nothing to turn off");

        final Exception[] failed = new Exception[1];
        final Error[] broke = new Error[1];

        // ONCE PER DISPATCH: the layout calls back more than once with the dispatch started, and the doors are to be
        // fired once.
        final boolean[] ran = new boolean[1];

        layout.setCallback("route doors probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || ran[0]) return null;

            ran[0] = true;

            try
            {
                probe.run(underTheTrain, otherSwitch);
            }
            catch (Exception e)
            {
                failed[0] = e;
            }
            catch (Error e)
            {
                broke[0] = e;
            }

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");
        }
        finally
        {
            model.go();

            model.waitForPowerState(true, POWER_PATIENCE_MS);

            otherSwitch.setSwitched(false);
            underTheTrain.setSwitched(false);

            if (model.getRoute(ROUTE) != null) model.deleteRoute(ROUTE);

            model.clearAutoLayout();
        }

        assertTrue(ran[0], "precondition: the dispatch never called back, so nothing was fired");

        if (broke[0] != null) throw broke[0];
        if (failed[0] != null) throw failed[0];
    }

    /** A, B and - where asked - an emergency stop. */
    private static List<RouteCommand> commands(boolean stop)
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(SWITCH_A, Accessory.accessoryDecoderType.MM2, true));
        commands.add(RouteCommand.RouteCommandAccessory(SWITCH_B, Accessory.accessoryDecoderType.MM2, true));

        if (stop) commands.add(RouteCommand.RouteCommandStop());

        return commands;
    }

    private static MarklinAccessory switchAt(int address)
    {
        MarklinAccessory present = model.getAccessoryByAddressIfPresent(address, Accessory.accessoryDecoderType.MM2);

        return present != null ? present : model.newSwitch(address, Accessory.accessoryDecoderType.MM2, false);
    }

    private static String accessoryName(int address)
    {
        return switchAt(address).getName();
    }

    // ---------------------------------------------------------------- the doors

    /** Door 0 is the route list's, door 1 the route's tile on the track diagram. */
    private static void fire(int door, LayoutLabel tile) throws Exception
    {
        if (door == 0)
        {
            SwingUtilities.invokeLater(() -> ui.executeRoute(ROUTE));
        }
        else
        {
            SwingUtilities.invokeLater(() -> tile.dispatchEvent(new java.awt.event.MouseEvent(tile,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false,
                java.awt.event.MouseEvent.BUTTON1)));
        }
    }

    private static String doorName(int door)
    {
        return door == 0 ? "route list" : "route's tile on the track diagram";
    }

    /** One of his route tiles on the main window's diagram. */
    private static LayoutLabel aRouteTile() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null || session.getGraph() == null) return null;

        List<org.traincontrol.automationui.TileGraph.TileKey> routes = new ArrayList<>();

        for (java.util.Map.Entry<org.traincontrol.automationui.TileGraph.TileKey,
            org.traincontrol.base.LayoutDiagramComponent> entry : session.getGraph().getTiles().entrySet())
        {
            if (entry.getValue() != null && entry.getValue().isRoute()) routes.add(entry.getKey());
        }

        routes.sort(java.util.Comparator.comparing(Object::toString));

        for (org.traincontrol.automationui.TileGraph.TileKey key : routes)
        {
            for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(key))
            {
                if (label.getComponent() != null && label.getComponent().isRoute()) return label;
            }
        }

        return null;
    }

    private static String questionShowing()
    {
        String title = I18n.t("layout.ui.dialogPleaseConfirm");

        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (window instanceof javax.swing.JDialog && window.isShowing()
                && title.equals(((javax.swing.JDialog) window).getTitle()))
            {
                return textOf((javax.swing.JDialog) window);
            }
        }

        return null;
    }

    private static javax.swing.JDialog awaitQuestion() throws Exception
    {
        String title = I18n.t("layout.ui.dialogPleaseConfirm");

        for (long end = System.currentTimeMillis() + 10000; System.currentTimeMillis() < end; )
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) return (javax.swing.JDialog) window;
            }

            Thread.sleep(50);
        }

        return null;
    }

    private static String textOf(javax.swing.JDialog dialog)
    {
        javax.swing.JOptionPane pane = find(dialog.getContentPane(), javax.swing.JOptionPane.class);

        return pane == null ? null : String.valueOf(pane.getMessage());
    }

    private static Object initialValueOf(javax.swing.JDialog dialog)
    {
        javax.swing.JOptionPane pane = find(dialog.getContentPane(), javax.swing.JOptionPane.class);

        return pane == null ? null : pane.getInitialValue();
    }

    private static void press(javax.swing.JDialog dialog, String button) throws Exception
    {
        final javax.swing.JOptionPane pane = find(dialog.getContentPane(), javax.swing.JOptionPane.class);

        assertNotNull(pane, "the question has no buttons");

        SwingUtilities.invokeAndWait(() ->
        {
            for (Object option : pane.getOptions())
            {
                if (button.equals(String.valueOf(option))) pane.setValue(option);
            }
        });
    }

    private static <T> T find(java.awt.Container container, Class<T> type)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof java.awt.Container)
            {
                T found = find((java.awt.Container) child, type);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
