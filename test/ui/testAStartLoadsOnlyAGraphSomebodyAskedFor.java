package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A start loads an old JSON graph only for somebody who ticked the box, and only when there is one (REG-B2).
 *
 * Auto-load became the default in this release, so that a configuration somebody set up is there when TrainControl
 * starts.  The same box also sends a layout with no configuration down the old JSON path - and at v2.8.1 the box was
 * unticked unless somebody ticked it.  So an upgrading user who never had was sent there on every start: with no
 * `autonomy.json`, a modal Blank / Sample chooser during start-up and a validation error after it, again on every
 * start; with a 2.8.1 file, its route activations applied before anybody asked, which switches off every s88 route
 * the file does not list.  The legacy import refuses those two keys for exactly that reason.
 *
 * **The preference is written only by the menu item**, so an absent key is a box nobody touched - 2.8.1's unticked
 * box.  The claims remove it or set it, and put back what was there.
 *
 * MUTATION: have `resumesFromJsonAtStart` answer true again and the first two claims fail; take its call out of the
 * start-up arm and the last one does.
 *
 * @author Adam
 */
public class testAStartLoadsOnlyAGraphSomebodyAskedFor
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    private static String keptKey;
    private static String keptText;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the main window needs a display");
        }

        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);

        // NOT given the model: `setViewListener` is the start-up itself, and would take the arm this is about.
        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        keptKey = prefs().get(TrainControlUI.AUTO_LOAD_AUTONOMY, null);
        keptText = json().getText();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (keptKey == null) prefs().remove(TrainControlUI.AUTO_LOAD_AUTONOMY);
            else prefs().put(TrainControlUI.AUTO_LOAD_AUTONOMY, keptKey);

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        json().setText(keptText);
                    }
                    catch (Exception e)
                    {
                        // Closing anyway.
                    }

                    closing.dispose();
                });
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A 2.8.1 user who never ticked the box, with their old graph beside the jar: not loaded.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testAnUntouchedBoxDoesNotLoadAnOldGraph() throws Exception
    {
        prefs().remove(TrainControlUI.AUTO_LOAD_AUTONOMY);

        setJson(legacyGraph());

        assertFalse(ui.resumesFromJsonAtStart(), "a start loaded the old JSON graph for somebody who never ticked"
            + " auto-load - which at 2.8.1 meant it was never loaded at start - and a 2.8.1 file's route activations"
            + " switch off every s88 route it does not list, before anybody asked (REG-B2)");
    }

    /**
     * Somebody who ticked it, with no graph: nothing to load, and nothing asked.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testATickedBoxWithNothingToLoadAsksNothing() throws Exception
    {
        prefs().putBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, true);

        setJson("");

        assertFalse(ui.resumesFromJsonAtStart(), "a start with no JSON graph went down the JSON path, which opens"
            + " the Blank / Sample chooser during start-up and a validation error after a cancel - on every start"
            + " (REG-B2)");
    }

    /**
     * And somebody who ticked it, with a graph: loaded, as at 2.8.1.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testATickedBoxStillLoadsItsGraph() throws Exception
    {
        prefs().putBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, true);

        setJson(legacyGraph());

        assertTrue(ui.resumesFromJsonAtStart(), "somebody who ticked auto-load and has a JSON graph no longer has it"
            + " loaded at start, which 2.8.1 did");
    }

    /**
     * The start-up arm asks it (REG-B2): the rule above is only a rule if the one door that loads the graph at start
     * goes through it.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testTheStartUpArmAsksIt() throws Exception
    {
        String source = new String(Files.readAllBytes(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), StandardCharsets.UTF_8);

        int from = source.indexOf("// Load autonomy if requested");
        int to = source.indexOf("// Release the latch", from);

        assertTrue(from >= 0 && to > from, "cannot find the start-up arm in TrainControlUI.java");

        String arm = source.substring(from, to);

        assertTrue(java.util.regex.Pattern.compile(
            "else if \\(resumesFromJsonAtStart\\(\\)\\)\\s*\\{\\s*this\\.validateButtonActionPerformed")
            .matcher(arm).find(), "the start-up arm loads the JSON graph without asking resumesFromJsonAtStart");

        assertEquals(arm.split("validateButtonActionPerformed", -1).length - 1, 1,
            "the start-up arm reaches the JSON graph by a second road");
    }

    // ---------------------------------------------------------------------------------------------

    private static Preferences prefs()
    {
        return TrainControlUI.getPrefs();
    }

    private static JTextArea json() throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("autonomyJSON");

        field.setAccessible(true);

        return (JTextArea) field.get(ui);
    }

    private static void setJson(String text) throws Exception
    {
        final Exception[] failed = new Exception[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                json().setText(text);
            }
            catch (Exception e)
            {
                failed[0] = e;
            }
        });

        if (failed[0] != null) throw failed[0];
    }

    private static String legacyGraph() throws Exception
    {
        return new String(Files.readAllBytes(
            new File("test/operator_layout/config/autonomy_legacy/autonomy.json").toPath()), StandardCharsets.UTF_8);
    }
}
