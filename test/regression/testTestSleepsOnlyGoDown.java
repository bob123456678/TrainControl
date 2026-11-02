package regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The tests' fixed sleeps can only go down.
 *
 * A sleep is a guess at how long something takes: too short on a busy machine and the claim reads too early and
 * fails, too long and every run pays for it.  `support.Components.waitFor` waits for the thing itself.  This pins
 * the count of `Thread.sleep` and `TimeUnit` sleeps in test code, outside comments, also when spelled through a static
 * import; one replaced by a wait is banked by lowering the pin.  A helper that sleeps counts once, however often it is
 * called: a new fixed wait goes into a claim, not into a helper.
 */
public class testTestSleepsOnlyGoDown
{
    private static final int SLEEPS_ALLOWED = 455;

    // Built from pieces so this file does not count itself
    private static final Pattern SLEEP = Pattern.compile("Thread" + "\\.sleep\\(|TimeUnit\\.[A-Z]+\\.sleep\\("
        + "|(?<![\\w.])(?:NANO|MICRO|MILLI)?SECONDS\\.sleep\\(|(?<![\\w.])(?:MINUTES|HOURS)\\.sleep\\(");

    /** `sleep(` alone, which is `Thread.sleep` in a file that imports it statically. */
    private static final Pattern BARE_SLEEP = Pattern.compile("(?<![\\w.])sleep\\(");

    @Test
    public void testNoNewSleepsInTheTests() throws Exception
    {
        File test = new File("test");

        assertTrue(test.isDirectory(), "cannot find " + test.getAbsolutePath()
            + " - this test reads the source, so it has to run from the project root");

        Map<String, Integer> byFile = new TreeMap<>();
        int found = 0;

        for (File file : javaFiles(test, new ArrayList<>()))
        {
            int here = sleepsIn(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

            if (here > 0) byFile.put(file.getPath(), here);

            found += here;
        }

        assertTrue(found <= SLEEPS_ALLOWED, "the tests now have " + found + " fixed sleeps, up from " + SLEEPS_ALLOWED
            + ".  Wait for the thing itself (support.Components.waitFor) instead of for a guessed time.  By file: "
            + byFile);

        assertEquals(found, SLEEPS_ALLOWED, found + " fixed sleeps remain, fewer than the " + SLEEPS_ALLOWED
            + " recorded.  Lower SLEEPS_ALLOWED to " + found + " so the improvement is kept.");
    }

    /** Sleeps on lines of code: a line that is all comment is skipped, as is whatever follows `//` outside a string. */
    private static int sleepsIn(String source)
    {
        int found = 0;
        boolean bare = source.contains("import static java.lang." + "Thread.");

        for (String line : source.split("\n"))
        {
            String trimmed = line.trim();

            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) continue;

            int comment = line.indexOf("//");
            String code = comment >= 0 && line.substring(0, comment).indexOf('"') < 0 ? line.substring(0, comment) : line;

            for (Matcher m = SLEEP.matcher(code); m.find();) found++;

            if (bare && !trimmed.startsWith("import")) for (Matcher m = BARE_SLEEP.matcher(code); m.find();) found++;
        }

        return found;
    }

    private static List<File> javaFiles(File from, List<File> into) throws IOException
    {
        File[] children = from.listFiles();

        if (children == null) return into;

        for (File child : children)
        {
            if (child.isDirectory()) javaFiles(child, into);
            else if (child.getName().endsWith(".java")) into.add(child);
        }

        return into;
    }
}
