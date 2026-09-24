package core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The end of a run gives back only the track that run still holds - whatever Atomic Routes says by then (GUI-A1).
 *
 * With Atomic Routes off, a run gives each edge back as its tail clears it, and another train may take it.  The unlock
 * at the end chose how to give track back by reading the setting AT THE END, so a run that started non-atomic and
 * finished atomic took the atomic road over edges it had already given back: it lowered the claim of the train that
 * had taken each one since, and emptied the Points that train held.  The setting can change under a run: the Atomic
 * Routes gate switches it on when a train turns out to have no length, and two of its doors - Execute Timetable and the
 * commands panel's hand dispatch - are reached while trains are running.
 *
 * **The state is set up directly**, as a finished non-atomic run leaves it: train A's path locked, its first edge given
 * back early and taken by train B, the setting then switched on, and A's run unlocked.
 *
 * MUTATION: have `unlockPath` choose by the setting alone again and the claim fails.
 *
 * @author Adam
 */
public class testAnUnlockGivesBackOnlyWhatItHolds
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    private static int addresses = 48700;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /**
     * Train B's claim on the edge A gave back early survives A's unlock after the setting was switched on.
     *
     * @throws Exception from reflection or the fixture
     */
    @Test
    public void testAnEdgeGivenBackEarlyIsNotGivenBackAgain() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setAtomicRoutes(false);

        Point s1 = point(layout, "U1");
        Point s2 = point(layout, "U2");
        Point s3 = point(layout, "U3");
        Point s4 = point(layout, "U4");

        Edge first = layout.createEdge(s1.getName(), s2.getName());
        Edge second = layout.createEdge(s2.getName(), s3.getName());
        Edge third = layout.createEdge(s3.getName(), s4.getName());

        List<Edge> path = Arrays.asList(first, second, third);

        Locomotive a = model.getLocByName(model.getLocList().get(0));
        Locomotive b = model.getLocByName(model.getLocList().get(1));

        // A'S RUN, LOCKED: every edge claimed once, the Points ahead held for it.
        for (Edge e : path) e.setOccupied();

        s1.setLocomotive(a);
        s2.setLocomotive(a);
        s3.setLocomotive(a);
        s4.setLocomotive(a);

        // ITS TAIL CLEARED THE FIRST EDGE, which it gave back - as a non-atomic run does.
        first.setUnoccupied();
        s1.setLocomotive(null);
        s2.setLocomotive(null);

        clearedEdges(layout).put(a, new java.util.HashSet<>(Arrays.asList(first)));
        runMap(layout, "releasedEarly").put(a, new java.util.HashSet<>(Arrays.asList(first)));

        // AND B TOOK IT, with the Point at its end.
        first.setOccupied();
        s2.setLocomotive(b);

        assertEquals(occupancy(first), 1, "precondition: B's claim on the first edge is not the only one on it");

        // THE SETTING SWITCHED ON UNDER THE RUN - the Atomic Routes gate's write - and A arrives.
        layout.setAtomicRoutes(true);

        layout.unlockPath(path, a);

        assertEquals(occupancy(first), 1, "A's unlock gave back the first edge a second time - it had already given it"
            + " back when its tail cleared it, and B has held it since.  The run began with Atomic Routes off and ended"
            + " with it on, and the unlock read the setting at the end (GUI-A1)");

        assertSame(s2.getCurrentLocomotive(), b, "A's unlock emptied U2, which B holds");

        // AND WHAT A STILL HELD IS GIVEN BACK, which is what an unlock is for.
        assertEquals(occupancy(second), 0, "A's unlock did not give back an edge it still held");
        assertEquals(occupancy(third), 0, "A's unlock did not give back an edge it still held");
    }

    /**
     * An atomic run gives everything back at its end, though its tail has passed edges on the way (GUI-A1's own fix).
     *
     * The set of edges a tail has cleared is kept in atomic mode too - routes read it to know which accessories behind a
     * train may move - but nothing in it is released until the end.  The first repair chose how to unlock by whether that
     * set was empty, so an atomic run whose tail had passed anything kept those edges, and their locks, for good: the
     * next leg of a Return Home plan was refused on a lock edge nobody held.
     *
     * MUTATION: choose the careful road by the cleared set rather than by what was released, and this fails.
     *
     * @throws Exception from reflection or the fixture
     */
    @Test
    public void testAnAtomicRunGivesBackEverythingItHeld() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setAtomicRoutes(true);

        Point s1 = point(layout, "V1");
        Point s2 = point(layout, "V2");
        Point s3 = point(layout, "V3");

        Edge first = layout.createEdge(s1.getName(), s2.getName());
        Edge second = layout.createEdge(s2.getName(), s3.getName());

        List<Edge> path = Arrays.asList(first, second);

        Locomotive a = model.getLocByName(model.getLocList().get(0));

        for (Edge e : path) e.setOccupied();

        s1.setLocomotive(a);
        s2.setLocomotive(a);
        s3.setLocomotive(a);

        // ITS TAIL PASSED THE FIRST EDGE, recorded and - atomic - not released.
        clearedEdges(layout).put(a, new java.util.HashSet<>(Arrays.asList(first)));

        layout.unlockPath(path, a);

        assertEquals(occupancy(first), 0, "an atomic run's unlock kept an edge its tail had passed - held for good, with"
            + " its locks, and the next route over it refused on a claim nobody holds");
        assertEquals(occupancy(second), 0, "an atomic run's unlock kept an edge it held");

        // AND THE POINTS BEHIND THE TRAIN.  Taken down the careful road instead - chosen when the cleared set is read
        // as what was given back - the edges come free and the Points this run held behind the train keep it, so
        // nothing can be routed onto them again.
        assertEquals(s1.getCurrentLocomotive(), null, "an atomic run's unlock left the train on V1, behind it");
        assertEquals(s2.getCurrentLocomotive(), null, "an atomic run's unlock left the train on V2, behind it");
    }

    // ---------------------------------------------------------------------------------------------

    private static Point point(Layout layout, String name) throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(addresses++, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint(name + addresses, true, sensor.getName());

        return layout.getPoint(name + addresses);
    }

    @SuppressWarnings("unchecked")
    private static Map<Locomotive, Set<Edge>> runMap(Layout layout, String name) throws Exception
    {
        java.lang.reflect.Field field = Layout.class.getDeclaredField(name);

        field.setAccessible(true);

        return (Map<Locomotive, Set<Edge>>) field.get(layout);
    }

    @SuppressWarnings("unchecked")
    private static Map<Locomotive, Set<Edge>> clearedEdges(Layout layout) throws Exception
    {
        java.lang.reflect.Field field = Layout.class.getDeclaredField("clearedEdges");

        field.setAccessible(true);

        return (Map<Locomotive, Set<Edge>>) field.get(layout);
    }

    private static int occupancy(Edge edge) throws Exception
    {
        java.lang.reflect.Field field = Edge.class.getDeclaredField("occupancy");

        field.setAccessible(true);

        return field.getInt(edge);
    }
}
