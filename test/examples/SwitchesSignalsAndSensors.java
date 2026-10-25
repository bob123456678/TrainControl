package examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Sets switches and signals, reads sensors (S88 feedback), and stores and runs a route made of those commands.
 *
 * Shows `newSwitch`, `newSignal`, `getAccessoryByName`, `getAccessoryByAddress`, `getAccessoryByAddressIfPresent`,
 * the accessory's `turn`, `straight`, `doSwitch`, `red`, `green`, `isTurned`, `isStraight`, `isRed`, `isGreen`, the
 * station's `setAccessoryState`, `getAccessoryState`, `getAccessoryStateIfPresent`, `newFeedback`, `isFeedbackSet`,
 * `getFeedbackState`, `setFeedbackState`, and `newRoute`, `getRouteList`, `execRoute` and `deleteRoute`.
 */
public class SwitchesSignalsAndSensors
{
    /** The sensor number the example uses. */
    public static final int SENSOR = 900;

    /** The route the example stores, and removes again at the end. */
    public static final String ROUTE = "Example route";

    /**
     * @param station the Central Station to command
     * @return a line of text for each thing read back
     * @throws Exception when a command fails
     */
    public static List<String> run(MarklinControlStation station) throws Exception
    {
        List<String> found = new ArrayList<>();

        accessoryDecoderType motorola = accessoryDecoderType.MM2;

        //
        // Make the accessories known.  A switch or signal that your Central Station already has does not need this;
        // these are only for addresses it has not been told about.  The number is the address printed on the decoder.
        // A signal and a switch at the same address are the same decoder.
        //
        station.newSwitch(5, motorola, false);
        station.newSignal(6, motorola, false);

        MarklinAccessory points = station.getAccessoryByName("Switch 5");
        MarklinAccessory signal = station.getAccessoryByName("Signal 6");

        // A station answers a command a moment later and the accessory records the answer, so each reading below
        // waits a moment first (`delay`, in milliseconds).
        points.turn().delay(100);

        found.add("after turn: turned " + points.isTurned() + ", straight " + points.isStraight());

        points.straight().delay(100);

        found.add("after straight: turned " + points.isTurned() + ", straight " + points.isStraight());

        // Flip to whichever it is not
        points.doSwitch().delay(100);

        found.add("after doSwitch: turned " + points.isTurned());

        signal.red().delay(100);

        found.add("after red: red " + signal.isRed() + ", green " + signal.isGreen());

        signal.green().delay(100);

        found.add("after green: red " + signal.isRed() + ", green " + signal.isGreen());

        //
        // The same switch by address instead of by name, which is how a program that does not know the names asks.
        //
        station.setAccessoryState(5, motorola, false);

        Thread.sleep(100);

        found.add("switch 5 turned: " + station.getAccessoryState(5, motorola));

        found.add("switch 5 found by address: " + station.getAccessoryByAddress(5, motorola).getName());

        // The "IfPresent" versions never make an accessory up, so they suit a check for something that may not exist.
        found.add("switch 299 exists: " + (station.getAccessoryByAddressIfPresent(299, motorola) != null)
            + ", its state " + station.getAccessoryStateIfPresent(299, motorola));

        //
        // Sensors.  A real sensor is set by the railway.  A simulated station has none to ask, so one is made and
        // set by hand; on a real railway do not call setFeedbackState, the train sets the sensor.
        //
        station.newFeedback(SENSOR, null);

        String sensor = Integer.toString(SENSOR);

        found.add("sensor " + sensor + " known: " + station.isFeedbackSet(sensor));

        station.setFeedbackState(sensor, true);

        found.add("sensor " + sensor + " occupied: " + station.getFeedbackState(sensor));

        station.setFeedbackState(sensor, false);

        found.add("sensor " + sensor + " occupied now: " + station.getFeedbackState(sensor));

        //
        // A route stores commands under a name so that one call runs them all.  It starts by hand here; a route can
        // also be started by a sensor (see ReactToEvents).
        //
        List<RouteCommand> commands = Arrays.asList(
            RouteCommand.RouteCommandAccessory(5, motorola, true),
            RouteCommand.RouteCommandAccessory(6, motorola, true));

        // No sensor (0), so nothing starts it but us
        station.newRoute(ROUTE, commands, 0, Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        found.add("routes: " + station.getRouteList().contains(ROUTE));

        station.execRoute(ROUTE);

        // The route runs in the background, a short pause between its commands, so wait until it has finished
        for (int waited = 0; waited < 5000 && !(points.isTurned() && signal.isRed()); waited += 50)
        {
            Thread.sleep(50);
        }

        found.add("after the route: switch 5 turned " + points.isTurned() + ", signal 6 red " + signal.isRed());

        station.deleteRoute(ROUTE);

        found.add("route still stored after deleteRoute: " + station.getRouteList().contains(ROUTE));

        return found;
    }

    public static void main(String[] args) throws Exception
    {
        // Change this to your own station to command your railway (see SimulatedStation).
        MarklinControlStation station = SimulatedStation.open();

        for (String line : run(station))
        {
            System.out.println(line);
        }

        System.exit(0);
    }
}
