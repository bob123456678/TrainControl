package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train reversed on the throttle turns round in autonomy, from the throttle's call to the setup's facing (MT-488).
 *
 * Adam, 2026-09-24, running TrainControl in simulation: *"reversing it on the throttle has no effect on autonomy.  when no
 * autonomy or manual path is running, have the throttle change its autonomy direction."*  The window follows a turn
 * from the direction MESSAGE (`followDirectionChanges`), which a Central Station sends back for every command - and a
 * simulation sends back nothing unless it echoes packets, which it did not.  So the railway was right and the
 * simulation was mute; Adam, asked: *"You don't need to add that workaround - we can just require the test to echo
 * packets."*
 *
 * What was never claimed is the whole path: `core.testATrainIsPutOnlyWhereItCanStart` calls `flipFacing` itself.  This
 * reverses the train as the throttle does, with packets echoed as a station would, and asks the setup.
 *
 * On the frozen `live-snapshot`, at BottomMainA, with this class's own train standing there facing east.
 *
 * MUTATION: take `followDirectionChanges` out of `TrainControlUI.repaintLoc`, and this fails.
 *
 * @author Adam
 */
public class testAThrottleReversalIsFollowedFromItsEcho
{
    private static final String PROBE = "throttle follow probe";

    /** BottomMainA's square. */
    private static final TileKey MAIN_A = new TileKey("1 - Main", 20, 12);

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static boolean echoWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the turn is followed by the window, and the window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // Simulating, with a window, in debug - the echo below answers only in debug.
        model = init(null, true, true, false, true);

        model.setNetworkCommState(false);

        // ECHOED, as a Central Station answers every command.
        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so nothing follows a turn");

        settle();

        assertNotNull(model.newMM2Locomotive(PROBE, 2397), "could not create this class's train");

        SwingUtilities.invokeAndWait(() -> session = ui.getAutonomySession());

        assertNotNull(session, "the sandbox copy holds no autonomy setup");

        assertEquals(session.getStationIndex().nameOf(MAIN_A), "BottomMainA",
            "the snapshot no longer calls " + MAIN_A + " BottomMainA, so this class is about a railway that has moved");

        // STANDING AT BOTTOMMAINA FACING EAST, on the running layout the window's own session built.
        SwingUtilities.invokeAndWait(() ->
        {
            session.placeLocomotive(MAIN_A, PROBE);
            session.setFacing(MAIN_A, Side.E);

            model.parseAuto(session.buildConfiguration());
        });

        settle();

        assertFalse(model.getAutoLayout().isRunning(), "precondition: something is running, and a turn is not followed"
            + " while it is");

        // What the window has seen, as a refresh at idle leaves it - otherwise the reversal below is a first sighting.
        SwingUtilities.invokeAndWait(() -> ui.reconcileFacingWhenIdle());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

        try
        {
            if (model != null) model.deleteLoc(PROBE);
        }
        catch (Exception alreadyGone)
        {
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Reversed as the throttle reverses it, and the setup follows.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheThrottleTurnsTheTrainRoundInAutonomy() throws Exception
    {
        assertEquals(session.getFacing(MAIN_A), Side.E, "precondition: the train is not recorded facing east");

        Locomotive train = model.getLocByName(PROBE);

        // THE THROTTLE'S OWN CALL - `TrainControlUI.switchDirection` stops the locomotive and sets the other direction.
        train.stop().setDirection(train.goingForward()
            ? Locomotive.locDirection.DIR_BACKWARD : Locomotive.locDirection.DIR_FORWARD);

        Side facing = session.getFacing(MAIN_A);

        for (long until = System.currentTimeMillis() + 10000; System.currentTimeMillis() < until
            && facing != Side.W; )
        {
            settle();

            Thread.sleep(100);

            facing = session.getFacing(MAIN_A);
        }

        assertEquals(facing, Side.W, "reversed on the throttle at BottomMainA, with its command echoed as a Central"
            + " Station echoes it, the train is still recorded facing east - Adam, 2026-09-24: \"reversing it on the"
            + " throttle has no effect on autonomy\"");
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 4; pass++) SwingUtilities.invokeAndWait(() -> { });
    }
}
