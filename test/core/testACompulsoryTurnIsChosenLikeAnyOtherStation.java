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
 * One switch decides whether autonomy chooses a station, and turning round is not it (OB-195).
 *
 * **Adam's ruling of 2026-09-09**, on being told that the editor's notice claimed autonomy would never
 * choose a compulsory turn while the runtime would choose one quite happily:
 *
 * > *"narrow the notice to match the runtime - autonomy should only allow a turn at a point if the
 * > 'allow in autonomy' option is checked, otherwise the train may only pass through in its current
 * > direction."*
 *
 * The option he names is **Can Be Chosen in Full Autonomy**, the one switch on a station's menu that
 * says whether autonomy may pick it.  So the two markings are independent and each says its own thing:
 *
 *   - **Changing Direction - Never / May / Must** says what happens when a train ARRIVES.
 *   - **Can Be Chosen in Full Autonomy** says who may send one there.
 *
 * A compulsory turn with the switch on is a station autonomy chooses, and the train turns round when
 * it gets there.  With the switch off, autonomy leaves it alone - and a train only ever passes through
 * in the direction it came in, because the turning copy is not somewhere it can be sent.
 *
 * **Where it went wrong.**  `AutonomySession.stationsAutonomyWillNotChoose` added a clause of its own,
 * `isMustTurnAround`, and a comment saying it was the runtime's rule.  It is not: a compulsory turn is
 * built as a TERMINUS, whose `isReversing()` is false, so `Layout.isSendableDestination` accepts it.
 * The editor therefore promised something the railway did not do, on the strength of a comment nobody
 * had checked.
 *
 * **It cost nothing on Adam's own railway**, which is why it survived: every compulsory turn he has is
 * also marked manual-only, so the first clause caught them all and the two spellings could not
 * disagree.  This class asserts on `single-switch`, where they can - its three stub ends are
 * compulsory turns and none of them is marked manual-only.
 *
 * @author Adam
 */
public class testACompulsoryTurnIsChosenLikeAnyOtherStation
{
    private static support.Scenario scenario;

    /**
     * A compulsory turn on the fixture: `MainPlatform` is a buffer stop, so every train that arrives
     * there turns round.
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
     * The fixture really does hold a compulsory turn that is not marked manual-only.
     *
     * Asserted first, because a square that is BOTH is the case Adam's railway is made of and the case
     * in which the defect is invisible.
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
     * A compulsory turn autonomy may choose is not reported as one it will not choose.
     *
     * The narrowing itself.  Before Adam's ruling this set carried every compulsory turn whatever the
     * switch said, so the editor told the operator autonomy would leave this square alone while
     * autonomy was sending trains to it.
     *
     * MUTATION: putting `|| isMustTurnAround(tile)` back into
     * `AutonomySession.stationsAutonomyWillNotChoose` fails this and passes everything else here.
     */
    @Test
    public void testItIsNotReportedAsOneAutonomyLeavesAlone()
    {
        TileKey berth = scenario.tile(X, Y);

        scenario.getSession().setAutoDestination(berth, true);

        Set<TileKey> left = scenario.getSession().stationsAutonomyWillNotChoose();

        assertFalse(left.contains(berth),
            "the editor still reports MainPlatform as a station autonomy never chooses, and autonomy"
            + " chooses it. Turning round says what happens when a train ARRIVES; the switch Adam"
            + " names - Can Be Chosen in Full Autonomy - says who may send one");
    }

    /**
     * And the runtime agrees: it really will send a train to that square.
     *
     * The other half, and the half that makes the first one a correction rather than a preference. A
     * notice narrowed to match a runtime that refuses would be wrong in the other direction.
     *
     * @throws Exception on a failure to build
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
     * With the switch off, both agree again - and that is the whole of the rule.
     *
     * The control. Without it the narrowing above could have emptied the set for every station, and no
     * assertion here would say so.
     *
     * @throws Exception on a failure to build
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
