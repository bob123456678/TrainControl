package core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A train turned where it stands is not sent out into another train's tail (OB-285).
 *
 * `Layout.isPathClear` asks three questions of each piece of track a route runs over - whose tail was traced along it,
 * whose tail lies on track sharing its metal, whose tail lies on the squares it runs over - and it stopped at the first
 * answer.  Where that answer was the moving train itself, the piece was passed and the other two were never asked.  That
 * is every train turned where it stands - reversed on the throttle, turned with the Facing menu, or turned at a square
 * trains may turn at - with its tail on the side it will now leave by: its way out runs over its own tail.  Measured on
 * the frozen railway, 10 cases on 4 squares, every one cleared.  Adam, 2026-09-24: *"Add the refusal"*.
 *
 * His railway's clearest case: a train comes into Tunnel from the south and is reversed, so it stands on Tunnel's
 * southbound copy with its tail to the south, and is sent back south towards BottomMainAPre.  A three-unit train in
 * TunnelRightPark lies back across the points at column 7, which that way out runs over.
 *
 * Return Home's planner mirrored the same exit on purpose (AUT2-C2: never refuse a move the railway would make), so the
 * two change together, and both are claimed here.
 *
 * MUTATION: stop at the moving train's own tail again, in either, and the matching claim fails.
 *
 * @author Adam
 */
public class testATurnedTrainIsNotSentIntoAnotherTail
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    private static Locomotive turned;
    private static Locomotive parked;
    private static Integer turnedWas;
    private static Integer parkedWas;

    private static Point atTunnel;
    private static Point inThePark;
    private static Edge parkApproach;
    private static Edge wayOut;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));
        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }

        List<String> names = model.getLocList();

        if (names.size() < 2) throw new SkipException("this fixture needs two locomotives");

        turned = model.getLocByName(names.get(0));
        parked = model.getLocByName(names.get(1));

        turnedWas = turned.getTrainLength();
        parkedWas = parked.getTrainLength();

        atTunnel = layout.getPoint("Tunnel (southbound)");

        assertNotNull(atTunnel, "precondition: the frozen railway has no southbound copy of Tunnel");

        for (Edge out : layout.getNeighbors(atTunnel))
        {
            if (out.getEnd().getName().startsWith("BottomMainAPre")) wayOut = out;
        }

        assertNotNull(wayOut, "precondition: no rail leaves Tunnel's southbound copy for BottomMainAPre");

        // The copy of TunnelRightPark a train stands on having come in over the points the way out runs over.
        for (String name : session.getStationIndex().pointNamesAt(named("TunnelRightPark")))
        {
            Point copy = layout.getPoint(name);

            if (copy == null || !copy.isDestination()) continue;

            for (Edge in : layout.getNeighborsAndIncoming(copy))
            {
                if (in.getEnd() != copy) continue;

                List<String> shared = new java.util.ArrayList<>(in.getPlaceIds());

                shared.retainAll(wayOut.getPlaceIds());

                if (!shared.isEmpty())
                {
                    inThePark = copy;
                    parkApproach = in;
                }
            }
        }

        assertNotNull(inThePark, "precondition: no copy of TunnelRightPark is come into over the track Tunnel's way south"
            + " runs over");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (turned != null) turned.setTrainLength(turnedWas);
            if (parked != null) parked.setTrainLength(parkedWas);

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void clearTheRailway()
    {
        for (Point point : new Point[] {atTunnel, inThePark})
        {
            if (point == null) continue;

            point.setLocomotive(null);
            point.setArrivedFrom(null);
            point.setArrivedAlong(null);
        }
    }

    private static TileKey named(String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        return null;
    }

    /**
     * The train at Tunnel: came in from the south, turned where it stands, so its tail lies on its way out.
     */
    private static void standTheTurnedTrain()
    {
        turned.setTrainLength(1);

        atTunnel.setLocomotive(turned);
        atTunnel.setArrivedFrom("S");

        assertEquals(layout.edgesCoveredByStandingTrains().get(wayOut), turned, "precondition: the turned train's own"
            + " tail is not what lies on its way out, so this is not the case OB-285 is about");
    }

    /**
     * The train in TunnelRightPark, come in over the points, as long as given.
     */
    private static void parkATrainOf(int length)
    {
        parked.setTrainLength(length);

        inThePark.setLocomotive(parked);
        inThePark.setArrivedFrom(layout.entrySideOf(parkApproach, inThePark));
        inThePark.setArrivedAlong(Arrays.asList(parkApproach));
    }

    private static boolean parkedTailLiesOnTheWayOut()
    {
        Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

        for (String place : wayOut.getPlaceIds())
        {
            if (claimed.get(place) == parked) return true;
        }

        return false;
    }

    /**
     * The railway refuses the turned train's way out while the parked train's tail lies across it.
     */
    @Test
    public void testTheRailwayRefusesTheWayOutAcrossAnotherTail() throws Exception
    {
        standTheTurnedTrain();

        // THE CONTROL: a one-unit train in the park lies on none of it, and the way out is clear.
        parkATrainOf(1);

        assertFalse(parkedTailLiesOnTheWayOut(), "precondition: a one-unit train in TunnelRightPark already lies on"
            + " Tunnel's way south");

        assertTrue(layout.isPathClear(Arrays.asList(wayOut), turned, false), "control: with nothing lying on it, the"
            + " turned train's way south from Tunnel is refused anyway, so the claim below is not about a tail");

        // THE CASE: three units, lying back across the points at column 7.
        parkATrainOf(3);

        assertTrue(parkedTailLiesOnTheWayOut(), "precondition: a three-unit train in TunnelRightPark does not lie on"
            + " Tunnel's way south");

        assertFalse(layout.isPathClear(Arrays.asList(wayOut), turned, false), "a train turned at Tunnel was cleared south"
            + " through the points a train in TunnelRightPark is lying across: its own tail was the first found on its"
            + " way out and nothing else was asked (OB-285)");
    }

    /**
     * Return Home asks the same: the way out stays shut while the parked train has not moved.
     */
    @Test
    public void testReturnHomeRefusesTheSameWayOut() throws Exception
    {
        standTheTurnedTrain();

        parkATrainOf(3);

        assertTrue(parkedTailLiesOnTheWayOut(), "precondition: a three-unit train in TunnelRightPark does not lie on"
            + " Tunnel's way south");

        HomeStaging staging = HomeStaging.snapshot(layout);

        Method passes = HomeStaging.class.getDeclaredMethod("passesTheTailsOfTrainsThatHaveNotMoved", Edge.class,
            Locomotive.class, Map.class);

        passes.setAccessible(true);

        Field startField = HomeStaging.class.getDeclaredField("start");

        startField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<Point, Locomotive> start = new LinkedHashMap<>((Map<Point, Locomotive>) startField.get(staging));

        // THE CONTROL: once the parked train has moved, its tail is not where it was.
        Map<Point, Locomotive> parkedGone = new LinkedHashMap<>(start);

        parkedGone.remove(inThePark);

        assertTrue((Boolean) passes.invoke(staging, wayOut, turned, parkedGone), "control: with the parked train gone,"
            + " Return Home still refuses the turned train's way south, so the claim below is not about its tail");

        assertFalse((Boolean) passes.invoke(staging, wayOut, turned, start), "Return Home let the train turned at Tunnel"
            + " out south through the points a train in TunnelRightPark is lying across, because the turned train's own"
            + " tail was the first found there (OB-285)");
    }
}
