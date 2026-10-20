package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.udp.CS2Message;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.Components;
import support.Packets;
import support.RouteMonitor;

/**
 * What the Central Station sends reaches the model: sensors, turnouts thrown at the station, locomotives driven from
 * another controller, and the power.
 *
 * Every claim here acts as the railway does, through `receiveMessage` with a raw frame built by `support.Packets`, and
 * reads what the program made of it: the sensor's state, a route the sensor arms firing (its turnout thrown and the
 * command it sent), a locomotive's speed, direction and functions, the power, and the running time the statistics
 * keep.  `setFeedbackState` is never called: it is a different door, the one the diagram's click and the simulation
 * use.
 *
 * MUTATION: each claim names the line it rests on; see its javadoc.
 *
 * @author Adam
 */
public class testWhatTheStationSendsIsHeard
{
    /** Sensors, turnouts, routes and locomotives of this class only, clear of the fixture's own. */
    private static final int SENSOR = 4710;
    private static final int SWITCH = 241;
    private static final String PREFIX = "PKT ";

    /** How long a frame takes to be acted on: the model hands each to an executor. */
    private static final long HEARD_MS = 5000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    /** Every command the model put on the wire, as `exec` was handed it. */
    private static final List<CS2Message> SENT = Collections.synchronizedList(new ArrayList<>());

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStationAddress.withNoStation(() -> init(null, true, false, false, true));

        model.setSentMessageObserver(SENT::add);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null)
            {
                model.setSentMessageObserver(null);

                for (String name : new ArrayList<>(model.getRouteList()))
                {
                    if (name.startsWith(PREFIX))
                    {
                        model.getRoute(name).disable();
                        model.deleteRoute(name);
                    }
                }

                for (String name : new ArrayList<>(model.getLocList()))
                {
                    if (name.startsWith(PREFIX)) model.deleteLoc(name);
                }

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A sensor made at the station reads made, and broken reads clear.
     *
     * MUTATION: in `MarklinFeedback.parseMessage`, `this._setState((state == 1))` to `(state == 0)`.
     */
    @Test
    public void testAnS88PacketMakesAndBreaksTheSensor()
    {
        final String sensor = Integer.toString(SENSOR);

        hear(Packets.s88(SENSOR, false));

        assertTrue(Components.waitFor(() -> model.isFeedbackSet(sensor), HEARD_MS), "precondition: the sensor is not known");

        hear(Packets.s88(SENSOR, true));

        assertTrue(Components.waitFor(() -> model.getFeedbackState(sensor), HEARD_MS),
            "the station said sensor " + SENSOR + " is made, and the model still reads it clear");

        hear(Packets.s88(SENSOR, false));

        assertTrue(Components.waitFor(() -> !model.getFeedbackState(sensor), HEARD_MS),
            "the station said sensor " + SENSOR + " is clear again, and the model still reads it made");
    }

    /**
     * A sensor the program has never heard of is added when the station first reports it, holding what it said.
     *
     * MUTATION: in `MarklinControlStation.receiveMessage`, make the `else if` that calls `newFeedback` false.
     */
    @Test
    public void testAnS88PacketFromAnUnknownSensorAddsIt()
    {
        final int unknown = SENSOR + 7;

        assertFalse(model.isFeedbackSet(Integer.toString(unknown)), "precondition: the sensor is already known");

        hear(Packets.s88(unknown, true));

        assertTrue(Components.waitFor(() -> model.isFeedbackSet(Integer.toString(unknown)), HEARD_MS),
            "a sensor the station reported is not in the model, so a route or a train waiting on it waits for ever");

        assertTrue(model.getFeedbackState(Integer.toString(unknown)), "the new sensor does not hold the state the station"
            + " reported for it");
    }

    /**
     * A route armed on a sensor fires when the station reports the sensor clear and then made: its turnout is thrown,
     * and the command for it goes to the railway.
     *
     * MUTATION: in `MarklinRoute.executeAutoRoute`, take out `this.execRoute(true);`.
     *
     * @throws Exception from the route
     */
    @Test
    public void testAnArmedRouteFiresWhenItsSensorIsMade() throws Exception
    {
        final int sensor = SENSOR + 1;

        final MarklinAccessory turnout = straightTurnout(SWITCH);

        MarklinRoute route = armedRoute("fires", sensor, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, SWITCH, null);

        int mark = SENT.size();

        pulseWhenWatched(route, sensor);

        assertTrue(Components.waitFor(turnout::isSwitched, HEARD_MS),
            "the station reported the route's sensor clear and then made, and the route's turnout was not thrown");

        assertTrue(Components.waitFor(() -> turnoutCommandsSince(mark, turnout) > 0, HEARD_MS),
            "the route threw its turnout in the model and sent nothing to the railway");
    }

    /**
     * A route with a condition does not fire while its condition is false, and fires once the station reports the
     * condition's sensor made.
     *
     * MUTATION: in `MarklinRoute.executeAutoRoute`, `this.hasConditions() && !this.conditions.evaluate(network)` to
     * `false`.
     *
     * @throws Exception from the route
     */
    @Test
    public void testAnArmedRouteWaitsForItsCondition() throws Exception
    {
        final int sensor = SENSOR + 2;
        final int condition = SENSOR + 3;

        final MarklinAccessory turnout = straightTurnout(SWITCH + 1);

        hear(Packets.s88(condition, false));

        assertTrue(Components.waitFor(() -> model.isFeedbackSet(Integer.toString(condition)), HEARD_MS),
            "precondition: the condition's sensor is not known");

        MarklinRoute route = armedRoute("condition", sensor, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, SWITCH + 1,
            NodeExpression.fromList(new ArrayList<>(Arrays.asList(RouteCommand.RouteCommandFeedback(condition, true)))));

        int mark = SENT.size();

        pulseWhenWatched(route, sensor);

        assertFalse(Components.waitFor(turnout::isSwitched, Locomotive.FEEDBACK_DURATION_THRESHOLD * 5L),
            "the route fired with its condition (sensor " + condition + " made) false");

        assertEquals(turnoutCommandsSince(mark, turnout), 0, "the route sent its turnout's command with its condition false");

        hear(Packets.s88(condition, true));

        // AND WATCHED AGAIN: the pass that found the condition false goes back round to wait for the sensor clear
        pulseWhenWatched(route, sensor);

        assertTrue(Components.waitFor(turnout::isSwitched, HEARD_MS),
            "the route did not fire once the station reported its condition's sensor made");
    }

    /**
     * A route that has been switched off does not fire when its sensor is made; armed again, it does.
     *
     * MUTATION: in `MarklinRoute.executeAutoRoute`, take out `if (!this.enabled) return;` after the wait.
     *
     * @throws Exception from the route
     */
    @Test
    public void testADisarmedRouteIgnoresItsSensor() throws Exception
    {
        final int sensor = SENSOR + 4;

        final MarklinAccessory turnout = straightTurnout(SWITCH + 2);

        MarklinRoute route = armedRoute("disarmed", sensor, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, SWITCH + 2, null);

        // Watching its sensor already, as an armed route is long before anybody switches it off
        assertTrue(Components.waitFor(() -> RouteMonitor.watching(route), HEARD_MS),
            "precondition: the route is not watching its sensor");

        // Switched off as the route list's Disable Auto Execution writes it
        assertTrue(model.editRoute(route.getName(), route.getName(), route.getRoute(), route.getS88(), route.getTriggerType(),
            false, route.getConditions()), "precondition: the route could not be switched off");

        pulse(sensor, false, true);

        assertFalse(Components.waitFor(turnout::isSwitched, Locomotive.FEEDBACK_DURATION_THRESHOLD * 5L),
            "a route switched off fired when its sensor was made");

        // The control: armed again, the same sensor fires it
        assertTrue(model.editRoute(route.getName(), route.getName(), route.getRoute(), route.getS88(), route.getTriggerType(),
            true, route.getConditions()), "precondition: the route could not be armed again");

        // Armed again is a new monitor, on its way to its wait
        pulseWhenWatched(model.getRoute(route.getName()), sensor);

        assertTrue(Components.waitFor(turnout::isSwitched, HEARD_MS), "the route, armed again, did not fire on its sensor");
    }

    /**
     * A route triggered "occupied then clear" fires when the sensor breaks, not when it is made.
     *
     * MUTATION: in `MarklinRoute.executeAutoRoute`, `this.triggerType == s88Triggers.CLEAR_THEN_OCCUPIED` to `true`.
     *
     * @throws Exception from the route
     */
    @Test
    public void testAnOccupiedThenClearRouteFiresWhenTheSensorBreaks() throws Exception
    {
        final int sensor = SENSOR + 5;

        final MarklinAccessory turnout = straightTurnout(SWITCH + 3);

        hear(Packets.s88(sensor, false));

        armedRoute("on clear", sensor, MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR, SWITCH + 3, null);

        holdThenHear(Packets.s88(sensor, true));

        assertFalse(Components.waitFor(turnout::isSwitched, Locomotive.FEEDBACK_DURATION_THRESHOLD * 5L),
            "a route triggered occupied-then-clear fired as its sensor was made");

        holdThenHear(Packets.s88(sensor, false));

        assertTrue(Components.waitFor(turnout::isSwitched, HEARD_MS),
            "a route triggered occupied-then-clear did not fire when its sensor broke");
    }

    /**
     * A turnout thrown at the station is thrown in the model, and the move is counted once; the station saying again
     * where it already is counts nothing.
     *
     * MUTATION: in `MarklinAccessory.parseMessage`, `if (this.switched != stateAtLastActuation)` to `if (true)`.
     *
     * @throws Exception from the wait
     */
    @Test
    public void testAnAccessoryEchoMovesTheTurnoutAndCountsTheMove() throws Exception
    {
        final MarklinAccessory turnout = straightTurnout(SWITCH + 4);

        final int counted = turnout.getNumActuations();

        hear(Packets.accessoryEcho(turnout, true));

        assertTrue(Components.waitFor(turnout::isSwitched, HEARD_MS), "the station threw the turnout and the model still"
            + " has it straight");

        assertTrue(Components.waitFor(() -> turnout.getNumActuations() == counted + 1, HEARD_MS),
            "the move the station reported was not counted once: " + turnout.getNumActuations() + " after " + counted);

        // The same answer again, later than the duplicate window the model drops a repeated frame within
        Thread.sleep(400);

        hear(Packets.accessoryEcho(turnout, true));

        hear(Packets.accessoryEcho(turnout, false));

        assertTrue(Components.waitFor(() -> !turnout.isSwitched(), HEARD_MS), "the station put the turnout straight and"
            + " the model still has it thrown");

        assertEquals(turnout.getNumActuations(), counted + 2, "the station repeating where the turnout already was was"
            + " counted as a move");
    }

    /**
     * An accessory command on the bus that is not the station's answer, as another controller sends one, moves
     * nothing; the station's answer that follows does.
     *
     * MUTATION: in `MarklinControlStation.receiveMessage`, `message.isAccessoryCommand() && message.getResponse()` to
     * `message.isAccessoryCommand()`.
     */
    @Test
    public void testAnAccessoryCommandThatIsNotTheStationsAnswerMovesNothing()
    {
        final MarklinAccessory turnout = straightTurnout(SWITCH + 5);

        hear(Packets.accessoryCommand(turnout, true));

        assertFalse(Components.waitFor(turnout::isSwitched, 1000), "a command that the station has not answered moved"
            + " the turnout in the model");

        hear(Packets.accessoryEcho(turnout, true));

        assertTrue(Components.waitFor(turnout::isSwitched, HEARD_MS), "the station's answer did not move the turnout");
    }

    /**
     * A speed set at the station reaches the locomotive, scaled from the station's 0-1000 and rounded.
     *
     * MUTATION: in `MarklinLocomotive.parseMessage`, `(int) Math.round(speed / 10.0)` to `speed / 10`.
     */
    @Test
    public void testASpeedEchoSetsTheSpeedRounded()
    {
        final MarklinLocomotive train = locomotive("speed", 71);

        hear(Packets.speedEcho(train, 455));

        assertTrue(Components.waitFor(() -> train.getSpeed() == 46, HEARD_MS), "the station set speed 455 of 1000 and the"
            + " locomotive reads " + train.getSpeed() + ", not 46");
    }

    /**
     * A change of direction at the station turns the locomotive and stops it, as the decoder does.
     *
     * MUTATION: in `MarklinLocomotive.parseMessage`, take out the `this._setSpeed(0)` under `direction == 2`.
     */
    @Test
    public void testADirectionEchoTurnsTheTrainAndStopsIt()
    {
        final MarklinLocomotive train = locomotive("direction", 72);

        hear(Packets.directionEcho(train, true));

        assertTrue(Components.waitFor(train::goingForward, HEARD_MS), "precondition: the train is not going forward");

        hear(Packets.speedEcho(train, 400));

        assertTrue(Components.waitFor(() -> train.getSpeed() == 40, HEARD_MS), "precondition: the train is not running");

        hear(Packets.directionEcho(train, false));

        assertTrue(Components.waitFor(() -> !train.goingForward(), HEARD_MS), "the station turned the train and the model"
            + " still has it going forward");

        assertEquals(train.getSpeed(), 0, "the station turned the train, which stops it, and the model still has it"
            + " running");
    }

    /**
     * A function turned on at the station is on in the model, and turned off is off.
     *
     * MUTATION: in `MarklinLocomotive.parseMessage`, `m.getData()[5] != 0` to `m.getData()[5] == 0`.
     */
    @Test
    public void testAFunctionEchoTurnsTheFunctionOnAndOff()
    {
        final MarklinLocomotive train = locomotive("function", 73);

        hear(Packets.functionEcho(train, 3, true));

        assertTrue(Components.waitFor(() -> train.getF(3), HEARD_MS), "the station turned F3 on and the model has it off");

        hear(Packets.functionEcho(train, 3, false));

        assertTrue(Components.waitFor(() -> !train.getF(3), HEARD_MS), "the station turned F3 off and the model has it on");
    }

    /**
     * The power cut at the station reads off in the model, and restored reads on.
     *
     * MUTATION: in `MarklinControlStation.receiveMessage`, the STOP branch's `this.setPowerState(false)` to `true`.
     *
     * @throws Exception from the wait
     */
    @Test
    public void testAStopPacketCutsThePowerAndAGoRestoresIt() throws Exception
    {
        try
        {
            hear(Packets.powerGo());

            assertTrue(model.waitForPowerState(true, HEARD_MS), "precondition: the power is not on");

            hear(Packets.powerStop());

            assertTrue(model.waitForPowerState(false, HEARD_MS), "the station cut the power and the model reads it on");

            hear(Packets.powerGo());

            assertTrue(model.waitForPowerState(true, HEARD_MS), "the station restored the power and the model reads it off");
        }
        finally
        {
            hear(Packets.powerGo());
        }
    }

    /**
     * The time a locomotive ran is banked when the station cuts the power under it, and none is added while the power
     * is off.
     *
     * MUTATION: in `MarklinControlStation.receiveMessage`, take out the STOP branch's `l.notifyOfPowerStateChange(false)`.
     *
     * @throws Exception from the wait
     */
    @Test
    public void testAPowerCutFromTheStationBanksTheRunningTime() throws Exception
    {
        final MarklinLocomotive train = locomotive("runtime", 74);

        try
        {
            hear(Packets.powerGo());

            assertTrue(model.waitForPowerState(true, HEARD_MS), "precondition: the power is not on");

            final long before = train.getRuntimeToday();

            hear(Packets.speedEcho(train, 300));

            assertTrue(Components.waitFor(() -> train.getSpeed() == 30, HEARD_MS), "precondition: the train is not running");

            // It runs, at the station's say-so, for this long
            Thread.sleep(300);

            hear(Packets.powerStop());

            assertTrue(model.waitForPowerState(false, HEARD_MS), "precondition: the power did not go off");

            assertTrue(Components.waitFor(() -> train.getRuntimeToday() - before >= 300, HEARD_MS),
                "the train ran for 300 ms before the station cut the power, and today's running time grew by "
                + (train.getRuntimeToday() - before) + " ms");

            final long banked = train.getRuntimeToday();

            Thread.sleep(300);

            hear(Packets.speedEcho(train, 0));

            assertTrue(Components.waitFor(() -> train.getSpeed() == 0, HEARD_MS), "precondition: the train did not stop");

            assertEquals(train.getRuntimeToday(), banked, "time with the power off was counted as running time");
        }
        finally
        {
            hear(Packets.powerGo());
        }
    }

    // ---------------------------------------------------------------- the railway

    private static void hear(CS2Message frame)
    {
        model.receiveMessage(frame);
    }

    /**
     * Sends a frame once the sensor has held its last state long enough to count, as a real one does.
     */
    private static void holdThenHear(CS2Message frame)
    {
        try
        {
            Thread.sleep(Locomotive.FEEDBACK_DURATION_THRESHOLD * 2L);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        hear(frame);
    }

    /**
     * The sensor reported clear and then made, each held long enough to count - made only once the route's monitor is
     * waiting for it (`support.RouteMonitor.watching`), as the route list's Enable Auto Execution claim waits (FR-136,
     * issue 22).  Arming a route, or a pass of its monitor that did not fire, leaves the monitor still on its way to its
     * wait, and a sensor made before it gets there is not heard.
     */
    private static void pulseWhenWatched(MarklinRoute route, int sensor)
    {
        holdThenHear(Packets.s88(sensor, false));

        assertTrue(Components.waitFor(() -> RouteMonitor.watching(route), HEARD_MS),
            "precondition: the route's monitor is not waiting for its sensor to be made");

        holdThenHear(Packets.s88(sensor, true));
    }

    /** The sensor goes to the first state, then the second, each held long enough to count. */
    private static void pulse(int sensor, boolean first, boolean second)
    {
        holdThenHear(Packets.s88(sensor, first));
        holdThenHear(Packets.s88(sensor, second));
    }

    private static MarklinAccessory straightTurnout(int address)
    {
        MarklinAccessory turnout = model.newSwitch(address, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(false);

        assertNotNull(turnout, "precondition: no turnout at " + address);

        return turnout;
    }

    /**
     * A route of one command, throwing the turnout, armed on the sensor.  The sensor is reported clear first, so the
     * route's monitor has a sensor to wait on.
     */
    private static MarklinRoute armedRoute(String name, int sensor, MarklinRoute.s88Triggers trigger, int turnout,
        NodeExpression condition) throws Exception
    {
        hear(Packets.s88(sensor, false));

        assertTrue(Components.waitFor(() -> model.isFeedbackSet(Integer.toString(sensor)) && !model.getFeedbackState(
            Integer.toString(sensor)), HEARD_MS), "precondition: the route's sensor is not known, or not clear");

        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(turnout, Accessory.accessoryDecoderType.MM2, true));

        assertTrue(model.newRoute(PREFIX + name, commands, sensor, trigger, true, condition),
            "precondition: the route could not be added");

        MarklinRoute route = model.getRoute(PREFIX + name);

        assertNotNull(route, "precondition: the route is not in the list");

        assertTrue(route.isEnabled(), "precondition: the route is not armed");

        return route;
    }

    private static MarklinLocomotive locomotive(String name, int address)
    {
        MarklinLocomotive train = model.getLocByName(PREFIX + name);

        if (train == null) train = model.newMM2Locomotive(PREFIX + name, address);

        assertNotNull(train, "precondition: the locomotive could not be added");

        return train;
    }

    /** How many commands for this turnout went to the wire since a mark. */
    private static int turnoutCommandsSince(int mark, MarklinAccessory turnout)
    {
        int count = 0;

        synchronized (SENT)
        {
            for (CS2Message m : SENT.subList(Math.min(mark, SENT.size()), SENT.size()))
            {
                if (m.getCommand() == CS2Message.CMD_ACC_SWITCH && m.extractUID() == turnout.getUID()) count++;
            }
        }

        return count;
    }
}
