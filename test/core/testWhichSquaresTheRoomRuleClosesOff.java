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
 * The census (`core.testTheRoomRuleCensusOnTheRealLayout`) counts JOURNEYS, which is not the number that decides
 * whether the railway runs: a third of journeys refused evenly strands nobody, while a third refused because three
 * one-unit tiles sit on every road out of the bottom main strands every train standing there. So this asks the door the
 * operator asks, `Layout.getPossiblePaths(loc, true)`, from every square a train can stand on, for each of the census's
 * six lengths, on a railway with nothing else on it.
 *
 * Since MT-333 both numbers are ZERO, and that is the claim: with a passed square no longer judged, no square is closed
 * to a longer train. Before it, a train of two units or more was offered nothing at all from seven squares (the bottom
 * main berths) that had offered it 31 to 34 destinations under the berth-only rule. A train with no destination is one
 * autonomy never dispatches and a Return Home that reports `NO_PLAN_FOUND`; the remedy is to measure the track
 * (`behaviour/length.md` section 5a).
 *
 * Read against a `LayoutSandbox` copy of `test/layouts/live-snapshot`, with the three tight tiles set by this class:
 * these figures are what a rule about room does on a railway with no room, not a survey of his track.
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
        // The frozen copy, not the railway he is operating (OB-111): every figure below is a measurement of the lengths
        // on the railway it opens, and his live one moves as he works.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

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

        // His measurements and station sizes come off first, so this stays an experiment on a railway with no room.
        session.clearEveryTileLength();
        session.clearEveryMaxTrainLength();

        // The three tight tiles are set here, not read from the fixture: a rule about room says nothing on a railway
        // with room to spare, and it is not a claim about what his track measures.
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
     * (square, length) pairs that had somewhere to go before the ruling and have nowhere after it, on
     * `test/layouts/live-snapshot` with the three tight tiles this class sets. A pin, not an endorsement: nobody
     * changes the number without saying so. 0 since MT-333; every one of the 35 before it was closed by a square the
     * train only passed, which was never what the rule was for (*"this switch blocking should only affect berthes"*).
     */
    private static final int CLOSED_BY_THE_RULING = 0;

    /**
     * How many routes between one pair the search will look at: "this square offers the train nothing" is a claim about
     * EVERY route out of it, so a cap that binds reports a square closed that is not. A different JVM yields routes in
     * a different order, so a cap that never binds in one run can bind in the next; 150 leaves five times the deepest
     * pair measured.
     */
    private static final int ROUTE_CAP = 150;

    /**
     * (square, length) pairs that offer a train nothing at all where a one-unit train has somewhere to go, so the
     * square is closed by the room rule rather than by the shape of the track. 0 since MT-333: the squares that did it
     * were squares on the way.
     */
    private static final int STRANDED = 0;

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

        // Pinned, not required to be zero: whether this is the railway Adam wants is his to say (the remedy is a tape
        // measure on three tiles), and what must not happen is the number moving without anybody noticing.
        assertEquals(closedByTheRuling.size(), CLOSED_BY_THE_RULING,
            closedByTheRuling.size() + " (square, length) pairs had somewhere to go under the"
            + " berth-only rule and have nowhere under Adam's ruling of 2026-09-09, against the "
            + CLOSED_BY_THE_RULING + " measured when it was made. Over the same routes, so the"
            + " difference is the ruling and nothing else: " + closedByTheRuling);
    }

    /**
     * Which squares offer a train of each length nothing at all, on an empty railway: the number to read beside the
     * journey census. The railway is emptied at the top of every pass, because `whereItMayGo` lifts only the train it
     * is about to place, and a previous length's probe left standing reports a square closed that is not.
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

            // The previous pass's train is still standing somewhere, and a train blocks track.
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

                    // Stranded BY THE ROOM RULE, not by the shape of the railway: a square that offers a one-unit train
                    // nothing offers nobody anything.
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
     * Takes every locomotive off the railway: `whereItMayGo` lifts only the train it is about to place, enough within
     * one pass and not between two.
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

    /** Every destination the railway offers this train from a square, asked at the door the operator asks it at. */
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
