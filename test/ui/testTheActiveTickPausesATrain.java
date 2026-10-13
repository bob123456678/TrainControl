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

    // ------------------------------------------------- the bulk tools and the order of the cards (FR-126)

    /**
     * Trains whose names sort differently from any order they could be put down in, and the squares they stand on: two
     * names that part ways when capitals are ignored ("BN Lights", "Big Boy 4019 UP"), digits before letters, and a pair
     * for each of the groups the tab is sorted into.  Not in alphabetical order, which is what the old sort gave them.
     */
    private static final String[][] CROWD = {
        {"Big Boy 4019 UP", "BottomSecondary"},
        {"BN Lights", "TunnelLongPark"},
        {"BDymf 457 DB", "TunnelRightPark"},
        {"ALCO UP", "TunnelCenterPark"},
        {"118 028-0 DB", "BottomMainC"},
        {"06 001 DRG", "TunnelLeftPark"}};

    /** The two of them the claims pause, and the two whose stations they put out of use. */
    private static final String[] PAUSED = {"BDymf 457 DB", "ALCO UP"};

    private static final String[] OUT_OF_USE = {"118 028-0 DB", "06 001 DRG"};

    /** The Auto tab's order once that is done: active first, then paused, then the stations out of use, each by name. */
    private static final List<String> SORTED = java.util.Arrays.asList(HIS_TRAIN, "Big Boy 4019 UP", "BN Lights",
        "ALCO UP", "BDymf 457 DB", "06 001 DRG", "118 028-0 DB");

    /** The crowd standing on the railway and in the setup, as the class puts his train. */
    private static void crowd() throws Exception
    {
        final List<TileKey> squares = new ArrayList<>();

        for (String[] each : CROWD) squares.add(square(each[1]));

        SwingUtilities.invokeAndWait(() ->
        {
            for (int i = 0; i < CROWD.length; i++)
            {
                Locomotive loco = model.getLocByName(CROWD[i][0]);

                assertNotNull(loco, "precondition: this data has no " + CROWD[i][0]);

                for (String copy : session.facingsFor(squares.get(i)).keySet())
                {
                    if (railway().getPoint(copy) != null && railway().getLocomotiveLocation(loco) == null)
                    {
                        railway().moveLocomotive(CROWD[i][0], copy, false);
                    }
                }

                assertNotNull(railway().getLocomotiveLocation(loco), "precondition: " + CROWD[i][0] + " could not be stood"
                    + " on " + CROWD[i][1]);

                session.placeLocomotive(squares.get(i), CROWD[i][0]);
            }
        });
    }

    /** The crowd gone again, every train going and every station back in use. */
    private static void uncrowd() throws Exception
    {
        final List<TileKey> squares = new ArrayList<>();

        for (String[] each : CROWD) squares.add(square(each[1]));

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                for (int i = 0; i < CROWD.length; i++)
                {
                    Locomotive loco = model.getLocByName(CROWD[i][0]);

                    if (loco == null) continue;

                    loco.setAutonomyPaused(false);

                    session.setLocomotivePaused(CROWD[i][0], false);

                    Point at = railway().getLocomotiveLocation(loco);

                    if (at != null)
                    {
                        at.setActive(true);

                        railway().moveLocomotive(null, at.getName(), true);
                    }

                    session.placeLocomotive(squares.get(i), null);
                }

                train.setAutonomyPaused(false);

                session.setLocomotivePaused(HIS_TRAIN, false);
            }
            catch (java.io.IOException cannotSave)
            {
                throw new IllegalStateException(cannotSave);
            }
        });

        pump();
    }

    /** His train and the crowd, the ones these claims are about. */
    private static List<String> mine()
    {
        List<String> names = new ArrayList<>();

        names.add(HIS_TRAIN);

        for (String[] each : CROWD) names.add(each[0]);

        return names;
    }

    /** The names on the Auto tab's cards, in the order the tab lays them out - only the ones this class stood there. */
    private static List<String> cardOrder() throws Exception
    {
        final List<String> names = new ArrayList<>();

        final List<String> mine = mine();

        SwingUtilities.invokeAndWait(() ->
        {
            List<java.awt.Component> all = new ArrayList<>();

            everything(ui.getContentPane(), all);

            for (java.awt.Component c : all)
            {
                if (!(c instanceof org.traincontrol.gui.AutoLocomotiveStatus)) continue;

                String name = ((org.traincontrol.gui.AutoLocomotiveStatus) c).getLocomotive().getName();

                if (mine.contains(name)) names.add(name);
            }
        });

        return names;
    }

    /** Waits for the cards to be built and for each to take its routes. */
    private static void settleTheCards() throws Exception
    {
        for (long end = System.currentTimeMillis() + 20000; cardOrder().size() < mine().size()
            && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            pump();
        }

        for (int turn = 0; turn < 8; turn++)
        {
            Thread.sleep(100);

            pump();
        }
    }

    /** The whole tab built again, as a placement builds it. */
    private static void rebuildTheCards() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.repaintAutoLocList(false));

        settleTheCards();
    }

    /**
     * The tab laid out with every train active, and then the trains changed behind it: two paused, as the card's own
     * button pauses them (the train alone), and two stations put out of use under the trains standing on them.  The cards
     * stay as they were built, which is not the order they are due.
     */
    private static void goStale() throws Exception
    {
        crowd();

        rebuildTheCards();

        SwingUtilities.invokeAndWait(() ->
        {
            for (String name : PAUSED) model.getLocByName(name).setAutonomyPaused(true);

            for (String name : OUT_OF_USE) railway().getLocomotiveLocation(model.getLocByName(name)).setActive(false);
        });

        assertNotEquals(cardOrder(), SORTED, "precondition: the cards were already in the order they are due, so"
            + " nothing here can show a sort");
    }

    /** The commands tab opened: away to the settings tab and back, as a click on each tab does. */
    private static void openTheCommandsTab() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.showAutonomySettingsTab());

        pump();

        SwingUtilities.invokeAndWait(() -> ui.showAutonomyRunTab());

        settleTheCards();
    }

    /**
     * Opening the locomotive commands tab puts the active trains first, then the paused ones, then the ones on stations out
     * of use, each group by name with capitals ignored (FR-126; Adam: "re-sort them to show active first, then inactive, then
     * ones on inactive stations (as currently), sorted alphabetically within those groups").
     *
     * MUTATION: leave the tab alone when it is opened, leave the paused trains among the active, or order the names with
     * capitals counting, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testOpeningTheCommandsTabSortsActiveThenPausedThenTheInactiveStations() throws Exception
    {
        try
        {
            goStale();

            openTheCommandsTab();

            assertEquals(cardOrder(), SORTED, "opening the locomotive commands tab did not lay the cards out active first,"
                + " then paused, then the trains on stations out of use, each group by name");
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * Coming back to the Auto tab sorts too, when the commands tab was the one it was left on (FR-126): the user did open it.
     *
     * MUTATION: sort on the inner tabs' change only, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testComingBackToTheAutoTabSortsTheCommandsTabToo() throws Exception
    {
        try
        {
            // The commands tab is the one showing, and the user then leaves for the track diagram and returns
            SwingUtilities.invokeAndWait(() -> ui.showAutonomyRunTab());

            pump();

            goStale();

            SwingUtilities.invokeAndWait(() -> ui.showLayoutTab());

            pump();

            assertNotEquals(cardOrder(), SORTED, "leaving the Auto tab re-sorted the cards, which are due to be sorted when"
                + " it is opened");

            SwingUtilities.invokeAndWait(() -> ui.showAutonomyRunTab());

            settleTheCards();

            assertEquals(cardOrder(), SORTED, "coming back to the Auto tab, on the commands tab, did not sort its cards");
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * Pressing the pause button on a card does not move any card (FR-126; Adam: "Don't trigger the re-sort if you just click
     * the pause button on the locomotive autonomy commands tab"): the train is paused, the cards stand where they were, and
     * the next opening of the tab takes the pause into account.
     *
     * His train is first among the active ones, so a sort at the click would take it from the head of the tab to behind the
     * trains still active.
     *
     * MUTATION: have the pause button sort the cards, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAPauseClickOnACardDoesNotMoveTheCards() throws Exception
    {
        try
        {
            goStale();

            openTheCommandsTab();

            assertEquals(cardOrder(), SORTED, "precondition: opening the tab did not sort it");

            List<String> afterThePause = new ArrayList<>(SORTED);

            afterThePause.remove(HIS_TRAIN);
            afterThePause.add(afterThePause.indexOf("ALCO UP"), HIS_TRAIN);

            final javax.swing.AbstractButton button = autoTabPauseButton();

            assertNotNull(button, "precondition: the Auto tab shows no pause button for " + HIS_TRAIN);

            SwingUtilities.invokeAndWait(button::doClick);

            settleTheCards();

            assertTrue(train.isAutonomyPaused(), "precondition: the click did not pause " + HIS_TRAIN);

            assertEquals(cardOrder(), SORTED, "pressing a card's pause button moved the cards");

            openTheCommandsTab();

            assertEquals(cardOrder(), afterThePause, "the next opening of the tab did not take the pause into account");
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * Bulk Tools has Deactivate All Trains and Activate All Trains, on the track diagram's right-click menu as in the editor:
     * each sets every train standing on the layout, in the railway and in the setup, saves it with the configuration so a
     * reload has it, says how many it changed, and greys when there is nothing for it to change (FR-126; Adam: "a bulk tool to
     * mass activate and mass deactivate all trains").
     *
     * MUTATION: leave a train out of either tool, set the train and not the setup, or save nothing, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheBulkToolsActivateAndDeactivateEveryTrain() throws Exception
    {
        try
        {
            crowd();

            // ONE PAUSED BY HAND FIRST, through the tick, so that the two tools have different counts to tell apart
            final Locomotive alco = model.getLocByName("ALCO UP");

            SwingUtilities.invokeAndWait(() -> ui.setLocomotiveActive(session, alco, false));

            pump();

            final List<String> standing = new ArrayList<>(new java.util.TreeSet<>(session.trainsWhereTheyStand().values()));

            assertTrue(standing.containsAll(mine()), "precondition: not every train stands on the layout: " + standing);

            // DEACTIVATE EVERY TRAIN: the count is the trains still active
            String deactivate = I18n.f("autosetup.ui.menuDeactivateAllTrains", standing.size() - 1);

            javax.swing.JMenuItem tool = diagramMenuItem(mainB, deactivate);

            assertNotNull(tool, "Bulk Tools on the track diagram's right-click menu offers no " + deactivate + ": "
                + diagramMenuTexts(mainB));

            assertTrue(tool.isEnabled(), deactivate + " is greyed while " + (standing.size() - 1) + " trains are active");

            assertTrue(tool.getToolTipText() != null && tool.getToolTipText().contains(String.valueOf(standing.size() - 1)),
                deactivate + " has no tooltip saying how many it will pause: " + tool.getToolTipText());

            click(tool);

            for (int turn = 0; turn < 6; turn++) pump();

            for (String name : standing)
            {
                assertTrue(model.getLocByName(name).isAutonomyPaused(), name + " is still active after " + deactivate);
            }

            assertTrue(session.getPausedLocomotives().containsAll(standing), deactivate + " paused the trains and did not"
                + " record them all in the setup: " + session.getPausedLocomotives());

            assertTrue(hintText().contains(I18n.f("autosetup.ui.infoTrainsDeactivated", standing.size() - 1)),
                deactivate + " did not say how many it paused: " + hintText());

            AutonomySession reloaded = new AutonomySession(sandbox.getFolder());

            reloaded.open(pages());

            assertTrue(reloaded.getPausedLocomotives().containsAll(standing), "the pauses were not saved with the"
                + " configuration: a reload lists " + reloaded.getPausedLocomotives());

            // AND NOTHING LEFT FOR IT: greyed
            javax.swing.JMenuItem spent = diagramMenuItem(mainB, I18n.f("autosetup.ui.menuDeactivateAllTrains", 0));

            assertTrue(spent != null && !spent.isEnabled(), "with every train paused, Deactivate All Trains is not greyed: "
                + diagramMenuTexts(mainB));

            // ACTIVATE EVERY TRAIN: the count is the trains paused
            String activate = I18n.f("autosetup.ui.menuActivateAllTrains", standing.size());

            tool = diagramMenuItem(mainB, activate);

            assertNotNull(tool, "Bulk Tools offers no " + activate + " with every train paused: " + diagramMenuTexts(mainB));

            assertTrue(tool.isEnabled(), activate + " is greyed while every train is paused");

            click(tool);

            for (int turn = 0; turn < 6; turn++) pump();

            for (String name : standing)
            {
                assertFalse(model.getLocByName(name).isAutonomyPaused(), name + " is still paused after " + activate);
            }

            assertTrue(java.util.Collections.disjoint(session.getPausedLocomotives(), standing), activate + " left"
                + " trains paused in the setup: " + session.getPausedLocomotives());

            assertTrue(hintText().contains(I18n.f("autosetup.ui.infoTrainsActivated", standing.size())),
                activate + " did not say how many it set going: " + hintText());

            reloaded = new AutonomySession(sandbox.getFolder());

            reloaded.open(pages());

            assertTrue(java.util.Collections.disjoint(reloaded.getPausedLocomotives(), standing), "the trains set going"
                + " are paused again after a reload: " + reloaded.getPausedLocomotives());

            spent = diagramMenuItem(mainB, I18n.f("autosetup.ui.menuActivateAllTrains", 0));

            assertTrue(spent != null && !spent.isEnabled(), "with no train paused, Activate All Trains is not greyed: "
                + diagramMenuTexts(mainB));
        }
        finally
        {
            uncrowd();
        }
    }

    /** What the track diagram's borrowed autonomy menu last said in its message line. */
    private static String hintText() throws Exception
    {
        java.lang.reflect.Field menus = TrainControlUI.class.getDeclaredField("autonomyTileMenus");

        menus.setAccessible(true);

        java.lang.reflect.Field hint = AutonomyEditorPanel.class.getDeclaredField("hint");

        hint.setAccessible(true);

        return ((javax.swing.JLabel) hint.get(menus.get(ui))).getText();
    }

    /** The pages the model has, as a setup opens them. */
    private static List<org.traincontrol.base.LayoutDiagram> pages()
    {
        List<org.traincontrol.base.LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        return pages;
    }
}
