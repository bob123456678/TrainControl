package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

/**
 * The accessory keyboard (FR-050): 16 range tabs over 128 keys, 16 across by 8 down.  A key shows its address over a
 * red half and a green half; the lit one is the position the accessory is in, the other is light gray and shows a pale
 * hint of its own color when pointed at.  A click on a half always asks for that color, the lit one included.
 *
 * Knows nothing of the railway: the window says what to show and hears what was clicked.
 *
 * @author Adam
 */
public class AccessoryKeyboard extends JPanel
{
    public static final int COLUMNS = 16;
    public static final int ROWS = 8;
    public static final int KEYS = COLUMNS * ROWS;
    public static final int PAGES = 16;

    /** The color of a half: clear red or green when lit, very light gray when not, a pale hint of its own color when pointed at. */
    public static Color lookOf(boolean red, boolean lit, boolean hinted)
    {
        if (lit) return red ? new Color(0xFF6666) : new Color(0x58C858);

        if (hinted) return red ? new Color(0xFBCACA) : new Color(0xC8EFC8);

        return new Color(0xF0, 0xF0, 0xF0);
    }

    private static final Font NUMBER_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final int KEY_HEIGHT = 43;
    private static final int NUMBER_HEIGHT = 16;
    private static final int TAB_HEIGHT = 26;
    private static final int GAP = 3;
    private static final int KEY_GAP = 4;
    private static final Color OUTLINE = new Color(0xBBBBBB);
    private static final Color KEY_BACKGROUND = new Color(0xFAFAFA);
    private static final Color DIVIDER = new Color(0xDDDDDD);

    /** What the window does with the keyboard's clicks. */
    public interface Listener
    {
        /** A range tab was chosen; pages count from 1. */
        void pageChosen(int page);

        /** A half was clicked: the accessory at this address should go red or green. */
        void clicked(int address, boolean red);

        /** The text for a pointer resting on this address, or null for none. */
        String tooltip(int address);
    }

    private final Listener listener;
    private final JToggleButton[] tabs = new JToggleButton[PAGES];
    private final JLabel[] numbers = new JLabel[KEYS];
    private final Half[] reds = new Half[KEYS];
    private final Half[] greens = new Half[KEYS];

    private int page = 1;

    public AccessoryKeyboard(Listener listener, int width)
    {
        super(new BorderLayout(0, 6));

        this.listener = listener;

        this.setBackground(Color.WHITE);
        this.setFocusable(false);

        JPanel tabRows = new JPanel(new Columns(2, PAGES / 2, KEY_GAP, GAP));
        tabRows.setOpaque(false);
        tabRows.setFocusable(false);

        ButtonGroup group = new ButtonGroup();

        for (int p = 1; p <= PAGES; p++)
        {
            final int chosen = p;
            JToggleButton tab = new JToggleButton((rangeStart(p)) + "-" + (rangeStart(p) + KEYS - 1));

            tab.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            tab.setMargin(new java.awt.Insets(1, 2, 1, 2));
            tab.setFocusable(false);
            tab.addActionListener(e -> this.listener.pageChosen(chosen));
            group.add(tab);
            tabRows.add(tab);
            this.tabs[p - 1] = tab;
        }

        this.tabs[0].setSelected(true);
        tabRows.setPreferredSize(new Dimension(width, 2 * TAB_HEIGHT + GAP));

        JPanel grid = new JPanel(new Columns(ROWS, COLUMNS, KEY_GAP, KEY_GAP));
        grid.setOpaque(false);
        grid.setFocusable(false);

        for (int i = 0; i < KEYS; i++)
        {
            grid.add(this.buildKey(i));
        }

        this.add(tabRows, BorderLayout.NORTH);
        this.add(grid, BorderLayout.CENTER);

        int height = 2 * TAB_HEIGHT + GAP + 6 + ROWS * KEY_HEIGHT + (ROWS - 1) * KEY_GAP;

        this.setPreferredSize(new Dimension(width, height));
        this.setMinimumSize(new Dimension(width, height));
        this.setMaximumSize(new Dimension(width, height));

        this.showPage(1);
    }

    /** The first address of a page. */
    public static int rangeStart(int page)
    {
        return (page - 1) * KEYS + 1;
    }

    private JPanel buildKey(int index)
    {
        JPanel key = new JPanel(new BorderLayout())
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                g.setColor(KEY_BACKGROUND);
                g.fillRoundRect(0, 0, this.getWidth() - 1, this.getHeight() - 1, 3, 3);
            }

            /** The halves are cut to the rounded corners, and the outline goes over them. */
            @Override
            protected void paintChildren(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();

                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, this.getWidth() - 1, this.getHeight() - 1, 3, 3));
                super.paintChildren(g2);
                g2.dispose();

                g.setColor(OUTLINE);
                g.drawRoundRect(0, 0, this.getWidth() - 1, this.getHeight() - 1, 3, 3);
            }
        };

        key.setOpaque(false);
        key.setFocusable(false);
        key.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        final int at = index;

        JLabel number = new JLabel("", SwingConstants.CENTER)
        {
            @Override
            public String getToolTipText(MouseEvent e)
            {
                return AccessoryKeyboard.this.listener.tooltip(AccessoryKeyboard.this.addressOf(at));
            }
        };

        number.setFont(NUMBER_FONT);
        number.setForeground(Color.BLACK);
        number.setPreferredSize(new Dimension(10, NUMBER_HEIGHT));
        number.setToolTipText(" ");
        this.numbers[index] = number;

        JPanel halves = new JPanel(new GridLayout(1, 2, 0, 0));
        halves.setOpaque(false);
        halves.setFocusable(false);

        Half red = new Half(index, true);
        Half green = new Half(index, false);

        this.reds[index] = red;
        this.greens[index] = green;

        halves.add(red);
        halves.add(green);

        key.add(number, BorderLayout.NORTH);
        key.add(halves, BorderLayout.CENTER);

        return key;
    }

    private int addressOf(int index)
    {
        return rangeStart(this.page) + index;
    }

    public int getPage()
    {
        return this.page;
    }

    /** Shows a page's numbers and chooses its tab; the colors follow from {@link #setKey}. */
    public void showPage(int newPage)
    {
        this.page = Math.max(1, Math.min(PAGES, newPage));
        this.tabs[this.page - 1].setSelected(true);

        for (int i = 0; i < KEYS; i++)
        {
            this.numbers[i].setText(Integer.toString(this.addressOf(i)));
        }
    }

    /**
     * Lights one half of a key.
     *
     * @param index the key on this page, 1 to {@link #KEYS}
     * @param red the lit half: red when true, green when false
     * @param usable false for an address the chosen protocol does not have: both halves stay gray and do nothing
     */
    public void setKey(int index, boolean red, boolean usable)
    {
        Half r = this.reds[index - 1];
        Half g = this.greens[index - 1];

        r.lit = usable && red;
        g.lit = usable && !red;
        r.setEnabled(usable);
        g.setEnabled(usable);
        this.numbers[index - 1].setForeground(usable ? Color.BLACK : Color.GRAY);
        r.repaint();
        g.repaint();
    }

    /** The key on this page for an address, or 0 when it is on another page. */
    public int keyOf(int address)
    {
        int index = address - rangeStart(this.page) + 1;

        return index >= 1 && index <= KEYS ? index : 0;
    }

    /** The red or green half of a key on this page. */
    public AbstractButton half(int index, boolean red)
    {
        return red ? this.reds[index - 1] : this.greens[index - 1];
    }

    /** Whether a half is the lit one. */
    public boolean isLit(int index, boolean red)
    {
        return ((Half) this.half(index, red)).lit;
    }

    /** The text on a key. */
    public String numberOf(int index)
    {
        return this.numbers[index - 1].getText();
    }

    public AbstractButton tab(int tabPage)
    {
        return this.tabs[tabPage - 1];
    }

    /**
     * Rows laid out in 16ths of the full width, so every row shares its outer edges and a range button has the edges of
     * the two key columns under it (FR-050); GridLayout would center each row's leftover pixels instead.
     */
    private static final class Columns implements LayoutManager
    {
        private final int rows;
        private final int perRow;
        private final int hgap;
        private final int vgap;

        Columns(int rows, int perRow, int hgap, int vgap)
        {
            this.rows = rows;
            this.perRow = perRow;
            this.hgap = hgap;
            this.vgap = vgap;
        }

        /** The edge before cell {@code i} of {@code n} across {@code size} pixels. */
        private static int edge(int i, int n, int size, int gap)
        {
            return Math.round(i * (size + gap) / (float) n);
        }

        @Override
        public void layoutContainer(Container parent)
        {
            // Both rows measure columns in 16ths of the width, so a range button covers whole key columns
            int width = parent.getWidth();
            int height = parent.getHeight();
            int span = COLUMNS / this.perRow;

            for (int i = 0; i < parent.getComponentCount(); i++)
            {
                int row = i / this.perRow;
                int first = (i % this.perRow) * span;
                int left = edge(first, COLUMNS, width, this.hgap);
                int right = edge(first + span, COLUMNS, width, this.hgap) - this.hgap;
                int top = edge(row, this.rows, height, this.vgap);
                int bottom = edge(row + 1, this.rows, height, this.vgap) - this.vgap;

                parent.getComponent(i).setBounds(left, top, right - left, bottom - top);
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container parent)
        {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent)
        {
            return new Dimension(0, 0);
        }

        @Override
        public void addLayoutComponent(String name, Component comp)
        {
        }

        @Override
        public void removeLayoutComponent(Component comp)
        {
        }
    }

    /** One half of a key. */
    private final class Half extends JButton
    {
        private final int index;
        private final boolean red;
        private boolean lit;

        Half(int index, boolean red)
        {
            this.index = index;
            this.red = red;

            this.setFocusable(false);
            this.setContentAreaFilled(false);
            this.setBorderPainted(false);
            this.setRolloverEnabled(true);
            this.setOpaque(false);
            this.setToolTipText(" ");
            this.addActionListener(e -> AccessoryKeyboard.this.listener.clicked(AccessoryKeyboard.this.addressOf(this.index), this.red));
        }

        @Override
        public String getToolTipText(MouseEvent e)
        {
            return AccessoryKeyboard.this.listener.tooltip(AccessoryKeyboard.this.addressOf(this.index));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Color fill = lookOf(this.red, this.lit, this.isEnabled() && this.getModel().isRollover());

            g.setColor(fill);
            g.fillRect(0, 0, this.getWidth(), this.getHeight());

            if (!this.red)
            {
                g.setColor(DIVIDER);
                g.fillRect(0, 0, 1, this.getHeight());
            }
        }
    }
}
