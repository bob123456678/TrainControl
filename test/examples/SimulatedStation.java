package examples;

import org.traincontrol.marklin.MarklinControlStation;

/**
 * A Central Station that is not there, for trying the examples without a railway.
 *
 * Every example takes its station as a parameter.  The `main` method of each one opens this simulated station, so
 * running an example by itself never sends anything to a real Central Station.  To drive your own railway, call the
 * example's `run` method with the station you get from
 * `MarklinControlStation.init("192.168.1.10", false, false, true, false)` (your Central Station's address in place
 * of the sample one).
 *
 * Only one station can be open in a program at a time, because it listens on a fixed network port.
 */
public final class SimulatedStation
{
    private SimulatedStation()
    {
    }

    /**
     * Opens a station that answers its own commands.
     *
     * @return the station, with sending turned off and the answers simulated
     * @throws Exception when the station cannot start
     */
    public static MarklinControlStation open() throws Exception
    {
        // Simulate mode: the address is never used to reach a Central Station.
        MarklinControlStation station = MarklinControlStation.init(null, true, false, false, true);

        // Nothing leaves the program...
        station.setNetworkCommState(false);

        // ...and the answers a real station would send back are made up here, so switches, signals and speeds
        // report back as they do on a real railway.
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        return station;
    }
}
