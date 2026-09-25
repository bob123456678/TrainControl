import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Faults in the layer between this application and the rails, backported to 2.8 from the 3.0 branch.
 *
 * They have nothing in common except where they end up: each takes a railway that is running
 * perfectly well and stops it, or quietly destroys something, without anybody doing anything wrong.
 * None of them announces itself - the connection just reads as lost, or the trains just stop - and the
 * reason is several layers away from anything the operator touched.
 */
public class testControlStationFaults
{
    private static MarklinControlStation model;
    private static boolean wasSimulating;
    private static boolean wasLogging;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        wasSimulating = MarklinControlStation.DEBUG_SIMULATE_PACKETS;
        wasLogging = MarklinControlStation.DEBUG_LOG_NETWORK;

        // Debug mode, because the keepalive test needs the simulated echo that only debug mode gives
        model = init(null, true, false, false, true);

        model.setNetworkCommState(false);
    }

    @AfterClass
    public static void tearDownClass()
    {
        // Both are process-global, so leaving either set would change how every later class in the
        // same JVM sees its own traffic
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = wasSimulating;
        MarklinControlStation.DEBUG_LOG_NETWORK = wasLogging;
    }

    /**
     * The keepalive resumes after a ping goes unanswered.
     *
     * The bug: a ping already in flight silenced the next one, and only a RESPONSE cleared the
     * in-flight mark.  UDP does not promise a response.  So one dropped packet - a Central Station
     * reboot, a moment of wireless - stopped the keepalive for the rest of the session.  The status
     * line read "lost network connection" for ever, including long after the network came back; and
     * because the five-second latency check fires off the same reading, a running layout with a
     * latency limit had its power cut every five seconds, five seconds after each time the operator
     * turned it back on.  Only a restart recovered.
     *
     * Staged rather than mocked, because the fault IS the sequence: send a ping with nothing
     * answering, wait past the retry interval, then let the simulated echo answer and see whether a
     * second ping ever went out.  Under the old code nothing is transmitted, so nothing comes back,
     * and the outage reading never returns to zero.
     *
     * Ported from the 3.0 branch (33403b49).
     */
    @Test
    public void testTheKeepaliveResumesAfterAnUnansweredPing() throws Exception
    {
        // Nothing answering.  This is a lost response, not a missing station: the ping is transmitted
        // and no reply arrives.
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = false;
        MarklinControlStation.DEBUG_LOG_NETWORK = true;

        model.sendPing(true);

        Thread.sleep(2100);

        assertTrue(model.getTimeSinceLastPing() >= 2000,
            "with no answer, the time since the last ping must keep growing - that reading is what "
            + "the lost-connection warning is made of");

        // Now the station answers again, as it would when the network came back
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        model.sendPing(false);

        // The echo is handled on the message processor, so poll rather than assume
        long deadline = System.currentTimeMillis() + 5000;

        while (model.getTimeSinceLastPing() > 0 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        assertEquals(model.getTimeSinceLastPing(), 0,
            "no second ping was ever sent, so nothing could answer it.  One lost packet has ended "
            + "the keepalive for the session: the connection reads as lost for ever, and under "
            + "autonomy the latency cutoff keeps taking the power off every five seconds");
    }

    /**
     * Clearing a point's priority leaves it with no priority, rather than with null.
     *
     * The priority dialog sends null when the box is emptied, which is the obvious way to say "no
     * priority" - and null was stored.  Two things then unbox it: getPriority(), which returns int,
     * and toJSON's `!= 0`.  The first threw inside the comparator that chooses where a train goes
     * next, on a code path with no try around it, so every autonomy thread died on its next pick and
     * autonomy silently stopped dispatching.  The second threw on every attempt to save the graph.
     *
     * Zero already means no priority, so that is what null becomes.
     *
     * Ported from the 3.0 branch (33403b49).
     */
    @Test
    public void testClearingAPriorityDoesNotPoisonThePoint() throws Exception
    {
        // Not a station, which is the only kind of point that needs a sensor.  Priority is asked of
        // every point the search considers, station or not.
        Point p = new Point("cleared priority", false, null);

        p.setPriority(5);
        p.setPriority(null);

        try
        {
            assertEquals(p.getPriority(), 0,
                "an emptied priority box means no priority, and no priority is zero");

            // The two unboxing sites, exercised rather than reasoned about
            assertNotNull(p.toJSON(), "a point with a cleared priority must still be saveable");

            Point other = new Point("other", false, null);

            assertEquals(Integer.compare(p.getPriority(), other.getPriority()), 0,
                "two points with no priority compare equal, which is what the path comparator asks");
        }
        catch (NullPointerException npe)
        {
            fail("clearing a point's priority stored null, and reading it back threw "
                + "NullPointerException - which kills every autonomy thread at its next path pick and "
                + "stops the graph being saved", npe);
        }
    }
}
