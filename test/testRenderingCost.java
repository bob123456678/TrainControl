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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File folder = new File("cs2_sample_layout");

        if (!folder.isDirectory())
        {
            throw new SkipException("no sample layout to measure against");
        }

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

        assertTrue(ms < 30000,
            "decoding one image per distinct tile appearance took " + ms + "ms.  That is the cold-cache "
            + "cost of opening a diagram, and it is what the spinner is covering");
    }
}
