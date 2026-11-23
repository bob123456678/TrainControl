package org.traincontrol.gui.widgets;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.util.Collections;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

/**
 * A list of rows under column headings, each row some cells and then its choices, that scrolls either way once it does
 * not fit: the manual Sync's table and the CSV import's duplicates are built on it (FR-249).  A column of bare choices
 * is centered under its heading, any other left-aligned; cells are built from lines one choice high, so a row's lines
 * align across it.
 */
public final class ChoiceTable extends JScrollPane
{
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final JComponent[] heads;
    private int rows;
    /** A choice's height with text, and the look it was measured in: on the event thread. */
    private static int high;
    private static LookAndFeel measuredIn;

    /** @param heads each column's heading, left to right: a label, or a {@link #link} that acts on every row */
    public ChoiceTable(JComponent... heads)
    {
        this.heads = heads.clone();
        this.grid.setBackground(Color.WHITE);
        addRow(heads);
        // A filler column takes any spare width, and the rows keep to the top
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = heads.length;
        c.weightx = 1;
        this.grid.add(Box.createHorizontalGlue(), c);
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        top.add(this.grid, BorderLayout.NORTH);
        setViewportView(top);
        setBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)));
        getVerticalScrollBar().setUnitIncrement(16);
    }

    /** Adds a row: one cell under each heading, top-aligned; a null cell leaves its place empty. */
    public void addRow(JComponent... cells)
    {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = this.rows++;
        c.insets = new Insets(c.gridy == 0 ? 6 : 4, 8, 4, 8);
        for (int i = 0; i < cells.length; i++)
        {
            if (cells[i] == null) continue;
            c.gridx = i;
            boolean centered = cells[i] instanceof JRadioButton;
            c.anchor = c.gridy == 0 ? GridBagConstraints.BASELINE_LEADING : centered ? GridBagConstraints.NORTH
                : GridBagConstraints.NORTHWEST;
            this.grid.add(cells[i], c);
            if (c.gridy == 0 || !centered) continue;
            // A heading sits as its column does
            GridBagLayout layout = (GridBagLayout) this.grid.getLayout();
            GridBagConstraints h = layout.getConstraints(this.heads[i]);
            h.anchor = GridBagConstraints.BASELINE;
            layout.setConstraints(this.heads[i], h);
        }
    }

    /** A choice in a row's group, `text` beside it (empty under a heading that says it all); one line high. */
    public static JRadioButton choice(ButtonGroup group, String text)
    {
        JRadioButton b = new JRadioButton(text);
        b.setOpaque(false);
        b.setFont(UiStyle.TEXT_FONT);
        b.setPreferredSize(new Dimension(b.getPreferredSize().width, high()));
        group.add(b);
        return b;
    }

    /** As high as a choice with text, at the scale now; measured once for each look. */
    private static int high()
    {
        if (measuredIn != UIManager.getLookAndFeel())
        {
            JRadioButton b = new JRadioButton("Xg");
            b.setFont(UiStyle.TEXT_FONT);
            high = b.getPreferredSize().height;
            measuredIn = UIManager.getLookAndFeel();
        }
        return high;
    }

    /**
     * A heading that acts on every row when clicked, drawn as a link: underlined, with the hand pointer.  It takes the
     * keyboard focus, and Enter presses it as Space does rather than the window's default button.
     */
    public static JButton link(String text, String tip, Runnable action)
    {
        JButton b = new JButton(text);
        b.setFont(UiStyle.LABEL_FONT.deriveFont(Collections.singletonMap(TextAttribute.UNDERLINE,
            TextAttribute.UNDERLINE_ON)));
        b.setForeground(UiStyle.TEXT);
        b.setContentAreaFilled(false);
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setCursor(HandPointer.cursor());
        b.setToolTipText(tip);
        b.addActionListener(e -> action.run());
        b.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "press");
        b.getActionMap().put("press", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                b.doClick();
            }
        });
        return b;
    }

    /** One line of a cell, one choice high, its parts side by side and centered on it; a hidden part takes no room. */
    public static JComponent line(JComponent... parts)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(0);
        p.add(Box.createRigidArea(new Dimension(0, high())));
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0) p.add(Box.createHorizontalStrut(4));
            p.add(parts[i]);
        }
        return p;
    }

    /** A cell of lines, one under another, left-aligned. */
    public static JPanel lines(JComponent... lines)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        for (JComponent l : lines) p.add(l);
        return p;
    }

    /** Every row up to half the screen's height and three quarters of its width; the rest scrolls. */
    @Override
    public Dimension getPreferredSize()
    {
        Dimension d = super.getPreferredSize(), screen = Toolkit.getDefaultToolkit().getScreenSize();
        int most = screen.height / 2;
        if (d.height > most) d = new Dimension(d.width + getVerticalScrollBar().getPreferredSize().width, most);
        return new Dimension(Math.min(d.width, screen.width * 3 / 4), d.height);
    }
}
