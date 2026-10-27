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
 * editor.*).
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
