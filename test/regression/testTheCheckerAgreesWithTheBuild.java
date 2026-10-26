package regression;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.GraphReducer.ReducedPoint;
import org.traincontrol.automationui.StationIndex;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * What the checker tells the user, against the railway the build actually emits.
 *
 * The checker walks the REDUCED graph and works out arrivals, departures and reachability for itself, while the builder
 * walks the same reduction and emits a Point per arrival side: two answers to one question, agreeing only by having
 * been written to.  This builds the frozen sample diagram, runs the checker, parses the generated configuration into a
 * real Layout, and asks whether the two agree about the same squares:
 *
 *   - a trapped arrival is a copy the build emits with no edge out of it;
 *   - a station that reaches nothing is one whose copies reach no other station's copies;
 *   - a station nothing reaches is one no other station's copies reach;
 *   - a facing the build gives a copy is a facing the editor offers for that square.
 *
 * The oracle is the BUILT graph (Points and Edges as parseAuto made them), not the reduction the checker read:
 * `Layout.getNeighbors` knows nothing about arrival sides, entry sides or turn sets.
 *
 * The trapped-arrival clause is true by construction now, since `AutonomySession.check` reads the built graph; it is
 * asserted only against the oracle's own filters.  The reachability clauses compare the checker's reach, read off the
 * build's JSON (`BuiltReach`), with what Layout's own route search offers a train on the Layout parseAuto made of it
 * (`offeredFrom`), over this fixture and every scenario.
 * Dropping the "never split, so no arrival to trap" filter once reported three squares with no arrival at all.
 *
 * The fixture is `test/test_layout`, frozen and wired the way `testAutonomyDiagramSampleLayout` wires it: a diagram
 * parsed alone has addresses on its tiles but no Accessory objects, and two thirds of the track does not connect.
 *
 * @author Adam
 */
public class testTheCheckerAgreesWithTheBuild
{
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout built;
    private static StationIndex index;
    private static List<AutonomyChecks.Finding> findings;
    private static Map<TileKey, Set<TileKey>> reachFromEachStation;

    /**
     * Reads the sample diagram, opens the setup that ships with it, and builds the railway it names.
     *
     * @throws Exception when the fixture cannot be read at all, a broken harness rather than a failing guard
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();

        File folder = new File("test/test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        wireAccessories(pages);

        session = new AutonomySession(folder);
        session.open(pages);

        assertNotNull(session.getReducer(), "the diagram did not reduce, so there is nothing to check");

        findings = session.check();

        model.parseAuto(session.buildConfiguration());

        built = model.getAutoLayout();

        assertNotNull(built, "the generated configuration produced no graph");
        assertTrue(built.isValid(), "the generated configuration is invalid: " + Layout.getLastError());

        // A fixture that reduced to a handful of Points (an unwired diagram does) would let every test pass on nothing.
        assertTrue(session.getReducer().getEdges().size() > 50,
            "only " + session.getReducer().getEdges().size() + " edges were derived.  The sample "
                + "layout reduces to about ninety; a number this low means the diagram did not wire "
                + "up, and every agreement below is being asserted about track that is not there");
    }

    /**
     * A square the checker calls trapped is a square the build emits a Point it cannot leave.
     *
     * The builder's `nodesFor` answers the same question by NOT emitting a way out of the copy, deliberately, so the
     * sensor is not lost from the graph; the two are one rule with two authors and this is its witness.
     *
     * Two squares are excluded, both because the checker cannot be about them:
     *   - a square with no arrival side at all is a sensor whose every connection has been closed, emitted whole with
     *     no way in or out; `checkIsolatedPoints` names it;
     *   - a square where trains may turn round gets a turning copy of every arrival, so an arrival with no way forward
     *     there is the turn, not a trap.
     *
     * Neither is told about a square that is no station, nor an arrival no train makes (OB-361).
     *
     * Mutation this must fail: in `AutonomySession.check`, delete the `if (isTurnAround(tile)) continue;` that opens
     * the trapped walk, so the parking berths, whose only copy is the turning one, are named as places a train cannot
     * leave.
     *
     * Two mutations it does not catch: replacing the `graph.exits` filter with `onwards.addAll(departures);`, and
     * deleting `onwards.remove(arrival);`.  Every trapped square here has NO outgoing reduced edge, so `departures` is
     * empty; the case the filter exists for (a double curve whose one arm dead-ends) is not drawn on the sample.
     */
    @Test
    public void testEveryTrappedArrivalIsACopyTheBuildCannotLeave()
    {
        Set<TileKey> reported = new TreeSet<>(BY_KEY);

        for (AutonomyChecks.Finding finding : findings)
        {
            if (AutonomyChecks.isArrivalTrapped(finding.getMessageKey()))
            {
                reported.add(finding.getTile());
            }
        }

        Set<TileKey> emitted = new TreeSet<>(BY_KEY);

        Set<String> arrivedAt = copiesATrainArrivesAt();

        // every copy no edge leaves, before the rules below choose among them
        int noWayOut = 0;

        for (Point point : built.getPoints())
        {
            if (!built.getNeighbors(point).isEmpty()) continue;

            TileKey square = index().squareOf(point.getName());

            if (square == null) continue;

            noWayOut++;

            // only at a station, and only an arrival a train makes (OB-361)
            if (!session.getStore().isStation(square) || !arrivedAt.contains(point.getName())) continue;

            // not split at all, so there is no arrival for a train to be trapped by
            if (session.arrivalSides(square).isEmpty()) continue;

            // the turning copy is the answer to a dead end, not a report of one
            if (session.isTurnAround(square)) continue;

            // a station's copy that is no station is a side trains may not arrive by: no train is sent to it, and with no
            // way out none passes through
            if (session.getStore().isStation(square) && !point.isDestination()) continue;

            emitted.add(square);
        }

        assertTrue(noWayOut >= 10, "the sample layout builds copies no edge leaves - ten trapped arrivals among them - so"
            + " a run finding " + noWayOut + " is a harness fault, not a clean setup");

        assertEquals(reported.toString(), emitted.toString(),
            "the checker and the build disagree about which squares a train can arrive at and not "
                + "leave.  The checker walks the reduced edges; the build emits a copy with no way "
                + "out.  One rule, two authors (DD-A7)");
    }

    /**
     * A station the checker says nothing can reach is one no other station's copies reach.
     *
     * The oracle is the parsed Layout's own route search (`offeredFrom`), what a train standing there is offered.
     *
     * Mutation this must fail: in `AutonomyChecks.checkStations`, invert the reverse direction so a station is reported
     * when something CAN reach it (`if (reachable)` in place of `if (!reachable)`).
     */
    @Test
    public void testTheStationsNothingReachesAreTheOnesTheBuildCannotRouteTo()
    {
        Map<TileKey, Set<TileKey>> reach = reachFromEachStation();

        // A floor like its siblings': without it "both sets are empty" and "the checker is silently broken" look alike.
        // MUTATION that survives: make `AutonomyChecks.checkStations` never report STATION_UNREACHABLE; the floor
        // misses it whenever no station is left unreached once the closed ones are taken out.
        assertFalse(reach.isEmpty(),
            "reachFromEachStation() examined no stations at all, so the comparison below is between "
            + "two empty sets and proves nothing about whether the checker and the build agree");

        Set<TileKey> unreached = new TreeSet<>(BY_KEY);

        for (TileKey station : reach.keySet())
        {
            boolean reachable = false;

            for (TileKey other : reach.keySet())
            {
                if (!other.equals(station) && reach.get(other).contains(station)) reachable = true;
            }

            if (!reachable) unreached.add(station);
        }

        // A STATION NOTHING CAN PASS IS SAID TO BE CLOSED, NOT UNREACHABLE (FR-101): nothing reaches such a square, so
        // the checker says so once, as closed, in place of the reachability sentences.  They are compared under their
        // own sentence, and the rest under this one.  Fifteen of this fixture's stations are closed.
        Set<TileKey> closed = closedAmong(reach.keySet());

        assertTrue(unreached.containsAll(closed), "a station nothing can pass was reached on the built graph: " + closed
            + " against " + unreached);

        assertEquals(reported(AutonomyChecks.STATION_CLOSED).toString(), closed.toString(),
            "the checker does not say, once each, that the stations nothing can pass are closed (FR-101)");

        unreached.removeAll(closed);

        assertEquals(reported(AutonomyChecks.STATION_UNREACHABLE).toString(), unreached.toString(),
            "the checker and the build disagree about which stations nothing can reach.  A station "
                + "the checker calls reachable and the built graph cannot route to is a destination "
                + "the user will be offered and the railway will refuse");
    }

    /**
     * A station the checker says reaches nothing is one whose copies reach no other station.
     *
     * The stranded and reaches-nothing findings are one verdict said two ways, so both are read here.
     * `1 - Main:12,11` (BottomSecondary) is one: its one copy arrives by the east side, so it cannot depart east, and
     * west leads to a trapped copy of TunnelPre.
     *
     * Mutation this must fail: in `AutonomyChecks.checkStations`, invert the forward direction so a station is
     * reported when it CAN reach another (`if (reachesAStation)` in place of `if (!reachesAStation)`).
     */
    @Test
    public void testTheStationsThatReachNothingAreTheOnesTheBuildCannotRouteOutOf()
    {
        Map<TileKey, Set<TileKey>> reach = reachFromEachStation();

        Set<TileKey> stranded = new TreeSet<>(BY_KEY);

        for (TileKey station : reach.keySet())
        {
            boolean reaches = false;

            for (TileKey other : reach.keySet())
            {
                if (!other.equals(station) && reach.get(station).contains(other)) reaches = true;
            }

            if (!reaches) stranded.add(station);
        }

        assertTrue(String.valueOf(stranded).contains("1 - Main:12,11"),
            "precondition: BottomSecondary, whose one copy cannot leave eastward, is no longer stranded on the built"
                + " graph, so this fixture has changed: " + stranded);

        // A closed station is said to be closed, and nothing else (FR-101) - see the claim above.
        stranded.removeAll(closedAmong(reach.keySet()));

        Set<TileKey> reported = reported(AutonomyChecks.STATION_REACHES_NOTHING);
        reported.addAll(reported(AutonomyChecks.TERMINUS_STRANDED));

        assertEquals(reported.toString(), stranded.toString(),
            "the checker and the build disagree about which stations can be left.  A station the "
                + "checker believes reaches somewhere and the built graph cannot route out of is a "
                + "train that arrives and stops for good");
    }

    /**
     * A train parked where it turned round is not reported as facing an impossible way.
     *
     * A train that turned round is pointing back at the side it came in by - so on a berth whose only
     * copy is the turning one, the single facing the square can actually hold was reported as
     * impossible.
     *
     * Sixteen squares on this fixture are of that shape (every parking berth plus four reversing points); it appears
     * the first time autonomy parks a train in one, because `captureFromLayout` writes that facing back.
     *
     * **What used to make that true, and what makes it true now.** `facingChoices` offered only where
     * a train could be sent ONWARD, so it never offered an arrival side, and the checker carried a
     * carve-out to forgive one. OB-145 is what that arrangement cost: the checker forgave a facing the
     * MENU would not offer, and on BottomMainC - one arrival side after a one-way run - that left a
     * single offered facing and no facing menu at all. So `facingChoices` now includes the turning
     * facings on these squares, the carve-out is gone, and this test passes because the answer is
     * right rather than because it was excused.
     *
     * The carve-out once forgiving this was added with no guard of its own: deleting it from the production check left
     * every class green, because nothing referenced FACING_IMPOSSIBLE.  It lives here rather than beside the session's
     * other tests because their fixture has no berth a train can turn round on.
     *
     * MUTATION: removing `if (isTurnAround(tile)) out.addAll(arrivalSides(tile));` from
     * `AutonomySession.facingChoices` fails this test, along with the two beside it.
     */
    @Test
    public void testATrainParkedWhereItTurnedRoundIsNotAnImpossibleFacing()
    {
        TileKey berth = null;
        Side turned = null;

        // The first square that is a berth in the sense this is about: a train may turn round on it,
        // and the side it would then be facing is an arrival side.
        //
        // THE SEARCH USED TO ADD "and facingChoices does not offer it", which was how it found the
        // squares the carve-out excused. OB-145 removed that gap - `facingChoices` now offers those
        // facings - so the old search matched nothing and this test's own vacuity guard fired, which
        // is what a vacuity guard is for. Narrowing the search to keep it satisfiable would have been
        // the wrong repair: the claim is about what the CHECKER says, not about which squares happen
        // to disagree with the menu on the way there.
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (!session.isTurnAround(tile)) continue;

            for (Side arrival : session.arrivalSides(tile))
            {
                berth = tile;
                turned = arrival;

                break;
            }

            if (berth != null) break;
        }

        assertNotNull(berth,
            "this fixture has no square a train can turn round on that anything arrives at - so the "
            + "case this is about cannot be built here, and this test would pass by testing nothing");

        session.placeLocomotive(berth, firstLocomotive());

        session.setFacing(berth, turned);

        List<AutonomyChecks.Finding> after = session.check();

        for (AutonomyChecks.Finding finding : after)
        {
            assertNotEquals(finding.getMessageKey(), AutonomyChecks.FACING_IMPOSSIBLE,
                "a train parked where it turned round was reported as holding a facing its square "
                + "cannot hold - and on a berth with one copy that is the only facing the square CAN "
                + "hold.  Square: " + berth + ", facing " + turned);
        }

        session.placeLocomotive(berth, null);
    }

    /**
     * Any locomotive the database has, for a placement whose identity does not matter.
     */
    private static String firstLocomotive()
    {
        return model.getLocList().get(0);
    }

    /**
     * Every facing the build gives a copy is a facing the editor would have offered for that square.
     *
     * `AutonomySession.onwardFrom` walks `getRoutes(tile)` to decide what the menu offers and
     * `AutonomyBuilder.facingOf` walks the same routes to name a copy; when they drift the menu offers a facing the
     * build has no copy for (MT-125's defect class).  Two shapes are accepted, because the build emits two kinds of
     * copy:
     *   - a plain copy is a train that arrived and carried on, its facing the far end of the track it came in on;
     *   - a turning copy is a train that turned round, pointing back at the side it came in by: an arrival side, never
     *     offered as a facing because a train standing there has not turned round yet.
     *
     * Mutation this must fail: in `AutonomyBuilder.facingOf`, answer the compass rule instead of the track
     * (`if (true) return node.arrival.opposite();` before the route loop).  `testFacingFollowsTheTrack` cannot catch
     * this one: it reads `facingChoices`, the session's side of the pair.
     */
    @Test
    public void testEveryFacingTheBuildEmitsIsOneTheEditorOffers()
    {
        StringBuilder wrong = new StringBuilder();

        int checked = 0;

        for (TileKey square : index().squares())
        {
            List<Side> offered = session.facingChoices(square);
            List<Side> arrivals = session.arrivalSides(square);

            for (Map.Entry<String, Side> copy : index().facingsAt(square).entrySet())
            {
                checked++;

                if (offered.contains(copy.getValue())) continue;

                // THE TURNING COPY IS NO LONGER EXCUSED HERE (OB-145).
                //
                // This used to skip any facing that was an arrival side, on the reasoning quoted in
                // the javadoc: a turning copy points back the way it came, and that is "never offered
                // as a facing because a train standing there has not turned round yet". True of a
                // train that arrived and stopped; false of one the operator placed, which is the
                // menu's whole job, and false of one autonomy parked, because captureFromLayout
                // writes that facing back.
                //
                // So the skip was excusing the disagreement it was written to catch, and Adam found
                // what it was hiding: at BottomMainC the build held two facings, the menu offered one,
                // and the menu therefore did not appear. `facingChoices` offers them now, so this test
                // no longer needs the excuse - and without it, a drift on ANY square is a failure.
                wrong.append("\n  ").append(square).append(": the build calls ")
                     .append(copy.getKey()).append(" ").append(copy.getValue())
                     .append(", the editor offers ").append(offered)
                     .append(" and the square arrives from ").append(arrivals);
            }
        }

        assertTrue(checked > 20, "only " + checked + " copies carry a facing, so this proves little - "
            + "the sample layout emits about sixty");

        assertEquals(wrong.toString(), "",
            "the build gives a copy a facing the editor would never offer for that square.  A facing "
                + "is the other end of the piece of track the train is standing on (MT-125), and "
                + "onwardFrom and AutonomyBuilder.facingOf both have to say so:" + wrong);
    }

    /**
     * On a square a train may turn round on, the menu offers every facing the build can hold (OB-145).
     *
     * Adam: "on bottommainc, I don't see a 'locomotive is facing' choice even though there is a path
     * out both ways" - and the context that finds it: "the menu disappears when switch 70 disallows
     * outbound travel to the west, which shouldn't affect leaving trains."
     *
     * BottomMainC carries `canReverse: true` and a recorded facing of W, which is an ARRIVAL side. The
     * build holds that facing perfectly well - `AutonomyBuilder.facingOf` returns `node.arrival` for a
     * turning copy - and `facingsThatCannotBeHeld` was taught to accept it. `facingChoices`, which is
     * what decides whether the menu appears at all, was not: it offers only where a train could be sent
     * ONWARD. So once the one-way run cut that square to a single arrival side, one onward facing was
     * all it offered, and `buildFacingMenu` returns null below two.
     *
     * **The sentence that allowed it** is in `testEveryFacingTheBuildEmitsIsOneTheEditorOffers` just
     * above, which skips a turning copy's facing on the reasoning that it is "never offered as a facing
     * because a train standing there has not turned round yet". That is true of a train that arrived
     * and stopped. It is false of a train the OPERATOR placed - which is the whole job of the menu -
     * and false of one autonomy parked, because `captureFromLayout` writes exactly that facing back.
     * So the pair were held in step by a rule that excused the disagreement instead of catching it.
     *
     * This is the strict half, over the squares where the excuse applied: the build emits a turning
     * copy only where a train may turn round, so on those squares the arrival sides ARE holdable
     * facings and the menu has to offer them.
     *
     * MUTATION: remove `if (isTurnAround(tile)) out.addAll(arrivalSides(tile));` from
     * `AutonomySession.facingChoices` and this fails, naming the berths.
     */
    @Test
    public void testTheMenuOffersEveryFacingATurnAroundSquareCanHold()
    {
        StringBuilder missing = new StringBuilder();

        int checked = 0;
        int berths = 0;

        for (TileKey square : index().squares())
        {
            if (!session.isTurnAround(square)) continue;

            berths++;

            List<Side> offered = session.facingChoices(square);

            for (Map.Entry<String, Side> copy : index().facingsAt(square).entrySet())
            {
                checked++;

                if (offered.contains(copy.getValue())) continue;

                missing.append("\n  ").append(square).append(": the build calls ")
                       .append(copy.getKey()).append(" ").append(copy.getValue())
                       .append(", but the editor offers only ").append(offered);
            }
        }

        // Both floors, because they fail differently: no berths at all means the fixture stopped
        // carrying the case, and berths with no copies means facingsAt has stopped answering.
        assertTrue(berths > 0,
            "no square in this fixture is one a train may turn round on, so the case OB-145 is about "
            + "cannot be built here and this test would pass by testing nothing");

        assertTrue(checked > 0,
            berths + " squares a train may turn round on, and not one of them has a copy carrying a "
            + "facing - so this compared nothing");

        assertEquals(missing.toString(), "",
            "the build can stand a train on a copy whose facing the editor will not offer for that "
            + "square.  Below two offered facings `buildFacingMenu` returns null, so the operator "
            + "loses the ability to say which way round the train is standing - which is what Adam "
            + "saw at BottomMainC (OB-145):" + missing);
    }

    /**
     * The arrival sides of a square are the entry sides of the edges that end on it.
     *
     * Three inline copies of this walk were consolidated onto `arrivalSides`; they skipped edges with a null entry
     * side, while that door BAILS on the whole square when it meets one.  It cannot presently happen (an edge ends at a
     * feedback tile, and a null entry side comes only from a portal's partner, a tunnel or link), and this test is
     * where that is written down: the consolidation stays behavior-preserving only while it holds.
     *
     * Mutation this must fail: in `StationIndex.arrivalSidesAt`, answer with the first side only
     * (`if (sides != null && sides.size() > 1) sides = sides.subList(0, 1);`).
     *
     * A mutation it does not catch: in `AutonomyBuilder.splitSides`, `continue;` in place of
     * `return Collections.emptyList();`, because no edge on this layout arrives by no side.
     */
    @Test
    public void testTheArrivalSidesDoorAnswersWhatTheEdgesSay()
    {
        GraphReducer reducer = session.getReducer();

        StringBuilder wrong = new StringBuilder();

        int split = 0;

        for (TileKey square : reducer.getPoints().keySet())
        {
            Set<Side> walked = new TreeSet<>();

            boolean arrivedByNoSide = false;

            for (ReducedEdge edge : reducer.getEdges())
            {
                if (!edge.getEnd().equals(square)) continue;

                if (edge.getEntrySide() == null) arrivedByNoSide = true;
                else walked.add(edge.getEntrySide());
            }

            if (arrivedByNoSide)
            {
                wrong.append("\n  ").append(square).append(" is reached by an edge with no entry "
                    + "side, which is the one case where the walk and the door part company");
            }

            if (!walked.isEmpty()) split++;

            if (!new ArrayList<>(walked).equals(session.arrivalSides(square)))
            {
                wrong.append("\n  ").append(square).append(": the edges say ").append(walked)
                     .append(", arrivalSides says ").append(session.arrivalSides(square));
            }
        }

        assertTrue(split > 30, "only " + split + " squares have an arrival at all, so the sweep is "
            + "not covering the layout");

        assertEquals(wrong.toString(), "",
            "the arrival-sides door and the edges it is derived from no longer agree, so the three "
                + "checks routed through it are asking a different question from the one they used to "
                + "ask inline (DR-B6):" + wrong);
    }

    /**
     * The copies a train arrives at on the built railway, asked of the railway rather than of the setup the checker
     * reads.
     */
    private static Set<String> copiesATrainArrivesAt()
    {
        Set<String> arrived = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<Point> queue = new ArrayDeque<>();

        for (Point point : built.getPoints())
        {
            if (point.getCurrentLocomotive() != null) arrived.add(point.getName());

            if ((point.isDestination() || point.getCurrentLocomotive() != null) && seen.add(point.getName())) queue.add(point);
        }

        while (!queue.isEmpty())
        {
            for (Edge edge : built.getNeighbors(queue.remove()))
            {
                arrived.add(edge.getEnd().getName());

                if (seen.add(edge.getEnd().getName())) queue.add(edge.getEnd());
            }
        }

        return arrived;
    }

    private static Map<TileKey, Set<TileKey>> reachFromEachStation()
    {
        if (reachFromEachStation != null) return reachFromEachStation;

        Map<TileKey, Set<TileKey>> out = new LinkedHashMap<>();
        Locomotive train = oracleTrain(model);

        for (ReducedPoint point : session.getReducer().getPoints().values())
        {
            if (point.isStation()) out.put(point.getTile(), offeredFrom(built, index(), point.getTile(), train));
        }

        assertTrue(out.size() > 10, "only " + out.size() + " stations, so reachability proves little");

        reachFromEachStation = out;

        return out;
    }

    /**
     * The other squares a train standing alone at `from` is offered, asked of Layout's own route search
     * (`getPossiblePaths`, the manual send list), so it shares no code with `BuiltReach`, which it checks.  The train
     * stands on each copy a route can end at there, or on every copy where it has none; the trains standing on the
     * railway are lifted off for the question and put back after it.
     */
    static Set<TileKey> offeredFrom(Layout layout, StationIndex index, TileKey from, Locomotive train)
    {
        List<Point> starts = new ArrayList<>();

        for (Point point : index.pointsAt(layout, from))
        {
            if (point.isDestination()) starts.add(point);
        }

        if (starts.isEmpty()) starts = index.pointsAt(layout, from);

        Map<Point, Locomotive> standing = new LinkedHashMap<>();

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) standing.put(point, point.getCurrentLocomotive());
        }

        Set<TileKey> squares = new LinkedHashSet<>();

        try
        {
            for (Point point : standing.keySet()) point.setLocomotive(null);

            for (Point start : starts)
            {
                start.setLocomotive(train);

                for (List<Edge> path : layout.getPossiblePaths(train, true))
                {
                    TileKey square = index.squareOf(path.get(path.size() - 1).getEnd().getName());

                    if (square != null && !square.equals(from)) squares.add(square);
                }

                start.setLocomotive(null);
            }
        }
        finally
        {
            for (Point start : starts)
            {
                if (train.equals(start.getCurrentLocomotive())) start.setLocomotive(null);
            }

            for (Map.Entry<Point, Locomotive> was : standing.entrySet()) was.getKey().setLocomotive(was.getValue());
        }

        return squares;
    }

    /** A train no station excludes and of no length, so only the track decides where it is offered. */
    private static Locomotive oracleTrain(MarklinControlStation station)
    {
        return new MarklinLocomotive(station, 1, MarklinLocomotive.decoderType.MM2, "Oracle train");
    }

    /**
     * On every scenario and the live snapshot, the stations the checker says reach nothing, and those it says nothing
     * reaches, are the ones the built Layout says so of.
     *
     * On the live snapshot the answers once differed: `1 - Main:2,5` and `1 - Main:2,7` are reached only by
     * TopMainR1Inter's copy that trains may not stop at.
     *
     * MUTATION: let `BuiltReach.startsOf` give every copy of a station, and this fails.  Letting `BuiltReach` pass a
     * Point switched off survives: no scenario has a station reached, or left, only through a closed square.
     */
    @Test
    public void testEveryScenarioIsReachedAsItsBuiltRailwayIs() throws Exception
    {
        List<String> compared = new ArrayList<>();

        for (String name : support.Scenario.names())
        {
            support.Scenario scenario = null;

            try
            {
                scenario = support.Scenario.open(name);

                AutonomySession opened = scenario.getSession();

                if (opened.getReducer() == null) continue;

                // Every station is asked, a closed one too; only those in service are judged, a closed one being
                // said to be closed instead (FR-101).
                Set<TileKey> stations = new TreeSet<>(BY_KEY);

                for (ReducedPoint point : opened.getReducer().getPoints().values())
                {
                    if (point.isStation()) stations.add(point.getTile());
                }

                if (stations.size() < 2) continue;

                Layout railway = scenario.build();
                StationIndex names = opened.getStationIndex();

                Map<TileKey, Set<TileKey>> reach = new LinkedHashMap<>();

                Locomotive train = oracleTrain(scenario.getModel());

                for (TileKey station : stations) reach.put(station, offeredFrom(railway, names, station, train));

                Set<TileKey> leaveNothing = new TreeSet<>(BY_KEY);
                Set<TileKey> reachedByNothing = new TreeSet<>(BY_KEY);

                for (TileKey station : stations)
                {
                    if (opened.shutTiles().contains(station)) continue;

                    boolean leaves = false;
                    boolean reached = false;

                    for (TileKey other : stations)
                    {
                        if (other.equals(station)) continue;

                        leaves |= reach.get(station).contains(other);
                        reached |= reach.get(other).contains(station);
                    }

                    if (!leaves) leaveNothing.add(station);
                    if (!reached) reachedByNothing.add(station);
                }

                Set<TileKey> saidToLeaveNothing = new TreeSet<>(BY_KEY);
                Set<TileKey> saidReachedByNothing = new TreeSet<>(BY_KEY);

                for (AutonomyChecks.Finding finding : opened.check())
                {
                    String key = finding.getMessageKey();

                    if (AutonomyChecks.STATION_REACHES_NOTHING.equals(key) || AutonomyChecks.TERMINUS_STRANDED.equals(key))
                    {
                        saidToLeaveNothing.add(finding.getTile());
                    }

                    if (AutonomyChecks.STATION_UNREACHABLE.equals(key)) saidReachedByNothing.add(finding.getTile());

                    // whose error already says no train sent there can leave
                    if (AutonomyChecks.STATION_AT_THE_END_OF_A_LINE.equals(key)) saidToLeaveNothing.add(finding.getTile());
                }

                assertEquals(saidToLeaveNothing.toString(), leaveNothing.toString(), name + ": the checker and the built"
                    + " railway disagree about which stations a train can be sent on from");

                assertEquals(saidReachedByNothing.toString(), reachedByNothing.toString(), name + ": the checker and the"
                    + " built railway disagree about which stations no train can be sent to");

                if ("live-snapshot".equals(name))
                {
                    assertTrue(saidReachedByNothing.toString().contains("1 - Main:2,5")
                        && saidReachedByNothing.toString().contains("1 - Main:2,7"),
                        "on the live snapshot, the two squares only a copy trains may not stop at reaches are not said to"
                            + " be unreachable: " + saidReachedByNothing);
                }

                compared.add(name);
            }
            finally
            {
                if (scenario != null) scenario.close();
            }
        }

        assertTrue(compared.contains("live-snapshot") && compared.size() > 1,
            "the sweep compared too little to mean anything: " + compared);
    }

    /**
     * The stations set to No - Nothing Can Pass, among these (FR-101).
     */
    private static Set<TileKey> closedAmong(Set<TileKey> stations)
    {
        Set<TileKey> out = new TreeSet<>(BY_KEY);

        for (TileKey station : stations)
        {
            if (session.shutTiles().contains(station)) out.add(station);
        }

        return out;
    }

    /**
     * The squares the checker reported one of AutonomyChecks' message keys against.
     */
    private static Set<TileKey> reported(String messageKey)
    {
        Set<TileKey> out = new TreeSet<>(BY_KEY);

        for (AutonomyChecks.Finding finding : findings)
        {
            if (messageKey.equals(finding.getMessageKey()) && finding.getTile() != null)
            {
                out.add(finding.getTile());
            }
        }

        return out;
    }

    private static StationIndex index()
    {
        if (index == null) index = session.getStationIndex();

        return index;
    }

    /**
     * Attaches an accessory to every switch and signal, the way syncLayouts does when the application loads a layout.
     * A diagram parsed alone has addresses on its tiles but no Accessory objects, and a switch with no accessory has no
     * position (the same helper `testAutonomyDiagramSampleLayout` needs).
     */
    private static void wireAccessories(List<LayoutDiagram> pages)
    {
        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                if (!component.isSwitch() && !component.isSignal()) continue;

                // the application skips tiles drawn without a digital address, and so does this
                if (component.getAddress() <= 0) continue;

                Accessory.accessoryType type = component.isSignal()
                    ? Accessory.accessoryType.SIGNAL : Accessory.accessoryType.SWITCH;

                component.setAccessory(accessory(component.getAddress(), type,
                    component.getProtocol()));

                if (component.isThreeWay())
                {
                    component.setAccessory2(accessory(component.getAddress() + 1,
                        Accessory.accessoryType.SWITCH, component.getProtocol()));
                }
            }
        }
    }

    /**
     * An accessory named exactly as the application would name it.
     */
    private static MarklinAccessory accessory(int logicalAddress, Accessory.accessoryType type,
        Accessory.accessoryDecoderType protocol)
    {
        return new MarklinAccessory(null, logicalAddress - 1, type, protocol,
            MarklinAccessory.getNameWithProtocol(logicalAddress, type, protocol), false, 0);
    }

    /**
     * Squares sorted by printed key, so a failure reads as a diff of two lists.
     */
    private static final Comparator<TileKey> BY_KEY = new Comparator<TileKey>()
    {
        @Override
        public int compare(TileKey a, TileKey b)
        {
            return a.toString().compareTo(b.toString());
        }
    };
}
