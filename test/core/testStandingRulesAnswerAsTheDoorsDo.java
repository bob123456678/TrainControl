package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.StandingRules;
import org.traincontrol.automation.StandingRules.Answer;
import org.traincontrol.automation.StandingRules.Row;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Locomotive;

/**
 * Station Settings' matrix (FR-111) against the real doors: every cell `Layout.standingRulesAt` gives, for every
 * combination of the settings that decide it, compared with what the railway does with two trains, one that can reverse
 * and one that cannot.  On `single-switch`: `Approach` lies between `WestEnd` and the switch to the platforms, so a train
 * from WestEnd arrives by its west side and one from `MainPlatform` by its east.
 */
public class testStandingRulesAnswerAsTheDoorsDo
{
    private static final RouteId THE_ROAD = new RouteId(0, 0);

    private static support.Scenario scenario;
    private static AutonomySession session;
    private static TileKey approach;
    private static Locomotive reversible;
    private static Locomotive oneWay;
    private static JSONObject pristine;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        scenario = support.Scenario.open("single-switch");

        session = scenario.getSession();

        approach = new TileKey(scenario.getPages().get(0).getName(), 3, 3);

        reversible = scenario.newTrain("Rules reversible", 2301);

        oneWay = scenario.newTrain("Rules one way", 2302);

        oneWay.setReversible(false);

        pristine = session.snapshotSetup();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    @BeforeMethod
    public void aFreshSetup() throws Exception
    {
        session.restoreSetup(new JSONObject(pristine.toString()));
    }

    private static List<Point> copiesOf(Layout railway)
    {
        List<Point> copies = new ArrayList<>();

        for (String name : session.getStationIndex().pointNamesAt(approach))
        {
            if (railway.getPoint(name) != null) copies.add(railway.getPoint(name));
        }

        return copies;
    }

    private static StandingRules rules(Layout railway)
    {
        return railway.standingRulesAt(session.getStationIndex().pointNamesAt(approach));
    }

    private static void clearTrains(Layout railway)
    {
        for (Point p : railway.getPoints())
        {
            if (p.getCurrentLocomotive() != null) p.setLocomotive(null);
        }
    }

    /** The side of Approach a train from this neighbor comes in by, read off the rails. */
    private static String sideFrom(Layout railway, String neighbor)
    {
        for (Edge e : railway.getEdges())
        {
            if (e.getStart().getName().equals(neighbor) && copiesOf(railway).contains(e.getEnd())) return e.getEntrySide();
        }

        return null;
    }

    private static Answer answer(boolean bothTrains, boolean theOneThatReverses)
    {
        return bothTrains ? Answer.YES : theOneThatReverses ? Answer.SOME : Answer.NO;
    }

    private static boolean reaches(Layout railway, Locomotive train, Point from, Predicate<List<Edge>> wanted)
    {
        clearTrains(railway);

        from.setLocomotive(train);

        for (List<Edge> path : railway.getPossiblePaths(train, false))
        {
            if (wanted.test(path)) return true;
        }

        return false;
    }

    /** Whether the path ends on Approach having come in by this side. */
    private static Predicate<List<Edge>> stopsCameInBy(Layout railway, String side)
    {
        List<Point> copies = copiesOf(railway);

        return path ->
        {
            Edge last = path.get(path.size() - 1);

            return copies.contains(last.getEnd()) && side.equals(last.getEntrySide());
        };
    }

    /**
     * Autonomy would choose this ending: the clauses `pickPath` asks, through the question it asks them with, asked at the
     * first route tested, when the train stands where it starts.
     */
    private static Predicate<List<Edge>> chosenBy(Layout railway, Locomotive train)
    {
        java.util.concurrent.atomic.AtomicReference<java.util.Set<String>> barred = new java.util.concurrent.atomic.AtomicReference<>();

        return path ->
        {
            Point end = path.get(path.size() - 1).getEnd();

            if (!railway.isSendableDestination(end)) return false;

            for (Edge e : path)
            {
                if (e.getEnd().isReversing()) return false;
            }

            if (barred.get() == null) barred.set(railway.destinationsBarredFromAutonomy(train));

            return !barred.get().contains(end.getName());
        };
    }

    private static Predicate<List<Edge>> offeredTo(Layout railway, Locomotive train)
    {
        return path -> railway.isOfferableToOperator(path.get(path.size() - 1).getEnd(), train);
    }

    /** Sent Here, by the two doors, from each neighbor. */
    private static void assertSentHere(Layout railway, StandingRules rules, String say)
    {
        for (String neighbor : new String[] {"WestEnd", "MainPlatform"})
        {
            String side = sideFrom(railway, neighbor);

            if (side == null) continue;

            Point from = railway.getPoint(neighbor);

            Predicate<List<Edge>> landing = stopsCameInBy(railway, side);

            Answer hand = answer(reaches(railway, oneWay, from, landing.and(offeredTo(railway, oneWay))),
                reaches(railway, reversible, from, landing.and(offeredTo(railway, reversible))));

            Answer auto = answer(reaches(railway, oneWay, from, landing.and(chosenBy(railway, oneWay))),
                reaches(railway, reversible, from, landing.and(chosenBy(railway, reversible))));

            assertEquals(rules.cells(Row.SENT_HERE, true).get(side).getAnswer(), hand, say + ": Sent here by hand from " + neighbor);
            assertEquals(rules.cells(Row.SENT_HERE, false).get(side).getAnswer(), auto, say + ": Sent here by autonomy from " + neighbor);
        }
    }

    /** Sent Through Here: a trip from one neighbor ending on the far side, by hand and, when it reverses nowhere, by autonomy. */
    private static void assertSentThrough(Layout railway, StandingRules rules, String say)
    {
        String[][] trips = {{"WestEnd", "MainPlatform"}, {"MainPlatform", "WestEnd"}};

        for (String[] trip : trips)
        {
            String side = sideFrom(railway, trip[0]);

            if (side == null) continue;

            Point from = railway.getPoint(trip[0]);
            Point to = railway.getPoint(trip[1]);

            Predicate<List<Edge>> over = path ->
            {
                if (path.get(path.size() - 1).getEnd() != to) return false;

                for (Edge e : path)
                {
                    if (e.getEnd().isReversing()) return false;
                }

                return true;
            };

            boolean overIt = reaches(railway, reversible, from, over);

            assertEquals(rules.cells(Row.SENT_THROUGH, true).get(side).getAnswer() == Answer.YES, overIt,
                say + ": Sent through from " + trip[0]);

            // Autonomy's own trip, from a square it starts the train from
            boolean chosen = reaches(railway, reversible, from, over.and(chosenBy(railway, reversible)))
                && railway.explainCannotStart(reversible, false) == null;

            assertEquals(rules.cells(Row.SENT_THROUGH, false).get(side).getAnswer() == Answer.YES, chosen,
                say + ": Sent through by autonomy from " + trip[0]);
        }
    }

    /** Sent From Here: a train stood on the copy facing that way. */
    private static void assertSentFrom(Layout railway, StandingRules rules, String say)
    {
        Map<String, StandingRules.Cell> hand = rules.cells(Row.SENT_FROM, true);
        Map<String, StandingRules.Cell> auto = rules.cells(Row.SENT_FROM, false);

        for (String side : hand.keySet())
        {
            boolean anyHand = false;
            boolean anyAuto = false;

            for (Point copy : copiesOf(railway))
            {
                if (copy.getCopyFacing() != null && !side.equals(copy.getCopyFacing())) continue;

                boolean sends = reaches(railway, reversible, copy, path -> true);

                anyHand |= sends;

                // Autonomy's own question of the square it starts from, and somewhere to go
                anyAuto |= sends && railway.explainCannotStart(reversible, false) == null;
            }

            assertEquals(hand.get(side).getAnswer() == Answer.YES, anyHand, say + ": Sent from by hand to " + side);

            // A door a train reaches nothing from says "no way out" in a sentence of its own, and the matrix is about the square
            if (anyHand) assertEquals(auto.get(side).getAnswer() == Answer.YES, anyAuto, say + ": Sent from by autonomy to " + side);
        }
    }

    /** Turned Round Here: a trip ending on a turning copy, or a hand trip through one, from the east, where something lies on both sides. */
    private static void assertTurned(Layout railway, StandingRules rules, String say)
    {
        String side = sideFrom(railway, "MainPlatform");

        if (side == null) return;

        Point from = railway.getPoint("MainPlatform");

        List<Point> copies = copiesOf(railway);

        Predicate<List<Edge>> turning = path ->
        {
            Edge last = path.get(path.size() - 1);

            // Ending on a copy that faces back the way the train came, and that way open: with it shut the train is stuck
            if (copies.contains(last.getEnd()) && last.getEnd().isTerminus() && side.equals(last.getEntrySide())
                && !railway.getNeighbors(last.getEnd()).isEmpty()) return true;

            for (Edge e : path)
            {
                if (copies.contains(e.getEnd()) && e.getEnd().isReversing()) return true;
            }

            return false;
        };

        // A turn partway: the only way back here throws the switch the train came by, which one path cannot do, so no
        // trip turns here and the cell says no
        boolean byHand = reaches(railway, reversible, from, turning.and(offeredTo(railway, reversible)));

        assertEquals(rules.cells(Row.TURNED_ROUND, true).get(side).getAnswer() != Answer.NO, byHand, say + ": Turned round by hand");

        boolean byAutonomy = reaches(railway, reversible, from, turning.and(chosenBy(railway, reversible)));

        assertEquals(rules.cells(Row.TURNED_ROUND, false).get(side).getAnswer() != Answer.NO, byAutonomy, say + ": Turned round by autonomy");
    }

    /**
     * Every cell, for every combination of stop and through, full autonomy, changing direction, a side closed to arrivals
     * and a way out closed, agrees with the doors.  The setup error of a Must with two sides open is the window's to say,
     * and covered there.
     *
     * MUTATION: drop any clause from `LayoutStandingRules` (the `isStopOnly` in `passing`, say, or `isSendableDestination`
     * in `stoppingChosen`), or read the copies' sides the wrong way round, and this fails on the row it answers.
     */
    @Test
    public void testEveryCellOfEveryCombinationMatchesTheDoors() throws Exception
    {
        boolean[][] boxes = {{true, true}, {true, false}, {false, true}, {false, false}};
        boolean[][] turns = {{false, false}, {true, false}, {true, true}};
        TilePorts.Side[] barred = {null, TilePorts.Side.W, TilePorts.Side.E};
        Direction[] ways = {Direction.BOTH, Direction.TOWARD_A, Direction.TOWARD_B, Direction.NONE};

        int asked = 0;

        for (boolean[] box : boxes)
        {
            for (boolean[] turn : turns)
            {
                for (boolean auto : new boolean[] {true, false})
                {
                    for (TilePorts.Side bar : barred)
                    {
                        for (Direction way : ways)
                        {
                            session.restoreSetup(new JSONObject(pristine.toString()));

                            session.setStopsAndRunsThrough(approach, box[0], box[1]);
                            session.setPointFlag(approach, AutonomyBuilder.CAN_REVERSE, turn[0] && !turn[1]);
                            session.setPointProperty(approach, AutonomyBuilder.MUST_REVERSE, turn[1] ? Boolean.TRUE : null);
                            session.setAutoDestination(approach, auto);

                            if (bar != null) session.setBarredArrivals(approach, java.util.Collections.singleton(bar));

                            session.setRunDirection(approach, THE_ROAD, way);

                            Layout railway = scenario.build();

                            if (!railway.isValid()) continue;

                            String say = "stops " + box[0] + ", through " + box[1] + ", may " + turn[0] + ", must " + turn[1]
                                + ", full autonomy " + auto + ", barred " + bar + ", way " + way;

                            StandingRules rules = rules(railway);

                            assertNotNull(rules, say);

                            assertSentHere(railway, rules, say);
                            assertSentThrough(railway, rules, say);
                            assertSentFrom(railway, rules, say);
                            assertTurned(railway, rules, say);

                            asked++;
                        }
                    }
                }
            }
        }

        assertTrue(asked > 200, "too few combinations built to say anything: " + asked);
    }

    /**
     * On the live snapshot, every cell of every square, by hand and automatically, says what the doors do with a
     * reversible train and one that cannot reverse put on each point in turn: the routes `getPossiblePaths` gives, the
     * ones offered, the ones autonomy would choose from a square it starts trains from, and where a train can be sent
     * from.  Lists are left out of the matrix, so whitelists are set to All first.
     *
     * MUTATION: answer `passing`'s automatic cell with the manual one, as the merged row did, and this fails.
     */
    @Test(timeOut = 300000)
    public void testEveryCellOnTheLiveSnapshotIsWhatThePathSearchGives() throws Exception
    {
        support.Scenario live = support.Scenario.open("live-snapshot");

        try
        {
            AutonomySession setup = live.getSession();

            for (TileKey square : setup.getStationIndex().squares())
            {
                if (setup.getWhichTrains(square) == AutonomySession.WhichTrains.WHITELIST)
                {
                    setup.setWhichTrains(square, AutonomySession.WhichTrains.ALL);
                }
            }

            Locomotive[] trains = {live.newTrain("Live reversible", 2311), live.newTrain("Live one way", 2312)};

            trains[1].setReversible(false);

            Layout railway = live.build();

            org.traincontrol.automationui.StationIndex index = setup.getStationIndex();

            // Square and side, or a start's name, to "tier:train" for what the doors did there
            Map<String, java.util.Set<String>> seen = new java.util.HashMap<>();

            for (Point start : new ArrayList<>(railway.getPoints()))
            {
                for (Locomotive train : trains)
                {
                    String who = train.isReversible() ? ":both" : ":one";

                    clearTrains(railway);

                    start.setLocomotive(train);

                    List<List<Edge>> paths = railway.getPossiblePaths(train, false);

                    if (paths.isEmpty()) continue;

                    Predicate<List<Edge>> offered = offeredTo(railway, train);
                    Predicate<List<Edge>> chosen = chosenBy(railway, train);

                    boolean starts = railway.explainCannotStart(train, false) == null;

                    note(seen, "from " + start.getName(), "hand" + who);

                    if (starts) note(seen, "from " + start.getName(), "auto" + who);

                    for (List<Edge> path : paths)
                    {
                        Edge last = path.get(path.size() - 1);

                        java.util.List<String> tiers = new ArrayList<>();

                        if (offered.test(path)) tiers.add("hand" + who);
                        if (starts && chosen.test(path)) tiers.add("auto" + who);

                        for (String tier : tiers)
                        {
                            note(seen, Row.SENT_HERE + " " + index.squareOf(last.getEnd()) + " " + last.getEntrySide(), tier);

                            if (last.getEnd().isTerminus() && !railway.getNeighbors(last.getEnd()).isEmpty())
                            {
                                note(seen, Row.TURNED_ROUND + " " + index.squareOf(last.getEnd()) + " " + last.getEntrySide(), tier);
                            }

                            for (Edge e : path.subList(0, path.size() - 1))
                            {
                                Row row = e.getEnd().isReversing() ? Row.TURNED_ROUND : Row.SENT_THROUGH;

                                note(seen, row + " " + index.squareOf(e.getEnd()) + " " + e.getEntrySide(), tier);
                            }
                        }
                    }
                }
            }

            clearTrains(railway);

            List<String> differ = new ArrayList<>();

            int cells = 0;

            for (TileKey square : index.squares())
            {
                List<String> copies = index.pointNamesAt(square);

                StandingRules rules = railway.standingRulesAt(copies);

                if (rules == null) continue;

                for (boolean byHand : new boolean[] {true, false})
                {
                    String tier = byHand ? "hand" : "auto";

                    for (Row row : Row.values())
                    {
                        for (Map.Entry<String, StandingRules.Cell> each : rules.cells(row, byHand).entrySet())
                        {
                            java.util.Set<String> did = new java.util.HashSet<>();

                            if (row == Row.SENT_FROM)
                            {
                                for (String name : copies)
                                {
                                    Point copy = railway.getPoint(name);

                                    if (copy != null && (copy.getCopyFacing() == null || copy.getCopyFacing().equals(each.getKey())))
                                    {
                                        did.addAll(seen.getOrDefault("from " + name, java.util.Collections.emptySet()));
                                    }
                                }
                            }
                            else
                            {
                                did.addAll(seen.getOrDefault(row + " " + square + " " + (each.getKey().isEmpty() ? null : each.getKey()),
                                    java.util.Collections.emptySet()));
                            }

                            boolean any = did.contains(tier + ":both") || did.contains(tier + ":one");

                            // Sent Here tells Yes from Some by the train that cannot reverse; the other rows say only whether
                            Answer want = !any ? Answer.NO : row != Row.SENT_HERE ? null
                                : did.contains(tier + ":one") ? Answer.YES : Answer.SOME;

                            Answer got = each.getValue().getAnswer();

                            if (want == null ? got == Answer.NO : got != want)
                            {
                                differ.add(square + " " + index.nameOf(square) + " " + row + " " + tier + " " + each.getKey()
                                    + ": the matrix says " + got + " (" + each.getValue().getWhy() + "), the doors " + did);
                            }

                            cells++;
                        }
                    }
                }
            }

            assertTrue(cells > 300, "precondition: only " + cells + " cells were compared");

            assertTrue(differ.isEmpty(), differ.size() + " cells differ from the doors: " + differ);
        }
        finally
        {
            live.close();
        }
    }

    private static void note(Map<String, java.util.Set<String>> seen, String what, String tier)
    {
        seen.computeIfAbsent(what, k -> new java.util.HashSet<>()).add(tier);
    }

    /**
     * A plain station says what the owner was shown: yes to everything but turning round.
     *
     * MUTATION: answer `neverTurns` with Answer.SOME in `LayoutStandingRules.turning` and this fails.
     */
    @Test
    public void testAPlainStationSaysYesButForTurning() throws Exception
    {
        StandingRules rules = rules(scenario.build());

        for (Row row : new Row[] {Row.SENT_HERE, Row.SENT_THROUGH, Row.SENT_FROM})
        {
            for (boolean byHand : new boolean[] {false, true})
            {
                for (StandingRules.Cell cell : rules.cells(row, byHand).values())
                {
                    assertEquals(cell.getAnswer(), Answer.YES, row + " by hand " + byHand + ": " + cell.getWhy());
                }
            }
        }

        for (boolean byHand : new boolean[] {false, true})
        {
            for (StandingRules.Cell cell : rules.cells(Row.TURNED_ROUND, byHand).values())
            {
                assertEquals(cell.getAnswer(), Answer.NO, "turned round by hand " + byHand);
            }
        }
    }

    /**
     * The window's list and lengths are said below the matrix, not in it: a blacklist, a whitelist and a maximum length
     * change no cell.
     *
     * MUTATION: ask `Point.excludes` for a probe train in `LayoutStandingRules`, and a whitelist turns every Sent Here cell
     * to no, and this fails.
     */
    @Test
    public void testTheListAndTheLengthChangeNoCell() throws Exception
    {
        session.setWhichTrains(approach, AutonomySession.WhichTrains.WHITELIST,
            java.util.Collections.<String>emptyList());
        session.setPointProperty(approach, "maxTrainLength", 1);

        StandingRules rules = rules(scenario.build());

        for (boolean byHand : new boolean[] {false, true})
        {
            for (StandingRules.Cell cell : rules.cells(Row.SENT_HERE, byHand).values())
            {
                assertEquals(cell.getAnswer(), Answer.YES, "the list reached the matrix: " + cell.getWhy());
            }
        }
    }
}
