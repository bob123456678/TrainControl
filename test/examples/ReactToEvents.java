package examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Makes things happen when something else does: a sensor that a train trips, and the commands the program sends.
 *
 * One reaction is a thread of your own that waits for a train to arrive at a sensor and leave it, and then sets a
 * switch.  The other is a stored route that the station starts by itself when a sensor changes.  A third counts the
 * commands the program sends.  A simulated station has no trains, so the example trips the sensors itself; on a real
 * railway the trains do.
 *
 * Shows the locomotive's `waitForOccupiedThenClear`, `waitForClearThenOccupied`, `waitForOccupiedFeedback`,
 * `waitForClearFeedback` (with the sensor as a number as well as a name), `isFeedbackSet`, a route started by a sensor
 * (`newRoute` with a sensor), and `setSentMessageObserver`.
 */
public class ReactToEvents
{
    /** The sensor the first reaction waits for. */
    public static final int ARRIVAL_SENSOR = 930;

    /** The sensor that starts the route. */
    public static final int ROUTE_SENSOR = 931;

    /** The route the example stores, and removes again at the end. */
    public static final String ROUTE = "Example sensor route";

    /**
     * @param station the Central Station to watch
     * @return a line of text for each reaction
     * @throws Exception when a command fails
     */
    public static List<String> run(MarklinControlStation station) throws Exception
    {
        final List<String> found = Collections.synchronizedList(new ArrayList<String>());

        accessoryDecoderType motorola = accessoryDecoderType.MM2;

        station.newSwitch(7, motorola, false);
        station.newSwitch(8, motorola, false);

        final MarklinAccessory arrivalPoints = station.getAccessoryByName("Switch 7");
        final MarklinAccessory routePoints = station.getAccessoryByName("Switch 8");

        arrivalPoints.straight();
        routePoints.straight();

        final String arrival = Integer.toString(ARRIVAL_SENSOR);
        final String trigger = Integer.toString(ROUTE_SENSOR);

        station.newFeedback(ARRIVAL_SENSOR, null);
        station.newFeedback(ROUTE_SENSOR, null);

        station.setFeedbackState(arrival, false);
        station.setFeedbackState(trigger, false);

        //
        // Counting the commands the program sends.  The observer hears each one before it is sent, whether or not it
        // is then sent, so it works on a simulated station too.  Pass null to stop listening.
        //
        final AtomicInteger sent = new AtomicInteger();

        station.setSentMessageObserver((message) -> sent.incrementAndGet());

        //
        // Reaction 1: a thread of your own.  Any locomotive can do the waiting, and one that is not in the station's
        // database does, so this uses a stand-in.  Each wait lasts as long as it takes: the sensor has to be
        // occupied for a fraction of a second before it counts, to ignore a flicker.
        //
        final Locomotive watcher = new MarklinLocomotive(station, 1, MarklinLocomotive.decoderType.MM2, "Watcher");

        found.add("sensor " + arrival + " known: " + watcher.isFeedbackSet(arrival));

        Thread reaction = new Thread(() ->
        {
            // The train arrives (the sensor is occupied), then leaves it (the sensor is clear)
            watcher.waitForOccupiedThenClear(arrival);

            arrivalPoints.turn();

            found.add("a train passed sensor " + arrival + ", switch 7 turned");

            // The other order: the sensor clear, and then occupied again.  The sensor can also be named by its number.
            watcher.waitForClearThenOccupied(ARRIVAL_SENSOR);

            arrivalPoints.straight();

            found.add("a train came back to sensor " + arrival + ", switch 7 straight again");

            // And plain waits, for one state alone
            watcher.waitForClearFeedback(arrival);

            watcher.waitForOccupiedFeedback(ARRIVAL_SENSOR);

            found.add("the sensor went clear and occupied once more");
        });

        reaction.setDaemon(true);

        reaction.start();

        //
        // Reaction 2: a route the station starts by itself.  It is stored with a sensor, and runs each time the sensor
        // goes from clear to occupied.  The last argument but one turns it on.
        //
        List<RouteCommand> commands = Arrays.asList(RouteCommand.RouteCommandAccessory(8, motorola, true));

        station.newRoute(ROUTE, commands, ROUTE_SENSOR, Route.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

        //
        // Trains.  A real railway does this itself.  A simulated station has no trains, so trip the sensors by hand: a
        // train arrives and leaves, comes back, and leaves, and arrives once more.
        //
        if (station.isSimulation())
        {
            station.setFeedbackState(arrival, true);
            holdForAWhile();
            station.setFeedbackState(arrival, false);
            holdForAWhile();
            station.setFeedbackState(arrival, true);
            holdForAWhile();
            station.setFeedbackState(arrival, false);
            holdForAWhile();
            station.setFeedbackState(arrival, true);
            holdForAWhile();

            // The route's sensor
            station.setFeedbackState(trigger, true);
            holdForAWhile();
        }

        reaction.join(10000);

        // The route runs in the background, a short pause between its commands
        for (int waited = 0; waited < 5000 && !routePoints.isTurned(); waited += 50)
        {
            Thread.sleep(50);
        }

        found.add("switch 8 turned by the route: " + routePoints.isTurned());

        station.deleteRoute(ROUTE);

        found.add("commands sent while this ran: " + (sent.get() > 0));

        station.setSentMessageObserver(null);

        return found;
    }

    /**
     * A sensor must hold its state for a moment to count, so the example holds each one a little longer than that.
     *
     * @throws InterruptedException when interrupted
     */
    private static void holdForAWhile() throws InterruptedException
    {
        Thread.sleep(450);
    }

    public static void main(String[] args) throws Exception
    {
        // Change this to your own station to react to your railway (see SimulatedStation).
        MarklinControlStation station = SimulatedStation.open();

        for (String line : run(station))
        {
            System.out.println(line);
        }

        System.exit(0);
    }
}
