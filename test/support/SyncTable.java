package support;

import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import org.traincontrol.util.I18n;

/**
 * Answers the manual Sync's table (FR-249) as the user would: a thread watches for it while open, picks each named
 * locomotive's answer ("KEEP", "RENAME", "ADDRESS", "BOTH", "IGNORE" or "ASK"), then presses Continue Sync, or Cancel
 * when told to, and keeps what each table showed.  Found by its title and its controls' names, so a claim compiles
 * before the window exists.  Close it in a finally, or with try-with-resources.
 */
public final class SyncTable implements AutoCloseable
{
    /** What one table showed, read just before it was answered. */
    public static final class Shown
    {
        public final JDialog dialog;
        public final String heading;
        /** Each row's locomotive, top to bottom. */
        public final List<String> rows = new ArrayList<>();
        /** Each row's answer selected as it opened. */
        public final Map<String, String> startsOn = new LinkedHashMap<>();

        Shown(JDialog dialog)
        {
            this.dialog = dialog;
            JLabel h = Components.find(dialog, JLabel.class, l -> l.getForeground().equals(
                org.traincontrol.gui.widgets.UiStyle.BLUE));
            this.heading = h == null ? null : h.getText();
            for (JRadioButton b : Components.all(dialog, JRadioButton.class, b -> b.getName() != null))
            {
                String row = b.getName().substring(0, b.getName().lastIndexOf(':'));
                if (!this.rows.contains(row)) this.rows.add(row);
                if (b.isSelected()) this.startsOn.put(row, b.getName().substring(row.length() + 1));
            }
        }

        /** The control named so in this row: an answer's radio, or "deletes" or "collision", a mark. */
        public <T extends java.awt.Component> T in(String row, String what, Class<T> type)
        {
            return Components.named(this.dialog, row + ":" + what, type);
        }
    }

    private final List<Shown> shown = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean going = new AtomicBoolean(true);
    private final Thread watching;

    private SyncTable(Map<String, String> answers, boolean cancel, Consumer<Shown> look)
    {
        Set<Window> handled = Collections.newSetFromMap(new IdentityHashMap<>());
        this.watching = new Thread(() ->
        {
            while (this.going.get())
            {
                for (Window w : Window.getWindows())
                {
                    if (w instanceof JDialog && w.isShowing() && I18n.t("sync.ui.title").equals(((JDialog) w)
                        .getTitle()) && handled.add(w)) answer((JDialog) w, answers, cancel, look);
                }
                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException stop)
                {
                    return;
                }
            }
        }, "sync-table");
        this.watching.setDaemon(true);
        this.watching.start();
    }

    /** Answers every table so, rows not named left as they start, then presses Continue Sync. */
    public static SyncTable answering(Map<String, String> answers)
    {
        return new SyncTable(answers, false, null);
    }

    /** The same, handing each table to `look` on the event thread first, which may press its controls. */
    public static SyncTable answering(Map<String, String> answers, Consumer<Shown> look)
    {
        return new SyncTable(answers, false, look);
    }

    /** Presses Cancel on every table, after `look`, which may be null. */
    public static SyncTable canceling(Consumer<Shown> look)
    {
        return new SyncTable(Collections.<String, String>emptyMap(), true, look);
    }

    /** One locomotive's answer, as `answering` takes them. */
    public static Map<String, String> answer(String... rowsAndAnswers)
    {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < rowsAndAnswers.length; i += 2) m.put(rowsAndAnswers[i], rowsAndAnswers[i + 1]);
        return m;
    }

    private void answer(JDialog dialog, Map<String, String> answers, boolean cancel, Consumer<Shown> look)
    {
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                Shown s = new Shown(dialog);
                this.shown.add(s);
                try
                {
                    if (look != null) look.accept(s);
                }
                finally
                {
                    // A look that closed the table leaves nothing to press
                    for (Map.Entry<String, String> a : answers.entrySet())
                    {
                        AbstractButton b = s.in(a.getKey(), a.getValue(), AbstractButton.class);
                        if (b != null && dialog.isDisplayable()) b.doClick();
                    }
                    if (dialog.isDisplayable())
                        Components.withText(dialog, I18n.t(cancel ? "ui.cancel" : "sync.ui.continue")).doClick();
                }
            });
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /** Every table shown so far. */
    public List<Shown> shown()
    {
        synchronized (this.shown)
        {
            return new ArrayList<>(this.shown);
        }
    }

    @Override
    public void close()
    {
        this.going.set(false);
        this.watching.interrupt();
    }
}
