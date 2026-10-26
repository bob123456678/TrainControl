package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * The scenario library cannot rot: no orphan fixtures, and no README that lies about who uses it.
 *
 * An ORPHAN FIXTURE is a checked-in railway nothing runs against: `test/` already had seven ad-hoc layout folders named
 * for who made them, four of them the same five pages, each added for one test and outliving it.  A STALE "USED BY"
 * LIST goes out of date first, because it changes when a DIFFERENT file changes, so it is not maintained by hand and
 * checked by eye but compared, here, with what the test sources say.
 *
 * **The link is a name in the source, not a call:** a test that writes the scenario's name in a comment counts as a
 * user, deliberately, since its author will want to know when the railway changes.
 *
 * @author Adam
 */
public class testEveryScenarioIsUsedAndSaysSo
{
    /** The heading a scenario's README lists its users under. */
    private static final String USED_BY = "## Used by";

    /**
     * The heading that says which properties a test must set for itself.  Required of every README because it is the
     * half of Adam's design a reader would assume the other way round: "hand-authored for topology, lengths in code."
     */
    private static final String NOT_INVARIANTS = "## Not invariants";

    /** Every scenario is opened by at least one test class. */
    @Test
    public void testEveryScenarioIsUsedByATest() throws Exception
    {
        List<String> scenarios = support.Scenario.names();

        assertTrue(!scenarios.isEmpty(), "there are no scenarios under " + support.Scenario.ROOT
            + " at all, so this guard is guarding nothing - either the library has been deleted or"
            + " this test is being run from somewhere other than the project root");

        Map<String, Set<String>> users = usersByScenario(scenarios);

        List<String> orphans = new ArrayList<>();

        for (String scenario : scenarios)
        {
            if (users.get(scenario).isEmpty()) orphans.add(scenario);
        }

        assertEquals(orphans.toString(), "[]",
            orphans.size() + " scenario(s) under " + support.Scenario.ROOT + " are opened by no test"
            + " class: " + orphans + ". A checked-in railway nothing runs against is a file that gets"
            + " maintained for nothing - write the test it was built for, or delete the folder.");
    }

    /** Every scenario has a README, and it carries the two sections that make it useful. */
    @Test
    public void testEveryScenarioHasAReadme() throws Exception
    {
        List<String> missing = new ArrayList<>();
        List<String> incomplete = new ArrayList<>();

        for (String scenario : support.Scenario.names())
        {
            File readme = new File(support.Scenario.folderFor(scenario), "README.md");

            if (!readme.isFile())
            {
                missing.add(scenario);

                continue;
            }

            String text = read(readme);

            if (!text.contains(USED_BY) || !text.contains(NOT_INVARIANTS)) incomplete.add(scenario);
        }

        assertEquals(missing.toString(), "[]",
            "these scenarios have no README.md, so nothing says what shape they are or what a test may"
            + " rely on: " + missing);

        assertEquals(incomplete.toString(), "[]",
            "these scenario READMEs are missing a \"" + USED_BY + "\" or \"" + NOT_INVARIANTS
            + "\" section: " + incomplete + ". The first is what the guard below compares against the"
            + " sources; the second is what stops the next author baking a length into the fixture.");
    }

    /**
     * And the "used by" list is the list of classes that actually name the scenario, compared both ways: a name in the
     * README that no source mentions has rotted, and a source that names the scenario without appearing in the README
     * is a user the next editor of that fixture will not know about (the more expensive direction).
     */
    @Test
    public void testEveryReadmeNamesExactlyItsUsers() throws Exception
    {
        List<String> scenarios = support.Scenario.names();

        Map<String, Set<String>> users = usersByScenario(scenarios);

        List<String> wrong = new ArrayList<>();

        for (String scenario : scenarios)
        {
            File readme = new File(support.Scenario.folderFor(scenario), "README.md");

            if (!readme.isFile()) continue;

            Set<String> claimed = listedUnderUsedBy(read(readme));

            Set<String> actual = users.get(scenario);

            if (claimed.equals(actual)) continue;

            wrong.add(scenario + ": README says " + sorted(claimed) + ", the sources say "
                + sorted(actual) + " (listed and not a user: " + sorted(missingFrom(claimed, actual))
                + "; a user and not listed: " + sorted(missingFrom(actual, claimed)) + ")");
        }

        assertEquals(wrong.toString(), "[]",
            wrong.size() + " scenario README(s) disagree with the test sources about who uses them."
            + " The list under \"" + USED_BY + "\" is maintained by hand and checked here, because it"
            + " changes when a DIFFERENT file changes and so goes stale without anybody touching it: "
            + wrong);
    }

    /** Which test classes name each scenario: scenario name to the fully qualified test classes that mention it. */
    private Map<String, Set<String>> usersByScenario(List<String> scenarios) throws Exception
    {
        Map<String, Set<String>> out = new LinkedHashMap<>();

        for (String scenario : scenarios) out.put(scenario, new LinkedHashSet<String>());

        File root = new File("test");

        File[] folders = root.listFiles();

        assertTrue(folders != null, "the test sources are not where this expects them: "
            + root.getAbsolutePath());

        for (File folder : folders)
        {
            if (!folder.isDirectory()) continue;

            // NOT `support`, WHICH HOLDS NO TESTS (`test/README.md`): `Scenario`'s own javadoc shows a scenario being
            // opened, and counting it would make that scenario permanently non-orphan whether or not any test ran
            // against it.
            if (folder.getName().equals("support")) continue;

            File[] files = folder.listFiles();

            if (files == null) continue;

            for (File file : files)
            {
                if (!file.getName().endsWith(".java")) continue;

                // NOT THIS FILE: it reads every scenario name off the filesystem and would otherwise be a listed user
                // of every scenario.
                if (file.getName().equals(getClass().getSimpleName() + ".java")) continue;

                String source = read(file);

                String qualified = folder.getName() + "."
                    + file.getName().substring(0, file.getName().length() - 5);

                for (String scenario : scenarios)
                {
                    // QUOTED, so a scenario name in a sentence is a mention and a folder name that is a substring of
                    // another word is not.
                    if (source.contains("\"" + scenario + "\"")) out.get(scenario).add(qualified);
                }
            }
        }

        return out;
    }

    /**
     * The class names a README lists under its "used by" heading: one per bullet, in backticks; anything else under it
     * is prose and ignored.
     */
    private Set<String> listedUnderUsedBy(String text)
    {
        Set<String> out = new LinkedHashSet<>();

        int at = text.indexOf(USED_BY);

        if (at < 0) return out;

        String after = text.substring(at + USED_BY.length());

        // Stops at the next heading of the same level, so a later section's code spans are not read as
        // users.
        int next = after.indexOf("\n## ");

        if (next >= 0) after = after.substring(0, next);

        for (String line : after.split("\r\n|\r|\n"))
        {
            String trimmed = line.trim();

            if (!trimmed.startsWith("- `")) continue;

            int close = trimmed.indexOf('`', 3);

            if (close < 0) continue;

            out.add(trimmed.substring(3, close).trim());
        }

        return out;
    }

    /** Everything in the first set that is not in the second. */
    private Set<String> missingFrom(Set<String> these, Set<String> those)
    {
        Set<String> out = new LinkedHashSet<>(these);

        out.removeAll(those);

        return out;
    }

    /** The names in a stable order, so a failure message reads the same twice. */
    private List<String> sorted(Set<String> names)
    {
        List<String> out = new ArrayList<>(names);

        Collections.sort(out);

        return out;
    }

    /** The file, as text. */
    private String read(File file) throws Exception
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
