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
 * **The gap this closes was that `getLockEdges` appeared nowhere in `test/` at all** (suite review,
 * A101). Lock edges are exercised in `testAutonomyPathValidation` - four tests - but every one of them
 * adds the lock itself, by calling `addLockEdge` on a graph it built. Nothing asked whether
 * `Layout.fromJSON` produces any. Delete the `addLockEdge` call in its loader and the whole suite stays
 * green while every file-loaded configuration comes up with no crossing protection whatsoever: the
 * builder writes `lockedges` into the file, the file is read, and the locks are silently dropped.
 *
 * That is the worst shape a defect can have on this railway - protection that is absent rather than
 * wrong - and it would show as two trains meeting on a shared throat, which is the thing lock edges
 * exist to make impossible.
 *
 * **Both halves, and the second is the one worth having.** That the list is populated is referential
 * integrity; that a held lock actually refuses a path is the protection. A loader could pass the first
 * by attaching the edges to the wrong copy.
 *
 * MUTATION this catches: removing the `addLockEdge` call from `Layout.fromJSON` fails both assertions;
 * attaching the lock to the wrong edge fails the second.
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
        // BEFORE the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
        model.stop();

        model.newMM2Locomotive(LOC, ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null) model.deleteLoc(LOC);

        if (sandbox != null) sandbox.close();
    }

    /**
     * The locks named in the file are on the graph, and they refuse a path.
     */
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

        // AND IT PROTECTS THE TRACK, which is the half that matters.
        //
        // The other route claims the throat; the first route's path must then be refused, and refused
        // for the LOCK rather than for the edge being occupied - the two edges are different track.
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

        // The control: with the rival let go, the same path is fine.  Without this the assertion above
        // would pass for a fixture that refuses everything.
        List<Edge> path = new ArrayList<>();

        path.add(approach);

        assertTrue(layout.isPathClear(path, loc()),
            "the same path is refused with nothing holding the lock, so the test above proves "
            + "nothing: " + Layout.getLastError());
    }

    /**
     * A places list with a hole in it is not read as a shorter places list.
     *
     * **SVX-C9.**  The entries are read POSITIONALLY - the last one is the square the edge arrives at,
     * and where a train stands there its measurement is an allowance rather than track, in both
     * `walkStandingTrains` and `whyABerthCannotHoldIt`.  The loader used to skip an entry with no `at`
     * and carry on, which does not shorten the list so much as renumber it: drop the FINAL entry and
     * the tile behind the berth becomes "the berth", the allowance comes off the wrong square, and
     * every rule downstream is out by one tile on the permitting side.
     *
     * A build never writes one, so this is a hand-edited or truncated file. The answer for one is the
     * answer a file with no places at all already gets - fall back to the whole edge, which every
     * reader handles and which refuses more rather than less.
     *
     * MUTATION, run 2026-09-13: restoring `if (place == null || !place.has("at")) continue;` gives
     * this edge two places, with the wrong one last.
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

        // THE CONTROL: the same fixture with every entry named keeps its places, or the claim above
        // would pass on a loader that had stopped reading places at all.
        Layout sound = load(oneEdgeWithASoundPlacesList());

        assertEquals(sound.getEdge("LE A", "LE Throat").getPlaceIds().size(), 3,
            "the well-formed fixture did not keep its three places, so the claim above says nothing"
            + " about holes: " + sound.getEdge("LE A", "LE Throat").getPlaceIds());
    }

    /**
     * The square two hops share is charged to the train once, not twice.
     *
     * **PRW-C3.**  A standing train's body is walked back edge by edge, and the square the walk turns
     * at belongs to both of them: it is the end of the edge just left and a place on the edge behind
     * it.  Charged twice, the tail stops one square's length short of where it really reaches - and
     * the track under the back end of the train is then claimed by nobody, which is what clears a
     * second train onto it.
     *
     * **It is the same seam three rounds have now found**, from three directions: the places budget
     * and the hop budget were two arithmetics over one train, agreeing only while every square was
     * measured (SEV-B3), the standing square was ordinary track (SVX-B1), and no square belonged to
     * two hops (this). There is one budget now - what the finer walk charges IS what the hop costs -
     * and the edge's own length is kept only for a file that carries no places at all.
     *
     * **The fixture is the smallest thing with the shape:** LE Stand is reached from LE Behind over an
     * edge that names the junction among its places, and the edge out of LE Stand names the junction
     * too. A five-unit train standing at LE Stand reaches past the junction into the tile behind it.
     *
     * MUTATION, run 2026-09-13: restoring `remaining -= segment.getLength()` (or dropping the
     * already-charged test) stops `LE:behind` being claimed.
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
     * Three points in a line, with the junction named by the edges either side of it.
     *
     * `LE Stand` is where the train is; the walk leaves over `LE Stand -> LE Junction`, whose places
     * are the junction alone, and continues over `LE Behind -> LE Junction`, whose places are the tile
     * behind and the junction again.
     *
     * @return the graph JSON
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

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * Two approaches to one throat, each locking the other, as the builder emits a shared tile.
     *
     * @return the graph JSON
     */
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
