package regression;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Importing a legacy autonomy.json unticks Startup -> Load Autonomy, every time (REG2-C3).
 *
 * The box is one preference for the whole install, shown ticked to anybody who never touched it, and on a layout with
 * only the old graph it loaded nothing until it had been set by hand - so what it showed and what it did disagreed for
 * every upgrading user.  Adam, 2026-09-24, asked which reset he meant: *"Set the setting to unchecked when importing a
 * legacy json file, each time.  Simple to track and implement."*
 *
 * Two halves, because the rule lives in one method and the import door is the only caller: what the method does to
 * the preference and the menu item, and that the door calls it after every import.  The door opens modal dialogs and
 * loads the railway, so it is read rather than driven - `testTheDestinationDoorsAgree`'s shape.
 *
 * MUTATION: empty the method, or drop its call from the import door, and the matching claim fails.
 *
 * @author Adam
 */
public class testALegacyImportUnticksLoadAutonomy
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the menu item is part of the window, and the window needs a display");
        }

        // BEFORE init, which opens whatever the layout preference names (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Ticked before, unticked after: in the preference the start-up reads, and on the menu.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheImportUnticksTheBox() throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField("AutoLoadAutonomyMenuItem");

        field.setAccessible(true);

        final JCheckBoxMenuItem box = (JCheckBoxMenuItem) field.get(ui);

        // The run's own preference node when one.sh or battery.sh runs this; put back either way.
        String was = TrainControlUI.getPrefs().get(TrainControlUI.AUTO_LOAD_AUTONOMY, null);

        try
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, true);

            SwingUtilities.invokeAndWait(() -> box.setSelected(true));

            SwingUtilities.invokeAndWait(() -> ui.autoLoadOffAfterLegacyImport());

            assertFalse(TrainControlUI.getPrefs().getBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, true), "a legacy import"
                + " left Load Autonomy set in the preference start-up reads (Adam, 2026-09-24: \"Set the setting to"
                + " unchecked when importing a legacy json file, each time.\")");

            assertFalse(box.isSelected(), "a legacy import left the Startup -> Load Autonomy box ticked on the menu, so"
                + " it shows a setting the next start will not use");
        }
        finally
        {
            if (was == null) TrainControlUI.getPrefs().remove(TrainControlUI.AUTO_LOAD_AUTONOMY);
            else TrainControlUI.getPrefs().put(TrainControlUI.AUTO_LOAD_AUTONOMY, was);
        }
    }

    /**
     * Every call of `importLegacy` in the window's code is followed by the reset - so every import door unticks the box.
     *
     * @throws Exception if the sources cannot be read
     */
    @Test
    public void testEveryImportDoorUnticksTheBox() throws Exception
    {
        int doors = 0;

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(Paths.get("src/org/traincontrol/gui")))
        {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                if (!file.toString().endsWith(".java")) continue;

                String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\\r\\n]*", " ");

                int at = code.indexOf(".importLegacy(");

                while (at >= 0)
                {
                    doors++;

                    // The rest of the method the call is in: to the next method-level closing brace.
                    int end = code.indexOf("\n    }", at);

                    String rest = code.substring(at, end < 0 ? code.length() : end);

                    assertTrue(rest.contains("autoLoadOffAfterLegacyImport()"), file.getFileName() + " imports a legacy"
                        + " autonomy.json without unticking Startup -> Load Autonomy afterwards (REG2-C3)");

                    at = code.indexOf(".importLegacy(", at + 1);
                }
            }
        }

        assertTrue(doors > 0, "precondition: no import door was found under gui/, so the census checked nothing");
    }
}
