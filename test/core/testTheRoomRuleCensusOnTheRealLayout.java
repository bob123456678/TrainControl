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

    /**
     * The lengths this census is over - and they are THIS TEST'S, not a reading of Adam's database.
     *
     * Adam, 2026-09-09: *"for the pinned train lengths - yes, generate trains programmatically in the
     * tests, and give them semantic names."*
     *
     * They used to be harvested: `trainLengths()` walked `model.getLocomotives()` - the real
     * locomotive database, which `init()` restores and which the layout sandbox does NOT freeze - and
     * collected the distinct positive lengths it found. Six of them, 1 to 6, which is what section 5b
     * says. **The census then had a population nobody in this file chose.** Adam measuring one of his
     * own trains, or buying one, or driving a length back to zero, moves the set and this class goes
     * red about a railway that has not changed. The reverse is worse: a length he happens to add can
     * change `newlyRefused` without anything here saying why.
     *
     * So the six trains are made here, with the lengths written down, and deleted again in
     * `tearDownClass`. The numbers below are unchanged, because these are the same six lengths - which
     * is the point: the census is over the same population it was over when section 5b was written,
     * and now it will stay over it.
     */
    private static final Integer[] LENGTHS = {1, 2, 3, 4, 5, 6};

    /**
     * What each census train is called, and it says what it is for.
     *
     * A name like "census 3-unit" says the whole of it: this train exists so that the census can ask
     * the room rule about three units. `newlyRefuses` reads exactly one thing off a locomotive -
     * `getTrainLength` - so the name is for whoever is reading a failure, and a borrowed engine's
     * name told them nothing about why it was in the census.
     */
    private static final String CENSUS_NAME = "census %d-unit";

    /**
     * The address the first census train takes; the others follow it.
     *
     * Any address will do - locomotive addresses are not unique in this database and several test
     * classes already share one - and 51 is chosen only because no other test class uses that band,
     * so a stray one left behind by a crash is identifiable.
     */
    private static final int FIRST_CENSUS_ADDRESS = 51;

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

        // THE CENSUS'S OWN TRAINS, before anything is measured.
        //
        // Deleted in tearDownClass. `init()` opens Adam's real locomotive database rather than an
        // empty one, so a train left behind here is a train on his railway.
        for (Integer units : LENGTHS)
        {
            MarklinLocomotive train = model.newMM2Locomotive(
                String.format(CENSUS_NAME, units), FIRST_CENSUS_ADDRESS + units);

            assertNotNull(train, "the census could not create its " + units + "-unit train, so the"
                + " population below would be short one length and every count wrong");

            train.setTrainLength(units);
        }

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
     * Takes the census's trains off Adam's railway, and puts the layout preference back.
     *
     * `alwaysRun`, and each deletion on its own, so that a failure part way through the six still
     * removes the other five. A locomotive left in his database is the exact harm this migration was
     * asked for.
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null)
        {
            for (Integer units : LENGTHS)
            {
                try
                {
                    model.deleteLoc(String.format(CENSUS_NAME, units));
                }
                catch (Exception alreadyGone)
                {
                }
            }
        }

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
            "the census ran over " + census.lengths + " rather than " + java.util.Arrays.asList(LENGTHS)
            + ".  These are this class's OWN trains now, not a reading of Adam's database, so this is"
            + " a broken fixture rather than a railway that has changed - and the count below would be"
            + " over a different set of journeys either way");

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

        // THE CENSUS'S OWN TRAINS, in length order.
        //
        // This used to build the list out of `model.getLocomotives()` and drive a throwaway
        // `MarklinLocomotive` round the loop with its length reset each time. The throwaway was right
        // about the hazard it named - "a length left behind on a real train is a change to his
        // railway" - and it only covered half of it: the LENGTHS were still his, so the census was
        // over whatever set his database happened to hold. Now the trains are ours and so is the set.
        List<Locomotive> trains = censusTrains();

        for (Locomotive train : trains)
        {
            census.lengths.add(train.getTrainLength());
        }

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

                for (Locomotive train : trains)
                {
                    if (!newlyRefuses(path, train)) continue;

                    census.newlyRefused++;

                    Point ending = path.get(path.size() - 1).getEnd();

                    Integer room = Layout.measuredRoomAtTheBerth(path, train);

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
     * The six trains `setUpClass` made, shortest first.
     *
     * Looked up by name rather than held in a field, so that a train that failed to be created, or was
     * removed by something else during the run, is a loud failure here rather than a silently shorter
     * census. That is the shape the old harvester could not have: reading the database gave whatever
     * was in it, and "whatever was in it" has no wrong answer to notice.
     *
     * @return the census trains
     */
    private static List<Locomotive> censusTrains()
    {
        List<Locomotive> trains = new ArrayList<>();

        for (Integer units : new TreeSet<>(java.util.Arrays.asList(LENGTHS)))
        {
            Locomotive train = model.getLocByName(String.format(CENSUS_NAME, units));

            assertNotNull(train, "the census's " + units + "-unit train is not in the database, so"
                + " this run is over a shorter population than the one section 5b states");

            assertEquals(train.getTrainLength(), units,
                "the census's " + units + "-unit train measures " + train.getTrainLength()
                + " units, so the population is not the one the counts below were measured over");

            trains.add(train);
        }

        return trains;
    }
}
