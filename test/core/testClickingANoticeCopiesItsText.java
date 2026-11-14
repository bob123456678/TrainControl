package core;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.TileKey;
import support.Display;
import support.Reflect;

/**
 * Clicking a guard notice in the autonomy editor copies its text to the system clipboard.  Split from
 * `testASignalGuardsItsStationAndTheNoticeNamesIt` so that only this one claim, which needs the clipboard to itself
 * (docs/tools/serial-lane.txt), takes the whole machine.
 *
 * @author Adam
 */
public class testClickingANoticeCopiesItsText extends AutonomySessionFixture
{
    /**
     * Clicking a notice copies its text to the clipboard as plain text (MT-767).  Read back after a sentinel is put
     * there, so a copy that never happens fails.  Windows opens the clipboard to one program at a time, so each use
     * waits and a dropped copy is made again; skipped only when another program holds it for the whole wait.
     *
     * MUTATION: take the copy out of the list's selection listener.
     */
    @Test
    public void testClickingANoticeCopiesItsTextToTheClipboard() throws Exception
    {
        Display.needed("there is no clipboard on a headless machine");

        openTheGuardedLine(pageWithAGuardOffTheLine());

        TileKey station = new TileKey("main", 1, 1);
        TileKey signal = new TileKey("main", 2, 3);

        session.setEntrySignals(station, Arrays.asList(signal));

        final org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel panel =
            new org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel(session, "main", () -> { });

        java.lang.reflect.Field field =
            Reflect.field(org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class, "findings");

        final javax.swing.JList<?> list = (javax.swing.JList<?>) field.get(panel);

        String expected = null;

        for (int i = 0; i < list.getModel().getSize(); i++)
        {
            String shown = String.valueOf(list.getModel().getElementAt(i));

            if (shown.contains("but no way into")) expected = shown.trim();
        }

        assertNotNull(expected, "precondition: the editor's list has no guard notice");

        final java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();

        final java.awt.datatransfer.StringSelection sentinel =
            new java.awt.datatransfer.StringSelection("nothing copied yet");

        String copied = "nothing copied yet";

        // A click the program could not copy is made again: it leaves a busy clipboard alone by design.
        for (int click = 0; click < CLIPBOARD_CLICKS && "nothing copied yet".equals(copied); click++)
        {
            onTheClipboard(() -> { clipboard.setContents(sentinel, sentinel); return null; });

            javax.swing.SwingUtilities.invokeAndWait(list::clearSelection);

            clickTheGuardNotice(panel);

            copied = onTheClipboard(() -> (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor));
        }

        assertEquals(copied, expected, "a click on a notice did not copy its text to the clipboard (MT-767)");

        assertFalse(copied.contains("<"), "the copied notice carries markup, which is not plain text: " + copied);

        // And a heading is not a notice: clicking "Warnings" or a page's name leaves the clipboard alone
        java.lang.reflect.Field severities =
            Reflect.field(org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class, "findingSeverity");

        final java.util.List<?> severity = (java.util.List<?>) severities.get(panel);

        int heading = -1;

        for (int i = 0; i < severity.size() && heading < 0; i++) if (severity.get(i) == null) heading = i;

        assertTrue(heading >= 0, "precondition: the editor's list has no heading row");

        final java.awt.datatransfer.StringSelection untouched = new java.awt.datatransfer.StringSelection("left alone");

        onTheClipboard(() -> { clipboard.setContents(untouched, untouched); return null; });

        final int row = heading;

        javax.swing.SwingUtilities.invokeAndWait(() -> list.setSelectedIndex(row));

        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertEquals(onTheClipboard(() -> clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor)), "left alone",
            "a click on the heading " + list.getModel().getElementAt(row) + " was copied over the clipboard");
    }

    /** How long a use of the clipboard waits for another program to let go of it. */
    private static final long CLIPBOARD_PATIENCE_MS = 10_000;

    /** How many times a notice is clicked while its copy keeps finding the clipboard busy. */
    private static final int CLIPBOARD_CLICKS = 5;

    /**
     * One use of the system clipboard, made again while another program has it open, and skipped when that lasts the
     * whole wait - the desktop, not the program, decides that.
     */
    private static <T> T onTheClipboard(java.util.concurrent.Callable<T> use) throws Exception
    {
        long until = System.currentTimeMillis() + CLIPBOARD_PATIENCE_MS;

        while (true)
        {
            try
            {
                return use.call();
            }
            catch (IllegalStateException busy)
            {
                if (System.currentTimeMillis() > until)
                {
                    throw new org.testng.SkipException("another program held the system clipboard open for "
                        + CLIPBOARD_PATIENCE_MS / 1000 + " s: " + busy);
                }

                Thread.sleep(20);
            }
        }
    }
}
