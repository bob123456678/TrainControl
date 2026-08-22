package core;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.util.Util;

/**
 * Tests Util.writeAtomically - the guarantee that a failed write cannot destroy what was there before.
 *
 * Three files carry the operator's accumulated work and are written exactly once per session, in the
 * window-close handler: the locomotive database, the UI state, and autonomy.json.  All three used to be
 * opened for writing directly, which empties the target immediately - so from the first byte until the
 * last was flushed the only copy of the data was incomplete.  Dying in that window did not leave the
 * database stale, it left it destroyed, and silently: an unreadable database reads as a first launch,
 * and the next Central Station sync repopulates the locomotive list, so the customizations look
 * mislaid rather than lost.
 *
 * A real crash cannot be staged in a unit test, so these provoke the same window the other way, with a
 * write that throws part way through.  The observable property is identical and is the one that
 * matters: after a write that did not complete, the previous contents are still there.
 *
 * To see these fail, replace writeAtomically's body with a direct write to the target - which is what
 * the three writers did before.
 */
public class testAtomicWrite
{
    private static final String BEFORE = "the previous contents, which must survive";

    private Path dir;
    private File target;

    @BeforeMethod
    public void setUp() throws Exception
    {
        dir = Files.createTempDirectory("tc-atomic");
        target = new File(dir.toFile(), "state.dat");

        Files.write(target.toPath(), BEFORE.getBytes(StandardCharsets.UTF_8));
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        Files.walk(dir)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    private String contents() throws Exception
    {
        return new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The ordinary case: a write that completes replaces the file.
     *
     * First, so that a helper which never wrote anything at all could not pass the failure tests below
     * by doing nothing.
     */
    @Test
    public void testACompletedWriteReplacesTheFile() throws Exception
    {
        Util.writeAtomically(target, out -> out.write("new contents".getBytes(StandardCharsets.UTF_8)));

        assertEquals(contents(), "new contents", "a completed write must land");
    }

    /**
     * A write that throws part way leaves the previous file untouched.
     */
    @Test
    public void testAFailedWriteLeavesThePreviousFileIntact() throws Exception
    {
        try
        {
            Util.writeAtomically(target, out ->
            {
                // Enough to have reached the disk had the target been opened directly
                out.write(new byte[64 * 1024]);
                out.flush();

                throw new IOException("interrupted part way, as a dying process would be");
            });

            fail("the failure must be reported to the caller, not swallowed");
        }
        catch (IOException expected)
        {
            // The point of the test is what is on disk afterwards
        }

        assertEquals(contents(), BEFORE,
            "a write that did not complete destroyed the previous contents - which for the locomotive "
                + "database is the operator's entire accumulated customization, lost silently");
    }

    /**
     * The same guarantee for the shape the locomotive database and the UI state actually use: an object
     * stream over the file, with serialization failing mid-stream.
     */
    @Test
    public void testAFailedObjectSerializationLeavesThePreviousFileIntact() throws Exception
    {
        List<Object> payload = Arrays.asList("a harmless first element", new Unserializable());

        try
        {
            Util.writeAtomically(target, out ->
            {
                try (ObjectOutputStream objects = new ObjectOutputStream(out))
                {
                    objects.writeObject(payload);
                }
            });

            fail("serialization of an unserializable member must fail");
        }
        catch (IOException | RuntimeException expected)
        {
            // As above - the assertion that matters is on disk
        }

        assertEquals(contents(), BEFORE,
            "a serialization failure part way through the list destroyed the previous database");
    }

    /**
     * The staging file must not be left behind, or it accumulates beside every file this protects.
     */
    @Test
    public void testAFailedWriteLeavesNoStagingFileBehind() throws Exception
    {
        try
        {
            Util.writeAtomically(target, out ->
            {
                throw new IOException("failed immediately");
            });

            fail("the failure must be reported");
        }
        catch (IOException expected)
        {
        }

        File staging = new File(target.getAbsolutePath() + Util.PARTIAL_DOWNLOAD_SUFFIX);

        assertFalse(staging.exists(), "the staging file must be cleaned up: " + staging);
    }

    /**
     * Writing a file that does not exist yet is the first-run case, and must simply create it.
     */
    @Test
    public void testWritingANewFileWorks() throws Exception
    {
        File fresh = new File(dir.toFile(), "brand-new.dat");

        Util.writeAtomically(fresh, out -> out.write("created".getBytes(StandardCharsets.UTF_8)));

        assertEquals(new String(Files.readAllBytes(fresh.toPath()), StandardCharsets.UTF_8), "created");
    }

    /**
     * Not serializable, so writeObject throws once the stream reaches it - after the header and the
     * first element are already in the stream, which is the mid-write case.
     */
    private static final class Unserializable implements Serializable
    {
        @SuppressWarnings("unused")
        private final Object trouble = new Object();
    }
}
