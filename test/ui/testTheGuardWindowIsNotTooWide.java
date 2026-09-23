package ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * The Exit and Entry Guard Signal windows put their sentence on two lines rather than one wide one (MT-479).
 *
 * Adam, 2026-09-23, on MT-479: *"works, but the editor window for entry/exit guards is much too wide.  make the sentence
 * split over 2 lines so the window isn't too wide."*  The heading was a plain label, so the window was as wide as its
 * sentence - the entry guard's runs to a hundred and fifty characters before a signal is paired, and more after.
 *
 * **The real window**, opened through the method the menu item calls, on a panel with a station of its own.  Two
 * claims: the entry window is narrower than its sentence on one line, which is the width he reported; and neither
 * window's first sentence - the one before a signal is paired - takes more than the two lines he asked for.
 *
 * MUTATION: wrap it at no width, as it was, and the first claim fails; wrap it at a tooltip's 320 pixels and the second
 * does.
 *
 * @author Adam
 */
public class testTheGuardWindowIsNotTooWide
{
    private static final TileKey STATION = new TileKey("main", 5, 1);

    private File folder;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the guard window needs a display");

        folder = Files.createTempDirectory("tc-guard-window").toFile();
        session = new AutonomySession(folder);

        // 1,1 sensor - 2,1 - 3,1 - 4,1 - 5,1 sensor, the station.
        LayoutDiagram page = new LayoutDiagram("main", 9, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 3, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Guards");
        session.setPointName(STATION, "A Platform With A Long Enough Name");
        session.setStation(STATION, true);
        session.rebuild();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception
    {
        closeTheWindows();

        if (folder != null)
        {
            java.nio.file.Files.walk(folder.toPath()).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }

    /**
     * The entry guard's window is narrower than its sentence on one line - which is what he reported.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheEntryGuardWindowSplitsItsSentence() throws Exception
    {
        Measured window = open("ENTRY", "autosetup.ui.menuPairEntrySignal");

        assertTrue(window.width < window.sentenceOnOneLine, "the Entry Guard Signal window is " + window.width + " pixels"
            + " wide and its sentence is " + window.sentenceOnOneLine + " on one line - the window is as wide as the"
            + " sentence.  Adam, MT-479: \"make the sentence split over 2 lines so the window isn't too wide\"");
    }

    /**
     * Neither window's first sentence takes more than two lines.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testNeitherFirstSentenceTakesMoreThanTwoLines() throws Exception
    {
        for (String[] guard : new String[][] { { "EXIT", "autosetup.ui.menuPairSignal" },
            { "ENTRY", "autosetup.ui.menuPairEntrySignal" } })
        {
            Measured window = open(guard[0], guard[1]);

            assertTrue(window.sentenceHeight <= window.lineHeight * 5 / 2, "the " + I18n.t(guard[1]) + " window's"
                + " sentence is " + window.sentenceHeight + " pixels tall at " + window.lineHeight + " a line - more than"
                + " the two lines Adam asked for, which trades a wide window for a tall one");

            closeTheWindows();
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** What was read off an open guard window. */
    private static final class Measured
    {
        int width;
        int sentenceOnOneLine;
        int sentenceHeight;
        int lineHeight;
    }

    /**
     * Opens one guard's window through `askAboutSignals`, which the menu item calls, and measures it.
     */
    private Measured open(String guard, String titleKey) throws Exception
    {
        final AutonomyEditorPanel[] panel = new AutonomyEditorPanel[1];

        SwingUtilities.invokeAndWait(() -> panel[0] = new AutonomyEditorPanel(session, "main", () -> { }));

        Class<?> kind = null;

        for (Class<?> inner : AutonomyEditorPanel.class.getDeclaredClasses())
        {
            if (inner.isEnum() && inner.getSimpleName().equals("Guard")) kind = inner;
        }

        assertNotNull(kind, "precondition: the panel has no Guard kind");

        Object which = null;

        for (Object constant : kind.getEnumConstants())
        {
            if (((Enum<?>) constant).name().equals(guard)) which = constant;
        }

        final Object chosen = which;

        final java.lang.reflect.Method ask = AutonomyEditorPanel.class.getDeclaredMethod("askAboutSignals", kind, TileKey.class);

        ask.setAccessible(true);

        SwingUtilities.invokeLater(() ->
        {
            try { ask.invoke(panel[0], chosen, STATION); } catch (Exception e) { throw new RuntimeException(e); }
        });

        final String title = I18n.t(titleKey);

        javax.swing.JDialog dialog = null;

        long giveUp = System.currentTimeMillis() + 10000;

        while (dialog == null && System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) dialog = (javax.swing.JDialog) window;
            }

            if (dialog == null) Thread.sleep(50);
        }

        if (dialog == null) fail("no " + title + " window opened");

        final javax.swing.JDialog open = dialog;
        final Measured measured = new Measured();

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.JLabel heading = firstLabel(open.getContentPane());

            assertNotNull(heading, "precondition: the window has no heading");

            java.awt.FontMetrics metrics = heading.getFontMetrics(heading.getFont());

            String shown = heading.getText().replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").trim();

            measured.width = open.getWidth();
            measured.sentenceOnOneLine = metrics.stringWidth(shown);
            // The sentence as laid out at the width it wraps to - not the label's preferred height, which is kept at the
            // taller of the window's two sentences.
            javax.swing.text.View view = (javax.swing.text.View) heading.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);

            measured.sentenceHeight = view == null ? heading.getPreferredSize().height
                : (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS));
            measured.lineHeight = metrics.getHeight();
        });

        return measured;
    }

    private static javax.swing.JLabel firstLabel(java.awt.Container container)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JLabel) return (javax.swing.JLabel) child;

            if (child instanceof java.awt.Container)
            {
                javax.swing.JLabel found = firstLabel((java.awt.Container) child);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static void closeTheWindows() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()) window.dispose();
            }
        });

        // The window's own loop returns once it is gone, and the panel's clean-up after it runs on the event thread.
        SwingUtilities.invokeAndWait(() -> { });
    }
}
