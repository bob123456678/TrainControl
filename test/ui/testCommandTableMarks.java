package ui;

import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.RouteEditorFrame;
import org.traincontrol.util.I18n;

/**
 * The three marks at the end of a command row do exactly what they say.
 *
 * MT-029, Adam: "Delete removes exactly one row; the arrows move a row and leave it moved; duplicate
 * makes one copy." He could not sit and count rows for every combination, and asked for it
 * programmatically: "seems to work in the UI, but this should have a programmatic test."
 *
 * **Exactly one** is the whole point of the entry. A delete that takes two rows, or a duplicate that
 * makes two copies, is not a cosmetic fault in a route editor - a route is a sequence of commands sent
 * to real hardware, and a doubled command is a second throw of the same accessory.
 *
 * The marks are values the table PAINTS rather than buttons in a cell, so there is nothing to click;
 * this drives the same private methods the cell editor calls, which keeps it from becoming a second
 * implementation of move and delete that could agree with itself while the real one is wrong.
 *
 * @author Adam
 */
public class testCommandTableMarks
{
    /**
     * Delete takes one row, the arrows move one and leave it moved, duplicate makes one copy.
     */
    @Test
    public void testEachMarkActsOnExactlyOneRow() throws Exception
    {
        needsADisplay();

        final RouteEditorFrame frame = open();

        try
        {
            // Three rows, each identifiable by its accessory address
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                for (int address : new int[] {11, 12, 13})
                {
                    frame.appendCommand(RouteCommand.RouteCommandAccessory(address,
                        Accessory.accessoryDecoderType.MM2, true).toLine(null).trim());
                }
            });

            assertEquals(frame.commandRowCountForTest(), 3, "the three rows were not added");

            String first = target(frame, 0);
            String second = target(frame, 1);
            String third = target(frame, 2);

            assertNotEquals(first, second, "the rows are indistinguishable, so nothing below can tell "
                + "which one moved");

            // --- the arrows -------------------------------------------------------------------
            act(frame, 0, RouteEditorFrame.markMoveDown());

            assertEquals(frame.commandRowCountForTest(), 3, "moving a row changed how many there are");

            assertEquals(target(frame, 0), second, "moving row 0 down did not bring row 1 up");
            assertEquals(target(frame, 1), first, "the moved row is not where it was moved to");
            assertEquals(target(frame, 2), third, "a row nobody touched moved");

            act(frame, 1, RouteEditorFrame.markMoveUp());

            assertEquals(target(frame, 0), first, "moving it back up did not restore the order - the "
                + "arrows must leave a row where they put it, not spring back");
            assertEquals(target(frame, 1), second);

            // --- duplicate --------------------------------------------------------------------
            act(frame, 0, RouteEditorFrame.markCopy());

            assertEquals(frame.commandRowCountForTest(), 4,
                "duplicate made " + (frame.commandRowCountForTest() - 3) + " copies rather than one. "
                + "A route is a sequence of commands sent to real hardware, so a doubled row is a "
                + "second throw of the same accessory");

            assertEquals(target(frame, 1), first,
                "the copy is not directly under the row it was made from, which is where it belongs - "
                + "the reason to copy a row is that the next command is nearly the same one");

            // --- delete -----------------------------------------------------------------------
            act(frame, 1, RouteEditorFrame.markDelete());

            assertEquals(frame.commandRowCountForTest(), 3,
                "delete removed " + (4 - frame.commandRowCountForTest()) + " rows rather than one");

            assertEquals(target(frame, 0), first, "delete took the wrong row");
            assertEquals(target(frame, 1), second, "delete took the wrong row");
            assertEquals(target(frame, 2), third, "delete took the wrong row");
        }
        finally
        {
            close(frame);
        }
    }

    private String target(RouteEditorFrame frame, int row)
    {
        org.traincontrol.base.CommandRow r = frame.commandRowForTest(row);

        assertNotNull(r, "row " + row + " holds nothing");

        return r.getTarget();
    }

    private void act(final RouteEditorFrame frame, final int row, final String mark) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.clickCommandMarkForTest(row, mark));
    }

    /**
     * An emergency stop stands alone: the editor will not save a route that has one among other commands, and says how to
     * split it - Adam, 2026-09-25: *"if a route has emergency stop, it cannot have any other types of commands.  Reject
     * it from being created or imported as such."*
     *
     * MUTATION: drop the rule from the editor's Save, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEmergencyStopStandsAlone() throws Exception
    {
        needsADisplay();

        final RouteEditorFrame frame = open();

        try
        {
            final String alone = I18n.f("route.ui.frameStopStandsAlone", I18n.t("route.kind.STOP"),
                I18n.t("route.kind.ROUTE"));

            // IN THE EDITOR'S OWN WORDS (RLA5-C6, RLU5-C3): the message names the rows as the editor labels them
            assertTrue(alone.contains(I18n.t("route.kind.STOP")) && alone.contains(I18n.t("route.kind.ROUTE")),
                "the editor's refusal does not name its rows as the editor labels them: " + alone);

            javax.swing.SwingUtilities.invokeAndWait(() ->
                frame.appendCommand(RouteCommand.RouteCommandStop().toLine(null).trim()));

            assertEquals(frame.commandRowCountForTest(), 1, "precondition: the stop was not added");

            final java.util.List<java.util.List<String>> problems = new java.util.ArrayList<>();

            javax.swing.SwingUtilities.invokeAndWait(() -> problems.add(frame.problemsForTest()));

            assertFalse(problems.get(0).contains(alone), "CONTROL: an emergency stop on its own is refused");

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                frame.appendCommand(RouteCommand.RouteCommandAccessory(11, Accessory.accessoryDecoderType.MM2, true)
                    .toLine(null).trim());

                problems.add(frame.problemsForTest());
            });

            assertEquals(frame.commandRowCountForTest(), 2, "precondition: the switch was not added");

            assertTrue(problems.get(1).contains(alone), "the editor would save an emergency stop among other commands: "
                + problems.get(1));
        }
        finally
        {
            close(frame);
        }
    }

    private static RouteEditorFrame open() throws Exception
    {
        final RouteEditorFrame[] frame = new RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> frame[0] = new RouteEditorFrame(null, null));

        return frame[0];
    }

    private static void close(final RouteEditorFrame frame) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.dispose());
    }

    private static void needsADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window");
        }
    }
}
