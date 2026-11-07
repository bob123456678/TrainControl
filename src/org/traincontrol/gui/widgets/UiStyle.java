package org.traincontrol.gui.widgets;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;

/** The owner's label style (docs/UI-standards.md), for windows that hand-build their labels. */
public final class UiStyle
{
    /** Dark blue: section headings only. */
    public static final Color BLUE = new Color(0, 0, 155);

    /** Black: item text, such as a field's or a row's label. */
    public static final Color TEXT = Color.BLACK;

    /** Segoe UI Semibold 13. */
    public static final Font LABEL_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 13);

    /** Segoe UI Bold 12: every button. */
    public static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 12);

    private UiStyle()
    {
    }

    /** A label's text without the colon some older bundle entries end in. */
    public static String withoutColon(String text)
    {
        String t = text.trim();

        return t.endsWith(":") ? t.substring(0, t.length() - 1).trim() : t;
    }

    /** A field or row label: item text, black. */
    public static JLabel label(String text)
    {
        JLabel l = new JLabel(text);

        style(l);

        return l;
    }

    /** A label in black, as the standard has it, for a window whose section headings alone are blue. */
    public static JLabel blackLabel(String text)
    {
        JLabel l = new JLabel(text);

        l.setFont(LABEL_FONT);
        l.setForeground(Color.BLACK);

        return l;
    }

    /** A section heading: dark blue. */
    public static JLabel heading(String text)
    {
        JLabel l = new JLabel(text);

        styleHeading(l);

        return l;
    }

    /** Puts a button in the standard's button font, and returns it. */
    public static <T extends javax.swing.AbstractButton> T button(T b)
    {
        b.setFont(BUTTON_FONT);

        return b;
    }

    /** Puts a label in the item style. */
    public static void style(JLabel l)
    {
        l.setFont(LABEL_FONT);
        l.setForeground(TEXT);
    }

    /** Puts a label in the section heading style. */
    public static void styleHeading(JLabel l)
    {
        l.setFont(LABEL_FONT);
        l.setForeground(BLUE);
    }
}
