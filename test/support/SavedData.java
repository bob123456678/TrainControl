package support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.Util;

/**
 * The run's saved data (the two data files, the backup folder and the locomotive icon folder under `Util.dataPath`) as
 * it was when `take` ran, to be put back by a test that saves.  The classes after it in a battery lane read the same
 * data folder, so a class that leaves a changed `LocDB.data` behind hands its state to them.
 *
 *     private static final SavedData FOUND = SavedData.take();
 *
 *     @AfterClass(alwaysRun = true, dependsOnMethods = "tearDownClass")
 *     public static void putTheDataBack() throws Exception { FOUND.putBack(); }
 *
 * `DataFolderWatch` reports a class that leaves this data changed; `fingerprint` is its comparison.
 *
 * @author Adam
 */
public final class SavedData
{
    /** What is saved, by name under the data folder: the two data files, then two folders. */
    private static final String[] ROOTS =
    {
        MarklinControlStation.DATA_FILE_NAME, TrainControlUI.DATA_FILE_NAME, Util.BACKUP_FOLDER, Util.LOC_ICON_FOLDER
    };

    /** Each file's bytes, by path. */
    private final Map<Path, byte[]> found = new TreeMap<>();

    private SavedData()
    {
    }

    /** Remembers every saved file now; usable in a static initializer, so a class takes it before any set-up runs. */
    public static SavedData take()
    {
        SavedData taken = new SavedData();

        try
        {
            for (Path file : files())
            {
                taken.found.put(file, Files.readAllBytes(file));
            }
        }
        catch (IOException e)
        {
            throw new java.io.UncheckedIOException(e);
        }

        return taken;
    }

    /** Writes each remembered file back as found, and deletes the saved files made since. */
    public void putBack() throws IOException
    {
        for (Path file : files())
        {
            if (!found.containsKey(file)) Files.deleteIfExists(file);
        }

        for (Map.Entry<Path, byte[]> entry : found.entrySet())
        {
            Path file = entry.getKey();

            if (!Files.exists(file) || !java.util.Arrays.equals(Files.readAllBytes(file), entry.getValue()))
            {
                if (file.getParent() != null) Files.createDirectories(file.getParent());

                Files.write(file, entry.getValue());
            }
        }
    }

    /** Each saved file's name and MD5 now, in name order. */
    public static Map<String, String> fingerprint() throws IOException
    {
        Map<String, String> prints = new TreeMap<>();

        try
        {
            for (Path file : files())
            {
                byte[] hash = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file));

                StringBuilder hex = new StringBuilder();

                for (byte b : hash) hex.append(String.format("%02x", b));

                prints.put(file.toString(), hex.toString());
            }
        }
        catch (java.security.NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }

        return prints;
    }

    /** Every saved file that exists now, in path order. */
    private static List<Path> files() throws IOException
    {
        List<Path> all = new ArrayList<>();

        for (String root : ROOTS)
        {
            Path at = Paths.get(Util.dataPath(root));

            if (Files.isRegularFile(at)) all.add(at);
            else if (Files.isDirectory(at))
            {
                try (Stream<Path> walk = Files.walk(at))
                {
                    walk.filter(Files::isRegularFile).forEach(all::add);
                }
            }
        }

        return all;
    }
}
