import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.Util;
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

        // Still nothing answering: the keepalive resends, and the silence is still measured from the
        // FIRST unanswered ping.  Measured from the resend, the reading would fall back at every retry,
        // so a station silent for an hour would read like one silent for two seconds - and both the
        // lost-connection warning and the latency power cut read this figure (BPV-C1).
        model.sendPing(false);

        Thread.sleep(2100);

        long silence = model.getTimeSinceLastPing();

        assertTrue(silence >= 4000,
            "the time since the last ping fell back when the unanswered ping was resent (" + silence
            + "ms after two unanswered pings 2.1s apart) - it must measure the whole silence, from the "
            + "first unanswered ping, or the lost-connection warning and the latency cutoff go quiet "
            + "during a real outage");

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

    /**
     * A locomotive database that is there but will not read is copied aside before it is saved over.
     *
     * restoreState reported an unreadable LocDB.data exactly as it reports a first launch: an empty
     * list and "initializing with default data".  The application then ran with an empty database,
     * and the normal save on exit wrote that emptiness over the real file - a complete, successful
     * write, so writing atomically does not help.  One transient lock at startup (antivirus, a cloud
     * sync placeholder, a second copy of the program) plus an ordinary exit destroyed every locomotive
     * customization: functions, icons, notes and statistics.
     *
     * This runs the real sequence - an unreadable file at startup, then the exit save - so it needs
     * LocDB.data in the working directory to be one it may replace.  It refuses to run where a real
     * database is present.
     *
     * Ported from the 3.0 branch (df5d291b).
     */
    @Test
    public void testAnUnreadableDatabaseIsKeptBeforeItIsSavedOver() throws Exception
    {
        File live = new File(MarklinControlStation.DATA_FILE_NAME);

        if (live.exists())
        {
            throw new SkipException("Not run here: the working directory holds a locomotive database ("
                + live.getAbsolutePath() + "), and this test has to put an unreadable one in its place");
        }

        byte[] unreadable = truncatedObjectStream();

        File backups = new File(Util.BACKUP_FOLDER);
        boolean hadBackups = backups.isDirectory();
        Set<String> before = names(backups);

        try
        {
            Files.write(live.toPath(), unreadable);

            // As at startup: the file is there, and will not read
            model.restoreState(MarklinControlStation.DATA_FILE_NAME);

            // As on exit
            model.saveState(false);

            assertEquals(keptCopies(backups, before, unreadable), 1,
                "the locomotive database could not be read at startup, and the save on exit wrote the "
                + "empty database over it without keeping a copy - every locomotive's functions, "
                + "icons, notes and statistics would be gone");

            // And a database that reads is a normal save: nothing more is copied aside
            model.restoreState(MarklinControlStation.DATA_FILE_NAME);
            model.saveState(false);

            assertEquals(names(backups).size(), before.size() + 1,
                "a readable database was copied aside as though it were unreadable");
        }
        finally
        {
            Files.deleteIfExists(live.toPath());

            for (String name : names(backups))
            {
                if (!before.contains(name)) Files.deleteIfExists(new File(backups, name).toPath());
            }

            if (!hadBackups) Files.deleteIfExists(backups.toPath());
        }
    }

    /**
     * A locomotive database that will not read is not saved over while its copy cannot be kept.
     *
     * The save cleared its "unreadable" mark first and then tried the copy.  When the copy failed, the
     * failure was logged and the save went on to replace the unreadable file - the copy the mark was
     * there to guarantee was never made, and the file was gone.  The mark is now cleared only once the
     * copy is known to exist, and until then the file is left as it is; the next save tries again
     * (BPV-C7).
     *
     * The copy alone is made to fail by a folder, with something in it, standing at every name the copy
     * could be given in the next minute - the file itself, and the save beside it, are untouched.  Like
     * the test above, this refuses to run where a real database is present.
     */
    @Test
    public void testAnUnreadableDatabaseIsNotSavedOverWhileItsCopyCannotBeKept() throws Exception
    {
        File live = new File(MarklinControlStation.DATA_FILE_NAME);

        if (live.exists())
        {
            throw new SkipException("Not run here: the working directory holds a locomotive database ("
                + live.getAbsolutePath() + "), and this test has to put an unreadable one in its place");
        }

        byte[] unreadable = truncatedObjectStream();

        File backups = new File(Util.BACKUP_FOLDER);
        boolean hadBackups = backups.isDirectory();
        Set<String> before = names(backups);
        List<File> blockers = new ArrayList<>();

        try
        {
            Files.write(live.toPath(), unreadable);

            // As at startup: the file is there, and will not read
            model.restoreState(MarklinControlStation.DATA_FILE_NAME);

            blockTheCopy(MarklinControlStation.DATA_FILE_NAME, blockers);

            // As on exit, with the copy impossible
            model.saveState(false);

            assertEquals(Files.readAllBytes(live.toPath()), unreadable,
                "the unreadable locomotive database could not be copied aside, and the save replaced it "
                + "anyway - no copy kept anywhere, and every locomotive's functions, icons, notes and "
                + "statistics gone (BPV-C7)");

            // Once the copy can be made, the next save makes it, and then saves
            unblock(blockers);

            model.saveState(false);

            assertEquals(keptCopies(backups, before, unreadable), 1,
                "the save after the copy became possible did not keep the unreadable file");

            assertFalse(Arrays.equals(Files.readAllBytes(live.toPath()), unreadable),
                "once the unreadable file was kept, the save must go ahead - refusing for ever would "
                + "lose whatever the session did");
        }
        finally
        {
            unblock(blockers);

            Files.deleteIfExists(live.toPath());

            for (String name : names(backups))
            {
                if (!before.contains(name)) Files.deleteIfExists(new File(backups, name).toPath());
            }

            if (!hadBackups) Files.deleteIfExists(backups.toPath());
        }
    }

    /**
     * An empty locomotive database - the commonest corruption - is saved over once its copy is kept.
     *
     * The load opened the file inside the stream that reads its header, as one resource; an empty file throws in that
     * header, before the resource is assigned, so the file was never closed.  On Windows a file left open cannot be
     * replaced, so the exit save kept the copy and then failed to write - and the next session started empty again,
     * until a garbage collection happened to close the file (BPV-C8, found by the validator of the 2.8.2 backports).
     *
     * MUTATION: open the file inside the header's resource again, and this fails on Windows.
     */
    @Test
    public void testAnEmptyDatabaseIsSavedOverOnceItsCopyIsKept() throws Exception
    {
        File live = new File(MarklinControlStation.DATA_FILE_NAME);

        if (live.exists())
        {
            throw new SkipException("Not run here: the working directory holds a locomotive database ("
                + live.getAbsolutePath() + "), and this test has to put an empty one in its place");
        }

        File backups = new File(Util.BACKUP_FOLDER);
        boolean hadBackups = backups.isDirectory();
        Set<String> before = names(backups);

        try
        {
            Files.write(live.toPath(), new byte[0]);

            // As at startup: the file is there, and empty
            model.restoreState(MarklinControlStation.DATA_FILE_NAME);

            java.lang.reflect.Field failed = MarklinControlStation.class.getDeclaredField("databaseLoadFailed");

            failed.setAccessible(true);

            assertTrue((Boolean) failed.get(model), "precondition: an empty file read as a first launch");

            // As on exit - with no garbage collection in between to close what the load left open
            model.saveState(false);

            assertEquals(keptCopies(backups, before, new byte[0]), 1, "the empty file was not kept before the save");

            assertTrue(live.length() > 0, "the save after the copy did not write the database - the load left the "
                + "empty file open, and Windows will not replace a file that is open (BPV-C8)");
        }
        finally
        {
            // A handle the load left open holds the file until it is collected; let it go, so the tests after this one
            // start from a clean folder whatever this one found
            System.gc();
            System.runFinalization();

            Files.deleteIfExists(live.toPath());

            for (String name : names(backups))
            {
                if (!before.contains(name)) Files.deleteIfExists(new File(backups, name).toPath());
            }

            if (!hadBackups) Files.deleteIfExists(backups.toPath());

            // The mark is state on the model every test here shares
            model.restoreState(new File(MarklinControlStation.DATA_FILE_NAME + ".absent").getPath());
        }
    }

    /**
     * A file that stops part way through its first object, as an interrupted copy or sync leaves one.
     *
     * A valid stream header, so the reader opens it and fails on the object itself.  Plain garbage
     * fails in the ObjectInputStream constructor instead, and on Windows the file handle opened for
     * that constructor then stays open until garbage collection, which stops this test cleaning up.
     */
    static byte[] truncatedObjectStream()
    {
        return new byte[]{ (byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x73, 0x72 };
    }

    /**
     * The names of the files in a folder, or none if it does not exist.
     */
    static Set<String> names(File folder)
    {
        Set<String> out = new HashSet<>();

        String[] list = folder.list();

        if (list != null) out.addAll(Arrays.asList(list));

        return out;
    }

    /**
     * How many files in the folder, other than those listed before, hold exactly these bytes.
     */
    static int keptCopies(File folder, Set<String> before, byte[] content) throws Exception
    {
        int kept = 0;

        for (String name : names(folder))
        {
            if (before.contains(name)) continue;

            if (Arrays.equals(Files.readAllBytes(new File(folder, name).toPath()), content)) kept++;
        }

        return kept;
    }

    /**
     * Makes the copy of an unreadable file - and only the copy - fail for the next minute: a folder,
     * with a file in it, at every name the copy could be given, which a copy that replaces what is
     * there cannot remove.  Undone by unblock.
     *
     * @param fileName the file whose copy is to fail
     * @param made where what is made is listed as it is made, for unblock
     */
    static void blockTheCopy(String fileName, List<File> made) throws Exception
    {
        // The middle of each second from the one before this to a minute after: a name is to the
        // second, and a time exactly on the minute would be written without its seconds
        long now = System.currentTimeMillis();
        long first = now - now % 1000 - 1000 + 500;

        for (long t = first; t <= now + 60000; t += 1000)
        {
            // Named exactly as the saves name the copy
            File folder = new File(Util.getBackupPath("unreadable"
                + Conversion.convertSecondsToDatetime(t).replace(':', '-').replace(' ', '_') + fileName));

            if (folder.exists()) continue;

            assertTrue(folder.mkdirs(), "the fixture could not make " + folder);
            made.add(folder);

            File inside = new File(folder, "blocker");
            Files.write(inside.toPath(), new byte[]{ 1 });
            made.add(inside);
        }
    }

    /**
     * Removes what blockTheCopy made, innermost first.
     */
    static void unblock(List<File> made) throws Exception
    {
        for (int i = made.size() - 1; i >= 0; i--)
        {
            Files.deleteIfExists(made.get(i).toPath());
        }

        made.clear();
    }
}
