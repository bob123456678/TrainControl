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
    private static final int SLEEPS_ALLOWED = 400;

    /** The milliseconds those sleeps and the quiet waits over 1,000 ms add up to, as far as they are plain numbers. */
    private static final long SLEEP_MS_ALLOWED = 152182;

    /** The sleeps and quiet waits with no plain number, which the sum cannot see: a swap to a constant lands here. */
    private static final long UNSUMMED_ALLOWED = 36;

    // Built from pieces so this file does not count itself
    private static final Pattern SLEEP = Pattern.compile("Thread" + "\\.sleep\\(|TimeUnit\\.[A-Z]+\\.sleep\\("
        + "|(?<![\\w.])(?:NANO|MICRO|MILLI)?SECONDS\\.sleep\\(|(?<![\\w.])(?:MINUTES|HOURS)\\.sleep\\(");

    /** `sleep(` alone, which is `Thread.sleep` in a file that imports it statically. */
    private static final Pattern BARE_SLEEP = Pattern.compile("(?<![\\w.])sleep\\(");

    /** A wait that expects nothing to happen: `assertFalse(Components.waitFor(condition, millis))`. */
    private static final Pattern QUIET_WAIT = Pattern.compile("assertFalse" + "\\(\\s*(?:Components\\.)?waitFor\\(");

    @Test
    public void testNoNewSleepsInTheTests() throws Exception
    {
        File test = new File("test");

        assertTrue(test.isDirectory(), "cannot find " + test.getAbsolutePath()
            + " - this test reads the source, so it has to run from the project root");

        Map<String, Integer> byFile = new TreeMap<>();
        int found = 0;
        long[] seconds = new long[4];

        for (File file : javaFiles(test, new ArrayList<>()))
        {
            long[] there = sleepsIn(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            int here = (int) there[0];

            for (int i = 1; i < 4; i++) seconds[i] += there[i];

            if (here > 0) byFile.put(file.getPath(), here);

            found += here;
        }

        assertTrue(found <= SLEEPS_ALLOWED, "the tests now have " + found + " fixed sleeps, up from " + SLEEPS_ALLOWED
            + ".  Wait for the thing itself (support.Components.waitFor) instead of for a guessed time.  By file: "
            + byFile);

        assertEquals(found, SLEEPS_ALLOWED, found + " fixed sleeps remain, fewer than the " + SLEEPS_ALLOWED
            + " recorded.  Lower SLEEPS_ALLOWED to " + found + " so the improvement is kept.");

        String covered = seconds[2] + " plain sleeps and quiet waits sum to " + seconds[1] + " ms; " + seconds[3]
            + " have no plain number and are not summed";

        assertTrue(seconds[1] <= SLEEP_MS_ALLOWED, covered + ", up from " + SLEEP_MS_ALLOWED
            + " ms.  A longer fixed wait is a slower suite; wait for the thing itself instead.");

        assertEquals(seconds[1], SLEEP_MS_ALLOWED, covered + ", fewer than the " + SLEEP_MS_ALLOWED
            + " recorded.  Lower SLEEP_MS_ALLOWED to " + seconds[1] + " so the improvement is kept.");

        assertTrue(seconds[3] <= UNSUMMED_ALLOWED, covered + ", up from " + UNSUMMED_ALLOWED
            + ".  A wait whose time is a constant or an expression hides it from the sum; use a plain number or wait for"
            + " the thing itself.");

        assertEquals(seconds[3], UNSUMMED_ALLOWED, covered + ", fewer than the " + UNSUMMED_ALLOWED
            + " recorded.  Lower UNSUMMED_ALLOWED to " + seconds[3] + " so the improvement is kept.");
    }

    /** The summing reads a source as the pin means it: plain numbers added, comments and other arguments not. */
    @Test
    public void testTheSumReadsASource()
    {
        String sleep = "Thread" + ".sleep(";
        String quiet = "assertFalse" + "(Components.waitFor(";

        long[] sum = sleepsIn("  " + sleep + "2500);\n  " + quiet + "() -> x,\n 1500), \"m\");\n  " + quiet
            + "x, 900));\n  // " + sleep + "9000);\n  /* " + sleep + "9000) */\n");

        assertEquals(sum, new long[] {1, 4000, 2, 0}, "a 2,500 ms sleep and a 1,500 ms quiet wait are 4,000; a wait of"
            + " 900 ms and a comment add nothing");

        sum = sleepsIn(sleep + "pause);\n" + sleep + "Math.min(5, n));\n" + quiet + "x, LIMIT));\n");

        assertEquals(sum, new long[] {2, 0, 0, 3}, "a sleep or quiet wait with no plain number is counted and skipped");
    }

    /**
     * What a source's fixed waits add up to: {sleeps, milliseconds summed, calls summed, calls skipped}.  A sleep counts
     * once and adds its argument; a quiet wait, `assertFalse(waitFor(condition, millis))`, adds its millis when they are
     * over 1,000.  Only a plain number can be summed: any other argument (a constant, an expression, a `TimeUnit`
     * sleep) is skipped and counted, never guessed.
     */
    private static long[] sleepsIn(String source)
    {
        String code = withoutComments(source);
        long[] total = new long[4];

        tally(total, code, SLEEP, true);

        if (source.contains("import static java.lang." + "Thread.")) tally(total, code, BARE_SLEEP, true);

        tally(total, code, QUIET_WAIT, false);

        return total;
    }

    private static void tally(long[] total, String code, Pattern pattern, boolean sleep)
    {
        for (Matcher m = pattern.matcher(code); m.find();)
        {
            int depth = 1, start = m.end(), end = start;

            for (; depth > 0 && end < code.length(); end++)
            {
                char c = code.charAt(end);

                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == ',' && depth == 1) start = end + 1;
            }

            String argument = code.substring(start, end - 1).trim();
            boolean plain = argument.matches("\\d+L?") && (!sleep || !m.group().contains("Unit"));
            long ms = plain ? Long.parseLong(argument.replaceAll("L$", "")) : 0;

            if (sleep) total[0]++;

            if (plain && (sleep || ms > 1000))
            {
                total[1] += ms;
                total[2]++;
            }
            else if (!plain) total[3]++;
        }
    }

    /** The code of a source: a line that is all comment is blank, and whatever follows `//` outside a string is cut. */
    private static String withoutComments(String source)
    {
        StringBuilder code = new StringBuilder();

        for (String line : source.split("\n"))
        {
            String trimmed = line.trim();

            if (!trimmed.startsWith("*") && !trimmed.startsWith("/*") && !trimmed.startsWith("//"))
            {
                int comment = line.indexOf("//");

                code.append(comment >= 0 && line.substring(0, comment).indexOf('"') < 0 ? line.substring(0, comment) : line);
            }

            code.append('\n');
        }

        return code.toString();
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
