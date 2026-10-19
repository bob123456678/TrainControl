package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
 * MUTATION: change by one any number a claim below reads, and the matching claim names it (VD17-T6: this said any number
 * in the three documents, and the README's split of the second round was read by none until then).
 * For the spelled-out total, the edit is the WORDS - "Two hundred and eight" to "Two hundred and
 * nine" - because every edit to the digits is caught by an earlier claim first: change `143` and `65`
 * together and the file total fails at 211 against 210; change `143` alone and the for-Adam
 * difference fails at 1 against the two notes it names.  The first version of this sentence named
 * exactly that unreachable mutation, which is VD15-T8 two files from where it was closed (VD16-T8).
 *
 * **THE STORE ALSO HANDS OUT THE NEXT ROUND (FR-136).**  With the review documents deleted the folder cannot say which
 * round is next, so `catalog-findings.py --next PREFIX` reads it from the store, `--check-unused REF` refuses one
 * already there, and no mode of the tool can rebuild the catalogue from the documents that remain.  The claims that
 * run it need `python` on the path, as the tool does.
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
            + " and " + halves.group(2) + " files - and for that round they must be equal: no for-Adam note was"
            + " deleted that day (VD18-T9)");

        // AND THE SECOND ROUND'S TWO PLACES (VD17-T6): "(44 in `docs/reviews/` itself and 21 in three dated folders
        // beside it)" was read by nothing, so 44 could become 43 with every claim here green
        Matcher places = Pattern.compile("([0-9]+) in .docs/reviews/. itself and ([0-9]+) in").matcher(said);

        assertTrue(places.find(), "docs/reviews/README.md no longer says where the 2026-09-21 round's files were, so the"
            + " split beside the total is unchecked");

        assertEquals(Integer.parseInt(places.group(1)) + Integer.parseInt(places.group(2)), second, "the README puts "
            + places.group(1) + " of the 2026-09-21 round's files in docs/reviews/ and " + places.group(2) + " in the"
            + " dated folders, which is not the " + second + " it says that round deleted");

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

    // ---------------------------------------------------------------- the next round comes from the store (FR-136)

    /**
     * Loads `catalog-findings.py` and `triagedb` as modules, for the claims that call its functions.
     *
     * The stand-in refs in the scripts are written with a tilde (`ZZ~A1`) and turned into dashes by `fix`, because
     * `testEveryCitationResolves` reads every finding-shaped id in this file as a citation, and these are not findings.
     */
    private static final String LOAD = String.join("\n",
        "import importlib.util, io, os, sys, tempfile",
        "spec = importlib.util.spec_from_file_location('cf', 'docs/tools/catalog-findings.py')",
        "cf = importlib.util.module_from_spec(spec)",
        "spec.loader.exec_module(cf)",
        "sys.path.insert(0, 'docs/manual-tests')",
        "import triagedb",
        "def fix(*refs):",
        "    return [r.replace('~', '-') for r in refs]",
        "");

    /**
     * What a python process printed and returned.
     */
    private static final class Ran
    {
        final int exit;
        final String out;
        final String err;

        /**
         * @param exit the exit code
         * @param out standard output
         * @param err standard error
         */
        Ran(int exit, String out, String err)
        {
            this.exit = exit;
            this.out = out;
            this.err = err;
        }
    }

    /**
     * Runs python at the repository root, as `python args...`.
     *
     * @param args the script and its arguments
     * @return what it did
     * @throws Exception if python cannot be started or does not finish
     */
    private static Ran python(String... args) throws Exception
    {
        File out = File.createTempFile("catalog-out", ".txt");
        File err = File.createTempFile("catalog-err", ".txt");

        try
        {
            List<String> command = new ArrayList<>();

            command.add("python");
            command.addAll(Arrays.asList(args));

            ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(out).redirectError(err);

            builder.environment().put("PYTHONIOENCODING", "utf-8");

            Process process = builder.start();

            process.getOutputStream().close();

            if (!process.waitFor(180, TimeUnit.SECONDS))
            {
                process.destroyForcibly();

                throw new AssertionError("python did not finish in three minutes: " + command);
            }

            return new Ran(process.exitValue(),
                new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n"),
                new String(Files.readAllBytes(err.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
        finally
        {
            out.delete();
            err.delete();
        }
    }

    /**
     * The tool, as the command line runs it.
     *
     * @param args its arguments
     * @return what it did
     * @throws Exception if python cannot be run
     */
    private static Ran tool(String... args) throws Exception
    {
        List<String> all = new ArrayList<>();

        all.add("docs/tools/catalog-findings.py");
        all.addAll(Arrays.asList(args));

        return python(all.toArray(new String[0]));
    }

    /**
     * A python script that loads the tool, run to completion.
     *
     * @param body its statements after the loading ones
     * @return what it printed
     * @throws Exception if it fails to run, or python exits non-zero
     */
    private static String script(String... body) throws Exception
    {
        File file = File.createTempFile("catalog-claim", ".py");

        try
        {
            Files.write(file.toPath(), (LOAD + String.join("\n", body) + "\n").getBytes(StandardCharsets.UTF_8));

            Ran ran = python(file.getPath());

            assertEquals(ran.exit, 0, "the script failed:\n" + ran.err + "\nafter printing:\n" + ran.out);

            return ran.out;
        }
        finally
        {
            file.delete();
        }
    }

    /**
     * The refs in the mirror whose prefix is this one, with the round each belongs to (no number is round 1).
     *
     * @param prefix e.g. RSA
     * @return ref to round
     * @throws Exception on a failure to read
     */
    private static java.util.Map<String, Integer> roundsInTheMirror(String prefix) throws Exception
    {
        java.util.Map<String, Integer> out = new java.util.TreeMap<>();

        Pattern mine = Pattern.compile("^" + prefix + "([0-9]*)-");

        for (String line : read(MIRROR).split("\n"))
        {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;

            String ref = line.split(String.valueOf((char) 9), -1)[0].trim();

            Matcher m = mine.matcher(ref);

            if (m.find()) out.put(ref, m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1)));
        }

        return out;
    }

    /**
     * The highest round the mirror holds for a prefix.
     *
     * @param prefix e.g. RSA
     * @return the number
     * @throws Exception on a failure to read
     */
    private static int highestRound(String prefix) throws Exception
    {
        int highest = 0;

        for (int round : roundsInTheMirror(prefix).values()) highest = Math.max(highest, round);

        assertTrue(highest > 0, "the mirror holds no " + prefix + " refs, so nothing below is a check of the prefix");

        return highest;
    }

    /**
     * `--next` prints one past the highest round the store holds, for the prefixes that run to many rounds and for
     * ones whose first round has no number.
     *
     * The expected value is counted here from the mirror, which `--next` cross-checks against the database before it
     * answers, so a tool that read a folder (empty by design) or sorted the numbers as text would print another.
     *
     * @throws Exception on a failure to run the tool
     */
    @Test
    public void testTheNextRoundIsOnePastTheHighestTheStoreHolds() throws Exception
    {
        assertTrue(highestRound("RSA") >= 75, "RSA is at round 75 or later, and only the store knows that");

        for (String prefix : new String[] {"RSA", "VD", "TDY", "RLV"})
        {
            int highest = highestRound(prefix);

            Ran ran = tool("--next", prefix);

            assertEquals(ran.exit, 0, "--next " + prefix + " was refused: " + ran.err);

            assertEquals(ran.out.trim(), prefix + (highest + 1),
                "the store's highest " + prefix + " round is " + highest + ", so the next is " + prefix
                + (highest + 1));
        }
    }

    /**
     * Round numbers are counted, not sorted as text, and a prefix is not a prefix of another prefix.
     *
     * @throws Exception on a failure to run python
     */
    @Test
    public void testRoundNumbersAreCountedAndPrefixesAreWholeWords() throws Exception
    {
        String said = script(
            "refs = fix('RSA~A1', 'RSA2~A1', 'RSA9~B1', 'RSA10~C1', 'RSAX3~A1', 'VD11~D1')",
            "print(cf.next_round(refs, 'RSA'))",
            "print(cf.next_round(refs, 'VD'))",
            "print(cf.next_round(fix('TDY~A1'), 'TDY'))",
            "for bad in ('RS', 'RSA10', 'MT'):",
            "    try:",
            "        cf.next_round(refs, bad)",
            "        print('allowed ' + bad)",
            "    except cf.Refusal:",
            "        print('refused ' + bad)",
            "print(cf.next_round(refs, 'RS', True))");

        assertEquals(said, String.join("\n", "RSA11", "VD12", "TDY2", "refused RS", "refused RSA10", "refused MT", "RS", ""),
            "the next round after RSA, RSA2, RSA9 and RSA10 is RSA11 (not RSA10, which sorting the numbers as text"
            + " gives), RSA is not RSAX, a prefix the store has never used is refused until it is declared new, and"
            + " the digits of RSA10 are a round, not a prefix");
    }

    /**
     * A prefix the store has never used is refused unless `--new` says it is one; `--new` on a used prefix is refused.
     *
     * @throws Exception on a failure to run the tool
     */
    @Test
    public void testAPrefixTheStoreHasNeverUsedIsRefused() throws Exception
    {
        assertTrue(roundsInTheMirror("ZQJ").isEmpty(), "ZQJ is meant to be a prefix nothing has used");

        Ran no = tool("--next", "ZQJ");

        assertEquals(no.exit, 1, "--next of a prefix the store has never used must be refused");
        assertEquals(no.out.trim(), "", "a refused prefix must print no round number: " + no.out);
        assertTrue(no.err.contains("never used"), "the refusal should say the store has never used it: " + no.err);

        Ran declared = tool("--next", "ZQJ", "--new");

        assertEquals(declared.exit, 0, "--new declares the prefix: " + declared.err);
        assertEquals(declared.out.trim(), "ZQJ", "a new prefix's first round is the bare prefix, as RSA-A1 is");

        assertEquals(tool("--next", "RSA", "--new").exit, 1, "--new must not be accepted for a prefix in use");
    }

    /**
     * `--check-unused` refuses a ref, or a whole round, the store already holds, and passes the next ones.
     *
     * @throws Exception on a failure to run the tool
     */
    @Test
    public void testARefOrRoundAlreadyInTheStoreIsRefused() throws Exception
    {
        int highest = highestRound("RSA");

        String taken = null;

        for (java.util.Map.Entry<String, Integer> e : roundsInTheMirror("RSA").entrySet())
        {
            if (e.getValue() == highest) taken = e.getKey();
        }

        assertTrue(taken != null, "no RSA ref of round " + highest);

        Ran ref = tool("--check-unused", taken);

        assertEquals(ref.exit, 1, taken + " is in the store, so --check-unused must refuse it");
        assertTrue(ref.err.contains(taken), "the refusal should name the ref: " + ref.err);

        assertEquals(tool("--check-unused", "RSA" + highest).exit, 1,
            "round RSA" + highest + " is in the store, so --check-unused must refuse the round");

        assertEquals(tool("--check-unused", "RSA" + (highest + 1)).exit, 0,
            "round RSA" + (highest + 1) + " is free");

        assertEquals(tool("--check-unused", "RSA" + (highest + 1) + "-A1").exit, 0,
            "RSA" + (highest + 1) + "-A1 is free");

        assertEquals(script(
            "refs = fix('RSA~A1', 'RSA2~A1')",
            "out = ''",
            "for ask in fix('RSA', 'RSA~A1', 'RSA~A2', 'RSA2', 'RSA3', 'RSA2~A1'):",
            "    try:",
            "        cf.check_unused(refs, ask)",
            "        out += 'F'",
            "    except cf.Refusal:",
            "        out += 'T'",
            "print(out)"),
            "TTFTFT\n",
            "asked in order - round 1, its first ref, a free ref of it, round 2, round 3, round 2's first ref - the answers are"
            + " taken or free:"
            + " round 1 is the bare prefix, a ref is taken only when it is exactly in the store, and a round only when"
            + " some ref carries its number");
    }

    /**
     * The store's refs include the dead citations, and a store that disagrees with its mirror hands out nothing.
     *
     * @throws Exception on a failure to run python
     */
    @Test
    public void testTheStoreAndItsMirrorMustAgreeBeforeANumberIsHandedOut() throws Exception
    {
        String said = script(
            "d = tempfile.mkdtemp()",
            "db = os.path.join(d, 't.db')",
            "mirror = os.path.join(d, 'm.tsv')",
            "conn = triagedb.connect(db)",
            "first, dead = fix('ZZ~A1', 'ZZ7~C1')",
            "conn.execute(\"INSERT INTO finding (ref, document) VALUES (?, 'ZZ.md')\", (first,))",
            "conn.execute(\"INSERT INTO dead_citation (ref) VALUES (?)\", (dead,))",
            "conn.commit()",
            "conn.close()",
            "tab = chr(9)",
            "io.open(mirror, 'w').write('# header\\n' + first + tab + 'ZZ.md\\n' + dead + tab + '-\\n')",
            "refs = cf.refs_in_the_store(db, mirror)",
            "print(len(refs), cf.next_round(refs, 'ZZ'))",
            "io.open(mirror, 'w').write('# header\\n' + first + tab + 'ZZ.md\\n')",
            "try:",
            "    cf.refs_in_the_store(db, mirror)",
            "    print('agreed')",
            "except cf.Refusal as no:",
            "    print('disagree' if 'disagree' in str(no) else str(no))");

        assertEquals(said, "2 ZZ8\ndisagree\n",
            "a ref cited from the code and a finding in no document (the DEAD roll) is a used number too, and a"
            + " store whose mirror lacks one of its refs must refuse to hand out a number");
    }

    // ---------------------------------------------------------------- no mode drops a deleted round's rows (FR-136)

    /**
     * The full rebuild refuses while the store holds a document the folder it re-reads does not, however many
     * documents the folder holds.
     *
     * The folder is 25 stand-in documents, past the "enough documents" floor, so only the stored-documents check
     * stands between this and replacing the table.  `--check` is used so that a failure here writes nothing.
     *
     * @throws Exception on a failure to run python
     */
    @Test
    public void testTheFullRebuildRefusesWhileTheStoreHoldsADocumentTheFolderLacks() throws Exception
    {
        String said = script(
            "d = tempfile.mkdtemp()",
            "for i in range(25):",
            "    io.open(os.path.join(d, 'ZQX%d.md' % i), 'w').write('### ZQX%d-A1 - a finding\\n' % (i + 1))",
            "cf.REVIEWS = d",
            "sys.argv = ['catalog-findings.py', '--check']",
            "print('returned', cf.main())",
            "print(cf.rebuild_would_drop({'a.md', 'b.md'}, {'a.md', 'c.md'}))",
            "print(cf.rebuild_would_drop({'a.md'}, {'a.md', 'c.md'}))");

        assertTrue(said.contains("REFUSING TO RUN"), "a full rebuild over a folder that lacks the store's documents must"
            + " refuse.  It printed:\n" + said);

        assertTrue(said.contains("returned 1"), "and return 1.  It printed:\n" + said);

        assertTrue(said.contains("['b.md']\n[]\n"), "the documents a rebuild would drop are those the store has and"
            + " the folder lacks.  It printed:\n" + said);

        assertTrue(!said.contains("findings:   "), "it must refuse before it reports a catalogue it would build.  It"
            + " printed:\n" + said);
    }

    /**
     * Loading rows over the table refuses to drop a stored document the rows do not name, and merging a round
     * leaves every other document's rows alone.
     *
     * @throws Exception on a failure to run python
     */
    @Test
    public void testNoLoadOrMergeDropsADocumentsRowsThatIsNotBeingReplaced() throws Exception
    {
        String said = script(
            "conn = triagedb.connect(':memory:')",
            "def put(ref, document):",
            "    conn.execute('INSERT INTO finding (ref, document) VALUES (?, ?)', (ref, document))",
            "put('X-A1', 'A.md')",
            "put('X-A2', 'A.md')",
            "put('Y-A1', 'B.md')",
            "conn.commit()",
            "try:",
            "    triagedb.load_findings(conn, [{'ref': 'X-A1', 'document': 'A.md'}, {'ref': 'X-A2', 'document': 'A.md'}])",
            "    print('loaded')",
            "except ValueError:",
            "    print('load refused')",
            "print(conn.execute('SELECT COUNT(*) FROM finding').fetchone()[0])",
            "print(triagedb.prune_findings(conn, [{'ref': 'X-A1', 'document': 'A.md'}]))",
            "print(conn.execute(\"SELECT COUNT(*) FROM finding WHERE document = 'B.md'\").fetchone()[0])",
            "print(triagedb.add_findings(conn, [{'ref': 'Z-A1', 'document': 'C.md'}]))",
            "print(conn.execute('SELECT COUNT(*) FROM finding').fetchone()[0])");

        assertEquals(said, String.join("\n", "load refused", "3", "[('A.md', 'X-A2')]", "1", "(1, 0)", "3", ""),
            "load_findings must refuse rows that do not name every stored document (B.md's row would go), pruning"
            + " removes only a document's own rows the document no longer makes, and adding a new document keeps all"
            + " the rest");
    }

    /**
     * `--add` refuses a document that shares its name with a deleted round's and makes another round, because
     * pruning is by document name; and `--add --check` writes nothing.
     *
     * The store is only read here: both runs are `--check`, and the claim holds the store's bytes.
     *
     * @throws Exception on a failure to run the tool
     */
    @Test
    public void testAddRefusesADocumentThatWouldTakeAnotherRoundsRows() throws Exception
    {
        File folder = java.nio.file.Files.createTempDirectory("catalog-round").toFile();

        byte[] round = ("**Prefix:** RSA99\n\n### RSA99" + "-A1 - a finding\n").getBytes(StandardCharsets.UTF_8);

        try
        {
            byte[] before = Files.readAllBytes(new File("docs/manual-tests/triage.db").toPath());

            // A deleted round's file name, holding a round it never made.
            Files.write(new File(folder, "RSA75.md").toPath(), round);

            Ran clash = tool("--add", folder.getPath(), "--check");

            assertEquals(clash.exit, 1, "adding RSA75.md as round 99 would delete round 75's rows:\n" + clash.out);
            assertTrue(clash.out.contains("REFUSING"), "it should say it refuses:\n" + clash.out);

            assertTrue(new File(folder, "RSA75.md").delete(), "could not remove the stand-in");

            Files.write(new File(folder, "RSA99.md").toPath(), round);

            Ran fresh = tool("--add", folder.getPath(), "--check");

            assertEquals(fresh.exit, 1, "a finding the store lacks leaves the folder not yet catalogued:\n" + fresh.out);
            assertTrue(fresh.out.contains("1 not in the store"), "it should count the finding:\n" + fresh.out);
            assertTrue(fresh.out.contains("NOTHING WRITTEN"), "and say it wrote nothing:\n" + fresh.out);

            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(new File("docs/manual-tests/triage.db").toPath())),
                "--add --check must not change the store");
        }
        finally
        {
            File[] left = folder.listFiles();

            if (left != null) for (File f : left) f.delete();

            folder.delete();
        }
    }

    /**
     * The two procedure documents tell a round to take its number from the store.
     *
     * @throws Exception on a failure to read
     */
    @Test
    public void testTheProcedureDocumentsSendARoundToTheStoreForItsNumber() throws Exception
    {
        for (String path : new String[] {"docs/reviews/README.md", "docs/reviews/FANOUT.md"})
        {
            String said = flowed(path);

            assertTrue(said.contains("catalog-findings.py --next"),
                path + " should tell a round to take its number with `catalog-findings.py --next PREFIX`, which"
                + " reads the finding store; the folder it once looked in is empty by design");
        }
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
