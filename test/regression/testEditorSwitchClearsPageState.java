package regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import support.SourceOf;

/**
 * What must NOT survive the editor switching page, now that the window does (OB-005: switching between the autonomy
 * view and the track diagram editor closed the window and opened a new one, which showed as a flash).
 *
 * That turns a whole class of per-window state into per-PAGE state, and every field was correct before.  The undo
 * history is the one with teeth: each entry is a snapshot of a page's components with nothing naming the page, so a
 * leftover entry would be written straight over the new page by a single Ctrl+Z.
 *
 * This test does not drive the window; it pins the CONTRACT by reading the fields directly: after a switch, nothing
 * that names a square or holds a page's components may still be holding the page that was left.
 *
 * @author Adam
 */
public class testEditorSwitchClearsPageState
{
    /**
     * The fields arriveAt has to clear, and the reason each one is on the list.  Kept as data so that the FAILURE names
     * the field and says why it matters to somebody who has just added per-page state and not cleared it.
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
     * Every piece of per-page state is cleared where the switch says it is.  Read out of the source rather than by
     * running the editor, because a compiled method cannot be asked what it clears and a real editor needs a display
     * and a layout on disk.
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
     * And the undo point for the SETUP is re-taken rather than carried or consumed.  Both wrong answers are silent:
     * carried over from when the window opened, Cancel on the new page undoes work the user chose to save on the way
     * here; consumed by the track editor's teardown, Cancel has nothing to put back.
     */
    @Test
    public void testTheSetupUndoPointIsRetaken() throws Exception
    {
        String source = arriveAtSource();

        // Through takeTheUndoPoint, which takes BOTH halves: the in-memory snapshot Cancel restores and the on-disk
        // note a restart restores.  They were separate statements and drifted, so asking for the call asks for both.
        assertTrue(source.contains("takeTheUndoPoint("),
            "arriveAt does not re-take the setup undo point, so Cancel after a switch is undoing "
            + "against the wrong starting point");
    }

    /**
     * The fields named above are really the fields that exist: a rename would otherwise leave the test passing against
     * a string nothing produces.
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

    /** The switch does not dispose the window, which is the whole of OB-005. */
    @Test
    public void testTheSwitchDoesNotCloseTheWindow() throws Exception
    {
        String source = methodSource("private void leaveFor(");

        assertFalse(source.contains("dispose()"),
            "leaveFor disposes the window, so switching still closes and reopens it - which is the "
            + "flash OB-005 reported");
    }

    /**
     * Leaving autonomy mode puts the track palette back (OB-017).  Autonomy mode EMPTIES the palette panel and gives it
     * a different layout, and leaving only removed the autonomy column, enough while coming back meant a new window;
     * since the editor stopped closing, the teardown left an empty palette under a heading reading "Autonomy Tools".
     */
    @Test
    public void testLeavingAutonomyModeRebuildsThePalette() throws Exception
    {
        String source = methodSource("public void setAutonomyMode(");

        assertTrue(source.contains("buildPalette()"),
            "setAutonomyMode does not rebuild the palette, so switching back to the track editor "
            + "leaves an empty New Components panel under the autonomy heading");
    }

    /**
     * The viewer's LAYOUT does not depend on the shared edit flag (OB-016: the viewer was drawn in edit mode while the
     * editor was open).  An earlier version posted arriveAt with two invokeLater calls to land behind the teardown's
     * repaint, which cannot work: repaintLayout submits to a single-thread ExecutorService and only calls invokeLater
     * from inside THAT.  What is pinned is the real fix: LayoutGrid decides how to lay a grid out by asking whether
     * THAT grid is in an editor, not by reading a flag that belongs to a diagram two windows share.
     */
    @Test
    public void testTheViewerLayoutIsNotDecidedByTheSharedFlag() throws Exception
    {
        assertTrue(SourceOf.allCode().contains("layout.getEdit() && master instanceof LayoutEditor"),
            "LayoutGrid lays the grid out on layout.getEdit() alone again. The main window shares that "
            + "diagram with the editor, so the viewer will be re-laid the editor's way by any repaint "
            + "while an editor is open - which is OB-016, and no amount of invokeLater fixes it.");
    }

    /**
     * Everything autonomy mode MOUNTS, it also unmounts.  Three things swap themselves into the window (the palette
     * becomes the setup column, the Addresses box the visibility box, the diagram's scroll pane a stack with the
     * findings list under it), each by a GroupLayout.replace or a container swap that has to be undone.  Every field
     * the mount block assigns must be cleared in the teardown, or coming BACK skips the mount and the previous
     * session's controls stay on screen looking current.
     */
    private static final String[] MUST_BE_UNMOUNTED = {
        "autonomyPanel", "autonomyBanner", "autonomyVisibility", "autonomyFindings",
    };

    @Test
    public void testLeavingAutonomyModeUndoesEverythingItMounted() throws Exception
    {
        String source = methodSource("public void setAutonomyMode(");

        String teardown = source.substring(source.indexOf("if (session == null)"));

        teardown = teardown.substring(0, teardown.indexOf("else if (autonomyPanel == null)"));

        for (String field : MUST_BE_UNMOUNTED)
        {
            assertTrue(teardown.contains(field + " = null"),
                "setAutonomyMode's teardown does not clear " + field + ". Leaving autonomy mode will "
                + "leave whatever it holds on screen, and coming back will skip the block that mounts "
                + "it - so the next session's control is orphaned and the last session's stays, "
                + "looking current. That is GC-A2, and OB-017 before it.");
        }
    }

    /**
     * And the fields named above are the fields the mount block actually assigns: a fifth one added to setAutonomyMode
     * and not to the list would walk past.
     */
    @Test
    public void testTheMountedFieldsAreTheOnesListed() throws Exception
    {
        String source = methodSource("public void setAutonomyMode(");

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(autonomy[A-Za-z]+) = (?!null)").matcher(source);

        java.util.Set<String> assigned = new java.util.LinkedHashSet<>();

        while (m.find()) assigned.add(m.group(1));

        java.util.Set<String> known = new java.util.LinkedHashSet<>(
            java.util.Arrays.asList(MUST_BE_UNMOUNTED));

        assigned.removeAll(known);

        assertEquals(assigned.toString(), "[]",
            "setAutonomyMode assigns a field the unmount list above does not know about. Add it to "
            + "MUST_BE_UNMOUNTED and make sure the teardown clears it.");
    }

    private String arriveAtSource() throws Exception
    {
        return methodSource("private void arriveAt(");
    }

    /**
     * One method's body, out of the program's source, with its comments taken out: a comment saying the switch "used to
     * dispose()" contains the word being looked for.
     */
    private String methodSource(String signature) throws Exception
    {
        return SourceOf.withoutComments(SourceOf.body(signature));
    }

    /** Not used, but named so the imports above are not mistaken for accidents. */
    @SuppressWarnings("unused")
    private Deque<List<LayoutDiagramComponent>> shape;

    @SuppressWarnings("unused")
    private Method unused;
}
