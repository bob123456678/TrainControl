package support;

import java.awt.Component;
import java.awt.Window;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The Locomotive Settings window as a test finds it: the one on screen, its parts by name, and the doors that open it.
 * Not a test.
 */
public final class SettingsWindow
{
    private SettingsWindow()
    {
    }

    /** The Locomotive Settings window on screen, or null. */
    public static LocomotiveSettingsFrame showing()
    {
        return Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof LocomotiveSettingsFrame && w.isShowing()) return (LocomotiveSettingsFrame) w;
            }

            return null;
        });
    }

    /** Waits for the window to be on screen and finished loading its icons. */
    public static LocomotiveSettingsFrame awaited() throws Exception
    {
        LocomotiveSettingsFrame[] found = new LocomotiveSettingsFrame[1];

        if (!Components.waitFor(() -> (found[0] = showing()) != null, 8000)) return null;

        Components.settle();
        Components.settle();

        return found[0];
    }

    /** A part of the window by the name the program gave it. */
    public static <T extends Component> T part(String name, Class<T> type)
    {
        LocomotiveSettingsFrame w = showing();

        return w == null ? null : Components.named(w.getContentPane(), name, type);
    }

    /** The window's Save button. */
    public static JButton save()
    {
        return part("settings.save", JButton.class);
    }

    /** The window's Cancel button. */
    public static JButton cancel()
    {
        return part("settings.cancel", JButton.class);
    }

    /** The tab on show, by its place in the order Locomotive, Functions, Notes, Multi-Unit. */
    public static int tabShowing()
    {
        return Components.onTheEventThread(() -> showing().tabsForTest().getSelectedIndex());
    }

    /** Opens the window through the program's own door, as a menu item does. */
    public static boolean open(TrainControlUI ui, Locomotive loc, LocomotiveSettingsFrame.Tab tab, Integer function)
    {
        boolean opened = Components.onTheEventThread(() -> ui.openLocomotiveSettings(loc, tab, function));

        Components.settle();

        return opened;
    }

    /** Closes the window as Cancel does, if it is open. */
    public static void closeIfOpen()
    {
        Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof LocomotiveSettingsFrame) w.dispose();
            }

            return null;
        });

        Components.settle();
    }

    /** Presses a button of the window and lets what it does finish. */
    public static void press(AbstractButton button)
    {
        Components.press(button);
        Components.settle();
    }

    /** Selects a tab of the window, as a click on it does. */
    public static void selectTab(int index)
    {
        Components.onTheEventThread(() ->
        {
            JTabbedPane tabs = showing().tabsForTest();

            tabs.setSelectedIndex(index);

            return null;
        });
    }

    /** Whether any window of this program is showing with this title. */
    public static boolean titled(String title)
    {
        return Components.onTheEventThread(() ->
        {
            for (Window w : Window.getWindows())
            {
                if (w instanceof JFrame && w.isShowing() && title.equals(((JFrame) w).getTitle())) return true;
            }

            return false;
        });
    }

    private static Object field(TrainControlUI ui, String name) throws Exception
    {
        java.lang.reflect.Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }

    /** The button of a letter key on the keyboard page. */
    @SuppressWarnings("unchecked")
    public static javax.swing.JButton key(TrainControlUI ui, int keyCode) throws Exception
    {
        return ((java.util.Map<Integer, javax.swing.JButton>) field(ui, "buttonMapping")).get(keyCode);
    }

    /**
     * Puts a locomotive on key A and makes it the one on show, as the keyboard does, and answers the key's button.
     */
    public static javax.swing.JButton showOnKeyA(TrainControlUI ui, Locomotive loc) throws Exception
    {
        javax.swing.JButton button = key(ui, java.awt.event.KeyEvent.VK_A);

        Keys.press((Component) field(ui, "KeyboardTab"), java.awt.event.KeyEvent.VK_A);

        if (!Components.waitFor(() -> field0(ui, "currentButton") == button, 5000)) throw new AssertionError("key A was not chosen");

        Components.onTheEventThread(() ->
        {
            if (ui.getButtonLocomotive(button) != loc) ui.mapLocToCurrentButton(loc.getName());

            return null;
        });

        if (!Components.waitFor(() -> ui.getActiveLoc() == loc, 5000)) throw new AssertionError("the locomotive is not on show");

        ui.repaintLoc(true, null);

        Components.settle();

        return button;
    }

    private static Object field0(TrainControlUI ui, String name)
    {
        try
        {
            return field(ui, name);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** The function button F-number of the locomotive panel. */
    public static javax.swing.JToggleButton functionButton(TrainControlUI ui, int function) throws Exception
    {
        return (javax.swing.JToggleButton) field(ui, "F" + function);
    }

    /** Sets whether the power is on, as the station would say it. */
    public static void power(TrainControlUI ui, org.traincontrol.marklin.MarklinControlStation model, boolean on)
        throws Exception
    {
        java.lang.reflect.Field power = org.traincontrol.marklin.MarklinControlStation.class.getDeclaredField("powerState");

        power.setAccessible(true);
        power.set(model, on);

        java.lang.reflect.Field heard = TrainControlUI.class.getDeclaredField("powerHeard");

        heard.setAccessible(true);
        heard.set(ui, true);
    }

    /** Types into a text field of the window. */
    public static void type(String name, String value)
    {
        Components.onTheEventThread(() ->
        {
            part(name, javax.swing.JTextField.class).setText(value);

            return null;
        });
    }

    /** What a text field of the window says. */
    public static String text(String name)
    {
        return Components.onTheEventThread(() -> part(name, javax.swing.JTextField.class).getText());
    }

    /** A right-click on a key's button, and the menu it opens. */
    public static javax.swing.JPopupMenu rightClickKey(javax.swing.JButton key)
    {
        Components.select(key);

        return Mouse.rightClick(key, 8000);
    }

    /** Does something that says one thing, answers it with the first button, and gives what was said. */
    public static java.util.List<String> asked(Runnable doThis)
    {
        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            doThis.run();

            if (!answering.awaitAsked(1, 8000)) throw new AssertionError("nothing was said");

            Components.settle();

            return answering.asked();
        }
    }

    /** A picture of one color in a folder. */
    public static java.io.File picture(java.io.File folder, String name, java.awt.Color color) throws Exception
    {
        java.awt.image.BufferedImage drawn = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = drawn.createGraphics();

        g.setColor(color);
        g.fillRect(0, 0, 40, 40);
        g.dispose();

        java.io.File file = new java.io.File(folder, name);

        if (!javax.imageio.ImageIO.write(drawn, "png", file)) throw new AssertionError("the picture was not written");

        return file;
    }

    /** Starts editing a cell of the Functions table and gives a part of its editor. */
    public static <T extends Component> T inEditor(int row, int column, String name, Class<T> type)
    {
        javax.swing.JTable table = part("functions.table", javax.swing.JTable.class);

        Components.onTheEventThread(() ->
        {
            table.editCellAt(row, column);

            return null;
        });

        Component editor = Components.onTheEventThread(table::getEditorComponent);

        if (editor == null) throw new AssertionError("the cell did not start editing");

        return name == null ? type.cast(editor) : Components.named(editor, name, type);
    }

    /** Picks a standard icon in a row's dropdown. */
    public static void pickIcon(int row, int icon)
    {
        javax.swing.JComboBox<?> combo = inEditor(row, 1, "functions.iconCombo", javax.swing.JComboBox.class);

        Components.onTheEventThread(() ->
        {
            combo.setSelectedIndex(icon);

            return null;
        });

        Components.settle();
    }

    /** Picks a behavior in a row's cell: the editor ends itself on the pick. */
    public static void pickBehavior(int row, int behavior)
    {
        javax.swing.JComboBox<?> combo = inEditor(row, 2, null, javax.swing.JComboBox.class);

        Components.onTheEventThread(() ->
        {
            combo.setSelectedIndex(behavior);

            return null;
        });

        Components.settle();
    }

    /** Presses a row's wrench and picks this picture in the chooser it opens. */
    public static void pickAnImage(int row, java.io.File picture)
    {
        javax.swing.JButton wrench = inEditor(row, 1, "functions.wrench", javax.swing.JButton.class);

        try (Answering answering = Answering.start(message -> null).choosing(picture))
        {
            Components.press(wrench);

            Components.waitFor(() -> false, 800);
        }

        Components.settle();
    }

    /** Ticks the locomotive of this name on the Multi-Unit tab. */
    public static void tickMember(String name)
    {
        java.util.List<javax.swing.JCheckBox> boxes = Components.all(showing().getContentPane(), javax.swing.JCheckBox.class,
            b -> name.equals(b.getToolTipText()));

        if (boxes.size() != 1) throw new AssertionError("the Multi-Unit tab does not list " + name);

        Components.onTheEventThread(() ->
        {
            boxes.get(0).doClick(0);

            return null;
        });
    }

    /** Presses Reset All Customizations and answers its question with this button; gives what was asked. */
    public static java.util.List<String> resetAll(String button)
    {
        try (Answering answering = Answering.start(message -> Answering.press(button)))
        {
            Components.press(part("functions.resetAll", javax.swing.JButton.class));

            if (!answering.awaitAsked(1, 8000)) throw new AssertionError("Reset asked nothing");

            Components.settle();

            return answering.asked();
        }
    }

    /**
     * What a window opened locked must show: the strip up with this sentence, typing kept, Save gray.  The window is
     * closed again.
     */
    public static void assertLocked(String where, String strip)
    {
        javax.swing.JLabel up = part("settings.strip", javax.swing.JLabel.class);

        if (!Components.onTheEventThread(up::isVisible)) throw new AssertionError(where + " opened without the strip");

        if (!strip.equals(Components.onTheEventThread(up::getText))) throw new AssertionError(where + " has the wrong strip");

        type("settings.name", "typed while locked");

        if (!"typed while locked".equals(text("settings.name"))) throw new AssertionError(where + " did not keep the typing");

        if (Components.onTheEventThread(save()::isEnabled)) throw new AssertionError("Save is lit in the window locked from " + where);

        closeIfOpen();
    }

    /** Chooses Locomotive Settings in the locomotive database's menu for this locomotive, and lets it finish. */
    public static void openFromTheTile(TrainControlUI ui, Locomotive loc) throws Exception
    {
        final javax.swing.JPopupMenu[] tile = new javax.swing.JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(
            () -> tile[0] = new org.traincontrol.gui.locomotive.RightClickSelectorMenu(ui, null, loc));

        AbstractButton settings = Components.withText(tile[0], I18n.t("loc.ui.menuLocomotiveSettings"));

        Components.onTheEventThread(() ->
        {
            settings.doClick(0);

            return null;
        });

        Components.settle();
    }

    /** The title the window should wear for a locomotive. */
    public static String titleFor(String locomotive)
    {
        return I18n.f("loc.ui.settingsTitle", locomotive);
    }
}
