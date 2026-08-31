package core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A Central Station that has gone away does not stop every tile in the application for ever.
 *
 * MT-035. Adam: "Switch the Central Station off, leave TrainControl open, press Stop, then click a
 * switch on the diagram. It should pause about two seconds, say the power was not confirmed, and throw
 * the switch anyway - and then the NEXT click should behave the same way rather than doing nothing.
 * Before this, the first such click stopped every tile in the application from ever responding again."
 * Then, on 2026-08-30: "make a simulated test case for this."
 *
 * **Why it could stop everything.** The socket is unconnected, so a datagram sent to a station that
 * has been switched off SUCCEEDS and disappears - no error is raised anywhere. The power state is
 * written in exactly one place, the inbound GO echo, so nothing local can release a wait for it. That
 * wait had no deadline, and it runs on `LayoutLabel.SWITCHING`, which is ONE thread shared by every
 * tile on every page. One click and no tile anywhere responded again, silently, until a restart.
 *
 * **Two halves, and the second is the one the entry is really about.** That the wait gives up is a
 * property of the wait; that the NEXT click still works is a property of the pool, and a bounded wait
 * is only useful because of it. They are tested apart because they can fail apart - a wait that
 * returned but left the thread parked somewhere else would satisfy the first and not the second.
 *
 * The hands-on entry stays open: what the operator sees, and whether the switch is actually thrown, is
 * a question about the diagram and not about these two mechanisms.
 *
 * @author Adam
 */
public class testTheStationGoingAwayDoesNotJamSwitching
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
        model.stop();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The wait for the power gives up, and gives up on time.
     *
     * Nothing here confirms the power, which is exactly what a switched-off station does: the command
     * goes out, the datagram vanishes, and no echo ever arrives.
     *
     * The upper bound is generous - three times the deadline - because this asserts that a bound
     * EXISTS, not that the scheduler is prompt. Without one the call never returns and the test hangs
     * rather than fails, which is why the class-level timeout is there too.
     *
     * MUTATION this catches: removing the deadline from waitForPowerState hangs this test; returning
     * true when it times out fails the first assertion.
     */
    @Test(timeOut = 60000)
    public void testTheWaitForPowerGivesUp() throws Exception
    {
        powerOff();

        assertFalse(model.getPowerState(),
            "precondition: the power must be off, or the wait below returns at once and asks nothing");

        long began = System.currentTimeMillis();

        boolean confirmed = model.waitForPowerState(true, MarklinControlStation.POWER_STATE_TIMEOUT);

        long took = System.currentTimeMillis() - began;

        assertFalse(confirmed,
            "the wait claimed the power came on, with nothing having confirmed it - the tile would "
            + "then say nothing was wrong about a station that is not there");

        assertTrue(took >= MarklinControlStation.POWER_STATE_TIMEOUT - 50,
            "the wait gave up after " + took + "ms, well before its "
            + MarklinControlStation.POWER_STATE_TIMEOUT + "ms deadline - so it is not waiting for the "
            + "station at all and a slow but healthy one would be reported as absent");

        assertTrue(took < MarklinControlStation.POWER_STATE_TIMEOUT * 3,
            "the wait took " + took + "ms against a deadline of "
            + MarklinControlStation.POWER_STATE_TIMEOUT + "ms");
    }

    /**
     * ...and a second wait behaves exactly like the first.
     *
     * "The NEXT click should behave the same way rather than doing nothing" is the sentence this is
     * from. A wait that latched something on its way out - a flag, a monitor, a thread - would let the
     * first call time out honestly and leave the second returning instantly or not at all.
     */
    @Test(timeOut = 60000)
    public void testTheSecondWaitIsLikeTheFirst() throws Exception
    {
        powerOff();

        long first = timeOneWait();
        long second = timeOneWait();

        assertTrue(second >= MarklinControlStation.POWER_STATE_TIMEOUT - 50,
            "the second wait returned after " + second + "ms where the first took " + first
            + "ms, so something the first one left behind is answering for the station");
    }

    /**
     * The one switching thread is still there afterwards.
     *
     * This is what the entry is actually about. `LayoutLabel.SWITCHING` is a single-thread pool shared
     * by every tile in the application; the click handler waits for the power on it. If that wait ever
     * fails to return, the pool is dead and no tile anywhere responds again - which is the symptom
     * Adam described, and it is invisible: nothing is logged and nothing is shown.
     *
     * So: a task that does what a click does with the station gone, and then another, and the second
     * has to run.
     *
     * MUTATION this catches: an unbounded wait inside the first task - which is what the code did
     * before MT-035's fix - leaves the second latch uncounted and fails here rather than hanging the
     * suite, because the wait is on the LATCH and not on the pool.
     */
    @Test(timeOut = 120000)
    public void testTheNextClickStillGetsAThread() throws Exception
    {
        powerOff();

        final CountDownLatch firstDone = new CountDownLatch(1);
        final CountDownLatch secondDone = new CountDownLatch(1);

        org.traincontrol.gui.LayoutLabel.submitSwitching(() ->
        {
            try
            {
                // What the tile does: ask for the power and wait for an echo that will never come.
                model.waitForPowerState(true, MarklinControlStation.POWER_STATE_TIMEOUT);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }
            finally
            {
                firstDone.countDown();
            }
        });

        assertTrue(firstDone.await(30, TimeUnit.SECONDS),
            "the first click never finished, so the switching thread is parked - which is the fault "
            + "this entry is about, seen from inside");

        org.traincontrol.gui.LayoutLabel.submitSwitching(() -> secondDone.countDown());

        assertTrue(secondDone.await(30, TimeUnit.SECONDS),
            "the second click never got the thread.  The pool has one, it is shared by every tile in "
            + "the application, and the first click has kept it - so nothing anywhere responds again");
    }

    /**
     * One timed wait for the power, with nothing to confirm it.
     *
     * @return how long it took, in milliseconds
     */
    private static long timeOneWait() throws InterruptedException
    {
        long began = System.currentTimeMillis();

        model.waitForPowerState(true, MarklinControlStation.POWER_STATE_TIMEOUT);

        return System.currentTimeMillis() - began;
    }
    /**
     * Puts the power down, which is the state a switched-off station leaves behind.
     *
     * `stop()` SENDS a stop and waits for the echo to come back and write the flag - and the echo is
     * exactly what a station that is not there never sends, so on a simulated model the flag stays up
     * and every wait below would return at once, asking nothing. Written directly for that reason: the
     * field has one private writer, deliberately, and this is the one caller that needs to stand in
     * for the station rather than talk to it.
     */
    private static void powerOff() throws Exception
    {
        java.lang.reflect.Field field =
            MarklinControlStation.class.getDeclaredField("powerState");

        field.setAccessible(true);
        field.setBoolean(model, false);

        assertFalse(model.getPowerState(), "the power flag did not go down");
    }

}
