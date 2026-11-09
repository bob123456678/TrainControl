package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Every review-finding id cited from the code leads somewhere.
 *
 * Adam: *"update our MT triage database to catalog each review item.  Now, no reference will ever be stale or lost, but
 * useless prose will go away."*
 *
 * Some ids do not resolve: they cluster into whole prefixes whose declaring document is gone.  They are rolled at the
 * foot of `triagedb.py findings` (`ref` and four dashes; the citing files are in the `dead_citation` table), and this
 * holds the count: a new one is a comment pointing at nothing.  Do not read a high count as license to delete the
 * documents.  It asks the catalog, not the prose, which would go vacuous once the paragraph is deleted.
 *
 * @author Adam
 */
public class testEveryCitationResolves
{
    /** Shaped like a finding id: two to eight capitals, a dash, an optional severity letter, digits. */
    private static final Pattern CITATION =
        Pattern.compile("\\b([A-Z][A-Z0-9]{1,7}-[A-Z]?\\d+[a-z]?)\\b");

    /**
     * Prefixes that look like a finding and are not one: the trackers `MT`, `FR` and `OB` (checked elsewhere),
     * protocols, units and connectors (`UTF-8`, `MM-2`, `SHA-1`, `RJ45-1`), locomotive classes (`SU45`, `EN57`), function ranges (`F17-32`)
     * and the tail of a regex character class (`Z0-9`).
     */
    private static final Pattern NOT_A_FINDING = Pattern.compile(
        "^(MT|FR|OB|SU45|EN57|F\\d+|Z0|UTF|ISO|CS|MM|DCC|RGB|ARGB|HTTP|JSON|IPV4|SHA|MD5|JDK|API|UI|RJ45"
        + "|ID|X|Y|Z)-");

    /** This file, which names dead ids rather than citing them, so it is left out of the scan. */
    private static final String THE_ROLL_ITSELF = "testEveryCitationResolves.java";

    /** Citations that resolve to nothing in `docs/`: a ratchet that may fall, and then must be lowered. */
    private static final int DEAD_CITATIONS = 15;

    /**
     * Every id cited from the code is defined in `docs/`, or is one of the known dead ones.
     *
     * MUTATION: cite `ZZ-A1` in any source comment and this fails, naming the file.
     */
    @Test
    public void testEveryCitationLeadsSomewhere() throws Exception
    {
        Map<String, Set<String>> cited = new LinkedHashMap<>();

        for (String root : new String[]{"src", "test"})
        {
            for (File f : filesUnder(new File(root), ".java", ".properties", ".xml"))
            {
                if (f.getName().equals(THE_ROLL_ITSELF)) continue;

                String body = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);

                Matcher m = CITATION.matcher(body);

                while (m.find())
                {
                    if (NOT_A_FINDING.matcher(m.group(1)).find()) continue;

                    Set<String> where = cited.get(m.group(1));

                    if (where == null) cited.put(m.group(1), where = new LinkedHashSet<String>());

                    where.add(f.getName());
                }
            }
        }

        assertTrue(cited.size() > 300,
            "only " + cited.size() + " finding ids were found in the code, which is far fewer than this"
            + " codebase carries - the pattern has gone stale and this test is checking almost nothing");

        // WHAT IS ACTUALLY A FINDING: a row in the catalog, its first column the ref in backticks.  Nine carry a `
        // @date` because they mean two things in two documents; a citation of the bare ref resolves through either.
        Set<String> defined = new LinkedHashSet<>();

        for (String line : support.Store.findings().split("\n"))
        {
            // THE ROLL IS AT THE FOOT of the same table, and its entries are shaped like findings. Read
            // past this marker and every dead citation resolves to itself.
            if (line.startsWith("# DEAD")) break;

            if (line.startsWith("#") || line.isEmpty()) continue;

            defined.add(line.split("\t")[0].split(" @")[0]);
        }

        assertTrue(defined.size() > 900,
            "the catalogue parsed to only " + defined.size() + " findings, far fewer than it holds."
            + " Its format has changed and this no longer reads it, which would let every citation in"
            + " the codebase read as dead");

        List<String> dead = new ArrayList<>();

        for (Map.Entry<String, Set<String>> each : cited.entrySet())
        {
            if (!defined.contains(each.getKey()))
            {
                dead.add(each.getKey() + " (cited in " + each.getValue() + ")");
            }
        }

        assertTrue(dead.size() <= DEAD_CITATIONS,
            "there are now " + dead.size() + " citations in the code that are findings in no document,"
            + " up from " + DEAD_CITATIONS + ". A comment naming a finding nobody can look up is"
            + " a comment that has stopped explaining anything - and the reviews are being retired on"
            + " the promise that every id still leads somewhere (MON-C11). " + dead);

        assertTrue(dead.size() == DEAD_CITATIONS,
            dead.size() + " dead citations remain, fewer than the " + DEAD_CITATIONS + " recorded."
            + " Lower DEAD_CITATIONS to " + dead.size() + " so the improvement is kept, and take them"
            + " out of the roll at the foot of `triagedb.py findings`");
    }

    /**
     * No comment cites a LINE NUMBER.  `CommandRow` states the rule: *"By method name, not by line: ... a stale line
     * number in a javadoc outlives every edit above it with nothing able to notice."*  Checking the cited line would
     * rot as the citations did, so the shape itself is refused.  Exempt: the rule's own counter-example in
     * `CommandRow` and a git revision (`git show master:path/File.java:111`).
     *
     * MUTATION: write `// see Layout.java:1234` in any source comment and this fails, naming the file.
     */
    @Test
    public void testNoCommentCitesALineNumber() throws Exception
    {
        // A name, then a colon, then at least two digits; the name starts with a capital, which keeps
        // `http://host:8080` and a clock time out of it.
        Pattern byLine = Pattern.compile(
            "`?\\b([A-Z][A-Za-z0-9_]*(?:\\.java)?):(\\d{2,5})\\b");

        List<String> found = new ArrayList<>();

        for (File f : filesUnder(new File("src"), ".java"))
        {
            String body = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            int lineNumber = 0;

            for (String line : body.split("\n"))
            {
                lineNumber++;

                String trimmed = line.trim();

                // Comments only: a citation in code would not compile, and a string literal of this shape is data.
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) continue;

                // The rule's own counter-example, and a git revision reference.
                if (trimmed.contains("which was already wrong when it was copied")) continue;

                if (trimmed.contains("git show ")) continue;

                Matcher m = byLine.matcher(trimmed);

                while (m.find())
                {
                    found.add(f.getPath() + ":" + lineNumber + " cites " + m.group());
                }
            }
        }

        assertTrue(found.isEmpty(),
            "a comment cites a line number.  `CommandRow` states the rule - by method name, not by "
            + "line - because a stale line number outlives every edit above it with nothing able to "
            + "notice, and when this test was written all six such citations in the tree pointed at "
            + "unrelated code (X8-C1).  Found:\n  " + String.join("\n  ", found));
    }

    /**
     * How many sub-lettered findings the catalog holds: a severity revision and a dead citation, both in deleted
     * documents.
     */
    private static final int SUBLETTERED = 2;

    /**
     * No cataloged finding carries a sub-letter its document does not write.  A parser once read a mutation table as
     * findings (`X8V-validation.md` opens with a Method table keyed `A1a`), so fifteen findings entered the catalog
     * that the document never made.  The guard is narrow because "every cataloged ref appears in its document" is red
     * on arrival (sixty documents declare a prefix and number headings `### A1`); no legitimate row has a SUB-LETTER.
     *
     * With every review document deleted, `resolvable` is zero by design and the `unwritten` assertion cannot fail, so
     * the count ratchet is the live half; the document arm revives when a review folder is added back through `--add`.
     *
     * MUTATION: add a sub-lettered finding to the store and the RATCHET names the count; the document
     * arm names the row itself only while some review document is present to be read.
     */
    @Test
    public void testNoCataloguedFindingHasASubLetterItsDocumentDoesNotWrite() throws Exception
    {
        String mirror = support.Store.findings();

        // The reading is in `subLetteredIn`, so a made-up mirror and made-up documents can drive the same code
        // (`testTheSubLetterCheckSeesADocumentThatDoesNotWriteIt`) - with every review document deleted, this
        // arm has nothing to read.
        SubLetters read = subLetteredIn(mirror, new File("docs"));

        List<String> unwritten = read.unwritten;

        int seen = read.seen;

        int resolvable = read.resolvable;

        // THE FLOOR, AND WHAT REPLACED IT: `resolvable > 0` would fail for the reason it was written, a guard anchored
        // to files scheduled for deletion.  What can still be held with no document to read is the COUNT, because the
        // fault always ADDS sub-lettered rows.
        assertTrue(seen <= SUBLETTERED,
            "the catalogue now holds " + seen + " sub-lettered findings (" + resolvable + " of its "
            + "rows name a document still in the tree) and held " + SUBLETTERED
            + " when the last review documents were deleted.  Sub-letters are rare by design - of "
            + rowsInTheMirror(mirror) + " rows exactly two carry one, and a fresh crop of them is the signature of a "
            + "Method or mutation table being read as findings (NSV-B1), which is what this guard is "
            + "for.  No document survives to check the new ones against, so the count is all there "
            + "is: re-run `python docs/tools/catalog-findings.py --add <folder>` and look at what it "
            + "added.  Lower this number if rows were legitimately removed");

        assertTrue(unwritten.isEmpty(),
            "the catalogue holds " + unwritten.size() + " finding(s) whose own document never writes "
            + "them, of " + seen + " sub-lettered rows checked.  That is the signature of a table being "
            + "read as findings - a Method or mutation table whose first column is A1a, B1a - and those "
            + "rows cannot be acted on by anybody, while one of them outranked a real disposition "
            + "(NSV-B1).  Re-run `python docs/tools/catalog-findings.py --add <folder>`, which is "
            + "authoritative for the documents in it.  Found:\n  " + String.join("\n  ", unwritten));
    }

    /** What reading the catalogue's sub-lettered rows came to: how many there were, how many could be compared, and the bad ones. */
    private static final class SubLetters
    {
        int seen;
        int resolvable;
        final List<String> unwritten = new ArrayList<>();
    }

    /** Reads the catalogue's sub-lettered rows against the documents under a folder. */
    private static SubLetters subLetteredIn(String mirror, File docs) throws Exception
    {
        SubLetters out = new SubLetters();

        java.util.regex.Pattern sublettered =
            java.util.regex.Pattern.compile("\\b([A-Z][A-Z0-9]{1,7}-[A-D]\\d{1,3}[a-z])\\b");

        List<String> unwritten = out.unwritten;

        int seen = 0;

        // HOW MANY ROWS COULD ACTUALLY BE CHECKED: without it the guard was vacuous, comparing nothing and reporting
        // success.
        int resolvable = 0;

        for (String row : mirror.split("\n"))
        {
            String[] cells = row.split("\t");

            if (cells.length < 2) continue;

            // COUNTED OVER EVERY ROW, not only the sub-lettered ones, which are legitimately rare.  COUNTED AND
            // REPORTED, not asserted on: "2 rows, 0 of them checkable" and "2 rows, all checked and clean" are
            // different states.
            if (documentExists(cells[1].trim(), docs)) resolvable++;

            java.util.regex.Matcher m = sublettered.matcher(cells[0].trim());

            if (!m.matches()) continue;

            seen++;

            // The document is the second column by the catalogue's own layout.  A sub-lettered ref is only
            // legitimate if the document writes it out, which is what the fifteen did not.
            File document = null;

            for (File candidate : filesUnder(docs, ".md"))
            {
                if (candidate.getName().equals(cells[1].trim()))
                {
                    document = candidate;

                    break;
                }
            }

            // A deleted document cannot be checked, and most have been: those rows are the catalog's whole purpose.
            if (document == null) continue;

            String body = new String(java.nio.file.Files.readAllBytes(document.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            if (!body.contains(cells[0].trim())) unwritten.add(cells[0].trim() + " in " + cells[1].trim());
        }

        out.seen = seen;
        out.resolvable = resolvable;

        return out;
    }

    /**
     * The sub-letter check can still see a document that does not write a ref, driven with made-up documents: the real
     * arm above compares nothing while every review document is deleted, so a break in the comparison would never show.
     * This runs `subLetteredIn` over a folder made here (writes its ref, does not, document gone, no sub-letter).
     *
     * MUTATION: break the `body.contains` comparison in `subLetteredIn`, and this fails while the arm above stays
     * green.
     */
    @Test
    public void testTheSubLetterCheckSeesADocumentThatDoesNotWriteIt() throws Exception
    {
        File docs = java.nio.file.Files.createTempDirectory("tc-citations").toFile();

        try
        {
            java.nio.file.Files.write(new File(docs, "ZZ-writes.md").toPath(),
                "# ZZ\n\nZZ-A1a is written out here.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            java.nio.file.Files.write(new File(docs, "ZZ-silent.md").toPath(),
                "# ZZ\n\nA Method table whose first column is B2b, never the full ref.\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

            String mirror = "ZZ-A1a\tZZ-writes.md\n"
                + "ZZ-B2b\tZZ-silent.md\n"
                + "ZZ-C3c\tZZ-gone.md\n"
                + "ZZ-A5\tZZ-silent.md\n";

            SubLetters read = subLetteredIn(mirror, docs);

            assertEquals(read.seen, 3, "three of the four rows carry a sub-letter");

            assertEquals(read.resolvable, 3, "three of the four rows name a document that exists");

            assertEquals(read.unwritten, java.util.Arrays.asList("ZZ-B2b in ZZ-silent.md"),
                "the check did not name the one sub-lettered row whose document never writes its ref - and it let the rows "
                + "whose document does write it, or is gone, through");
        }
        finally
        {
            for (File each : docs.listFiles()) each.delete();

            docs.delete();
        }
    }

    /**
     * The catalogue comes from the store, the only copy of what the review findings were: `triagedb.py findings` prints
     * it from `docs/manual-tests/triage.db`, and both are in the repository.
     */
    @Test
    public void testTheCatalogueComesFromTheStore() throws Exception
    {
        assertTrue(new File("docs/manual-tests/triage.db").isFile(),
            "docs/manual-tests/triage.db is gone. Since the review folder was deleted on 2026-09-08 it is the"
            + " only record of what the review findings were about");

        assertTrue(support.Store.findings().startsWith("# Every review finding, printed from"
            + " docs/manual-tests/triage.db by `triagedb.py findings`"),
            "`triagedb.py findings` no longer says where the catalogue comes from");
    }

    /** How many rows the catalogue holds, counted rather than quoted, since a figure typed into a message goes stale. */
    private static int rowsInTheMirror(String mirror)
    {
        int rows = 0;

        for (String line : mirror.split("\n"))
        {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;

            rows++;
        }

        return rows;
    }

    /**
     * Whether a review document named in the catalog is still in the tree.  Most are not, deliberately; this says
     * whether the name-to-file resolution works rather than silently matching nothing.
     */
    private static boolean documentExists(String named, File docs)
    {
        if (named == null || named.isEmpty()) return false;

        for (File candidate : filesUnder(docs, ".md"))
        {
            if (candidate.getName().equals(named)) return true;
        }

        return false;
    }

    /** Every .java or .md file under a directory, recursively. */
    private static List<File> filesUnder(File from, String... extensions)
    {
        List<File> out = new ArrayList<>();

        File[] children = from.listFiles();

        if (children == null) return out;

        for (File child : children)
        {
            if (child.isDirectory())
            {
                out.addAll(filesUnder(child, extensions));

                continue;
            }

            for (String extension : extensions)
            {
                if (child.getName().endsWith(extension))
                {
                    out.add(child);

                    break;
                }
            }
        }

        return out;
    }
}
