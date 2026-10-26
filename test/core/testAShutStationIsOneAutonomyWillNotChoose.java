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
 * `AutonomySession.stationsAutonomyWillNotChoose` is the editor's statement of the runtime's rule,
 * `Layout.isSendableDestination` (`isDestination() && isActive() && isAutoDestination() && !isReversing()`).  The
 * editor asked one of those four: `!isReversing()` genuinely cannot be asked of a square (it is a property of a COPY),
 * but `isActive` is a square-level property, so a station Adam had shut was reported as one autonomy WILL choose, in
 * the panel he opens to find out why a train is not moving.  Three readers had it: the Path Type note on the Auto
 * setting, the magenta leg color, and `AutonomyChecks.checkReversingGoesSomewhere`.
 *
 * The guard written to catch this could not: `core.testTheAutoTierScopeMatchesTheRuntime`'s paraphrase of the runtime
 * omitted the same clause.  This is the deterministic case, since whether the operator's railway has a shut station is
 * not something a guard should depend on; the shutting is done in code on the `single-switch` fixture.
 *
 * MUTATION: dropping the `isActive` term from `AutonomySession.stationsAutonomyWillNotChoose` fails
 * `testAShutStationIsReported` and leaves the control green.
 *
 * @author Adam
 */
public class testAShutStationIsOneAutonomyWillNotChoose
{
    private static support.Scenario scenario;

    /** `MainPlatform`, which is a station on this fixture and an auto destination by default. */
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
     * The control: in service, the square is not on the list and the runtime would choose it.  Asserted first and
     * separately: a set that contained every station would satisfy every claim below without the rule being asked.
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

    /** Switched out of service, the runtime stops choosing it: the half that was never wrong. */
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

    /** And the editor says so too, which is the half that was wrong. */
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

    /** Marks the square as a station autonomy may choose, and in service. */
    private void inService()
    {
        scenario.getSession().setAutoDestination(scenario.tile(X, Y), true);
        scenario.getSession().setPointProperty(scenario.tile(X, Y), "active", null);
    }

    /** Switches the square out of service, the way the right-click menu does. */
    private void shut()
    {
        scenario.getSession().setAutoDestination(scenario.tile(X, Y), true);
        scenario.getSession().setPointProperty(scenario.tile(X, Y), "active", Boolean.FALSE);
    }

    /** The running layout, rebuilt from the setup as it stands. */
    private Layout built() throws Exception
    {
        return scenario.build();
    }
}
