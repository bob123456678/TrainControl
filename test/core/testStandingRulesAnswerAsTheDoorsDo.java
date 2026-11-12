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

    /** Turned Around Here: a trip ending on a turning copy, or a hand trip through one, from the east, where something lies on both sides. */
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

        assertEquals(rules.cells(Row.TURNED_ROUND, true).get(side).getAnswer() != Answer.NO, byHand, say + ": Turned around by hand");

        boolean byAutonomy = reaches(railway, reversible, from, turning.and(chosenBy(railway, reversible)));

        assertEquals(rules.cells(Row.TURNED_ROUND, false).get(side).getAnswer() != Answer.NO, byAutonomy, say + ": Turned around by autonomy");
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
     * from.  The probe trains have no length and are on no list, so lists and lengths, which turn a Yes into Some trains
     * (`testALengthOrAListMakesEndingHereSomeTrains`), are taken off first.
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
                setup.setWhichTrains(square, AutonomySession.WhichTrains.ALL);
                setup.setPointProperty(square, "maxTrainLength", 0);
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
     * A plain station says what the owner was shown: yes to everything but turning around.
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
                assertEquals(cell.getAnswer(), Answer.NO, "turned around by hand " + byHand);
            }
        }
    }

    /**
     * A maximum length or a list in force turns a Yes for a trip ending here into Some trains, in both columns, the
     * reason naming the limit; running through and leaving, which neither limits, stay Yes (OB-440, D34).
     *
     * MUTATION: have `LayoutStandingRules.limited` hand back its cell unchanged, and this fails.
     */
    @Test
    public void testALengthOrAListMakesEndingHereSomeTrains() throws Exception
    {
        for (int limit = 0; limit < 3; limit++)
        {
            session.restoreSetup(new JSONObject(pristine.toString()));

            String named;

            if (limit == 0)
            {
                session.setPointProperty(approach, "maxTrainLength", 3);
                named = org.traincontrol.util.I18n.f("autolayout.rules.limitLength", 3);
            }
            else
            {
                AutonomySession.WhichTrains which = limit == 1 ? AutonomySession.WhichTrains.BLACKLIST : AutonomySession.WhichTrains.WHITELIST;

                session.setWhichTrains(approach, which, java.util.Collections.singletonList(oneWay.getName()));
                named = org.traincontrol.util.I18n.f(limit == 1 ? "autolayout.rules.limitBlacklist" : "autolayout.rules.limitWhitelist", oneWay.getName());
            }

            StandingRules rules = rules(scenario.build());

            for (boolean byHand : new boolean[] {false, true})
            {
                for (StandingRules.Cell cell : rules.cells(Row.SENT_HERE, byHand).values())
                {
                    assertEquals(cell.getAnswer(), Answer.SOME, "limit " + limit + ", by hand " + byHand + ": " + cell.getWhy());
                    assertTrue(cell.getWhy().contains(named), "the reason does not name the limit: " + cell.getWhy());
                }

                for (Row row : new Row[] {Row.SENT_THROUGH, Row.SENT_FROM})
                {
                    for (StandingRules.Cell cell : rules.cells(row, byHand).values())
                    {
                        assertEquals(cell.getAnswer(), Answer.YES, row + " is limited (" + limit + "): " + cell.getWhy());
                    }
                }
            }
        }
    }

    /**
     * An Allowed trains whitelist naming no train keeps every train out (`Point.excludes`), so every cell for a trip
     * ending here says No with its own reason, and no side is a way in.
     *
     * MUTATION: drop the empty-whitelist answer from `LayoutStandingRules.limited`, and this fails.
     */
    @Test
    public void testAnEmptyWhitelistSaysNoTrainEndsHere() throws Exception
    {
        session.setWhichTrains(approach, AutonomySession.WhichTrains.WHITELIST, java.util.Collections.<String>emptyList());

        Layout railway = scenario.build();

        for (Point copy : copiesOf(railway))
        {
            assertTrue(copy.excludes(reversible) && copy.excludes(oneWay), "precondition: the empty whitelist lets a train in");
        }

        StandingRules rules = rules(railway);

        for (boolean byHand : new boolean[] {false, true})
        {
            for (Row row : new Row[] {Row.SENT_HERE, Row.TURNED_ROUND})
            {
                for (StandingRules.Cell cell : rules.cells(row, byHand).values())
                {
                    assertEquals(cell.getAnswer(), Answer.NO, row + ", by hand " + byHand + ": " + cell.getWhy());
                }
            }

            for (StandingRules.Cell cell : rules.cells(Row.SENT_HERE, byHand).values())
            {
                assertEquals(cell.getWhy(), org.traincontrol.util.I18n.t("autolayout.rules.limitNoneAllowed"), "the reason");
            }
        }

        assertTrue(rules.waysIn().isEmpty(), "the picture would draw ways in: " + rules.waysIn());
    }

    /**
     * On a square that is no station, the path search refuses a trip through it or starting from it for a train its list
     * keeps out, so Sent through here and Sent from here say Some trains under a blacklist, naming it, and No under an
     * empty whitelist; checked against the door, the trips from WestEnd to MainPlatform over Approach made a plain
     * sensor, and those from Approach.
     *
     * MUTATION: have `LayoutStandingRules.limited` hand back its cell for a trip that does not end there, and this
     * fails; have `leaving` leave out `limited`, and this fails.
     */
    @Test
    public void testAListOnASquareThatIsNoStationLimitsRunningThroughAndStarting() throws Exception
    {
        // Before any list: the Sent from cells that say Yes, which the list must limit
        session.setStopsAndRunsThrough(approach, false, true);

        Map<String, StandingRules.Cell> before = rules(scenario.build()).cells(Row.SENT_FROM, true);

        assertTrue(before.values().stream().anyMatch(c -> c.getAnswer() == Answer.YES), "precondition: no train may be sent from Approach");

        for (boolean empty : new boolean[] {false, true})
        {
            session.restoreSetup(new JSONObject(pristine.toString()));
            session.setStopsAndRunsThrough(approach, false, true);
            session.setWhichTrains(approach, empty ? AutonomySession.WhichTrains.WHITELIST : AutonomySession.WhichTrains.BLACKLIST,
                empty ? java.util.Collections.<String>emptyList() : java.util.Collections.singletonList(oneWay.getName()));

            Layout railway = scenario.build();
            List<Point> copies = copiesOf(railway);
            Point to = railway.getPoint("MainPlatform");

            Predicate<List<Edge>> over = path ->
            {
                if (path.get(path.size() - 1).getEnd() != to) return false;

                for (Edge e : path.subList(0, path.size() - 1))
                {
                    if (copies.contains(e.getEnd())) return true;
                }

                return false;
            };

            // The door: the path search
            assertEquals(reaches(railway, reversible, railway.getPoint("WestEnd"), over), !empty, "the door, the reversible train (" + empty + ")");
            assertFalse(reaches(railway, oneWay, railway.getPoint("WestEnd"), over), "the door, the listed train (" + empty + ")");

            String side = sideFrom(railway, "WestEnd");
            assertLimited(rules(railway).cells(Row.SENT_THROUGH, true).get(side), empty, "Sent through by hand");

            // The door: a train stood on Approach
            boolean anyReversible = false;
            boolean anyListed = false;

            for (Point copy : copies)
            {
                anyReversible |= reaches(railway, reversible, copy, path -> true);
                anyListed |= reaches(railway, oneWay, copy, path -> true);
            }

            assertEquals(anyReversible, !empty, "the door from Approach, the reversible train (" + empty + ")");
            assertFalse(anyListed, "the door from Approach, the listed train (" + empty + ")");

            for (Map.Entry<String, StandingRules.Cell> each : rules(railway).cells(Row.SENT_FROM, true).entrySet())
            {
                if (before.get(each.getKey()).getAnswer() == Answer.YES) assertLimited(each.getValue(), empty, "Sent from by hand to " + each.getKey());
            }
        }
    }

    /** A cell limited by Approach's blacklist of the one-way train, or by an empty whitelist. */
    private static void assertLimited(StandingRules.Cell cell, boolean empty, String say)
    {
        assertEquals(cell.getAnswer(), empty ? Answer.NO : Answer.SOME, say + ": " + cell.getWhy());
        assertTrue(cell.getWhy().contains(empty ? org.traincontrol.util.I18n.t("autolayout.rules.limitNoneThrough")
            : org.traincontrol.util.I18n.f("autolayout.rules.limitBlacklist", oneWay.getName())), say + ", the reason: " + cell.getWhy());
    }

    /**
     * A train claiming its route never waits for the window's question: while the search is held up partway, inside a
     * switch-setting preview, another thread takes the railway's monitor, the one `configureAndLockPath` locks a route
     * under.  The search runs over every station, so a hold for all of it stops every train at its next milestone.
     *
     * MUTATION: put `synchronized` back on `Layout.standingRulesAt`, and this fails.
     */
    @Test(timeOut = 60000)
    public void testATrainClaimingItsRouteDoesNotWaitForTheSearch() throws Exception
    {
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch letGo = new java.util.concurrent.CountDownLatch(1);
        org.traincontrol.model.ViewListener model = scenario.getModel();

        // The scenario's control, holding up the search's first lookup of an accessory until let go
        Layout railway = Layout.fromJSON(session.buildConfiguration(), (org.traincontrol.model.ViewListener)
            java.lang.reflect.Proxy.newProxyInstance(org.traincontrol.model.ViewListener.class.getClassLoader(),
            new Class<?>[] {org.traincontrol.model.ViewListener.class}, (proxy, method, args) ->
            {
                if (method.getName().equals("getAccessoryByName") && inside.getCount() > 0 && inTheSearch())
                {
                    inside.countDown();
                    letGo.await(30, java.util.concurrent.TimeUnit.SECONDS);
                }

                return method.invoke(model, args);
            }));

        java.util.concurrent.ExecutorService threads = java.util.concurrent.Executors.newFixedThreadPool(2);

        try
        {
            java.util.concurrent.Future<StandingRules> asked = threads.submit(() -> rules(railway));

            assertTrue(inside.await(20, java.util.concurrent.TimeUnit.SECONDS),
                "precondition: the search never previewed a switch setting, so nothing held it up");

            java.util.concurrent.Future<Boolean> claimed = threads.submit(() ->
            {
                synchronized (railway)
                {
                    return true;
                }
            });

            try
            {
                claimed.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (java.util.concurrent.TimeoutException waited)
            {
                fail("a train claiming its route waited for Station Settings' search: the search holds the railway's"
                    + " monitor, so every train stops at its next milestone until it ends");
            }

            letGo.countDown();

            assertNotNull(asked.get(20, java.util.concurrent.TimeUnit.SECONDS), "the search, let go, gave no answer");
        }
        finally
        {
            letGo.countDown();
            threads.shutdownNow();
        }
    }

    private static boolean inTheSearch()
    {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace())
        {
            if (frame.getClassName().equals("org.traincontrol.automation.LayoutStandingRules")) return true;
        }

        return false;
    }
}
