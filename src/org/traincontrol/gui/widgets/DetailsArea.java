package org.traincontrol.gui.widgets;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import org.traincontrol.util.I18n;

/**
 * A popup's message at a readable width: a short sentence, with any long list under it in a scrolling Details area.
 */
public final class DetailsArea
{
    /** The text's width in pixels: with the icon and the insets the popup stays under about 640. */
    public static final int WIDTH = 440;

    /** Lines of the list shown before it scrolls. */
    private static final int ROWS = 8;

    private DetailsArea()
    {
    }

    private static JTextArea wrapped(String text)
    {
        JTextArea area = textArea(text);

        area.setSize(WIDTH, Short.MAX_VALUE);
        area.setPreferredSize(new Dimension(WIDTH, area.getPreferredSize().height));

        return area;
    }

    /** The text wrapped at {@link #WIDTH} in a box that scrolls past `rows` lines, for a list or a long help page. */
    public static JScrollPane scrolling(String text, int rows)
    {
        JTextArea area = textArea(text);
        area.setSize(WIDTH, Short.MAX_VALUE);
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setPreferredSize(new Dimension(WIDTH, Math.min(area.getPreferredSize().height,
            rows * area.getFontMetrics(area.getFont()).getHeight())));
        return scroll;
    }

    /** The sentence over the list, one item a line, if any; it scrolls past {@link #ROWS} lines. */
    public static JComponent message(String sentence, List<String> lines)
    {
        JScrollPane scroll = scrolling(String.join("\n", lines), ROWS);
        scroll.setBorder(BorderFactory.createTitledBorder(I18n.t("ui.details")));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(wrapped(sentence), BorderLayout.NORTH);
        if (!lines.isEmpty()) panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    /** Read-only text in the label font, wrapping at words. */
    private static JTextArea textArea(String text)
    {
        JTextArea area = new JTextArea(text);

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(UIManager.getFont("Label.font"));

        return area;
    }
}
