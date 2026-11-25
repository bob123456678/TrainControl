package org.traincontrol.gui.app;

import java.awt.event.MouseEvent;
import java.util.logging.Level;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.util.I18n;
import support.Components;

/**
 * `LogPanel` on its own (FR-190): the level a key names, newest on top, the plain text, the triangles beside the first
 * line of warnings and errors, and the cap.  `regression.testTheLogIsStampedAndExports` is its integration test,
 * through the real window.
 */
public class testLogPanel
{
    private static final String STAMP = "\\d\\d:\\d\\d:\\d\\d ";

    /** What the panel shows once everything logged so far has reached it. */
    private static LogPanel settled(LogPanel panel) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        return panel;
    }

    private static Level[] levels(LogPanel panel, int lines) throws Exception
    {
        return Components.onTheEventThread(() ->
        {
            Level[] found = new Level[lines];

            for (int i = 0; i < lines; i++) found[i] = panel.levelAt(i);

            return found;
        });
    }

    /**
     * A key whose last part starts with "error" logs an error, whatever its namespace.
     *
     * MUTATION: in `LogPanel.levelOf`, drop `name.startsWith("error") ||`, and this fails.
     */
    @Test
    public void testAnErrorKeyLogsAnError()
    {
        assertEquals(LogPanel.levelOf("ui.errorSavingUiState"), Level.SEVERE);
        assertEquals(LogPanel.levelOf("layout.ui.errorLinkedPageGone"), Level.SEVERE);
    }

    /**
     * A key whose last part starts with "fatalError" logs an error.
     *
     * MUTATION: in `LogPanel.levelOf`, drop `|| name.startsWith("fatalError")`, and this fails.
     */
    @Test
    public void testAFatalErrorKeyLogsAnError()
    {
        assertEquals(LogPanel.levelOf("ui.fatalErrorInitializing"), Level.SEVERE);
    }

    /**
     * A key whose last part starts with "warn" ("warnX" or "warningX") logs a warning.
     *
     * MUTATION: in `LogPanel.levelOf`, answer `Level.INFO` in place of `Level.WARNING`, and this fails.
     */
    @Test
    public void testAWarnKeyLogsAWarning()
    {
        assertEquals(LogPanel.levelOf("autolayout.warnAtomicRoutesKeptOn"), Level.WARNING);
        assertEquals(LogPanel.levelOf("route.warningRouteNotExistCalledFrom"), Level.WARNING);
    }

    /**
     * Any other key logs a notice: one that only names an error further in, one in the "error." namespace whose last
     * part does not, and an info key.
     *
     * MUTATION: in `LogPanel.levelOf`, test `key` in place of `name` (the whole key), and "error.prefLoadAdminHint"
     * logs an error.
     */
    @Test
    public void testAnyOtherKeyLogsANotice()
    {
        assertEquals(LogPanel.levelOf("route.ui.autolayoutErrorS88RequiredForAutoFire"), Level.INFO);
        assertEquals(LogPanel.levelOf("error.prefLoadAdminHint"), Level.INFO);
        assertEquals(LogPanel.levelOf("ui.infoLogSaved"), Level.INFO);
    }

    /**
     * The newest entry is on top, stamped; an entry of several lines has the stamp on its first.
     *
     * MUTATION: in `LogPanel.log`, insert at the document's end in place of 0, and this fails.
     */
    @Test
    public void testTheNewestEntryIsOnTop() throws Exception
    {
        LogPanel panel = Components.onTheEventThread(LogPanel::new);

        panel.log("first", Level.INFO);
        panel.log("second\nmore", Level.INFO);

        String text = Components.onTheEventThread(settled(panel)::text);

        assertTrue(Pattern.matches(STAMP + "second\nmore\n" + STAMP + "first\n", text), text);
    }

    /**
     * A warning's and an error's text is the same plain text a notice of the same words shows: no mark is written into
     * it, so selection, copy and Export Log read what they read before.
     *
     * MUTATION: in `LogPanel.log`, put "! " before the message of a level above INFO, and this fails.
     */
    @Test
    public void testTheTextIsPlainAtEveryLevel() throws Exception
    {
        LogPanel panel = Components.onTheEventThread(LogPanel::new);

        panel.log("same words", Level.INFO);
        panel.log("same words", Level.WARNING);
        panel.log("same words", Level.SEVERE);

        String text = Components.onTheEventThread(settled(panel)::text);

        assertTrue(Pattern.matches("(" + STAMP + "same words\n){3}", text), text);
    }

    /**
     * An error shows the red triangle and a warning the yellow one beside the entry's first line; a notice and the
     * later lines of an entry show none, with the tooltip saying which.
     *
     * MUTATION: in `LogPanel.levelAt`, answer the entry's level for every line of it, and the second line of the
     * warning is marked too.
     */
    @Test
    public void testWarningsAndErrorsAreMarkedOnTheirFirstLine() throws Exception
    {
        LogPanel panel = Components.onTheEventThread(LogPanel::new);

        panel.log("an error", Level.SEVERE);
        panel.log("a warning\nits detail", Level.WARNING);
        panel.log("a notice", Level.INFO);
        settled(panel);

        assertEquals(levels(panel, 4), new Level[] {Level.INFO, Level.WARNING, Level.INFO, Level.SEVERE});

        String[] tips = Components.onTheEventThread(() ->
        {
            JComponent gutter = panel.gutter;
            JTextArea area = panel.area;
            String[] said = new String[4];

            // Laid out as a shown text area would be: a text area with no size places no line
            area.setSize(area.getPreferredSize());

            for (int line = 0; line < 4; line++)
            {
                int y = area.modelToView(area.getLineStartOffset(line)).y + 1;

                said[line] = gutter.getToolTipText(new MouseEvent(gutter, MouseEvent.MOUSE_MOVED, 0, 0, 2, y, 0, false));
            }

            return said;
        });

        assertEquals(tips, new String[] {null, I18n.t("ui.log.markWarning"), null, I18n.t("ui.log.markError")});
        assertNotSame(panel.iconFor(Level.SEVERE), panel.iconFor(Level.WARNING), "the error and warning marks are one");
        assertNull(panel.iconFor(Level.INFO), "a notice is marked");
    }

    /** Logs `count` notices of 100 characters each, stamp and line break included. */
    private static void hundreds(LogPanel panel, int count)
    {
        for (int i = 0; i < count; i++) panel.log(String.format("%090d", i), Level.INFO);
    }

    /**
     * The text is cut at the cap, keeping the newest; a warning whose first line still shows keeps its mark, and one
     * cut off entirely is forgotten.
     *
     * MUTATION: in `LogPanel.trim`, leave out the `while` that forgets cut entries, and the older warning is still
     * remembered.
     */
    @Test
    public void testTheCapKeepsTheNewestAndForgetsWhatWasCut() throws Exception
    {
        LogPanel panel = Components.onTheEventThread(LogPanel::new);

        panel.log("older warning", Level.WARNING);
        panel.log(new String(new char[200]).replace('\0', 'w'), Level.WARNING);
        hundreds(panel, 999);

        String text = Components.onTheEventThread(settled(panel)::text);

        assertEquals(text.length(), LogPanel.MAX_CHARS, "the text is not cut at the cap");
        assertTrue(Pattern.compile("^" + STAMP + String.format("%090d", 998) + "\n").matcher(text).find(),
            "the newest entry is not on top");
        assertEquals(levels(panel, 1001)[999], Level.WARNING, "the warning whose first line shows lost its mark");
        assertEquals(levels(panel, 1001)[1000], Level.INFO, "the warning cut off entirely is still remembered");
    }

    /**
     * A cut just after a line break leaves an empty last line, which holds no entry: the warning cut there leaves no
     * triangle beside the empty line.
     *
     * MUTATION: in `LogPanel.trim`, leave out the `kept--` for an empty last line, and the warning is kept.
     */
    @Test
    public void testACutAtALineBreakLeavesNoMarkOnTheEmptyLine() throws Exception
    {
        LogPanel panel = Components.onTheEventThread(LogPanel::new);

        panel.log("cut warning", Level.WARNING);
        hundreds(panel, 1000);

        String text = Components.onTheEventThread(settled(panel)::text);

        assertTrue(text.endsWith(String.format("%090d", 0) + "\n"), "the cut is not at a line break");
        assertEquals(levels(panel, 1001)[1000], Level.INFO, "the empty line left by the cut is marked");
    }
}
