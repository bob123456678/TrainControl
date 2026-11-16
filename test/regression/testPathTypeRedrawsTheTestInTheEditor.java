package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.autonomy.DestinationTable;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.Components;
import support.Display;
import support.Reflect;
import support.Teardown;

/**
 * Test a path and Why not Moving? show autonomy and a hand send at once in the real autonomy editor (FR-221, OB-225):
 * a `LayoutEditor` in autonomy mode on the frozen snapshot, the tool pressed and the squares clicked through
 * `tileClicked` (what the grid calls).
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).  The station made manual-only is put back.
 *
 * @author Adam
 */
public class testPathTypeRedrawsTheTestInTheEditor
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        Display.needed("the editor needs a display");

        support.FrozenEditor frozen = support.FrozenEditor.window("live-snapshot");

        sandbox = frozen.sandbox;
        model = frozen.model;
        ui = frozen.ui;

        session = frozen.sessionOrFail("no autonomy configuration on the snapshot");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Teardown.close(sandbox, model, ui);
    }

    /**
     * Test a path to a station autonomy leaves alone answers for both tiers at once (FR-221): the leg there is drawn
     * purple, the leg back is autonomy's, and the strip says what purple means and why autonomy would not go there.
     *
     * MUTATION: in `AutonomyEditorPanel.applyTest`, take every leg from `session.testPath` alone, and this fails.
     */
    @Test
    public void testTestAPathShowsBothTiersInTheEditor() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        assertNotNull(page, "precondition: no page " + PAGE);

        TileKey[] pair = aConnectedPairOfStations();

        assertNotNull(pair, "precondition: no two stations on " + PAGE + " with autonomy's route between them");

        final TileKey from = pair[0];
        final TileKey to = pair[1];

        org.json.JSONObject asFound = session.snapshotSetup();

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            session.setAutoDestination(to, false);

            openAnEditorOn(page, built);

            Components.drain(8);

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();

            // ARMED AS A PERSON ARMS IT, and the squares clicked through the door the grid uses.
            final javax.swing.AbstractButton test = (javax.swing.AbstractButton) field(panel, "testButton");

            SwingUtilities.invokeAndWait(() ->
            {
                test.doClick();
                panel.tileClicked(from, page.getComponent(from.getX(), from.getY()), false);
                panel.tileClicked(to, page.getComponent(to.getX(), to.getY()), false);
            });

            Components.drain(8);

            String said = shown(panel);

            assertTrue(said.contains(describe(panel, to)), "the tested route is not named on screen: " + said);

            for (String note : new String[] {org.traincontrol.util.I18n.t("autosetup.ui.testColorLegend"),
                org.traincontrol.util.I18n.f("autosetup.ui.testNotAnAutoDestination", session.describeTile(to))})
            {
                assertTrue(said.contains(note.replace("&", "&amp;").replace("<", "&lt;")), "the strip does not say \""
                    + note + "\" of a route only a hand send takes, to a station autonomy leaves alone: " + said);
            }

            @SuppressWarnings("unchecked")
            final java.util.Map<TileKey, java.util.List<TileAnnotation.Trace>> drawn =
                (java.util.Map<TileKey, java.util.List<TileAnnotation.Trace>>) field(panel, "traces");
            final int[] lines = new int[2];

            SwingUtilities.invokeAndWait(() ->
            {
                for (java.util.List<TileAnnotation.Trace> here : drawn.values())
                {
                    for (TileAnnotation.Trace line : here) lines[line.isManualOnly() ? 0 : 1]++;
                }
            });

            assertTrue(lines[0] > 0, "the leg to a station autonomy leaves alone is not drawn purple");
            assertTrue(lines[1] > 0, "the leg back, which autonomy takes, is drawn purple too");
        }
        finally
        {
            closeTheEditor(built);

            session.restoreSetup(asFound);
        }
    }

    /**
     * Test a Path outlines in orange what can be clicked: every station once it is armed, the start in blue once
     * clicked, and with the answer both ends in blue and every other station orange, when the next click starts a new
     * test; nothing once the tool is put down (FR-159).  No station takes Why not Moving?'s colors here: the
     * green/purple station highlighting belongs to "why not moving" (MT-854).
     *
     * MUTATION: drop the `annotation.markedByATool` call in `AutonomyEditorPanel.annotationFor`, mark `testFrom`
     * CLICKABLE in `toolMark`, or forget the last test's ends there, and this fails.
     */
    @Test
    public void testTestAPathMarksTheStationsInBlue() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        TileKey[] pair = aConnectedPairOfStations();

        assertNotNull(pair, "precondition: no two stations on " + PAGE + " with a route between them");

        final TileKey from = pair[0];
        final TileKey to = pair[1];

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            openAnEditorOn(page, built);

            Components.drain(8);

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton test = (javax.swing.AbstractButton) field(panel, "testButton");

            assertNull(mark(panel, to), "precondition: a station is marked before Test a Path is pressed");

            SwingUtilities.invokeAndWait(test::doClick);
            Components.drain(8);

            everyStationIs(panel, null, null, "armed, with no start chosen");

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(from, page.getComponent(from.getX(), from.getY()),
                false));
            Components.drain(8);

            assertEquals(mark(panel, from), TileAnnotation.ToolMark.PICKED, "the start is not marked as the square"
                + " clicked");

            assertFalse(panel.isOutlined(from), "the start still has the selection's outline");

            everyStationIs(panel, from, null, "with the start chosen");

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(to, page.getComponent(to.getX(), to.getY()), false));
            Components.drain(8);

            assertEquals(mark(panel, from), TileAnnotation.ToolMark.PICKED, "with the answer shown, the start is not"
                + " marked as clicked");
            assertEquals(mark(panel, to), TileAnnotation.ToolMark.PICKED, "with the answer shown, the end is not"
                + " marked as clicked");

            everyStationIs(panel, from, to, "with the answer shown, when the next click starts a new test,");

            assertFalse(fading(panel), "the Test a Path answer fades on a timer (MT-864)");

            SwingUtilities.invokeAndWait(test::doClick);
            Components.drain(8);

            assertNull(mark(panel, to), "putting Test a Path down left the station marked");
            assertNull(mark(panel, from), "putting Test a Path down left the start marked");
        }
        finally
        {
            closeTheEditor(built);
        }
    }

    /**
     * Why not Moving? outlines in orange the squares with a train while it waits, then, with its answer, the train's
     * square in blue and each station in the answer's colors: green where it can go now, purple where only a hand send
     * can (OB-394, FR-221), gray where nothing can right now.
     *
     * Each station's row in the table says what its square is colored; putting the tool down, or asking about a square
     * with no train, takes the marks off (and asking about a square with no train, the lines), and an answer that lands
     * after the tool was put down and armed again, or after a right-click let go of the tool, marks nothing.
     *
     * MUTATION: install no marks in `AutonomyEditorPanel.paintWhy`, mark the by-hand stations NOT_NOW or none NOT_NOW
     * in `EditorWhy.composeWhy`, leave the marks or `whyAsked` alone in `clearGesture`, leave `whyAsked` alone in
     * `cancelPendingGesture`, or give the no-train answer no marks or no lines, and this fails.
     */
    @Test
    public void testWhyNotMovingMarksTheStationsByItsAnswer() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        org.traincontrol.base.Locomotive train = null;
        Integer lengthWas = null;

        try
        {
            // The editor first: opening it builds the railway the trains stand on, which this claim run alone has not.
            openAnEditorOn(page, built);

            Components.drain(8);

            final TileKey standing = aSquareATrainStandsOn();

            assertNotNull(standing, "precondition: no train stands on a station square of " + PAGE);

            // No length while it is asked, as in testWhyNotMovingShowsBothTiersInATable: a long one fits nowhere.
            train = trainOn(standing);
            lengthWas = train == null ? null : train.getTrainLength();

            if (train != null) train.setTrainLength(0);

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field(panel, "whyButton");

            SwingUtilities.invokeAndWait(why::doClick);
            Components.drain(8);

            assertEquals(mark(panel, standing), TileAnnotation.ToolMark.CLICKABLE, "while Why not Moving? waits, the"
                + " square with a train is not marked as one to click");

            assertFalse(panel.isOutlined(standing), "while Why not Moving? waits, the square with a train still has"
                + " the selection's outline");

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(standing,
                page.getComponent(standing.getX(), standing.getY()), false));

            answered(panel);

            assertEquals(mark(panel, standing), TileAnnotation.ToolMark.PICKED, "the train asked about is not marked as"
                + " the square clicked");

            String said = shown(panel);

            assertEquals(marked(panel, TileAnnotation.ToolMark.CAN_GO), placesItCanGo(said), "the stations marked"
                + " green are not the stations the answer says it can go to: " + said);

            assertTrue(marked(panel, TileAnnotation.ToolMark.BY_HAND) > 0, "no station only a hand send can reach is"
                + " marked purple: " + said);

            // The other trains standing in the snapshot hold stations it cannot go to right now
            assertTrue(marked(panel, TileAnnotation.ToolMark.NOT_NOW) > 0, "no station it cannot go to right now is"
                + " marked gray: " + said);

            rowsSayTheMarks(panel, said);

            SwingUtilities.invokeAndWait(why::doClick);
            Components.drain(8);

            assertNull(mark(panel, standing), "putting Why not Moving? down left the train's square marked");

            assertEquals(markedAtAll(panel), 0, "putting Why not Moving? down kept the answer's marks");

            SwingUtilities.invokeAndWait(why::doClick);
            SwingUtilities.invokeAndWait(() -> panel.tileClicked(standing,
                page.getComponent(standing.getX(), standing.getY()), false));

            answered(panel);

            assertTrue(markedAtAll(panel) > 0, "precondition: asked again, the answer marks nothing");

            final TileKey free = aStationWithNoTrain();

            assertNotNull(free, "precondition: every station square of " + PAGE + " has a train");

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(free, page.getComponent(free.getX(), free.getY()),
                false));

            answered(panel);

            assertEquals(markedAtAll(panel), 0, "asked about " + free + ", where no train stands, the previous train's"
                + " marks stay: " + shown(panel));

            final java.util.Map<?, ?> lines = (java.util.Map<?, ?>) field(panel, "traces");
            final int[] drawn = new int[1];

            SwingUtilities.invokeAndWait(() -> drawn[0] = lines.size());

            assertEquals(drawn[0], 0, "asked about " + free + ", where no train stands, the previous train's lines stay");

            // Asked, then the tool put down and armed again before the answer can paint: it lands on the event thread
            SwingUtilities.invokeAndWait(() ->
            {
                panel.tileClicked(standing, page.getComponent(standing.getX(), standing.getY()), false);
                why.doClick();
                why.doClick();
            });

            answered(panel);

            assertEquals(markedAtAll(panel), 0, "an answer asked before the tool was put down and armed again marked the"
                + " stations");

            // And let go by a right-click's menu before the answer can paint
            final java.lang.reflect.Method cancel = Reflect.method(AutonomyEditorPanel.class, "cancelPendingGesture");

            SwingUtilities.invokeAndWait(() ->
            {
                panel.tileClicked(standing, page.getComponent(standing.getX(), standing.getY()), false);

                try
                {
                    cancel.invoke(panel);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new AssertionError(e);
                }
            });

            answered(panel);

            assertEquals(markedAtAll(panel), 0, "an answer asked before a right-click let go of the tool marked the"
                + " stations");
        }
        finally
        {
            if (train != null) train.setTrainLength(lengthWas == null ? 0 : lengthWas);

            closeTheEditor(built);
        }
    }

    /**
     * For every train on the page, Why not Moving?'s colors and lines agree with the routes the track diagram's own menu
     * offers (`Layout.getPossiblePaths`), worked out here without the answer's reasons (OB-394, FR-221): green where
     * autonomy has a route to a copy, else purple where a hand send has, else gray.  Every green and purple square has
     * a line ending at it, and no square draws one stretch of track twice.  Under a ranked Routing Logic the route is
     * autonomy's pick, the cheapest.
     *
     * MUTATION: mark a station purple without asking whether it could be sent there by hand in
     * `EditorWhy.composeWhy` (`DestinationTable.statusOf`), draw no purple routes there, stop a route one edge short in `EditorWhy.drawRoute`, add
     * every line without asking `alreadyDrawn` in `EditorWhy.addLine`, or take the first clear route in
     * `Layout.rankedFrom`, and this fails.  The shapes the snapshot lacks are claimed by
     * `testAWhySquareTakesAllItsCopiesAndAGreenLineStays`.
     */
    @Test
    public void testWhyNotMovingAgreesWithTheRoutesOffered() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            openAnEditorOn(page, built);

            Components.drain(8);

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field(panel, "whyButton");

            org.traincontrol.automation.Layout layout = model.getAutoLayout();

            java.lang.reflect.Method turns =
                Reflect.method(org.traincontrol.automation.Layout.class, "reversesAlongTheWay", java.util.List.class);

            int trains = 0;
            int purple = 0;
            int gray = 0;

            SwingUtilities.invokeAndWait(why::doClick);
            Components.drain(8);

            for (org.traincontrol.automation.Point at : layout.getPoints())
            {
                final org.traincontrol.base.Locomotive train = at.getCurrentLocomotive();
                final TileKey standing = session.getStationIndex().squareOf(at.getName());

                if (train == null || standing == null || !PAGE.equals(standing.getPage())) continue;

                trains++;

                // The routes the menu offers, then the ones autonomy would take, from Layout's route list alone
                java.util.Set<String> byHand = new java.util.HashSet<>();
                java.util.Set<String> byAutonomy = new java.util.HashSet<>();
                java.util.Map<String, java.util.List<org.traincontrol.automation.Edge>> firstByHand =
                    new java.util.HashMap<>();
                java.util.Map<String, java.util.List<org.traincontrol.automation.Edge>> firstByAutonomy =
                    new java.util.HashMap<>();
                java.util.Set<String> barred = layout.destinationsBarredFromAutonomy(train);

                for (java.util.List<org.traincontrol.automation.Edge> route : layout.getPossiblePaths(train, false))
                {
                    org.traincontrol.automation.Point end = route.get(route.size() - 1).getEnd();

                    if (!layout.isOfferableToOperator(end, train)) continue;

                    byHand.add(end.getName());
                    firstByHand.putIfAbsent(end.getName(), route);

                    if (!barred.contains(end.getName()) && !(Boolean) turns.invoke(layout, route))
                    {
                        byAutonomy.add(end.getName());
                        firstByAutonomy.putIfAbsent(end.getName(), route);
                    }
                }

                SwingUtilities.invokeAndWait(() -> panel.tileClicked(standing,
                    page.getComponent(standing.getX(), standing.getY()), false));

                answered(panel);

                String when = train.getName() + " at " + at.getName();

                // Each square's copies, then the square's color from all of them
                java.util.Set<TileKey> squares = new java.util.LinkedHashSet<>();
                java.util.Set<TileKey> anyGreen = new java.util.HashSet<>();
                java.util.Set<TileKey> anyByHand = new java.util.HashSet<>();

                for (org.traincontrol.automation.Point end : layout.getPoints())
                {
                    TileKey square = session.getStationIndex().squareOf(end.getName());

                    if (square == null || square.equals(standing) || !end.isDestination()) continue;

                    if (at.getBlock() != null && at.getBlock().equals(end.getBlock())) continue;

                    squares.add(square);

                    if (byAutonomy.contains(end.getName())) anyGreen.add(square);
                    if (byHand.contains(end.getName())) anyByHand.add(square);
                }

                java.util.Map<TileKey, TileAnnotation.ToolMark> expected = new java.util.HashMap<>();

                for (TileKey square : squares)
                {
                    expected.put(square, anyGreen.contains(square) ? TileAnnotation.ToolMark.CAN_GO
                        : anyByHand.contains(square) ? TileAnnotation.ToolMark.BY_HAND : TileAnnotation.ToolMark.NOT_NOW);
                }

                final java.util.Map<TileKey, TileAnnotation.ToolMark> shownMarks = new java.util.HashMap<>();
                final java.util.Map<TileKey, java.util.List<TileAnnotation.Trace>> lines =
                    new java.util.HashMap<>();

                @SuppressWarnings("unchecked")
                final java.util.Map<TileKey, TileAnnotation.ToolMark> live =
                    (java.util.Map<TileKey, TileAnnotation.ToolMark>) field(panel, "whyMarks");

                @SuppressWarnings("unchecked")
                final java.util.Map<TileKey, java.util.List<TileAnnotation.Trace>> drawn =
                    (java.util.Map<TileKey, java.util.List<TileAnnotation.Trace>>) field(panel, "traces");

                SwingUtilities.invokeAndWait(() ->
                {
                    shownMarks.putAll(live);

                    for (java.util.Map.Entry<TileKey, java.util.List<TileAnnotation.Trace>> e : drawn.entrySet())
                    {
                        lines.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
                    }
                });

                for (java.util.Map.Entry<TileKey, TileAnnotation.ToolMark> e : expected.entrySet())
                {
                    assertEquals(shownMarks.get(e.getKey()), e.getValue(), when + ", " + describe(panel, e.getKey())
                        + " is not colored as the routes offered say");

                    if (e.getValue() == TileAnnotation.ToolMark.BY_HAND) purple++;
                    if (e.getValue() == TileAnnotation.ToolMark.NOT_NOW) gray++;

                    if (e.getValue() == TileAnnotation.ToolMark.NOT_NOW) continue;

                    assertTrue(lines.containsKey(e.getKey()), when + ", no line reaches " + describe(panel,
                        e.getKey()));

                    if (e.getValue() != TileAnnotation.ToolMark.CAN_GO) continue;

                    boolean ownLine = false;

                    for (TileAnnotation.Trace line : lines.get(e.getKey())) ownLine |= !line.isManualOnly();

                    assertTrue(ownLine, when + ", " + describe(panel, e.getKey()) + " is reached only by a hand"
                        + " send's line: " + lines.get(e.getKey()));
                }

                // Each colored square's line follows a route the menu offers to one of its copies, through every
                // station on it: autonomy's first for green, the first by hand for purple
                for (java.util.Map.Entry<TileKey, TileAnnotation.ToolMark> e : expected.entrySet())
                {
                    if (e.getValue() == TileAnnotation.ToolMark.NOT_NOW) continue;

                    java.util.Map<String, java.util.List<org.traincontrol.automation.Edge>> first =
                        e.getValue() == TileAnnotation.ToolMark.CAN_GO ? firstByAutonomy : firstByHand;

                    boolean followed = false;
                    String missed = "";

                    for (String copy : session.getStationIndex().pointNamesAt(e.getKey()))
                    {
                        java.util.List<org.traincontrol.automation.Edge> route = first.get(copy);

                        if (route == null) continue;

                        String gap = null;

                        for (org.traincontrol.automation.Edge edge : route)
                        {
                            TileKey via = session.getStationIndex().squareOf(edge.getEnd().getName());

                            if (via != null && PAGE.equals(via.getPage()) && !lines.containsKey(via))
                            {
                                gap = describe(panel, via);
                            }
                        }

                        if (gap == null) followed = true;
                        else missed += " " + copy + " misses " + gap + ";";
                    }

                    assertTrue(followed, when + ", the line to " + describe(panel, e.getKey()) + " follows no"
                        + " route offered to it:" + missed);
                }

                // No stretch of track twice: the same two sides once, and no end of a line where another runs on
                for (java.util.Map.Entry<TileKey, java.util.List<TileAnnotation.Trace>> square : lines.entrySet())
                {
                    java.util.List<TileAnnotation.Trace> here = square.getValue();

                    for (int i = 0; i < here.size(); i++)
                    {
                        for (int j = i + 1; j < here.size(); j++)
                        {
                            assertFalse(sameStretch(here.get(i), here.get(j)), when + ", "
                                + describe(panel, square.getKey()) + " draws one stretch twice: " + here);
                        }
                    }
                }
            }
            assertTrue(trains > 0, "precondition: no train stands on " + PAGE);
            assertTrue(purple > 0, "precondition: no train has a station only a person could send it to");
            assertTrue(gray > 0, "precondition: no train has a station it cannot go to");

            // UNDER A RANKED ROUTING LOGIC the route drawn is the one autonomy would run: fewest stations on the way
            org.traincontrol.automation.Layout.PathPreference preferenceWas = layout.getPathPreference();
            int ranked = 0;

            try
            {
                layout.setPathPreference(org.traincontrol.automation.Layout.PathPreference.FEWEST_STATIONS);

                for (org.traincontrol.automation.Point at : layout.getPoints())
                {
                    final org.traincontrol.base.Locomotive train = at.getCurrentLocomotive();

                    if (train == null) continue;

                    java.util.Set<String> barred = layout.destinationsBarredFromAutonomy(train);
                    java.util.Map<String, Integer> fewest = new java.util.HashMap<>();
                    java.util.Map<String, Integer> most = new java.util.HashMap<>();

                    for (java.util.List<org.traincontrol.automation.Edge> route : layout.getPossiblePaths(train, false))
                    {
                        org.traincontrol.automation.Point end = route.get(route.size() - 1).getEnd();

                        if (barred.contains(end.getName()) || !layout.isOfferableToOperator(end, train)
                            || (Boolean) turns.invoke(layout, route) || !layout.isPathClear(route, train, false))
                        {
                            continue;
                        }

                        fewest.merge(end.getName(), stationsOn(route), Math::min);
                        most.merge(end.getName(), stationsOn(route), Math::max);
                    }

                    java.util.Map<String, java.util.List<org.traincontrol.automation.Edge>> drawnBy =
                        layout.explainDestinationsGrouped(train).getRoutes();

                    for (java.util.Map.Entry<String, Integer> end : fewest.entrySet())
                    {
                        java.util.List<org.traincontrol.automation.Edge> route = drawnBy.get(end.getKey());

                        assertTrue(route != null, train.getName() + " at " + at.getName() + " has no route to "
                            + end.getKey() + ", which autonomy could choose");
                        assertEquals(stationsOn(route), (int) end.getValue(), train.getName() + " at " + at.getName()
                            + ": the route to " + end.getKey() + " is not the one with the fewest stations on the way");

                        if (!most.get(end.getKey()).equals(end.getValue())) ranked++;
                    }
                }
            }
            finally
            {
                layout.setPathPreference(preferenceWas);
            }

            assertTrue(ranked > 0, "precondition: no station has two clear routes with different stations on the way");
        }
        finally
        {
            closeTheEditor(built);
        }
    }

    /**
     * The shapes the snapshot does not hold, built here (OB-394): a square one of whose copies a hand send reaches and
     * another nothing reaches is purple, in either order, as is one with a copy autonomy could choose but not now
     * (FR-221); a purple route through a green station keeps that station's own line, adding only its other half.
     *
     * MUTATION: let a later copy replace an earlier one in `EditorWhy.settle`, or let a purple line replace any stub in
     * `EditorWhy.addLine`, and this fails.
     */
    @Test
    public void testAWhySquareTakesAllItsCopiesAndAGreenLineStays() throws Exception
    {
        Class<?> why = Class.forName("org.traincontrol.gui.autonomyeditor.EditorWhy");

        java.lang.reflect.Method settle = why.getDeclaredMethod("settle", java.util.Map.class, TileKey.class,
            DestinationTable.Status.class);
        java.lang.reflect.Method addLine = why.getDeclaredMethod("addLine", java.util.List.class,
            TileAnnotation.Trace.class);

        settle.setAccessible(true);
        addLine.setAccessible(true);

        TileKey square = new TileKey("Main", 4, 4);

        DestinationTable.Status[][] copies = {
            {DestinationTable.Status.BY_HAND, DestinationTable.Status.NOT_NOW},
            {DestinationTable.Status.NOT_NOW, DestinationTable.Status.BY_HAND}};

        for (DestinationTable.Status[] order : copies)
        {
            java.util.Map<TileKey, DestinationTable.Status> best = new java.util.HashMap<>();

            for (DestinationTable.Status copy : order) settle.invoke(null, best, square, copy);

            assertEquals(best.get(square), DestinationTable.Status.BY_HAND, "a square a hand send reaches by one copy, "
                + java.util.Arrays.toString(order) + ", is not purple");
        }

        // The green station's own line ends here from the west; a purple route runs on through to the east
        java.util.List<TileAnnotation.Trace> here = new java.util.ArrayList<>();

        addLine.invoke(null, here, new TileAnnotation.Trace(org.traincontrol.automationui.TilePorts.Side.W, null, true,
            false));
        addLine.invoke(null, here, new TileAnnotation.Trace(org.traincontrol.automationui.TilePorts.Side.W,
            org.traincontrol.automationui.TilePorts.Side.E, true, true));

        boolean ownLine = false;

        for (TileAnnotation.Trace line : here) ownLine |= !line.isManualOnly();

        assertTrue(ownLine, "a purple route through a green station took over its line: " + here);
        assertEquals(here.size(), 2, "the purple route did not add its half beyond the station: " + here);

        for (int i = 0; i < here.size(); i++)
        {
            for (int j = i + 1; j < here.size(); j++)
            {
                assertFalse(sameStretch(here.get(i), here.get(j)), "one stretch is drawn twice: " + here);
            }
        }
    }

    /** Builds an editor on the page in autonomy mode, on the event thread, and puts it in `built[0]` at once. */
    private static void openAnEditorOn(LayoutDiagram page, LayoutEditor[] built) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });
    }

    /** Disposes of the editor in `built[0]`, if one was made. */
    private static void closeTheEditor(LayoutEditor[] built) throws Exception
    {
        if (built[0] != null)
        {
            final LayoutEditor closing = built[0];

            SwingUtilities.invokeAndWait(() -> closing.dispose());
        }
    }

    /** The stations a route passes on its way, not counting where it ends. */
    private static int stationsOn(java.util.List<org.traincontrol.automation.Edge> route)
    {
        int stations = 0;

        for (int i = 0; i < route.size() - 1; i++)
        {
            if (route.get(i).getEnd().isDestination()) stations++;
        }

        return stations;
    }

    /** Whether two lines in one square draw the same track: the same sides either way round, or one ends on it. */
    private static boolean sameStretch(TileAnnotation.Trace a, TileAnnotation.Trace b)
    {
        if ((a.getFrom() == b.getFrom() && a.getTo() == b.getTo())
            || (a.getFrom() == b.getTo() && a.getTo() == b.getFrom()))
        {
            return true;
        }

        boolean aEnds = a.getFrom() == null || a.getTo() == null;
        boolean bEnds = b.getFrom() == null || b.getTo() == null;

        if (aEnds == bEnds) return false;

        TileAnnotation.Trace stub = aEnds ? a : b;
        TileAnnotation.Trace through = aEnds ? b : a;

        org.traincontrol.automationui.TilePorts.Side side = stub.getFrom() != null ? stub.getFrom() : stub.getTo();

        return side == through.getFrom() || side == through.getTo();
    }

    /** Every station on the page marked CLICKABLE but the ones picked, and nothing else marked. */
    private static void everyStationIs(AutonomyEditorPanel panel, TileKey picked, TileKey alsoPicked, String when)
        throws Exception
    {
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (!PAGE.equals(tile.getPage()) || tile.equals(picked) || tile.equals(alsoPicked)) continue;

            assertEquals(mark(panel, tile), session.getStore().isStation(tile) ? TileAnnotation.ToolMark.CLICKABLE
                : null, when + ", " + describe(panel, tile) + " is marked wrongly");
        }
    }

    /** How many squares the Why answer gives this mark, on any page. */
    @SuppressWarnings("unchecked")
    private static int marked(AutonomyEditorPanel panel, TileAnnotation.ToolMark which) throws Exception
    {
        final java.util.Map<TileKey, TileAnnotation.ToolMark> marks =
            (java.util.Map<TileKey, TileAnnotation.ToolMark>) field(panel, "whyMarks");

        final int[] count = new int[1];

        SwingUtilities.invokeAndWait(() ->
        {
            for (TileAnnotation.ToolMark mark : marks.values()) if (mark == which) count[0]++;
        });

        return count[0];
    }

    /**
     * A square takes the best of its copies' answers whatever their order: can go over by hand over not now (FR-159,
     * FR-221), as the table files a station.
     *
     * MUTATION: let a later answer replace an earlier one in `EditorWhy.settle`, or put BY_HAND after NOT_NOW in
     * `DestinationTable.Status`, and this fails.
     */
    @Test
    public void testASquareTakesTheBestOfItsCopies() throws Exception
    {
        java.lang.reflect.Method settle = Class.forName("org.traincontrol.gui.autonomyeditor.EditorWhy")
            .getDeclaredMethod("settle", java.util.Map.class, TileKey.class, DestinationTable.Status.class);

        settle.setAccessible(true);

        DestinationTable.Status[][] orders = {
            {DestinationTable.Status.BY_HAND, DestinationTable.Status.CAN_GO, DestinationTable.Status.NOT_NOW},
            {DestinationTable.Status.CAN_GO, DestinationTable.Status.NOT_NOW, DestinationTable.Status.BY_HAND},
            {DestinationTable.Status.NOT_NOW, DestinationTable.Status.BY_HAND}};

        DestinationTable.Status[] best = {DestinationTable.Status.CAN_GO, DestinationTable.Status.CAN_GO,
            DestinationTable.Status.BY_HAND};

        TileKey square = new TileKey(PAGE, 1, 1);

        for (int i = 0; i < orders.length; i++)
        {
            java.util.Map<TileKey, DestinationTable.Status> answers = new java.util.HashMap<>();

            for (DestinationTable.Status answer : orders[i]) settle.invoke(null, answers, square, answer);

            assertEquals(answers.get(square), best[i], "copies answering " + java.util.Arrays.toString(orders[i])
                + " leave the square answering " + answers.get(square));
        }
    }

    /**
     * Each station of the page the answer marks has its row in the answer's table saying the same, the best of the
     * squares sharing its name: the colors named are the colors drawn.
     */
    private static void rowsSayTheMarks(AutonomyEditorPanel panel, String answer) throws Exception
    {
        java.util.Map<String, String> rows = new java.util.HashMap<>();

        for (String[] row : support.DestinationRows.of(answer)) rows.put(row[0], row[1]);

        assertFalse(rows.isEmpty(), "the answer has no table of destinations: " + answer);

        // Best first, as the table files a station
        String[][] words = {{"CAN_GO", "autolayout.ui.whyCanGoNow"}, {"BY_HAND", "autolayout.ui.whyByHandOnly"},
            {"NOT_NOW", "autolayout.ui.whyNotRightNow"}};

        java.util.Map<String, String> best = new java.util.HashMap<>();

        for (String[] word : words)
        {
            for (TileKey square : markedAs(panel, TileAnnotation.ToolMark.valueOf(word[0])))
            {
                if (PAGE.equals(square.getPage())) best.putIfAbsent(describe(panel, square), word[1]);
            }
        }

        for (java.util.Map.Entry<String, String> station : best.entrySet())
        {
            assertEquals(rows.get(station.getKey()), org.traincontrol.util.I18n.t(station.getValue()), station.getKey()
                + " is not shown in the table as it is colored: " + answer);
        }
    }

    /** The squares the answer marks this way, read on the event thread. */
    @SuppressWarnings("unchecked")
    private static java.util.List<TileKey> markedAs(AutonomyEditorPanel panel, TileAnnotation.ToolMark which)
        throws Exception
    {
        final java.util.Map<TileKey, TileAnnotation.ToolMark> marks =
            (java.util.Map<TileKey, TileAnnotation.ToolMark>) field(panel, "whyMarks");

        final java.util.List<TileKey> out = new java.util.ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            for (java.util.Map.Entry<TileKey, TileAnnotation.ToolMark> e : marks.entrySet())
            {
                if (e.getValue() == which) out.add(e.getKey());
            }
        });

        return out;
    }

    /** How many squares the Why answer marks at all. */
    private static int markedAtAll(AutonomyEditorPanel panel) throws Exception
    {
        int all = 0;

        for (TileAnnotation.ToolMark which : TileAnnotation.ToolMark.values()) all += marked(panel, which);

        return all;
    }

    /** A station square of the page with no train on it, or null. */
    private static TileKey aStationWithNoTrain()
    {
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getStore().isStation(tile)
                && session.getLocomotiveNameAt(tile) == null) return tile;
        }

        return null;
    }

    /** How the editor marks a square for its tools, asked on the event thread. */
    private static TileAnnotation.ToolMark mark(final AutonomyEditorPanel panel, final TileKey tile) throws Exception
    {
        final TileAnnotation.ToolMark[] out = new TileAnnotation.ToolMark[1];

        SwingUtilities.invokeAndWait(() -> out[0] = panel.annotationFor(tile).getToolMark());

        return out[0];
    }

    /**
     * Why Not Moving? answers for autonomy and a hand send at once, in a table (FR-221; Adam: "the output should
     * include a table of destinations, rather than just text"): a station autonomy leaves alone that a person may send
     * the train to is "By hand only" with autonomy's reason, and one it can go to now has no reason.  The answer stays
     * until the next notice, never fading on a timer (MT-864), and closes when the user goes back to editing: the tool's
     * button pressed again, or Escape.
     *
     * MUTATION: in `AutonomyEditorPanel.backToEditing`, always say the editing hint, and the answer is replaced but the
     * strip stays up.  In `DestinationTable.reasonOf`, give a by-hand station the hand send's reason, and this fails.
     */
    @Test
    public void testWhyNotMovingShowsBothTiersInATable() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        assertNotNull(page, "precondition: no page " + PAGE);

        final TileKey standing = aSquareATrainStandsOn();

        assertNotNull(standing, "precondition: no train stands on a station square of " + PAGE);

        // NOT THE LENGTH THE REAL DATABASE GIVES IT: the frozen railway's train lengths are read from the locomotive
        // database on this machine, and a long one fits nowhere ("0 against 0").  So the train asked about has no
        // length while it is asked, and has its own back afterwards.
        final org.traincontrol.base.Locomotive train = trainOn(standing);
        final Integer lengthWas = train == null ? null : train.getTrainLength();

        if (train != null) train.setTrainLength(0);

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            openAnEditorOn(page, built);

            Components.drain(8);

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field(panel, "whyButton");

            SwingUtilities.invokeAndWait(() ->
            {
                why.doClick();
                panel.tileClicked(standing, page.getComponent(standing.getX(), standing.getY()), false);
            });

            answered(panel);

            String said = shown(panel);

            assertFalse(fading(panel), "the Why answer fades on a timer (MT-864): " + said);

            for (String heading : new String[] {"autolayout.ui.whyColumnStation", "autolayout.ui.whyColumnStatus",
                "autolayout.ui.whyColumnReason"})
            {
                assertTrue(said.contains(">" + org.traincontrol.util.I18n.t(heading) + "</th>"),
                    "the answer's table has no " + heading + " column: " + said);
            }

            String notChosen = org.traincontrol.util.I18n.t("autolayout.why.notAutoDestination");
            String byHand = org.traincontrol.util.I18n.t("autolayout.ui.whyByHandOnly");
            String canGo = org.traincontrol.util.I18n.t("autolayout.ui.whyCanGoNow");

            int leftAlone = 0;

            for (String[] row : support.DestinationRows.of(said))
            {
                if (row[1].equals(canGo)) assertEquals(row[2], "", row[0] + " can go now and is given a reason: " + said);

                if (row[1].equals(byHand))
                {
                    assertFalse(row[2].isEmpty(), row[0] + " is by hand only and does not say why autonomy will not: "
                        + said);

                    if (row[2].equals(notChosen)) leftAlone++;
                }
            }

            assertTrue(leftAlone > 0, "no station autonomy leaves alone is shown as one a person may send the train to,"
                + " with autonomy's reason: " + said);

            // BACK TO EDITING CLOSES THE ANSWER: the tool's button pressed again, or Escape putting the tools down.
            final java.awt.Component strip = (java.awt.Component) field(panel, "messageBanner");

            assertTrue(support.Components.onTheEventThread(strip::isVisible), "precondition: the strip is not showing the answer");

            SwingUtilities.invokeAndWait(why::doClick);

            assertFalse(support.Components.onTheEventThread(strip::isVisible),
                "Why Not Moving? pressed again leaves its answer on the strip: " + shown(panel));

            SwingUtilities.invokeAndWait(() ->
            {
                why.doClick();
                panel.tileClicked(standing, page.getComponent(standing.getX(), standing.getY()), false);
            });

            answered(panel);

            assertTrue(support.Components.onTheEventThread(strip::isVisible), "precondition: the second answer is not on the strip");

            SwingUtilities.invokeAndWait(panel::putToolsDown);

            assertFalse(support.Components.onTheEventThread(strip::isVisible),
                "Escape putting the tools down leaves the answer on the strip: " + shown(panel));
        }
        finally
        {
            if (train != null) train.setTrainLength(lengthWas == null ? 0 : lengthWas);

            closeTheEditor(built);
        }
    }

    /** Whether the banner's message is timed to fade. */
    private static boolean fading(AutonomyEditorPanel panel) throws Exception
    {
        Object banner = field(panel, "messageBanner");

        assertNotNull(banner, "precondition: the editor has no banner");

        return ((javax.swing.Timer) field(banner, "clear")).isRunning();
    }

    /** A station square on the page a train is standing on, as the railway sees it. */
    private static TileKey aSquareATrainStandsOn()
    {
        org.traincontrol.automation.Layout layout = model.getAutoLayout();

        if (layout == null) return null;

        org.traincontrol.automationui.StationIndex index = session.getStationIndex();

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            TileKey square = index.squareOf(point.getName());

            if (square != null && PAGE.equals(square.getPage())) return square;
        }

        return null;
    }

    /** The train the railway has standing on this square, or null. */
    private static org.traincontrol.base.Locomotive trainOn(TileKey square)
    {
        org.traincontrol.automation.Layout layout = model.getAutoLayout();

        if (layout == null) return null;

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null && square.equals(session.getStationIndex().squareOf(point.getName())))
            {
                return point.getCurrentLocomotive();
            }
        }

        return null;
    }

    /** Waits for the worker to finish the answer, then for the event thread to paint it. */
    private static void answered(AutonomyEditorPanel panel) throws Exception
    {
        java.lang.reflect.Method await = Reflect.method(AutonomyEditorPanel.class, "awaitWhy", long.class);

        assertTrue((Boolean) await.invoke(panel, 120000L), "the Why answer was not worked out within two minutes");

        Components.drain(8);
    }

    /** The count in "can go to N stations now", or 0 where the answer says it can go nowhere. */
    private static int placesItCanGo(String shown)
    {
        String pattern = org.traincontrol.util.I18n.t("autosetup.ui.whyCanGo");
        String before = pattern.substring(pattern.indexOf("{0}") + 3, pattern.indexOf("{1}"));

        java.util.regex.Matcher count = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(before) + "(\\d+)")
            .matcher(shown);

        return count.find() ? Integer.parseInt(count.group(1)) : 0;
    }

    /** What the editor shows the operator: the hint line and the banner across the top, together. */
    private static String shown(AutonomyEditorPanel panel) throws Exception
    {
        StringBuilder out = new StringBuilder();

        Object hint = field(panel, "hint");

        if (hint instanceof javax.swing.JLabel && ((javax.swing.JLabel) hint).getText() != null)
        {
            out.append(((javax.swing.JLabel) hint).getText());
        }

        // THE BANNER KEEPS WHAT IT IS SAYING AS TEXT (AutonomyBanner.saying), not in child labels: every message the
        // panel says goes there when the editor has one, which Adam's editor does.
        Object banner = field(panel, "messageBanner");

        if (banner != null)
        {
            Object saying = field(banner, "saying");

            if (saying != null) out.append(" | ").append(saying);
        }

        return out.toString();
    }

    private static void collectText(java.awt.Container container, StringBuilder out)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JLabel && ((javax.swing.JLabel) child).getText() != null)
            {
                out.append(" | ").append(((javax.swing.JLabel) child).getText());
            }

            if (child instanceof java.awt.Container) collectText((java.awt.Container) child, out);
        }
    }

    private static String describe(AutonomyEditorPanel panel, TileKey tile) throws Exception
    {
        java.lang.reflect.Method describe = Reflect.method(AutonomyEditorPanel.class, "describeTile", TileKey.class);

        String text = String.valueOf(describe.invoke(panel, tile));

        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** Two stations on the page, joined both ways by autonomy's route, the second one autonomy may choose today. */
    private static TileKey[] aConnectedPairOfStations()
    {
        java.util.List<TileKey> stations = new java.util.ArrayList<>();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getStore().isStation(tile)
                && !session.manualOnlyStations().contains(tile))
            {
                stations.add(tile);
            }
        }

        for (TileKey a : stations)
        {
            for (TileKey b : stations)
            {
                if (a.equals(b)) continue;

                if (session.testPathForAutonomy(a, b) != null && session.testPathForAutonomy(b, a) != null)
                {
                    return new TileKey[] { a, b };
                }
            }
        }

        return null;
    }

    private static Object field(Object on, String name) throws Exception
    {
        Class<?> type = on.getClass();

        while (type != null)
        {
            try
            {
                return Reflect.get(type, on, name);
            }
            catch (NoSuchFieldException notHere)
            {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }
}
