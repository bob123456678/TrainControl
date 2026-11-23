package org.traincontrol.gui.widgets;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

/**
 * A list of rows under column headings, each row some cells and then its choices, that scrolls either way once it does
 * not fit: the manual Sync's table and the CSV import's duplicates are built on it (FR-249).  A column of bare choices
 * is centered under its heading, any other left-aligned; cells are built from lines one choice high, so a row's lines
 * align across it.  Every other row is a step off the background, with a hairline between rows; the headings stay above
 * the rows as they scroll.
 */
public final class ChoiceTable extends JScrollPane
{
    /** Each row's first cell, whose top is the row's top less {@link #PAD}. */
    private final List<JComponent> firsts = new ArrayList<>();
    private static final int PAD = 4;
    /** The headings, over a stronger rule, in columns as wide as the rows'. */
    private final JPanel head = new JPanel(new GridBagLayout())
    {
        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            g.setColor(step(77));
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }
    };
    private final JPanel grid = new JPanel(new GridBagLayout())
    {
        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            paintRows(g);
        }

        @Override
        public void doLayout()
        {
            super.doLayout();
            GridBagLayout headings = (GridBagLayout) ChoiceTable.this.head.getLayout();
            int[] wide = Arrays.copyOf(((GridBagLayout) getLayout()).getLayoutDimensions()[0],
                ChoiceTable.this.heads.length);
            if (Arrays.equals(wide, headings.columnWidths)) return;
            headings.columnWidths = wide;
            ChoiceTable.this.head.revalidate();
        }
    };
    private final JComponent[] heads;
    private int rows = 1;
    /** A choice's height with text, at the scale this table was made in. */
    private final int high;

    /** @param heads each column's heading, left to right: a label, or a {@link #link} that acts on every row */
    public ChoiceTable(JComponent... heads)
    {
        this.heads = heads.clone();
        JRadioButton measure = new JRadioButton("Xg");
        measure.setFont(UiStyle.TEXT_FONT);
        this.high = measure.getPreferredSize().height;
        // Each heading over room as wide as it in row 0, so its column is never narrower than it
        GridBagConstraints c = new GridBagConstraints(), room = new GridBagConstraints();
        c.insets = new Insets(6, 8, PAD, 8);
        c.anchor = GridBagConstraints.BASELINE_LEADING;
        room.insets = new Insets(0, 8, 0, 8);
        for (int i = 0; i < heads.length; i++)
        {
            c.gridx = room.gridx = i;
            this.head.add(heads[i], c);
            Dimension d = new Dimension(heads[i].getPreferredSize().width, 0);
            this.grid.add(new Box.Filler(d, d, d), room);
        }
        // A filler column takes any spare width, and the rows keep to the top
        GridBagConstraints spare = new GridBagConstraints();
        spare.gridx = heads.length;
        spare.weightx = 1;
        this.grid.add(Box.createHorizontalGlue(), spare);
        this.head.add(Box.createHorizontalGlue(), spare);
        this.grid.setBackground(Color.WHITE);
        this.head.setBackground(Color.WHITE);
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        top.add(this.grid, BorderLayout.NORTH);
        setViewportView(top);
        setColumnHeaderView(this.head);
        setBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)));
        getVerticalScrollBar().setUnitIncrement(16);
    }

    /** Adds a row: one cell under each heading, top-aligned; a null cell leaves its place empty. */
    public void addRow(JComponent... cells)
    {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = this.rows++;
        c.insets = new Insets(PAD, 8, PAD, 8);
        for (int i = 0; i < cells.length; i++)
        {
            if (cells[i] == null) continue;
            if (this.firsts.size() < c.gridy) this.firsts.add(cells[i]);
            c.gridx = i;
            boolean centered = cells[i] instanceof JRadioButton;
            c.anchor = centered ? GridBagConstraints.NORTH : GridBagConstraints.NORTHWEST;
            this.grid.add(cells[i], c);
            if (!centered) continue;
            // A heading sits as its column does
            GridBagLayout layout = (GridBagLayout) this.head.getLayout();
            GridBagConstraints h = layout.getConstraints(this.heads[i]);
            h.anchor = GridBagConstraints.BASELINE;
            layout.setConstraints(this.heads[i], h);
        }
    }

    /** Every other row a step off the background, and a hairline over each row below the first. */
    private void paintRows(Graphics g)
    {
        int wide = this.grid.getWidth();
        for (int i = 0; i < this.firsts.size(); i++)
        {
            int top = this.firsts.get(i).getY() - PAD;
            int below = i + 1 < this.firsts.size() ? this.firsts.get(i + 1).getY() - PAD : this.grid.getHeight();
            g.setColor(step(10));
            if (i % 2 == 1) g.fillRect(0, top, wide, below - top);
            g.setColor(step(31));
            if (i > 0) g.drawLine(0, top, wide, top);
        }
    }

    /** A step off the background: translucent black over a light one, white over a dark one. */
    private Color step(int alpha)
    {
        Color bg = this.grid.getBackground();
        int to = bg.getRed() + bg.getGreen() + bg.getBlue() > 3 * 127 ? 0 : 255;
        return new Color(to, to, to, alpha);
    }

    /** A bare choice in a row's group, one line high. */
    public JRadioButton choice(ButtonGroup group)
    {
        JRadioButton b = new JRadioButton();
        b.setOpaque(false);
        b.setFont(UiStyle.TEXT_FONT);
        b.setPreferredSize(new Dimension(b.getPreferredSize().width, this.high));
        group.add(b);
        return b;
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

    /** A cell's line, one choice high, its parts side by side, centered on it; null or hidden parts take no room. */
    public JComponent line(JComponent... parts)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(0);
        p.add(Box.createRigidArea(new Dimension(0, this.high)));
        for (JComponent part : parts)
        {
            if (part == null) continue;
            if (p.getComponentCount() > 1) p.add(Box.createHorizontalStrut(4));
            p.add(part);
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

    /** Every row up to half the screen's height and three quarters of its width; the rest scrolls, beside its bars. */
    @Override
    public Dimension getPreferredSize()
    {
        Dimension d = super.getPreferredSize(), screen = Toolkit.getDefaultToolkit().getScreenSize();
        int most = screen.height / 2;
        if (d.height > most) d = new Dimension(d.width + getVerticalScrollBar().getPreferredSize().width, most);
        if (d.width > screen.width * 3 / 4)
            d = new Dimension(screen.width * 3 / 4, d.height + getHorizontalScrollBar().getPreferredSize().height);
        return d;
    }
}
