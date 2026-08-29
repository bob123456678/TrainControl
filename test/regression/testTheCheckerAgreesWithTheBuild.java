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
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;

/**
 * What the checker tells the user, against the railway the build actually emits.
 *
 * DD-A7's opening sentence is that the interface can end up telling the user one thing while the
 * railway does another, and the whole family it named is the same shape: the checker walks the REDUCED
 * graph and works out arrivals, departures and reachability for itself, while the builder walks the
 * same reduction and emits a Point per arrival side.  Two answers to one question, agreeing by having
 * been written to.  Every guard this repository had over that family read one side only - the facings
 * test checks the map the facings are built from (TA-B8), the reducer tests check the reducer - so a
 * drift between the two sides could not be seen by anything but another reading.
 *
 * This is the missing half.  It builds the frozen sample diagram, runs the checker over it, parses the
 * generated configuration into a real Layout, and asks whether the two agree about the same squares:
 *
 *   - a trapped arrival is a copy the build emits with no edge out of it;
 *   - a station that reaches nothing is one whose copies reach no other station's copies;
 *   - a station nothing reaches is one no other station's copies reach;
 *   - a facing the build gives a copy is a facing the editor offers for that square.
 *
 * The oracle is the BUILT graph - Points and Edges as parseAuto made them - not the reduction the
 * checker read.  That is what makes it an independent statement rather than the checker agreeing with
 * itself: `Layout.getNeighbors` knows nothing about arrival sides, entry sides or turn sets, only about
 * the edges the builder chose to write down.
 *
 * The fixture is `test_layout`, frozen, wired the way `testAutonomyDiagramSampleLayout` wires it - a
 * diagram parsed on its own has addresses on its tiles but no Accessory objects behind them, and
 * without them two thirds of the track does not connect.
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

    /**
     * The one square where the checker and the build genuinely disagree, and the reason is not DR-B6's.
     *
     * See testTheStationsThatReachNothingAreTheOnesTheBuildCannotRouteOutOf, which is where the
     * divergence is written out.  Named by its printed key rather than rebuilt as a TileKey so that a
     * layout change reads as "the exemption is stale" instead of as a missing square.
     */
    private static final String UNCONSTRAINED_START = "1 - Main:12,11";

    /**
     * Reads the sample diagram, opens the setup that ships with it, and builds the railway it names.
     *
     * @throws Exception when the fixture cannot be read at all, which is a broken harness rather than a
     *         failing guard and is better raised than reported as four skipped tests
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        File folder = new File("test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        wireAccessories(pages);

        session = new AutonomySession(folder);
        session.open(pages);

        assertNotNull(session.getReducer(), "the diagram did not reduce, so there is nothing to check");

        findings = session.check();

        model.parseAuto(session.buildConfiguration());

        built = model.getAutoLayout();

        assertNotNull(built, "the generated configuration produced no graph");
        assertTrue(built.isValid(), "the generated configuration is invalid: " + Layout.getLastError());

        // The premise every assertion below rests on.  A fixture that reduced to a handful of Points -
        // an unwired diagram does exactly that - would let all four tests pass on almost nothing
        assertTrue(session.getReducer().getEdges().size() > 50,
            "only " + session.getReducer().getEdges().size() + " edges were derived.  The sample "
                + "layout reduces to about ninety; a number this low means the diagram did not wire "
                + "up, and every agreement below is being asserted about track that is not there");
    }

    /**
     * A square the checker calls trapped is a square the build emits a Point it cannot leave.
     *
     * The checker's own walk, before this class existed: gather the arrival sides and departure sides
     * of a square from the reduced edges, and for each arrival ask the tile graph whether any departure
     * lies onward of it.  The builder asks the same question in `nodesFor` and answers it by NOT
     * emitting a way out - the copy is written with no edge leaving it, deliberately, "because the
     * alternative is emitting nothing and losing the sensor from the graph entirely; that case is what
     * the trapped-arrival check exists to put in front of the user".
     *
     * So the two are one rule with two authors, and this is what makes them one rule with a witness.
     *
     * Two squares are excluded, both because the checker cannot be about them:
     *   - a square with no arrival side at all is a sensor whose every connection has been closed.  It
     *     is emitted whole, with no way out and no way in, and `checkIsolatedPoints` is the finding
     *     that names it;
     *   - a square where trains may turn round gets a turning copy of every arrival, so an arrival with
     *     no way forward is not a trap there - it is the turn.  The checker skips them for the same
     *     reason.
     *
     * Mutation this must fail: in `AutonomySession.check`, delete the `if (isTurnAround(tile))
     * continue;` that opens the trapped walk, so the squares where turning is allowed are judged like
     * everything else.  Run 2026-08-25: 1 of 5 tests in this class fails - the checker starts naming
     * the parking berths, whose only copy is the turning one, as places a train cannot leave.
     *
     * TWO MUTATIONS IT DOES NOT CATCH, recorded because a guard's blind spots are worth knowing.
     * Replacing the `graph.exits` filter with `onwards.addAll(departures);`, and deleting
     * `onwards.remove(arrival);`, both leave all 5 green (run 2026-08-25).  Both relax the test for
     * "is there anywhere onward", and on this layout every trapped square has NO outgoing reduced edge
     * at all - so `departures` is empty and the relaxation changes nothing.  The case the exits filter
     * was added for, a double curve whose one arm dead-ends while the other carries traffic, is not
     * drawn on the sample diagram.  Covering it needs a hand-built square, not a bigger sweep here.
     */
    @Test
    public void testEveryTrappedArrivalIsACopyTheBuildCannotLeave()
    {
        Set<TileKey> reported = new TreeSet<>(BY_KEY);

        for (AutonomyChecks.Finding finding : findings)
        {
            if (AutonomyChecks.ARRIVAL_TRAPPED.equals(finding.getMessageKey()))
            {
                reported.add(finding.getTile());
            }
        }

        Set<TileKey> emitted = new TreeSet<>(BY_KEY);

        for (Point point : built.getPoints())
        {
            if (!built.getNeighbors(point).isEmpty()) continue;

            TileKey square = index().squareOf(point.getName());

            if (square == null) continue;

            // not split at all, so there is no arrival for a train to be trapped by
            if (session.arrivalSides(square).isEmpty()) continue;

            // the turning copy is the answer to a dead end, not a report of one
            if (session.isTurnAround(square)) continue;

            emitted.add(square);
        }

        assertFalse(reported.isEmpty(), "the sample layout has trapped arrivals - ten of them - so a "
            + "run finding none is a harness fault, not a clean setup");

        assertEquals(reported.toString(), emitted.toString(),
            "the checker and the build disagree about which squares a train can arrive at and not "
                + "leave.  The checker walks the reduced edges; the build emits a copy with no way "
                + "out.  One rule, two authors (DD-A7)");
    }

    /**
     * A station the checker says nothing can reach is one no other station's copies reach.
     *
     * The reachability the checker uses is `GraphReducer.reachableTiles`, which walks the reduction
     * carrying the side each square was arrived by.  The oracle here is a plain breadth-first walk over
     * the BUILT edges from every copy of the starting square - no arrival sides, no turn sets, nothing
     * but the edges the builder wrote - which is the graph a dispatched train is actually routed over.
     *
     * Mutation this must fail: in `AutonomyChecks.checkStations`, invert the reverse direction so a
     * station is reported when something CAN reach it - `if (reachable)` in place of `if (!reachable)`.
     * Run 2026-08-25: 1 of 5 tests in this class fails, this one.
     */
    @Test
    public void testTheStationsNothingReachesAreTheOnesTheBuildCannotRouteTo()
    {
        Map<TileKey, Set<TileKey>> reach = reachFromEachStation();

        // Its two siblings each prove their population is not trivially empty before comparing -
        // `testDeadEndArrivalsAgree` at :195 asserts ten trapped arrivals, and
        // `testTheStationsThatReachNothingAreTheOnesTheBuildCannotRouteOutOf` above asserts a KNOWN
        // stranded station is found. This one had neither: MUTATION that survived, per TST-B18 - make
        // `AutonomyChecks.checkStations` never report STATION_UNREACHABLE, on the assumption the
        // frozen fixture has none reachable-checked to begin with. Whether it does was unverified by
        // the review that found this; this floor is the part worth adding regardless of the answer,
        // because without it "both sets are empty" and "the checker is silently broken" look identical.
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

        assertEquals(reported(AutonomyChecks.STATION_UNREACHABLE).toString(), unreached.toString(),
            "the checker and the build disagree about which stations nothing can reach.  A station "
                + "the checker calls reachable and the built graph cannot route to is a destination "
                + "the user will be offered and the railway will refuse");
    }

    /**
     * A station the checker says reaches nothing is one whose copies reach no other station.
     *
     * The stranded and the reaches-nothing findings are one verdict said two ways - a terminus that
     * cannot be left is named separately because a train sent there is stuck - so both are read here.
     *
     * ONE SQUARE IS EXEMPT, and the exemption is the finding this test was written to surface.
     * `GraphReducer.reachableTiles` starts its walk with no arrival side - deliberately, and its
     * javadoc says why: "the train is already standing there, and the question is whether the TRACK
     * allows the journey onward.  Same as findPath's start state."  But a train standing at a square
     * is standing on one of its COPIES, and every copy carries the side it arrived by.  Where a square
     * has a single copy, the unconstrained start lets the walk leave by a side that copy cannot leave
     * by, and the checker then believes in a journey no train can make.
     *
     * `1 - Main:12,11` (BottomSecondary) is that square here.  Its one copy arrives by the east side,
     * so it cannot depart east; the built graph takes it west to a trapped copy of TunnelPre and no
     * further, and it therefore reaches no station at all.  The checker leaves eastward and finds
     * nineteen more squares, so it says nothing.  Not fixed here: constraining the start would put
     * `reachableTiles` at odds with `findPath`, which starts the same way and which
     * `testReachableTilesDoesNotCrossADoubleCurve` pins to it.  Reported instead, and the exemption is
     * asserted to be still needed so that fixing it fails this test rather than outliving it.
     *
     * Mutation this must fail: in `AutonomyChecks.checkStations`, invert the forward direction so a
     * station is reported when it CAN reach another - `if (reachesAStation)` in place of
     * `if (!reachesAStation)`.  Run 2026-08-25: 1 of 5 tests in this class fails, this one.
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

        TileKey exempt = null;

        for (TileKey station : stranded)
        {
            if (UNCONSTRAINED_START.equals(station.toString())) exempt = station;
        }

        assertNotNull(exempt, "the built graph can now route out of " + UNCONSTRAINED_START
            + ", so the unconstrained-start divergence this test exempts has either been fixed or the "
            + "layout has changed.  Delete the exemption rather than moving it");

        stranded.remove(exempt);

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
     * `facingChoices` offers where a train could be sent ONWARD from a square, so it never offers an
     * arrival side. A train that turned round is pointing back at the side it came in by - so on a
     * berth whose only copy is the turning one, the single facing the square can actually hold was
     * reported as impossible.
     *
     * Sixteen squares on this fixture are of that shape - every parking berth plus four reversing
     * points - and it appears on the railway the first time autonomy parks a train in one, because
     * `captureFromLayout` writes that facing back.
     *
     * **This test exists because the fix shipped without one.** The carve-out was written into
     * {@link #testEveryFacingTheBuildEmitsIsOneTheEditorOffers} when the arrival-sides consolidation
     * was checked, and into the production check afterwards - and a validation pass then found that
     * deleting it from the production check left every class green, because nothing anywhere
     * referenced FACING_IMPOSSIBLE. A behaviour change with no guard is one that can be undone by
     * accident.
     *
     * It lives here rather than beside the session's other tests because their fixture is a synthetic
     * three-square page with no berth on it - there is no square there that a train can turn round on,
     * so the case cannot be built.
     *
     * MUTATION: removing the `isTurnAround(...) && arrivalSides(...).contains(recorded)` carve-out
     * from `AutonomySession.facingsThatCannotBeHeld` fails this test.
     */
    @Test
    public void testATrainParkedWhereItTurnedRoundIsNotAnImpossibleFacing()
    {
        TileKey berth = null;
        Side turned = null;

        // The first square that is a berth in the sense this is about: a train may turn round on it,
        // and the side it would then face is one facingChoices does not offer.
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (!session.isTurnAround(tile)) continue;

            for (Side arrival : session.arrivalSides(tile))
            {
                if (session.facingChoices(tile).contains(arrival)) continue;

                berth = tile;
                turned = arrival;

                break;
            }

            if (berth != null) break;
        }

        assertNotNull(berth,
            "this fixture has no square a train can turn round on whose turned facing is not also "
            + "offered onward - so the case the carve-out exists for cannot be built here, and this "
            + "test would pass by testing nothing");

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
     *
     * @return its name
     */
    private static String firstLocomotive()
    {
        return model.getLocList().get(0);
    }

    /**
     * Every facing the build gives a copy is a facing the editor would have offered for that square.
     *
     * The pair kept in step by a sentence.  `AutonomySession.onwardFrom` walks `getRoutes(tile)` to
     * decide what the menu offers; `AutonomyBuilder.facingOf` walks the same routes to decide what a
     * copy is called and which copy a placement lands on, and the session's javadoc asserts that the
     * two "now answer alike" with nothing behind the claim.  When they drift the menu offers a facing
     * the build has no copy for, which is MT-125's own defect class: a curve offering the compass
     * opposite of the arrival, a direction that square has no track in.
     *
     * Two shapes are accepted, because the build emits two kinds of copy:
     *   - a plain copy is a train that arrived and carried on, and its facing is the far end of the
     *     track it came in on - exactly what `facingChoices` offers;
     *   - a turning copy is a train that turned round, and it is pointing back at the side it came in
     *     by - which is an arrival side of the square, and never offered as a facing because a train
     *     standing there has not turned round yet.
     *
     * Mutation this must fail: in `AutonomyBuilder.facingOf`, answer the compass rule instead of the
     * track - `if (true) return node.arrival.opposite();` before the route loop.  Run 2026-08-25: 1 of
     * 5 tests in this class fails, naming the curved sensors whose two ends are not opposite each
     * other.  Note that `testFacingFollowsTheTrack` cannot catch this one: it reads `facingChoices`,
     * which is the session's side of the pair, and says nothing about what the build emits.
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

                // the turning copy, pointing back the way it came
                if (arrivals.contains(copy.getValue())) continue;

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
     * The arrival sides of a square are the entry sides of the edges that end on it.
     *
     * The invariant the DR-B6 consolidation rests on.  Three inline copies of this walk lived in
     * `AutonomySession` - the pointless-turn check, the trapped-arrival check and `facingChoices` -
     * each skipping edges with a null entry side, while the door they should have used
     * (`arrivalSides` to `StationIndex` to `AutonomyBuilder.arrivalSidesOf` to `splitSides`) BAILS on
     * the whole square when it meets one.  The two rules differ only on a square an edge lands at
     * having arrived by no side of the grid.
     *
     * That cannot presently happen, and this test is where that is written down: an edge ends at a
     * Point, a Point is a feedback tile (`GraphReducer.buildPoints` reads `getFeedbackTiles`), and a
     * null entry side comes only from `TileGraph.landing` returning a portal's partner - which is a
     * TUNNEL or a LINK and so never a feedback tile.  The consolidation is therefore behaviour-
     * preserving, and stays so only while this holds.
     *
     * Mutation this must fail: in `StationIndex.arrivalSidesAt`, answer with the first side only -
     * `if (sides != null && sides.size() > 1) sides = sides.subList(0, 1);`.  Run 2026-08-25: 2 of 5
     * tests in this class fail, this one and the trapped-arrival one, which is the pair of them
     * saying that the three consolidated checks now genuinely read this door.
     *
     * A MUTATION IT DOES NOT CATCH, and that is the point: in `AutonomyBuilder.splitSides`, bail on a
     * side rather than on the square - `continue;` in place of `return Collections.emptyList();`,
     * which is the other reading of the same rule and the one the three inline loops had.  Run
     * 2026-08-25: all 5 stay green, because no edge on this layout arrives by no side.  The sweep
     * above is what says so, and it is why the consolidation could be made at all.
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
     * The squares each station's copies can reach, over the built edges alone.
     *
     * @return station square to every square reachable from any of its copies, itself included
     */
    private static Map<TileKey, Set<TileKey>> reachFromEachStation()
    {
        Map<TileKey, Set<TileKey>> out = new LinkedHashMap<>();

        for (ReducedPoint point : session.getReducer().getPoints().values())
        {
            if (point.isStation()) out.put(point.getTile(), walk(point.getTile()));
        }

        assertTrue(out.size() > 10, "only " + out.size() + " stations, so reachability proves little");

        return out;
    }

    /**
     * A breadth-first walk of the built graph from every copy of one square.
     *
     * Deliberately ignorant of everything the checker knows.  It does not ask whether a Point is a
     * destination, which side a train arrived by, or whether trains may turn round: the builder has
     * already written all of that into which edges exist, and re-reading it here would make this the
     * checker's own answer wearing a different hat.
     *
     * @param from the square to start from
     * @return every square reached, including the starting one
     */
    private static Set<TileKey> walk(TileKey from)
    {
        Set<String> seen = new LinkedHashSet<>();
        Deque<Point> queue = new ArrayDeque<>();

        for (Point point : index().pointsAt(built, from))
        {
            if (seen.add(point.getName())) queue.add(point);
        }

        Set<TileKey> squares = new LinkedHashSet<>();

        while (!queue.isEmpty())
        {
            Point point = queue.remove();

            TileKey square = index().squareOf(point.getName());

            if (square != null) squares.add(square);

            for (Edge edge : built.getNeighbors(point))
            {
                if (seen.add(edge.getEnd().getName())) queue.add(edge.getEnd());
            }
        }

        return squares;
    }

    /**
     * @param messageKey one of AutonomyChecks' message keys
     * @return the squares the checker reported it against
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

    /**
     * @return the square-to-Point translation of the setup as it stands
     */
    private static StationIndex index()
    {
        if (index == null) index = session.getStationIndex();

        return index;
    }

    /**
     * Attaches an accessory to every switch and signal, the way syncLayouts does when the application
     * loads a layout.
     *
     * The same helper `testAutonomyDiagramSampleLayout` needs, for the same reason: parseLayout uses
     * the accessory database only to pick a decoder protocol, so a diagram parsed on its own has
     * addresses on its tiles and no Accessory objects behind them - and a switch with no accessory has
     * no position, so most of the track does not connect.
     *
     * @param pages the parsed diagram
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
     * @param logicalAddress the address drawn on the tile
     * @param type switch or signal
     * @param protocol the decoder protocol the tile was drawn with
     * @return an accessory named exactly as the application would name it
     */
    private static MarklinAccessory accessory(int logicalAddress, Accessory.accessoryType type,
        Accessory.accessoryDecoderType protocol)
    {
        return new MarklinAccessory(null, logicalAddress - 1, type, protocol,
            MarklinAccessory.getNameWithProtocol(logicalAddress, type, protocol), false, 0);
    }

    /**
     * Squares sorted by their printed key, so a failure reads as a diff of two lists rather than as
     * two sets in whatever order they happened to be built in.
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
