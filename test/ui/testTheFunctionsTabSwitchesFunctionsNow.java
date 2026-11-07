package ui;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinLocomotive;
import support.Components;
import support.SettingsWindow;
import support.WireRecorder;

/**
 * The Functions tab's Now column switches a function on the railway at once, by the door the main window's function
 * buttons use: not at Save, not undone by Cancel, following the railway, and locked when the moving rule locks the
 * window.  Commands are counted on the wire.
 */
public class testTheFunctionsTabSwitchesFunctionsNow
{
    private static final String NAME = "FN train";
    private static final int F = 3;
    private static final int NOW = 3;
    private static final long WAIT = 8000;

    private static support.SettingsFixture fixture;
    private static TrainControlUI ui;
    private static MarklinLocomotive loc;
    private static WireRecorder wire;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        fixture = support.SettingsFixture.open();
        ui = fixture.ui;
        wire = WireRecorder.open();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (wire != null) wire.close();
            if (fixture != null) fixture.finish(NAME);
        }
        finally
        {
            if (fixture != null) fixture.closeSandbox();
        }
    }

    @BeforeMethod
    public void lay() throws Exception
    {
        loc = fixture.layDcc(NAME, 3701, LocomotiveSettingsFrame.Tab.FUNCTIONS);
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        fixture.quiet();
    }

    private static JTable table()
    {
        return SettingsWindow.part("functions.table", JTable.class);
    }

    /** A click on the Now cell of a function's row, as the pointer delivers it. */
    private static void clickNow(int row) throws Exception
    {
        JTable table = table();

        Components.onTheEventThread(() ->
        {
            Rectangle r = table.getCellRect(row, NOW, false);
            int x = r.x + r.width / 2;
            int y = r.y + r.height / 2;
            long t = System.currentTimeMillis();

            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_PRESSED, t, 0, x, y, 1, false, MouseEvent.BUTTON1));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_RELEASED, t, 0, x, y, 1, false, MouseEvent.BUTTON1));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, t, 0, x, y, 1, false, MouseEvent.BUTTON1));

            return null;
        });

        Components.settle();
    }

    private static boolean shown(int row)
    {
        return (Boolean) Components.onTheEventThread(() -> table().getValueAt(row, NOW));
    }

    /** What the function commands for the locomotive were, from a mark, as text. */
    private static List<String> sent(int mark)
    {
        List<String> out = new ArrayList<>();

        for (WireRecorder.Sent s : wire.functionCommandsSince(mark, loc.getIntUID())) out.add(s.toString());

        return out;
    }

    /**
     * A click on a function's Now box turns it on with one command, and the next click turns it off with one, on the
     * locomotive at once and without Save, which stays grayed.
     *
     * MUTATION: make `TableModel.setValueAt` ignore column 3, or let `isCellEditable` refuse it, and this fails.
     */
    @Test
    public void testTheNowBoxSwitchesTheFunctionOnAndOffWithOneCommandEach() throws Exception
    {
        assertFalse(loc.getF(F));

        int mark = wire.mark();

        clickNow(F);

        assertTrue(Components.waitFor(() -> loc.getF(F), WAIT), "the click did not switch the function on");
        assertTrue(Components.waitFor(() -> shown(F), WAIT), "the box does not show it on");
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).size(), 1, "on took " + sent(mark));
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).get(0).sixth(), 1, "not an on command");
        assertFalse(SettingsWindow.save().isEnabled(), "a switch counts as an unsaved change");

        clickNow(F);

        assertTrue(Components.waitFor(() -> !loc.getF(F), WAIT), "the second click did not switch it off");
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).size(), 2, "off took " + sent(mark));
        assertEquals(wire.functionCommandsSince(mark, loc.getIntUID()).get(1).sixth(), 0, "not an off command");
    }

    /**
     * A momentary function pressed in the Now box sends what the main window's button sends for it: the same commands
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

        clickNow(F);

        assertTrue(Components.waitFor(() -> wire.functionCommandsSince(second, loc.getIntUID()).size() >= 2, WAIT),
            "the Now box sent " + sent(second));
        assertEquals(sent(second), fromTheButton);
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

        assertTrue(Components.waitFor(() -> shown(F + 1), WAIT), "the box did not follow the function");

        loc.setF(F + 1, false);

        assertTrue(Components.waitFor(() -> !shown(F + 1), WAIT), "the box did not follow it off");
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
        clickNow(F);

        assertTrue(Components.waitFor(() -> loc.getF(F), WAIT));

        int mark = wire.mark();

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(loc.getF(F), "Cancel undid a switch");
        assertTrue(sent(mark).isEmpty(), "Cancel sent " + sent(mark));

        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.FUNCTIONS, null));
        assertTrue(SettingsWindow.awaited() != null);

        Components.onTheEventThread(() ->
        {
            table().setValueAt(Boolean.TRUE, F + 1, 4);

            return null;
        });

        Components.settle();
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(loc.getF(F), "Save undid a switch");
        assertTrue(sent(mark).isEmpty(), "Save sent " + sent(mark));
    }

    /**
     * While the moving rule locks the window the Now boxes cannot be clicked.
     *
     * MUTATION: drop `!blocked` from `isCellEditable` and this fails.
     */
    @Test
    public void testALockedWindowCannotSwitchAFunction() throws Exception
    {
        SettingsWindow.power(ui, fixture.model, true);
        loc.setSpeed(40);

        assertTrue(Components.waitFor(() -> !Components.onTheEventThread(() -> table().isCellEditable(F, NOW)), WAIT),
            "the box is still clickable in a locked window");

        int mark = wire.mark();

        clickNow(F);

        assertFalse(loc.getF(F), "a locked window switched a function");
        assertTrue(sent(mark).isEmpty(), "a locked window sent " + sent(mark));
    }
}
