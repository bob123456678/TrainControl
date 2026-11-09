package support;

import java.io.File;
import java.nio.file.Files;
import static org.testng.Assert.assertTrue;

/** Folders a test makes for itself: copied in, and deleted after. */
public final class Trees
{
    private Trees()
    {
    }

    /** Deletes a file, or a folder and everything in it, as far as it can; null or missing is nothing to do. */
    public static void delete(File file)
    {
        if (file == null) return;

        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children) delete(child);
        }

        file.delete();
    }

    /** Copies a folder into `to`, each file copied marked to be deleted when the run's JVM ends. */
    public static void copyForTheRun(File from, File to) throws Exception
    {
        assertTrue(to.mkdirs() || to.isDirectory(), "could not make " + to);

        File[] children = from.listFiles();

        if (children == null) return;

        for (File one : children)
        {
            File target = new File(to, one.getName());

            if (one.isDirectory()) copyForTheRun(one, target);
            else Files.copy(one.toPath(), target.toPath());

            target.deleteOnExit();
        }
    }
}
