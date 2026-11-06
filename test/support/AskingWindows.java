package support;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.testng.Assert;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * For the claims that a question hangs off the window that asked it (OB-324): opening a page in its own window as the
 * Layout tab's buttons do, finding its squares and menu items, and reading a question against what it should be.
 */
public final class AskingWindows
{
    private static final long PATIENCE_MS = 20000;

    /** For {@link #assertAsked}: a question whose message is no text to read, an array of parts. */
    public static final String ANY_MESSAGE = "(any)";

    /** The Yes and No of the program's own questions. */
    public static final String YES = String.valueOf(TrainControlUI.YES_NO_OPTS[0]);
    public static final String NO = String.valueOf(TrainControlUI.YES_NO_OPTS[1]);

    /** A simulated railway in its window, over a sandbox: what a class's claims stand on. */
    public static final class Railway
    {
        public final LayoutSandbox sandbox;
        public final MarklinControlStation model;
        public final TrainControlUI ui;

        private Railway(LayoutSandbox sandbox, MarklinControlStation model)
        {
            this.sandbox = sandbox;
            this.model = model;
            this.ui = (TrainControlUI) model.getGUI();

            Assert.assertNotNull(this.ui, "there is no window");
        }
    }

    /**
     * Opens the window on a sandbox's copy of a railway, with no station to answer.  Skips the claim on a machine with no
     * display.
     *
     * @param scenario a folder under `test/layouts`
     * @param inTheSandbox what to change in the copy first, given its folder, or null
     * @return the railway
     */
    public static Railway openRailway(String scenario, java.util.function.Consumer<java.io.File> inTheSandbox) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("these claims need a display");

        LayoutSandbox opened = null;

        try
        {
            opened = LayoutSandbox.open(Scenario.folderFor(scenario));

            if (inTheSandbox != null) inTheSandbox.accept(opened.getFolder());

            MarklinControlStation station = TestStation.simulatedWithWindowInDebug();

            station.setNetworkCommState(false);

            return new Railway(opened, station);
        }
        catch (Exception | Error failed)
        {
            // Closing puts the layout preference back
            if (opened != null) opened.close();

            throw failed;
        }
    }

    /** The station a setup calls by this name. */
    public static TileKey stationNamed(org.traincontrol.automationui.AutonomySession session, String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new AssertionError("this railway has no " + name);
    }

    /** Right-clicks the square in this window, as the mouse does; the menu that opens. */
    public static JPopupMenu menuOn(TrainControlUI ui, Window window, TileKey square)
    {
        JPopupMenu menu = Mouse.rightClick(awaitTileIn(ui, window, square), PATIENCE_MS);

        Assert.assertNotNull(menu, "no menu opened on " + square);

        return menu;
    }

    /** Chooses an item that is not grayed, answering by the rule, until this many questions are asked; what they were. */
    public static List<Answering.Question> choose(JMenuItem item, java.util.function.Function<String, Answering.Answer> rule,
        int questions)
    {
        Assert.assertTrue(item.isEnabled(), "precondition: \"" + item.getText() + "\" is grayed");

        try (Answering answering = Answering.start(rule))
        {
            Mouse.choose(item);

            Assert.assertTrue(answering.awaitAsked(questions, PATIENCE_MS), "\"" + item.getText() + "\" asked " + answering.asked());

            Components.settle();

            return answering.questions();
        }
    }

    /** The dialog on the screen, once there is one. */
    public static JDialog awaitADialog()
    {
        final JDialog[] question = new JDialog[1];

        Assert.assertTrue(Components.waitFor(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()) question[0] = (JDialog) window;
            }

            return question[0] != null;
        }, PATIENCE_MS), "nothing was asked");

        return question[0];
    }

    /** Closes the window under the question it owns, as its close box does, and sees the question go with it. */
    public static void assertClosingTakesTheQuestion(final Window window)
    {
        final JDialog question = awaitADialog();

        Assert.assertSame(question.getOwner(), window, "the question is not that window's");

        SwingUtilities.invokeLater(() -> window.setVisible(false));

        Assert.assertTrue(Components.waitFor(() -> !question.isShowing(), PATIENCE_MS),
            "the question was left standing after its window closed");

        Components.settle();
    }

    /** Does this to each component of this kind in a dialog on the screen, as a person fills a form in. */
    public static <T extends Component> void inTheQuestion(Class<T> kind, java.util.function.Predicate<? super T> which,
        java.util.function.Consumer<T> what)
    {
        Components.onTheEventThread(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (!(window instanceof JDialog) || !window.isShowing()) continue;

                T found = Components.find(window, kind, which);

                if (found != null) what.accept(found);
            }

            return null;
        });
    }

    /** What a claim does inside {@link #withOnlyTheseLocomotives}. */
    public interface Door
    {
        void run() throws Exception;
    }

    private AskingWindows()
    {
    }

    /**
     * Presses the Layout tab's Small button and waits for the page's own window.
     *
     * @param ui the main window
     * @return the window, showing
     */
    public static LayoutPopupUI openThePage(TrainControlUI ui) throws Exception
    {
        final Set<Window> before = new HashSet<>(Arrays.asList(Window.getWindows()));

        final Field field = TrainControlUI.class.getDeclaredField("smallButton");

        field.setAccessible(true);

        Components.press((AbstractButton) field.get(ui));

        final LayoutPopupUI[] opened = new LayoutPopupUI[1];

        Assert.assertTrue(Components.waitFor(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (window instanceof LayoutPopupUI && window.isShowing() && !before.contains(window))
                {
                    opened[0] = (LayoutPopupUI) window;

                    return true;
                }
            }

            return false;
        }, PATIENCE_MS), "the Small button opened no window");

        Components.settle();

        return opened[0];
    }

    /** Picks the square's page in the Layout tab's list, as the operator does, and waits until it is drawn. */
    public static void showThePageOf(TrainControlUI ui, TileKey square) throws Exception
    {
        final Field field = TrainControlUI.class.getDeclaredField("LayoutList");

        field.setAccessible(true);

        Components.select((JComboBox<?>) field.get(ui));

        Components.select((JComboBox<?>) field.get(ui), square.getPage());

        Assert.assertTrue(Components.waitFor(() -> tileIn(ui, ui, square) != null, PATIENCE_MS),
            "precondition: the main window did not draw " + square);

        Components.settle();
    }

    /**
     * Runs a door over a database holding only these locomotives, in memory, and puts the rest back: an empty or a
     * one-train database is a state a door asks about, and no other test shares this model.
     */
    @SuppressWarnings("unchecked")
    public static void withOnlyTheseLocomotives(MarklinControlStation model, List<String> keep, Door door) throws Exception
    {
        final Field field = MarklinControlStation.class.getDeclaredField("locDB");

        field.setAccessible(true);

        final RemoteDeviceCollection<MarklinLocomotive, String> db =
            (RemoteDeviceCollection<MarklinLocomotive, String>) field.get(model);

        final List<MarklinLocomotive> all = db.getItems();

        for (MarklinLocomotive each : all)
        {
            if (!keep.contains(each.getName())) db.delete(each.getName());
        }

        try
        {
            door.run();
        }
        finally
        {
            for (MarklinLocomotive each : all)
            {
                if (!db.hasName(each.getName())) db.add(each, each.getName(), each.getUID());
            }
        }
    }

    /**
     * A key pressed on this component, heard by its own listeners, posted so a question it asks cannot hold this thread.
     * Dispatched instead, the focus manager would hand it to whichever window has the focus now.
     */
    public static void pressKey(final Component on, final int key, final int modifiers)
    {
        hearKey(on, KeyEvent.KEY_PRESSED, key, modifiers);
    }

    /** {@link #pressKey} for a component that hears a key as it is let go. */
    public static void releaseKey(final Component on, final int key, final int modifiers)
    {
        hearKey(on, KeyEvent.KEY_RELEASED, key, modifiers);
    }

    private static void hearKey(final Component on, final int id, final int key, final int modifiers)
    {
        SwingUtilities.invokeLater(() ->
        {
            final KeyEvent event = new KeyEvent(on, id, System.currentTimeMillis(), modifiers, key, KeyEvent.CHAR_UNDEFINED);

            for (KeyListener each : on.getKeyListeners())
            {
                if (id == KeyEvent.KEY_PRESSED) each.keyPressed(event);
                else each.keyReleased(event);
            }
        });

        Components.settle();
    }

    /** Closes a page's window as its close box does, and lets it go. */
    public static void closeThePage(final LayoutPopupUI page) throws Exception
    {
        if (page == null) return;

        Components.onTheEventThread(() ->
        {
            page.setVisible(false);
            page.dispose();

            return null;
        });
    }

    /**
     * A square as this window shows it.
     *
     * @param ui the main window
     * @param window the window it is drawn in
     * @param square the square
     * @return its label, or null while it is not on screen there
     */
    public static LayoutLabel tileIn(TrainControlUI ui, Window window, TileKey square)
    {
        for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(square))
        {
            if (label.getComponent() != null && label.isShowing()
                && SwingUtilities.getWindowAncestor(label) == window) return label;
        }

        return null;
    }

    /** {@link #tileIn}, waited for: a new window draws its squares after it opens. */
    public static LayoutLabel awaitTileIn(TrainControlUI ui, Window window, TileKey square)
    {
        Assert.assertTrue(Components.waitFor(() -> tileIn(ui, window, square) != null, PATIENCE_MS),
            "precondition: " + square + " is not drawn in " + window.getClass().getSimpleName());

        return tileIn(ui, window, square);
    }

    /** Takes the window to the taskbar, or back out of it, and waits until it has gone there. */
    public static void minimize(final Frame window, final boolean minimized) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            window.setExtendedState(minimized ? window.getExtendedState() | Frame.ICONIFIED
                : window.getExtendedState() & ~Frame.ICONIFIED);

            return null;
        });

        Assert.assertTrue(Components.waitFor(() -> ((window.getExtendedState() & Frame.ICONIFIED) != 0) == minimized,
            PATIENCE_MS), "precondition: the window did not go where it was told");

        Components.settle();
    }

    /**
     * An item of a menu, its submenus searched too, by what it says.
     *
     * @param menu the open menu
     * @param text what the item says
     * @return the item
     */
    public static JMenuItem itemOf(JPopupMenu menu, String text)
    {
        final JMenuItem found = find(menu, text);

        Assert.assertNotNull(found, "precondition: the menu has no \"" + text + "\"");

        return found;
    }

    private static JMenuItem find(MenuElement in, String text)
    {
        for (MenuElement each : in.getSubElements())
        {
            if (each instanceof JMenuItem && text.equals(((JMenuItem) each).getText())) return (JMenuItem) each;

            final JMenuItem deeper = find(each, text);

            if (deeper != null) return deeper;
        }

        return null;
    }

    /** Whether the menu holds an item that says this. */
    public static boolean hasItem(JPopupMenu menu, String text)
    {
        return find(menu, text) != null;
    }

    /**
     * Reads a question against what it must be: over this window, which is on screen, saying and offering what it always
     * has.
     *
     * @param question as it stood
     * @param over the window that asked
     * @param title its title
     * @param message what it says
     * @param type its `JOptionPane` message type
     * @param startsOn the button it starts on, or null for a plain message
     * @param buttons its buttons
     */
    public static void assertAsked(Answering.Question question, Window over, String title, String message, int type,
        String startsOn, String... buttons)
    {
        Assert.assertSame(question.owner, over, "\"" + message + "\" hangs off "
            + (question.owner == null ? "nothing" : question.owner.getClass().getSimpleName()) + ", not the window that"
            + " asked, " + over.getClass().getSimpleName());

        Assert.assertTrue(question.ownerShowing, "\"" + message + "\" hangs off a window that is not on screen");

        if (message != ANY_MESSAGE) Assert.assertEquals(question.message, message, "the question's words changed");
        Assert.assertEquals(question.title, title, "the title of \"" + message + "\" changed");
        Assert.assertEquals(question.type, type, "the message type of \"" + message + "\" changed");
        Assert.assertEquals(question.buttons, Arrays.asList(buttons), "the buttons of \"" + message + "\" changed");
        Assert.assertEquals(question.startsOn, startsOn, "\"" + message + "\" starts on another button");
    }

    /** A plain message, as `showMessageDialog` makes it, over this window. */
    public static void assertSaid(Answering.Question question, Window over, String message)
    {
        assertAsked(question, over, UIManager.getString("OptionPane.messageDialogTitle"), message,
            JOptionPane.INFORMATION_MESSAGE, null, UIManager.getString("OptionPane.okButtonText"));
    }

    /** The only question asked so far, once it has been. */
    public static Answering.Question onlyQuestion(Answering answering, String notAsked)
    {
        Assert.assertTrue(answering.awaitAsked(1, PATIENCE_MS), notAsked);

        final List<Answering.Question> questions = answering.questions();

        Assert.assertEquals(questions.size(), 1, "more than one question: " + answering.asked());

        return questions.get(0);
    }

    /**
     * Waits until the question answered has gone: the answer is posted, and a question still up when the next claim starts
     * answering would be taken for that claim's.
     */
    public static void awaitAnswered()
    {
        Assert.assertTrue(Components.waitFor(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()
                    && Components.find(window, JOptionPane.class, each -> true) != null) return false;
            }

            return true;
        }, PATIENCE_MS), "a question was left standing");
    }

    /** Nothing left open for the next claim: a claim that fails with a question up leaves it modal on the screen. */
    public static void closeEveryDialog() throws Exception
    {
        Mouse.closeMenus();

        Components.onTheEventThread(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()) window.dispose();
            }

            return null;
        });

        Components.settle();
    }
}
