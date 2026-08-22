package ui;

import java.awt.GraphicsEnvironment;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.ThreeWaySwitch;

/**
 * A row that looks right and means nothing.
 *
 * Adam turned an accessory row into a locomotive command and got a command for a locomotive called
 * "3".  Nothing refused it: the address had simply stayed behind in the column that had become the
 * name column, the row built, the route saved, and it did nothing whatever when it ran.  A route
 * that quietly does nothing is the worst thing this editor can produce, because there is no error
 * anywhere to lead anybody back to it.
 *
 * Two things had to change and both are tested here.  Changing the kind clears the row, because the
 * two kinds' columns hold different sorts of thing and only both happen to accept text; and Save
 * checks each row against the layout, because "there is no locomotive called 3" is a question only
 * the layout can answer.
 */
public class testRouteEditorValidation
{
    /**
     * Changing the kind does not leave the old target behind.
     *
     * The one Adam found.  Tested through the table model, which is the thing that was wrong - the
     * row it built is what got saved.
     */
    @Test
    public void testChangingTheKindClearsTheRow() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame.appendCommand(RouteCommand.RouteCommandAccessory(3,
                Accessory.accessoryDecoderType.MM2, true).toLine(null).trim());

            frame.setCommandKindForTest(0, CommandRow.Kind.LOCOMOTIVE_SPEED);
        });

        CommandRow row = frame.commandRowForTest(0);

        assertEquals(row.getKind(), CommandRow.Kind.LOCOMOTIVE_SPEED, "the kind did not change");

        assertEquals(row.getTarget(), "",
            "the accessory's address stayed behind as the locomotive's NAME, which is how a route "
            + "ends up commanding a locomotive called 3");

        close(frame);
    }

    /**
     * A three-way row starts with the pause its two motors need.
     */
    @Test
    public void testAThreeWayRowStartsWithItsPause() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame.appendCommand(RouteCommand.RouteCommandAccessory(3,
                Accessory.accessoryDecoderType.MM2, true).toLine(null).trim());

            frame.setCommandKindForTest(0, CommandRow.Kind.THREE_WAY);
        });

        assertEquals(frame.commandRowForTest(0).getDelay(), ThreeWaySwitch.SETTLE,
            "a three-way with no pause sends its second motor while the first is still moving");

        close(frame);
    }

    /**
     * A three-way row is saved as the two commands it stands for, in order.
     */
    @Test
    public void testAThreeWayIsSavedAsBothOfItsCommands() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame.appendCommand(RouteCommand.RouteCommandAccessory(1,
                Accessory.accessoryDecoderType.MM2, false).toLine(null).trim());

            frame.setCommandKindForTest(0, CommandRow.Kind.THREE_WAY);
            frame.setCommandTargetForTest(0, "1");
            frame.setCommandSettingForTest(0, ThreeWaySwitch.wordFor(ThreeWaySwitch.Position.LEFT));
        });

        java.util.List<RouteCommand> saved = frame.commandsAsSaved();

        assertEquals(saved.size(), 2, "one row, two motors - a three-way saved as one command is "
            + "half a point");

        assertEquals(saved.get(0).getAddress(), 2,
            "left settles the SECOND address first; sending them the other way round drives the "
            + "point through the position in between on its way");

        assertFalse(saved.get(0).getSetting(), "and settles it straight");

        assertEquals(saved.get(0).getDelay(), ThreeWaySwitch.SETTLE, "with the pause on the first");

        assertEquals(saved.get(1).getAddress(), 1, "then turns the first");

        assertTrue(saved.get(1).getSetting());

        close(frame);
    }

    private static org.traincontrol.gui.RouteEditorFrame open() throws Exception
    {
        final org.traincontrol.gui.RouteEditorFrame[] frame =
            new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            frame[0] = new org.traincontrol.gui.RouteEditorFrame(null, null));

        return frame[0];
    }

    private static void close(org.traincontrol.gui.RouteEditorFrame frame) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.dispose());
    }

    private static void needsADisplay()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window - this needs a display");
        }
    }
}
