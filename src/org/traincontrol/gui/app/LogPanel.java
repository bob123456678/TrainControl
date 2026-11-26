package org.traincontrol.gui.app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.logging.Level;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.traincontrol.gui.widgets.RowIcons;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The Log tab: the program's messages, newest first, stamped when logged, capped at {@link #MAX_CHARS}.
 *
 * A message's level (FR-190) is passed by its caller: SEVERE an error, WARNING a warning, less a notice; one logged by
 * key takes the level the key names ({@link #levelOf}), so it is the same in every language.  A gutter shows the red
 * or yellow triangle beside an error's or a warning's first line, a blank of the same width beside a notice.  The text
 * stays plain for selection, copy and Export Log.  Three boxes above the text (FR-257) hide the notices, the warnings
 * or the errors: every message stays kept, in order, and shows again when its box is ticked again.  The cap counts the
 * hidden messages too.  {@link #log} takes any thread; the rest is event thread.
 */
public final class LogPanel extends JPanel
{
    /** About a thousand lines: inserting at the front stays cheap for the life of a session. */
    static final int MAX_CHARS = 100000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");
    final JTextArea area = new JTextArea(5, 20);
    private final JScrollPane scroll = new JScrollPane(this.area);
    final Gutter gutter = new Gutter();
    private final Icon warning;
    private final Icon error;
    /** One per message, newest first, shown or not, while its first line is kept. */
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    /** The characters the entries hold. */
    private int chars = 0;
    final JCheckBox infoBox;
    final JCheckBox warningBox;
    final JCheckBox errorBox;

    private static final class Entry implements java.io.Serializable
    {
        private static final long serialVersionUID = 1L;
        final Level level;
        String text;
        int lines;
        Entry(Level level, String text)
        {
            this.level = level;
            this.text = text;
            this.lines = count(text);
        }

        /** Keeps the first `keep` characters: the cap's cut. */
        void keep(int keep)
        {
            this.text = this.text.substring(0, keep);
            this.lines = count(this.text);
        }
        /** The lines a text spans, the last one cut short by the cap included. */
        private static int count(String text)
        {
            return text.length() - text.replace("\n", "").length() + (text.endsWith("\n") ? 0 : 1);
        }
    }

    public LogPanel()
    {
        super(new BorderLayout());
        // Read-only (MT-362): typed text would sit among the program's messages.
        this.area.setEditable(false);
        this.area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.warning = RowIcons.warning(rowHeight() - 2, false);
        this.error = RowIcons.warning(rowHeight() - 2, true);
        this.gutter.setFocusable(false);
        ToolTipManager.sharedInstance().registerComponent(this.gutter);
        this.scroll.setRowHeaderView(this.gutter);
        setOpaque(false);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.setOpaque(false);
        this.infoBox = box(row, "ui.log.showInfo", null);
        this.warningBox = box(row, "ui.log.showWarnings", this.warning);
        this.errorBox = box(row, "ui.log.showErrors", this.error);
        add(row, BorderLayout.NORTH);
        add(this.scroll, BorderLayout.CENTER);
    }

    /** A ticked box that shows or hides its type, followed by the type's triangle when it has one. */
    private JCheckBox box(JPanel row, String key, Icon icon)
    {
        JCheckBox box = new JCheckBox(I18n.t(key), true);
        box.setFont(UiStyle.LABEL_FONT);
        box.setOpaque(false);
        box.addActionListener(e -> refilter());
        row.add(box);
        if (icon != null) row.add(new JLabel(icon));
        return box;
    }

    /** Whether a message of this level is on show: an error's box, a warning's, else the notices'. */
    private boolean shows(Level level)
    {
        if (level.intValue() >= Level.SEVERE.intValue()) return this.errorBox.isSelected();
        return (level.intValue() >= Level.WARNING.intValue() ? this.warningBox : this.infoBox).isSelected();
    }

    /** Shows the messages whose boxes are ticked, in their order, and no others. */
    private void refilter()
    {
        StringBuilder shown = new StringBuilder();
        for (Entry entry : this.entries) if (shows(entry.level)) shown.append(entry.text);
        this.area.setText(shown.toString());
        this.area.setCaretPosition(0);
        this.gutter.revalidate();
        this.gutter.repaint();
    }

    /** A key's level: SEVERE when its last part starts "error" or "fatalError", WARNING when "warn", else INFO. */
    public static Level levelOf(String key)
    {
        String name = key.substring(key.lastIndexOf('.') + 1);
        if (name.startsWith("error") || name.startsWith("fatalError")) return Level.SEVERE;
        return name.startsWith("warn") ? Level.WARNING : Level.INFO;
    }

    /** Puts a message on top, stamped now rather than when the event thread gets to it (FR-051). */
    public void log(String message, Level level)
    {
        final String entry = LocalTime.now().format(STAMP) + " " + message + "\n";
        SwingUtilities.invokeLater(() ->
        {
            this.entries.addFirst(new Entry(level, entry));
            this.chars += entry.length();
            if (shows(level))
            {
                // The newest line shows at the top, but a sideways position the reader chose stays (OB-471)
                int across = this.scroll.getHorizontalScrollBar().getValue();
                this.area.insert(entry, 0);
                this.area.setCaretPosition(0);
                // After the caret's own scroll, which is queued by the insert
                SwingUtilities.invokeLater(() -> this.scroll.getHorizontalScrollBar().setValue(across));
            }
            trim();
            this.gutter.revalidate();
            this.gutter.repaint();
        });
    }

    /** Cuts the oldest messages' text at the cap, as every insert at the front shifts it all, and forgets the empty. */
    private void trim()
    {
        Document document = this.area.getDocument();
        int excess = this.chars - MAX_CHARS;
        while (excess > 0)
        {
            Entry last = this.entries.getLast();
            int cut = Math.min(excess, last.text.length());
            // The oldest message's text ends the shown text only while it is shown
            if (shows(last.level))
            {
                try
                {
                    document.remove(document.getLength() - cut, cut);
                }
                catch (BadLocationException e)
                {
                    // Unreachable: both offsets come from the document itself
                }
            }
            this.chars -= cut;
            excess -= cut;
            if (cut == last.text.length()) this.entries.removeLast();
            else last.keep(last.text.length() - cut);
        }
    }

    /** The log as it shows, newest first, stamps included: plain text, as copied and exported. */
    public String text()
    {
        return this.area.getText();
    }

    /** The level marked beside a line (0 the top): its entry's on an entry's first line, else INFO. */
    Level levelAt(int line)
    {
        int start = 0;

        for (Entry entry : this.entries)
        {
            if (!shows(entry.level)) continue;
            if (start == line) return entry.level;
            start += entry.lines;
            if (start > line) break;
        }
        return Level.INFO;
    }

    /** The triangle for a level, null for a notice. */
    Icon iconFor(Level level)
    {
        if (level.intValue() >= Level.SEVERE.intValue()) return this.error;
        return level.intValue() >= Level.WARNING.intValue() ? this.warning : null;
    }

    private int rowHeight()
    {
        return this.area.getFontMetrics(this.area.getFont()).getHeight();
    }

    /** The triangles' column, as tall as the text and scrolled with it up and down, never sideways. */
    final class Gutter extends JComponent
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
