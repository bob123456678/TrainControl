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
 * OB-121: the + row at the bottom of the conditions list is white (Adam: "make all its cells white, not gray").
 *
 * Reading the code said this could not happen: `greyWhatCannotBeEdited` exempts the last row, which IS the + row.  What
 * the reading missed is that a table cell renderer hands back ONE recycled component for every cell, so the cell drawn
 * before the + row paints that shared label gray, and the exemption handed the same label back for each cell of the
 * + row.  So the order below is the test: ask for a grayed cell FIRST and the + row's cells after it, as the table
 * paints them; the other way round passes with the defect present.
 *
 * @author Adam
 */
public class testRouteEditorShading
{
    /**
     * Every cell of the + row is the table's own color, even straight after a grayed one.
     *
     * MUTATION: either exemption going back to a bare `return out` fails this, and only because the grayed cell is
     * rendered first.
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

    /** One cell, asked of the table exactly as painting asks for it. */
    private Component render(JTable table, int row, int column)
    {
        return table.getCellRenderer(row, column).getTableCellRendererComponent(
            table, table.getValueAt(row, column), false, false, row, column);
    }

    /**
     * The conditions table, told apart from the commands table by its class: both are JTables with different renderers.
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
     * Highlight on Diagram survives a route that drives a locomotive.  The button gathers the addresses a route touches
     * so it can flash them, and asked every command for its address; `RouteCommand.getAddress()` is an
     * `Integer.parseInt` over a key only accessory and feedback commands carry, so a locomotive speed, direction or
     * function command threw out of the button.  `hasAddress()` exists for this.  Driven through the button, because
     * that is what was reported.
     *
     * MUTATION this catches: taking `hasAddress()` back out of either loop.
     */
    @Test
    public void testHighlightSurvivesALocomotiveCommand() throws Exception
    {
        needsADisplay();

        java.util.List<org.traincontrol.base.RouteCommand> commands = new java.util.ArrayList<>();

        // An accessory, which has an address, and a locomotive, which has not.
        commands.add(RouteCommand.RouteCommandAccessory(12,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true));
        commands.add(RouteCommand.RouteCommandLocomotiveSpeed("BR 218", 50));

        MarklinRoute route = new MarklinRoute(null, "IPR-B1", 41,
            commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            new NodeRouteCommand(RouteCommand.RouteCommandFeedback(10, false)));

        final org.traincontrol.gui.RouteEditorFrame[] frame =
            new org.traincontrol.gui.RouteEditorFrame[1];

        // A REAL WINDOW IS THE PARENT: with none, highlightOnDiagram returns before the loop that asks each command
        // for its address, and the claim below would pass with that question taken out.
        support.LayoutSandbox sandbox = null;

        org.traincontrol.marklin.MarklinControlStation model = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedWithWindowInDebug();

            model.setNetworkCommState(false);

            final org.traincontrol.gui.TrainControlUI ui = (org.traincontrol.gui.TrainControlUI) model.getGUI();

            assertNotNull(ui, "precondition: there is no window to be the editor's parent");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame[0] = new org.traincontrol.gui.RouteEditorFrame(ui, "IPR-B1", route));

            try
            {
                // THE ROUTE'S COMMANDS HAVE TO REACH THE TABLE, or the loop under test sees nothing and this passes
                // vacuously.
                java.lang.reflect.Field rows = org.traincontrol.gui.RouteEditorFrame.class
                    .getDeclaredField("commands");

                rows.setAccessible(true);

                Object table = rows.get(frame[0]);

                java.lang.reflect.Field list = table.getClass().getDeclaredField("rows");

                list.setAccessible(true);

                assertTrue(((java.util.List<?>) list.get(table)).size() >= 2,
                    "precondition: the editor did not load the route's two commands, so the loop under "
                    + "test has nothing to walk.  Rows: " + list.get(table));

                java.lang.reflect.Method highlight = org.traincontrol.gui.RouteEditorFrame.class
                    .getDeclaredMethod("highlightOnDiagram");

                highlight.setAccessible(true);

                final Throwable[] thrown = new Throwable[1];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        highlight.invoke(frame[0]);
                    }
                    catch (java.lang.reflect.InvocationTargetException failed)
                    {
                        thrown[0] = failed.getCause();
                    }
                    catch (Exception other)
                    {
                        thrown[0] = other;
                    }
                });

                assertNull(thrown[0],
                    "Highlight on Diagram threw on a route that drives a locomotive.  It asks every "
                    + "command for an address, and a locomotive command has none - so the button a "
                    + "person presses to see what a route touches fails on the routes most worth "
                    + "looking at.  Threw: " + thrown[0]);

                // AND THE CONTRACT THE GUARD RESTS ON, asserted directly: a locomotive command has no address
                // to read, so the caller has to ask first.
                org.traincontrol.base.RouteCommand driving =
                    RouteCommand.RouteCommandLocomotiveSpeed("BR 218", 50);

                assertFalse(driving.hasAddress(),
                    "a locomotive command reports that it has an address, so the guard that asks before "
                    + "reading one would let the read through");

                try
                {
                    driving.getAddress();

                    fail("getAddress() answered for a locomotive command instead of throwing.  If that is "
                        + "deliberate the guard at the highlight is unnecessary, and this test should go "
                        + "with it");
                }
                catch (RuntimeException expected)
                {
                    // Which is why the caller has to ask hasAddress() first.
                }
            }
            finally
            {
                close(frame[0]);
            }
        }
        finally
        {
            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }
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
