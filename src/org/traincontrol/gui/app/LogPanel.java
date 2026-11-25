package org.traincontrol.gui.app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.logging.Level;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.util.I18n;

/**
 * The Log tab: the program's messages, newest first, each stamped with the time it was logged at, capped at
 * {@link #MAX_CHARS}.
 *
 * A message's level (FR-190) is a {@link Level} its caller passes: SEVERE is an error, WARNING a warning, anything less
 * a notice.  A message logged by bundle key takes the level the key names ({@link #levelOf}), so a translated line
 * classifies the same in every language; its text is never read.  Errors show the red triangle and warnings the yellow
 * one in a gutter beside the entry's first line; notices a blank of the same width, so every line starts at the same
 * place.  The text itself stays plain: selection, copy and Export Log read it unchanged.
 *
 * {@link #log} may be called from any thread; everything else on the event thread.
 */
public final class LogPanel extends JPanel
{
    /**
     * How much of the log is kept: roughly a thousand lines, far more than anyone scrolls back through, and small
     * enough that inserting at the front stays cheap for the life of a session.
     */
    static final int MAX_CHARS = 100000;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextArea area = new JTextArea(5, 20);
    private final JScrollPane scroll = new JScrollPane(this.area);
    private final Gutter gutter = new Gutter();
    private final Icon warning;
    private final Icon error;

    /** One per logged message, newest first, as far as its first line is still in the text; event thread. */
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    /** The lines the entries span. */
    private int lines = 0;

    private static final class Entry
    {
        final Level level;
        final int lines;

        Entry(Level level, int lines)
        {
            this.level = level;
            this.lines = lines;
        }
    }

    public LogPanel()
    {
        super(new BorderLayout());

        // Read-only (MT-362): typed text would sit among the program's messages.
        this.area.setEditable(false);
        this.area.setFont(new Font("Monospaced", Font.PLAIN, 12));

        int size = rowHeight() - 2;
        this.warning = RowIcons.warning(size, false);
        this.error = RowIcons.warning(size, true);

        this.gutter.setFocusable(false);
        ToolTipManager.sharedInstance().registerComponent(this.gutter);
        this.scroll.setRowHeaderView(this.gutter);

        setOpaque(false);
        add(this.scroll, BorderLayout.CENTER);
    }

    /**
     * The level a message logged under a bundle key has: SEVERE when the key's last part starts with "error" or
     * "fatalError", WARNING when it starts with "warn", INFO otherwise.
     */
    public static Level levelOf(String key)
    {
        String name = key.substring(key.lastIndexOf('.') + 1);

        if (name.startsWith("error") || name.startsWith("fatalError")) return Level.SEVERE;

        return name.startsWith("warn") ? Level.WARNING : Level.INFO;
    }

    /** Puts a message at the top, stamped now rather than when the event thread gets to it (FR-051); any thread. */
    public void log(String message, Level level)
    {
        final String entry = LocalTime.now().format(STAMP) + " " + message + "\n";

        SwingUtilities.invokeLater(() ->
        {
            // The newest line shows at the top, but a sideways position the reader chose stays (OB-471)
            int across = this.scroll.getHorizontalScrollBar().getValue();

            this.area.insert(entry, 0);
            this.area.setCaretPosition(0);
            // After the caret's own scroll, which is queued by the insert
            SwingUtilities.invokeLater(() -> this.scroll.getHorizontalScrollBar().setValue(across));

            int spans = entry.length() - entry.replace("\n", "").length();
            this.entries.addFirst(new Entry(level, spans));
            this.lines += spans;

            trim();
            this.gutter.revalidate();
            this.gutter.repaint();
        });
    }

    /** Cuts the text at the cap (every insert at the front shifts the whole document) and forgets the cut entries. */
    private void trim()
    {
        Document document = this.area.getDocument();
        int length = document.getLength();

        if (length <= MAX_CHARS) return;

        try
        {
            document.remove(MAX_CHARS, length - MAX_CHARS);
        }
        catch (BadLocationException e)
        {
            // Unreachable: both offsets come from the document itself one statement earlier
        }

        // A last line left empty by a cut just after a line break holds no entry
        Element root = document.getDefaultRootElement();
        int kept = root.getElementCount();

        if (root.getElement(kept - 1).getStartOffset() == document.getLength()) kept--;

        while (this.lines - this.entries.getLast().lines >= kept) this.lines -= this.entries.removeLast().lines;
    }

    /** The log as it shows, newest first, stamps included; plain text, as copied and exported. */
    public String text()
    {
        return this.area.getText();
    }

    /** The level marked beside a line (0 is the top): its entry's for an entry's first line, INFO for any other. */
    Level levelAt(int line)
    {
        int start = 0;

        for (Entry entry : this.entries)
        {
            if (start == line) return entry.level;

            start += entry.lines;

            if (start > line) break;
        }

        return Level.INFO;
    }

    /** The triangle for a level, or null for a notice. */
    Icon iconFor(Level level)
    {
        if (level.intValue() >= Level.SEVERE.intValue()) return this.error;

        return level.intValue() >= Level.WARNING.intValue() ? this.warning : null;
    }

    private int rowHeight()
    {
        return this.area.getFontMetrics(this.area.getFont()).getHeight();
    }

    /** The column of triangles, as tall as the text and scrolled with it up and down, never sideways. */
    private final class Gutter extends JComponent
    {
        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(LogPanel.this.warning.getIconWidth() + 4, LogPanel.this.area.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Rectangle clip = g.getClipBounds();
            int row = rowHeight();
            int top = LogPanel.this.area.getInsets().top;

            g.setColor(LogPanel.this.area.getBackground());
            g.fillRect(clip.x, clip.y, clip.width, clip.height);

            for (int line = Math.max(0, (clip.y - top) / row); top + line * row < clip.y + clip.height; line++)
            {
                Icon icon = iconFor(levelAt(line));

                if (icon != null) icon.paintIcon(this, g, 2, top + line * row + (row - icon.getIconHeight()) / 2);
            }
        }

        @Override
        public String getToolTipText(MouseEvent event)
        {
            Icon icon = iconFor(levelAt((event.getY() - LogPanel.this.area.getInsets().top) / rowHeight()));

            if (icon == null) return null;

            return I18n.t(icon == LogPanel.this.error ? "ui.log.markError" : "ui.log.markWarning");
        }
    }
}
