package core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
 * The length rules, checked against the SPEC over hundreds of random railways.
 *
 * Adam, 2026-09-06: **"make sure we have non-deterministic tests of various lengths to stress test the
 * setup.  You have the spec, make sure the code honestly implements it."**
 *
 * **Why this is a different kind of test from the ones beside it.**  Every other length test picks the
 * cases I thought of, and the whole history of this feature is cases nobody thought of: a train
 * exactly filling its berth, a fork behind a tail, an unmeasured segment cancelling a refusal that had
 * already been earned.  These generate the railway instead, and check a property that must hold on
 * every one of them - so they cover the shapes I would not have chosen, including the ones I would
 * have got wrong.
 *
 * **The oracle is the spec, written out independently.**  Each property below re-states Adam's rule in
 * its own terms and compares that answer with the code's.  A test that re-implemented the code would
 * agree with it about everything including its mistakes; these agree with the RULING.
 *
 * **Seeded, so a failure can be reproduced.**  The seed is printed in every assertion message.  A
 * non-deterministic test that cannot be replayed reports a bug nobody can then find.
 */
public class testLengthRulesUnderRandomLayouts
{
    private static MarklinControlStation model;

    private static int addresses = 2000;

    /** How many random railways each property is checked against. */
    private static final int RAILWAYS = 200;

    @BeforeClass
    public static void setUp() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * The tail covers exactly the segments the spec says it covers, on every generated railway.
     *
     * The three rules, restated: walk back one segment at a time while the train has length left;
     * stop where more than one way in exists; stop at any segment without a positive length.
     */
    @Test
    public void testTheTailMatchesTheSpecOnRandomLines() throws Exception
    {
        for (int seed = 0; seed < RAILWAYS; seed++)
        {
            Random random = new Random(seed);

            int hops = 1 + random.nextInt(6);

            int trainLength = 1 + random.nextInt(20);

            List<Integer> lengths = new ArrayList<>();

            for (int i = 0; i < hops; i++)
            {
                // A third of segments are left unmeasured on purpose - that is the case the rules are
                // most specific about, and the one a hand-written fixture forgets.
                lengths.add(random.nextInt(3) == 0 ? 0 : 1 + random.nextInt(8));
            }

            Chain chain = straightChain(lengths, trainLength);

            Map<Edge, Locomotive> covered = chain.layout.edgesCoveredByStandingTrains();

            // THE SPEC, computed here rather than read from the code.  The chain runs
            // hop[0] -> hop[1] -> ... -> berth, so walking back from the berth is the reverse order.
            Set<Edge> expected = new LinkedHashSet<>();

            int remaining = trainLength;

            for (int i = chain.hops.size() - 1; i >= 0 && remaining > 0; i--)
            {
                Edge segment = chain.hops.get(i);

                if (segment.getLength() <= 0) break;

                expected.add(segment);

                remaining -= segment.getLength();
            }

            assertEquals(covered.keySet(), expected,
                "seed " + seed + ": a train of " + trainLength + " on segments " + lengths
                + " covers " + names(covered.keySet()) + ", but the spec says " + names(expected));
        }
    }

    /**
     * A tail never reaches past a fork, however long the train is.
     *
     * Stated as its own property because it is the limit Adam accepted knowingly, and a limit that
     * quietly stops holding is worse than one that was never there: it would start blocking track on a
     * guess, and a guess that refuses still stops a train.
     */
    @Test
    public void testNoTailEverCrossesAFork() throws Exception
    {
        for (int seed = 0; seed < RAILWAYS; seed++)
        {
            Random random = new Random(1000 + seed);

            // Long enough that an unlimited walk would certainly run past the fork.
            int trainLength = 20 + random.nextInt(80);

            Layout layout = new Layout(model);

            String tag = "_rf" + seed;

            Point berth = point(layout, "RBERTH" + tag, true);
            Point throat = point(layout, "RTHROAT" + tag, false);

            Edge leadIn = layout.createEdge(throat.getName(), berth.getName());

            leadIn.setLength(1 + random.nextInt(3));

            // Two or three ways into the throat.
            List<Edge> arms = new ArrayList<>();

            for (int i = 0; i < 2 + random.nextInt(2); i++)
            {
                Point arm = point(layout, "RARM" + tag + "_" + i, false);

                Edge in = layout.createEdge(arm.getName(), throat.getName());

                in.setLength(1 + random.nextInt(9));

                arms.add(in);
            }

            Locomotive loc = model.getLocByName(model.getLocList().get(0));

            loc.setTrainLength(trainLength);

            berth.setLocomotive(loc);

            Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

            for (Edge arm : arms)
            {
                assertFalse(covered.containsKey(arm),
                    "seed " + seed + ": a train of " + trainLength + " carried its tail through a fork"
                    + " onto one of " + arms.size() + " possible approaches. Only one of them holds it"
                    + " and the graph cannot say which");
            }

            assertTrue(covered.containsKey(leadIn),
                "seed " + seed + ": the deterministic hop before the fork is not covered, so this"
                + " property is passing because nothing is covered at all");
        }
    }

    /**
     * A train is admitted to a berth exactly when it fits in the measured room.
     *
     * The spec, restated: count back from the berth, stop at unmeasured track, and judge on what was
     * counted - unless nothing was measured at all, in which case do not judge.
     */
    @Test
    public void testTheBerthRuleMatchesTheSpecOnRandomRunIns() throws Exception
    {
        for (int seed = 0; seed < RAILWAYS; seed++)
        {
            Random random = new Random(2000 + seed);

            int hops = 1 + random.nextInt(4);

            int trainLength = 1 + random.nextInt(15);

            List<Integer> lengths = new ArrayList<>();

            for (int i = 0; i < hops; i++)
            {
                lengths.add(random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(6));
            }

            Chain chain = straightChain(lengths, trainLength);

            chain.berth.setTerminus(true);

            // THE SPEC, again computed rather than read.
            int measured = 0;

            for (int i = chain.hops.size() - 1; i >= 0; i--)
            {
                if (chain.hops.get(i).getLength() <= 0) break;

                measured += chain.hops.get(i).getLength();
            }

            boolean shouldBeJudged = measured > 0;

            boolean shouldFit = !shouldBeJudged || trainLength <= measured;

            boolean allowed = chain.layout.isPathClear(chain.hops, chain.loc, false);

            assertEquals(allowed, shouldFit,
                "seed " + seed + ": a train of " + trainLength + " into segments " + lengths
                + " (measured room " + measured + (shouldBeJudged ? "" : ", nothing measured")
                + ") was " + (allowed ? "admitted" : "refused")
                + " but the spec says it should be " + (shouldFit ? "admitted" : "refused"));
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static final class Chain
    {
        private Layout layout;
        private List<Edge> hops;
        private Point berth;
        private Locomotive loc;
    }

    /**
     * A straight chain of points joined by segments of the given lengths, train standing at the end.
     *
     * @param lengths the segment lengths in travel order, 0 meaning unmeasured
     * @param trainLength how long the train at the far end is
     * @return the built chain
     * @throws Exception on a failure to build
     */
    private Chain straightChain(List<Integer> lengths, int trainLength) throws Exception
    {
        Layout layout = new Layout(model);

        String tag = "_c" + (addresses++);

        List<Point> points = new ArrayList<>();

        for (int i = 0; i <= lengths.size(); i++)
        {
            points.add(point(layout, "P" + i + tag, i == 0 || i == lengths.size()));
        }

        List<Edge> hops = new ArrayList<>();

        for (int i = 0; i < lengths.size(); i++)
        {
            Edge hop = layout.createEdge(points.get(i).getName(), points.get(i + 1).getName());

            hop.setLength(lengths.get(i));

            hops.add(hop);
        }

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        loc.setTrainLength(trainLength);

        Point berth = points.get(points.size() - 1);

        berth.setLocomotive(loc);

        Chain chain = new Chain();

        chain.layout = layout;
        chain.hops = hops;
        chain.berth = berth;
        chain.loc = loc;

        return chain;
    }

    private Point point(Layout layout, String name, boolean destination) throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(addresses++, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint(name, destination, sensor.getName());

        return layout.getPoint(name);
    }

    private String names(java.util.Collection<Edge> edges)
    {
        List<String> out = new ArrayList<>();

        for (Edge e : edges) out.add(e.getName());

        java.util.Collections.sort(out);

        return out.toString();
    }
}
