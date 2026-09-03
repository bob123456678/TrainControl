package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
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
