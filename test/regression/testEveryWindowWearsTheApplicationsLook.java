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
 * A window of this application looks the same whoever built it, and is the same SIZE.
 *
 * Adam: "Some tests start with the small font in the UI menu bar (suggesting the UI isn't initialized the same way as
 * in the production app), while others have the same font as the production app. Can you make this be consistent?"
 *
 * The look and feel is installed by `MarklinControlStation.init` and by `TrainControlUI`'s constructor, so a window
 * built by a test without either was drawn in Metal.  The fix is in the windows: every top-level window asks for the
 * look and feel before it builds itself, at CLASS LOAD.  The claims measure the SIZE and the window's chrome, not only
 * whether the look and feel is FlatLaf, which was true in every failing case:
 *
 * <pre>
 *   a Swing component built, then the install ... scale 1.25, every font 15
 *   the look and feel queried, then the install .. scale 1.0,  every font 12
 *   installed in the constructor, built cold ..... MetalRootPaneUI, OS title bar,  166x77
 *   installed at class load, built cold ......... FlatRootPaneUI,  FlatLaf's bar,  166x78
 * </pre>
 *
 * The query in the second line is `installLookAndFeel`'s own first statement.  The scale is now stated from the
 * display.
 *
 * MUTATIONS: stop stating the scale and the first test fails on a scaled display, naming both numbers;
 * move any window class's install from its static block back into its constructor and the same test
 * names the root pane it got; take it out altogether and the second test names the file.
 *
 * @author Adam
 */
public class testEveryWindowWearsTheApplicationsLook
{
    private static final String GUI = "src/org/traincontrol/gui";

    /**
     * A window built cold, as a test builds it, wears the application's look.  Asked once per JVM (once anything has
     * installed the look and feel the question stops meaning anything; the battery runs one JVM per class), so it
     * asserts its own precondition.  `RouteEditorFrame` is the window four test classes build this way.
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

            // AND THE MENU FONT MOVED, the symptom of the report: asserted too, since the look and feel could be
            // installed without its fonts reaching the menus.  Not "menus are bigger than labels": `MENU_FONT_STEP` is
            // 0.
            java.awt.Font menusNow = javax.swing.UIManager.getFont("MenuBar.font");

            assertTrue(menusNow != null, "the installed look and feel has no menu font at all");

            assertFalse(menusNow.equals(menuFontBefore),
                "the menu font is the same one that was in force before this window was built ("
                + menuFontBefore + "), which was the default look and feel's rather than the "
                + "application's. This is the difference Adam reported: a test window whose menu bar "
                + "does not match the production app's");

            // AND THE SIZE IS THE DISPLAY'S: FlatLaf fixes one scale per process when it installs, and it was decided
            // by AWT state (a Swing component built first gave 1.25, a look and feel QUERY first gave 1.0).  Asserted
            // against the display rather than a number written here; the expected scale is asked of the program itself
            // (96 dpi baseline, the guard on a display that reports none, the UI_SCALE override).
            double expected;

            try
            {
                java.lang.reflect.Method states = org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod("uiScaleFor", int.class);

                states.setAccessible(true);

                expected = Double.parseDouble((String) states.invoke(null,
                    java.awt.Toolkit.getDefaultToolkit().getScreenResolution()));
            }
            catch (ReflectiveOperationException e)
            {
                throw new AssertionError("org.traincontrol.gui.TrainControlUI.uiScaleFor is not there to be asked: " + e, e);
            }

            assertEquals(com.formdev.flatlaf.util.UIScale.getUserScaleFactor(), (float) expected, 0.001f,
                "the interface is scaled " + com.formdev.flatlaf.util.UIScale.getUserScaleFactor()
                + " on a display that implies " + expected + ", so every font, inset and button padding "
                + "in this window is the wrong size - and the wrongness depends on what ran first, which "
                + "is why some test windows match the production app and others do not. "
                + "`TrainControlUI.installLookAndFeel` states this scale; if it has stopped being "
                + "stated, or UI_SCALE has been pinned to something else, this is where it shows");

            // ON A PLAIN DISPLAY THE CLAIM ABOVE IS TRUE EITHER WAY, so the symptom is asserted too: scaled fonts must
            // be bigger.
            if (expected > 1.0)
            {
                assertTrue(menusNow.getSize() > 12,
                    "the menu font is " + menusNow.getSize() + " point on a display scaled " + expected
                    + ", which is the unscaled size. This is the small menu bar Adam reported three "
                    + "times");
            }

            // AND THE WINDOW'S OWN CHROME, which a constructor cannot fix and a static block can: `JFrame`'s
            // constructor creates the root pane before any subclass constructor body runs, giving FlatLaf's components
            // with METAL'S root pane (an OS title bar with the menu bar below it).
            assertTrue(built[0].getRootPane().getUI() instanceof com.formdev.flatlaf.ui.FlatRootPaneUI,
                "this window's root pane is a "
                + built[0].getRootPane().getUI().getClass().getSimpleName()
                + ", so it has the operating system's title bar and not the application's. The look and "
                + "feel has to be installed before the window is CONSTRUCTED, which is why every window "
                + "class asks in a static initialiser rather than in its constructor");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> built[0].dispose());
        }
    }

    /**
     * And every window class asks, so a new one cannot bring the inconsistency back.  Discovered rather than declared,
     * since the class missing from a list is the one with the bug; a class satisfies this by asking for itself or by
     * extending a window class that asks (`LayoutEditor` via `PositionAwareJFrame`).
     */
    @Test
    public void testEveryWindowClassAsksForIt() throws Exception
    {
        Map<String, String> windows = new LinkedHashMap<>();

        // Walked, not listed: window classes live in gui's sub-packages too.
        List<File> files = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walked = Files.walk(new File(GUI).toPath()))
        {
            walked.filter(path -> path.toString().endsWith(".java")).forEach(path -> files.add(path.toFile()));
        }

        assertTrue(!files.isEmpty(), "cannot read " + GUI);

        for (File f : files)
        {
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
            if (asksAtClassLoad(window.getValue())) continue;

            // Or it inherits the block from a window class under gui that has one.
            if (inheritsTheAsk(window.getValue(), windows)) continue;

            bare.add(window.getKey());
        }

        assertEquals(bare.toString(), "[]",
            "these windows do not install the application's look and feel in a STATIC INITIALISER, so "
            + "one built cold - which is what a test does - gets the root pane of whatever look and "
            + "feel was installed when `JFrame`'s constructor ran, before theirs. A call in the "
            + "constructor is not enough and was what this test used to accept (Adam, 2026-09-11): "
            + bare);
    }

    /**
     * And a TEST that stands up a bare Swing window installs it too (Adam: "I still see tests popping up with the small
     * font in the menu bar, FYI.").  A `JFrame`, `JDialog`, `JWindow` or `JOptionPane` built by a test is nobody's
     * window but the test's.  Only BARE windows are asked about: a test that builds a `TrainControlUI` or
     * `RouteEditorFrame` is covered by the claim above.
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
                || code.contains("= init(") || code.contains("TestStation.simulated")) continue;

            bare.add(source.getName());
        }

        assertEquals(bare.toString(), "[]",
            "these tests stand up a Swing window of their own without installing the application's "
            + "look and feel, so what pops up is drawn in Metal - a different font and different "
            + "insets from the window the operator sees. Add "
            + "`org.traincontrol.gui.TrainControlUI.installLookAndFeel()` before the window is built "
            + "(Adam, 2026-09-11): " + bare);
    }

    /** A source with its comments taken out: a comment mentioning `new JFrame(` is not a test building one. */
    private static String withoutComments(String source)
    {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\n]*", "");
    }

    /** Whether this source builds a Swing window that belongs to no application class. */
    private static boolean standsUpABareWindow(String code)
    {
        return code.contains("new JFrame(") || code.contains("new javax.swing.JFrame(")
            || code.contains("new JDialog(") || code.contains("new javax.swing.JDialog(")
            || code.contains("new JWindow(") || code.contains("new javax.swing.JWindow(")
            || code.contains("JOptionPane.show");
    }

    /** Every test source under a folder. */
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

    /** Whether this source declares a top-level window. */
    private static boolean isAWindow(String source)
    {
        return source.contains("extends javax.swing.JFrame") || source.contains("extends JFrame")
            || source.contains("extends javax.swing.JDialog") || source.contains("extends JDialog")
            || source.contains("extends PositionAwareJFrame");
    }

    /** Whether this class extends another window under gui that asks for the look and feel. */
    private static boolean inheritsTheAsk(String source, Map<String, String> windows)
    {
        for (Map.Entry<String, String> other : windows.entrySet())
        {
            String name = other.getKey().replace(".java", "");

            if (source.contains("extends " + name) && asksAtClassLoad(other.getValue()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether this source installs the look and feel at CLASS LOAD rather than in a constructor: a static block is in
     * front of `JFrame`'s own constructor, which creates the root pane.
     */
    private static boolean asksAtClassLoad(String source)
    {
        return java.util.regex.Pattern
            .compile("(?s)\\bstatic\\s*\\{[^}]*installLookAndFeel\\s*\\(")
            .matcher(source).find();
    }
}
