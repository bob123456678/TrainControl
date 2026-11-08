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

            SwingUtilities.invokeLater(() ->
            {
                busy.countDown();

                try
                {
                    Thread.sleep(2200);
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

    /**
     * MUTATION: `LogExportDoor` swallows the write error, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAFileThatCannotBeWrittenIsSaidPlainly() throws Exception
    {
        inAWindow(ui ->
        {
            File missing = new File(new File(temporaryFolder(), "no-such-folder"), "log.txt");

            try (support.Answering answering = support.Answering.start(message -> null).choosing(missing))
            {
                SwingUtilities.invokeLater(item(ui)::doClick);

                assertTrue(answering.awaitAsked(1, 60000), "a failed write said nothing");

                assertTrue(cancelTheChooserThatCameBack(), "the chooser did not come back for another place");

                support.Components.settle();

                assertEquals(answering.asked().size(), 1, "the failed write was not said once: " + answering.asked());

                assertTrue(answering.asked().get(0).contains(missing.getAbsolutePath()),
                    "the message does not name the file: " + answering.asked().get(0));

                assertFalse(missing.exists(), "a file appeared");
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
}
