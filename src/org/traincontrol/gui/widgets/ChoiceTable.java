package org.traincontrol.gui.widgets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

/**
 * A list of rows under column headings, each row some cells and then its choices, that scrolls once it would pass half
 * the screen: the manual Sync's table and the CSV import's duplicates are built on it (FR-249).
 */
public final class ChoiceTable extends JScrollPane
{
    private final JPanel grid = new JPanel(new GridBagLayout());
    private int rows;

    /** @param heads each column's heading, left to right */
    public ChoiceTable(String... heads)
    {
        this.grid.setBackground(Color.WHITE);
        JComponent[] labels = new JComponent[heads.length];
        for (int i = 0; i < heads.length; i++) labels[i] = UiStyle.blackLabel(heads[i]);
        addRow(labels);

        setViewportView(this.grid);
        setBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)));
        getVerticalScrollBar().setUnitIncrement(16);
    }

    /** Adds a row: one cell under each heading. */
    public void addRow(JComponent... cells)
    {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = this.rows++;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 8, 4, 8);
        for (int i = 0; i < cells.length; i++)
        {
            c.gridx = i;
            c.weightx = i == cells.length - 1 ? 1 : 0;
            if (cells[i] != null) this.grid.add(cells[i], c);
        }
    }

    /** A choice in a row's group, `text` beside it (empty under a heading that says it all). */
    public static JRadioButton choice(ButtonGroup group, String text)
    {
        JRadioButton b = new JRadioButton(text);
        b.setOpaque(false);
        b.setFont(UiStyle.TEXT_FONT);
        group.add(b);
        return b;
    }

    /** Every row up to half the screen's height; the rest scrolls. */
    @Override
    public Dimension getPreferredSize()
    {
        Dimension d = super.getPreferredSize();
        int most = Toolkit.getDefaultToolkit().getScreenSize().height / 2;
        return d.height <= most ? d : new Dimension(d.width + getVerticalScrollBar().getPreferredSize().width, most);
    }
}
