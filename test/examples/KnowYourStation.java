package examples;

import java.util.ArrayList;
import java.util.List;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Asks the Central Station what it has: the locomotives it knows, how quickly it answers, the track power, the
 * routes stored in it and the track diagrams (layouts) it holds.
 *
 * Shows `getMarklinLocomotives` (the locomotives as the Marklin type, with their address and decoder),
 * `getLastLatency` (how many milliseconds the last answer took), the power switch (`go`, `stop`, `getPowerState`,
 * `waitForPowerState`), `getRouteList`, `getLayoutList` and `isSimulation`.
 */
public class KnowYourStation
{
    /**
     * @param station the Central Station to ask
     * @return a line of text for each thing found
     * @throws Exception when the wait for the power is interrupted
     */
    public static List<String> run(MarklinControlStation station) throws Exception
    {
        List<String> found = new ArrayList<>();

        found.add(station.isSimulation() ? "This station is a simulation" : "This is a real Central Station");

        // The same list as getLocomotives, but each one is already the Marklin type, so its address and decoder can
        // be read without a cast.
        for (MarklinLocomotive loco : station.getMarklinLocomotives())
        {
            found.add("Locomotive " + loco.getName() + ": address " + loco.getAddress() + ", " + loco.getDecoderType()
                + ", " + loco.getNumF() + " functions");
        }

        // A ping asks the station to answer, and the time the answer took is kept.  Ask first, read after.
        station.sendPing(true);

        found.add("Last answer took " + station.getLastLatency() + " ms");

        // Track power.  The station tells us the power has changed by sending a message back, so wait for it to
        // arrive before relying on it; the wait gives up after the number of milliseconds given.
        station.stop();
        boolean off = station.waitForPowerState(false, 2000);

        found.add("Power off, confirmed by the station: " + off + " (now " + station.getPowerState() + ")");

        station.go();
        boolean on = station.waitForPowerState(true, 2000);

        found.add("Power on, confirmed by the station: " + on + " (now " + station.getPowerState() + ")");

        found.add("Routes stored: " + station.getRouteList());

        found.add("Track diagrams: " + station.getLayoutList());

        return found;
    }

    public static void main(String[] args) throws Exception
    {
        // Change this to your own station to use the example on your railway (see SimulatedStation).
        MarklinControlStation station = SimulatedStation.open();

        for (String line : run(station))
        {
            System.out.println(line);
        }

        System.exit(0);
    }
}
