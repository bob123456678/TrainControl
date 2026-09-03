package core;

import java.io.File;
import java.nio.file.Files;
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
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Where a train actually goes, counted, against the operator's own railway (MT-231, OB-156).
 *
 * Adam, after trying to test the priority rules by hand: "make an automated test case for this against
 * the current layout, by varying the priority dynamically at test time.  use bottommainA/B/C, as these
 * are all reachable by a train arriving from tunnel.  try at least 10 combinations of priorities.  as
 * validation, make sure the distribution is as expected."
 *
 * **This counts DECISIONS, not journeys.**  `pickPath` is the whole of the rule under test: it reads
 * the priorities, ranks the destinations and hands back a route.  Executing that route would add
 * several seconds per sample and change nothing about the answer, so the sampling calls pickPath
 * directly and never starts a locomotive.  That is also why no delay setting appears here - there is
 * nothing to wait for.  (The fixtures' minDelay/maxDelay are 0 and 1 regardless, which is the other
 * half of what he asked for.)
 *
 * THE FIXTURE IS HIS RAILWAY, frozen.  `test/test_layout_snapshot` carries the legacy autonomy.json
 * this loads, and BottomMainA, BottomMainB and BottomMainC really are the three destinations a train
 * leaving Tunnel can reach - which is why he named them.  Their priorities are set here, per case, so
 * the file is read and never written.
 *
 * ON RANDOMNESS.  `pickPath` shuffles its destinations with a Random this test cannot seed, so these
 * assertions are statistical.  They are written to be decisive rather than tight: "this station is
 * never chosen" and "each of these is chosen at least a tenth of the time" over 400 samples.  A rule
 * that ignored priority would fail the first by hundreds of counts, not by one or two, and a rule that
 * always picked the same station would fail the second the same way.  Nothing here is within a
 * standard deviation of passing by luck.
 */
public class testStationPriorityDistribution
{
    /** The three destinations a train leaving Tunnel can reach - Adam named them. */
    private static final String A = "BottomMainA";
    private static final String B = "BottomMainB";
    private static final String C = "BottomMainC";

    /** Where the train stands for every sample. */
    private static final String FROM = "Tunnel";

    /**
     * How many decisions each case counts.
     *
     * Large enough that "never chosen" and "roughly a third" are separated by hundreds of counts, and
     * cheap because a decision is a graph search and not a train.
     */
    private static final int SAMPLES = 400;

    /** The other stations that carry a priority of their own in the snapshot, flattened per case. */
    private static final String[] OTHER_PRIORITISED =
    {
        "BottomMainCTerm", "TopMainR0", "TopMainR1", "TopMainR2"
    };

    private static MarklinControlStation model;

    /** A throwaway copy of the fixture layout, so building a model cannot open the real railway. */
    private static support.LayoutSandbox sandbox;

    /**
     * BEFORE THE MODEL, not just before a window (OB-111).
     *
     * MarklinControlStation.init reads the saved layout-path preference, and on the operator's machine
     * that names his own railway - so a model built without this loads it, and the battery's guard on
     * that folder fires.  It did: this class was the fifty-seventh to build a model without a sandbox,
     * and testNoTestOpensTheOperatorsRailway counted it.
     *
     * The layout this class actually reads is the frozen snapshot, loaded by hand from JSON.  The
     * sandbox is about what init does on the way to giving us a model at all.
     */
    @BeforeClass
    public void connect() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, true);
    }

    @AfterClass(alwaysRun = true)
    public void disconnect() throws Exception
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /**
     * A rule that respects priority never leaves the highest band that has somewhere to go (OB-156).
     *
     * Twelve combinations, which is Adam's "at least 10".  Each names the stations that share the top
     * priority; every sample must land on one of those and never on the others, however reachable they
     * are.
     *
     * That single assertion is what "uses the station priority and randomly chooses from the highest
     * available" means, and it is the half he thought was missing.  It was not missing - it was called
     * "At Random", which said nothing about priority.  It is now called what it does, and this is the
     * measurement that says so.
     */
    @Test
    public void testTheHighestPriorityBandIsTheOnlyOneUsed() throws Exception
    {
        Layout layout = snapshot();
        Locomotive loc = atTunnel(layout);

        layout.setPathPreference(Layout.PathPreference.RANDOM);

        // priorities for A, B, C, and who should win
        Object[][] cases =
        {
            {5, 0, 0, new String[] {A}},
            {0, 5, 0, new String[] {B}},
            {0, 0, 5, new String[] {C}},
            {5, 5, 0, new String[] {A, B}},
            {5, 0, 5, new String[] {A, C}},
            {0, 5, 5, new String[] {B, C}},
            {5, 5, 5, new String[] {A, B, C}},
            {9, 5, 1, new String[] {A}},
            {1, 9, 5, new String[] {B}},
            {1, 5, 9, new String[] {C}},
            {-1, -5, -5, new String[] {A}},
            {-5, -5, -5, new String[] {A, B, C}},
        };

        for (Object[] one : cases)
        {
            prioritise(layout, (Integer) one[0], (Integer) one[1], (Integer) one[2]);

            String[] expected = (String[]) one[3];

            Map<String, Integer> seen = sample(layout, loc);

            String where = "priorities A=" + one[0] + " B=" + one[1] + " C=" + one[2]
                + ", counted " + seen;

            for (String station : expected)
            {
                // A TENTH, which is what this class's own javadoc promises and what its sibling
                // asserts (TSX-C10).  One sample in four hundred satisfied "> 0", and a 399-to-1
                // split is exactly the fixed order this message says it is ruling out.
                assertTrue(seen.getOrDefault(station, 0) >= SAMPLES / 10,
                    station + " shares the highest priority and was chosen "
                    + seen.getOrDefault(station, 0) + " times in " + SAMPLES + " - so the band is not "
                    + "being picked from at random, it is being picked from in some fixed order.  "
                    + where);
            }

            for (String station : new String[] {A, B, C})
            {
                if (Arrays.asList(expected).contains(station)) continue;

                assertEquals(seen.getOrDefault(station, 0), (Integer) 0,
                    station + " was chosen even though a higher-priority station was available.  "
                    + "Priority must be absolute for this rule - a station the user marked important "
                    + "is not beaten by an ordinary one being nearer.  " + where);
            }
        }
    }

    /**
     * The completely random rule ignores priority, which is the half that did not exist (OB-156).
     *
     * Adam: "let's have one completely random, and one that respects priority."
     *
     * Same fixture, same three stations, the same lopsided priorities - and now all three have to turn
     * up, because this rule is blind to the thing the other one obeys.  Ten per cent is far below an
     * even third and far above the nothing the banded rule gives the losers.
     */
    @Test
    public void testTheCompletelyRandomRuleIgnoresPriority() throws Exception
    {
        Layout layout = snapshot();
        Locomotive loc = atTunnel(layout);

        layout.setPathPreference(Layout.PathPreference.RANDOM_ANY_STATION);

        int[][] cases =
        {
            {9, 0, 0}, {0, 9, 0}, {0, 0, 9}, {9, 9, 0}, {9, 0, 9},
            {0, 9, 9}, {5, 1, -5}, {-5, 1, 5}, {0, 0, 0}, {-1, -2, -3},
        };

        for (int[] one : cases)
        {
            prioritise(layout, one[0], one[1], one[2]);

            Map<String, Integer> seen = sample(layout, loc);

            String where = "priorities A=" + one[0] + " B=" + one[1] + " C=" + one[2]
                + ", counted " + seen;

            for (String station : new String[] {A, B, C})
            {
                assertTrue(seen.getOrDefault(station, 0) >= SAMPLES / 10,
                    station + " came up fewer than one time in ten, so this rule is still steering by "
                    + "priority - and it is the one that must not.  " + where);
            }
        }
    }

    /**
     * And the two rules really do disagree, on one set of priorities (OB-156).
     *
     * The control.  Without it, both tests above pass if the two rules are the same rule - the banded
     * one would simply never be asked about a station it excludes.  Here they are asked the same
     * question and have to answer differently.
     */
    @Test
    public void testTheTwoRandomRulesAreNotTheSameRule() throws Exception
    {
        Layout layout = snapshot();
        Locomotive loc = atTunnel(layout);

        prioritise(layout, 9, 0, 0);

        layout.setPathPreference(Layout.PathPreference.RANDOM);

        Map<String, Integer> banded = sample(layout, loc);

        layout.setPathPreference(Layout.PathPreference.RANDOM_ANY_STATION);

        Map<String, Integer> blind = sample(layout, loc);

        assertEquals(banded.getOrDefault(B, 0), (Integer) 0,
            "the priority-respecting rule sent a train to B while A outranked it: " + banded);

        assertTrue(blind.getOrDefault(B, 0) > 0,
            "the completely random rule refused B just as the banded one did, so the two are one rule "
            + "under two names: " + blind);
    }

    /**
     * The operator's railway as it was frozen, loaded fresh so no case can leak into the next.
     */
    private static Layout snapshot() throws Exception
    {
        File json = new File("test/test_layout_snapshot/config/autonomy_legacy/autonomy.json");

        assertTrue(json.exists(), "the frozen snapshot of the railway is missing: " + json);

        Layout layout = Layout.fromJSON(
            new String(Files.readAllBytes(json.toPath()), java.nio.charset.StandardCharsets.UTF_8),
            model);

        assertTrue(layout.isValid(), "the snapshot did not build into a valid layout");

        for (String name : new String[] {A, B, C, FROM})
        {
            assertNotNull(layout.getPoint(name), name + " is not on the frozen railway any more, so "
                + "this test is measuring something else - see MT-231, where Adam named these four");
        }

        // EVERY OTHER TRAIN IS TAKEN OFF THE RAILWAY FIRST.
        //
        // The snapshot is a real railway at rest, so it has locomotives parked all over it - and a
        // train standing anywhere along the way makes isPathClear refuse the route, whatever the
        // priorities say.  BottomMainC is reachable by bfs and was still never chosen, for exactly
        // that reason: somebody was in the way.
        //
        // Clearing them leaves a question about the RULE rather than about where his trains happened
        // to be parked on the day the snapshot was taken.  The train under test keeps its place.
        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (!FROM.equals(point.getName())) point.setLocomotive(null);
        }

        // WHY A STATION MIGHT NOT COUNT, said here rather than as a puzzling distribution.
        //
        // pickPath skips a destination that is not a station, is switched off, is a reversing point,
        // is not an automatic destination, or has a train standing anywhere on its block.  Any of
        // those makes a station invisible to every rule at once - which reads downstream as "priority
        // is being ignored" when it is nothing of the kind.
        for (String name : new String[] {A, B, C})
        {
            org.traincontrol.automation.Point point = layout.getPoint(name);

            assertTrue(point.isDestination() && point.isActive() && !point.isReversing()
                    && point.isAutoDestination() && point.getBlockLocomotive() == null,
                name + " cannot be chosen by any rule on the frozen railway, so a distribution that "
                + "leaves it out says nothing about priority.  station=" + point.isDestination()
                + " active=" + point.isActive() + " reversing=" + point.isReversing()
                + " autoDestination=" + point.isAutoDestination()
                + " blockedBy=" + point.getBlockLocomotive()
                + " excluded=" + point.getExcludedLocs());
        }

        // AND THAT A TRAIN LEAVING FROM HERE CAN ACTUALLY GET THERE.
        //
        // Adam named these three because "these are all reachable by a train arriving from tunnel".
        // If the frozen graph disagrees, every distribution below is a measurement of the graph rather
        // than of the rule, so it is said here and not discovered as a puzzling count.
        StringBuilder reach = new StringBuilder();

        for (String name : new String[] {A, B, C, "BottomMainCPre", "BottomMainCTerm"})
        {
            if (layout.getPoint(name) == null) continue;

            List<Edge> route = layout.bfs(layout.getPoint(FROM), layout.getPoint(name), null);

            reach.append(name).append(route == null ? "=unreachable " : "=reachable ");
        }

        for (String name : new String[] {A, B, C})
        {
            assertNotNull(layout.bfs(layout.getPoint(FROM), layout.getPoint(name), null),
                name + " cannot be reached from " + FROM + " on the frozen railway, so no rule can "
                + "ever choose it and the distribution says nothing about priority.  " + reach);
        }

        return layout;
    }

    /**
     * Puts a locomotive on Tunnel and hands it back.
     *
     * The snapshot already stands one there; taking whoever it is avoids inventing a locomotive the
     * railway has no length or speed for.
     */
    private static Locomotive atTunnel(Layout layout) throws Exception
    {
        Locomotive standing = layout.getPoint(FROM).getCurrentLocomotive();

        assertNotNull(standing, "nobody is standing at " + FROM + " on the frozen railway");

        // SHORT ENOUGH FOR ALL THREE PLATFORMS.
        //
        // BottomMainC takes a train of 2, and the locomotive the snapshot parks at Tunnel is 4 - so
        // isPathClear refused every route to C on length, and C never appeared in any distribution
        // however its priority was set.  That is the train-length rule working, and it has its own
        // tests; here it would only be measuring which platform is longest.
        //
        // One, not zero: zero means "unknown length", which the rule waves through entirely, and a
        // fixture that turns a rule off tests less than one that satisfies it.
        standing.setTrainLength(1);

        return standing;
    }

    /**
     * Sets the three priorities under test, and flattens every other station that carries one.
     *
     * Without the flattening these cases would not be about A, B and C at all: the snapshot gives
     * BottomMainCTerm, TopMainR0, TopMainR1 and TopMainR2 priorities of their own, and a banded rule
     * would settle one of those bands before it ever looked at these three.
     */
    private static void prioritise(Layout layout, int a, int b, int c)
    {
        layout.getPoint(A).setPriority(a);
        layout.getPoint(B).setPriority(b);
        layout.getPoint(C).setPriority(c);

        for (String name : OTHER_PRIORITISED)
        {
            if (layout.getPoint(name) != null) layout.getPoint(name).setPriority(0);
        }
    }

    /**
     * Asks for a route SAMPLES times and counts where each one ends.
     *
     * Nothing is executed and nothing is locked, so every sample sees the same railway - which is what
     * makes the counts a measurement of the rule rather than of the order the samples ran in.
     */
    private static Map<String, Integer> sample(Layout layout, Locomotive loc)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (int i = 0; i < SAMPLES; i++)
        {
            List<Edge> path = layout.pickPath(loc);

            if (path == null || path.isEmpty()) continue;

            String end = path.get(path.size() - 1).getEnd().getName();

            counts.put(end, counts.getOrDefault(end, 0) + 1);
        }

        assertFalse(counts.isEmpty(),
            "no route at all was found in " + SAMPLES + " attempts, so nothing below is a measurement "
            + "of anything - is " + FROM + " still connected to these three stations?");

        return counts;
    }
}
