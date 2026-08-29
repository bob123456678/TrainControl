package ui;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * What drawing the track diagram actually costs, measured rather than guessed.
 *
 * A MEASUREMENT, and deliberately generous about what it will accept. Its job is to keep the numbers
 * in docs/reviews/2026-08-19-rendering-cost.md honest: a report saying "this is fast now" that is
 * never re-run stops being true the first time somebody puts a loop where a lookup should be.
 *
 * Thresholds are roughly ten times the measured cost. A tenfold regression is not a micro-optimisation
 * anybody argues about - it is a loop that should not be there - and setting them near the real figure
 * would make this fail on a busy machine and teach everyone to ignore it.
 *
 * The numbers themselves are printed, so a run of this class is the report's evidence.
 */
public class testRenderingCost
{
    private static MarklinControlStation model;
    private static TileGraph graph;
    private static GraphReducer reducer;
    private static List<LayoutDiagram> parsed;
    private static java.util.Set<String> pageExclusions;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File folder = new File("test_layout");

        if (!folder.isDirectory())
        {
            throw new SkipException("no sample layout to measure against");
        }

        // BEFORE THE MODEL (OB-111), and this one also stops a MODAL dialog reaching the operator.
        //
        // `init` loads whatever layout the preference names. With none, `setViewListener` below offers
        // to create a track diagram - and waits for an answer no test will give, so the run stalls
        // with a window sitting on Adam’s screen. He watched it happen during a battery.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);

        String path = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        parsed = parser.parseLayout(new LinkedList<MarklinAccessory>());

        // The same wiring and the same exclusions testAutonomyDiagramSampleLayout uses.
        //
        // Without them the reduction produces NO points, and every timing below is zero - which is not
        // a fast layout, it is an empty one.  The first version of this class measured exactly that
        // and reported 0.00ms for everything, which is the failure mode a benchmark is most likely to
        // have and least likely to notice: it looks like good news.
        wireAccessories(parsed);

        java.util.Set<String> excluded = new java.util.LinkedHashSet<>();

        for (LayoutDiagram page : parsed)
        {
            String name = page.getName().toLowerCase();

            if (name.contains("combined") || name.contains("test") || name.contains("parking"))
            {
                excluded.add(page.getName());
            }
        }

        pageExclusions = excluded;

        graph = new TileGraph(parsed, excluded);

        reducer = new GraphReducer(graph, new GraphReducer.NothingAuthored());

        // reduce(), which is the work.  Constructing a GraphReducer does nothing but hold the graph -
        // the first version of this class timed the constructor and reported 0.00ms for a reduction
        // that had not happened, which is the benchmark failure mode that looks like good news.
        reducer.reduce();
    }

    /**
     * Gives every switch and signal tile an accessory, the way the application does when it parses a
     * layout against a real database.  Without it the reduction has nothing to reduce.
     */
    private static void wireAccessories(List<LayoutDiagram> pages)
    {
        for (LayoutDiagram page : pages)
        {
            for (org.traincontrol.base.LayoutDiagramComponent c : page.getAll())
            {
                if (!c.isSwitch() && !c.isSignal()) continue;

                if (c.getAddress() <= 0) continue;

                org.traincontrol.base.Accessory.accessoryType type = c.isSignal()
                    ? org.traincontrol.base.Accessory.accessoryType.SIGNAL
                    : org.traincontrol.base.Accessory.accessoryType.SWITCH;

                c.setAccessory(accessory(c.getAddress(), type, c.getProtocol()));

                if (c.isThreeWay())
                {
                    c.setAccessory2(accessory(c.getAddress() + 1,
                        org.traincontrol.base.Accessory.accessoryType.SWITCH, c.getProtocol()));
                }
            }
        }
    }

    private static MarklinAccessory accessory(int logicalAddress,
        org.traincontrol.base.Accessory.accessoryType type,
        org.traincontrol.base.Accessory.accessoryDecoderType protocol)
    {
        return new MarklinAccessory(null, logicalAddress - 1, type, protocol,
            MarklinAccessory.getNameWithProtocol(logicalAddress, type, protocol), false, 0);
    }

    /**
     * How big the thing being measured is, so the numbers below mean something.
     */
    @Test
    public void testTheSampleLayoutIsWorthMeasuring()
    {
        System.out.println("RENDERCOST tiles=" + graph.getTiles().size()
            + " reducedPoints=" + reducer.getPoints().size());

        assertTrue(reducer.getPoints().size() > 10,
            "the sample layout has too few points for a timing to say anything - found "
            + reducer.getPoints().size());
    }

    /**
     * Naming every point, which is what a configuration load and every rebuild pay for.
     *
     * Allowed to be slow: it happens on load, not while trains are running. Measured so the report can
     * say which side of that line it is on, and so that it moving ONTO the running path is noticed.
     */
    @Test
    public void testNamingEveryPoint()
    {
        AutonomyBuilder builder = new AutonomyBuilder(reducer, new AutonomyBuilder.Globals());

        // Warm, so the first-call class loading is not what gets timed
        builder.uniqueNames();

        long start = System.nanoTime();

        for (int i = 0; i < 50; i++) builder.uniqueNames();

        double each = (System.nanoTime() - start) / 1000000.0 / 50.0;

        System.out.println("RENDERCOST uniqueNames=" + String.format("%.2f", each) + "ms each");

        assertTrue(each < 250,
            "naming every point took " + each + "ms.  That is a load-time cost and may be slow, but at "
            + "this size it says the naming has become superlinear - and it is one refactor away from "
            + "the feedback path, where it would be felt at once");
    }

    /**
     * The name-to-tile map the diagram overlay is driven from.
     */
    @Test
    public void testMappingNamesBackToSquares()
    {
        AutonomyBuilder builder = new AutonomyBuilder(reducer, new AutonomyBuilder.Globals());

        builder.tilesByName();

        long start = System.nanoTime();

        for (int i = 0; i < 50; i++) builder.tilesByName();

        double each = (System.nanoTime() - start) / 1000000.0 / 50.0;

        System.out.println("RENDERCOST tilesByName=" + String.format("%.2f", each) + "ms each");

        assertTrue(each < 250, "mapping names back to squares took " + each + "ms each");
    }

    /**
     * Reducing the tile graph, which is the expensive half of opening a diagram.
     */
    @Test
    public void testReducingTheGraph()
    {
        long start = System.nanoTime();

        for (int i = 0; i < 10; i++)
        {
            new GraphReducer(graph, new GraphReducer.NothingAuthored()).reduce();
        }

        double each = (System.nanoTime() - start) / 1000000.0 / 10.0;

        System.out.println("RENDERCOST graphReduction=" + String.format("%.2f", each) + "ms each");

        assertTrue(each < 2000, "reducing the graph took " + each + "ms each");
    }

    /**
     * Building the tile graph from the parsed pages.
     */
    @Test
    public void testBuildingTheTileGraph() throws Exception
    {
        long start = System.nanoTime();

        for (int i = 0; i < 10; i++) new TileGraph(parsed, pageExclusions);

        double each = (System.nanoTime() - start) / 1000000.0 / 10.0;

        System.out.println("RENDERCOST tileGraph=" + String.format("%.2f", each) + "ms each");

        assertTrue(each < 2000, "building the tile graph took " + each + "ms each");
    }

    /**
     * Decoding tile images, which is what a diagram actually spends its time on.
     *
     * The model side above is fast - a whole reduction is under two milliseconds. What a user waits
     * for when a page appears is several hundred PNGs being read off disk, scaled and rotated, and
     * this is the number that says how many of those there really are.
     *
     * The cache is keyed by type, state and orientation rather than by tile, so a page of five hundred
     * squares does NOT decode five hundred images - it decodes one per distinct appearance. Measuring
     * both is the point: the distinct count is what the first draw costs, and the total is what it
     * would cost without the cache.
     */
    @Test
    public void testDecodingTheTileImages() throws Exception
    {
        java.util.Set<String> distinct = new java.util.LinkedHashSet<>();

        int tiles = 0;

        for (LayoutDiagram page : parsed)
        {
            if (pageExclusions.contains(page.getName())) continue;

            for (org.traincontrol.base.LayoutDiagramComponent c : page.getAll())
            {
                if (c == null) continue;

                tiles++;
                distinct.add(c.getImageKey(30, false));
            }
        }

        // One decode per distinct appearance, which is what a cold cache pays
        long start = System.nanoTime();

        int decoded = 0;

        for (LayoutDiagram page : parsed)
        {
            if (pageExclusions.contains(page.getName())) continue;

            java.util.Set<String> seen = new java.util.LinkedHashSet<>();

            for (org.traincontrol.base.LayoutDiagramComponent c : page.getAll())
            {
                if (c == null || c.isText()) continue;

                if (!seen.add(c.getImageKey(30, false))) continue;

                try
                {
                    c.getImage(30, false);
                    decoded++;
                }
                catch (Exception e)
                {
                    // A tile whose icon is missing is not what this is measuring
                }
            }
        }

        double ms = (System.nanoTime() - start) / 1000000.0;

        System.out.println("RENDERCOST tilesOnLivePages=" + tiles
            + " distinctAppearances=" + distinct.size()
            + " coldDecodes=" + decoded
            + " coldDecodeTotal=" + String.format("%.1f", ms) + "ms"
            + " perDecode=" + String.format("%.2f", decoded == 0 ? 0 : ms / decoded) + "ms");

        // TST-C14: the decode loop swallows every exception, so a getImage that throws for EVERY
        // tile leaves decoded at 0 and ms near zero - which used to read as a fast, passing test.
        // That is the "0.00ms looks like good news" failure this class's own header comment says it
        // was rewritten to avoid; the floor below is what closes it for this loop too.
        assertTrue(decoded > 0,
            "not a single tile image decoded (coldDecodes=0 of " + tiles + " tiles on "
            + distinct.size() + " distinct appearances).  Either the fixture lost its icons or "
            + "getImage is throwing for every tile, and this loop's own catch block would hide "
            + "that - a near-zero decode time here is not a fast diagram, it is nothing measured");

        assertTrue(ms < 30000,
            "decoding one image per distinct tile appearance took " + ms + "ms.  That is the cold-cache "
            + "cost of opening a diagram, and it is what the spinner is covering");
    }

    /**
     * One accessory drawn on several squares keeps a label on every one of them.
     *
     * A real layout puts one address on several tiles - the sample layout has 162 on four squares of
     * "3 - Top Parking" and five of "4 - Combined" - and the control station gives all of them the SAME
     * MarklinAccessory, because it resolves by address out of the database.  Each of those tiles has to
     * stay registered with it or it stops being repainted when the accessory is thrown.
     *
     * This is the test that was missing when a pruning rule was carried over from a map keyed by SQUARE
     * into three collections keyed by DEVICE.  Inside one square's entry, "an older label of this
     * window that is no longer displayable" means "the label this one replaces"; inside one device's,
     * it also matches that device's OTHER squares - and LayoutGrid registers every label in its build
     * loop and only attaches the container afterwards, so during a build none of them is displayable.
     * Every arriving label therefore evicted its own siblings and only the last one survived.
     *
     * The hands-on test for that change could not see it: three of the four tiles stopped updating and
     * the fourth still worked, which looks like a working diagram unless you are counting.
     *
     * A fresh parse rather than the shared fixture, because the accessories have to be shared by
     * address the way the control station shares them, and the fixture deliberately gives each tile
     * its own.
     */
    @Test
    public void testEveryTileOfOneAccessoryStaysRegistered() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("building labels needs a display");
        }

        File folder = new File("test_layout");

        String path = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> fresh = parser.parseLayout(new LinkedList<MarklinAccessory>());

        // Wired by ADDRESS, one accessory object however many tiles carry it - which is what
        // MarklinControlStation.syncLayouts does through accDB.getById
        java.util.Map<Integer, MarklinAccessory> byAddress = new java.util.HashMap<>();

        LayoutDiagram page = null;
        MarklinAccessory shared = null;
        int squares = 0;

        for (LayoutDiagram candidate : fresh)
        {
            if (pageExclusions.contains(candidate.getName())) continue;

            java.util.Map<Integer, Integer> seen = new java.util.HashMap<>();

            for (org.traincontrol.base.LayoutDiagramComponent c : candidate.getAll())
            {
                if (!c.isSwitch() && !c.isSignal()) continue;

                if (c.getAddress() <= 0) continue;

                org.traincontrol.base.Accessory.accessoryType type = c.isSignal()
                    ? org.traincontrol.base.Accessory.accessoryType.SIGNAL
                    : org.traincontrol.base.Accessory.accessoryType.SWITCH;

                MarklinAccessory one = byAddress.get(c.getAddress());

                if (one == null)
                {
                    one = accessory(c.getAddress(), type, c.getProtocol());
                    byAddress.put(c.getAddress(), one);
                }

                c.setAccessory(one);

                Integer count = seen.get(c.getAddress());

                seen.put(c.getAddress(), count == null ? 1 : count + 1);

                if (seen.get(c.getAddress()) > squares)
                {
                    squares = seen.get(c.getAddress());
                    shared = one;
                    page = candidate;
                }
            }
        }

        if (page == null || squares < 2)
        {
            throw new org.testng.SkipException(
                "no page in the sample layout draws one accessory on two squares");
        }

        System.out.println("SHARED ACCESSORY page=" + page.getName()
            + " accessory=" + shared.getName() + " squares=" + squares);

        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> ui[0] = new org.traincontrol.gui.TrainControlUI());

        ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        final LayoutDiagram drawing = page;
        final javax.swing.JPanel host = new javax.swing.JPanel();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                new org.traincontrol.gui.LayoutGrid(drawing, 30, host, null, true, ui[0]);
            });

            assertEquals(registeredTiles(shared), squares,
                "the accessory is drawn on " + squares + " squares of " + drawing.getName()
                + " and kept " + registeredTiles(shared) + " of them.  The others will not be "
                + "repainted when it is thrown - the diagram will show them in whatever state they "
                + "were drawn in, for the rest of the session");
        }
        finally
        {
            final org.traincontrol.gui.TrainControlUI toClose = ui[0];

            javax.swing.SwingUtilities.invokeAndWait(() -> toClose.dispose());
        }
    }

    /**
     * How many labels an accessory holds.  Private in the model, and rightly: nothing in the
     * application has any business reading it, and a test is not the application.
     */
    private static int registeredTiles(MarklinAccessory accessory) throws Exception
    {
        java.lang.reflect.Field field = MarklinAccessory.class.getDeclaredField("tiles");

        field.setAccessible(true);

        return ((java.util.Collection<?>) field.get(accessory)).size();
    }

    /**
     * How many LayoutLabels a grid builds, against how many cells it has.
     *
     * Needs a display, and is skipped without one.  The point is a ratio, not a duration: if a grid
     * builds more labels than it keeps, that is the largest lever on this page and no amount of
     * tuning anything else matters.
     */
    @Test
    public void testLabelsBuiltPerCell() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("building labels needs a display");
        }

        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> ui[0] = new org.traincontrol.gui.TrainControlUI());

        ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        LayoutDiagram page = null;

        for (LayoutDiagram p : parsed)
        {
            if (!pageExclusions.contains(p.getName())) { page = p; break; }
        }

        final LayoutDiagram drawing = page;
        final javax.swing.JPanel host = new javax.swing.JPanel();

        // Counted ON the thread that builds, between the reset and the constructor returning
        // (OB-084).
        //
        // This is what makes the number mean something. It was read from the test thread the instant
        // invokeAndWait returned, and both LayoutGrid and LayoutLabel post further work with
        // invokeLater - so the reading was a stable floor plus however much of the deferred work had
        // landed, and five runs against one unchanged fixture gave 720, 621, 685, 720, 597 against a
        // bound of 672. It failed or passed depending on nothing at all.
        //
        // Waiting for quiet instead was tried first and is worse: the deferred work rebuilds labels,
        // so the longer you wait the more you count. Eight runs of that gave 756 to 1152 - the same
        // coin toss with a bigger coin. There is no "total labels built" to measure, because the
        // total depends on when you stop looking.
        //
        // Inside one EDT runnable there is nothing to race: invokeLater work cannot run while the
        // event thread is busy with this, so what is counted is exactly what BUILDING THE GRID does,
        // which is the question the test is asking.
        final long[] built = new long[1];
        final long[] applied = new long[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            org.traincontrol.gui.LayoutLabel.COUNT_CONSTRUCTED.set(0);
            org.traincontrol.gui.LayoutLabel.COUNT_APPLIED.set(0);

            new org.traincontrol.gui.LayoutGrid(drawing, 30, host, null, true, ui[0]);

            built[0] = org.traincontrol.gui.LayoutLabel.COUNT_CONSTRUCTED.get();
            applied[0] = org.traincontrol.gui.LayoutLabel.COUNT_APPLIED.get();
        });

        int cells = (drawing.getMaxx() - drawing.getMinx() + 2)
            * (drawing.getMaxy() - drawing.getMiny() + 2);

        System.out.println("RENDERCOST page=" + drawing.getName()
            + " cells=" + cells
            + " labelsBuilt=" + built[0]
            + " iconApplications=" + applied[0]);

        final org.traincontrol.gui.TrainControlUI toClose = ui[0];
        javax.swing.SwingUtilities.invokeAndWait(() -> toClose.dispose());

        // Exactly ONE label per cell, and the bound is a quarter above that (OB-084).
        //
        // Measured six times, deterministically, at 384 for 384: building the grid constructs one
        // label per cell of the bounding box and no more. The bound was twice the cell count, and the
        // comment here said 1.6 per cell "when this was written" - but every figure in that history,
        // from 613 up to the 720 and 1152 that made this a coin toss, was measuring construction PLUS
        // however much deferred rebuilding had landed before somebody looked. That is a different
        // quantity and it has no fixed value.
        //
        // So the ratio this test was written to watch was never 1.6; it is 1.0, and always was. The
        // bound is tight now because the measurement finally deserves one: a grid that builds itself
        // twice over goes to 2.0 and fails, where against the old bound of 2.0 it would have passed.
        //
        // WHAT IS NOT MEASURED HERE, and is the interesting number: after construction the diagram
        // goes on rebuilding labels, and settles somewhere between 2.0 and 3.0 per cell depending on
        // timing. That is real work and it is the subject of OB-053, which Adam has asked to be left
        // alone for now. It is not asserted because there is nothing stable to assert - the total
        // depends on when you stop watching.
        //
        // MUTATION, run: building a second LayoutLabel for every populated cell in LayoutGrid takes
        // the count to 729 and fails this. Against the old bound of twice the cell count - 768 - the
        // same mutation PASSED. So the assertion this test was named for could not catch the thing it
        // was named for, and the tightening is not bookkeeping.
        assertTrue(built[0] <= cells * 5 / 4,
            "the grid built " + built[0]
            + " labels for " + cells + " cells.  Building it constructs exactly one label per cell, "
            + "measured six times; anything materially above that means the grid has started building "
            + "itself more than once");
    }

    @org.testng.annotations.AfterClass
    public static void putTheLayoutPreferenceBack()
    {
        if (sandbox != null) sandbox.close();
    }
}
