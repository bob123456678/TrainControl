package ui;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A UIState.data that will not read is copied aside before it is written over.
 *
 * MT-038.  The rule comes from the locomotive database, which learned it the hard way: one transient
 * read failure at startup plus an ordinary exit destroyed everything the user had set up, with no undo
 * and no automatic backup.  Writing atomically is no protection at all against this - a complete
 * successful write of nothing is not a partial write.
 *
 * **This test writes to the working directory**, because that is where the application keeps the file
 * and it uses a relative path to find it.  So the real `UIState.data` is copied aside before anything
 * happens and put back afterwards, whatever the outcome - which is exactly why Adam asked for a backup
 * before this was run by hand.  If the restore ever fails, the copy is left in the scratch file named
 * in the failure message rather than deleted.
 */
public class testUiStateIsNotLostWhenUnreadable
{
    private static final String DATA = "UIState.data";

    private static final String BACKUPS = "tc_backup";

    /**
     * What the operator's file held, read before this class does anything at all.
     *
     * In memory rather than in a temp file, and taken in @BeforeClass rather than in the test.  The
     * first version took the copy inside the test method, AFTER the headless check - so the headless
     * run threw SkipException before the copy was taken, and the teardown, seeing no copy and a file
     * on disk, deleted the operator's real one.  It was recovered from a safety copy taken by hand.
     *
     * That is the whole lesson of this test in one paragraph: a guard that runs after the thing it
     * guards is not a guard.
     */
    private static byte[] original;

    /** Whether there was a file at all when this started - the only thing that licenses a delete */
    private static boolean hadOne;

    /** And whether this class is the one that put a file there */
    private static boolean weWroteIt;

    /**
     * The same bytes, on DISK, for the whole time the live file is rubbish.
     *
     * Holding the only copy in a static field is holding it nowhere: this test overwrites the
     * operator's real file and then builds a whole TrainControlUI, and anything that ends the JVM in
     * between - a hang in invokeAndWait, an out-of-memory, the operator stopping the run, or the
     * runner's own orphan reaper, which taskkills leftover test JVMs before every battery - takes the
     * only copy with it and leaves the file corrupt with nothing to restore from.
     *
     * An earlier version of this class destroyed that file once already, by a different route.  Once is
     * a mistake; twice would be a habit.
     */
    private static File onDisk;

    /**
     * Before anything: what is on disk now.
     *
     * Unconditionally, and before the headless check - a run that skips must still put back anything a
     * teardown might otherwise tidy away.
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File live = new File(DATA);

        hadOne = live.exists();

        if (hadOne) original = Files.readAllBytes(live.toPath());
    }

    /**
     * The whole thing: an unreadable file, a session, and a copy in the backup folder afterwards.
     */
    @Test
    public void testAnUnreadableStateFileIsKept() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("this builds the main window, which needs a display");
        }

        File live = new File(DATA);

        // What the backup folder held before, so that only a NEW file counts
        java.util.Set<String> before = listBackups();

        // On disk before a single byte is changed, and not deleted until the restore has been checked
        if (hadOne)
        {
            onDisk = new File(DATA + ".reviewbak");

            Files.write(onDisk.toPath(), original);
        }

        // A file that exists and cannot possibly be read as a serialised state
        weWroteIt = true;

        Files.write(live.toPath(), "this is not a serialised anything".getBytes(StandardCharsets.UTF_8));

        MarklinControlStation model = init(null, true, false, false, true);

        model.stop();

        final TrainControlUI[] window = new TrainControlUI[1];

        SwingUtilities.invokeAndWait(() -> window[0] = new TrainControlUI());

        try
        {
            // Which reads the state file, fails, and remembers that it failed
            window[0].setViewListener(model, new CountDownLatch(1));

            // And this is the write that would otherwise destroy it
            SwingUtilities.invokeAndWait(() -> window[0].saveState(false));

            java.util.Set<String> after = listBackups();

            after.removeAll(before);

            String copied = null;

            for (String name : after)
            {
                if (name.startsWith("unreadable") && name.endsWith(DATA)) copied = name;
            }

            assertNotNull(copied,
                "the unreadable state file was written over without being kept.  A backup folder that "
                + "gained " + after + " is not a backup of it, and there is no undo: "
                + "tc_backup should hold an unreadable<timestamp>UIState.data");

            File copy = new File(BACKUPS, copied);

            assertTrue(copy.length() > 0, "the copy that was kept is empty, which keeps nothing");

            assertEquals(new String(Files.readAllBytes(copy.toPath()), StandardCharsets.UTF_8),
                "this is not a serialised anything",
                "the copy is not what the file held, so it is a copy of the wrong thing");
        }
        finally
        {
            final TrainControlUI closing = window[0];

            SwingUtilities.invokeAndWait(() -> closing.dispose());
        }
    }

    /**
     * Puts the operator's own file back, whatever happened above.
     */
    @AfterClass
    public static void tearDownClass() throws Exception
    {
        File live = new File(DATA);

        if (hadOne)
        {
            Files.write(live.toPath(), original);

            // Checked, not assumed.  This file is the operator's, and "restored" is a claim.
            //
            // The copy on disk is named here rather than described, because a failure message about a
            // file somebody has to go and find is only useful if it says where.
            assertEquals(Files.readAllBytes(live.toPath()), original,
                "UIState.data was NOT put back as it was found.  A copy of the original is on disk at "
                + (onDisk == null ? "(none)" : onDisk.getAbsolutePath())
                + " and has deliberately NOT been deleted - rename it back over UIState.data");

            // Only now, with the restore verified
            if (onDisk != null && onDisk.exists()) onDisk.delete();

            onDisk = null;
        }
        else if (weWroteIt && live.exists())
        {
            // There was none before, and this class is the one that made it
            live.delete();
        }

        // And the staging file, if the save that this test provokes did not get as far as moving it
        // into place.  Harmless where it lies - the next write overwrites it - but it is litter in the
        // project root, and litter beside a data file is the sort of thing somebody later has to work
        // out the meaning of.
        File staging = new File(DATA + ".part");

        if (staging.exists()) staging.delete();

        // And the copy this run put in the backup folder, which holds the test's own rubbish rather
        // than anything of the operator's.  Checked by CONTENT, not by name: deleting from a backup
        // folder on a guess is exactly the wrong instinct.
        File backups = new File(BACKUPS);

        String[] found = backups.list();

        if (found == null) return;

        for (String name : found)
        {
            if (!name.startsWith("unreadable") || !name.endsWith(DATA)) continue;

            File candidate = new File(backups, name);

            byte[] held = Files.readAllBytes(candidate.toPath());

            if (new String(held, StandardCharsets.UTF_8).equals("this is not a serialised anything"))
            {
                candidate.delete();
            }
        }
    }

    private static java.util.Set<String> listBackups()
    {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();

        String[] found = new File(BACKUPS).list();

        if (found != null) names.addAll(java.util.Arrays.asList(found));

        return names;
    }
}
