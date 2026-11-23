package org.traincontrol.gui.widgets;

import com.formdev.flatlaf.ui.FlatOptionPaneUI;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.util.StringJoiner;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;

/**
 * Every popup's text wraps at {@link #CAP}, so no message, exception text or path makes a popup as wide as its longest
 * line (FR-254).  Lines that fit and the text's own line breaks are kept; HTML and component messages are left alone.
 * Swing only calls {@link #createUI}, so this is a factory, not a UI class that would hide FlatLaf's createUI.
 */
public final class WrappingOptionPaneUI
{
    /** The widest a line of a popup's text may be, in pixels at 100%; it grows with the UI scale. */
    public static final int CAP = 440;

    public static ComponentUI createUI(JComponent pane)
    {
        return new FlatOptionPaneUI()
        {
            @Override
            protected void addMessageComponents(Container container, GridBagConstraints cons, Object msg, int maxll,
                boolean internallyCreated)
            {
                if (msg != null && !(msg instanceof Component || msg instanceof Object[] || msg instanceof Icon)
                    && !BasicHTML.isHTMLString(msg.toString()))
                {
                    Font font = UIManager.getFont("OptionPane.messageFont");
                    msg = wrap(msg.toString(), optionPane.getFontMetrics(font != null ? font
                        : UIManager.getFont("Label.font")), UIScale.scale(CAP));
                    maxll = Integer.MAX_VALUE;
                }
                super.addMessageComponents(container, cons, msg, maxll, internallyCreated);
            }
        };
    }

    /** The text broken where a line would pass `width`: at a space, after / or \ in a path, else inside the word. */
    private static String wrap(String text, FontMetrics metrics, int width)
    {
        StringJoiner out = new StringJoiner("\n");
        for (String line : text.split("\r?\n", -1))
        {
            StringBuilder wrapped = new StringBuilder();
            int start = 0;
            while (metrics.stringWidth(line.substring(start)) > width)
            {
                int end = start + 1;
                while (metrics.stringWidth(line.substring(start, end + 1)) <= width) end++;
                int cut = Math.max(line.lastIndexOf(' ', end),
                    Math.max(line.lastIndexOf('/', end - 1), line.lastIndexOf('\\', end - 1)) + 1);
                if (cut <= start) cut = end;
                wrapped.append(line.substring(start, cut).replaceAll(" +$", "")).append('\n');
                for (start = cut; start < line.length() && line.charAt(start) == ' '; ) start++;
            }
            out.add(wrapped.append(line.substring(start)));
        }
        return out.toString();
    }
}
