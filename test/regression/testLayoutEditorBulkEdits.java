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
}
