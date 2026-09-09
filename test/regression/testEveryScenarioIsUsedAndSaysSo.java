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
 * **Two failures this guards against, and both have already happened here in other forms.**
 *
 * An ORPHAN FIXTURE is a checked-in railway nothing runs against.  `test/` already has seven ad-hoc
 * layout folders at its root, named for who made them rather than for what shape they are, and
 * `test/README.md`'s own survey (MON-C20) found that four of them are the same five pages counted four
 * times.  Nobody set out to do that; each was added for one test and outlived it.  A fixture that no
 * test opens is a file that gets maintained, moved, renamed and argued about for nothing.
 *
 * A STALE "USED BY" LIST is the same defect as the review index that read `open` on eleven findings
 * whose bodies said `fixed` (`audit-the-bodies-not-the-index`).  A scenario's README exists to tell the
 * next author what may be relied on and what may not; the list of who relies on it is the part that
 * goes out of date first, because it changes when a DIFFERENT file changes.  So it is not maintained by
 * hand and checked by eye - it is compared, here, with what the test sources actually say.
 *
 * **The link is a name in the source, not a call.**  A test that merely writes the scenario's name in a
 * comment counts as a user, deliberately: the question being asked is "does anything in the suite refer
 * to this railway", and a class that names it in prose is a class whose author will want to know when it
 * is changed.
 *
 * @author Adam
 */
public class testEveryScenarioIsUsedAndSaysSo
{
    /**
     * The heading a scenario's README lists its users under.
     */
    private static final String USED_BY = "## Used by";

    /**
     * The heading that says which properties a test must set for itself.
     *
     * Required of every README because it is the half of Adam's design that a reader will otherwise
     * assume the other way round: *"hand-authored for topology, lengths in code."*  A scenario that
     * quietly baked a length in would be a scenario two tests could disagree about without either of
     * them saying so.
     */
    private static final String NOT_INVARIANTS = "## Not invariants";

    /**
     * Every scenario is opened by at least one test class.
     */
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

    /**
     * Every scenario has a README, and it carries the two sections that make it useful.
     */
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
     * And the "used by" list is the list of classes that actually name the scenario.
     *
     * Compared both ways.  A name in the README that no source mentions is a link that has rotted - the
     * class was renamed, moved or deleted - and a source that names the scenario without appearing in
     * the README is a user the next person to edit that fixture will not know about.  The second is the
     * more expensive one, so neither is allowed to be the "harmless" direction.
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

    /**
     * Which test classes name each scenario.
     *
     * @param scenarios the scenario names
     * @return scenario name to the fully qualified test classes that mention it
     */
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

            // NOT `support`, WHICH HOLDS NO TESTS. `test/README.md`: "Not tests. Fixtures the others
            // use." `Scenario`'s own javadoc shows a scenario being opened, which is exactly the
            // documentation a reader wants there - and counting it as a user would make that scenario
            // permanently non-orphan whether or not any test ever ran against it, which is the one
            // thing the guard above exists to catch.
            if (folder.getName().equals("support")) continue;

            File[] files = folder.listFiles();

            if (files == null) continue;

            for (File file : files)
            {
                if (!file.getName().endsWith(".java")) continue;

                // NOT THIS FILE. It reads every scenario name off the filesystem and would otherwise
                // have to be listed in every README as a user of every scenario - which would make the
                // list above say nothing, since every entry would be there whatever happened.
                if (file.getName().equals(getClass().getSimpleName() + ".java")) continue;

                String source = read(file);

                String qualified = folder.getName() + "."
                    + file.getName().substring(0, file.getName().length() - 5);

                for (String scenario : scenarios)
                {
                    // QUOTED, so that "curve-into-platform" in a sentence is a mention and a folder
                    // name that happens to be a substring of another word is not.
                    if (source.contains("\"" + scenario + "\"")) out.get(scenario).add(qualified);
                }
            }
        }

        return out;
    }

    /**
     * The class names a README lists under its "used by" heading.
     *
     * One per bullet, in backticks: `- `core.testSomething``. Anything else under the heading is prose
     * and is ignored, so a note about why a class uses the scenario does not read as a class name.
     *
     * @param text the README
     * @return the names listed
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

    /**
     * @return everything in the first set that is not in the second
     */
    private Set<String> missingFrom(Set<String> these, Set<String> those)
    {
        Set<String> out = new LinkedHashSet<>(these);

        out.removeAll(those);

        return out;
    }

    /**
     * @return the names in a stable order, so a failure message reads the same twice
     */
    private List<String> sorted(Set<String> names)
    {
        List<String> out = new ArrayList<>(names);

        Collections.sort(out);

        return out;
    }

    /**
     * @return the file, as text
     */
    private String read(File file) throws Exception
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
