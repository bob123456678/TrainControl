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
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * FR-065: Escape closes the editor - after it has let go of whatever the editor was holding.
 *
 * Adam, 2026-09-08: *"escape closes autonomy/track editor - same as closing via button, with warning
 * shown as needed."*
 *
 * **AND THE ORDER IS THE WHOLE DESIGN, because two open findings ask for the same key.**  The
 * independent review of 2026-09-09 files B5 - *"Escape does not put an armed editor tool down"* -
 * against the same keystroke: `AutonomyEditorPanel.installEscape` binds it `WHEN_IN_FOCUSED_WINDOW`,
 * which `LayoutEditor`'s own comment records as dead in this window (the focus owner is the frame, a
 * non-JComponent, so key bindings are never consulted), and the frame's `formKeyPressed` had its
 * Escape branch BELOW `if (isAutonomyMode()) return;` - so in autonomy mode nothing handled Escape at
 * all.
 *
 * Made to close outright, Escape would take a half-finished gesture with it, and take it through
 * `mayLeave` - so a user who armed "Test a path" by mistake, pressed Escape to think again, and had
 * touched anything would be asked whether to throw their edits away.  That is the opposite of what a
 * get-me-out key is for, and it is the more expensive of the two mistakes: pressing Escape twice costs
 * nothing, and there is no way back from a window that closed.
 *
 * So: **Escape lets go first, and closes only when there is nothing left to let go of.**  That is what
 * every editor does, it satisfies FR-065 as asked - one press closes an editor nobody is holding
 * anything in, which is the state it is in almost all the time - and it is B5's remedy in the same
 * branch, on the one path that actually runs.
 *
 * **BEHAVIOURALLY, at the key.**  Every test here dispatches a real `KEY_PRESSED` to the frame and
 * then asks what happened to the window.  Dispatched at the frame rather than posted to the queue,
 * which `testTheWindowTakesTheKeyboard` explains: the queue routes by the OS's idea of the focus
 * owner, and in a test JVM that is usually a terminal.  The frame is the focus owner in this window by
 * construction - `setFocusable(true)`, and every control `setFocusable(false)`.
 *
 * A FRESH EDITOR PER TEST, because the point of half of them is that the window is gone afterwards.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
 *
 * MUTATION: put the Escape branch back below `if (isAutonomyMode()) return;` and the two autonomy
 * tests go red; make it call `confirmExit()` unconditionally and the two "let go" tests go red with
 * the window disposed.
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

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no autonomy mode to open");
        }

        page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);
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
     * Escape closes the track diagram editor when nothing is being held.
     *
     * The state the editor is in almost all the time, and the one FR-065 is about.  `mayLeave` lets a
     * clean editor go without asking - `canUndo()` is false on one nobody has edited - so this is the
     * "no warning needed" half of *"with warning shown as needed"*.
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
     * And the autonomy editor, which is the same window with the autonomy column showing.
     *
     * This is the half that had NO Escape handling at all: the frame's branch sat below
     * `if (isAutonomyMode()) return;`, and the panel's own binding is `WHEN_IN_FOCUSED_WINDOW`, which
     * this window's own comment records as never firing.
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
     * An armed tool is put down, and the window stays open (B5).
     *
     * Adam armed "Test a path" or "Why is it not moving", changed his mind, and pressed Escape.  The
     * button stayed pressed and the tool stayed armed, so the next click on the diagram was swallowed
     * by the gesture - which the panel's own javadoc names as the hazard, because a click that falls
     * through to `cycle()` CHANGES a square.
     *
     * Closing the window instead would be a worse answer than either: it would throw the gesture away
     * along with everything else, through the unsaved-work prompt.
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
     * And in the track editor, the picking mode goes rather than the window.
     *
     * The same rule on the other surface.  `setSelectMode` is what the picking button turns on, and a
     * drag with it on picks squares - so Escape while it is on means "stop picking", not "close the
     * editor I am in the middle of using".
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
     * A second Escape then closes it.
     *
     * THE CONTROL for the two above, and the reason "let go first" is not a way of never closing.
     * Without this, an Escape that did nothing at all would pass both of them.
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

    /**
     * A fresh editor on the frozen railway's first page.
     *
     * @param autonomy whether to put it in autonomy mode
     * @return the window, rendered and laid out
     */
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
     * Arms the "Test a path" tool the way a user does - by pressing its button.
     *
     * Through the button rather than the field, so the action listener that actually arms the tool is
     * what runs.  Reached by reflection because the palette is the panel's own business; what is being
     * tested is what happens when it has been used.
     *
     * @param editor the editor, in autonomy mode
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
     * Presses Escape on the window.
     *
     * Dispatched at the FRAME, not posted to the queue: the queue routes by the operating system's
     * idea of the focus owner, which in a test JVM is usually something else entirely.  The frame is
     * the focus owner in this window by construction - it is `setFocusable(true)` and every control in
     * it is `setFocusable(false)`, which is what `formKeyPressed` exists to take advantage of.
     *
     * @param editor the window
     */
    private static void escape(final LayoutEditor editor) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> editor.dispatchEvent(new KeyEvent(editor,
            KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE,
            KeyEvent.CHAR_UNDEFINED)));

        // `formKeyPressed` does all its work inside an `invokeLater`, and `confirmExit` posts again
        settle();
    }

    /**
     * @param editor a window that may already have closed itself
     */
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
