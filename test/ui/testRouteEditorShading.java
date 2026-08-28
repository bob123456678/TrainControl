package ui;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import javax.swing.JTable;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.NodeRouteCommand;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinRoute;

/**
 * OB-121: the + row at the bottom of the conditions list is white.
 *
 * Adam, 2026-08-27: "in the route editor, remove the shading in the condition row with the +... make
 * all its cells white, not gray."
 *
 * **Reading the code said this could not happen**, which is why it is worth a test rather than a
 * glance. `greyWhatCannotBeEdited` has always exempted the last row, and the last row IS the + row,
 * with a comment explaining that shading it "reads as a row that has been switched off rather than as
 * the way to add another".
 *
 * What the reading missed is that a table cell renderer hands back ONE recycled component for every
 * cell in the table. The cell drawn immediately before the + row is the previous row's un-editable
 * column, which paints that shared label grey. The exemption then handed the same label straight back
 * for each cell of the + row, still grey. It was not failing to run - it ran, and returned somebody
 * else's paint.
 *
 * So the order below is the test. Ask for a greyed cell FIRST and the + row's cells after it, which is
 * the order the table paints them in; asking the other way round passes with the defect present.
 *
 * @author Adam
 */
public class testRouteEditorShading
{
    /**
     * Every cell of the + row is the table's own colour, even straight after a greyed one.
     *
     * MUTATION: either exemption going back to a bare `return out` fails this - and it fails only
     * because the greyed cell is rendered first, which is the whole point of the ordering.
     */
    @Test
    public void testThePlusRowIsNotLeftHoldingTheLastCellsGrey() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = openWithOneCondition();

        try
        {
            JTable conditions = conditionsTableOf(frame);

            assertNotNull(conditions, "cannot find the conditions table in the route editor");

            // One condition and the + row beneath it.
            assertEquals(conditions.getRowCount(), 2,
                "expected one condition and the + row, got " + conditions.getRowCount()
                + " rows - the fixture is not what this test is about");

            int plus = conditions.getRowCount() - 1;

            java.awt.Color plain = conditions.getBackground();

            // PRIME the recycled component, exactly as painting the row above does.
            boolean primed = false;

            for (int column = 0; column < conditions.getColumnCount(); column++)
            {
                if (conditions.getModel().isCellEditable(0, column)) continue;

                Component grey = render(conditions, 0, column);

                if (grey.getBackground() != null && !grey.getBackground().equals(plain))
                {
                    primed = true;
                }
            }

            assertTrue(primed,
                "no cell on the condition row came back shaded, so nothing has painted the shared "
                + "renderer grey and the + row below could not inherit it either - this test is no "
                + "longer reproducing the thing it is about");

            // And now the + row, in the order the table draws it.
            for (int column = 0; column < conditions.getColumnCount(); column++)
            {
                Component cell = render(conditions, plus, column);

                assertEquals(cell.getBackground(), plain,
                    "column " + column + " of the + row came back " + cell.getBackground()
                    + " rather than the table's own " + plain + ". The renderer hands back one "
                    + "recycled component for every cell, so declining to ADD the grey is not the "
                    + "same as not having it - the exemption has to take the previous cell's wash "
                    + "off before returning");
            }
        }
        finally
        {
            close(frame);
        }
    }

    /**
     * One cell, asked of the table exactly as painting asks for it.
     */
    private Component render(JTable table, int row, int column)
    {
        return table.getCellRenderer(row, column).getTableCellRendererComponent(
            table, table.getValueAt(row, column), false, false, row, column);
    }

    /**
     * The conditions table, told apart from the commands table by its class.
     *
     * Both are JTables in the same window and the two have different renderers, so picking the wrong
     * one would test the half that was never broken.
     */
    private JTable conditionsTableOf(java.awt.Container from)
    {
        for (Component child : from.getComponents())
        {
            if (child instanceof JTable
                && child.getClass().getSimpleName().contains("Condition"))
            {
                return (JTable) child;
            }

            if (child instanceof java.awt.Container)
            {
                JTable found = conditionsTableOf((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * The editor, open on a route carrying a single S88 condition.
     *
     * The three-argument constructor takes the route rather than a name to look up, which is what
     * lets this run without a control station - and is why it exists.
     */
    private org.traincontrol.gui.RouteEditorFrame openWithOneCondition() throws Exception
    {
        MarklinRoute route = new MarklinRoute(null, "OB-121", 40,
            new java.util.ArrayList<>(), 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            new NodeRouteCommand(RouteCommand.RouteCommandFeedback(10, false)));

        final org.traincontrol.gui.RouteEditorFrame[] frame =
            new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            frame[0] = new org.traincontrol.gui.RouteEditorFrame(null, "OB-121", route));

        return frame[0];
    }

    private void close(org.traincontrol.gui.RouteEditorFrame frame) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.dispose());
    }

    private void needsADisplay()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window - this needs a display");
        }
    }
}
