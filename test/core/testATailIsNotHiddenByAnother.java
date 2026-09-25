package core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * One train's tail claim does not hide another train's on the same place (TDD-A1).
 *
 * The walk that claims the track behind every standing train records ONE train per place, and the train walked last
 * wins - in the order of the railway's map of Points, which nobody chose.  Two tails that foul one switch from its two
 * legs both claim the switch's square, and only one of them is recorded there.  When the one recorded is the train
 * being routed - its own tail is never an obstacle to it (behaviour.md 5c) - the other train's claim on that square is
 * gone, and a route leaving over its own tail through the switch is cleared into the other train.
 *
 * The fixture is the smallest one with the shape: a switch square S, two berths behind its two legs, a train in each
 * that came in over S and now faces back out over it.  Each train's way out is asked about with the other standing -
 * whichever of the two the walk records on S, one of them is the train being routed.
 *
 * A third train stands on a rail of its own, unmoved, so a record that blamed every train for every tail has somebody to
 * blame (TDA2-C2, TDD2-C4).
 *
 * MUTATION: walk the train being routed along with the others in `isPathClear`, and the first claim fails; give each
 * train Return Home's whole record of the starting tails, and the second fails at its control; keep one owner per place
 * in it, and the second and the third fail.
 *
 * @author Adam
 */
public class testATailIsNotHiddenByAnother
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static int addresses = 2600;

    private static final String ONE = "TDD-A1 one";
    private static final String TWO = "TDD-A1 two";
    private static final String THREE = "TDD-A1 three";

    private static Layout layout;
    private static Locomotive one;
    private static Locomotive two;
    private static Locomotive three;
    private static Point junction;
    private static Point berthOne;
    private static Point berthTwo;
    private static Point berthThree;
    private static Edge inTwo;
    private static Edge outOne;
    private static Edge outTwo;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);

        one = model.newMM2Locomotive(ONE, 2298);
        two = model.newMM2Locomotive(TWO, 2299);
        three = model.newMM2Locomotive(THREE, 2297);

        assertNotNull(one, "could not create this class's first train");
        assertNotNull(two, "could not create this class's second train");
        assertNotNull(three, "could not create this class's third train");

        layout = new Layout(model);

        // J - the switch - with a berth behind each leg.  The switch's square S is on both legs; each leg has a square of
        // its own, a and b.  Every rail is written both ways, as the build writes rail.
        junction = point("A1_J", false);
        berthOne = point("A1_BERTH_ONE", true);
        berthTwo = point("A1_BERTH_TWO", true);

        Edge inOne = layout.createEdge(junction.getName(), berthOne.getName());
        outOne = layout.createEdge(berthOne.getName(), junction.getName());
        inTwo = layout.createEdge(junction.getName(), berthTwo.getName());
        outTwo = layout.createEdge(berthTwo.getName(), junction.getName());

        // AND A THIRD, on a rail of its own from K: nowhere near the switch, and never moved.
        Point elsewhere = point("A1_K", false);
        berthThree = point("A1_BERTH_THREE", true);

        Edge inThree = layout.createEdge(elsewhere.getName(), berthThree.getName());
        Edge outThree = layout.createEdge(berthThree.getName(), elsewhere.getName());

        inThree.setPlaces(Arrays.asList("A1:T", "A1:c"), Arrays.asList(1, 1));
        outThree.setPlaces(Arrays.asList("A1:c", "A1:T"), Arrays.asList(1, 1));
        inThree.setLength(2);
        outThree.setLength(2);
        inThree.setEntrySide("N");
        three.setTrainLength(2);

        inOne.setPlaces(Arrays.asList("A1:S", "A1:a"), Arrays.asList(1, 1));
        outOne.setPlaces(Arrays.asList("A1:a", "A1:S"), Arrays.asList(1, 1));
        inTwo.setPlaces(Arrays.asList("A1:S", "A1:b"), Arrays.asList(1, 1));
        outTwo.setPlaces(Arrays.asList("A1:b", "A1:S"), Arrays.asList(1, 1));

        for (Edge e : new Edge[] {inOne, outOne, inTwo, outTwo}) e.setLength(2);

        inOne.setEntrySide("N");
        inTwo.setEntrySide("N");

        one.setTrainLength(2);
        two.setTrainLength(2);

        // Each came in over S and lies back across it; each leaves by the way it came, over its own tail.
        berthOne.setLocomotive(one);
        berthOne.setArrivedFrom("N");
        berthOne.setArrivedAlong(Arrays.asList(inOne));

        berthTwo.setLocomotive(two);
        berthTwo.setArrivedFrom("N");
        berthTwo.setArrivedAlong(Arrays.asList(inTwo));

        berthThree.setLocomotive(three);
        berthThree.setArrivedFrom("N");
        berthThree.setArrivedAlong(Arrays.asList(inThree));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (berthOne != null) berthOne.setLocomotive(null);
            if (berthTwo != null) berthTwo.setLocomotive(null);
            if (berthThree != null) berthThree.setLocomotive(null);

            if (model != null)
            {
                model.deleteLoc(ONE);
                model.deleteLoc(TWO);
                model.deleteLoc(THREE);
            }
        }
        catch (Exception alreadyGone)
        {
        }
        finally
        {
            try
            {
                if (model != null) model.stop();
            }
            catch (Exception stopping)
            {
            }
            finally
            {
                if (sandbox != null) sandbox.close();
            }
        }
    }

    private static Point point(String name, boolean destination) throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(addresses++, null);

        model.setFeedbackState(sensor.getName(), false);

        layout.createPoint(name, destination, sensor.getName());

        return layout.getPoint(name);
    }

    /** Each train, walked alone, claims S - the premise of both claims. */
    private static void bothTailsClaimTheSwitch()
    {
        berthTwo.setLocomotive(null);
        assertEquals(layout.placesCoveredByStandingTrains().get("A1:S"), one, "precondition: the first train's tail does"
            + " not reach the switch square");

        berthTwo.setLocomotive(two);
        berthOne.setLocomotive(null);
        assertEquals(layout.placesCoveredByStandingTrains().get("A1:S"), two, "precondition: the second train's tail does"
            + " not reach the switch square");

        berthOne.setLocomotive(one);
    }

    /**
     * The railway refuses each train's way out through the switch while the other's tail lies across it.
     */
    @Test
    public void testTheRailwayAsksAboutEveryTailOnThePlace()
    {
        bothTailsClaimTheSwitch();

        assertFalse(layout.isPathClear(Arrays.asList(outOne), one, false), "the first train was cleared out through the"
            + " switch the second train's tail lies across - its own claim on the switch square hid the other's (TDD-A1)");

        assertFalse(layout.isPathClear(Arrays.asList(outTwo), two, false), "the second train was cleared out through the"
            + " switch the first train's tail lies across - its own claim on the switch square hid the other's (TDD-A1)");

        // THE CONTROL: with the other train gone, each way out is clear.
        berthTwo.setLocomotive(null);

        try
        {
            assertTrue(layout.isPathClear(Arrays.asList(outOne), one, false), "control: with the second train gone the"
                + " first is still refused, so the claims above are not about the second train's tail - " + Layout.getLastError());
        }
        finally
        {
            berthTwo.setLocomotive(two);
        }
    }

    /**
     * Return Home asks the same: its record of the tails at the start of a plan keeps every train on a place.
     *
     * @throws Exception from the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testReturnHomeAsksAboutEveryTailOnThePlace() throws Exception
    {
        bothTailsClaimTheSwitch();

        HomeStaging staging = HomeStaging.snapshot(layout);

        Method passes = HomeStaging.class.getDeclaredMethod("passesTheTailsOfTrainsThatHaveNotMoved", Edge.class,
            Locomotive.class, Map.class);

        passes.setAccessible(true);

        Field startField = HomeStaging.class.getDeclaredField("start");

        startField.setAccessible(true);

        Map<Point, Locomotive> start = (Map<Point, Locomotive>) startField.get(staging);

        assertFalse((Boolean) passes.invoke(staging, outOne, one, start), "Return Home let the first train out through"
            + " the switch the second train's tail lies across - its own claim on the switch square hid the other's"
            + " (TDD-A1)");

        assertFalse((Boolean) passes.invoke(staging, outTwo, two, start), "Return Home let the second train out through"
            + " the switch the first train's tail lies across - its own claim on the switch square hid the other's"
            + " (TDD-A1)");

        // THE CONTROL: once the other train has moved, its tail is not where it was.
        Map<Point, Locomotive> twoGone = new LinkedHashMap<>(start);

        twoGone.remove(berthTwo);

        assertTrue((Boolean) passes.invoke(staging, outOne, one, twoGone), "control: with the second train moved, Return"
            + " Home still refuses the first train's way out, so the claims above are not about the second train's tail");

        // AND THE RAIL THE SECOND TRAIN'S TAIL LAY ALONG IS FREE, though the third train has not moved (TDA2-C2, TDD2-C4):
        // each train answers only for its own tail.
        assertTrue((Boolean) passes.invoke(staging, inTwo, one, twoGone), "with the second train moved, the rail its tail"
            + " lay along is still refused - blamed on the third train, which stands unmoved on a rail of its own: each"
            + " train's record holds every train's tail (TDA2-C2)");
    }

    /**
     * Return Home keeps the switch's sensor shut while EITHER train still lies across it (TDD2-C5): each train accounts
     * for the end of the rail its own tail reaches, so the first to move does not free the sensor under the other.
     *
     * The switch's sensor reads occupied, as tails hold sensors (AMH-B2), with no train standing on its square.
     *
     * @throws Exception from the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testReturnHomeKeepsTheSensorShutWhileEitherTailIsOnIt() throws Exception
    {
        bothTailsClaimTheSwitch();

        model.setFeedbackState(junction.getS88(), true);

        try
        {
            assertTrue(layout.isFeedbackOccupied(junction.getS88()), "precondition: the switch's sensor does not read"
                + " occupied");

            HomeStaging staging = HomeStaging.snapshot(layout);

            Method blocked = HomeStaging.class.getDeclaredMethod("blockedSensors", Map.class);

            blocked.setAccessible(true);

            Field startField = HomeStaging.class.getDeclaredField("start");

            startField.setAccessible(true);

            Map<Point, Locomotive> start = (Map<Point, Locomotive>) startField.get(staging);

            assertTrue(((java.util.Set<String>) blocked.invoke(staging, start)).contains(junction.getS88()),
                "precondition: with both trains where they started, the switch's sensor is not shut");

            Map<Point, Locomotive> oneGone = new LinkedHashMap<>(start);

            oneGone.remove(berthOne);

            assertTrue(((java.util.Set<String>) blocked.invoke(staging, oneGone)).contains(junction.getS88()),
                "with the first train moved, the switch's sensor was freed though the second train's tail still lies on"
                + " it - the planner offers a leg the railway refuses (TDD2-C5, OB-073)");

            Map<Point, Locomotive> twoGone = new LinkedHashMap<>(start);

            twoGone.remove(berthTwo);

            assertTrue(((java.util.Set<String>) blocked.invoke(staging, twoGone)).contains(junction.getS88()),
                "with the second train moved, the switch's sensor was freed though the first train's tail still lies on"
                + " it - the planner offers a leg the railway refuses (TDD2-C5, OB-073)");

            // THE CONTROL: with both gone, nothing explains it any more but the tails that have left - it is free.
            Map<Point, Locomotive> bothGone = new LinkedHashMap<>(twoGone);

            bothGone.remove(berthOne);

            assertFalse(((java.util.Set<String>) blocked.invoke(staging, bothGone)).contains(junction.getS88()),
                "control: with both trains moved the switch's sensor is still shut, so the claims above are not about"
                + " the tails");
        }
        finally
        {
            model.setFeedbackState(junction.getS88(), false);
        }
    }
}
