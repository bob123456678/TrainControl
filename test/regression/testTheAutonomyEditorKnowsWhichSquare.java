package regression;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * In autonomy mode the editor knows which square the pointer is over, and the naming shortcut asks it.
 *
 * Adam, MT-258 item 4: **"control+s is not firing in the autonomy editor"**.
 *
 * It was firing. `formKeyPressed` reaches it - Control+G and Control+L sit two branches above it and
 * both work - and it did exactly what it was written to do, which was the wrong thing:
 * `getLastHoveredLabel()` reads `lastHoveredX/Y`, and `receiveMoveEvent` deliberately does not set
 * those in autonomy mode. Its own comment says why, and the comment is right:
 *
 *     `lastHoveredX/Y` are deliberately NOT set. They are where a paste would land, and nothing is
 *     pasted here - leaving them alone keeps this from teaching the placement code a position it
 *     has no business acting on.
 *
 * So in the one mode where Control+S means anything, it asked for the square at -1,-1, got null, and
 * returned. A rule lifted from where it works, without the precondition that made it work there.
 *
 * The fix keeps that comment true: autonomy mode records the square in a field of its own, and the
 * placement variables are left exactly as untouched as they were. **Which makes the third test below
 * the important one** - a fix that repaired the shortcut by feeding the placement variables would
 * pass the first two and break dragging.
 */
public class testTheAutonomyEditorKnowsWhichSquare
{
    /** Everything one of these tests needs, torn down in one place. */
    private static final class Editor
    {
        support.LayoutSandbox sandbox;

        org.traincontrol.marklin.MarklinControlStation model;

        org.traincontrol.gui.TrainControlUI ui;

        org.traincontrol.gui.LayoutEditor editor;

        org.traincontrol.gui.LayoutGrid grid;

        void close() throws Exception
        {
            if (editor != null)
            {
                final org.traincontrol.gui.LayoutEditor window = editor;

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
     * A layout editor over a small diagram, in autonomy mode or in track mode.
     *
     * BEFORE the model, not just before the window (OB-111): constructing a TrainControlUI reads the
     * layout-path preference, and without the sandbox that is Adam's own railway.
     *
     * @param autonomy true to put the editor into autonomy mode, false to leave it editing track
     */
    private static Editor open(boolean autonomy) throws Exception
    {
        Editor open = new Editor();

        try
        {
            open.sandbox = support.LayoutSandbox.open();

            open.model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

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

            final org.traincontrol.gui.LayoutEditor[] built = new org.traincontrol.gui.LayoutEditor[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
                built[0] = new org.traincontrol.gui.LayoutEditor(diagram, 30, made[0], 0));

            open.editor = built[0];

            java.lang.reflect.Method drawGrid =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredMethod("drawGrid");
            drawGrid.setAccessible(true);

            java.lang.reflect.Field gridField =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredField("grid");
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
            // WHATEVER FAILED, THE PREFERENCE GOES BACK (TSX-B8).
            //
            // Every caller wraps the RETURNED holder in a try/finally, and the holder is
            // what does not exist when this throws.  `init` binds a UDP port and a window
            // constructor can fail; either left the machine-global layout preference
            // pointing at a folder under %TEMP%, which is what TrainControl opens next
            // time.  `close()` is already null-guarded for a partial build.
            open.close();

            throw failed;
        }
    }

    /**
     * Java source with its comments removed - `//` and block alike, in the order the compiler reads
     * them.
     *
     * @param source the file's text
     * @return the same text with every comment gone
     */
    private static String withoutComments(String source)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < source.length(); i++)
        {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/') inLine = true;
            else if (c == '/' && next == '*') inBlock = true;
            else out.append(c);
        }

        return out.toString();
    }

    /** A private field of the editor, by name. */
    private static Object field(org.traincontrol.gui.LayoutEditor of, String name) throws Exception
    {
        java.lang.reflect.Field f =
            org.traincontrol.gui.LayoutEditor.class.getDeclaredField(name);

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
     * Moves the pointer over a square and hands back the label it moved over.
     *
     * **The grid is read at the moment of the move, not before it.** The editor redraws its grid after
     * the constructor returns, and `LayoutGrid.getCoordinates` compares labels by identity - so a
     * label captured earlier is simply not in the grid the handler consults, and `getX` answers -1 for
     * it. The track-mode test below failed against correct code for exactly that reason.
     *
     * @param on the editor to move the pointer in
     * @param x which square
     * @param y which square
     * @return the label the pointer was moved over
     */
    private static org.traincontrol.gui.LayoutLabel hover(org.traincontrol.gui.LayoutEditor on,
        int x, int y) throws Exception
    {
        java.lang.reflect.Field gridField =
            org.traincontrol.gui.LayoutEditor.class.getDeclaredField("grid");
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
     * Moving the pointer over a square in autonomy mode is remembered.
     *
     * This is the state Control+S was missing, and it was missing entirely: before the fix nothing at
     * all recorded the square in this mode, so the shortcut asked the placement variables and found
     * -1,-1.
     *
     * MUTATION this catches: delete `autonomyHover = label;` from `receiveMoveEvent`'s autonomy
     * branch. That is the shipped defect, restored.
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
     * And the naming shortcut asks THAT, not the placement variables.
     *
     * Read out of the source, and the reason is the same one `testTheWindowAttachesItsRefreshCallback`
     * gives for its own reading: the value is right and the CALLER asked the wrong question, which is
     * a thing a behavioural test cannot see when it can only reach the caller through a modal dialog.
     * `promptName` opens one, so driving Control+S here would hang the battery rather than test it.
     *
     * MUTATION this catches: put `getLastHoveredLabel()` back in the Control+S branch. The test above
     * still passes - the field is still being set - and the shortcut is broken again.
     */
    @Test
    public void testTheNamingShortcutAsksTheSquareAutonomyModeTracks() throws Exception
    {
        java.io.File source = new java.io.File("src/org/traincontrol/gui/LayoutEditor.java");

        assertTrue(source.exists(), "cannot find " + source.getAbsolutePath()
            + " - this test reads the editor's source, so it has to run from the project root");

        // WITHOUT ITS COMMENTS, and this is not a precaution.
        //
        // The branch's own comment reads "`autonomyHover`, NOT `getLastHoveredLabel()`", which is a
        // sentence about the fix and was read as the defect - the first run of this test failed
        // against correct code because of it.  `testTheWindowAttachesItsRefreshCallback` strips
        // comments for the same reason and says so at more length.
        String body = withoutComments(new String(java.nio.file.Files.readAllBytes(source.toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        int at = body.indexOf("evt.getKeyCode() == KeyEvent.VK_S");

        assertTrue(at > 0, "the Control+S branch is gone from the editor altogether");

        // The branch's own body: from the test down to the closing of the block, which on this
        // handler is the next `return;`.
        int ends = body.indexOf("return;", at);

        assertTrue(ends > at, "the Control+S branch never returns, so its body cannot be read");

        String branch = body.substring(at, ends);

        assertTrue(branch.contains("autonomyHover"),
            "the Control+S branch does not ask the square autonomy mode tracks.  What it does ask:\n"
            + branch);

        assertFalse(branch.contains("getLastHoveredLabel"),
            "the Control+S branch asks getLastHoveredLabel(), which autonomy mode deliberately never "
            + "sets - so the key fires, finds nothing, and does nothing, which is exactly what Adam "
            + "reported");
    }

    /**
     * And track mode still feeds the placement variables, which are not this fix's to touch.
     *
     * The wrong fix for this bug is one line long and passes both tests above: set `lastHoveredX/Y`
     * in the autonomy branch too. `receiveMoveEvent`'s own comment is the argument against it - those
     * two are where a paste would land, and autonomy mode pastes nothing - and the cost of ignoring
     * it is not visible from the shortcut at all.
     *
     * So this is the other half of the rule: in TRACK mode the placement variables are still set, and
     * in AUTONOMY mode they are still left alone.
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
}
