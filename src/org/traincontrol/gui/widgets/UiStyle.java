package org.traincontrol.gui.widgets;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;

/** The owner's label style (docs/UI-standards.md), for windows that hand-build their labels. */
public final class UiStyle
{
    /** Dark blue: section headings, and field and row labels. */
    public static final Color BLUE = new Color(0, 0, 155);

    /** Segoe UI Semibold 13. */
    public static final Font LABEL_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 13);

    private UiStyle()
    {
    }

    /** A label's text without the colon some older bundle entries end in. */
    public static String withoutColon(String text)
    {
        String t = text.trim();

        return t.endsWith(":") ? t.substring(0, t.length() - 1).trim() : t;
    }

    /** A field or row label. */
    public static JLabel label(String text)
    {
        JLabel l = new JLabel(text);

        style(l);

        return l;
    }

    /** Puts a label in the style. */
    public static void style(JLabel l)
    {
        l.setFont(LABEL_FONT);
        l.setForeground(BLUE);
    }
}
