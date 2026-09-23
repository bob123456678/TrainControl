package ui;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Turning automatic execution on or off for a route the Central Station never carried does not sync with it
 * (MT-467).
 *
 * Adam, 2026-09-23, on MT-467: *"there is a spinner for my own routes too, both single and bulk."*  The guard
 * asked whether the route's id was 1000 or more - the ids this application has handed out since 2025-02-01 -
 * and most of his own routes are older than that: ids 1 to 87.  So they synced, behind the modal spinner.
 * What says whether the station carried a route is its lock, which only the sync's import sets and which
 * travels with the route in the database.
 *
 * **How a sync is seen.**  The test holds the one-sync-at-a-time flag, so a sync asked for is turned away
 * with a line in the log and nothing fetched; the lines are counted.  A route the station carried - locked -
 * still syncs, and is the control that shows a sync can be seen at all.
 *
 * MUTATION: put back `isLocalRouteId` as the toggle's test and both claims fail.
 *
 * @author Adam
 */
public class testYourOwnRoutesToggleWithoutASync
{
    private static final String PREFIX = "MT467 ";

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

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            holdTheSync(false);

            for (String name : new ArrayList<>(model.getRouteList()))
            {
                if (name.startsWith(PREFIX)) model.deleteRoute(name);
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
     * One of his own routes, from its own right-click item: no sync.  One the station carried: a sync.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTogglingOneOfYourOwnRoutesDoesNotSync() throws Exception
    {
        String own = addRoute(PREFIX + "own", false);
        String station = addRoute(PREFIX + "station", true);

        holdTheSync(true);

        try
        {
            int before = syncsAskedFor();

            ui.enableOrDisableRoute(own, true);

            assertTrue(waitFor(() -> isEnabled(own), 10000), "precondition: the toggle never reached the route");

            settle();

            assertEquals(syncsAskedFor(), before, "turning on automatic execution for a route the Central Station"
                + " never carried synced with the station - the whole database, behind a modal spinner.  Its id is"
                + " below 1000, as most of Adam's own routes are.  Adam, MT-467: \"there is a spinner for my own"
                + " routes too, both single and bulk.\"");

            // THE CONTROL: a route the station carried still syncs, so a sync is something this test can see.
            ui.enableOrDisableRoute(station, true);

            assertTrue(waitFor(() -> syncsAskedFor() > before, 10000), "a route the Central Station carried was"
                + " toggled and no sync was asked for, so this test cannot see a sync at all and the claim above"
                + " says nothing");
        }
        finally
        {
            holdTheSync(false);
        }
    }

    /**
     * And from Bulk Enable, over a search that matches only his own routes.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheBulkToggleOverYourOwnRoutesDoesNotSync() throws Exception
    {
        String first = addRoute(PREFIX + "bulk first", false);
        String second = addRoute(PREFIX + "bulk second", false);
        String station = addRoute(PREFIX + "bulk station", true);

        holdTheSync(true);

        try
        {
            int before = syncsAskedFor();

            ui.enableOrDisableMatching(PREFIX + "bulk f", true);
            ui.enableOrDisableMatching(PREFIX + "bulk s", true);

            assertTrue(waitFor(() -> isEnabled(first) && isEnabled(second) && isEnabled(station), 10000),
                "precondition: the bulk toggle never reached the routes");

            settle();

            // "bulk s" matched "bulk second" and "bulk station": one batch, one sync, because of the station's.
            assertEquals(syncsAskedFor(), before + 1, "Bulk Enable over routes the Central Station never carried"
                + " synced with the station; only the batch that held one of the station's routes should have."
                + "  Adam, MT-467: \"there is a spinner for my own routes too, both single and bulk.\"");
        }
        finally
        {
            holdTheSync(false);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * A route with an id below 1000 that nothing else uses, locked when the station is to have carried it.
     */
    private static String addRoute(String name, boolean fromTheStation) throws Exception
    {
        int id = 999;

        while (model.getRoute(id) != null) id--;

        assertTrue(id > 0, "precondition: no route id below 1000 is free");

        assertTrue(model.newRoute(name, id, new ArrayList<>(), 1999, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            false, null), "precondition: the route " + name + " was not added");

        ((MarklinRoute) model.getRoute(name)).setLocked(fromTheStation);

        return name;
    }

    private static boolean isEnabled(String name)
    {
        return model.getRoute(name) != null && model.getRoute(name).isEnabled();
    }

    /**
     * How many syncs were turned away since the window opened - one per sync asked for while the flag is held.
     */
    private static int syncsAskedFor()
    {
        final String[] text = new String[1];

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    java.lang.reflect.Field area = TrainControlUI.class.getDeclaredField("debugArea");

                    area.setAccessible(true);

                    text[0] = ((javax.swing.JTextArea) area.get(ui)).getText();
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

        String line = I18n.t("ui.infoSyncAlreadyRunning");

        int count = 0;

        for (int at = text[0].indexOf(line); at >= 0; at = text[0].indexOf(line, at + line.length())) count++;

        return count;
    }

    private static void holdTheSync(boolean held) throws Exception
    {
        java.lang.reflect.Field flag = TrainControlUI.class.getDeclaredField("syncInFlight");

        flag.setAccessible(true);

        ((java.util.concurrent.atomic.AtomicBoolean) flag.get(ui)).set(held);
    }

    /**
     * Long enough for a worker that has written its route to reach the sync it would ask for, and for the log
     * line that says so to be on screen.
     */
    private static void settle() throws Exception
    {
        Thread.sleep(1500);

        SwingUtilities.invokeAndWait(() -> { });
    }

    private static boolean waitFor(BooleanSupplier condition, long millis) throws Exception
    {
        long until = System.currentTimeMillis() + millis;

        while (System.currentTimeMillis() < until)
        {
            if (condition.getAsBoolean()) return true;

            Thread.sleep(50);
        }

        return condition.getAsBoolean();
    }
}
