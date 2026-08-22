package ui;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinRoute;

/**
 * A route belonging to the Central Station opens to be read, and cannot be changed.
 *
 * The station owns those.  TrainControl imports them, marks them locked, and shows them with a star
 * in the route list; saving one would be writing over something the station sends again on the next
 * sync.  The old text editor greyed its controls, and the new one has to as well.
 *
 * It did not.  Greying the tables looked like enough and was not: the marks in the rows - the plus,
 * the trash, the arrows - are values the table PAINTS rather than buttons in cells, and the click
 * that works them is a click on a cell picked up by a listener belonging to the window.  A disabled
 * table does nothing about that.  So a station's route opened with its name greyed and its Save
 * switched off, and a working plus at the bottom of the command list, which is how Adam found it.
 *
 * The tests below are about the two halves of the rule, because either alone leaves the hole: the
 * marks must not be DRAWN, so nothing invites a press, and the actions must REFUSE, so a press that
 * arrives some other way does nothing.
 */
public class testRouteEditorLocked
{
    /**
     * The plus is not offered on a route that is not ours to change.
     */
    @Test
    public void testALockedRouteOffersNoWayToAddACommand() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open(locked(true));

        assertFalse(frame.isEditable(), "a route the station owns is not this window's to change");

        assertFalse(frame.offersToAddCommands(),
            "the command list is still showing its plus, so the route can be added to - which is "
            + "exactly what a locked route must not allow");

        assertFalse(frame.offersToAddConditions(),
            "and the condition list is showing its own");

        close(frame);
    }

    /**
     * And is offered on an ordinary one, or the test above would pass on a window that shows nothing.
     */
    @Test
    public void testAnOrdinaryRouteStillOffersIt() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open(locked(false));

        assertTrue(frame.isEditable(), "a local route is editable");

        assertTrue(frame.offersToAddCommands(),
            "a local route lost its plus, which would make the editor useless rather than safe");

        close(frame);
    }

    /**
     * The public way into the command list refuses too.
     *
     * Capture is switched off in a locked window, so nothing should reach appendCommand - but it is
     * public, and a rule that lives only in the control that usually calls it is a rule with a door
     * left open behind it.
     *
     * Both windows are handed the same line, and the ordinary one has to take it.  A line the parser
     * cannot read would make the locked half of this test pass while proving nothing, which is the
     * quietest way for a test about refusing to stop testing anything at all.
     */
    @Test
    public void testALockedRouteRefusesACapturedCommand() throws Exception
    {
        needsADisplay();

        // Built the way the railway builds it, rather than written out here: the format is the
        // parser's business and a guess at it is what this test would silently rest on.
        final String captured = RouteCommand.RouteCommandAccessory(90,
            Accessory.accessoryDecoderType.MM2, true).toLine(null).trim();

        org.traincontrol.gui.RouteEditorFrame ordinary = open(locked(false));

        int was = ordinary.commandCount();

        javax.swing.SwingUtilities.invokeAndWait(() -> ordinary.appendCommand(captured));

        assertEquals(ordinary.commandCount(), was + 1,
            "the line did not append to an ordinary route either, so this test proves nothing about "
            + "locked ones: " + captured);

        close(ordinary);

        org.traincontrol.gui.RouteEditorFrame owned = open(locked(true));

        int before = owned.commandCount();

        javax.swing.SwingUtilities.invokeAndWait(() -> owned.appendCommand(captured));

        assertEquals(owned.commandCount(), before,
            "a command was appended to a route belonging to the Central Station");

        close(owned);
    }

    /**
     * A field on a locked route cannot be tabbed or clicked into.
     *
     * Uneditable is not the same as out of the way.  A text field that refuses to be typed in still
     * takes the caret, still selects its text, and still shows the white box and the I-beam of one
     * that is waiting for input - so somebody who clicks it and finds nothing happens has been told
     * the window is broken rather than that the route belongs to the station.
     */
    @Test
    public void testALockedRouteHasNothingToTypeInto() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open(locked(true));

        for (javax.swing.text.JTextComponent field : fieldsIn(frame.getContentPane()))
        {
            assertFalse(field.isFocusable(),
                "a text field on a route belonging to the Central Station still takes the caret: \""
                + field.getText() + "\"");
        }

        close(frame);
    }

    /**
     * And an ordinary route still has fields to fill in, or the test above passes on an empty window.
     */
    @Test
    public void testAnOrdinaryRouteStillHasFieldsToTypeInto() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open(locked(false));

        boolean any = false;

        for (javax.swing.text.JTextComponent field : fieldsIn(frame.getContentPane()))
        {
            any = any || field.isFocusable();
        }

        assertTrue(any, "a local route has no field the keyboard can reach, which would make the "
            + "editor unusable rather than safe");

        close(frame);
    }

    /**
     * Every text field in a window, however deeply it is nested.
     */
    private static List<javax.swing.text.JTextComponent> fieldsIn(java.awt.Container where)
    {
        List<javax.swing.text.JTextComponent> found = new ArrayList<>();

        for (java.awt.Component part : where.getComponents())
        {
            if (part instanceof javax.swing.text.JTextComponent)
            {
                found.add((javax.swing.text.JTextComponent) part);
            }
            else if (part instanceof java.awt.Container)
            {
                found.addAll(fieldsIn((java.awt.Container) part));
            }
        }

        return found;
    }

    /**
     * A route with one command in it, locked or not.
     */
    private static Route locked(boolean owned)
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(80,
            Accessory.accessoryDecoderType.MM2, true));

        MarklinRoute route = new MarklinRoute(null, "Imported", 4242, commands, 0,
            Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        route.setLocked(owned);

        return route;
    }

    private static org.traincontrol.gui.RouteEditorFrame open(Route route) throws Exception
    {
        final org.traincontrol.gui.RouteEditorFrame[] frame =
            new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            frame[0] = new org.traincontrol.gui.RouteEditorFrame(null, route.getName(), route));

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
