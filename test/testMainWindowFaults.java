import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicOptionPaneUI;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.PositionAwareJFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.Util;

/**
 * Faults in the main window's handlers, backported to 2.8 from the 3.0 branch.
 *
 * The window is never built here.  Building it opens whatever layout the preferences name, reads the
 * UI state file from the working directory and can raise modal dialogs - none of which these handlers
 * need.  Each test instead takes a TrainControlUI allocated WITHOUT running its constructor, and gives
 * it exactly the few fields the handler under test touches.  Nothing here is shown on screen or
 * writes a preference.  The tests that need the UI state file work on UIState.data in the working
 * directory, and refuse to run where one is already present.
 */
public class testMainWindowFaults
{
    /**
     * Thrown by the stand-in model to end a Start worker at a known point, before it reaches any
     * dialog or starts any train.
     */
    private static final class StopHere extends RuntimeException
    {
        StopHere()
        {
            super("the test stops the Start worker here");
        }
    }

    /**
     * A double press on Start is one start, not two.
     *
     * The button used to be greyed only by the worker thread the press spawns, after all of its
     * checks - so a second press arriving before that (a double-click, most likely when the first
     * press also had to open the graph window) spawned a second worker.  Both passed the busy check
     * before either had set the layout running, and both called runLocomotives, which has no guard of
     * its own: two driving threads per train, and two threads able to lock two paths for one train
     * and throw switches for a route it is not taking.
     *
     * Two clicks in one event: the second arrives exactly as a double-click's does, while the first
     * press's worker is still working.  The stand-in model counts how many workers asked for the
     * power state, which is the first thing each one does.
     *
     * Ported from the 3.0 branch (739c933c).
     */
    @Test
    public void testADoublePressOnStartStartsOnce() throws Exception
    {
        final AtomicInteger workers = new AtomicInteger();
        final AtomicInteger stopped = new AtomicInteger();

        ViewListener model = stubModel((method, args) ->
        {
            if (method.getName().equals("getPowerState"))
            {
                workers.incrementAndGet();
                return true;
            }

            if (method.getName().equals("getRouteList"))
            {
                stopped.incrementAndGet();
                throw new StopHere();
            }

            return null;
        });

        TrainControlUI ui = windowless();
        set(ui, "model", model);

        final JButton start = new JButton("Start");
        set(ui, "startAutonomy", start);

        // Wired the way the form wires it
        final Method handler = TrainControlUI.class.getDeclaredMethod(
            "startAutonomyActionPerformed", java.awt.event.ActionEvent.class);
        handler.setAccessible(true);

        start.addActionListener(evt ->
        {
            try
            {
                handler.invoke(ui, evt);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        // The workers end on StopHere, by design; anything else still gets reported
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
        {
            if (!(e instanceof StopHere) && previous != null) previous.uncaughtException(t, e);
            else if (!(e instanceof StopHere)) e.printStackTrace();
        });

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                start.setEnabled(true);

                start.doClick(0);
                start.doClick(0);
            });

            // Every worker that was spawned reaches the stop point quickly; give a second one time to
            long deadline = System.currentTimeMillis() + 5000;

            while (stopped.get() < 1 && System.currentTimeMillis() < deadline)
            {
                Thread.sleep(20);
            }

            Thread.sleep(500);

            // And let the event thread run whatever the workers handed it
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
        }
        finally
        {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }

        assertEquals(workers.get(), 1,
            "a second press on Start was accepted while the first was still being checked, so two "
            + "workers ran - the button was only greyed by the worker, after its checks, and a "
            + "double-click could start every train twice");

        assertTrue(start.isEnabled(),
            "nothing was started, so the button must be given back - otherwise one refused press "
            + "leaves Start greyed until a restart");
    }

    /**
     * A press that starts the run leaves Start greyed; only a press that starts nothing gives it back.
     *
     * The test above ends every worker before it can start anything, so it only ever sees the button
     * given back.  The other half matters as much: runLocomotives sets the layout running on a thread
     * of its own, a moment after the worker has finished, and a Start given back in that moment lets a
     * second press through the busy check - the double start the greying exists to prevent (BPV-C3).
     *
     * The stand-in layout is valid, has a train to run and counts the runs it is asked for, without
     * running anything; it is not running, as a real one is not yet at that moment.
     */
    @Test
    public void testAPressThatStartsTheRunLeavesStartGreyed() throws Exception
    {
        final AtomicInteger runs = new AtomicInteger();

        final Layout layout = new Layout(stubModel((method, args) -> null))
        {
            @Override
            public boolean isValid()
            {
                return true;
            }

            @Override
            public Set<Locomotive> getLocomotivesToRun()
            {
                // Start asks only whether there is anything to run
                return Collections.singleton((Locomotive) null);
            }

            @Override
            public void runLocomotives()
            {
                runs.incrementAndGet();
            }
        };

        ViewListener model = stubModel((method, args) ->
        {
            switch (method.getName())
            {
                case "getPowerState":
                case "hasAutoLayout":
                    return true;

                case "getRouteList":
                    return Collections.emptyList();

                case "getAutoLayout":
                    return layout;

                default:
                    return null;
            }
        });

        TrainControlUI ui = windowless();
        set(ui, "model", model);
        set(ui, "gracefulStop", new JButton("Graceful stop"));
        set(ui, "returnHomeButton", new JButton("Return home"));

        final JButton start = new JButton("Start");
        set(ui, "startAutonomy", start);

        // Wired the way the form wires it
        final Method handler = TrainControlUI.class.getDeclaredMethod(
            "startAutonomyActionPerformed", java.awt.event.ActionEvent.class);
        handler.setAccessible(true);

        start.addActionListener(evt ->
        {
            try
            {
                handler.invoke(ui, evt);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        SwingUtilities.invokeAndWait(() ->
        {
            start.setEnabled(true);
            start.doClick(0);
        });

        long deadline = System.currentTimeMillis() + 5000;

        while (runs.get() < 1 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        assertEquals(runs.get(), 1, "precondition: the press did not start the run, so this test says "
            + "nothing about what a started run does to the button");

        // Let the worker end, and the event thread run whatever it handed over
        Thread.sleep(500);

        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertFalse(start.isEnabled(),
            "the run was started and Start was given back anyway - a second press now passes the "
            + "busy check before the run has set the layout running, and every train starts twice");

        // And a second press is what the greying is for
        SwingUtilities.invokeAndWait(() -> start.doClick(0));

        Thread.sleep(500);

        assertEquals(runs.get(), 1, "a second press after the run had started started it again");
    }

    /**
     * Records the thread every option pane is built on.  Installed as the option pane UI for the one
     * test that needs it, and removed again.
     */
    public static final class RecordingOptionPaneUI extends BasicOptionPaneUI
    {
        static volatile String builtOn;
        static volatile boolean builtOnEventThread;

        public static ComponentUI createUI(JComponent pane)
        {
            builtOnEventThread = SwingUtilities.isEventDispatchThread();
            builtOn = Thread.currentThread().getName();

            return new RecordingOptionPaneUI();
        }
    }

    /**
     * The question Start asks when a conditional route is switched on is built and shown on the event
     * thread.
     *
     * Start's checks run on a worker thread, and every other dialog in them is handed to the event
     * thread.  This one was built and shown straight from the worker: a modal dialog off the event
     * thread, which mispaints on a good day and deadlocks on a bad one.  Since the press greys Start
     * until the worker ends, a worker hung in that dialog would also leave Start greyed for the rest
     * of the session (BPV-C4).
     *
     * The stand-in model has one conditional route switched on, which is what raises the question.
     * The window here has no constructor run, so the dialog cannot actually be built on it - it fails
     * wherever it is raised, and nothing is shown on screen.  What is asserted is the thread the
     * question was built on, which the option pane UI records.
     *
     * Ported from the 3.0 branch (739c933c, confirmOnEventThread).
     */
    @Test
    public void testTheConditionalRoutesQuestionIsAskedOnTheEventThread() throws Exception
    {
        final MarklinRoute conditional = new MarklinRoute(null, "Conditional route", 1);

        Field enabled = MarklinRoute.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(conditional, true);

        ViewListener model = stubModel((method, args) ->
        {
            switch (method.getName())
            {
                case "getPowerState":
                    return true;

                case "getRouteList":
                    return Collections.singletonList(conditional.getName());

                case "getRoute":
                    return conditional;

                case "getAutoLayout":
                    // Past the question, which only a yes reaches
                    throw new StopHere();

                default:
                    return null;
            }
        });

        TrainControlUI ui = windowless();
        set(ui, "model", model);

        final JButton start = new JButton("Start");
        set(ui, "startAutonomy", start);

        final Method handler = TrainControlUI.class.getDeclaredMethod(
            "startAutonomyActionPerformed", java.awt.event.ActionEvent.class);
        handler.setAccessible(true);

        RecordingOptionPaneUI.builtOn = null;

        UIManager.put(RecordingOptionPaneUI.class.getName(), RecordingOptionPaneUI.class);
        UIManager.put("OptionPaneUI", RecordingOptionPaneUI.class.getName());

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        // The dialog cannot be built on a window-less instance, so a question raised on the worker ends
        // the worker with that failure; it is expected, and not what is asserted
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { });

        try
        {
            SwingUtilities.invokeAndWait(() ->
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
            });

            long deadline = System.currentTimeMillis() + 5000;

            while (RecordingOptionPaneUI.builtOn == null && System.currentTimeMillis() < deadline)
            {
                Thread.sleep(20);
            }

            // Let the worker end, and the event thread run whatever it handed over
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

        assertNotNull(RecordingOptionPaneUI.builtOn,
            "precondition: the Start worker never reached the conditional-routes question, so this "
            + "test says nothing about where it is asked");

        assertTrue(RecordingOptionPaneUI.builtOnEventThread,
            "the conditional-routes question was built and shown on thread \"" + RecordingOptionPaneUI.builtOn
            + "\" - Start's worker - rather than on the event thread: a modal dialog off the event "
            + "thread, which can hang the worker and leave Start greyed for the session (BPV-C4)");

        assertTrue(start.isEnabled(),
            "the question could not be asked, which is a no, so Start must be given back");
    }

    /**
     * A UI state file that is there but will not read is copied aside before it is saved over.
     *
     * The window's restoreState reported an unreadable UIState.data as "No data file found", the same
     * as a first launch, and the save on exit then wrote the empty state over it: every key mapping on
     * every page, the page names and the active page, gone with no copy kept.  The locomotive database
     * beside it had the same fault.
     *
     * This runs the real restore and the real save, so it needs UIState.data in the working directory
     * to be one it may replace; it refuses to run where a real one is present.  It also refuses when
     * this folder remembers window positions, because the save then rewrites the saved diagram window
     * titles, which are shared with every other folder.
     *
     * Ported from the 3.0 branch (d6b9b00c).
     */
    @Test
    public void testAnUnreadableUiStateIsKeptBeforeItIsSavedOver() throws Exception
    {
        File live = new File("UIState.data");

        if (live.exists())
        {
            throw new SkipException("Not run here: the working directory holds a UI state file ("
                + live.getAbsolutePath() + "), and this test has to put an unreadable one in its place");
        }

        if (TrainControlUI.getPrefs().getBoolean(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, false))
        {
            throw new SkipException("Not run here: this folder remembers window positions, so saving "
                + "the UI state would rewrite the saved diagram window titles");
        }

        TrainControlUI ui = windowless();

        // What the window's save reads, and nothing more
        set(ui, "model", stubModel((method, args) -> null));
        set(ui, "buttonMapping", new HashMap<Integer, JButton>());
        set(ui, "pageNames", new HashMap<Integer, String>());
        set(ui, "autosave", new JCheckBox());
        set(ui, "autonomyJSON", new JTextArea());

        List<HashMap<JButton, Locomotive>> pages = new ArrayList<>();
        pages.add(new HashMap<>());
        set(ui, "locMapping", pages);

        byte[] unreadable = testControlStationFaults.truncatedObjectStream();

        File backups = new File(Util.BACKUP_FOLDER);
        boolean hadBackups = backups.isDirectory();
        Set<String> before = testControlStationFaults.names(backups);

        try
        {
            Files.write(live.toPath(), unreadable);

            // As at startup: the file is there, and will not read
            ui.restoreState();

            // As on exit
            ui.saveState(false);

            assertEquals(testControlStationFaults.keptCopies(backups, before, unreadable), 1,
                "the UI state file could not be read at startup, and the save on exit wrote the empty "
                + "state over it without keeping a copy - every key mapping and page name would be gone");

            // And a file that reads is a normal save: nothing more is copied aside
            ui.restoreState();
            ui.saveState(false);

            assertEquals(testControlStationFaults.names(backups).size(), before.size() + 1,
                "a readable UI state file was copied aside as though it were unreadable");
        }
        finally
        {
            Files.deleteIfExists(live.toPath());

            for (String name : testControlStationFaults.names(backups))
            {
                if (!before.contains(name)) Files.deleteIfExists(new File(backups, name).toPath());
            }

            if (!hadBackups) Files.deleteIfExists(backups.toPath());
        }
    }

    /**
     * A UI state file that will not read is not saved over while its copy cannot be kept.
     *
     * The save cleared its "unreadable" mark first and then tried the copy.  When the copy failed, the
     * failure was logged and the save went on to replace the unreadable file - every key mapping and
     * page name gone, with no copy anywhere.  The mark is now cleared only once the copy is known to
     * exist, and until then the file is left as it is; the next save tries again (BPV-C7).
     *
     * The copy alone is made to fail as testControlStationFaults does it for the locomotive database:
     * a folder standing at every name the copy could be given.  Refuses to run where the test above
     * does.
     */
    @Test
    public void testAnUnreadableUiStateIsNotSavedOverWhileItsCopyCannotBeKept() throws Exception
    {
        File live = new File("UIState.data");

        if (live.exists())
        {
            throw new SkipException("Not run here: the working directory holds a UI state file ("
                + live.getAbsolutePath() + "), and this test has to put an unreadable one in its place");
        }

        if (TrainControlUI.getPrefs().getBoolean(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, false))
        {
            throw new SkipException("Not run here: this folder remembers window positions, so saving "
                + "the UI state would rewrite the saved diagram window titles");
        }

        TrainControlUI ui = windowless();

        // What the window's save reads, and nothing more
        set(ui, "model", stubModel((method, args) -> null));
        set(ui, "buttonMapping", new HashMap<Integer, JButton>());
        set(ui, "pageNames", new HashMap<Integer, String>());
        set(ui, "autosave", new JCheckBox());
        set(ui, "autonomyJSON", new JTextArea());

        List<HashMap<JButton, Locomotive>> pages = new ArrayList<>();
        pages.add(new HashMap<>());
        set(ui, "locMapping", pages);

        byte[] unreadable = testControlStationFaults.truncatedObjectStream();

        File backups = new File(Util.BACKUP_FOLDER);
        boolean hadBackups = backups.isDirectory();
        Set<String> before = testControlStationFaults.names(backups);
        List<File> blockers = new ArrayList<>();

        try
        {
            Files.write(live.toPath(), unreadable);

            // As at startup: the file is there, and will not read
            ui.restoreState();

            testControlStationFaults.blockTheCopy("UIState.data", blockers);

            // As on exit, with the copy impossible
            ui.saveState(false);

            assertEquals(Files.readAllBytes(live.toPath()), unreadable,
                "the unreadable UI state file could not be copied aside, and the save replaced it anyway "
                + "- no copy kept anywhere, and every key mapping and page name gone (BPV-C7)");

            // Once the copy can be made, the next save makes it, and then saves
            testControlStationFaults.unblock(blockers);

            ui.saveState(false);

            assertEquals(testControlStationFaults.keptCopies(backups, before, unreadable), 1,
                "the save after the copy became possible did not keep the unreadable file");

            assertFalse(java.util.Arrays.equals(Files.readAllBytes(live.toPath()), unreadable),
                "once the unreadable file was kept, the save must go ahead - refusing for ever would "
                + "lose whatever the session did");
        }
        finally
        {
            testControlStationFaults.unblock(blockers);

            Files.deleteIfExists(live.toPath());

            for (String name : testControlStationFaults.names(backups))
            {
                if (!before.contains(name)) Files.deleteIfExists(new File(backups, name).toPath());
            }

            if (!hadBackups) Files.deleteIfExists(backups.toPath());
        }
    }

    /**
     * Page names survive a UI state file that has fewer pages than this version shows.
     *
     * The file is the key mappings of each page followed by one last entry holding the page names and
     * the active page.  Restoring that last entry was gated on the file having MORE entries than this
     * version's ten pages - true for every file 2.8 writes, but not for one written by TrainControl
     * 3.0, where the number of pages can be changed and is often fewer.  Going back to 2.8 after
     * trying 3.0 then skipped the page names and the active page, and the save on exit wrote the
     * empty names over them.  The last entry is the page names whenever there is one.
     *
     * The restore is the first thing setViewListener does; everything after it builds the window, so
     * on a window-less instance the call ends at the first component it needs.  What is asserted is
     * what the restore left behind.
     *
     * Ported from the 3.0 branch (the page-name gate of d6b9b00c, OB-255).
     */
    @Test
    public void testPageNamesSurviveAFileWithFewerPages() throws Exception
    {
        File live = new File("UIState.data");

        if (live.exists())
        {
            throw new SkipException("Not run here: the working directory holds a UI state file ("
                + live.getAbsolutePath() + "), and this test has to put its own in its place");
        }

        try
        {
            // The control first: a file as 2.8 writes it, with all ten pages
            assertEquals(pageNamesAfterRestoring(live, 10).get(1), "Yard",
                "the fixture does not work even for a file with all ten pages, so the assertion "
                + "below would prove nothing");

            // And a file as 3.0 writes it for someone using four pages
            assertEquals(pageNamesAfterRestoring(live, 4).get(1), "Yard",
                "the page names were not restored from a UI state file with fewer than ten pages, as "
                + "TrainControl 3.0 writes - so they are lost at the next exit");
        }
        finally
        {
            Files.deleteIfExists(live.toPath());
        }
    }

    /**
     * Writes a UI state file with the given number of (empty) pages followed by the page names, runs
     * the window's restore on it, and returns the page names the window ended up with.
     */
    private static Map<Integer, String> pageNamesAfterRestoring(File live, int pages) throws Exception
    {
        List<Map<Integer, String>> state = new ArrayList<>();

        for (int i = 0; i < pages; i++)
        {
            state.add(new HashMap<>());
        }

        Map<Integer, String> names = new HashMap<>();
        names.put(1, "Yard");
        names.put(2, "Mainline");
        names.put(-1, "1");     // the active page
        names.put(-2, "-1");    // the active button: none
        state.add(names);

        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(
            new java.io.FileOutputStream(live)))
        {
            out.writeObject(state);
        }

        TrainControlUI ui = windowless();

        // What the restore reads, and nothing more
        set(ui, "buttonMapping", new HashMap<Integer, JButton>());
        set(ui, "pageNames", new HashMap<Integer, String>());

        try
        {
            ui.setViewListener(stubModel((method, args) -> null), null);

            fail("setViewListener finished on a window-less instance, so this test no longer knows "
                + "where it stops");
        }
        catch (NullPointerException windowNotBuilt)
        {
            // Expected: the first component the window-building part needs is not there
        }

        Field field = TrainControlUI.class.getDeclaredField("pageNames");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<Integer, String> restored = (Map<Integer, String>) field.get(ui);

        return restored;
    }

    /**
     * The name of a route and of a locomotive as a German layout has them: every accented letter that
     * windows-1252 and UTF-8 write differently.
     */
    private static final String ACCENTED = "Stra\u00dfe G\u00fcterzug";

    /**
     * Routes > Import reads a routes file exported before 2.7.4, in the computer's own character set, as
     * written - and a UTF-8 one, as every export since writes, still reads as written (MRV1-B2).
     *
     * From October 2023 until 2.7.4 the routes export wrote the computer's own character set -
     * windows-1252 on a Western European Windows - and 2.8.1 read the import the same way, so an older
     * backup came back as it went out.  2.8.2 read every file as UTF-8, and each accented letter of such
     * a backup became the replacement character: route names garbled, and a route command naming a
     * locomotive with an accent named no locomotive.
     *
     * The window's own handler, with the file chooser answered by the test and the model a stand-in
     * that keeps what it is asked to import and then stops the handler, before it records the folder
     * in the preferences.  Only meaningful where the computer's own character set is not UTF-8, which
     * is where such files were written; skipped elsewhere.
     */
    @Test
    public void testRoutesImportReadsAnOlderExportAsWritten() throws Exception
    {
        byte[] older = olderExport();

        assertEquals(importedByTheRoutesDoor(older), ACCENTED,
            "a routes file exported before 2.7.4, in this computer's own character set, was imported "
            + "with its accented letters garbled - route names and the locomotives its commands name "
            + "(MRV1-B2)");

        assertEquals(importedByTheRoutesDoor(ACCENTED.getBytes(StandardCharsets.UTF_8)), ACCENTED,
            "a routes file in UTF-8, as every export since 2.7.4 writes, no longer imports as written");
    }

    /**
     * Autonomy > Load JSON reads an autonomy file exported in the computer's own character set as
     * written - and a UTF-8 one still reads as written (MRV1-B2's sibling, older than 2.8.2).
     *
     * The same one-way decode as the routes import: everything was read as UTF-8, so a hand-exported or
     * older graph with accented station or locomotive names came back garbled.  The window's own
     * handler, as above; the stand-in text box keeps what the door decoded and stops the handler before
     * it records the folder or validates.
     */
    @Test
    public void testAutonomyImportReadsAnOlderExportAsWritten() throws Exception
    {
        byte[] older = olderExport();

        assertEquals(importedByTheAutonomyDoor(older), ACCENTED,
            "an autonomy file in this computer's own character set was loaded with its accented letters "
            + "garbled (MRV1-B2)");

        assertEquals(importedByTheAutonomyDoor(ACCENTED.getBytes(StandardCharsets.UTF_8)), ACCENTED,
            "an autonomy file in UTF-8, as TrainControl writes it, no longer loads as written");
    }

    /**
     * The accented text as an export before 2.7.4 wrote it on this computer: getBytes() with no
     * character set named.
     */
    private static byte[] olderExport() throws Exception
    {
        byte[] older = ACCENTED.getBytes(java.nio.charset.Charset.defaultCharset());

        if (java.util.Arrays.equals(older, ACCENTED.getBytes(StandardCharsets.UTF_8)))
        {
            throw new SkipException("Not run here: this computer's own character set is UTF-8, so its "
                + "older exports were UTF-8 too");
        }

        // The fallback is only reached by bytes that are not UTF-8; windows-1252 accents never are
        try
        {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(older));

            throw new SkipException("Not run here: this computer's own character set writes the fixture "
                + "as valid UTF-8, so it cannot tell the two readings apart");
        }
        catch (java.nio.charset.CharacterCodingException expected)
        {
            return older;
        }
    }

    /**
     * A window whose file chooser picks the file the test names, without building or showing anything.
     */
    public static class ChoosingWindow extends TrainControlUI
    {
        static volatile File chosen;

        @Override
        public JFileChooser getFileChooser(int type, String extension)
        {
            try
            {
                return windowless(PickingChooser.class);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * A file chooser, never built, that answers "open" with the test's file.
     */
    public static class PickingChooser extends JFileChooser
    {
        @Override
        public int showOpenDialog(java.awt.Component parent)
        {
            return APPROVE_OPTION;
        }

        @Override
        public File getSelectedFile()
        {
            return ChoosingWindow.chosen;
        }
    }

    /**
     * What Routes > Import hands the model to import, for a file holding the given bytes.
     */
    private static String importedByTheRoutesDoor(byte[] contents) throws Exception
    {
        final java.util.concurrent.atomic.AtomicReference<String> imported =
            new java.util.concurrent.atomic.AtomicReference<>();

        File file = File.createTempFile("routes", ".json");

        try
        {
            Files.write(file.toPath(), contents);
            ChoosingWindow.chosen = file;

            ChoosingWindow ui = windowless(ChoosingWindow.class);

            set(ui, "model", stubModel((method, args) ->
            {
                if (method.getName().equals("importRoutes"))
                {
                    imported.set((String) args[0]);

                    // Before the handler records the folder in the preferences
                    throw new StopHere();
                }

                return null;
            }));

            final javax.swing.JMenuItem item = new javax.swing.JMenuItem("Import");
            set(ui, "importRoutesMenuItem", item);

            Method handler = TrainControlUI.class.getDeclaredMethod(
                "importRoutesMenuItemActionPerformed", java.awt.event.ActionEvent.class);
            handler.setAccessible(true);

            runSwallowingItsEnd(() -> handler.invoke(ui, (java.awt.event.ActionEvent) null),
                () -> imported.get() != null && item.isEnabled());

            assertNotNull(imported.get(), "precondition: the routes import never reached the model");

            return imported.get();
        }
        finally
        {
            ChoosingWindow.chosen = null;
            Files.deleteIfExists(file.toPath());
        }
    }

    /**
     * What Autonomy > Load JSON puts in the configuration text box, for a file holding the given bytes.
     */
    private static String importedByTheAutonomyDoor(byte[] contents) throws Exception
    {
        final java.util.concurrent.atomic.AtomicReference<String> loaded =
            new java.util.concurrent.atomic.AtomicReference<>();

        File file = File.createTempFile("autonomy", ".json");

        try
        {
            Files.write(file.toPath(), contents);
            ChoosingWindow.chosen = file;

            ChoosingWindow ui = windowless(ChoosingWindow.class);

            set(ui, "model", stubModel((method, args) -> null));
            set(ui, "loadJSONButton", new JButton("Load"));

            set(ui, "autonomyJSON", new JTextArea()
            {
                @Override
                public void setText(String text)
                {
                    loaded.set(text);

                    // Before the handler records the folder in the preferences, or validates
                    throw new StopHere();
                }
            });

            Method handler = TrainControlUI.class.getDeclaredMethod(
                "loadJSONButtonActionPerformed", java.awt.event.ActionEvent.class);
            handler.setAccessible(true);

            runSwallowingItsEnd(() -> handler.invoke(ui, (java.awt.event.ActionEvent) null),
                () -> loaded.get() != null);

            assertNotNull(loaded.get(), "precondition: the autonomy import never reached the text box");

            return loaded.get();
        }
        finally
        {
            ChoosingWindow.chosen = null;
            Files.deleteIfExists(file.toPath());
        }
    }

    private interface Step
    {
        void run() throws Exception;
    }

    private interface Condition
    {
        boolean holds();
    }

    /**
     * Runs a handler whose worker the test ends on purpose, waits until the condition holds, and lets the
     * event thread run whatever the worker handed it.  The worker's end, and a dialog that cannot be
     * built on a window-less instance, are expected and not reported.
     */
    private static void runSwallowingItsEnd(Step step, Condition done) throws Exception
    {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { });

        try
        {
            step.run();

            long deadline = System.currentTimeMillis() + 5000;

            while (!done.holds() && System.currentTimeMillis() < deadline)
            {
                Thread.sleep(20);
            }

            Thread.sleep(200);

            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
        }
        finally
        {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * A TrainControlUI with no constructor run: no window, no components, no preferences, no files.
     * Package-private, as testAPathThatFailsPartWay drives the window's doors the same way.
     */
    static TrainControlUI windowless() throws Exception
    {
        return windowless(TrainControlUI.class);
    }

    /**
     * An instance of the given class with no constructor run - a window, or anything else whose constructor
     * would build or show something the test does not want.
     */
    static <T> T windowless(Class<T> type) throws Exception
    {
        // Reached reflectively so that the compiler does not warn about the internal class
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);

        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), type));
    }

    static void set(Object target, String name, Object value) throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * What a stand-in model answers.  Returning null means "the type's default".
     */
    interface Answer
    {
        Object answer(Method method, Object[] args) throws Throwable;
    }

    /**
     * A ViewListener that answers through the given function, with each return type's default for
     * everything the function leaves as null.
     */
    static ViewListener stubModel(Answer answer)
    {
        InvocationHandler handler = (proxy, method, args) ->
        {
            Object result = answer.answer(method, args);

            if (result != null) return result;

            Class<?> type = method.getReturnType();

            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0.0;
            if (type == float.class) return 0.0f;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            if (type == char.class) return (char) 0;

            return null;
        };

        return (ViewListener) Proxy.newProxyInstance(ViewListener.class.getClassLoader(),
            new Class<?>[]{ ViewListener.class }, handler);
    }
}
