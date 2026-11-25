package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Reflect;

/**
 * The main window's log starts each entry with the time it was logged at, and File > Export Log saves the log as it
 * shows (FR-051).  Driven as the operator drives it: the line logged, the menu item pressed, the file chosen.
 */
public class testTheLogIsStampedAndExports
{
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private interface Claim
    {
        void check(TrainControlUI ui) throws Exception;
    }

    private static void inAWindow(Claim claim) throws Exception
    {
        inAWindow(testTheImportDoorReadsAnOldFile::openTheWindow, claim);
    }

    /** The same, in the window `open` builds. */
    private static void inAWindow(java.util.concurrent.Callable<TrainControlUI> open, Claim claim) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            claim.check(sandboxed.hold(open.call()));
        }
        finally
        {
            sandboxed.close();
        }
    }

    /** The log's text once everything logged so far has reached it. */
    private static String logOf(TrainControlUI ui) throws Exception
    {
        final String[] text = new String[1];

        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> text[0] = shownLog(ui));

        return text[0];
    }

    private static String shownLog(TrainControlUI ui)
    {
        try
        {
            return ((org.traincontrol.gui.app.LogPanel) Reflect.get(TrainControlUI.class, ui, "logView")).text();
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static Matcher stamped(String text, String entry)
    {
        Matcher found = Pattern.compile("(?m)^(\\d\\d:\\d\\d:\\d\\d) " + Pattern.quote(entry) + "$").matcher(text);

        return found;
    }

    /**
     * MUTATION: `TrainControlUI.log` inserts the message without the stamp, and this fails.
     */
    @Test(timeOut = 180000)
    public void testALoggedLineStartsWithTheTimeItWasLoggedAt() throws Exception
    {
        inAWindow(ui ->
        {
            String before = HMS.format(LocalTime.now());

            ui.log("FR051 one");

            String after = HMS.format(LocalTime.now());

            Matcher line = stamped(logOf(ui), "FR051 one");

            assertTrue(line.find(), "the logged line has no HH:mm:ss stamp before it: " + logOf(ui).split("\n")[0]);

            assertTrue(line.group(1).equals(before) || line.group(1).equals(after),
                "the stamp " + line.group(1) + " is not the time of logging, " + before + " to " + after);
        });
    }

    /**
     * MUTATION: the stamp is read inside the event-thread task instead of before it, and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheStampIsWhenItWasLoggedNotWhenItWasShown() throws Exception
    {
        inAWindow(ui ->
        {
            final java.util.concurrent.CountDownLatch busy = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

            SwingUtilities.invokeLater(() ->
            {
                busy.countDown();

                try
                {
                    release.await(60, java.util.concurrent.TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(busy.await(30, java.util.concurrent.TimeUnit.SECONDS), "precondition: the event thread was not held");

            String before = HMS.format(LocalTime.now());

            ui.log("FR051 late");

            String after = HMS.format(LocalTime.now());

            // The event thread stays held until the clock has moved past the logging's second, then shows the line
            long loggedBy = System.currentTimeMillis();

            assertTrue(support.Components.waitFor(() -> System.currentTimeMillis() >= loggedBy + 2200, 30000),
                "precondition: the clock did not move on");

            release.countDown();

            Matcher line = stamped(logOf(ui), "FR051 late");

            assertTrue(line.find(), "the late line has no stamp");

            assertTrue(line.group(1).equals(before) || line.group(1).equals(after),
                "the stamp " + line.group(1) + " is when the line was shown, not when it was logged (" + before + ")");
        });
    }

    /**
     * MUTATION: every line of a multi-line entry gets its own stamp, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAnEntryOfSeveralLinesHasOneStampOnItsFirstLine() throws Exception
    {
        inAWindow(ui ->
        {
            ui.log("FR051 first\nFR051 second\nFR051 third");

            String text = logOf(ui);

            assertTrue(Pattern.compile("(?m)^\\d\\d:\\d\\d:\\d\\d FR051 first$").matcher(text).find(),
                "the first line of the entry has no stamp");

            assertTrue(text.contains("\nFR051 second\nFR051 third\n"),
                "the later lines of the entry are not plain: " + text.substring(0, Math.min(300, text.length())));

            assertFalse(Pattern.compile("(?m)^\\d\\d:\\d\\d:\\d\\d FR051 (second|third)").matcher(text).find(),
                "a later line of the entry carries a stamp");
        });
    }

    /**
     * In the Log tab a warning shows the yellow triangle beside its line, an error the red one and a notice none,
     * however it reached the log: a key the model logs, an exception, a task's uncaught error (FR-190).  The text keeps
     * no mark, and Export Log saves exactly it.
     *
     * MUTATION: in `MarklinControlStation.logf`, `Level.INFO` for `LogPanel.levelOf(key)`; or in its `log(Exception)`
     * or `THREAD_ERRORS`, `Level.INFO` for `Level.SEVERE`; or `TrainControlUI.log(String, Level)` passes `Level.INFO`.
     */
    @Test(timeOut = 180000)
    public void testWarningsAndErrorsShowTheirTriangles() throws Exception
    {
        // The window its station logs to, as the application builds it
        inAWindow(() -> (TrainControlUI) support.TestStation.simulatedWithWindowInDebug().getGUI(), ui ->
        {
            IllegalStateException failure = new IllegalStateException("FR190 uncaught");
            String[] entries = {"FR190 notice", I18n.f("layout.warnPowerNotConfirmed", "FR190"),
                I18n.f("ui.errorSavingUiState", "FR190"), "FR190 exception",
                I18n.f("error.uncaughtInThread", "fr190-task", failure)};

            ui.log(entries[0]);
            ui.getModel().logf("layout.warnPowerNotConfirmed", "FR190");
            ui.getModel().logf("ui.errorSavingUiState", "FR190");
            ui.getModel().log(new Exception(entries[3]));
            org.traincontrol.marklin.MarklinControlStation.THREAD_ERRORS.uncaughtException(new Thread("fr190-task"), failure);

            String shown = logOf(ui);

            for (String entry : entries) assertTrue(stamped(shown, entry).find(), "not shown plain: " + entry);

            assertEquals(support.Components.onTheEventThread(() -> marks(ui, shown, entries)), "-YRRR",
                "the marks beside notice, warning, error, exception, uncaught error");

            File file = new File(temporaryFolder(), "marked-log.txt");

            try (support.Answering answering = support.Answering.start(message -> null).choosing(file))
            {
                SwingUtilities.invokeLater(item(ui)::doClick);

                assertTrue(support.Components.waitFor(file::isFile, 60000), "Export Log wrote no file");
                support.Components.settle();
                assertEquals(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
                    shown.replace("\n", System.lineSeparator()), "the file is not the log as it showed");
            }
            finally
            {
                file.delete();
            }
        });
    }

    /**
     * The mark painted beside each entry's line in the shown Log tab: Y for the yellow triangle, R for the red, - for
     * none; event thread.
     */
    private static String marks(TrainControlUI ui, String shown, String[] entries) throws Exception
    {
        javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) Reflect.get(TrainControlUI.class, ui, "KeyboardTab");
        tabs.setSelectedComponent((java.awt.Component) Reflect.get(TrainControlUI.class, ui, "logPanel"));
        ui.validate();

        Object view = Reflect.get(TrainControlUI.class, ui, "logView");
        javax.swing.JComponent gutter = (javax.swing.JComponent) Reflect.get(view.getClass(), view, "gutter");
        javax.swing.JTextArea area = (javax.swing.JTextArea) Reflect.get(view.getClass(), view, "area");
        StringBuilder said = new StringBuilder();

        for (String entry : entries)
        {
            Matcher at = stamped(shown, entry);
            at.find();

            java.awt.Rectangle line = area.modelToView(at.start());
            java.awt.image.BufferedImage band = new java.awt.image.BufferedImage(gutter.getWidth(), line.height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = band.createGraphics();

            g.translate(0, -line.y);
            g.setClip(0, line.y, gutter.getWidth(), line.height);
            gutter.paint(g);
            g.dispose();

            char mark = '-';

            for (int x = 0; x < band.getWidth(); x++)
            {
                for (int y = 0; y < band.getHeight(); y++)
                {
                    int rgb = band.getRGB(x, y) & 0xffffff;

                    // The triangles' fills
                    if (rgb == 0xfabe28) mark = 'Y';
                    if (rgb == 0xd22323) mark = 'R';
                }
            }

            said.append(mark);
        }

        return said.toString();
    }

    /** Cancels the save chooser that has come back after a failed write. */
    private static boolean cancelTheChooserThatCameBack() throws Exception
    {
        return support.Components.waitFor(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                final JFileChooser chooser = support.Components.inside(((javax.swing.JDialog) window).getContentPane(),
                    JFileChooser.class);

                if (chooser != null)
                {
                    SwingUtilities.invokeLater(chooser::cancelSelection);

                    return true;
                }
            }

            return false;
        }, 30000);
    }

    private static JMenuItem item(TrainControlUI ui) throws Exception
    {
        JMenuItem item = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "exportLogMenuItem");

        assertNotNull(item, "the File menu has no Export Log item");

        return item;
    }

    private static File temporaryFolder() throws Exception
    {
        File folder = Files.createTempDirectory("tc-logexport").toFile();

        folder.deleteOnExit();

        return folder;
    }

    /**
     * MUTATION: `LogExportDoor` saves something other than `logText()` (the text without its stamps, say), or leaves the
     * line separators as LF, or writes no file, and this fails.
     */
    @Test(timeOut = 180000)
    public void testExportLogWritesExactlyTheShownTextToTheChosenFile() throws Exception
    {
        inAWindow(ui ->
        {
            ui.log("FR051 export one");
            ui.log("FR051 export two\nFR051 export tail");

            String shown = logOf(ui);

            File file = new File(temporaryFolder(), "chosen-log.txt");

            try (support.Answering answering = support.Answering.start(message -> null).choosing(file))
            {
                SwingUtilities.invokeLater(item(ui)::doClick);

                assertTrue(support.Components.waitFor(file::isFile, 60000), "no file was written where the chooser was told to save");

                support.Components.settle();

                assertEquals(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
                    shown.replace("\n", System.lineSeparator()), "the file is not the log as it showed");

                assertTrue(logOf(ui).contains(I18n.f("ui.infoLogSaved", file.getAbsolutePath())),
                    "the log does not say where it was saved");

                assertTrue(answering.asked().isEmpty(), "a message came up on a good save: " + answering.asked());
            }
            finally
            {
                file.delete();
            }
        });
    }

    /** What the door said when the chooser was told to save to `target`, which it cannot write. */
    private static String saidWhenWritingFails(TrainControlUI ui, File target) throws Exception
    {
        try (support.Answering answering = support.Answering.start(message -> null).choosing(target))
        {
            SwingUtilities.invokeLater(item(ui)::doClick);

            assertTrue(answering.awaitAsked(1, 60000), "a failed write said nothing");

            assertTrue(cancelTheChooserThatCameBack(), "the chooser did not come back for another place");

            support.Components.settle();

            assertEquals(answering.asked().size(), 1, "the failed write was not said once: " + answering.asked());

            return answering.asked().get(0);
        }
    }

    /** The message names the file and gives `reason`, which is more than the path. */
    private static void assertSaysWhy(String said, File target, String reason)
    {
        assertTrue(said.contains(target.getAbsolutePath()), "the message does not name the file: " + said);

        assertTrue(said.contains(reason), "the message gives no plain reason (" + reason + "): " + said);

        assertEquals(said.indexOf(target.getAbsolutePath()), said.lastIndexOf(target.getAbsolutePath()),
            "the message names the file twice: " + said);
    }

    /**
     * MUTATION: `LogExportDoor` swallows the write error, or says `e.getMessage()` (which is only the path for a
     * missing folder), and this fails.
     */
    @Test(timeOut = 180000)
    public void testAMissingFolderIsSaidPlainly() throws Exception
    {
        inAWindow(ui ->
        {
            File missing = new File(new File(temporaryFolder(), "no-such-folder"), "log.txt");

            assertSaysWhy(saidWhenWritingFails(ui, missing), missing, I18n.t("ui.reasonFolderMissing"));

            assertFalse(missing.exists(), "a file appeared");
        });
    }

    /**
     * MUTATION: as above; for a file the user may not write, `e.getMessage()` is only the path.
     */
    @Test(timeOut = 180000)
    public void testAFileThatMayNotBeWrittenIsSaidPlainly() throws Exception
    {
        inAWindow(ui ->
        {
            File locked = new File(temporaryFolder(), "locked-log.txt");

            Files.write(locked.toPath(), new byte[] {1});

            assertTrue(locked.setReadOnly(), "precondition: the file cannot be made read-only");

            try
            {
                assertSaysWhy(saidWhenWritingFails(ui, locked), locked, I18n.t("ui.reasonNoPermission"));

                assertEquals(Files.readAllBytes(locked.toPath()).length, 1, "the locked file was changed");
            }
            finally
            {
                locked.setWritable(true);
                locked.delete();
            }
        });
    }

    /**
     * MUTATION: the item is moved out of the File menu or given a tooltip, and this fails.
     */
    @Test(timeOut = 180000)
    public void testExportLogIsInTheFileMenuWithNoTooltip() throws Exception
    {
        inAWindow(ui ->
        {
            JMenuItem item = item(ui);

            javax.swing.JPopupMenu parent = (javax.swing.JPopupMenu) item.getParent();

            assertEquals(((javax.swing.JMenu) parent.getInvoker()).getText(), I18n.t("ui.main.toolbar.file"),
                "Export Log is not in the File menu");

            assertEquals(item.getText(), I18n.t("ui.main.toolbar.exportLog"));

            assertNull(item.getToolTipText(), "Export Log has a tooltip");
        });
    }

    /**
     * The File menu reads in four groups with one separator between them, none doubled or at an end (FR-051).
     * MUTATION: `mountRestoreFromBackup` stops calling `arrangeFileMenu`, and Restore lands after Exit.
     */
    @Test(timeOut = 180000)
    public void testTheFileMenuReadsInFourGroups() throws Exception
    {
        inAWindow(ui ->
        {
            java.awt.Component[] parts = ((javax.swing.JPopupMenu) item(ui).getParent()).getComponents();

            StringBuilder read = new StringBuilder();

            for (java.awt.Component part : parts)
            {
                read.append(part instanceof JMenuItem ? ((JMenuItem) part).getText() : "---").append('|');
            }

            String[][] groups = {
                {"ui.main.toolbar.changeIP"},
                {"ui.main.toolbar.backupData", "ui.main.toolbar.restoreData"},
                {"ui.main.toolbar.exportLog"},
                {"ui.main.toolbar.exit"}};

            StringBuilder want = new StringBuilder();

            for (int g = 0; g < groups.length; g++)
            {
                if (g > 0) want.append("---|");

                for (String key : groups[g]) want.append(I18n.t(key)).append('|');
            }

            assertEquals(read.toString(), want.toString(), "the File menu is not the four groups, top to bottom");
        });
    }
}
