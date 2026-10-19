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
 * or Download & Update made the default, or the tooltip promising an update without a jar (the buttons claim); `jarAt`
 * accepting a folder (the classes claim); the refusal dropping the model's running question, or the routes (the
 * refusal claim); `whenNoModalDialog` not waiting (the dialog claim); `aModalDialogIsShowing` not seeing one (the
 * modal claim).
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

        // And the item's tooltip says what the question will offer (FR-125, 2026-10-13)
        assertEquals(TrainControlUI.updateItemTooltip(false), I18n.t("ui.main.tooltip.downloadUpdate"), "without a jar"
            + " the tooltip promises an update the question does not offer");
        assertEquals(TrainControlUI.updateItemTooltip(true), I18n.t("ui.main.tooltip.downloadAndUpdate"));
        assertNotEquals(TrainControlUI.updateItemTooltip(true), TrainControlUI.updateItemTooltip(false));

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
     * The update's exit waits while a modal dialog is open and runs once it has closed; with none open it runs at once
     * (FR-125, 2026-10-13: a posted exit ran inside a rename dialog and closed it with the typing unapplied).
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheUpdateWaitsForAnOpenDialog() throws Exception
    {
        final java.util.concurrent.atomic.AtomicBoolean showing = new java.util.concurrent.atomic.AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
        final boolean[] atOnce = new boolean[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            atOnce[0] = TrainControlUI.whenNoModalDialog(ran::incrementAndGet, showing::get, 50));

        assertFalse(atOnce[0], "the update's exit ran with a modal dialog open");

        Thread.sleep(500);

        assertEquals(ran.get(), 0, "the update's exit ran while the dialog was still open");

        showing.set(false);

        long until = System.currentTimeMillis() + 10000;

        while (ran.get() == 0 && System.currentTimeMillis() < until) Thread.sleep(20);

        Thread.sleep(300);

        assertEquals(ran.get(), 1, "the update's exit did not run, once, after the dialog closed");

        javax.swing.SwingUtilities.invokeAndWait(() ->
            atOnce[0] = TrainControlUI.whenNoModalDialog(ran::incrementAndGet, () -> false, 50));

        assertTrue(atOnce[0], "with no dialog open the update's exit did not run at once");
        assertEquals(ran.get(), 2);
    }

    /**
     * A real modal dialog on screen is seen, and is not once it has closed.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAModalDialogIsSeen() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("a modal dialog needs a display");
        }

        assertFalse(TrainControlUI.aModalDialogIsShowing(), "precondition: a modal dialog is already open");

        final javax.swing.JDialog[] dialog = new javax.swing.JDialog[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            dialog[0] = new javax.swing.JDialog((java.awt.Frame) null, "FR-125", true);

            dialog[0].setSize(120, 60);
        });

        try
        {
            // Shown later: a modal setVisible holds the task that calls it until the dialog closes
            javax.swing.SwingUtilities.invokeLater(() -> dialog[0].setVisible(true));

            long until = System.currentTimeMillis() + 10000;

            while (!TrainControlUI.aModalDialogIsShowing() && System.currentTimeMillis() < until) Thread.sleep(20);

            assertTrue(TrainControlUI.aModalDialogIsShowing(), "an open modal dialog was not seen, so the update's exit"
                + " would run inside it");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> dialog[0].dispose());
        }

        long until = System.currentTimeMillis() + 10000;

        while (TrainControlUI.aModalDialogIsShowing() && System.currentTimeMillis() < until) Thread.sleep(20);

        assertFalse(TrainControlUI.aModalDialogIsShowing(), "a closed dialog still reads as open, so the update would"
            + " wait for ever");
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

            model = support.TestStationAddress.withNoStation(
                () -> MarklinControlStation.init(null, true, false, false, true));

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
