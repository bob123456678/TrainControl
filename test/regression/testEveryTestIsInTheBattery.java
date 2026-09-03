package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Every test class is in `ant test`, and this is what notices when one is not.
 *
 * `build.xml` names each test class on its own line, because `NetworkProxy` binds a fixed UDP port and
 * only one control station can exist per JVM - so the battery runs one class per JVM and there is no
 * wildcard that could do it. The list is therefore kept by hand, and a hand-kept list of everything is
 * a list that falls behind.
 *
 * It has fallen behind twice. DD-A2 found **thirty-five** classes missing, among them the matrix test
 * written specifically to catch this project's commonest bug class. That was closed on 2026-08-22 by
 * adding them - and by 2026-08-22 evening six more were missing, one of them added in the very commit
 * that closed it, and one of them the guard for the four defects around the editor switch. A green
 * `ant test` was reporting on a suite that did not include the tests written that day.
 *
 * So the fix for DD-A2 was not adding thirty-four lines. It is this: the list is still kept by hand,
 * but forgetting it now fails.
 *
 * **`testAutoDetect` is the one deliberate omission.** It probes the network for a real Central Station
 * and cannot pass without one, so it is excluded on purpose and excluded here too. Anything else that
 * needs to be left out should be added to the exclusion below WITH ITS REASON, so the next reader can
 * tell a decision from an oversight - which is the whole difference this test exists to preserve.
 *
 * @author Adam
 */
public class testEveryTestIsInTheBattery
{
    /**
     * Classes deliberately not in `ant test`, and why.
     */
    private static final String[][] DELIBERATELY_OUT = {
        {"testAutoDetect",
         "probes the network for a real Central Station and cannot pass without one"},

    };

    @Test
    public void testTheBatteryRunsEveryTestClass() throws Exception
    {
        File build = new File("build.xml");

        assertTrue(build.exists(), "cannot find " + build.getAbsolutePath()
            + " - this test reads the build file, so it has to run from the project root");

        // WITHOUT its comments (TA-B1).
        //
        // The check below is a substring search for a `test-one-class` line, and a line inside
        // `<!-- -->` is still a substring - so commenting a class out satisfied this guard while
        // `ant test` stopped running it. That is not a hypothetical edit. It is what somebody does to
        // skip a slow class while debugging, and forgetting to put it back is exactly how thirty-five
        // classes came to be silently out of the battery (DD-A2), which is the state this test exists
        // to stop returning to. Demonstrated by mutation: commented out, 3 of 3 green; deleted
        // outright, correctly red.
        String xml = withoutXmlComments(
            new String(Files.readAllBytes(build.toPath()), StandardCharsets.UTF_8));

        List<String> missing = new ArrayList<>();

        for (File folder : new File("test").listFiles())
        {
            if (!folder.isDirectory()) continue;

            File[] files = folder.listFiles();

            if (files == null) continue;

            for (File file : files)
            {
                String name = file.getName();

                if (!name.endsWith(".java")) continue;

                name = name.substring(0, name.length() - 5);

                // A file with no @Test is a fixture, not a test - see test/support
                if (!new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    .contains("@Test")) continue;

                if (excused(name)) continue;

                if (!xml.contains("<test-one-class class=\"" + name + "\"/>")) missing.add(name);
            }
        }

        assertEquals(missing.toString(), "[]",
            missing.size() + " test class(es) carry @Test and are not in build.xml, so `ant test` "
            + "does not run them and a green battery says nothing about what they cover. Add a "
            + "<test-one-class class=\"...\"/> line for each, or - if one is genuinely meant to be "
            + "left out - add it to DELIBERATELY_OUT in this file with the reason.");
    }

    /**
     * And the exclusion list does not quietly grow.
     *
     * A test like the one above is defeated by adding names to its own exemption list, which is easier
     * than fixing what it found and leaves no trace. One entry is the number there is a reason for.
     */
    @Test
    public void testTheExclusionListIsStillOneEntry()
    {
        // BACK TO ONE, on Adam's instruction of 2026-09-01: "ok, so that test should then be red."
        //
        // testTheParkingBerthsGetTheirTrainsBack was excused because a permanently red battery costs
        // more than the test earns.  That was the wrong trade and he said so: Return Home does not
        // work for this arrangement, nothing moves when it is pressed, and a test for something that
        // does not work belongs in the battery being red rather than in a table being quiet.  The
        // outcome improved twice today - IMPOSSIBLE to NO_PLAN_FOUND, and a false claim removed - and
        // improving is not the same as working.
        //
        // The one that is left is testAutoDetect, which needs hardware that is not here.  That is a
        // different kind of reason from the one just removed: a test nothing on this machine could
        // ever satisfy, rather than a test whose subject does not work yet.
        assertEquals(DELIBERATELY_OUT.length, 1,
            "something has been added to the list of tests `ant test` deliberately skips. That may be "
            + "right, but it is a decision worth a second look: the reason must be in the table, and "
            + "this assertion updated on purpose rather than to make a failure go away.");
    }

    /**
     * And every name on it is a real file, so a rename cannot leave a silent exemption behind.
     */
    @Test
    public void testTheExcusedClassesExist()
    {
        for (String[] each : DELIBERATELY_OUT)
        {
            assertTrue(exists(each[0]),
                "no test class called " + each[0] + " - it is excused from the battery by name in "
                + "this file, so a rename would leave the exemption pointing at nothing and the real "
                + "class silently unchecked");
        }
    }

    /**
     * Every test-SHAPED method carries an annotation, not just the class (TST-C2).
     *
     * The check above answers "is this FILE in the battery", by whether `@Test` appears in it
     * anywhere - which is also true of a file where one method quietly lost its `@Test` and four
     * others kept theirs. That happened: five methods in one class carried no `@Test` at all and had
     * never run, and this test's own sibling could not see it, because the class itself was still
     * correctly listed in `build.xml`.
     *
     * Deliberately permissive about WHICH annotation: `@AfterClass`/`@BeforeClass`/`@BeforeMethod`/
     * etc. on a method named like a test is a lifecycle hook that happens to share the naming
     * convention (`testTheGoldenLayoutHoldsTogether`'s golden-write check is exactly this, run via
     * `@AfterClass`), and flagging those would be noise this test would train someone to ignore. What
     * must never happen is a `public void testX()` with no TestNG annotation above it at all - that
     * is a method TestNG will never call, whatever it is named.
     *
     * MUTATION: removing the annotation from any `public void testX(...)` method, leaving the method
     * itself in place, fails this and names the file and method.
     */
    @Test
    public void testEveryTestShapedMethodCarriesAnAnnotation() throws Exception
    {
        java.util.regex.Pattern method = java.util.regex.Pattern.compile(
            "^\\s*public\\s+void\\s+(test\\w+)\\s*\\(");

        List<String> missing = new ArrayList<>();

        int methodsChecked = 0;

        for (File folder : new File("test").listFiles())
        {
            if (!folder.isDirectory()) continue;

            File[] files = folder.listFiles();

            if (files == null) continue;

            for (File file : files)
            {
                if (!file.getName().endsWith(".java")) continue;

                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

                for (int i = 0; i < lines.size(); i++)
                {
                    java.util.regex.Matcher m = method.matcher(lines.get(i));

                    if (!m.find()) continue;

                    methodsChecked++;

                    boolean annotated = false;

                    // Walk upward through blank lines, comments and stacked annotations. Anything
                    // else - a real statement, a closing brace - means there was never an annotation.
                    for (int j = i - 1; j >= 0; j--)
                    {
                        String line = lines.get(j).trim();

                        if (line.isEmpty() || line.startsWith("//") || line.startsWith("*")
                            || line.startsWith("/**") || line.endsWith("*/"))
                        {
                            continue;
                        }

                        if (line.startsWith("@"))
                        {
                            // A TESTNG ONE, NOT ANY ONE (TSX-C19).
                            //
                            // The javadoc above argues for being permissive about WHICH TestNG
                            // annotation - a lifecycle hook named like a test is fine - and this
                            // accepted anything beginning with @.  `@Override`, `@Deprecated` and
                            // `@SuppressWarnings("unchecked")` all marked a test-shaped method as
                            // annotated, and the last of those is exactly what somebody adds above a
                            // method while working on it.  A method whose @Test had been lost and a
                            // @SuppressWarnings gained would have passed this guard, which is the
                            // five-methods-never-run failure it exists for, one step subtler.
                            //
                            // Anything further up the list of annotations still counts: a method may
                            // carry @Override above @Test, and the loop keeps reading until it meets
                            // something that is not an annotation.
                            if (isTestNGAnnotation(line)) annotated = true;

                            continue;
                        }

                        break;
                    }

                    if (!annotated)
                    {
                        missing.add(file.getName() + ":" + m.group(1));
                    }
                }
            }
        }

        // A pattern that stopped matching - a reformat, a renamed convention - would pass having
        // examined nothing. Over a thousand methods are named like a test today; 500 is a floor well
        // under that, not a pin on the count.
        assertTrue(methodsChecked >= 500,
            "only " + methodsChecked + " methods named like a test (public void test...) were found "
            + "under test/ - fewer than the suite is known to have, so this scan ran from the wrong "
            + "directory or the pattern it looks for has gone stale");

        assertEquals(missing, new ArrayList<String>(),
            "these methods are shaped like a test - public void test...(...) - but carry no "
            + "annotation at all, so TestNG never calls them and a green run says nothing about them: "
            + missing);
    }

    private boolean excused(String name)
    {
        for (String[] each : DELIBERATELY_OUT)
        {
            if (each[0].equals(name)) return true;
        }

        return false;
    }

    private boolean exists(String name)
    {
        for (String folder : new String[] {"core", "ui", "regression", "support"})
        {
            if (new File("test/" + folder + "/" + name + ".java").exists()) return true;
        }

        return false;
    }
    /**
     * The build file with everything between comment delimiters removed.
     *
     * Deliberately crude: it blanks spans rather than parsing XML. A comment delimiter inside an
     * attribute value would confuse it, and `build.xml` has none - a real parser here would be a
     * dependency and a second thing that can be wrong about a file this test exists to read
     * literally.
     *
     * An unterminated comment swallows the rest of the file, which is what ant would do with it too,
     * so a class after one is correctly reported as missing.
     *
     * @param xml the file's text
     * @return it, with comment spans removed
     */
    private static String withoutXmlComments(String xml)
    {
        StringBuilder out = new StringBuilder();

        int at = 0;

        while (true)
        {
            int opens = xml.indexOf("<!--", at);

            if (opens < 0)
            {
                return out.append(xml.substring(at)).toString();
            }

            out.append(xml, at, opens);

            int closes = xml.indexOf("-->", opens);

            if (closes < 0) return out.toString();

            at = closes + 3;
        }
    }



    /**
     * Whether an annotation line is one TestNG acts on.
     *
     * By name rather than by import, because this reads source rather than classes. The list is
     * every annotation in `org.testng.annotations` that can sit on a method; a fully-qualified one
     * (`@org.testng.annotations.Test`) is matched by the same suffix test.
     *
     * @param line the trimmed source line, beginning with `@`
     * @return true if TestNG would call the method beneath it
     */
    private static boolean isTestNGAnnotation(String line)
    {
        String name = line.substring(1);

        int bracket = name.indexOf('(');

        if (bracket >= 0) name = name.substring(0, bracket);

        name = name.trim();

        int dot = name.lastIndexOf('.');

        if (dot >= 0) name = name.substring(dot + 1);

        for (String known : TESTNG_METHOD_ANNOTATIONS)
        {
            if (known.equals(name)) return true;
        }

        return false;
    }

    /** Every `org.testng.annotations` name that can sit on a method. */
    private static final String[] TESTNG_METHOD_ANNOTATIONS = {
        "Test", "BeforeSuite", "AfterSuite", "BeforeTest", "AfterTest", "BeforeGroups",
        "AfterGroups", "BeforeClass", "AfterClass", "BeforeMethod", "AfterMethod",
        "DataProvider", "Factory", "Listeners", "Parameters",
    };
}
