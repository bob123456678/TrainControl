import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.PositionAwareJFrame;
import org.traincontrol.gui.TrainControlUI;
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

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * A TrainControlUI with no constructor run: no window, no components, no preferences, no files.
     */
    private static TrainControlUI windowless() throws Exception
    {
        // Reached reflectively so that the compiler does not warn about the internal class
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);

        return (TrainControlUI) unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), TrainControlUI.class);
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * What a stand-in model answers.  Returning null means "the type's default".
     */
    private interface Answer
    {
        Object answer(Method method, Object[] args) throws Throwable;
    }

    /**
     * A ViewListener that answers through the given function, with each return type's default for
     * everything the function leaves as null.
     */
    private static ViewListener stubModel(Answer answer)
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
