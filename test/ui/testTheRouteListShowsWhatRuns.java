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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
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
        model = init(null, true, true, false, true);

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
