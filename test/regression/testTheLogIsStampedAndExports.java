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
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            claim.check(sandboxed.hold(testTheImportDoorReadsAnOldFile.openTheWindow()));
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
            java.lang.reflect.Method read = TrainControlUI.class.getDeclaredMethod("logText");

            read.setAccessible(true);

            return (String) read.invoke(ui);
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
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("exportLogMenuItem");

        field.setAccessible(true);

        JMenuItem item = (JMenuItem) field.get(ui);

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
                {"ui.main.toolbar.syncDBWithCS", "ui.main.toolbar.changeIP"},
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
