package ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The route list turns a mark beside every route that is running (FR-137).
 *
 * Adam: "in the route tab, show a spinner next to any route that is currently running, similar to the autonomy spinner.
 * 3.0.2 feature".  The mark is the one Start and Return Home turn (FR-077, OB-309), drawn by the list's renderer from
 * `Route.isExecuting()`, so it is on for a route whoever started it - the play button, a click on the row, the diagram's
 * route tile, an s88 trigger, another route - and off when the route really ends.
 *
 * **How it is seen.**  The renderer is asked for a cell, sized and painted to a picture, and the strip left of the play
 * button is searched for the mark's grey; the route is a real one, run by `MarklinRoute.execRoute` with a delay between
 * its commands so that it runs long enough to look at.
 *
 * MUTATION: take the `routeStarted` notice out of `MarklinRoute.execRoute`, or the paint out of the renderer, and the
 * claims fail.
 *
 * @author Adam
 */
public class testTheRouteListShowsWhatRuns
{
    private static final String PREFIX = "FR137 ";

    private static final int CELL_W = 300;
    private static final int CELL_H = 30;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the main window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // The window is the model's own, so the notices a route sends reach it as they do in the application
        model = support.TestStationAddress.withNoStation(() -> init(null, true, true, false, true));

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window to tell");

        // `init` posts the window's display and returns; behind it, twice, because display() posts work of its own
        for (int turn = 0; turn < 2; turn++) SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null)
            {
                for (String name : new ArrayList<>(model.getRouteList()))
                {
                    if (name.startsWith(PREFIX)) model.deleteRoute(name);
                }
            }

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

    /**
     * A running route has the mark beside it, turning, and a route that is not running does not; when the route ends the
     * mark goes and the animation stops.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheMarkIsOnWhileARouteRunsAndOffWhenItEnds() throws Exception
    {
        String slow = aSlowRoute(PREFIX + "slow", 4);
        String idle = aSlowRoute(PREFIX + "idle", 1);

        assertFalse(markOf(slow), "precondition: the mark is on a route at rest");
        assertFalse(isTurning(), "precondition: the mark is turning with nothing running");

        model.getRoute(slow).execRoute(false);

        assertTrue(waitFor(() -> model.getRoute(slow).isExecuting(), 5000), "precondition: the route never started");

        assertTrue(waitFor(() -> markOf(slow), 3000), "a route is running and the route list shows no mark beside it."
            + "  Adam, FR-137: \"show a spinner next to any route that is currently running\"");

        assertTrue(model.getRoute(slow).isExecuting(), "precondition: the route ended before the mark could be looked at");

        assertFalse(markOf(idle), "a route that is not running has a mark beside it");

        assertTrue(isTurning(), "the mark is on and nothing animates it");

        // TURNING: the picture changes with time
        BufferedImage first = pictureOf(slow);

        Thread.sleep(250);

        BufferedImage second = pictureOf(slow);

        assertTrue(model.getRoute(slow).isExecuting(), "precondition: the route ended while the mark was watched");
        assertTrue(differ(first, second), "the mark did not turn between two looks a quarter of a second apart");

        // OVER: gone, and the animation with it
        assertTrue(waitFor(() -> !model.getRoute(slow).isExecuting(), 30000), "precondition: the route never ended");

        assertTrue(waitFor(() -> !markOf(slow), 3000), "the route ended and the mark is still beside it");
        assertTrue(waitFor(() -> !isTurning(), 3000), "nothing runs and the mark is still being animated");
    }

    /**
     * A route started by another route shows the mark too: the one that fires it can be over before the other has begun.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARouteStartedByAnotherRouteShowsTheMark() throws Exception
    {
        String inner = aSlowRoute(PREFIX + "inner", 4);

        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandRoute(inner));

        String outer = PREFIX + "outer";

        assertTrue(model.newRoute(outer, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "precondition: the outer route was not made");

        refresh();

        model.getRoute(outer).execRoute(false);

        assertTrue(waitFor(() -> model.getRoute(inner).isExecuting(), 5000),
            "precondition: the route the other fires never started");

        assertTrue(waitFor(() -> markOf(inner), 3000), "a route started by another route has no mark beside it");

        assertTrue(waitFor(() -> isTurning(), 3000), "a route started by another route has its mark and nothing animates it");

        assertTrue(waitFor(() -> !model.getRoute(inner).isExecuting() && !model.getRoute(outer).isExecuting(), 30000),
            "precondition: the routes never ended");

        assertTrue(waitFor(() -> !markOf(inner) && !markOf(outer) && !isTurning(), 3000),
            "the routes ended and a mark is still there");
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * With the power off, a click on a route's row - not its play button - opens the route's editor and asks nothing
     * (FR-140).  Adam: "when the power is off, clicking on routes in the route tab should open the editor rather than ask
     * for execution".
     *
     * The click is posted rather than waited for, so that the question it asked before - a modal dialog - cannot hold
     * the run; any dialog left open is closed at the end.
     *
     * MUTATION: take the power question out of `RouteListMouseClicked`, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testWithThePowerOffARowClickOpensTheRoutesEditor() throws Exception
    {
        final String route = aSlowRoute(PREFIX + "edit", 1);

        final java.lang.reflect.Field power = MarklinControlStation.class.getDeclaredField("powerState");

        power.setAccessible(true);

        final boolean was = power.getBoolean(model);

        try
        {
            power.setBoolean(model, false);

            final java.awt.event.MouseEvent[] click = new java.awt.event.MouseEvent[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("RouteList");

                    field.setAccessible(true);

                    JTable table = (JTable) field.get(ui);

                    for (int row = 0; row < table.getRowCount() && click[0] == null; row++)
                    {
                        for (int column = 0; column < table.getColumnCount(); column++)
                        {
                            Object value = table.getValueAt(row, column);

                            if (!(value instanceof org.traincontrol.base.Route)
                                || !route.equals(((org.traincontrol.base.Route) value).getName())) continue;

                            Rectangle cell = table.getCellRect(row, column, false);

                            // the left of the cell, on the route's name and away from the play button at its right
                            click[0] = new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_CLICKED,
                                System.currentTimeMillis(), 0, cell.x + 6, cell.y + cell.height / 2, 1, false,
                                java.awt.event.MouseEvent.BUTTON1);

                            break;
                        }
                    }
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertNotNull(click[0], "precondition: the route " + route + " is not in the route list");

            final java.lang.reflect.Method clicked = TrainControlUI.class.getDeclaredMethod("RouteListMouseClicked",
                java.awt.event.MouseEvent.class);

            clicked.setAccessible(true);

            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    clicked.invoke(ui, click[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(waitFor(() -> routeEditorShowing(), 8000), "with the power off, a click on the route's row did not"
                + " open its editor.  Adam, FR-140: \"when the power is off, clicking on routes in the route tab should open"
                + " the editor rather than ask for execution\"");

            assertFalse(model.getRoute(route).isExecuting(), "a click on the row ran the route with the power off");
        }
        finally
        {
            power.setBoolean(model, was);

            SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window instanceof javax.swing.JDialog
                        || "RouteEditorFrame".equals(window.getClass().getSimpleName()))
                    {
                        window.dispose();
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------------------------------------
    // THE ROUTE LIST AS THE USER DRIVES IT (FR-136 Phase 6): real clicks on the table, its right-click menu, Find Route
    // and Bulk Enable, each answered through the real dialogs.  What each asserts is what came of it: the accessories
    // switched and their commands on the wire, a route made, the tab and the cell shown.

    /**
     * A click on a route's play button runs it at once, with no question: its accessories switch and their commands
     * go to the railway (FR-043, MT-217).  Adam: "if play button is hit, route executes without confirming".
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testThePlayButtonRunsTheRouteWithoutAsking() throws Exception
    {
        final String route = aQuickRoute(PREFIX + "play", 246, 247);

        support.WireRecorder wire = support.WireRecorder.open();

        try (support.Answering answering = support.Answering.start(message -> support.Answering.pressAt(1)))
        {
            int mark = wire.mark();

            clickTheRoute(route, true, java.awt.event.MouseEvent.MOUSE_CLICKED, false);

            assertTrue(waitFor(() -> switched(246) && switched(247), 10000), "the play button did not switch the"
                + " route's accessories");

            assertTrue(waitFor(() -> accessoryCommandsSince(wire, mark) >= 2, 5000), "the route's accessories switched"
                + " and no command for them reached the railway: " + wire.since(mark));

            assertEquals(answering.asked(), new ArrayList<String>(), "the play button asked before running the route"
                + " (MT-217: it runs without confirming)");
        }
        finally
        {
            wire.close();

            theRouteIsOver(route);
        }
    }

    /**
     * A click elsewhere on a route's row asks first, naming the route and its id; Yes runs it (FR-043).
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARowClickAsksAndYesRunsTheRoute() throws Exception
    {
        final String route = aQuickRoute(PREFIX + "row yes", 246, 247);

        support.WireRecorder wire = support.WireRecorder.open();

        try (support.Answering answering = support.Answering.start(
            message -> support.Answering.press(org.traincontrol.util.I18n.t("ui.yes"))))
        {
            int mark = wire.mark();

            clickTheRoute(route, false, java.awt.event.MouseEvent.MOUSE_CLICKED, false);

            assertTrue(answering.awaitAsked(1, 10000), "a click on the route's row asked nothing");

            assertEquals(answering.asked().get(0), org.traincontrol.util.I18n.f("route.ui.confirmExecuteRouteWithId",
                route, model.getRouteId(route)), "the question does not name the route and its id");

            assertTrue(waitFor(() -> switched(246) && switched(247), 10000), "Yes did not switch the route's accessories");

            assertTrue(waitFor(() -> accessoryCommandsSince(wire, mark) >= 2, 5000), "Yes switched the accessories and"
                + " no command for them reached the railway: " + wire.since(mark));
        }
        finally
        {
            wire.close();

            theRouteIsOver(route);
        }
    }

    /**
     * The same question answered No runs nothing: no accessory switches and nothing goes to the railway.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARowClickAnsweredNoSendsNothing() throws Exception
    {
        final String route = aQuickRoute(PREFIX + "row no", 246, 247);

        support.WireRecorder wire = support.WireRecorder.open();

        try (support.Answering answering = support.Answering.start(
            message -> support.Answering.press(org.traincontrol.util.I18n.t("ui.no"))))
        {
            int mark = wire.mark();

            clickTheRoute(route, false, java.awt.event.MouseEvent.MOUSE_CLICKED, false);

            assertTrue(answering.awaitAsked(1, 10000), "a click on the route's row asked nothing");

            settle();

            assertFalse(model.getRoute(route).isExecuting(), "No ran the route");

            assertFalse(switched(246) || switched(247), "No switched the route's accessories");

            assertEquals(accessoryCommandsSince(wire, mark), 0, "No sent the route's commands: " + wire.since(mark));
        }
        finally
        {
            wire.close();
        }
    }

    /**
     * The right-click menu's Execute runs the route, as the play button does.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testRightClickExecuteRunsTheRoute() throws Exception
    {
        final String route = aQuickRoute(PREFIX + "menu run", 246, 247);

        support.WireRecorder wire = support.WireRecorder.open();

        try
        {
            int mark = wire.mark();

            javax.swing.JPopupMenu menu = rightClickTheRoute(route);

            final javax.swing.AbstractButton execute = support.Components.withText(menu,
                org.traincontrol.util.I18n.f("route.ui.menuExecuteRoute", route + " ("
                    + org.traincontrol.util.I18n.t("route.ui.id") + ": " + model.getRouteId(route) + ")"));

            assertNotNull(execute, "the route's right-click menu has no Execute naming the route and its id");

            SwingUtilities.invokeLater(execute::doClick);

            assertTrue(waitFor(() -> switched(246) && switched(247), 10000), "Execute did not switch the route's"
                + " accessories");

            assertTrue(waitFor(() -> accessoryCommandsSince(wire, mark) >= 2, 5000), "Execute switched the accessories"
                + " and no command for them reached the railway: " + wire.since(mark));
        }
        finally
        {
            wire.close();

            theRouteIsOver(route);

            closeAnyMenu();
        }
    }

    /**
     * The right-click menu's Duplicate Route makes a copy beside the route: a new name, an id of its own, the same
     * commands, and a cell of its own in the list.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testDuplicateRouteMakesACopyWithItsOwnId() throws Exception
    {
        final String route = aQuickRoute(PREFIX + "original", 246, 247);

        final String copy = route + " (" + org.traincontrol.util.I18n.t("route.ui.copy") + " 1)";

        assertNull(model.getRoute(copy), "precondition: the copy is there before the duplicate");

        try
        {
            javax.swing.JPopupMenu menu = rightClickTheRoute(route);

            final javax.swing.AbstractButton duplicate = support.Components.withText(menu,
                org.traincontrol.util.I18n.t("route.ui.menuDuplicateRoute"));

            assertNotNull(duplicate, "the route's right-click menu has no Duplicate Route");

            SwingUtilities.invokeLater(duplicate::doClick);

            assertTrue(waitFor(() -> model.getRoute(copy) != null, 30000), "Duplicate Route made no route called "
                + copy + ": " + model.getRouteList());

            assertNotEquals(model.getRouteId(copy), model.getRouteId(route), "the copy has the original's id");

            assertEquals(model.getRoute(copy).toCSV(), model.getRoute(route).toCSV(), "the copy's commands are not the"
                + " original's");

            assertTrue(waitFor(() -> cellOf(copy) != null, 30000), "the copy is not in the route list");
        }
        finally
        {
            closeAnyMenu();

            if (model.getRoute(copy) != null) model.deleteRoute(copy);
        }
    }

    /**
     * Find Route, given part of a route's name that only it has, brings the route list forward and highlights that
     * route's cell (FR-044, MT-218).  Adam: "system jumps to the route page and highlights the matched cell".
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testFindRouteShowsTheRoute() throws Exception
    {
        final String route = aQuickRoute(PREFIX + "findable zebra", 246);
        final String other = aQuickRoute(PREFIX + "plain", 247);

        // Another tab in front, as when the user is driving
        final JTable table = routeTable();

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) SwingUtilities.getAncestorOfClass(
                javax.swing.JTabbedPane.class, table);

            tabs.setSelectedIndex(0);
        });

        assertFalse(table.isShowing(), "precondition: the route list is in front already");

        final javax.swing.AbstractButton find = support.Components.withText(ui,
            org.traincontrol.util.I18n.t("route.ui.menuFindRoute"));

        assertNotNull(find, "the window has no Find Route");

        try (support.Answering answering = support.Answering.start(message -> support.Answering.type("ZEBRA")))
        {
            SwingUtilities.invokeLater(find::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Find Route asked nothing");

            assertEquals(answering.asked().get(0), org.traincontrol.util.I18n.t("route.ui.promptFindRoute"), "asked: "
                + answering.asked());

            assertTrue(waitFor(table::isShowing, 5000), "Find Route did not bring the route list forward");

            settle();
        }

        assertTrue(waitFor(() -> !backgroundOf(route).equals(backgroundOf(other)), 5000), "Find Route did not highlight"
            + " the route it found");

        // Until the next click in the table, which clears it
        clickTheRoute(other, false, java.awt.event.MouseEvent.MOUSE_PRESSED, false);
    }

    /**
     * Find Route, given a name no route has, says so, naming what was typed.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testFindRouteSaysWhenNoRouteHasTheName() throws Exception
    {
        final String missing = PREFIX + "no such route " + System.nanoTime();

        final javax.swing.AbstractButton find = support.Components.withText(ui,
            org.traincontrol.util.I18n.t("route.ui.menuFindRoute"));

        try (support.Answering answering = support.Answering.start(message ->
            message.equals(org.traincontrol.util.I18n.t("route.ui.promptFindRoute"))
                ? support.Answering.type(missing) : support.Answering.press(org.traincontrol.util.I18n.t("ui.ok"))))
        {
            SwingUtilities.invokeLater(find::doClick);

            assertTrue(answering.awaitAsked(2, 10000), "Find Route said nothing about a route that is not there: "
                + answering.asked());

            assertEquals(answering.asked().get(1), org.traincontrol.util.I18n.f("route.ui.errorNoSuchRoute", missing), "asked: "
                + answering.asked());
        }
    }

    /**
     * Bulk Enable, given part of a name, arms exactly the routes with a sensor whose names contain it.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testBulkEnableArmsOnlyTheMatchingRoutes() throws Exception
    {
        final String match = PREFIX + "bulk kiwi";
        final String unmatched = PREFIX + "bulk other";

        for (String name : new String[] {match, unmatched})
        {
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(248, Accessory.accessoryDecoderType.MM2, true));

            if (model.getRoute(name) != null) model.deleteRoute(name);

            assertTrue(model.newRoute(name, commands, 77, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "precondition: the route " + name + " was not made");
        }

        refresh();

        assertFalse(model.getRoute(match).isEnabled() || model.getRoute(unmatched).isEnabled(),
            "precondition: a route is armed before Bulk Enable");

        final javax.swing.AbstractButton bulk = support.Components.withText(ui,
            org.traincontrol.util.I18n.t("ui.main.bulkEnable"));

        assertNotNull(bulk, "the route list has no Bulk Enable");

        try (support.Answering answering = support.Answering.start(message -> support.Answering.type("kiwi")))
        {
            SwingUtilities.invokeLater(bulk::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Bulk Enable asked for nothing");

            assertEquals(answering.asked().get(0), org.traincontrol.util.I18n.f(
                "route.ui.promptEnterSearchStringWithS88State", org.traincontrol.util.I18n.t("ui.stateEnabled")));

            assertTrue(waitFor(() -> model.getRoute(match).isEnabled(), 30000), "Bulk Enable did not arm the route"
                + " whose name matches");

            settle();

            assertFalse(model.getRoute(unmatched).isEnabled(), "Bulk Enable armed a route whose name does not match");
        }
    }

    /**
     * Find Route, given part of a name several routes have, asks which, and shows the one picked (MT-218).  Adam:
     * "let's support partial matching if there is no exact match as entered."
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testFindRouteAsksWhichWhenSeveralMatch() throws Exception
    {
        final String first = aQuickRoute(PREFIX + "twin mango one", 246);
        final String second = aQuickRoute(PREFIX + "twin mango two", 247);

        final JTable table = routeTable();

        final javax.swing.AbstractButton find = support.Components.withText(ui,
            org.traincontrol.util.I18n.t("route.ui.menuFindRoute"));

        List<String> asked;

        try (support.Answering answering = support.Answering.start(message ->
            message.equals(org.traincontrol.util.I18n.t("route.ui.promptFindRoute"))
                ? support.Answering.type("mango") : support.Answering.type(second)))
        {
            SwingUtilities.invokeLater(find::doClick);

            assertTrue(answering.awaitAsked(2, 10000), "Find Route did not ask which of two matching routes: "
                + answering.asked());

            assertEquals(answering.asked().get(1), org.traincontrol.util.I18n.t("route.ui.promptWhichRoute"), "asked: "
                + answering.asked());

            assertTrue(waitFor(table::isShowing, 5000), "Find Route did not bring the route list forward");

            asked = answering.asked();
        }

        assertTrue(waitFor(() -> !backgroundOf(second).equals(backgroundOf(first)), 5000), "Find Route did not highlight"
            + " the route picked; asked " + asked);

        clickTheRoute(first, false, java.awt.event.MouseEvent.MOUSE_PRESSED, false);
    }

    /**
     * Bulk Disable, given part of a name, disarms exactly the armed routes whose names contain it.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testBulkDisableDisarmsOnlyTheMatchingRoutes() throws Exception
    {
        final String match = PREFIX + "armed lime";
        final String unmatched = PREFIX + "armed other";

        for (String name : new String[] {match, unmatched})
        {
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(248, Accessory.accessoryDecoderType.MM2, true));

            if (model.getRoute(name) != null) model.deleteRoute(name);

            assertTrue(model.newRoute(name, commands, 78, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null),
                "precondition: the route " + name + " was not made");
        }

        refresh();

        assertTrue(model.getRoute(match).isEnabled() && model.getRoute(unmatched).isEnabled(),
            "precondition: a route is not armed before Bulk Disable");

        final javax.swing.AbstractButton bulk = support.Components.withText(ui,
            org.traincontrol.util.I18n.t("ui.main.bulkDisable"));

        assertNotNull(bulk, "the route list has no Bulk Disable");

        try (support.Answering answering = support.Answering.start(message -> support.Answering.type("lime")))
        {
            SwingUtilities.invokeLater(bulk::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Bulk Disable asked for nothing");

            assertEquals(answering.asked().get(0), org.traincontrol.util.I18n.f(
                "route.ui.promptEnterSearchStringWithS88State", org.traincontrol.util.I18n.t("ui.stateDisabled")));

            assertTrue(waitFor(() -> !model.getRoute(match).isEnabled(), 30000), "Bulk Disable did not disarm the route"
                + " whose name matches");

            settle();

            assertTrue(model.getRoute(unmatched).isEnabled(), "Bulk Disable disarmed a route whose name does not match");
        }
    }

    /**
     * The right-click menu's Edit Route opens the route's editor, titled with its name and id; a second Edit while it
     * is open says only one route can be edited at a time, and opens no second editor.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testEditRouteOpensOneEditorAtATime() throws Exception
    {
        final String route = aQuickRoute(PREFIX + "edit me", 246);

        final String title = org.traincontrol.util.I18n.f("route.ui.dialogEditRouteWithId", route,
            model.getRouteId(route));

        try
        {
            javax.swing.JPopupMenu menu = rightClickTheRoute(route);

            final javax.swing.AbstractButton edit = support.Components.withText(menu,
                org.traincontrol.util.I18n.t("route.ui.menuEditRoute"));

            assertNotNull(edit, "the route's right-click menu has no Edit Route");

            SwingUtilities.invokeLater(edit::doClick);

            assertTrue(waitFor(() -> routeEditorsTitled(title) == 1, 10000), "Edit Route opened no editor titled \""
                + title + "\"");

            closeAnyMenu();

            try (support.Answering answering = support.Answering.start(message -> null))
            {
                final javax.swing.AbstractButton again = support.Components.withText(rightClickTheRoute(route),
                    org.traincontrol.util.I18n.t("route.ui.menuEditRoute"));

                SwingUtilities.invokeLater(again::doClick);

                assertTrue(answering.awaitAsked(1, 10000), "a second Edit Route said nothing");

                assertEquals(answering.asked().get(0),
                    org.traincontrol.util.I18n.t("route.ui.errorOnlyOneRouteEditorAllowed"));

                settle();
            }

            assertEquals(routeEditorsTitled(title), 1, "a second Edit Route opened a second editor");
        }
        finally
        {
            closeAnyMenu();

            SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if ("RouteEditorFrame".equals(window.getClass().getSimpleName())) window.dispose();
                }
            });
        }
    }

    /** How many route editors are showing under this title. */
    private static int routeEditorsTitled(String title)
    {
        int found = 0;

        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if ("RouteEditorFrame".equals(window.getClass().getSimpleName()) && window.isShowing()
                && title.equals(((java.awt.Frame) window).getTitle())) found++;
        }

        return found;
    }

    /** The route list's table: the one holding routes. */
    private static JTable routeTable()
    {
        JTable table = support.Components.find(ui, JTable.class, t ->
        {
            for (int row = 0; row < t.getRowCount(); row++)
            {
                for (int column = 0; column < t.getColumnCount(); column++)
                {
                    if (t.getValueAt(row, column) instanceof org.traincontrol.base.Route) return true;
                }
            }

            return false;
        });

        assertNotNull(table, "the window has no table of routes");

        return table;
    }

    /** A route's cell in the list, or null. */
    private static Rectangle cellOf(final String route)
    {
        return support.Components.onTheEventThread(() ->
        {
            JTable table = routeTable();

            for (int row = 0; row < table.getRowCount(); row++)
            {
                for (int column = 0; column < table.getColumnCount(); column++)
                {
                    Object value = table.getValueAt(row, column);

                    if (value instanceof org.traincontrol.base.Route
                        && route.equals(((org.traincontrol.base.Route) value).getName()))
                    {
                        return table.getCellRect(row, column, true);
                    }
                }
            }

            return null;
        });
    }

    /**
     * Brings the route list forward and sends a mouse event to a route's cell: on its play button, or on its name.
     * Posted, so that a question the click asks cannot hold this thread.
     */
    private static void clickTheRoute(String route, boolean onThePlayButton, final int kind, boolean popup)
        throws Exception
    {
        final JTable table = routeTable();

        support.Components.select(table);

        settle();

        Rectangle cell = cellOf(route);

        assertNotNull(cell, "precondition: the route " + route + " is not in the route list");

        final java.awt.Point at = onThePlayButton
            ? new java.awt.Point((int) TrainControlUI.routePlayButtonBox(cell).getCenterX(), (int) cell.getCenterY())
            : new java.awt.Point(cell.x + 6, (int) cell.getCenterY());

        final int button = popup ? java.awt.event.MouseEvent.BUTTON3 : java.awt.event.MouseEvent.BUTTON1;
        final int mask = popup ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        final int modifiers = kind == java.awt.event.MouseEvent.MOUSE_CLICKED ? 0 : mask;

        SwingUtilities.invokeLater(() -> table.dispatchEvent(new java.awt.event.MouseEvent(table, kind,
            System.currentTimeMillis(), modifiers, at.x, at.y, 1, popup, button)));
    }

    /** Right-clicks a route's cell and gives back the menu that opened. */
    private static javax.swing.JPopupMenu rightClickTheRoute(String route) throws Exception
    {
        clickTheRoute(route, false, java.awt.event.MouseEvent.MOUSE_PRESSED, true);

        assertTrue(waitFor(() -> support.Components.popupShowing() != null, 5000), "a right-click on the route opened"
            + " no menu");

        return support.Components.popupShowing();
    }

    private static void closeAnyMenu()
    {
        support.Components.onTheEventThread(() ->
        {
            javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();

            return null;
        });
    }

    /** The background the list paints a route's cell with. */
    private static Color backgroundOf(final String route)
    {
        return support.Components.onTheEventThread(() ->
        {
            JTable table = routeTable();

            for (int row = 0; row < table.getRowCount(); row++)
            {
                for (int column = 0; column < table.getColumnCount(); column++)
                {
                    Object value = table.getValueAt(row, column);

                    if (value instanceof org.traincontrol.base.Route
                        && route.equals(((org.traincontrol.base.Route) value).getName()))
                    {
                        return table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, false,
                            false, row, column).getBackground();
                    }
                }
            }

            throw new AssertionError("the route " + route + " is not in the route list");
        });
    }

    /**
     * A route switching these MM2 accessories on, at once, with each accessory switched off first so the route has
     * something to change; and the power on, so that a row click asks rather than opening the editor.
     */
    private static String aQuickRoute(String name, int... addresses) throws Exception
    {
        List<RouteCommand> list = new ArrayList<>();

        for (int address : addresses)
        {
            list.add(RouteCommand.RouteCommandAccessory(address, Accessory.accessoryDecoderType.MM2, true));

            model.setAccessoryState(address, Accessory.accessoryDecoderType.MM2, false);
        }

        if (model.getRoute(name) != null) model.deleteRoute(name);

        assertTrue(model.newRoute(name, list, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "precondition: the route " + name + " was not made");

        if (!model.getPowerState()) model.go();

        assertTrue(waitFor(() -> model.getPowerState(), 5000), "precondition: the power is off");

        for (int address : addresses)
        {
            assertFalse(switched(address), "precondition: accessory " + address + " is switched before the route runs");
        }

        refresh();

        return name;
    }

    /** Waits until the route has ended and its mark has stopped, so the next claim starts with nothing running. */
    private static void theRouteIsOver(String route)
    {
        assertTrue(waitFor(() -> !model.getRoute(route).isExecuting() && !isTurning(), 10000), "the route "
            + route + " never ended");
    }

    private static boolean switched(int address)
    {
        return model.getAccessoryState(address, Accessory.accessoryDecoderType.MM2);
    }

    /** How many accessory commands the railway was sent since a mark. */
    private static int accessoryCommandsSince(support.WireRecorder wire, int mark)
    {
        int seen = 0;

        for (String line : wire.since(mark))
        {
            if (line.contains("Type: Accessory")) seen++;
        }

        return seen;
    }

    private static void settle() throws Exception
    {
        support.Components.settle();
    }

    /** Whether a route editor window is showing. */
    private static boolean routeEditorShowing()
    {
        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if ("RouteEditorFrame".equals(window.getClass().getSimpleName()) && window.isShowing()) return true;
        }

        return false;
    }

    /**
     * A route of this many commands with half a second between them, put in the list.
     */
    private static String aSlowRoute(String name, int commands) throws Exception
    {
        List<RouteCommand> list = new ArrayList<>();

        for (int i = 0; i < commands; i++)
        {
            RouteCommand rc = RouteCommand.RouteCommandAccessory(246 + i, Accessory.accessoryDecoderType.MM2, true);

            rc.setDelay(500);

            list.add(rc);
        }

        assertTrue(model.newRoute(name, list, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "precondition: the route " + name + " was not made");

        refresh();

        return name;
    }

    private static void refresh() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.refreshRouteList());

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });
    }

    private static boolean isTurning()
    {
        try
        {
            return (Boolean) TrainControlUI.class.getMethod("isRouteMarkTurning").invoke(ui);
        }
        catch (NoSuchMethodException e)
        {
            return false;
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The list's cell for a route, as the renderer paints it at the size a cell has.
     */
    private static BufferedImage pictureOf(final String route)
    {
        final BufferedImage[] picture = new BufferedImage[1];

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("RouteList");

                    field.setAccessible(true);

                    JTable table = (JTable) field.get(ui);

                    for (int row = 0; row < table.getRowCount(); row++)
                    {
                        for (int column = 0; column < table.getColumnCount(); column++)
                        {
                            Object value = table.getValueAt(row, column);

                            if (!(value instanceof org.traincontrol.base.Route)
                                || !route.equals(((org.traincontrol.base.Route) value).getName())) continue;

                            Component cell = table.getCellRenderer(row, column)
                                .getTableCellRendererComponent(table, value, false, false, row, column);

                            cell.setSize(CELL_W, CELL_H);

                            picture[0] = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_INT_ARGB);

                            java.awt.Graphics2D g = picture[0].createGraphics();

                            try
                            {
                                g.setColor(Color.WHITE);
                                g.fillRect(0, 0, CELL_W, CELL_H);

                                cell.paint(g);
                            }
                            finally
                            {
                                g.dispose();
                            }

                            return;
                        }
                    }
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });
        }
        catch (InterruptedException | java.lang.reflect.InvocationTargetException e)
        {
            throw new IllegalStateException(e);
        }

        assertNotNull(picture[0], "the route " + route + " is not in the route list");

        return picture[0];
    }

    /**
     * Whether the strip of cell just left of the play button holds the mark's grey - which a short name never reaches.
     */
    private static boolean markOf(String route)
    {
        BufferedImage picture = pictureOf(route);

        Rectangle play = TrainControlUI.routePlayButtonBox(new Rectangle(0, 0, CELL_W, CELL_H));

        int ink = 0;

        for (int x = play.x - 24; x < play.x; x++)
        {
            for (int y = 0; y < CELL_H; y++)
            {
                int rgb = picture.getRGB(x, y);

                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;

                if (r < 140 && Math.abs(r - g) < 12 && Math.abs(r - b) < 12) ink++;
            }
        }

        return ink >= 8;
    }

    private static boolean differ(BufferedImage a, BufferedImage b)
    {
        for (int x = 0; x < CELL_W; x++)
        {
            for (int y = 0; y < CELL_H; y++)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
            }
        }

        return false;
    }

    private static boolean waitFor(BooleanSupplier condition, long millis)
    {
        long until = System.currentTimeMillis() + millis;

        while (System.currentTimeMillis() < until)
        {
            if (condition.getAsBoolean()) return true;

            try
            {
                Thread.sleep(25);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return condition.getAsBoolean();
    }
}
