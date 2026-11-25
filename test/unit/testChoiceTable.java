package unit;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import org.traincontrol.gui.widgets.ChoiceTable;
import org.traincontrol.gui.widgets.HandPointer;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import static support.Components.onTheEventThread;

/**
 * `ChoiceTable` on its own (FR-249): its rows, choices, linked headings and stripes, built and laid out without a
 * window.  The Sync table's window tests (`ui.testTheSyncDifferencesWindow`) are its integration tests; graying a choice
 * with its reason is `SyncDifferencesWindow.choice`, not this class.
 */
public class testChoiceTable
{
    /**
     * A row's cell sits under its heading, even when a cell in an earlier column is wider than that column's heading.
     *
     * MUTATION: in the grid's `doLayout`, leave out `headings.columnWidths = wide`, and the second heading stays left
     * of its cells.
     */
    @Test
    public void testACellSitsUnderItsHeading()
    {
        int[] x = onTheEventThread(() ->
        {
            JLabel name = new JLabel("Name"), address = new JLabel("Address");
            JLabel cell = new JLabel("72");
            ChoiceTable table = new ChoiceTable(name, address);
            table.addRow(new JLabel("A locomotive name far wider than its heading"), cell);
            layOut(table);
            return new int[] {address.getX(), cell.getX(), name.getX()};
        });

        assertEquals(x[1], x[0], "the second column's cell starts at " + x[1] + " px and its heading at " + x[0]);
        assertTrue(x[0] > x[2], "control: the second heading is not right of the first");
    }

    /**
     * A column of bare choices is centered under its heading, and the heading over it too.
     *
     * MUTATION: in `addRow`, anchor every cell `NORTHWEST` (`centered` always false), and the choice sits at the
     * column's left.
     */
    @Test
    public void testABareChoiceIsCenteredUnderItsHeading()
    {
        int[] middles = onTheEventThread(() ->
        {
            JLabel keep = new JLabel("Keep As Is, the long heading");
            ChoiceTable table = new ChoiceTable(new JLabel("Name"), keep);
            JRadioButton choice = table.choice(new ButtonGroup());
            table.addRow(new JLabel("BR 86"), choice);
            layOut(table);
            return new int[] {keep.getX() + keep.getWidth() / 2, choice.getX() + choice.getWidth() / 2,
                keep.getWidth(), choice.getWidth()};
        });

        assertTrue(middles[2] > middles[3] + 10, "precondition: the heading is not wider than the choice");
        assertEquals(middles[1], middles[0], 1, "the choice's middle is at " + middles[1] + " px, its heading's at "
            + middles[0]);
    }

    /**
     * A row's choices answer once, through its group, and each stands one line high, as a line of text beside it does.
     *
     * MUTATION: in `choice`, leave out `group.add(b)`, and both choices stay selected.
     */
    @Test
    public void testARowsChoicesAnswerOnceAndStandOneLineHigh()
    {
        Object[] seen = onTheEventThread(() ->
        {
            ChoiceTable table = new ChoiceTable(new JLabel("Keep"), new JLabel("Apply"));
            ButtonGroup group = new ButtonGroup();
            JRadioButton keep = table.choice(group), apply = table.choice(group);
            keep.setSelected(true);
            apply.setSelected(true);
            return new Object[] {keep.isSelected(), apply.isSelected(), keep.isOpaque(),
                keep.getPreferredSize().height, table.line(new JLabel("Name")).getPreferredSize().height};
        });

        assertEquals(seen[0], false, "choosing Apply left Keep chosen in the same row");
        assertEquals(seen[1], true, "Apply is not chosen");
        assertEquals(seen[2], false, "a choice paints its own background over the row's stripe");
        assertEquals(seen[3], seen[4], "a choice is " + seen[3] + " px high and a line of text " + seen[4]);
    }

    /**
     * A linked heading's click acts on every row: here it chooses each row's first choice.
     *
     * MUTATION: in `link`, leave out `b.addActionListener(e -> action.run())`, and the click does nothing.
     */
    @Test
    public void testAHeadingClickActsOnEveryRow()
    {
        boolean[] chosen = onTheEventThread(() ->
        {
            List<JRadioButton> firsts = new ArrayList<>();
            JButton all = ChoiceTable.link("Keep all", () -> firsts.forEach(b -> b.setSelected(true)));
            ChoiceTable table = new ChoiceTable(new JLabel("Name"), all, new JLabel("Apply"));
            for (int i = 0; i < 3; i++)
            {
                ButtonGroup group = new ButtonGroup();
                firsts.add(table.choice(group));
                JRadioButton apply = table.choice(group);
                apply.setSelected(true);
                table.addRow(new JLabel("Row " + i), firsts.get(i), apply);
            }
            all.doClick(0);
            boolean[] out = new boolean[firsts.size()];
            for (int i = 0; i < out.length; i++) out[i] = firsts.get(i).isSelected();
            return out;
        });

        assertEquals(chosen, new boolean[] {true, true, true}, "the heading's click did not choose every row's Keep");
    }

    /**
     * A linked heading reads as a link, underlined under the hand pointer, and Enter presses it as Space does rather
     * than the window's default button.
     *
     * MUTATION: in `link`, leave out the Enter key's binding, and Enter is not the heading's.
     */
    @Test
    public void testALinkIsUnderlinedAndTakesEnter()
    {
        AtomicInteger pressed = new AtomicInteger();
        Object[] seen = onTheEventThread(() ->
        {
            JButton link = ChoiceTable.link("Keep all", pressed::incrementAndGet);
            Object key = link.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            Action enter = key == null ? null : link.getActionMap().get(key);
            if (enter != null) enter.actionPerformed(new ActionEvent(link, ActionEvent.ACTION_PERFORMED, "enter"));
            return new Object[] {enter != null, link.getFont().getAttributes().get(TextAttribute.UNDERLINE),
                HandPointer.isHand(link.getCursor()), link.isContentAreaFilled()};
        });

        assertEquals(seen[0], true, "Enter has no binding on the heading, so it presses the window's default button");
        assertEquals(pressed.get(), 1, "Enter's binding did not press the heading");
        assertEquals(seen[1], TextAttribute.UNDERLINE_ON, "the heading is not underlined");
        assertEquals(seen[2], true, "the heading does not show the hand pointer");
        assertEquals(seen[3], false, "the heading is drawn as a button, not a link");
    }

    /**
     * A line of a cell is one choice high whatever its parts, and a null part takes no room.
     *
     * MUTATION: in `line`, leave out the rigid area, and a line of text is shorter than the choice beside it.
     */
    @Test
    public void testALineIsOneChoiceHighAndANullPartTakesNoRoom()
    {
        int[] sizes = onTheEventThread(() ->
        {
            ChoiceTable table = new ChoiceTable(new JLabel("Name"));
            JComponent both = table.line(new JLabel("Name"), null, new JLabel("BR 86"));
            JComponent two = table.line(new JLabel("Name"), new JLabel("BR 86"));
            return new int[] {both.getPreferredSize().height, table.choice(new ButtonGroup()).getPreferredSize().height,
                both.getPreferredSize().width, two.getPreferredSize().width};
        });

        assertEquals(sizes[0], sizes[1], "a line is " + sizes[0] + " px high and a choice " + sizes[1]);
        assertEquals(sizes[2], sizes[3], "a null part took room in the line");
    }

    /**
     * Every other row is a step off the background: the first and third white, the second a light gray.
     *
     * MUTATION: in `paintRows`, shade the even rows (`i % 2 == 0`), and the first row is gray.
     */
    @Test
    public void testEveryOtherRowIsAStepOffTheBackground()
    {
        int[] rows = onTheEventThread(() ->
        {
            Striped s = new Striped(3);
            int[] out = new int[3];
            for (int i = 0; i < 3; i++) out[i] = s.blue(s.middle(i));
            return out;
        });

        assertEquals(rows[0], 255, "the first row is not the background");
        assertTrue(rows[1] < 255 && rows[1] >= 235, "the second row is not a light step off the background: " + rows[1]);
        assertEquals(rows[2], 255, "the third row is not the background");
    }

    /**
     * A hairline darker than either row runs between two rows, and none above the first.
     *
     * MUTATION: in `paintRows`, leave out the `drawLine`, and nothing parts the rows.
     */
    @Test
    public void testAHairlineRunsBetweenRows()
    {
        int[] darkest = onTheEventThread(() ->
        {
            Striped s = new Striped(2);
            return new int[] {s.darkest(0, s.middle(0)), s.darkest(s.middle(0), s.middle(1)), s.blue(s.middle(1))};
        });

        assertEquals(darkest[0], 255, "a line runs above the first row");
        assertTrue(darkest[1] < darkest[2], "between the rows nothing is darker than the second row's " + darkest[2]
            + ": " + darkest[1]);
    }

    /**
     * Rows past half the screen's height scroll: the table asks for half the screen, and room for the bar beside it.
     *
     * MUTATION: in `getPreferredSize`, leave out the cap on the height, and the table asks for every row; leave out
     * the `getVerticalScrollBar` width, and the table asks for no room for the bar.
     */
    @Test
    public void testATallTableAsksForHalfTheScreen()
    {
        Dimension[] sizes = onTheEventThread(() ->
        {
            ChoiceTable few = new ChoiceTable(new JLabel("Name")), many = new ChoiceTable(new JLabel("Name"));
            few.addRow(new JLabel("Row"));
            for (int i = 0; i < 200; i++) many.addRow(new JLabel("Row " + i));
            return new Dimension[] {few.getPreferredSize(), many.getPreferredSize(),
                many.getViewport().getView().getPreferredSize(), many.getVerticalScrollBar().getPreferredSize()};
        });
        int half = Toolkit.getDefaultToolkit().getScreenSize().height / 2;

        assertTrue(sizes[2].height > half, "precondition: 200 rows fit in half the screen");
        assertEquals(sizes[1].height, half, "200 rows ask for " + sizes[1].height + " px, not half the screen");
        assertTrue(sizes[1].width - sizes[2].width >= sizes[3].width, "the table asks for " + (sizes[1].width
            - sizes[2].width) + " px beside its rows, less than the " + sizes[3].width + " px scroll bar");
        assertTrue(sizes[0].height < half, "control: one row asks for half the screen");
    }

    /**
     * A table wider than three quarters of the screen asks for three quarters, and a bar below it.
     *
     * MUTATION: in `getPreferredSize`, cap the width at `screen.width * 3 / 2`, and the table asks for more.
     */
    @Test
    public void testAWideTableAsksForThreeQuartersOfTheScreen()
    {
        int screen = Toolkit.getDefaultToolkit().getScreenSize().width;
        Dimension[] sizes = onTheEventThread(() ->
        {
            ChoiceTable table = new ChoiceTable(new JLabel("Name"));
            JLabel wide = new JLabel("Row");
            wide.setPreferredSize(new Dimension(screen * 2, 20));
            table.addRow(wide);
            return new Dimension[] {table.getPreferredSize(), table.getViewport().getView().getPreferredSize()};
        });

        assertTrue(sizes[1].width > screen * 3 / 4, "precondition: the row is wider than three quarters of the screen");
        assertEquals(sizes[0].width, screen * 3 / 4, "the table asks for " + sizes[0].width + " px, not three quarters"
            + " of the " + screen + " px screen");
    }

    /**
     * With a width limit given, it alone decides: rows wider than three quarters of the screen but inside the limit
     * ask for their full width and no bar below; a limit narrower than the rows narrows to it, with the bar.
     *
     * MUTATION: in `getPreferredSize`, use `Math.min(screen.width * 3 / 4, widest)`, and the roomy limit is cut to three quarters.
     */
    @Test
    public void testALimitAloneDecidesTheWidth()
    {
        int screen = Toolkit.getDefaultToolkit().getScreenSize().width, rows = screen * 7 / 8;
        Dimension[] sizes = onTheEventThread(() ->
        {
            ChoiceTable table = new ChoiceTable(new JLabel("Name"));
            JLabel wide = new JLabel("Row");
            wide.setPreferredSize(new Dimension(rows, 20));
            table.addRow(wide);
            table.limitWidth(screen * 2);
            Dimension roomy = table.getPreferredSize();
            table.limitWidth(rows / 2);
            return new Dimension[] {roomy, table.getPreferredSize(), table.getViewport().getView().getPreferredSize(),
                table.getHorizontalScrollBar().getPreferredSize()};
        });

        assertTrue(sizes[2].width > screen * 3 / 4, "precondition: the rows are wider than three quarters of the screen");
        assertTrue(sizes[0].width >= sizes[2].width, "inside the limit the table asks for " + sizes[0].width
            + " px, less than its rows' " + sizes[2].width);
        assertEquals(sizes[1].width, rows / 2, "past the limit the table asks for " + sizes[1].width + " px");
        assertEquals(sizes[1].height - sizes[0].height, sizes[3].height, "the bar's room is off: " + sizes[0].height
            + " px high inside the limit, " + sizes[1].height + " past it");
    }

    /** A table with this many rows, laid out and painted, read down its right edge, clear of every cell. */
    private static final class Striped
    {
        private final List<Component> firsts = new ArrayList<>();
        private final BufferedImage image;

        Striped(int count)
        {
            ChoiceTable table = new ChoiceTable(new JLabel("Name"), new JLabel("Address"));
            for (int i = 0; i < count; i++)
            {
                JLabel first = new JLabel("Row " + i);
                firsts.add(first);
                table.addRow(first, new JLabel(String.valueOf(i)));
            }
            layOut(table);
            Container grid = (Container) ((Container) table.getViewport().getView()).getComponent(0);
            image = new BufferedImage(grid.getWidth(), grid.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            grid.printAll(g);
            g.dispose();
        }

        int middle(int row)
        {
            Component c = firsts.get(row);
            return c.getY() + c.getHeight() / 2;
        }

        int blue(int y)
        {
            return image.getRGB(image.getWidth() - 3, y) & 0xff;
        }

        /** The darkest blue down the edge from `from` up to `to`. */
        int darkest(int from, int to)
        {
            int least = 255;
            for (int y = from; y < to; y++) least = Math.min(least, blue(y));
            return least;
        }
    }

    /** Sizes the table as it asks and lays it out, twice, so the headings take the widths the rows gave them. */
    private static void layOut(ChoiceTable table)
    {
        table.setSize(table.getPreferredSize());
        for (int pass = 0; pass < 2; pass++) layOutTree(table);
    }

    private static void layOutTree(Component c)
    {
        if (!(c instanceof Container)) return;
        ((Container) c).doLayout();
        for (Component child : ((Container) c).getComponents()) layOutTree(child);
    }
}
