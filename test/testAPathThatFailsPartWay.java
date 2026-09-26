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
import org.traincontrol.automation.Edge;
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
import org.traincontrol.util.I18n;
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
     * Between a failed trip and Validate no locomotive can be placed on the graph: putting the failed train
     * somewhere by hand is refused, and the graph kept is unchanged; after Validate a placement works
     * (MRV4, Adam's decision of 2026-09-26; it was MRV2-C1).
     *
     * The failed train is shown on every point of its locked path, so a placement by hand before validating -
     * Ctrl+V, Assign, or the Place/Edit dialog's OK on any of those points - moved the point it is kept at
     * to wherever it was put, including a station it never reached, and the station it stands at then read
     * free (MRV4-B2).  Rather than each door working out what a hand edit on that graph means, none is
     * allowed until the reload: the train is kept where its sensors last saw it, and moved after.
     */
    @Test(timeOut = 120000)
    public void testPlacingTheFailedTrainBeforeValidatingIsRefused() throws Exception
    {
        Run run = aRunWithAFailedPath(8720, null);

        String kept = graphTheWindowKeeps(run.layout);

        assertFalse(run.layout.moveLocomotive(FAILING, "PF S8", false),
            "the failed train was placed by hand before validating (MRV4)");

        assertEquals(graphTheWindowKeeps(run.layout), kept, "a refused placement changed the graph that is kept (MRV4)");

        // After Validate: a new layout, with no failure recorded, where placing works again
        Layout reloaded = Layout.fromJSON(kept, model);

        assertTrue(reloaded.moveLocomotive(FAILING, "PF S8", false),
            "after validating, the failed train could not be placed where it stands");

        assertEquals(where(reloaded, model.getLocByName(FAILING)), Collections.singletonList("PF S8"),
            "after validating, the failed train was not placed where it was put");
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

    /**
     * Between a failed trip and Validate no locomotive can be taken off the graph: Remove from Point and Remove
     * from Graph (as Delete, Backspace and Ctrl+X do) are refused on the failed train's station, on a point of
     * its path it never reached, and for another train, and the graph kept is unchanged (MRV4; it was MRV3-B1
     * and MRV4-B1).
     *
     * Each such removal meant something different depending on which point of the failed train's locked path
     * it was made on: off its station, it left the train recorded on points it never reached; off one of
     * those, it took the train off the station it stands at too.  None is allowed until the reload.
     */
    @Test(timeOut = 120000)
    public void testRemovingALocomotiveBeforeValidatingIsRefused() throws Exception
    {
        Layout layout = aDepartureThatFails("RM", 8730);

        String kept = graphTheWindowKeeps(layout);

        assertFalse(layout.moveLocomotive(null, "RM S3", false),
            "Remove from Point took the failed train off its station before validating (MRV4)");

        assertFalse(layout.moveLocomotive(null, "RM S4", true),
            "Remove from Graph took the failed train off a point it never reached before validating (MRV4, MRV4-B1)");

        assertFalse(layout.moveLocomotive(null, "RM S7", false),
            "another train was taken off the graph before validating (MRV4)");

        assertEquals(graphTheWindowKeeps(layout), kept, "a refused removal changed the graph that is kept (MRV4)");

        assertEquals(where(layout, model.getLocByName(FAILING)).size(), 3,
            "a refused removal changed where the failed train is recorded");
    }

    /**
     * Ctrl+X on a failed train's station, then Ctrl+V on another, are both refused before validating, and the
     * graph kept is unchanged (MRV4; it was MRV2-C1 and MRV3-B1).
     */
    @Test(timeOut = 120000)
    public void testCuttingAndPastingBeforeValidatingIsRefused() throws Exception
    {
        Layout layout = aDepartureThatFails("CP", 8850);

        String kept = graphTheWindowKeeps(layout);

        assertFalse(layout.moveLocomotive(null, "CP S3", true), "the cut was made before validating (MRV4)");
        assertFalse(layout.moveLocomotive(FAILING, "CP M", false), "the paste was made before validating (MRV4)");

        assertEquals(graphTheWindowKeeps(layout), kept, "a refused cut and paste changed the graph that is kept (MRV4)");
    }

    /**
     * Clear all locomotives is refused before validating, and the graph kept is unchanged (MRV4; it was
     * MRV3-B1).  The door's own loop, after its question.
     */
    @Test(timeOut = 120000)
    public void testClearingAllLocomotivesBeforeValidatingIsRefused() throws Exception
    {
        Layout layout = aDepartureThatFails("CL", 8750);

        String kept = graphTheWindowKeeps(layout);

        for (Locomotive l : new Locomotive[]{ model.getLocByName(FAILING), model.getLocByName(OTHER) })
        {
            Point at = layout.getLocomotiveLocation(l);

            if (at != null && !at.isReversing() && at.isDestination())
            {
                assertFalse(layout.moveLocomotive(null, at.getName(), false),
                    "Clear all locomotives took " + l.getName() + " off " + at.getName() + " before validating (MRV4)");
            }
        }

        assertEquals(graphTheWindowKeeps(layout), kept, "a refused Clear all changed the graph that is kept (MRV4)");
    }

    /**
     * Another train placed on a point of the failed train's path is refused before validating - on the station
     * it stands at and on one it never reached - and the graph kept is unchanged (MRV4; it was MRV3-B1 and
     * MRV4-B1).
     */
    @Test(timeOut = 120000)
    public void testPlacingAnotherTrainOverTheFailedTrainBeforeValidatingIsRefused() throws Exception
    {
        Layout layout = aDepartureThatFails("PO", 8770);

        String kept = graphTheWindowKeeps(layout);

        assertFalse(layout.moveLocomotive(OTHER, "PO S3", false),
            "another train was placed on the failed train's station before validating (MRV4)");

        assertFalse(layout.moveLocomotive(OTHER, "PO S4", false),
            "another train was placed on a point of the failed train's path before validating (MRV4, MRV4-B1)");

        assertEquals(graphTheWindowKeeps(layout), kept, "a refused placement changed the graph that is kept (MRV4)");

        assertEquals(where(layout, model.getLocByName(OTHER)), Collections.singletonList("PO S7"),
            "a refused placement moved the other train");
    }

    /**
     * The Place/Edit dialog's OK, on a point of the failed train's path it never reached, does not move the
     * point it is kept at there (MRV4, MRV4-B2).
     *
     * The dialog selects the train shown on that point, and its OK places that train there before it applies
     * the settings - for a failed train, shown on every point of its locked path, a placement by hand.  The
     * doors that open it are refused before validating; this is the model refusing even so.
     */
    @Test(timeOut = 120000)
    public void testThePlaceDialogsOkBeforeValidatingMovesNothing() throws Exception
    {
        Layout layout = aDepartureThatFails("PD", 8870);

        String kept = graphTheWindowKeeps(layout);

        TrainControlUI ui = aWindowOn(layout, null);

        org.traincontrol.gui.GraphLocAssign dialog = new org.traincontrol.gui.GraphLocAssign(ui, layout.getPoint("PD M"),
            false);

        assertEquals(dialog.getLoc(), FAILING, "precondition: the dialog on PD M did not select the failed train");

        dialog.commitChanges();

        assertEquals(graphTheWindowKeeps(layout), kept, "the Place/Edit dialog's OK on a point the failed train never "
            + "reached moved the point it is kept at there, and its station reads free after the reload (MRV4-B2)");
    }

    /**
     * The graph's right-click menu, on the failed train's station, offers no placement or removal before
     * validating: each such item is greyed, and says why (MRV4).  Edit is offered: on a point a train stands on, the
     * dialog changes that train's settings and places nothing (MRV5-C2).
     *
     * The menu built as the graph builds it, on a window and a graph window that are never shown.
     */
    @Test(timeOut = 120000)
    public void testTheGraphMenuSaysWhyItCannotPlaceOrRemove() throws Exception
    {
        Layout layout = aDepartureThatFails("GM", 8890);

        TrainControlUI ui = aWindowOn(layout, null);

        Class<?> menuClass = Class.forName("org.traincontrol.gui.GraphRightClickPointMenu");
        java.lang.reflect.Constructor<?> build = menuClass.getDeclaredConstructor(TrainControlUI.class, Point.class,
            org.traincontrol.gui.GraphViewer.class);
        build.setAccessible(true);

        javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) build.newInstance(ui, layout.getPoint("GM S3"),
            testMainWindowFaults.windowless(org.traincontrol.gui.GraphViewer.class));

        List<String> offered = new ArrayList<>();
        List<String> greyed = new ArrayList<>();
        String tooltip = null;

        String[] edits = {
            I18n.f("autolayout.ui.menuRemoveLocomotiveFromNode", FAILING),
            I18n.f("autolayout.ui.menuRemoveLocomotiveFromGraph", FAILING),
            I18n.t("autolayout.ui.menuAddLocomotiveAtNode")
        };

        boolean editOffered = false;

        for (java.awt.Component c : menu.getComponents())
        {
            // THE TRAIN'S OWN SETTINGS, which place nothing (MRV5-C2)
            if (c instanceof javax.swing.JMenuItem
                && I18n.t("autolayout.ui.labelEditLocomotiveAt").equals(((javax.swing.JMenuItem) c).getText()))
            {
                editOffered = ((javax.swing.JMenuItem) c).isEnabled();
            }

            if (c instanceof javax.swing.JMenuItem && Arrays.asList(edits).contains(((javax.swing.JMenuItem) c).getText()))
            {
                javax.swing.JMenuItem item = (javax.swing.JMenuItem) c;

                (item.isEnabled() ? offered : greyed).add(item.getText());

                if (!item.isEnabled()) tooltip = item.getToolTipText();
            }
        }

        assertEquals(offered.size() + greyed.size(), edits.length, "precondition: the menu does not hold the three "
            + "placement and removal items: offered " + offered + ", greyed " + greyed);

        assertTrue(editOffered, "the graph's menu greys Edit on the failed train's own point, so the function that "
            + "made its trip fail cannot be changed until Validate - the dialog then places nothing (MRV5-C2)");

        assertTrue(offered.isEmpty(), "the graph's menu offers a placement or removal before validating, which the "
            + "model then refuses: " + offered + " (MRV4)");

        assertEquals(tooltip, Layout.class.getMethod("getPlacementRefusal").invoke(layout),
            "the greyed items do not say why (MRV4)");

        assertTrue(tooltip.contains(FAILING) && tooltip.contains(I18n.t("ui.main.validateConfigOpenGraphUI")),
            "the reason does not name the failed train and the button to press: " + tooltip);
    }

    /**
     * The graph window's keyboard and double-click doors - Ctrl+V, Ctrl+X, Delete, Backspace, and the
     * double-click that opens the Place/Edit dialog - say why and do nothing before validating (MRV4).
     *
     * Read from the source: the graph window needs a display.
     */
    @Test
    public void testTheGraphWindowsKeysSayWhyTheyCannotPlaceOrRemove() throws Exception
    {
        File viewer = new File(ROOT, "src/org/traincontrol/gui/GraphViewer.java");

        assertTrue(viewer.isFile(), "precondition: the graph window is not at " + viewer);

        String source = new String(Files.readAllBytes(viewer.toPath()), StandardCharsets.UTF_8);

        int doubleClick = source.indexOf("new GraphLocAssign(parent, p,");
        int paste = source.indexOf("moveLocomotive(toPlace.getName()");
        int cut = source.indexOf("moveLocomotive(null, this.getLastHoveredNode(), true)");

        assertTrue(doubleClick > 0 && paste > 0 && cut > 0, "precondition: the graph window's doors were not found");

        for (int[] door : new int[][]{ { doubleClick }, { paste }, { cut } })
        {
            // The door's own question: the nearest one before it, with no other door in between - the removing keys
            // ask whether the point has a train to take off (MRV5-C2)
            int asked = Math.max(source.lastIndexOf("getGraphEditRefusal()", door[0]),
                source.lastIndexOf("getGraphRemovalRefusal(", door[0]));
            String between = asked < 0 ? "" : source.substring(asked, door[0]);

            assertTrue(asked >= 0 && !between.contains("moveLocomotive(") && !between.contains("new GraphLocAssign("),
                "a graph window door places or removes a locomotive without asking first whether a failed trip "
                + "forbids it: " + source.substring(door[0], Math.min(source.length(), door[0] + 60)) + " (MRV4)");
        }
    }

    /**
     * A failed train is kept at the last point whose sensor it has tripped - here its start - and not at a
     * point with no sensor it was only passing into (MRV3-B2).
     *
     * A point with no sensor is passed without waiting, so the loop reaches it - and it became a milestone -
     * the moment the train sets off.  The failure's arrival function threw a millisecond after the start, and
     * the train was kept at that point, ahead of itself, with the station it was still standing at reading
     * free.  NS J has no sensor, as the sample graph's "Departure" has none.
     */
    @Test(timeOut = 120000)
    public void testAFailureJustPastAPointWithNoSensorKeepsTheTrainAtItsStart() throws Exception
    {
        quietCallbacks();

        MarklinLocomotive failing = model.getLocByName(FAILING);
        MarklinLocomotive other = model.getLocByName(OTHER);

        Layout layout = new Layout(model);

        point(layout, "NS S1", true, 8791);
        point(layout, "NS J", false, null);
        point(layout, "NS S2", true, 8792);
        point(layout, "NS S7", true, 8793);

        layout.createEdge("NS S1", "NS J");
        layout.createEdge("NS J", "NS S2");
        layout.createEdge("NS S7", "NS S1");
        layout.setDefaultLocSpeed(30);

        layout.getPoint("NS S1").setLocomotive(failing);
        layout.getPoint("NS S7").setLocomotive(other);
        layout.setSimulate(true);

        failTrip(layout, trip(layout, "NS S1", "NS J", "NS S2"), failing, Layout.CB_PRE_ARRIVAL);

        Layout reloaded = Layout.fromJSON(graphTheWindowKeeps(layout), model);

        assertTrue(reloaded.isValid(), "precondition: the graph kept does not load: " + Layout.getLastError());

        assertEquals(where(reloaded, failing), Collections.singletonList("NS S1"),
            "the failed train was not kept at its start, the last point whose sensor it has tripped - a point with no "
            + "sensor is reached the moment it sets off, and says nothing about where it is (MRV3-B2)");

        assertFalse(reloaded.isPathClear(Collections.singletonList(reloaded.getEdge("NS S7", "NS S1")), other, false),
            "after the reload another train can be sent into NS S1, which the failed train had only just left (MRV3-B2)");
    }

    /**
     * A train still under way, just past a point with no sensor, is kept at its start when the graph is kept -
     * as it is after Validate stops it - and not at that point (MRV3-B2).
     *
     * LATE sets off UW S5 - J2 (no sensor) - Q - S6 and waits for Q's sensor; simulation is off, so it waits
     * for as long as the test likes.  Its milestones are S5 and J2, and it was kept at J2, with S5 reading free.
     */
    @Test(timeOut = 120000)
    public void testATrainUnderWayJustPastAPointWithNoSensorIsKeptAtItsStart() throws Exception
    {
        quietCallbacks();

        MarklinLocomotive failing = model.getLocByName(FAILING);
        MarklinLocomotive other = model.getLocByName(OTHER);
        MarklinLocomotive late = model.getLocByName(LATE);

        Layout layout = new Layout(model);

        point(layout, "UW S3", true, 8811);
        point(layout, "UW S4", true, 8812);
        point(layout, "UW S5", true, 8813);
        point(layout, "UW J2", false, null);
        point(layout, "UW Q", false, 8814);
        point(layout, "UW S6", true, 8815);
        point(layout, "UW S7", true, 8816);

        layout.createEdge("UW S3", "UW S4");
        layout.createEdge("UW S5", "UW J2");
        layout.createEdge("UW J2", "UW Q");
        layout.createEdge("UW Q", "UW S6");
        layout.createEdge("UW S7", "UW S5");
        layout.setDefaultLocSpeed(30);

        layout.getPoint("UW S3").setLocomotive(failing);
        layout.getPoint("UW S5").setLocomotive(late);
        layout.getPoint("UW S7").setLocomotive(other);

        final String q = layout.getPoint("UW Q").getS88();

        Thread under = new Thread(() ->
        {
            try
            {
                layout.executePath(trip(layout, "UW S5", "UW J2", "UW Q", "UW S6"), late, 30, null);
            }
            catch (RuntimeException ended)
            {
                // Not what is asserted
            }
        });

        Layout reloaded;

        try
        {
            under.start();

            long deadline = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < deadline
                && (layout.getReachedMilestones(late) == null || layout.getReachedMilestones(late).size() < 2))
            {
                Thread.sleep(20);
            }

            Thread.sleep(300);

            assertEquals(layout.getReachedMilestones(late).size(), 2,
                "precondition: " + LATE + " is not waiting for Q's sensor just past J2: " + layout.getReachedMilestones(late));

            failTrip(layout, trip(layout, "UW S3", "UW S4"), failing, Layout.CB_ROUTE_START);

            reloaded = Layout.fromJSON(graphTheWindowKeeps(layout), model);
        }
        finally
        {
            // The reload retired LATE's layout, so it stops at Q's milestone once Q is reached
            model.setFeedbackState(q, true);
            under.join(15000);
            model.setFeedbackState(q, false);
        }

        assertTrue(reloaded.isValid(), "precondition: the graph kept does not load: " + Layout.getLastError());

        assertEquals(where(reloaded, late), Collections.singletonList("UW S5"),
            "a train under way just past a point with no sensor was not kept at its start, the last point whose sensor "
            + "it has tripped (MRV3-B2)");

        assertFalse(reloaded.isPathClear(Collections.singletonList(reloaded.getEdge("UW S7", "UW S5")), other, false),
            "after the reload another train can be sent into UW S5, which the train under way had only just left "
            + "(MRV3-B2)");
    }

    /**
     * On a non-atomic route a failed train is kept at the last point whose sensor it tripped, even when that
     * point has already been released behind it, and the graph kept loads (MRV3-B2, MRV4-C2).
     *
     * A non-atomic route releases the track behind a train as it goes, and a run of points with no sensor is
     * passed without waiting: past NA A, J1 and J2 (neither has a sensor) release NA A before the train is
     * anywhere near them.  Kept at A as it stood, it was written wherever it was still recorded - three
     * points, a graph that does not load - so it was kept at the earliest of them, J1, a point with no sensor,
     * which the message then called the last place it is known to have reached (MRV4-C2).  It is kept at A,
     * which the message names truly, and A is held after the reload.
     */
    @Test(timeOut = 120000)
    public void testANonAtomicTripPastTwoPointsWithNoSensorKeepsAGraphThatLoads() throws Exception
    {
        quietCallbacks();

        MarklinLocomotive failing = model.getLocByName(FAILING);
        Integer length = failing.getTrainLength();

        Layout layout = new Layout(model);

        point(layout, "NA S1", true, 8831);
        point(layout, "NA A", true, 8832);
        point(layout, "NA J1", false, null);
        point(layout, "NA J2", false, null);
        point(layout, "NA S2", true, 8833);
        point(layout, "NA S7", true, 8834);

        layout.createEdge("NA S1", "NA A");
        layout.createEdge("NA A", "NA J1");
        layout.createEdge("NA J1", "NA J2");
        layout.createEdge("NA J2", "NA S2");
        layout.createEdge("NA S7", "NA A");
        layout.setDefaultLocSpeed(30);
        layout.setAtomicRoutes(false);

        layout.getPoint("NA S1").setLocomotive(failing);
        layout.getPoint("NA S7").setLocomotive(model.getLocByName(OTHER));
        layout.setSimulate(true);

        try
        {
            // Each stretch released as soon as the train is past it
            failing.setTrainLength(0);

            failTrip(layout, trip(layout, "NA S1", "NA A", "NA J1", "NA J2", "NA S2"), failing, Layout.CB_PRE_ARRIVAL);
        }
        finally
        {
            failing.setTrainLength(length);
        }

        assertNotEquals(layout.getPoint("NA A").getCurrentLocomotive(), failing,
            "precondition: NA A, the last point whose sensor the train tripped, was not released behind it");

        Layout reloaded = Layout.fromJSON(graphTheWindowKeeps(layout), model);

        assertTrue(reloaded.isValid(), "on a non-atomic route, the graph kept after a failure past two points with no "
            + "sensor does not load: " + Layout.getLastError() + " (MRV3-B2)");

        assertEquals(where(reloaded, failing), Collections.singletonList("NA A"),
            "on a non-atomic route the failed train was not kept at NA A, the last point whose sensor it tripped, "
            + "which the message names as the last place it is known to have reached (MRV4-C2)");

        assertFalse(reloaded.isPathClear(Collections.singletonList(reloaded.getEdge("NA S7", "NA A")),
            model.getLocByName(OTHER), false), "after the reload another train can be sent into NA A (MRV4-C2)");
    }

    /**
     * The graph kept after the failed train is deleted from the database, before Validate, loads (MRV5-B1).
     *
     * Delete Locomotive is refused only while autonomy runs, and a failed trip has stopped it.  It does not go through
     * moveLocomotive, so the guard does not stop it, and the failure stays recorded.  The kept graph wrote the deleted
     * train back on its kept point, so Validate - and every later start, once the exit save had written that graph -
     * failed with "does not exist in database".
     *
     * MUTATION: write a train on its empty kept point whether or not the graph still shows it, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test(timeOut = 120000)
    public void testAGraphKeptAfterTheFailedTrainIsDeletedLoads() throws Exception
    {
        Layout layout = aDepartureThatFails("DL", 8910);

        MarklinLocomotive failing = model.getLocByName(FAILING);

        try
        {
            assertTrue(model.deleteLoc(FAILING), "precondition: " + FAILING + " was not deleted");

            // As Delete Locomotive goes on: the layout told, then the graph's repaint taking the deleted locomotive off
            // every point
            layout.locDeleted(failing);

            for (Point p : layout.getPoints())
            {
                if (failing.equals(p.getCurrentLocomotive())) p.setLocomotive(null);
            }

            assertFalse(layout.getLocomotivesLeftOnAFailedPath().isEmpty(),
                "precondition: deleting the failed train forgot its failure");

            Layout kept = Layout.fromJSON(graphTheWindowKeeps(layout), model);

            assertTrue(kept.isValid(), "the graph kept after the failed train was deleted from the database does not "
                + "load, so Validate and every later start fail: " + Layout.getLastError() + " (MRV5-B1)");

            assertEquals(where(kept, model.getLocByName(OTHER)), Collections.singletonList("DL S7"),
                "the reload moved a train nobody touched");
        }
        finally
        {
            if (model.getLocByName(FAILING) == null) model.newMM2Locomotive(FAILING, 97);
        }
    }

    /**
     * The graph kept after a locomotive edit's sweep takes the failed train off the graph, before Validate, reloads
     * with the trains where the graph shows them (MRV5-B1).
     *
     * Change Name or Address, Edit Multi-Unit and the Central Station's rename end in sanitizeMultiUnits, not
     * moveLocomotive, so the guard does not stop them.  Linking the failed train into a standing train's consist - or
     * giving the standing train its address - takes the failed one off every point.  The kept graph wrote it back on
     * its kept point beside the other, and the reload's own sweep kept whichever it met first by station name: here
     * the failed train, so the train just edited, which stands at MU S0, was taken off and its station read free.
     *
     * MUTATION: write a train on its empty kept point whether or not the graph still shows it, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test(timeOut = 120000)
    public void testAGraphKeptAfterALocomotiveEditSweepsTheFailedTrainOffKeepsTheGraphAsShown() throws Exception
    {
        Layout layout = aDepartureThatFails("MU", 8940, "S0");

        MarklinLocomotive failing = model.getLocByName(FAILING);
        MarklinLocomotive other = model.getLocByName(OTHER);

        try
        {
            // Edit Multi-Unit: the failed train linked into the standing train's consist, then the sweep that door runs
            java.util.Map<String, Double> consist = new HashMap<>();

            consist.put(FAILING, 1.0);

            other.preSetLinkedLocomotives(consist);
            other.setLinkedLocomotives();

            assertTrue(other.isLinkedTo(failing), "precondition: " + FAILING + " was not linked into " + OTHER);

            layout.sanitizeMultiUnits(other);

            assertEquals(where(layout, failing), Collections.emptyList(),
                "precondition: the edit's sweep did not take " + FAILING + " off the graph");

            assertEquals(where(layout, other), Collections.singletonList("MU S0"),
                "precondition: the edit's sweep took " + OTHER + " off the graph");

            Layout kept = Layout.fromJSON(graphTheWindowKeeps(layout), model);

            assertTrue(kept.isValid(), "the graph kept after the edit does not load: " + Layout.getLastError());

            assertEquals(where(kept, other), Collections.singletonList("MU S0"), "after the reload " + OTHER + ", which "
                + "stands at MU S0 on the graph, is off it and its station reads free - the kept graph wrote " + FAILING
                + ", which the edit had taken off, back beside it (MRV5-B1)");

            assertEquals(where(kept, failing), Collections.emptyList(), "the reload put " + FAILING + " back on the "
                + "graph, which the edit had taken it off (MRV5-B1)");
        }
        finally
        {
            other.preSetLinkedLocomotives(new HashMap<>());
            other.setLinkedLocomotives();
        }
    }

    /**
     * On a non-atomic route, the graph kept after another train has taken the point the failed train is kept at
     * still has the failed train, on the next point of its path that holds it (MRV5-C1).
     *
     * The release behind the train frees the point it last tripped, so another train can lock through it, and
     * locking records that train there.  The kept graph wrote the other train on it and took the failed one off
     * every other point, so after the reload it was on no point, and the track it stands on read free.
     *
     * MUTATION: keep the train at the point it last tripped whoever stands there now, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test(timeOut = 120000)
    public void testANonAtomicKeptPointAnotherTrainTakesAfterTheFailureKeepsTheTrainOnItsPath() throws Exception
    {
        MarklinLocomotive failing = model.getLocByName(FAILING);
        MarklinLocomotive other = model.getLocByName(OTHER);

        Layout layout = aNonAtomicTripThatFails("NT", 8960, null);

        assertNotEquals(layout.getPoint("NT A").getCurrentLocomotive(), failing,
            "precondition: NT A, the last point whose sensor the train tripped, was not released behind it");

        // OTHER locks through the released point, as a train sent S7 - A does
        layout.getPoint("NT S7").setLocomotive(null);
        layout.getPoint("NT A").setLocomotive(other);

        String ahead = firstPointHolding(layout, failing, "NT S1", "NT A", "NT J1", "NT J2", "NT S2");

        assertNotNull(ahead, "precondition: no point of the failed path still records " + FAILING);

        Layout reloaded = Layout.fromJSON(graphTheWindowKeeps(layout), model);

        assertTrue(reloaded.isValid(), "the graph kept does not load: " + Layout.getLastError());

        assertEquals(where(reloaded, other), Collections.singletonList("NT A"), "the reload moved " + OTHER);

        assertEquals(where(reloaded, failing), Collections.singletonList(ahead), "after another train took NT A, "
            + "where the failed train was kept, the reload has it on " + where(reloaded, failing) + " - not on "
            + ahead + ", the next point of its path that holds it (MRV5-C1)");
    }

    /**
     * The same where the other train took the point before the trip failed: the message names where the train is
     * kept, and says why that is not the last place it is known to have reached (MRV5-C1).
     *
     * It named that point, which the graph then showed with the other train on it.
     *
     * MUTATION: keep the train at the point it last tripped whoever stands there, or name that point, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test(timeOut = 120000)
    public void testANonAtomicKeptPointTakenBeforeTheFailureIsNamedAsTaken() throws Exception
    {
        MarklinLocomotive failing = model.getLocByName(FAILING);
        MarklinLocomotive other = model.getLocByName(OTHER);

        final Layout[] made = new Layout[1];

        Layout layout = aNonAtomicTripThatFails("NB", 8970, () ->
        {
            made[0].getPoint("NB S7").setLocomotive(null);
            made[0].getPoint("NB A").setLocomotive(other);
        }, made);

        String ahead = firstPointHolding(layout, failing, "NB S1", "NB A", "NB J1", "NB J2", "NB S2");

        assertNotNull(ahead, "precondition: no point of the failed path still records " + FAILING);

        assertEquals(layout.getPathFailedMessage(), I18n.f("autolayout.errorPathFailedSentByHandKeptAhead", FAILING,
            I18n.t("ui.main.validateConfigOpenGraphUI"), ahead, "NB A"), "the message does not say that the train is "
            + "kept at " + ahead + " because another train now stands at NB A, the last place it is known to have "
            + "reached (MRV5-C1)");

        assertEquals(where(Layout.fromJSON(graphTheWindowKeeps(layout), model), failing),
            Collections.singletonList(ahead), "the reload does not keep the failed train where the message says");
    }

    /**
     * With two failed trips, the refusal names both, in words for more than one (MRV5-C2).
     *
     * @throws Exception from the fixture
     */
    @Test(timeOut = 120000)
    public void testTheRefusalNamesTwoFailedTrainsAsTwo() throws Exception
    {
        Layout layout = aDepartureThatFails("TW", 8980);

        // A second failure, as a train already on its way when the first failed records one
        java.lang.reflect.Field field = Layout.class.getDeclaredField("failedPaths");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Map<Locomotive, String> failed = (java.util.Map<Locomotive, String>) field.get(layout);

        synchronized (failed)
        {
            failed.put(model.getLocByName(OTHER), "a second failure");
        }

        assertEquals(layout.getPlacementRefusal(), I18n.f("autolayout.errorNoGraphEditUntilValidatedSeveral",
            FAILING + ", " + OTHER, I18n.t("ui.main.validateConfigOpenGraphUI")), "with two failed trips the refusal "
            + "speaks of one trip and one train (MRV5-C2)");
    }

    /**
     * Delete, Backspace and Ctrl+X over a point no train stands on say nothing before validating, as they did nothing
     * there before; over a train, they say why they cannot take it off (MRV4, MRV5-C2).
     *
     * @throws Exception from the fixture
     */
    @Test(timeOut = 120000)
    public void testTheRemovingKeysOverAnEmptyPointSayNothing() throws Exception
    {
        Layout layout = aDepartureThatFails("RK", 8990);

        point(layout, "RK E", true, 8999);

        TrainControlUI ui = aWindowOn(layout, null);

        Method ask = TrainControlUI.class.getDeclaredMethod("getGraphRemovalRefusal", String.class);
        ask.setAccessible(true);

        assertNull(ask.invoke(ui, "RK E"), "over a point no train stands on, the removing keys say that no locomotive "
            + "can be taken off - they had nothing to take (MRV5-C2)");

        assertEquals(ask.invoke(ui, "RK S7"), layout.getPlacementRefusal(), "over a train, the removing keys do not "
            + "say why they cannot take it off (MRV4)");
    }

    /**
     * The locomotive dialog on the failed train's point, before validating, changes that train's settings and places
     * nothing: the choice of train is locked, and its OK applies the settings - the departure function that failed
     * among them (MRV5-C2).
     *
     * @throws Exception from the fixture
     */
    @Test(timeOut = 120000)
    public void testTheLocomotiveDialogBeforeValidatingChangesSettingsOnly() throws Exception
    {
        Layout layout = aDepartureThatFails("LD", 9000);

        // Where the kept graph stands each train - not the whole graph, which carries the settings this changes
        Layout keptBefore = Layout.fromJSON(graphTheWindowKeeps(layout), model);

        List<String> failingWas = where(keptBefore, model.getLocByName(FAILING));
        List<String> otherWas = where(keptBefore, model.getLocByName(OTHER));

        TrainControlUI ui = aWindowOn(layout, null);

        org.traincontrol.gui.GraphLocAssign dialog = new org.traincontrol.gui.GraphLocAssign(ui, layout.getPoint("LD S3"),
            false);

        assertEquals(dialog.getLoc(), FAILING, "precondition: the dialog on LD S3 did not select the failed train");

        java.lang.reflect.Field chooser = org.traincontrol.gui.GraphLocAssign.class.getDeclaredField("locAssign");
        chooser.setAccessible(true);

        assertFalse(((java.awt.Component) chooser.get(dialog)).isEnabled(), "before validating, the dialog lets "
            + "another train be chosen for the failed train's point, which its OK would place there (MRV5-C2)");

        java.lang.reflect.Field departure = org.traincontrol.gui.GraphLocAssign.class.getDeclaredField("departureFunc");
        departure.setAccessible(true);

        javax.swing.JComboBox<?> functions = (javax.swing.JComboBox<?>) departure.get(dialog);

        Integer before = model.getLocByName(FAILING).getDepartureFunc();

        functions.setSelectedIndex(functions.getSelectedIndex() == 1 ? 2 : 1);

        Integer chosen = dialog.getDepartureFunc();

        assertNotEquals(chosen, before, "precondition: the dialog's departure function was not changed");

        dialog.commitChanges();

        assertEquals(model.getLocByName(FAILING).getDepartureFunc(), chosen, "the dialog's OK did not change the "
            + "failed train's departure function (MRV5-C2)");

        Layout keptAfter = Layout.fromJSON(graphTheWindowKeeps(layout), model);

        assertEquals(where(keptAfter, model.getLocByName(FAILING)), failingWas, "the dialog's OK before validating moved "
            + FAILING);
        assertEquals(where(keptAfter, model.getLocByName(OTHER)), otherWas, "the dialog's OK before validating moved "
            + OTHER);
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * A point: a station or not, with a sensor of the given number or none.
     */
    private static void point(Layout layout, String name, boolean station, Integer sensor) throws Exception
    {
        String s88 = null;

        if (sensor != null)
        {
            MarklinFeedback feedback = model.newFeedback(sensor, null);
            model.setFeedbackState(feedback.getName(), false);
            s88 = feedback.getName();
        }

        layout.createPoint(name, station, s88);
    }

    /**
     * The path through the named points, in order.
     */
    private static List<Edge> trip(Layout layout, String... points)
    {
        List<Edge> out = new ArrayList<>();

        for (int i = 0; i + 1 < points.length; i++) out.add(layout.getEdge(points[i], points[i + 1]));

        return out;
    }

    /**
     * Sends the train on the path with the named callback throwing, and asserts that the path failed part way.
     */
    private static void failTrip(Layout layout, List<Edge> path, Locomotive loc, String callback)
    {
        loc.setCallback(callback, l ->
        {
            throw new IllegalStateException("the " + callback + " function failed");
        });

        try
        {
            boolean sent = layout.executePath(path, loc, 30, null);

            fail("precondition: the path was meant to fail part way, and executePath returned " + sent + " ("
                + Layout.getLastError() + ")");
        }
        catch (IllegalStateException expected)
        {
            // As intended
        }
        finally
        {
            loc.setCallback(callback, l -> { });
        }

        assertFalse(layout.isValid(), "precondition: the failed path did not stop the layout");
    }

    /**
     * FAILING's departure fails at PREFIX S3 on a two-stretch trip, S3 - M - S4, so it is recorded on all three
     * points and has reached none; OTHER stands at S7, with an edge into S3.
     */
    private static Layout aDepartureThatFails(String prefix, int firstSensor) throws Exception
    {
        return aDepartureThatFails(prefix, firstSensor, "S7");
    }

    /**
     * The same, with OTHER at the station named - which the reload places before or after S3, by name.
     */
    private static Layout aDepartureThatFails(String prefix, int firstSensor, String otherAt) throws Exception
    {
        quietCallbacks();

        Layout layout = new Layout(model);

        point(layout, prefix + " S3", true, firstSensor + 3);
        point(layout, prefix + " M", true, firstSensor + 4);
        point(layout, prefix + " S4", true, firstSensor + 5);
        point(layout, prefix + " " + otherAt, true, firstSensor + 7);

        layout.createEdge(prefix + " S3", prefix + " M");
        layout.createEdge(prefix + " M", prefix + " S4");
        layout.createEdge(prefix + " " + otherAt, prefix + " S3");
        layout.setDefaultLocSpeed(30);

        // Placed as the graph's doors place them, so that they are on the list of trains to run
        assertTrue(layout.moveLocomotive(FAILING, prefix + " S3", false), "precondition: " + FAILING + " was not placed");
        assertTrue(layout.moveLocomotive(OTHER, prefix + " " + otherAt, false), "precondition: " + OTHER + " was not placed");
        layout.setSimulate(true);

        failTrip(layout, trip(layout, prefix + " S3", prefix + " M", prefix + " S4"), model.getLocByName(FAILING),
            Layout.CB_ROUTE_START);

        assertEquals(where(layout, model.getLocByName(FAILING)).size(), 3,
            "precondition: the failed train is not recorded on the three points of its locked path");

        return layout;
    }

    /**
     * FAILING on a non-atomic trip PREFIX S1 - A - J1 - J2 - S2, released as soon as it is past each point, whose
     * arrival function throws approaching S2; A is a station with a sensor, J1 and J2 have none.  OTHER stands at S7,
     * with an edge into A.  `before`, if given, runs just before the arrival function throws.
     */
    private static Layout aNonAtomicTripThatFails(String prefix, int firstSensor, Runnable before) throws Exception
    {
        return aNonAtomicTripThatFails(prefix, firstSensor, before, new Layout[1]);
    }

    /**
     * The same, handing the layout to `before` through `made` before the trip.
     */
    private static Layout aNonAtomicTripThatFails(String prefix, int firstSensor, Runnable before, Layout[] made)
        throws Exception
    {
        quietCallbacks();

        MarklinLocomotive failing = model.getLocByName(FAILING);
        Integer length = failing.getTrainLength();

        Layout layout = new Layout(model);

        made[0] = layout;

        point(layout, prefix + " S1", true, firstSensor + 1);
        point(layout, prefix + " A", true, firstSensor + 2);
        point(layout, prefix + " J1", false, null);
        point(layout, prefix + " J2", false, null);
        point(layout, prefix + " S2", true, firstSensor + 3);
        point(layout, prefix + " S7", true, firstSensor + 4);

        layout.createEdge(prefix + " S1", prefix + " A");
        layout.createEdge(prefix + " A", prefix + " J1");
        layout.createEdge(prefix + " J1", prefix + " J2");
        layout.createEdge(prefix + " J2", prefix + " S2");
        layout.createEdge(prefix + " S7", prefix + " A");
        layout.setDefaultLocSpeed(30);
        layout.setAtomicRoutes(false);

        layout.getPoint(prefix + " S1").setLocomotive(failing);
        layout.getPoint(prefix + " S7").setLocomotive(model.getLocByName(OTHER));
        layout.setSimulate(true);

        try
        {
            // Each stretch released as soon as the train is past it
            failing.setTrainLength(0);

            failing.setCallback(Layout.CB_PRE_ARRIVAL, l ->
            {
                if (before != null) before.run();

                throw new IllegalStateException("the " + Layout.CB_PRE_ARRIVAL + " function failed");
            });

            try
            {
                boolean sent = layout.executePath(trip(layout, prefix + " S1", prefix + " A", prefix + " J1",
                    prefix + " J2", prefix + " S2"), failing, 30, null);

                fail("precondition: the path was meant to fail part way, and executePath returned " + sent + " ("
                    + Layout.getLastError() + ")");
            }
            catch (IllegalStateException expected)
            {
                // As intended
            }
            finally
            {
                failing.setCallback(Layout.CB_PRE_ARRIVAL, l -> { });
            }
        }
        finally
        {
            failing.setTrainLength(length);
        }

        assertFalse(layout.isValid(), "precondition: the failed path did not stop the layout");

        return layout;
    }

    /**
     * The first of the named points, in order, the locomotive is recorded on; null if none.
     */
    private static String firstPointHolding(Layout layout, Locomotive loc, String... points)
    {
        for (String name : points)
        {
            if (loc.equals(layout.getPoint(name).getCurrentLocomotive())) return name;
        }

        return null;
    }

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
