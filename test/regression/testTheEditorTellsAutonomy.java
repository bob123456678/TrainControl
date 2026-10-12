package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The track-diagram editor must tell the autonomy setup what it did to the track.
 *
 * Five findings from the 2026-08-30 LayoutEditor review (LE-A1, B1, B2, C1, C2), and they are one
 * test class because they are one question asked of five gestures: when the diagram changes, does
 * anything carry the setup that was keyed to it?
 *
 * READ FROM THE SOURCE, not by driving the editor. LayoutEditor is a Swing component wired to a live
 * window, and nothing in this suite constructs one - the established shape for its behaviour is the
 * bounded source scan testARunSurvivesADiagramEdit uses, and this follows it. That has a real limit
 * worth stating: these assert that the CALL is in the right place, not that the call does the right
 * thing. What the calls do is covered where it can be exercised - AutonomySession.moveTiles,
 * forgetTiles and forgetCaptionsAt are tested directly in testAutonomyDiagramSession.
 *
 * Each scan is bounded to one method body. An unbounded search would find the term anywhere in a
 * six-thousand-line file and pass while the method it names does nothing - which is the vacuous-scan
 * failure this suite has already been bitten by once (TST-B7). Both terms of every ordering assertion
 * are proved present before they are compared, because an absent term's index is -1, which is less
 * than every real index and would make a reversed call pass in silence.
 */
public class testTheEditorTellsAutonomy
{
    private static final String EDITOR = "src/org/traincontrol/gui/LayoutEditor.java";
    private static final String MENU = "src/org/traincontrol/gui/LayoutEditorRightclickMenu.java";

    /**
     * A group cut and paste has to carry the setup, the way every other move does (LE-A1).
     *
     * deleteSelection told autonomy about captions and nothing else, and pasteSelection only cleared
     * what it built over - so a cut left the station, name, length, facings, barred arrivals,
     * protecting signal, portal partner and placed locomotive keyed to the emptied squares, and the
     * next reconciling save pruned them as squares that no longer exist. The single-tile cut, the
     * selection drag and the bulk row move all called moveTile or moveTiles; this one did not.
     *
     * MUTATION this catches: delete the moveTiles call from pasteSelection, or stop cutSelection
     * setting clipboardWasCut - either leaves the paste with nothing to carry.
     */
    @Test
    public void testAGroupCutCarriesItsSetupToWhereItIsPasted() throws Exception
    {
        String paste = bodyOf(EDITOR, "public boolean pasteSelection(int atX, int atY)");

        assertTrue(paste.contains("moveTiles("),
            "pasteSelection no longer calls moveTiles, so a group cut and paste leaves the autonomy "
            + "setup on the squares it emptied and the next reconciling save prunes it (LE-A1)");

        String cut = bodyOf(EDITOR, "public boolean cutSelection()");

        // The ASSIGNMENT, not a mention of the name.  This asked only that the identifier appeared,
        // so setting the flag to false here - which is precisely "the paste cannot tell a move from a
        // copy" - left the test green.  A test that cannot fail for the reason it names is worse than
        // no test, because it reads as protection (LE-C1).
        assertTrue(cut.contains("clipboardWasCut = cut"),
            "cutSelection no longer sets the cut flag FROM the delete's result, so the paste cannot "
            + "tell a move from a copy and will not carry the setup (LE-A1)");

        assertTrue(cut.contains("this.clipboardCutSquares = emptied"),
            "cutSelection no longer records WHICH squares it emptied, so a non-rectangular cut moves "
            + "the setup off squares that still hold their track (LE-A5)");

        // The ORDER, which is what LE-A5's fix turns on: the picked squares have to be collected
        // before deleteSelection, because deleting clears the selection.  Collected after, the set is
        // empty, every origin is skipped and the paste carries nothing - and an assertion that only
        // asked for the assignment could not see it (LE-C8).
        int collected = cut.indexOf("this.selection.all()");
        int deleted = cut.indexOf("this.deleteSelection(");

        // Both proved present before they are compared, which this class's own header promises and
        // this one assertion was not doing: an absent term's index is -1, which is less than every
        // real one, so a rename would have made it pass whatever the order (LE2-C13).  It went red
        // for exactly that reason when deleteSelection gained an argument.
        assertTrue(collected >= 0, "cutSelection no longer collects the picked squares (LE-A5)");
        assertTrue(deleted >= 0, "cutSelection no longer calls deleteSelection - scan needs updating");

        assertTrue(collected < deleted,
            "cutSelection collects the emptied squares AFTER deleting them, and deleting clears the "
            + "selection - so it collects nothing and the paste carries nothing (LE-A5)");

        // LE2-B7: the cut must NOT let the per-square delete forget the captions, or the paste has
        // none left to carry - the four bulk movers pass false for the same reason.
        assertTrue(cut.contains("deleteSelection(false)"),
            "cutSelection lets the delete forget the captions, so a cut and paste carries the station, "
            + "its length and its locomotive but loses the name drawn on the diagram (LE2-B7)");

        // LE-A7: the whole of A1, A4 and A5 was unreachable because cutMoves re-read the field that
        // snapshotLayout had just cleared.  Every other assertion here asks whether a call is present;
        // this one asks that the decision is taken from the ARGUMENT, which is the only part of
        // reachability a source scan can actually see.
        String moves = bodyOf(EDITOR, "> cutMoves(int atX, int atY, boolean wasCut,");

        // Unqualified, because "clipboardWasCut" and "this.clipboardWasCut" are the same read and
        // Java requires neither spelling - pinning one let the other through (LE2-C12).  The method
        // has no legitimate mention of the field: its whole point is that the caller decides.
        assertFalse(moves.contains("clipboardWasCut"),
            "cutMoves reads the cut flag from the field again instead of the argument it was handed. "
            + "The decision belongs to the gesture, and a field re-read is how LE-A7 turned the whole "
            + "group-cut fix into dead code while every one of these tests stayed green");

        // AND IT USES THE ANSWER IT WAS HANDED (RC-A1, RC-A6).
        //
        // The per-square test replaced LE-A6, which stood the flag down inside snapshotLayout - and
        // since every edit snapshots, any edit between the cut and the paste turned the move into a
        // copy and abandoned the setup on the emptied squares.
        //
        // RC-A6 then moved the question OUT of this method, because asking it here was asking it too
        // late: pasteSelection places its tiles first, so an origin that is also a landing had been
        // refilled by the paste itself.  So what this method must do now is exactly what it must do
        // with the flag - use what the caller worked out, and not go and look again.
        assertTrue(moves.contains("!vacated.contains(origin)"),
            "cutMoves does not consult the set of squares the caller found empty, so either it is "
            + "carrying setup off squares that are full or it is asking the diagram itself - and "
            + "asking the diagram from here is RC-A6, where the paste had already refilled them");

        assertFalse(moves.contains("layout.getComponent("),
            "cutMoves asks the diagram which squares are empty.  By the time it runs, pasteSelection "
            + "has placed its tiles, so an origin that is also a landing reads as full and its setup "
            + "is skipped and then forgotten as built over (RC-A6)");

        // WHERE THE QUESTION LIVES NOW.  Both halves of it, asked before anything is placed.
        String vacated = bodyOf(EDITOR, "> emptyCutOrigins()");

        assertTrue(vacated.contains("getComponent(square.getX(), square.getY()) == null"),
            "emptyCutOrigins no longer checks that the square is still EMPTY, so a cut whose squares "
            + "have been refilled - by an undo, or by a page switch answered with Discard - would "
            + "carry their setup to the paste target and strip squares that still hold track (RC-A1)");

        assertTrue(vacated.contains("layout.getName().equals(square.getPage())"),
            "emptyCutOrigins no longer checks that the square is on the page being pasted onto, so a "
            + "cut on one page and a paste on another would move the setup across pages - which "
            + "cannot be undone, because arriveAt has thrown the source page's history away (RC-A1)");

        // A copy must NOT carry it: the original keeps everything, and moving its setup onto the copy
        // would take the station and its placed locomotive away from the squares still holding track.
        String copy = bodyOf(EDITOR, "public boolean copySelection()");

        assertTrue(copy.contains("clipboardWasCut = false"),
            "copySelection must clear the cut flag, or pasting a COPY would move the original's setup "
            + "onto the copy and leave the original squares bare (LE-A1)");

        // LE-A3 - undo makes the cut untrue - IS NOT PINNED HERE ANY MORE, and deliberately.
        //
        // It used to be, as `undo` and `redo` each containing "clipboardWasCut = false".  RC-A1 took
        // those out: the rule is now the per-square emptiness test above, which undo satisfies by
        // refilling the squares rather than by being told about them.
        //
        // The rule itself is tested by RUNNING it, in
        // regression.testLayoutEditorBulkEdits.testUndoingTheCutBeforeThePasteLeavesTheSetupWhereItIs,
        // which cuts, undoes, pastes and asserts the setup stayed where the track came back - and its
        // sibling asserts an unrelated edit does NOT cost the move.  A scan for a line of text could
        // never have told those two cases apart; a source scan cannot see reachability, which is the
        // whole lesson of LE-A7.
    }

    /**
     * Shrinking the page must not leave a station's name outside it (LE-B1).
     *
     * edgesAreEmpty asks whether the edge holds TRACK. A caption is placed on a blank square by
     * preference, so the edge could hold a name and still read as empty; trimming left the caption
     * with coordinates off the page - not drawn, still present, so the "station is not shown anywhere"
     * check stayed quiet and no square remained to click to move or remove it.
     */
    @Test
    public void testShrinkingThePageDropsCaptionsOnTheEdgeItRemoves() throws Exception
    {
        String body = bodyOf(EDITOR, "public void shrinkEdges()");

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
     * Clearing a page tells the setup that the page has gone (LE-C2).
     *
     * Deleting one square says so; emptying the whole page said nothing, so the setup outlived the
     * diagram it described until somebody opened the autonomy editor and pressed Save - and the
     * non-reconciling writes on the way out committed that state to disk first.
     */
    @Test
    public void testClearingThePageTellsTheSetup() throws Exception
    {
        String body = bodyOf(EDITOR, "public void clear()");

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
     * Shift down and shift right must not record an undo step for an edit they refuse (LE-B2).
     *
     * They snapshotted first and checked for a hovered square afterwards, so invoking them with none
     * pushed an undo entry for an edit that never happened - which also clears the redo stack and
     * makes the editor ask about saving work nobody did. Shift up and shift left already checked
     * first, which is what made this a drift between siblings rather than a design.
     */
    @Test
    public void testTheShiftsCheckBeforeTheyRecordAnUndoStep() throws Exception
    {
        for (String method : new String[] {"public void shiftDown()", "public void shiftRight()"})
        {
            String body = bodyOf(EDITOR, method);

            // Any refusal, not one spelling of it: LE-C2 replaced the inline comparison with a named
            // predicate, and a scan pinned to the old wording would have gone red for a change that
            // improved the thing it was guarding.
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
     * The menu offers a shift only where the method would perform one (LE-C1).
     *
     * Shift Up returns in silence on the last row and Shift Left on the last column, and the menu
     * offered them anyway - so right-clicking the bottom row, the natural gesture for taking an empty
     * row away, chose an item that did nothing and said nothing.
     *
     * Asserted as the MENU ASKING THE EDITOR'S OWN PREDICATE, not merely as the item being greyed by
     * some condition. A menu that restates the rule is a menu that can drift from it, and that drift
     * is this finding: two copies of one question, one of which was never updated.
     */
    @Test
    public void testTheMenuAsksTheSamePredicateTheShiftUses() throws Exception
    {
        String menu = read(MENU);

        // EACH ITEM AGAINST ITS OWN PREDICATE (LE-C9).
        //
        // This asked whether the four strings appeared anywhere in the file, so swapping which
        // predicate greys which item - Shift Up enabled by canShiftDown - passed, and the menu was
        // back to offering Shift Up on the bottom row where it refuses in silence. That is LE-C1
        // restored with the test watching. Bounded to the one addShift call each.
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
        assertTrue(bodyOf(MENU, "private void addShift(").contains("setEnabled(enabled)"),
            "addShift no longer wires its enabled parameter to setEnabled, so every item is offered "
            + "whatever its predicate says (LE-C1)");

        // and the method must ask it too, or the two can still disagree
        for (String pair : new String[] {"public void shiftUp()|canShiftUp",
            "public void shiftLeft()|canShiftLeft"})
        {
            String method = pair.split("\\|")[0];
            String predicate = pair.split("\\|")[1];

            assertTrue(bodyOf(EDITOR, method).contains(predicate + "()"),
                method + " no longer asks " + predicate + " - the menu and the method would then be "
                + "two copies of one rule, which is how this finding happened (LE-C1)");
        }
    }

    /**
     * One method's body, braces included, so a scan cannot wander into its neighbours.
     */
    private static String bodyOf(String file, String declaration) throws Exception
    {
        String source = read(file);

        int at = source.indexOf(declaration);

        assertTrue(at >= 0, declaration + " was not found in " + file
            + " - it was renamed or removed, and this scan needs updating rather than deleting");

        int open = source.indexOf('{', at + declaration.length());

        assertTrue(open >= 0, "no body found for " + declaration);

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(at, i + 1);
        }

        fail("the body of " + declaration + " does not close");

        return "";
    }

    private static String read(String file) throws Exception
    {
        File found = new File(file);

        // Run from the project root by the harness; the battery runs from one directory up in some
        // configurations, so the parent is tried rather than failing on a path.
        if (!found.isFile()) found = new File("../" + file);

        assertTrue(found.isFile(), file + " was not found from " + new File(".").getAbsolutePath());

        return new String(Files.readAllBytes(found.toPath()), StandardCharsets.UTF_8);
    }
}
