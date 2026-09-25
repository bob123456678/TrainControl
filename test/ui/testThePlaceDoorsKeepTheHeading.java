package ui;

import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.ArrivalSidePrompt;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.FacingPrompt;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The track diagram's right-click Place, and Control+X then Control+V, keep a train's heading - on the frozen copy of
 * Adam's railway with its bars as he left them, so BottomMainA takes no arrivals from the east (Adam, 2026-09-25:
 * automated tests supersede the MTs they answer).
 *
 * His own 75 407 DB, from the run's copy of his locomotive data, made the active locomotive the way the window makes one
 * active; read back where the steps read it - the diagram's *... Is Facing* menu, and the autonomy editor's Why not
 * Moving?.  MT-581 is BottomMainB, which holds both headings; MT-585 and MT-498 are BottomMainA facing west, the way no
 * train may arrive there.
 *
 * @author Adam
 */
public class testThePlaceDoorsKeepTheHeading
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
            throw new SkipException("the diagram's doors belong to a window, and a window needs a display");
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

        assertNotNull(train, "precondition: this data has no " + HIS_TRAIN + ", the train the steps use");

        mainA = square("BottomMainA");
        mainB = square("BottomMainB");

        // NOTHING ELSE STANDING ABOUT, so every square the steps use is free for his train.
        for (Point point : railway().getPoints())
        {
            if (point.getCurrentLocomotive() != null) railway().moveLocomotive(null, point.getName(), true);
        }

        // THE ACTIVE LOCOMOTIVE, as the window makes one: mapped to the key button in use.
        ui.mapLocToCurrentButton(HIS_TRAIN);

        for (long end = System.currentTimeMillis() + 10000; (ui.getActiveLoc() == null
            || !HIS_TRAIN.equals(ui.getActiveLoc().getName())) && System.currentTimeMillis() < end; ) pump();

        assertNotNull(ui.getActiveLoc(), "precondition: " + HIS_TRAIN + " could not be made the active locomotive");

        assertEquals(ui.getActiveLoc().getName(), HIS_TRAIN, "precondition: another train is the active locomotive");

        // EVERY QUESTION A PLACEMENT MAY ASK, answered as the steps answer it: where the tail lies is "Not known".
        TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            FacingPrompt.answerForTests(null);
            ArrivalSidePrompt.answerForTests(null);
            TailCrossedPrompt.answerForTests(null);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** Nothing left open for the next claim: a claim that fails with a question up leaves it modal on the screen. */
    @AfterMethod(alwaysRun = true)
    public void closeEveryDialog() throws Exception
    {
        for (int round = 0; round < 50; round++)
        {
            pump();

            boolean any = false;

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                final javax.swing.JOptionPane pane = find(((javax.swing.JDialog) window).getContentPane(),
                    javax.swing.JOptionPane.class);

                if (pane == null) continue;

                any = true;

                SwingUtilities.invokeAndWait(() -> pane.setValue(Integer.valueOf(javax.swing.JOptionPane.CLOSED_OPTION)));
            }

            if (!any) return;

            Thread.sleep(100);
        }
    }

    /**
     * BottomMainB holds both headings, and the right-click Place puts the active train there facing the way it faced,
     * three times over from each heading, placed back between (MT-581).
     *
     * MUTATION: have the right-click Place choose a copy that is not the heading's, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheRightClickPlaceAtBottomMainBKeepsTheHeading() throws Exception
    {
        Map<String, Side> atB = placeableCopiesOf(mainB);

        assertTrue(atB.containsValue(Side.E) && atB.containsValue(Side.W), "precondition: BottomMainB does not hold both"
            + " headings, as MT-581 says it does: " + atB);

        TileKey origin = aStationHoldingBothHeadings();

        assertNotNull(origin, "precondition: the frozen railway has no other station holding both headings to start from");

        for (Side heading : new Side[] {Side.E, Side.W})
        {
            // STEP 1: standing on a station, facing the way noted.
            standOn(origin, heading);

            assertEquals(facingShown(origin), heading, "precondition: at " + name(origin) + " the Facing item does not"
                + " say what the train stands on");

            for (int round = 1; round <= 3; round++)
            {
                // STEPS 2 AND 3.
                placeByRightClick(mainB);

                assertEquals(facingShown(mainB), heading, "placed on BottomMainB by the right-click Place, a train facing "
                    + heading + " faces " + facingShown(mainB) + " (MT-581, time " + round + ")");

                // STEP 4: back where it came from, facing the way noted.
                placeByRightClick(origin);

                if (facingShown(origin) != heading) turnTo(origin, heading);

                assertEquals(facingShown(origin), heading, "precondition: the train could not be turned back to face "
                    + heading + " at " + name(origin));
            }
        }
    }

    /**
     * At BottomMainA, which takes no arrivals from the east, the right-click Place keeps a train facing west - and Why
     * not Moving? says trains may not arrive there facing that way, so autonomy will not start it there (MT-585).
     *
     * MUTATION: let the right-click Place choose among the copies trains may arrive at only, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheRightClickPlaceAtBottomMainAKeepsWest() throws Exception
    {
        // STEP 1: on BottomMainB, turned to face west through its Facing menu.
        standOn(mainB, Side.E);

        turnTo(mainB, Side.W);

        assertEquals(facingShown(mainB), Side.W, "precondition: the Facing menu did not turn the train west at"
            + " BottomMainB");

        // STEP 2.
        placeByRightClick(mainA);

        // STEP 3.
        assertEquals(facingShown(mainA), Side.W, "placed on BottomMainA by the right-click Place, a train facing west"
            + " faces " + facingShown(mainA) + " (MT-585)");

        String said = whyNotMoving(mainA);

        // Spaces as the hint line reads them: the report is HTML, and the reader folds runs of spaces into one.
        String expected = I18n.f("autolayout.why.startFacingBarred", "BottomMainA",
            I18n.t("autosetup.ui.menuArrivalsGroup")).replaceAll("\\s+", " ");

        assertTrue(said.contains(expected), "Why not Moving? for the train facing west at BottomMainA does not say trains"
            + " may not arrive there facing that way (MT-585): " + said);
    }

    /**
     * A train facing west at BottomMainA is still facing west there after Control+X and Control+V on BottomMainA (MT-498).
     *
     * MUTATION: have the paste choose among the copies trains may arrive at only, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testACutAndPasteAtBottomMainAKeepsWest() throws Exception
    {
        // STEP 1: at BottomMainA, facing west.
        standOn(mainA, Side.W);

        assertEquals(facingShown(mainA), Side.W, "precondition: the train does not face west at BottomMainA");

        // STEP 2: Control+X over it.
        assertEquals(gesture(mainA, KeyEvent.VK_X), Boolean.TRUE, "Control+X over BottomMainA was not taken");

        assertNull(railway().getLocomotiveLocation(train), "Control+X left the train on the railway, so this is not a cut");

        // STEP 3: Control+V over BottomMainA.
        assertEquals(gesture(mainA, KeyEvent.VK_V), Boolean.TRUE, "Control+V over BottomMainA was not taken");

        assertNotNull(railway().getLocomotiveLocation(train), "the paste put the train nowhere");

        assertEquals(facingShown(mainA), Side.W, "a train cut facing west at BottomMainA and pasted back faces "
            + facingShown(mainA) + " (MT-498)");
    }

    /**
     * Placed from the autonomy editor, the tail question is the list, in front of the editor, and choosing TunnelPre in
     * it answers it (MT-575).
     *
     * His 75 407 DB, given a length of 5, put on Tunnel from the editor's own Place door with "arrived from the north" -
     * the editor opened as Autonomy > Edit Autonomy opens it.  The question must not wait on the main window's diagram,
     * whose squares the editor covers; it is the list, owned by the editor so it opens over it, and the answer picked
     * there is the road the train stands on.
     *
     * MUTATION: put the question on the diagram with the editor open, or hang the list from the main window, and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testFromTheEditorTheTailQuestionIsTheListInFrontOfIt() throws Exception
    {
        TileKey tunnel = square("Tunnel");

        Integer lengthWas = train.getTrainLength();

        java.awt.Window editor = null;

        try
        {
            // STEP 1: a length of 5, and nothing standing at Tunnel - taken off the squares but left among the trains
            // autonomy runs, as a train is that is simply elsewhere: the editor offers Place only on a railway with
            // trains to run.
            train.setTrainLength(5);

            SwingUtilities.invokeAndWait(() ->
            {
                if (!railway().getLocomotivesToRun().contains(train))
                {
                    Point any = null;

                    for (Point point : railway().getPoints())
                    {
                        if (point.isDestination() && point.getCurrentLocomotive() == null && any == null) any = point;
                    }

                    if (any != null) railway().moveLocomotive(HIS_TRAIN, any.getName(), false);
                }

                for (Point point : railway().getPoints())
                {
                    if (point.getCurrentLocomotive() != null) railway().moveLocomotive(null, point.getName(), false);
                }
            });

            assertTrue(railway().getLocomotivesToRun().contains(train), "precondition: " + HIS_TRAIN + " is not among the"
                + " trains autonomy runs, so the editor offers no Place");

            // THE QUESTION ASKED FOR REAL: this class answers it "Not known" for the other claims.
            TailCrossedPrompt.answerForTests(null);

            // THE EDITOR, as Autonomy > Edit Autonomy > 1 - Main opens it.
            SwingUtilities.invokeLater(() -> ui.openAutonomyEditorOnPage(tunnel.getPage()));

            for (long end = System.currentTimeMillis() + 60000; editor == null && System.currentTimeMillis() < end; )
            {
                Thread.sleep(100);

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window.isShowing() && "LayoutEditor".equals(window.getClass().getSimpleName())) editor = window;
                }
            }

            assertNotNull(editor, "the autonomy editor did not open on " + tunnel.getPage());

            for (int turn = 0; turn < 6; turn++) pump();

            java.lang.reflect.Field panelField = editor.getClass().getDeclaredField("autonomyPanel");

            panelField.setAccessible(true);

            final AutonomyEditorPanel panel = (AutonomyEditorPanel) panelField.get(editor);

            assertNotNull(panel, "precondition: the editor opened without its autonomy panel");

            // THE EDITOR'S RIGHT-CLICK ON TUNNEL, and its Place item.
            final javax.swing.JMenuItem[] place = new javax.swing.JMenuItem[1];

            final List<String> offered = new ArrayList<>();

            Method label = Class.forName("org.traincontrol.gui.GraphLocAssign").getDeclaredMethod("menuLabelFor", Point.class);

            label.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JPopupMenu menu = panel.buildTileMenu(tunnel, null);

                texts(menu, offered);

                for (String copy : session.facingsFor(tunnel).keySet())
                {
                    Point point = railway().getPoint(copy);

                    try
                    {
                        if (point != null && place[0] == null) place[0] = itemNamed(menu, (String) label.invoke(null, point));
                    }
                    catch (ReflectiveOperationException failed)
                    {
                        throw new IllegalStateException(failed);
                    }
                }
            });

            assertNotNull(place[0], "the editor's right-click on Tunnel has no Place item: " + offered);

            SwingUtilities.invokeLater(place[0]::doClick);

            // THE PLACE DIALOG: his train, arrived from the north, OK.
            javax.swing.JDialog dialog = awaitDialogStarting(I18n.f("autolayout.ui.dialogEditOrAssignLocomotive", "")
                .replaceAll("\\s*$", ""));

            final Object assign = find(dialog.getContentPane(), Class.forName("org.traincontrol.gui.GraphLocAssign"));

            assertNotNull(assign, "the Place dialog carries no locomotive form");

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    java.lang.reflect.Field locs = assign.getClass().getDeclaredField("locAssign");
                    java.lang.reflect.Field side = assign.getClass().getDeclaredField("arrivedFrom");

                    locs.setAccessible(true);
                    side.setAccessible(true);

                    ((javax.swing.JComboBox<?>) locs.get(assign)).setSelectedItem(HIS_TRAIN);

                    // THE SIDE, where the dialog asks it: at a square whose copies are the directions, the copy says it.
                    javax.swing.JComboBox<?> sides = (javax.swing.JComboBox<?>) side.get(assign);

                    if (sides != null) sides.setSelectedItem(org.traincontrol.gui.ArrivalSidePrompt.labelFor("N"));
                }
                catch (ReflectiveOperationException failed)
                {
                    throw new IllegalStateException(failed);
                }
            });

            final javax.swing.JOptionPane form = find(dialog.getContentPane(), javax.swing.JOptionPane.class);

            SwingUtilities.invokeLater(() -> form.setValue(TrainControlUI.OK_CANCEL_OPTS[0]));

            // THE QUESTION: the list, in front of the editor.
            javax.swing.JDialog question = awaitDialogStarting(I18n.t("autolayout.ui.askArrivalSideTitle"));

            java.lang.reflect.Field armed = TailCrossedPrompt.class.getDeclaredField("armed");

            armed.setAccessible(true);

            assertNull(armed.get(null), "with the editor open the tail question was put on the main window's diagram,"
                + " which the editor covers (MT-575)");

            assertTrue(question.isShowing(), "the tail question's list is not on screen");

            assertEquals(question.getOwner(), editor, "the tail question's list does not belong to the editor, so it can"
                + " open behind it (MT-575): its owner is " + question.getOwner());

            // TUNNELPRE, CHOSEN IN IT.
            final javax.swing.JList<?> list = find(question.getContentPane(), javax.swing.JList.class);

            assertNotNull(list, "the tail question has no list");

            final int[] at = {-1};

            SwingUtilities.invokeAndWait(() ->
            {
                for (int i = 0; i < list.getModel().getSize(); i++)
                {
                    if (String.valueOf(list.getModel().getElementAt(i)).contains("TunnelPre")) at[0] = i;
                }

                if (at[0] >= 0) list.setSelectedIndex(at[0]);
            });

            assertTrue(at[0] >= 0, "the tail question's list does not offer TunnelPre");

            final javax.swing.JOptionPane asked = find(question.getContentPane(), javax.swing.JOptionPane.class);

            SwingUtilities.invokeLater(() -> asked.setValue(I18n.t("ui.ok")));

            for (long end = System.currentTimeMillis() + 10000; question.isShowing() && System.currentTimeMillis() < end; )
            {
                Thread.sleep(50);
            }

            for (int turn = 0; turn < 6; turn++) pump();

            // ANSWERED: the train stands on Tunnel with TunnelPre's road behind it.
            Point standing = railway().getLocomotiveLocation(train);

            assertNotNull(standing, "the Place dialog put the train nowhere");

            TailCrossedPrompt.Choice tunnelPre = null;

            for (TailCrossedPrompt.Choice choice : TailCrossedPrompt.choicesFor(railway(), standing, "N", 5, null))
            {
                if (choice.getFarthest().getName().startsWith("TunnelPre")) tunnelPre = choice;
            }

            assertNotNull(tunnelPre, "precondition: a five-unit train at Tunnel from the north is not offered TunnelPre");

            assertEquals(named(standing.getArrivedAlong()), named(tunnelPre.getRoad()), "choosing TunnelPre in the list did"
                + " not give the train at Tunnel TunnelPre's road (MT-575)");
        }
        finally
        {
            TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);

            closeEveryDialog();

            if (editor != null) closeTheEditor(editor);

            train.setTrainLength(lengthWas);
        }
    }

    /**
     * With the autonomy editor open but minimised, the tail question's list belongs to the main window - where it can be
     * seen - and not to the minimised editor (RLA-C4).
     *
     * MT-575 hung the list from the editor whenever one was open, and a minimised editor is still open: on Windows a
     * window owned by a minimised frame is hidden, so the question was a modal nobody could see, holding the application
     * until the editor was restored.  Asked here for his 75 407 DB, 5 long, on Tunnel from the north, as the main
     * window's own doors ask it.
     *
     * MUTATION: hang the list from any editor that is open, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testWithTheEditorMinimisedTheTailQuestionBelongsToTheMainWindow() throws Exception
    {
        TileKey tunnel = square("Tunnel");

        Integer lengthWas = train.getTrainLength();

        java.awt.Window editor = null;

        try
        {
            train.setTrainLength(5);

            // NO EDITOR LEFT FROM THE CLAIM BEFORE: closing one finishes after the window has gone.
            for (long end = System.currentTimeMillis() + 20000; (ui.isLayoutEditorOpen()
                || ui.whyLayoutCannotBeEdited() != null) && System.currentTimeMillis() < end; )
            {
                Thread.sleep(100);

                pump();
            }

            assertFalse(ui.isLayoutEditorOpen(), "precondition: an editor from the claim before is still open");

            assertNull(ui.whyLayoutCannotBeEdited(), "precondition: the layout cannot be edited: "
                + ui.whyLayoutCannotBeEdited());

            // THE EDITOR, as Autonomy > Edit Autonomy opens it - and then minimised.
            SwingUtilities.invokeLater(() -> ui.openAutonomyEditorOnPage(tunnel.getPage()));

            for (long end = System.currentTimeMillis() + 60000; editor == null && System.currentTimeMillis() < end; )
            {
                Thread.sleep(100);

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window.isShowing() && "LayoutEditor".equals(window.getClass().getSimpleName())) editor = window;
                }
            }

            assertNotNull(editor, "the autonomy editor did not open on " + tunnel.getPage() + "; showing: " + showing()
                + "; says: " + messagesShowing());

            final java.awt.Frame minimising = (java.awt.Frame) editor;

            SwingUtilities.invokeAndWait(() -> minimising.setExtendedState(java.awt.Frame.ICONIFIED));

            for (long end = System.currentTimeMillis() + 10000; (minimising.getExtendedState() & java.awt.Frame.ICONIFIED)
                == 0 && System.currentTimeMillis() < end; ) Thread.sleep(50);

            assertTrue((minimising.getExtendedState() & java.awt.Frame.ICONIFIED) != 0, "precondition: the editor could"
                + " not be minimised");

            // HIS TRAIN ON TUNNEL FROM THE NORTH, and the question asked as the main window's doors ask it.
            final Point[] at = new Point[1];

            SwingUtilities.invokeAndWait(() ->
            {
                for (Point point : railway().getPoints())
                {
                    if (point.getCurrentLocomotive() != null) railway().moveLocomotive(null, point.getName(), false);
                }

                railway().moveLocomotive(HIS_TRAIN, "Tunnel (southbound)", false);

                at[0] = railway().getPoint("Tunnel (southbound)");
            });

            assertTrue(at[0] != null && at[0].getCurrentLocomotive() == train, "precondition: " + HIS_TRAIN + " could not"
                + " be stood on Tunnel");

            TailCrossedPrompt.answerForTests(null);

            SwingUtilities.invokeLater(() -> TailCrossedPrompt.askAfterPlacement(railway(), at[0], "N", 5, HIS_TRAIN, ui,
                null, null));

            javax.swing.JDialog question = null;

            for (long end = System.currentTimeMillis() + 15000; question == null && System.currentTimeMillis() < end; )
            {
                Thread.sleep(50);

                // SHOWING OR NOT: a list hung from the minimised editor is hidden with it.
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window instanceof javax.swing.JDialog && window.isVisible() && I18n.t("autolayout.ui.askArrivalSideTitle")
                        .equals(((javax.swing.JDialog) window).getTitle())) question = (javax.swing.JDialog) window;
                }
            }

            assertNotNull(question, "precondition: no tail question was asked for " + HIS_TRAIN + " at Tunnel from the"
                + " north: " + showing());

            assertEquals(question.getOwner(), ui, "with the editor minimised, the tail question's list belongs to "
                + question.getOwner() + " - hidden with a minimised editor, a modal nobody can see (RLA-C4)");
        }
        finally
        {
            TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);

            // THE QUESTION TAKEN DOWN BY ITS OWN PANE, shown or hidden: a hidden one is past closeEveryDialog.
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isVisible()) continue;

                final javax.swing.JOptionPane pane = find(((javax.swing.JDialog) window).getContentPane(),
                    javax.swing.JOptionPane.class);

                if (pane != null) SwingUtilities.invokeAndWait(() -> pane.setValue(
                    Integer.valueOf(javax.swing.JOptionPane.CLOSED_OPTION)));
            }

            closeEveryDialog();

            if (editor != null) closeTheEditor(editor);

            train.setTrainLength(lengthWas);
        }
    }

    /**
     * Closes the editor as its window's close button does, answering Yes to leaving without saving - so the window lets
     * the layout be edited again.  A close answered with anything else keeps the editor, and one disposed then leaves the
     * main window believing an editor is still open, which refuses the next claim's.
     */
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

    /** The messages of the dialogs on screen, for a failure to show. */
    private static String messagesShowing()
    {
        StringBuilder out = new StringBuilder();

        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

            javax.swing.JOptionPane pane = find(((javax.swing.JDialog) window).getContentPane(), javax.swing.JOptionPane.class);

            if (pane != null) out.append('[').append(pane.getMessage()).append(']');
        }

        return out.toString();
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

    /** A road as the names of its rails, which survive a rebuild of the railway. */
    private static List<String> named(List<Edge> road)
    {
        List<String> out = new ArrayList<>();

        if (road != null) for (Edge edge : road) out.add(edge.getStart().getName() + " -> " + edge.getEnd().getName());

        return out;
    }

    private static javax.swing.JDialog awaitDialogStarting(String title) throws Exception
    {
        for (long end = System.currentTimeMillis() + 15000; System.currentTimeMillis() < end; )
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && String.valueOf(((javax.swing.JDialog) window).getTitle()).startsWith(title))
                {
                    return (javax.swing.JDialog) window;
                }
            }

            Thread.sleep(50);
        }

        fail("no dialog titled \"" + title + "...\" appeared; showing: " + showing());

        return null;
    }

    // ---------------------------------------------------------------- the doors

    /** Right-clicks the square on the track diagram and chooses Place (the active train), as the steps do. */
    private static void placeByRightClick(TileKey square) throws Exception
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

        final javax.swing.JMenuItem[] place = new javax.swing.JMenuItem[1];

        final String label = I18n.f("layout.ui.menuPlaceLocomotive", HIS_TRAIN);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) make.newInstance(ui, square, square, options);

                place[0] = itemNamed(menu, label);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        assertNotNull(place[0], "the right-click menu on " + name(square) + " has no " + label + " item");

        assertTrue(place[0].isEnabled(), "the right-click menu on " + name(square) + " greys " + label + ": "
            + place[0].getToolTipText());

        SwingUtilities.invokeLater(place[0]::doClick);

        for (long end = System.currentTimeMillis() + 10000; !standsOn(square) && System.currentTimeMillis() < end; )
        {
            Thread.sleep(50);
        }

        pump();

        assertTrue(standsOn(square), "the right-click Place did not put " + HIS_TRAIN + " on " + name(square)
            + "; showing: " + showing());
    }

    /** Which way the diagram's *... Is Facing* menu on this square says the train there faces. */
    private static Side facingShown(TileKey square) throws Exception
    {
        final Side[] shown = new Side[1];

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu menu = ui.buildAutonomyFacingMenu(square);

            if (menu == null) return;

            for (java.awt.Component child : menu.getMenuComponents())
            {
                if (!(child instanceof javax.swing.JRadioButtonMenuItem)) continue;

                javax.swing.JRadioButtonMenuItem item = (javax.swing.JRadioButtonMenuItem) child;

                if (!item.isSelected()) continue;

                for (Side side : Side.values())
                {
                    if (I18n.t("autosetup.ui.facing" + side.name()).equals(item.getText())) shown[0] = side;
                }
            }
        });

        return shown[0];
    }

    /** Chooses a heading on the diagram's *... Is Facing* menu, as the steps turn a train. */
    private static void turnTo(TileKey square, Side heading) throws Exception
    {
        final javax.swing.JMenuItem[] item = new javax.swing.JMenuItem[1];

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JMenu menu = ui.buildAutonomyFacingMenu(square);

            if (menu != null) item[0] = itemNamed(menu, I18n.t("autosetup.ui.facing" + heading.name()));
        });

        assertNotNull(item[0], "the Facing menu on " + name(square) + " offers no " + heading);

        SwingUtilities.invokeLater(item[0]::doClick);

        for (long end = System.currentTimeMillis() + 10000; facingShown(square) != heading
            && System.currentTimeMillis() < end; ) Thread.sleep(50);
    }

    /** Presses Why not Moving? in the autonomy editor on this square's page, clicks the square, and reads the answer. */
    private static String whyNotMoving(TileKey square) throws Exception
    {
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, square.getPage(), () -> { });

        panel.setRunningLayoutSource(() -> model.getAutoLayout());
        panel.setLayoutSource(() -> model.getAutoLayout());

        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("whyButton");

        field.setAccessible(true);

        final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field.get(panel);

        SwingUtilities.invokeAndWait(why::doClick);

        SwingUtilities.invokeAndWait(() -> panel.tileClicked(square, session.getGraph().getTiles().get(square), false));

        String working = I18n.t("autolayout.ui.whyWorking");

        String said = hintOf(panel);

        for (long end = System.currentTimeMillis() + 30000; (said == null || said.contains(working))
            && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            pump();

            said = hintOf(panel);
        }

        return String.valueOf(said);
    }

    /** Presses a key over a square, through the diagram's own gesture door. */
    private static Object gesture(TileKey over, int key) throws Exception
    {
        final Method door = TrainControlUI.class.getDeclaredMethod("locomotiveGestureOnDiagram", int.class,
            boolean.class);

        door.setAccessible(true);

        final Object[] handled = new Object[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ui.setHoveredDiagramTile(over.getPage(), over.getX(), over.getY());

                handled[0] = door.invoke(ui, key, true);
            }
            catch (Exception refused)
            {
                handled[0] = refused;
            }
        });

        pump();

        return handled[0];
    }

    // ---------------------------------------------------------------- the railway

    /** Stands his train on the copy of this square facing this way - on the railway, as a hand drive leaves it. */
    private static void standOn(TileKey square, Side heading) throws Exception
    {
        String copy = null;

        for (Map.Entry<String, Side> each : session.facingsFor(square).entrySet())
        {
            if (each.getValue() == heading && railway().getPoint(each.getKey()) != null) copy = each.getKey();
        }

        assertNotNull(copy, "precondition: " + name(square) + " has no copy facing " + heading + ": "
            + session.facingsFor(square));

        final String onto = copy;

        SwingUtilities.invokeAndWait(() ->
        {
            Point was = railway().getLocomotiveLocation(train);

            if (was != null) railway().moveLocomotive(null, was.getName(), false);

            railway().moveLocomotive(HIS_TRAIN, onto, false, true);
        });

        pump();

        assertTrue(standsOn(square), "precondition: " + HIS_TRAIN + " could not be stood on " + onto);
    }

    private static boolean standsOn(TileKey square)
    {
        Point at = railway().getLocomotiveLocation(train);

        return at != null && session.facingsFor(square).containsKey(at.getName());
    }

    /** A station other than the two the steps use, whose copies a train may stand on hold both headings. */
    private static TileKey aStationHoldingBothHeadings()
    {
        List<TileKey> stations = new ArrayList<>();

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (session.getStore().isStation(key) && !key.equals(mainA) && !key.equals(mainB)) stations.add(key);
        }

        stations.sort(java.util.Comparator.comparing(TileKey::toString));

        for (TileKey station : stations)
        {
            Map<String, Side> copies = placeableCopiesOf(station);

            if (copies.containsValue(Side.E) && copies.containsValue(Side.W)) return station;
        }

        return null;
    }

    /** The copies of a square a train may stand on, with the way each faces. */
    private static Map<String, Side> placeableCopiesOf(TileKey square)
    {
        Map<String, Side> out = new LinkedHashMap<>();

        for (Map.Entry<String, Side> copy : session.facingsFor(square).entrySet())
        {
            Point point = railway().getPoint(copy.getKey());

            if (point != null && point.isDestination()) out.put(copy.getKey(), copy.getValue());
        }

        return out;
    }

    /** The square the setup gives this name. */
    private static TileKey square(String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new SkipException("this railway has no " + name);
    }

    private static String name(TileKey square)
    {
        return session.getStore().getPointName(square);
    }

    private static String hintOf(AutonomyEditorPanel panel) throws Exception
    {
        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("hint");

        field.setAccessible(true);

        final String[] text = new String[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                text[0] = ((javax.swing.JLabel) field.get(panel)).getText();
            }
            catch (IllegalAccessException cannot)
            {
                throw new IllegalStateException(cannot);
            }
        });

        return text[0] == null ? null : text[0].replaceAll("<[^>]*>", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"").replaceAll("\\s+", " ").trim();
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

    /** The dialogs on screen, for a failure message. */
    private static String showing()
    {
        StringBuilder out = new StringBuilder();

        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (window instanceof javax.swing.JDialog && window.isShowing())
            {
                out.append(out.length() > 0 ? ", " : "").append('"').append(((javax.swing.JDialog) window).getTitle())
                    .append('"');
            }
        }

        return out.length() == 0 ? "nothing" : out.toString();
    }

    /**
     * The railway as the model has it NOW.  Turning a train with the Facing menu saves the setup and rebuilds the
     * running railway, so a railway read once and kept is the one before - a train moved on it stands nowhere the menus
     * look.
     */
    private static Layout railway()
    {
        return model.getAutoLayout();
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
