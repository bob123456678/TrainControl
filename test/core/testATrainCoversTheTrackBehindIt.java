package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train standing somewhere covers the track behind it, and nothing may route over that.
 *
 * Adam, 2026-09-06: **"the mechanics around collision detection are new complexity that we also need
 * to carefully check, especially if a train protrudes far behind where it is standing.  Those edges it
 * reaches need to be considered blocked.  For example, bottommainc should currently be blocked since a
 * train of length 4 is standing at bottommainb, which has a length of 1 leading up to its switch, but
 * the length of the train is 4, so it protrudes past the switch."**
 *
 * And his three rulings on how far the tail reaches, which are what this pins:
 *
 * 1. **EDGES, not points** - *"because the points are technically unoccupied.  But the blocked edges
 *    should prevent routing to the covered points."*  A train standing at one sensor does not make the
 *    next sensor occupied; it makes the track between them impassable, and a destination reachable
 *    only across that track becomes unreachable as a consequence rather than by a second rule.
 * 2. **Only the deterministic way back** - *"if entering a switch from the no fork direction, keep
 *    following it.  If entering a switch from the fork direction where it splits, just end locking at
 *    the switch and call it a day."*  Walking backwards, one way in means the tail certainly lies
 *    there; several means the graph cannot say which, and a guess would block track that is clear.
 * 3. **Only measured track counts** - *"if no length specified, just stop there.  Only segments with a
 *    positive length are determinate."*
 *
 * The last two are limits, deliberately.  They under-claim: a tail that really does extend past a fork
 * or across an unmeasured segment is not blocked, and Adam knows it - the alternative is blocking
 * track on a guess, which stops trains that could have run.
 */
public class testATrainCoversTheTrackBehindIt
{
    private static MarklinControlStation model;

    private static int addresses = 700;

    @BeforeClass
    public static void setUp() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * Adam's own example: a 4-long train, one unit of lead-in, so the tail reaches past it.
     */
    @Test
    public void testATailLongerThanItsLeadInReachesTheEdgeBeyond() throws Exception
    {
        Line line = straightLine(4, 1, 5);

        Map<Edge, Locomotive> covered = line.layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(line.leadIn),
            "the one unit of track the train is standing on the end of is not covered at all");

        assertTrue(covered.containsKey(line.behind),
            "a train of 4 with 1 unit of lead-in does not reach the track beyond it. That is Adam's"
            + " BottomMainB case exactly: it protrudes past the switch, and the edge past the switch"
            + " has to be blocked or the next train routes straight into it");
    }

    /**
     * And a train that fits within its lead-in does not reach beyond it.
     *
     * The control. Without it, a rule that blocks everything behind every train passes the case above.
     */
    @Test
    public void testATailThatFitsDoesNotReachAnyFurther() throws Exception
    {
        Line line = straightLine(1, 4, 5);

        Map<Edge, Locomotive> covered = line.layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(line.leadIn),
            "the track immediately behind a standing train is always covered by it");

        assertFalse(covered.containsKey(line.behind),
            "a train of 1 in 4 units of lead-in reaches past it, so this rule blocks track no train is"
            + " anywhere near - and the case above passes for no reason");
    }

    /**
     * Exactly as long as the lead-in: covered, and not one edge further.
     */
    @Test
    public void testATailThatExactlyFillsItsLeadInStopsThere() throws Exception
    {
        Line line = straightLine(4, 4, 5);

        Map<Edge, Locomotive> covered = line.layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(line.leadIn), "the lead-in it exactly fills is not covered");

        assertFalse(covered.containsKey(line.behind),
            "a train that exactly fills its lead-in was taken to reach past it. Off by one at the"
            + " boundary blocks a whole extra section on every correctly measured layout");
    }

    /**
     * An unmeasured segment stops the walk, because nothing can be said about it (ruling 3).
     */
    @Test
    public void testAnUnmeasuredSegmentEndsTheTail() throws Exception
    {
        Line line = straightLine(9, 1, 0);

        Map<Edge, Locomotive> covered = line.layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(line.leadIn), "the measured lead-in is still covered");

        assertFalse(covered.containsKey(line.behind),
            "an unmeasured segment was counted as though its length were known. Adam: \"if no length"
            + " specified, just stop there - only segments with a positive length are determinate\"");
    }

    /**
     * A fork behind the train ends the walk, because the graph cannot say which way the tail lies.
     */
    @Test
    public void testAForkBehindTheTrainEndsTheTail() throws Exception
    {
        Layout layout = new Layout(model);

        String tag = "_fork" + (addresses++);

        Point berth = point(layout, "BERTH" + tag, true);
        Point throat = point(layout, "THROAT" + tag, false);
        Point armA = point(layout, "ARM_A" + tag, false);
        Point armB = point(layout, "ARM_B" + tag, false);

        // Two ways INTO the throat, which is a fork when walked backwards.
        Edge leadIn = layout.createEdge(throat.getName(), berth.getName());
        Edge fromA = layout.createEdge(armA.getName(), throat.getName());
        Edge fromB = layout.createEdge(armB.getName(), throat.getName());

        leadIn.setLength(1);
        fromA.setLength(9);
        fromB.setLength(9);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        loc.setTrainLength(9);

        berth.setLocomotive(loc);

        Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(leadIn), "the deterministic first hop is still covered");

        assertFalse(covered.containsKey(fromA),
            "the tail was carried through a fork onto one of two possible approaches. Only one of them"
            + " holds the train and the graph cannot say which, so blocking either stops a train that"
            + " could have run (Adam's ruling 2)");

        assertFalse(covered.containsKey(fromB), "the same, from the other arm");
    }

    /**
     * And the covered track is not routed over - which is the point of computing it (ruling 1).
     *
     * **The first version of this proved nothing, and its own control caught it.**  It routed a second
     * train to the square the standing train was on, so `isPathClear` refused it for occupancy and
     * would have refused it just as firmly with no tail involved.  A refusal is only evidence when the
     * same route on the same railway is ALLOWED once the cause is removed.
     *
     * So the route here ends somewhere free, and crosses the covered track on the way:
     * `APPROACH -> JUNCTION -> SIDING`, with the standing train at `BERTH` protruding back across
     * `APPROACH -> JUNCTION`.  Points either side stay technically unoccupied, which is exactly why
     * the edge has to carry it.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAPathOverCoveredTrackIsRefused() throws Exception
    {
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        other.setTrainLength(1);

        // The tail reaches back across APPROACH -> JUNCTION: 4 long, 1 unit of lead-in.
        assertFalse(canRunToTheSiding(4, other),
            "another train was cleared to run over track a standing train is protruding across. The"
            + " points either side are technically unoccupied, which is exactly why the EDGES have to"
            + " carry it (Adam's ruling 1)");

        // The same railway, the same route, a train that does not protrude.
        assertTrue(canRunToTheSiding(1, other),
            "the identical route is refused even when nothing protrudes across it, so the refusal"
            + " above says nothing about covered track");
    }

    /**
     * A STRAIGHT run, because a junction behind the train stops the walk by design.
     *
     * The first version put the standing train beyond a junction and expected its tail to reach the
     * approach.  It does not, and should not: walked backwards the junction has two ways on, the graph
     * cannot say which the tail lies along, and Adam's ruling is to stop there.  So the fixture was
     * asking for behaviour the spec forbids.
     *
     * A -> B -> C -> D, train standing at D reaching back over C -> D and B -> C.  The second train
     * runs A -> B -> C, crossing B -> C, and C is free.
     *
     * @param standingTrainLength how far the standing train reaches back
     * @param mover the train being routed
     * @return whether the route is clear
     * @throws Exception on a failure to build
     */
    private boolean canRunToTheSiding(int standingTrainLength, Locomotive mover) throws Exception
    {
        Layout layout = new Layout(model);

        String tag = "_route" + (addresses++);

        Point a = point(layout, "RA" + tag, true);
        Point b = point(layout, "RB" + tag, false);
        Point c = point(layout, "RC" + tag, true);
        Point d = point(layout, "RD" + tag, true);

        Edge ab = layout.createEdge(a.getName(), b.getName());
        Edge bc = layout.createEdge(b.getName(), c.getName());
        Edge cd = layout.createEdge(c.getName(), d.getName());

        ab.setLength(5);
        bc.setLength(5);
        cd.setLength(1);

        Locomotive standing = model.getLocByName(model.getLocList().get(0));

        standing.setTrainLength(standingTrainLength);

        d.setLocomotive(standing);

        a.setLocomotive(mover);

        List<Edge> route = new ArrayList<>();

        route.add(ab);
        route.add(bc);

        return layout.isPathClear(route, mover, false);
    }

    /**
     * The railway writes down which way a train came in, as it arrives.
     *
     * Adam, 2026-09-07, asked whether autonomy should set this itself: **"yes, auto write it."**
     *
     * The arrival is the only moment anything knows it for certain.  Afterwards the train is standing
     * still and nothing about it says which side it came from - facing points the way it will LEAVE,
     * and after a reversal at the platform the two agree while the tail is on the opposite side.
     *
     * **Checked as an ordering, not only as a value.**  `executePathInternal` turns the train round a
     * few lines below this write, and turning it does not move its carriages - so recording the side
     * AFTER the reversal would store the way it is about to depart, which is the exact mistake the
     * property exists to end.  A test that only read the final value would pass either way on a run
     * with no reversal, which is most of them.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheArrivalRecordsWhichWayTheTrainCameIn() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automation/Layout.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        String flat = source.replaceAll("\\s+", " ");

        assertTrue(flat.contains(
            "arrived.setArrivedFrom(sideTowards(arrived, path.get(path.size() - 1).getStart()));"),
            "the arrival no longer records which way the train came in, so nothing knows where any"
            + " tail lies until somebody sets it by hand");

        int written = flat.indexOf("arrived.setArrivedFrom(");
        int turned = flat.indexOf("loc.delay(this.getMinDelay(), this.getMaxDelay()).switchDirection()");

        assertTrue(turned > written,
            "the arrival side is recorded AFTER the train is turned round. Turning it does not move"
            + " its carriages, so that records the side it is about to depart by - which is the"
            + " mistake arrivedFrom exists to end");

        assertTrue(flat.contains("path.get(0).getStart().setArrivedFrom(null);"),
            "the square the train left keeps its arrival side, so the track behind an empty platform"
            + " stays blocked by a train that drove away from it - and only a rebuild clears it");
    }

    /**
     * And the value itself, on a run the test drives by hand.
     *
     * The ordering above is source; this is the behaviour.  A train moved from A to B has come in from
     * A, and `sideTowards` names that side from the coordinates - so B records the side A lies on.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheRecordedSideIsTheOneTheTrainCameFrom() throws Exception
    {
        Layout layout = new Layout(model);

        String tag = "_side" + (addresses++);

        Point west = point(layout, "WEST" + tag, true);
        Point east = point(layout, "EAST" + tag, true);

        // WEST lies to the west of EAST, which is what the sides are computed from.
        west.setX(0);
        west.setY(0);

        east.setX(10);
        east.setY(0);

        assertEquals(layout.sideTowards(east, west), "W",
            "a point at x=0 is not west of one at x=10, so every arrival side on this railway would"
            + " be recorded back to front");

        assertEquals(layout.sideTowards(west, east), "E", "and the other way about");

        // Y grows downwards on a diagram drawn from the top left, so a larger y is SOUTH.
        Point south = point(layout, "SOUTH" + tag, true);

        south.setX(0);
        south.setY(10);

        assertEquals(layout.sideTowards(west, south), "S",
            "a point at y=10 is not south of one at y=0. Y grows downwards on the diagram, and getting"
            + " this the wrong way round puts every tail on the wrong side of its train");
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * A straight run: FAR -> NEAR -> BERTH, with a train standing at the berth.
     */
    private static final class Line
    {
        private Layout layout;
        private Edge leadIn;
        private Edge behind;
    }

    /**
     * @param trainLength how long the train standing at the berth is
     * @param leadInLength the measured track between NEAR and the berth
     * @param behindLength the measured track between FAR and NEAR, or 0 to leave it unmeasured
     * @return the built line
     * @throws Exception on a failure to build
     */
    private Line straightLine(int trainLength, int leadInLength, int behindLength) throws Exception
    {
        Layout layout = new Layout(model);

        String tag = "_" + (addresses++);

        Point far = point(layout, "FAR" + tag, false);
        Point near = point(layout, "NEAR" + tag, false);
        Point berth = point(layout, "BERTH" + tag, true);

        Edge behind = layout.createEdge(far.getName(), near.getName());
        Edge leadIn = layout.createEdge(near.getName(), berth.getName());

        behind.setLength(behindLength);
        leadIn.setLength(leadInLength);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        loc.setTrainLength(trainLength);

        berth.setLocomotive(loc);

        Line line = new Line();

        line.layout = layout;
        line.leadIn = leadIn;
        line.behind = behind;

        return line;
    }

    private Point point(Layout layout, String name, boolean destination) throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(addresses++, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint(name, destination, sensor.getName());

        return layout.getPoint(name);
    }
}
