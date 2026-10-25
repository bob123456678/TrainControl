package core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.ProgramConstants;

/**
 * A simulated path to a station autonomy will never choose is drawn in a color of its own.
 *
 * Adam: "just use a different color going to manual-only points. yellow is currently forward, and orange is backwards-
 * path, not the chevron arrows."  So this is about the editor's path tracing only, not the arrival chevrons or the
 * one-way restriction arrows.  A leg whose DESTINATION is a parking berth, or a square that turns every train it
 * takes, is drawn in a third color, because "the track is passable" and "autonomy will ever use it" are different
 * answers.
 *
 * Read off the painted pixels, not off the source: `paint` is called for real and the line's own color is read back
 * out of the image, since a test that inspected the flag would pass with all three drawn identically.  The exact
 * shades are asserted for the two that already existed, so redefining one of them into the new one cannot pass; the
 * new one is asserted only to differ from both, from the chevron on top of it, and from gray.
 *
 * @author Adam
 */
public class testManualOnlyPathsAreADifferentColour
{
    /** The tested route's outbound leg, asked of TileAnnotation.TRACE. */
    private static final Color FORWARD = ProgramConstants.colour(TileAnnotation.class, "TRACE");

    /** And the way back, asked of TileAnnotation.TRACE_RETURN. */
    private static final Color RETURN = ProgramConstants.colour(TileAnnotation.class, "TRACE_RETURN");

    /** The arrowheads drawn on top of whichever line it is, asked of TileAnnotation.TRACE_CHEVRON. */
    private static final Color CHEVRON = ProgramConstants.colour(TileAnnotation.class, "TRACE_CHEVRON");

    private static MarklinControlStation model;
    private static AutonomySession session;
    private static TileGraph graph;
    private static GraphReducer reducer;

    /** The station switched to manual-only for the wiring test, put back afterwards. */
    private static TileKey parked;

    /** The copy of the fixture the layout preference is pointed at, opened before the model. */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE init (OB-111): `MarklinControlStation.init` reads the machine-global layout preference, which names
        // the operator's real railway, so the redirection has to happen before the call that reads it.
        // `testSwitchingToACentralStationLayout.testNoTestOpensTheOperatorsRailway` counts models built without one.
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();

        // THE SANDBOX'S COPY, not the checked-in fixture: the session writes, and can then only reach the copy.
        File folder = sandbox.getFolder();

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(folder);
        session.open(pages);

        graph = session.getGraph();
        reducer = session.getReducer();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        // The session outlives this class in the same JVM under a battery; a station left marked as parking would
        // change what every later class sees.
        if (session != null && parked != null) session.setAutoDestination(parked, true);

        // AND THE PREFERENCE GOES BACK, whatever happened above (`alwaysRun` covers a set-up that threw): an open
        // sandbox changes which railway the application opens the next time Adam starts it.
        if (sandbox != null) sandbox.close();
    }

    // What is painted

    /** The three cases, painted, in one test because the assertion is that the three colors DIFFER. */
    @Test
    public void testTheThreeLegsArePaintedInThreeColours()
    {
        // The shades are the program's to choose; "yellow is currently forward, and orange is backwards" is the hue,
        // kept as a rough band so the constant can move within each color but cannot swap them.
        assertTrue(FORWARD.getRed() > 200 && FORWARD.getGreen() > 190 && FORWARD.getBlue() < 60,
            "the outbound leg is no longer yellow, which is what Adam named it.  Got: " + FORWARD);

        assertTrue(RETURN.getRed() > 200 && RETURN.getGreen() > 120 && RETURN.getGreen() < 180
            && RETURN.getBlue() < 80,
            "the way back is no longer orange, which is what Adam named it.  Got: " + RETURN);

        Color forward = lineColour(false, true);
        Color back = lineColour(false, false);
        Color manual = lineColour(true, true);

        assertEquals(forward, FORWARD,
            "the outbound leg is drawn in the colour this application uses to say 'look here', and "
            + "it is what the other two are told apart from.  Got: " + forward);

        assertEquals(back, RETURN,
            "and the way back keeps the orange it has always had - the ruling adds a third colour, it "
            + "does not renumber the two.  Got: " + back);

        assertNotEquals(manual, FORWARD,
            "a path to a station autonomy will never choose is drawn exactly like one it would take, "
            + "so the picture answers 'can a train get there' and silently drops 'would autonomy send "
            + "one'.  That is the whole of what Adam asked for");

        assertNotEquals(manual, RETURN,
            "the manual-only leg took the colour of the RETURN leg, which is worse than no colour at "
            + "all: an outbound run to a parking berth now reads as a run coming back");

        assertNotEquals(manual, CHEVRON,
            "the line is drawn in the colour of the arrowheads that sit on it, so the direction marks "
            + "disappear into it");

        assertTrue(saturated(manual),
            "the manual-only leg is drawn in a grey, which on this diagram means 'autonomy takes no "
            + "notice of this track' - a different statement, made about the squares rather than "
            + "about the destination.  Got: " + manual);

        // The return leg to a manual-only destination is the same third color: it is about where the line GOES.
        assertEquals(lineColour(true, false), manual,
            "the two directions of a manual-only leg were drawn in different colours, which invents a "
            + "distinction the ruling does not make");
    }

    /**
     * An ordinary run is untouched, which is the control: a flag set everywhere would repaint the whole editor and
     * still pass the test above.
     */
    @Test
    public void testAnOrdinaryRouteIsStillYellowAndOrange()
    {
        assertEquals(lineColour(false, true), FORWARD, "an ordinary outbound leg must not change");
        assertEquals(lineColour(false, false), RETURN, "nor an ordinary return leg");
    }

    // Where the flag comes from

    /**
     * The editor's own path test marks the leg that ends at a manual-only station, and only that leg.
     *
     * The flag is set at the one place a leg's destination is known, `AutonomyEditorPanel.trace`, which the "test a
     * path" and "why is this train stuck" tools both call.  Two clicks on two real squares, the second marked as
     * parking.  Both directions are asserted: the run OUT ends at the parking berth and is manual-only, the run BACK
     * ends at the ordinary station and is not, so a flag set from the wrong end fails one of them.
     */
    @Test
    public void testTheEditorMarksTheLegThatEndsAtAManualOnlyStation() throws Exception
    {
        TileKey[] pair = aConnectedPairOfStations();

        TileKey from = pair[0];
        TileKey to = pair[1];

        parked = to;
        session.setAutoDestination(to, false);

        assertTrue(session.stationsAutonomyWillNotChoose().contains(to),
            "the fixture did not take: " + to + " has to be a station autonomy will not choose");

        assertFalse(session.stationsAutonomyWillNotChoose().contains(from),
            "and the other end must be an ordinary one, or the two legs cannot be told apart");

        Map<TileKey, List<TileAnnotation.Trace>> traces = traceBetween(from, to);

        assertFalse(traces.isEmpty(),
            "the path test drew nothing at all between " + from + " and " + to
            + ", so there is no colour to be wrong");

        int manualLegs = 0;
        int ordinaryLegs = 0;

        for (List<TileAnnotation.Trace> here : traces.values())
        {
            for (TileAnnotation.Trace trace : here)
            {
                if (trace.isManualOnly()) manualLegs++; else ordinaryLegs++;
            }
        }

        assertTrue(manualLegs > 0,
            "the run OUT ends at a parking berth and nothing on it was marked, so the editor draws it "
            + "in the ordinary colour - the feature is inert");

        assertTrue(ordinaryLegs > 0,
            "the run BACK ends at an ordinary station and everything was marked, so every tested path "
            + "on a layout with one parking berth would be drawn as though it went there");
    }

    // Fixtures

    /**
     * Paints one square with a single traced segment through it and returns the line's color: the commonest color in
     * the image, skipping partly transparent rim pixels so it is a color that was asked for, not a blend.
     */
    private static Color lineColour(boolean manualOnly, boolean forward)
    {
        TileAnnotation annotation = new TileAnnotation(null, -1, false, null, false, false, false,
            Arrays.asList(new TileAnnotation.Trace(TilePorts.Side.W, TilePorts.Side.E, forward,
                manualOnly)));

        int size = 60;

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();

        try
        {
            annotation.paint(g, size, size);
        }
        finally
        {
            g.dispose();
        }

        Map<Integer, Integer> counts = new LinkedHashMap<>();

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                int argb = image.getRGB(x, y);

                if (((argb >>> 24) & 0xFF) < 250) continue;

                int rgb = argb & 0xFFFFFF;

                counts.put(rgb, counts.containsKey(rgb) ? counts.get(rgb) + 1 : 1);
            }
        }

        assertFalse(counts.isEmpty(),
            "nothing solid was painted at all, so this reads no colour rather than the wrong one");

        int best = -1;
        int bestCount = 0;

        for (Map.Entry<Integer, Integer> e : counts.entrySet())
        {
            if (e.getValue() > bestCount)
            {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }

        return new Color(best);
    }

    /** Whether a color says anything at all, or is a gray. */
    private static boolean saturated(Color c)
    {
        int max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
        int min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));

        return max - min > 60;
    }

    /**
     * Switching Path Type redraws the tested route for the new tier, without testing it again (MT-399).
     *
     * What the tier changes is the verdict written under the route: on Auto, a route to a station autonomy will never
     * choose says so; on Manual it does not.  Through the real two-click gesture and the real radio button.
     */
    @Test
    public void testSwitchingPathTypeRedrawsTheTestedRoute() throws Exception
    {
        TileKey[] pair = aConnectedPairOfStations();

        parked = pair[1];

        session.setAutoDestination(parked, false);

        try
        {
            AutonomyEditorPanel panel = new AutonomyEditorPanel(session, null, () -> { });

            // ARMED AS A PERSON ARMS IT, with the Test a Path button: the tier only matters to a tool in use.
            ((javax.swing.AbstractButton) field(panel, "testButton")).doClick();

            java.lang.reflect.Method applyTest = AutonomyEditorPanel.class.getDeclaredMethod("applyTest",
                TileKey.class, org.traincontrol.base.LayoutDiagramComponent.class);

            applyTest.setAccessible(true);

            applyTest.invoke(panel, pair[0], graph.getTiles().get(pair[0]));
            applyTest.invoke(panel, pair[1], graph.getTiles().get(pair[1]));

            String onAuto = said(panel);

            String note = org.traincontrol.util.I18n.t("autosetup.ui.testNotAnAutoDestination");

            String noteStart = note.substring(0, note.indexOf("{0}"));

            assertTrue(onAuto.contains(noteStart),
                "precondition: on Auto the tested route to a station autonomy will never choose does not say so, so"
                + " there is no difference for the radio to make: " + onAuto);

            javax.swing.JRadioButton manual = (javax.swing.JRadioButton) field(panel, "pathTypeManual");

            manual.doClick();

            String onManual = said(panel);

            assertFalse(onManual.contains(noteStart),
                "switching Path Type to Manual left the Auto verdict on screen - the tested route was not redrawn for"
                + " the new tier.  Adam, MT-399: 'make it update the shown route to the new selection without having"
                + " to repeat the button press sequence'.  It says: " + onManual);

            String route = org.traincontrol.util.I18n.t("autosetup.ui.testBothWays");

            assertTrue(onManual.contains(route.substring(route.indexOf("</b> to <b>") >= 0 ? route.indexOf("</b> to <b>") : 0,
                route.indexOf("</b> to <b>") >= 0 ? route.indexOf("</b> to <b>") + 11 : 0)),
                "switching Path Type cleared the tested route instead of redrawing it: " + onManual);

            javax.swing.JRadioButton auto = (javax.swing.JRadioButton) field(panel, "pathTypeAuto");

            auto.doClick();

            assertTrue(said(panel).contains(noteStart),
                "switching back to Auto did not put the Auto verdict back: " + said(panel));
        }
        finally
        {
            session.setAutoDestination(parked, true);

            parked = null;
        }
    }

    /** What the panel's hint line says, mark-up included. */
    private static String said(AutonomyEditorPanel panel) throws Exception
    {
        String text = ((javax.swing.JLabel) field(panel, "hint")).getText();

        return text == null ? "" : text;
    }

    private static Object field(AutonomyEditorPanel panel, String name) throws Exception
    {
        java.lang.reflect.Field f = AutonomyEditorPanel.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(panel);
    }

    /**
     * Two stations of the sample layout with a route between them in both directions, chosen by asking the reduction
     * so the fixture cannot rot; both directions are required because a one-way pair would only ever draw one leg.
     */
    private static TileKey[] aConnectedPairOfStations()
    {
        List<TileKey> stations = new ArrayList<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (session.getStore().isStation(tile) && session.isAutoDestination(tile))
            {
                stations.add(tile);
            }
        }

        assertTrue(stations.size() > 1,
            "the sample layout must have at least two ordinary stations for this to test anything");

        for (TileKey a : stations)
        {
            for (TileKey b : stations)
            {
                if (a.equals(b)) continue;

                if (pathExists(a, b) && pathExists(b, a)) return new TileKey[] { a, b };
            }
        }

        fail("no two stations on the sample layout are connected both ways, so the path test has "
            + "nothing to draw");

        return null;
    }

    /** As the path test asks it: a train starts on the copies it stands on at `from`. */
    private static boolean pathExists(TileKey from, TileKey to)
    {
        return reducer.findPath(from, to, session.mayTurnTiles(), session.mandatoryTurnTiles(),
            session.barredArrivals(), session.shutTiles(), session.startingArrivals().get(from)) != null;
    }

    /**
     * Runs the editor's "test a path" tool over two squares and hands back what it drew, by reflection: a hook in
     * production code would be a second entrance to the thing under test.
     */
    @SuppressWarnings("unchecked")
    private static Map<TileKey, List<TileAnnotation.Trace>> traceBetween(TileKey from, TileKey to)
        throws Exception
    {
        // A null page, as TrainControlUI builds this panel for the tile menus; the squares go straight to the tool.
        AutonomyEditorPanel panel = new AutonomyEditorPanel(session, null, new Runnable()
        {
            @Override
            public void run()
            {
                // The editor redraws its diagram here.  There is no diagram in this test.
            }
        });

        java.lang.reflect.Method applyTest = AutonomyEditorPanel.class.getDeclaredMethod("applyTest",
            TileKey.class, org.traincontrol.base.LayoutDiagramComponent.class);

        applyTest.setAccessible(true);

        // The first click names the origin and draws nothing; the second runs the test.
        applyTest.invoke(panel, from, graph.getTiles().get(from));
        applyTest.invoke(panel, to, graph.getTiles().get(to));

        java.lang.reflect.Field traces = AutonomyEditorPanel.class.getDeclaredField("traces");

        traces.setAccessible(true);

        return new HashMap<>((Map<TileKey, List<TileAnnotation.Trace>>) traces.get(panel));
    }
}
