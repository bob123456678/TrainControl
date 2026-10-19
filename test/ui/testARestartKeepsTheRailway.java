package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import support.Components;
import support.Packets;
import support.Restart;

/**
 * What the railway was doing when the program closed is what it is doing when it starts again (list 2's restart row).
 *
 * The class starts the program with its window on a copy of the frozen railway, changes the state the way the railway
 * and the operator do - a turnout thrown at the station, a train turned and run from another controller, a route armed,
 * a train put on the diagram with its tail lying back over the track behind it - and then closes the window as its close
 * box does and starts the program again on the same folder and data (`support.Restart`).  Each claim reads one thing
 * back from the program that started second: where the trains stand and face, what their tails cover, the switches, the
 * statistics, and a route the station's sensor still fires.
 *
 * MUTATION: each claim names the line it rests on; see its javadoc.
 *
 * @author Adam
 */
public class testARestartKeepsTheRailway
{
    private static final String PREFIX = "RS ";
    private static final String TRAIN = PREFIX + "standing train";
    private static final String DRIVEN = PREFIX + "driven train";

    private static final int TURNOUT = 252;
    private static final int ROUTE_TURNOUT = 253;
    private static final int SENSOR = 4795;
    private static final String ROUTE = PREFIX + "armed route";

    /** As long as the platform it stands at, so its tail lies back over the track it came in on. */
    private static final int TRAIN_LENGTH = 4;

    private static final long PATIENCE_MS = 10000;

    private static Restart restart;
    private static MarklinControlStation first;
    private static MarklinControlStation second;

    /** What the first program had, read just before it closed. */
    private static int actuations;
    private static long runtime;
    private static String standingAt;
    private static String facing;
    private static TreeSet<String> covered;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        restart = Restart.open(support.Scenario.folderFor("live-snapshot"));

        first = restart.getModel();

        assertNotNull(first.getAutoLayout(), "precondition: the frozen railway's setup did not load");

        // A turnout the station threw, and says it threw
        MarklinAccessory turnout = first.newSwitch(TURNOUT, Accessory.accessoryDecoderType.MM2, false);

        first.receiveMessage(Packets.accessoryEcho(turnout, true));

        assertTrue(Components.waitFor(turnout::isSwitched, PATIENCE_MS), "precondition: the turnout was not thrown");

        actuations = turnout.getNumActuations();

        assertTrue(actuations > 0, "precondition: the throw was not counted");

        // A train run and turned from another controller, with the power on
        MarklinLocomotive driven = first.getLocByName(DRIVEN) != null ? first.getLocByName(DRIVEN)
            : first.newMM2Locomotive(DRIVEN, 64);

        first.receiveMessage(Packets.powerGo());

        assertTrue(first.waitForPowerState(true, PATIENCE_MS), "precondition: the power is not on");

        first.receiveMessage(Packets.speedEcho(driven, 300));

        assertTrue(Components.waitFor(() -> driven.getSpeed() == 30, PATIENCE_MS), "precondition: the train is not running");

        Thread.sleep(300);

        first.receiveMessage(Packets.directionEcho(driven, false));

        assertTrue(Components.waitFor(() -> !driven.goingForward() && driven.getSpeed() == 0, PATIENCE_MS),
            "precondition: the train did not turn and stop");

        runtime = driven.getRuntimeToday();

        assertTrue(runtime >= 300, "precondition: the train's running time was not counted: " + runtime);

        // A route armed on a sensor
        first.receiveMessage(Packets.s88(SENSOR, false));

        assertTrue(Components.waitFor(() -> first.isFeedbackSet(Integer.toString(SENSOR)), PATIENCE_MS),
            "precondition: the route's sensor is not known");

        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(ROUTE_TURNOUT, Accessory.accessoryDecoderType.MM2, true));

        assertTrue(first.newRoute(ROUTE, commands, SENSOR, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null),
            "precondition: the route was not made");

        first.setAccessoryState(ROUTE_TURNOUT, Accessory.accessoryDecoderType.MM2, false);

        // A long train put on a platform of the diagram
        MarklinLocomotive standing = first.getLocByName(TRAIN) != null ? first.getLocByName(TRAIN)
            : first.newMM2Locomotive(TRAIN, 65);

        standing.setTrainLength(TRAIN_LENGTH);

        Layout railway = first.getAutoLayout();

        standingAt = placeOnAPlatform(railway, TRAIN, "BottomMainB (eastbound)");

        facing = railway.getLocomotiveLocation(standing).getCopyFacing();

        // It came in from behind, as a train on a straight platform does: the side opposite the way it faces
        assertNotNull(facing, "precondition: the platform's copy says no facing");

        railway.getLocomotiveLocation(standing).setArrivedFrom(
            String.valueOf("NESW".charAt(("NESW".indexOf(facing) + 2) % 4)));

        covered = coveredBy(railway, TRAIN);

        assertFalse(covered.isEmpty(), "precondition: the train's tail covers nothing, so there is nothing to keep");

        // And the program closes and starts again
        second = restart.again();

        assertNotNull(second.getAutoLayout(), "the setup did not load again after the restart");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            for (MarklinControlStation model : new MarklinControlStation[] {second, first})
            {
                if (model != null && model.getRoute(ROUTE) != null) model.deleteRoute(ROUTE);
            }
        }
        finally
        {
            if (restart != null) restart.close();
        }
    }

    /**
     * A turnout thrown before the close is thrown after the start, and its count of moves is kept.
     *
     * MUTATION: in `MarklinSimpleComponent(MarklinAccessory)`, `this.state = a.isSwitched();` to `this.state = false;`.
     */
    @Test
    public void testATurnoutThrownBeforeTheCloseIsThrownAfterTheStart()
    {
        MarklinAccessory turnout = second.getAccessoryByAddressIfPresent(TURNOUT, Accessory.accessoryDecoderType.MM2);

        assertNotNull(turnout, "the turnout is gone after the restart");

        assertTrue(turnout.isSwitched(), "the turnout the station threw before the close reads straight after the start");

        assertEquals(turnout.getNumActuations(), actuations, "the turnout's count of moves was not kept");
    }

    /**
     * A train turned before the close faces the same way after the start.
     *
     * MUTATION: in `MarklinSimpleComponent(MarklinLocomotive)`, `this.state = l.goingForward();` to `this.state = true;`.
     */
    @Test
    public void testATrainTurnedBeforeTheCloseStillFacesThatWay()
    {
        Locomotive driven = second.getLocByName(DRIVEN);

        assertNotNull(driven, "the train is gone after the restart");

        assertFalse(driven.goingForward(), "the train turned before the close is going forward after the start");
    }

    /**
     * The time a train ran today is still counted after the start.
     *
     * MUTATION: in `MarklinSimpleComponent(MarklinLocomotive)`, `this.historicalOperatingTimeNew =
     * l.getHistoricalOperatingTime();` to `this.historicalOperatingTimeNew = new java.util.HashMap<>();`.
     */
    @Test
    public void testTheRunningTimeCountedBeforeTheCloseIsKept()
    {
        Locomotive driven = second.getLocByName(DRIVEN);

        assertNotNull(driven, "the train is gone after the restart");

        assertEquals(driven.getRuntimeToday(), runtime, "today's running time was not kept across the restart");
    }

    /**
     * A route armed before the close is armed after the start, and the station's sensor fires it.
     *
     * MUTATION: in `MarklinSimpleComponent(MarklinRoute)`, `this.routeEnabled = r.isEnabled();` to
     * `this.routeEnabled = false;`.
     *
     * @throws Exception from the wait
     */
    @Test
    public void testARouteArmedBeforeTheCloseIsFiredByItsSensorAfterTheStart() throws Exception
    {
        MarklinRoute route = second.getRoute(ROUTE);

        assertNotNull(route, "the route is gone after the restart");

        assertTrue(route.isEnabled(), "the route armed before the close is not armed after the start");

        second.setAccessoryState(ROUTE_TURNOUT, Accessory.accessoryDecoderType.MM2, false);

        for (boolean made : new boolean[] {false, true})
        {
            Thread.sleep(Locomotive.FEEDBACK_DURATION_THRESHOLD * 2L);

            second.receiveMessage(Packets.s88(SENSOR, made));
        }

        assertTrue(Components.waitFor(() -> second.getAccessoryState(ROUTE_TURNOUT, Accessory.accessoryDecoderType.MM2),
            PATIENCE_MS), "the station reported the route's sensor clear then made after the restart, and the route did"
            + " not run");
    }

    /**
     * A train put on the diagram before the close stands on the same platform, facing the same way, after the start.
     *
     * MUTATION: in `TrainControlUI.saveState(boolean)`, `saveState(backup, !backup);` to `saveState(backup, false);`.
     */
    @Test
    public void testATrainPutOnThePlatformStandsThereAfterTheStart()
    {
        Locomotive standing = second.getLocByName(TRAIN);

        assertNotNull(standing, "the train is gone after the restart");

        Point at = second.getAutoLayout().getLocomotiveLocation(standing);

        assertNotNull(at, "the train put on " + standingAt + " before the close stands nowhere after the start");

        assertEquals(at.getName(), standingAt, "the train stands somewhere else after the restart");

        assertEquals(at.getCopyFacing(), facing, "the train faces the other way after the restart");
    }

    /**
     * The track a standing train's tail lay over before the close is covered by it after the start, so no other train
     * is sent into it.
     *
     * MUTATION: in `MarklinSimpleComponent(MarklinLocomotive)`, `this.trainLength = l.getTrainLength();` to
     * `this.trainLength = null;`.
     */
    @Test
    public void testTheTrackATailCoveredIsCoveredAfterTheStart()
    {
        assertEquals(coveredBy(second.getAutoLayout(), TRAIN), covered, "the track the standing train's tail covers"
            + " changed across the restart");
    }

    // ---------------------------------------------------------------- the fixture

    /**
     * Puts the train on the named copy of a platform, as the diagram's placement does, and says where.
     */
    private static String placeOnAPlatform(Layout railway, String train, String platform)
    {
        List<String> tried = new ArrayList<>();

        for (Point p : new ArrayList<>(railway.getPoints()))
        {
            if (!p.getName().equals(platform) || !p.isDestination() || p.getCurrentLocomotive() != null) continue;

            tried.add(p.getName());

            if (railway.moveLocomotive(train, p.getName(), false)) return p.getName();
        }

        List<String> there = new ArrayList<>();

        for (Point p : new ArrayList<>(railway.getPoints()))
        {
            if (p.getName().startsWith(platform)) there.add(p.getName() + " destination " + p.isDestination() + " holding "
                + p.getCurrentLocomotive());
        }

        throw new AssertionError("precondition: " + train + " could not be put on " + platform + "; tried " + tried
            + " of " + there + " among " + railway.getPoints().size() + " points");
    }

    /** The edges the train's tail covers, by name. */
    private static TreeSet<String> coveredBy(Layout railway, String train)
    {
        TreeSet<String> names = new TreeSet<>();

        for (Map.Entry<Edge, Locomotive> e : railway.edgesCoveredByStandingTrains().entrySet())
        {
            if (e.getValue() != null && train.equals(e.getValue().getName())) names.add(e.getKey().getName());
        }

        return names;
    }
}
