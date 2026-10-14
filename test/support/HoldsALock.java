package support;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Holds a lock on a file from a JVM of its own until told to stop, then ends without letting go - so the operating
 * system releases the lock as the process ends, which is what TrainControl's exit does for the updater (FR-125).
 *
 * For `core.testTheUpdaterSwapsTheJar`'s one claim that crosses a process boundary.  Nothing of TrainControl's: it
 * uses `java.*` alone, so the JVM it runs in is a plain one.
 *
 * Usage: `java -cp <test classes> support.HoldsALock <lock file> <ready file> <stop file>`.  It writes the ready file
 * once the lock is held, and ends when the stop file appears, or after a minute whatever happens, so a test that dies
 * cannot leave it behind.
 */
public final class HoldsALock
{
    private HoldsALock()
    {
    }

    /**
     * @param args the lock file, the ready file, the stop file
     * @throws Exception from the files
     */
    public static void main(String[] args) throws Exception
    {
        RandomAccessFile file = new RandomAccessFile(args[0], "rw");

        FileChannel channel = file.getChannel();

        if (channel.tryLock() == null) System.exit(2);

        Files.write(new File(args[1]).toPath(), "held".getBytes(StandardCharsets.UTF_8));

        long until = System.currentTimeMillis() + 60000;

        while (!new File(args[2]).exists() && System.currentTimeMillis() < until) Thread.sleep(50);

        // Not released: the process ending is what lets go
        System.exit(0);
    }
}
