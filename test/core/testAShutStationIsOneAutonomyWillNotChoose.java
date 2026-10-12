package core;

import java.util.Set;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * A station switched out of service is one autonomy will not choose, and the editor has to say so.
 *
 * **E8-B1.** `AutonomySession.stationsAutonomyWillNotChoose` is the editor's statement of the runtime's
 * rule, and the runtime is `Layout.isSendableDestination`:
 *
 *     isDestination() && isActive() && isAutoDestination() && !isReversing()
 *
 * The editor asked one of those four. `!isReversing()` genuinely cannot be asked of a square - it is a
 * property of a COPY, and a may-reverse square keeps a plain copy autonomy can choose perfectly well -
 * but **`isActive` is a square-level property**, written by the menu item that switches a square out of
 * service and read one square at a time elsewhere in the same class. So a station Adam had shut was
 * reported to him as one autonomy WILL choose, in the panel he opens to find out why a train is not
 * moving.
 *
 * **Three readers had it**: the Path Type note on the Auto setting, the magenta leg colour, and
 * `AutonomyChecks.checkReversingGoesSomewhere` - a reversing point whose only reachable stations are
 * all switched off counted as leading somewhere.
 *
 * **And the guard written to catch exactly this could not.**
 * `core.testTheAutoTierScopeMatchesTheRuntime` exists to hold the two statements together, and its
 * paraphrase of the runtime omitted the same clause - so the two agreed about a rule neither of them
 * had. That paraphrase is corrected with this; what is here is the deterministic case, because whether
 * the operator's own railway happens to have a shut station is not something a guard should depend on.
 *
 * `single-switch` is the fixture for the reason it usually is: the shutting is done in code, so the
 * claim is about one square changing and nothing else.
 *
 * MUTATION: dropping the `isActive` term from `AutonomySession.stationsAutonomyWillNotChoose` fails
 * `testAShutStationIsReported` and leaves the control green.
 *
 * @author Adam
 */
public class testAShutStationIsOneAutonomyWillNotChoose
{
    private static support.Scenario scenario;

    /**
     * `MainPlatform`, which is a station on this fixture and an auto destination by default.
     */
    private static final int X = 7;

    private static final int Y = 3;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        scenario = support.Scenario.open("single-switch");

        scenario.getModel().stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null)
        {
            try
            {
                scenario.getSession().setPointProperty(scenario.tile(X, Y), "active", null);
            }
            catch (Exception alreadyGone)
            {
            }

            scenario.close();
        }
    }

    /**
     * The control: in service, the square is not on the list and the runtime would choose it.
     *
     * Asserted first and separately. Every claim below is that the square IS on the list, and a set
     * that contained every station would satisfy them without the rule being asked.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAStationInServiceIsNotReported() throws Exception
    {
        inService();

        assertFalse(scenario.getSession().stationsAutonomyWillNotChoose().contains(scenario.tile(X, Y)),
            "MainPlatform is reported as one autonomy leaves alone while it is in service and marked"
            + " as one autonomy may choose, so this class's fixture says nothing");

        Point station = built().getPoint("MainPlatform");

        assertNotNull(station, "there is no square called MainPlatform on this railway");

        assertTrue(built().isSendableDestination(station),
            "the runtime refuses MainPlatform while it is in service, so the two answers below would"
            + " agree for a reason that has nothing to do with switching it off");
    }

    /**
     * Switched out of service, the runtime stops choosing it - which is the half that was never wrong.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheRuntimeStopsChoosingAShutStation() throws Exception
    {
        shut();

        try
        {
            Point station = built().getPoint("MainPlatform");

            assertNotNull(station, "there is no square called MainPlatform on this railway");

            assertFalse(station.isActive(),
                "the square was not shut by setting its `active` property to false, so the claim"
                + " below is about a station that is still in service");

            assertFalse(built().isSendableDestination(station),
                "the runtime still chooses a station switched out of service, so `isActive` is not a"
                + " clause of `isSendableDestination` and E8-B1 is about a rule that does not exist");
        }
        finally
        {
            inService();
        }
    }

    /**
     * And the editor says so too, which is the half that was wrong.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAShutStationIsReported() throws Exception
    {
        shut();

        try
        {
            Set<TileKey> left = scenario.getSession().stationsAutonomyWillNotChoose();

            assertTrue(left.contains(scenario.tile(X, Y)),
                "a station switched OUT OF SERVICE is not reported as one autonomy will never choose,"
                + " and the runtime will never choose it. Three surfaces read this set - the Path Type"
                + " note, the magenta leg colour, and the check that a reversing point leads"
                + " somewhere - and all three told the operator autonomy would use a square he had"
                + " shut. The set says: " + left);
        }
        finally
        {
            inService();
        }
    }

    /**
     * Marks the square as a station autonomy may choose, and in service.
     */
    private void inService()
    {
        scenario.getSession().setAutoDestination(scenario.tile(X, Y), true);
        scenario.getSession().setPointProperty(scenario.tile(X, Y), "active", null);
    }

    /**
     * Switches the square out of service, the way the right-click menu does.
     */
    private void shut()
    {
        scenario.getSession().setAutoDestination(scenario.tile(X, Y), true);
        scenario.getSession().setPointProperty(scenario.tile(X, Y), "active", Boolean.FALSE);
    }

    /**
     * The running layout, rebuilt from the setup as it stands.
     *
     * @return the layout
     * @throws Exception on a failure to build
     */
    private Layout built() throws Exception
    {
        return scenario.build();
    }
}
