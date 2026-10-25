package support;

import org.traincontrol.marklin.MarklinControlStation;

/**
 * The simulated control station a test starts, one entry point per set-up the suite uses.
 *
 * Each is `MarklinControlStation.init` in simulation with the power left off, run through
 * `TestStationAddress.withNoStation`, so the init's sync goes nowhere and no Central Station is contacted.  Open a
 * `LayoutSandbox` first: init loads whatever layout the preference names.
 */
public final class TestStation
{
    private TestStation()
    {
    }

    /**
     * No window, not in debug.
     *
     * @return the station
     * @throws Exception from init
     */
    public static MarklinControlStation simulated() throws Exception
    {
        return start(false, false);
    }

    /**
     * No window, in debug: `Layout.setSimulate` refuses outside debug, and an unconnected station logs what it would
     * have sent only in debug.
     *
     * @return the station
     * @throws Exception from init
     */
    public static MarklinControlStation simulatedInDebug() throws Exception
    {
        return start(false, true);
    }

    /**
     * With the application's own window (`getGUI()`), not in debug.  Needs a display.
     *
     * @return the station
     * @throws Exception from init
     */
    public static MarklinControlStation simulatedWithWindow() throws Exception
    {
        return start(true, false);
    }

    /**
     * With the application's own window (`getGUI()`), in debug.  Needs a display.
     *
     * @return the station
     * @throws Exception from init
     */
    public static MarklinControlStation simulatedWithWindowInDebug() throws Exception
    {
        return start(true, true);
    }

    private static MarklinControlStation start(boolean showUI, boolean debug) throws Exception
    {
        return TestStationAddress.withNoStation(() -> MarklinControlStation.init(null, true, showUI, false, debug));
    }
}
