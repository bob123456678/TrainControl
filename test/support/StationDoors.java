package support;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.testng.Assert;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;

/**
 * Drives the Station Settings and train windows as the user does (FR-111): by their controls' names, on the event thread,
 * with the program's own questions answered by {@link Answering}.
 */
public final class StationDoors
{
    private StationDoors()
    {
    }

    /** Opens Station Settings on a square, and waits for the matrix's answer. */
    public static StationSettingsWindow open(final AutonomyEditorPanel panel, final TileKey square)
    {
        StationSettingsWindow window = Components.onTheEventThread(() -> panel.openStationSettings(square));

        current(window);

        return window;
    }

    /** Opens Station Settings on a square's Train tab, as Change Train at... does. */
    public static StationSettingsWindow openTrain(final AutonomyEditorPanel panel, final TileKey square)
    {
        StationSettingsWindow window = Components.onTheEventThread(() -> panel.openTrainTab(square));

        Components.settle();

        return window;
    }

    /** Whether Station Settings shows its tabs with the Train tab chosen (FR-195). */
    public static boolean showsTheTrainTab(final java.awt.Window window)
    {
        return Components.onTheEventThread(() ->
        {
            javax.swing.JTabbedPane tabs = Components.named(window, "station.tabs", javax.swing.JTabbedPane.class);

            return tabs != null && tabs.isShowing() && Components.named(tabs.getSelectedComponent(), "train.form",
                java.awt.Container.class) != null;
        });
    }

    /** Waits until the rebuild a change posted has reached the window and its matrix answers the latest question. */
    public static void current(StationSettingsWindow window)
    {
        Components.settle();

        Assert.assertTrue(Components.waitFor(window::matrixIsCurrent, 30000), "the matrix never answered");

        Components.settle();
    }

    /** A control of a window by its name. */
    public static <T extends Component> T part(Component window, String name, Class<T> type)
    {
        T found = Components.onTheEventThread(() -> Components.named(window, name, type));

        Assert.assertNotNull(found, "the window has no " + name);

        return found;
    }

    /** Clicks a named button, box or radio; its door's questions are answered by the rule; what was asked comes back. */
    public static List<String> press(Component window, String name, Function<String, Answering.Answer> rule) throws Exception
    {
        return pressAndAnswer(part(window, name, AbstractButton.class), rule);
    }

    /** Clicks a named control whose door asks nothing. */
    public static void press(Component window, String name) throws Exception
    {
        press(window, name, message -> null);
    }

    /** Clicks this control as a click does, answers its questions by the rule, and returns once the click's work is done. */
    public static List<String> pressAndAnswer(final AbstractButton item, Function<String, Answering.Answer> rule)
        throws Exception
    {
        final CountDownLatch done = new CountDownLatch(1);

        try (Answering answering = Answering.start(rule))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    item.doClick();
                }
                finally
                {
                    done.countDown();
                }
            });

            Assert.assertTrue(done.await(30, TimeUnit.SECONDS), "\"" + item.getName() + "\" did not finish; it asked: "
                + answering.asked());

            Components.settle();

            return answering.asked();
        }
    }

    /** On the event thread: a control's own listeners, as a click the instant before it grayed runs them. */
    public static void fireThoughGrayed(AbstractButton control)
    {
        // Listeners read the box's state, which a click turns over
        control.setSelected(!control.isSelected());

        for (ActionListener listener : control.getActionListeners())
        {
            listener.actionPerformed(new ActionEvent(control, ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    /**
     * That from a test's thread: a run may begin in the second before the window notices, and the door must refuse by
     * itself.  Its message is answered, and comes back.
     */
    public static List<String> pressThoughGrayed(final AbstractButton control) throws Exception
    {
        final CountDownLatch done = new CountDownLatch(1);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    fireThoughGrayed(control);
                }
                finally
                {
                    done.countDown();
                }
            });

            Assert.assertTrue(done.await(30, TimeUnit.SECONDS), "\"" + control.getName() + "\" did not finish; it asked: "
                + answering.asked());

            Components.settle();

            return answering.asked();
        }
    }

    /** Types into a named field and presses Enter. */
    public static void typeAndEnter(Component window, String name, final String text) throws Exception
    {
        final JTextField field = part(window, name, JTextField.class);

        Components.onTheEventThread(() ->
        {
            field.setText(text);
            field.postActionEvent();

            return null;
        });

        Components.settle();
    }

    /** Picks an item of a named list. */
    public static void pick(Component window, String name, Object item) throws Exception
    {
        Components.select(part(window, name, JComboBox.class), item);

        Components.settle();
    }

    /** Puts a train on a square through the editor's train window, picked from its list, and closes the window. */
    public static void placeFromTheList(AutonomyEditorPanel panel, TileKey square, String train) throws Exception
    {
        StationSettingsWindow window = openTrain(panel, square);

        pick(window, "train.train", train);

        close(window);
    }

    /** The text of a named label or field. */
    public static String text(Component window, String name)
    {
        return Components.onTheEventThread(() ->
        {
            JLabel label = Components.named(window, name, JLabel.class);

            return label != null ? label.getText() : Components.named(window, name, JTextField.class).getText();
        });
    }

    /** A named label's words, past the wrapping. */
    public static String said(Component window, String name)
    {
        return text(window, name).replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    /** Picks an item of a named list whose door asks, answering by the rule; what was asked. */
    public static List<Answering.Question> pickAndAnswer(Component window, String name, Object item,
        Function<String, Answering.Answer> rule) throws Exception
    {
        try (Answering answering = Answering.start(rule))
        {
            Components.select(part(window, name, JComboBox.class), item);

            Components.settle();

            return answering.questions();
        }
    }

    /** The guard signal window (Exit or Entry) showing, or null. */
    public static javax.swing.JDialog guardWindow()
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window each : java.awt.Window.getWindows())
            {
                String title = each instanceof javax.swing.JDialog ? ((javax.swing.JDialog) each).getTitle() : null;

                if (each.isShowing() && (org.traincontrol.util.I18n.t("autosetup.ui.menuPairSignal").equals(title)
                    || org.traincontrol.util.I18n.t("autosetup.ui.menuPairEntrySignal").equals(title)))
                {
                    return (javax.swing.JDialog) each;
                }
            }

            return null;
        });
    }

    /** The one showing window of this kind, or null. */
    public static <T extends java.awt.Window> T showing(Class<T> kind)
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window each : java.awt.Window.getWindows())
            {
                if (kind.isInstance(each) && each.isShowing()) return kind.cast(each);
            }

            return null;
        });
    }

    /** Whether the square's window offers this control: it opens there, and the control takes clicks. */
    public static boolean offeredInTheWindow(final AutonomyEditorPanel panel, final TileKey tile, final String control)
    {
        return Components.onTheEventThread(() ->
        {
            if (!panel.offersStationSettings(tile)) return false;

            StationSettingsWindow window = panel.openStationSettings(tile);

            boolean live = part(window, control, Component.class).isEnabled();

            panel.closeStationWindows();

            return live;
        });
    }

    /** Whether a named control takes clicks. */
    public static boolean live(Component window, String name)
    {
        return Components.onTheEventThread(() -> part(window, name, Component.class).isEnabled());
    }

    /** Whether a named box or radio is on. */
    public static boolean on(Component window, String name)
    {
        return Components.onTheEventThread(() -> part(window, name, AbstractButton.class).isSelected());
    }

    /** Steps a list with an arrow, down or, at its last answer, up, as the user does (it only shows); what it showed before. */
    public static Object step(JComboBox<?> list)
    {
        Object was = Components.onTheEventThread(list::getSelectedItem);

        Keys.press(list, Components.onTheEventThread(() -> list.getSelectedIndex() < list.getItemCount() - 1)
            ? java.awt.event.KeyEvent.VK_DOWN : java.awt.event.KeyEvent.VK_UP);

        return was;
    }

    /** A control loses the keyboard, as AWT tells it: for now (another window clicked) or for good, to `next` (or none). */
    public static void loseKeyboard(Component focused, boolean forNow, Component next)
    {
        Components.onTheEventThread(() ->
        {
            for (java.awt.event.FocusListener each : focused.getFocusListeners())
            {
                each.focusLost(new java.awt.event.FocusEvent(focused, java.awt.event.FocusEvent.FOCUS_LOST, forNow, next));
            }

            return null;
        });
        Components.settle();
    }

    /** Another window is clicked, as AWT tells it: the control with the keyboard loses it for now, then the window its focus. */
    public static void toAnotherWindow(java.awt.Window window, Component focused)
    {
        loseKeyboard(focused, true, null);

        Components.onTheEventThread(() ->
        {
            for (java.awt.event.WindowFocusListener each : window.getWindowFocusListeners())
            {
                each.windowLostFocus(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_LOST_FOCUS));
            }

            return null;
        });
        Components.settle();
    }

    /** Closes a window with its Close button. */
    public static void close(Component window) throws Exception
    {
        press(window, "station.close");
    }

    /** Selects this locomotive in the list of the question on screen and presses OK (`Answering` does not type into lists). */
    public static Answering.Answer choosing(final String... names)
    {
        final List<String> wanted = java.util.Arrays.asList(names);

        return choosingRows(wanted::contains);
    }

    /** Selects every row of the list of the question on screen that `row` takes, and presses OK. */
    public static Answering.Answer choosingRows(final java.util.function.Predicate<String> row)
    {
        Components.onTheEventThread(() ->
        {
            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (!w.isShowing()) continue;

                JOptionPane pane = Components.find(w, JOptionPane.class, null);

                if (pane == null) continue;

                @SuppressWarnings("unchecked")
                javax.swing.JList<String> list = Components.find(pane, javax.swing.JList.class, null);

                if (list == null) continue;

                list.clearSelection();

                for (int i = 0; i < list.getModel().getSize(); i++)
                {
                    if (row.test(String.valueOf(list.getModel().getElementAt(i)))) list.addSelectionInterval(i, i);
                }
            }

            return null;
        });

        return Answering.pressAt(0);
    }

    /**
     * The track diagram's right-click menu on a square, built as a right-click builds it: the paths gathered off the
     * event thread, the menu made on it.  Call it off the event thread.
     */
    public static javax.swing.JPopupMenu diagramMenu(final org.traincontrol.gui.TrainControlUI ui, final TileKey square)
        throws Exception
    {
        return diagramMenu(ui, square, Components.onTheEventThread(() -> ui.getAutonomyPointForTile(square)));
    }

    /** The same, for the railway's Point the menu is about. */
    public static javax.swing.JPopupMenu diagramMenu(final org.traincontrol.gui.TrainControlUI ui, final TileKey square,
        org.traincontrol.automation.Point standing) throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final java.lang.reflect.Method gather = menuClass.getDeclaredMethod("gatherPathOptions",
            org.traincontrol.gui.TrainControlUI.class, org.traincontrol.automation.Point.class);

        gather.setAccessible(true);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(
            org.traincontrol.gui.TrainControlUI.class, TileKey.class, TileKey.class,
            Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

        make.setAccessible(true);

        final Object options = gather.invoke(null, ui, standing);

        return Components.onTheEventThread(() -> (javax.swing.JPopupMenu) make.newInstance(ui, square, square, options));
    }
}
