package core;

import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import support.TestStation;
import support.TestStationAddress;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Each of `support.TestStation`'s entry points starts the station its name says: simulated, with or without the
 * application's window, in debug or not, and with its init's sync sent nowhere, so no Central Station is contacted.
 * Some 230 test classes start their station through it.
 *
 * MUTATION: flip a flag in one of `TestStation`'s entry points, or drop `withNoStation` from `TestStation.start`, and
 * that entry point's claim fails.
 */
public class testTheTestStationIsSimulated
{
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    @Test
    public void testSimulated() throws Exception
    {
        assertStarts(TestStation::simulated, false, false);
    }

    @Test
    public void testSimulatedInDebug() throws Exception
    {
        assertStarts(TestStation::simulatedInDebug, false, true);
    }

    @Test
    public void testSimulatedWithWindow() throws Exception
    {
        assertStarts(TestStation::simulatedWithWindow, true, false);
    }

    @Test
    public void testSimulatedWithWindowInDebug() throws Exception
    {
        assertStarts(TestStation::simulatedWithWindowInDebug, true, true);
    }

    private static void assertStarts(Callable<MarklinControlStation> entry, boolean window, boolean debug)
        throws Exception
    {
        if (window && java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        MarklinControlStation station = entry.call();

        try
        {
            assertTrue(station.isSimulation(), "the station is not a simulation");

            assertEquals(station.isDebug(), debug, "the station's debug mode");

            assertEquals(station.getGUI() instanceof TrainControlUI, window,
                "whether the station has the application's window: " + station.getGUI());

            // The init's sync is the last one, and its parser keeps the address it read from.
            assertEquals(syncedFrom(station), "http://" + TestStationAddress.NOWHERE + "/config/lokomotive.cs2",
                "the init's sync did not go nowhere, so it could have reached a real Central Station");

            assertNull(TestStationAddress.get(), "the test address was not put back after the init");
        }
        finally
        {
            station.stop();

            if (station.getGUI() instanceof TrainControlUI)
            {
                final TrainControlUI closing = (TrainControlUI) station.getGUI();

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
    }

    private static String syncedFrom(MarklinControlStation station) throws Exception
    {
        java.lang.reflect.Field parser = MarklinControlStation.class.getDeclaredField("fileParser");

        parser.setAccessible(true);

        return ((CS2File) parser.get(station)).getLocURL();
    }
}
