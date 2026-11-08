package regression;

import java.io.File;
import java.nio.file.Files;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.util.Util;
import support.SavedData;

/**
 * `support.SavedData` touches the data folder only in a run that has one of its own (`traincontrol.dataDir`): without it
 * the data files are the operator's, and a put-back must not delete or rewrite what the program wrote there.  In a run
 * with one, a new entry at the top of the data folder (a `name-restored` folder) is a change the watch can see.
 *
 * MUTATION: drop the `isolated()` test from `SavedData.files`, and the first claim finds the operator's file deleted.
 *
 * @author Adam
 */
public class testSavedDataLeavesAFolderOutsideARunAlone
{
    @Test(timeOut = 60000)
    public void testPutBackLeavesTheWorkingFolderAlone() throws Exception
    {
        String was = System.getProperty(Util.DATA_DIR_PROPERTY);

        File icons = new File(Util.LOC_ICON_FOLDER).getAbsoluteFile();
        File mine = new File(icons, "operators-own-crop.png");

        boolean made = false;

        try
        {
            System.clearProperty(Util.DATA_DIR_PROPERTY);

            SavedData found = SavedData.take();

            assertFalse(SavedData.isolated(), "precondition: the run has a data folder");

            if (!icons.isDirectory()) made = icons.mkdirs();

            // As the operator's own program would crop an icon while the class ran
            Files.write(mine.toPath(), new byte[] {1, 2, 3});

            found.putBack();

            assertTrue(mine.exists(), "put-back outside a run deleted " + mine + ", a file it did not make");
            assertEquals(SavedData.fingerprint().size(), 0, "outside a run the fingerprint read the working folder");
        }
        finally
        {
            mine.delete();

            if (made) icons.delete();

            if (was != null) System.setProperty(Util.DATA_DIR_PROPERTY, was);
        }
    }

    @Test(timeOut = 60000)
    public void testANewEntryAtTheTopOfTheDataFolderChangesTheFingerprint() throws Exception
    {
        String was = System.getProperty(Util.DATA_DIR_PROPERTY);

        File folder = Files.createTempDirectory("saveddata").toFile();
        File restored = new File(folder, "LocDB.data-restored");

        try
        {
            System.setProperty(Util.DATA_DIR_PROPERTY, folder.getPath());

            java.util.Map<String, String> before = SavedData.fingerprint();

            assertTrue(restored.mkdir(), "precondition: the folder was not made");

            assertNotEquals(SavedData.fingerprint(), before, "a new folder at the top of the data folder went unseen");
        }
        finally
        {
            restored.delete();
            folder.delete();

            if (was != null) System.setProperty(Util.DATA_DIR_PROPERTY, was);
            else System.clearProperty(Util.DATA_DIR_PROPERTY);
        }
    }
}
