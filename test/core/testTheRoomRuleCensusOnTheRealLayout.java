package core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.file.CS2File;

/**
 * The section 5b census, as something that can be run rather than something that was run once.
 *
 * `behaviour.md` section 5b records a measurement made on the operator's own railway on 2026-09-08,
 * after MT-262 removed the fence that had kept the track-room rule to termini and reversing berths:
 * across every ordered station pair, with each train length in his database in turn, the journeys the
 * widened rule NEWLY refuses, and where they arrive. It ends *"a layout with lengths scattered over
 * more of its track would be a different answer, and the way to find out is to run that census again
 * rather than to reason about it"* - and there was nothing committed to run. The number could be
 * quoted and could not be checked.
 *
 * This is the probe. It is a test rather than a tool in `docs/tools` because it costs a few seconds -
 * one BFS per ordered pair over a ninety-six point graph - so it can simply live in the battery and
 * be re-measured on every run, which a tool nobody remembers to invoke cannot.
 *
 * **What "newly refused" means, exactly.** A journey is newly refused when the room behind its
 * destination is measured, is shorter than the train, and the destination is NEITHER a terminus NOR a
 * reversing point - because `ending.isTerminus() || ending.isReversing()` is the fence `Layout`'s own
 * comment says used to stand in front of this rule. A refusal at a terminus is not new; it is what the
 * rule already did.
 *
 * The station-capacity rule is deliberately not counted. That is the number somebody typed on the
 * platform, it is asked separately in `whyTooLongForTheBerth`, and MT-262 did not touch it.
 *
 * **It does not reproduce the published totals, and it cannot.** Section 5b said *"across all 3488
 * ordered station pairs ... the journeys it newly refuses are 332"*. This finds **1980** ordered pairs
 * - 45 destination Points, each against the other 44 - of which **1848** can be routed, and **880**
 * newly refused journeys over the six lengths. The original probe was never committed, so which pairs
 * it enumerated cannot be recovered, and that is the whole reason this file exists: a number nobody
 * can re-derive is a number nobody can check. Section 5b now carries these figures and states the
 * method, so the next person can disagree with it by running something.
 *
 * **What DID reproduce, exactly, is the half the section reasons from.** Every newly refused journey
 * arrives at one of four berths - `BottomMainA (eastbound)`, `BottomMainB (eastbound)`,
 * `BottomMainC (westbound)`, `BottomMainPost (northbound)` - each with ONE measured unit of room
 * behind it. Those are the four the section names, in the order it names them, with the number Adam
 * gave. So the conclusion stands on its own measurement: the widening refuses the journeys he asked to
 * have refused, at the squares he was looking at, and nowhere else on this railway.
 *
 * Read against a `LayoutSandbox` copy of `cs2_sample_layout` (OB-111). His railway is only ever read.
 *
 * @author Adam
 */
public class testTheRoomRuleCensusOnTheRealLayout
{
    /**
     * The census, re-measured on 2026-09-08 by this file.
     *
     * These pin the sentences in `behaviour.md` section 5b. If a number here moves, the railway or the
     * rule has changed and that section is out of date - which is exactly the state section 5b was in
     * while it had no probe: it could say anything, and nothing would disagree.
     */
    private static final int ORDERED_PAIRS = 1980;
    private static final int PAIRS_WITH_A_PATH = 1848;
    private static final int NEWLY_REFUSED = 880;

    /** The train lengths recorded in his database - section 5b's "six train lengths", as a set. */
    private static final Integer[] LENGTHS = {1, 2, 3, 4, 5, 6};

    /**
     * Where every newly refused journey arrives, and the room measured behind each.
     *
     * These are the four berths `behaviour.md` section 5b names, spelled as it spells them - by their
     * split copies - and the ONE unit of room is the number Adam gave himself: *"bottommainb, which
     * has a length of 1 leading up to its switch"*.
     */
    private static final String[] BERTHS =
    {
        "BottomMainA (eastbound)", "BottomMainB (eastbound)",
        "BottomMainC (westbound)", "BottomMainPost (northbound)"
    };

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout built;

    /**
     * Opens a throwaway copy of the operator's railway and builds the configuration it names.
     *
     * @throws Exception when the folder cannot be read at all, which is a broken harness rather than a
     *         failing guard
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        model = init(null, true, false, false, false);

        String path = "file:///"
            + sandbox.getFolder().getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        session = new AutonomySession(sandbox.getFolder());
        session.open(pages);

        model.parseAuto(session.buildConfiguration());

        built = model.getAutoLayout();
    }

    /**
     * Puts the layout preference back.
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The census itself, printed and pinned.
     *
     * MUTATION, run: putting the fence back - answering `false` from `newlyRefuses` unless the
     * destination is a terminus or a reversing point is what the rule DID before MT-262 - takes the
     * count to 0 and empties the berth list. Widening a berth's approach in the setup moves the count
     * too, which is what section 5b's closing sentence asks for: *"a layout with lengths scattered
     * over more of its track would be a different answer, and the way to find out is to run that
     * census again rather than to reason about it."*
     *
     * @throws Exception on a failure to search
     */
    @Test
    public void testTheCensusIsWhatSection5bSays() throws Exception
    {
        assertNotNull(built, "the configuration did not build, so nothing was measured");

        Census census = run();

        // PRINTED AS WELL AS ASSERTED.  Re-measuring is the point of this file, and a person who has
        // just scattered more lengths over the railway wants the new numbers, not only the news that
        // the old ones are wrong.
        System.out.println("### section 5b census, re-measured");
        System.out.println("  ordered station pairs   : " + census.orderedPairs);
        System.out.println("  of those, with a path   : " + census.pairsWithAPath);
        System.out.println("  train lengths asked     : " + census.lengths);
        System.out.println("  journeys newly refused  : " + census.newlyRefused);
        System.out.println("  arriving at             : " + census.berths);

        assertEquals(census.lengths, java.util.Arrays.asList(LENGTHS),
            "the train lengths in the database are now " + census.lengths + ".  Section 5b's census is"
            + " over each of them in turn, so the count below is over a different set of journeys");

        assertEquals(census.orderedPairs, ORDERED_PAIRS,
            "this railway now has " + census.orderedPairs + " ordered station pairs on it rather than "
            + ORDERED_PAIRS + ", so every number below is over a different population");

        assertEquals(census.pairsWithAPath, PAIRS_WITH_A_PATH,
            census.pairsWithAPath + " of those pairs can be routed, against " + PAIRS_WITH_A_PATH
            + " when this was measured.  A large drop is a railway that has come apart rather than a"
            + " rule that has changed");

        assertEquals(census.newlyRefused, NEWLY_REFUSED,
            census.newlyRefused + " journeys are newly refused by the widened room rule, against "
            + NEWLY_REFUSED + " when behaviour.md section 5b was written.  That section states this"
            + " number and reasons from it - that the widening refuses the journeys Adam asked to have"
            + " refused, at the squares he was looking at, and not a wider set - so update it there"
            + " and here together, or the document is describing a railway that no longer exists");

        assertEquals(new TreeSet<>(census.berths.keySet()),
            new TreeSet<>(java.util.Arrays.asList(BERTHS)),
            "the newly refused journeys now arrive at " + census.berths.keySet() + " rather than at "
            + java.util.Arrays.asList(BERTHS) + ".  This is the load-bearing half of section 5b: every"
            + " refusal the widening adds lands on one of the four berths Adam named himself, each"
            + " with ONE measured unit of room behind it.  A fifth berth appearing means the rule has"
            + " started refusing somewhere he was not looking at");

        for (Map.Entry<String, Integer> berth : census.berths.entrySet())
        {
            assertEquals(berth.getValue(), Integer.valueOf(1),
                berth.getKey() + " now measures " + berth.getValue() + " units of room rather than the"
                + " ONE section 5b records - \"bottommainb, which has a length of 1 leading up to its"
                + " switch\"");
        }
    }

    /**
     * What one run of the census found.
     */
    private static final class Census
    {
        int orderedPairs;
        int pairsWithAPath;
        int newlyRefused;

        List<Integer> lengths = new ArrayList<>();

        /** Berth name to the measured room behind it, for every berth that refused something. */
        Map<String, Integer> berths = new LinkedHashMap<>();
    }

    /**
     * Walks every ordered station pair and every train length, and counts the new refusals.
     *
     * The path is searched ONCE per pair: the room rule reads the path and the train, and the path
     * does not depend on how long the train is.
     *
     * @return what it found
     * @throws Exception on a failure to search
     */
    private static Census run() throws Exception
    {
        Census census = new Census();

        for (Integer length : trainLengths())
        {
            census.lengths.add(length);
        }

        // NOT ONE OF ADAM'S LOCOMOTIVES, whose length this would otherwise have to change and change
        // back - and a length left behind on a real train is a change to his railway.  The room rule
        // reads exactly one thing off the locomotive, `getTrainLength`, so a throwaway that is not in
        // the database answers the question identically.
        MarklinLocomotive probe = new MarklinLocomotive(model, 1, MarklinLocomotive.decoderType.MM2,
            "Room census probe");

        List<Point> stations = new ArrayList<>();

        for (Point point : built.getPoints())
        {
            if (point.isDestination()) stations.add(point);
        }

        Collections.sort(stations, (a, b) -> a.getName().compareTo(b.getName()));

        for (Point from : stations)
        {
            for (Point to : stations)
            {
                if (from.equals(to)) continue;

                census.orderedPairs++;

                List<Edge> path = built.bfs(from, to, new LinkedList<List<Edge>>());

                if (path == null || path.isEmpty()) continue;

                census.pairsWithAPath++;

                for (Integer length : census.lengths)
                {
                    probe.setTrainLength(length);

                    if (!newlyRefuses(path, probe)) continue;

                    census.newlyRefused++;

                    Point ending = path.get(path.size() - 1).getEnd();

                    Integer room = Layout.measuredRoomAtTheBerth(path, probe);

                    census.berths.put(ending.getName(), room);
                }
            }
        }

        return census;
    }

    /**
     * Whether the widened rule refuses this journey and the fence it replaced would not have.
     *
     * The two halves are asked separately on purpose. `measuredRoomAtTheBerth` is the rule; the
     * terminus-or-reversing test is the fence `Layout.isPathClear`'s own comment records as having
     * stood in front of it before MT-262. A refusal at a terminus is not new.
     *
     * @param path the journey
     * @param loc the train, with its length already set
     * @return true when the widening is what refuses it
     */
    private static boolean newlyRefuses(List<Edge> path, Locomotive loc)
    {
        Point ending = path.get(path.size() - 1).getEnd();

        if (ending.isTerminus() || ending.isReversing()) return false;

        Integer room = Layout.measuredRoomAtTheBerth(path, loc);

        return room != null && loc.getTrainLength() != null && loc.getTrainLength() > room;
    }

    /**
     * The distinct train lengths recorded in the operator's locomotive database, shortest first.
     *
     * Section 5b's census is over "each of the six train lengths in his database in turn", which is
     * the set rather than the count - two locomotives of the same length ask the same question twice.
     *
     * @return the lengths, each once
     */
    private static List<Integer> trainLengths()
    {
        TreeSet<Integer> lengths = new TreeSet<>();

        for (Locomotive loc : model.getLocomotives())
        {
            Integer length = loc.getTrainLength();

            if (length != null && length > 0) lengths.add(length);
        }

        return new ArrayList<>(lengths);
    }
}
