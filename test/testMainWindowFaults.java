import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.model.ViewListener;

/**
 * Faults in the main window's handlers, backported to 2.8 from the 3.0 branch.
 *
 * The window is never built here.  Building it opens whatever layout the preferences name, reads the
 * UI state file from the working directory and can raise modal dialogs - none of which these handlers
 * need.  Each test instead takes a TrainControlUI allocated WITHOUT running its constructor, and gives
 * it exactly the few fields the handler under test touches.  Nothing here is shown on screen, and
 * nothing here touches a preference or a file.
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
