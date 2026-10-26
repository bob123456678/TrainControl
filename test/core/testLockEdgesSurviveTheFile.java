package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A configuration loaded from a file arrives with its lock edges, and they protect the track.
 *
 * `getLockEdges` appeared nowhere in `test/`: `testAutonomyPathValidation` adds the lock itself on a graph it built, so
 * nothing asked whether `Layout.fromJSON` produces any.  Delete the `addLockEdge` call in its loader and the suite
 * stays green while every file-loaded configuration comes up with no crossing protection, the builder's `lockedges`
 * silently dropped: protection absent rather than wrong, shown as two trains meeting on a shared throat.
 *
 * **Both halves, and the second is the one worth having:** that the list is populated is referential integrity; that a
 * held lock actually refuses a path is the protection, and a loader could pass the first by attaching the edges to the
 * wrong copy.
 *
 * MUTATION this catches: removing the `addLockEdge` call from `Layout.fromJSON` fails both assertions; attaching the
 * lock to the wrong edge fails the second.
 *
 * @author Adam
 */
public class testLockEdgesSurviveTheFile
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static final String LOC = "LE runner";

    private static final int ADDRESS = 78;

    /** Sensor numbers of this class's own, so it can run beside the other graph suites. */
    private static final int S88_BASE = 8790;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: init reads the layout preference and would otherwise open Adam's own railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();
        model.stop();

        model.newMM2Locomotive(LOC, ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null) model.deleteLoc(LOC);

        if (sandbox != null) sandbox.close();
    }

    /** The locks named in the file are on the graph, and they refuse a path. */
    @Test
    public void testALockEdgeInTheFileReachesTheGraphAndProtectsTheTrack() throws Exception
    {
        Layout layout = load(twoRoutesOverOneThroat());

        Edge approach = layout.getEdge("LE A", "LE Throat");

        assertNotNull(approach, "the fixture did not take: LE A -> LE Throat is not on the graph");

        List<Edge> locks = approach.getLockEdges();

        assertFalse(locks.isEmpty(),
            "the edge came out of the file with no lock edges at all, so every crossing protection "
            + "the builder writes is being dropped as the configuration loads");

        List<String> names = new ArrayList<>();

        for (Edge e : locks) names.add(e.getName());

        assertTrue(names.contains(layout.getEdge("LE B", "LE Throat").getName()),
            "the lock did not name the edge the file named: " + names);

        // AND IT PROTECTS THE TRACK, the half that matters: the other route claims the throat, so the first route's
        // path must be refused for the LOCK rather than for the edge being occupied (the two edges are different
        // track).
        Edge rival = layout.getEdge("LE B", "LE Throat");

        rival.setOccupied();

        try
        {
            List<Edge> path = new ArrayList<>();

            path.add(approach);

            assertFalse(layout.isPathClear(path, loc()),
                "a path over an edge whose lock edge is held was allowed, so the lock arrived on the "
                + "graph and enforces nothing: " + Layout.getLastError());
        }
        finally
        {
            rival.setUnoccupied();
        }

        // The control: with the rival let go, the same path is fine; otherwise the assertion above would pass for a
        // fixture that refuses everything.
        List<Edge> path = new ArrayList<>();

        path.add(approach);

        assertTrue(layout.isPathClear(path, loc()),
            "the same path is refused with nothing holding the lock, so the test above proves "
            + "nothing: " + Layout.getLastError());
    }

    /**
     * A places list with a hole in it is not read as a shorter places list.  The entries are read POSITIONALLY: the
     * last is the square the edge arrives at, where a standing train's measurement is an allowance rather than track
     * (`walkStandingTrains`, `whyABerthCannotHoldIt`).  The loader used to skip an entry with no `at`, which renumbers
     * the list: drop the FINAL entry and the tile behind the berth becomes "the berth", and every rule downstream is
     * out by one tile on the permitting side.
     *
     * A build never writes one, so this is a hand-edited or truncated file.  The answer is the one a file with no
     * places gets: fall back to the whole edge, which refuses more rather than less.
     *
     * MUTATION: restoring `if (place == null || !place.has("at")) continue;` gives this edge two places, with the wrong
     * one last.
     */
    @Test
    public void testAPlacesListWithAHoleIsNotUsedAtAll() throws Exception
    {
        Layout layout = load(oneEdgeWithABrokenPlacesList());

        Edge described = layout.getEdge("LE A", "LE Throat");

        assertNotNull(described, "the fixture did not take: LE A -> LE Throat is not on the graph");

        assertTrue(described.getPlaceIds().isEmpty(),
            "an edge whose places list has an entry with no square named came back describing "
            + described.getPlaceIds().size() + " places: " + described.getPlaceIds() + ". These are"
            + " read by position - the last one is the square a train stands on, and its measurement"
            + " is an allowance rather than track - so a list with a hole in it is not a shorter list,"
            + " it is a list where the wrong tile is the berth. The whole edge is the honest answer,"
            + " and it is the one an old configuration without places already gets.");

        // THE CONTROL: the same fixture with every entry named keeps its places, or the claim above would pass on a
        // loader that stopped reading places.
        Layout sound = load(oneEdgeWithASoundPlacesList());

        assertEquals(sound.getEdge("LE A", "LE Throat").getPlaceIds().size(), 3,
            "the well-formed fixture did not keep its three places, so the claim above says nothing"
            + " about holes: " + sound.getEdge("LE A", "LE Throat").getPlaceIds());
    }

    /**
     * The square two hops share is charged to the train once, not twice.  A standing train's body is walked back edge
     * by edge, and the square the walk turns at is the end of the edge just left and a place on the edge behind it;
     * charged twice, the tail stops one square's length short and the track under the back end of the train is claimed
     * by nobody, which clears a second train onto it.
     *
     * There is one budget: what the finer walk charges IS what the hop costs, and the edge's own length is kept only
     * for a file with no places.  The fixture is the smallest thing with the shape: LE Stand is reached from LE Behind
     * over an edge that names the junction among its places, and the edge out of LE Stand names it too.  A five-unit
     * train at LE Stand reaches past the junction into the tile behind it.
     *
     * MUTATION: restoring `remaining -= segment.getLength()` (or dropping the already-charged test) stops `LE:behind`
     * being claimed.
     */
    @Test
    public void testASquareOnTwoHopsIsChargedOnce() throws Exception
    {
        Layout layout = load(aTailAcrossAJunction());

        Point stand = layout.getPoint("LE Stand");

        assertNotNull(stand, "the fixture did not take: there is no LE Stand");

        MarklinLocomotive train = loc();

        Integer was = train.getTrainLength();

        try
        {
            train.setTrainLength(5);

            stand.setLocomotive(train);

            java.util.Map<String, org.traincontrol.base.Locomotive> claimed =
                layout.placesCoveredByStandingTrains();

            assertTrue(claimed.containsKey("LE:junction"),
                "the train does not even reach the junction it is standing next to, so the fixture is"
                + " not the case this claim is about: " + claimed.keySet());

            assertTrue(claimed.containsKey("LE:behind"),
                "a five-unit train stands at LE Stand, spends 3 reaching the junction and has 2 left"
                + " for the two-unit tile behind it - and that tile is claimed by nobody. The junction"
                + " belongs to both hops, and charging it twice spends 6 of a 5-unit train: the walk"
                + " stops early and the metal under the train's back end is left clear for somebody"
                + " else. Claimed: " + claimed.keySet());
        }
        finally
        {
            train.setTrainLength(was);

            stand.setLocomotive(null);
        }
    }

    /**
     * Three points in a line, with the junction named by the edges either side of it: the walk leaves over `LE Stand ->
     * LE Junction` and continues over `LE Behind -> LE Junction`.
     */
    private static String aTailAcrossAJunction()
    {
        return ("{'points': ["
            + station("LE Stand", 4) + "," + station("LE Junction", 5) + ","
            + station("LE Behind", 6)
            + "],'edges': ["
            + "{'start': 'LE Stand', 'end': 'LE Junction', 'length': 3, 'places': ["
            + "{'at': 'LE:junction', 'length': 3}]},"
            + "{'start': 'LE Behind', 'end': 'LE Junction', 'length': 5, 'places': ["
            + "{'at': 'LE:behind', 'length': 2},{'at': 'LE:junction', 'length': 3}]}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');
    }

    /**
     * A square whose placement the loader dropped hands no tail to whoever reserves it next.  A file outliving the
     * fleet is a case Adam ruled on ("drop the train and keep the rest"), and the drop promised that the next save
     * writes the file back without the phantom, but the side and the road were written onto the square anyway and
     * reloaded on every start.  `Point.reserve` sets an occupant and nothing else, so the first locked path ending
     * there gave the reserving train the file's side and road, and `walkStandingTrains` claimed track along a road that
     * train never drove.
     *
     * Three doors, three claims: the load does not apply a side to an empty square, the save does not write one, and a
     * reservation clears whatever it found.
     */
    @Test
    public void testADroppedPlacementLeavesNoTailBehind() throws Exception
    {
        Layout layout = load(aSquareWhoseTrainHasGone());

        Point stand = layout.getPoint("LE Stand");

        assertNull(stand.getCurrentLocomotive(),
            "precondition: the locomotive the file names does not exist, so the loader should have"
            + " dropped the placement and left the square empty");

        assertNull(stand.getArrivedFrom(),
            "the square came out of the file with the side a train that is no longer in the database"
            + " arrived by.  Nothing stands there, and the next thing to reserve the square inherits"
            + " it (AUR-C1)");

        assertNull(stand.getArrivedAlong(),
            "and with the road that train came along, which is what the tail walk follows");

        // THE RECORD.  THE SIDE IS PUT BACK FIRST, which makes this a test of the SAVE: asserted straight after the
        // assertion above, the field is null already, so `toJSON` could not write it whatever it does.  Set on the
        // empty square, the gate is the only thing that can keep it out.
        stand.setArrivedFrom("LE Junction");

        assertFalse(layout.toJSON().toString().contains("arrivedFrom"),
            "the layout wrote back a side for a square with nobody on it, so a reload rebuilds the"
            + " same state: " + layout.toJSON());

        stand.setArrivedFrom(null);

        // AND THE LIVE DOOR, which is the one that mends every future way of leaving a side behind.
        stand.setArrivedFrom("LE Junction");

        java.lang.reflect.Method reserve = Point.class.getDeclaredMethod(
            "reserve", org.traincontrol.base.Locomotive.class);

        reserve.setAccessible(true);
        reserve.invoke(stand, loc());

        assertNull(stand.getArrivedFrom(),
            "a reservation took the square over and kept the side left on it by somebody else."
            + "  `setLocomotive` clears both on a change of occupant and this door did not, so the"
            + " reserving train's tail is walked back along a road it has never driven (AUR-C1)");

        // THE SAME INSTANCE BOTH TIMES, which makes this a control: the guard in `assign` is a reference comparison, so
        // a `loc()` that built a new object each call would fail here for a reason unrelated to the rule.
        assertSame(loc(), loc(), "precondition: loc() no longer returns one instance, so the control that follows"
            + " is not testing the same-occupant case at all");

        // THE CONTROL: the train that is ALREADY there keeps the tail it really has.
        stand.setArrivedFrom("LE Junction");

        reserve.invoke(stand, loc());

        assertEquals(stand.getArrivedFrom(), "LE Junction",
            "reserving a square for the train already standing on it threw away its real tail");
    }

    // Fixtures

    /** Two approaches to one throat, each locking the other, as the builder emits a shared tile. */
    private static String twoRoutesOverOneThroat()
    {
        return ("{'points': ["
            + station("LE A", 0) + "," + station("LE B", 1) + ","
            + station("LE Throat", 2) + "," + station("LE Out", 3)
            + "],'edges': ["
            + "{'start': 'LE A', 'end': 'LE Throat', 'lockedges': ["
            + "{'start': 'LE B', 'end': 'LE Throat'}]},"
            + "{'start': 'LE B', 'end': 'LE Throat', 'lockedges': ["
            + "{'start': 'LE A', 'end': 'LE Throat'}]},"
            + "{'start': 'LE Throat', 'end': 'LE Out'},"
            + "{'start': 'LE Out', 'end': 'LE A'}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');
    }

    /** One described edge whose LAST place forgot to say which square it is. */
    private static String oneEdgeWithABrokenPlacesList()
    {
        return placesFixture("{'at': 'P:1,1', 'length': 2},"
            + "{'at': 'P:1,2', 'length': 3},"
            + "{'length': 4}");
    }

    /** One square holding a locomotive the database has never heard of, with a side and a road. */
    private static String aSquareWhoseTrainHasGone()
    {
        return ("{'points': ["
            + "{'name': 'LE Stand', 'station': true, 's88': " + (S88_BASE + 4)
            + ", 'loc': {'name': 'LE ghost that is not in the database'}"
            + ", 'arrivedFrom': 'LE Junction'"
            + ", 'arrivedAlong': [['LE Behind', 'LE Junction'], ['LE Junction', 'LE Stand']]},"
            + station("LE Junction", 5) + "," + station("LE Behind", 6)
            + "],'edges': ["
            + "{'start': 'LE Behind', 'end': 'LE Junction', 'length': 5},"
            + "{'start': 'LE Junction', 'end': 'LE Stand', 'length': 3},"
            + "{'start': 'LE Stand', 'end': 'LE Junction', 'length': 3}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');
    }

    /** The same fixture with nothing missing, so the claim above has a control. */
    private static String oneEdgeWithASoundPlacesList()
    {
        return placesFixture("{'at': 'P:1,1', 'length': 2},"
            + "{'at': 'P:1,2', 'length': 3},"
            + "{'at': 'P:1,3', 'length': 4}");
    }

    private static String placesFixture(String places)
    {
        return ("{'points': ["
            + station("LE A", 0) + "," + station("LE B", 1) + ","
            + station("LE Throat", 2) + "," + station("LE Out", 3)
            + "],'edges': ["
            + "{'start': 'LE A', 'end': 'LE Throat', 'places': [" + places + "]},"
            + "{'start': 'LE Throat', 'end': 'LE Out'},"
            + "{'start': 'LE Out', 'end': 'LE A'}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');
    }

    private static String station(String name, int s88Offset)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (S88_BASE + s88Offset) + "}";
    }

    private static Layout load(String config)
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration produced no graph");

        assertTrue(layout.isValid(),
            "precondition: the test graph must parse - " + Layout.getLastError());

        return layout;
    }

    private static MarklinLocomotive loc()
    {
        return model.getLocByName(LOC);
    }
}
