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
 * They are rolled at the foot of `docs/manual-tests/findings.tsv`, with the files that cite each one,
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
