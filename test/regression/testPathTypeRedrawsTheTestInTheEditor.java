package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Switching Path Type redraws the path test in the real autonomy editor (OB-225).
 *
 * The note "will never be chosen in autonomy" is written only while Auto is selected, so seeing it on Manual means the
 * test was never run again.  The first claim missed it:
 * `core.testManualOnlyPathsAreADifferentColour.testSwitchingPathTypeRedrawsTheTestedRoute` drove `applyTest` on a panel
 * with no page and no window and compared the route against an empty substring.  This class asks the editor Adam uses:
 * a `LayoutEditor` in autonomy mode on the frozen snapshot, Test a Path pressed, the two squares clicked through
 * `tileClicked` (what the grid calls) and the radio clicked.
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
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration on the snapshot");
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
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

    /** Test a Path to a station autonomy will never choose, then Manual: the note goes, and Auto brings it back. */
    @Test
    public void testSwitchingPathTypeRedrawsTheTestInTheEditor() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        assertNotNull(page, "precondition: no page " + PAGE);

        TileKey[] pair = aConnectedPairOfStations();

        assertNotNull(pair, "precondition: no two stations on " + PAGE + " with a route between them");

        final TileKey from = pair[0];
        final TileKey to = pair[1];

        org.json.JSONObject asFound = session.snapshotSetup();

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            session.setAutoDestination(to, false);

            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            settle();

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();

            // ARMED AS A PERSON ARMS IT, and the squares clicked through the door the grid uses.
            final javax.swing.AbstractButton test = (javax.swing.AbstractButton) field(panel, "testButton");

            SwingUtilities.invokeAndWait(() ->
            {
                test.doClick();
                panel.tileClicked(from, page.getComponent(from.getX(), from.getY()), false);
                panel.tileClicked(to, page.getComponent(to.getX(), to.getY()), false);
            });

            settle();

            String note = org.traincontrol.util.I18n.t("autosetup.ui.testNotAnAutoDestination");
            String noteStart = note.substring(0, note.indexOf("{0}"));

            String onAuto = shown(panel);

            assertTrue(onAuto.contains(noteStart),
                "precondition: on Auto the tested route to a station autonomy will never choose does not say so: " + onAuto);

            final javax.swing.AbstractButton manual = (javax.swing.AbstractButton) field(panel, "pathTypeManual");

            SwingUtilities.invokeAndWait(manual::doClick);

            settle();

            String onManual = shown(panel);

            assertFalse(onManual.contains(noteStart),
                "Path Type switched to Manual in the editor and the Auto verdict is still on screen - the test was not run"
                + " again.  Adam, MT-434: 'in manual mode, I still get reasons like tunnellongpark will never be chosen in"
                + " autonomy'.  It says: " + onManual);

            assertTrue(onManual.contains(describe(panel, to)),
                "Path Type switched to Manual and the tested route is no longer named on screen: " + onManual);

            final javax.swing.AbstractButton auto = (javax.swing.AbstractButton) field(panel, "pathTypeAuto");

            SwingUtilities.invokeAndWait(auto::doClick);

            settle();

            assertTrue(shown(panel).contains(noteStart),
                "switching back to Auto did not put the Auto verdict back: " + shown(panel));
        }
        finally
        {
            if (built[0] != null)
            {
                final LayoutEditor closing = built[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            session.restoreSetup(asFound);
        }
    }

    /**
     * Test a Path outlines in orange what can be clicked: every station once it is armed, the start in blue once
     * clicked, and every station again with the answer, when the next click starts a new test; nothing once the tool is
     * put down (FR-159; OB-395, Adam: "I want them to be orange when I click and then they can become blue post
     * click").  No station takes Why not Moving?'s colors here (MT-854: "The station highlighting in green/purple
     * should happen in "why not moving", not test a path").
     *
     * MUTATION: drop the `annotation.markedByATool` call in `AutonomyEditorPanel.annotationFor`, or mark `testFrom`
     * CLICKABLE in `toolMark`, and this fails.
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
            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            settle();

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton test = (javax.swing.AbstractButton) field(panel, "testButton");

            assertNull(mark(panel, to), "precondition: a station is marked before Test a Path is pressed");

            SwingUtilities.invokeAndWait(test::doClick);
            settle();

            everyStationIs(panel, null, "armed, with no start chosen");

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(from, page.getComponent(from.getX(), from.getY()),
                false));
            settle();

            assertEquals(mark(panel, from), TileAnnotation.ToolMark.PICKED, "the start is not marked as the square"
                + " clicked");

            assertFalse(panel.isOutlined(from), "the start still has the selection's outline");

            everyStationIs(panel, from, "with the start chosen");

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(to, page.getComponent(to.getX(), to.getY()), false));
            settle();

            everyStationIs(panel, null, "with the answer shown, when the next click starts a new test,");

            SwingUtilities.invokeAndWait(test::doClick);
            settle();

            assertNull(mark(panel, to), "putting Test a Path down left the station marked");
            assertNull(mark(panel, from), "putting Test a Path down left the start marked");
        }
        finally
        {
            if (built[0] != null)
            {
                final LayoutEditor closing = built[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
    }

    /**
     * Why not Moving? outlines in orange the squares with a train while it waits, then, with its answer, the train's
     * square in blue and each station in the answer's colors: green where it can go now, gray where it cannot right
     * now, and purple, on Auto alone, where autonomy will never choose it but it could be sent by hand (OB-394), with a
     * line saying so (FR-159; Adam, MT-854: "The station highlighting in green/purple should happen in "why not moving"").
     *
     * The colors named are the colors drawn; putting the tool down, or asking about a square with no train, takes the
     * marks off (and asking about a square with no train, the lines), and an answer that lands after the tool was put
     * down and armed again, or after a right-click let go of the tool, marks nothing.
     *
     * MUTATION: install no marks in `AutonomyEditorPanel.paintWhy`, mark the never-chosen stations NOT_NOW or none
     * NOT_NOW in `EditorWhy.composeWhy`, leave the marks or `whyAsked` alone in `clearGesture`, leave `whyAsked` alone
     * in `cancelPendingGesture`, or give the no-train answer no marks or no lines, and this fails.
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
            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            settle();

            final TileKey standing = aSquareATrainStandsOn();

            assertNotNull(standing, "precondition: no train stands on a station square of " + PAGE);

            // No length while it is asked, as in testWhyNotMovingFollowsPathType: a long one fits nowhere.
            train = trainOn(standing);
            lengthWas = train == null ? null : train.getTrainLength();

            if (train != null) train.setTrainLength(0);

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field(panel, "whyButton");

            SwingUtilities.invokeAndWait(why::doClick);
            settle();

            assertEquals(mark(panel, standing), TileAnnotation.ToolMark.CLICKABLE, "while Why not Moving? waits, the"
                + " square with a train is not marked as one to click");

            assertFalse(panel.isOutlined(standing), "while Why not Moving? waits, the square with a train still has"
                + " the selection's outline");

            SwingUtilities.invokeAndWait(() -> panel.tileClicked(standing,
                page.getComponent(standing.getX(), standing.getY()), false));

            answered(panel);

            assertEquals(mark(panel, standing), TileAnnotation.ToolMark.PICKED, "the train asked about is not marked as"
                + " the square clicked");

            String onAuto = shown(panel);

            assertEquals(marked(panel, TileAnnotation.ToolMark.CAN_GO), placesItCanGo(onAuto), "the stations marked"
                + " green are not the stations the answer says it can go to: " + onAuto);

            assertTrue(marked(panel, TileAnnotation.ToolMark.NEVER) > 0, "on Auto, no station autonomy will never"
                + " choose is marked purple: " + onAuto);


            // The other trains standing in the snapshot hold stations it cannot go to right now
            assertTrue(marked(panel, TileAnnotation.ToolMark.NOT_NOW) > 0, "on Auto, no station it cannot go to right"
                + " now is marked gray: " + onAuto);

            coloursSaid(panel, onAuto, "on Auto");

            final javax.swing.AbstractButton manual = (javax.swing.AbstractButton) field(panel, "pathTypeManual");

            SwingUtilities.invokeAndWait(manual::doClick);

            answered(panel);

            String onManual = shown(panel);

            assertEquals(marked(panel, TileAnnotation.ToolMark.NEVER), 0, "on Manual, a station is marked as one"
                + " autonomy will never choose: " + onManual);

            assertEquals(marked(panel, TileAnnotation.ToolMark.CAN_GO), placesItCanGo(onManual), "on Manual, the"
                + " stations marked green are not the stations the answer says it can go to: " + onManual);

            coloursSaid(panel, onManual, "on Manual");

            SwingUtilities.invokeAndWait(why::doClick);
            settle();

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
            final java.lang.reflect.Method cancel = AutonomyEditorPanel.class.getDeclaredMethod("cancelPendingGesture");

            cancel.setAccessible(true);

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

            if (built[0] != null)
            {
                final LayoutEditor closing = built[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
    }

    /**
     * For every train on the page and both tiers, Why not Moving?'s colors and lines agree with the routes the track
     * diagram's own menu offers (`Layout.getPossiblePaths`), worked out here without the answer's reasons (OB-394).
     *
     * A station is green where a route reaches it that its tier accepts (on Auto, one autonomy may choose that does not
     * turn on the way), purple on Auto where only a person could send the train there, gray otherwise; a square takes
     * green over gray over purple from its copies.  Every green and purple square has a line ending at it, its route's
     * stations all carry the line, and no square draws one stretch of track twice.
     *
     * MUTATION: mark a never-chosen station purple without asking whether it could be sent there by hand in
     * `EditorWhy.composeWhy`, draw no purple routes there, stop a route one edge short in `EditorWhy.drawRoute`, or add
     * every line there without asking `alreadyDrawn`, and this fails.
     */
    @Test
    public void testWhyNotMovingAgreesWithTheRoutesOffered() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            settle();

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field(panel, "whyButton");
            final javax.swing.AbstractButton auto = (javax.swing.AbstractButton) field(panel, "pathTypeAuto");
            final javax.swing.AbstractButton manual = (javax.swing.AbstractButton) field(panel, "pathTypeManual");
            final boolean wasAuto = auto.isSelected();

            org.traincontrol.automation.Layout layout = model.getAutoLayout();

            java.lang.reflect.Method turns = org.traincontrol.automation.Layout.class.getDeclaredMethod(
                "reversesAlongTheWay", java.util.List.class);

            turns.setAccessible(true);

            int trains = 0;
            int purple = 0;
            int gray = 0;

            SwingUtilities.invokeAndWait(why::doClick);
            settle();

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

                for (final javax.swing.AbstractButton tier : new javax.swing.AbstractButton[] {auto, manual})
                {
                    final boolean onAuto = tier == auto;

                    SwingUtilities.invokeAndWait(tier::doClick);
                    SwingUtilities.invokeAndWait(() -> panel.tileClicked(standing,
                        page.getComponent(standing.getX(), standing.getY()), false));

                    answered(panel);

                    String when = train.getName() + " at " + at.getName() + (onAuto ? ", on Auto" : ", on Manual");

                    java.util.Map<TileKey, TileAnnotation.ToolMark> expected = new java.util.HashMap<>();

                    for (org.traincontrol.automation.Point end : layout.getPoints())
                    {
                        TileKey square = session.getStationIndex().squareOf(end.getName());

                        if (square == null || square.equals(standing) || !end.isDestination()) continue;

                        if (at.getBlock() != null && at.getBlock().equals(end.getBlock())) continue;

                        boolean green = (onAuto ? byAutonomy : byHand).contains(end.getName());
                        boolean isPurple = onAuto && !green && barred.contains(end.getName())
                            && byHand.contains(end.getName());

                        TileAnnotation.ToolMark mark = green ? TileAnnotation.ToolMark.CAN_GO
                            : isPurple ? TileAnnotation.ToolMark.NEVER : TileAnnotation.ToolMark.NOT_NOW;

                        TileAnnotation.ToolMark had = expected.get(square);

                        if (had == null || mark.ordinal() < had.ordinal()) expected.put(square, mark);
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

                        if (e.getValue() == TileAnnotation.ToolMark.NEVER) purple++;
                        if (e.getValue() == TileAnnotation.ToolMark.NOT_NOW) gray++;

                        if (e.getValue() == TileAnnotation.ToolMark.NOT_NOW) continue;

                        assertTrue(lines.containsKey(e.getKey()), when + ", no line reaches " + describe(panel,
                            e.getKey()));
                    }

                    // Each colored square's line follows a route the menu offers to one of its copies, through every
                    // station on it: autonomy's first on Auto for green, the first by hand otherwise
                    for (java.util.Map.Entry<TileKey, TileAnnotation.ToolMark> e : expected.entrySet())
                    {
                        if (e.getValue() == TileAnnotation.ToolMark.NOT_NOW) continue;

                        java.util.Map<String, java.util.List<org.traincontrol.automation.Edge>> first =
                            onAuto && e.getValue() == TileAnnotation.ToolMark.CAN_GO ? firstByAutonomy : firstByHand;

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
            }

            assertTrue(trains > 0, "precondition: no train stands on " + PAGE);
            assertTrue(purple > 0, "precondition: no train has a station only a person could send it to");
            assertTrue(gray > 0, "precondition: no train has a station it cannot go to");

            SwingUtilities.invokeAndWait((wasAuto ? auto : manual)::doClick);
        }
        finally
        {
            if (built[0] != null)
            {
                final LayoutEditor closing = built[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
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

    /** Every station on the page marked CLICKABLE but the one picked, and nothing else marked. */
    private static void everyStationIs(AutonomyEditorPanel panel, TileKey picked, String when) throws Exception
    {
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (!PAGE.equals(tile.getPage()) || tile.equals(picked)) continue;

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
     * A square takes the best of its copies' answers whatever their order: can go over not now over never, and the
     * train's own square keeps its mark (FR-159).
     *
     * MUTATION: let a later answer replace an earlier one in `EditorWhy.settle`, or move PICKED after the answers in
     * `TileAnnotation.ToolMark`, and this fails.
     */
    @Test
    public void testASquareTakesTheBestOfItsCopies() throws Exception
    {
        java.lang.reflect.Method settle = Class.forName("org.traincontrol.gui.autonomyeditor.EditorWhy")
            .getDeclaredMethod("settle", java.util.Map.class, TileKey.class, TileAnnotation.ToolMark.class);

        settle.setAccessible(true);

        TileAnnotation.ToolMark[][] orders = {
            {TileAnnotation.ToolMark.NEVER, TileAnnotation.ToolMark.CAN_GO, TileAnnotation.ToolMark.NOT_NOW},
            {TileAnnotation.ToolMark.CAN_GO, TileAnnotation.ToolMark.NOT_NOW, TileAnnotation.ToolMark.NEVER},
            {TileAnnotation.ToolMark.NOT_NOW, TileAnnotation.ToolMark.NEVER}};

        TileAnnotation.ToolMark[] best = {TileAnnotation.ToolMark.CAN_GO, TileAnnotation.ToolMark.CAN_GO,
            TileAnnotation.ToolMark.NOT_NOW};

        TileKey square = new TileKey(PAGE, 1, 1);

        for (int i = 0; i < orders.length; i++)
        {
            java.util.Map<TileKey, TileAnnotation.ToolMark> marks = new java.util.HashMap<>();

            for (TileAnnotation.ToolMark answer : orders[i]) settle.invoke(null, marks, square, answer);

            assertEquals(marks.get(square), best[i], "copies answering " + java.util.Arrays.toString(orders[i])
                + " leave the square marked " + marks.get(square));
        }

        java.util.Map<TileKey, TileAnnotation.ToolMark> marks = new java.util.HashMap<>();

        marks.put(square, TileAnnotation.ToolMark.PICKED);

        settle.invoke(null, marks, square, TileAnnotation.ToolMark.CAN_GO);

        assertEquals(marks.get(square), TileAnnotation.ToolMark.PICKED, "a station's answer took the train's own"
            + " square's blue");
    }

    /** The answer names each color exactly where it marks a station in it. */
    private static void coloursSaid(AutonomyEditorPanel panel, String answer, String when) throws Exception
    {
        String[][] colours = {{"CAN_GO", "autosetup.ui.whyColourGreen"}, {"NOT_NOW", "autosetup.ui.whyColourGray"},
            {"NEVER", "autosetup.ui.whyColourPurple"}};

        for (String[] colour : colours)
        {
            boolean drawn = marked(panel, TileAnnotation.ToolMark.valueOf(colour[0])) > 0;

            assertEquals(answer.contains(org.traincontrol.util.I18n.t(colour[1])), drawn, when + ", the answer "
                + (drawn ? "does not say" : "says") + " what " + colour[0] + " means: " + answer);
        }
    }

    /** How many squares the Why answer marks at all. */
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
     * Why Not Moving? answers for the tier Path Type names, and switching the radio answers again (OB-225).
     *
     * On Auto the answer files the stations autonomy is told to leave alone under "will never choose"; on Manual a
     * person may send a train to every one of them, so neither that heading nor autonomy's reason belongs in the
     * answer.  The square is the one a train stands on in the frozen snapshot, clicked once; only the radio is clicked
     * after that.
     */
    @Test
    public void testWhyNotMovingFollowsPathType() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        assertNotNull(page, "precondition: no page " + PAGE);

        final TileKey standing = aSquareATrainStandsOn();

        assertNotNull(standing, "precondition: no train stands on a station square of " + PAGE);

        // NOT THE LENGTH THE REAL DATABASE GIVES IT: the frozen railway's train lengths are read from the locomotive
        // database on this machine, and a long one fits nowhere on either tier ("0 against 0").  So the train asked
        // about has no length while it is asked, and has its own back afterwards.
        final org.traincontrol.base.Locomotive train = trainOn(standing);
        final Integer lengthWas = train == null ? null : train.getTrainLength();

        if (train != null) train.setTrainLength(0);

        final LayoutEditor[] built = new LayoutEditor[1];

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                built[0] = new LayoutEditor(page, 30, ui, 0);
                built[0].render();
                built[0].setAutonomyMode(session);
            });

            settle();

            final AutonomyEditorPanel panel = built[0].getAutonomyPanel();
            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field(panel, "whyButton");

            SwingUtilities.invokeAndWait(() ->
            {
                why.doClick();
                panel.tileClicked(standing, page.getComponent(standing.getX(), standing.getY()), false);
            });

            answered(panel);

            String neverHeading = headingStart("autolayout.ui.whyHeaderBarred");
            String notChosen = org.traincontrol.util.I18n.t("autolayout.why.notAutoDestination");

            String onAuto = shown(panel);

            assertTrue(onAuto.contains(neverHeading),
                "precondition: on Auto the answer names no station autonomy will never choose, so the tier cannot be"
                + " seen to change: " + onAuto);

            final javax.swing.AbstractButton manual = (javax.swing.AbstractButton) field(panel, "pathTypeManual");

            SwingUtilities.invokeAndWait(manual::doClick);

            answered(panel);

            String onManual = shown(panel);

            assertFalse(onManual.contains(neverHeading) || onManual.contains(notChosen),
                "Path Type is Manual and Why Not Moving? still answers for autonomy.  Adam, MT-434: 'in manual mode, I"
                + " still get reasons like tunnellongpark will never be chosen in autonomy'.  It says: " + onManual);

            // AND AN ANSWER, not only the absence of autonomy's: the stations autonomy will never choose are
            // somewhere a person may send it, so more of them are places it can go - an empty or failed answer has none.
            int canGoOnAuto = placesItCanGo(onAuto);
            int canGoOnManual = placesItCanGo(onManual);

            assertTrue(canGoOnManual > canGoOnAuto,
                "Path Type is Manual and the answer names " + canGoOnManual + " station(s) it can go to, against "
                + canGoOnAuto + " on Auto, though the stations autonomy will never choose are somewhere a person may send"
                + " it: " + onManual);

            final javax.swing.AbstractButton auto = (javax.swing.AbstractButton) field(panel, "pathTypeAuto");

            SwingUtilities.invokeAndWait(auto::doClick);

            answered(panel);

            assertTrue(shown(panel).contains(neverHeading),
                "switching back to Auto did not bring autonomy's answer back: " + shown(panel));
        }
        finally
        {
            if (train != null) train.setTrainLength(lengthWas == null ? 0 : lengthWas);

            if (built[0] != null)
            {
                final LayoutEditor closing = built[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
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
        java.lang.reflect.Method await = AutonomyEditorPanel.class.getDeclaredMethod("awaitWhy", long.class);

        await.setAccessible(true);

        assertTrue((Boolean) await.invoke(panel, 120000L), "the Why answer was not worked out within two minutes");

        settle();
    }

    /** The count in "can go to N station(s)", or 0 where the answer says it can go nowhere. */
    private static int placesItCanGo(String shown)
    {
        String pattern = org.traincontrol.util.I18n.t("autosetup.ui.whyCanGo");
        String before = pattern.substring(0, pattern.indexOf("{0}"));

        java.util.regex.Matcher count = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(before) + "(\\d+)")
            .matcher(shown);

        return count.find() ? Integer.parseInt(count.group(1)) : 0;
    }

    /** A heading's words before its count. */
    private static String headingStart(String key)
    {
        String heading = org.traincontrol.util.I18n.t(key);

        return heading.substring(0, heading.indexOf("{0}")).trim();
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
        java.lang.reflect.Method describe = AutonomyEditorPanel.class.getDeclaredMethod("describeTile", TileKey.class);

        describe.setAccessible(true);

        String text = String.valueOf(describe.invoke(panel, tile));

        return text.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** Two stations on the page, joined by a route the reducer finds, the second one autonomy may choose today. */
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

                if (session.getReducer().findPath(a, b, session.mayTurnTiles(), session.mandatoryTurnTiles(),
                    session.barredArrivals(), session.shutTiles()) != null)
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
                java.lang.reflect.Field f = type.getDeclaredField(name);

                f.setAccessible(true);

                return f.get(on);
            }
            catch (NoSuchFieldException notHere)
            {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
