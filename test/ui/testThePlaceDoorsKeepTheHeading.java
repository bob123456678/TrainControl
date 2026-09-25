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
