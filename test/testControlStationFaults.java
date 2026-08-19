import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Faults an independent review found below the interface, in the layers the earlier reviews had
 * taken as settled.
 *
 * They have nothing in common except where they end up: every one of them takes a railway that is
 * running perfectly well and stops it, or quietly destroys something, without anybody doing anything
 * wrong. That is the class of bug worth a test apiece, because none of them announces itself - the
 * train just stops, or the power just goes off, or the customizations are gone, and the reason is
 * three layers away from anything the operator touched.
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
     * A system frame too short to carry a sub command is not read as a stop.
     *
     * The bug, stated as a layout would meet it: some other participant on the CAN bus - another
     * controller, a booster, anything the station gateways onto the network - sends a system frame
     * with four payload bytes. TrainControl read the fifth byte anyway, found the zero that was never
     * written there, and zero is STOP. The application then believed the power had been cut: power
     * state off, every locomotive told so (which corrupts the running-time accounting), the indicator
     * dark, and everything waiting on the power state released. On a layout still visibly running.
     *
     * The guard meant to prevent this tested `data.length`, which is eight for every message that has
     * ever been parsed, because the parsing constructor always allocates eight bytes and fills only as
     * many as the frame declared. The field that says how many were filled is `length`.
     */
    @Test
    public void testAShortSystemFrameIsNotAStop()
    {
        assertEquals(new CS2Message(systemFrame(4)).getSubCommand(), -1,
            "a system frame declaring four payload bytes has no sub command byte, so there is no "
            + "answer to give - and the answer it gave was zero, which is CMD_SYSSUB_STOP: a frame "
            + "that says nothing read as an order to cut the power");
    }

    /**
     * And a real stop is still a stop, so the guard above did not simply switch the feature off.
     *
     * Worth its own test rather than a line in the one above: a fix to a false positive that reaches
     * far enough to suppress the true positives is the more dangerous bug of the two. A stop from
     * another controller is how a person at the layout hits an emergency stop.
     */
    @Test
    public void testARealStopIsStillReadAsAStop()
    {
        byte[] stop = systemFrame(5);
        stop[9] = (byte) CS2Message.CMD_SYSSUB_STOP;

        assertEquals(new CS2Message(stop).getSubCommand(), CS2Message.CMD_SYSSUB_STOP,
            "a five byte system frame carrying the stop sub command must still read as a stop");

        byte[] go = systemFrame(5);
        go[9] = (byte) CS2Message.CMD_SYSSUB_GO;

        assertEquals(new CS2Message(go).getSubCommand(), CS2Message.CMD_SYSSUB_GO,
            "and a go must still read as a go");
    }

    /**
     * The keepalive resumes after a ping goes unanswered.
     *
     * The bug: a ping already in flight silenced the next one, and only a RESPONSE cleared the
     * in-flight mark. UDP does not promise a response. So one dropped packet - a station reboot, a
     * moment of wireless - stopped the keepalive for the rest of the session. The status line read
     * "lost connection" for ever, including long after the network came back; and because the
     * five-second latency check fires off the same reading, a running layout with a latency limit had
     * its power cut every five seconds, five seconds after each time the operator turned it back on.
     *
     * Staged rather than mocked, because the fault IS the sequence: send a ping with nothing
     * answering, wait past the retry, then let the echo answer and see whether a second ping ever
     * went out. Under the old code nothing is transmitted, so nothing comes back, and the outage
     * reading never returns to zero.
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
     * priority" - and null was stored. Two things then unbox it: getPriority(), which returns int,
     * and toJSON's `!= 0`. The first threw inside the comparator that chooses where a train goes
     * next, on a code path outside the try that guards path execution, so the locomotive's dispatch
     * thread died and that train silently stopped being sent anywhere for the rest of the session.
     * The second threw on every attempt to save the layout.
     *
     * Zero already means no priority, so that is what null becomes - the same fix setMaxTrainLength
     * carries, for the same reason.
     */
    @Test
    public void testClearingAPriorityDoesNotPoisonThePoint() throws Exception
    {
        // Not a destination, which is the only kind of point that needs an s88.  Priority is asked of
        // every point the search considers, destination or not.
        Point p = new Point("cleared priority", false, null);

        p.setPriority(5);
        p.setPriority(null);

        assertEquals(p.getPriority(), 0,
            "an emptied priority box means no priority, and no priority is zero");

        // The two unboxing sites, exercised rather than reasoned about
        assertNotNull(p.toJSON(), "a point with a cleared priority must still be saveable");

        Point other = new Point("other", false, null);

        assertEquals(p.getPriority() == other.getPriority(), true,
            "two points with no priority compare equal, which is what the path comparator asks - and "
            + "asking it was what threw");
    }

    /**
     * A database file that exists and will not read is told apart from one that is not there.
     *
     * That distinction is the whole fix, so it is the thing to test. A load failure was silent: the
     * application carried on with an EMPTY locomotive database and the save on the way out wrote that
     * emptiness over the real file - a complete, successful write, so the atomic-write staging that
     * protects against dying mid-save does not help at all. One transiently locked file at startup
     * plus a normal exit destroyed every locomotive customization the user had ever made, and the
     * backups are manual.
     *
     * Getting the discrimination wrong in the other direction would be its own bug: treating a first
     * launch as a failed load would make the application keep a copy of a file that was never there
     * and warn about data nobody has.
     */
    @Test
    public void testAnUnreadableDatabaseIsToldApartFromAbsentOne() throws Exception
    {
        java.io.File missing = java.io.File.createTempFile("tc-absent", ".data");
        assertTrue(missing.delete(), "the point of this one is that the file is not there");

        model.restoreState(missing.getAbsolutePath());

        assertFalse(model.isDatabaseLoadFailed(),
            "a database that is not there is a first launch, and there is nothing to lose - "
            + "treating it as a failure would warn about data nobody has");

        // Present, and not a serialized component list
        java.io.File corrupt = java.io.File.createTempFile("tc-corrupt", ".data");

        try (java.io.FileOutputStream out = new java.io.FileOutputStream(corrupt))
        {
            out.write("this is not an object stream".getBytes("UTF-8"));
        }

        model.restoreState(corrupt.getAbsolutePath());

        assertTrue(model.isDatabaseLoadFailed(),
            "a database file that is THERE and will not read is not a first launch.  Read as one, "
            + "the empty database it leaves behind gets saved over the real thing on the way out, "
            + "and every locomotive customization is gone with no undo");

        corrupt.delete();
    }

    /**
     * A timetable that has to run one entry at a time still does after being saved and loaded.
     *
     * The flag exists because a staging plan's moves contend: dispatched in parallel, the second
     * takes an edge the planner never considered and retries for ever on a route it cannot abandon.
     * That was observed before the flag existed. It was written into the plan and not into the file,
     * so saving a return-home plan and reloading it brought back exactly the failure the flag was
     * added to prevent - and brought it back silently, because everything about the timetable looks
     * right until it runs.
     */
    @Test
    public void testAStagingPlanIsStillSequentialAfterASaveAndLoad() throws Exception
    {
        Layout layout = Layout.fromJSON(minimalLayout(), model);

        assertTrue(layout.isValid(), "the fixture itself has to load: " + layout.getInvalidReason());

        layout.setTimetableSequential(true);

        Layout reloaded = Layout.fromJSON(layout.toJSON(), model);

        assertTrue(reloaded.isTimetableSequential(),
            "the timetable came back as an ordinary one, so its entries will be dispatched as soon "
            + "as the previous entry STARTS rather than arrives - which is the contention this flag "
            + "was added to prevent");
    }

    /**
     * And an ordinary timetable is not turned into a sequential one by the same round trip.
     *
     * Overlapping execution is the normal behaviour and much the faster one; a flag that defaulted
     * the wrong way would make every layout's timetable crawl for no visible reason.
     */
    @Test
    public void testAnOrdinaryTimetableStaysParallel() throws Exception
    {
        Layout layout = Layout.fromJSON(minimalLayout(), model);

        Layout reloaded = Layout.fromJSON(layout.toJSON(), model);

        assertFalse(reloaded.isTimetableSequential(),
            "an ordinary layout came back sequential, so every timetable would wait for each entry "
            + "to arrive before starting the next");
    }

    /**
     * The smallest layout fromJSON will accept: two points and the edge between them.
     *
     * Built rather than borrowed because an EMPTY layout is not a valid one - fromJSON invalidates it
     * before it ever reaches the timetable - and the first version of these tests used one, which made
     * them fail for a reason that had nothing to do with what they were asking.
     */
    private static String minimalLayout()
    {
        return "{"
            + "\"points\": ["
            + "  {\"name\":\"TT_a\",\"station\":true,\"s88\":180},"
            + "  {\"name\":\"TT_b\",\"station\":true,\"s88\":181}"
            + "],"
            + "\"edges\": ["
            + "  {\"start\":\"TT_a\",\"end\":\"TT_b\",\"length\":1}"
            + "],"
            + "\"minDelay\":1,\"maxDelay\":2,\"defaultLocSpeed\":35}";
    }

    /**
     * A raw thirteen byte CAN frame carrying a system command with the given payload length.
     *
     * Built by hand rather than through the outgoing constructor, because the whole point is a frame
     * whose declared length and actual buffer disagree - which is every frame that arrives off the
     * network, and none that this application builds.
     */
    private static byte[] systemFrame(int payloadLength)
    {
        byte[] raw = new byte[CS2Message.MESSAGE_LENGTH];

        // Priority 0, command CMD_SYSTEM (0x00), response bit clear.  The system branch of
        // receiveMessage deliberately ignores the response bit, so it makes no difference here.
        raw[0] = 0;
        raw[1] = 0;

        raw[2] = 0x47;
        raw[3] = 0x11;

        raw[4] = (byte) payloadLength;

        // Four bytes of UID, which is all a short system frame carries
        raw[5] = 0;
        raw[6] = 0;
        raw[7] = 0;
        raw[8] = 0;

        return raw;
    }
}
