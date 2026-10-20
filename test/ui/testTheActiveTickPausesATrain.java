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
 * THE TRAINS ARE NOT ASKED OF HIS DATABASE (C1).  The claims use about a dozen trains by name, and a database without one
 * turned the class red.  Each the loaded database lacks is made for the class, in memory, through the model's own door, and
 * taken out again when the class ends; the class checks that it wrote no locomotive database file.
 *
 * The same pause is what Bulk Tools' Activate All Trains and Deactivate All Trains do for every train, and what the Auto
 * tab's locomotive commands tab sorts its cards by (FR-126; Adam: "a bulk tool to mass activate and mass deactivate all
 * trains", and "upon opening the locomotive autonomy commands tab, re-sort them to show active first, then inactive, then
 * ones on inactive stations").  So this class also holds:
 *
 * - the order of the cards when the tab is opened - active, then paused, then the stations out of use (which a paused train
 *   there belongs to), then a train standing nowhere - by name with capitals ignored, and that coming back to the Auto tab
 *   counts as opening it;
 * - that a card's pause button, and the refresh a run fires, move no card;
 * - that the two tools reach every train they should, are counted and greyed on the train's own flag, save with the
 *   configuration, are refused while autonomy runs, and tell the diagram's grey for a paused train on no Point.
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

        // THE DATABASE FILE AS IT IS BEFORE ANYTHING HERE TOUCHES THE MODEL, for the check at the end that this class wrote
        // none of it (C1)
        databaseFile = new java.io.File(org.traincontrol.util.Util.dataPath(MarklinControlStation.DATA_FILE_NAME));
        databaseModified = databaseFile.lastModified();
        databaseLength = databaseFile.length();

        model = support.TestStationAddress.withNoStation(
            () -> MarklinControlStation.init(null, true, true, false, true));
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new AssertionError("no autonomy setup on this railway");

        assertNotNull(railway(), "the frozen railway built no autonomy layout");

        makeTheTrainsTheDatabaseLacks();

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
            try
            {
                removeTheTrainsMade();
            }
            finally
            {
                try
                {
                    assertTheDatabaseFileWasNotWritten();
                }
                finally
                {
                    if (sandbox != null) sandbox.close();
                }
            }
        }
    }

    // ----------------------------------------------------------- the trains these claims use (C1)

    /** The train the paused-grey claim stands on a square its facing cannot be held on. */
    private static final String STRAY = "BR 10 001";

    /** The first address of the trains made for the class, one each: nobody's but this class's. */
    private static final int FIRST_ADDRESS = 3401;

    /** The trains the class made because the database it ran against lacked them, by name. */
    private static final List<String> made = new ArrayList<>();

    /** The locomotive database file as the class began, to show that the class did not write it. */
    private static java.io.File databaseFile;

    private static long databaseModified;

    private static long databaseLength;

    /** Every train the claims use, by name. */
    private static List<String> trainsNeeded()
    {
        List<String> names = new ArrayList<>();

        names.add(HIS_TRAIN);

        for (String[] each : CROWD) names.add(each[0]);

        names.add(NOWHERE);
        names.add(UNPLACED_PAUSED);
        names.add(UNPLACED_GOING);
        names.add(STRAY);

        return names;
    }

    /**
     * Makes each train the claims use that the database this class runs against does not have (C1): the class does not ask
     * Adam's database for ten trains by name, which turned it red on a database without one.  In memory only, through the
     * model's own door; nothing here or in a claim saves the model - that is the window closing and the Backup Data menu
     * item, neither of which is used - and `removeTheTrainsMade` takes them out before the class ends all the same.
     */
    private static void makeTheTrainsTheDatabaseLacks()
    {
        int address = FIRST_ADDRESS;

        for (String name : trainsNeeded())
        {
            if (model.getLocByName(name) == null)
            {
                model.newMM2Locomotive(name, address++);

                made.add(name);
            }
        }
    }

    /**
     * Takes the trains made for the class out of the model, with the window's own repair of the setup that names them: on
     * the event thread, where the window's repair of the setup belongs.  Those the database already had are left.
     */
    private static void removeTheTrainsMade() throws Exception
    {
        if (model == null || made.isEmpty()) return;

        final List<String> gone = new ArrayList<>(made);

        made.clear();

        SwingUtilities.invokeAndWait(() ->
        {
            for (String name : gone) model.deleteLoc(name);
        });
    }

    /**
     * Shows that the class did not write the locomotive database (C1): the file the application reads - the folder this run
     * was given, or the project's own - has the length and the time it had before the model was started.  The model saves
     * only when the window closes or Backup Data is chosen, and the class does neither; this fails the class if that ever
     * stops being so, with the trains made for it in the model.
     */
    private static void assertTheDatabaseFileWasNotWritten()
    {
        if (databaseFile == null) return;

        assertEquals(databaseFile.length(), databaseLength, "the locomotive database " + databaseFile + " changed size"
            + " while this class ran: something saved the model, with the trains made for the class in it");

        assertEquals(databaseFile.lastModified(), databaseModified, "the locomotive database " + databaseFile + " was"
            + " written while this class ran: something saved the model, with the trains made for the class in it");
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

        throw new AssertionError("this railway has no " + name);
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
     * The Auto tab's pause button pressed after the diagram's tick is not undone by the next rebuild.
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

    // ------------------------------------------- a pause made on a card outlives what is done to another train (OB-343)

    /** The train the claims below press the card's pause button of, and the square it stands on among the crowd. */
    private static final String CARD_TRAIN = "ALCO UP";

    private static final String CARD_TRAIN_SQUARE = "TunnelCenterPark";

    /** A second crowd train, for the claim that has one train paused by its card and another listed by the setup. */
    private static final String LISTED_TRAIN = "BN Lights";

    /**
     * The Auto tab's pause button on a train's card, once the tab has built it: the tab is built again after a rebuild, and
     * on the event thread a moment later.
     */
    private static javax.swing.AbstractButton cardsButtonOnceBuilt(Locomotive who) throws Exception
    {
        javax.swing.AbstractButton button = autoTabPauseButton(who);

        for (long end = System.currentTimeMillis() + 10000; button == null && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            pump();

            button = autoTabPauseButton(who);
        }

        assertNotNull(button, "the Auto tab shows no card with a pause button for " + who.getName());

        return button;
    }

    /** The card's pause button pressed, as a click on it: the train alone, which is what the button changes. */
    private static void pressTheCardsButton(Locomotive who, boolean expectedToPause) throws Exception
    {
        final javax.swing.AbstractButton button = cardsButtonOnceBuilt(who);

        assertEquals(button.isSelected(), !expectedToPause, "precondition: the card of " + who.getName() + " reads "
            + (button.isSelected() ? "paused" : "going") + " before the press that should "
            + (expectedToPause ? "pause" : "set going") + " it");

        SwingUtilities.invokeAndWait(button::doClick);

        pump();

        assertEquals(who.isAutonomyPaused(), expectedToPause, "precondition: the card's button did not "
            + (expectedToPause ? "pause " : "set going ") + who.getName());
    }

    /** The rebuild a tick or a tool asks for has been done, and the Auto tab's cards built again. */
    private static void settleTheRebuild() throws Exception
    {
        for (int turn = 0; turn < 10; turn++)
        {
            Thread.sleep(50);

            pump();
        }
    }

    /**
     * What the next setup gesture does - a home set, a caption typed, the editor closed: the railway is built again from the
     * setup.  The diagram's tick does not rebuild for itself, so the setup's list is read when that next gesture comes.
     */
    private static void theNextSetupGestureRebuilds() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        settleTheRebuild();
    }

    /**
     * Whether a train is paused (or going) everywhere the program keeps that: the train, which autonomy reads; the railway's
     * own list of the paused; the setup's; and the card's button.  Says which of them disagrees with the answer asked for.
     */
    private static void assertPausedEverywhere(Locomotive who, boolean paused, String after) throws Exception
    {
        String name = who.getName();
        String word = paused ? "paused" : "going";

        assertEquals(who.isAutonomyPaused(), paused, name + ", " + word + " from its card's button, is not " + word + " "
            + after + " - the rebuild took the setup's older answer for a train nobody asked about (OB-343)");

        assertEquals(Layout.pausedNamesOf(model).contains(name), paused, name + " is not " + word + " in the railway's list "
            + after + ": " + Layout.pausedNamesOf(model));

        assertEquals(session.getPausedLocomotives().contains(name), paused, name + " is not " + word + " in the setup "
            + after + ": " + session.getPausedLocomotives());

        assertEquals(cardsButtonOnceBuilt(who).isSelected(), paused, "the Auto tab's card of " + name + " does not read "
            + word + " " + after);
    }

    /**
     * A train paused from its card's pause button stays paused whatever is done to another train's Is Active tick (OB-343).
     *
     * The button changes the train alone, and the setup learns of it at the next fold.  The tick writes the train it is
     * given and the setup's list together, and then tells the window the railway's pauses and the setup's agree - as if the
     * railway had been built so - and a railway whose pauses agree has the setup's list taken as the word on every train.
     * So the tick on a second train made the window believe the first one's pause had been built, which it had not: the
     * setup's older answer stood and the first train was set going again, with nobody asking.
     *
     * One train paused from its card; another's tick unticked, and ticked again.  Neither rebuild sets the first going.
     *
     * MUTATION: have the tick note every train's pause as built, not only the ones it changed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATrainPausedFromItsCardStaysPausedWhenAnotherTrainsTickIsChanged() throws Exception
    {
        try
        {
            // BUILT WITH NOBODY PAUSED, so that how the railway was built is not left over from another claim
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            pressTheCardsButton(card, true);

            assertFalse(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the setup already lists "
                + CARD_TRAIN + ", which the card's button paused on the train alone: " + session.getPausedLocomotives());

            String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

            // ANOTHER TRAIN UNTICKED
            javax.swing.JMenuItem tick = diagramMenuItem(mainB, label);

            assertNotNull(tick, "precondition: the track diagram's right-click on BottomMainB offers no " + label);

            click(tick);

            theNextSetupGestureRebuilds();

            assertTrue(train.isAutonomyPaused(), "precondition: unticking " + label + " did not pause " + HIS_TRAIN);

            assertPausedEverywhere(card, true, "after unticking " + label);

            // AND TICKED AGAIN
            tick = diagramMenuItem(mainB, label);

            assertTrue(tick != null && !tick.isSelected(), "precondition: " + label + " does not read unticked");

            click(tick);

            theNextSetupGestureRebuilds();

            assertFalse(train.isAutonomyPaused(), "precondition: ticking " + label + " did not set " + HIS_TRAIN + " going");

            assertPausedEverywhere(card, true, "after ticking " + label);
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * The mirror (OB-343): a train set going from its card's button, while the setup still lists it as paused, stays going
     * whatever is done to another train's Is Active tick.
     *
     * The train is paused through its own tick first, so the setup lists it and the railway is rebuilt with it paused; its
     * card's button then sets it going, on the train alone; and a tick on another train, unticked and ticked, must not read
     * the setup's older list as the word on it.
     *
     * MUTATION: have the tick note every train's pause as built, not only the ones it changed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATrainSetGoingFromItsCardStaysGoingWhenAnotherTrainsTickIsChanged() throws Exception
    {
        try
        {
            // BUILT WITH NOBODY PAUSED, so that how the railway was built is not left over from another claim
            theNextSetupGestureRebuilds();

            crowd();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            // PAUSED THROUGH ITS OWN TICK on the diagram, so the setup lists it and the rebuild has built it paused
            String cardsLabel = I18n.f("autolayout.ui.menuLocomotiveActive", CARD_TRAIN);

            javax.swing.JMenuItem own = diagramMenuItem(square(CARD_TRAIN_SQUARE), cardsLabel);

            assertNotNull(own, "precondition: the track diagram's right-click on " + CARD_TRAIN_SQUARE + " offers no "
                + cardsLabel);

            click(own);

            theNextSetupGestureRebuilds();

            assertTrue(card.isAutonomyPaused(), "precondition: unticking " + cardsLabel + " did not pause the train");

            assertTrue(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the setup does not list "
                + CARD_TRAIN + " after its tick");

            // SET GOING BY ITS CARD'S BUTTON: the train alone, and the list still names it
            pressTheCardsButton(card, false);

            assertTrue(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the setup has already been told "
                + CARD_TRAIN + " is going: " + session.getPausedLocomotives());

            String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

            // ANOTHER TRAIN UNTICKED
            javax.swing.JMenuItem tick = diagramMenuItem(mainB, label);

            assertNotNull(tick, "precondition: the track diagram's right-click on BottomMainB offers no " + label);

            click(tick);

            theNextSetupGestureRebuilds();

            assertTrue(train.isAutonomyPaused(), "precondition: unticking " + label + " did not pause " + HIS_TRAIN);

            assertPausedEverywhere(card, false, "after unticking " + label);

            // AND TICKED AGAIN
            tick = diagramMenuItem(mainB, label);

            assertTrue(tick != null && !tick.isSelected(), "precondition: " + label + " does not read unticked");

            click(tick);

            theNextSetupGestureRebuilds();

            assertPausedEverywhere(card, false, "after ticking " + label);
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * The same through the autonomy editor's tick, which rebuilds the railway itself (OB-343): a train paused from its card's
     * button stays paused when another train is unticked in the editor, and then, set going from its card's button while the
     * setup lists it as paused, stays going when that train is ticked again.
     *
     * MUTATION: have the tick note every train's pause as built, not only the ones it changed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testACardsPauseAndGoingSurviveAnotherTrainsTickInTheEditor() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            // BUILT WITH NOBODY PAUSED, so that how the railway was built is not left over from another claim
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            editor = openTheEditor();

            final AutonomyEditorPanel panel = editorsPanel(editor);

            String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

            // PAUSED BY ITS CARD'S BUTTON, and another train unticked in the editor
            pressTheCardsButton(card, true);

            clickTheEditorsTick(panel, label);

            settleTheRebuild();

            assertTrue(train.isAutonomyPaused(), "precondition: unticking " + label + " in the editor did not pause "
                + HIS_TRAIN);

            assertPausedEverywhere(card, true, "after unticking " + label + " in the editor");

            // SET GOING BY ITS CARD'S BUTTON, the setup listing it now, and the other train ticked again
            pressTheCardsButton(card, false);

            assertTrue(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the setup has already been told "
                + CARD_TRAIN + " is going: " + session.getPausedLocomotives());

            clickTheEditorsTick(panel, label);

            settleTheRebuild();

            assertFalse(train.isAutonomyPaused(), "precondition: ticking " + label + " in the editor did not set "
                + HIS_TRAIN + " going");

            assertPausedEverywhere(card, false, "after ticking " + label + " in the editor");
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            uncrowd();
        }
    }

    /**
     * The autonomy editor, opened as Autonomy > Edit Autonomy > the page opens it.  Opening it folds the running railway
     * into the setup, whole, with no rebuild after.
     */
    private static java.awt.Window openTheEditor() throws Exception
    {
        java.awt.Window editor = null;

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

        return editor;
    }

    /** The autonomy panel of an editor window. */
    private static AutonomyEditorPanel editorsPanel(java.awt.Window editor) throws Exception
    {
        java.lang.reflect.Field panelField = editor.getClass().getDeclaredField("autonomyPanel");

        panelField.setAccessible(true);

        AutonomyEditorPanel panel = (AutonomyEditorPanel) panelField.get(editor);

        assertNotNull(panel, "precondition: the editor opened without its autonomy panel");

        return panel;
    }

    /**
     * A fold of the railway into the setup that no rebuild follows leaves how the railway was built behind (OB-343): the
     * editor opens on the running railway's settings, whole, and its cards stay pressable - the editor is not modal.
     *
     * The railway was built with nobody paused, a card pauses a train, the editor opens and takes the pause into the setup;
     * the card then sets the train going, and the railway's list is empty again, the same as when it was built.  Taken to
     * be the railway unchanged since then, the setup's list - which now names the train - stood at the next setup gesture's
     * rebuild, and the train was paused again.
     *
     * MUTATION: leave the record of how the railway was built as it was when a fold writes the railway's settings into the
     * setup, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAPauseTheEditorOpeningTookIntoTheSetupIsUndoneByItsCardForGood() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            // BUILT WITH NOBODY PAUSED
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            // PAUSED BY ITS CARD'S BUTTON: the train alone
            pressTheCardsButton(card, true);

            assertFalse(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the setup already lists "
                + CARD_TRAIN);

            // THE EDITOR OPENS: the fold takes the pause into the setup, and nothing rebuilds after it
            editor = openTheEditor();

            assertTrue(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: opening the editor did not fold"
                + " the railway's pause of " + CARD_TRAIN + " into the setup: " + session.getPausedLocomotives());

            // SET GOING BY ITS CARD'S BUTTON: the railway lists nobody again, as when it was built
            pressTheCardsButton(card, false);

            // A SETUP GESTURE'S REBUILD
            theNextSetupGestureRebuilds();

            assertPausedEverywhere(card, false, "after the editor opened and took its pause into the setup");
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            uncrowd();
        }
    }

    /**
     * The mirror, and a tick on another train in that state (OB-343): the railway was built with a train paused, the card
     * sets it going, the editor opens and takes that into the setup, the card pauses it again - the railway's list is the
     * list it was built with - and another train's tick in the editor, with the rebuild it asks for, must not set it going.
     *
     * MUTATION: leave the record of how the railway was built as it was when a fold writes the railway's settings into the
     * setup, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAPauseOnItsCardAfterTheEditorOpenedSurvivesAnotherTrainsTick() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            // BUILT WITH THE TRAIN PAUSED: through its own tick, and the rebuild after it
            String cardsLabel = I18n.f("autolayout.ui.menuLocomotiveActive", CARD_TRAIN);

            javax.swing.JMenuItem own = diagramMenuItem(square(CARD_TRAIN_SQUARE), cardsLabel);

            assertNotNull(own, "precondition: the track diagram's right-click on " + CARD_TRAIN_SQUARE + " offers no "
                + cardsLabel);

            click(own);

            theNextSetupGestureRebuilds();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            assertTrue(card.isAutonomyPaused() && session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: "
                + CARD_TRAIN + " was not built paused");

            rebuildTheCards();

            // SET GOING BY ITS CARD'S BUTTON, and the editor opens: the fold takes that into the setup
            pressTheCardsButton(card, false);

            editor = openTheEditor();

            assertFalse(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: opening the editor did not fold"
                + " the railway's " + CARD_TRAIN + ", going, into the setup: " + session.getPausedLocomotives());

            // PAUSED AGAIN BY ITS CARD'S BUTTON: the railway lists what it was built with
            pressTheCardsButton(card, true);

            // ANOTHER TRAIN UNTICKED IN THE EDITOR
            String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

            clickTheEditorsTick(editorsPanel(editor), label);

            settleTheRebuild();

            assertTrue(train.isAutonomyPaused(), "precondition: unticking " + label + " in the editor did not pause "
                + HIS_TRAIN);

            assertPausedEverywhere(card, true, "after the editor opened, and " + label + " was unticked in it");
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            uncrowd();
        }
    }

    // ------------------------------- a rollback puts the record back with the setup (OB-343)

    /** The window's record of how the railway was built, for the railway given. */
    private static org.json.JSONObject recordOf(Layout railway) throws Exception
    {
        Method record = TrainControlUI.class.getDeclaredMethod("settingsAsBuiltFor", Object.class);

        record.setAccessible(true);

        return (org.json.JSONObject) record.invoke(ui, railway);
    }

    /**
     * An old-format import that fails after folding the running railway into the setup puts the setup back, and the record of
     * how the railway was built with it (OB-343).
     *
     * The fold the import makes first is the one every fold makes, and brings the record up to date with what it took.
     * The rollback restored the setup and left the record, so the railway - which the rollback does not touch - read to the
     * next setup gesture's rebuild as unchanged since it was built, nothing was taken from it, and the railway was rebuilt
     * from the older setup: a timetable entry a run captured (a lost timetable, grade B), a setting moved on the Auto tab and
     * a train paused by its card's button all went.
     *
     * The railway alone gets the three; the import is of a file `detect` calls an old graph, with `edges` an object, which
     * `whatALegacyImportLeaves` reads strictly and throws on after the fold; and a setup gesture's rebuild follows.
     *
     * MUTATION: leave the record as the fold left it when the import puts the setup back, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnOldFileImportThatFailsAfterItsFoldLeavesWhatTheRailwayChangedAlone() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        final int[] delayAsFound = {-1};

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);
            final Layout running = railway();
            final String configuration = ui.getActiveDiagramConfiguration();

            assertNotNull(configuration, "precondition: no configuration is running");

            // THE RAILWAY ALONE CHANGES, as it does while it runs: a train paused by its card's button, a timetable entry
            // captured, a setting moved on the Auto tab
            pressTheCardsButton(card, true);

            final int entries = running.getTimetable().size();
            final int delay = running.getMinDelay();
            final int moved = delay + 1 <= running.getMaxDelay() ? delay + 1 : Math.max(0, delay - 1);

            delayAsFound[0] = delay;

            assertNotEquals(moved, delay, "precondition: the minimum delay cannot be moved");

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    running.getTimetable().add(new org.traincontrol.automation.TimetablePath(train,
                        new ArrayList<>(java.util.Collections.singletonList(running.getEdges().iterator().next())),
                        System.currentTimeMillis()));

                    running.setMinDelay(moved);
                }
                catch (Exception cannot)
                {
                    throw new IllegalStateException(cannot);
                }
            });

            // THE IMPORT, which folds the railway into the setup first and fails after it
            final org.traincontrol.gui.AutonomyViewerPanel viewer = ui.getAutonomyViewerPanel();

            final Method importIt = org.traincontrol.gui.AutonomyViewerPanel.class.getDeclaredMethod("importLegacyGraph",
                org.json.JSONObject.class, String.class);

            importIt.setAccessible(true);

            final org.json.JSONObject file = new org.json.JSONObject("{\"points\": [], \"edges\": {}}");

            List<String> said = runAndDismiss(() ->
            {
                try
                {
                    importIt.invoke(viewer, file, configuration);
                }
                catch (ReflectiveOperationException cannot)
                {
                    throw new IllegalStateException(cannot);
                }
            });

            pump();

            String unreadable = I18n.f("autosetup.ui.errorImportUnreadable", "#X#").split("#X#")[0].trim();

            assertTrue(said.toString().contains(unreadable), "precondition: the import did not fail as the claim needs it to,"
                + " after its fold: " + said);

            assertSame(railway(), running, "precondition: the failed import replaced the railway");

            // A SETUP GESTURE'S REBUILD
            theNextSetupGestureRebuilds();

            Layout after = railway();

            assertEquals(after.getTimetable().size(), entries + 1, "the timetable entry the railway captured is gone after an"
                + " import failed and a setup gesture rebuilt it (OB-343)");

            assertEquals(after.getMinDelay(), moved, "the setting moved on the Auto tab went back after an import failed"
                + " and a setup gesture rebuilt the railway (OB-343)");

            assertPausedEverywhere(card, true, "after an import failed and a setup gesture rebuilt the railway");
        }
        finally
        {
            // THE RAILWAY'S SIDE BACK FIRST, then the setup, so that the rebuild after them folds nothing back
            Layout now = railway();

            if (now != null)
            {
                now.getTimetable().clear();

                if (delayAsFound[0] >= 0) now.setMinDelay(delayAsFound[0]);
            }

            session.restoreSetup(asFound);

            theNextSetupGestureRebuilds();

            uncrowd();
        }
    }

    /**
     * The editor's Cancel after a fold the editor's own rebuild made keeps what the railway changed alone (OB-343).
     *
     * An in-editor rebuild folds the railway's changes into the setup before it builds, and a build the setup does not allow
     * - a link left unpaired - leaves the railway running with the setup newer.  The fold brings the record up to date with
     * what it took, and the editor's Cancel restores the setup it opened with, which does not have the change the fold took:
     * the Cancel's rebuild then read the railway as unchanged since it was built, took nothing from it and rebuilt from the
     * older setup - a train paused by its card's button after the editor opened went.
     *
     * The declined build is stood in for by its fold alone, which is all of it that touches the record: the settings fold
     * with the railway left running.  The train is paused by its card's button after the editor has opened, the editor is
     * closed discarding, and the train must still be paused.
     *
     * MUTATION: leave what the fold took out of what Cancel puts back, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsCancelAfterAFoldItsRebuildMadeKeepsAPauseTheCardMade() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            // THE EDITOR OPENS: its undo point is the setup as it is, and the record as it is
            editor = openTheEditor();

            // PAUSED BY ITS CARD'S BUTTON after the editor has opened: the railway alone
            pressTheCardsButton(card, true);

            // THE FOLD A DECLINED IN-EDITOR REBUILD MAKES before the build is refused: the pause is taken into the setup, and
            // the railway is left running
            final Layout running = railway();

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    session.captureTheRailwaysSettings(running.toJSON(), ui.getActiveDiagramConfiguration(),
                        recordOf(running));
                }
                catch (Exception cannot)
                {
                    throw new IllegalStateException(cannot);
                }
            });

            assertTrue(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the fold did not take the pause into"
                + " the setup: " + session.getPausedLocomotives());

            assertSame(railway(), running, "precondition: the railway was replaced");

            // CANCEL: the editor closed, discarding what was changed in it
            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertPausedEverywhere(card, true, "after the editor's Cancel put the setup it opened with back");
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            uncrowd();
        }
    }

    /**
     * The editor's Cancel keeps what the railway changed alone while the editor was open, whatever was rebuilt between, and
     * undoes what the editor changed (OB-343).
     *
     * With the editor open the Auto tab and the cards are live: a setting is moved and a train paused, on the railway alone.
     * An edit in the editor then rebuilds the railway and REPLACES it, folding what the railway changed into the setup first.
     * Cancel restores the setup the editor opened with - which has none of that - and the rebuild it makes found the new
     * railway unchanged since it was built, so the setting and the pause went back with the edit.
     *
     * The edit here is another train's tick: Cancel must undo it, and keep the setting and the pause.
     *
     * MUTATION: leave what the folds took from the railway out of what Cancel puts back, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsCancelKeepsWhatTheRailwayChangedAloneWhateverWasRebuiltBetween() throws Exception
    {
        java.awt.Window editor = null;

        final org.json.JSONObject asFound = session.snapshotSetup();

        final int[] delayAsFound = {-1};

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            editor = openTheEditor();

            final Layout opened = railway();
            final int delay = opened.getMinDelay();
            final int moved = delay + 1 <= opened.getMaxDelay() ? delay + 1 : Math.max(0, delay - 1);

            delayAsFound[0] = delay;

            assertNotEquals(moved, delay, "precondition: the minimum delay cannot be moved");

            // THE RAILWAY ALONE CHANGES with the editor open: a setting moved on the Auto tab, a train paused by its card
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    opened.setMinDelay(moved);
                }
                catch (Exception cannot)
                {
                    throw new IllegalStateException(cannot);
                }
            });

            pressTheCardsButton(card, true);

            // AN EDIT IN THE EDITOR whose rebuild replaces the railway
            String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

            clickTheEditorsTick(editorsPanel(editor), label);

            settleTheRebuild();

            assertNotSame(railway(), opened, "precondition: the editor's rebuild did not replace the railway");

            assertTrue(train.isAutonomyPaused(), "precondition: unticking " + label + " in the editor did not pause "
                + HIS_TRAIN);

            assertEquals(railway().getMinDelay(), moved, "precondition: the rebuilt railway lost the moved setting");

            // CANCEL
            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertEquals(railway().getMinDelay(), moved, "the setting moved on the Auto tab while the editor was open went"
                + " back with the editor's Cancel (OB-343)");

            assertPausedEverywhere(card, true, "after the editor's Cancel, a rebuild between having replaced the railway");

            assertFalse(train.isAutonomyPaused(), "the editor's own tick of " + HIS_TRAIN + " was kept by its Cancel");

            assertFalse(session.getPausedLocomotives().contains(HIS_TRAIN), "the editor's own tick of " + HIS_TRAIN + " is"
                + " still in the setup after its Cancel: " + session.getPausedLocomotives());
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            Layout now = railway();

            if (now != null && delayAsFound[0] >= 0) now.setMinDelay(delayAsFound[0]);

            session.restoreSetup(asFound);

            theNextSetupGestureRebuilds();

            uncrowd();
        }
    }

    /**
     * The mirror (OB-343): a tick in the editor whose rebuild was refused is undone by Cancel.
     *
     * A refused rebuild leaves the railway running, the train paused and the setup and the record of how the railway was built
     * with the tick.  Cancel restores the setup the editor opened with; the record, still holding the tick, says the railway
     * is as the setup last agreed, so the rebuild builds from the restored setup and the tick is undone - as it is when the
     * rebuild replaced the railway.  Put back with the setup (as the record was for a short while), the record read the tick
     * as a change the railway had made alone and kept it.
     *
     * The refusal is stood in for by its effects: the door's own write, with no rebuild after it, which is all a refused
     * build leaves of a tick (it needs a setup the graph refuses, which a claim cannot make cheaply).
     *
     * MUTATION: put the record back with the setup when Cancel restores it, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsCancelUndoesATickWhoseRebuildWasRefused() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            editor = openTheEditor();

            final Layout opened = railway();

            SwingUtilities.invokeAndWait(() -> ui.setLocomotiveActive(session, train, false));

            assertTrue(train.isAutonomyPaused() && session.getPausedLocomotives().contains(HIS_TRAIN), "precondition: the"
                + " door did not pause " + HIS_TRAIN + " and list it");

            assertSame(railway(), opened, "precondition: the railway was replaced");

            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertFalse(train.isAutonomyPaused(), "the tick made in the editor, whose rebuild was refused, was kept by the"
                + " editor's Cancel (OB-343)");

            assertFalse(session.getPausedLocomotives().contains(HIS_TRAIN), "the tick is still in the setup after Cancel: "
                + session.getPausedLocomotives());
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);
        }
    }

    /**
     * Cancel's own fold takes a card's pause BY TRAIN and leaves the editor's tick to be undone (OB-343), scenario 1: the
     * editor's tick pauses a train, a card then pauses another, and Cancel puts the setup it opened with back.
     *
     * The record holds the tick - the tick wrote it - and the railway's list holds the tick and the card's pause, so what
     * the railway has against the record is the card's alone.  The fold Cancel forces took the railway's whole list, and the
     * tick, which Cancel had just taken out of the setup, came back with it.
     *
     * MUTATION: take the railway's whole list of the paused in a fold, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsCancelUndoesATickWhenACardPausedAnotherTrainAfterIt() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            editor = openTheEditor();

            // THE EDITOR'S TICK pauses a train
            String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

            clickTheEditorsTick(editorsPanel(editor), label);

            settleTheRebuild();

            assertTrue(train.isAutonomyPaused(), "precondition: unticking " + label + " in the editor did not pause " + HIS_TRAIN);

            // A CARD PAUSES ANOTHER after it
            pressTheCardsButton(card, true);

            // CANCEL
            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertFalse(train.isAutonomyPaused(), "the editor's own tick of " + HIS_TRAIN + " was kept by its Cancel, taken"
                + " back into the setup with the card's pause of " + CARD_TRAIN + " (OB-343)");

            assertFalse(session.getPausedLocomotives().contains(HIS_TRAIN), "the tick is still in the setup after Cancel: "
                + session.getPausedLocomotives());

            assertPausedEverywhere(card, true, "after the editor's Cancel");
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            uncrowd();
        }
    }

    /**
     * Scenario 2 (OB-343): a train the setup had paused, ticked active in the editor, stays paused after Cancel when a card
     * pauses another train in between.
     *
     * The tick left the record with no list of the paused at all, and a fold with such a record took the railway's list as
     * the whole - the card's train alone - so the train the setup had paused came out running.
     *
     * MUTATION: take the railway's whole list when the record has none, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsCancelKeepsATrainTheSetupHadPausedWhenACardPausedAnotherAfterItsTick() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            // THE SETUP HAS HIS TRAIN PAUSED, and the railway was built so
            String label = I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN);

            javax.swing.JMenuItem tick = diagramMenuItem(mainB, label);

            assertNotNull(tick, "precondition: the track diagram's right-click on BottomMainB offers no " + label);

            click(tick);

            theNextSetupGestureRebuilds();

            assertTrue(train.isAutonomyPaused() && session.getPausedLocomotives().contains(HIS_TRAIN), "precondition: "
                + HIS_TRAIN + " was not built paused");

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            editor = openTheEditor();

            // TICKED ACTIVE IN THE EDITOR: the record then lists nobody
            clickTheEditorsTick(editorsPanel(editor), label);

            settleTheRebuild();

            assertFalse(train.isAutonomyPaused(), "precondition: ticking " + label + " in the editor did not set it going");

            // A CARD PAUSES ANOTHER
            pressTheCardsButton(card, true);

            // CANCEL
            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertTrue(train.isAutonomyPaused(), HIS_TRAIN + ", which the setup had paused, came out running after the editor's"
                + " Cancel (OB-343)");

            assertTrue(session.getPausedLocomotives().contains(HIS_TRAIN), "the setup lost the pause it opened with: "
                + session.getPausedLocomotives());

            assertPausedEverywhere(card, true, "after the editor's Cancel");
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            uncrowd();
        }
    }

    /**
     * A bulk tool over a train a card paused leaves that pause the card's (OB-343): Deactivate All Trains names every train
     * standing, the one a card has paused among them, and wrote that train into the record as the tool's own change - so
     * Cancel, which takes the tool out, found the railway equal to the record and set the train going.
     *
     * One train paused by its card with the editor open, Deactivate All Trains from the editor's menu, and Cancel: the card's
     * pause stays, and what the tool paused does not.
     *
     * MUTATION: have the door note in the record every train it names, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorsCancelKeepsACardsPauseThroughABulkToolOverIt() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            final List<String> standing = new ArrayList<>(new java.util.TreeSet<>(session.trainsWhereTheyStand().values()));

            editor = openTheEditor();

            pressTheCardsButton(card, true);

            String deactivate = I18n.f("autosetup.ui.menuDeactivateAllTrains", standing.size() - 1);

            clickTheEditorsTick(editorsPanel(editor), deactivate);

            settleTheRebuild();

            for (String name : standing)
            {
                assertTrue(model.getLocByName(name).isAutonomyPaused(), "precondition: " + name + " is still active after "
                    + deactivate);
            }

            // CANCEL
            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            for (String name : standing)
            {
                assertEquals(model.getLocByName(name).isAutonomyPaused(), CARD_TRAIN.equals(name), name + (CARD_TRAIN.equals(name)
                    ? ", paused by its card's button before the bulk tool, was set going by the editor's Cancel"
                    : ", paused by the bulk tool in the editor, was kept by its Cancel") + " (OB-343)");
            }
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            uncrowd();
        }
    }

    // ----------------------------------------------------- a locomotive renamed, with a card's change not yet folded (OB-343)

    /** What the card's train is called while a claim has it renamed. */
    private static final String RENAMED_TRAIN = "ALCO UP Renamed";

    /** The locomotive renamed as its window's rename door does it, on the event thread. */
    private static void renameATrain(final String from, final String to) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> assertTrue(model.renameLoc(from, to), "precondition: " + from + " could not be"
            + " renamed to " + to));

        pump();
    }

    /** The card's train called what it is called, whatever a claim did to it. */
    private static void nameTheCardsTrainAsItWas() throws Exception
    {
        if (model.getLocByName(RENAMED_TRAIN) != null) renameATrain(RENAMED_TRAIN, CARD_TRAIN);
    }

    /** The card's train paused through its own tick and the railway built so: the setup, the record and the train agree. */
    private static Locomotive theCardsTrainBuiltPaused() throws Exception
    {
        String label = I18n.f("autolayout.ui.menuLocomotiveActive", CARD_TRAIN);

        javax.swing.JMenuItem own = diagramMenuItem(square(CARD_TRAIN_SQUARE), label);

        assertNotNull(own, "precondition: the track diagram's right-click on " + CARD_TRAIN_SQUARE + " offers no " + label);

        click(own);

        theNextSetupGestureRebuilds();

        final Locomotive card = model.getLocByName(CARD_TRAIN);

        assertTrue(card.isAutonomyPaused() && session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: "
            + CARD_TRAIN + " was not built paused");

        rebuildTheCards();

        return card;
    }

    /**
     * A locomotive renamed after a card set it going stays going (OB-343): the setup, the record of how the railway was built
     * and the railway all had the train paused; the card's button sets it going, which the railway alone knows; and the rename
     * repairs the setup's list but not the record, which still named the old one.  The fold applies the trains the railway
     * lost against the record, by name, and removed nothing from a list holding the new name - so the rebuild paused the
     * train again, where the list the fold replaced used to be right.
     *
     * MUTATION: leave the record's list of the paused as it was when a locomotive is renamed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testALocomotiveRenamedAfterACardSetItGoingStaysGoing() throws Exception
    {
        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            final Locomotive card = theCardsTrainBuiltPaused();

            pressTheCardsButton(card, false);

            renameATrain(CARD_TRAIN, RENAMED_TRAIN);

            theNextSetupGestureRebuilds();

            assertFalse(card.isAutonomyPaused(), CARD_TRAIN + ", set going by its card and then renamed, was paused again by the"
                + " next rebuild (OB-343)");

            assertFalse(session.getPausedLocomotives().contains(RENAMED_TRAIN) || session.getPausedLocomotives().contains(CARD_TRAIN),
                "the setup still lists the train, set going by its card, after it was renamed: " + session.getPausedLocomotives());
        }
        finally
        {
            nameTheCardsTrainAsItWas();

            uncrowd();
        }
    }

    /** The variant (OB-343): renamed first, set going by the card after. */
    @Test
    public void testALocomotiveRenamedBeforeACardSetItGoingStaysGoing() throws Exception
    {
        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            final Locomotive card = theCardsTrainBuiltPaused();

            renameATrain(CARD_TRAIN, RENAMED_TRAIN);

            pressTheCardsButton(card, false);

            theNextSetupGestureRebuilds();

            assertFalse(card.isAutonomyPaused(), CARD_TRAIN + ", renamed and then set going by its card, was paused again by the"
                + " next rebuild (OB-343)");

            assertFalse(session.getPausedLocomotives().contains(RENAMED_TRAIN) || session.getPausedLocomotives().contains(CARD_TRAIN),
                "the setup still lists the train, set going by its card, after it was renamed: " + session.getPausedLocomotives());
        }
        finally
        {
            nameTheCardsTrainAsItWas();

            uncrowd();
        }
    }

    /**
     * Through Cancel (OB-343): a train the setup had paused is set going by its card with the editor open, an edit's rebuild
     * folds that, and the train is renamed; Cancel puts back the setup - whose snapshot the rename repaired - and what the
     * folds took, which named the old train, so the card's going was not applied and the train came out paused.
     *
     * MUTATION: leave the names in what the folds took as they were when a locomotive is renamed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testACancelKeepsACardsGoingOfATrainRenamedMeanwhile() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            final Locomotive card = theCardsTrainBuiltPaused();

            editor = openTheEditor();

            pressTheCardsButton(card, false);

            // AN EDIT whose rebuild folds the card's change
            clickTheEditorsTick(editorsPanel(editor), I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN));

            settleTheRebuild();

            assertFalse(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the fold did not take the card's going");

            renameATrain(CARD_TRAIN, RENAMED_TRAIN);

            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertFalse(card.isAutonomyPaused(), CARD_TRAIN + ", set going by its card and renamed with the editor open, came out"
                + " paused after the editor's Cancel (OB-343)");

            assertFalse(session.getPausedLocomotives().contains(RENAMED_TRAIN) || session.getPausedLocomotives().contains(CARD_TRAIN),
                "the setup lists the train after Cancel: " + session.getPausedLocomotives());
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            nameTheCardsTrainAsItWas();

            uncrowd();
        }
    }

    /** The other direction (OB-343): paused by its card with the editor open, renamed, Cancel - paused still, under its new name. */
    @Test
    public void testACancelKeepsACardsPauseOfATrainRenamedMeanwhile() throws Exception
    {
        java.awt.Window editor = null;

        try
        {
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);

            editor = openTheEditor();

            pressTheCardsButton(card, true);

            clickTheEditorsTick(editorsPanel(editor), I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN));

            settleTheRebuild();

            assertTrue(session.getPausedLocomotives().contains(CARD_TRAIN), "precondition: the fold did not take the card's pause");

            renameATrain(CARD_TRAIN, RENAMED_TRAIN);

            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertTrue(card.isAutonomyPaused(), CARD_TRAIN + ", paused by its card and renamed with the editor open, came out"
                + " running after the editor's Cancel (OB-343)");

            assertTrue(session.getPausedLocomotives().contains(RENAMED_TRAIN) && !session.getPausedLocomotives().contains(CARD_TRAIN),
                "the setup does not list the train under its new name, and only that, after Cancel: "
                + session.getPausedLocomotives());
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            nameTheCardsTrainAsItWas();

            uncrowd();
        }
    }

    /**
     * A locomotive deleted with the editor open is not written back by Cancel (OB-343): the pause a fold took for it is dropped
     * with the locomotive, as the setup's list and the snapshot are by the delete.
     *
     * MUTATION: leave the names the folds noted as they were when a locomotive is deleted, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testACancelDoesNotWriteBackADeletedTrainsPause() throws Exception
    {
        java.awt.Window editor = null;

        final String doomed = "Zz Doomed Train";

        try
        {
            theNextSetupGestureRebuilds();

            Locomotive made = model.newMM2Locomotive(doomed, 3499);

            editor = openTheEditor();

            // PAUSED, as a card pauses it: the train alone, and an edit's rebuild folds it
            made.setAutonomyPaused(true);

            clickTheEditorsTick(editorsPanel(editor), I18n.f("autolayout.ui.menuLocomotiveActive", HIS_TRAIN));

            settleTheRebuild();

            assertTrue(session.getPausedLocomotives().contains(doomed), "precondition: the fold did not take the pause");

            SwingUtilities.invokeAndWait(() -> model.deleteLoc(doomed));

            closeTheEditor(editor);

            editor = null;

            settleTheRebuild();

            assertFalse(session.getPausedLocomotives().contains(doomed), "Cancel wrote the name of a locomotive that was deleted"
                + " back into the setup's list of the paused: " + session.getPausedLocomotives());
        }
        finally
        {
            if (editor != null) closeTheEditor(editor);

            if (model.getLocByName(doomed) != null) model.deleteLoc(doomed);

            uncrowd();
        }
    }

    /** The editor's right-click on BottomMainB built and the tick on it clicked, as the editor's own menu does. */
    private static void clickTheEditorsTick(final AutonomyEditorPanel panel, final String label) throws Exception
    {
        final javax.swing.JMenuItem[] tick = new javax.swing.JMenuItem[1];

        final List<String> offered = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JPopupMenu menu = panel.buildTileMenu(mainB, null);

            texts(menu, offered);

            tick[0] = itemNamed(menu, label);
        });

        assertNotNull(tick[0], "precondition: the editor's right-click on BottomMainB offers no " + label + ": " + offered);

        click(tick[0]);
    }

    /**
     * The same for the bulk tool that writes only some of the trains (OB-343): Deactivate All Trains pauses the trains
     * standing on the layout and leaves the rest alone, and a train off the layout has a card whose button pauses it.  One
     * paused by its card stays paused.
     *
     * MUTATION: have the tool note every train's pause as built, not only the ones it changed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testDeactivateAllTrainsLeavesATrainOffTheLayoutPausedByItsCardPaused() throws Exception
    {
        try
        {
            // BUILT WITH NOBODY PAUSED, so that how the railway was built is not left over from another claim
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive away = model.getLocByName(NOWHERE);

            final List<String> standing = new ArrayList<>(new java.util.TreeSet<>(session.trainsWhereTheyStand().values()));

            assertFalse(standing.contains(NOWHERE), "precondition: " + NOWHERE + " stands on the layout: " + standing);

            String deactivate = I18n.f("autosetup.ui.menuDeactivateAllTrains", standing.size());

            // PAUSED BY ITS CARD: the train alone
            pressTheCardsButton(away, true);

            javax.swing.JMenuItem tool = diagramMenuItem(mainB, deactivate);

            assertNotNull(tool, "precondition: Bulk Tools offers no " + deactivate + ": " + diagramMenuTexts(mainB));

            click(tool);

            settleTheRebuild();

            for (String name : standing)
            {
                assertTrue(model.getLocByName(name).isAutonomyPaused(), "precondition: " + name + " is still active after "
                    + deactivate);
            }

            assertTrue(away.isAutonomyPaused(), NOWHERE + ", paused from its card's button, was set going by " + deactivate
                + ", which does not reach a train off the layout (OB-343)");

            assertTrue(session.getPausedLocomotives().contains(NOWHERE), NOWHERE + ", paused from its card's button, is not"
                + " in the setup after " + deactivate + ": " + session.getPausedLocomotives());
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * The other half of the claim above (OB-343): the train off the layout set going from its card's button, with the setup
     * still listing it as paused, stays going.
     *
     * MUTATION: have the tool note every train's pause as built, not only the ones it changed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testDeactivateAllTrainsLeavesATrainOffTheLayoutSetGoingByItsCardGoing() throws Exception
    {
        try
        {
            // BUILT WITH NOBODY PAUSED, so that how the railway was built is not left over from another claim
            theNextSetupGestureRebuilds();

            crowd();

            final Locomotive away = model.getLocByName(NOWHERE);

            final List<String> standing = new ArrayList<>(new java.util.TreeSet<>(session.trainsWhereTheyStand().values()));

            // PAUSED THROUGH THE DOOR, as a configuration loaded with it paused leaves it: the train and the setup together
            SwingUtilities.invokeAndWait(() -> ui.setLocomotiveActive(session, away, false));

            pump();

            assertTrue(session.getPausedLocomotives().contains(NOWHERE), "precondition: the setup does not list " + NOWHERE);

            settleTheCards();

            // SET GOING BY ITS CARD'S BUTTON: the train alone, and the setup still lists it
            pressTheCardsButton(away, false);

            assertTrue(session.getPausedLocomotives().contains(NOWHERE), "precondition: the setup has already been told "
                + NOWHERE + " is going: " + session.getPausedLocomotives());

            String deactivate = I18n.f("autosetup.ui.menuDeactivateAllTrains", standing.size());

            javax.swing.JMenuItem tool = diagramMenuItem(mainB, deactivate);

            assertNotNull(tool, "precondition: Bulk Tools offers no " + deactivate + ": " + diagramMenuTexts(mainB));

            click(tool);

            settleTheRebuild();

            for (String name : standing)
            {
                assertTrue(model.getLocByName(name).isAutonomyPaused(), "precondition: " + name + " is still active after "
                    + deactivate);
            }

            assertFalse(away.isAutonomyPaused(), NOWHERE + ", set going from its card's button, was paused again by "
                + deactivate + ", which does not reach a train off the layout (OB-343)");

            assertFalse(session.getPausedLocomotives().contains(NOWHERE), NOWHERE + ", set going from its card's button, is"
                + " still listed paused in the setup after " + deactivate + ": " + session.getPausedLocomotives());
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * Activate All Trains takes a train the setup lists as paused off the list when the train is going (OB-343; FR-126): the
     * tool reads the train, so such a train is not one it counts, and it goes through the door all the same.
     *
     * The state is the setup listing a train the railway was not built pausing - the train going, as a button left it - and
     * another train paused by its card's button, so that there is something for the tool to do.  The rebuild the tool asks
     * for takes the railway's list over the setup's only where the railway changed it since it was built, so what keeps the
     * listed train going is the tool taking it off the list itself.
     *
     * MUTATION: leave the trains the setup lists out of what Activate writes, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testActivateAllTrainsTakesAGoingTrainOffTheSetupsList() throws Exception
    {
        try
        {
            // BUILT WITH NOBODY PAUSED, so that how the railway was built is not left over from another claim
            theNextSetupGestureRebuilds();

            crowd();

            rebuildTheCards();

            final Locomotive card = model.getLocByName(CARD_TRAIN);
            final Locomotive listed = model.getLocByName(LISTED_TRAIN);

            // PAUSED BY ITS CARD'S BUTTON: the train alone
            pressTheCardsButton(card, true);

            // LISTED, AND GOING: the setup names it, and the railway was built with nobody paused
            session.setLocomotivePaused(LISTED_TRAIN, true);

            assertFalse(listed.isAutonomyPaused(), "precondition: " + LISTED_TRAIN + " is paused");

            // THE TRAIN IS READ, NOT THE LIST: one to set going
            String activate = I18n.f("autosetup.ui.menuActivateAllTrains", 1);

            javax.swing.JMenuItem tool = diagramMenuItem(mainB, activate);

            assertNotNull(tool, "precondition: Bulk Tools offers no " + activate + ": " + diagramMenuTexts(mainB));

            click(tool);

            settleTheRebuild();

            assertFalse(card.isAutonomyPaused(), CARD_TRAIN + " is still paused after " + activate);

            assertFalse(listed.isAutonomyPaused(), LISTED_TRAIN + ", going, and listed paused by the setup, was paused by the"
                + " rebuild " + activate + " asked for (OB-343)");

            assertFalse(session.getPausedLocomotives().contains(LISTED_TRAIN), activate + " left " + LISTED_TRAIN
                + ", which is going, listed as paused in the setup: " + session.getPausedLocomotives());

            assertTrue(session.getPausedLocomotives().isEmpty() && Layout.pausedNamesOf(model).isEmpty(), "trains are still"
                + " paused after " + activate + ": the setup lists " + session.getPausedLocomotives() + ", the railway "
                + Layout.pausedNamesOf(model));
        }
        finally
        {
            uncrowd();
        }
    }

    /** The Auto tab's pause button on the card of his train, or null where the tab has none. */
    private static javax.swing.AbstractButton autoTabPauseButton() throws Exception
    {
        return autoTabPauseButton(train);
    }

    /** The Auto tab's pause button on the card of a train, or null where the tab has none. */
    private static javax.swing.AbstractButton autoTabPauseButton(final Locomotive who) throws Exception
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
                    if (who.equals(loco.get(c))) found[0] = (javax.swing.AbstractButton) pause.get(c);
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

    /**
     * The three the claims pause, as the card's own button pauses them (the train alone).  The last is on a station put out of
     * use as well, and sorts with the stations out of use: the station is the stronger fact, and the card is greyed for it.
     */
    private static final String[] PAUSED = {"BDymf 457 DB", "ALCO UP", "118 028-0 DB"};

    /** The two whose stations the claims put out of use. */
    private static final String[] OUT_OF_USE = {"118 028-0 DB", "06 001 DRG"};

    /** A train on the railway's list to run that stands on no square, so its card has no station at all. */
    private static final String NOWHERE = "02 0314-1 DDR";

    /** Two trains the model has that stand on no square of the setup: one is paused, the other going. */
    private static final String UNPLACED_PAUSED = "BR 03 1022";

    private static final String UNPLACED_GOING = "BR 05 003";

    /** A name the configuration may list as paused that the model has no train for. */
    private static final String GHOST = "Zz Ghost Train";

    /**
     * The Auto tab's order once that is done: going on active stations, then paused, then the stations out of use (the paused
     * train among them, though by name it would lead the paused), then the train standing nowhere (though by name it would
     * lead them all) - each group by name.
     */
    private static final List<String> SORTED = java.util.Arrays.asList(HIS_TRAIN, "Big Boy 4019 UP", "BN Lights",
        "ALCO UP", "BDymf 457 DB", "06 001 DRG", "118 028-0 DB", NOWHERE);

    /** The crowd standing on the railway and in the setup, as the class puts his train, and the train that stands nowhere. */
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

            Locomotive nowhere = model.getLocByName(NOWHERE);

            assertNotNull(nowhere, "precondition: this data has no " + NOWHERE);
            assertNull(railway().getLocomotiveLocation(nowhere), "precondition: " + NOWHERE + " stands on the railway");

            railway().getLocomotivesToRun().add(nowhere);
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

                for (String name : new String[] {NOWHERE, UNPLACED_PAUSED, UNPLACED_GOING})
                {
                    Locomotive loco = model.getLocByName(name);

                    if (loco == null) continue;

                    loco.setAutonomyPaused(false);

                    railway().getLocomotivesToRun().remove(loco);

                    session.setLocomotivePaused(name, false);
                }

                session.setLocomotivePaused(GHOST, false);

                train.setAutonomyPaused(false);

                session.setLocomotivePaused(HIS_TRAIN, false);

                // EVERY OTHER TRAIN, so a claim that fails part way - after a tool has paused all that stand - leaves the
                // next one a railway with nobody paused
                for (Locomotive each : model.getLocomotives()) each.setAutonomyPaused(false);

                session.setLocomotivesPaused(new ArrayList<>(session.getPausedLocomotives()), false);
            }
            catch (java.io.IOException cannotSave)
            {
                throw new IllegalStateException(cannotSave);
            }
        });

        pump();
    }

    /** His train and the crowd: the trains that stand on the layout. */
    private static List<String> mine()
    {
        List<String> names = new ArrayList<>();

        names.add(HIS_TRAIN);

        for (String[] each : CROWD) names.add(each[0]);

        return names;
    }

    /** The trains these claims expect a card for: those that stand on the layout, and the one that stands nowhere. */
    private static List<String> cardNames()
    {
        List<String> names = mine();

        names.add(NOWHERE);

        return names;
    }

    /** The names on the Auto tab's cards, in the order the tab lays them out - only the ones this class stood there. */
    private static List<String> cardOrder() throws Exception
    {
        final List<String> names = new ArrayList<>();

        final List<String> ours = cardNames();

        SwingUtilities.invokeAndWait(() ->
        {
            List<java.awt.Component> all = new ArrayList<>();

            everything(ui.getContentPane(), all);

            for (java.awt.Component c : all)
            {
                if (!(c instanceof org.traincontrol.gui.AutoLocomotiveStatus)) continue;

                String name = ((org.traincontrol.gui.AutoLocomotiveStatus) c).getLocomotive().getName();

                if (ours.contains(name)) names.add(name);
            }
        });

        return names;
    }

    /**
     * How many of this class's cards the tab builds now: one for each of its trains the railway runs.  A rebuilt railway
     * no longer runs the train standing nowhere, so counting every name waited out the whole deadline (FR-136).
     */
    private static int cardsDue() throws Exception
    {
        final List<String> ours = cardNames();
        final int[] due = new int[1];

        SwingUtilities.invokeAndWait(() ->
        {
            Layout running = railway();

            if (running == null) return;

            for (Locomotive loc : new ArrayList<>(running.getLocomotivesToRun()))
            {
                if (ours.contains(loc.getName())) due[0]++;
            }
        });

        return due[0];
    }

    /** Waits for the cards to be built and for each to take its routes. */
    private static void settleTheCards() throws Exception
    {
        for (long end = System.currentTimeMillis() + 20000; cardOrder().size() < cardsDue()
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
     * The tab laid out with every train active, and then the trains changed behind it: three paused, as the card's own
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
     * of use, then one that stands nowhere, each group by name with capitals ignored (FR-126; Adam: "re-sort them to show
     * active first, then inactive, then ones on inactive stations (as currently), sorted alphabetically within those
     * groups").
     *
     * Two of the groups' edges are fixed here.  A train both paused and on a station out of use goes with the stations out
     * of use ("118 028-0 DB" would lead the paused by name), and a train on no station goes last of all ("02 0314-1 DDR"
     * would lead everything by name).
     *
     * MUTATION: leave the tab alone when it is opened, leave the paused trains among the active, put a paused train on a
     * station out of use with the paused, put the train on no station before the stations out of use, or order the names
     * with capitals counting, and this fails.
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
                + " then paused, then the trains on stations out of use (a paused one among them), then the train on no"
                + " station, each group by name");
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
     * What a run fires moves no card either (FR-126): the refresh the railway posts at every path start and end - the
     * Auto tab's own repaint on a run's say-so, and every callback the railway has, the Auto tab's among them - redraws the
     * cards in place, after a pause button has been pressed and a sort would now move one.
     *
     * Waited past the interval the repaint holds back for, since the repaint runs after it.
     *
     * MUTATION: sort the cards in the light repaint a run fires, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARunsRefreshOfTheCardsDoesNotMoveThemEither() throws Exception
    {
        try
        {
            goStale();

            openTheCommandsTab();

            assertEquals(cardOrder(), SORTED, "precondition: opening the tab did not sort it");

            final javax.swing.AbstractButton button = autoTabPauseButton();

            assertNotNull(button, "precondition: the Auto tab shows no pause button for " + HIS_TRAIN);

            SwingUtilities.invokeAndWait(button::doClick);

            settleTheCards();

            assertTrue(train.isAutonomyPaused(), "precondition: the click did not pause " + HIS_TRAIN);

            // A RUN'S REFRESH: what the window does for an arrival, and what the railway's callbacks do at a path's start
            // and end - from a thread of its own, as a locomotive's thread fires them
            SwingUtilities.invokeAndWait(() -> ui.repaintAutoLocList(true));

            railway().refreshUI();

            Thread.sleep(TrainControlUI.REPAINT_ROUTE_INTERVAL * 5);

            settleTheCards();

            assertEquals(cardOrder(), SORTED, "the refresh a run fires moved the cards, which only opening the tab sorts");
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * Bulk Tools has Deactivate All Trains and Activate All Trains, on the track diagram's right-click menu as in the editor:
     * each sets every train it covers, in the railway and in the setup, saves it with the configuration so a reload has it,
     * says how many it changed, and greys when there is nothing for it to change (FR-126; Adam: "a bulk tool to mass activate
     * and mass deactivate all trains").  Deactivate covers the trains standing on the layout - the ones on a station put out
     * of use too, which are on it though their cards are grey.
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

            // TWO STATIONS OUT OF USE under their trains, which the tools reach all the same
            SwingUtilities.invokeAndWait(() ->
            {
                for (String name : OUT_OF_USE) railway().getLocomotiveLocation(model.getLocByName(name)).setActive(false);
            });

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
                assertTrue(model.getLocByName(name).isAutonomyPaused(), name + " is still active after " + deactivate
                    + (OUT_OF_USE[0].equals(name) || OUT_OF_USE[1].equals(name) ? ", though its station is out of use" : ""));
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

    /**
     * Activate All Trains reaches every paused train - the ones on no square of the layout too, and a name the configuration
     * lists that the model has no train for - and Deactivate All Trains pauses only the trains standing on the layout (FR-126).
     * Both read the TRAIN's own flag where the model has the train, and the configuration's list only for a name it has none
     * for: the Auto tab's pause button changes the train alone, and the list learns of it later.
     *
     * Set up: one standing train paused by its card's button (the flag, not the list); one standing train the list still names
     * though its card's button set it going (the list, not the flag); a train on no square paused; a train on no square going;
     * and a listed name with no train.  The labels count the paused ones the tool covers and the going ones it covers, and
     * after Deactivate the train on no square that was going is still going, and after Activate nothing is paused or listed.
     *
     * MUTATION: have Activate cover only the trains standing, read the list before the train, or let Deactivate pause the
     * trains on no square, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testActivateAllTrainsReachesEveryPausedTrainAndTheToolsReadTheTrain() throws Exception
    {
        try
        {
            crowd();

            final Locomotive card = model.getLocByName("ALCO UP");
            final Locomotive resumed = model.getLocByName("BN Lights");
            final Locomotive away = model.getLocByName(UNPLACED_PAUSED);
            final Locomotive waiting = model.getLocByName(UNPLACED_GOING);

            assertNotNull(away, "precondition: this data has no " + UNPLACED_PAUSED);
            assertNotNull(waiting, "precondition: this data has no " + UNPLACED_GOING);
            assertNull(model.getLocByName(GHOST), "precondition: this data has a train called " + GHOST);

            final List<String> standing = new ArrayList<>(new java.util.TreeSet<>(session.trainsWhereTheyStand().values()));

            assertFalse(standing.contains(UNPLACED_PAUSED) || standing.contains(UNPLACED_GOING), "precondition: a train that"
                + " should stand on no square stands on the layout: " + standing);

            // PAUSED BY ITS CARD'S BUTTON: the flag alone
            card.setAutonomyPaused(true);

            // SET GOING BY ITS CARD'S BUTTON after the list had it: the list alone
            session.setLocomotivePaused(resumed.getName(), true);

            // NOT ON THE LAYOUT: one paused as a configuration load leaves it, the train, the list and the window's record of
            // how the railway was built agreeing (no card has a button to pause it), and a name with no train at all
            SwingUtilities.invokeAndWait(() -> ui.setLocomotiveActive(session, away, false));

            session.setLocomotivePaused(GHOST, true);

            pump();

            // THE LABELS: Deactivate counts the standing trains going (the resumed one among them); Activate the paused
            // anywhere (the card's, the one away, the ghost) and not the resumed one
            String deactivate = I18n.f("autosetup.ui.menuDeactivateAllTrains", standing.size() - 1);
            String activate = I18n.f("autosetup.ui.menuActivateAllTrains", 3);

            javax.swing.JMenuItem deactivateTool = diagramMenuItem(mainB, deactivate);
            javax.swing.JMenuItem activateTool = diagramMenuItem(mainB, activate);

            assertNotNull(deactivateTool, "Bulk Tools offers no " + deactivate + ", the trains standing that the train's own flag"
                + " says are going: " + diagramMenuTexts(mainB));

            assertNotNull(activateTool, "Bulk Tools offers no " + activate + ": the train paused by its card's button, the one"
                + " away from the layout and the name no train has: " + diagramMenuTexts(mainB));

            click(deactivateTool);

            for (int turn = 0; turn < 6; turn++) pump();

            for (String name : standing)
            {
                assertTrue(model.getLocByName(name).isAutonomyPaused(), name + " is still active after " + deactivate);
            }

            assertFalse(waiting.isAutonomyPaused(), deactivate + " paused " + UNPLACED_GOING + ", which stands on no square"
                + " of the layout");

            assertFalse(session.getPausedLocomotives().contains(UNPLACED_GOING), deactivate + " listed " + UNPLACED_GOING
                + ", which stands on no square of the layout");

            assertTrue(away.isAutonomyPaused(), deactivate + " set " + UNPLACED_PAUSED + " going");

            // EVERY PAUSED TRAIN: the standing ones, the one away, and the ghost
            activate = I18n.f("autosetup.ui.menuActivateAllTrains", standing.size() + 2);

            activateTool = diagramMenuItem(mainB, activate);

            assertNotNull(activateTool, "Bulk Tools offers no " + activate + " with every train standing paused, one away"
                + " paused and a name listed: " + diagramMenuTexts(mainB));

            click(activateTool);

            for (int turn = 0; turn < 6; turn++) pump();

            for (String name : standing)
            {
                assertFalse(model.getLocByName(name).isAutonomyPaused(), name + " is still paused after " + activate);
            }

            assertFalse(away.isAutonomyPaused(), activate + " left " + UNPLACED_PAUSED + " paused, which stands on no square of"
                + " the layout");

            assertFalse(session.getPausedLocomotives().contains(GHOST), activate + " left " + GHOST + " listed, which no"
                + " train answers to");

            assertTrue(hintText().contains(I18n.f("autosetup.ui.infoTrainsActivated", standing.size() + 2)),
                activate + " did not say how many it set going: " + hintText());

            assertFalse(waiting.isAutonomyPaused(), activate + " paused " + UNPLACED_GOING);

            AutonomySession reloaded = new AutonomySession(sandbox.getFolder());

            reloaded.open(pages());

            java.util.Set<String> covered = new java.util.TreeSet<>(standing);

            covered.add(UNPLACED_PAUSED);
            covered.add(GHOST);

            assertTrue(java.util.Collections.disjoint(reloaded.getPausedLocomotives(), covered), "a reload lists trains"
                + " paused that " + activate + " set going: " + reloaded.getPausedLocomotives());
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * Both tools do nothing while autonomy runs, and say why (FR-126): they are items of the setup's menu, which every setup
     * edit is refused from during a run, as the Is Active tick is.  Built at rest and clicked after a run has begun, which
     * is the way a menu opened at rest can reach a run.
     *
     * MUTATION: let a Bulk Tools item through while autonomy runs, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheBulkToolsAreRefusedWhileAutonomyRuns() throws Exception
    {
        try
        {
            crowd();

            final Locomotive alco = model.getLocByName("ALCO UP");

            SwingUtilities.invokeAndWait(() -> ui.setLocomotiveActive(session, alco, false));

            pump();

            final List<String> standing = new ArrayList<>(new java.util.TreeSet<>(session.trainsWhereTheyStand().values()));

            javax.swing.JMenuItem deactivate = diagramMenuItem(mainB,
                I18n.f("autosetup.ui.menuDeactivateAllTrains", standing.size() - 1));
            javax.swing.JMenuItem activate = diagramMenuItem(mainB, I18n.f("autosetup.ui.menuActivateAllTrains", 1));

            assertNotNull(deactivate, "precondition: Bulk Tools offers no Deactivate All Trains at rest: "
                + diagramMenuTexts(mainB));

            assertNotNull(activate, "precondition: Bulk Tools offers no Activate All Trains at rest: " + diagramMenuTexts(mainB));

            java.lang.reflect.Field running = TrainControlUI.class.getDeclaredField("stagingFlowActive");

            running.setAccessible(true);

            running.set(ui, true);

            try
            {
                for (javax.swing.JMenuItem tool : new javax.swing.JMenuItem[] {deactivate, activate})
                {
                    List<String> said = clickAndDismiss(tool);

                    assertTrue(said.contains(I18n.t("autolayout.errorCannotEditWhileRunning")), tool.getText() + ", clicked"
                        + " while autonomy ran, did not say why it did nothing: " + said);
                }
            }
            finally
            {
                running.set(ui, false);
            }

            for (String name : standing)
            {
                assertEquals(model.getLocByName(name).isAutonomyPaused(), name.equals("ALCO UP"), name + " was changed by a"
                    + " Bulk Tools item clicked while autonomy ran");
            }

            assertEquals(new java.util.TreeSet<>(session.getPausedLocomotives()), java.util.Collections.singleton("ALCO UP"),
                "a Bulk Tools item clicked while autonomy ran changed the setup's paused trains");
        }
        finally
        {
            uncrowd();
        }
    }

    /**
     * A paused train on no Point is drawn grey on the diagram, and the tools tell the diagram (FR-126): its grey is read from
     * the configuration's list, which Deactivate All Trains and Activate All Trains change, and the diagram was told only of
     * a train placed or put back.
     *
     * The train is placed in the setup on a square it is recorded facing a way no copy there faces, so the railway cannot
     * stand it on a Point.
     *
     * MUTATION: leave the diagram untold when the tools or the tick pause a train, or when a rebuild binds it again, and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testThePausedGreyOfATrainOnNoPointFollowsTheTools() throws Exception
    {
        final String stray = STRAY;

        try
        {
            assertNotNull(model.getLocByName(stray), "precondition: this data has no " + stray);

            SwingUtilities.invokeAndWait(() ->
            {
                session.placeLocomotive(mainA, stray);

                session.setFacing(mainA, org.traincontrol.automationui.TilePorts.Side.N);
            });

            pump();

            assertTrue(session.trainsOnNoPoint().containsKey(mainA), "precondition: " + stray + ", placed on BottomMainA facing"
                + " north, is not a train on no Point: " + session.trainsOnNoPoint());

            final List<String> standing = new ArrayList<>(new java.util.TreeSet<>(session.trainsWhereTheyStand().values()));

            assertTrue(standing.contains(stray), "precondition: " + stray + " does not stand on the layout: " + standing);

            // THE TICK'S OWN DOOR, which asks for no rebuild of the railway: the diagram is told by the door itself
            final Locomotive strayTrain = model.getLocByName(stray);

            SwingUtilities.invokeAndWait(() -> ui.setLocomotiveActive(session, strayTrain, false));

            pump();

            assertTrue(pausedOnNoPointOnTheDiagram().contains(mainA), "the diagram does not draw " + stray + ", paused by the"
                + " Is Active door and standing on no Point, grey: " + pausedOnNoPointOnTheDiagram());

            SwingUtilities.invokeAndWait(() -> ui.setLocomotiveActive(session, strayTrain, true));

            pump();

            assertFalse(pausedOnNoPointOnTheDiagram().contains(mainA), "the diagram still draws " + stray + ", set going by"
                + " the Is Active door, grey: " + pausedOnNoPointOnTheDiagram());

            // EVERY TRAIN PAUSED
            javax.swing.JMenuItem tool = diagramMenuItem(mainB, I18n.f("autosetup.ui.menuDeactivateAllTrains",
                standing.size()));

            assertNotNull(tool, "Bulk Tools offers no Deactivate All Trains (" + standing.size() + "): "
                + diagramMenuTexts(mainB));

            click(tool);

            for (int turn = 0; turn < 10; turn++)
            {
                Thread.sleep(100);

                pump();
            }

            assertTrue(session.pausedTrainsOnNoPoint().contains(mainA), "precondition: the setup does not list the train on no"
                + " Point as paused: " + session.pausedTrainsOnNoPoint());

            assertTrue(pausedOnNoPointOnTheDiagram().contains(mainA), "the diagram does not draw " + stray + ", paused by"
                + " Deactivate All Trains and standing on no Point, grey: " + pausedOnNoPointOnTheDiagram());

            // AND SET GOING AGAIN
            tool = diagramMenuItem(mainB, I18n.f("autosetup.ui.menuActivateAllTrains", standing.size()));

            assertNotNull(tool, "Bulk Tools offers no Activate All Trains (" + standing.size() + "): " + diagramMenuTexts(mainB));

            click(tool);

            for (int turn = 0; turn < 10; turn++)
            {
                Thread.sleep(100);

                pump();
            }

            assertFalse(pausedOnNoPointOnTheDiagram().contains(mainA), "the diagram still draws " + stray + ", set going by"
                + " Activate All Trains, grey: " + pausedOnNoPointOnTheDiagram());
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                session.setFacing(mainA, null);

                session.placeLocomotive(mainA, null);
            });

            uncrowd();
        }
    }

    /** The squares the diagram draws a paused train grey on, among the trains the railway stands on no Point. */
    @SuppressWarnings("unchecked")
    private static java.util.Set<TileKey> pausedOnNoPointOnTheDiagram() throws Exception
    {
        java.lang.reflect.Field driverField = TrainControlUI.class.getDeclaredField("diagramMonitorDriver");

        driverField.setAccessible(true);

        java.lang.reflect.Field monitorField = org.traincontrol.gui.DiagramMonitorDriver.class.getDeclaredField("monitor");

        monitorField.setAccessible(true);

        Object monitor = monitorField.get(driverField.get(ui));

        assertNotNull(monitor, "precondition: the diagram has no monitor");

        java.lang.reflect.Field paused = monitor.getClass().getDeclaredField("pausedOnNoPoint");

        paused.setAccessible(true);

        return (java.util.Set<TileKey>) paused.get(monitor);
    }

    /**
     * Clicks an item on the event thread and dismisses the message it puts up, answering for a modal dialog that would hold
     * the click for ever.
     *
     * @param item the item
     * @return the messages that were put up
     * @throws Exception from the window
     */
    private static List<String> clickAndDismiss(final javax.swing.JMenuItem item) throws Exception
    {
        return runAndDismiss(item::doClick);
    }

    /**
     * Runs an action on the event thread and dismisses the messages it puts up, as `clickAndDismiss` does for a click.
     *
     * @param action what to run
     * @return the messages that were put up
     * @throws Exception from the window
     */
    private static List<String> runAndDismiss(final Runnable action) throws Exception
    {
        final List<String> said = java.util.Collections.synchronizedList(new ArrayList<String>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        Thread answering = new Thread(() ->
        {
            while (going.get())
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                    final javax.swing.JOptionPane pane = find(((javax.swing.JDialog) window).getContentPane(),
                        javax.swing.JOptionPane.class);

                    if (pane == null) continue;

                    said.add(String.valueOf(pane.getMessage()));

                    SwingUtilities.invokeLater(() -> pane.setValue(javax.swing.JOptionPane.OK_OPTION));
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException stop)
                {
                    return;
                }
            }
        });

        answering.setDaemon(true);
        answering.start();

        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);

        try
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    action.run();
                }
                finally
                {
                    done.countDown();
                }
            });

            assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS), "the click did not come back; it put up: " + said);

            pump();

            return new ArrayList<>(said);
        }
        finally
        {
            going.set(false);

            answering.join(1000);
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
