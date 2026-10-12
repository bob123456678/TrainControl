package ui;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The Is Active tick on the track diagram's right-click menu and on the autonomy editor's pauses the train standing
 * there and sets it going again, in the railway and in the setup (FR-117; Adam, 2026-10-09: "add an 'active' checkbox to
 * the right click autonomy menu (also accessible via track diagram).  Show it if there is a locomotive at that station").
 *
 * On the frozen copy of Adam's railway, with his own 75 407 DB on BottomMainB and nothing else standing.
 *
 * @author Adam
 */
public class testTheActiveTickPausesATrain
{
    private static final String HIS_TRAIN = "75 407 DB";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Locomotive train;

    private static TileKey mainA;
    private static TileKey mainB;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the diagram's menus belong to a window, and a window needs a display");
        }

        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup on this railway");

        assertNotNull(railway(), "the frozen railway built no autonomy layout");

        train = model.getLocByName(HIS_TRAIN);

        assertNotNull(train, "precondition: this data has no " + HIS_TRAIN);

        mainA = square("BottomMainA");
        mainB = square("BottomMainB");

        // NOTHING ELSE STANDING ABOUT, and his train on BottomMainB - on the railway, and in the setup the editor reads
        SwingUtilities.invokeAndWait(() ->
        {
            for (Point point : railway().getPoints())
            {
                if (point.getCurrentLocomotive() != null) railway().moveLocomotive(null, point.getName(), true);
            }

            for (String copy : session.facingsFor(mainB).keySet())
            {
                Point point = railway().getPoint(copy);

                if (point != null && railway().getLocomotiveLocation(train) == null)
                {
                    railway().moveLocomotive(HIS_TRAIN, copy, false);
                }
            }

            session.placeLocomotive(mainB, HIS_TRAIN);
        });

        assertNotNull(railway().getLocomotiveLocation(train), "precondition: " + HIS_TRAIN + " could not be stood on"
            + " BottomMainB");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (train != null) train.setAutonomyPaused(false);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** His train set going again, so a claim that fails part way leaves the next one nothing paused. */
    @AfterMethod(alwaysRun = true)
    public void setItGoing() throws Exception
    {
        train.setAutonomyPaused(false);

        session.setLocomotivePaused(HIS_TRAIN, false);

        pump();
    }

    /**
     * On the track diagram: BottomMainB, with his train on it, offers the tick, ticked; unticking it pauses the train and
     * the setup lists it; the tick then reads unticked, and ticking it sets the train going and takes it off the list.
     * BottomMainA, with nobody on it, offers no tick.
     *
     * MUTATION: leave the tick off the diagram's menu, or have it change the train and not the setup, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsTickPausesTheTrainStandingThere() throws Exception
    {
        String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

        javax.swing.JMenuItem tick = diagramMenuItem(mainB, label);

        assertNotNull(tick, "the track diagram's right-click on BottomMainB, where " + HIS_TRAIN + " stands, offers no "
            + label + ": " + diagramMenuTexts(mainB));

        assertTrue(tick instanceof javax.swing.JCheckBoxMenuItem, label + " is not a tick");

        assertTrue(tick.isSelected(), label + " reads unticked for a train nobody paused");

        click(tick);

        assertTrue(train.isAutonomyPaused(), "unticking " + label + " on the diagram did not pause the train");

        assertTrue(session.getPausedLocomotives().contains(HIS_TRAIN), "unticking " + label + " on the diagram paused the"
            + " train and did not record it in the setup: " + session.getPausedLocomotives());

        tick = diagramMenuItem(mainB, label);

        assertTrue(tick != null && !tick.isSelected(), label + " reads ticked for the paused train");

        click(tick);

        assertFalse(train.isAutonomyPaused(), "ticking " + label + " on the diagram did not set the train going");

        assertFalse(session.getPausedLocomotives().contains(HIS_TRAIN), "ticking " + label + " left the train paused in"
            + " the setup");

        // AND NONE WHERE NOBODY STANDS
        for (String text : diagramMenuTexts(mainA))
        {
            assertFalse(isATick(text), "the right-click on BottomMainA, where nobody stands, offers " + text);
        }
    }

    /**
     * In the autonomy editor: BottomMainB's right-click offers the same tick, and unticking it pauses his train and lists
     * it in the setup.
     *
     * MUTATION: leave the tick off the editor's menu, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsTickPausesTheTrainStandingThere() throws Exception
    {
        String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

        java.awt.Window editor = null;

        try
        {
            // THE EDITOR, as Autonomy > Edit Autonomy > the page opens it
            SwingUtilities.invokeLater(() -> ui.openAutonomyEditorOnPage(mainB.getPage()));

            for (long end = System.currentTimeMillis() + 60000; editor == null && System.currentTimeMillis() < end; )
            {
                Thread.sleep(100);

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window.isShowing() && "LayoutEditor".equals(window.getClass().getSimpleName())) editor = window;
                }
            }

            assertNotNull(editor, "the autonomy editor did not open on " + mainB.getPage());

            for (int turn = 0; turn < 6; turn++) pump();

            java.lang.reflect.Field panelField = editor.getClass().getDeclaredField("autonomyPanel");

            panelField.setAccessible(true);

            final AutonomyEditorPanel panel = (AutonomyEditorPanel) panelField.get(editor);

            assertNotNull(panel, "precondition: the editor opened without its autonomy panel");

            java.lang.reflect.Field setupField = AutonomyEditorPanel.class.getDeclaredField("session");

            setupField.setAccessible(true);

            final AutonomySession edited = (AutonomySession) setupField.get(panel);

            assertEquals(edited.getLocomotiveNameAt(mainB), HIS_TRAIN, "precondition: the editor's setup does not have "
                + HIS_TRAIN + " on BottomMainB");

            final javax.swing.JMenuItem[] tick = new javax.swing.JMenuItem[1];

            final List<String> offered = new ArrayList<>();

            SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JPopupMenu menu = panel.buildTileMenu(mainB, null);

                texts(menu, offered);

                tick[0] = itemNamed(menu, label);
            });

            assertNotNull(tick[0], "the editor's right-click on BottomMainB, where " + HIS_TRAIN + " stands, offers no "
                + label + ": " + offered);

            assertTrue(tick[0] instanceof javax.swing.JCheckBoxMenuItem && tick[0].isSelected(), label + " is not a tick,"
                + " or reads unticked for a train nobody paused");

            click(tick[0]);

            for (int turn = 0; turn < 6; turn++) pump();

            assertTrue(train.isAutonomyPaused(), "unticking " + label + " in the editor did not pause the train");

            assertTrue(edited.getPausedLocomotives().contains(HIS_TRAIN), "unticking " + label + " in the editor paused"
                + " the train and did not record it in the setup: " + edited.getPausedLocomotives());
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);
        }
    }

    // ---------------------------------------------------------------- the menus

    /** The track diagram's right-click menu on a square, built as a right-click builds it. */
    private static javax.swing.JPopupMenu diagramMenu(TileKey square) throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class, Point.class);

        gather.setAccessible(true);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
            TileKey.class, TileKey.class, Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

        make.setAccessible(true);

        final Point[] standing = new Point[1];

        SwingUtilities.invokeAndWait(() -> standing[0] = ui.getAutonomyPointForTile(square));

        final Object options = gather.invoke(null, ui, standing[0]);

        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                menu[0] = (javax.swing.JPopupMenu) make.newInstance(ui, square, square, options);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        return menu[0];
    }

    private static javax.swing.JMenuItem diagramMenuItem(TileKey square, String text) throws Exception
    {
        return itemNamed(diagramMenu(square), text);
    }

    private static List<String> diagramMenuTexts(TileKey square) throws Exception
    {
        List<String> out = new ArrayList<>();

        texts(diagramMenu(square), out);

        return out;
    }

    /** Whether a menu item's text is the tick's, for whichever train. */
    private static boolean isATick(String text)
    {
        String[] around = I18n.f("autolayout.ui.menuLocomotiveActive", "#TRAIN#").split("#TRAIN#", -1);

        return text != null && text.startsWith(around[0]) && text.endsWith(around[1])
            && text.length() > around[0].length() + around[1].length();
    }

    /** Clicks an item as the menu does, and waits for what it does. */
    private static void click(javax.swing.JMenuItem item) throws Exception
    {
        SwingUtilities.invokeAndWait(item::doClick);

        pump();
    }

    private static void closeTheEditor(java.awt.Window editor) throws Exception
    {
        // LATER, NOT AND-WAIT: closing asks whether to leave without saving, and a question asked inside invokeAndWait
        // waits for an answer this thread can then never give.
        SwingUtilities.invokeLater(() -> editor.dispatchEvent(
            new java.awt.event.WindowEvent(editor, java.awt.event.WindowEvent.WINDOW_CLOSING)));

        String leaving = I18n.t("layout.ui.dialogExitConfirmation");

        for (long end = System.currentTimeMillis() + 15000; (editor.isDisplayable() || ui.whyLayoutCannotBeEdited() != null)
            && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()
                    || !leaving.equals(((javax.swing.JDialog) window).getTitle())) continue;

                final javax.swing.JOptionPane pane = find(((javax.swing.JDialog) window).getContentPane(),
                    javax.swing.JOptionPane.class);

                if (pane != null && pane.getOptions() != null && pane.getOptions().length > 0)
                {
                    final Object yes = pane.getOptions()[0];

                    SwingUtilities.invokeAndWait(() -> pane.setValue(yes));
                }
            }
        }

        if (editor.isDisplayable()) SwingUtilities.invokeLater(editor::dispose);
    }

    /** Every item's text on a menu and its submenus, for a failure to show. */
    private static void texts(java.awt.Container menu, List<String> into)
    {
        java.awt.Component[] children = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component child : children)
        {
            if (child instanceof javax.swing.JMenuItem) into.add(((javax.swing.JMenuItem) child).getText());

            if (child instanceof javax.swing.JMenu) texts((javax.swing.JMenu) child, into);
        }
    }

    private static javax.swing.JMenuItem itemNamed(java.awt.Container menu, String text)
    {
        java.awt.Component[] children = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component child : children)
        {
            if (child instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) child).getText()))
            {
                return (javax.swing.JMenuItem) child;
            }

            if (child instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = itemNamed((javax.swing.JMenu) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static <T> T find(java.awt.Container container, Class<T> type)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof java.awt.Container)
            {
                T found = find((java.awt.Container) child, type);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static TileKey square(String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new SkipException("this railway has no " + name);
    }

    /** The railway as the model has it now: the editor's door rebuilds it. */
    private static Layout railway()
    {
        return model.getAutoLayout();
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * The Auto tab's pause button pressed after the diagram's tick is not undone by the next rebuild (RSA60-B1).
     *
     * The tick pauses the train and records it in the setup; the button then sets the train going on the railway alone,
     * as it is meant to, for the fold to take in; and a setup gesture's rebuild folds the railway's settings that changed
     * since it was built.  The tick did not tell the window the railway's pauses had changed, so the button's "going"
     * read as no change and the setup's "paused" stood: the rebuild paused the train again.
     *
     * MUTATION: leave the railway's settings as built alone when the tick changes a pause, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheAutoTabsButtonAfterTheTickIsNotUndoneByARebuild() throws Exception
    {
        String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

        javax.swing.JMenuItem tick = diagramMenuItem(mainB, label);

        assertNotNull(tick, "precondition: the track diagram's right-click on BottomMainB offers no " + label);

        click(tick);

        assertTrue(train.isAutonomyPaused(), "precondition: unticking " + label + " did not pause the train");

        final javax.swing.AbstractButton button = autoTabPauseButton();

        assertNotNull(button, "precondition: the Auto tab shows no pause button for " + HIS_TRAIN);

        SwingUtilities.invokeAndWait(button::doClick);

        pump();

        assertFalse(train.isAutonomyPaused(), "precondition: the Auto tab's pause button did not set the train going");

        // A SETUP GESTURE'S REBUILD
        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        for (int turn = 0; turn < 10; turn++) pump();

        assertFalse(train.isAutonomyPaused(), "the Auto tab's pause button set " + HIS_TRAIN + " going after the diagram's"
            + " tick paused it, and the next rebuild paused it again - the last choice made for it was undone (RSA60-B1)");
    }

    /** The Auto tab's pause button on the card of his train, or null where the tab has none. */
    private static javax.swing.AbstractButton autoTabPauseButton() throws Exception
    {
        final javax.swing.AbstractButton[] found = new javax.swing.AbstractButton[1];

        final java.lang.reflect.Field loco = Class.forName("org.traincontrol.gui.AutoLocomotiveStatus").getDeclaredField("locomotive");
        final java.lang.reflect.Field pause = Class.forName("org.traincontrol.gui.AutoLocomotiveStatus").getDeclaredField("pauseButton");

        loco.setAccessible(true);
        pause.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            java.util.List<java.awt.Component> all = new ArrayList<>();

            everything(ui.getContentPane(), all);

            for (java.awt.Component c : all)
            {
                if (!c.getClass().getSimpleName().equals("AutoLocomotiveStatus")) continue;

                try
                {
                    if (train.equals(loco.get(c))) found[0] = (javax.swing.AbstractButton) pause.get(c);
                }
                catch (IllegalAccessException cannot)
                {
                    throw new IllegalStateException(cannot);
                }
            }
        });

        return found[0];
    }

    private static void everything(java.awt.Container in, java.util.List<java.awt.Component> into)
    {
        for (java.awt.Component c : in.getComponents())
        {
            into.add(c);

            if (c instanceof java.awt.Container) everything((java.awt.Container) c, into);
        }
    }
}
