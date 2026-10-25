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
 * The section 5c census (`behaviour.md`) as something that can be run: across every ordered station pair and each of
 * six train lengths, the journeys the widened track-room rule (MT-262) NEWLY refuses, and where they arrive.
 *
 * A journey is newly refused when the room behind its destination is measured, is shorter than the train, and the
 * destination is neither a terminus nor a reversing point (the fence `Layout` used to have). The station-capacity rule
 * is not counted: it is asked separately in `whyTooLongForThisRoute`.
 *
 * The original probe was never committed, so the published totals cannot be reproduced; this file carries the figures
 * and the method so they can be re-derived. Every newly refused journey arrives at one of the berths in BERTHS, each
 * with ONE measured unit of room behind it.
 *
 * Runs on a `LayoutSandbox` copy of `test/layouts/live-snapshot`, with the three tight tiles set by this class, so
 * every figure is what a rule about room does on a railway with no room, not a survey of his track.
 *
 * @author Adam
 */
public class testTheRoomRuleCensusOnTheRealLayout
{
    /**
     * Pins `behaviour.md` section 5c, over the snapshot's 37 destination Points: if a number moves, the railway or the
     * rule has changed.
     */
    private static final int ORDERED_PAIRS = 1332;
    /**
     * Pairs with a route, and the newly refused journeys among them: `Layout.bfs` does not extend a route through a
     * terminus that is not its end (OB-229), and routes doubling back through another copy of the journey's own start
     * or end are not walked.
     */
    private static final int PAIRS_WITH_A_PATH = 956;
    private static final int NEWLY_REFUSED = 505;

    /**
     * The census's six lengths are this test's own, not a reading of Adam's database (which `init()` restores): his
     * edits must not move the set.
     */
    private static final Integer[] LENGTHS = {1, 2, 3, 4, 5, 6};

    /** Name of each census train, so a failure says why it is there. */
    private static final String CENSUS_NAME = "census %d-unit";

    /**
     * Address of the first census train; 51 because no other test class uses that band, so a leftover is identifiable.
     */
    private static final int FIRST_CENSUS_ADDRESS = 51;

    /**
     * Where every newly refused journey arrives, and the room measured behind each: the berths `behaviour.md` section
     * 5c names, by their split copies. BottomMainC is a compulsory turn that builds to a terminus, whose refusal is not
     * new.
     */
    private static final String[] BERTHS =
    {
        "BottomMainA (eastbound)", "BottomMainB (eastbound)", "BottomMainPost (northbound)"
    };

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout built;

    /** Opens a throwaway copy of the railway and builds the configuration it names. */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The frozen copy, not the railway he is operating: a clean checkout must measure the same railway.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

        // The census's own trains, deleted in tearDownClass: `init()` opens Adam's real locomotive database.
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

        // His measurements and station sizes come off first, so this stays an experiment on a railway with no room.
        session.clearEveryTileLength();
        session.clearEveryMaxTrainLength();

        // The three tight tiles are set here, not read from the fixture: a rule about room says nothing on a railway
        // with room to spare.
        //
        // 22,7 is the last unswitched square before BottomMainPost; the other two are the equivalent squares before
        // BottomMainB and BottomMainC.
        for (TileKey tight : new TileKey[] { new TileKey("1 - Main", 22, 7),
            new TileKey("1 - Main", 19, 12), new TileKey("1 - Main", 14, 13) })
        {
            session.setTileLength(tight, 1);
        }

        model.parseAuto(session.buildConfiguration());

        built = model.getAutoLayout();
    }

    /** Removes the census trains (each deletion on its own) and restores the layout preference. */
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
     * The census, printed and pinned.
     *
     * MUTATION, run: answering `false` from `newlyRefuses` unless the destination is a terminus or a reversing point
     * (the fence before MT-262) takes the count to 0 and empties the berth list.
     */
    @Test
    public void testTheCensusIsWhatSection5bSays() throws Exception
    {
        assertNotNull(built, "the configuration did not build, so nothing was measured");

        Census census = run();

        // Printed as well as asserted: re-measuring is the point.
        System.out.println("### section 5c census, re-measured");
        System.out.println("  ordered station pairs   : " + census.orderedPairs);
        System.out.println("  of those, with a path   : " + census.pairsWithAPath);
        System.out.println("  train lengths asked     : " + census.lengths);
        System.out.println("  journeys newly refused  : " + census.newlyRefused);
        System.out.println("  arriving at             : " + census.berths);

        // WHAT A REFRESH OF THE SNAPSHOT CHANGES (FR-136): the figures, all of them, in one sentence a failure ends with.
        String refresh = "  If live-snapshot was refreshed on purpose, set in this class ORDERED_PAIRS = "
            + census.orderedPairs + ", PAIRS_WITH_A_PATH = " + census.pairsWithAPath + ", NEWLY_REFUSED = "
            + census.newlyRefused + " and BERTHS = " + new TreeSet<>(census.berths.keySet())
            + ", and NEWLY_REFUSED in docs/reference/behaviour.md section 5c";

        assertEquals(census.lengths, java.util.Arrays.asList(LENGTHS),
            "the census ran over " + census.lengths + " rather than " + java.util.Arrays.asList(LENGTHS)
            + ".  These are this class's OWN trains now, not a reading of Adam's database, so this is"
            + " a broken fixture rather than a railway that has changed - and the count below would be"
            + " over a different set of journeys either way");

        assertEquals(census.orderedPairs, ORDERED_PAIRS,
            "this railway now has " + census.orderedPairs + " ordered station pairs on it rather than "
            + ORDERED_PAIRS + ", so every number below is over a different population." + refresh);

        assertEquals(census.pairsWithAPath, PAIRS_WITH_A_PATH,
            census.pairsWithAPath + " of those pairs can be routed, against " + PAIRS_WITH_A_PATH
            + " when this was measured.  A large drop is a railway that has come apart rather than a"
            + " rule that has changed." + refresh);

        assertEquals(census.newlyRefused, NEWLY_REFUSED,
            census.newlyRefused + " journeys are newly refused by the widened room rule, against "
            + NEWLY_REFUSED + " when behaviour.md section 5c was written.  That section states this"
            + " number and reasons from it - that the widening refuses the journeys Adam asked to have"
            + " refused, at the squares he was looking at, and not a wider set - so update it there"
            + " and here together, or the document is describing a railway that no longer exists." + refresh);

        assertEquals(new TreeSet<>(census.berths.keySet()),
            new TreeSet<>(java.util.Arrays.asList(BERTHS)),
            "the newly refused journeys now arrive at " + census.berths.keySet() + " rather than at "
            + java.util.Arrays.asList(BERTHS) + ".  This is the load-bearing half of section 5c: every"
            + " refusal the widening adds lands on one of the four berths Adam named himself, each"
            + " with ONE measured unit of room behind it.  A fifth berth appearing means the rule has"
            + " started refusing somewhere he was not looking at." + refresh);

        for (Map.Entry<String, Integer> berth : census.berths.entrySet())
        {
            assertEquals(berth.getValue(), Integer.valueOf(1),
                berth.getKey() + " now measures " + berth.getValue() + " units of room rather than the"
                + " ONE section 5c records - \"bottommainb, which has a length of 1 leading up to its"
                + " switch\"");
        }
    }

    /**
     * Journeys refused on the way though the berth has room: every route to it turns at a square too short. Banded,
     * because `bfs(from, to, exclude)` returns SOME route and the adjacency is keyed by identity hashes, so counts move
     * about two per cent between JVMs; the ceiling catches the pass-through check coming back.
     *
     * Zero on this railway: routes doubling back through a copy of their own start or end are no longer walked. This
     * census cannot catch the ruling being taken out (its loop is a replica of the rule);
     * `core.testATrainIsJudgedOnlyWhereItStops` and `core.testNonReversibleTrains` hold it, through the real door.
     */
    private static final int REFUSED_ON_THE_WAY_AT_LEAST = 0;

    /** The other end of the band; see `REFUSED_ON_THE_WAY_AT_LEAST`. */
    private static final int REFUSED_ON_THE_WAY_AT_MOST = 10;

    /**
     * Route-journeys refused at a square the train turns at (Adam's reversal bound), counted per ROUTE and so banded as
     * above. Zero on this railway: its one-way marks took away the turn-backs, so this census no longer reaches the
     * bound; `core.testNonReversibleTrains` and `core.testATrainIsJudgedOnlyWhereItStops` hold it.
     */
    private static final int AT_A_TURN_AT_LEAST = 0;

    /** The other end of that band; see `AT_A_TURN_AT_LEAST`. */
    private static final int AT_A_TURN_AT_MOST = 40;

    /**
     * Journeys refused at the destination, what the rule did before the ruling, over the snapshot's 956 routable pairs.
     */
    private static final int REFUSED_AT_THE_BERTH = 850;

    /**
     * The squares that refuse on the way: none, so any square appearing here is the rule refusing somewhere Adam was
     * not looking (see `REFUSED_ON_THE_WAY_AT_LEAST`).
     */
    private static final String[] ON_THE_WAY = { };

    /**
     * How many routes per destination the census looks at. "Is this train refused" is a question about ALL of them, so
     * a cap that binds is a wrong answer; `testNoPairHitTheRouteCap` says it never does.
     */
    private static final int ROUTE_CAP = 150;

    /**
     * What Adam's ruling that "will the train fit" applies everywhere on the way, not only at the destination, costs on
     * top of the census above (*"This should only apply if lengths are specified."*).
     *
     * A train is refused a destination when every route to it refuses (as `getPossiblePaths` does), so this asks every
     * route: one route per pair gave different counts from run to run. The track rule is asked directly, by prefix, not
     * through `whyTooLongForThisRoute`, whose station-capacity rule is a different rule.
     */
    @Test
    public void testWhatTheRouteWideRuleCostsOnTopOfIt() throws Exception
    {
        assertNotNull(built, "the configuration did not build, so nothing was measured");

        int pairs = 0;
        int refusedAtTheBerth = 0;
        int refusedOnTheWay = 0;

        // What the reversal bound costs on this railway: the berth beyond the turn and the track the train waits on
        // while turning.
        int refusedAtATurn = 0;

        // Counted per ROUTE, and a measure of the condition rather than the rule: refusals the walk would make with
        // only the start of the route to stop it.
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

                        // The production rule, asked as production asks it: the berth on the whole route behind it, a
                        // square on the way only where a switch bounds it.
                        String where = null;

                        Integer room = null;

                        for (int i = 0; i < route.size(); i++)
                        {
                            boolean last = i == route.size() - 1;

                            List<Edge> prefix = route.subList(0, i + 1);

                            // A square the train turns at is judged like a berth: `whyTooLongForThisRoute` asks
                            // `comesToRest`.
                            boolean comesToRest = last || route.get(i).getEnd().isReversing();

                            // A square it only passes is not asked at all (MT-333), as in production.
                            if (!comesToRest) continue;

                            Integer here = Layout.measuredRoomAtTheEndOf(prefix, train);

                            if (here == null || train.getTrainLength() <= here) continue;

                            if (!last) refusedAtATurn++;

                            where = route.get(i).getEnd().getName();

                            room = here;

                            break;
                        }

                        if (where == null) someRouteAdmits = true;
                        else refusedBy.put(where, room);

                        // What the rule would cost without the switch condition: a prefix reaching back to the start of
                        // the route measures nothing, so counting the difference shows the condition is load-bearing.
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

                    // Every square that refused, over every route, not the first the search reached: that is the list
                    // to measure.
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
        System.out.println("  refusals at a square the train turns at: " + refusedAtATurn);

        // Asserted, not only printed: `behaviour.md` section 5a says every refusal this ruling adds is bounded by a
        // switch.
        //
        // This measures the railway, not the guard: the loop is a replica of `whyTooLongForThisRoute`, so removing
        // `comesToRest` from production leaves these numbers where they are and `core.testNonReversibleTrains` is what
        // fails. A band, for the reason `REFUSED_ON_THE_WAY_AT_LEAST` gives: the floor catches the bound being taken
        // out, the ceiling its refusing everything.
        assertTrue(refusedAtATurn >= AT_A_TURN_AT_LEAST && refusedAtATurn <= AT_A_TURN_AT_MOST,
            refusedAtATurn + " route-journeys are refused at a square the train turns at, outside the"
            + " measured band of " + AT_A_TURN_AT_LEAST + " to " + AT_A_TURN_AT_MOST + ". Below it,"
            + " the bound Adam ruled on 2026-09-11 has stopped reaching this railway; above it, it is"
            + " refusing journeys it was not asked to");

        assertEquals(offTheEndOfTheRoute, 0,
            offTheEndOfTheRoute + " of the refusals on the way would come from the walk running off"
            + " the start of the route rather than from a switch behind the square. behaviour.md"
            + " section 5a states that none of them do on this railway, which is what makes the"
            + " condition the pass-through walk carried until 2026-09-14 free here - so the railway has changed"
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

        // A subset, not an equality, as the count is a band; a square outside the list is the rule refusing somewhere
        // Adam was not looking.
        Set<String> unexpected = new TreeSet<>(squares.keySet());

        unexpected.removeAll(java.util.Arrays.asList(ON_THE_WAY));

        assertEquals(unexpected, new TreeSet<String>(),
            "the ruling now refuses trains at " + unexpected + ", which is not among the squares it"
            + " was measured against.  This is the load-bearing half: it was made about ONE tile -"
            + " 22,7, measuring BottomMainPost - and every square it costs on his railway is one he"
            + " has measured at a unit or three");

        // The list and the count agree whichever way round: a square refusing while the list is empty is the rule
        // reaching somewhere unmeasured, and a list entry that refuses nothing describes a moved railway. What holds
        // the rule is `core.testATrainIsJudgedOnlyWhereItStops` and `core.testNonReversibleTrains`.
        assertEquals(squares.isEmpty(), ON_THE_WAY.length == 0,
            "the squares refusing on the way (" + squares.keySet() + ") and the pinned list "
            + java.util.Arrays.asList(ON_THE_WAY) + " disagree about whether any square refuses at all.  One of the two"
            + " has moved: re-measure both, and behaviour.md section 5c with them");

        for (Map.Entry<String, Integer> square : squares.entrySet())
        {
            assertTrue(square.getValue() != null && square.getValue() <= 3,
                square.getKey() + " refuses a train with " + square.getValue() + " units of room,"
                + " which is not the tight track this ruling was made about - so the rule is refusing"
                + " on roomy track and something else has changed");
        }
    }

    /**
     * Whether a switch lies in the route up to this edge, so a refusal is the rule and not a prefix with nothing behind
     * it.
     */
    private static boolean crossesASwitch(List<Edge> route, int upTo)
    {
        for (int i = 0; i <= upTo && i < route.size(); i++)
        {
            if (route.get(i).crossesASwitch()) return true;
        }

        return false;
    }

    /** The route cap never binds, so the census is over all routes (a binding cap is a silent under-count). */
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

    /** Every route between two squares, in search order; empty when there is none. */
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

    /** The destinations the census runs over, in name order. */
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

    /** What one run of the census found. */
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
     * Walks every ordered station pair and every train length; the path is searched once per pair, as it does not
     * depend on length.
     */
    private static Census run() throws Exception
    {
        Census census = new Census();

        // The census's own trains, in length order, not whatever his database holds.
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

    /** Whether the widened rule refuses this journey and the old terminus-or-reversing fence would not. */
    private static boolean newlyRefuses(List<Edge> path, Locomotive loc)
    {
        Point ending = path.get(path.size() - 1).getEnd();

        if (ending.isTerminus() || ending.isReversing()) return false;

        Integer room = Layout.measuredRoomAtTheEndOf(path, loc);

        return room != null && loc.getTrainLength() != null && loc.getTrainLength() > room;
    }

    /**
     * The six trains `setUpClass` made, shortest first, looked up by name so a missing one fails loudly instead of
     * shortening the census.
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
