package ui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import support.Components;
import support.SettingsWindow;

/**
 * The Functions tab's Now column switches a function on the railway at once, by the door the main window's function
 * buttons use: not at Save, not undone by Cancel, following the railway, and locked when the moving rule locks the
 * window.
 */
public class testTheFunctionsTabSwitchesFunctionsNow
{
    private static final String NAME = "FN train";
    private static final int F = 3;
    private static final int NOW = 3;
    private static final long WAIT = 8000;

    private static support.SettingsFixture fixture;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static MarklinLocomotive loc;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        fixture = support.SettingsFixture.open();
        model = fixture.model;
        ui = fixture.ui;
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
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
        fixture.quiet();
        fixture.forget(NAME);

        SettingsWindow.power(ui, model, false);

        loc = model.newDCCLocomotive(NAME, 3701);

        SettingsWindow.showOnKeyA(ui, loc);

        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.FUNCTIONS, null), "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
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

    /**
     * A click on a function's Now box turns it on, and the next click turns it off, on the locomotive at once and
     * without Save, which stays grayed.
     *
     * MUTATION: make `TableModel.setValueAt` ignore column 3, or let `isCellEditable` refuse it, and this fails.
     */
    @Test
    public void testTheNowBoxSwitchesTheFunctionOnAndOff() throws Exception
    {
        assertFalse(loc.getF(F));

        clickNow(F);

        assertTrue(Components.waitFor(() -> loc.getF(F), WAIT), "the click did not switch the function on");
        assertTrue(Components.waitFor(() -> shown(F), WAIT), "the box does not show it on");
        assertFalse(SettingsWindow.save().isEnabled(), "a switch counts as an unsaved change");

        clickNow(F);

        assertTrue(Components.waitFor(() -> !loc.getF(F), WAIT), "the second click did not switch it off");
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
     * Cancel does not put a switched function back: the switch was a command, never part of Save.
     *
     * MUTATION: have `close` switch every function off and this fails.
     */
    @Test
    public void testCancelLeavesASwitchedFunctionOn() throws Exception
    {
        clickNow(F);

        assertTrue(Components.waitFor(() -> loc.getF(F), WAIT));

        SettingsWindow.press(SettingsWindow.cancel());

        assertTrue(loc.getF(F), "Cancel undid a switch");
    }

    /**
     * While the moving rule locks the window the Now boxes cannot be clicked.
     *
     * MUTATION: drop `!blocked` from `isCellEditable` and this fails.
     */
    @Test
    public void testALockedWindowCannotSwitchAFunction() throws Exception
    {
        SettingsWindow.power(ui, model, true);
        loc.setSpeed(40);

        assertTrue(Components.waitFor(() -> !Components.onTheEventThread(() -> table().isCellEditable(F, NOW)), WAIT),
            "the box is still clickable in a locked window");

        clickNow(F);

        assertFalse(loc.getF(F), "a locked window switched a function");
    }
}
