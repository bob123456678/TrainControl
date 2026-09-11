package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * The repair that runs after a killed test JVM does what its javadoc says.
 *
 * REG9-B2. On 2026-09-11 two killed JVMs left the machine-global layout preference naming a folder
 * under `%TEMP%` - and that is the key the APPLICATION reads, so Adam's own window would have opened a
 * test fixture as though it were his railway. `6fe9ec38` added a marker file, a repair that runs at the
 * next sandbox open, and a shutdown hook. Ninety-seven test classes depend on that machinery, and
 * nothing asserted any of it: `grep -rn "LayoutSandbox" test/` finds 97 files and every one of them is
 * a *user*.
 *
 * **Nothing here touches the real preference.** `repairALeakedPreference` and `putBack` take the
 * preference node as a parameter, so this hands them a node of their own under a name nothing else
 * uses. That is deliberate and it is the whole reason this test can exist: the reviewer who found this
 * gap declined to mutate the machinery precisely because getting it wrong writes the key that damaged
 * the railway in the first place.
 *
 * The marker file is shared - one fixed path in the temp directory - so whatever is there when this
 * starts is saved and put back at the end. If a marker is sitting there, a repair is PENDING, and
 * deleting it would destroy the value it is holding on the operator's behalf.
 *
 * MUTATION: remove the `if (repairMarker().exists()) return;` from `rememberForRepair` and the
 * second claim fails - a sandbox opening inside a killed one's mess records the sandbox path as the
 * thing to restore; drop the `now.contains("tc-sandbox-layout")` test and the last one fails.
 *
 * @author Adam
 */
public class testTheSandboxPutsThePreferenceBack
{
    /** A node of this test's own, so the real preference is never read or written. */
    private static Preferences scratch;

    /** Whatever marker was there before, so a pending repair is not thrown away. */
    private static byte[] markerWas;

    private static boolean thereWasAMarker;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        scratch = Preferences.userRoot().node("traincontrol-test-sandbox-repair");

        File marker = markerFile();

        thereWasAMarker = marker.exists();

        if (thereWasAMarker)
        {
            markerWas = Files.readAllBytes(marker.toPath());

            Files.delete(marker.toPath());
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        File marker = markerFile();

        Files.deleteIfExists(marker.toPath());

        if (thereWasAMarker) Files.write(marker.toPath(), markerWas);

        if (scratch != null)
        {
            scratch.removeNode();

            scratch.flush();
        }
    }

    /**
     * The marker records what the preference was, and it is written BEFORE the preference changes.
     *
     * The order is the whole point: a kill between the two lines has to leave a marker that says the
     * truth rather than one that does not exist.
     */
    @Test
    public void testTheMarkerRecordsWhatWasThere() throws Exception
    {
        remember("C:\\\\Users\\\\adamo\\\\his-real-railway");

        assertTrue(markerFile().exists(), "no marker was written, so a killed JVM leaves nothing to "
            + "repair from and the preference stays pointing at a fixture");

        assertEquals(new String(Files.readAllBytes(markerFile().toPath()), StandardCharsets.UTF_8),
            "C:\\\\Users\\\\adamo\\\\his-real-railway",
            "the marker does not hold the value the preference had");

        Files.deleteIfExists(markerFile().toPath());
    }

    /**
     * A second sandbox does not overwrite the first one's marker.
     *
     * The first marker holds the OPERATOR's value. A second sandbox opening inside a killed one's mess
     * would otherwise write down the leaked sandbox path as the thing to restore - and the repair would
     * then faithfully restore the fixture.
     */
    @Test
    public void testASecondSandboxDoesNotOverwriteTheFirstMarker() throws Exception
    {
        remember("C:\\\\Users\\\\adamo\\\\his-real-railway");

        remember(System.getProperty("java.io.tmpdir") + "tc-sandbox-layout-12345");

        assertEquals(new String(Files.readAllBytes(markerFile().toPath()), StandardCharsets.UTF_8),
            "C:\\\\Users\\\\adamo\\\\his-real-railway",
            "the second sandbox overwrote the marker with its own path, so the repair would put the "
            + "preference back to a temp folder - which is the state this machinery exists to end");

        Files.deleteIfExists(markerFile().toPath());
    }

    /**
     * A leaked preference is put back, and the marker goes with it.
     */
    @Test
    public void testALeakedPreferenceIsPutBack() throws Exception
    {
        remember("C:\\\\Users\\\\adamo\\\\his-real-railway");

        // The state a killed JVM leaves: the preference naming a sandbox, and a marker beside it.
        scratch.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF,
            System.getProperty("java.io.tmpdir") + "tc-sandbox-layout-99999");

        repair();

        assertEquals(scratch.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""),
            "C:\\\\Users\\\\adamo\\\\his-real-railway",
            "the preference was left naming a sandbox. That is the key the APPLICATION reads, so the "
            + "operator's own window opens a test fixture as though it were his railway");

        Files.deleteIfExists(markerFile().toPath());
    }

    /**
     * And a value somebody set deliberately is left alone.
     *
     * The marker is only a diagnosis while the preference still points at a sandbox. If it names
     * something else, the operator has chosen it since, and theirs wins - the marker is simply stale.
     *
     * This is the claim that stops the repair being a machine that overwrites a real setting with an
     * old one every time a stale marker is lying about.
     */
    @Test
    public void testADeliberateValueIsNotOverwritten() throws Exception
    {
        remember("C:\\\\Users\\\\adamo\\\\an-older-railway");

        scratch.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "C:\\\\Users\\\\adamo\\\\chosen-since");

        repair();

        assertEquals(scratch.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""),
            "C:\\\\Users\\\\adamo\\\\chosen-since",
            "the repair overwrote a value that does not name a sandbox at all. A stale marker would "
            + "then undo the operator's own choice at the start of every test run");

        Files.deleteIfExists(markerFile().toPath());
    }

    /**
     * `LayoutSandbox.rememberForRepair`, which is private because nothing but the sandbox should call it.
     */
    private static void remember(String was) throws Exception
    {
        java.lang.reflect.Method write =
            support.LayoutSandbox.class.getDeclaredMethod("rememberForRepair", String.class);

        write.setAccessible(true);

        write.invoke(null, was);
    }

    /**
     * `LayoutSandbox.repairALeakedPreference`, against this test's own node.
     */
    private static void repair() throws Exception
    {
        java.lang.reflect.Method fix = support.LayoutSandbox.class
            .getDeclaredMethod("repairALeakedPreference", Preferences.class);

        fix.setAccessible(true);

        fix.invoke(null, scratch);
    }

    /**
     * The one marker path, asked of the class that owns it rather than spelled again here - two
     * spellings of a path is how a test comes to clean up something else.
     */
    private static File markerFile() throws Exception
    {
        java.lang.reflect.Method where =
            support.LayoutSandbox.class.getDeclaredMethod("repairMarker");

        where.setAccessible(true);

        return (File) where.invoke(null);
    }
}
