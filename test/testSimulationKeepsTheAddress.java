import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Starting in simulation keeps the Central Station's address the user saved (OB-339).
 *
 * A start that did not connect removed the saved address so the next start would ask for it again: right after a
 * real start that failed, wrong after a simulation, which never tries to connect.  The next real start then asked for
 * the address again.  The saved address here is a documentation address that nothing answers on; a simulation start
 * never uses it.
 */
public class testSimulationKeepsTheAddress
{
    private static final String SAVED = "192.0.2.7";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // It writes the real preference node, which only docs/tools/one.sh replaces with a node of its own
        if (System.getProperty(org.traincontrol.util.Util.PREFERENCES_PROPERTY, "").trim().isEmpty())
            throw new org.testng.SkipException("needs docs/tools/one.sh");

        TrainControlUI.getPrefs().put(TrainControlUI.IP_PREF, SAVED);

        init(null, true, false, false, false);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        TrainControlUI.getPrefs().remove(TrainControlUI.IP_PREF);
    }

    @Test
    public void testASimulationStartKeepsTheSavedAddress()
    {
        assertEquals(TrainControlUI.getPrefs().get(TrainControlUI.IP_PREF, null), SAVED,
            "a simulation start forgot the Central Station's address, so the next real start asks for it again");
    }
}
