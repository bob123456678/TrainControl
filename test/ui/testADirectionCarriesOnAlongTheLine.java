package ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Diagrams;
import support.MenuPage;

/**
 * A run's direction set so trains may go a way they could not carries on along the line (MT-1068, Adam: "if it's
 * through of a non-splitting switch, flip that as well.  make sure all affected tiles are highlighted.  and when locking
 * down again, it needs only be the adjacent tile."): past plain sensors and through switches entered by a branch, to a
 * station, a split, or track that already lets trains go that way, after a notice that asks with every square lit.
 *
 * Each claim draws a line of its own along row 1 of a page: S a station, P a plain sensor, - a straight, &gt; or &lt; a
 * switch whose toe points east or west.  A claim's line starts with every run one way west.
 *
 * @author Adam
 */
public class testADirectionCarriesOnAlongTheLine
{
    /**
     * Reversed east, the run beside the station carries on past the plain sensor to the next station, and no further.
     *
     * MUTATION: have `SessionRuns.carryOn` pass a station as it passes a plain sensor, and this fails.
     */
    @Test(timeOut = 60000)
    public void testAReversalCarriesOnPastAPlainSensorAndStopsAtAStation() throws Exception
    {
        try (MenuPage page = line("tc-along-sensor", "S--P--S--P"))
        {
            AutonomySession session = page.session;

            assertEquals(session.alongTheLine(at(2), roadOf(session, 2), toward(session, 2, Side.E)).keySet(),
                squares(5, 6), "the reversal does not carry on over the next run alone");

            session.setRunDirection(at(2), roadOf(session, 2), toward(session, 2, Side.E), true);

            assertEquals(ways(session), "2:E 3:E 5:E 6:E 8:W 9:W", "the line past the plain sensor was not reversed, or"
                + " the reversal went on past the station");
        }
    }

    /**
     * Through a switch entered by a branch the walk sets the road it takes and goes on; at a switch entered by its toe,
     * where the line splits, it stops.
     *
     * MUTATION: have `carryOn` go straight on through a switch entered by its toe, and the split is passed and this
     * fails; drop the switch from `carryOn`, and the switch entered by a branch stops the walk and this fails.
     */
    @Test(timeOut = 60000)
    public void testItGoesThroughASwitchEnteredByABranchAndStopsWhereTheLineSplits() throws Exception
    {
        try (MenuPage page = line("tc-along-trailing", "S-P->-S"))
        {
            AutonomySession session = page.session;

            assertFalse(ways(session).contains("5:E"), "precondition: the switch already lets trains east");

            session.setRunDirection(at(2), roadOf(session, 2), toward(session, 2, Side.E), true);

            assertEquals(ways(session), "2:E 4:E 5:E 6:E", "the line was not carried through the switch entered by a"
                + " branch");
        }

        try (MenuPage page = line("tc-along-facing", "S-P-<-S"))
        {
            AutonomySession session = page.session;

            // Its straight road one way toward its toe, so only the split stops the walk
            session.getRoutes(at(5)).forEach((id, road) ->
            {
                if (road.touches(Side.E)) session.setDirection(at(5), id, road.getA() == Side.W ? Direction.TOWARD_A
                    : Direction.TOWARD_B);
            });

            assertEquals(ways(session), "2:W 4:W 5:W 6:W", "precondition: the line does not start one way west");

            session.setRunDirection(at(2), roadOf(session, 2), toward(session, 2, Side.E), true);

            assertEquals(ways(session), "2:E 4:E 5:W 6:W", "the walk did not stop at the switch where the line splits");
        }
    }

    /**
     * The walk stops at a run that already lets trains go the new way, and on a loop it stops before a square it has
     * been on, having set the rest of the loop once.
     *
     * MUTATION: have `carryOn` go on past a run that lets the way, and the first half fails; drop the `seen` test from
     * its loop, and the second never ends.
     */
    @Test(timeOut = 60000)
    public void testItStopsAtARunThatAlreadyAllowsTheWayAndOnALoop() throws Exception
    {
        try (MenuPage page = line("tc-along-open", "S-P-P-S"))
        {
            AutonomySession session = page.session;

            session.setRunDirection(at(4), roadOf(session, 4), Direction.BOTH);

            session.setRunDirection(at(2), roadOf(session, 2), toward(session, 2, Side.E), true);

            assertEquals(ways(session), "2:E 4:EW 6:W", "the walk went past a run that already lets trains east");
        }

        try (MenuPage page = new MenuPage("tc-along-loop", aLoop()))
        {
            AutonomySession session = page.session;

            session.initialize("MT-1068");

            // Both sides of the loop run clockwise: east along the top, west along the bottom
            TileKey top = new TileKey("main", 3, 1);
            TileKey bottom = new TileKey("main", 1, 3);

            session.setRunDirection(top, roadOf(session, top), toward(session, top, Side.S));
            session.setRunDirection(bottom, roadOf(session, bottom), toward(session, bottom, Side.N));

            assertEquals(session.alongTheLine(top, roadOf(session, top), toward(session, top, Side.W)).keySet(),
                new LinkedHashSet<>(session.getGraph().runOf(bottom).getTiles()), "the other side of the loop is not"
                + " what the reversal carries on to");
        }
    }

    /**
     * A setting that only takes ways away, both ways to one way or anything to closed, changes the run clicked alone,
     * with no notice, though the run past it points the other way.
     *
     * MUTATION: have `SessionRuns.alongTheLine` carry every way the new setting allows, not only those the old did not,
     * and this fails.
     */
    @Test(timeOut = 120000)
    public void testLockingDownChangesTheClickedRunAloneWithNoNotice() throws Exception
    {
        try (MenuPage page = line("tc-along-lock", "S--P--S"))
        {
            AutonomySession session = page.session;

            AutonomyEditorPanel panel = panelOn(session);

            session.setRunDirection(at(5), roadOf(session, 5), toward(session, 5, Side.E));
            session.setRunDirection(at(2), roadOf(session, 2), Direction.BOTH);

            List<String> asked = menuPick(panel, 2, I18n.f("autosetup.ui.menuRouteToward", "W"), null).asked();

            assertEquals(ways(session), "2:W 3:W 5:E 6:E", "both ways to one way did not change the run clicked alone");

            asked.addAll(menuPick(panel, 2, I18n.t("autosetup.ui.menuRouteNone"), null).asked());

            assertEquals(ways(session), "2: 3: 5:E 6:E", "closing did not change the run clicked alone");
            assertTrue(asked.isEmpty(), "locking down asked: " + asked);
        }
    }

    /**
     * The notice says how many squares further along would change while every square to change, the run clicked too,
     * is outlined; No changes nothing and takes the outline away; Yes changes all of it in one step, which putting the
     * setup back as it was before the click (as Cancel does) undoes whole.
     *
     * MUTATION: have `AutonomyEditorPanel.agreedAlongTheLine` light only the squares past the run, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheNoticeCountsAndLightsWhatChangesAndNoChangesNothing() throws Exception
    {
        try (MenuPage page = line("tc-along-notice", "S--P--S--P"))
        {
            AutonomySession session = page.session;

            AutonomyEditorPanel panel = panelOn(session);

            String before = ways(session);
            org.json.JSONObject asBefore = session.snapshotSetup();

            Set<TileKey> lit = new LinkedHashSet<>();

            Answering no = menuPick(panel, 2, I18n.f("autosetup.ui.menuRouteToward", "E"), dialog ->
            {
                for (int x = 1; x <= 10; x++)
                {
                    if (panel.isOutlined(at(x))) lit.add(at(x));
                }
            }, I18n.t("ui.no"));

            assertEquals(no.asked(), Arrays.asList(I18n.f("autosetup.ui.alongTheLine", 2)),
                "the notice did not say that two squares further along would change");
            assertEquals(lit, squares(2, 3, 5, 6), "the squares to change were not every one outlined while it asked");
            assertEquals(ways(session), before, "No changed something");
            assertFalse(Components.onTheEventThread(() -> panel.isOutlined(at(5))), "the outline outlived the notice");

            menuPick(panel, 2, I18n.f("autosetup.ui.menuRouteToward", "E"), null, I18n.t("ui.yes"));

            assertEquals(ways(session), "2:E 3:E 5:E 6:E 8:W 9:W", "Yes did not change the run and the line past it");

            Components.onTheEventThread(() -> { session.restoreSetup(asBefore); return null; });

            assertEquals(ways(session), before, "putting the setup back as it was before the click left some of it");
        }
    }

    /**
     * The run's right-click menu and the editor's click on it each give the line what the session's one rule gives it,
     * after the notice.  Station Settings' picture is claimed in `testStationSettingsWindow`, which has the main window.
     *
     * MUTATION: have `AutonomyEditorPanel.cycle` call `session.setRunDirection` without `alongTheLine`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testEveryDoorCarriesTheSameRule() throws Exception
    {
        try (MenuPage page = line("tc-along-doors", "S--P--S"))
        {
            AutonomySession session = page.session;

            AutonomyEditorPanel panel = panelOn(session);

            org.json.JSONObject start = session.snapshotSetup();

            Route road = session.getRoutes(at(2)).get(roadOf(session, 2));
            Side a = road.getA();
            Side b = road.getB();

            // The rule, through the session: the run turned toward B
            startFrom(session, start, a);
            session.setRunDirection(at(2), roadOf(session, 2), Direction.TOWARD_B, true);

            String wanted = ways(session);

            assertTrue(wanted.contains("5:" + b), "precondition: the rule changed nothing past the run: " + wanted);

            startFrom(session, start, a);
            menuPick(panel, 2, I18n.f("autosetup.ui.menuRouteToward", String.valueOf(b)), null, I18n.t("ui.yes"));
            assertEquals(ways(session), wanted, "the right-click menu set otherwise than the rule");

            startFrom(session, start, a);
            Answering clicked = answering(null, I18n.t("ui.yes"));

            try
            {
                Components.onTheEventThread(() -> { panel.tileClicked(at(2), null, false); return null; });
            }
            finally
            {
                clicked.close();
            }

            assertEquals(clicked.asked().size(), 1, "the editor's click did not ask");
            assertEquals(ways(session), wanted, "the editor's click set otherwise than the rule");
        }
    }

    // ------------------------------------------------------------------------------------------------------- the rigs

    /** A session over one page with `squares` along row 1 from x = 1, stations made, every run one way west. */
    private static MenuPage line(String tag, String squares) throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", squares.length() + 2, 3, null, null);

        for (int i = 0; i < squares.length(); i++)
        {
            char square = squares.charAt(i);
            int x = i + 1;

            if (square == 'S' || square == 'P') Diagrams.feedback(page, x, 1, 60 + x, 60 + x);
            else if (square == '-') Diagrams.straight(page, x, 1);
            else Diagrams.put(page, componentType.SWITCH_LEFT, x, 1, toeAt(square == '>' ? Side.E : Side.W), 7, 7);
        }

        page.setPageId("1");
        Diagrams.wireAccessories(Arrays.asList(page));

        MenuPage made = new MenuPage(tag, page);
        AutonomySession session = made.session;

        session.initialize("MT-1068");

        for (int i = 0; i < squares.length(); i++)
        {
            if (squares.charAt(i) == 'S') session.setStation(at(i + 1), true);
        }

        startFrom(session, null, Side.W);

        return made;
    }

    /** The orientation of a switch along row 1 whose toe is on this side. */
    private static int toeAt(Side toe)
    {
        for (int orientation = 0; orientation < 4; orientation++)
        {
            if (TilePorts.deriveToe(componentType.SWITCH_LEFT, orientation) == toe) return orientation;
        }

        throw new AssertionError("no switch has its toe at " + toe);
    }

    /** Puts the setup back to `start` (none: as it is) and sets every run on row 1 one way toward `way`. */
    private static void startFrom(AutonomySession session, org.json.JSONObject start, Side way)
    {
        Components.onTheEventThread(() ->
        {
            if (start != null) session.restoreSetup(start);

            for (TileKey leader : new ArrayList<>(session.runs().keySet()))
            {
                session.setRunDirection(leader, roadOf(session, leader), toward(session, leader, way));
            }

            return null;
        });
    }

    /** A ring of track with a plain sensor in the middle of its top and its bottom, and no station. */
    private static LayoutDiagram aLoop() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("main", 5, 5, null, null);

        Diagrams.put(page, componentType.CURVE, 1, 1, 0, 0, 0);
        Diagrams.feedback(page, 2, 1, 61, 61);
        Diagrams.put(page, componentType.CURVE, 3, 1, 3, 0, 0);
        Diagrams.straight(page, 3, 2, 1);
        Diagrams.put(page, componentType.CURVE, 3, 3, 2, 0, 0);
        Diagrams.feedback(page, 2, 3, 62, 62);
        Diagrams.put(page, componentType.CURVE, 1, 3, 1, 0, 0);
        Diagrams.straight(page, 1, 2, 1);

        page.setPageId("1");

        return page;
    }

    private static AutonomyEditorPanel panelOn(AutonomySession session)
    {
        return Components.onTheEventThread(() ->
        {
            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            panel.refresh();

            return panel;
        });
    }

    /** Picks an item of a square's right-click menu, answering any notice with `button` (null: none expected). */
    private static Answering menuPick(AutonomyEditorPanel panel, int x, String item,
        java.util.function.Consumer<javax.swing.JDialog> look, String... button) throws Exception
    {
        Answering answering = answering(look, button.length == 0 ? null : button[0]);

        try
        {
            Components.onTheEventThread(() ->
            {
                javax.swing.AbstractButton found = Components.withText(panel.buildTileMenu(at(x), null), item);

                assertNotNull(found, "the menu of " + at(x) + " has no " + item);

                found.doClick();

                return null;
            });
        }
        finally
        {
            answering.close();
        }

        return answering;
    }

    private static Answering answering(java.util.function.Consumer<javax.swing.JDialog> look, String button)
    {
        Answering answering = Answering.start(message -> Answering.press(button == null
            ? String.valueOf(TrainControlUI.YES_NO_OPTS[1]) : button));

        return look == null ? answering : answering.looking(look);
    }

    // ------------------------------------------------------------------------------------------------------ the reads

    private static TileKey at(int x)
    {
        return new TileKey("main", x, 1);
    }

    private static Set<TileKey> squares(int... xs)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (int x : xs) out.add(at(x));

        return out;
    }

    private static RouteId roadOf(AutonomySession session, int x)
    {
        return roadOf(session, at(x));
    }

    /** A plain square's one road. */
    private static RouteId roadOf(AutonomySession session, TileKey square)
    {
        return session.getRoutes(square).keySet().iterator().next();
    }

    /** A plain square's direction one way out by `side`. */
    private static Direction toward(AutonomySession session, int x, Side side)
    {
        return toward(session, at(x), side);
    }

    private static Direction toward(AutonomySession session, TileKey square, Side side)
    {
        Route road = session.getRoutes(square).get(roadOf(session, square));

        assertTrue(road.touches(side), "precondition: " + square + " has no track to the " + side);

        return road.getA() == side ? Direction.TOWARD_A : Direction.TOWARD_B;
    }

    /**
     * Which ways trains may cross each square of row 1 that is no sensor, by x: "E" east, "W" west, "EW" both, "" none.
     */
    private static String ways(AutonomySession session)
    {
        return Components.onTheEventThread(() ->
        {
            TileGraph graph = session.getGraph();
            Map<Integer, String> out = new TreeMap<>();

            for (Map.Entry<TileKey, org.traincontrol.base.LayoutDiagramComponent> each : graph.getTiles().entrySet())
            {
                TileKey square = each.getKey();

                if (square.getY() != 1 || each.getValue().isFeedback()) continue;

                out.put(square.getX(), (leaves(graph, square, Side.W, Side.E) ? "E" : "")
                    + (leaves(graph, square, Side.E, Side.W) ? "W" : ""));
            }

            List<String> words = new ArrayList<>();

            out.forEach((x, way) -> words.add(x + ":" + way));

            return String.join(" ", words);
        });
    }

    private static boolean leaves(TileGraph graph, TileKey square, Side in, Side out)
    {
        return graph.exits(square, in).stream().anyMatch(exit -> exit.getSide() == out);
    }
}
