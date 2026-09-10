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
 * `ParkingTrack12` is in the first list and not the second: it offers a long train nothing under
 * either rule, so it is a fact about the track rather than about the ruling.
 *
 * Read against a `LayoutSandbox` copy of `cs2_sample_layout` (OB-111).  His railway is only ever read.
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
     * How many of the dead ends the ruling of 2026-09-09 added, over the same routes.
     *
     * `whyTooLongForThisRoute` is the rule as it stands; the berth-only question - the rule as it
     * stood before the ruling - is `measuredRoomAtTheEndOf` over the whole route plus the station's
     * stated capacity, which is what `whyTooLongForThisRoute` asked before the prefix loop was added.
     * Asked over the SAME route set, so the difference between the two is the ruling and nothing else.
     *
     * @throws Exception on a failure to search
     */
    /**
     * (square, length) pairs that had somewhere to go before the ruling and have nowhere after it.
     *
     * Measured on `test/layouts/live-snapshot`.  **A pin, not an endorsement**: it says nobody changes
     * this without saying so, and `docs/manual-tests/issues.md` carries what it costs and what the
     * remedy is.
     */
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

    private static final int CLOSED_BY_THE_RULING = 35;

    /**
     * (square, length) pairs that offer a train nothing at all where a one-unit train has somewhere
     * to go - so the square is closed by the room rule rather than by the shape of the track.
     */
    private static final int STRANDED = 40;

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

    @Test
    public void testWhichSquaresOfferALongTrainNothingAtAll() throws Exception
    {
        assertNotNull(built, "the configuration did not build");

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null)
            {
                built.moveLocomotive(null, point.getName(), true);
            }
        }

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
