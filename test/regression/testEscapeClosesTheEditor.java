package regression;

import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * FR-065: Escape closes the editor, after it has let go of whatever the editor was holding.
 *
 * Adam: *"escape closes autonomy/track editor - same as closing via button, with warning shown as needed."*
 *
 * The order is the whole design.  Escape in autonomy mode was handled nowhere: `AutonomyEditorPanel.installEscape`
 * binds it `WHEN_IN_FOCUSED_WINDOW`, which is dead in this window (the focus owner is the frame, a non-JComponent), and
 * the frame's `formKeyPressed` had its Escape branch BELOW `if (isAutonomyMode()) return;`.  Made to close outright,
 * Escape would take a half-finished gesture with it, through `mayLeave`: a user who armed "Test a path" by mistake and
 * pressed Escape to think again would be asked whether to throw their edits away.  Pressing Escape twice costs nothing
 * and there is no way back from a window that closed, so Escape lets go first and closes only when there is nothing
 * left to let go of.
 *
 * Behaviorally, at the key: every test dispatches a real `KEY_PRESSED` to the frame (not posted to the queue, which
 * routes by the OS's idea of the focus owner; see `testTheWindowTakesTheKeyboard`) and asks what happened to the
 * window.  A fresh editor per test, because the point of half of them is that the window is gone afterwards.  On the
 * frozen railway, never `cs2_sample_layout` (OB-111).
 *
 * MUTATION: put the Escape branch back below `if (isAutonomyMode()) return;` and the two autonomy tests go red; make it
 * call `confirmExit()` unconditionally and the two "let go" tests go red with the window disposed.
 *
 * @author Adam
 */
public class testEscapeClosesTheEditor
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutDiagram page;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor's grid and its keyboard need a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration, so there is no autonomy mode to open");
        }

        page = model.getLayout(PAGE);

        if (page == null) throw new AssertionError("no page " + PAGE);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
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

    // ---------------------------------------------------------------- closing

    /**
     * Escape closes the track diagram editor when nothing is being held: `mayLeave` lets a clean editor go without
     * asking, the "no warning needed" half of *"with warning shown as needed"*.
     */
    @Test
    public void testEscapeClosesTheTrackEditor() throws Exception
    {
        LayoutEditor editor = opened(false);

        try
        {
            escape(editor);

            assertFalse(editor.isDisplayable(),
                "Escape did not close the track diagram editor, and nothing was armed for it to let go"
                + " of instead.  That is FR-065: \"escape closes autonomy/track editor - same as"
                + " closing via button\"");
        }
        finally
        {
            dispose(editor);
        }
    }

    /**
     * And the autonomy editor, the same window with the autonomy column showing: this half had NO Escape handling at
     * all.
     */
    @Test
    public void testEscapeClosesTheAutonomyEditor() throws Exception
    {
        LayoutEditor editor = opened(true);

        try
        {
            assertTrue(editor.isAutonomyMode(), "the editor did not open in autonomy mode");

            escape(editor);

            assertFalse(editor.isDisplayable(),
                "Escape did not close the autonomy editor.  Its branch used to sit below the"
                + " `isAutonomyMode()` guard in `formKeyPressed`, so this window dropped the key"
                + " entirely - and `AutonomyEditorPanel.installEscape` cannot cover it, because a"
                + " WHEN_IN_FOCUSED_WINDOW binding is never consulted while the FRAME holds the focus"
                + " (B5)");
        }
        finally
        {
            dispose(editor);
        }
    }

    // ---------------------------------------------------------------- letting go first

    /**
     * An armed tool is put down, and the window stays open: the button stayed pressed and the tool armed, so the next
     * click on the diagram was swallowed by the gesture (a click that falls through to `cycle()` CHANGES a square).
     * Closing instead would throw the gesture away through the unsaved-work prompt.
     */
    @Test
    public void testEscapePutsAnArmedToolDownRatherThanClosing() throws Exception
    {
        LayoutEditor editor = opened(true);

        try
        {
            arm(editor);

            assertEquals(String.valueOf(editor.getAutonomyPanel().getTool()), "TEST",
                "the tool was not armed, so nothing below tests anything");

            escape(editor);

            assertEquals(String.valueOf(editor.getAutonomyPanel().getTool()), "NONE",
                "Escape did not put the armed tool down (B5).  The next click on the diagram would be"
                + " swallowed by the gesture, and a click that falls through to `cycle()` changes a"
                + " square");

            assertTrue(editor.isDisplayable(),
                "Escape closed the editor while a tool was armed.  Letting go of the gesture is what"
                + " the user meant; closing takes the gesture away along with the window, and through"
                + " the unsaved-work prompt.  A second Escape is what closes it");
        }
        finally
        {
            dispose(editor);
        }
    }

    /**
     * And in the track editor, the picking mode goes rather than the window: `setSelectMode` is what the picking button
     * turns on.
     */
    @Test
    public void testEscapeDropsThePickingModeRatherThanClosing() throws Exception
    {
        LayoutEditor editor = opened(false);

        try
        {
            SwingUtilities.invokeAndWait(() -> editor.setSelectMode(true));

            assertTrue(editor.isSelectMode(), "the picking mode did not turn on");

            escape(editor);

            assertFalse(editor.isSelectMode(), "Escape did not turn the picking mode off");

            assertTrue(editor.isDisplayable(),
                "Escape closed the track editor while it was in picking mode.  The mode is something"
                + " the editor is holding, and Escape lets go of what it is holding before it closes");
        }
        finally
        {
            dispose(editor);
        }
    }

    /**
     * A second Escape then closes it: the control for the two above, since an Escape that did nothing would pass both.
     */
    @Test
    public void testASecondEscapeClosesIt() throws Exception
    {
        LayoutEditor editor = opened(true);

        try
        {
            arm(editor);

            escape(editor);

            assertTrue(editor.isDisplayable(), "the first Escape closed it, so this tests nothing");

            escape(editor);

            assertFalse(editor.isDisplayable(),
                "the tool was put down by the first Escape and the second did not close the editor, so"
                + " an armed tool leaves the window unclosable by keyboard");
        }
        finally
        {
            dispose(editor);
        }
    }

    // ---------------------------------------------------------------- the fixture

    /** A fresh editor on the frozen railway's first page, in autonomy mode or not. */
    private static LayoutEditor opened(final boolean autonomy) throws Exception
    {
        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);

            built[0].render();

            if (autonomy) built[0].setAutonomyMode(session);
        });

        settle();

        return built[0];
    }

    /**
     * Arms the "Test a path" tool the way a user does, through its button so the action listener that arms the tool
     * runs; reached by reflection.
     */
    private static void arm(final LayoutEditor editor) throws Exception
    {
        java.lang.reflect.Field button = editor.getAutonomyPanel().getClass()
            .getDeclaredField("testButton");

        button.setAccessible(true);

        final javax.swing.JToggleButton arming =
            (javax.swing.JToggleButton) button.get(editor.getAutonomyPanel());

        assertNotNull(arming, "there is no Test a path button to arm");

        SwingUtilities.invokeAndWait(() -> arming.doClick());

        settle();
    }

    /**
     * Presses Escape on the window, dispatched at the FRAME: it is the focus owner by construction
     * (`setFocusable(true)`, every control `setFocusable(false)`).
     */
    private static void escape(final LayoutEditor editor) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> editor.dispatchEvent(new KeyEvent(editor,
            KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE,
            KeyEvent.CHAR_UNDEFINED)));

        // `formKeyPressed` does all its work inside an `invokeLater`, and `confirmExit` posts again
        settle();
    }

    /** Disposes a window that may already have closed itself. */
    private static void dispose(final LayoutEditor editor) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> editor.dispose());

        settle();
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 5; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
