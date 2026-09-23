package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Mass Assign Train Lengths' prompt has the keyboard in the real editor window - on the first train, after a length
 * is refused, and on the next train (MT-474).
 *
 * Adam, 2026-09-23, on MT-474: *"Works, make sure the text field is focused by default."*
 *
 * **In the real window, from the real menu item.**  `core.testMassAssignLengths` asks the same of a panel with no
 * window round it, which is not where he met it: here the autonomy editor is on screen with the main window behind
 * it, and the walk is started from its own Bulk Tools item.
 *
 * A desktop that will not give the editor the keyboard at all cannot show where it goes inside a prompt, and says
 * so as a skip.
 *
 * On the frozen railway (OB-111).  The two trains measured here are given back their lengths afterwards.
 *
 * @author Adam
 */
public class testTheLengthPromptHasTheKeyboard
{
    private static final String PAGE = "1 - Main";
    private static final String TRAINS = "autosetup.ui.menuMassAssignTrainLengths";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;

    private static final List<Locomotive> unmeasured = new ArrayList<>();
    private static final List<Integer> theirLengths = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor and its prompts need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // THE WHOLE APPLICATION, as testNonAtomicRoutesNeedTheirLengths starts it: only its own start-up puts the
        // setup's trains on the running railway, and the walk is about those.
        model = init(null, true, true, false, true);

        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "precondition: there is no main window");

        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null || !model.hasAutoLayout())
        {
            throw new SkipException("no autonomy configuration, so there is no walk to open");
        }

        // TWO TRAINS WITH NO LENGTH, the walk's subject - standing on the page on screen, so each is outlined and
        // scrolled to before it is asked about, as it is when the operator is looking at his trains.
        java.util.Set<String> onThisPage = new java.util.HashSet<>();

        for (java.util.Map.Entry<org.traincontrol.automationui.TileGraph.TileKey, String> placed
            : session.placementsAutonomyWillWrite().entrySet())
        {
            if (PAGE.equals(placed.getKey().getPage())) onThisPage.add(placed.getValue());
        }

        List<Locomotive> running = new ArrayList<>(model.getAutoLayout().getLocomotivesToRun());

        running.removeIf(l -> l == null || !onThisPage.contains(l.getName()));
        running.sort((a, b) -> a.getName().compareTo(b.getName()));

        if (running.size() < 2) throw new SkipException("the frozen railway places fewer than two trains on " + PAGE);

        for (Locomotive loc : running.subList(0, 2))
        {
            unmeasured.add(loc);
            theirLengths.add(loc.getTrainLength());
            loc.setTrainLength(0);
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
            built[0].setVisible(true);
            built[0].toFront();
        });

        editor = built[0];
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            closeEveryPrompt();

            for (int i = 0; i < unmeasured.size(); i++) unmeasured.get(i).setTrainLength(theirLengths.get(i));

            if (editor != null)
            {
                final LayoutEditor closing = editor;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The first prompt, the prompt after a refused length, and the next train's prompt each have the number field
     * focused.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testEveryPromptOfTheWalkHasTheFieldFocused() throws Exception
    {
        awaitTheEditorFocused();

        final javax.swing.JMenuItem item = bulkItem(TRAINS);

        assertNotNull(item, "precondition: Bulk Tools has no Mass Assign Train Lengths item");
        assertTrue(item.isEnabled(), "precondition: the item is greyed with two trains made unmeasured");

        SwingUtilities.invokeLater(item::doClick);

        // THE FIRST TRAIN.
        javax.swing.JDialog first = awaitPrompt(null);

        javax.swing.JTextField field = focusedField(first, "the first train's prompt");

        // A REFUSED LENGTH: 0 is not a train length, and the same prompt comes back after the sentence saying so.
        type(field, "0");

        javax.swing.JDialog refusal = awaitMessage(first);

        closeMessage(refusal);

        javax.swing.JDialog again = awaitPrompt(first);

        field = focusedField(again, "the prompt asked again after 0 was refused");

        // THE NEXT TRAIN.
        type(field, "3");

        javax.swing.JDialog second = awaitPrompt(again);

        focusedField(second, "the second train's prompt, after the first was answered");

        answer(second, I18n.t("ui.cancel"));
    }

    /**
     * And started the way a person starts it: the real right-click menu, Bulk Tools open, and the item released on.
     *
     * A release on a menu item is handled by Swing's own menu code, which takes the whole menu down and only then runs
     * the item's action - `doClick`, above, runs the action with nothing taken down, and that is the one step between
     * the claim above and what Adam did.  The release is given to the item as an event rather than made with the
     * mouse, because a locked desktop - where these tests run unattended - drops synthetic mouse input.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testEveryPromptOfTheWalkHasTheFieldFocused")
    public void testThePromptOpenedFromTheRightClickMenuHasTheFieldFocused() throws Exception
    {
        awaitTheEditorFocused();

        // A SQUARE OF TRACK ON THE PAGE, whose menu ends with Bulk Tools.
        final org.traincontrol.automationui.TileGraph.TileKey[] track = new org.traincontrol.automationui.TileGraph.TileKey[1];

        SwingUtilities.invokeAndWait(() ->
        {
            LayoutDiagram page = model.getLayout(PAGE);

            for (int x = 0; x < 60 && track[0] == null; x++)
            {
                for (int y = 0; y < 60 && track[0] == null; y++)
                {
                    org.traincontrol.base.LayoutDiagramComponent c = page.getComponent(x, y);

                    if (c != null && !c.isText()) track[0] = new org.traincontrol.automationui.TileGraph.TileKey(PAGE, x, y);
                }
            }
        });

        assertNotNull(track[0], "precondition: " + PAGE + " has no square of track");

        final javax.swing.JMenuItem[] walk = new javax.swing.JMenuItem[1];

        SwingUtilities.invokeAndWait(() ->
        {
            editor.getAutonomyPanel().tileRightClicked(track[0], null, editor.getContentPane(), 40, 40);

            javax.swing.JPopupMenu shown = null;

            for (javax.swing.MenuElement element : javax.swing.MenuSelectionManager.defaultManager().getSelectedPath())
            {
                if (element instanceof javax.swing.JPopupMenu) shown = (javax.swing.JPopupMenu) element;
            }

            if (shown == null) return;

            for (java.awt.Component item : shown.getComponents())
            {
                if (!(item instanceof javax.swing.JMenu)) continue;

                javax.swing.JMenu bulk = (javax.swing.JMenu) item;

                if (!I18n.t("autosetup.ui.menuBulkTools").equals(bulk.getText())) continue;

                // OPEN, as hovering it opens it.
                javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
                    new javax.swing.MenuElement[] { shown, bulk, bulk.getPopupMenu() });

                for (java.awt.Component inner : bulk.getPopupMenu().getComponents())
                {
                    if (inner instanceof javax.swing.JMenuItem
                        && I18n.t(TRAINS).equals(((javax.swing.JMenuItem) inner).getText()))
                    {
                        walk[0] = (javax.swing.JMenuItem) inner;
                    }
                }
            }
        });

        assertNotNull(walk[0], "precondition: the square's right-click menu has no Bulk Tools holding Mass Assign Train"
            + " Lengths");

        final javax.swing.JMenuItem item = walk[0];

        // THE RELEASE, as a button let go over the middle of the item.
        SwingUtilities.invokeLater(() ->
        {
            long now = System.currentTimeMillis();
            int x = item.getWidth() / 2;
            int y = item.getHeight() / 2;

            item.dispatchEvent(new java.awt.event.MouseEvent(item, java.awt.event.MouseEvent.MOUSE_PRESSED, now,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));

            item.dispatchEvent(new java.awt.event.MouseEvent(item, java.awt.event.MouseEvent.MOUSE_RELEASED, now,
                0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        });

        javax.swing.JDialog first = awaitPrompt(null);

        focusedField(first, "the first train's prompt, opened from the right-click menu");

        answer(first, I18n.t("ui.cancel"));
    }

    // ---------------------------------------------------------------------------------------------

    private static void awaitTheEditorFocused() throws Exception
    {
        long giveUp = System.currentTimeMillis() + 5000;

        while (System.currentTimeMillis() < giveUp)
        {
            final boolean[] focused = new boolean[1];

            SwingUtilities.invokeAndWait(() -> focused[0] = editor.isFocused());

            if (focused[0]) return;

            SwingUtilities.invokeAndWait(() -> { editor.toFront(); editor.requestFocus(); });

            Thread.sleep(100);
        }

        throw new SkipException("this desktop did not give the autonomy editor the keyboard, so where it goes inside a"
            + " prompt cannot be seen");
    }

    /**
     * The prompt's number field, once it has the keyboard - waited for, because the focus arrives after the window is
     * shown.  Fails naming where the keyboard went instead.
     */
    private static javax.swing.JTextField focusedField(javax.swing.JDialog prompt, String which) throws Exception
    {
        final java.awt.Component[] owner = new java.awt.Component[1];
        final java.awt.Window[] active = new java.awt.Window[1];

        long giveUp = System.currentTimeMillis() + 3000;

        while (System.currentTimeMillis() < giveUp)
        {
            SwingUtilities.invokeAndWait(() ->
            {
                owner[0] = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                active[0] = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
            });

            if (owner[0] instanceof javax.swing.JTextField
                && SwingUtilities.getWindowAncestor(owner[0]) == prompt) return (javax.swing.JTextField) owner[0];

            Thread.sleep(50);
        }

        answer(prompt, I18n.t("ui.cancel"));

        fail("on " + which + " the keyboard is not in the number field - it is on "
            + (owner[0] == null ? "nothing" : owner[0].getClass().getSimpleName()) + " in "
            + (active[0] == null ? "no window" : describe(active[0])) + ", so what is typed goes nowhere.  Adam, MT-474:"
            + " \"make sure the text field is focused by default\"");

        return null;
    }

    /** Types into the field and presses Enter on it, as a person does. */
    private static void type(javax.swing.JTextField field, String text) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            field.setText(text);

            field.dispatchEvent(new java.awt.event.KeyEvent(field, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CHAR_UNDEFINED));
        });
    }

    private static javax.swing.JMenuItem bulkItem(String key) throws Exception
    {
        final javax.swing.JMenuItem[] found = new javax.swing.JMenuItem[1];

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu bulk = editor.getAutonomyPanel().buildBulkMenuForTest();

            for (int i = 0; i < bulk.getItemCount(); i++)
            {
                javax.swing.JMenuItem item = bulk.getItem(i);

                if (item != null && I18n.t(key).equals(item.getText())) found[0] = item;
            }
        });

        return found[0];
    }

    private static javax.swing.JDialog awaitPrompt(javax.swing.JDialog notThisOne) throws Exception
    {
        String title = I18n.t(TRAINS);
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window != notThisOne && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle()))
                {
                    return (javax.swing.JDialog) window;
                }
            }

            Thread.sleep(50);
        }

        fail("no " + title + " prompt appeared.  Showing now: " + showing());

        return null;
    }

    /** The refusal: a dialog on screen that is neither the prompt it answers nor a window of the application. */
    private static javax.swing.JDialog awaitMessage(javax.swing.JDialog prompt) throws Exception
    {
        String title = I18n.t(TRAINS);
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window != prompt && window.isShowing()
                    && !title.equals(((javax.swing.JDialog) window).getTitle()))
                {
                    return (javax.swing.JDialog) window;
                }
            }

            Thread.sleep(50);
        }

        fail("0 was typed and no refusal appeared.  Showing now: " + showing());

        return null;
    }

    private static void closeMessage(javax.swing.JDialog message) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            JOptionPane pane = findPane(message.getContentPane());

            if (pane != null) pane.setValue(JOptionPane.OK_OPTION);
            else message.dispose();
        });
    }

    /** Presses one of the prompt's buttons, the way a click does: by giving the option pane that value. */
    private static void answer(javax.swing.JDialog dialog, String button) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            JOptionPane pane = findPane(dialog.getContentPane());

            if (pane == null) return;

            for (Object option : pane.getOptions())
            {
                if (button.equals(option)) pane.setValue(option);
            }
        });
    }

    private static void closeEveryPrompt() throws Exception
    {
        for (int round = 0; round < 10; round++)
        {
            boolean any = false;

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing())
                {
                    any = true;

                    answer((javax.swing.JDialog) window, I18n.t("ui.cancel"));
                    closeMessage((javax.swing.JDialog) window);
                }
            }

            if (!any) return;

            Thread.sleep(200);
        }
    }

    private static JOptionPane findPane(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof JOptionPane) return (JOptionPane) child;

            if (child instanceof java.awt.Container)
            {
                JOptionPane found = findPane((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static String showing()
    {
        StringBuilder open = new StringBuilder();

        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (window.isShowing()) open.append(open.length() > 0 ? ", " : "").append(describe(window));
        }

        return open.length() == 0 ? "nothing" : open.toString();
    }

    private static String describe(java.awt.Window window)
    {
        String title = window instanceof javax.swing.JDialog ? ((javax.swing.JDialog) window).getTitle()
            : window instanceof java.awt.Frame ? ((java.awt.Frame) window).getTitle() : "";

        return window.getClass().getSimpleName() + " \"" + title + "\"";
    }
}
