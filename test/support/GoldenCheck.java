package support;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.testng.Assert.fail;

/**
 * Compares a dump with its golden file (and the golden's listed changes, support.GoldenChanges); a mismatch writes what
 * this run saw to `build/<name>` and fails naming the first line that differs.  A missing golden fails the same way,
 * which is how a golden is made.
 *
 * @author Adam
 */
public final class GoldenCheck
{
    private GoldenCheck()
    {
    }

    public static void check(String actual, String goldenPath, String changesPath, String actualName) throws Exception
    {
        File golden = new File(goldenPath);
        String expected = GoldenChanges.apply(!golden.isFile() ? ""
            : new String(Files.readAllBytes(golden.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n"), changesPath);

        if (actual.equals(expected))
        {
            return;
        }

        File out = new File("build/" + actualName);
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), actual.getBytes(StandardCharsets.UTF_8));

        String[] now = actual.split("\n", -1);
        String[] was = expected.split("\n", -1);
        int line = firstDifference(now, was);

        fail("differs from " + goldenPath + " with " + changesPath + " at line " + (line + 1) + " (written to " + out
            + "):\n  golden: " + (line < was.length ? was[line] : "(end)") + "\n  now:    "
            + (line < now.length ? now[line] : "(end)"));
    }

    /** The index of the first line the two differ at; the shorter one's length if one is the start of the other. */
    private static int firstDifference(String[] now, String[] was)
    {
        int shorter = Math.min(now.length, was.length);

        for (int i = 0; i < shorter; i++)
        {
            if (!now[i].equals(was[i]))
            {
                return i;
            }
        }

        return shorter;
    }
}
