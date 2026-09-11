package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * A window of this application looks the same whoever built it.
 *
 * Adam, 2026-09-11: **"Some tests start with the small font in the UI menu bar (suggesting the UI isn't
 * initialized the same way as in the production app), while others have the same font as the production
 * app. Can you make this be consistent?"**
 *
 * He was right about the cause. The look and feel is installed by `MarklinControlStation.init` and by
 * `TrainControlUI`'s constructor, so a test that calls either gets the operator's fonts - and six test
 * classes build a window WITHOUT calling either. Those were drawn in Metal: different font, different
 * insets, different button padding. `ui.testEveryLanguageFits`, whose whole job is measuring whether
 * translated text fits its controls, was one of them.
 *
 * **The fix is in the windows, not in the tests.** Every top-level window of this application now asks
 * for the look and feel before it builds itself; in the running program it is already installed and the
 * call returns immediately. Putting it in each test instead would be a rule the next test to build a
 * window would have to remember, and this suite has watched that fail often enough to stop writing
 * rules that way.
 *
 * MUTATION: take the `installLookAndFeel()` call out of `RouteEditorFrame` and the first test fails,
 * naming the look and feel it got instead; take it out of any window class and the second names it.
 *
 * @author Adam
 */
public class testEveryWindowWearsTheApplicationsLook
{
    private static final String GUI = "src/org/traincontrol/gui";

    /**
     * A window built cold - as a test builds it - wears the application's look.
     *
     * **This can only be asked once per JVM**, which is why it is the only behavioural claim here and
     * why it asserts its own precondition: once anything has installed the look and feel, every later
     * window gets it for free and the question stops meaning anything. The battery runs one JVM per
     * class, so "cold" is real here as long as nothing in this class calls `init` first - and nothing
     * does.
     *
     * `RouteEditorFrame` because it is the window four test classes really do build this way.
     */
    @Test
    public void testAWindowBuiltColdStillWearsTheApplicationsLook() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("a look and feel needs a display");
        }

        assertFalse(javax.swing.UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatLightLaf,
            "the application's look and feel was already installed before this test built anything, so "
            + "the question it asks - does a window install it ITSELF - cannot be answered in this JVM. "
            + "Something in this class now calls init, or the runner installs it");

        // WHAT THE MENUS LOOKED LIKE BEFORE, to compare against afterwards.
        java.awt.Font menuFontBefore = javax.swing.UIManager.getFont("MenuBar.font");

        final org.traincontrol.gui.RouteEditorFrame[] built =
            new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            built[0] = new org.traincontrol.gui.RouteEditorFrame(null, null));

        try
        {
            assertTrue(javax.swing.UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatLightLaf,
                "a window of this application was built and the look and feel is still "
                + javax.swing.UIManager.getLookAndFeel().getName() + ". Every window a test builds "
                + "cold is then drawn in a different typeface from the one the operator sees - which "
                + "is what Adam reported, and what makes a test that MEASURES text fitting its "
                + "controls measure the wrong thing");

            // AND THE MENU FONT MOVED, which is the thing Adam actually sees.
            //
            // The look and feel is the cause and the font is the symptom; both are asserted because a
            // future change could install the look and feel without its fonts reaching the menus, and
            // the report was about a menu bar.
            //
            // NOT "menus are bigger than labels": `MENU_FONT_STEP` is 0, so this application's menus
            // are deliberately the same size as everything else. The first version of this test
            // asserted that step and failed - a good failure, since it was the test's assumption that
            // was wrong and not the code.
            java.awt.Font menusNow = javax.swing.UIManager.getFont("MenuBar.font");

            assertTrue(menusNow != null, "the installed look and feel has no menu font at all");

            assertFalse(menusNow.equals(menuFontBefore),
                "the menu font is the same one that was in force before this window was built ("
                + menuFontBefore + "), which was the default look and feel's rather than the "
                + "application's. This is the difference Adam reported: a test window whose menu bar "
                + "does not match the production app's");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> built[0].dispose());
        }
    }

    /**
     * And every window class asks, so a new one cannot bring the inconsistency back.
     *
     * Discovered rather than declared: a list of window classes written here would go stale the day
     * somebody adds one, and the class that is missing from the list is exactly the class that would
     * have the bug.
     *
     * A class satisfies this by asking for itself, or by extending a window class that asks - which is
     * how `LayoutEditor` is covered by `PositionAwareJFrame`.
     */
    @Test
    public void testEveryWindowClassAsksForIt() throws Exception
    {
        Map<String, String> windows = new LinkedHashMap<>();

        File[] files = new File(GUI).listFiles();

        assertTrue(files != null && files.length > 0, "cannot read " + GUI);

        for (File f : files)
        {
            if (!f.getName().endsWith(".java")) continue;

            String source = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

            if (!isAWindow(source)) continue;

            windows.put(f.getName(), source);
        }

        assertTrue(windows.size() >= 7,
            "only " + windows.size() + " window classes were found under " + GUI + ", which is fewer "
            + "than there were when this was written - the pattern that finds them has gone stale and "
            + "is checking less than it thinks");

        List<String> bare = new ArrayList<>();

        for (Map.Entry<String, String> window : windows.entrySet())
        {
            if (window.getValue().contains("installLookAndFeel()")) continue;

            // Or it inherits the call from a window class in this package that does ask.
            if (inheritsTheAsk(window.getValue(), windows)) continue;

            bare.add(window.getKey());
        }

        assertEquals(bare.toString(), "[]",
            "these windows do not install the application's look and feel, so one built cold - which "
            + "is what a test does - is drawn in Metal and is not the window the operator sees "
            + "(Adam, 2026-09-11): " + bare);
    }

    /**
     * And a TEST that stands up a bare Swing window installs it too.
     *
     * Adam, 2026-09-11, after the product-side fix: *"I still see tests popping up with the small font
     * in the menu bar, FYI."* He was right, and the fix above could not have covered it: every window
     * the APPLICATION owns now asks for the look and feel in its own constructor, and a `JFrame`, a
     * `JDialog`, a `JWindow` or a `JOptionPane` built by a test is nobody's window but the test's.
     *
     * Four classes were doing that. This is the rule that stops a fifth, and it is the same shape as
     * the sandbox rule in `testSwitchingToACentralStationLayout`: a test that stands something up has
     * to put the machine in the state the application would be in first.
     *
     * **Only BARE windows are asked about.** A test that builds a `TrainControlUI`, a
     * `RouteEditorFrame` or any other class under `gui/` is already covered by the claim above, and
     * requiring the call there as well would be a rule that is satisfied twice and understood once.
     *
     * MUTATION: take the install out of any of the four and this names the file.
     */
    @Test
    public void testATestThatBuildsABareWindowInstallsItToo() throws Exception
    {
        List<String> bare = new ArrayList<>();

        for (File source : everyTestSource(new File("test")))
        {
            String code = withoutComments(new String(Files.readAllBytes(source.toPath()),
                StandardCharsets.UTF_8));

            if (!standsUpABareWindow(code)) continue;

            // Calling init is the application's own door and installs it; so is asking directly.
            if (code.contains("installLookAndFeel") || code.contains("MarklinControlStation.init(")
                || code.contains("= init(")) continue;

            bare.add(source.getName());
        }

        assertEquals(bare.toString(), "[]",
            "these tests stand up a Swing window of their own without installing the application's "
            + "look and feel, so what pops up is drawn in Metal - a different font and different "
            + "insets from the window the operator sees. Add "
            + "`org.traincontrol.gui.TrainControlUI.installLookAndFeel()` before the window is built "
            + "(Adam, 2026-09-11): " + bare);
    }

    /**
     * A source with its comments taken out.
     *
     * A comment mentioning `new JFrame(` is not a test building one - and this class's own javadoc
     * above says exactly that phrase, so without this it reports itself.
     *
     * @param source the file's text
     * @return the code alone
     */
    private static String withoutComments(String source)
    {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\n]*", "");
    }

    /**
     * Whether this source builds a Swing window that belongs to no application class.
     */
    private static boolean standsUpABareWindow(String code)
    {
        return code.contains("new JFrame(") || code.contains("new javax.swing.JFrame(")
            || code.contains("new JDialog(") || code.contains("new javax.swing.JDialog(")
            || code.contains("new JWindow(") || code.contains("new javax.swing.JWindow(")
            || code.contains("JOptionPane.show");
    }

    /**
     * Every test source under a folder.
     */
    private static List<File> everyTestSource(File folder)
    {
        List<File> out = new ArrayList<>();

        File[] here = folder.listFiles();

        if (here == null) return out;

        for (File f : here)
        {
            if (f.isDirectory()) out.addAll(everyTestSource(f));

            else if (f.getName().endsWith(".java")) out.add(f);
        }

        return out;
    }

    /**
     * Whether this source declares a top-level window.
     */
    private static boolean isAWindow(String source)
    {
        return source.contains("extends javax.swing.JFrame") || source.contains("extends JFrame")
            || source.contains("extends javax.swing.JDialog") || source.contains("extends JDialog")
            || source.contains("extends PositionAwareJFrame");
    }

    /**
     * Whether this class extends another window in this package that asks for the look and feel.
     */
    private static boolean inheritsTheAsk(String source, Map<String, String> windows)
    {
        for (Map.Entry<String, String> other : windows.entrySet())
        {
            String name = other.getKey().replace(".java", "");

            if (source.contains("extends " + name) && other.getValue().contains("installLookAndFeel()"))
            {
                return true;
            }
        }

        return false;
    }
}
