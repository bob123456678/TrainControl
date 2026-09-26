import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicOptionPaneUI;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.PositionAwareJFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.Util;

/**
 * A train whose path fails part way stops autonomy, and no train is sent again until the autonomy
 * configuration is reloaded.
 *
 * When a dispatch throws after its path is locked - a departure function that fails, or anything else
 * in the middle of executePath - the train is stopped and its path deliberately left locked: the train
 * may be standing on that track, and releasing it would let another train be routed into it.  But every
 * point along that path still records the train, so it stands on several points at once;
 * getLocomotiveLocation answers whichever comes first, and autonomy, Return Home or a train sent by
 * hand could then set off from a point the train is not on.  Autonomy used to carry on dispatching the
 * other trains regardless, and only a reload of the configuration clears those reservations.
 *
 * So the failure now stops autonomy the graceful way - no new path is sent, trains already under way
 * finish theirs - and marks the layout invalid, which every dispatch refuses until the configuration is
 * reloaded (BPV-C13, SG-A3; Adam's ruling of 2026-09-25).
 *
 * And what follows from that (MRV1, MRV2): the reload, the exit save, a backup and Export JSON keep the
 * graph as the run left it rather than the configuration as it was last loaded, with each train that
 * stands on several points - the failed one, and any still part way along a path - kept at the last
 * point it is known to have reached, so its place stays held; and the failure is said once as a dialog,
 * and again by every door that then refuses.  The window's doors are driven on a window that is never
 * built, as testMainWindowFaults drives them.
 */
public class testAPathThatFailsPartWay
{
    private static MarklinControlStation model;

    private static final String FAILING = "PF failing";
    private static final String OTHER = "PF other";
    private static final String MOVER = "PF mover";
    private static final String LATE = "PF late";
    private static final String HOMER = "PF homer";

    /** The project folder: the working directory, as NetBeans runs the tests, unless a run names another. */
    private static final File ROOT = new File(System.getProperty("traincontrol.projectRoot", "."));

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Debug mode, because simulation requires it
        model = init(null, true, false, false, true);
        model.stop();

        model.newMM2Locomotive(FAILING, 97);
        model.newMM2Locomotive(OTHER, 98);
        model.newMM2Locomotive(MOVER, 96);
        model.newMM2Locomotive(LATE, 95);
        model.newMM2Locomotive(HOMER, 94);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (model != null)
        {
            model.deleteLoc(FAILING);
            model.deleteLoc(OTHER);
            model.deleteLoc(MOVER);
            model.deleteLoc(LATE);
            model.deleteLoc(HOMER);
        }
    }

    /**
     * A path that fails part way while autonomy runs: autonomy stops, the layout reads as invalid, the
     * next train is refused with "must be reloaded", and the failed train's track stays held.
     *
     * The failure is the locomotive's route-start callback - where the loader installs the departure
     * functions - throwing once the path is locked, which nothing guards.
     *
     * ON A DEADLINE, as a safety net: were the second train ever actually sent, simulation makes it
     * arrive rather than wait for ever for a sensor.
     */
    @Test(timeOut = 60000)
    public void testAPathThatFailsPartWayStopsAutonomyUntilReloaded() throws Exception
    {
        final List<String> keys = Collections.synchronizedList(new ArrayList<>());

        // The model, with every message key it is asked to log written down on the way through
        ViewListener recording = (ViewListener) Proxy.newProxyInstance(
            ViewListener.class.getClassLoader(), new Class<?>[]{ ViewListener.class }, (proxy, method, args) ->
            {
                if (method.getName().equals("logf")) keys.add((String) args[0]);

                try
                {
                    return method.invoke(model, args);
                }
                catch (InvocationTargetException e)
                {
                    throw e.getCause();
                }
            });

        Layout layout = new Layout(recording);

        String[] names = { "PF A", "PF B", "PF C", "PF D" };

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback sensor = model.newFeedback(8590 + i, null);
            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(names[i], true, sensor.getName());
        }

        layout.createEdge("PF A", "PF B");
        layout.createEdge("PF C", "PF D");

        MarklinLocomotive failing = model.getLocByName(FAILING);
        MarklinLocomotive other = model.getLocByName(OTHER);

        layout.getPoint("PF A").setLocomotive(failing);
        layout.getPoint("PF C").setLocomotive(other);

        layout.setSimulate(true);

        assertTrue(layout.isSimulate(), "precondition: simulation must be on before anything is asked to move");

        failing.setCallback(Layout.CB_ROUTE_START, l ->
        {
            throw new IllegalStateException("the departure function failed");
        });

        try
        {
            // Autonomy running, with nothing of its own to send
            layout.runLocomotives();

            assertTrue(layout.isAutoRunning(), "precondition: autonomy is running");

            try
            {
                layout.executePath(Collections.singletonList(layout.getEdge("PF A", "PF B")), failing, 30, null);

                fail("precondition: the path was meant to fail part way, and it ran");
            }
            catch (IllegalStateException expected)
            {
                // The failure itself still reaches the caller, as before
            }

            assertEquals(layout.getPoint("PF B").getCurrentLocomotive(), failing,
                "precondition: the failed path has to have been locked, with the train recorded along it");

            assertFalse(layout.isAutoRunning(),
                "a train's path failed part way and autonomy carried on sending the others - the failed "
                + "train is recorded on every point of its path, so a later path can set off from a point "
                + "it is not on");

            assertFalse(layout.isValid(),
                "a train's path failed part way and the layout still reads as valid, so a train can be sent "
                + "again before the configuration is reloaded - and only the reload clears the failed "
                + "train's reservations");

            keys.clear();

            assertFalse(layout.executePath(Collections.singletonList(layout.getEdge("PF C", "PF D")), other, 30,
                null), "another train was sent after a path had failed part way, before the configuration "
                + "was reloaded");

            assertTrue(keys.contains("autolayout.errorConfigurationInvalidMustReload"),
                "the refusal did not say the configuration must be reloaded: " + keys);

            // What the message tells the operator, and why nothing else is sent: the track stays held
            assertEquals(layout.getPoint("PF A").getCurrentLocomotive(), failing,
                "the failed train's track was released - it is kept, because the train may be standing on it");

            assertEquals(layout.getPoint("PF B").getCurrentLocomotive(), failing,
                "the failed train's track was released - it is kept, because the train may be standing on it");
        }
        finally
        {
            layout.stopLocomotives();

            failing.setCallback(Layout.CB_ROUTE_START, l -> { });
        }
    }

    /**
     * The exit save, and a backup, keep the graph as the run left it after a path failed part way: every
     * other train where it stands, and the failed train at the last point it is known to have reached
     * (MRV1-A1, MRV2-B3).
     *
     * Both skipped the graph of a layout that is not valid, and wrote the configuration text as it was
     * last loaded instead - so closing TrainControl after a failed trip put every train back where it
     * stood when the session began, and threw away every change made to the graph since.  The live
     * graph cannot simply be written either: the failed train is still recorded on every point of its
     * locked path, and a graph that places one train twice does not load.  So it is kept on one of them:
     * the last it is known to have reached - here its start, as its departure failed.
     *
     * The window's own save, on a window that is never built.  It needs the UI state and autonomy files
     * in the working directory to be its own, so it refuses to run where either is present, or where
     * this folder remembers window positions (the save would rewrite the saved diagram window titles).
     */
    @Test(timeOut = 120000)
    public void testTheExitSaveAndABackupKeepWhereTheRunLeftTheTrains() throws Exception
    {
        File uiState = new File("UIState.data");
        File autonomy = new File(TrainControlUI.AUTONOMY_FILE_NAME);

        if (uiState.exists() || autonomy.exists())
        {
            throw new SkipException("Not run here: the working directory holds a UI state or autonomy file, and "
                + "this test has to write its own in their place");
        }

        if (TrainControlUI.getPrefs().getBoolean(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, false))
        {
            throw new SkipException("Not run here: this folder remembers window positions, so saving the UI state "
                + "would rewrite the saved diagram window titles");
        }

        Run run = aRunWithAFailedPath(8600, null);

        TrainControlUI ui = aWindowOn(run.layout, null);

        // What the window's save reads, and nothing more - with Auto-save on exit ticked, its default
        JCheckBox autosave = new JCheckBox();
        autosave.setSelected(true);

        testMainWindowFaults.set(ui, "autosave", autosave);
        testMainWindowFaults.set(ui, "autonomyJSON", new JTextArea(run.asLoaded));
        testMainWindowFaults.set(ui, "buttonMapping", new HashMap<Integer, JButton>());
        testMainWindowFaults.set(ui, "pageNames", new HashMap<Integer, String>());

        List<HashMap<JButton, Locomotive>> pages = new ArrayList<>();
        pages.add(new HashMap<>());
        testMainWindowFaults.set(ui, "locMapping", pages);

        File backups = new File(Util.BACKUP_FOLDER);
        boolean hadBackups = backups.isDirectory();
        Set<String> before = testControlStationFaults.names(backups);

        try
        {
            // As on exit
            ui.saveState(false);

            String saved = new String(Files.readAllBytes(autonomy.toPath()), StandardCharsets.UTF_8);

            // As Backup
            ui.saveState(true);

            String backedUp = null;

            for (String name : testControlStationFaults.names(backups))
            {
                if (!before.contains(name) && name.endsWith(TrainControlUI.AUTONOMY_FILE_NAME))
                {
                    backedUp = new String(Files.readAllBytes(new File(backups, name).toPath()), StandardCharsets.UTF_8);
                }
            }

            assertNotNull(backedUp, "precondition: the backup wrote no autonomy file");

            assertKeptWhereTheRunLeftThem(saved, "the exit save");
            assertKeptWhereTheRunLeftThem(backedUp, "a backup");
        }
        finally
        {
            Files.deleteIfExists(uiState.toPath());
            Files.deleteIfExists(autonomy.toPath());

            for (String name : testControlStationFaults.names(backups))
            {
                if (!before.contains(name)) Files.deleteIfExists(new File(backups, name).toPath());
            }

            if (!hadBackups) Files.deleteIfExists(backups.toPath());
        }
    }

    /**
     * Validate - the button the failure's message names - reloads the graph as the run left it, with the
     * failed train at the last point it is known to have reached, rather than the configuration as it was
     * last loaded (MRV1-A1, MRV2-B3).
     *
     * On a layout that is not valid Validate skipped its "unsaved changes" question and reloaded the
     * configuration text, which only a load, an exit save or a backup ever sets: every train went back to
     * the station it stood on when the session began, though they were physically wherever the run had
     * left them, and Start would then plan from there.
     *
     * The window's own handler, pressed as the button presses it - with an event; the stand-in model keeps
     * what it is asked to reload and stops the handler there, before anything is built.
     */
    @Test(timeOut = 120000)
    public void testValidateReloadsWhereTheRunLeftTheTrains() throws Exception
    {
        Run run = aRunWithAFailedPath(8610, null);

        final AtomicReference<String> reloaded = new AtomicReference<>();

        TrainControlUI ui = aWindowOn(run.layout, (method, args) ->
        {
            if (method.getName().equals("parseAuto"))
            {
                reloaded.set((String) args[0]);

                throw new StopHere();
            }

            return null;
        });

        testMainWindowFaults.set(ui, "autonomyJSON", new JTextArea(run.asLoaded));
        testMainWindowFaults.set(ui, "layoutStations", new HashMap<String, Set<JLabel>>());

        Method handler = TrainControlUI.class.getDeclaredMethod(
            "validateButtonActionPerformed", java.awt.event.ActionEvent.class);
        handler.setAccessible(true);

        // As the button fires it: with an event of its own
        java.awt.event.ActionEvent press = new java.awt.event.ActionEvent(new JButton("Validate"),
            java.awt.event.ActionEvent.ACTION_PERFORMED, "");

        shownWhile(() -> handler.invoke(ui, press), () -> reloaded.get() != null);

        assertNotNull(reloaded.get(), "precondition: Validate never reached the reload");

        assertKeptWhereTheRunLeftThem(reloaded.get(), "Validate");
    }

    /**
     * Closing TrainControl asks "autonomy is still running - quit?" on a layout a failed path has stopped,
     * as on a valid one (MRV1-A1).
     *
     * The question was asked only on a valid layout.  After a failed path the trains already under way
     * still finish theirs, so closing in that window exited with no question and nothing left to stop them
     * at their stations.  It now asks the same question as the save that follows it.
     *
     * Read from the source: the handler ends the program, which a test cannot let it do.
     */
    @Test
    public void testClosingAsksWhileTrainsFinishAfterAFailedPath() throws Exception
    {
        File window = new File(ROOT, "src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(window.isFile(), "precondition: the main window is not at " + window);

        String source = new String(Files.readAllBytes(window.toPath()), StandardCharsets.UTF_8);

        int start = source.indexOf("private void WindowClosed(");
        int end = source.indexOf("//GEN-LAST:event_WindowClosed", start);

        assertTrue(start >= 0 && end > start, "precondition: the window's close handler was not found");

        String handler = source.substring(start, end);

        assertTrue(handler.contains("autolayout.ui.confirmExitAutonomyRunning"),
            "precondition: the close handler no longer asks whether to quit while autonomy runs");

        assertTrue(handler.contains("hasAutonomyGraphToKeep()") && !handler.contains(".isValid()"),
            "closing TrainControl asks whether to quit while trains are running only on a valid layout - after "
            + "a path failed part way, the trains still finishing their paths are left running with no "
            + "question (MRV1-A1)");
    }

    /**
     * A path that fails part way says why once, as a dialog - not only in the log - and a second failure on
     * the same layout does not say it again (MRV1-B1).  The graph kept keeps a train still part way along
     * its path, and a train whose path failed after it had left, at the last point it is known to have
     * reached (MRV2-C2, MRV2-B3).
     *
     * LATE sets off on PF S5 - PF S6 - PF S8 before FAILING's path fails, passes PF S6, and is held as it
     * arrives; then it fails too, as a train already under way can.  Until then it is recorded on every point
     * of its path, as a train is that a reload stops part way.  It used to be taken off the graph - stopped
     * between stations, off the graph, and not named anywhere.
     */
    @Test(timeOut = 120000)
    public void testAFailedPathSaysWhyOnceAsADialog() throws Exception
    {
        final MarklinLocomotive late = model.getLocByName(LATE);

        final CountDownLatch arriving = new CountDownLatch(1);
        final CountDownLatch go = new CountDownLatch(1);
        final AtomicReference<Thread> lateTrip = new AtomicReference<>();

        try
        {
            Run run = aRunWithAFailedPath(8620, layout ->
            {
                late.setCallback(Layout.CB_PRE_ARRIVAL, l ->
                {
                    arriving.countDown();

                    try
                    {
                        go.await(60, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }

                    throw new IllegalStateException("the arrival function failed too");
                });

                Thread trip = new Thread(() ->
                {
                    try
                    {
                        layout.executePath(Arrays.asList(layout.getEdge("PF S5", "PF S6"), layout.getEdge("PF S6", "PF S8")),
                            late, 30, null);
                    }
                    catch (IllegalStateException expected)
                    {
                        // Its failure, as intended
                    }
                });

                lateTrip.set(trip);
                trip.start();

                assertTrue(arriving.await(30, TimeUnit.SECONDS), "precondition: " + LATE + " never reached its last stretch");
            });

            assertEquals(run.dialogs, Collections.singletonList(run.said),
                "a path that failed part way was only written to the log - no dialog said why autonomy stopped, "
                + "and every door then refused with a reason of its own, or none (MRV1-B1)");

            // LATE is still part way along its path, past PF S6
            assertEquals(where(Layout.fromJSON(graphTheWindowKeeps(run.layout), model), late),
                Collections.singletonList("PF S6"), "a train still part way along its path, as a reload stops it, was not "
                + "kept at the last point it is known to have reached - it was left off the graph, stopped between "
                + "stations with nothing holding its place and nothing naming it (MRV2-C2)");

            assertKeptWhereTheRunLeftThem(graphTheWindowKeeps(run.layout), "the graph kept while " + LATE
                + " is still on its path");

            go.countDown();
            lateTrip.get().join(30000);

            assertEquals(run.dialogs, Collections.singletonList(run.said),
                "a second path failing on the same layout raised a second dialog - it is said once per layout");

            String kept = graphTheWindowKeeps(run.layout);

            assertEquals(where(Layout.fromJSON(kept, model), late), Collections.singletonList("PF S6"),
                "the second train whose path failed, after it had passed PF S6, was not kept there (MRV2-B3)");

            assertKeptWhereTheRunLeftThem(kept, "the graph kept after two failed paths");
        }
        finally
        {
            go.countDown();

            late.setCallback(Layout.CB_PRE_ARRIVAL, l -> { });
        }
    }

    /**
     * The graph kept holds the failed train's place: reloaded, it still stands at the last point it is known
     * to have reached, and no other train can be sent there (MRV2-B3).
     *
     * It used to be taken off every point, so after the reload - Validate, or the next start after an exit
     * save - its station read free, and Start, Return Home or a train sent by hand could send another train
     * onto track it was standing on.  Here its departure failed, so it never left PF S3; OTHER at PF S7 has
     * an edge into PF S3.
     */
    @Test(timeOut = 120000)
    public void testTheFailedTrainsPlaceStaysHeldAfterTheReload() throws Exception
    {
        Run run = aRunWithAFailedPath(8660, null);

        Layout reloaded = Layout.fromJSON(graphTheWindowKeeps(run.layout), model);

        assertTrue(reloaded.isValid(), "precondition: the graph kept does not load: " + Layout.getLastError());

        assertEquals(where(reloaded, model.getLocByName(FAILING)), Collections.singletonList("PF S3"),
            "after the reload the failed train, whose departure failed at PF S3, is not at PF S3 - its station reads "
            + "free (MRV2-B3)");

        assertFalse(reloaded.isPathClear(Collections.singletonList(reloaded.getEdge("PF S7", "PF S3")),
            model.getLocByName(OTHER), false),
            "after the reload another train can be sent into PF S3, where the failed train is standing (MRV2-B3)");
    }

    /**
     * A failed train put back on the graph by hand before validating is kept where it was put (MRV2-C1).
     *
     * The message asks for the train to be moved after validating, but nothing stops it being moved before,
     * and the graph then kept it at the point its failure recorded - discarding the placement, or, when the
     * move had taken it off that point, keeping it on two points, which does not load.  The operator's
     * placement is the latest thing known about where it is.
     */
    @Test(timeOut = 120000)
    public void testAFailedTrainPutBackBeforeValidatingIsKeptWhereItWasPut() throws Exception
    {
        Run run = aRunWithAFailedPath(8720, null);

        assertTrue(run.layout.moveLocomotive(FAILING, "PF S8", false), "precondition: the train could not be placed");

        Layout reloaded = Layout.fromJSON(graphTheWindowKeeps(run.layout), model);

        assertTrue(reloaded.isValid(), "the graph kept after the failed train was put back by hand does not load: "
            + Layout.getLastError() + " (MRV2-C1)");

        assertEquals(where(reloaded, model.getLocByName(FAILING)), Collections.singletonList("PF S8"),
            "the failed train, put back at PF S8 by hand before validating, was not kept there (MRV2-C1)");
    }

    /**
     * The failure's message names the last point the train is known to have reached, where the reload keeps
     * it (MRV2-B3).
     */
    @Test(timeOut = 120000)
    public void testTheMessageNamesWhereTheTrainIsKept() throws Exception
    {
        Run run = aRunWithAFailedPath(8670, null);

        assertTrue(run.said.contains("PF S3"),
            "the failure's message does not name PF S3, the last point " + FAILING + " is known to have reached and "
            + "where the reload keeps it: " + run.said);
    }

    /**
     * A graph kept while the failure is still being recorded loads, with the failed train at its last point
     * (MRV2-C4).
     *
     * The failure took the train out of the running trains before it recorded it as failed, and did a
     * network command, a log line and the graceful stop in between.  A Validate, Backup or exit save landing
     * there found it in neither: kept on every point of its path, the graph did not load.  The layout's
     * control keeps the graph the window would keep at the moment the failure is logged, which is in that
     * window.
     */
    @Test(timeOut = 120000)
    public void testAGraphKeptWhileTheFailureIsRecordedLoads() throws Exception
    {
        Run run = aRunWithAFailedPath(8680, null);

        assertNotNull(run.keptWhileRecorded, "precondition: the failure was never logged");

        assertKeptWhereTheRunLeftThem(run.keptWhileRecorded, "a graph kept while the failure was being recorded");
    }

    /**
     * Autonomy > Load JSON loads the file chosen, on a layout a failed path stopped (MRV2-B1).
     *
     * Load JSON, like the new-graph button, puts its graph in the configuration text box and then runs
     * Validate's handler.  The handler's failed-path branch, meant for Validate's own press, replaced that text
     * with the session's graph, so the chosen file was never loaded, and nothing said so.
     *
     * The window's own handler, with the file chooser answered by the test and the reload captured.
     */
    @Test(timeOut = 120000)
    public void testLoadJsonLoadsTheChosenFileAfterAPathFailed() throws Exception
    {
        Run run = aRunWithAFailedPath(8690, null);

        final String chosen = "{\"points\": [], \"edges\": [], \"note\": \"the graph the operator chose\"}";
        final AtomicReference<String> reloaded = new AtomicReference<>();

        File file = File.createTempFile("chosen", ".json");
        String recorded = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            Files.write(file.toPath(), chosen.getBytes(StandardCharsets.UTF_8));
            testMainWindowFaults.ChoosingWindow.chosen = leavingThePreferencesAlone(file, recorded);

            TrainControlUI ui = aWindowOn(testMainWindowFaults.ChoosingWindow.class, run.layout, (method, args) ->
            {
                if (method.getName().equals("parseAuto"))
                {
                    reloaded.set((String) args[0]);

                    throw new StopHere();
                }

                return null;
            });

            testMainWindowFaults.set(ui, "autonomyJSON", new JTextArea(run.asLoaded));
            testMainWindowFaults.set(ui, "loadJSONButton", new JButton("Load JSON"));
            testMainWindowFaults.set(ui, "layoutStations", new HashMap<String, Set<JLabel>>());

            Method handler = TrainControlUI.class.getDeclaredMethod(
                "loadJSONButtonActionPerformed", java.awt.event.ActionEvent.class);
            handler.setAccessible(true);

            shownWhile(() -> handler.invoke(ui, (java.awt.event.ActionEvent) null), () -> reloaded.get() != null);

            assertNotNull(reloaded.get(), "precondition: Load JSON never reached the reload");

            assertEquals(reloaded.get(), chosen, "Load JSON, on a layout a failed path stopped, did not load the file "
                + "chosen - the session's graph was loaded in its place, and nothing said so (MRV2-B1)");
        }
        finally
        {
            testMainWindowFaults.ChoosingWindow.chosen = null;
            Files.deleteIfExists(file.toPath());
            forgetTheFolderIfNoneWasRecorded(recorded);
        }
    }

    /**
     * Export JSON writes the graph that is kept, which loads, as Backup does (MRV2-C3).
     *
     * It wrote the graph as it stands, with the failed train on every point of its locked path, which is
     * refused on load for a duplicate locomotive - so an operator who exported "to be safe" after a failure
     * had a file that would not import.
     *
     * The window's own handler; its export panel saves at once, to the file the test's chooser answers with.
     */
    @Test(timeOut = 120000)
    public void testExportJsonWritesTheGraphThatIsKept() throws Exception
    {
        Run run = aRunWithAFailedPath(8700, null);

        File file = File.createTempFile("exported", ".json");
        String recorded = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            testMainWindowFaults.ChoosingWindow.chosen = leavingThePreferencesAlone(file, recorded);

            TrainControlUI ui = aWindowOn(testMainWindowFaults.ChoosingWindow.class, run.layout, null);

            Method handler = TrainControlUI.class.getDeclaredMethod(
                "exportJSONActionPerformed", java.awt.event.ActionEvent.class);
            handler.setAccessible(true);

            shownWhile(() -> handler.invoke(ui, (java.awt.event.ActionEvent) null), () -> file.length() > 0);

            String exported = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            assertFalse(exported.isEmpty(), "precondition: Export JSON wrote nothing");

            assertKeptWhereTheRunLeftThem(exported, "Export JSON");
        }
        finally
        {
            testMainWindowFaults.ChoosingWindow.chosen = null;
            Files.deleteIfExists(file.toPath());
            forgetTheFolderIfNoneWasRecorded(recorded);
        }
    }

    /**
     * Start, and the right-click Start, refuse a layout a failed path has stopped with the failure's own
     * words (MRV1-B1).
     *
     * Start said the Central Station had sent new data - never true on 2.8 - or, with the button still
     * greyed from the run the failure ended, the right-click item said to wait for the trains to reach
     * their stations, which they already had.
     */
    @Test(timeOut = 120000)
    public void testStartSaysWhyAfterAPathFailed() throws Exception
    {
        Run run = aRunWithAFailedPath(8630, null);

        TrainControlUI ui = aWindowOn(run.layout, null);

        final JButton start = new JButton("Start");
        testMainWindowFaults.set(ui, "startAutonomy", start);
        testMainWindowFaults.set(ui, "gracefulStop", new JButton("Graceful stop"));
        testMainWindowFaults.set(ui, "returnHomeButton", new JButton("Return home"));

        Method handler = TrainControlUI.class.getDeclaredMethod(
            "startAutonomyActionPerformed", java.awt.event.ActionEvent.class);
        handler.setAccessible(true);

        List<String> shown = shownWhile(() -> SwingUtilities.invokeAndWait(() ->
        {
            start.setEnabled(true);

            try
            {
                handler.invoke(ui, (java.awt.event.ActionEvent) null);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        }), null);

        assertEquals(shown, Collections.singletonList(run.said),
            "Start, on a layout a failed path has stopped, did not say so (MRV1-B1)");

        // The right-click Start, with the button greyed as a run leaves it when the failure ends it
        SwingUtilities.invokeAndWait(() -> start.setEnabled(false));

        try
        {
            ui.requestStartAutonomy();

            fail("the right-click Start started autonomy on a layout a failed path has stopped");
        }
        catch (Exception refused)
        {
            assertEquals(refused.getMessage(), run.said,
                "the right-click Start, on a layout a failed path has stopped, did not say so (MRV1-B1)");
        }
    }

    /**
     * Return Home refuses a layout a failed path has stopped up front, with the failure's own words
     * (MRV1-B1); and a trip that fails during a Return Home run is said once, in the failure's words
     * (MRV2-B2).
     *
     * It planned, had every leg refused, and ended "a train's path stayed blocked - move it out of the way
     * by hand", which sends the operator to shunt trains for a fault that is not on the track.  After the
     * up-front refusal, a failure during the run still ended with that line, just after the failure's own
     * dialog said the opposite.
     */
    @Test(timeOut = 120000)
    public void testReturnHomeSaysWhyAfterAPathFailed() throws Exception
    {
        Run run = aRunWithAFailedPath(8640, null);

        TrainControlUI ui = aWindowOn(run.layout, null);

        testMainWindowFaults.set(ui, "returnHomeButton", new JButton("Return home"));
        testMainWindowFaults.set(ui, "executeTimetable", new JButton("Execute timetable"));
        testMainWindowFaults.set(ui, "startAutonomy", new JButton("Start"));
        testMainWindowFaults.set(ui, "gracefulStop", new JButton("Graceful stop"));

        List<String> shown = shownWhile(() ->
        {
            try
            {
                SwingUtilities.invokeAndWait(ui::requestReturnToHome);
            }
            catch (InvocationTargetException dialogNotBuilt)
            {
                // The message cannot be shown on a window that was never built; it has been recorded
            }
        }, null);

        assertEquals(shown, Collections.singletonList(run.said),
            "Return Home, on a layout a failed path has stopped, did not say so up front (MRV1-B1)");

        // And a trip that fails during the run
        aReturnHomeWhoseLegFailsSaysItOnce(8710);
    }

    /**
     * One homed train, one station from home, sent home by the window's Return Home; its departure fails.
     * The layout raises its dialog through the window, as the model does, so every dialog is recorded in one
     * list: it must be the failure's, and only it.
     */
    private static void aReturnHomeWhoseLegFailsSaysItOnce(int firstSensor) throws Exception
    {
        quietCallbacks();

        final AtomicReference<TrainControlUI> window = new AtomicReference<>();

        ViewListener control = (ViewListener) Proxy.newProxyInstance(
            ViewListener.class.getClassLoader(), new Class<?>[]{ ViewListener.class }, (proxy, method, args) ->
            {
                if (method.getName().equals("showAutonomyAlert"))
                {
                    if (args.length == 2) window.get().showAutonomyAlert((String) args[0], (String) args[1]);
                    else window.get().showAutonomyAlert((String) args[0]);

                    return null;
                }

                try
                {
                    return method.invoke(model, args);
                }
                catch (InvocationTargetException e)
                {
                    throw e.getCause();
                }
            });

        final Layout layout = new Layout(control);

        for (int i = 1; i <= 2; i++)
        {
            MarklinFeedback sensor = model.newFeedback(firstSensor + i, null);
            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(i == 1 ? "PF HOME" : "PF AWAY", true, sensor.getName());
        }

        layout.createEdge("PF AWAY", "PF HOME");
        layout.createEdge("PF HOME", "PF AWAY");
        layout.setDefaultLocSpeed(30);

        MarklinLocomotive homer = model.getLocByName(HOMER);
        homer.setPreferredSpeed(30);

        layout.getPoint("PF AWAY").setLocomotive(homer);
        layout.setHomeLocomotive("PF HOME", HOMER);
        layout.setSimulate(true);

        homer.setCallback(Layout.CB_ROUTE_START, l ->
        {
            throw new IllegalStateException("the departure function failed during Return Home");
        });

        try
        {
            TrainControlUI ui = aWindowOn(layout, null);
            window.set(ui);

            testMainWindowFaults.set(ui, "returnHomeButton", new JButton("Return home"));
            testMainWindowFaults.set(ui, "executeTimetable", new JButton("Execute timetable"));
            testMainWindowFaults.set(ui, "startAutonomy", new JButton("Start"));
            testMainWindowFaults.set(ui, "gracefulStop", new JButton("Graceful stop"));
            testMainWindowFaults.set(ui, "timetableCapture", new JToggleButton("Capture"));

            final java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");
            staging.setAccessible(true);

            List<String> shown = shownWhile(() ->
            {
                try
                {
                    SwingUtilities.invokeAndWait(ui::requestReturnToHome);
                }
                catch (InvocationTargetException dialogNotBuilt)
                {
                    // A message that cannot be shown on a window that was never built; it has been recorded
                }
            }, () ->
            {
                try
                {
                    // The run's worker has finished: whatever it says is handed to the event thread by then
                    return !layout.isValid() && !staging.getBoolean(ui);
                }
                catch (IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }
            }, 60000);

            assertFalse(layout.isValid(), "precondition: the Return Home leg did not fail");

            assertEquals(shown, Collections.singletonList(layout.getPathFailedMessage()),
                "a trip that failed during Return Home was not said once, in the failure's own words - the run also "
                + "ended with \"a train's path stayed blocked, move it out of the way by hand\", the opposite of what "
                + "the failure's dialog had just said (MRV2-B2)");
        }
        finally
        {
            homer.setCallback(Layout.CB_ROUTE_START, l -> { });

            layout.stopLocomotives();
        }
    }

    /**
     * Execute Timetable refuses a layout a failed path has stopped up front, with the failure's own words
     * (MRV1-B1).
     *
     * It checked nothing of the kind: with entries to run, it retried each refused entry for as long as
     * the run lasted, four times a second, with nothing on screen.
     */
    @Test(timeOut = 120000)
    public void testExecuteTimetableSaysWhyAfterAPathFailed() throws Exception
    {
        Run run = aRunWithAFailedPath(8650, null);

        TrainControlUI ui = aWindowOn(run.layout, null);

        final JButton execute = new JButton("Execute timetable");
        testMainWindowFaults.set(ui, "executeTimetable", execute);
        testMainWindowFaults.set(ui, "startAutonomy", new JButton("Start"));
        testMainWindowFaults.set(ui, "returnHomeButton", new JButton("Return home"));
        testMainWindowFaults.set(ui, "gracefulStop", new JButton("Graceful stop"));

        Method handler = TrainControlUI.class.getDeclaredMethod(
            "executeTimetableActionPerformed", java.awt.event.ActionEvent.class);
        handler.setAccessible(true);

        List<String> shown = shownWhile(() -> SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                handler.invoke(ui, (java.awt.event.ActionEvent) null);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        }), null);

        assertEquals(shown, Collections.singletonList(run.said),
            "Execute Timetable, on a layout a failed path has stopped, did not say so up front (MRV1-B1)");
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * Thrown by a stand-in to end a handler at a known point, before it builds anything.
     */
    private static final class StopHere extends RuntimeException
    {
        StopHere()
        {
            super("the test stops the handler here");
        }
    }

    private interface Step
    {
        void run(Layout layout) throws Exception;
    }

    private interface Action
    {
        void run() throws Exception;
    }

    private interface Condition
    {
        boolean holds();
    }

    /**
     * A layout after a short session, and what it said.
     */
    private static final class Run
    {
        Layout layout;

        /** The configuration as the window last loaded it: MOVER still at PF S1 */
        String asLoaded;

        /** What the failure said */
        String said;

        /** The dialogs the layout raised */
        final List<String> dialogs = Collections.synchronizedList(new ArrayList<>());

        /** FAILING's failure */
        final IllegalStateException departure = new IllegalStateException("the departure function failed");

        /** The graph the window would have kept at the moment the failure was logged, part way through recording it */
        volatile String keptWhileRecorded;
    }

    /**
     * Loading a graph gives each of its locomotives callbacks of its own, and the one run at a path's end
     * asks the model for its layout - which builds one when the model has none, and so retires the test's
     * layout part way through.  A graph an earlier test loaded must not reach into the next one.
     */
    private static void quietCallbacks()
    {
        for (String name : new String[]{ MOVER, FAILING, LATE, OTHER, HOMER })
        {
            for (String callback : new String[]{ Layout.CB_ROUTE_START, Layout.CB_PRE_ARRIVAL, Layout.CB_ROUTE_END })
            {
                model.getLocByName(name).setCallback(callback, l -> { });
            }
        }
    }

    /**
     * A small graph with a run behind it.  MOVER stands at PF S1, FAILING at PF S3, LATE at PF S5 and
     * OTHER at PF S7, which has an edge into PF S3.  The configuration is taken as the window would have
     * loaded it; then MOVER runs to PF S2 and arrives, beforeTheFailure runs, and FAILING's path to PF S4
     * fails part way - its route-start callback throws once the path is locked, as
     * testAPathThatFailsPartWayStopsAutonomyUntilReloaded does, so it never leaves PF S3.  The layout's
     * control is the model, with every dialog it raises written down, and the graph the window would keep
     * taken at the moment the failure is logged.
     */
    private static Run aRunWithAFailedPath(int firstSensor, Step beforeTheFailure) throws Exception
    {
        final Run run = new Run();

        quietCallbacks();

        ViewListener control = (ViewListener) Proxy.newProxyInstance(
            ViewListener.class.getClassLoader(), new Class<?>[]{ ViewListener.class }, (proxy, method, args) ->
            {
                // The message is the last argument, whether or not a title comes first
                if (method.getName().equals("showAutonomyAlert")) run.dialogs.add((String) args[args.length - 1]);

                // The failure's own log line, which executePath writes while it records the failure
                if (method.getName().equals("log") && args[0] == run.departure && run.keptWhileRecorded == null)
                {
                    // Never thrown from here: it would replace the failure the catch is handling
                    try
                    {
                        run.keptWhileRecorded = graphTheWindowKeeps(run.layout);
                    }
                    catch (Exception | Error keptFailed)
                    {
                        run.keptWhileRecorded = "the window could not keep a graph: " + keptFailed;
                    }
                }

                try
                {
                    return method.invoke(model, args);
                }
                catch (InvocationTargetException e)
                {
                    throw e.getCause();
                }
            });

        Layout layout = new Layout(control);
        run.layout = layout;

        for (int i = 1; i <= 8; i++)
        {
            MarklinFeedback sensor = model.newFeedback(firstSensor + i, null);
            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint("PF S" + i, true, sensor.getName());
        }

        layout.createEdge("PF S1", "PF S2");
        layout.createEdge("PF S3", "PF S4");
        layout.createEdge("PF S5", "PF S6");
        layout.createEdge("PF S6", "PF S8");
        layout.createEdge("PF S7", "PF S3");

        // A graph that loads needs a default speed
        layout.setDefaultLocSpeed(30);

        MarklinLocomotive mover = model.getLocByName(MOVER);
        MarklinLocomotive failing = model.getLocByName(FAILING);

        layout.getPoint("PF S1").setLocomotive(mover);
        layout.getPoint("PF S3").setLocomotive(failing);
        layout.getPoint("PF S5").setLocomotive(model.getLocByName(LATE));
        layout.getPoint("PF S7").setLocomotive(model.getLocByName(OTHER));

        run.asLoaded = layout.toJSON();

        layout.setSimulate(true);

        assertTrue(layout.executePath(Collections.singletonList(layout.getEdge("PF S1", "PF S2")), mover, 30, null),
            "precondition: " + MOVER + "'s trip did not run");

        if (beforeTheFailure != null) beforeTheFailure.run(layout);

        failing.setCallback(Layout.CB_ROUTE_START, l ->
        {
            throw run.departure;
        });

        try
        {
            boolean sent = layout.executePath(Collections.singletonList(layout.getEdge("PF S3", "PF S4")), failing, 30,
                null);

            fail("precondition: " + FAILING + "'s path was meant to fail part way, and executePath returned " + sent
                + " (" + Layout.getLastError() + ")");
        }
        catch (IllegalStateException expected)
        {
            // As intended
        }
        finally
        {
            failing.setCallback(Layout.CB_ROUTE_START, l -> { });
        }

        run.said = Layout.getLastError();

        assertFalse(layout.isValid(), "precondition: the failed path did not stop the layout");

        return run;
    }

    /**
     * Asserts the given graph loads, with MOVER where the run left it, OTHER where it stood, and FAILING at
     * PF S3, the last point it is known to have reached: its departure failed.  Loading it retires the run's
     * layout, so this comes last.
     */
    private static void assertKeptWhereTheRunLeftThem(String json, String door)
    {
        Layout kept = Layout.fromJSON(json, model);

        assertTrue(kept.isValid(), door + " kept a graph that does not load: " + Layout.getLastError());

        assertEquals(where(kept, model.getLocByName(MOVER)), Collections.singletonList("PF S2"),
            door + " put " + MOVER + " back at the station it stood on when the configuration was last loaded, "
            + "not where the run left it - it is physically elsewhere, and Start would plan from there (MRV1-A1)");

        assertEquals(where(kept, model.getLocByName(OTHER)), Collections.singletonList("PF S7"),
            door + " moved a train the run never touched");

        assertEquals(where(kept, model.getLocByName(FAILING)), Collections.singletonList("PF S3"),
            door + " did not keep " + FAILING + ", whose departure failed, at PF S3, the last point it is known to have "
            + "reached - taken off the graph, its station reads free while it stands there (MRV2-B3)");
    }

    /**
     * The points the locomotive is recorded on.
     */
    private static List<String> where(Layout layout, Locomotive loc)
    {
        List<String> at = new ArrayList<>();

        for (Point p : layout.getPoints())
        {
            if (loc.equals(p.getCurrentLocomotive())) at.add(p.getName());
        }

        Collections.sort(at);

        return at;
    }

    /**
     * The graph the window keeps for the given layout - what Validate reloads and the exit save writes.
     */
    private static String graphTheWindowKeeps(Layout layout) throws Exception
    {
        TrainControlUI ui = aWindowOn(layout, null);

        Method keep = TrainControlUI.class.getDeclaredMethod("getAutonomyGraphToKeep");
        keep.setAccessible(true);

        return (String) keep.invoke(ui);
    }

    /**
     * A window, never built, whose model is the real one except that it holds the given layout, the track
     * power is on and no route is switched on.  The given answer comes first; null means "not answered".
     */
    private static TrainControlUI aWindowOn(Layout layout, testMainWindowFaults.Answer first) throws Exception
    {
        return aWindowOn(TrainControlUI.class, layout, first);
    }

    /**
     * As above, with a window of the given class - testMainWindowFaults.ChoosingWindow for a door that asks for
     * a file.
     */
    private static <T extends TrainControlUI> T aWindowOn(Class<T> type, Layout layout, testMainWindowFaults.Answer first)
        throws Exception
    {
        T ui = testMainWindowFaults.windowless(type);

        testMainWindowFaults.set(ui, "model", (ViewListener) Proxy.newProxyInstance(
            ViewListener.class.getClassLoader(), new Class<?>[]{ ViewListener.class }, (proxy, method, args) ->
            {
                if (first != null)
                {
                    Object answer = first.answer(method, args);

                    if (answer != null) return answer;
                }

                switch (method.getName())
                {
                    case "hasAutoLayout":
                    case "getPowerState":
                        return true;

                    case "getAutoLayout":
                        return layout;

                    case "getRouteList":
                        return Collections.emptyList();

                    default:
                        try
                        {
                            return method.invoke(model, args);
                        }
                        catch (InvocationTargetException e)
                        {
                            throw e.getCause();
                        }
                }
            }));

        return ui;
    }

    /**
     * The file a door's chooser hands over, answering for its folder the one the preferences already record.
     * Load JSON and Export JSON record the chosen file's folder in the preferences, and a test must not change
     * them: the door writes back what is there.  Where nothing is recorded, forgetTheFolderIfNoneWasRecorded
     * removes what the door wrote.
     */
    private static File leavingThePreferencesAlone(File file, String recorded)
    {
        return new File(file.getPath())
        {
            @Override
            public String getParent()
            {
                return recorded != null ? recorded : super.getParent();
            }
        };
    }

    private static void forgetTheFolderIfNoneWasRecorded(String recorded)
    {
        if (recorded == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
    }

    /**
     * Records the message of every option pane built, for the one test step that needs it.
     */
    public static final class RecordingOptionPaneUI extends BasicOptionPaneUI
    {
        static final List<String> shown = Collections.synchronizedList(new ArrayList<>());

        public static ComponentUI createUI(JComponent pane)
        {
            shown.add(String.valueOf(((JOptionPane) pane).getMessage()));

            return new RecordingOptionPaneUI();
        }
    }

    /**
     * Runs the action with every option pane's message recorded, waits until the condition holds - or, with
     * none, until a message has been shown - lets the event thread run whatever was handed to it, and returns
     * the messages.  A handler the test ends, and a dialog that cannot be built on a window that never was,
     * are expected and not reported.
     */
    private static List<String> shownWhile(Action action, Condition done) throws Exception
    {
        return shownWhile(action, done, 10000);
    }

    /**
     * As above, waiting up to the given number of milliseconds for the condition.
     */
    private static List<String> shownWhile(Action action, Condition done, long waitMs) throws Exception
    {
        RecordingOptionPaneUI.shown.clear();

        UIManager.put(RecordingOptionPaneUI.class.getName(), RecordingOptionPaneUI.class);
        UIManager.put("OptionPaneUI", RecordingOptionPaneUI.class.getName());

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { });

        try
        {
            action.run();

            long deadline = System.currentTimeMillis() + waitMs;

            while (System.currentTimeMillis() < deadline
                && (done != null ? !done.holds() : RecordingOptionPaneUI.shown.isEmpty()))
            {
                Thread.sleep(20);
            }

            // Anything else it was going to say
            Thread.sleep(500);

            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
        }
        finally
        {
            Thread.setDefaultUncaughtExceptionHandler(previous);

            UIManager.put("OptionPaneUI", null);
            UIManager.put(RecordingOptionPaneUI.class.getName(), null);
        }

        return new ArrayList<>(RecordingOptionPaneUI.shown);
    }
}
