package support;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Window;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.text.JTextComponent;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestResult;

/**
 * Ends a test JVM that is stuck behind a modal dialog nobody answers, and says which dialog it was.
 *
 * A modal dialog no test answers holds the test thread, the JVM and its slot until the class cap (an hour).  This
 * watches for one: when the same modal dialog has been showing, and no test method has started or finished, for
 * `traincontrol.dialogWatchdog` seconds (the environment's `TC_DIALOG_WATCH`, else 60), it prints one line naming the dialog's title and text and halts
 * the JVM with status 3.  The runners (docs/tools/one.sh, battery.sh) name it with TestNG's `-listener` and report the
 * class as failed with that line; the other methods of the class are not run.
 *
 * Test code only: it reads the open windows and touches nothing in production.
 */
public class DialogWatchdog implements ISuiteListener, IInvokedMethodListener
{
    /** What the runners look for in a class's output. */
    public static final String MARKER = "*** UNANSWERED DIALOG";

    private static volatile long lastProgress = System.currentTimeMillis();

    private static Thread watcher;

    @Override
    public synchronized void onStart(ISuite suite)
    {
        if (watcher != null) return;

        String set = System.getProperty("traincontrol.dialogWatchdog", System.getenv("TC_DIALOG_WATCH"));

        final long limit = (set == null ? 60L : Long.parseLong(set.trim())) * 1000L;

        watcher = new Thread("dialog-watchdog")
        {
            @Override
            public void run()
            {
                Dialog watched = null;
                long since = 0;

                while (true)
                {
                    try
                    {
                        Thread.sleep(Math.max(100L, Math.min(1000L, limit / 5)));
                    }
                    catch (InterruptedException e)
                    {
                        return;
                    }

                    Dialog now = modalDialog();
                    long time = System.currentTimeMillis();

                    if (now != watched)
                    {
                        watched = now;
                        since = time;
                    }

                    if (watched != null && time - since >= limit && time - lastProgress >= limit)
                    {
                        System.out.println(MARKER + ": \"" + watched.getTitle() + "\" says \"" + textOf(watched)
                            + "\" - no test finished in " + limit / 1000 + " s with it showing, so the JVM was ended ***");
                        System.out.flush();
                        Runtime.getRuntime().halt(3);
                    }
                }
            }
        };

        watcher.setDaemon(true);
        watcher.start();
    }

    @Override
    public void onFinish(ISuite suite)
    {
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult result)
    {
        lastProgress = System.currentTimeMillis();
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result)
    {
        lastProgress = System.currentTimeMillis();
    }

    private static Dialog modalDialog()
    {
        for (Window w : Window.getWindows())
        {
            if (w instanceof Dialog && w.isShowing() && ((Dialog) w).getModalityType() != Dialog.ModalityType.MODELESS)
            {
                return (Dialog) w;
            }
        }

        return null;
    }

    /** The message of the option pane in the dialog, else the text of its labels and fields, on one line. */
    private static String textOf(Dialog dialog)
    {
        StringBuilder out = new StringBuilder();

        collect(dialog, out);

        String text = out.toString().replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();

        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private static void collect(Component c, StringBuilder out)
    {
        if (c instanceof JOptionPane && ((JOptionPane) c).getMessage() instanceof String)
        {
            out.append(' ').append(((JOptionPane) c).getMessage());

            return;
        }

        if (c instanceof JLabel) out.append(' ').append(((JLabel) c).getText());
        else if (c instanceof JTextComponent) out.append(' ').append(((JTextComponent) c).getText());

        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents()) collect(child, out);
        }
    }
}
