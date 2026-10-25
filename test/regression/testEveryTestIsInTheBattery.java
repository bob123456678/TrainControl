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
 * `build.xml` names each test class on its own line, because `NetworkProxy` binds a fixed UDP port and only one control
 * station can exist per JVM, so the battery runs one class per JVM with no wildcard.  A hand-kept list of everything
 * falls behind (a review once found thirty-five classes missing); it is still kept by hand, but forgetting it now
 * fails.
 *
 * `testAutoDetect` is the one deliberate omission: it probes the network for a real Central Station.  Anything else
 * left out is added to the exclusion below WITH ITS REASON, so the next reader can tell a decision from an oversight.
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

        // WITHOUT its comments: the check below is a substring search for a `test-one-class` line, and a line inside
        // `<!-- -->` is still a substring, so commenting a class out (to skip a slow class while debugging) satisfied
        // this guard while `ant test` stopped running it.
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
     * And the exclusion list does not quietly grow: adding names to its own exemption list is easier than fixing what
     * the test found.  One entry is the number there is a reason for.
     */
    @Test
    public void testTheExclusionListIsStillOneEntry()
    {
        // BACK TO ONE, on Adam's instruction: "ok, so that test should then be red."  A test for something that does
        // not work (Return Home for the parking-berth arrangement) belongs in the battery being red rather than
        // excused.  The one left is testAutoDetect, which needs hardware that is not here: a test nothing on this
        // machine could satisfy.
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
     * NO TEST BUILDS ITS PAGES WITHOUT WIRING THEM.
     *
     * `CS2File.parseLayout` builds the components, but what attaches each to a live `Accessory` is
     * `MarklinControlStation.wireComponents`, in a loop after the parse.  A test that calls `parseLayout` directly
     * skips it, every switch and signal has a null accessory, and `TileGraph` refuses to trace through, so the railway
     * falls to pieces (measured on the sample layout):
     *
     * | | re-parsed | wired |
     * |---|---|---|
     * | switches and signals with an accessory | 0 of 222 | 221 of 222 |
     * | reduced edges | 18 | 128 |
     * | built points / edges | 59 / 5 | 96 / 149 |
     * | isolated points | 51 | 3 |
     *
     * Twenty test classes did it and passed, since an assertion about a square with no edges is usually an assertion
     * about null.  `support.LayoutSandbox.wired(model, parser)` is the recipe.
     *
     * MUTATION: writing `parser.parseLayout(new LinkedList<MarklinAccessory>())` in any test outside the excused list
     * fails this.
     *
     * @throws Exception on a failure to read the tests
     */
    @Test
    public void testNoTestParsesALayoutWithoutWiringIt() throws Exception
    {
        // THE GUARD SEARCHES FOR A CALL AND CANNOT SEE CONTEXT, so three files are excused by name.
        //
        // `testParseCS2Layout` is testing the PARSER: an unwired parse is the thing under test there.
        //
        // `LayoutSandbox` is the CURE: the wiring helper contains the call by definition, and its javadoc quotes the
        // wrong form to explain it.  This file quotes it too, in the search below.
        //
        // `testTimetableOnDerivedGraph` spells the call with `java.util.LinkedList` and wires the pages by hand
        // straight after (`wireAccessories(pages)`, with the reason written beside it).
        java.util.List<String> excused = java.util.Arrays.asList(
            "testParseCS2Layout.java", "LayoutSandbox.java", "testEveryTestIsInTheBattery.java",
            "testTimetableOnDerivedGraph.java");

        java.util.List<String> unwired = new java.util.ArrayList<String>();

        for (File folder : new File("test").listFiles())
        {
            if (!folder.isDirectory()) continue;

            File[] files = folder.listFiles();

            if (files == null) continue;

            for (File file : files)
            {
                if (!file.getName().endsWith(".java")) continue;

                if (excused.contains(file.getName())) continue;

                String source = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);

                // The call, not the words: LayoutSandbox.wired contains this text itself, inside the
                // helper that does the wiring, and naming it here would report the cure as the disease.
                if (parsesWithoutAccessories(source))
                {
                    unwired.add(folder.getName() + "/" + file.getName());
                }
            }
        }

        assertTrue(unwired.isEmpty(),
            "these tests parse a layout and never wire its tiles to their accessories, so they run against a railway with almost no edges in it and pass by asserting about null. Use support.LayoutSandbox.wired(model, parser): " + unwired);
    }
    /** A parse handed an empty list of accessories, spelled any way a test spells it: plain or qualified, any type argument. */
    private static final java.util.regex.Pattern UNWIRED_PARSE = java.util.regex.Pattern.compile(
        "\\.parseLayout\\(\\s*new\\s+(?:java\\.util\\.)?LinkedList\\s*<[^>()]*>\\s*\\(\\s*\\)\\s*\\)");

    /**
     * Whether a source file parses a layout with an empty accessory list.
     */
    private static boolean parsesWithoutAccessories(String source)
    {
        return UNWIRED_PARSE.matcher(source).find();
    }

    /**
     * The search for an unwired parse sees the spellings tests use, and not a wired one: the guard once looked for two
     * exact spellings, so `new java.util.LinkedList<MarklinAccessory>()` went by it.  This drives the same search over
     * made-up lines, so it cannot go quiet unnoticed.
     *
     * MUTATION: put an unwired parse spelled `new java.util.LinkedList<MarklinAccessory>()` in a test outside the excused
     * list and `testNoTestParsesALayoutWithoutWiringIt` fails; narrow `UNWIRED_PARSE` to the plain spelling and this does.
     */
    @Test
    public void testTheUnwiredParseSearchSeesEverySpelling()
    {
        assertTrue(parsesWithoutAccessories("pages = parser.parseLayout(new LinkedList<MarklinAccessory>());"),
            "the plain spelling is no longer found");

        assertTrue(parsesWithoutAccessories("pages = parser.parseLayout(new LinkedList<>());"),
            "the diamond spelling is no longer found");

        assertTrue(parsesWithoutAccessories("pages = parser.parseLayout(new java.util.LinkedList<MarklinAccessory>());"),
            "the qualified spelling is no longer found - the one the guard used to miss");

        assertTrue(parsesWithoutAccessories("pages = parser.parseLayout(\n    new java.util.LinkedList<org.traincontrol.marklin.MarklinAccessory>()\n);"),
            "a parse split over lines, with a qualified argument type, is no longer found");

        assertFalse(parsesWithoutAccessories("pages = parser.parseLayout(accessories);"),
            "a parse handed a real accessory list is flagged as unwired");

        assertFalse(parsesWithoutAccessories("pages = support.LayoutSandbox.wired(model, parser);"),
            "the wiring helper is flagged as an unwired parse");
    }

    /**
     * Every test-SHAPED method carries an annotation, not just the class.
     *
     * The check above answers "is this FILE in the battery" by whether `@Test` appears anywhere, which is also true of
     * a file where one method quietly lost its `@Test` (five methods in one class never ran).  Permissive about WHICH
     * annotation: a lifecycle hook (`@AfterClass`) on a method named like a test is legitimate.  What must never happen
     * is a `public void testX()` with no TestNG annotation above it at all.
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
                            // A TESTNG ONE, NOT ANY ONE: `@Override`, `@Deprecated` and
                            // `@SuppressWarnings("unchecked")` all marked a test-shaped method as annotated, so a
                            // method that lost its @Test and gained a
                            // @SuppressWarnings passed.  Anything further up the list still counts: the loop keeps
                            // reading
                            // until it meets something that is not an annotation.
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

        // A pattern that stopped matching would pass having examined nothing; 500 is a floor well under the number of
        // test-shaped methods, not a pin on the count.
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
     * The build file with everything between comment delimiters removed.  Deliberately crude, blanking spans rather
     * than parsing XML (a real parser would be a second thing that can be wrong about a file this test reads
     * literally).  An unterminated comment swallows the rest of the file, as ant would, so a class after one is
     * correctly reported as missing.
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
     * Whether an annotation line is one TestNG acts on, by name rather than by import (this reads source): every
     * annotation in `org.testng.annotations` that can sit on a method, fully-qualified or not.
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
