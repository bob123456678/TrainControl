package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Every count the reference documents quote is the count the store holds (VD14).
 *
 * **Why this exists.**  Three validation rounds in a row found a stale number in a reference document,
 * and the third one found the pattern rather than another instance: *"every one of these numbers is
 * counted by hand from a file the same commit is still editing.  Until one of them is COMPUTED, a
 * fourth round will find a fourth stale count."*  Two were wrong inside the very sentence written to
 * apologise for the previous wrong one - the Inbox count three times, the finding count twice - and one
 * "correction" reversed a pair of numbers that had been right.
 *
 * So the numbers are no longer trusted to a reader's care.  Each one below is read out of the document
 * and compared with the thing it describes, and the failure message says what to write instead.
 *
 * **WHAT IT DOES NOT CHECK, deliberately.**  How many review documents were deleted on 2026-09-08 is a
 * judgement about which files were reviews (a consolidation plan and an archive README were not), and no
 * test can settle it - what IS checked is that the two halves the documents quote add up to the total
 * they quote, which is where the arithmetic went wrong.
 *
 * MUTATION: change any of the four numbers in the documents by one and the matching claim names it.
 *
 * @author Adam
 */
public class testTheRecordsCountTheStore
{
    /** The mirror is the store's Java-readable projection - the same file the citation guard reads. */
    private static final String MIRROR = "docs/manual-tests/findings.tsv";

    /**
     * The finding count quoted in the two reference documents is the number of rows in the store.
     *
     * @throws Exception on a failure to read
     */
    @Test
    public void testTheFindingCountIsTheStoresOwn() throws Exception
    {
        int findings = findingsInTheMirror();

        assertTrue(findings > 3000,
            "only " + findings + " findings were counted in " + MIRROR + ", so the reader of that file"
            + " has stopped matching its rows and every claim below is about nothing");

        assertEquals(numberBefore("docs/reference/behaviour.md", "of them are in `docs/manual-tests/triage.db`"),
            findings,
            "behaviour.md quotes a finding count the store does not hold.  The store has " + findings
            + " - write that, or better, say where to count it");

        assertEquals(numberBefore("docs/reference/open-questions.md", "findings in the store now"),
            findings,
            "open-questions.md quotes a finding count the store does not hold.  The store has "
            + findings);
    }

    /**
     * The Inbox count quoted in `open-questions.md` is the number of entries the Inbox holds.
     *
     * @throws Exception on a failure to read
     */
    @Test
    public void testTheInboxCountIsTheInboxsOwn() throws Exception
    {
        String issues = read("docs/manual-tests/issues.md");

        int inbox = issues.indexOf("## Inbox");

        int after = issues.indexOf("## What has been picked up");

        assertTrue(inbox > 0 && after > inbox, "issues.md no longer has an Inbox section to count");

        String section = issues.substring(inbox, after);

        int obs = occurrences(section, "\n### OB-");
        int frs = occurrences(section, "\n### FR-");

        assertTrue(obs > 0 && frs > 0, "the Inbox holds no entries at all, so nothing below is a check");

        String said = read("docs/reference/open-questions.md");

        Matcher held = Pattern.compile("it holds (\\d+) entries - (\\d+) OB and (\\d+) FR").matcher(said);

        assertTrue(held.find(),
            "open-questions.md no longer states the Inbox count in the form this reads, so the number"
            + " it does state is unchecked again - it should say \"it holds " + (obs + frs)
            + " entries - " + obs + " OB and " + frs + " FR\"");

        assertEquals(Integer.parseInt(held.group(2)), obs,
            "open-questions.md quotes " + held.group(2) + " OB entries; the Inbox holds " + obs);

        assertEquals(Integer.parseInt(held.group(3)), frs,
            "open-questions.md quotes " + held.group(3) + " FR entries; the Inbox holds " + frs);

        assertEquals(Integer.parseInt(held.group(1)), obs + frs,
            "open-questions.md quotes a total of " + held.group(1) + " Inbox entries against its own "
            + obs + " OB and " + frs + " FR, which add to " + (obs + frs));
    }

    /**
     * The review-document arithmetic in `docs/reviews/README.md` adds up.
     *
     * Which files counted as reviews is a judgement no test can make; that the halves add to the total
     * is arithmetic, and that is where it went wrong - "143 on 2026-09-08 and 63 more on 2026-09-21"
     * stood beside "two hundred and eight" for a day.
     *
     * @throws Exception on a failure to read
     */
    @Test
    public void testTheDeletedReviewsAddUp() throws Exception
    {
        String said = read("docs/reviews/README.md");

        Matcher halves = Pattern.compile("(\\d+) on 2026-09-08 and (\\d+) on 2026-09-21").matcher(said);

        assertTrue(halves.find(),
            "docs/reviews/README.md no longer says how many review documents were deleted on each date,"
            + " so the total beside it is unchecked");

        int first = Integer.parseInt(halves.group(1));
        int second = Integer.parseInt(halves.group(2));

        // THE PHRASE AS THE README WRAPS IT: the words are split across a line break, so the
        // check reads the normalised text with its newlines turned into spaces.
        String flowed = said.replace(String.valueOf((char) 10), " ").replaceAll("  +", " ");

        assertTrue(flowed.contains("Two hundred and eight review documents"),
            "the README's spelled-out total has changed; it has to agree with " + first + " + " + second
            + " = " + (first + second));

        assertEquals(first + second, 208,
            "the README says " + first + " review documents were deleted on 2026-09-08 and " + second
            + " on 2026-09-21, which is " + (first + second) + " - and states the total as two hundred"
            + " and eight");
    }

    // ---------------------------------------------------------------- the counting

    /**
     * The findings in the mirror: its data rows, less the dead-citation roll at the foot.
     *
     * @return the count
     * @throws Exception on a failure to read
     */
    private static int findingsInTheMirror() throws Exception
    {
        int findings = 0;

        for (String line : read(MIRROR).split("\n"))
        {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;

            String[] cells = line.split("\t", -1);

            if (cells.length < 2) continue;

            // THE ROLL IS NOT A FINDING.  A dead citation is written as its ref and four dashes.
            boolean dead = true;

            for (int at = 1; at < cells.length && at < 5; at++)
            {
                if (!"-".equals(cells[at].trim())) dead = false;
            }

            if (!dead) findings++;
        }

        return findings;
    }

    /**
     * The number immediately before a phrase, wherever it appears in a document.
     *
     * @param path the document
     * @param phrase what follows the number
     * @return the number, or -1 when the phrase is not there
     * @throws Exception on a failure to read
     */
    private static int numberBefore(String path, String phrase) throws Exception
    {
        String said = read(path);

        int at = said.indexOf(phrase);

        assertTrue(at > 0, path + " no longer says \"" + phrase + "\", so the number beside it - which"
            + " this guard exists to keep true - is no longer checked.  Restore the phrase or move the"
            + " check");

        // Backwards over the digits and any thousands separators.
        int end = at;

        while (end > 0 && !Character.isDigit(said.charAt(end - 1))) end--;

        int start = end;

        while (start > 0 && (Character.isDigit(said.charAt(start - 1)) || said.charAt(start - 1) == ','))
        {
            start--;
        }

        assertTrue(end > start, path + " has no number before \"" + phrase + "\"");

        return Integer.parseInt(said.substring(start, end).replace(",", ""));
    }

    /**
     * How many times one string occurs in another.
     *
     * @param text the whole
     * @param needle what to count
     * @return the count
     */
    private static int occurrences(String text, String needle)
    {
        int count = 0;

        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) count++;

        return count;
    }

    /**
     * A file, whole.
     *
     * @param path where
     * @return its text, with the line endings normalised so a regex need not care
     * @throws Exception on a failure to read
     */
    private static String read(String path) throws Exception
    {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
