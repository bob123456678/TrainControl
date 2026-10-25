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
 * JOptionPane sizes a message to its longest line, so a list of names or a long path made a popup wider than the
 * screen.  The caller still shows the popup, hung off the window that asked.
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

    /**
     * @param text the message
     * @return the text wrapped at {@link #WIDTH}; a word longer than a line, such as a path, breaks where it must
     */
    public static JTextArea wrapped(String text)
    {
        JTextArea area = textArea(text);

        area.setSize(WIDTH, Short.MAX_VALUE);
        area.setPreferredSize(new Dimension(WIDTH, area.getPreferredSize().height));

        return area;
    }

    /**
     * @param sentence what happened, or the question
     * @param lines the list, one item a line; it scrolls past {@link #ROWS} lines
     * @return the sentence over the list
     */
    public static JComponent message(String sentence, List<String> lines)
    {
        JTextArea list = textArea(String.join("\n", lines));

        list.setSize(WIDTH, Short.MAX_VALUE);

        int shown = Math.min(list.getPreferredSize().height,
            ROWS * list.getFontMetrics(list.getFont()).getHeight());

        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scroll.setBorder(BorderFactory.createTitledBorder(I18n.t("ui.details")));
        scroll.getViewport().setPreferredSize(new Dimension(WIDTH, shown));

        list.setCaretPosition(0);

        JPanel panel = new JPanel(new BorderLayout(0, 8));

        panel.setOpaque(false);
        panel.add(wrapped(sentence), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

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
