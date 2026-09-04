package regression;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.gui.LayoutEditor;

/**
 * Moving or copying a whole column or row on the diagram, and what happens to the setup underneath it.
 *
 * Adam moved a column in the layout editor and his links came unpaired.  The cause was that a bulk
 * edit is not built out of single-tile moves: it copies the whole line into place with the move flag
 * OFF and deletes the source line afterwards, so the one call that carries a square's setup - which
 * only happens on a move - was never made for any of the twenty squares.  Everything autonomy knew
 * about that column stayed where the track used to be, and reconcile, finding stations on squares with
 * no sensors, threw the lot away.  Links show it first because a pairing is mutual: the partner is
 * left pointing at a bare square, which reads as "it unpaired itself".
 *
 * The other half of the same defect is the line being written ONTO.  Its tiles are deleted and other
 * tiles put in their place, so what the setup says about those squares is about track that is gone -
 * and reconcile cannot catch that one at all, because it drops setup from squares that are EMPTY and
 * these are occupied, just by something else.  A copied column therefore arrived carrying whatever
 * station names the column it landed on used to have.
 *
 * So there are two rules, and they are tested apart because they fail apart:
 *
 *   - what is VACATED travels, on a move, and stays put on a copy
 *   - what is BUILT OVER is forgotten, on a move and on a copy alike
 *
 * These run against the rule and the store rather than against the editor, which is a window wanting a
 * whole running TrainControlUI behind it.  What that leaves uncovered is the single line in
 * executeTool that calls planBulkLine at all - see the note at the bottom of this file.
 */
public class testLayoutEditorBulkEdits
{
    private static final String PAGE = "1 - Main";

    /**
     * A column carries its stations to the new column.
     *
     * The bug, in one assertion: everything about the square, on every square of the line.
     */
    @Test
    public void testAMovedColumnTakesItsSetupWithIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = at(5, 3);

        store.setStation(was, true);
        store.setPointName(was, "BottomInner");
        store.setTileLength(was, 42);
        store.setBarredArrivals(was, sides(TilePorts.Side.N));
        store.setTileDirection(was, new RouteId(0, 0), Direction.TOWARD_A);

        apply(store, plan(true, 5, 9, 8, occupied(3), true));

        TileKey now = at(9, 3);

        assertTrue(store.isStation(now),
            "the station did not travel with the column - which is the whole bug: a setup destroyed "
            + "by moving a column of track that autonomy was using");

        assertEquals(store.getPointName(now), "BottomInner", "it arrived unnamed");

        assertEquals(store.getTileLength(now), 42, "the length stayed behind");

        assertEquals(store.getBarredArrivals(now), sides(TilePorts.Side.N),
            "the arrival restriction stayed behind, so trains may now arrive from a side the "
            + "operator had shut");

        assertEquals(store.getTileDirection(now, new RouteId(0, 0)), Direction.TOWARD_A,
            "the facing stayed behind, and a facing left on bare track is dropped by the next "
            + "reconcile");

        assertFalse(store.isStation(was), "the old square is still a station, with no track on it");

        assertNull(store.getPointName(was), "and is still named");
    }

    /**
     * A pairing survives, and so does the half of it that lives on another page.
     *
     * This is what Adam saw.  A pairing is two entries, one at each end, and moving one end has to
     * rewrite the OTHER end's entry as well - otherwise the partner points at a square that no longer
     * holds a link and the pair is broken from the far side while looking intact from the near one.
     */
    @Test
    public void testAMovedLinkKeepsItsPartnerAndItsPartnerKeepsIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = at(5, 3);
        TileKey partner = new TileKey("2 - Bottom", 10, 9);

        store.pairPortals(was, partner);
        store.setLinkName(was, "ToTheLowerLevel");
        store.setPortalDisabled(was, true);

        apply(store, plan(true, 5, 9, 8, occupied(3), true));

        TileKey now = at(9, 3);

        assertEquals(store.getPortalPartner(now), partner,
            "the link arrived unpaired, which is exactly what Adam reported after moving a column");

        assertEquals(store.getPortalPartner(partner), now,
            "the far end is still pointing at the square the link used to be on - so the pair is "
            + "broken from the other page, where nobody was looking");

        assertEquals(store.getLinkName(now), "ToTheLowerLevel", "the link lost its name");

        assertTrue(store.isPortalDisabled(now),
            "a link the operator had switched off came back on when the column moved, which puts "
            + "trains through a hole they were told to leave alone");

        assertFalse(store.isPortalDisabled(was), "and the old square is still switched off");
    }

    /**
     * A caption follows its square, and a caption elsewhere still names the station that moved.
     */
    @Test
    public void testCaptionsFollowAndKeepPointingAtTheRightStation()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey station = at(5, 3);
        TileKey labelOnTheColumn = at(5, 4);
        TileKey labelElsewhere = at(12, 3);

        store.setStation(station, true);
        store.setCaption(labelOnTheColumn, station);
        store.setCaption(labelElsewhere, station);

        apply(store, plan(true, 5, 9, 8, occupied(3, 4), true));

        assertEquals(store.getCaptionTarget(at(9, 4)), at(9, 3),
            "the caption that moved with the column is naming the square the station used to be on");

        assertEquals(store.getCaptionTarget(labelElsewhere), at(9, 3),
            "a caption that did NOT move still has to follow the station it names - it is a "
            + "reference, not a location");
    }

    /**
     * The line being written over lets go of what it was.
     *
     * Reconcile cannot find these: it drops setup from squares that are empty, and one of these is
     * not empty, it is occupied by somebody else's track.
     */
    @Test
    public void testTheColumnBeingBuiltOverIsForgotten()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey doomed = at(9, 6);

        store.setStation(doomed, true);
        store.setPointName(doomed, "TopMainR1");
        store.setTileLength(doomed, 12);
        store.setPortalDisabled(doomed, true);

        apply(store, plan(true, 5, 9, 8, occupied(3), true));

        assertFalse(store.isStation(doomed),
            "a square that has been built over is still a station - it is now a different piece of "
            + "track wearing another square's name");

        assertNull(store.getPointName(doomed), "and still carries the old name");

        assertEquals(store.getTileLength(doomed), 0, "and the old length");

        assertFalse(store.isPortalDisabled(doomed), "and is still switched off");
    }

    /**
     * A COPY takes nothing away from the source, and still clears what it lands on.
     *
     * Two squares cannot both be one station, so nothing travels on a copy.  But the line being
     * copied onto is being built over just the same.
     */
    @Test
    public void testACopiedColumnLeavesTheSourceAloneAndStillClearsTheTarget()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey source = at(5, 3);
        TileKey doomed = at(9, 3);

        store.setStation(source, true);
        store.setPointName(source, "BottomInner");

        store.setStation(doomed, true);
        store.setPointName(doomed, "SomethingElse");

        apply(store, plan(true, 5, 9, 8, occupied(3), false));

        assertTrue(store.isStation(source), "the copy took the station away from the square copied FROM");

        assertEquals(store.getPointName(source), "BottomInner", "and its name with it");

        assertFalse(store.isStation(doomed),
            "the square copied ONTO kept its station, so a copied column arrives carrying the names "
            + "of the column it replaced");

        assertNull(store.getPointName(doomed), "and kept its name");
    }

    /**
     * A row is the same rule with the axes swapped, which is the sort of thing that gets typed once
     * and pasted once.
     */
    @Test
    public void testAMovedRowTakesItsSetupWithIt()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        TileKey was = at(3, 5);

        store.setStation(was, true);
        store.setPointName(was, "BottomInner");

        apply(store, plan(false, 5, 9, 8, occupied(3), true));

        assertTrue(store.isStation(at(3, 9)),
            "the row moved along the wrong axis - x and y are swapped somewhere");

        assertEquals(store.getPointName(at(3, 9)), "BottomInner", "the name did not follow the row");

        assertFalse(store.isStation(was), "the old square is still a station");
    }

    /**
     * An empty square on the source line moves nothing, and invents nothing.
     */
    @Test
    public void testEmptySquaresOnTheLineCarryNothing()
    {
        LayoutEditor.BulkPlan plan = plan(true, 5, 9, 8, occupied(3), true);

        assertEquals(plan.moves.size(), 1,
            "every square on the column was moved, including the ones with no track on them - which "
            + "would carry a blank square's setup over the top of a real one");

        assertTrue(plan.moves.containsKey(at(5, 3)), "the one occupied square is not the one that moved");
    }

    /**
     * Every square of the destination line is forgotten, occupied or not.
     *
     * A destination square whose source was empty has its tile DELETED and nothing put back, so its
     * setup is about track that is gone just as much as the overwritten ones are.
     */
    @Test
    public void testTheWholeDestinationLineIsForgottenNotOnlyTheOverwrittenPart()
    {
        LayoutEditor.BulkPlan plan = plan(true, 5, 9, 8, occupied(3), true);

        assertEquals(plan.builtOver.size(), 8,
            "only part of the destination column is being let go of - the squares whose source was "
            + "empty keep their setup, and their track has gone");

        for (int y = 0; y < 8; y++)
        {
            assertTrue(plan.builtOver.contains(at(9, y)), "row " + y + " of the destination was missed");
        }
    }

    /**
     * A square that is both landed on and vacated keeps what it is taking with it.
     *
     * Cannot arise for a column moved onto a different column, and is silent data loss the day some
     * other operation reuses this rule: the forgetting happens first, so a square that is about to
     * travel would be emptied before it went anywhere.
     *
     * Asserted through the store rather than through the plan, because the store is what decides it -
     * that is the point of handing it both halves in one call.
     */
    @Test
    public void testASquareThatIsBothSourceAndTargetIsNotForgotten()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.setStation(at(5, 3), true);
        store.setPointName(at(5, 3), "Travelling");

        store.setStation(at(6, 3), true);
        store.setPointName(at(6, 3), "BuiltOver");

        LayoutEditor.BulkPlan plan = new LayoutEditor.BulkPlan();

        plan.moves.put(at(5, 3), at(6, 3));
        plan.builtOver.add(at(6, 3));
        plan.builtOver.add(at(5, 3));

        apply(store, plan);

        assertEquals(store.getPointName(at(6, 3)), "Travelling",
            "the square whose setup was on its way somewhere else was emptied before it left, which "
            + "throws away the thing the move exists to carry");
    }

    /**
     * Moving a line onto itself does nothing at all.
     */
    @Test
    public void testALineMovedOntoItselfIsNotAnEdit()
    {
        LayoutEditor.BulkPlan plan = plan(true, 5, 5, 8, occupied(3), true);

        assertTrue(plan.moves.isEmpty(), "a column moved onto itself produced moves");

        assertTrue(plan.builtOver.isEmpty(),
            "a column moved onto itself is about to forget its own setup, which deletes the line "
            + "rather than moving it");
    }

    /**
     * A paste that overlaps the squares it was cut from still carries all of them (RC-A6).
     *
     * RC-A1 made cutMoves ask, per origin, whether that square is still empty.  Right question, wrong
     * moment: pasteSelection places its tiles first, so an origin that is also a LANDING has been
     * refilled by this very paste before cutMoves looks at it.
     *
     * That is not merely a skipped move.  A landing square is in `builtOver`, and the per-index sparing
     * only spares a landing that equals its OWN origin - so the setup on such a square was forgotten as
     * built-over while nothing had carried it anywhere.  Destroyed, where before RC-A1 it moved.
     *
     * Cut a column and paste it one square down and that is every square on the column except the top.
     * Two stations, one square apart, so both the moved-onto-a-landing case and the ordinary case are
     * in the same run.
     */
    @Test
    public void testAPasteOverlappingItsOwnOriginCarriesEveryone() throws Exception
    {
        withEditor("tc-cut-overlap", (editor, session, page) ->
        {
            // A second station, one square below the one withEditor sets up.
            session.getStore().setStation(at(page, 5, 4), true);
            session.getStore().setPointName(at(page, 5, 4), "The One Below");

            // A THREE-SQUARE STRIP, not the whole column.  A full-height selection shifted down can
            // never fit - pasteSelection refuses it with a modal dialog, and a modal dialog inside
            // invokeAndWait is a deadlock, which is how this test hung a suite for six hours (RC-B10).
            editor.getSelection().addRectangle(5, 2, 5, 4);
            editor.cutSelection();

            // One square DOWN, so the middle and bottom origins are also landings.
            editor.pasteSelection(5, 3);
        }, (session, page) ->
        {
            assertTrue(session.getStore().isStation(at(page, 5, 4)),
                "the upper station did not arrive one square down - and its origin (5,3) was the top "
                + "of the strip, so if this failed the whole carry is broken, not just the overlap");

            assertEquals(session.getStore().getPointName(at(page, 5, 4)), "Cut And Carried",
                "the upper station arrived, but not as itself");

            assertTrue(session.getStore().isStation(at(page, 5, 5)),
                "the LOWER station did not arrive.  Its origin square (5,4) is also where the upper "
                + "one landed, so the paste had already refilled it by the time cutMoves asked "
                + "whether it was empty - and a refilled origin is a landing, so its setup was then "
                + "forgotten as built over rather than carried (RC-A6)");

            assertEquals(session.getStore().getPointName(at(page, 5, 5)), "The One Below",
                "the lower station arrived carrying the wrong name, so the two moves crossed");
        });
    }

    /**
     * Undo after a shrink does not put a station’s name back outside the page (RC-B1).
     *
     * shrinkEdges snapshots for undo BEFORE it drops the captions on the row and column it is about to
     * remove - that is LE-B1’s fix, and correct on its own path.  Undo then restores the components
     * and the captions, but the page SIZE is not part of an undo entry, and a shrink is only offered
     * when the trimmed edge holds no track, so no restored component pins the size back either.
     *
     * So Ctrl+Z after "-" gives the caption back and not the row it stood on: a name that is present in
     * the setup, never drawn, and with no square left to click to remove it.  Exactly the state LE-B1
     * was raised for, reached through undo instead of through the shrink.
     *
     * RC-B1 was fixed with no test of its own and its disposition said otherwise, which is the failure
     * this file exists to catch in the editor and did not catch in itself.
     */
    @Test
    public void testUndoingAShrinkDoesNotRestoreACaptionOffThePage() throws Exception
    {
        withEditor("tc-shrink-undo", (editor, session, page) ->
        {
            // On the last row, which is what the shrink takes away.  A caption goes on a blank square
            // by preference, so this is where one naturally ends up.
            session.setCaption(at(page, 3, 15), at(page, 5, 3));

            editor.shrinkEdges();

            editor.undo();
        }, (session, page) ->
        {
            assertFalse(session.captionsOnPage(page).containsKey(at(page, 3, 15)),
                "undo put the station name back on a square the page no longer has - present in the "
                + "setup, never drawn, and with nowhere left to click to remove it, which is the whole "
                + "of LE-B1 reached through undo instead of through the shrink (RC-B1)");
        });
    }

    /**
     * A cut still carries its setup when the paste is not the very next thing the user does (RC-A1).
     *
     * `clipboardWasCut` is the ONLY thing that carries a group cut's setup - cutSelection calls
     * deleteSelection(false), which deliberately tells autonomy nothing, because the paste is the other
     * half of the gesture. LE-A6 stood that flag down inside snapshotLayout, on the reasoning that any
     * edit falsifies it. Every edit snapshots, so any edit at all between the two halves - growing the
     * diagram to make room, rotating a tile, dropping one from the palette - turned the move back into
     * a delete-and-lose, with nothing on screen to say so.
     *
     * Here the user cuts a set-up column, presses "+" to make room, and pastes. Growing is the most
     * likely of those gestures and the most obviously harmless: it adds a column on the right and a row
     * at the bottom, and moves nothing.
     *
     * A SOURCE SCAN CANNOT SEE THIS, which is why it is here and not in testTheEditorTellsAutonomy.
     * The flag is read in the right place and written in the right place; what is wrong is when the
     * write happens relative to the read, and that is only visible by driving the two gestures apart.
     */
    @Test
    public void testAnEditBetweenTheCutAndThePasteStillCarriesTheSetup() throws Exception
    {
        withEditor("tc-cut-then-edit", (editor, session, page) ->
        {
            editor.selectColumn(5);
            editor.cutSelection();

            // The unrelated edit.  Right and bottom only, so no square this test names moves.
            editor.growEdges();

            editor.pasteSelection(9, 0);
        }, (session, page) ->
        {
            assertTrue(session.getStore().isStation(at(page, 9, 3)),
                "the station did not arrive with the pasted column, because an unrelated edit between "
                + "the cut and the paste stood the cut flag down - so the paste ran as a copy and the "
                + "setup was left on squares the cut had emptied, where the next reconciling save "
                + "prunes it (RC-A1)");

            assertEquals(session.getStore().getPointName(at(page, 9, 3)), "Cut And Carried",
                "it arrived unnamed");

            assertFalse(session.getStore().isStation(at(page, 5, 3)),
                "the square the cut emptied is still a station");
        });
    }

    /**
     * An undo between the cut and the paste leaves the setup where the track came back (RC-A1).
     *
     * The other direction, and the reason LE-A6 stood the flag down in the first place. Undo puts the
     * cut track back on the squares it emptied, so those squares are occupied again and their setup
     * belongs to what is standing on them - carrying it to the paste target would be the loss LE-A6
     * was written to prevent, arriving from the opposite side.
     *
     * The fix is not the flag but the question it was standing in for, asked per square: is the square
     * this setup came from still EMPTY? Undo makes the answer no for exactly the squares it refilled,
     * which is why this test and the one above can both pass.
     */
    @Test
    public void testUndoingTheCutBeforeThePasteLeavesTheSetupWhereItIs() throws Exception
    {
        withEditor("tc-cut-then-undo", (editor, session, page) ->
        {
            editor.selectColumn(5);
            editor.cutSelection();

            editor.undo();

            editor.pasteSelection(9, 0);
        }, (session, page) ->
        {
            assertFalse(session.getStore().isStation(at(page, 9, 3)),
                "the setup was carried to the paste target even though undo had put the cut track "
                + "back on the squares it came from - so a station now names a square whose track was "
                + "never moved, and the square it belongs to has lost it (RC-A1)");

            assertTrue(session.getStore().isStation(at(page, 5, 3)),
                "undo put the track back and the station did not come with it");
        });
    }

    /**
     * A paste that carried nothing does not use up the cut (SVN-B11).
     *
     * `clipboardWasCut` says "the squares these tiles came from are empty now, so the setup should
     * move with them". It was cleared by **any** paste that reached `cutMoves`, and `cutMoves` returns
     * an empty-but-not-null map whenever no origin is both cut and still empty - after an undo has put
     * the track back, or on another page, where the setup is deliberately left behind to be picked up
     * on the way back.
     *
     * So a paste that moved nothing spent the cut, and the next paste - the one that would have
     * carried it - fell into the `else` and called `forgetBuiltOver` on the origins. Pasting the block
     * back where it came from then **destroyed** its setup instead of leaving it alone, which is
     * `LE-A4` arriving through a door the `LE-A3`/`RC-A1` rewrite opened.
     *
     * **One square, not a column**, and that is the whole difficulty of writing this. A column cut
     * from this fixture has fifteen squares that were never occupied; `emptyCutOrigins` counts them as
     * vacated - rightly, an empty square can still hold a caption - so the map comes back with fifteen
     * moves in it and the paste is a real move that spends the cut fairly. The sequence the finding is
     * about needs every cut square full again, which here means cutting only the square that has
     * anything on it.
     *
     * MUTATION: clearing `clipboardWasCut` unconditionally - which is what `:2967` did - fails this.
     */
    @Test
    public void testAPasteThatCarriedNothingDoesNotUseUpTheCut() throws Exception
    {
        withEditor("tc-cut-spent", (editor, session, page) ->
        {
            // The one square with anything on it.  There is no single-square door on the editor -
            // selectRow and selectColumn are the two it offers - and a row or a column would drag in
            // squares that were empty before the cut, which is the case this test exists to avoid.
            try
            {
                java.lang.reflect.Field picked =
                    org.traincontrol.gui.LayoutEditor.class.getDeclaredField("selection");

                picked.setAccessible(true);

                ((org.traincontrol.base.TileSelection) picked.get(editor)).addRectangle(5, 3, 5, 3);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }

            editor.cutSelection();

            // The origin is full again, so nothing is a move any more...
            editor.undo();

            // ...and this paste therefore carries no setup and must not spend the cut.
            editor.pasteSelection(9, 0);

            // Back where it came from.  The landing square gets its own tile, so there is nothing here
            // to forget - unless the paste above has already been read as the move.
            editor.pasteSelection(5, 3);
        }, (session, page) ->
        {
            assertTrue(session.getStore().isStation(at(page, 5, 3)),
                "pasting the square back where it came from destroyed its setup.  The paste before "
                + "it carried nothing - undo had put the track back on the origin - but it spent "
                + "clipboardWasCut all the same, so this paste took the else branch and called "
                + "forgetBuiltOver on the square the block came from (SVN-B11)");
        });
    }

    /**
     * An undone cut is outstanding nowhere, not mostly (SVN-B11, second half).
     *
     * `clipboardCutSquares` is every picked square, occupied or not - deliberately, because an empty
     * square can still hold a caption worth moving. `emptyCutOrigins` then asked each of them "are you
     * empty now?", and **a square that was always empty answers yes whether the cut has been undone or
     * not**. A column selection is mostly such squares.
     *
     * So: cut a column, press Ctrl+Z, and fifteen of its sixteen squares still said the cut was
     * outstanding. The move map came back with fifteen entries in it, the next paste was read as the
     * move and spent the cut - and the setup on the one square that had any track, correctly left
     * behind because that square was full again, was then forgotten by the paste that put the block
     * back. The station was destroyed by a sequence whose second step was an undo.
     *
     * Undo is atomic, so the question is now asked of the squares the cut emptied **of a tile**: if any
     * of those is full again, the cut has been put back and nothing is outstanding.
     *
     * Found while writing the test above, which failed against this rather than against the flag it
     * was aimed at - the two are one gesture apart and need separate fixes.
     *
     * **One assertion, not two.** This carried a second - that the station had not travelled to
     * `9,3` - and no implementation can make it false (`VD9-C6`): with the fix nothing moves at all,
     * and without it `5,3` is full again, so it is excluded from `vacated` and skipped by `cutMoves`.
     * A check that both branches satisfy is not a check.
     *
     * **One assertion, not two.** This carried a second - that the station had not travelled to
     * `9,3` - and no implementation can make it false (`VD9-C6`): with the fix nothing moves at all,
     * and without it `5,3` is full again, so it is excluded from `vacated` and skipped by `cutMoves`.
     * A check that both branches satisfy is not a check.
     *
     * MUTATION: dropping the `clipboardCutHadTiles` check from `emptyCutOrigins` fails this.
     */
    @Test
    public void testAnUndoneCutIsNotStillOutstandingOnItsEmptySquares() throws Exception
    {
        withEditor("tc-cut-undone", (editor, session, page) ->
        {
            editor.selectColumn(5);
            editor.cutSelection();

            editor.undo();

            // Fifteen of the sixteen squares on this column were never occupied, so before the fix
            // this paste carried their setup away and spent the cut with it.
            editor.pasteSelection(9, 0);

            editor.pasteSelection(5, 0);
        }, (session, page) ->
        {
            assertTrue(session.getStore().isStation(at(page, 5, 3)),
                "a cut column, undone, and pasted back lost the station on it.  The undo put the "
                + "track back, but the fifteen squares on that column which were never occupied "
                + "still read as emptied by the cut - so the paste in between looked like the move, "
                + "spent the cut, and left this square's setup behind for the paste back to forget "
                + "(SVN-B11, second half)");

        });
    }

    /** What the editor does between the cut and the paste. */
    private interface Gesture
    {
        void run(LayoutEditor editor, AutonomySession session, String page);
    }

    /** What has to be true of the setup afterwards. */
    private interface Check
    {
        void run(AutonomySession session, String page);
    }

    /**
     * One set-up square, a real editor over it, a gesture, and a look at what the setup says after.
     *
     * The same wiring as testTheRealColumnMoveGestureCarriesTheStation - a LayoutEditor that never
     * shows itself and an AutonomySession in a temp folder, joined through the reflectively-set
     * TrainControlUI field, so the call sites under test are the real ones. Factored out because two
     * tests need it and a third copy of forty lines of wiring is where the drift starts.
     *
     * @param folder a name for the temp autonomy folder, so a failure says which test left it
     * @param gesture what to do to the editor, run on the event thread
     * @param check what to assert afterwards, run on this thread
     */
    private void withEditor(String folder, Gesture gesture, Check check) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the editor is a window");
        }

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];
        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        try
        {
            // Before the model, not just before the window (OB-111).
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            java.io.File autonomyFolder = java.nio.file.Files.createTempDirectory(folder).toFile();

            AutonomySession session = new AutonomySession(autonomyFolder);

            LayoutDiagram diagram = new LayoutDiagram("Cut Page", 21, 16, null, null);

            diagram.addComponent(componentType.FEEDBACK, 5, 3, 0, 0, 1, 1,
                accessoryDecoderType.MM2, null);

            // Every square counts towards the bounds, not only the one with track on it.
            diagram.setEdit(true);
            diagram.checkBounds();

            session.open(Arrays.asList(diagram));

            final String page = diagram.getName();

            session.getStore().setStation(at(page, 5, 3), true);
            session.getStore().setPointName(at(page, 5, 3), "Cut And Carried");

            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], session);

            javax.swing.SwingUtilities.invokeAndWait(() ->
                editor[0] = new org.traincontrol.gui.LayoutEditor(diagram, 30, ui[0], 0));

            java.lang.reflect.Method drawGrid =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredMethod("drawGrid");
            drawGrid.setAccessible(true);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    drawGrid.invoke(editor[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            // ON A DEADLINE, not invokeAndWait (RC-B10).
            //
            // A gesture that makes the editor raise a modal dialog - an out-of-bounds paste is one -
            // deadlocks invokeAndWait outright: the gesture waits for the dialog and the dialog waits
            // for a click.  One did, and the suite sat on it for six hours using twelve seconds of CPU,
            // reporting nothing, because a class that prints no summary reads like a class that passed.
            //
            // A minute is far more than any of these gestures needs and far less than a working day.
            // The failure names the likely cause, because the stack trace of a blocked event thread
            // does not.
            final java.util.concurrent.CountDownLatch finished =
                new java.util.concurrent.CountDownLatch(1);

            final Throwable[] thrown = new Throwable[1];

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                try
                {
                    gesture.run(editor[0], session, page);
                }
                catch (Throwable bad)
                {
                    thrown[0] = bad;
                }
                finally
                {
                    finished.countDown();
                }
            });

            assertTrue(finished.await(60, java.util.concurrent.TimeUnit.SECONDS),
                "the gesture did not finish in a minute.  The event thread is blocked - the usual "
                + "cause is the editor raising a modal dialog that nothing will ever click, and the "
                + "usual reason for THAT is a paste or a shift that does not fit on the page "
                + "(RC-B10)");

            if (thrown[0] instanceof Error) throw (Error) thrown[0];
            if (thrown[0] != null) throw new RuntimeException(thrown[0]);

            check.run(session, page);
        }
        finally
        {
            // POSTED, NOT WAITED ON (RC-B10).  If the deadline above expired the event thread is
            // still blocked, and waiting for it here would turn a reported failure back into a hang.
            if (editor[0] != null)
            {
                javax.swing.SwingUtilities.invokeLater(() -> editor[0].dispose());
            }

            if (ui[0] != null)
            {
                javax.swing.SwingUtilities.invokeLater(() -> ui[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The real gesture - cut a column, drop it on another one - carries the station with it.
     *
     * Every test above hands `planBulkLine`'s arguments, or its result, straight to the assertions.
     * Adam's report came through `LayoutEditor.executeTool`'s COL branch, which builds those same
     * arguments itself from `layout.getName()`, the column a drag started on, the column it ended on,
     * and whether it was a drag or a paste - and nothing anywhere in the suite ever calls it; grepping
     * `test/` for `planBulkLine` finds only tests, here and in testDeleteAndInsertKeepTheSetup and
     * testStationLabelsFollowMoves, that build the call themselves.
     *
     * A LayoutEditor that never shows itself and an AutonomySession in a temp folder, wired to each
     * other the way the running application wires the real ones - through TrainControlUI.
     * getAutonomySession(), reflectively pointed at this session instead of whatever the sandboxed
     * fixture would otherwise build - so the call site under test is the actual one, not a stand-in.
     *
     * MUTATION this catches: swap the endpoints at the COL branch's call -
     * `planBulkLine(layout.getName(), true, destCol, startCol, sourceColumn.size(), occupied, isMove)`
     * - and the station this test sets on the source column arrives nowhere: moveTiles is told the
     * setup travelled from the empty destination to the square the drag started on, so BOTH squares
     * end up with nothing, and the assertion that the destination now holds it fails. Passing
     * `!isMove` has the same effect by a different route: the diagram's track still moves - execCopy
     * and delete do not consult this argument - but `moves` is never populated, so the station is
     * left behind, unnamed, on the bare square the track just vacated.
     */
    @Test
    public void testTheRealColumnMoveGestureCarriesTheStation() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the editor is a window");
        }

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];
        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        try
        {
            // Before the model, not just before the window (OB-111) - constructing a TrainControlUI
            // reads the layout-path preference, and without the sandbox it is Adam's own railway.
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            // Our own page, in our own temp folder - not "1 - Main", so this cannot be confused with
            // the sandboxed fixture and does not depend on what it happens to contain.
            java.io.File autonomyFolder =
                java.nio.file.Files.createTempDirectory("tc-bulk-gesture").toFile();

            AutonomySession session = new AutonomySession(autonomyFolder);

            LayoutDiagram diagram = new LayoutDiagram("Bulk Page", 21, 16, null, null);

            int startCol = 5;
            int destCol = 15;

            diagram.addComponent(componentType.FEEDBACK, startCol, 3, 0, 0, 1, 1,
                accessoryDecoderType.MM2, null);

            // Every square counts towards the bounds, not only the one with track on it - otherwise
            // checkBounds ties the diagram's extent to that single square and the grid built from it
            // is too narrow to reach column 15 at all (see LayoutDiagram.checkBounds).
            diagram.setEdit(true);
            diagram.checkBounds();

            session.open(Arrays.asList(diagram));

            session.getStore().setStation(at(diagram.getName(), startCol, 3), true);
            session.getStore().setPointName(at(diagram.getName(), startCol, 3),
                "Moved Along The Column");

            // getAutonomySession() only ever builds a session when the field is still null - pointed
            // here at ours instead, so the editor's real call site writes to the session this test can
            // see rather than to whatever the sandboxed fixture would otherwise produce.
            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], session);

            javax.swing.SwingUtilities.invokeAndWait(() ->
                editor[0] = new org.traincontrol.gui.LayoutEditor(diagram, 30, ui[0], 0));

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
                    // Builds the grid the constructor leaves for the first paint to ask for.
                    drawGrid.invoke(editor[0]);

                    org.traincontrol.gui.LayoutGrid grid =
                        (org.traincontrol.gui.LayoutGrid) gridField.get(editor[0]);

                    org.traincontrol.gui.LayoutLabel source = grid.getValueAt(startCol, 0);
                    org.traincontrol.gui.LayoutLabel dest = grid.getValueAt(destCol, 0);

                    // The real gesture, in the real order: pick the column up, then drop it on
                    // another one - see LayoutEditor's mouse handling, which calls these two the
                    // same way.
                    editor[0].initCopy(source, null, true);
                    editor[0].executeTool(dest, org.traincontrol.gui.LayoutEditor.bulk.COL);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(session.getStore().isStation(at(diagram.getName(), destCol, 3)),
                "the station did not arrive on the column it was dropped on through the real editor "
                + "gesture - testAMovedColumnTakesItsSetupWithIt proves the RULE carries it correctly, "
                + "so the gap is in the call that feeds the rule its arguments");

            assertEquals(session.getStore().getPointName(at(diagram.getName(), destCol, 3)),
                "Moved Along The Column", "it arrived unnamed");

            assertFalse(session.getStore().isStation(at(diagram.getName(), startCol, 3)),
                "the square the column moved away from is still a station");
        }
        finally
        {
            if (editor[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    // ----------------------------------------------------------------------------------------
    // What the editor does with the plan, in the same order, so that the two calls are exercised
    // together.  applyBulkPlan adds only the null checks and the save - see LayoutEditor.
    // ----------------------------------------------------------------------------------------

    private static void apply(AutonomyCompanionStore store, LayoutEditor.BulkPlan plan)
    {
        store.moveTiles(plan.moves, plan.builtOver);
    }

    private static LayoutEditor.BulkPlan plan(boolean column, int from, int to, int span,
        Set<Integer> occupied, boolean move)
    {
        return LayoutEditor.planBulkLine(PAGE, column, from, to, span, occupied, move);
    }

    private static Set<Integer> occupied(Integer... indices)
    {
        return new LinkedHashSet<>(Arrays.asList(indices));
    }

    private static Set<TilePorts.Side> sides(TilePorts.Side... sides)
    {
        return new LinkedHashSet<>(Arrays.asList(sides));
    }

    private static TileKey at(int x, int y)
    {
        return new TileKey(PAGE, x, y);
    }

    private static TileKey at(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    /**
     * Nothing outlines the grid's padding.
     *
     * Adam, on MT-228: "the flicker is gone, but when dragging the selected tiles to the bottom of the
     * diagram, a phantom row gets permanently highlighted in blue."
     *
     * The grid is built one row taller and one column wider than the diagram, and that extra row and
     * column are blank labels that hold the GridBagLayout together (OB-055).  getValueAt hands them out
     * like any other square, so a group dragged onto the last row has its landing outline - the blue
     * one - painted straight onto the padding underneath.  And clearBordersFromChildren deliberately
     * leaves spacers alone, being the grid's own furniture rather than squares, so nothing ever takes
     * that outline off again.  Permanent, exactly as reported.
     *
     * Both doors are tested, because both are open: the landing set gets there through a drag, and the
     * picked set gets there through a box released on the padding, which is the same mistake in red.
     *
     * MUTATION this catches: the whole of it.  Take the spacer guard out of highlightLabel and both
     * assertions fail with a border on a label that is not a square.
     */
    @Test
    public void testTheOutlineNeverLandsOnTheGridsPadding() throws Exception
    {
        withEditor("tc-landing-padding", (editor, session, page) ->
        {
            try
            {
                java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");
                gridField.setAccessible(true);

                org.traincontrol.gui.LayoutGrid grid =
                    (org.traincontrol.gui.LayoutGrid) gridField.get(editor);

                // The fixture's diagram is 21 x 16, so the padding is row 16.  Asserted rather than
                // assumed: if the grid ever stops being built one bigger than the diagram, this test
                // would pass by looking at nothing at all.
                org.traincontrol.gui.LayoutLabel padding = grid.getValueAt(5, 16);

                assertNotNull(padding, "the grid has no row 16, so this test is looking at nothing");

                assertTrue(padding.isSpacer(),
                    "row 16 is a real square, so this test is looking at the wrong row");

                assertNull(padding.getBorder(), "the padding started out with a border");

                java.lang.reflect.Field landingField =
                    LayoutEditor.class.getDeclaredField("landingSelection");
                landingField.setAccessible(true);

                org.traincontrol.base.TileSelection landing =
                    (org.traincontrol.base.TileSelection) landingField.get(editor);

                java.lang.reflect.Method refresh =
                    LayoutEditor.class.getDeclaredMethod("refreshSelectionBorders");
                refresh.setAccessible(true);

                // A group on the bottom row, dragged one row further down: the landing runs off the
                // diagram and onto the padding.
                editor.getSelection().add(5, 15);

                landing.add(5, 16);

                refresh.invoke(editor);

                assertNull(grid.getValueAt(5, 16).getBorder(),
                    "the padding row is wearing the blue landing outline, and clearBordersFromChildren "
                    + "will never take it off again - which is the phantom row Adam reported");

                // And the same square picked outright, which is what a selection box released on the
                // padding does.
                landing.clear();

                editor.getSelection().add(5, 16);

                refresh.invoke(editor);

                assertNull(grid.getValueAt(5, 16).getBorder(),
                    "the padding row is wearing the red selected outline, by the same door");

                // The real square above it still gets its outline, so the guard has not simply turned
                // the feature off.
                assertNotNull(grid.getValueAt(5, 15).getBorder(),
                    "the square that IS picked lost its outline, so the guard is too wide");
            }
            catch (ReflectiveOperationException bad)
            {
                throw new RuntimeException(bad);
            }
        },
        (session, page) -> { });
    }
}
