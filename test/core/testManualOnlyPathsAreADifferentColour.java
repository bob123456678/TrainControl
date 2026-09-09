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
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A simulated path to a station autonomy will never choose is drawn in a colour of its own.
 *
 * Adam asked for this twice. First: *"We should draw arrows to manual-only destinations in a
 * different color, like orange."*  Then, narrowing it on 2026-09-09: *"I was likely talking about the
 * simulated paths drawn in the autonomy editor. just use a different color going to manual-only
 * points. yellow is currently forward, and orange is backwards- path, not the chevron arrows."*
 *
 * So this is about the editor's path tracing and nothing else - not the station arrival chevrons, and
 * not the one-way restriction arrows. The two legs of a tested route are drawn yellow out and orange
 * back; a leg whose DESTINATION is a parking berth, or a square that turns every train it takes, is
 * drawn in a third colour, because "the track is passable" and "autonomy will ever use it" are
 * different answers and the picture was giving only the first.
 *
 * **Read off the painted pixels, not off the source.** The whole feature is a colour reaching the
 * screen, and the annotation is where the three colours are decided - so a test that inspected the
 * flag would pass with all three drawn identically. `paint` is called for real and the line's own
 * colour is read back out of the image.
 *
 * The exact shades are asserted for the two that already existed, so that redefining one of them into
 * the new one cannot pass here; the new one is asserted only to differ from both, from the chevron
 * that sits on top of it, and from grey - which is what "a colour of its own" means and all Adam
 * asked for.
 *
 * @author Adam
 */
public class testManualOnlyPathsAreADifferentColour
{
    /** The tested route's outbound leg, from TileAnnotation.TRACE. */
    private static final Color FORWARD = new Color(255, 214, 0);

    /** And the way back, from TileAnnotation.TRACE_RETURN. */
    private static final Color RETURN = new Color(255, 150, 40);

    /** The arrowheads drawn on top of whichever line it is, from TileAnnotation.TRACE_CHEVRON. */
    private static final Color CHEVRON = new Color(120, 80, 0);

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
        // BEFORE init, and this is the whole reason the order is written out (OB-111).
        //
        // `MarklinControlStation.init` reads the layout preference and loads whatever railway it
        // names.  That preference is machine-global and on Adam's machine it names his real,
        // unrecoverable layout - so this class opened HIS railway on every run, including under a
        // battery, while every line below it worked on the fixture and looked entirely careful.
        // Nothing in the suite names his folder; the path is in his preferences, which is why the
        // redirection has to happen before the call that reads it rather than anywhere after it.
        //
        // Found by `regression.testSwitchingToACentralStationLayout.testNoTestOpensTheOperatorsRailway`,
        // which counted this class as the fifty-sixth model built without a sandbox.  The pin was NOT
        // raised: its own comment says to give the class a sandbox instead, and that is this.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);

        // THE SANDBOX'S COPY, not the checked-in fixture.
        //
        // It holds the same files - `LayoutSandbox.open()` copies `test/test_layout` - so nothing
        // below sees a different railway.  What changes is that the session, which writes, can only
        // ever reach the copy, so a tracked fixture cannot be dirtied by a run of this class either.
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
        // Nothing is saved, but the session outlives this class in the same JVM under a battery, and a
        // station left marked as parking would change what every later class sees.
        if (session != null && parked != null) session.setAutoDestination(parked, true);

        // AND THE PREFERENCE GOES BACK, whatever happened above.  A sandbox left open has changed
        // which railway the application opens the next time Adam starts it, to a folder under %TEMP%
        // - which is worse than the churn the sandbox exists to remove.  `alwaysRun` on this method
        // is what makes that true even when the set-up itself threw.
        if (sandbox != null) sandbox.close();
    }

    // ---------------------------------------------------------------------------------------------
    // What is painted
    // ---------------------------------------------------------------------------------------------

    /**
     * The three cases, painted, with the line's own colour read back off the image.
     *
     * One test rather than three, because the assertion is about the three colours being DIFFERENT and
     * a test that could only see one of them at a time could not make it.
     */
    @Test
    public void testTheThreeLegsArePaintedInThreeColours()
    {
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

        // And the return leg to a manual-only destination is the same third colour, not a fourth one:
        // what is being said is about where the line GOES, and it says it whichever way round the run
        // is being drawn.
        assertEquals(lineColour(true, false), manual,
            "the two directions of a manual-only leg were drawn in different colours, which invents a "
            + "distinction the ruling does not make");
    }

    /**
     * And an ordinary run is untouched, which is the control.
     *
     * Every square of a traced route carries the flag, so a mistake that set it everywhere would
     * repaint the whole editor and still pass the test above - the assertions there are all about the
     * manual-only case being different, and a world where everything is that colour satisfies none of
     * them except by accident.
     */
    @Test
    public void testAnOrdinaryRouteIsStillYellowAndOrange()
    {
        assertEquals(lineColour(false, true), FORWARD, "an ordinary outbound leg must not change");
        assertEquals(lineColour(false, false), RETURN, "nor an ordinary return leg");
    }

    // ---------------------------------------------------------------------------------------------
    // Where the flag comes from
    // ---------------------------------------------------------------------------------------------

    /**
     * The editor's own path test marks the leg that ends at a manual-only station, and only that leg.
     *
     * The painting above is worth nothing if nothing ever sets the flag, and the flag is set at the
     * one place a leg's destination is known - `AutonomyEditorPanel.trace`, which both the "test a
     * path" tool and the "why is this train stuck" tool call. This drives the real tool: two clicks on
     * two real squares of the sample layout, with the second one marked as parking.
     *
     * Both directions are asserted, and they differ: the run OUT ends at the parking berth and is
     * manual-only; the run BACK ends at the ordinary station it started from and is not. A flag set
     * from the wrong end of the leg passes an assertion about the first and fails one about the
     * second, which is why both are here.
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

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * Paints one square with a single traced segment through it and returns the line's colour.
     *
     * The commonest colour in the image is the line: the stroke is a seventh of the tile wide and runs
     * edge to edge, while the only other thing painted is the arrowhead on it. Partly transparent
     * pixels - the antialiased rim - are skipped, so what comes back is a colour that was actually
     * asked for rather than a blend of two that were.
     *
     * @param manualOnly whether the leg ends somewhere autonomy will never choose
     * @param forward whether it is the outbound leg
     * @return the colour the line was drawn in
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

    /**
     * Whether a colour says anything at all, or is a grey.
     */
    private static boolean saturated(Color c)
    {
        int max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
        int min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));

        return max - min > 60;
    }

    /**
     * Two stations of the sample layout with a route between them in both directions.
     *
     * Chosen by asking the reduction rather than named, so the fixture cannot rot when the sample
     * layout is edited - and both directions are required, because the test is about the two legs
     * being marked differently and a one-way pair would only ever draw one of them.
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

    private static boolean pathExists(TileKey from, TileKey to)
    {
        return reducer.findPath(from, to, session.mayTurnTiles(), session.mandatoryTurnTiles(),
            session.barredArrivals(), session.shutTiles()) != null;
    }

    /**
     * Runs the editor's "test a path" tool over two squares and hands back what it drew.
     *
     * Through the real panel and the real two-click gesture, by reflection: the tool is a private
     * method reached from a mouse listener, and a hook added to production code so a test could call
     * it would be a second entrance to the thing under test.
     */
    @SuppressWarnings("unchecked")
    private static Map<TileKey, List<TileAnnotation.Trace>> traceBetween(TileKey from, TileKey to)
        throws Exception
    {
        // A null page, which is how TrainControlUI builds this panel for the tile menus: nothing here
        // needs the diagram it would name, because the two squares are handed straight to the tool.
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
