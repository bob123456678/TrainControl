package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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

    /** What the door showed: the name it suggested, and every message. */
    private static final class Seen
    {
        volatile String suggested;
        volatile int choosers;
        final List<String> said = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Presses File > Export Log; the first chooser takes `file` (null cancels) and a second one, which comes back after
     * a failed write, is canceled.  Every message is answered OK.
     */
    private static Seen press(TrainControlUI ui, File file) throws Exception
    {
        final Seen seen = new Seen();
        final boolean[] going = {true};
        final Set<Object> handled = Collections.newSetFromMap(new IdentityHashMap<>());

        Thread answering = new Thread(() -> support.DialogWatch.until(() -> going[0], 100, dialog ->
        {
            JFileChooser chooser = support.Components.inside(dialog.getContentPane(), JFileChooser.class);

            if (chooser != null)
            {
                if (!handled.add(dialog)) return;

                seen.choosers++;

                if (seen.choosers == 1) seen.suggested = chooser.getSelectedFile().getName();

                final boolean approve = seen.choosers == 1 && file != null;

                SwingUtilities.invokeLater(() ->
                {
                    if (approve)
                    {
                        chooser.setSelectedFile(file);
                        chooser.approveSelection();
                    }
                    else
                    {
                        chooser.cancelSelection();
                    }
                });

                return;
            }

            JOptionPane pane = support.Components.inside(dialog.getContentPane(), JOptionPane.class);

            if (pane == null || !handled.add(pane)) return;

            seen.said.add(support.PopupShape.of(pane).sentence);

            SwingUtilities.invokeLater(() -> pane.setValue(Integer.valueOf(JOptionPane.OK_OPTION)));
        }), "log export answerer");

        answering.setDaemon(true);
        answering.start();

        try
        {
            SwingUtilities.invokeLater(item(ui)::doClick);

            support.Components.waitFor(() -> seen.choosers >= 1, 60000);

            support.Components.settle();

            if (file != null) support.Components.waitFor(() -> seen.choosers >= 2 || file.exists(), 10000);

            support.Components.settle();
        }
        finally
        {
            going[0] = false;
        }

        return seen;
    }

    private static JMenuItem item(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("exportLogMenuItem");

        field.setAccessible(true);

        JMenuItem item = (JMenuItem) field.get(ui);

        assertNotNull(item, "the File menu has no Export Log item");

        return item;
    }

    private static File aFileIn(File folder, String name)
    {
        return new File(folder, name);
    }

    private static File temporaryFolder() throws Exception
    {
        File folder = Files.createTempDirectory("tc-logexport").toFile();

        folder.deleteOnExit();

        return folder;
    }

    /**
     * MUTATION: `LogExportDoor` saves something other than `logText()` (the text without its stamps, say), or leaves the
     * line separators as "\n", or writes no file, and this fails.
     */
    @Test(timeOut = 180000)
    public void testExportLogWritesExactlyTheShownTextToTheChosenFile() throws Exception
    {
        inAWindow(ui ->
        {
            ui.log("FR051 export one");
            ui.log("FR051 export two\nFR051 export tail");

            String shown = logOf(ui);

            File file = aFileIn(temporaryFolder(), "chosen-log.txt");

            String remembered = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

            try
            {
                Seen seen = press(ui, file);

                assertTrue(file.isFile(), "no file was written where the chooser was told to save");

                assertEquals(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
                    shown.replace("\n", System.lineSeparator()), "the file is not the log as it showed");

                assertTrue(Pattern.compile("^TrainControl-log-\\d{4}-\\d\\d-\\d\\d-\\d{4}\\.txt$").matcher(seen.suggested)
                    .matches(), "the suggested name is " + seen.suggested);

                assertTrue(logOf(ui).contains(I18n.f("ui.infoLogSaved", file.getAbsolutePath())),
                    "the log does not say where it was saved");

                assertTrue(seen.said.isEmpty(), "a message came up on a good save: " + seen.said);
            }
            finally
            {
                if (remembered == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
                else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, remembered);

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
            File missing = aFileIn(aFileIn(temporaryFolder(), "no-such-folder"), "log.txt");

            String remembered = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

            try
            {
                Seen seen = press(ui, missing);

                assertEquals(seen.said.size(), 1, "the failed write was not said once: " + seen.said);

                assertTrue(seen.said.get(0).contains(missing.getAbsolutePath()),
                    "the message does not name the file: " + seen.said.get(0));

                assertEquals(seen.choosers, 2, "the chooser did not come back for another place");

                assertFalse(missing.exists(), "a file appeared");
            }
            finally
            {
                if (remembered == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
                else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, remembered);
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
