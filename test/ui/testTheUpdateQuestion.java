package ui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.TrainControlUI.UpdateChoice;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import org.traincontrol.util.SelfUpdate;

/**
 * The update question offers Download & Update only where there is a jar to replace, and the update refuses to start
 * while trains are running (FR-125).
 *
 * Adam, 2026-10-12: *"when user clicks on the new version, they see a download, download & update, or cancel button"*,
 * and *"If no jar in current folder, it shouldn't be offered as a menu option."*
 *
 * **No window and no dialog.**  The question's buttons go to a hook that answers in place of the user
 * (`TrainControlUI.updateQuestionForTests`), and the refusal is the predicate Download & Update asks before it
 * downloads and again before it closes TrainControl, asked of a real model on a sandboxed layout.
 *
 * MUTATIONS, each seen red: the answer read by position alone, so the second of two buttons counts as Download & Update,
 * or Download & Update made the default (the buttons claim); `jarAt` accepting a folder (the classes claim); the
 * refusal dropping the model's running question, or the routes (the refusal claim).
 */
public class testTheUpdateQuestion
{
    @AfterMethod(alwaysRun = true)
    public void tearDown()
    {
        TrainControlUI.updateQuestionForTests(null);
    }

    /**
     * With a jar: Download, Download & Update, Cancel.  Without one: Download and Cancel, and the second button is
     * Cancel there, not Download & Update.  Download is the default either way.
     */
    @Test
    public void testTheButtonsWithAndWithoutAJar()
    {
        final String download = I18n.t("ui.update.download");
        final String install = I18n.t("ui.update.downloadAndUpdate");
        final String cancel = I18n.t("ui.cancel");

        final List<List<Object>> shown = new ArrayList<>();
        final List<Object> defaults = new ArrayList<>();
        final int[] press = {1};

        TrainControlUI.updateQuestionForTests((choices, initial) ->
        {
            shown.add(Arrays.asList(choices));
            defaults.add(initial);

            return press[0];
        });

        assertEquals(TrainControlUI.askAboutTheUpdate(null, "3.0.2", "3.0.1", true), UpdateChoice.DOWNLOAD_AND_UPDATE,
            "the second button, with a jar, is Download & Update");

        assertEquals(shown.get(0), Arrays.<Object>asList(download, install, cancel), "with a jar to replace, the"
            + " question offers Download, Download & Update and Cancel");

        assertEquals(TrainControlUI.askAboutTheUpdate(null, "3.0.2", "3.0.1", false), UpdateChoice.CANCEL,
            "without a jar the second button is Cancel, and pressing it started an update with nothing to replace");

        assertEquals(shown.get(1), Arrays.<Object>asList(download, cancel), "without a jar to replace, Download & Update"
            + " must not be offered at all (Adam, 2026-10-12)");

        assertEquals(defaults, Arrays.<Object>asList(download, download), "the default button must be Download, with a"
            + " jar or without: Download & Update closes TrainControl, and a reflex Enter must not do that");

        press[0] = 0;

        assertEquals(TrainControlUI.askAboutTheUpdate(null, "3.0.2", "3.0.1", true), UpdateChoice.DOWNLOAD);
        assertEquals(TrainControlUI.askAboutTheUpdate(null, "3.0.2", "3.0.1", false), UpdateChoice.DOWNLOAD);

        press[0] = -1;

        assertEquals(TrainControlUI.askAboutTheUpdate(null, "3.0.2", "3.0.1", true), UpdateChoice.CANCEL,
            "closing the question is Cancel");

        press[0] = 2;

        assertEquals(TrainControlUI.askAboutTheUpdate(null, "3.0.2", "3.0.1", true), UpdateChoice.CANCEL);
    }

    /**
     * Run from a folder of classes, as an IDE or this test runs it, there is no jar to replace; a jar in a folder that
     * can be written is one.
     *
     * @throws Exception from the files
     */
    @Test
    public void testRunningFromClassesIsNoJar() throws Exception
    {
        File from = new File(TrainControlUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        assertTrue(from.isDirectory(), "precondition: this test runs TrainControlUI from a folder of classes, and it"
            + " was loaded from " + from);

        assertNull(SelfUpdate.runningJar(TrainControlUI.class), "running from a folder of classes counted as running"
            + " from a jar, so Download & Update would be offered with nothing to replace");

        File folder = Files.createTempDirectory("tc-fr125-jar").toFile();

        try
        {
            File jar = new File(folder, "TrainControl.jar");
            File text = new File(folder, "TrainControl.txt");

            Files.write(jar.toPath(), new byte[] {1});
            Files.write(text.toPath(), new byte[] {1});

            assertEquals(SelfUpdate.jarAt(jar.toURI().toURL()), jar.getAbsoluteFile(), "a jar in a folder that can be"
                + " written is a jar an update can replace");

            assertNull(SelfUpdate.jarAt(folder.toURI().toURL()), "a folder is not a jar");
            assertNull(SelfUpdate.jarAt(text.toURI().toURL()), "a file that is not a .jar is not a jar");
            assertNull(SelfUpdate.jarAt(new File(folder, "gone.jar").toURI().toURL()), "a jar that is not there");
            assertNull(SelfUpdate.jarAt(null));
        }
        finally
        {
            for (File each : folder.listFiles()) each.delete();

            folder.delete();
        }
    }

    /**
     * Download & Update is refused while autonomy, a timetable, Return Home or a route is running, and allowed at rest.
     *
     * @throws Exception from the model
     */
    @Test
    public void testTheUpdateIsRefusedWhileTrainsRun() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        MarklinControlStation model = null;

        final String probe = "FR-125 probe route";

        try
        {
            // The sandbox first, before the model: init reads the layout preference.
            sandbox = support.LayoutSandbox.open();

            model = init(null, true, false, false, true);

            assertNull(TrainControlUI.whyAnUpdateMustWait(model, false), "precondition: an update was refused with"
                + " nothing running");

            assertEquals(TrainControlUI.whyAnUpdateMustWait(model, true), I18n.t("ui.update.refusedWhileRunning"),
                "an update was allowed while Return Home works out its plan");

            final Layout layout = model.getAutoLayout();

            // A Return Home run, as the window marks it on the railway
            layout.setStagingInProgress(true);

            try
            {
                assertNotNull(TrainControlUI.whyAnUpdateMustWait(model, false),
                    "an update was allowed while Return Home is running");
            }
            finally
            {
                layout.setStagingInProgress(false);
            }

            // Autonomy or a timetable running, which is the railway's own running flag
            java.lang.reflect.Field running = Layout.class.getDeclaredField("running");

            running.setAccessible(true);
            running.setBoolean(layout, true);

            try
            {
                assertNotNull(TrainControlUI.whyAnUpdateMustWait(model, false),
                    "an update was allowed while autonomy is running");
            }
            finally
            {
                running.setBoolean(layout, false);
            }

            assertNull(TrainControlUI.whyAnUpdateMustWait(model, false), "precondition: the railway is at rest again");

            // A route, run by hand with autonomy idle
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

            assertTrue(model.newRoute(probe, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "precondition: the probe route could not be made");

            assertTrue(model.getRoute(probe).setExecuting(), "precondition: the probe route would not run");

            try
            {
                assertNotNull(TrainControlUI.whyAnUpdateMustWait(model, false),
                    "an update was allowed while a route is running");
            }
            finally
            {
                model.getRoute(probe).stopExecuting();
            }

            assertNull(TrainControlUI.whyAnUpdateMustWait(model, false), "a route that has finished still refuses");
        }
        finally
        {
            if (model != null && model.getRoute(probe) != null) model.deleteRoute(probe);

            if (sandbox != null) sandbox.close();
        }
    }
}
