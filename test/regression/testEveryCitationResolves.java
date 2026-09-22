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
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Every review-finding id cited from the code leads somewhere.
 *
 * Adam's ruling on MON-C11, 2026-09-08: *"update our MT triage database to catalog each review item.
 * Now, no reference will ever be stale or lost, but useless prose will go away."*
 *
 * The review folder had reached 143 documents and 1,252 findings, and the comments in this codebase
 * cite them constantly - `RGD-B2`, `MON-C6`, `DD-A7` - because that is how a comment says *why* rather
 * than *what*. The plan is to delete the prose once it has stopped earning its place. That is only safe
 * if every id a comment names still resolves to something, which is what this holds.
 *
 * **Forty-five do not, today**, and they are not scattered: they cluster into whole prefixes whose
 * declaring document is gone. `RC` has one that defines `A1-A5` and `B1-B5` and stops, so every
 * `RC-A6` upward is a dead end; `LE2` and `LD` have no declaring document in `docs/` at all. Those
 * three are two thirds of the list.
 *
 * They are rolled at the foot of `docs/manual-tests/findings.tsv` - `ref` and four dashes, the citing
 * files being in the `dead_citation` table rather than in the mirror (VD12-R11) -
 * so the citation leads somewhere even though the finding does not - and this holds the count at
 * forty-five.
 *
 * A forty-sixth is a comment pointing at nothing, and the point of catching it here is the day it is
 * written rather than six weeks later, when nobody remembers what it meant.
 *
 * **Do not read a high count as licence to delete the documents.** It was 230 an hour before this was
 * written, and every fall since came from teaching the catalogue a spelling the review folder was
 * already using - four ways of declaring a prefix, headings with and without separators, refs bolded
 * inside table cells. `V33` and `DOC` were on this list as dead prefixes while their documents sat in
 * `docs/reviews/` with every finding in them. An id that resolves to nothing may mean the finding is
 * gone, or only that this file cannot see it.
 *
 * **It asks the catalogue, not the prose**, which is the whole point. An earlier version searched every
 * `.md` under `docs/` and passed if anything mentioned the id at all. Three of the ten above -
 * `LD-4`, `LD-9`, `SM31-108` - are named in a review paragraph that never filed them, so they passed.
 * Under a ruling whose second clause is *"useless prose will go away"*, that check goes vacuous at the
 * moment it matters: delete the paragraph and the citation still "resolves", to a file that is gone.
 *
 * @author Adam
 */
public class testEveryCitationResolves
{
    /** Shaped like a finding id: two to eight capitals, a dash, an optional severity letter, digits. */
    private static final Pattern CITATION =
        Pattern.compile("\\b([A-Z][A-Z0-9]{1,7}-[A-Z]?\\d+[a-z]?)\\b");

    /**
     * Prefixes that look like a finding and are not one.
     *
     * `MT`, `FR` and `OB` are the manual-test and issue trackers, which live in `docs/manual-tests/`
     * and have their own consistency check. The rest are protocols, encodings and units that happen to
     * share the shape - `UTF-8`, `MM-2`, `SHA-1`.
     *
     * `SU45` and `EN57` are locomotive classes, `F\d` is a Marklin function range and `Z0` is the tail
     * of a character class in a regex: `SU45-070`, `F17-32` and `Z0-9` were all read as findings. On a
     * railway, real data is shaped like a citation.
     */
    private static final Pattern NOT_A_FINDING = Pattern.compile(
        "^(MT|FR|OB|SU45|EN57|F\\d+|Z0|UTF|ISO|CS|MM|DCC|RGB|ARGB|HTTP|JSON|IPV4|SHA|MD5|JDK|API|UI"
        + "|ID|X|Y|Z)-");

    /**
     * The catalogue, as plain text: `ref<TAB>document<TAB>disposition`, `#` for a comment.
     *
     * NOT `docs/reviews/findings.md`, which this used to read. Adam is deleting the reviews folder
     * whole, that file included, and a guard anchored to a file scheduled for deletion is a guard that
     * fails the week after nobody is thinking about it. The mirror sits beside the database instead.
     *
     * The database is the record, but there is no SQLite driver on this project's classpath, so this
     * cannot read it. `docs/tools/catalog-findings.py` writes both from the same rows in one pass.
     */
    private static final File CATALOGUE = new File("docs/manual-tests/findings.tsv");

    /**
     * This file, which names dead ids rather than citing them.
     *
     * The javadoc above lists the seven that resolve nowhere, and the test below describes its own
     * mutation as citing `ZZ-A1`. Scanned like every other file, that mutation is permanently applied
     * and five of the seven are counted twice: the guard reports itself, which is the fault
     * `testNoTestParsesALayoutWithoutWiringIt` had. The cost is that a citation added to THIS file is
     * not checked, which is the right trade for the one file whose subject is dead citations.
     */
    private static final String THE_ROLL_ITSELF = "testEveryCitationResolves.java";

    /**
     * How many citations resolve to nothing anywhere in `docs/`, measured 2026-09-08.
     *
     * A ratchet, not a target: it may fall, and when it does this test says so and asks for the number
     * to come down with it.
     */
    private static final int DEAD_CITATIONS = 45;

    /**
     * Every id cited from the code is defined in `docs/`, or is one of the known dead ones.
     *
     * MUTATION: cite `ZZ-A1` in any source comment and this fails, naming the file.
     *
     * @throws Exception on a failure to read the tree
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

        // WHAT IS ACTUALLY A FINDING: a row in the catalogue, and nothing else. Its first column is
        // the ref, in backticks, and nine of them carry a ` @date` because they mean two things in two
        // documents - a citation of the bare ref resolves through either.
        //
        // The roll of dead ids at the foot of the same file is a BULLET LIST, so it is not read back
        // here as ten findings that resolve themselves.
        Set<String> defined = new LinkedHashSet<>();

        assertTrue(CATALOGUE.isFile(),
            CATALOGUE + " is missing, so there is nothing to resolve citations against."
            + " Regenerate it: python docs/tools/catalog-findings.py");

        for (String line : new String(java.nio.file.Files.readAllBytes(CATALOGUE.toPath()),
                java.nio.charset.StandardCharsets.UTF_8).split("\n"))
        {
            // THE ROLL IS AT THE FOOT of the same file, and its entries are shaped like findings. Read
            // past this marker and every dead citation resolves to itself.
            if (line.startsWith("# DEAD")) break;

            if (line.startsWith("#") || line.isEmpty()) continue;

            // Nine refs mean two things in two documents and are written `REF @date`; a citation of
            // the bare ref resolves through either.
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
            + " out of the roll at the foot of " + CATALOGUE);
    }

    /**
     * No comment cites a LINE NUMBER (X8-C1).
     *
     * `CommandRow` states the rule and nothing enforced it: *"By method name, not by line (VD9-C12):
     * the first version of this paragraph cited `RouteEditorFrame:3415-3419`, which was already wrong
     * when it was copied here out of a review, and a stale line number in a javadoc outlives every
     * edit above it with nothing able to notice."*
     *
     * It was written and not swept. Six such citations were in the tree when this test was added and
     * **all six pointed at unrelated code** - the closest was 70 lines out, the furthest 232. The test
     * beside this one could not see any of them: its pattern finds review ids, so it reported clean
     * about a whole class of citation it had never heard of. `guard-knows-only-what-it-lists`.
     *
     * **Refused outright rather than resolved.** Checking that the cited line still holds the quoted
     * phrase would pass today and rot exactly as the citations did; the rule the codebase already
     * wrote down is that the shape itself is wrong, and a name is a thing the compiler and every
     * search can follow.
     *
     * Two exemptions, both deliberate:
     *
     *   the rule's own counter-example    `CommandRow` quotes the bad citation to explain the rule.
     *   a git revision                    `git show master:path/File.java:111` names a revision of a
     *                                     file, not a line of the tree.
     *
     * MUTATION: write `// see Layout.java:1234` in any source comment and this fails, naming the file.
     *
     * @throws Exception on a failure to read the tree
     */
    @Test
    public void testNoCommentCitesALineNumber() throws Exception
    {
        // A name, then a colon, then at least two digits - the shape every one of the six had.  The
        // name has to start with a capital, which is what keeps `http://host:8080` and a clock time
        // out of it.
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

                // Comments only.  A citation in code would be a compile error, and a string literal
                // that happens to hold this shape is data.
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) continue;

                // The rule's own counter-example, which quotes the bad citation to explain why the
                // shape is wrong, and a git revision reference.
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
     * How many sub-lettered findings the catalogue held when the last review documents were deleted.
     *
     * `DD-D7a`, a severity revision in a document deleted on 2026-09-08, and `WP-C19d`, a dead
     * citation.  Both name documents that are gone, which is why the floor above could no longer be
     * "at least one row is resolvable".
     */
    private static final int SUBLETTERED = 2;

    /**
     * No catalogued finding carries a sub-letter its document does not write (N8-B3, NSV-B1).
     *
     * **A parser read a mutation table as findings.** `catalog-findings.py` accepted any table row whose
     * first cell looked like `A1a` as a finding of the document's declared prefix, and
     * `X8V-validation.md` opens with a Method table keyed exactly that way - so fifteen findings entered
     * the catalogue that the document never made, four of them at severity A against an A section that
     * says "Nothing". The same rows outranked the real status table, so `X8V-C1` was catalogued as
     * "2 of 2 red" where its own table said closed.
     *
     * **Why the guard is this narrow.** The obvious check - every catalogued ref must appear in its
     * document - is red on arrival: sixty documents declare a prefix and then number their headings
     * `### A1`, so the full ref never appears in the text, and that check would fail on 64 legitimate
     * rows before reaching the bad ones. What the fifteen had in common, and what no legitimate row in
     * the catalogue has, is a SUB-LETTER: `A1a`, `B2b`. Across 2,486 rows only twelve carry one and
     * eleven were X8V's. So a sub-lettered ref whose document does not write it out is the signature of
     * this fault and of nothing else.
     *
     * This reads the mirror rather than the database, for the reason the test above does: the mirror is
     * what is committed and greppable, and a Java test cannot run the generator.
     *
     * **WHAT THIS STILL HOLDS, AND WHAT IT NO LONGER CAN (VD12-R17).**  With every review document
     * deleted, `resolvable` is zero by design and the `document == null` skip below fires for every
     * row - so the `unwritten` assertion at the foot cannot fail, and the count ratchet is the live
     * half.  Both are kept: the ratchet catches the fault (a fresh crop of sub-lettered rows), and
     * the document arm revives the moment a review folder is added back through `--add`.
     *
     * MUTATION: add a sub-lettered row to the mirror and the RATCHET names the count; the document
     * arm names the row itself only while some review document is present to be read.
     */
    @Test
    public void testNoCataloguedFindingHasASubLetterItsDocumentDoesNotWrite() throws Exception
    {
        String mirror = new String(java.nio.file.Files.readAllBytes(
            new File("docs/manual-tests/findings.tsv").toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        java.util.regex.Pattern sublettered =
            java.util.regex.Pattern.compile("\\b([A-Z][A-Z0-9]{1,7}-[A-D]\\d{1,3}[a-z])\\b");

        List<String> unwritten = new ArrayList<>();

        int seen = 0;

        // HOW MANY ROWS COULD ACTUALLY BE CHECKED (FV3).
        //
        // Without this the guard was vacuous as shipped: both surviving sub-lettered rows name documents
        // that no longer exist, so every row was skipped, nothing was compared, and it reported success.
        // A guard that examines nothing is the thing this class exists to catch.
        int resolvable = 0;

        for (String row : mirror.split("\n"))
        {
            String[] cells = row.split("\t");

            if (cells.length < 2) continue;

            // COUNTED OVER EVERY ROW, not only the sub-lettered ones (FV3).  The floor asks whether the
            // resolution works at all, and sub-lettered refs are legitimately rare - scoping the count
            // inside the filter below made the floor as vacuous as the check it was added to guard.
            // COUNTED AND REPORTED, not asserted on.  It is zero today - no review document is left
            // in the tree - and the failure below names it, because "2 rows, 0 of them checkable"
            // and "2 rows, all checked and clean" are two different states and the reader has to be
            // told which one held.
            if (documentExists(cells[1].trim())) resolvable++;

            java.util.regex.Matcher m = sublettered.matcher(cells[0].trim());

            if (!m.matches()) continue;

            seen++;

            // The document is the second column by the mirror's own layout.  A sub-lettered ref is only
            // legitimate if the document writes it out, which is what the fifteen did not.
            File document = null;

            for (File candidate : filesUnder(new File("docs"), ".md"))
            {
                if (candidate.getName().equals(cells[1].trim()))
                {
                    document = candidate;

                    break;
                }
            }

            // A document that has been deleted cannot be checked, and most of them have been - the
            // folder was retired on 2026-09-08.  Those rows are the catalogue's whole purpose.
            if (document == null) continue;

            String body = new String(java.nio.file.Files.readAllBytes(document.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            if (!body.contains(cells[0].trim())) unwritten.add(cells[0].trim() + " in " + cells[1].trim());
        }

        // THE FLOOR, AND WHAT REPLACED IT.
        //
        // It used to be `resolvable > 0` - at least one catalogued row naming a document still in the
        // tree - because both sub-lettered rows name deleted documents, so every row was skipped and
        // the guard reported success having compared nothing (FV3).
        //
        // The last review folders were deleted on 2026-09-21 and `resolvable` is now zero by design, so
        // that floor would fail for the reason it was written: a guard anchored to files scheduled for
        // deletion.  What can still be held with no document to read is the COUNT.  The fault this
        // catches - a Method or mutation table read as findings - always ADDS sub-lettered rows, so a
        // ratchet on how many exist catches it whether or not anything can be resolved.
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

    /**
     * The catalogue is regenerable and current.
     *
     * It is rendered from `docs/manual-tests/triage.db` by `triagedb.render_findings`, and a generated
     * file that has drifted from its source is worse than none: it is read as authoritative. This does
     * not re-run the generator - that needs Python - but it does hold the three things that would make
     * it a lie, the third being the database itself, which is now the only copy.
     *
     * @throws Exception on a failure to read the catalogue
     */
    @Test
    public void testTheCatalogueIsThereAndSaysWhereItCameFrom() throws Exception
    {
        assertTrue(CATALOGUE.isFile(),
            CATALOGUE + " has gone. It is the index every citation in this codebase resolves through,"
            + " and the reviews are being deleted on the strength of it (MON-C11)");

        String body = new String(java.nio.file.Files.readAllBytes(CATALOGUE.toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(body.contains("triagedb"),
            "the catalogue no longer names the script that generates it, so the next person to find it"
            + " out of date has no way to bring it up to date");

        File generator = new File("docs/manual-tests/triagedb.py");

        assertTrue(generator.isFile(),
            "the generator named by the catalogue is not in the repository. That is the fault one.sh had"
            + " - a tool nobody can see is a tool nobody can review, and it lived in a scratch directory"
            + " while its bug was fixed five times in the copy people could read");

        assertTrue(new File("docs/manual-tests/triage.db").isFile(),
            "docs/manual-tests/triage.db is gone. Since the review folder was deleted on 2026-09-08 it"
            + " and the mirror beside it are the only record of what 2,265 findings were about, and the"
            + " mirror is rendered FROM it - losing it means the next regeneration writes an empty file");
    }

    /**
     * How many rows the mirror holds, counted rather than quoted (VD13-T7).
     *
     * The figure in the ratchet's message was typed in twice and was wrong both times: 3,353 was the
     * finding count BEFORE the dead citations were rolled in, and 3,398 was that stale number plus
     * the 45.  A denominator the sentence argues from has to be counted from the file it has already
     * read.
     *
     * @param mirror the whole file
     * @return its data rows
     */
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
     * Whether a review document named in the catalogue is still in the tree.
     *
     * Most are not, and deliberately: the folder was retired on 2026-09-08 once the catalogue carried
     * it, which is the whole reason the catalogue exists. What this answers is whether ANY of them can
     * still be found, which is what says the name-to-file resolution above is working rather than
     * silently matching nothing.
     *
     * @param named the document cell from the mirror
     * @return true when a file of that name exists under docs/
     */
    private static boolean documentExists(String named)
    {
        if (named == null || named.isEmpty()) return false;

        for (File candidate : filesUnder(new File("docs"), ".md"))
        {
            if (candidate.getName().equals(named)) return true;
        }

        return false;
    }

    /**
     * Every .java or .md file under a directory, recursively.
     *
     * @param from where to look
     * @param extensions which files to take
     * @return the files
     */
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
