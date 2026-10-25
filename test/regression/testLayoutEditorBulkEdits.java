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
import org.traincontrol.gui.layouteditor.LayoutEditor;

/**
 * Moving or copying a whole line of squares at once, and what happens to the setup underneath it.
 *
 * A bulk edit copies the whole line into place with the move flag OFF and deletes the source afterwards, so the one
 * call that carries a square's setup (a move) was never made and reconcile threw the setup away; links show it first
 * because a pairing is mutual.  The line being written ONTO is the other half: its setup is about track that is gone,
 * and reconcile cannot catch it because those squares are occupied.  Two rules, tested apart because they fail apart:
 *
 *   - what is VACATED travels, on a move, and stays put on a copy
 *   - what is BUILT OVER is forgotten, on a move and on a copy alike
 *
 * These run against the store, since the editor is a window wanting a whole running TrainControlUI; the lines are built
 * by `support.TileLine`.
 */
public class testLayoutEditorBulkEdits
{
    private static final String PAGE = "1 - Main";

    /** A column carries its stations to the new column: everything about the square, on every square of the line. */
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
     * A pairing survives, and so does the half of it on another page: moving one end rewrites the OTHER end's entry.
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

    /** A caption follows its square, and a caption elsewhere still names the station that moved. */
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
     * The line being written over lets go of what it was; reconcile cannot find these, as they are occupied by other
     * track.
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

    /** A COPY takes nothing away from the source, and still clears what it lands on. */
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

    /** A row is the same rule with the axes swapped. */
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
     * A square that is both landed on and vacated keeps what it is taking with it: the forgetting happens first, so a
     * square about to travel would be emptied before it went anywhere.  Asserted through the store, which decides it.
     */
    @Test
    public void testASquareThatIsBothSourceAndTargetIsNotForgotten()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        store.setStation(at(5, 3), true);
        store.setPointName(at(5, 3), "Travelling");

        store.setStation(at(6, 3), true);
        store.setPointName(at(6, 3), "BuiltOver");

        support.TileLine plan = new support.TileLine();

        plan.moves.put(at(5, 3), at(6, 3));
        plan.builtOver.add(at(6, 3));
        plan.builtOver.add(at(5, 3));

        apply(store, plan);

        assertEquals(store.getPointName(at(6, 3)), "Travelling",
            "the square whose setup was on its way somewhere else was emptied before it left, which "
            + "throws away the thing the move exists to carry");
    }

    /**
     * A paste that overlaps the squares it was cut from still carries all of them: pasteSelection places its tiles
     * first, so an origin that is also a LANDING was refilled before cutMoves looked, and its setup was forgotten as
     * built-over.  Two stations one square apart cover both the landing case and the ordinary one.
     */
    @Test
    public void testAPasteOverlappingItsOwnOriginCarriesEveryone() throws Exception
    {
        withEditor("tc-cut-overlap", (editor, session, page) ->
        {
            // A second station, one square below the one withEditor sets up.
            session.getStore().setStation(at(page, 5, 4), true);
            session.getStore().setPointName(at(page, 5, 4), "The One Below");

            // A THREE-SQUARE STRIP, not the whole column: a full-height selection shifted down is refused with a modal
            // dialog, which deadlocks invokeAndWait.
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
     * Undo after a shrink does not put a station's name back outside the page: the page SIZE is not part of an undo
     * entry, so the restored caption would be present in the setup, never drawn, with no square to click to remove it.
     */
    @Test
    public void testUndoingAShrinkDoesNotRestoreACaptionOffThePage() throws Exception
    {
        withEditor("tc-shrink-undo", (editor, session, page) ->
        {
            // On the last row, which is what the shrink takes away.
            session.setCaption(at(page, 3, 15), at(page, 5, 3));

            // THE CAPTION IS THERE BEFORE THE SHRINK, or the absence below means nothing
            assertEquals(session.captionsOnPage(page).get(at(page, 3, 15)), at(page, 5, 3),
                "precondition: the caption was not set on the last row, so its absence after the undo shows nothing");

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
     * A cut still carries its setup when the paste is not the very next thing the user does: `clipboardWasCut` is the
     * only thing that carries a group cut's setup, and any edit between the halves (growing the diagram, rotating a
     * tile) once turned the move into a delete-and-lose.  A source scan cannot see this, since the flag is read and
     * written in the right places and what is wrong is the order.
     */
    @Test
    public void testAnEditBetweenTheCutAndThePasteStillCarriesTheSetup() throws Exception
    {
        withEditor("tc-cut-then-edit", (editor, session, page) ->
        {
            editor.selectColumn(5);
            editor.cutSelection();

            // The unrelated edit, right and bottom only, so no square this test names moves.
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
     * An undo between the cut and the paste leaves the setup where the track came back: those squares are occupied
     * again, so their setup belongs to what stands on them.  The question is asked per square (is the origin still
     * EMPTY?).
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
     * A paste that carried nothing does not use up the cut: `cutMoves` returns an empty map after an undo or on another
     * page, and clearing `clipboardWasCut` then made the next paste destroy the setup it should have carried.  One
     * square, not a column, because `emptyCutOrigins` counts the column's never-occupied squares as vacated and the
     * paste would be a real move.
     *
     * MUTATION: clearing `clipboardWasCut` unconditionally fails this.
     */
    @Test
    public void testAPasteThatCarriedNothingDoesNotUseUpTheCut() throws Exception
    {
        withEditor("tc-cut-spent", (editor, session, page) ->
        {
            // The one square with anything on it: the editor has no single-square door, and a row or column would drag
            // in empty squares.
            try
            {
                java.lang.reflect.Field picked =
                    org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredField("selection");

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

            // Back where it came from.  The landing square gets its own tile, so there is nothing to forget unless the
            // paste above was read as the move.
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
     * An undone cut is outstanding nowhere, not mostly: `emptyCutOrigins` asked each cut square "are you empty now?"
     * and a square that was always empty answers yes whether or not the cut was undone, so a column cut then undone
     * still moved fifteen of its sixteen squares.  The question is now asked of the squares the cut emptied of a tile.
     *
     * One assertion, not two: a second (the station did not travel to `9,3`) no implementation can make false.
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

            // Fifteen of the sixteen squares on this column were never occupied, so this paste once carried their setup
            // away.
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
     * One set-up square, a real editor over it, a gesture, and a look at what the setup says after.  A LayoutEditor
     * that never shows itself and an AutonomySession in a temp folder, joined through the reflectively-set
     * TrainControlUI field, so the call sites under test are the real ones; `folder` names the temp autonomy folder so
     * a failure says which test left it.
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
        final org.traincontrol.gui.layouteditor.LayoutEditor[] editor = new org.traincontrol.gui.layouteditor.LayoutEditor[1];

        try
        {
            // Before the model, not just before the window (OB-111).
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

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
                editor[0] = new org.traincontrol.gui.layouteditor.LayoutEditor(diagram, 30, ui[0], 0));

            java.lang.reflect.Method drawGrid =
                org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredMethod("drawGrid");
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

            // ON A DEADLINE, not invokeAndWait: a gesture that raises a modal dialog (an out-of-bounds paste) deadlocks
            // it, and a suite once sat on that for six hours reporting nothing.  A minute is far more than any gesture
            // needs, and the failure names the likely cause, since a blocked event thread's stack does not.
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
            // POSTED, NOT WAITED ON: if the deadline expired the event thread is still blocked, and waiting would turn
            // a failure back into a hang.
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

    // What a line moved at once asks of the store: one call, both halves.

    private static void apply(AutonomyCompanionStore store, support.TileLine plan)
    {
        store.moveTiles(plan.moves, plan.builtOver);
    }

    private static support.TileLine plan(boolean column, int from, int to, int span,
        Set<Integer> occupied, boolean move)
    {
        return support.TileLine.of(PAGE, column, from, to, span, occupied, move);
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
     * Nothing outlines the grid's padding (MT-228; Adam: "a phantom row gets permanently highlighted in blue.").  The
     * grid is one row taller and one column wider than the diagram, blank labels hold it together (OB-055), and
     * clearBordersFromChildren leaves spacers alone, so a landing outline painted there never came off.  Both doors are
     * tested: the landing set through a drag, the picked set through a box released on the padding.
     *
     * MUTATION: take the spacer guard out of highlightLabel and both assertions fail.
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

                // The fixture's diagram is 21 x 16, so the padding is row 16; asserted, or the test would look at
                // nothing.
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

                // A group on the bottom row, dragged one row further down: the landing runs off the diagram onto the
                // padding.
                editor.getSelection().add(5, 15);

                landing.add(5, 16);

                refresh.invoke(editor);

                assertNull(grid.getValueAt(5, 16).getBorder(),
                    "the padding row is wearing the blue landing outline, and clearBordersFromChildren "
                    + "will never take it off again - which is the phantom row Adam reported");

                // And the same square picked outright, as a selection box released on the padding does.
                landing.clear();

                editor.getSelection().add(5, 16);

                refresh.invoke(editor);

                assertNull(grid.getValueAt(5, 16).getBorder(),
                    "the padding row is wearing the red selected outline, by the same door");

                // The real square above it still gets its outline, so the guard has not simply turned the feature off.
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
