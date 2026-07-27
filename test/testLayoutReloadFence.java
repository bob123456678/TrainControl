import java.util.Arrays;
import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The version fence in executePath, which retires a path whose Layout has been replaced.
 *
 * Reloading the autonomy graph builds a new Layout and abandons the old one.  Locomotive threads
 * belonging to the old Layout cannot be interrupted, so executePath instead captures
 * Layout.layoutVersion at the start of a run and re-checks it at every milestone, returning early
 * once a newer Layout exists.
 *
 * That early return used to leave the locomotive at its cruising speed.  Nothing else would ever
 * stop it: retiring a Layout calls stopLocomotives(), which is only `running = false` - it ends the
 * dispatch loop between paths and never commands a locomotive.  So a train between stations kept
 * running, with the replacement graph holding no record of it.
 *
 * These tests drive the fence directly by constructing a second Layout, which is what bumps the
 * counter.  Reloading from the UI now warns first and stops every active locomotive before the swap,
 * so in practice there is usually nothing left for the fence to catch - but that guard is a
 * confirmation the user can accept, not a barrier, and it cannot help a path that started between the
 * stop and the swap.  The fence is the backstop, so it is tested on its own.
 */
public class testLayoutReloadFence
{
    private static MarklinControlStation model;

    /**
     * Debug mode - the last argument - is required, not incidental: setSimulate refuses unless
     * control.isDebug() is set and the power is off (getNetworkCommState reports the power state, not
     * a connection).  init leaves the power off because autoPowerOn is false, and stop() keeps it that
     * way.
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
        model.stop();
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        model.deleteLoc("RF loc");
    }

    /**
     * Three stations in a line, each with feedback so the run pauses at every milestone rather than
     * racing to the end.  Simulation mode fires the feedback itself after the configured delay.
     *
     * Each test passes its own feedback base, because feedback state is held by the control station and
     * not by the Layout - it therefore outlives the Layout that set it.  isPathClear refuses any path
     * whose destination feedback still reads occupied, so a test that abandons a path part way through
     * would otherwise leave its sensors set and block the next test's departure.  Separate modules also
     * keep a late simulation thread from one test off the sensors of another.
     *
     * The bases are high because the modules are created here rather than assumed: createPoint rejects
     * a feedback the control station has never heard of, and setFeedbackState only updates one that
     * already exists - it reports false for anything else rather than creating it.
     */
    private Layout twoLegLayout(int feedbackBase) throws Exception
    {
        Layout layout = new Layout(model);

        layout.setSimulate(true);

        // Max before min - setMinDelay rejects a value above the current maximum
        layout.setMaxDelay(1);
        layout.setMinDelay(1);

        String[] s88 = new String[3];

        for (int i = 0; i < s88.length; i++)
        {
            // Take the name from the module rather than assuming it matches the id
            s88[i] = model.newFeedback(feedbackBase + i, null).getName();

            // Clear it, so a re-run after a failure starts from the same state as a first run
            model.setFeedbackState(s88[i], false);
        }

        layout.createPoint("RF_A", true, s88[0]);
        layout.createPoint("RF_B", true, s88[1]);
        layout.createPoint("RF_C", true, s88[2]);

        layout.createEdge("RF_A", "RF_B");
        layout.createEdge("RF_B", "RF_C");

        return layout;
    }

    private List<Edge> twoLegPath(Layout layout)
    {
        return Arrays.asList(layout.getEdge("RF_A", "RF_B"), layout.getEdge("RF_B", "RF_C"));
    }

    private MarklinLocomotive placeLoc(Layout layout) throws Exception
    {
        MarklinLocomotive loc = model.getLocByName("RF loc");

        if (loc == null)
        {
            loc = model.newDCCLocomotive("RF loc", 50);
        }

        loc.setSpeed(0);
        layout.getPoint("RF_A").setLocomotive(loc);

        return loc;
    }

    /**
     * The fix for the runaway: a path abandoned by the fence leaves the locomotive stopped.
     *
     * The locomotive is at a known milestone at that moment - it has just arrived at an intermediate
     * station - so stopping there is exactly what a graceful stop would have done.
     */
    @Test(timeOut = 90000)
    public void testFencedAbortStopsTheLocomotive() throws Exception
    {
        Layout layout = twoLegLayout(47100);
        MarklinLocomotive loc = placeLoc(layout);

        Thread runner = new Thread(() -> layout.executePath(twoLegPath(layout), loc, 50, null));

        // Daemon, so a hang here can never hold the JVM open
        runner.setDaemon(true);
        runner.start();

        // Wait until it is actually moving, which means the run is past its version capture.  Bumping
        // the counter before that point would test nothing - the run would capture the new value.
        long deadline = System.currentTimeMillis() + 30000;

        while (loc.getSpeed() == 0 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        assertTrue(loc.getSpeed() > 0, "precondition: the locomotive departed and is under way");

        // Retire the layout out from under the running path.  Constructing a Layout is what increments
        // the counter, so this is the same signal a JSON reload produces.
        new Layout(model);

        runner.join(60000);

        assertFalse(runner.isAlive(), "the fenced path must return rather than run to completion");

        assertEquals(loc.getSpeed(), 0,
            "a locomotive abandoned mid-path must be stopped - nothing else will ever command it, "
            + "because retiring a layout only clears its dispatch flag");

        assertTrue(layout.getActiveLocomotives().containsKey(loc),
            "and it must have aborted rather than finished: the abort returns without unlocking the "
            + "path or clearing the active-locomotive entry, which is how this is told apart from a "
            + "normal completion that also ends at speed 0");
    }

    /**
     * The fence must not fire when nothing has replaced the layout - otherwise every path would abort
     * at its first milestone.  This is the control for the test above: same layout, same path, no
     * version bump.
     */
    @Test(timeOut = 90000)
    public void testPathRunsToCompletionWhenNoLayoutReplacesIt() throws Exception
    {
        Layout layout = twoLegLayout(47200);
        MarklinLocomotive loc = placeLoc(layout);

        assertTrue(layout.executePath(twoLegPath(layout), loc, 50, null));

        assertEquals(loc.getSpeed(), 0, "a completed path also ends stopped");

        assertFalse(layout.getActiveLocomotives().containsKey(loc),
            "but a completed path clears its active-locomotive entry, which an abort does not");
    }
}
