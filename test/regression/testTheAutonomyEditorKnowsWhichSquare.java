package regression;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import support.SourceOf;

/**
 * In autonomy mode the editor knows which square the pointer is over, and the naming shortcut asks it (MT-258).
 *
 * `getLastHoveredLabel()` reads `lastHoveredX/Y`, which `receiveMoveEvent` deliberately does not set in autonomy mode:
 * they are where a paste would land, and nothing is pasted there.  So Control+S asked for the square at -1,-1 and
 * returned.  Autonomy mode now records the square in a field of its own and leaves the placement variables untouched,
 * which makes the third test the important one: a fix that fed the placement variables would pass the first two and
 * break dragging.
 */
public class testTheAutonomyEditorKnowsWhichSquare
{
    /** Everything one of these tests needs, torn down in one place. */
    private static final class Editor
    {
        support.LayoutSandbox sandbox;

        org.traincontrol.marklin.MarklinControlStation model;

        org.traincontrol.gui.TrainControlUI ui;

        org.traincontrol.gui.layouteditor.LayoutEditor editor;

        org.traincontrol.gui.LayoutGrid grid;

        void close() throws Exception
        {
            if (editor != null)
            {
                final org.traincontrol.gui.layouteditor.LayoutEditor window = editor;

                javax.swing.SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            if (ui != null)
            {
                final org.traincontrol.gui.TrainControlUI window = ui;

                javax.swing.SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A layout editor over a small diagram, in autonomy mode or in track mode.  The sandbox is opened before the model:
     * constructing a TrainControlUI reads the layout-path preference (OB-111).
     */
    private static Editor open(boolean autonomy) throws Exception
    {
        Editor open = new Editor();

        try
        {
            open.sandbox = support.LayoutSandbox.open();

            open.model = support.TestStation.simulatedInDebug();

            final org.traincontrol.marklin.MarklinControlStation model = open.model;
            final org.traincontrol.gui.TrainControlUI[] made = new org.traincontrol.gui.TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    made[0] = new org.traincontrol.gui.TrainControlUI();
                    made[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            open.ui = made[0];

            java.io.File folder = java.nio.file.Files.createTempDirectory("tc-which-square").toFile();

            final LayoutDiagram diagram = new LayoutDiagram("Naming Page", 12, 8, null, null);

            for (int x = 2; x <= 6; x++)
            {
                diagram.addComponent(componentType.STRAIGHT, x, 2, 0, 0, 0, 0,
                    accessoryDecoderType.MM2, null);
            }

            diagram.addComponent(componentType.FEEDBACK, 4, 2, 0, 0, 9, 21,
                accessoryDecoderType.MM2, null);

            diagram.setEdit(true);
            diagram.checkBounds();

            final AutonomySession session = new AutonomySession(folder);

            session.open(Arrays.asList(diagram));

            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(open.ui, session);

            final org.traincontrol.gui.layouteditor.LayoutEditor[] built = new org.traincontrol.gui.layouteditor.LayoutEditor[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
                built[0] = new org.traincontrol.gui.layouteditor.LayoutEditor(diagram, 30, made[0], 0));

            open.editor = built[0];

            java.lang.reflect.Method drawGrid =
                org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredMethod("drawGrid");
            drawGrid.setAccessible(true);

            java.lang.reflect.Field gridField =
                org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredField("grid");
            gridField.setAccessible(true);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    if (autonomy) built[0].setAutonomyMode(session);

                    drawGrid.invoke(built[0]);

                    open.grid = (org.traincontrol.gui.LayoutGrid) gridField.get(built[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            return open;
        }
        catch (Exception | Error failed)
        {
            // Whatever failed, the preference goes back: `init` binds a UDP port and a window constructor can fail, and
            // the caller's holder does not exist when this throws.
            open.close();

            throw failed;
        }
    }

    /** A private field of the editor, by name. */
    private static Object field(org.traincontrol.gui.layouteditor.LayoutEditor of, String name) throws Exception
    {
        java.lang.reflect.Field f =
            org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(of);
    }

    /** A plain move event over a label, which is all the handler reads off it. */
    private static java.awt.event.MouseEvent over(org.traincontrol.gui.LayoutLabel label)
    {
        return new java.awt.event.MouseEvent(label, java.awt.event.MouseEvent.MOUSE_MOVED,
            0L, 0, 1, 1, 0, false, java.awt.event.MouseEvent.NOBUTTON);
    }

    /**
     * Moves the pointer over a square and hands back the label it moved over.  The grid is read at the moment of the
     * move: the editor redraws it after the constructor returns, and `LayoutGrid.getCoordinates` compares labels by
     * identity, so an earlier label is not in the grid the handler consults.
     */
    private static org.traincontrol.gui.LayoutLabel hover(org.traincontrol.gui.layouteditor.LayoutEditor on,
        int x, int y) throws Exception
    {
        java.lang.reflect.Field gridField =
            org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredField("grid");
        gridField.setAccessible(true);

        final org.traincontrol.gui.LayoutLabel[] moved = new org.traincontrol.gui.LayoutLabel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                org.traincontrol.gui.LayoutGrid grid =
                    (org.traincontrol.gui.LayoutGrid) gridField.get(on);

                moved[0] = grid.getValueAt(x, y);

                if (moved[0] != null) on.receiveMoveEvent(over(moved[0]), moved[0]);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        return moved[0];
    }

    /**
     * Moving the pointer over a square in autonomy mode is remembered: the state Control+S was missing.
     *
     * MUTATION this catches: delete `autonomyHover = label;` from `receiveMoveEvent`'s autonomy branch.
     */
    @Test(timeOut = 300000)
    public void testTheEditorRemembersTheSquareUnderThePointer() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the editor is a window");
        }

        Editor open = open(true);

        try
        {
            assertTrue(open.editor.isAutonomyMode(),
                "precondition: the editor is not in autonomy mode, so this says nothing about the "
                + "mode the shortcut was broken in");

            assertNull(field(open.editor, "autonomyHover"),
                "precondition: the editor already thinks the pointer is somewhere, before it has "
                + "been moved anywhere");

            org.traincontrol.gui.LayoutLabel square = hover(open.editor, 4, 2);

            assertNotNull(square, "the fixture has no square at 4,2");

            assertSame(field(open.editor, "autonomyHover"), square,
                "after moving the pointer over a square in autonomy mode the editor still does not "
                + "know which square that was - which is why Control+S 'did not fire': it fired, "
                + "asked which square, and was told none");
        }
        finally
        {
            open.close();
        }
    }

    /**
     * And the naming shortcut asks that, not the placement variables.
     *
     * Read out of the source because the caller asked the wrong question, which a behavioral test cannot see when it
     * can only reach the caller through a modal dialog (`promptName` would hang the battery).
     *
     * MUTATION this catches: put `getLastHoveredLabel()` back in the Control+S branch.  The test above still passes
     * - the field is still being set - and the shortcut is broken again.
     */
    @Test
    public void testTheNamingShortcutAsksTheSquareAutonomyModeTracks() throws Exception
    {
        // Without its comments: the branch's own comment names the field and the method, and was read as the defect.
        // formKeyPressed is every window's key handler, so it is cut out of the editor's own file.
        String body = SourceOf.withoutComments(SourceOf.declarationIn(
            SourceOf.fileDeclaring("public class LayoutEditor extends"),
            "private void formKeyPressed(java.awt.event.KeyEvent evt)"));

        int at = body.indexOf("evt.getKeyCode() == KeyEvent.VK_S");

        assertTrue(at > 0, "the Control+S branch is gone from the editor altogether");

        // The branch's own body: from the test down to the next `return;`.
        int ends = body.indexOf("return;", at);

        assertTrue(ends > at, "the Control+S branch never returns, so its body cannot be read");

        String branch = body.substring(at, ends);

        // `hoveredSquare()` since OB-198: one question that forgets a label not on the grid it is asked about.
        // `regression.testTheHoveredSquareIsForgotten` asserts the behavior; this checks which door the key goes
        // through.
        assertTrue(branch.contains("hoveredSquare()"),
            "the Control+S branch does not ask the square autonomy mode tracks.  What it does ask:\n"
            + branch);

        assertFalse(branch.contains("autonomyHover"),
            "the Control+S branch reads `autonomyHover` itself again. Nothing clears that field on a"
            + " page change, so the key would name a square on the page before - which is OB-198,"
            + " and `hoveredSquare()` is the one door that refuses it:\n" + branch);

        assertFalse(branch.contains("getLastHoveredLabel"),
            "the Control+S branch asks getLastHoveredLabel(), which autonomy mode deliberately never "
            + "sets - so the key fires, finds nothing, and does nothing, which is exactly what Adam "
            + "reported");
    }

    /**
     * And track mode still feeds the placement variables, which are not this fix's to touch.  The wrong fix is one line
     * and passes both tests above: set `lastHoveredX/Y` in the autonomy branch too.  In track mode they are still set,
     * in autonomy mode still left alone.
     *
     * MUTATION this catches: adding `lastHoveredX = getX(label);` to the autonomy branch.
     */
    @Test(timeOut = 300000)
    public void testTrackModeStillFeedsThePlacementVariables() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the editor is a window");
        }

        Editor track = open(false);

        try
        {
            assertFalse(track.editor.isAutonomyMode(),
                "precondition: this half of the rule is about track mode");

            assertNotNull(hover(track.editor, 4, 2), "the fixture has no square at 4,2");

            assertEquals(field(track.editor, "lastHoveredX"), 4,
                "hovering in TRACK mode no longer records where a paste would land - which is what "
                + "drags and pastes read, and nothing to do with the shortcut this was fixing");

            assertEquals(field(track.editor, "lastHoveredY"), 2,
                "hovering in TRACK mode no longer records where a paste would land");
        }
        finally
        {
            track.close();
        }

        Editor autonomy = open(true);

        try
        {
            hover(autonomy.editor, 4, 2);

            assertEquals(field(autonomy.editor, "lastHoveredX"), -1,
                "hovering in AUTONOMY mode now teaches the placement code a position, which is what "
                + "receiveMoveEvent's own comment says not to do: 'they are where a paste would "
                + "land, and nothing is pasted here'");
        }
        finally
        {
            autonomy.close();
        }
    }

    /**
     * A square a click changes shows a hand and says so, and one it does not shows neither (FR-112).
     *
     * Three cases: track in the run, which a click turns; a blank square, which it does not; and that track again once
     * the Test tool is armed, with the pointer held still - the panel's refresh reaches the diagram, so the hand goes
     * when the button is pressed rather than at the next move.
     *
     * MUTATION: leave the hand or the line off, or stop the refresh asking again, and this fails.
     */
    @Test(timeOut = 300000)
    public void testAClickableSquareShowsAHandAndSaysSo() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the editor is a window");
        }

        Editor open = open(true);

        try
        {
            org.traincontrol.gui.LayoutLabel track = hover(open.editor, 3, 2);

            assertNotNull(track, "the fixture has no square at 3,2");

            assertEquals(cursorOf(track), java.awt.Cursor.HAND_CURSOR, "the pointer over track a click turns is not a"
                + " hand, so nothing says the square can be clicked (FR-112)");

            String says = org.traincontrol.util.I18n.t("autosetup.ui.tooltipClickToChangeDirections");

            assertTrue(String.valueOf(track.getToolTipText()).contains(says), "track a click turns does not say so: "
                + track.getToolTipText());

            org.traincontrol.gui.LayoutLabel blank = hover(open.editor, 3, 5);

            assertNotNull(blank, "the fixture has no square at 3,5");

            assertNotEquals(cursorOf(blank), java.awt.Cursor.HAND_CURSOR, "a blank square shows a hand, and a click there"
                + " does nothing");

            assertFalse(String.valueOf(blank.getToolTipText()).contains(says), "a blank square says a click changes its"
                + " directions: " + blank.getToolTipText());

            // The test tool armed, with the pointer back on the track and held still
            final org.traincontrol.gui.LayoutLabel again = hover(open.editor, 3, 2);

            final javax.swing.JToggleButton test = (javax.swing.JToggleButton) panelField(open.editor, "testButton");

            javax.swing.SwingUtilities.invokeAndWait(() -> test.doClick());

            for (int i = 0; i < 4; i++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            assertEquals(open.editor.getAutonomyPanel().getTool(), org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.Tool.TEST,
                "precondition: pressing Test did not arm it");

            assertNotEquals(cursorOf(again), java.awt.Cursor.HAND_CURSOR, "with Test armed a click tests a path, and the"
                + " track still shows the hand for changing its directions");

            assertFalse(String.valueOf(again.getToolTipText()).contains(says), "with Test armed the track still says a"
                + " click changes its directions: " + again.getToolTipText());
        }
        finally
        {
            open.close();
        }
    }

    /**
     * The click's line goes once a square has been clicked, and comes back with the next opening; the hand stays
     * (MT-697).  The editor window remembers: closed and opened again, the line is back.  Any square counts; a blank
     * one is clicked here, which changes nothing.
     *
     * MUTATION: let a click leave the line to be said, say it whatever was clicked, or remember the click past the
     * window, and this fails.
     */
    @Test(timeOut = 300000)
    public void testTheClickLineGoesOnceASquareIsClicked() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the editor is a window");
        }

        final Editor open = open(true);
        Editor reopened = null;
        boolean closed = false;

        try
        {
            String says = org.traincontrol.util.I18n.t("autosetup.ui.tooltipClickToChangeDirections");

            assertTrue(String.valueOf(hover(open.editor, 3, 2).getToolTipText()).contains(says), "precondition: the track"
                + " does not say a click changes its directions, so there is no line to go");

            // Any square clicked - a blank one
            final org.traincontrol.gui.LayoutLabel blank = hover(open.editor, 3, 5);

            javax.swing.SwingUtilities.invokeAndWait(() -> open.editor.receiveClickEvent(new java.awt.event.MouseEvent(blank,
                java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1), blank));

            for (int i = 0; i < 4; i++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            org.traincontrol.gui.LayoutLabel track = hover(open.editor, 3, 2);

            assertFalse(String.valueOf(track.getToolTipText()).contains(says), "after a square was clicked the track still"
                + " says a click changes its directions (MT-697's note): " + track.getToolTipText());

            assertEquals(cursorOf(track), java.awt.Cursor.HAND_CURSOR, "the hand went with the line - it is the line that"
                + " was asked to go");

            // Closed, and opened again: the line is back
            open.close();
            closed = true;

            reopened = open(true);

            assertTrue(String.valueOf(hover(reopened.editor, 3, 2).getToolTipText()).contains(says), "an editor opened after"
                + " the one a square was clicked in does not say what a click does - Adam: \"have that tooltip tracking state"
                + " reset after the editor is closed\"");
        }
        finally
        {
            if (!closed) open.close();

            if (reopened != null) reopened.close();
        }
    }


    /** The type of the pointer a square shows, read on the event thread. */
    private static int cursorOf(final org.traincontrol.gui.LayoutLabel label) throws Exception
    {
        final int[] type = new int[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> type[0] = label.getCursor().getType());

        return type[0];
    }

    /** A private field of the editor's autonomy panel, by name. */
    private static Object panelField(org.traincontrol.gui.layouteditor.LayoutEditor of, String name) throws Exception
    {
        java.lang.reflect.Field f = org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(of.getAutonomyPanel());
    }
}
