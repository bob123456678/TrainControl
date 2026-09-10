package core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * The route-wide room rule leaves no train with nowhere to go.
 *
 * **The census counts JOURNEYS, and that is not the number that decides whether the railway runs.**
 * `core.testTheRoomRuleCensusOnTheRealLayout` measures Adam's ruling of 2026-09-09 at about one
 * journey in three refused for want of room, and that is the figure he was given and accepted.  A
 * third of journeys refused EVENLY leaves nobody stranded.  A third refused because three tiles
 * measured at one unit sit on every road out of the bottom main strands every train standing there,
 * and the two are the same number.
 *
 * So this asks the door the operator asks - `Layout.getPossiblePaths(loc, true)` - from every square
 * a train can stand on, for each of the census's six lengths, on a railway with nothing else on it.
 *
 * **Measured on 2026-09-10, and both claims below are red.**  A one-unit train is offered somewhere
 * to go from all 45 squares.  A train of TWO units or more is offered nothing at all from seven of
 * them - `BottomMainA (eastbound)`, `BottomMainB (eastbound)`, `BottomMainB (eastbound, reverse)`,
 * `BottomMainC (eastbound)`, `BottomMainC (westbound, reverse)`, `BottomMainPost (northbound,
 * reverse)` and `BottomMainPost (southbound)` - each of which offered that same train between 31 and
 * 34 destinations under the berth-only rule, over the same routes.  Not fewer: none.
 *
 * A train with no destination is a train autonomy will never dispatch and a Return Home that reports
 * `NO_PLAN_FOUND`.  Whether that is the right railway is Adam's to say - the remedy is to measure the
 * track, which `behaviour.md` section 5a already states - but it is a different fact from "one
 * journey in three", and it is the one an operator meets first.
 *
 * **`ParkingTrack12` used to be on this list and is not on it.**  It appeared at every length while
 * this class emptied the railway once instead of once per pass, so each length after the first ran
 * with the previous pass's train still standing somewhere.  Seven squares, not eight; 35 pairs, not
 * 40.  A review found it after both wrong numbers had been published.
 *
 * Read against a `LayoutSandbox` copy of `test/layouts/live-snapshot` - the frozen railway, not the
 * one Adam is operating - with the three tight tiles set by this class rather than carried in the
 * fixture.  Adam, 2026-09-10: *"We put the lengths of 1 in there for testing.  Actual tracks are much
 * longer."*  So these figures are what a rule about room does on a railway with no room, which is what
 * makes them worth measuring, and are not a survey of his track.
 *
 * @author Adam
 */
public class testWhichSquaresTheRoomRuleClosesOff
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout built;

    private static final Integer[] LENGTHS = {1, 2, 3, 4, 5, 6};

    private static final String NAME = "stranding probe %d-unit";

    private static final int FIRST_ADDRESS = 81;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and the review's B8).
        //
        // Every figure below is a measurement of the lengths on the railway it opens, and his live
        // one moves as he works.  `live-snapshot` is the same railway with the clock stopped.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, false);

        for (Integer units : LENGTHS)
        {
            MarklinLocomotive train = model.newMM2Locomotive(
                String.format(NAME, units), FIRST_ADDRESS + units);

            assertNotNull(train, "could not create the " + units + "-unit probe train");

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

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null)
        {
            for (Integer units : LENGTHS)
            {
                try
                {
                    model.deleteLoc(String.format(NAME, units));
                }
                catch (Exception alreadyGone)
                {
                }
            }
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * (square, length) pairs that had somewhere to go before the ruling and have nowhere after it.
     *
     * Measured on `test/layouts/live-snapshot` with the three tight tiles this class sets itself.
     * **A pin, not an endorsement**: it says nobody changes the number without saying so.
     */
    private static final int CLOSED_BY_THE_RULING = 35;

    /**
     * How many routes between one pair the search will look at.
     *
     * "This square offers the train nothing" is a claim about EVERY route out of it, so a cap that
     * binds turns it into a claim about the first few - and in the direction that matters here, which
     * is reporting a square closed that is not.
     *
     * 30 until a full battery found a pair at exactly that in the sibling census: a different JVM
     * yields routes in a different order, so a cap that never binds in one run can bind in the next.
     * 150 leaves five times the deepest pair measured, and the whole census still runs in seconds.
     */
    private static final int ROUTE_CAP = 150;

    /**
     * (square, length) pairs that offer a train nothing at all where a one-unit train has somewhere
     * to go - so the square is closed by the room rule rather than by the shape of the track.
     *
     * **40 until 2026-09-10, and five of those forty were this class's own leftover train.**  The
     * railway was cleared once, before the sweep over lengths, and `whereItMayGo` lifts only the train
     * it is about to place - so from two units on, the previous pass's probe was still standing on the
     * last square it had been put on.  It made `ParkingTrack12` read as closed at every length, which
     * is where the eighth square in the published figures came from.  A review found it.
     */
    private static final int STRANDED = 35;

    @Test
    public void testHowManySquaresTheRulingClosedCompletely() throws Exception
    {
        assertNotNull(built, "the configuration did not build");

        List<Point> stations = new ArrayList<>();

        for (Point point : built.getPoints())
        {
            if (point.isDestination()) stations.add(point);
        }

        Map<String, String> closedByTheRuling = new TreeMap<>();

        for (Integer units : LENGTHS)
        {
            Locomotive train = model.getLocByName(String.format(NAME, units));

            for (Point from : stations)
            {
                int berthOnly = 0;
                int routeWide = 0;

                for (Point to : stations)
                {
                    if (from.equals(to)) continue;

                    boolean berthFits = false;
                    boolean routeFits = false;

                    List<List<Edge>> seen = new java.util.LinkedList<>();

                    while (seen.size() < ROUTE_CAP)
                    {
                        List<Edge> route = built.bfs(from, to, seen);

                        if (route == null || route.isEmpty()) break;

                        seen.add(route);

                        if (Layout.whyTooLongForThisRoute(route, train) == null) routeFits = true;

                        Integer atTheBerth = Layout.measuredRoomAtTheEndOf(route, train);

                        if (to.validateTrainLength(train)
                            && (atTheBerth == null || train.getTrainLength() <= atTheBerth))
                        {
                            berthFits = true;
                        }
                    }

                    if (berthFits) berthOnly++;
                    if (routeFits) routeWide++;
                }

                if (routeWide == 0 && berthOnly > 0)
                {
                    closedByTheRuling.put(units + "-unit at " + from.getName(),
                        berthOnly + " destinations before the ruling, 0 after");
                }
            }
        }

        System.out.println("### squares the ruling of 2026-09-09 closed completely");
        System.out.println("  " + closedByTheRuling);

        // PINNED, NOT REQUIRED TO BE ZERO.
        //
        // Whether this is the railway Adam wants is his to say - the remedy is a tape measure on three
        // tiles, not a change to the rule - and his standing rule is to price a trade rather than
        // block on it.  What must not happen is the number moving without anybody noticing.
        assertEquals(closedByTheRuling.size(), CLOSED_BY_THE_RULING,
            closedByTheRuling.size() + " (square, length) pairs had somewhere to go under the"
            + " berth-only rule and have nowhere under Adam's ruling of 2026-09-09, against the "
            + CLOSED_BY_THE_RULING + " measured when it was made. Over the same routes, so the"
            + " difference is the ruling and nothing else: " + closedByTheRuling);
    }

    /**
     * Which squares offer a train of each length nothing at all, on an empty railway.
     *
     * The number to read beside the journey census: a third of journeys refused evenly strands nobody,
     * and a third refused at the squares on every road out closes those squares to everybody standing
     * there.
     *
     * **Emptied at the top of every pass, and that is not a detail.**  It used to be emptied once, and
     * `whereItMayGo` lifts only the train it is about to place - so each length after the first ran
     * against a railway with the previous length's probe still standing on it, which reported an
     * eighth square closed that is not and put five phantom pairs into the pinned figure.
     *
     * @throws Exception on a failure to search
     */
    @Test
    public void testWhichSquaresOfferALongTrainNothingAtAll() throws Exception
    {
        assertNotNull(built, "the configuration did not build");

        emptyTheRailway();

        List<Point> starts = new ArrayList<>();

        for (Point point : built.getPoints())
        {
            if (point.isDestination()) starts.add(point);
        }

        Map<Integer, Integer> offeredByLength = new TreeMap<>();
        Map<Integer, Integer> deadEnds = new TreeMap<>();
        Map<String, Integer> stranded = new TreeMap<>();
        Map<String, Integer> shortTrainReach = new TreeMap<>();

        for (Integer units : LENGTHS)
        {
            Locomotive train = model.getLocByName(String.format(NAME, units));

            assertNotNull(train, "the " + units + "-unit probe train is gone");

            // THE PREVIOUS PASS'S TRAIN IS STILL STANDING SOMEWHERE, and a train blocks track.
            emptyTheRailway();

            int total = 0;
            int nothing = 0;

            for (Point start : starts)
            {
                Set<String> offered = whereItMayGo(train, start);

                total += offered.size();

                if (units == 1) shortTrainReach.put(start.getName(), offered.size());

                if (offered.isEmpty())
                {
                    nothing++;

                    Integer shortOne = shortTrainReach.get(start.getName());

                    // Stranded BY THE ROOM RULE, not by the shape of the railway: a square that
                    // offers a one-unit train nothing offers nobody anything, and that is a fact
                    // about the track rather than about this rule.
                    if (shortOne != null && shortOne > 0)
                    {
                        stranded.put(units + "-unit at " + start.getName(),
                            shortOne);
                    }
                }
            }

            offeredByLength.put(units, total);
            deadEnds.put(units, nothing);
        }

        System.out.println("### where a train may go, by length, on an empty railway");
        System.out.println("  squares a train can stand on   : " + starts.size());
        System.out.println("  destinations offered in total  : " + offeredByLength);
        System.out.println("  squares offering NOTHING       : " + deadEnds);
        System.out.println("  stranded (a 1-unit train could): " + stranded);

        assertTrue(offeredByLength.get(1) > 0,
            "a one-unit train is offered nothing anywhere on this railway, so the comparison below"
            + " is between two trains that both go nowhere and says nothing");

        assertEquals(stranded.size(), STRANDED,
            stranded.size() + " (square, length) pairs offer a train NOTHING AT ALL where a one-unit"
            + " train standing on the same square is offered somewhere to go, against the " + STRANDED
            + " measured when the ruling was made: " + stranded
            + ".  A train with no destination is a train autonomy will never move and a Return Home"
            + " that reports NO_PLAN_FOUND. This is the number to read beside"
            + " core.testTheRoomRuleCensusOnTheRealLayout's: a third of journeys refused evenly"
            + " strands nobody, and a third refused at the squares on every road out strands"
            + " everybody standing there");
    }

    /**
     * Takes every locomotive off the railway.
     *
     * `whereItMayGo` lifts only the train it is about to place, which is enough within one pass and
     * not enough between two.
     */
    private void emptyTheRailway()
    {
        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null)
            {
                built.moveLocomotive(null, point.getName(), true);
            }
        }
    }

    /**
     * Every destination the railway offers this train from a square, asked at the door the operator
     * asks it at.
     *
     * @param train the locomotive
     * @param start where to stand it
     * @return the destination names
     * @throws Exception on a failure to search
     */
    private Set<String> whereItMayGo(Locomotive train, Point start) throws Exception
    {
        for (Point point : built.getPoints())
        {
            if (train.equals(point.getCurrentLocomotive()))
            {
                built.moveLocomotive(null, point.getName(), true);
            }
        }

        built.moveLocomotive(train.getName(), start.getName(), false);

        Set<String> out = new LinkedHashSet<>();

        List<List<Edge>> paths = built.getPossiblePaths(train, true);

        if (paths != null)
        {
            for (List<Edge> p : paths)
            {
                if (!p.isEmpty()) out.add(p.get(p.size() - 1).getEnd().getName());
            }
        }

        return out;
    }
}
