package support;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.traincontrol.util.JarUpdater;
import org.traincontrol.util.SelfUpdate;

/**
 * A plain JVM that does what the two ends of an update do with the started marker, for
 * `regression.testTheUpdateIsWiredEndToEnd` (FR-125).  It is not TrainControl: it calls `SelfUpdate`'s one method and
 * its real starter, and nothing else of the application.
 *
 * Three modes, each ending on its own within a minute so a test that dies cannot leave it behind:
 *
 * - `marker`: `SelfUpdate.reportStarted()`, as TrainControl does once its window is up - it writes the file the
 *   updater named in `JarUpdater.STARTED_ENVIRONMENT`, if it named one - then waits a moment, so it is seen running.
 * - `probe <file> <ms>`: writes what it found in `STARTED_ENVIRONMENT` to the file, then waits `ms`.
 * - `relay <file>`: starts a `probe` through `SelfUpdate.startDetached`, the starter TrainControl starts its updater
 *   with, and waits for it - so the probe says what an updater started from inside an updated TrainControl inherits.
 */
public final class ReportsStarted
{
    private ReportsStarted()
    {
    }

    /**
     * @param args the mode and its arguments
     * @throws Exception from the files or the process
     */
    public static void main(String[] args) throws Exception
    {
        if ("marker".equals(args[0]))
        {
            SelfUpdate.reportStarted();

            Thread.sleep(1500);
        }
        else if ("probe".equals(args[0]))
        {
            Files.write(new File(args[1]).toPath(), ("STARTED_ENV=" + System.getenv(JarUpdater.STARTED_ENVIRONMENT))
                .getBytes(StandardCharsets.UTF_8));

            Thread.sleep(Math.min(60000, Long.parseLong(args[2])));
        }
        else if ("relay".equals(args[0]))
        {
            String java = new File(new File(System.getProperty("java.home"), "bin"),
                File.separatorChar == '\\' ? "java.exe" : "java").getPath();

            SelfUpdate.Updater probe = SelfUpdate.startDetached(Arrays.asList(java, "-cp",
                System.getProperty("java.class.path"), ReportsStarted.class.getName(), "probe", args[1], "0"),
                new File(args[1]).getAbsoluteFile().getParentFile());

            long until = System.currentTimeMillis() + 30000;

            while (probe.isAlive() && System.currentTimeMillis() < until) Thread.sleep(50);
        }

        System.exit(0);
    }
}
