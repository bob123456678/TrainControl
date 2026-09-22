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
 * Every count the reference documents quote is the count the store holds (VD14, VD15).
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
 * **ROWS ARE NOT FINDINGS (VD15-T5).**  A finding written up in two documents has a row for each, so
 * the `finding` table holds more rows than findings - 3,726 against 3,369 when this was written - and
 * three documents had quoted the row count as a finding count.  Both are checked here, against the
 * mirror's rows and its distinct refs, so the distinction cannot quietly collapse again.
 *
 * **AND THE REVIEW TOTAL IS NO LONGER A CONSTANT OF THIS TEST (VD15-T2).**  It used to be `208`, which
 * is the number the document says: a guard whose expectation is its subject's own claim checks nothing
 * but its own arithmetic.  What the README now states is four countable things - files deleted per
 * round, reviews per round, the two for-Adam notes that are the difference, and the spelled-out total -
 * and this asks whether they agree with each other.  Which of those files counted as a review is a
 * judgement no test can make; how the stated numbers relate is arithmetic, and that is where it went
 * wrong, twice.
 *
 * MUTATION: change any number in any of the three documents by one and the matching claim names it.
 * For the spelled-out total, the edit is the WORDS - "Two hundred and eight" to "Two hundred and
 * nine" - because every edit to the digits is caught by an earlier claim first: change `143` and `65`
 * together and the file total fails at 211 against 210; change `143` alone and the for-Adam
 * difference fails at 1 against the two notes it names.  The first version of this sentence named
 * exactly that unreachable mutation, which is VD15-T8 two files from where it was closed (VD16-T8).
 *
 * @author Adam
 */
public class testTheRecordsCountTheStore
{
    /**
     * The mirror is the store's Java-readable projection - the same file the citation guard reads.
     *
     * **THIS CLASS NEVER OPENS `triage.db`, and cannot (VD16-T9):** there is no SQLite driver on this
     * project's classpath, which is the reason the mirror exists at all.  What makes reading it the
     * same as reading the store is `triagedb.verify_findings_mirror`, which every `sync` now runs -
     * before that, a store written without a render left this counting rows that were no longer
     * there, and it could not have known.
     */
    private static final String MIRROR = "docs/manual-tests/findings.tsv";

    /**
     * The finding counts quoted in the two reference documents are the store's own, rows and findings.
     *
     * @throws Exception on a failure to read
     */
    @Test
    public void testTheFindingCountIsTheStoresOwn() throws Exception
    {
        int rows = rowsInTheMirror();
        int findings = findingsInTheMirror();

        assertTrue(rows > 3000,
            "only " + rows + " rows were counted in " + MIRROR + ", so the reader of that file has"
            + " stopped matching its rows and every claim below is about nothing");

        assertTrue(findings > 3000 && findings < rows,
            "the mirror holds " + rows + " rows for " + findings + " distinct refs.  This guard exists"
            + " to keep those two apart, and it cannot do that if the reader has stopped telling them"
            + " apart: a row is a finding in one document, and a finding written up twice has two");

        String flowed = flowed("docs/reference/behaviour.md");

        Matcher both = Pattern.compile("([0-9,]+) rows for ([0-9,]+) findings").matcher(flowed);

        assertTrue(both.find(),
            "behaviour.md no longer says how many rows the store holds for how many findings, in the"
            + " form this reads, so both numbers are unchecked again - it should say \""
            + count(rows) + " rows for " + count(findings) + " findings\"");

        assertEquals(number(both.group(1)), rows,
            "behaviour.md quotes " + both.group(1) + " rows; the store holds " + count(rows));

        assertEquals(number(both.group(2)), findings,
            "behaviour.md quotes " + both.group(2) + " findings; the store holds " + count(findings)
            + " distinct refs in " + count(rows) + " rows");

        assertEquals(numberBefore("docs/reference/open-questions.md", "finding rows in the store now"),
            rows,
            "open-questions.md quotes a row count the store does not hold.  The store has "
            + count(rows) + " rows, for " + count(findings) + " findings");
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
     * The review-document arithmetic in `docs/reviews/README.md` agrees with itself.
     *
     * Which files counted as reviews is a judgement no test can make; how the four numbers the README
     * states relate to each other is arithmetic, and that is where it went wrong - "143 on 2026-09-08
     * and 63 more on 2026-09-21" stood beside "two hundred and eight" for a day, and a validation round
     * later re-derived the total from `git` alone and got 207, because the two for-Adam notes the
     * sentence excludes are the difference between the files git counts and the reviews it means.
     *
     * @throws Exception on a failure to read
     */
    @Test
    public void testTheDeletedReviewsAddUp() throws Exception
    {
        String said = flowed("docs/reviews/README.md");

        Matcher halves = Pattern.compile("([0-9]+) on 2026-09-08 and ([0-9]+) on 2026-09-21")
            .matcher(said);

        assertTrue(halves.find(),
            "docs/reviews/README.md no longer says how many review documents were deleted on each date,"
            + " so the total beside it is unchecked");

        int reviews = Integer.parseInt(halves.group(1));
        int second = Integer.parseInt(halves.group(2));

        assertTrue(halves.find(),
            "docs/reviews/README.md states the reviews per round but no longer states the FILES per"
            + " round that git can confirm, which is the half a validation round re-derived and"
            + " disagreed with (VD15-R2).  It should say how many files each round deleted, and what"
            + " the difference from " + reviews + " and " + second + " is");

        int files = Integer.parseInt(halves.group(1));

        assertEquals(Integer.parseInt(halves.group(2)), second,
            "the README gives two different numbers for the 2026-09-21 round - " + second + " reviews"
            + " and " + halves.group(2) + " files - without saying what the difference is");

        Matcher total = Pattern.compile("[*][*]([0-9]+) files[*][*] deleted from the review folders")
            .matcher(said);

        assertTrue(total.find(),
            "docs/reviews/README.md no longer states the file total git counts, so the two halves"
            + " above are added up by nobody.  It should say \"**" + (files + second) + " files**"
            + " deleted from the review folders\"");

        assertEquals(Integer.parseInt(total.group(1)), files + second,
            "the README says " + files + " files were deleted on 2026-09-08 and " + second
            + " on 2026-09-21, which is " + (files + second) + ", and states the total as "
            + total.group(1));

        Matcher notes = Pattern.compile("between ([0-9]+) files and ([0-9]+) reviews").matcher(said);

        assertTrue(notes.find(),
            "docs/reviews/README.md no longer says what the difference between the files deleted and"
            + " the reviews among them is.  Without it the total below cannot be checked against the"
            + " file count at all, which is how one round got 207 and another 208");

        assertEquals(Integer.parseInt(notes.group(1)), files,
            "the README's for-Adam sentence quotes " + notes.group(1) + " files against the "
            + files + " it states above");

        assertEquals(Integer.parseInt(notes.group(2)), reviews,
            "the README's for-Adam sentence quotes " + notes.group(2) + " reviews against the "
            + reviews + " it states above");

        // ONE NEEDLE, NOT TWO: "2026-09-03-questions-for-adam.md" ends with "-for-adam.md" as well,
        // so counting both names counted that file twice and this claim failed at 3 against 2.
        assertEquals(occurrences(said, "-for-adam.md"),
            files - reviews,
            "the README says the difference between " + files + " files and " + reviews
            + " reviews is " + (files - reviews) + " for-Adam notes, and names "
            + occurrences(said, "-for-adam.md") + " of them.  Name each one: an unnamed exclusion is"
            + " how this arithmetic"
            + " came to be re-derived twice");

        // THE SPELLED-OUT TOTAL, DERIVED (VD15-T2).  It used to be compared with a constant `208` in
        // this file, which is the document's own claim written down twice.
        String words = spelled(reviews + second);

        assertTrue(said.toLowerCase().contains(words + " review documents"),
            "the README says " + reviews + " review documents were deleted on 2026-09-08 and " + second
            + " on 2026-09-21, which is " + (reviews + second) + " - so its spelled-out total has to"
            + " read \"" + words + " review documents\"");
    }

    // ---------------------------------------------------------------- the counting

    /**
     * The rows in the mirror: its data rows, less the dead-citation roll at the foot.
     *
     * @return the count
     * @throws Exception on a failure to read
     */
    private static int rowsInTheMirror() throws Exception
    {
        int rows = 0;

        for (String line : read(MIRROR).split("\n"))
        {
            if (!isAFinding(line)) continue;

            rows++;
        }

        return rows;
    }

    /**
     * The findings in the mirror: its distinct refs, which is fewer than its rows.
     *
     * @return the count
     * @throws Exception on a failure to read
     */
    private static int findingsInTheMirror() throws Exception
    {
        java.util.Set<String> refs = new java.util.HashSet<>();

        for (String line : read(MIRROR).split("\n"))
        {
            if (!isAFinding(line)) continue;

            refs.add(line.split(String.valueOf((char) 9), -1)[0].trim());
        }

        return refs.size();
    }

    /**
     * Whether a line of the mirror is a finding row rather than a comment or the dead-citation roll.
     *
     * @param line as read
     * @return true when it counts
     */
    private static boolean isAFinding(String line)
    {
        if (line.trim().isEmpty() || line.startsWith("#")) return false;

        String[] cells = line.split(String.valueOf((char) 9), -1);

        if (cells.length < 2) return false;

        // THE ROLL IS NOT A FINDING.  A dead citation is written as its ref and four dashes.
        for (int at = 1; at < cells.length && at < 5; at++)
        {
            if (!"-".equals(cells[at].trim())) return true;
        }

        return false;
    }

    /**
     * The number immediately before a phrase, wherever it appears in a document.
     *
     * @param path the document
     * @param phrase what follows the number
     * @return the number
     * @throws Exception on a failure to read
     */
    private static int numberBefore(String path, String phrase) throws Exception
    {
        String said = read(path);

        int at = said.indexOf(phrase);

        assertTrue(at > 0, path + " no longer says \"" + phrase + "\", so the number beside it - which"
            + " this guard exists to keep true - is no longer checked.  Restore the phrase or move the"
            + " check");

        // BACKWARDS OVER THE DIGITS, BUT NOT FAR (VD15-T6).  Unbounded, this walked past the phrase's
        // own words to whatever number came earlier in the document and then compared THAT, so a
        // sentence that lost its count would be checked against a number somewhere else entirely.
        int end = at;

        while (end > 0 && end > at - 3 && !Character.isDigit(said.charAt(end - 1))) end--;

        assertTrue(end > 0 && Character.isDigit(said.charAt(end - 1)),
            path + " says \"" + phrase + "\" with no number in front of it, so the count it is there"
            + " to carry has gone.  Write the number immediately before the phrase");

        int start = end;

        while (start > 0 && (Character.isDigit(said.charAt(start - 1)) || said.charAt(start - 1) == ','))
        {
            start--;
        }

        return Integer.parseInt(said.substring(start, end).replace(",", ""));
    }

    /**
     * A number as the documents write it, with thousands separators.
     *
     * @param value the number
     * @return "3,448"
     */
    private static String count(int value)
    {
        return String.format("%,d", value);
    }

    /**
     * A number as the document writes it, without them.
     *
     * @param said "3,448"
     * @return the number
     */
    private static int number(String said)
    {
        return Integer.parseInt(said.replace(",", ""));
    }

    /**
     * A number up to 999 in words, the way the README spells its total.
     *
     * @param value the number
     * @return "two hundred and eight"
     */
    private static String spelled(int value)
    {
        String[] ones = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"};

        String[] tens = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty",
            "ninety"};

        assertTrue(value > 0 && value < 1000, "this spells 1 to 999, and was asked for " + value);

        StringBuilder out = new StringBuilder();

        if (value >= 100)
        {
            out.append(ones[value / 100]).append(" hundred");

            if (value % 100 > 0) out.append(" and ");

            value = value % 100;
        }

        if (value >= 20)
        {
            out.append(tens[value / 10]);

            if (value % 10 > 0) out.append("-").append(ones[value % 10]);
        }
        else if (value > 0)
        {
            out.append(ones[value]);
        }

        return out.toString();
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
     * A document with its line breaks turned into spaces, so a phrase the file wraps still reads.
     *
     * @param path where
     * @return the text, flowed
     * @throws Exception on a failure to read
     */
    private static String flowed(String path) throws Exception
    {
        return read(path).replace(String.valueOf((char) 10), " ").replaceAll("  +", " ");
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
