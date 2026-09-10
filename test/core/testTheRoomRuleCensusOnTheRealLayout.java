package core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.file.CS2File;

/**
 * The section 5c census, as something that can be run rather than something that was run once.
 *
 * `behaviour.md` section 5c records a measurement made on the operator's own railway on 2026-09-08,
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
 * platform, it is asked separately in `whyTooLongForThisRoute`, and MT-262 did not touch it.
 *
 * **It does not reproduce the published totals, and it cannot.** Section 5c said *"across all 3488
 * ordered station pairs ... the journeys it newly refuses are 332"*. This finds **1980** ordered pairs
 * - 45 destination Points, each against the other 44 - of which **1848** can be routed, and **880**
 * newly refused journeys over the six lengths. The original probe was never committed, so which pairs
 * it enumerated cannot be recovered, and that is the whole reason this file exists: a number nobody
 * can re-derive is a number nobody can check. Section 5c now carries these figures and states the
 * method, so the next person can disagree with it by running something.
 *
 * **What DID reproduce, exactly, is the half the section reasons from.** Every newly refused journey
 * arrives at one of four berths - `BottomMainA (eastbound)`, `BottomMainB (eastbound)`,
 * `BottomMainC (westbound)`, `BottomMainPost (northbound)` - each with ONE measured unit of room
 * behind it. Those are the four the section names, in the order it names them, with the number Adam
 * gave. So the conclusion stands on its own measurement: the widening refuses the journeys he asked to
 * have refused, at the squares he was looking at, and nowhere else on this railway.
 *
 * Read against a `LayoutSandbox` copy of `test/layouts/live-snapshot` - the frozen railway rather
 * than the one Adam is operating - with the three tight tiles set by this class rather than
 * carried in the fixture.  Adam, 2026-09-10: *"We put the lengths of 1 in there for testing.
 * Actual tracks are much longer."*  So every figure here is what a rule about room does on a
 * railway with no room, and not a survey of his track.
 *
 * @author Adam
 */
public class testTheRoomRuleCensusOnTheRealLayout
{
    /**
     * The census, re-measured on 2026-09-08 by this file.
     *
     * These pin the sentences in `behaviour.md` section 5c. If a number here moves, the railway or the
     * rule has changed and that section is out of date - which is exactly the state section 5c was in
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
     * collected the distinct positive lengths it found. Six of them, 1 to 6, which is what section 5c
     * says. **The census then had a population nobody in this file chose.** Adam measuring one of his
     * own trains, or buying one, or driving a length back to zero, moves the set and this class goes
     * red about a railway that has not changed. The reverse is worse: a length he happens to add can
     * change `newlyRefused` without anything here saying why.
     *
     * So the six trains are made here, with the lengths written down, and deleted again in
     * `tearDownClass`. The numbers below are unchanged, because these are the same six lengths - which
     * is the point: the census is over the same population it was over when section 5c was written,
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
     * These are the four berths `behaviour.md` section 5c names, spelled as it spells them - by their
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
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (the review's B8).
        //
        // This opened `cs2_sample_layout`, and every figure below is a measurement of whatever state
        // Adam had left it in.  One of the squares this class pins - `TopR1ParkShort` at three units -
        // existed only in his UNCOMMITTED working copy, so a clean checkout ran the census against a
        // railway where that square is unmeasured and the pinned list quietly stopped being about the
        // repository.
        //
        // `live-snapshot` is the same railway with the clock stopped, checked in, and the last of the
        // classes that were reading his live one.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

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


        // THE THREE TIGHT TILES, SET HERE RATHER THAN READ OUT OF THE FIXTURE (Adam, 2026-09-10).
        //
        // `live-snapshot` carried these three at one unit and nothing else at all, so every figure
        // below was really a measurement of a railway with three one-unit tiles on it - which is what
        // Adam had set them to for testing.  **"A/B/C are distinct pieces of track.  We put the lengths
        // of 1 in there for testing.  Actual tracks are much longer."**
        //
        // So the fixture carries no lengths now and this census states its own configuration: the
        // tight case, deliberately, because a rule about room says nothing on a railway with room to
        // spare.  It is not a claim about what his track measures.
        //
        // 22,7 is the last unswitched square before BottomMainPost; the other two are the equivalent
        // squares before BottomMainB and BottomMainC.  Together they are the four berths the MT-262
        // census named.
        for (TileKey tight : new TileKey[] { new TileKey("1 - Main", 22, 7),
            new TileKey("1 - Main", 19, 12), new TileKey("1 - Main", 14, 13) })
        {
            session.setTileLength(tight, 1);
        }

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
     * too, which is what section 5c's closing sentence asks for: *"a layout with lengths scattered
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
        System.out.println("### section 5c census, re-measured");
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
            + NEWLY_REFUSED + " when behaviour.md section 5c was written.  That section states this"
            + " number and reasons from it - that the widening refuses the journeys Adam asked to have"
            + " refused, at the squares he was looking at, and not a wider set - so update it there"
            + " and here together, or the document is describing a railway that no longer exists");

        assertEquals(new TreeSet<>(census.berths.keySet()),
            new TreeSet<>(java.util.Arrays.asList(BERTHS)),
            "the newly refused journeys now arrive at " + census.berths.keySet() + " rather than at "
            + java.util.Arrays.asList(BERTHS) + ".  This is the load-bearing half of section 5c: every"
            + " refusal the widening adds lands on one of the four berths Adam named himself, each"
            + " with ONE measured unit of room behind it.  A fifth berth appearing means the rule has"
            + " started refusing somewhere he was not looking at");

        for (Map.Entry<String, Integer> berth : census.berths.entrySet())
        {
            assertEquals(berth.getValue(), Integer.valueOf(1),
                berth.getKey() + " now measures " + berth.getValue() + " units of room rather than the"
                + " ONE section 5c records - \"bottommainb, which has a length of 1 leading up to its"
                + " switch\"");
        }
    }

    /**
     * Journeys Adam's ruling of 2026-09-09 refuses that the berth-only rule admitted.
     *
     * Measured on `test/layouts/live-snapshot` over 1848 routable pairs and this class's six trains -
     * **11088 journeys, of which the berth rule already refuses 1760 and this ruling refuses about
     * 1655 more**.  Roughly one journey in three is now refused for want of room, and **every square
     * that does the refusing measures ONE unit**.  The way to get those journeys back is to measure
     * that track, not to change the rule.
     *
     * **A BAND, BECAUSE THE EXACT COUNT IS NOT REPRODUCIBLE.**  Six runs on Adam's live railway gave
     * 2116, 2146, 1648, 1617, 1625 and 1645 - the first two asking one route per pair, the rest asking
     * every route the search will yield - and the snapshot answers in the same neighbourhood, 1640 to
     * 1660.  `bfs(from, to, exclude)` returns SOME route avoiding the ones already found rather
     * than the next in a defined order, and which one depends on an adjacency keyed by objects whose
     * hash is their identity.  So the count moves by about two per cent between JVMs, and a pin to one
     * value would be a flake with a comment on it.
     *
     * The band is wide enough to hold that and narrow enough to fail on a change of rule: the ruling
     * doubling its reach, or being quietly taken out, moves the number by hundreds.  The exact figure
     * is printed on every run.
     */
    private static final int REFUSED_ON_THE_WAY_AT_LEAST = 1450;

    /**
     * The other end of the band.  See `REFUSED_ON_THE_WAY_AT_LEAST` for why there is one.
     */
    private static final int REFUSED_ON_THE_WAY_AT_MOST = 1800;

    /**
     * Journeys refused at the destination, which is what the rule did before the ruling.
     *
     * 1892 while this census read Adam's live railway; 1760 on the frozen snapshot, which is the
     * railway the repository actually holds.
     */
    private static final int REFUSED_AT_THE_BERTH = 1760;

    /**
     * The squares that do the refusing on the way, which is the half worth reading.
     *
     * **Every one of them measures ONE unit**, and every one is a copy of the four berths the MT-262
     * census already names - the same tiles, now refusing a train running THROUGH them as well as one
     * stopping at them.  So the cost of the ruling lands exactly where Adam was looking when he made
     * it, and the way to get those journeys back is to measure that track rather than change the rule.
     *
     * `TopR1ParkShort` was on this list until the census moved off his live railway.  It measures
     * three units in his UNCOMMITTED working copy and nothing at all in the repository - a pinned list
     * that was really a photograph of somebody's desk, which is the whole of the review's B8.
     */
    private static final String[] ON_THE_WAY =
    {
        "BottomMainA (eastbound)", "BottomMainA (westbound)", "BottomMainB (eastbound)",
        "BottomMainB (eastbound, reverse)", "BottomMainB (westbound)",
        "BottomMainB (westbound, reverse)", "BottomMainBCPre (westbound)", "BottomMainC (westbound)",
        "BottomMainC (westbound, reverse)", "BottomMainPost (northbound)",
        "BottomMainPost (northbound, reverse)"
    };

    /**
     * How many routes to one destination the census will look at.
     *
     * The point of enumerating them at all is that "is this train refused" is a question about ALL of
     * them, so a cap is a wrong answer rather than a slow one.  It was 30, and a full battery - a
     * different JVM, so a different route order - found BottomMainC (westbound) to BottomSecondary
     * at the cap, which is the guard below doing exactly its job - it can only make the census report a
     * refusal that the railway would not make.  `testNoPairHitTheRouteCap` says it never binds, which
     * is the only thing that makes the number below mean anything.
     */
    private static final int ROUTE_CAP = 150;

    /**
     * What Adam's ruling of 2026-09-09 costs on top of the census above.
     *
     * He was asked whether "will the train fit" means at the destination or everywhere on the way,
     * with the cost of the second stated - it refuses through moves that a berth-only rule allows -
     * and answered **"For 1, it's b.  This should only apply if lengths are specified."**  This is
     * that cost, over the same population as the census above.
     *
     * **A train is refused a destination when every route to it refuses**, which is what
     * `getPossiblePaths` does, so the census asks it over every route rather than over the one a
     * search finds first.  That is not tidiness: one route per pair gave 2116 on one run and 2146 on
     * the next, because the adjacency is keyed by objects whose hash is their identity.
     *
     * The track rule is asked directly, by prefix, rather than through `whyTooLongForThisRoute` -
     * that door also asks the station's stated capacity, which is a different rule and would be
     * counted here as though the ruling had caused it.
     *
     * @throws Exception on a failure to search
     */
    @Test
    public void testWhatTheRouteWideRuleCostsOnTopOfIt() throws Exception
    {
        assertNotNull(built, "the configuration did not build, so nothing was measured");

        int pairs = 0;
        int refusedAtTheBerth = 0;
        int refusedOnTheWay = 0;

        // Counted per ROUTE rather than per journey, and it is a measure of the CONDITION rather
        // than of the rule: how many refusals the walk would make with nothing but the start of the
        // route to stop it.  See the loop that raises it.
        int offTheEndOfTheRoute = 0;

        Map<String, Integer> squares = new java.util.TreeMap<>();

        List<Locomotive> trains = censusTrains();

        for (Point from : censusStations())
        {
            for (Point to : censusStations())
            {
                if (from.equals(to)) continue;

                List<List<Edge>> routes = everyRoute(from, to);

                if (routes.isEmpty()) continue;

                pairs++;

                for (Locomotive train : trains)
                {
                    boolean someBerthAdmits = false;
                    boolean someRouteAdmits = false;

                    Map<String, Integer> refusedBy = new java.util.TreeMap<>();

                    for (List<Edge> route : routes)
                    {
                        Integer atTheBerth = Layout.measuredRoomAtTheEndOf(route, train);

                        if (atTheBerth == null || train.getTrainLength() <= atTheBerth)
                        {
                            someBerthAdmits = true;
                        }

                        // THE PRODUCTION RULE, asked the way production asks it: the berth on the
                        // whole route behind it, a square on the way only where a switch bounds it.
                        String where = null;

                        Integer room = null;

                        for (int i = 0; i < route.size(); i++)
                        {
                            boolean last = i == route.size() - 1;

                            List<Edge> prefix = route.subList(0, i + 1);

                            Integer here = last ? Layout.measuredRoomAtTheEndOf(route, train)
                                : Layout.roomAfterASwitchOnTheWay(prefix, train);

                            if (here == null || train.getTrainLength() <= here) continue;

                            where = route.get(i).getEnd().getName();

                            room = here;

                            break;
                        }

                        if (where == null) someRouteAdmits = true;
                        else refusedBy.put(where, room);

                        // AND WHAT THE RULE WOULD HAVE COST WITHOUT THAT CONDITION.
                        //
                        // The walk stops at the last switch, which is Adam's rule - or at the start
                        // of the route, which measures nothing: a two-edge prefix answers "two edges
                        // of room" when the track behind where the train started has not been looked
                        // at.  The first cut of ruling 1b had no such condition and refused a
                        // four-unit train four units of room.  This counts the difference, so the
                        // condition can be seen to be load-bearing rather than believed to be.
                        for (int i = 0; i < route.size() - 1; i++)
                        {
                            Integer loose =
                                Layout.measuredRoomAtTheEndOf(route.subList(0, i + 1), train);

                            if (loose == null || train.getTrainLength() <= loose) continue;

                            if (!crossesASwitch(route, i)) offTheEndOfTheRoute++;

                            break;
                        }
                    }

                    if (!someBerthAdmits)
                    {
                        refusedAtTheBerth++;

                        continue;
                    }

                    if (someRouteAdmits) continue;

                    refusedOnTheWay++;

                    // EVERY square that refused, over every route, rather than the first one a
                    // search happened to reach: which route comes out first is not stable, and this
                    // is the list the operator would go and measure.
                    squares.putAll(refusedBy);
                }
            }
        }

        System.out.println("### what ruling 1b costs, re-measured");
        System.out.println("  routable pairs             : " + pairs);
        System.out.println("  journeys asked             : " + (pairs * trains.size()));
        System.out.println("  refused at the berth       : " + refusedAtTheBerth);
        System.out.println("  refused only on the way    : " + refusedOnTheWay);
        System.out.println("  squares that refuse, by name: " + squares);
        System.out.println("  refusals the unbounded walk would have added: "
            + offTheEndOfTheRoute);

        // ASSERTED, NOT ONLY PRINTED (the review's C2).  `behaviour.md` section 5a says every refusal
        // this ruling adds is bounded by a switch, and leans on this census for it - a claim a
        // document makes and nothing checks is a claim that goes stale the day it stops being true.
        // WHAT THIS MEASURES IS THE RAILWAY, NOT THE GUARD, and the message used to say otherwise.
        //
        // The counter is built from `measuredRoomAtTheEndOf` and this class's own `crossesASwitch`;
        // it never calls `roomAfterASwitchOnTheWay`, so weakening that condition cannot move it.  What
        // it says is that on THIS railway every refusal the ruling adds has a switch behind it - which
        // is the sentence behaviour.md section 5a leans on, and the reason the condition costs nothing
        // here.
        assertEquals(offTheEndOfTheRoute, 0,
            offTheEndOfTheRoute + " of the refusals on the way would come from the walk running off"
            + " the start of the route rather than from a switch behind the square. behaviour.md"
            + " section 5a states that none of them do on this railway, which is what makes the"
            + " condition in `Layout.roomAfterASwitchOnTheWay` free here - so the railway has changed"
            + " shape, and that sentence needs re-measuring");

        assertEquals(refusedAtTheBerth, REFUSED_AT_THE_BERTH,
            refusedAtTheBerth + " journeys are refused at their destination, against "
            + REFUSED_AT_THE_BERTH + " when this was measured.  That is the rule as it stood BEFORE"
            + " the ruling, so a change here is a change to the population the next number is a"
            + " fraction of");

        assertTrue(refusedOnTheWay >= REFUSED_ON_THE_WAY_AT_LEAST
            && refusedOnTheWay <= REFUSED_ON_THE_WAY_AT_MOST,
            refusedOnTheWay + " journeys are refused by Adam's ruling of 2026-09-09 and would have"
            + " been admitted by the berth-only rule, which is outside the "
            + REFUSED_ON_THE_WAY_AT_LEAST + " to " + REFUSED_ON_THE_WAY_AT_MOST + " measured when it"
            + " was made.  A rise is the guard closing the railway down, which is the cost he was told"
            + " about and accepted a measured amount of; a fall is the ruling not being enforced."
            + "  Either way, re-measure it here and in behaviour.md section 5c together");

        // A SUBSET, NOT AN EQUALITY, and for the same reason the count is a band: which routes the
        // search yields decides which of a station's copies gets named.  A square OUTSIDE the list is
        // the rule refusing somewhere Adam was not looking, and that is what this has to catch.
        Set<String> unexpected = new TreeSet<>(squares.keySet());

        unexpected.removeAll(java.util.Arrays.asList(ON_THE_WAY));

        assertEquals(unexpected, new TreeSet<String>(),
            "the ruling now refuses trains at " + unexpected + ", which is not among the squares it"
            + " was measured against.  This is the load-bearing half: it was made about ONE tile -"
            + " 22,7, measuring BottomMainPost - and every square it costs on his railway is one he"
            + " has measured at a unit or three");

        assertTrue(squares.size() >= ON_THE_WAY.length - 2,
            "only " + squares.size() + " of the " + ON_THE_WAY.length + " squares that used to refuse"
            + " on the way still do (" + squares.keySet() + "), so the ruling has stopped being"
            + " enforced over most of the railway it was measured on");

        for (Map.Entry<String, Integer> square : squares.entrySet())
        {
            assertTrue(square.getValue() != null && square.getValue() <= 3,
                square.getKey() + " refuses a train with " + square.getValue() + " units of room,"
                + " which is not the tight track this ruling was made about - so the rule is refusing"
                + " on roomy track and something else has changed");
        }
    }

    /**
     * Whether any edge up to and including this one crosses a switch.
     *
     * The walk that decides a refusal stops at the last switch OR at the start of the route.  Only the
     * first of those is a rule anybody made; the second is a prefix having nothing behind it yet, and
     * a refusal that comes from it is the guard being strict about track it never looked at.
     *
     * @param route the whole route
     * @param upTo the index the refusal was found at
     * @return true when a switch bounds the stretch, so the refusal is the rule
     */
    private static boolean crossesASwitch(List<Edge> route, int upTo)
    {
        for (int i = 0; i <= upTo && i < route.size(); i++)
        {
            if (route.get(i).crossesASwitch()) return true;
        }

        return false;
    }

    /**
     * The cap on routes per pair never binds, so the census is over all of them.
     *
     * Asserted separately because a cap that binds turns the census from a measurement into an
     * under-count, and silently: a pair whose thirty-first route was the one that fitted is reported
     * as refused.  Adam's standing rule on this is that a bounded sweep says what it dropped.
     *
     * @throws Exception on a failure to search
     */
    @Test
    public void testNoPairHitTheRouteCap() throws Exception
    {
        assertNotNull(built, "the configuration did not build, so nothing was measured");

        List<String> atTheCap = new ArrayList<>();

        for (Point from : censusStations())
        {
            for (Point to : censusStations())
            {
                if (from.equals(to)) continue;

                if (everyRoute(from, to).size() >= ROUTE_CAP)
                {
                    atTheCap.add(from.getName() + " -> " + to.getName());
                }
            }
        }

        assertEquals(atTheCap, new ArrayList<String>(),
            atTheCap.size() + " station pairs have at least " + ROUTE_CAP + " routes between them, so"
            + " the census stopped looking and may be reporting refusals the railway would not make: "
            + atTheCap);
    }

    /**
     * Every route between two squares, in the order the search finds them.
     *
     * @param from where the train stands
     * @param to where it is being sent
     * @return the routes, empty when there is none
     * @throws Exception on a failure to search
     */
    private static List<List<Edge>> everyRoute(Point from, Point to) throws Exception
    {
        List<List<Edge>> seen = new LinkedList<>();

        while (seen.size() < ROUTE_CAP)
        {
            List<Edge> path = built.bfs(from, to, seen);

            if (path == null || path.isEmpty()) break;

            seen.add(path);
        }

        return seen;
    }

    /**
     * The destinations the census runs over, in name order.
     *
     * @return the stations
     */
    private static List<Point> censusStations()
    {
        List<Point> stations = new ArrayList<>();

        for (Point point : built.getPoints())
        {
            if (point.isDestination()) stations.add(point);
        }

        Collections.sort(stations, (a, b) -> a.getName().compareTo(b.getName()));

        return stations;
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

                    Integer room = Layout.measuredRoomAtTheEndOf(path, train);

                    census.berths.put(ending.getName(), room);
                }
            }
        }

        return census;
    }

    /**
     * Whether the widened rule refuses this journey and the fence it replaced would not have.
     *
     * The two halves are asked separately on purpose. `measuredRoomAtTheEndOf` is the rule; the
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

        Integer room = Layout.measuredRoomAtTheEndOf(path, loc);

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
                + " this run is over a shorter population than the one section 5c states");

            assertEquals(train.getTrainLength(), units,
                "the census's " + units + "-unit train measures " + train.getTrainLength()
                + " units, so the population is not the one the counts below were measured over");

            trains.add(train);
        }

        return trains;
    }
}
