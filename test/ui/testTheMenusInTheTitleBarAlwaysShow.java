package ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.JRootPane;
import javax.swing.UIManager;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import support.Components;
import support.Display;
import support.Keys;
import support.Reflect;

/**
 * Menus drawn in the title bar take no room from the window, so they always show: the Toggle Menu Bar checkbox gives
 * its place to Window Always on Top, Control+M does nothing, and a stored "off" neither hides them nor shrinks the
 * window (OB-484).  Where FlatLaf leaves the menus in a bar of their own (a Mac), the checkbox and its preference work
 * as before.
 *
 * Each claim starts the application as `MarklinControlStation.init` does, in one sandbox for the class.
 */
public class testTheMenusInTheTitleBarAlwaysShow
{
    private static final String EMBEDDED = "TitlePane.menuBarEmbedded";

    private static support.LayoutSandbox sandbox;

    private static String barWas;

    private static String onTopWas;

    /** What a started window shows: its menu bar and the two checkboxes, and the frame's size and minimum size. */
    private static final class Seen
    {
        boolean bar;
        boolean toggleMenuBar;
        boolean alwaysOnTop;
        Dimension size;
        Dimension minimum;

        @Override
        public String toString()
        {
            return "menu bar " + bar + ", Toggle Menu Bar " + toggleMenuBar + ", Always on Top " + alwaysOnTop
                + ", size " + size + ", minimum " + minimum;
        }
    }

    private interface Then
    {
        void on(TrainControlUI ui) throws Exception;
    }

    @BeforeClass(timeOut = 60000)
    public static void setUpClass() throws Exception
    {
        Display.needed("the main window needs a display");

        // Installed first, so the false branch's UI default is not overwritten by the install.
        TrainControlUI.installLookAndFeel();

        barWas = prefs().get(TrainControlUI.MENUBAR_SETTING_PREF, null);
        onTopWas = prefs().get(TrainControlUI.ONTOP_SETTING_PREF, null);

        sandbox = support.LayoutSandbox.open();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (sandbox != null) sandbox.close();
        }
        finally
        {
            putBack(TrainControlUI.MENUBAR_SETTING_PREF, barWas);
            putBack(TrainControlUI.ONTOP_SETTING_PREF, onTopWas);
        }
    }

    /**
     * Stored off before the window opens, the menus in the title bar still show, Window Always on Top shows in place of
     * Toggle Menu Bar, and the window has the size and minimum size it has with the preference on: it lost the bar's
     * height, cutting off its bottom.
     *
     * MUTATION: drop `displayMenuBar`'s first line (the return while Toggle Menu Bar is hidden), and this fails.
     */
    @Test(timeOut = 180000)
    public void testAStoredOffNeitherHidesTheMenusNorShrinksTheWindow() throws Exception
    {
        Seen on = started(true, true, null);
        Seen off = started(false, true, null);

        assertTrue(on.bar && off.bar, "a stored off hid the menus in the title bar: on " + on + "; off " + off);
        assertFalse(on.toggleMenuBar || off.toggleMenuBar, "Toggle Menu Bar shows with the menus in the title bar: on "
            + on + "; off " + off);
        assertTrue(on.alwaysOnTop && off.alwaysOnTop, "Window Always on Top is not in Toggle Menu Bar's place: on " + on
            + "; off " + off);

        assertEquals(off.size, on.size, "stored off, the window is not the size it is with the menu bar on");
        assertEquals(off.minimum, on.minimum, "stored off, the window's minimum size is not the one it has with the"
            + " menu bar on");
    }

    /**
     * Control+M changes neither the menus in the title bar, nor the window's size, nor the stored preference.
     *
     * MUTATION: have the Control+M row click Toggle Menu Bar whether it shows or not, and this fails.
     */
    @Test(timeOut = 180000)
    public void testControlMLeavesTheMenusInTheTitleBarAlone() throws Exception
    {
        started(true, true, ui ->
        {
            Seen before = seen(ui);

            Keys.press((Component) Reflect.get(TrainControlUI.class, ui, "KeyboardTab"), KeyEvent.VK_M,
                InputEvent.CTRL_DOWN_MASK);

            Seen after = seen(ui);

            assertEquals(after.toString(), before.toString(), "Control+M changed the window");
            assertEquals(prefs().get(TrainControlUI.MENUBAR_SETTING_PREF, null), "true",
                "Control+M stored the menu bar preference");
        });
    }

    /**
     * The Keyboard tab's Window Always on Top and the View menu's are one switch: unticking either unticks the other,
     * stores it and takes the window off the top; ticking either puts all three back.
     *
     * MUTATION: give the Keyboard tab's checkbox a model of its own, and this fails.
     */
    @Test(timeOut = 180000)
    public void testBothAlwaysOnTopCheckboxesAreOneSwitch() throws Exception
    {
        String onTopBefore = prefs().get(TrainControlUI.ONTOP_SETTING_PREF, null);

        prefs().putBoolean(TrainControlUI.ONTOP_SETTING_PREF, true);

        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            // Not displayed: `display()` raises the window topmost and hands the setting back 400 ms later, which
            // would race the clicks below.
            model = support.TestStation.simulatedInDebug();
            ui = Components.onTheEventThread(TrainControlUI::new);
            ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            Components.settle();

            if (ui.getRootPane().getWindowDecorationStyle() == JRootPane.NONE)
            {
                throw new SkipException("FlatLaf leaves the native title bar on this platform");
            }

            final TrainControlUI window = ui;

            AbstractButton tab = (AbstractButton) Reflect.get(TrainControlUI.class, ui, "alwaysOnTopCheckbox");
            AbstractButton menu = (AbstractButton) Reflect.get(TrainControlUI.class, ui, "windowAlwaysOnTopMenuItem");

            for (AbstractButton clicked : new AbstractButton[] {tab, menu})
            {
                for (boolean on : new boolean[] {false, true})
                {
                    String which = (clicked == tab ? "the Keyboard tab's" : "the View menu's") + " Always on Top, "
                        + (on ? "ticked" : "unticked");

                    Components.onTheEventThread(() -> { clicked.doClick(0); return null; });

                    assertEquals(Components.onTheEventThread(tab::isSelected), (Boolean) on, which + ": the tab's tick");
                    assertEquals(Components.onTheEventThread(menu::isSelected), (Boolean) on, which + ": the menu's tick");
                    assertEquals(prefs().getBoolean(TrainControlUI.ONTOP_SETTING_PREF, !on), on, which + ": stored");
                    assertEquals(Components.onTheEventThread(window::isAlwaysOnTop), (Boolean) on, which + ": the window");
                }
            }
        }
        finally
        {
            // Off the top and the setting back before the next claim's window: it would sit above the operator's work.
            putBack(TrainControlUI.ONTOP_SETTING_PREF, onTopBefore);

            try
            {
                final TrainControlUI window = ui;

                if (window != null) Components.onTheEventThread(() -> { window.setAlwaysOnTop(false); return null; });
            }
            finally
            {
                support.Teardown.close(null, model, ui);
            }
        }
    }

    /**
     * Where the menus are not in the title bar, Toggle Menu Bar shows in its place, unticked when stored off, and the
     * menus are hidden; Control+M shows them again and stores it, as before.  The menus are taken out of the title bar
     * through FlatLaf's own UI default.
     *
     * MUTATION: have `TitlePane.holdsTheMenus` answer true, and this fails.
     */
    @Test(timeOut = 180000)
    public void testOutsideTheTitleBarToggleMenuBarStillHidesTheMenus() throws Exception
    {
        started(false, false, ui ->
        {
            AbstractButton checkbox = (AbstractButton) Reflect.get(TrainControlUI.class, ui, "toggleMenuBar");

            Seen off = seen(ui);

            assertFalse(off.bar, "stored off, the menus outside the title bar still show: " + off);
            assertTrue(off.toggleMenuBar, "Toggle Menu Bar is hidden though the menus are outside the title bar: "
                + off);
            assertFalse(off.alwaysOnTop, "Window Always on Top shows though the menus are outside the title bar: "
                + off);
            assertFalse(Components.onTheEventThread(checkbox::isSelected), "Toggle Menu Bar is ticked, stored off");

            Keys.press((Component) Reflect.get(TrainControlUI.class, ui, "KeyboardTab"), KeyEvent.VK_M,
                InputEvent.CTRL_DOWN_MASK);

            Seen on = seen(ui);

            assertTrue(on.bar, "Control+M did not show the menus outside the title bar: " + on);
            assertTrue(on.size.height > off.size.height, "the window did not grow by the menu bar: off " + off
                + "; on " + on);
            assertEquals(prefs().get(TrainControlUI.MENUBAR_SETTING_PREF, null), "true",
                "Control+M did not store the menu bar shown");
        });
    }

    /**
     * Starts the application with the menu bar preference stored as given, the menus in the title bar or taken out
     * of it, runs `then` on its window, and returns what the window showed last.
     */
    private static Seen started(boolean barOn, boolean embedded, Then then) throws Exception
    {
        Object embeddedWas = UIManager.get(EMBEDDED);

        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            prefs().putBoolean(TrainControlUI.MENUBAR_SETTING_PREF, barOn);

            if (!embedded) UIManager.put(EMBEDDED, false);

            model = support.TestStation.simulatedWithWindow();
            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "the model has no window");

            Components.settle();

            if (embedded && ui.getRootPane().getWindowDecorationStyle() == JRootPane.NONE)
            {
                throw new SkipException("FlatLaf leaves the native title bar on this platform");
            }

            if (then != null) then.on(ui);

            return seen(ui);
        }
        finally
        {
            try
            {
                support.Teardown.close(null, model, ui);
            }
            finally
            {
                UIManager.put(EMBEDDED, embeddedWas);
            }
        }
    }

    private static Seen seen(final TrainControlUI ui)
    {
        return Components.onTheEventThread(() ->
        {
            Seen seen = new Seen();

            seen.bar = ui.getJMenuBar().isVisible();
            seen.toggleMenuBar = inTheWindow(ui, "toggleMenuBar");
            seen.alwaysOnTop = inTheWindow(ui, "alwaysOnTopCheckbox");
            seen.size = ui.getSize();
            seen.minimum = ui.getMinimumSize();

            return seen;
        });
    }

    /** Whether the Keyboard tab shows this checkbox: in the window and not hidden. */
    private static boolean inTheWindow(TrainControlUI ui, String field) throws Exception
    {
        Component c = (Component) Reflect.get(TrainControlUI.class, ui, field);

        return c.isVisible() && javax.swing.SwingUtilities.isDescendingFrom(c, ui);
    }

    private static void putBack(String preference, String was)
    {
        if (was == null) prefs().remove(preference);
        else prefs().put(preference, was);
    }

    private static Preferences prefs()
    {
        return TrainControlUI.getPrefs();
    }
}
