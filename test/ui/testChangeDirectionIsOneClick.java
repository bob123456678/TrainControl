package ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomy.ArrivalSidePrompt;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Teardown;

/**
 * A station's right-click menu turns the train standing there in one click (FR-181): "Change Direction (now facing
 * East)" and "Change Arrived From (currently West)" sit on the menu itself, on the track diagram and in the autonomy
 * editor alike, in place of the "Is Facing..." submenu.  Each click is asked of what it changed, in the setup and on
 * the railway; a square with one way is grayed with the reason as its tooltip, and an arrival side with no single other
 * is the list of sides.
 *
 * MUTATION: put the items in one "Is Facing..." submenu again, the old two levels, and every claim fails.
 *
 * @author Adam
 */
public class testChangeDirectionIsOneClick
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    private org.json.JSONObject asFound;
    private LayoutEditor editor;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        support.AskingWindows.Railway opened = support.AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        support.Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session == null ? null : session.getStore().getActiveConfiguration(),
            "precondition: no autonomy configuration on the snapshot");

        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            support.Components.settle();
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Teardown.close(sandbox, model, ui);
    }

    @BeforeMethod
    public void railwayFromTheSetup() throws Exception
    {
        asFound = session.snapshotSetup();

        rebuild();
    }

    @AfterMethod(alwaysRun = true)
    public void putBack() throws Exception
    {
        support.Mouse.closeMenus();

        if (editor != null)
        {
            final LayoutEditor closing = editor;

            editor = null;

            SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        session.restoreSetup(asFound);

        rebuild();
    }

    /**
     * In the autonomy editor, Change Direction is one item on the station's own menu, naming the way the train faces;
     * a click turns it to the other way the square allows, in the setup and on the railway, and the menu then names
     * that way.  Not a submenu, which a click would hang on rather than fail.
     *
     * MUTATION: leave out the two-facing branch of `EditorTileMenus.changeDirectionItem`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheEditorTurnsTheTrainInOneClick() throws Exception
    {
        TileKey square = squareWithTwoFacings();

        Side now = facingOnTheRailway(square);
        Side other = theOther(session.facingChoices(square), now);

        JMenuItem change = topLevel(editorMenu(square), changeDirection(now));

        assertFalse(change instanceof JMenu, "Change Direction at " + square + " is a submenu, not one click");

        assertTrue(change.isEnabled(), "Change Direction at " + square + " is grayed where the train can face "
            + other + " too");

        support.Mouse.choose(change);

        rebuild();

        assertEquals(session.getFacing(square), other, "Change Direction at " + square + " did not write " + other
            + " to the setup");

        assertEquals(facingOnTheRailway(square), other, "Change Direction at " + square + " did not turn the train on"
            + " the railway to " + other);

        topLevel(editorMenu(square), changeDirection(other));
    }

    /**
     * The track diagram's menu carries the same one-click item, and its click turns the train the same way.
     *
     * MUTATION: leave out the two-facing branch of `EditorTileMenus.changeDirectionItem`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheDiagramTurnsTheTrainInOneClick() throws Exception
    {
        TileKey square = squareWithTwoFacings();

        Side now = facingOnTheRailway(square);
        Side other = theOther(session.facingChoices(square), now);

        JMenuItem change = topLevel(support.StationDoors.diagramMenu(ui, square), changeDirection(now));

        assertFalse(change instanceof JMenu, "the track diagram's Change Direction at " + square + " is a submenu, not"
            + " one click");

        assertTrue(change.isEnabled(), "the track diagram's Change Direction at " + square + " is grayed");

        support.Mouse.choose(change);

        rebuild();

        assertEquals(session.getFacing(square), other, "the track diagram's Change Direction at " + square + " did not"
            + " write " + other + " to the setup");

        assertEquals(facingOnTheRailway(square), other, "the track diagram's Change Direction at " + square + " did not"
            + " turn the train on the railway to " + other);

        topLevel(support.StationDoors.diagramMenu(ui, square), changeDirection(other));
    }

    /**
     * Where the track allows one facing, Change Direction is shown with the same words, grayed, with the reason as its
     * tooltip, on both menus.
     */
    @Test
    public void testOneWayIsGrayedWithItsReason() throws Exception
    {
        TileKey square = null;

        for (TileKey at : stationsWithATrain())
        {
            if (session.facingChoices(at).size() == 1) square = at;
        }

        if (square == null)
        {
            for (TileKey at : session.getStore().getNamedTiles())
            {
                if (square == null && session.getStore().isStation(at) && model.getLayout(at.getPage()) != null
                    && session.facingChoices(at).size() == 1) square = at;
            }

            assertNotNull(square, "precondition: no station of the snapshot allows one facing");

            support.StandingTrain.standOn(model, session, support.StandingTrain.standingNowhere(model, session), square);
        }

        Side only = session.facingChoices(square).get(0);

        String reason = I18n.t("autosetup.ui.hintFacingOnlyOne");

        for (JPopupMenu menu : new JPopupMenu[] {support.StationDoors.diagramMenu(ui, square), editorMenu(square)})
        {
            JMenuItem change = topLevel(menu, changeDirection(only));

            assertFalse(change.isEnabled(), "Change Direction at " + square + ", which allows only " + only
                + ", is not grayed");

            assertTrue(String.valueOf(change.getToolTipText()).contains(reason), "the grayed Change Direction at "
                + square + " does not say why: " + change.getToolTipText());
        }
    }

    /**
     * Where the train can have come in by two sides and one is recorded, Change Arrived From names it and a click
     * records the other, in the setup and on the railway; both menus offer it.
     */
    @Test
    public void testTwoSidesAreOneClick() throws Exception
    {
        TileKey square = squareWithTwoSides();

        String was = session.getArrivedFrom(square);
        String other = theOther(sides(square), was);

        topLevel(support.StationDoors.diagramMenu(ui, square), changeArrivedFrom(was));

        JMenuItem change = topLevel(editorMenu(square), changeArrivedFrom(was));

        assertTrue(change.isEnabled() && !(change instanceof JMenu), "Change Arrived From at " + square + " is not one"
            + " click to " + other);

        support.Mouse.choose(change);

        rebuild();

        assertEquals(session.getArrivedFrom(square), other, "Change Arrived From at " + square + " did not record "
            + other + " in the setup");

        assertEquals(standing(square).getArrivedFrom(), other, "Change Arrived From at " + square + " did not record "
            + other + " for the train on the railway");
    }

    /**
     * With no single other side, Change Arrived From is the list of sides: where none is recorded it says no side,
     * and where the recorded one is not offered here the list still states it, grayed.  A side is never cleared.
     */
    @Test
    public void testNoSingleOtherSideIsAList() throws Exception
    {
        TileKey square = squareWithTwoSides();

        List<String> sides = sides(square);

        String gone = null;

        for (String side : new String[] {"N", "E", "S", "W"})
        {
            if (gone == null && !sides.contains(side)) gone = side;
        }

        // NONE RECORDED: the list, each side offered, none ticked
        session.setArrivedFrom(square, null);
        standing(square).setArrivedFrom(null);

        for (JPopupMenu menu : new JPopupMenu[] {support.StationDoors.diagramMenu(ui, square), editorMenu(square)})
        {
            JMenuItem list = topLevel(menu, I18n.t("autosetup.ui.menuChangeArrivedFromUnknown"));

            assertTrue(list instanceof JMenu, "Change Arrived From at " + square + ", with no side recorded, is not the"
                + " list of sides");

            List<String> offered = texts((JMenu) list);

            for (String side : sides)
            {
                assertTrue(offered.contains(ArrivalSidePrompt.labelFor(side)), "the list at " + square + " does not"
                    + " offer " + side + ": " + offered);
            }

            assertEquals(offered.size(), sides.size(), "the list at " + square + " offers something besides the sides,"
                + " such as a way to clear it: " + offered);
        }

        // RECORDED, NOT OFFERED HERE: said, in the list
        session.setArrivedFrom(square, gone);
        standing(square).setArrivedFrom(gone);

        JMenuItem list = topLevel(editorMenu(square), changeArrivedFrom(gone));

        assertTrue(list instanceof JMenu, "Change Arrived From at " + square + ", recorded " + gone + " which the track"
            + " has not, is not the list of sides");

        String stated = I18n.f("autosetup.ui.arrivedFromNotOnTrack", ArrivalSidePrompt.labelFor(gone));

        JMenuItem said = support.Components.find(list, JMenuItem.class, i -> stated.equals(i.getText()));

        assertNotNull(said, "the list at " + square + " does not say " + gone + " is recorded: " + texts((JMenu) list));

        assertFalse(said.isEnabled(), "the recorded side the track has not is offered as a choice at " + square);
    }

    /**
     * In English the side is capitalized, as the menus are title case; English is the base
     * bundle, with no messages_en of its own.
     *
     * MUTATION: have `menuSide` capitalize only where the language is "en", and this fails.
     */
    @Test(timeOut = 180000)
    public void testEnglishMenusCapitalizeTheSide() throws Exception
    {
        assertEquals(I18n.t("autosetup.ui.menuChangeDirectionUnknown"), "Change Direction",
            "precondition: the messages on show are not English");

        TileKey square = squareWithTwoFacings();

        Side now = facingOnTheRailway(square);

        String word = now == Side.N ? "North" : now == Side.S ? "South" : now == Side.E ? "East" : "West";

        topLevel(editorMenu(square), "Change Direction (now facing " + word + ")");

        assertEquals(I18n.f("autosetup.ui.menuChangeDirection", org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel
            .menuSide("E")), "Change Direction (now facing East)", "the English menu does not say \"now facing East\"");
    }

    /**
     * A recorded arrival side that is not N, S, E or W, which a file can hold, still lets both menus open, and the side
     * reads as north there, as it does in the train window.
     *
     * MUTATION: have `menuSide` look up the recorded side as it is, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAnOddRecordedSideStillOpensTheMenu() throws Exception
    {
        TileKey square = stationsWithATrain().get(0);

        standing(square).setArrivedFrom("X");
        session.setArrivedFrom(square, "X");

        String north = changeArrivedFrom("N");

        topLevel(support.StationDoors.diagramMenu(ui, square), north);
        topLevel(editorMenu(square), north);
    }

    // ---------------------------------------------------------------- the doors

    /** The editor's right-click menu on the square, from an editor opened on its page and kept for the claim. */
    private JPopupMenu editorMenu(final TileKey square) throws Exception
    {
        if (editor == null)
        {
            final org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

            SwingUtilities.invokeAndWait(() ->
            {
                editor = new LayoutEditor(page, 30, ui, 0);
                editor.render();
                editor.setAutonomyMode(session);
            });

            support.Components.settle();
        }

        return support.Components.onTheEventThread(() -> editor.getAutonomyPanel().buildTileMenu(square, null));
    }

    /** The item on the menu itself, not in a submenu, that says this; fails naming what the menu does say. */
    private static JMenuItem topLevel(JPopupMenu menu, String text)
    {
        assertNotNull(menu, "precondition: no menu");

        List<String> said = new ArrayList<>();

        for (Component part : menu.getComponents())
        {
            if (!(part instanceof JMenuItem)) continue;

            if (text.equals(((JMenuItem) part).getText())) return (JMenuItem) part;

            said.add(((JMenuItem) part).getText());
        }

        fail("the menu has no \"" + text + "\" of its own: " + said);

        return null;
    }

    private static List<String> texts(JMenu menu)
    {
        List<String> out = new ArrayList<>();

        for (Component part : menu.getMenuComponents())
        {
            if (part instanceof JMenuItem) out.add(((JMenuItem) part).getText());
        }

        return out;
    }

    /** The message, or a line no menu says where the bundle has no such key. */
    private static String said(String key, String side)
    {
        try
        {
            return I18n.f(key, org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.menuSide(side));
        }
        catch (java.util.MissingResourceException none)
        {
            return "(no " + key + ")";
        }
    }

    private static String changeDirection(Side now)
    {
        return said("autosetup.ui.menuChangeDirection", now.name());
    }

    private static String changeArrivedFrom(String side)
    {
        return said("autosetup.ui.menuChangeArrivedFrom", side);
    }

    // ---------------------------------------------------------------- the railway

    private static void rebuild() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        support.Components.settle();
        support.Components.settle();
    }

    /** Stations with a train standing on the railway that the setup names there too, in a stable order. */
    private static List<TileKey> stationsWithATrain()
    {
        List<TileKey> out = new ArrayList<>();

        for (Point point : model.getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            TileKey at = session.getStationIndex().squareOf(point);

            if (at == null || model.getLayout(at.getPage()) == null || !session.getStore().isStation(at)) continue;

            if (point.getCurrentLocomotive().getName().equals(session.getLocomotiveNameAt(at)) && !out.contains(at))
            {
                out.add(at);
            }
        }

        out.sort((a, b) -> a.toString().compareTo(b.toString()));

        return out;
    }

    private static TileKey squareWithTwoFacings()
    {
        for (TileKey at : stationsWithATrain())
        {
            if (session.facingChoices(at).size() == 2 && facingOnTheRailway(at) != null) return at;
        }

        throw new AssertionError("precondition: no train on the snapshot stands where two facings can be chosen");
    }

    /**
     * A train standing where two arrival sides are offered, one recorded in the setup and on the railway.  Every station
     * of the snapshot is arrived at by one side, so one whose track has two is opened to both.
     */
    private static TileKey squareWithTwoSides() throws Exception
    {
        TileKey square = null;

        for (TileKey at : stationsWithATrain()) if (square == null && session.arrivalSides(at).size() == 2) square = at;

        assertNotNull(square, "precondition: no train on the snapshot stands where the track has two arrival sides");

        final TileKey opened = square;

        SwingUtilities.invokeAndWait(() -> session.setBarredArrivals(opened, new java.util.HashSet<>()));

        rebuild();

        assertEquals(sides(square).size(), 2, "precondition: " + square + " opened to both sides offers " + sides(square));

        if (!sides(square).contains(session.getArrivedFrom(square)))
        {
            session.setArrivedFrom(square, sides(square).get(0));
            standing(square).setArrivedFrom(sides(square).get(0));
        }

        return square;
    }

    private static List<String> sides(TileKey square)
    {
        return ArrivalSidePrompt.sidesOf(session.unbarredArrivalSides(square));
    }

    private static Side facingOnTheRailway(TileKey square)
    {
        return session.facingOnTheRailway(square, model.getAutoLayout());
    }

    /** The copy of the square the train stands on, on the railway. */
    private static Point standing(TileKey square)
    {
        for (String name : session.getStationIndex().pointNamesAt(square))
        {
            Point copy = model.getAutoLayout().getPoint(name);

            if (copy != null && copy.getCurrentLocomotive() != null) return copy;
        }

        throw new AssertionError("precondition: no train stands on " + square + " on the railway");
    }

    private static <T> T theOther(List<T> two, T one)
    {
        assertEquals(two.size(), 2, "precondition: not two to choose between: " + two);

        return two.get(0).equals(one) ? two.get(1) : two.get(0);
    }
}
