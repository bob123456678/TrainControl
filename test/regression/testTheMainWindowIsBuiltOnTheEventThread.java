package regression;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import support.Display;
import support.Reflect;

/**
 * OB-444: start-up built the main window on its own thread, but the window is realized part way through its build
 * (the generated form packs it), so a window event's layout on the event thread could meet a half-mounted GroupLayout
 * and throw in `getBaseline`.  Start-up's own build step is called from a plain thread, as `init` calls it.
 *
 * @author Adam
 */
public class testTheMainWindowIsBuiltOnTheEventThread
{
    /**
     * While start-up builds the window, the event thread lays out every realized window over and over (what a resize
     * event does), and a watcher records each change to a realized component made from another thread.  Neither may
     * find anything.
     *
     * MUTATION: make `MarklinControlStation.buildTheWindow` return `new TrainControlUI()` on the calling thread and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testNothingInTheWindowIsChangedOffTheEventThreadWhileItIsBuilt() throws Exception
    {
        Display.needed("the main window is built");

        support.LayoutSandbox sandbox = null;

        final List<String> offTheEventThread = Collections.synchronizedList(new ArrayList<>());
        final List<Throwable> thrownByALayout = Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean building = new AtomicBoolean(true);

        RepaintManager was = RepaintManager.currentManager((JComponent) null);
        TrainControlUI ui = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            RepaintManager watcher = new RepaintManager()
            {
                @Override
                public void addDirtyRegion(JComponent c, int x, int y, int w, int h)
                {
                    note(c);
                    super.addDirtyRegion(c, x, y, w, h);
                }

                @Override
                public void addInvalidComponent(JComponent c)
                {
                    note(c);
                    super.addInvalidComponent(c);
                }

                private void note(JComponent c)
                {
                    if (!SwingUtilities.isEventDispatchThread() && c.isDisplayable() && offTheEventThread.size() < 5)
                    {
                        offTheEventThread.add(c.getClass().getName() + " on " + Thread.currentThread().getName());
                    }
                }
            };

            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(watcher));

            // Reposts itself until the build returns, so it runs between any two steps a build off this thread takes.
            Runnable layOutEveryWindow = new Runnable()
            {
                @Override
                public void run()
                {
                    for (java.awt.Window w : java.awt.Window.getWindows())
                    {
                        if (!w.isDisplayable()) continue;

                        try
                        {
                            invalidateAll(w);
                            w.validate();
                        }
                        catch (Throwable t)
                        {
                            if (thrownByALayout.size() < 5) thrownByALayout.add(t);
                        }
                    }

                    if (building.get()) SwingUtilities.invokeLater(this);
                }
            };

            SwingUtilities.invokeLater(layOutEveryWindow);

            try
            {
                ui = (TrainControlUI) Reflect.method(MarklinControlStation.class, "buildTheWindow").invoke(null);
            }
            finally
            {
                building.set(false);
            }

            assertNotNull(ui, "start-up built no window, so nothing below was watched");

            // Drained, so a layout still queued from the build is counted.
            SwingUtilities.invokeAndWait(() -> { });

            assertTrue(thrownByALayout.isEmpty(), "the event thread's layout threw while start-up built the main"
                + " window (OB-444's NullPointerException in GroupLayout): " + describe(thrownByALayout));

            assertTrue(offTheEventThread.isEmpty(), "start-up changed components of the realized main window off the"
                + " event thread, which a window event's layout races (OB-444): " + offTheEventThread);
        }
        finally
        {
            final TrainControlUI closing = ui;

            SwingUtilities.invokeAndWait(() ->
            {
                RepaintManager.setCurrentManager(was);

                if (closing != null) closing.dispose();
            });

            if (sandbox != null) sandbox.close();
        }
    }

    private static void invalidateAll(java.awt.Container c)
    {
        synchronized (c.getTreeLock())
        {
            c.invalidate();

            for (java.awt.Component child : c.getComponents())
            {
                if (child instanceof java.awt.Container) invalidateAll((java.awt.Container) child);
                else child.invalidate();
            }
        }
    }

    private static String describe(List<Throwable> thrown)
    {
        StringBuilder said = new StringBuilder();

        synchronized (thrown)
        {
            for (Throwable t : thrown)
            {
                said.append(t).append(" at ").append(t.getStackTrace().length > 0 ? t.getStackTrace()[0] : "?")
                    .append("; ");
            }
        }

        return said.toString();
    }
}
