package core;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.assertEquals;
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

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

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

    /**
     * BottomMainPost to TunnelLongPark: which tile decides it, re-measured after the bound.
     *
     * **This test recorded the wrong answer for an hour, and the reason is worth keeping.**  It was
     * written when the room rule declined to judge an unmeasured stretch, and it measured that
     * `1 - Main:19,12` made no difference: the tile sits on the far side of the last switch, and only
     * what lies between the switch and the berth was counted.
     *
     * Then Adam reported that he could still make the run, and the fix was to bound the unmeasured
     * stretch by the segment it lies in - a stretch inside a segment cannot be longer than it.  That
     * bound reads the SEGMENT length, which 19,12 is part of.  So the tile that made no difference now
     * makes all of it, and this test says so rather than being quietly deleted.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheSegmentLengthNowDecidesTheRouteToTunnelLongPark() throws Exception
    {
        measureEverythingAs(0);

        Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "75 407 DB is not on this railway any more");

        train.setTrainLength(4);

        session.setTileLength(new TileKey(MAIN, 19, 12), 1);

        assertFalse(offers(train, "TunnelLongPark"),
            "one unit of measured segment admitted a four-unit train. The run in cannot hold more"
            + " than the segment it lies in, so one unit bounds it from above and four does not fit");

        session.setTileLength(new TileKey(MAIN, 19, 12), 4);

        assertTrue(offers(train, "TunnelLongPark"),
            "four units of measured segment refused a four-unit train, so exactly-fits is being"
            + " refused and every berth measured to its train becomes unusable");

        // AND WITH NOTHING MEASURED THERE IS NO EVIDENCE, which is his own ruling for that case.
        measureEverythingAs(0);

        assertTrue(offers(train, "TunnelLongPark"),
            "the route is refused with nothing measured anywhere. That is a refusal on no evidence,"
            + " and Adam ruled the other way: \"generally, allow it\"");
    }
    /**
     * Whether this train is offered a destination whose name starts with the given text.
     *
     * @param loc the train
     * @param destination the name to look for
     * @return true when it is offered
     * @throws Exception on a failure to build
     */
    private boolean offers(Locomotive loc, String destination) throws Exception
    {
        Layout built = rebuild();

        for (List<Edge> path : built.getPossiblePaths(loc, true))
        {
            if (path.isEmpty()) continue;

            if (path.get(path.size() - 1).getEnd().getName().startsWith(destination)) return true;
        }

        return false;
    }

    /**
     * Four units of room is exactly the minimum a four-unit train is admitted on.
     *
     * Adam: **"if we made both lengths 4, would it then be accepted?  That should be the minimum
     * acceptable length."**  Measured on his railway, with 75 407 DB at its recorded four units:
     *
     * | room between the last switch and TunnelLongPark | offered |
     * |---|---|
     * | 3 (one tile at 3) | no |
     * | 3 (1 + 2 across two tiles) | no |
     * | 4 (one tile at 4) | YES |
     * | 4 (2 + 2 across two tiles) | YES |
     * | 8 (4 + 4) | YES |
     *
     * Two things worth having in one table.  Four is the boundary and it is inclusive - a train that
     * exactly fills its berth is admitted, which is what "minimum acceptable" means.  And 2 + 2 is
     * admitted while 1 + 2 is not, so the rule is reading the TOTAL across the stretch rather than any
     * single tile - his ruling of the same day, checked rather than asserted.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testFourUnitsIsTheMinimumRoomAFourUnitTrainIsAdmittedOn() throws Exception
    {
        measureEverythingAs(0);

        Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "75 407 DB is not on this railway any more");

        train.setTrainLength(4);

        assertFalse(roomOf(3, 0, train), "three units of room admitted a four-unit train");

        assertFalse(roomOf(1, 2, train),
            "1 + 2 admitted a four-unit train, so the rule is reading a single tile rather than the"
            + " total across the stretch");

        assertTrue(roomOf(4, 0, train),
            "four units of room REFUSED a four-unit train. Exactly-fits must be admitted or every"
            + " berth measured to the train that lives in it becomes unusable - Adam: \"that should be"
            + " the minimum acceptable length\"");

        assertTrue(roomOf(2, 2, train),
            "2 + 2 refused a four-unit train while 4 + 0 admitted it, so the two tiles are not being"
            + " added together");

        assertTrue(roomOf(4, 4, train), "eight units of room refused a four-unit train");
    }

    /**
     * Sets the two tiles that bind the run into TunnelLongPark and asks whether it is offered.
     *
     * @param first 1 - Main:10,9
     * @param second 1 - Main:10,10
     * @param train the locomotive
     * @return whether TunnelLongPark is offered
     * @throws Exception on a failure to build
     */
    private boolean roomOf(int first, int second, Locomotive train) throws Exception
    {
        session.setTileLength(new TileKey(MAIN, 10, 9), first);
        session.setTileLength(new TileKey(MAIN, 10, 10), second);

        return offers(train, "TunnelLongPark");
    }

    /**
     * The railway as Adam describes it: two numbered segments, both one unit - and the route refused.
     *
     * **"There are only two numbered track segments, both of length 1.  Direction should not matter."**
     *
     * His saved setup does not say that, and the difference is mine.  When his layout was damaged I
     * restored `tileLengths` from the last commit, which put SIX older measurements back - including
     * `1 - Main:1,10 = 4` and `1 - Main:0,11 = 4`.  Those two are the 8 the guard was reading, and
     * they are the reason a four-unit train was admitted to a berth he had measured at one.
     *
     * So this clears everything and sets only the two he named.  If it refuses, the guard is correct
     * and the disagreement was entirely stale data in his file.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTwoOneUnitSegmentsRefuseTheFourUnitTrain() throws Exception
    {
        measureEverythingAs(0);

        // The two he named, and nothing else.
        session.setTileLength(new TileKey(MAIN, 19, 12), 1);
        session.setTileLength(new TileKey(MAIN, 22, 7), 1);

        Locomotive train = model.getLocByName("75 407 DB");

        assertNotNull(train, "75 407 DB is not on this railway any more");

        train.setTrainLength(4);

        assertFalse(offers(train, "TunnelLongPark"),
            "with only the two one-unit segments he measured, a four-unit train is still offered"
            + " TunnelLongPark - so the guard is wrong and the stale lengths were not the cause");
    }

    /**
     * BottomMainC is blocked while the 2-8-4 lies across the switch behind BottomMainB.
     *
     * Adam, 2026-09-07: **"I turned the 2-8-4 train around, so it is now facing east.  Assume it
     * arrived from the west.  We set arrivedFrom for 2-8-4 at BottomMainB to west.  Now, you should be
     * able to block the switch that leads to BottomMainC."**
     *
     * **Why facing could not have told us this.**  A train faces the way it will leave; its tail is
     * behind it, and once it has been turned round the two point the same way.  Before `arrivedFrom`
     * the walk looked up the track this train is about to depart along - which is clear - and reported
     * nothing blocked.  The rail it is actually lying across is the one it came in by, and only the
     * train knows that.
     *
     * The tail has to be longer than the run into the platform for any of this to matter, so the lead
     * in is measured at one and the train at four: three units of it are past the switch.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testBottomMainCIsBlockedByTheTailAtBottomMainB() throws Exception
    {
        measureEverythingAs(0);

        // One unit of platform road, so a four-unit train is three units past the switch.
        session.setTileLength(new TileKey(MAIN, 19, 13), 1);

        Layout built = rebuild();

        Locomotive engine = model.getLocByName("2-8-4 3505 SP");

        assertNotNull(engine, "2-8-4 3505 SP is not on this railway any more");

        engine.setTrainLength(4);

        Point platform = null;

        for (Point p : built.getPoints())
        {
            if (engine.equals(p.getCurrentLocomotive())) platform = p;
        }

        assertNotNull(platform, "the 2-8-4 is not standing anywhere");

        assertTrue(platform.getName().startsWith("BottomMainB"),
            "the 2-8-4 is at " + platform.getName() + " rather than BottomMainB");

        // NOTHING RECORDED YET: the walk cannot tell which way the tail lies, and says so by
        // blocking nothing.  This is the control - without it the assertion below could pass on a
        // rule that blocks the whole railway.
        platform.setArrivedFrom(null);

        assertTrue(built.edgesCoveredByStandingTrains().isEmpty(),
            "track is being blocked with no arrival side recorded, so the rule is guessing which way"
            + " the tail lies rather than being told");

        // AND NOW HIS CASE: it came in from the west.
        platform.setArrivedFrom("W");

        java.util.Map<Edge, Locomotive> covered = built.edgesCoveredByStandingTrains();

        StringBuilder sides = new StringBuilder();
        for (Edge e : built.getNeighborsAndIncoming(platform))
        {
            Point other = e.getStart() == platform ? e.getEnd() : e.getStart();
            sides.append(" ").append(built.sideTowards(platform, other)).append("->")
                 .append(other.getName()).append("(len=").append(e.getLength()).append(")");
        }

        assertFalse(covered.isEmpty(),
            "with the arrival side recorded as west, the tail covers nothing. Copy " + platform.getName()
            + " offers:" + sides);

        StringBuilder what = new StringBuilder();

        for (Edge e : covered.keySet()) what.append(" ").append(e.getName());

        boolean touchesTheThroat = what.toString().contains("BottomMainBCPre")
            || what.toString().contains("BottomMainPost");

        assertTrue(touchesTheThroat,
            "the tail covers" + what + ", none of which is the track behind BottomMainB - so whatever"
            + " it found is not the rail the 2-8-4 is lying across");
    }

    // ---------------------------------------------------------------- the setups Adam asked for

    /**
     * Sets EVERY tile the reduction knows to one length, which is what makes the guard testable here.
     *
     * **Every page, not just the main one, since 2026-09-08.** It filtered to `1 - Main` and its name
     * still said "everything", which was true of the railway this suite used to build: the fixture
     * parsed its pages without wiring their accessories, so the graph was eighteen edges and never left
     * that page. Wired, it is the whole railway - and a berth on another page arrived UNMEASURED, which
     * the guard cannot judge, so a nine-unit train was offered `LowerFront` on a layout this method had
     * just declared measured at one unit throughout.
     *
     * Two of the four failures in this class were that, and they are the good kind: the test said
     * something about the whole railway while looking at a fifth of it.
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

        everyTile.addAll(session.getReducer().getPoints().keySet());

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge : session.getReducer()
            .getEdges())
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) everyTile.add(step.getTile());
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
