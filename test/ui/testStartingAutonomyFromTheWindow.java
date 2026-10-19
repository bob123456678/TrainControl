package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Settle;

/**
 * Start Autonomous Operation and its stops, pressed as the user presses them, and what each leaves (FR-136 Phase 6).
 *
 * The button's worker has refusals of its own after the gate: a route armed to run on a sensor (asked), no train to run,
 * and a railway made invalid since it was loaded.  Each answer, and each refusal, leaves autonomy stopped with Start
 * offered again.  A run that does start is simulated, so its trains move, and is stopped gracefully - from the button and
 * from the track diagram's right-click menu - until the railway says it is idle, with no track held.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
 *
 * @author Adam
 */
public class testStartingAutonomyFromTheWindow
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static org.json.JSONObject asOpened;

    /** The route a claim arms, deleted after it. */
    private static final String ARMED = "FR-136 armed route";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStationAddress.withNoStation(
            () -> MarklinControlStation.init(null, true, false, false, true));

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is nothing to start");
        }

        asOpened = Components.onTheEventThread(() -> session.snapshotSetup());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null && model.hasAutoLayout()) Settle.gracefully(model.getAutoLayout(), Settle.PATIENCE_MS);

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** The setup as it opened and the railway built from it, simulated, with Start offered. */
    @BeforeMethod
    public void atRest() throws Exception
    {
        if (asOpened == null) throw new SkipException("the class did not set up");

        Components.onTheEventThread(() ->
        {
            session.restoreSetup(asOpened);

            ui.rebuildRunningLayoutFromSetup(false, new java.util.LinkedHashSet<>(
                session.placementsAutonomyWillWrite().values()));

            return null;
        });

        Components.settle();

        Settle.simulate(model.getAutoLayout());

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "precondition: Start is not offered at rest: "
            + start().getToolTipText());
    }

    @AfterMethod(alwaysRun = true)
    public void stopAndTidy() throws Exception
    {
        if (model.hasAutoLayout()) Settle.gracefully(model.getAutoLayout(), Settle.PATIENCE_MS);

        if (model.getRoute(ARMED) != null) model.deleteRoute(ARMED);

        Settle.until(() -> start().isEnabled(), 30000);
    }

    /**
     * A route armed to run on a sensor makes Start ask first; No starts nothing, and Start is offered again.
     *
     * MUTATION: read the No as a Yes, and the trains start.
     *
     * First of the class: once answered Yes, the window does not ask again.
     */
    @Test(priority = -2)
    public void testAnArmedRouteAnsweredNoStartsNothing() throws Exception
    {
        arm();

        List<String> asked = press(start(), message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1])), 1);

        assertEquals(asked, java.util.Collections.singletonList(I18n.t("route.ui.confirmConditionalRoutesActiveProceed")),
            "Start did not ask about the armed route, or asked more");

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again after No");

        assertFalse(model.getAutoLayout().isAutoRunning(), "autonomy started after No");

        assertFalse(gracefulStop().isEnabled(), "Graceful Stop is offered after No, with nothing running");
    }

    /**
     * The same question answered Yes starts the run: autonomy runs, a train sets off, and Graceful Stop is offered in
     * Start's place.
     *
     * MUTATION: have the Yes return as No does, and nothing starts.
     */
    @Test(priority = -1)
    public void testAnArmedRouteAnsweredYesStartsTheRun() throws Exception
    {
        arm();

        press(start(), message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0])), 1);

        Layout layout = model.getAutoLayout();

        assertTrue(Settle.until(() -> layout.isAutoRunning(), 30000), "autonomy did not start after Yes");

        assertTrue(Settle.until(() -> !layout.getActiveLocomotives().isEmpty(), Settle.PATIENCE_MS),
            "autonomy started and no train set off");

        assertTrue(Settle.until(() -> gracefulStop().isEnabled() && !start().isEnabled(), 30000), "Graceful Stop is not"
            + " offered in Start's place while the run goes");
    }

    /**
     * Start with no train on the railway says so, starts nothing, and is offered again.
     *
     * MUTATION: drop the return after the sentence, and this fails.
     */
    @Test
    public void testStartWithNoTrainSaysSo() throws Exception
    {
        model.getAutoLayout().getLocomotivesToRun().clear();

        List<String> asked = press(start(), message -> null, 1);

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again");

        assertEquals(said(asked), java.util.Collections.singletonList(I18n.t("autolayout.ui.infoPleaseAddLocomotivesToGraph")),
            "Start with no train to run did not say so, or said more");

        assertFalse(model.getAutoLayout().isAutoRunning(), "autonomy started with no train to run");
    }

    /**
     * Start on a railway made invalid since it was loaded says to load it again, and starts nothing.
     *
     * MUTATION: drop the sentence, and this fails.
     */
    @Test
    public void testStartOnAnInvalidRailwaySaysToLoadItAgain() throws Exception
    {
        model.getAutoLayout().invalidate();

        List<String> asked = press(start(), message -> null, 1);

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again");

        assertEquals(said(asked), java.util.Collections.singletonList(
            I18n.t("autolayout.ui.errorLayoutStateInvalidRevalidateJson")), "Start on an invalid railway did not say so,"
            + " or said more");

        assertFalse(model.getAutoLayout().isAutoRunning(), "autonomy started on an invalid railway");
    }

    /**
     * Start, then Graceful Stop: every train finishes its journey and the railway comes to rest with no track held, and
     * Start is offered again.
     *
     * MUTATION: have Graceful Stop's worker skip `stopLocomotives`, and the run never comes to rest.
     */
    @Test
    public void testGracefulStopLetsTheRunComeToRest() throws Exception
    {
        Layout layout = startTheRun(() -> press(start(), message -> null));

        press(gracefulStop(), message -> null);

        assertRestsWithNothingHeld(layout, "Graceful Stop");
    }

    /**
     * The track diagram's right-click menu starts the run, and its Gracefully Stop Autonomy brings it to rest.
     *
     * MUTATION: have the menu's Gracefully Stop Autonomy do nothing, and the run never comes to rest.
     */
    @Test
    public void testTheDiagramsMenuStartsAndStopsTheRun() throws Exception
    {
        final TileKey square = aStationWithATrain();

        Layout layout = startTheRun(() -> press(diagramItem(square, I18n.t("autolayout.ui.menuStartAutonomy")),
            message -> null));

        press(diagramItem(square, I18n.t("autolayout.ui.menuStopAutonomyGracefully")), message -> null);

        assertRestsWithNothingHeld(layout, "the diagram's Gracefully Stop Autonomy");
    }

    // ---------------------------------------------------------------- doors

    private interface Pressing
    {
        List<String> press() throws Exception;
    }

    /** Starts the run through a door, and waits until autonomy runs with a train under way. */
    private static Layout startTheRun(Pressing door) throws Exception
    {
        Layout layout = model.getAutoLayout();

        List<String> asked = door.press();

        assertTrue(Settle.until(() -> layout.isAutoRunning(), 30000), "autonomy did not start: " + asked);

        assertTrue(Settle.until(() -> !layout.getActiveLocomotives().isEmpty(), Settle.PATIENCE_MS),
            "autonomy started and no train set off: " + asked);

        return layout;
    }

    private static void assertRestsWithNothingHeld(Layout layout, String how)
    {
        assertTrue(Settle.idle(layout, Settle.PATIENCE_MS), "the run did not come to rest after " + how + ": trains under"
            + " way " + layout.getActiveLocomotives().keySet());

        assertFalse(layout.isAutoRunning(), "autonomy still runs after " + how);

        List<String> held = new ArrayList<>();

        for (Edge e : layout.getEdges())
        {
            if (e.isLockHeld(null)) held.add(e.getName());
        }

        assertEquals(held, new ArrayList<String>(), "track is still held after " + how);

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again after " + how);
    }

    /** A route armed to run when a sensor is occupied, which Start asks about. */
    private static void arm() throws Exception
    {
        assertTrue(model.newRoute(ARMED, new ArrayList<>(), 0,
            org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null), "precondition: no route made");

        ((org.traincontrol.marklin.MarklinRoute) model.getRoute(ARMED)).enable();
    }

    private static JButton start()
    {
        return field("startAutonomy");
    }

    private static JButton gracefulStop()
    {
        return field("gracefulStop");
    }

    private static JButton field(String name)
    {
        try
        {
            java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField(name);

            field.setAccessible(true);

            return (JButton) field.get(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the window has no " + name, e);
        }
    }

    /** A square with a train standing on it, on any page. */
    private static TileKey aStationWithATrain()
    {
        for (TileKey tile : session.placementsAutonomyWillWrite().keySet()) return tile;

        throw new AssertionError("precondition: no train stands on the frozen railway");
    }

    /**
     * An item of the track diagram's right-click menu on a square, the menu built as a right-click builds it: the
     * railway asked first, then the menu made from the answer.
     */
    private static AbstractButton diagramItem(final TileKey square, String text) throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final java.lang.reflect.Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class,
            Point.class);

        gather.setAccessible(true);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
            TileKey.class, TileKey.class, Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

        make.setAccessible(true);

        final Point standing = Components.onTheEventThread(() -> ui.getAutonomyPointForTile(square));

        final Object options = gather.invoke(null, ui, standing);

        javax.swing.JPopupMenu menu = Components.onTheEventThread(
            () -> (javax.swing.JPopupMenu) make.newInstance(ui, square, square, options));

        AbstractButton item = Components.withText(menu, text);

        assertNotNull(item, "precondition: the diagram's menu on " + square + " has no \"" + text + "\"");

        return item;
    }

    /**
     * Presses a button as a click does, answering each question by the rule, and returns what was asked once the click's
     * own work, and the worker it starts, have had their say.
     */
    private static List<String> press(final AbstractButton button,
        java.util.function.Function<String, Answering.Answer> rule) throws Exception
    {
        return press(button, rule, 0);
    }

    /** The same, waiting for this many questions, which the worker posts once it has decided. */
    private static List<String> press(final AbstractButton button,
        java.util.function.Function<String, Answering.Answer> rule, int questions) throws Exception
    {
        try (Answering answering = Answering.start(rule))
        {
            Components.press(button);

            Components.settle();

            answering.awaitAsked(questions, 30000);

            Components.settle();

            return answering.asked();
        }
    }

    private static List<String> said(List<String> asked)
    {
        return new ArrayList<>(asked);
    }
}
