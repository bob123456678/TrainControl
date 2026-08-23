package regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutEditor;

/**
 * What must NOT survive the editor switching page, now that the window does.
 *
 * OB-005, 2026-08-22: switching between the autonomy view and the track diagram editor closed the
 * window and opened a new one, which showed as a flash. The window stays open now and re-points
 * itself at the new page instead.
 *
 * That turns a whole class of per-window state into per-PAGE state overnight, and the thing about
 * this particular change is that every one of those fields was correct before it. A window that
 * edits one page for its whole life does not need its undo history to say which page it is about.
 *
 * The undo history is the one with teeth. Each entry is a snapshot of a page's components with
 * nothing in it naming the page, so an entry left over from the previous page would be written
 * straight over the new one by a single Ctrl+Z - the user's own undo key silently replacing one
 * page's track with another's.
 *
 * This test does not drive the window. It pins the CONTRACT the switch has to keep, by reading the
 * fields directly: after a switch, nothing that names a square or holds a page's components may still
 * be holding the page that was left. A test that opened a real editor would need a display, a model
 * and a layout folder, and would still be checking these same fields at the end.
 *
 * @author Adam
 */
public class testEditorSwitchClearsPageState
{
    /**
     * The fields arriveAt has to clear, and the reason each one is on the list.
     *
     * Kept as data rather than as four assertions so that the FAILURE names the field and says why it
     * matters, which is the only useful thing a test like this can say to somebody who has just added
     * a fifth piece of per-page state and not cleared it.
     */
    private static final String[][] MUST_BE_CLEARED = {
        {"previousLayoutComponents",
         "the undo history - an entry is a snapshot of a page's components with nothing naming the "
         + "page, so one Ctrl+Z after a switch writes the old page over the new one"},
        {"previousLayoutComponentsRedo",
         "the redo history, for the same reason as the undo history"},
        {"selection",
         "a selection is a set of TileKeys, and a TileKey is a page name and coordinates - carried "
         + "across, it names squares on a page that is no longer on screen"},
        {"previewSelection", "the drag preview, same reason as the selection"},
        {"landingSelection", "where a drag would land, same reason as the selection"},
    };

    /**
     * Every piece of per-page state is cleared where the switch says it is.
     *
     * Read out of the source rather than by running the editor: what is being pinned is that arriveAt
     * clears these, and a compiled method cannot be asked what it clears. The alternative - opening a
     * real editor, switching it, and reading the fields - needs a display and a layout on disk, and
     * would be pinning the same line of code from further away.
     */
    @Test
    public void testTheSwitchClearsEverythingThatNamesASquare() throws Exception
    {
        String source = arriveAtSource();

        for (String[] each : MUST_BE_CLEARED)
        {
            assertTrue(source.contains(each[0] + ".clear()"),
                "arriveAt does not clear " + each[0] + " - " + each[1]);
        }
    }

    /**
     * And the undo point for the SETUP is re-taken rather than carried or consumed.
     *
     * Both of the wrong answers here are silent. Carried over from when the window opened, Cancel on
     * the new page undoes the setup past work the user was asked about and chose to save on the way
     * here. Consumed by the track editor's own teardown, Cancel on the new page has nothing to put
     * back at all.
     */
    @Test
    public void testTheSetupUndoPointIsRetaken() throws Exception
    {
        String source = arriveAtSource();

        assertTrue(source.contains("autonomyAsOpened = live == null ? null : live.snapshotSetup()"),
            "arriveAt does not re-take the setup snapshot, so Cancel after a switch is undoing "
            + "against the wrong starting point");
    }

    /**
     * The fields named above are really the fields that exist.
     *
     * A rename would otherwise leave the test above passing against a string nothing produces any
     * more - the failure mode of testing source text, closed here rather than accepted.
     */
    @Test
    public void testTheNamedFieldsExist() throws NoSuchFieldException
    {
        for (String[] each : MUST_BE_CLEARED)
        {
            Field f = LayoutEditor.class.getDeclaredField(each[0]);

            assertNotNull(f, each[0]);
        }

        assertNotNull(LayoutEditor.class.getDeclaredField("autonomyAsOpened"));

        // And the one that had to stop being final for any of this to be possible
        Field page = LayoutEditor.class.getDeclaredField("layout");

        assertFalse(java.lang.reflect.Modifier.isFinal(page.getModifiers()),
            "the page being edited is final again, which means the window cannot re-point itself "
            + "and the switch is back to closing and reopening");
    }

    /**
     * The switch does not dispose the window - which is the whole of OB-005.
     */
    @Test
    public void testTheSwitchDoesNotCloseTheWindow() throws Exception
    {
        String source = methodSource("leaveFor");

        assertFalse(source.contains("dispose()"),
            "leaveFor disposes the window, so switching still closes and reopens it - which is the "
            + "flash OB-005 reported");
    }

    // ------------------------------------------------------------------------------------------

    private String arriveAtSource() throws Exception
    {
        return methodSource("arriveAt");
    }

    /**
     * One method's body, out of the source file.
     *
     * Braces are counted rather than matched to the next "private", because a method containing a
     * lambda or an anonymous class - and these do - has plenty of both inside it.
     */
    private String methodSource(String name) throws Exception
    {
        java.io.File file = new java.io.File(
            "src/org/traincontrol/gui/LayoutEditor.java");

        assertTrue(file.exists(), "cannot find " + file.getAbsolutePath()
            + " - this test reads the source, so it has to run from the project root");

        String all = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");

        int at = all.indexOf(" " + name + "(");

        assertTrue(at > 0, "no method called " + name);

        int open = all.indexOf('{', at);

        int depth = 0;

        for (int i = open; i < all.length(); i++)
        {
            if (all.charAt(i) == '{') depth++;
            else if (all.charAt(i) == '}' && --depth == 0) return withoutComments(all.substring(open, i));
        }

        fail("could not find the end of " + name);

        return "";
    }

    /**
     * The same body with its comments taken out.
     *
     * Added after this test failed on its own first run, for the right reason and the wrong cause: the
     * comment explaining that the switch "used to dispose()" contains the word it was looking for. A
     * test that reads source has to read the CODE, and a codebase whose comments say what the code no
     * longer does is exactly the codebase where that distinction matters.
     */
    private String withoutComments(String body)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';

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

    /**
     * Not used, but named so the imports above are not mistaken for accidents: the undo history is a
     * Deque of Lists of components, and that is what makes an entry page-agnostic.
     */
    @SuppressWarnings("unused")
    private Deque<List<LayoutDiagramComponent>> shape;

    @SuppressWarnings("unused")
    private Method unused;
}
