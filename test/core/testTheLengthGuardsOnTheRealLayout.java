package core;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The length guards, on the real railway, with lengths set deliberately small.
 *
 * Adam, 2026-09-06: **"For testing, it should be 1 between BottomMainA and BottomMainPost, and 1
 * between BottomMainA and TunnelLongPark.  Therefore, the route should be refused.  In your tests,
 * these are the types of setups I want you to set up.  It may not be realistic, but it will allow us
 * whether the guards work correctly."**
 *
 * **This is a different instrument from the generated railways beside it.**  Those check the rules
 * against the spec on shapes nobody drew; this checks them on the shape Adam actually operates, with
 * the measurements turned down until the guard has to fire.  A rule can be perfect on a chain of three
 * synthetic points and never fire on his layout - which is exactly what was found the day this was
 * written: with four fifty-unit trains standing on it, not one edge was covered, because almost
 * nothing carried a length.
 *
 * So the lengths are set here rather than assumed.  Unrealistic on purpose: one unit between stations
 * is not a railway anybody would build, and it is the only way to be sure the refusal comes from the
 * measurement rather than from the guard never being reached.
 *
 * Everything happens in a `LayoutSandbox` copy (OB-111).  His railway is never written to.
 */
public class testTheLengthGuardsOnTheRealLayout
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    private static int edgesAtOpen;

    /** The page his main railway is on, which the setup names rather than numbers. */
    private static final String MAIN = "1 - Main";

    @BeforeClass
    public static void setUp() throws Exception
    {
        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        model = init(null, true, false, false, false);

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        // THE SWITCHES HAVE TO BE WIRED BEFORE THE DIAGRAM IS REDUCED, and forgetting it is why every
        // probe written against this layout today reported "no path".
        //
        // `parseLayout` reads the drawing; the accessory behind each switch is attached separately,
        // by the application, from the addresses on the tiles.  A switch with no accessory is a tile
        // the reducer cannot trace through - so the graph falls from fifty-odd edges to eighteen, the
        // railway comes apart into fragments, and every station is unreachable from every other.
        //
        // Measured here rather than assumed: `edgesAtOpen` was 18 without this and the assertion below
        // now holds it above 50.  `testTheCheckerAgreesWithTheBuild` has done this all along, which is
        // why that test sees a connected railway and four probes written today did not.
        wireAccessories(pages);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        edgesAtOpen = session.getReducer().getEdges().size();

        assertTrue(edgesAtOpen > 50,
            "only " + edgesAtOpen + " edges were derived from the sample diagram. The railway is in"
            + " fragments and nothing below can be routed anywhere - which reads as \"the guard"
            + " refused it\" and is nothing of the kind");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDown() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * With every section one unit long, a long train is offered nothing it has to reverse into.
     *
     * Adam's case generalised. He named BottomMainPost to TunnelLongPark; the guard is not about that
     * pair, it is about a train being longer than the room at the far end - so this asserts it of every
     * berth on the railway at once, which cannot be satisfied by getting one pair right.
     */
    @Test
    public void testALongTrainIsOfferedNoBerthWhenEverySectionIsOneUnit() throws Exception
    {
        measureEverythingAs(1);

        Layout built = rebuild();

        Locomotive longTrain = anyPlacedLocomotive(built);

        assertNotNull(longTrain, "no locomotive is standing on the railway, so nothing was asked");

        longTrain.setTrainLength(9);

        Set<String> offered = berthsOfferedTo(built, longTrain);

        assertTrue(offered.isEmpty(),
            "a nine-unit train is still offered " + offered + " with every section measured at one"
            + " unit. The room at the end of each of those is 1, so every one of them is a refusal the"
            + " guard did not make");
    }

    /**
     * And the control: the same railway, a train that fits, is offered somewhere.
     *
     * Without this the assertion above passes on a railway that offers nothing to anybody - which is
     * exactly the state the first version of this file was in, and the reason it needed measuring
     * rather than assuming.
     */
    @Test
    public void testAShortTrainIsStillOfferedSomewhere() throws Exception
    {
        measureEverythingAs(1);

        Layout built = rebuild();

        Locomotive shortTrain = anyPlacedLocomotive(built);

        assertNotNull(shortTrain, "no locomotive is standing on the railway");

        shortTrain.setTrainLength(1);

        Set<String> offered = berthsOfferedTo(built, shortTrain);

        assertFalse(offered.isEmpty(),
            "a one-unit train is offered no reversing berth at all on a railway measured at one unit"
            + " per section, so the refusal in the test above says nothing about lengths - it says"
            + " this railway offers nothing to anybody, which is the state this file was in until the"
            + " accessories were wired");
    }

    /**
     * Turn the measurements up and the same long train becomes welcome again.
     *
     * The strongest form of the control: one railway, one train, two measurements, opposite answers.
     * Nothing but the lengths changed between them.
     */
    @Test
    public void testTheSameTrainIsAdmittedOnceThereIsRoom() throws Exception
    {
        measureEverythingAs(1);

        Layout tight = rebuild();

        Locomotive train = anyPlacedLocomotive(tight);

        assertNotNull(train, "no locomotive is standing on the railway");

        train.setTrainLength(9);

        Set<String> whenTight = berthsOfferedTo(tight, train);

        measureEverythingAs(50);

        Layout roomy = rebuild();

        Locomotive again = anyPlacedLocomotive(roomy);

        assertNotNull(again, "the locomotive is no longer placed after remeasuring");

        again.setTrainLength(9);

        Set<String> whenRoomy = berthsOfferedTo(roomy, again);

        assertTrue(whenTight.isEmpty(),
            "the nine-unit train was offered " + whenTight + " on one-unit sections");

        assertFalse(whenRoomy.isEmpty(),
            "the same train is offered nothing even with fifty units in every section, so the guard is"
            + " refusing for a reason that has nothing to do with length and both other tests pass"
            + " for that reason too");
    }

    /**
     * Attaches an accessory to every switch and signal drawn with an address.
     *
     * The application does this on load; a test that reads the diagram itself has to do it too, or the
     * reducer cannot trace through a single switch.  Copied from
     * `testTheCheckerAgreesWithTheBuild`, which has had it since it was written.
     *
     * @param pages the parsed diagram
     */
    private static void wireAccessories(List<LayoutDiagram> pages)
    {
        for (LayoutDiagram page : pages)
        {
            for (org.traincontrol.base.LayoutDiagramComponent component : page.getAll())
            {
                if (!component.isSwitch() && !component.isSignal()) continue;

                // the application skips tiles drawn without a digital address, and so does this
                if (component.getAddress() <= 0) continue;

                org.traincontrol.base.Accessory.accessoryType type = component.isSignal()
                    ? org.traincontrol.base.Accessory.accessoryType.SIGNAL
                    : org.traincontrol.base.Accessory.accessoryType.SWITCH;

                component.setAccessory(accessory(component.getAddress(), type,
                    component.getProtocol()));

                if (component.isThreeWay())
                {
                    component.setAccessory2(accessory(component.getAddress() + 1,
                        org.traincontrol.base.Accessory.accessoryType.SWITCH,
                        component.getProtocol()));
                }
            }
        }
    }

    private static org.traincontrol.marklin.MarklinAccessory accessory(int logicalAddress,
        org.traincontrol.base.Accessory.accessoryType type,
        org.traincontrol.base.Accessory.accessoryDecoderType protocol)
    {
        return new org.traincontrol.marklin.MarklinAccessory(null, logicalAddress - 1, type, protocol,
            org.traincontrol.marklin.MarklinAccessory.getNameWithProtocol(logicalAddress, type,
                protocol), false, 0);
    }

    // ---------------------------------------------------------------- the setups Adam asked for

    /**
     * Sets every tile on the main page to one length, which is what makes the guard testable here.
     *
     * @param units the length to give every tile
     */
    private void measureEverythingAs(int units)
    {
        // COLLECTED FIRST, THEN WRITTEN.  setTileLength rebuilds the reducer, so walking its own
        // collections while writing to it re-derives the graph under the iterator - which quietly
        // dropped it from 50-odd edges to 18, and every route on the railway with them.  The first
        // version of this test did exactly that and then reported that nothing was reachable.
        Set<TileKey> everyTile = new LinkedHashSet<>();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (MAIN.equals(tile.getPage())) everyTile.add(tile);
        }

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : session.getReducer()
            .getEdges())
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null && MAIN.equals(step.getTile().getPage()))
                {
                    everyTile.add(step.getTile());
                }
            }
        }

        for (TileKey tile : everyTile) session.setTileLength(tile, units);
    }

    private Layout rebuild() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the configuration did not build");

        return built;
    }

    private Locomotive anyPlacedLocomotive(Layout built)
    {
        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null) return point.getCurrentLocomotive();
        }

        return null;
    }

    /**
     * The berths this train is offered that it would have to come to rest inside - terminus or
     * reversing, which is where the room rule applies.
     *
     * @param built the railway
     * @param loc the train
     * @return the names of those destinations
     */
    private Set<String> berthsOfferedTo(Layout built, Locomotive loc)
    {
        Set<String> out = new LinkedHashSet<>();

        List<List<Edge>> paths = built.getPossiblePaths(loc, true);

        if (paths == null) return out;

        for (List<Edge> path : paths)
        {
            if (path.isEmpty()) continue;

            Point end = path.get(path.size() - 1).getEnd();

            if (end.isTerminus() || end.isReversing()) out.add(end.getName());
        }

        return out;
    }
}
