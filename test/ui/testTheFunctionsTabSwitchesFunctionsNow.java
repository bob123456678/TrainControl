package ui;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.gui.widgets.RowIcons;
import support.Components;
import support.SettingsWindow;
import support.WireRecorder;

/**
 * The play icon at the right of the Functions tab's Function cell switches a function on the railway at once, by the
 * door the main window's function buttons use: not at Save, not undone by Cancel, following the railway, turning while
 * a timed or momentary function fires, and locked when the moving rule locks the window.  Commands are counted on the
 * wire.
 */
public class testTheFunctionsTabSwitchesFunctionsNow extends support.SettingsClass
{
    private static final String NAME = "FN train";
    private static final int F = 3;
    private static final int FUNCTION = 1;
    private static final long WAIT = 8000;

    private static MarklinLocomotive loc;
    private static WireRecorder wire;

    @org.testng.annotations.BeforeClass
    public static void listen()
    {
        wire = WireRecorder.open();
    }

    @org.testng.annotations.AfterClass(alwaysRun = true)
    public static void stopListening()
    {
        if (wire != null) wire.close();
    }

    @Override
    protected String[] made()
    {
        return new String[] { NAME };
    }

    @BeforeMethod
    public void lay() throws Exception
    {
        loc = fixture.layDcc(NAME, 3701, LocomotiveSettingsFrame.Tab.FUNCTIONS);
    }

    private static JTable table()
    {
        return SettingsWindow.part("functions.table", JTable.class);
    }

    /** A click on a row's play icon, at the right edge of its Function cell, as the pointer delivers it. */
    private static void clickPlay(int row) throws Exception
    {
        JTable table = table();

        Components.onTheEventThread(() ->
        {
            Rectangle r = table.getCellRect(row, FUNCTION, false);
            int x = r.x + r.width - 12;
            int y = r.y + r.height / 2;
            long t = System.currentTimeMillis();

            for (int id : new int[] { MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED })
            {
                table.dispatchEvent(new MouseEvent(table, id, t, 0, x, y, 1, false, MouseEvent.BUTTON1));
            }

            return null;
        });

        Components.settle();
    }

    /** How the row's play icon reads: the state its renderer drew. */
    private static RowIcons.PlayState state(int row)
    {
        return Components.onTheEventThread(() ->
        {
            JTable table = table();
            java.awt.Component cell = table.prepareRenderer(table.getCellRenderer(row, FUNCTION), row, FUNCTION);

            for (java.awt.Component c : ((java.awt.Container) cell).getComponents())
            {
                if (c instanceof javax.swing.JLabel && ((javax.swing.JLabel) c).getClientProperty("playState") != null)
                {
                    return (RowIcons.PlayState) ((javax.swing.JLabel) c).getClientProperty("playState");
                }
            }

            return null;
        });
    }

    /** What the function commands for the locomotive were, from a mark, as text. */
    private static List<String> sent(int mark)
    {
        List<String> out = new ArrayList<>();

        for (WireRecorder.Sent s : wire.functionCommandsSince(mark, loc.getIntUID())) out.add(s.toString());

        return out;
    }

    /**
     * A click on a function's play icon turns it on with one command, and the next click turns it off with one, on the
     * locomotive at once and without Save, which stays grayed.
     *
     * MUTATION: make `TableModel.setValueAt` ignore column 3, or let `isCellEditable` refuse it, and this fails.
     */
    @Test
    public void testThePlayIconSwitchesTheFunctionOnAndOffWithOneCommandEach() throws Exception
    {
        assertFalse(loc.getF(F));

        int mark = wire.mark();

        clickPlay(F);

        assertTrue(Components.waitFor(() -> loc.getF(F), WAIT), "the click did not switch the function on");
        assertTrue(Components.waitFor(() -> (state(F) == RowIcons.PlayState.ON), WAIT), "the box does not show it on");
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).size(), 1, "on took " + sent(mark));
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).get(0).sixth(), 1, "not an on command");
        assertFalse(SettingsWindow.save().isEnabled(), "a switch counts as an unsaved change");

        clickPlay(F);

        assertTrue(Components.waitFor(() -> !loc.getF(F), WAIT), "the second click did not switch it off");
        assertTrue(Components.waitFor(() -> wire.functionCommandsSince(mark, loc.getIntUID()).size() >= 2, WAIT),
            "the off command never reached the wire: " + sent(mark));
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).size(), 2, "off took " + sent(mark));
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).get(1).sixth(), 0, "not an off command");
    }

    /**
     * A momentary function pressed on its play icon sends what the main window's button sends for it: the same commands
     * in the same order.
     *
     * MUTATION: have `TrainControlUI.fireFunction` call `setF` for a pulse function and this fails.
     */
    @Test
    public void testAMomentaryFunctionSendsWhatTheMainWindowsButtonSends() throws Exception
    {
        loc.setFunctionType(F, 9, Locomotive.FUNCTION_PULSE);

        int mark = wire.mark();

        Components.press(SettingsWindow.functionButton(ui, F));

        assertTrue(Components.waitFor(() -> wire.functionCommandsSince(mark, loc.getIntUID()).size() >= 2, WAIT),
            "the main window's button sent " + sent(mark));

        List<String> fromTheButton = sent(mark);

        assertTrue(Components.waitFor(() -> !loc.getF(F), WAIT));

        int second = wire.mark();

        clickPlay(F);

        assertTrue(Components.waitFor(() -> wire.functionCommandsSince(second, loc.getIntUID()).size() >= 2, WAIT),
            "the play icon sent " + sent(second));
        assertEquals(sent(second), fromTheButton);
    }

    /**
     * A timed function shows a turning arc while it runs its course, then goes back to the outline.
     *
     * MUTATION: make `firing` return false and this fails.
     */
    @Test
    public void testATimedFunctionTurnsThenReturns() throws Exception
    {
        loc.setFunctionType(F, 9, 2);

        clickPlay(F);

        assertTrue(Components.waitFor(() -> state(F) == RowIcons.PlayState.FIRING, WAIT), "no spinner while it ran: "
            + state(F));
        assertTrue(Components.waitFor(() -> state(F) == RowIcons.PlayState.OFF, WAIT), "the icon did not go back: "
            + state(F));
    }

    /**
     * A function switched from elsewhere shows in the box while the window is open.
     *
     * MUTATION: return false from `getValueAt` for column 3 and this fails.
     */
    @Test
    public void testTheBoxFollowsTheRailway() throws Exception
    {
        loc.setF(F + 1, true);

        assertTrue(Components.waitFor(() -> (state(F + 1) == RowIcons.PlayState.ON), WAIT), "the box did not follow the function");

        loc.setF(F + 1, false);

        assertTrue(Components.waitFor(() -> !(state(F + 1) == RowIcons.PlayState.ON), WAIT), "the box did not follow it off");
    }

    /**
     * Neither Cancel nor Save touches a switched function or sends another function command: the switch was a
     * command, never part of Save.
     *
     * MUTATION: have `close` or `write` switch every function off and this fails.
     */
    @Test
    public void testSaveAndCancelLeaveAFunctionAsSwitched() throws Exception
    {
        clickPlay(F);

        assertTrue(Components.waitFor(() -> loc.getF(F), WAIT));

        int mark = wire.mark();

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(loc.getF(F), "Cancel undid a switch");
        assertTrue(sent(mark).isEmpty(), "Cancel sent " + sent(mark));

        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.FUNCTIONS, null));
        assertTrue(SettingsWindow.awaited() != null);

        Components.onTheEventThread(() ->
        {
            table().setValueAt(Boolean.TRUE, F + 1, 3);

            return null;
        });

        Components.settle();
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(loc.getF(F), "Save undid a switch");
        assertTrue(sent(mark).isEmpty(), "Save sent " + sent(mark));
    }

    /**
     * While the moving rule locks the window the play icons are grayed and do nothing.
     *
     * MUTATION: drop `!blocked` from `isCellEditable` and this fails.
     */
    @Test
    public void testALockedWindowCannotSwitchAFunction() throws Exception
    {
        SettingsWindow.power(ui, fixture.model, true);
        loc.setSpeed(40);

        assertTrue(Components.waitFor(() -> state(F) == RowIcons.PlayState.DISABLED, WAIT),
            "the play icon is not grayed in a locked window");

        int mark = wire.mark();

        clickPlay(F);

        assertFalse(loc.getF(F), "a locked window switched a function");
        assertTrue(sent(mark).isEmpty(), "a locked window sent " + sent(mark));
    }
}
