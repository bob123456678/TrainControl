package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * The track-diagram editor must tell the autonomy setup what it did to the track: five findings from the LayoutEditor
 * review, one test class because they are one question asked of five gestures (when the diagram changes, does anything
 * carry the setup that was keyed to it?).
 *
 * READ FROM THE SOURCE, not by driving the editor: LayoutEditor is a Swing component wired to a live window, so this
 * follows the bounded source scan `testARunSurvivesADiagramEdit` uses. These assert that the CALL is in the right
 * place, not that it does the right thing; that is covered by `testAStationsTimetableFollowsItThroughRenamesAndEdits`
 * (moveTiles, forgetTiles) and `testACaptionStaysWithItsSquare` (forgetCaptionsAt).
 *
 * Each scan is bounded to one method body, or it would find the term anywhere in a six-thousand-line file and pass
 * while the method does nothing. Both terms of every ordering assertion are proved present before they are compared,
 * because an absent term's index is -1, less than every real index, which makes a reversed call pass in silence.
 */
public class testTheEditorTellsAutonomy
{
    /**
     * A group cut and paste has to carry the setup, the way every other move does: a cut that did not left the station,
     * name, length, facings, barred arrivals, protecting signal, portal partner and placed locomotive keyed to the
     * emptied squares, and the next reconciling save pruned them.
     *
     * MUTATION this catches: delete the moveTiles call from pasteSelection, or stop cutSelection setting
     * clipboardWasCut - either leaves the paste with nothing to carry.
     */
    @Test
    public void testAGroupCutCarriesItsSetupToWhereItIsPasted() throws Exception
    {
        String paste = bodyOf("    boolean pasteSelection(int atX, int atY)");

        assertTrue(paste.contains("moveTiles("),
            "pasteSelection no longer calls moveTiles, so a group cut and paste leaves the autonomy "
            + "setup on the squares it emptied and the next reconciling save prunes it (LE-A1)");

        String cut = bodyOf("    boolean cutSelection()");

        // The ASSIGNMENT, not a mention of the name: setting the flag to false here ("the paste cannot tell a move from
        // a copy") must fail the test.
        assertTrue(cut.contains("clipboardWasCut = cut"),
            "cutSelection no longer sets the cut flag FROM the delete's result, so the paste cannot "
            + "tell a move from a copy and will not carry the setup (LE-A1)");

        assertTrue(cut.contains("this.editor.clipboardCutSquares = emptied"),
            "cutSelection no longer records WHICH squares it emptied, so a non-rectangular cut moves "
            + "the setup off squares that still hold their track (LE-A5)");

        // The ORDER: the picked squares have to be collected before deleteSelection, because deleting clears the
        // selection; collected after, the set is empty, every origin is skipped and the paste carries nothing.
        int collected = cut.indexOf("this.editor.selection.all()");
        int deleted = cut.indexOf("this.editor.deleteSelection(");

        // Both proved present before they are compared, as this class's header promises: an absent term's index is -1,
        // which is less than every real one.
        assertTrue(collected >= 0, "cutSelection no longer collects the picked squares (LE-A5)");
        assertTrue(deleted >= 0, "cutSelection no longer calls deleteSelection - scan needs updating");

        assertTrue(collected < deleted,
            "cutSelection collects the emptied squares AFTER deleting them, and deleting clears the "
            + "selection - so it collects nothing and the paste carries nothing (LE-A5)");

        // The cut must NOT let the per-square delete forget the captions, or the paste has none left to carry - the
        // four bulk movers pass false for the same reason.
        assertTrue(cut.contains("deleteSelection(false)"),
            "cutSelection lets the delete forget the captions, so a cut and paste carries the station, "
            + "its length and its locomotive but loses the name drawn on the diagram (LE2-B7)");

        // Every other assertion here asks whether a call is present; this one asks that the decision is taken from the
        // ARGUMENT, the only part of reachability a source scan can see (cutMoves once re-read the field that
        // snapshotLayout had just cleared).
        String moves = bodyOf("> cutMoves(int atX, int atY, boolean wasCut,");

        // Unqualified, because "clipboardWasCut" and "this.clipboardWasCut" are the same read; the method has no
        // legitimate mention of the field, since the caller decides.
        assertFalse(moves.contains("clipboardWasCut"),
            "cutMoves reads the cut flag from the field again instead of the argument it was handed. "
            + "The decision belongs to the gesture, and a field re-read is how LE-A7 turned the whole "
            + "group-cut fix into dead code while every one of these tests stayed green");

        // And it uses the answer it was handed. The question is taken OUT of this method because asking it here is too
        // late: pasteSelection places its tiles first, so an origin that is also a landing has been refilled by the
        // paste itself. So the method uses what the caller worked out and does not look again.
        assertTrue(moves.contains("!vacated.contains(origin)"),
            "cutMoves does not consult the set of squares the caller found empty, so either it is "
            + "carrying setup off squares that are full or it is asking the diagram itself - and "
            + "asking the diagram from here is RC-A6, where the paste had already refilled them");

        assertFalse(moves.contains("layout.getComponent("),
            "cutMoves asks the diagram which squares are empty.  By the time it runs, pasteSelection "
            + "has placed its tiles, so an origin that is also a landing reads as full and its setup "
            + "is skipped and then forgotten as built over (RC-A6)");

        // Where the question lives now. Both halves of it, asked before anything is placed.
        String vacated = bodyOf("> emptyCutOrigins()");

        assertTrue(vacated.contains("getComponent(square.getX(), square.getY()) == null"),
            "emptyCutOrigins no longer checks that the square is still EMPTY, so a cut whose squares "
            + "have been refilled - by an undo, or by a page switch answered with Discard - would "
            + "carry their setup to the paste target and strip squares that still hold track (RC-A1)");

        assertTrue(vacated.contains("layout.getName().equals(square.getPage())"),
            "emptyCutOrigins no longer checks that the square is on the page being pasted onto, so a "
            + "cut on one page and a paste on another would move the setup across pages - which "
            + "cannot be undone, because arriveAt has thrown the source page's history away (RC-A1)");

        // A copy must NOT carry it: the original keeps everything, and moving its setup onto the copy would take the
        // station and its placed locomotive away from squares still holding track.
        String copy = bodyOf("    boolean copySelection()");

        assertTrue(copy.contains("clipboardWasCut = false"),
            "copySelection must clear the cut flag, or pasting a COPY would move the original's setup "
            + "onto the copy and leave the original squares bare (LE-A1)");

        // Undo making the cut untrue is not pinned here, deliberately: the rule is the per-square emptiness test above,
        // which undo satisfies by refilling the squares. It is tested by RUNNING it, in
        // `regression.testLayoutEditorBulkEdits.testUndoingTheCutBeforeThePasteLeavesTheSetupWhereItIs`, and its
        // sibling asserts an unrelated edit does NOT cost the move; a scan for a line of text could not tell those
        // apart.
    }

    /**
     * Shrinking the page must not leave a station's name outside it: edgesAreEmpty asks whether the edge holds TRACK,
     * but a caption is placed on a blank square by preference, so trimming left the caption with coordinates off the
     * page, not drawn and still present.
     */
    @Test
    public void testShrinkingThePageDropsCaptionsOnTheEdgeItRemoves() throws Exception
    {
        String body = bodyOf("    void shrinkEdges()");

        int forgotten = body.indexOf("forgetCaptionsOnTrimmedEdge");
        int trimmed = body.indexOf("trimEdges()");

        assertTrue(forgotten >= 0,
            "shrinkEdges no longer drops captions on the row and column it removes, so a station name "
            + "on a blank edge square ends up off the page, invisible and unremovable (LE-B1)");

        assertTrue(trimmed >= 0, "shrinkEdges no longer calls trimEdges - this scan needs updating");

        assertTrue(forgotten < trimmed,
            "the captions have to be dropped BEFORE the trim, while the squares holding them still "
            + "exist to be named (LE-B1)");
    }

    /**
     * Clearing a page tells the setup that the page has gone: emptying the whole page said nothing, so the setup
     * outlived the diagram until somebody opened the autonomy editor and pressed Save.
     */
    @Test
    public void testClearingThePageTellsTheSetup() throws Exception
    {
        String body = SourceOf.declarationIn(SourceOf.fileDeclaring("public class LayoutEditor extends"), "public void clear()");

        int forgotten = body.indexOf("forgetWholePage");
        int cleared = body.indexOf("layout.clear()");

        assertTrue(forgotten >= 0,
            "clear() no longer tells autonomy anything, so every station, name, length, facing, "
            + "signal pairing, portal and placement stays keyed to squares that hold nothing (LE-C2)");

        assertTrue(cleared >= 0, "clear() no longer calls layout.clear() - this scan needs updating");

        assertTrue(forgotten < cleared,
            "the setup has to be told before the page is emptied, while its squares still exist");
    }

    /**
     * Shift down and shift right must not record an undo step for an edit they refuse: they snapshotted first and
     * checked for a hovered square afterwards, which also clears the redo stack and makes the editor ask about saving
     * work nobody did. Shift up and shift left already checked first.
     */
    @Test
    public void testTheShiftsCheckBeforeTheyRecordAnUndoStep() throws Exception
    {
        for (String method : new String[] {"    void shiftDown()", "    void shiftRight()"})
        {
            String body = bodyOf(method);

            // Any refusal, not one spelling of it: a scan pinned to the old wording would go red for a change that
            // improved what it guards.
            int guarded = body.indexOf(") return;");
            int snapshot = body.indexOf("snapshotLayout()");

            assertTrue(guarded >= 0,
                method + " no longer refuses when there is no hovered square (LE-B2)");

            assertTrue(snapshot >= 0,
                method + " no longer snapshots for undo - this scan needs updating");

            assertTrue(guarded < snapshot,
                method + " snapshots before it checks it has anything to do, so invoking it with no "
                + "hovered square pushes an undo step for an edit that never happened, clears the "
                + "redo stack, and makes the editor ask about saving work nobody did (LE-B2)");
        }
    }

    /**
     * The menu offers a shift only where the method would perform one: Shift Up returns in silence on the last row and
     * Shift Left on the last column. Asserted as the MENU ASKING THE EDITOR'S OWN PREDICATE, not merely as the item
     * being grayed by some condition, since a menu that restates the rule can drift from it.
     */
    @Test
    public void testTheMenuAsksTheSamePredicateTheShiftUses() throws Exception
    {
        // The diagram submenu is built by the menu's constructor.
        String menu = bodyOf("public LayoutEditorRightclickMenu(LayoutEditor edit, TrainControlUI ui, LayoutLabel label,"
            + " LayoutDiagramComponent component)");

        // Each item against its own predicate: swapping which predicate grays which item (Shift Up enabled by
        // canShiftDown) must fail. Bounded to the one addShift call each.
        for (String pair : new String[] {"shiftUp|canShiftUp", "shiftLeft|canShiftLeft",
            "shiftDown|canShiftDown", "shiftRight|canShiftRight"})
        {
            String method = pair.split("\\|")[0];
            String predicate = pair.split("\\|")[1];

            int at = menu.indexOf("() -> edit." + method + "()");

            assertTrue(at >= 0, "the " + method + " item is no longer in the diagram submenu");

            // The whole call, up to the semicolon that ends it.
            int ends = menu.indexOf(");", at);

            assertTrue(ends > at, "the " + method + " item's addShift call does not close");

            assertTrue(menu.substring(at, ends).contains("edit." + predicate + "()"),
                "the " + method + " item is not greyed by " + predicate + " - a shift greyed by "
                + "another shift's predicate is offered where it refuses in silence (LE-C1, LE-C9)");
        }

        // and the parameter has to reach setEnabled, or asking the predicate changes nothing
        assertTrue(bodyOf("private void addShift(").contains("setEnabled(enabled)"),
            "addShift no longer wires its enabled parameter to setEnabled, so every item is offered "
            + "whatever its predicate says (LE-C1)");

        // and the method must ask it too, or the two can still disagree
        for (String pair : new String[] {"    void shiftUp()|canShiftUp",
            "    void shiftLeft()|canShiftLeft"})
        {
            String method = pair.split("\\|")[0];
            String predicate = pair.split("\\|")[1];

            assertTrue(bodyOf(method).contains(predicate + "()"),
                method + " no longer asks " + predicate + " - the menu and the method would then be "
                + "two copies of one rule, which is how this finding happened (LE-C1)");
        }
    }

    /**
     * One declaration's text, braces included, so a scan cannot wander into its neighbors; a miss is a failure that
     * names it.
     */
    private static String bodyOf(String declaration) throws Exception
    {
        return SourceOf.declaration(declaration);
    }
}
