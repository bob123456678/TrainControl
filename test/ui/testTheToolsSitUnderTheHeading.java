package ui;

import java.awt.Component;
import java.awt.Container;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The autonomy editor's Test a Path button sits right under the window's heading, with only the form's own gap between
 * (Adam: *just remove the gap between the label and the "test path" "why isn't it moving" buttons in the autonomy
 * editor.*), and Page Settings sits under the mode buttons in Autonomy Setup only.
 *
 * Measured in shown editor windows on the frozen railway, never `cs2_sample_layout` (OB-111).
 *
 * @author Adam
 */
public class testTheToolsSitUnderTheHeading
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutDiagram page;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the gap is measured in a shown window, and a window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration, so there is no autonomy editor to open");
        }

        page = model.getLayout(PAGE);

        if (page == null) throw new AssertionError("no page " + PAGE);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            close(editor);

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
     * Test a Path's top is no further below the heading's bottom than the form's own gap between the heading and the
     * column, measured at 6 px (FlatLaf, 100%); the ordinary editor's first palette piece sits 8 px under its heading.
     *
     * MUTATION: give the panel a 6 px top inset (13 px under the heading), or the first row its 1 px top gap (7 px),
     * and this fails.
     */
    @Test
    public void testTestAPathSitsRightUnderTheHeading() throws Exception
    {
        editor = shown(null);

        final int[] palette = new int[2];

        SwingUtilities.invokeAndWait(() ->
        {
            palette[0] = formGap(editor);
            palette[1] = below(editor, firstPiece(editor));
        });

        close(editor);

        editor = shown(session);

        final int[] autonomy = new int[2];

        SwingUtilities.invokeAndWait(() ->
        {
            autonomy[0] = formGap(editor);
            autonomy[1] = below(editor, testButton(editor.getAutonomyPanel()));
        });

        System.out.println("Heading gaps: form " + autonomy[0] + " px, Test a Path " + autonomy[1]
            + " px; ordinary editor: form " + palette[0] + " px, first palette piece " + palette[1] + " px");

        assertTrue(autonomy[0] >= 0, "precondition: the column starts above the heading's bottom (" + autonomy[0]
            + " px), so the window is not laid out as the form lays it out");

        assertTrue(autonomy[1] <= autonomy[0], "Test a Path sits " + autonomy[1] + " px under the autonomy editor's"
            + " heading, more than the form's own " + autonomy[0] + " px gap (the ordinary editor's first palette piece"
            + " sits " + palette[1] + " px under its heading).  Adam: *just remove the gap between the label and the"
            + " \"test path\" \"why isn't it moving\" buttons in the autonomy editor.*");
    }

    /**
     * The Page Settings heading and its Exclude Page box sit in the side strip below the Layout and Autonomy Setup
     * buttons, in that order, and neither shows in Layout mode, opened there or switched to with its button.
     *
     * MUTATION: in `showPageSettings`, add the heading and show the panel in either mode, or leave out
     * `pageSettings.add(exclude)`, and this fails.
     */
    @Test
    public void testPageSettingsSitUnderTheModeButtonsInAutonomySetupOnly() throws Exception
    {
        final String heading = org.traincontrol.util.I18n.t("autosetup.ui.labelPageSettings");

        editor = shown(null);

        SwingUtilities.invokeAndWait(() -> assertTrue(showingLabels(editor, heading).isEmpty(),
            "Layout mode shows a Page Settings heading"));

        close(editor);

        editor = shown(session);

        final LayoutEditor autonomyEditor = editor;

        SwingUtilities.invokeAndWait(() ->
        {
            Component box = autonomyEditor.getAutonomyPanel().getExcludePage();
            Component sidebar = field(autonomyEditor, LayoutEditor.class, "sidebar");
            Component modeButton = field(autonomyEditor, LayoutEditor.class, "autonomyModeButton");

            assertTrue(box.isShowing(), "Autonomy Setup does not show Exclude Page");

            assertTrue(SwingUtilities.isDescendingFrom(box, (Container) sidebar),
                "Exclude Page is not in the side strip with the mode buttons");

            java.util.List<Component> labels = showingLabels(autonomyEditor, heading);

            assertTrue(labels.size() == 1, "Autonomy Setup shows " + labels.size() + " Page Settings headings, not one");

            int buttonBottom = top(modeButton) + modeButton.getHeight();
            int headingTop = top(labels.get(0));
            int headingBottom = headingTop + labels.get(0).getHeight();

            assertTrue(headingTop >= buttonBottom, "the Page Settings heading (top " + headingTop
                + ") is not below the Autonomy Setup button (bottom " + buttonBottom + ")");

            assertTrue(top(box) >= headingBottom, "Exclude Page (top " + top(box)
                + ") is not below its heading (bottom " + headingBottom + ")");
        });

        // Back to Layout by its own button: the setup is unchanged, so nothing is asked.
        final javax.swing.AbstractButton toLayout = field(editor, LayoutEditor.class, "trackModeButton");

        SwingUtilities.invokeLater(() -> toLayout.doClick());

        for (long end = System.currentTimeMillis() + 15000; editor.isAutonomyMode() && System.currentTimeMillis() < end; )
        {
            Thread.sleep(50);
        }

        SwingUtilities.invokeAndWait(() -> { });

        assertTrue(!editor.isAutonomyMode(), "precondition: the Layout button did not leave Autonomy Setup in 15 s");

        SwingUtilities.invokeAndWait(() -> assertTrue(showingLabels(autonomyEditor, heading).isEmpty(),
            "Page Settings still shows after switching to Layout"));

        close(editor);

        editor = null;
    }

    /**
     * The page list keeps its height and place when the editor switches between the track diagram and Autonomy Setup,
     * both ways, by the mode buttons: the strip's spare room goes below it, not into it.
     *
     * MUTATION: drop the page list's height cap in `buildPageControl` (back to `Short.MAX_VALUE`), and this fails.
     */
    @Test
    public void testThePageListKeepsItsHeightAcrossModes() throws Exception
    {
        editor = shown(null);

        final int[] track = bounds(editor);

        switchTo(editor, "autonomyModeButton", true);

        final int[] autonomy = bounds(editor);

        switchTo(editor, "trackModeButton", false);

        final int[] back = bounds(editor);

        System.out.println("Page list (top, height): track diagram " + track[0] + ", " + track[1] + "; Autonomy Setup "
            + autonomy[0] + ", " + autonomy[1] + "; track diagram again " + back[0] + ", " + back[1]);

        assertTrue(autonomy[1] == track[1] && back[1] == track[1], "the page list's height jumps between modes: "
            + track[1] + " px in the track diagram, " + autonomy[1] + " px in Autonomy Setup, " + back[1] + " px back");

        assertTrue(autonomy[0] == track[0] && back[0] == track[0], "the page list moves between modes: top at "
            + track[0] + ", " + autonomy[0] + ", " + back[0]);

        close(editor);

        editor = null;
    }

    /** The page list's top in the window and its height, laid out. */
    private static int[] bounds(final LayoutEditor on) throws Exception
    {
        final int[] out = new int[2];

        SwingUtilities.invokeAndWait(() ->
        {
            on.validate();

            Component list = field(on, LayoutEditor.class, "pageList");

            assertTrue(list.isShowing(), "precondition: the page list is not on the screen");

            out[0] = SwingUtilities.convertPoint(list.getParent(), list.getLocation(), on.getContentPane()).y;
            out[1] = list.getHeight();
        });

        return out;
    }

    /** Presses a mode button, as a click does, and waits for the mode to change. */
    private static void switchTo(final LayoutEditor on, String button, boolean autonomy) throws Exception
    {
        final javax.swing.AbstractButton b = field(on, LayoutEditor.class, button);

        SwingUtilities.invokeLater(() -> b.doClick());

        for (long end = System.currentTimeMillis() + 15000; on.isAutonomyMode() != autonomy
            && System.currentTimeMillis() < end; )
        {
            Thread.sleep(50);
        }

        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertTrue(on.isAutonomyMode() == autonomy, "precondition: " + button + " did not switch mode in 15 s");
    }

    /** Every label on the screen in the window that reads the text. */
    private static java.util.List<Component> showingLabels(Container in, String text)
    {
        java.util.List<Component> out = new java.util.ArrayList<>();

        for (Component c : in.getComponents())
        {
            if (c instanceof javax.swing.JLabel && c.isShowing() && text.equals(((javax.swing.JLabel) c).getText()))
            {
                out.add(c);
            }

            if (c instanceof Container) out.addAll(showingLabels((Container) c, text));
        }

        return out;
    }

    /** A component's top, in screen pixels, so two in different panels compare. */
    private static int top(Component c)
    {
        return c.getLocationOnScreen().y;
    }

    /** An editor on the page, shown and laid out: the ordinary one for a null session. */
    private static LayoutEditor shown(final AutonomySession autonomy) throws Exception
    {
        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();

            if (autonomy != null) built[0].setAutonomyMode(autonomy);
        });

        // render() queues the showing.
        for (long end = System.currentTimeMillis() + 15000; !built[0].isShowing() && System.currentTimeMillis() < end; )
        {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(50);
        }

        assertTrue(built[0].isShowing(), "precondition: the editor window did not show within 15 s");

        SwingUtilities.invokeAndWait(() -> built[0].validate());
        SwingUtilities.invokeAndWait(() -> { });

        return built[0];
    }

    private static void close(final LayoutEditor closing) throws Exception
    {
        if (closing != null) SwingUtilities.invokeAndWait(() -> closing.dispose());
    }

    /** The form's own gap between the heading and the column under it. */
    private static int formGap(LayoutEditor on)
    {
        Component heading = field(on, LayoutEditor.class, "jLabel1");
        Component column = field(on, LayoutEditor.class, "newComponents");

        return SwingUtilities.convertPoint(column.getParent(), column.getLocation(), heading.getParent()).y
            - (heading.getY() + heading.getHeight());
    }

    /** How far a component's top is below the bottom of the heading. */
    private static int below(LayoutEditor on, Component c)
    {
        Component heading = field(on, LayoutEditor.class, "jLabel1");

        assertTrue(c.isShowing(), "precondition: " + c.getClass().getSimpleName() + " is not on the screen");

        return SwingUtilities.convertPoint(c.getParent(), c.getLocation(), heading.getParent()).y
            - (heading.getY() + heading.getHeight());
    }

    private static Component firstPiece(LayoutEditor on)
    {
        Container palette = field(on, LayoutEditor.class, "newComponents");

        assertTrue(palette.getComponentCount() > 0, "precondition: the ordinary editor's palette is empty");

        return palette.getComponent(0);
    }

    private static Component testButton(AutonomyEditorPanel panel)
    {
        assertNotNull(panel, "precondition: the autonomy editor has no tools panel");

        return field(panel, AutonomyEditorPanel.class, "testButton");
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object of, Class<?> in, String name)
    {
        try
        {
            java.lang.reflect.Field f = in.getDeclaredField(name);

            f.setAccessible(true);

            T value = (T) f.get(of);

            assertNotNull(value, "precondition: " + in.getSimpleName() + "." + name + " is null");

            return value;
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("there is no " + in.getSimpleName() + "." + name, e);
        }
    }
}
