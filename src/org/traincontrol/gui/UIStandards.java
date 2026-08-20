package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * How this application's interface is supposed to look.
 *
 * Adam set these, and they are here rather than only in a document because a document does not stop
 * the next panel being built in whatever the platform default happens to be. Everything hand-built
 * should go through this class; the older screens are NetBeans form files and carry their styling in
 * the generated blocks, which are not editable by hand.
 *
 * The standard, in his words:
 *
 * <pre>
 * Section headings:        Segoe UI Semibold 13, colour 0,0,155
 *                          (same indentation as the panels themselves)
 * Important text/labels:   Segoe UI Semibold 13, black
 * Buttons:                 Segoe UI Bold 12, black
 * Regular text:            Segoe UI Plain 14, black
 * Panels:                  white background, 1px LineBorder in 204,204,204
 * </pre>
 *
 * A note on the font names. "Segoe UI Semibold" is its own family on Windows rather than a weight of
 * "Segoe UI", so it has to be asked for by that name; bold is the ordinary family with the bold style.
 * On a machine without Segoe UI - which this application is not shipped for, but is developed on
 * sometimes - Java silently substitutes a default, so the sizes and the weights still hold and only
 * the shapes differ.
 */
public final class UIStandards
{
    /** Section headings, and the colour they are drawn in. */
    public static final Font HEADING_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 13);

    public static final Color HEADING_COLOR = new Color(0, 0, 155);

    /** A label that names something the reader has to act on. */
    public static final Font LABEL_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 13);

    /** Buttons. */
    public static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 12);

    /** Everything else - table contents, explanations, the ordinary run of text. */
    public static final Font TEXT_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    /** The line round a panel. */
    public static final Color PANEL_BORDER_COLOR = new Color(204, 204, 204);

    private UIStandards()
    {
    }

    /**
     * A section heading.
     *
     * @param text what the section is called
     * @return the label, styled
     */
    public static JLabel heading(String text)
    {
        JLabel label = new JLabel(text);

        label.setFont(HEADING_FONT);
        label.setForeground(HEADING_COLOR);

        return label;
    }

    /**
     * A label for something the reader has to act on, as against ordinary prose.
     *
     * @param text the label
     * @return the label, styled
     */
    public static JLabel label(String text)
    {
        JLabel label = new JLabel(text);

        label.setFont(LABEL_FONT);
        label.setForeground(Color.BLACK);

        return label;
    }

    /**
     * A run of ordinary text.
     *
     * @param text the text
     * @return the label, styled
     */
    public static JLabel text(String text)
    {
        JLabel label = new JLabel(text);

        label.setFont(TEXT_FONT);
        label.setForeground(Color.BLACK);

        return label;
    }

    /**
     * Styles a button in place, for the ones that are built elsewhere.
     *
     * @param button the button
     * @return the same button, so this can be written inline
     */
    public static JButton style(JButton button)
    {
        button.setFont(BUTTON_FONT);
        button.setForeground(Color.BLACK);

        return button;
    }

    /**
     * Gives a panel the standard white ground and line.
     *
     * @param panel the panel
     * @return the same panel, so this can be written inline
     */
    public static JPanel style(JPanel panel)
    {
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(PANEL_BORDER_COLOR, 1));

        return panel;
    }

    /**
     * The standard panel line on its own, for a panel that needs padding inside it too.
     *
     * @param pad how much room to leave between the line and the contents
     * @return the border
     */
    public static javax.swing.border.Border panelBorder(int pad)
    {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(pad, pad, pad, pad));
    }
}
