package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.util.I18n;

/**
 * A square's arrows are the whole of which way trains may run through it (OB-351).
 *
 * Adam, of a switch whose spur was closed while its main line ran both ways: *"into spur should simply require S to be
 * green, and out of spur should require W to be green.  Align it so that the arrows tell the full story.   If we need
 * more granular control, we can just prevent movement on the track attached to s."*  The arrows are drawn per arm - a
 * side of the square is green where any road through it lets a train out - and the directions were stored per road, so a
 * road could be shut a way its arm's arrow showed open.  Switch 118 on his example layout held its main line both ways
 * and its spur closed: drawn exactly as a spur trains may leave toward the toe, it let none out.
 *
 * Each claim asks a train's own question of the graph - entered by this side, may it leave by that one - and holds the
 * answer to the arrows drawn: a move is allowed exactly where the arm it leaves by is green and the blades run that way.
 * The switch is `test/layouts/single-switch`'s, toe west, main line east and spur north; the other kinds are drawn here.
 *
 * @author Adam
 */
public class testTheArrowsTellTheWholeStory
{
    private static support.Scenario scenario;

    private static AutonomySession session;

    private static TileKey turnout;

    private static RouteId main;

    private static RouteId spur;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("single-switch");

        scenario.getModel().stop();

        session = scenario.getSession();

        turnout = scenario.tile(5, 3);

        for (Map.Entry<RouteId, Route> road : session.getGraph().getRoutes(turnout).entrySet())
        {
            if (road.getValue().touches(Side.E)) main = road.getKey();
            if (road.getValue().touches(Side.N)) spur = road.getKey();
        }

        assertNotNull(main, "precondition: the switch at 5,3 has no main line east");
        assertNotNull(spur, "precondition: the switch at 5,3 has no spur north");
        assertTrue(session.getGraph().getRoutes(turnout).get(main).touches(Side.W)
            && session.getGraph().getRoutes(turnout).get(spur).touches(Side.W), "precondition: the switch's toe is not west");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    /**
     * Every combination of arms the click steps through moves trains exactly as the arrows say: west to east where east
     * is green, east to west and out of the spur where west is, into the spur where north is.
     *
     * MUTATION: store a road's answer other than its two arms' in `applyArmMask`, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testEveryCombinationTheClickSetsMovesAsItsArrowsShow() throws Exception
    {
        allOpen();

        final org.traincontrol.gui.AutonomyEditorPanel panel = panel();

        Set<Set<Side>> seen = new LinkedHashSet<>();

        for (int click = 1; click <= 8; click++)
        {
            cycle(panel, turnout);

            Set<Side> green = arrows(turnout);

            seen.add(green);

            assertFourMovesFollow(green, "click " + click);
        }

        assertEquals(seen.size(), 8, "eight clicks on a switch with three arms did not reach its eight combinations: "
            + seen);
    }

    /**
     * A setup holding a switch's main line both ways and its spur closed - switch 118 on Adam's layout - is read as its
     * arrows draw it: the spur runs toward the toe, the square is said once, the setup is saved that way, and the path
     * out of the spur is there.
     *
     * MUTATION: leave out `normalizeToArms` from `AutonomyCompanionStore.applyTo`, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testASpurClosedUnderAGreenToeIsReadAsItsArrowsShow() throws Exception
    {
        File setup = new File(scenario.getFolder(), "config" + File.separator + "autonomy" + File.separator + "setup.json");

        byte[] was = Files.readAllBytes(setup.toPath());

        try
        {
            org.json.JSONObject json = new org.json.JSONObject(new String(was, StandardCharsets.UTF_8));

            json.getJSONObject("tileDirections").put(key(main), "BOTH");
            json.getJSONObject("tileDirections").put(key(spur), "NONE");

            Files.write(setup.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));

            AutonomySession read = new AutonomySession(scenario.getFolder());

            read.open(scenario.getPages());

            Route road = read.getGraph().getRoutes(turnout).get(spur);

            Direction towardTheToe = road.getA() == Side.W ? Direction.TOWARD_A : Direction.TOWARD_B;

            assertEquals(read.getGraph().getDirection(turnout, spur), towardTheToe, "a spur closed under a toe whose"
                + " arrow is green was not read as running toward the toe, as its arrows draw it");

            assertEquals(read.getGraph().getDirection(turnout, main), Direction.BOTH, "the main line was changed");

            assertNull(hidden(read, turnout), "the switch read from the file still hides a move from its arrows");

            assertEquals(read.getSquaresBroughtToTheirArrows().keySet(), Collections.singleton(turnout), "the squares"
                + " said to have been brought to their arrows are not the switch alone");

            String said = read.getSquaresBroughtToTheirArrows().get(turnout);

            assertTrue(said.contains("5,3") && said.contains(scenario.getPages().get(0).getName()), "what is logged does"
                + " not say where the switch is: " + said);

            // SAVED THAT WAY
            org.json.JSONObject saved = new org.json.JSONObject(new String(Files.readAllBytes(setup.toPath()),
                StandardCharsets.UTF_8)).getJSONObject("tileDirections");

            assertEquals(saved.optString(key(spur), null), towardTheToe.name(), "the setup was not saved with the spur"
                + " running toward the toe: " + saved);

            // AND THE PATH OUT OF THE SPUR IS THERE - the one switch 118 refused - while the way in stays shut
            assertTrue(hasEdge(read, scenario.tile(6, 2), scenario.tile(3, 3)), "there is no path out of the spur, from"
                + " BranchPlatform to Approach: " + edges(read));

            assertFalse(hasEdge(read, scenario.tile(3, 3), scenario.tile(6, 2)), "a path into the spur appeared, though"
                + " its north arrow is red: " + edges(read));

            // ONCE: read again, nothing is left to bring to its arrows
            AutonomySession again = new AutonomySession(scenario.getFolder());

            again.open(scenario.getPages());

            assertTrue(again.getSquaresBroughtToTheirArrows().isEmpty(), "reading the saved setup again said again: "
                + again.getSquaresBroughtToTheirArrows());
        }
        finally
        {
            Files.write(setup.toPath(), was);
        }
    }

    /**
     * No door stores a road shut a way its square's arrow shows open: the session's single-road and bulk setters, the
     * run setter the branch submenus called, the one-way tool through the switch and through its spur, and Allow Every
     * Path.  Every road and answer of the switch is tried through the single-road setter, from both ways open - which
     * holds the brief's own example, main line toward east and spur both ways, drawn all green and refusing east to west.
     *
     * MUTATION: record what was wanted in `AutonomySession.recordSquare` and leave out `normalizeToArms`, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testNoDoorStoresAMoveItsArrowsDoNotShow() throws Exception
    {
        for (RouteId road : Arrays.asList(main, spur))
        {
            for (Direction answer : Direction.values())
            {
                allOpen();

                session.setDirection(turnout, road, answer);

                assertFourMovesFollow(arrows(turnout), "the road " + road + " set " + answer);

                allOpen();

                session.setRunDirection(turnout, road, answer);

                assertFourMovesFollow(arrows(turnout), "the road " + road + " set " + answer + " as its submenu did");
            }
        }

        for (Direction answer : Direction.values())
        {
            allOpen();

            session.setDirection(new LinkedHashSet<>(Collections.singleton(turnout)), answer);

            assertFourMovesFollow(arrows(turnout), "every road set " + answer);
        }

        Map<RouteId, Direction> eastAndSpur = new LinkedHashMap<>();

        Route mainLine = session.getGraph().getRoutes(turnout).get(main);

        eastAndSpur.put(main, mainLine.getA() == Side.E ? Direction.TOWARD_A : Direction.TOWARD_B);
        eastAndSpur.put(spur, Direction.BOTH);

        allOpen();

        session.setDirections(turnout, eastAndSpur);

        assertFourMovesFollow(arrows(turnout), "the main line toward east and the spur both ways");

        allOpen();

        assertTrue(session.setOneWayRun(scenario.tile(3, 3), scenario.tile(7, 3)) > 0, "precondition: no one-way run");

        assertFourMovesFollow(arrows(turnout), "a one-way run from Approach to MainPlatform");

        assertFalse(arrows(turnout).contains(Side.W), "a one-way run from Approach to MainPlatform left trains leaving"
            + " the switch toward Approach");

        allOpen();

        assertTrue(session.setOneWayRun(scenario.tile(6, 2), scenario.tile(3, 3)) > 0, "precondition: no one-way run");

        assertFourMovesFollow(arrows(turnout), "a one-way run out of the spur to Approach");

        session.setDirection(turnout, spur, Direction.NONE);

        session.allowEveryPath();

        assertFourMovesFollow(arrows(turnout), "Allow Every Path");

        assertEquals(arrows(turnout), EnumSet.of(Side.N, Side.E, Side.W), "Allow Every Path did not open every arm");
    }

    /**
     * A switch's menu offers its arms and every branch at once, and no road of its own: a road's submenu could shut the
     * spur's way out at the toe under the main line's green arrow.  A crossing's roads end at arms of their own, so its
     * roads keep their submenus.
     *
     * MUTATION: offer the branch submenus on every square with more than one road again, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testASwitchsMenuOffersItsArmsAndNoRoadOfItsOwn() throws Exception
    {
        allOpen();

        Set<String> texts = menuTexts(panel().buildTileMenu(turnout, null));

        for (Route road : session.getGraph().getRoutes(turnout).values())
        {
            String branch = I18n.f("autosetup.ui.menuBranch", String.valueOf(road.getA()), String.valueOf(road.getB()));

            assertFalse(texts.contains(branch), "the switch's menu still offers its road " + branch + " on its own: "
                + texts);
        }

        for (Side arm : Arrays.asList(Side.N, Side.E, Side.W))
        {
            assertTrue(texts.contains(I18n.f("autosetup.ui.menuArm", String.valueOf(arm))), "the switch's menu has no box"
                + " for its " + arm + " arm: " + texts);
        }

        assertTrue(texts.contains(I18n.t("autosetup.ui.menuAllBranches")), "the switch's menu lost Every Branch: "
            + texts);

        Kinds kinds = new Kinds();

        try
        {
            final org.traincontrol.gui.AutonomyEditorPanel[] other = new org.traincontrol.gui.AutonomyEditorPanel[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
                other[0] = new org.traincontrol.gui.AutonomyEditorPanel(kinds.session, "main", () -> { }));

            Set<String> crossing = menuTexts(other[0].buildTileMenu(kinds.crossing, null));

            for (Route road : kinds.session.getGraph().getRoutes(kinds.crossing).values())
            {
                String branch = I18n.f("autosetup.ui.menuBranch", String.valueOf(road.getA()), String.valueOf(road.getB()));

                assertTrue(crossing.contains(branch), "the crossing's menu lost its road " + branch + ": " + crossing);
            }
        }
        finally
        {
            kinds.close();
        }
    }

    /**
     * The other squares whose roads share an arm - a three-way, a double slip, a permanent left and a permanent
     * three-way - hold no move their arrows hide, whatever road is set to whatever answer, and keep their blades' own
     * restriction: no road is left an answer the hardware refuses, and the click steps only through what it may store.
     * A crossing and a double curve, whose roads end at arms of their own, store each answer exactly as it was set.
     *
     * MUTATION: record what was wanted in `AutonomySession.recordSquare` and leave out `normalizeToArms`, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testEveryKindOfSquareRunsAsItsArrowsShowWithinItsBlades() throws Exception
    {
        Kinds kinds = new Kinds();

        try
        {
            AutonomySession on = kinds.session;

            for (TileKey square : kinds.shared())
            {
                assertTrue(on.getGraph().sharesAnArm(square), "precondition: " + square + "'s roads share no arm");

                for (RouteId road : on.getGraph().getRoutes(square).keySet())
                {
                    for (Direction answer : Direction.values())
                    {
                        on.setDirection(square, road, answer);

                        String why = square + " (" + on.getGraph().getTiles().get(square).getType() + ") after its road "
                            + road + " was set " + answer;

                        assertNull(hidden(on, square), why + " hides a move from its arrows");

                        assertPossible(on, square, why);
                    }
                }

                final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                    panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(on, "main", () -> { }));

                for (int click = 1; click <= 20; click++)
                {
                    cycle(panel[0], square);

                    String why = square + " (" + on.getGraph().getTiles().get(square).getType() + ") after click " + click;

                    assertNull(hidden(on, square), why + " hides a move from its arrows");

                    assertPossible(on, square, why);
                }
            }

            for (TileKey square : Arrays.asList(kinds.crossing, kinds.doubleCurve))
            {
                assertFalse(on.getGraph().sharesAnArm(square), "precondition: " + square + "'s roads share an arm");

                for (RouteId road : on.getGraph().getRoutes(square).keySet())
                {
                    for (Direction answer : Direction.values())
                    {
                        on.setDirection(square, road, answer);

                        assertEquals(on.getGraph().getDirection(square, road), answer, square + "'s road " + road
                            + " was set " + answer + " and holds another answer");

                        assertNull(hidden(on, square), square + " hides a move from its arrows");
                    }
                }
            }
        }
        finally
        {
            kinds.close();
        }
    }

    // ---------------------------------------------------------------- what a train may do, and what is drawn

    /**
     * Why a square's moves and its arrows disagree, or null where they agree: a train entering by one end of a road may
     * leave by the other exactly where that end's arrow is green and the blades run that way.
     *
     * Not half of a two-square crossing: the side its squares meet at draws no arrow, and each track is told by its two
     * outer ends, one on each square (OB-320, OB-322).
     */
    static String hidden(AutonomySession on, TileKey square)
    {
        if (on.getGraph().crossingPartner(square) != null) return null;

        Set<Side> green = arrows(on, square);

        for (Map.Entry<RouteId, Route> road : on.getGraph().getRoutes(square).entrySet())
        {
            Route route = road.getValue();

            if (route.getA() == route.getB()) continue;

            for (Side entry : new Side[] {route.getA(), route.getB()})
            {
                Side exit = route.other(entry);

                boolean drawn = green.contains(exit) && route.isTraversableFrom(entry);

                boolean runs = false;

                for (TileGraph.Exit leaving : on.getGraph().exits(square, entry))
                {
                    if (leaving.getSide() == exit && road.getKey().equals(leaving.getRouteId())) runs = true;
                }

                if (drawn != runs)
                {
                    return "entered by " + entry + " along " + route + ", a train " + (runs ? "may" : "may not")
                        + " leave by " + exit + " where the arrows are green at " + green;
                }
            }
        }

        return null;
    }

    /** The sides of a square drawn green: those a train may leave by, as the arrows combine its roads. */
    private static Set<Side> arrows(AutonomySession on, TileKey square)
    {
        Set<Side> green = EnumSet.noneOf(Side.class);

        for (TileAnnotation.Mark mark : on.directionMarks(square))
        {
            if (mark.getDirection() == Direction.BOTH || mark.getDirection() == Direction.TOWARD_A) green.add(mark.getA());
            if (mark.getDirection() == Direction.BOTH || mark.getDirection() == Direction.TOWARD_B) green.add(mark.getB());
        }

        return green;
    }

    private static Set<Side> arrows(TileKey square)
    {
        return arrows(session, square);
    }

    /** Whether a train entering the switch by one side may leave it by another. */
    private static boolean moves(Side entry, Side exit)
    {
        for (TileGraph.Exit leaving : session.getGraph().exits(turnout, entry))
        {
            if (leaving.getSide() == exit) return true;
        }

        return false;
    }

    /** The four moves through the switch, each against the arm it leaves by (Adam's rule). */
    private static void assertFourMovesFollow(Set<Side> green, String after)
    {
        assertEquals(moves(Side.W, Side.E), green.contains(Side.E), after + ": west to east does not follow the east"
            + " arrow, with " + green + " green");
        assertEquals(moves(Side.E, Side.W), green.contains(Side.W), after + ": east to west does not follow the west"
            + " arrow, with " + green + " green");
        assertEquals(moves(Side.W, Side.N), green.contains(Side.N), after + ": into the spur does not follow the north"
            + " arrow, with " + green + " green");
        assertEquals(moves(Side.N, Side.W), green.contains(Side.W), after + ": out of the spur does not follow the west"
            + " arrow, with " + green + " green");

        assertNull(hidden(session, turnout), after + ": the switch hides a move from its arrows");
    }

    /**
     * Every road of the square stores an answer its blades leave a meaning (VD18-C2).  A road left at its default stores
     * nothing: a permanent turnout's is `BOTH`, which the blades narrow to the one way there is.
     */
    private static void assertPossible(AutonomySession on, TileKey square, String after)
    {
        for (Map.Entry<RouteId, Route> road : on.getGraph().getRoutes(square).entrySet())
        {
            if (on.getStore().getTileDirection(square, road.getKey()) == null) continue;

            Direction held = on.getGraph().getDirection(square, road.getKey());

            assertTrue(TileGraph.directionIsPossible(held, road.getValue()), after + ": the road " + road.getValue()
                + " holds " + held + ", an answer its blades refuse");
        }
    }

    /** Both roads of the switch open both ways - every arm green. */
    private static void allOpen()
    {
        Map<RouteId, Direction> open = new LinkedHashMap<>();

        open.put(main, Direction.BOTH);
        open.put(spur, Direction.BOTH);

        session.setDirections(turnout, open);

        assertEquals(arrows(turnout), EnumSet.of(Side.N, Side.E, Side.W), "precondition: the switch is not open");
    }

    private static boolean hasEdge(AutonomySession on, TileKey from, TileKey to)
    {
        for (GraphReducer.ReducedEdge edge : on.getReducer().getEdges())
        {
            if (from.equals(edge.getStart()) && to.equals(edge.getEnd())) return true;
        }

        return false;
    }

    private static List<String> edges(AutonomySession on)
    {
        List<String> out = new ArrayList<>();

        for (GraphReducer.ReducedEdge edge : on.getReducer().getEdges())
        {
            out.add(edge.getStart().getX() + "," + edge.getStart().getY() + ">" + edge.getEnd().getX() + ","
                + edge.getEnd().getY());
        }

        return out;
    }

    /** The setup's key for one of the switch's roads: the page by its id, as the file writes it. */
    private static String key(RouteId road)
    {
        return "1:" + turnout.getX() + "," + turnout.getY() + "#" + road.getState() + "," + road.getIndex();
    }

    private static org.traincontrol.gui.AutonomyEditorPanel panel() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session,
            scenario.getPages().get(0).getName(), () -> { }));

        return panel[0];
    }

    /** A left click on a square, as the editor takes it. */
    private static void cycle(final org.traincontrol.gui.AutonomyEditorPanel panel, final TileKey square) throws Exception
    {
        final java.lang.reflect.Method cycle =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("cycle", TileKey.class);

        cycle.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                cycle.invoke(panel, square);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });
    }

    /** The text of every item on a menu and its submenus. */
    private static Set<String> menuTexts(java.awt.Component in)
    {
        Set<String> out = new LinkedHashSet<>();

        if (in instanceof javax.swing.JMenuItem) out.add(((javax.swing.JMenuItem) in).getText());

        java.awt.Component[] inside = in instanceof javax.swing.JMenu ? ((javax.swing.JMenu) in).getMenuComponents()
            : in instanceof java.awt.Container ? ((java.awt.Container) in).getComponents() : new java.awt.Component[0];

        for (java.awt.Component child : inside) out.addAll(menuTexts(child));

        return out;
    }

    // ---------------------------------------------------------------- every other kind, drawn here

    /**
     * One page holding each kind of square with more than one road, each with track off every arm, in a session of its
     * own in a folder of its own.
     */
    private static final class Kinds
    {
        private final File folder;
        private final AutonomySession session;

        private final TileKey threeWay = at(3, 3);
        private final TileKey doubleSlip = at(9, 3);
        private final TileKey permanentLeft = at(3, 9);
        private final TileKey permanentThreeWay = at(9, 9);
        private final TileKey crossing = at(15, 3);
        private final TileKey doubleCurve = at(15, 9);

        Kinds() throws Exception
        {
            folder = Files.createTempDirectory("tc-arrows").toFile();

            LayoutDiagram page = new LayoutDiagram("main", 20, 14, null, null);

            page.setPageId("1");

            page.addComponent(componentType.SWITCH_THREE, 3, 3, 0, 0, 1, 1, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.SWITCH_CROSSING, 9, 3, 0, 0, 3, 3, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.CUSTOM_PERM_LEFT, 3, 9, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.CUSTOM_PERM_THREEWAY, 9, 9, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.CROSSING, 15, 3, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
            page.addComponent(componentType.DOUBLE_CURVE, 15, 9, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

            int sensor = 10;

            for (TileKey square : Arrays.asList(threeWay, doubleSlip, permanentLeft, permanentThreeWay, crossing))
            {
                // a sensor off each side, so every arm leads somewhere
                page.addComponent(componentType.FEEDBACK, square.getX(), square.getY() - 1, 1, 0, sensor, sensor++,
                    accessoryDecoderType.MM2, null);
                page.addComponent(componentType.FEEDBACK, square.getX(), square.getY() + 1, 1, 0, sensor, sensor++,
                    accessoryDecoderType.MM2, null);
                page.addComponent(componentType.FEEDBACK, square.getX() - 1, square.getY(), 0, 0, sensor, sensor++,
                    accessoryDecoderType.MM2, null);
                page.addComponent(componentType.FEEDBACK, square.getX() + 1, square.getY(), 0, 0, sensor, sensor++,
                    accessoryDecoderType.MM2, null);
            }

            session = new AutonomySession(folder);

            session.open(Arrays.asList(page));
            session.initialize("OB-351");
        }

        List<TileKey> shared()
        {
            return Arrays.asList(threeWay, doubleSlip, permanentLeft, permanentThreeWay);
        }

        void close()
        {
            delete(folder);
        }

        private static TileKey at(int x, int y)
        {
            return new TileKey("main", x, y);
        }
    }

    private static void delete(File file)
    {
        File[] inside = file.listFiles();

        if (inside != null) for (File f : inside) delete(f);

        if (!file.delete()) file.deleteOnExit();
    }
}
