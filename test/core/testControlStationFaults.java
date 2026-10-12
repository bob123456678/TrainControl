package core;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.file.CS2File;
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

    @AfterClass(alwaysRun = true)
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

        // A retry, with still nothing answering, and then the same reading again.  This is the half
        // of the fix the assertions above cannot see: the outage clock and the in-flight clock are
        // separate, and reading the retry instead would reset this to nearly zero - a station that
        // has been unreachable for hours looking like one that has been unreachable for a moment,
        // which is exactly the distinction the warning exists to draw.
        model.sendPing(false);

        assertTrue(model.getTimeSinceLastPing() >= 2000,
            "the retry reset the outage clock, so the connection reads as healthy for five seconds "
            + "out of every five while the station says nothing at all");

        // Past the retry window again.  The retry above reset the in-flight clock, which is what it
        // is supposed to do - so asking for another one immediately would be refused as a ping
        // already in flight, and the recovery below would have nothing to answer.
        Thread.sleep(2100);

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

        // Put the shared model back.  The flag is process-global state on an instance every test in
        // this class shares, and leaving it set would have the next save keep a needless copy of a
        // database that loaded perfectly well.
        model.restoreState(missing.getAbsolutePath());

        assertFalse(model.isDatabaseLoadFailed(), "the flag should not outlive this test");
    }

    /**
     * A database file that reads, and holds something other than a locomotive list, is unreadable too (GSP-C1).
     *
     * `restoreState` took the list when the file held one and logged success either way, so a file that deserialized
     * to anything else - written by another program, or swapped in by hand - left the mark that guards the exit save
     * clear.  The application then ran with an empty database, said it had loaded one, and the save on the way out
     * wrote that emptiness over the file with no copy kept: the loss `isDatabaseLoadFailed` exists to prevent, by a
     * third road past it.
     *
     * Written to a temporary file this test owns; the live database is neither read nor written.
     *
     * MUTATION: drop `this.databaseLoadFailed = true;` from `restoreState`'s branch for an object that is not a List,
     * and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testADatabaseFileHoldingSomethingElseIsUnreadable() throws Exception
    {
        java.io.File stranger = java.io.File.createTempFile("tc-not-a-list", ".data");

        java.io.File missing = java.io.File.createTempFile("tc-absent", ".data");

        assertTrue(missing.delete(), "the reset below needs a file that is not there");

        try
        {
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(
                new java.io.FileOutputStream(stranger)))
            {
                out.writeObject("a perfectly good object, and not a locomotive database");
            }

            java.util.List<?> restored = model.restoreState(stranger.getAbsolutePath());

            assertTrue(restored.isEmpty(), "precondition: a file holding no locomotives restored some");

            assertTrue(model.isDatabaseLoadFailed(), "a database file that is THERE and holds something other than a"
                + " locomotive list was taken as a good load.  The application runs with an empty database, and the"
                + " save on the way out writes that over the file with no copy kept - every locomotive customization"
                + " gone (GSP-C1)");
        }
        finally
        {
            stranger.delete();

            // The mark is state on the model every test here shares
            model.restoreState(missing.getAbsolutePath());
        }
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
     *
     * A fold no longer writes a plan: while one has the timetable, the owner's is written, with no flag
     * (RLV9-B1).  What this keeps working is a file a fold wrote before that, with a plan as its
     * timetable and the flag set - stated here by hand, as the file states it (RLV10-C4).
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
     * An MFX locomotive whose file gives no address gets a real address, not its UID.
     *
     * The Central Station leaves the address out for some records, and the importer falls back to the
     * UID - which is the address plus the decoder type's base. The DCC branch has always subtracted
     * its base back off; the MFX branch never did. So such a locomotive was created pointing past the
     * highest MFX address there is: every command sent to a decoder that cannot exist, every state
     * update from the real one unmatched, and the bad address written into the database, where it
     * stays. Nothing catches it later, because only setAddress validates and a fresh import does not
     * go through setAddress.
     */
    @Test
    public void testAnMfxLocomotiveWithNoAddressGetsARealOne() throws Exception
    {
        java.util.Map<String, String> record = new java.util.HashMap<>();

        record.put("_type", "lokomotive");
        record.put("name", "TC_MFX_NO_ADDRESS");
        record.put("typ", "mfx");

        // No "adresse" at all, which is the case the fallback exists for
        record.put("uid", "0x4005");

        java.util.List<java.util.Map<String, String>> records = new java.util.LinkedList<>();
        records.add(record);

        java.util.List<MarklinLocomotive> parsed =
            new CS2File("localhost:8080", model).parseLocomotives(records);

        assertEquals(parsed.size(), 1, "the record should have produced one locomotive");

        MarklinLocomotive loc = parsed.get(0);

        assertEquals(loc.getAddress(), 5,
            "the UID was used as the address without the MFX base being taken back off, so this "
            + "locomotive addresses a decoder that cannot exist - and nothing downstream will "
            + "notice, because only setAddress validates and an import does not go through it");
    }

    /**
     * Two identical commands far enough apart are two commands, not one and an echo.
     *
     * The CS3 sends its responses twice and the second is dropped, which is right - but the drop had
     * no time bound at all, so an accessory told to go to the same place twice with no other traffic
     * in between had the second command swallowed whole: no confirmation, no log, nothing to wake
     * whatever was waiting on the actuation. Repeating a route is an ordinary thing to do.
     */
    @Test
    public void testAnIdenticalCommandLaterIsNotADuplicate() throws Exception
    {
        int before = model.getNumMessagesProcessed();

        model.receiveMessage(accessoryPacket());
        model.receiveMessage(accessoryPacket());

        assertEquals(model.getNumMessagesProcessed(), before + 1,
            "the back-to-back repeat is the station saying the same thing twice, and only one of "
            + "them is news");

        // Past the window a double-send could possibly span
        Thread.sleep(400);

        model.receiveMessage(accessoryPacket());

        assertEquals(model.getNumMessagesProcessed(), before + 2,
            "an identical command a moment later was dropped as an echo.  It is a second real "
            + "command - the same accessory told to go to the same place again - and dropping it "
            + "leaves whatever waits on the actuation waiting");
    }

    /**
     * One accessory command, built fresh each time so the two are equal without being the same object.
     */
    private static CS2Message accessoryPacket()
    {
        return new CS2Message(CS2Message.CMD_ACC_SWITCH, new byte[]{0x00, 0x00, 0x30, 0x40, 0x01, 0x01});
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
    /**
     * A locomotive database that will not read is not saved over while its copy cannot be kept.
     *
     * The save cleared its "unreadable" mark first and then tried the copy.  When the copy failed, the failure was logged
     * and the save went on to replace the unreadable file - the copy the mark was there to guarantee was never made, and
     * the file was gone.  The mark is now cleared only once the copy is known to exist, and until then the file is left
     * as it is; the next save tries again (BPV-C7, fixed for 2.8.2 on master and brought here as RLD-B1).
     *
     * The copy alone is made to fail by a folder, with something in it, standing at every name the copy could be given
     * in the next minute.  It writes the database file, so it runs only where the run has its own copy of the data
     * (one.sh, battery.sh), and puts that copy's bytes back.
     *
     * MUTATION: clear the mark before the copy, or save on after a failed copy, and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testAnUnreadableDatabaseIsNotSavedOverWhileItsCopyCannotBeKept() throws Exception
    {
        java.io.File live = runsOwnFile(MarklinControlStation.DATA_FILE_NAME);

        byte[] was = live.exists() ? java.nio.file.Files.readAllBytes(live.toPath()) : null;

        byte[] unreadable = truncatedObjectStream();

        java.io.File backups = new java.io.File(org.traincontrol.util.Util.dataPath(org.traincontrol.util.Util.BACKUP_FOLDER));
        boolean hadBackups = backups.isDirectory();
        java.util.Set<String> before = names(backups);
        java.util.List<java.io.File> blockers = new java.util.ArrayList<>();

        try
        {
            java.nio.file.Files.write(live.toPath(), unreadable);

            // As at startup: the file is there, and will not read
            model.restoreState(live.getPath());

            assertTrue(model.isDatabaseLoadFailed(), "precondition: the truncated file read as a first launch");

            blockTheCopy(MarklinControlStation.DATA_FILE_NAME, blockers);

            // As on exit, with the copy impossible
            model.saveState(false);

            assertEquals(java.nio.file.Files.readAllBytes(live.toPath()), unreadable, "the unreadable locomotive database"
                + " could not be copied aside, and the save replaced it anyway - no copy kept anywhere, and every"
                + " locomotive's functions, icons, notes and statistics gone (BPV-C7, RLD-B1)");

            // Once the copy can be made, the next save makes it, and then saves
            unblock(blockers);

            model.saveState(false);

            assertEquals(keptCopies(backups, before, unreadable), 1, "the save after the copy became possible did not"
                + " keep the unreadable file");

            assertFalse(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(live.toPath()), unreadable), "once the"
                + " unreadable file was kept, the save must go ahead - refusing for ever would lose whatever the session"
                + " did");
        }
        finally
        {
            unblock(blockers);

            putBack(live, was);

            forgetNew(backups, before, hadBackups);

            // The mark is state on the model every test here shares
            java.io.File missing = java.io.File.createTempFile("tc-absent", ".data");

            missing.delete();

            model.restoreState(missing.getAbsolutePath());
        }
    }

    /**
     * A UI state file that will not read is not saved over while its copy cannot be kept.
     *
     * The window's save had the same order as the database's: the mark cleared, the copy tried, a failed copy logged,
     * and the file replaced - every key mapping and page name gone, with no copy anywhere (BPV-C7, RLD-B1).  The copy is
     * made to fail as above.  The window is one allocated without its constructor, holding only what its save reads,
     * over a model that answers nothing.
     *
     * MUTATION: clear the mark before the copy, or save on after a failed copy, and this fails.
     *
     * @throws Exception from the files or the reflection
     */
    @Test
    public void testAnUnreadableUiStateIsNotSavedOverWhileItsCopyCannotBeKept() throws Exception
    {
        java.io.File live = runsOwnFile("UIState.data");

        // The save ends by writing the diagram windows' titles into the preferences, where it remembers window
        // positions: a run's own preference node, never the real one.
        if (System.getProperty(org.traincontrol.util.Util.PREFERENCES_PROPERTY) == null)
        {
            throw new org.testng.SkipException("Not run here: saving the UI state can write the preferences, so this runs"
                + " only where the run has its own (one.sh, battery.sh)");
        }

        org.traincontrol.gui.TrainControlUI ui = windowless();

        // What the window's save reads, and nothing more
        set(ui, "model", java.lang.reflect.Proxy.newProxyInstance(org.traincontrol.model.ViewListener.class.getClassLoader(),
            new Class<?>[]{ org.traincontrol.model.ViewListener.class }, (proxy, method, args) ->
            {
                Class<?> type = method.getReturnType();

                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == double.class) return 0.0;

                return null;
            }));
        set(ui, "buttonMapping", new java.util.HashMap<Integer, javax.swing.JButton>());
        set(ui, "pageNames", new java.util.HashMap<Integer, String>());

        java.util.List<java.util.HashMap<javax.swing.JButton, org.traincontrol.base.Locomotive>> pages
            = new java.util.ArrayList<>();

        pages.add(new java.util.HashMap<>());

        set(ui, "locMapping", pages);
        set(ui, "popups", new java.util.ArrayList<>());

        byte[] was = live.exists() ? java.nio.file.Files.readAllBytes(live.toPath()) : null;

        byte[] unreadable = truncatedObjectStream();

        java.io.File backups = new java.io.File(org.traincontrol.util.Util.dataPath(org.traincontrol.util.Util.BACKUP_FOLDER));
        boolean hadBackups = backups.isDirectory();
        java.util.Set<String> before = names(backups);
        java.util.List<java.io.File> blockers = new java.util.ArrayList<>();

        try
        {
            java.nio.file.Files.write(live.toPath(), unreadable);

            // As at startup: the file is there, and will not read
            ui.restoreState();

            blockTheCopy("UIState.data", blockers);

            // As on exit, with the copy impossible
            ui.saveState(false);

            assertEquals(java.nio.file.Files.readAllBytes(live.toPath()), unreadable, "the unreadable UI state file could"
                + " not be copied aside, and the save replaced it anyway - no copy kept anywhere, and every key mapping"
                + " and page name gone (BPV-C7, RLD-B1)");

            // Once the copy can be made, the next save makes it, and then saves
            unblock(blockers);

            ui.saveState(false);

            assertEquals(keptCopies(backups, before, unreadable), 1, "the save after the copy became possible did not"
                + " keep the unreadable file");

            assertFalse(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(live.toPath()), unreadable), "once the"
                + " unreadable file was kept, the save must go ahead - refusing for ever would lose whatever the session"
                + " did");
        }
        finally
        {
            unblock(blockers);

            putBack(live, was);

            forgetNew(backups, before, hadBackups);
        }
    }

    /**
     * A first sync that fails is asked about until it works (OB-338).
     *
     * Adam: "there is no warning popup in the UI, and no way to reattempt a connection, if the cs3 ip is reachable but
     * the initial DB sync fails."  The start-up synced once; a failure left the application running with communication
     * off for good, because the one place it was switched on was the line after that sync.  Now the user is asked after
     * each failure, with that failure's reason, and the sync is made again for as long as the answer is to try again.
     *
     * Staged with a sync that fails twice and then works, so the claim is the sequence: two questions, each carrying
     * its own attempt's reason, three syncs, no exit, and a result that is a connection - which is what the
     * constructor turns communication on for.
     *
     * MUTATION: sync only once, or ask with the first failure's reason both times, and this fails.
     */
    @Test
    public void testAFirstSyncThatFailsTwiceIsAskedAboutTwiceThenConnects()
    {
        final int[] syncs = {0};
        final int[] exits = {0};
        final java.util.List<String> asked = new java.util.ArrayList<>();
        final String[] reason = {null};

        int result = MarklinControlStation.syncUntilConnected(
            () ->
            {
                syncs[0]++;

                if (syncs[0] < 3)
                {
                    reason[0] = "attempt " + syncs[0] + " failed";

                    return -1;
                }

                reason[0] = null;

                return 4;
            },
            () -> reason[0],
            error ->
            {
                asked.add(error);

                return true;
            },
            () -> exits[0]++);

        assertEquals(asked, java.util.Arrays.asList("attempt 1 failed", "attempt 2 failed"),
            "each failure must be asked about once, with the reason that failure gave");
        assertEquals(syncs[0], 3, "the sync must be made again after each Resync, until it works");
        assertEquals(exits[0], 0, "nobody chose Exit, so nothing may exit");
        assertTrue(result >= 0, "the third sync worked, so the start must end connected: the constructor turns"
            + " communication on for a result that is not negative");
    }

    /**
     * Choosing Exit exits, once, and syncs no more (OB-338).
     *
     * Two shapes of it: Exit on the first failure, and Exit after one Resync has failed as well.  The sync never works
     * here, so a helper that went on syncing after the answer, or asked again after the exit, would show as a count.
     *
     * MUTATION: sync again after Exit, or never call the exit, and this fails.
     */
    @Test
    public void testChoosingExitExitsOnceAndSyncsNoMore()
    {
        final int[] syncs = {0};
        final int[] exits = {0};
        final java.util.List<String> asked = new java.util.ArrayList<>();

        int result = MarklinControlStation.syncUntilConnected(
            () ->
            {
                syncs[0]++;

                return -1;
            },
            () -> "the station said no",
            error ->
            {
                asked.add(error);

                return false;
            },
            () -> exits[0]++);

        assertEquals(exits[0], 1, "Exit on the first failure must exit, once");
        assertEquals(syncs[0], 1, "and not sync again once the user has chosen to leave");
        assertEquals(asked.size(), 1, "nor ask again");
        assertTrue(result < 0, "an exit is not a connection");

        syncs[0] = 0;
        exits[0] = 0;
        asked.clear();

        final java.util.Iterator<Boolean> answers = java.util.Arrays.asList(true, false).iterator();

        result = MarklinControlStation.syncUntilConnected(
            () ->
            {
                syncs[0]++;

                return -1;
            },
            () -> "still no",
            error ->
            {
                asked.add(error);

                return answers.next();
            },
            () -> exits[0]++);

        assertEquals(syncs[0], 2, "one Resync is one more sync, and the Exit after it is no more");
        assertEquals(asked.size(), 2, "two failures, two questions");
        assertEquals(exits[0], 1, "and the Exit exits once");
        assertTrue(result < 0, "still not a connection");
    }

    /**
     * A first sync that works asks nothing (OB-338).
     *
     * The common start.  Zero is a success - a station with nothing new to add syncs to zero - so it is tried as well
     * as a positive count.
     *
     * MUTATION: ask when the result is zero, or ask before the first sync, and this fails.
     */
    @Test
    public void testAFirstSyncThatWorksAsksNothing()
    {
        for (int worked : new int[]{0, 7})
        {
            final int[] syncs = {0};
            final int[] asks = {0};
            final int[] exits = {0};

            int result = MarklinControlStation.syncUntilConnected(
                () ->
                {
                    syncs[0]++;

                    return worked;
                },
                () -> null,
                error ->
                {
                    asks[0]++;

                    return true;
                },
                () -> exits[0]++);

            assertEquals(result, worked, "the sync's own result must come back unchanged");
            assertEquals(syncs[0], 1, "a sync that worked is not repeated");
            assertEquals(asks[0], 0, "a sync that worked leaves nothing to ask (result " + worked + ")");
            assertEquals(exits[0], 0, "and nothing to exit");
        }
    }

    /**
     * Nothing is asked without a station to reach, a window to ask in, or in a simulation (OB-338).
     *
     * A simulation has no Central Station, so its sync fails by design and every test builds one; a model with no
     * window has nobody to ask.  Either would otherwise put a dialog in front of a suite - or loop for ever in one -
     * so each is its own case, with the other two things present so that only one differs.  The control is the full
     * case, which must ask, and must pass the reason and the answer through unchanged.
     *
     * MUTATION: ask in a simulation, or without a window, or without an address, and this fails.
     */
    @Test
    public void testNothingIsAskedWithoutAStationAWindowOrInASimulation()
    {
        final java.util.List<String> viewAsked = new java.util.ArrayList<>();
        final boolean[] viewAnswer = {true};
        org.traincontrol.model.View view = recordingView(viewAsked, viewAnswer);

        java.util.function.Predicate<String> noStation = MarklinControlStation.resyncAsker(null, false, view);
        java.util.function.Predicate<String> blankStation = MarklinControlStation.resyncAsker("  ", false, view);
        java.util.function.Predicate<String> simulated = MarklinControlStation.resyncAsker("192.168.1.5", true, view);
        java.util.function.Predicate<String> noWindow = MarklinControlStation.resyncAsker("192.168.1.5", false, null);

        assertNull(noStation, "no address is no station to reach");
        assertNull(blankStation, "and a blank one is no address");
        assertNull(simulated, "a simulation has no station, so a sync that fails there is the expected result");
        assertNull(noWindow, "with no window there is nobody to ask");

        // And run through the loop, which is what the constructor does with them: one sync, no question, no exit
        for (java.util.function.Predicate<String> asker : java.util.Arrays.asList(noStation, blankStation, simulated, noWindow))
        {
            final int[] syncs = {0};
            final int[] exits = {0};

            int result = MarklinControlStation.syncUntilConnected(
                () ->
                {
                    syncs[0]++;

                    return -1;
                },
                () -> "unreachable", asker, () -> exits[0]++);

            assertEquals(syncs[0], 1, "with nobody to ask, the one sync is all there is");
            assertEquals(exits[0], 0, "and a failure then is not an exit: the application starts without the station");
            assertTrue(result < 0, "and reports that it did not connect");
        }

        assertTrue(viewAsked.isEmpty(), "the window must not have been asked in any of those cases");

        // The control: a station, a window, a real start
        java.util.function.Predicate<String> asker = MarklinControlStation.resyncAsker("192.168.1.5", false, view);

        assertNotNull(asker, "with a station, a window and a real start, there is somebody to ask");
        assertTrue(asker.test("connection refused"), "the window's answer to resync must come back as true");

        viewAnswer[0] = false;

        assertFalse(asker.test("timed out"), "and its answer to exit as false");
        assertEquals(viewAsked, java.util.Arrays.asList("connection refused", "timed out"),
            "the window must be given the reason, as it was");
    }

    /**
     * A failed sync keeps its reason where the start-up can read it (OB-338).
     *
     * The sync logged the exception and returned minus one, so the caller knew it had failed and not why.  Pointed at
     * a port nothing listens on, which is a station that is there and will not talk.
     *
     * MUTATION: leave the field unset in the catch, and this fails.
     */
    @Test
    public void testAFailedSyncKeepsItsReason() throws Exception
    {
        String was = support.TestStationAddress.get();

        // The sync replaces the model's file parser, which every test here shares: put back as it was, with the address
        java.lang.reflect.Field parser = MarklinControlStation.class.getDeclaredField("fileParser");
        parser.setAccessible(true);
        Object parserWas = parser.get(model);

        try
        {
            support.TestStationAddress.set("127.0.0.1:1");

            assertTrue(model.syncWithCS2() < 0, "a station that refuses every connection cannot be synced with");

            String reason = model.getLastSyncError();

            assertNotNull(reason, "the failure's reason must be kept");
            assertFalse(reason.trim().isEmpty(), "and must say something");
        }
        finally
        {
            support.TestStationAddress.set(was);

            parser.set(model, parserWas);
        }
    }

    /**
     * A simulation never forgets the saved Central Station address (OB-339).
     *
     * A start that did not connect forgets the address, so that the next start asks for one.  A simulation never connects,
     * so every simulation forgot the address of somebody who had only been trying the program out.  Asked of the rule
     * `init` applies, over the three starts that matter and the one that cannot happen, so that a rule widened or
     * narrowed in either direction shows.
     *
     * MUTATION: forget whenever communication is off, simulation or not, and this fails.
     */
    @Test
    public void testASimulationNeverForgetsTheSavedAddress()
    {
        assertFalse(MarklinControlStation.forgetsTheAddress(true, false), "a simulation never connects, and must not forget"
            + " the address saved for the real station");
        assertFalse(MarklinControlStation.forgetsTheAddress(true, true), "and a simulation that somehow reads as connected"
            + " does not either");
        assertTrue(MarklinControlStation.forgetsTheAddress(false, false), "a real start that did not connect forgets the"
            + " address, so that the next start asks for one");
        assertFalse(MarklinControlStation.forgetsTheAddress(false, true), "a real start that connected keeps it");
    }

    /**
     * The first sync of a start goes through the question, and the start says whether it is a simulation (OB-338).
     *
     * The claims above are of the helpers.  What makes them the start-up's is the constructor calling them and `init`
     * telling it whether there is a station: put the constructor back to a bare sync, or have `init` pass false, and every
     * one of them stays green while a failed first sync goes back to leaving the application offline in silence - or a
     * simulation puts a dialog in front of a suite.  The constructor cannot be run here, since it binds the network and
     * the exit ends the process, so its text is read, with the comments out of it.
     *
     * MUTATION: put the restore path back to `if (syncWithCS2() >= 0)`, or pass anything but `simulate` from `init`, and
     * this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheFirstSyncOfAStartGoesThroughTheQuestion() throws Exception
    {
        String code = codeOf("src/org/traincontrol/marklin/MarklinControlStation.java");

        int from = code.indexOf("public MarklinControlStation(NetworkProxy network, View view, boolean autoPowerOn,"
            + " boolean debug, boolean simulate)");
        int to = code.indexOf("this.logf(\"log.csDataImported\"", from);

        assertTrue(from >= 0 && to > from, "precondition: the constructor's restore path was not found in the source");

        String restore = code.substring(from, to);

        assertFalse(restore.contains("if (syncWithCS2()"), "the restore path syncs once and takes the result as it comes,"
            + " so a failed first sync is never asked about");
        assertTrue(restore.contains("syncUntilConnected(this::syncWithCS2, this::getLastSyncError, asker,"),
            "the restore path does not sync through the question, with the reason of the sync that just failed");
        assertTrue(restore.contains("asker, MarklinControlStation::exitUnconnected)"),
            "an Exit answer is not wired to the exit that forgets the saved address");
        assertTrue(restore.contains("asker = resyncAsker(this.cs2Address(), simulate,"),
            "who is asked is not decided from the station's address and whether this is a simulation");

        java.util.regex.Matcher builds = java.util.regex.Pattern.compile("new MarklinControlStation\\(([^;]*)\\);")
            .matcher(code);
        int found = 0;

        while (builds.find())
        {
            found++;

            assertTrue(builds.group(1).endsWith(", simulate"), "a station is built without being told whether it is a"
                + " simulation, so a simulation would ask about its sync: new MarklinControlStation(" + builds.group(1) + ")");
        }

        assertTrue(found > 0, "precondition: init's construction of the station was not found in the source");

        assertTrue(code.contains("if (forgetsTheAddress(simulate, model.getNetworkCommState())) {"
            + " TrainControlUI.getPrefs().remove(TrainControlUI.IP_PREF); }"),
            "init does not decide whether to forget the saved address by the rule above (OB-339)");
    }

    /**
     * Exit forgets the saved address, then ends the application, once each (OB-338, RSA67-C3).
     *
     * The owner's rule is that Exit forgets the saved Central Station address, as a start that does not connect always
     * has.  The claims above run the loop with an exit of their own and read only that the constructor names the
     * method, so nothing ran the method itself: an Exit that left the address in place, or forgot it and did not exit,
     * kept every one of them green.  This runs the two halves through the arguments the method now takes, on a
     * throwaway node under a test-only path and with an exit that only counts, so the operator's own preferences are
     * not read and the JVM does not end.  The address must already be gone when the exit runs, since after
     * `System.exit` nothing does, and the exit must still run when the preferences refuse the removal (a node that
     * has been removed does), or the dialog's Exit would leave the application open.  Other settings in the node stay.
     *
     * MUTATION: drop the `remove`, drop the `exit.run()`, run it before the `remove`, or take the `try` off the
     * `remove`, and this fails.
     *
     * @throws Exception from the preferences
     */
    @Test
    public void testExitForgetsTheSavedAddressAndEnds() throws Exception
    {
        // Under the run's own preferences only, never the operator's: a node the runner made for this JVM
        if (System.getProperty(org.traincontrol.util.Util.PREFERENCES_PROPERTY) == null)
        {
            throw new org.testng.SkipException("Not run here: this writes preferences, so it runs only where the run has"
                + " its own (one.sh, battery.sh)");
        }

        java.util.prefs.Preferences root = org.traincontrol.gui.TrainControlUI.getPrefs();
        java.util.prefs.Preferences scratch = root.node("exitUnconnected");
        java.util.prefs.Preferences gone = root.node("exitUnconnectedRemoved");

        final String key = org.traincontrol.gui.TrainControlUI.IP_PREF;

        try
        {
            scratch.put(key, "192.168.0.99");
            scratch.put("exitUnconnectedOther", "kept");

            final int[] exits = { 0 };
            final String[] addressWhenItEnded = { "not run" };

            MarklinControlStation.exitUnconnected(scratch, () ->
            {
                exits[0]++;
                addressWhenItEnded[0] = scratch.get(key, null);
            });

            assertNull(scratch.get(key, null), "Exit leaves the saved address in place, so the next start asks the same"
                + " question about the same address");
            assertEquals(exits[0], 1, "Exit must end the application exactly once");
            assertNull(addressWhenItEnded[0], "the address is forgotten after the application has ended, which means"
                + " never");
            assertEquals(scratch.get("exitUnconnectedOther", null), "kept", "Exit forgets the address and nothing else");

            gone.removeNode();

            exits[0] = 0;

            MarklinControlStation.exitUnconnected(gone, () -> exits[0]++);

            assertEquals(exits[0], 1, "preferences that refuse the removal must not stop Exit from ending the"
                + " application");
        }
        finally
        {
            scratch.removeNode();

            try
            {
                gone.removeNode();
            }
            catch (IllegalStateException alreadyGone)
            {
                // The claim removed it
            }

            root.flush();
        }
    }

    /**
     * The Exit the dialog reaches is the one that forgets the real address and ends with code 1 (OB-338, RSA67-C3).
     *
     * The claim above runs the halves on a node of its own.  What makes them Exit's is the constructor naming the
     * no-argument method and that method passing the operator's preferences and `System.exit(1)` - the exit code the
     * address prompt gives for leaving the start-up.  Pass another node or another code and the claim above stays
     * green, so the text is read, with the comments out of it, as `testTheFirstSyncOfAStartGoesThroughTheQuestion`
     * reads the constructor's.
     *
     * MUTATION: wire the constructor to anything but `exitUnconnected`, or pass another node, another code or another
     * exit in the no-argument method, and this fails.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testExitUnconnectedForgetsTheRealAddressAndEndsWithCodeOne() throws Exception
    {
        String code = codeOf("src/org/traincontrol/marklin/MarklinControlStation.java");

        assertTrue(code.contains("asker, MarklinControlStation::exitUnconnected)"),
            "an Exit answer is not wired to the method that forgets the saved address");
        assertTrue(code.contains("private static void exitUnconnected() {"
            + " exitUnconnected(TrainControlUI.getPrefs(), () -> System.exit(1)); }"),
            "the method an Exit answer reaches does not forget the address in the operator's preferences and end with"
            + " code 1");
    }

    /**
     * The popup answers by its buttons: Resync resyncs, and Exit or the close box leave (OB-338).
     *
     * The loop's claims put a recording view in the window's place, so nothing above has ever pressed a button.  This
     * calls the real `offerResync` the way the start-up does, from a thread that is not the event thread, finds the
     * dialog by its title, and answers it three ways: the Resync button, the Exit button, and the close box.  It also
     * reads what the dialog says about the failure, and that it stands over the window and on top of it.
     *
     * MUTATION: map any of the three answers the other way in `offerResync`, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheResyncPopupAnswersByItsButtons() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the popup needs a display");

        support.LayoutSandbox sandbox = null;
        final org.traincontrol.gui.TrainControlUI[] made = new org.traincontrol.gui.TrainControlUI[1];

        try
        {
            sandbox = support.LayoutSandbox.open();

            javax.swing.SwingUtilities.invokeAndWait(() -> made[0] = new org.traincontrol.gui.TrainControlUI());

            assertTrue(askAndAnswer(made[0], "ui.startupSyncResync"), "Resync must come back as an answer to sync again");
            assertFalse(askAndAnswer(made[0], "ui.main.toolbar.exit"), "Exit must come back as an answer to leave");
            assertFalse(askAndAnswer(made[0], null), "the close box must come back as an answer to leave");
        }
        finally
        {
            // Whatever a failed claim left standing, then the window
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (window instanceof javax.swing.JDialog && window.isShowing()
                        && org.traincontrol.util.I18n.t("ui.startupSyncFailedTitle").equals(((javax.swing.JDialog) window).getTitle()))
                    {
                        window.dispose();
                    }
                }

                if (made[0] != null)
                {
                    made[0].stopDrivingTrainsFromTheWholeWindow();
                    made[0].dispose();
                }
            });

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Asks through the window's `offerResync` from a worker thread and answers the popup it puts up.
     *
     * @param ui the window
     * @param button the key of the button to press, or null to close the dialog by its close box
     * @return what `offerResync` returned
     */
    private static boolean askAndAnswer(org.traincontrol.gui.TrainControlUI ui, String button) throws Exception
    {
        final String reason = "SocketTimeoutException: connect timed out";
        final Boolean[] answer = new Boolean[1];
        final Throwable[] failed = new Throwable[1];

        Thread worker = new Thread(() ->
        {
            try
            {
                answer[0] = ui.offerResync(reason);
            }
            catch (Throwable t)
            {
                failed[0] = t;
            }
        }, "resync-asker");

        worker.setDaemon(true);
        worker.start();

        String title = org.traincontrol.util.I18n.t("ui.startupSyncFailedTitle");
        javax.swing.JDialog dialog = null;

        for (long giveUp = System.currentTimeMillis() + 10000; dialog == null && System.currentTimeMillis() < giveUp; )
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing() && title.equals(((javax.swing.JDialog) window).getTitle()))
                {
                    dialog = (javax.swing.JDialog) window;
                }
            }

            if (dialog == null) Thread.sleep(50);
        }

        assertNotNull(dialog, "no popup titled \"" + title + "\" appeared");

        final javax.swing.JDialog popup = dialog;

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            assertSame(popup.getOwner(), ui, "the popup must stand over the window, or it can be hidden behind it");
            assertTrue(popup.isAlwaysOnTop(), "and be on top, or the splash and other windows can cover it");
            assertTrue(labelsOf(popup).stream().anyMatch(text -> text.contains(reason)),
                "the popup must say what went wrong: " + labelsOf(popup));

            if (button == null)
            {
                popup.dispatchEvent(new java.awt.event.WindowEvent(popup, java.awt.event.WindowEvent.WINDOW_CLOSING));

                return;
            }

            javax.swing.AbstractButton pressed = buttonLabelled(popup, org.traincontrol.util.I18n.t(button));

            assertNotNull(pressed, "the popup has no button labelled " + org.traincontrol.util.I18n.t(button));

            pressed.doClick();
        });

        worker.join(10000);

        assertFalse(worker.isAlive(), "offerResync did not return once the popup was answered");
        assertNull(failed[0], "offerResync failed: " + failed[0]);
        assertNotNull(answer[0], "offerResync gave no answer");

        return answer[0];
    }

    /**
     * The button in a window with exactly this label, or null.
     */
    private static javax.swing.AbstractButton buttonLabelled(java.awt.Container container, String label)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.AbstractButton && label.equals(((javax.swing.AbstractButton) child).getText()))
            {
                return (javax.swing.AbstractButton) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.AbstractButton found = buttonLabelled((java.awt.Container) child, label);

                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * Every label's text in a window, in the order they are found.
     */
    private static java.util.List<String> labelsOf(java.awt.Container container)
    {
        java.util.List<String> texts = new java.util.ArrayList<>();

        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JLabel) texts.add(((javax.swing.JLabel) child).getText());

            if (child instanceof java.awt.Container) texts.addAll(labelsOf((java.awt.Container) child));
        }

        return texts;
    }

    /**
     * A source file as code: the comments taken out and every run of white space made one space.
     */
    private static String codeOf(String path) throws Exception
    {
        String text = new String(java.nio.file.Files.readAllBytes(new java.io.File(path).toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        return text.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*", " ").replaceAll("\\s+", " ");
    }

    /**
     * A view that records what it is asked to resync about and answers as told; every other call does nothing.
     */
    private static org.traincontrol.model.View recordingView(java.util.List<String> asked, boolean[] answer)
    {
        return (org.traincontrol.model.View) java.lang.reflect.Proxy.newProxyInstance(
            org.traincontrol.model.View.class.getClassLoader(), new Class<?>[]{ org.traincontrol.model.View.class },
            (proxy, method, args) ->
            {
                if (method.getName().equals("offerResync"))
                {
                    asked.add((String) args[0]);

                    return answer[0];
                }

                Class<?> type = method.getReturnType();

                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == double.class) return 0.0;

                return null;
            });
    }

    /**
     * The run's own copy of a data file, or a skip where the run has none: these tests write the file.
     */
    private static java.io.File runsOwnFile(String name)
    {
        if (System.getProperty(org.traincontrol.util.Util.DATA_DIR_PROPERTY) == null)
        {
            throw new org.testng.SkipException("Not run here: this writes " + name + ", so it runs only where the run has"
                + " its own copy of the data (one.sh, battery.sh)");
        }

        return new java.io.File(org.traincontrol.util.Util.dataPath(name));
    }

    /**
     * A file that stops part way through its first object, as an interrupted copy or sync leaves one.
     */
    private static byte[] truncatedObjectStream()
    {
        return new byte[]{ (byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x73, 0x72 };
    }

    /**
     * The names of the files in a folder, or none if it does not exist.
     */
    private static java.util.Set<String> names(java.io.File folder)
    {
        java.util.Set<String> out = new java.util.HashSet<>();

        String[] list = folder.list();

        if (list != null) out.addAll(java.util.Arrays.asList(list));

        return out;
    }

    /**
     * How many files in the folder, other than those listed before, hold exactly these bytes.
     */
    private static int keptCopies(java.io.File folder, java.util.Set<String> before, byte[] content) throws Exception
    {
        int kept = 0;

        for (String name : names(folder))
        {
            if (before.contains(name)) continue;

            java.io.File file = new java.io.File(folder, name);

            if (file.isFile() && java.util.Arrays.equals(java.nio.file.Files.readAllBytes(file.toPath()), content)) kept++;
        }

        return kept;
    }

    /**
     * Makes the copy of an unreadable file - and only the copy - fail for the next minute: a folder, with a file in it,
     * at every name the copy could be given, which a copy that replaces what is there cannot remove.  Undone by unblock.
     */
    private static void blockTheCopy(String fileName, java.util.List<java.io.File> made) throws Exception
    {
        // The middle of each second from the one before this to a minute after: a name is to the second, and a time
        // exactly on the minute would be written without its seconds
        long now = System.currentTimeMillis();
        long first = now - now % 1000 - 1000 + 500;

        for (long t = first; t <= now + 60000; t += 1000)
        {
            // Named exactly as the saves name the copy
            java.io.File folder = new java.io.File(org.traincontrol.util.Util.getBackupPath("unreadable"
                + org.traincontrol.util.Conversion.convertSecondsToDatetime(t).replace(':', '-').replace(' ', '_')
                + fileName));

            if (folder.exists()) continue;

            assertTrue(folder.mkdirs(), "the fixture could not make " + folder);
            made.add(folder);

            java.io.File inside = new java.io.File(folder, "blocker");
            java.nio.file.Files.write(inside.toPath(), new byte[]{ 1 });
            made.add(inside);
        }
    }

    /**
     * Removes what blockTheCopy made, innermost first.
     */
    private static void unblock(java.util.List<java.io.File> made) throws Exception
    {
        for (int i = made.size() - 1; i >= 0; i--)
        {
            java.nio.file.Files.deleteIfExists(made.get(i).toPath());
        }

        made.clear();
    }

    /**
     * The run's copy of a data file back as it was, or gone where there was none.
     */
    private static void putBack(java.io.File live, byte[] was) throws Exception
    {
        if (was == null) java.nio.file.Files.deleteIfExists(live.toPath());
        else java.nio.file.Files.write(live.toPath(), was);
    }

    /**
     * Every file the test left in the backup folder removed, and the folder too where it was not there before.
     */
    private static void forgetNew(java.io.File backups, java.util.Set<String> before, boolean hadBackups) throws Exception
    {
        for (String name : names(backups))
        {
            if (!before.contains(name)) java.nio.file.Files.deleteIfExists(new java.io.File(backups, name).toPath());
        }

        if (!hadBackups) java.nio.file.Files.deleteIfExists(backups.toPath());
    }

    /**
     * A main window with no constructor run, for a test of a method that reads only the fields it is given.
     */
    private static org.traincontrol.gui.TrainControlUI windowless() throws Exception
    {
        // Reached reflectively so that the compiler does not warn about the internal class
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);

        return (org.traincontrol.gui.TrainControlUI) unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(theUnsafe.get(null), org.traincontrol.gui.TrainControlUI.class);
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        java.lang.reflect.Field field = org.traincontrol.gui.TrainControlUI.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
