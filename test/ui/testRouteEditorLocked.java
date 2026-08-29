package ui;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
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

        List<javax.swing.text.JTextComponent> fields = fieldsIn(frame.getContentPane());

        // Assert the variable, not the control.  This loop used to be vacuous-if-empty, and its
        // non-vacuity companion opens a DIFFERENT window down a different branch - so a locked frame
        // that built no fields at all would have passed both (TA-B10)
        assertTrue(fields.size() > 0,
            "the locked window has no text fields at all, so the loop below proves nothing about "
            + "what the keyboard can reach");

        for (javax.swing.text.JTextComponent field : fields)
        {
            assertFalse(field.isFocusable(),
                "a text field on a route belonging to the Central Station still takes the caret: \""
                + field.getText() + "\"");
        }

        close(frame);
    }

    /**
     * No cell of a locked route's tables will accept a keystroke.
     *
     * The surface this class's own javadoc is about - "the plus, the trash, the arrows" - and until
     * TA-B10 of the 2026-08-24 test suite audit nothing in the repository so much as named the gate
     * that holds it.  The tests above walk the component TREE, and a cell editor is not in the tree
     * until the cell is already being edited; by then the gate has been passed.  The gate itself is
     * `isCellEditable` on the table model, and deleting the `if (locked) return false;` at the top of
     * it made a station-owned route's command and condition cells typeable with the whole class green.
     *
     * Asked of the MODEL rather than of the JTable, because that is where the rule lives and where the
     * cell editor asks it.  Every cell of every table, including the adding row at the bottom.
     *
     * Mutation this must fail: delete `if (locked) return false;` from the command table model's
     * `isCellEditable` (RouteEditorFrame.java, around :2751).  Run 2026-08-25 against a mutant
     * compiled outside the repository: 1 of 8 fails, this test, on "row 0 column 3".  Before it, the
     * whole class was green under that mutant.
     */
    @Test
    public void testNoCellOfALockedRouteCanBeEdited() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open(locked(true));

        List<javax.swing.JTable> tables = tablesIn(frame.getContentPane());

        assertTrue(tables.size() >= 2, "the locked window should hold a command table and a "
            + "condition table; found " + tables.size() + ", so the sweep below sees less than the "
            + "window does");

        int cells = 0;

        for (javax.swing.JTable table : tables)
        {
            javax.swing.table.TableModel model = table.getModel();

            for (int row = 0; row < model.getRowCount(); row++)
            {
                for (int column = 0; column < model.getColumnCount(); column++)
                {
                    cells++;

                    assertFalse(model.isCellEditable(row, column),
                        "row " + row + " column " + column + " of a route belonging to the Central "
                        + "Station will accept a keystroke.  The station sends this route again on "
                        + "the next sync, so anything typed here is lost - and the window says so "
                        + "everywhere except in the one place that decides");
                }
            }
        }

        assertTrue(cells > 0, "no cell was examined, so nothing above tested anything");

        close(frame);
    }

    /**
     * And an ordinary route has cells that take one, or the sweep above passes on empty tables.
     *
     * The same window, the same tables, the same question - only the lock differs.  The companion to
     * the focusability test opens a different frame down a different branch, which is how a
     * vacuous-if-empty loop survived next to a non-vacuity guard that could not see it.
     */
    @Test
    public void testAnOrdinaryRouteHasCellsThatCanBeEdited() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame frame = open(locked(false));

        boolean any = false;

        for (javax.swing.JTable table : tablesIn(frame.getContentPane()))
        {
            javax.swing.table.TableModel model = table.getModel();

            for (int row = 0; row < model.getRowCount(); row++)
            {
                for (int column = 0; column < model.getColumnCount(); column++)
                {
                    any = any || model.isCellEditable(row, column);
                }
            }
        }

        assertTrue(any, "a local route has no cell the keyboard can reach, which would make the "
            + "editor useless rather than safe - and would make the locked sweep prove nothing");

        close(frame);
    }

    /**
     * A locked route draws none of the row marks, and an ordinary one draws them.
     *
     * The marks are values the table PAINTS rather than buttons in cells, which is the whole reason
     * greying the tables was not enough: the click that works them belongs to the window, not to the
     * cell.  `offersToAddCommands` asks about the plus; this asks about the other three, by looking
     * for the mark strings themselves anywhere in the tables.
     *
     * Mutation this must fail: the editability gate is a different branch and deleting it does not
     * move these, so this is checked against the drawing branch - `if (column == DELETE) return
     * DELETE_ROW; if (column == DUPLICATE) return COPY_ROW;`, the lock dropped from both
     * (RouteEditorFrame.java around :2704).  Run 2026-08-25: 1 of 8 fails, this test.
     */
    @Test
    public void testALockedRouteDrawsNoRowMarks() throws Exception
    {
        needsADisplay();

        List<String> marks = Arrays.asList(
            org.traincontrol.gui.RouteEditorFrame.markMoveUp(),
            org.traincontrol.gui.RouteEditorFrame.markMoveDown(),
            org.traincontrol.gui.RouteEditorFrame.markDelete(),
            org.traincontrol.gui.RouteEditorFrame.markCopy());

        org.traincontrol.gui.RouteEditorFrame ordinary = open(locked(false));

        // The control first: unless an ordinary route draws them, finding none on a locked one says
        // nothing at all
        assertTrue(marksFound(ordinary, marks) > 0,
            "an ordinary route draws none of " + marks + ", so their absence on a locked one means "
            + "nothing.  Either the marks were renamed or the table stopped drawing them");

        close(ordinary);

        org.traincontrol.gui.RouteEditorFrame owned = open(locked(true));

        assertEquals(marksFound(owned, marks), 0,
            "a route belonging to the Central Station is still drawing the trash and the arrows.  "
            + "They are painted values rather than buttons, so a disabled table does nothing about "
            + "them - which is how a working plus survived on a locked route");

        close(owned);
    }

    /**
     * moveRow's own guard refuses a move, not just the arrow that is never drawn.
     *
     * testALockedRouteDrawsNoRowMarks proves the arrow is not painted; this proves that reaching
     * moveRow anyway - the way the table's mouse listener reaches it, through
     * clickCommandMarkForTest rather than by calling shift() directly - still does nothing. One
     * rule, two guards, under the comment at moveRow's own declaration; this is the second one,
     * and nothing in the suite drove it before now.
     *
     * Mutation this must fail: delete "if (locked) return;" from moveRow (RouteEditorFrame.java,
     * around :1902). The ordinary-route half runs first as the control: without it, a
     * clickCommandMarkForTest that stopped reaching moveRow at all would leave both halves
     * unchanged and green.
     */
    @Test
    public void testALockedRouteRefusesToMoveARow() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame ordinary = open(lockedWithTwoCommands(false));

        int movableFirst = ordinary.commandsAsSaved().get(0).getAddress();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            ordinary.clickCommandMarkForTest(0, org.traincontrol.gui.RouteEditorFrame.markMoveDown()));

        assertNotEquals(ordinary.commandsAsSaved().get(0).getAddress(), movableFirst,
            "moving the top row down on an ordinary route left it where it was, so the locked "
            + "half below would prove nothing about moveRow's own guard");

        close(ordinary);

        org.traincontrol.gui.RouteEditorFrame owned = open(lockedWithTwoCommands(true));

        int lockedFirst = owned.commandsAsSaved().get(0).getAddress();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            owned.clickCommandMarkForTest(0, org.traincontrol.gui.RouteEditorFrame.markMoveDown()));

        assertEquals(owned.commandsAsSaved().get(0).getAddress(), lockedFirst,
            "moveRow moved a row on a route belonging to the Central Station - the drawing guard "
            + "is not the only door in");

        close(owned);
    }

    /**
     * deleteRow's own guard refuses, not just the trash mark that is never drawn.
     *
     * Same pairing as the move test above, for the second of the four guards RouteEditorFrame
     * carries beside the drawing rule.
     *
     * Mutation this must fail: delete "if (locked) return;" from deleteRow (RouteEditorFrame.java,
     * around :1910).
     */
    @Test
    public void testALockedRouteRefusesToDeleteARow() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame ordinary = open(locked(false));

        int before = ordinary.commandCount();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            ordinary.clickCommandMarkForTest(0, org.traincontrol.gui.RouteEditorFrame.markDelete()));

        assertEquals(ordinary.commandCount(), before - 1,
            "deleting a row on an ordinary route did nothing, so the locked half below would "
            + "prove nothing about deleteRow's own guard");

        close(ordinary);

        org.traincontrol.gui.RouteEditorFrame owned = open(locked(true));

        int lockedBefore = owned.commandCount();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            owned.clickCommandMarkForTest(0, org.traincontrol.gui.RouteEditorFrame.markDelete()));

        assertEquals(owned.commandCount(), lockedBefore,
            "deleteRow removed a row from a route belonging to the Central Station - the drawing "
            + "guard is not the only door in");

        close(owned);
    }

    /**
     * duplicateRow's own guard refuses, not just the copy mark that is never drawn.
     *
     * Third of the four guards.
     *
     * Mutation this must fail: delete "if (locked || table != commands) return;" from
     * duplicateRow (RouteEditorFrame.java, around :1925).
     */
    @Test
    public void testALockedRouteRefusesToDuplicateARow() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame ordinary = open(locked(false));

        int before = ordinary.commandCount();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            ordinary.clickCommandMarkForTest(0, org.traincontrol.gui.RouteEditorFrame.markCopy()));

        assertEquals(ordinary.commandCount(), before + 1,
            "duplicating a row on an ordinary route did nothing, so the locked half below would "
            + "prove nothing about duplicateRow's own guard");

        close(ordinary);

        org.traincontrol.gui.RouteEditorFrame owned = open(locked(true));

        int lockedBefore = owned.commandCount();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            owned.clickCommandMarkForTest(0, org.traincontrol.gui.RouteEditorFrame.markCopy()));

        assertEquals(owned.commandCount(), lockedBefore,
            "duplicateRow copied a row on a route belonging to the Central Station - the drawing "
            + "guard is not the only door in");

        close(owned);
    }

    /**
     * addTo's own guard refuses even though nothing can click its way there.
     *
     * The fourth guard is different from the other three: a locked table's adding row never
     * shows ADD_HERE - CommandTable.getValueAt returns "" there when locked - so the mouse
     * listener that calls addTo(table) never fires on a locked frame, and clickCommandMarkForTest
     * has no case for it either (there is no mark to name). That makes the drawing guard the
     * only one a real click, or the test above it in this class, could ever exercise - so the
     * second guard inside addTo itself was reachable from nothing in the suite. This calls the
     * private method directly, the only way left to ask whether it would also have stopped a
     * caller that reached it some other way - reflection standing in for "anything added later
     * that forgets to ask", which is the reason the comment at moveRow's declaration gives for
     * having two guards at all.
     *
     * Mutation this must fail: delete "if (locked) return;" from addTo (RouteEditorFrame.java,
     * around :1934).
     */
    @Test
    public void testALockedRouteRefusesToAddARowEvenCalledDirectly() throws Exception
    {
        needsADisplay();

        org.traincontrol.gui.RouteEditorFrame ordinary = open(locked(false));

        int before = ordinary.commandCount();

        invokeAddTo(ordinary);

        assertEquals(ordinary.commandCount(), before + 1,
            "calling addTo directly on an ordinary route did nothing, so the locked half below "
            + "would prove nothing about addTo's own guard");

        close(ordinary);

        org.traincontrol.gui.RouteEditorFrame owned = open(locked(true));

        int lockedBefore = owned.commandCount();

        invokeAddTo(owned);

        assertEquals(owned.commandCount(), lockedBefore,
            "addTo appended a command to a route belonging to the Central Station when called "
            + "directly - the drawing guard that keeps a real click from ever reaching it is not "
            + "the only thing standing in the way");

        close(owned);
    }

    /**
     * RouteEditorFrame.addTo(commands), the private method a real click cannot reach on a locked
     * table because the adding row never shows ADD_HERE to click on.
     */
    private static void invokeAddTo(org.traincontrol.gui.RouteEditorFrame frame) throws Exception
    {
        java.lang.reflect.Field commandsField = frame.getClass().getDeclaredField("commands");
        commandsField.setAccessible(true);
        Object commandsTable = commandsField.get(frame);

        java.lang.reflect.Method addTo = frame.getClass().getDeclaredMethod("addTo",
            javax.swing.JTable.class);
        addTo.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                addTo.invoke(frame, commandsTable);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * How many cells of the window's tables hold one of the row marks.
     */
    private static int marksFound(org.traincontrol.gui.RouteEditorFrame frame, List<String> marks)
    {
        int found = 0;

        for (javax.swing.JTable table : tablesIn(frame.getContentPane()))
        {
            for (int row = 0; row < table.getRowCount(); row++)
            {
                for (int column = 0; column < table.getColumnCount(); column++)
                {
                    Object value = table.getValueAt(row, column);

                    if (value != null && marks.contains(value.toString())) found++;
                }
            }
        }

        return found;
    }

    /**
     * Every table in a window, however deeply it is nested.
     *
     * A JTable is a Container, but what it holds is the cell editor currently open - never the cells -
     * so the walk stops at one rather than descending into it.
     */
    private static List<javax.swing.JTable> tablesIn(java.awt.Container where)
    {
        List<javax.swing.JTable> found = new ArrayList<>();

        for (java.awt.Component part : where.getComponents())
        {
            if (part instanceof javax.swing.JTable)
            {
                found.add((javax.swing.JTable) part);
            }
            else if (part instanceof java.awt.Container)
            {
                found.addAll(tablesIn((java.awt.Container) part));
            }
        }

        return found;
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

    /**
     * A route with two DISTINCT commands, locked or not - so a moved row has somewhere to go and
     * can be told apart from the one it swapped with.
     */
    private static Route lockedWithTwoCommands(boolean owned)
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(80,
            Accessory.accessoryDecoderType.MM2, true));

        commands.add(RouteCommand.RouteCommandAccessory(81,
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
