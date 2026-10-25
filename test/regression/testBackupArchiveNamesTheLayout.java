package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.util.Util;

/**
 * A backup archive keeps each folder under the name it came from (MT-159; Adam: "we are missing the folder name of the
 * active layout (i.e., zip file contains 'config' instead of 'cs2_sample_layout')").
 *
 * The fix was already in the build he tested, so either it does not work or he opened an archive made before it.  This
 * settles the half that can be settled without him: whether a key with a folder in it produces a nested archive.
 * `Util.zipInto` takes a map of entry name to file and the backup passes `<layout folder name>/config`, so a flattened
 * key, or a recursion that dropped the prefix, would give a bare `config` whatever the caller asked for.  The test
 * builds its own throwaway layout folder.  The other half, which archive he opened, is his to answer.
 */
public class testBackupArchiveNamesTheLayout
{
    /**
     * Show Files, after a backup, opens the backup folder itself (OB-311).  On Windows it asked Explorer to select the
     * archive (`explorer /select,`), and an Explorer that cannot take the path (spaces, a folder it will not resolve)
     * opens its default place instead, Documents, with nothing said.  The folder is opened as a folder now.
     *
     * MUTATION: ask Explorer to select the file again, and this fails on Windows.
     */
    @Test
    public void testShowFilesOpensTheBackupFolder() throws Exception
    {
        File folder = java.nio.file.Files.createTempDirectory("tc_backup OB-311 ").toFile();

        File archive = new File(folder, "backup2026-10-03_12-00-00-TrainControl.zip");

        java.util.List<String> asked = new java.util.ArrayList<>();

        try
        {
            assertTrue(archive.createNewFile(), "precondition: no archive to show");

            org.traincontrol.util.Util.fileManagerForTests(asked::add);

            org.traincontrol.util.Util.showInFileManager(archive, folder.getAbsolutePath());

            assertEquals(asked, java.util.Collections.singletonList("open " + folder.getAbsolutePath()), "Show Files did"
                + " not open the backup folder itself (OB-311)");
        }
        finally
        {
            org.traincontrol.util.Util.fileManagerForTests(null);

            archive.delete();
            folder.delete();
        }
    }

    /** A folder handed over under a prefixed name arrives under that prefix, all the way down. */
    @Test
    public void testAPrefixedFolderKeepsItsPrefixThroughoutTheArchive() throws Exception
    {
        File work = temp();

        // A layout folder shaped like the real one: config, with files at two depths under it.
        File layout = new File(work, "test_layout");
        File config = new File(layout, "config");
        File autonomy = new File(config, "autonomy");

        assertTrue(autonomy.mkdirs(), "could not build the fixture");

        write(new File(config, "gleisbild.cs2"), "index");
        write(new File(autonomy, "setup.json"), "{}");

        // What the backup does: the whole config folder, under the LAYOUT's own name.
        Map<String, File> state = new LinkedHashMap<>();

        state.put("UIState.data", write(new File(work, "UIState.data"), "ui"));
        state.put(layout.getName() + "/config", config);

        File archive = new File(work, "backup.zip");

        List<String> failed = Util.zipInto(archive, state);

        assertTrue(failed.isEmpty(), "the archive reported failures: " + failed);

        List<String> entries = entriesOf(archive);

        assertTrue(entries.contains("test_layout/config/gleisbild.cs2"),
            "the layout's own name is not in the entry path, so two backups of two layouts are "
            + "indistinguishable once they are off the machine (MT-159).  Got: " + entries);

        assertTrue(entries.contains("test_layout/config/autonomy/setup.json"),
            "the prefix was dropped somewhere below the first level, so the deeper files - which are "
            + "the autonomy setup - are filed under a name that does not say which layout they "
            + "describe.  Got: " + entries);

        for (String entry : entries)
        {
            assertFalse(entry.startsWith("config/"),
                "an entry is still filed under a bare 'config', which is the symptom reported.  Got: "
                + entries);
        }

        // And the things that live beside the application rather than inside the layout stay put.
        assertTrue(entries.contains("UIState.data"),
            "a top-level entry was moved under the layout folder.  Got: " + entries);
    }

    private static List<String> entriesOf(File archive) throws Exception
    {
        List<String> out = new ArrayList<>();

        try (ZipFile zip = new ZipFile(archive))
        {
            java.util.Enumeration<? extends ZipEntry> all = zip.entries();

            while (all.hasMoreElements()) out.add(all.nextElement().getName());
        }

        return out;
    }

    private static File write(File file, String contents) throws Exception
    {
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));

        return file;
    }

    private static File temp() throws Exception
    {
        File temp = File.createTempFile("tc-backup", "");

        assertTrue(temp.delete(), "making room for a directory of the same name");
        assertTrue(temp.mkdirs(), "could not make the working directory");

        temp.deleteOnExit();

        return temp;
    }
}
