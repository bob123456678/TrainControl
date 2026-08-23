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

        String xml = new String(Files.readAllBytes(build.toPath()), StandardCharsets.UTF_8);

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
}
