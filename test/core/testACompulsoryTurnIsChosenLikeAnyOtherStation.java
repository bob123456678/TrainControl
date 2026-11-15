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
 * One switch decides whether autonomy chooses a station, and turning around is not it (OB-195).  Runs on
 * `single-switch`, whose three stub ends are compulsory turns and none is manual-only.
 *
 * @author Adam
 */
public class testACompulsoryTurnIsChosenLikeAnyOtherStation
{
    private static support.Scenario scenario;

    /**
     * A compulsory turn on the fixture: `MainPlatform` is a buffer stop, so every train that arrives there turns around.
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
        if (scenario != null) scenario.close();
    }

    /**
     * The fixture really does hold a compulsory turn that is not marked manual-only; asserted first, because a square
     * that is BOTH is the case in which the defect is invisible.
     */
    @Test
    public void testTheFixtureSeparatesTheTwoMarkings()
    {
        TileKey berth = scenario.tile(X, Y);

        assertTrue(scenario.getSession().isMustTurnAround(berth),
            "MainPlatform is not a compulsory turn on this fixture, so nothing below is about one."
            + " The scenario's README states it as an invariant - the three stub ends carry"
            + " mustReverse, because that is what a buffer stop is");

        assertTrue(scenario.getSession().isAutoDestination(berth),
            "MainPlatform is already marked as one autonomy leaves alone, which is the case where the"
            + " two markings agree by accident - and the case in which this defect cannot be seen");
    }

    /**
     * A compulsory turn autonomy may choose is not reported as one it will not choose: the editor must not say
     * autonomy leaves the square alone while it sends trains there.
     *
     * MUTATION: putting `|| isMustTurnAround(tile)` back into `AutonomySession.stationsAutonomyWillNotChoose` fails
     * this and passes everything else here.
     */
    @Test
    public void testItIsNotReportedAsOneAutonomyLeavesAlone()
    {
        TileKey berth = scenario.tile(X, Y);

        scenario.getSession().setAutoDestination(berth, true);

        Set<TileKey> left = scenario.getSession().stationsAutonomyWillNotChoose();

        assertFalse(left.contains(berth),
            "the editor still reports MainPlatform as a station autonomy never chooses, and autonomy"
            + " chooses it. Turning around says what happens when a train ARRIVES; the switch Adam"
            + " names - Can Be Chosen in Full Autonomy - says who may send one");
    }

    /**
     * And the runtime agrees: it really will send a train to that square. The other half, which makes the first a
     * correction rather than a preference.
     */
    @Test
    public void testTheRuntimeWouldChooseIt() throws Exception
    {
        TileKey berth = scenario.tile(X, Y);

        scenario.getSession().setAutoDestination(berth, true);

        Layout built = scenario.build();

        Point station = built.getPoint("MainPlatform");

        assertNotNull(station, "there is no square called MainPlatform on this railway");

        assertTrue(station.isTerminus(),
            "MainPlatform is not built as a terminus, so the reasoning above - that a compulsory turn"
            + " has isReversing() false and is therefore sendable - is about some other square");

        assertTrue(built.isSendableDestination(station),
            "the runtime refuses to send a train to MainPlatform, so the editor was right to report it"
            + " and the narrowing is a defect rather than a repair");
    }

    /**
     * With the switch off, both agree again. The control: without it the narrowing above could have emptied the set for
     * every station and no assertion here would say so.
     */
    @Test
    public void testTheSwitchIsWhatDecides() throws Exception
    {
        TileKey berth = scenario.tile(X, Y);

        scenario.getSession().setAutoDestination(berth, false);

        try
        {
            assertTrue(scenario.getSession().stationsAutonomyWillNotChoose().contains(berth),
                "MainPlatform is marked as one autonomy leaves alone and the editor does not report"
                + " it, so the notice has stopped saying anything at all");

            Layout built = scenario.build();

            Point station = built.getPoint("MainPlatform");

            assertNotNull(station, "there is no square called MainPlatform on this railway");

            assertFalse(built.isSendableDestination(station),
                "the runtime still chooses MainPlatform with the switch off, so the switch does not"
                + " decide anything and the notice is describing a rule that is not enforced");
        }
        finally
        {
            scenario.getSession().setAutoDestination(berth, true);
        }
    }
}
