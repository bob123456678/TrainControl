package regression;

import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Cancel in the autonomy editor undoes the setup edits made in it (Adam, MT-406, 2026-09-14).
 *
 * *"Escape works, but it seems a one-way run (or any other edits to arrows) persist after I press cancel.
 * They are not undone by cancelling."*
 *
 * **WHY THEY PERSISTED.**  Every setup gesture in the editor rebuilds the running layout, so the railway
 * follows the edit at once - and that rebuild goes through `AutonomyViewerPanel.load`, which SAVES the setup
 * ("remembered for next start").  Saving clears the session's unsaved flag.  So by the time Cancel asked
 * `mayLeave` whether there was unsaved work, the answer was always no: the question was never put, nothing
 * was discarded, and the edit was already on disk.  The snapshot `LayoutEditor` takes when it opens -
 * `autonomyAsOpened`, the thing the track editor's Cancel restores - was never consulted in autonomy mode.
 *
 * **ON THE REAL PATH.**  The gesture is the click on a square (`AutonomyEditorPanel.cycle`), with an autonomy
 * configuration loaded, because the save that hid the edit happens only when the rebuild runs.  Cancel is
 * the button's own `confirmExit`.  A confirmation, if one is put up, is answered Yes by a watcher thread - so
 * the claim is about what Cancel DOES, and a second claim says it asks.
 *
 * **AND SAVE STILL KEEPS THEM**, the control: a Cancel that reverted everything, or an editor that never
 * saved, would pass the first claim.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).  Each claim puts the setup back as it found it.
 *
 * @author Adam
 */
public class testCancelUndoesAutonomyEdits
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
            throw new SkipException("the editor and its Cancel button need a display");
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

        // A CONFIGURATION LOADED, as it is on Adam's railway: the rebuild each gesture asks for - and the
        // save inside it that hid the edit - runs only when one is.
        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            assertNotNull(ui.getAutonomyViewerPanel(), "there is no autonomy panel to load a configuration through");

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            settle();
        }

        assertNotNull(ui.getActiveDiagramConfiguration(),
            "precondition: no configuration is loaded, so the per-gesture rebuild never runs and this class"
            + " would test an editor Adam does not use");
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

    /**
     * An arrow changed in the autonomy editor is put back by Cancel - in the session and in the file.
     */
    @Test
    public void testCancelPutsTheArrowsBack() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();

        final LayoutEditor editor = opened();

        Answerer answerer = null;

        try
        {
            TileKey square = aSquareWithOneRoute();

            Map<String, String> before = directions(session);

            click(editor, square);

            Map<String, String> edited = directions(session);

            assertNotEquals(edited, before, "precondition: clicking " + square + " changed no arrow, so there is"
                + " nothing for Cancel to undo");

            assertNotEquals(directions(fromDisk()), before, "precondition: the arrow did not reach the file, so the"
                + " per-gesture save that hid it from Cancel did not run and this is not Adam's editor");

            answerer = Answerer.start(String.valueOf(TrainControlUI.YES_NO_OPTS[0]));

            cancel(editor);

            answerer.stop();

            assertEquals(directions(session).toString(), before.toString(),
                "Cancel did not put the arrow back in the setup.  Adam, MT-406: 'a one-way run (or any other"
                + " edits to arrows) persist after I press cancel.  They are not undone by cancelling.'"
                + "  Asked before closing: " + answerer.answered());

            assertEquals(directions(fromDisk()).toString(), before.toString(),
                "Cancel put the arrow back in memory but the file still has the edit, so the next start brings"
                + " it back");
        }
        finally
        {
            if (answerer != null) answerer.stop();

            dispose(editor);

            session.restoreSetup(asFound);
        }
    }

    /**
     * And Cancel asks first, because there is something to throw away.
     *
     * `mayLeave` is written to ask whenever the setup has unsaved edits, and never did in autonomy mode:
     * the per-gesture save had always just cleared the flag it reads.
     */
    @Test
    public void testCancelAsksBeforeThrowingTheEditAway() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();

        final LayoutEditor editor = opened();

        Answerer answerer = null;

        try
        {
            click(editor, aSquareWithOneRoute());

            answerer = Answerer.start(String.valueOf(TrainControlUI.YES_NO_OPTS[0]));

            cancel(editor);

            answerer.stop();

            assertTrue(answerer.answered(),
                "Cancel closed the autonomy editor without asking, with an arrow changed in it - the"
                + " unsaved-work question is never put, because the setup has already been saved by the"
                + " time Cancel looks");
        }
        finally
        {
            if (answerer != null) answerer.stop();

            dispose(editor);

            session.restoreSetup(asFound);
        }
    }

    /**
     * Save keeps the arrow - the control for the two above.
     */
    @Test
    public void testSaveKeepsTheArrows() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();

        final LayoutEditor editor = opened();

        Answerer answerer = null;

        try
        {
            TileKey square = aSquareWithOneRoute();

            Map<String, String> before = directions(session);

            click(editor, square);

            Map<String, String> edited = directions(session);

            assertNotEquals(edited, before, "precondition: clicking " + square + " changed no arrow");

            // Any report the save puts up is dismissed - its first button.
            answerer = Answerer.start(null);

            save(editor);

            answerer.stop();

            assertEquals(directions(session), edited, "Save did not keep the arrow in the setup");
            assertEquals(directions(fromDisk()), edited, "Save did not keep the arrow in the file");
        }
        finally
        {
            if (answerer != null) answerer.stop();

            dispose(editor);

            session.restoreSetup(asFound);
        }
    }

    /**
     * And the locomotives a bulk clear took off come back with Cancel (WK7-C2).
     *
     * Found by the seven-day review of 2026-09-14: the discard restores the setup as the editor opened it,
     * placements included, and the rebuild when the editor closes regenerates placements from that setup -
     * `putTheTrainsBack` only moves trains that were standing, which the cleared ones no longer are.  So the
     * OB-194 warning, *"Cancel will not put them back"*, stopped being true when OB-223 made Cancel undo.  Asked
     * through the real Bulk Tools door and the real Cancel, in the setup and on the running railway.
     */
    @Test
    public void testCancelPutsBackTheLocomotivesABulkClearTook() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();

        final LayoutEditor editor = opened();

        Answerer answerer = null;

        try
        {
            Map<String, String> before = placements();

            assertFalse(before.isEmpty(), "precondition: no locomotive stands anywhere on the snapshot, so a bulk"
                + " clear takes nothing and Cancel has nothing to put back");

            final java.lang.reflect.Method clear =
                editor.getAutonomyPanel().getClass().getDeclaredMethod("clearAllPlacements");

            clear.setAccessible(true);

            answerer = Answerer.start(String.valueOf(TrainControlUI.YES_NO_OPTS[0]));

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    clear.invoke(editor.getAutonomyPanel());
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });

            settle();

            answerer.stop();

            assertTrue(placements().isEmpty(), "precondition: the bulk clear left " + placements() + " on the setup,"
                + " so there is nothing for Cancel to put back");

            answerer = Answerer.start(String.valueOf(TrainControlUI.YES_NO_OPTS[0]));

            cancel(editor);

            answerer.stop();

            settle();

            assertEquals(placements(), before,
                "Cancel did not put back the locomotives the bulk clear took off the setup");

            java.util.Set<String> running = new java.util.TreeSet<>();

            for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
            {
                if (point.getCurrentLocomotive() != null) running.add(point.getCurrentLocomotive().getName());
            }

            assertTrue(running.containsAll(before.values()),
                "Cancel put the locomotives back in the setup but not on the running railway: standing now "
                + running + ", before the clear " + before.values());
        }
        finally
        {
            if (answerer != null) answerer.stop();

            dispose(editor);

            session.restoreSetup(asFound);
        }
    }

    /**
     * Discard on the way out of the application puts back the locomotives a bulk clear took, as Cancel does
     * (WKV-B2).
     *
     * Closing TrainControl with the autonomy editor open asks Save / Discard / Cancel through
     * `LayoutEditor.maySettleBeforeExit`, and Discard restores the setup as the editor opened it - but the
     * running layout was rebuilt after the clear and still has no trains on those squares, and the save on the
     * way out folds the running layout back over the setup (`captureFromLayout`).  Cancel does not have this
     * problem because the editor closing rebuilds from the restored setup first.
     *
     * The exit itself ends in `System.exit`, so it is asked in its two parts: the real settle, answered
     * Discard, and then the fold the exit's save does - `captureRunningLayout` runs the same
     * `captureFromLayout` and `saveWithoutReconciling` under the same conditions, without writing the
     * operator's `UIState.data`.
     */
    @Test
    public void testDiscardOnTheWayOutPutsBackTheLocomotivesABulkClearTook() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();
        final LayoutEditor editor = opened();
        Answerer answerer = null;
        try
        {
            Map<String, String> before = placements();
            assertFalse(before.isEmpty(), "precondition: no locomotive stands anywhere on the snapshot");
            final java.lang.reflect.Method clear =
                editor.getAutonomyPanel().getClass().getDeclaredMethod("clearAllPlacements");
            clear.setAccessible(true);
            answerer = Answerer.start(String.valueOf(TrainControlUI.YES_NO_OPTS[0]));
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    clear.invoke(editor.getAutonomyPanel());
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });
            settle();
            answerer.stop();
            assertTrue(placements().isEmpty(), "precondition: the bulk clear left " + placements() + " on the setup");
            answerer = Answerer.start(org.traincontrol.util.I18n.t("layout.ui.switchDiscard"));
            final boolean[] mayExit = new boolean[1];
            SwingUtilities.invokeAndWait(() -> mayExit[0] = editor.maySettleBeforeExit());
            settle();
            answerer.stop();
            assertTrue(answerer.answered(), "precondition: the exit did not ask about the unsaved clear");
            assertTrue(mayExit[0], "precondition: Discard did not let the exit go ahead");
            final java.lang.reflect.Method fold = TrainControlUI.class.getDeclaredMethod("captureRunningLayout");
            fold.setAccessible(true);
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    fold.invoke(ui);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });
            settle();
            assertEquals(placements(), before,
                "Discard on the way out restored the setup, and the save on the way out then folded the running"
                + " layout - still without the cleared locomotives - back over it");
            Map<String, String> onDisk = new java.util.TreeMap<>();
            AutonomySession reread = fromDisk();
            for (TileKey tile : reread.getReducer().getPoints().keySet())
            {
                String name = reread.getLocomotiveNameAt(tile);
                if (name != null) onDisk.put(tile.toString(), name);
            }
            assertEquals(onDisk, before, "the file does not hold the locomotives Discard put back, so the next start"
                + " opens without them");
        }
        finally
        {
            if (answerer != null) answerer.stop();
            dispose(editor);
            session.restoreSetup(asFound);
        }
    }

    /**
     * And an arrow changed in the editor is put back by Discard on the way out - the control for the claim above.
     *
     * The validator named point settings beside placements; run on 2026-09-14 this was green BEFORE the fix, so
     * the exit's fold does not carry arrows and only placements were lost.  Kept so that the rebuild the fix
     * added cannot bring an arrow back without this saying so.
     */
    @Test
    public void testDiscardOnTheWayOutPutsTheArrowsBack() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();
        final LayoutEditor editor = opened();
        Answerer answerer = null;
        try
        {
            TileKey square = aSquareWithOneRoute();
            Map<String, String> before = directions(session);
            click(editor, square);
            assertNotEquals(directions(session), before, "precondition: clicking " + square + " changed no arrow");
            answerer = Answerer.start(org.traincontrol.util.I18n.t("layout.ui.switchDiscard"));
            final boolean[] mayExit = new boolean[1];
            SwingUtilities.invokeAndWait(() -> mayExit[0] = editor.maySettleBeforeExit());
            settle();
            answerer.stop();
            assertTrue(mayExit[0], "precondition: Discard did not let the exit go ahead");
            final java.lang.reflect.Method fold = TrainControlUI.class.getDeclaredMethod("captureRunningLayout");
            fold.setAccessible(true);
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    fold.invoke(ui);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });
            settle();
            assertEquals(directions(session).toString(), before.toString(),
                "Discard on the way out did not keep the arrow put back in the setup");
            assertEquals(directions(fromDisk()).toString(), before.toString(),
                "Discard on the way out did not keep the arrow put back in the file");
        }
        finally
        {
            if (answerer != null) answerer.stop();
            dispose(editor);
            session.restoreSetup(asFound);
        }
    }

    /**
     * And a home set in the editor is put back by Discard on the way out (WKW-C1).
     *
     * The exit's fold carries more than placements - `active`, `maxTrainLength`, `speedMultiplier`, `priority`,
     * `home` and `excludedLocs` - so before round 2 a discarded home was written back as well.  The arrow claim
     * above could not say so: a tile direction is the one setting the fold never carries.  The home is set
     * through the session and the running layout rebuilt, which is what the editor's home door does.
     */
    @Test
    public void testDiscardOnTheWayOutPutsTheHomeBack() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();
        final LayoutEditor editor = opened();
        Answerer answerer = null;
        try
        {
            TileKey square = null;
            String locomotive = null;
            for (TileKey tile : session.getReducer().getPoints().keySet())
            {
                String name = session.getLocomotiveNameAt(tile);
                if (name != null && session.getPointProperty(tile, "home") == null)
                {
                    square = tile;
                    locomotive = name;
                    break;
                }
            }
            if (square == null) fail("precondition: no square with a locomotive and no home to give it one");
            Map<String, String> before = homes(session);
            final TileKey at = square;
            final String name = locomotive;
            SwingUtilities.invokeAndWait(() ->
            {
                session.setHome(at, name);
                ui.rebuildRunningLayoutFromSetup();
            });
            settle();
            assertNotEquals(homes(session), before, "precondition: making " + at + " home to " + name + " changed nothing");
            boolean carried = false;
            for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
            {
                if (point.getHomeLoc() != null && name.equals(point.getHomeLoc().getName())) carried = true;
            }
            assertTrue(carried, "precondition: the running layout does not carry the home given to " + name
                + ", so the exit's fold has nothing to write back and this claim cannot fail");
            answerer = Answerer.start(org.traincontrol.util.I18n.t("layout.ui.switchDiscard"));
            final boolean[] mayExit = new boolean[1];
            SwingUtilities.invokeAndWait(() -> mayExit[0] = editor.maySettleBeforeExit());
            settle();
            answerer.stop();
            assertTrue(mayExit[0], "precondition: Discard did not let the exit go ahead");
            final java.lang.reflect.Method fold = TrainControlUI.class.getDeclaredMethod("captureRunningLayout");
            fold.setAccessible(true);
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    fold.invoke(ui);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });
            settle();
            assertEquals(homes(session), before,
                "Discard on the way out restored the setup, and the save on the way out then folded the running"
                + " layout - still carrying the home given to " + name + " - back over it");
            assertEquals(homes(fromDisk()), before, "the file keeps the home Discard threw away");
        }
        finally
        {
            if (answerer != null) answerer.stop();
            dispose(editor);
            session.restoreSetup(asFound);
        }
    }

    /**
     * A setup edit declined during a run is not removed by the next door that folds the running layout back
     * (WKW-B2).
     *
     * An edit made as a run starts reaches the file and not the running layout, and the message then says it
     * will be picked up at the next load.  Since ACC-B3 the exit save skips its fold while that is so.
     * `captureRunningLayout` is the same fold at every other door - opening an editor, closing one,
     * re-downloading the diagram, renaming or deleting a page - and did not ask, so opening the autonomy
     * editor after the run removed the edit before the exit could protect it.
     *
     * The race itself - an edit and a Start in the same event - is set up as the state it leaves behind: the
     * home written to the setup and the file without a rebuild, and the flag the declined rebuild sets.  The
     * fold is called directly rather than through `openLayoutEditor`, which also writes the operator's
     * last-editor preference.
     */
    @Test
    public void testADeclinedEditSurvivesTheNextCapture() throws Exception
    {
        org.json.JSONObject asFound = session.snapshotSetup();
        final java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");
        declined.setAccessible(true);
        try
        {
            TileKey square = null;
            String locomotive = null;
            for (TileKey tile : session.getReducer().getPoints().keySet())
            {
                String name = session.getLocomotiveNameAt(tile);
                if (name != null && session.getPointProperty(tile, "home") == null)
                {
                    square = tile;
                    locomotive = name;
                    break;
                }
            }
            if (square == null) fail("precondition: no square with a locomotive and no home to give it one");
            final TileKey at = square;
            final String name = locomotive;
            SwingUtilities.invokeAndWait(() ->
            {
                session.setHome(at, name);
                try
                {
                    session.saveWithoutReconciling();
                }
                catch (java.io.IOException failed)
                {
                    throw new RuntimeException(failed);
                }
            });
            settle();
            Map<String, String> edited = homes(session);
            assertEquals(edited.get(at.toString()), name, "precondition: the home did not reach the setup");
            assertEquals(homes(fromDisk()).get(at.toString()), name, "precondition: the home did not reach the file");
            // The running layout was not rebuilt after the write, so it does not carry the home - which is what
            // the red run before the fix showed: the fold removed it.
            declined.setBoolean(ui, true);
            final java.lang.reflect.Method fold = TrainControlUI.class.getDeclaredMethod("captureRunningLayout");
            fold.setAccessible(true);
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    fold.invoke(ui);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });
            settle();
            assertEquals(homes(session).get(at.toString()), name,
                "the fold removed the home that was declined during the run - the layout it folded was built"
                + " before the edit - so the promise that the edit is picked up at the next load is broken"
                + " before the exit can keep it (ACC-B3)");
            assertEquals(homes(fromDisk()).get(at.toString()), name, "the file lost the declined home");
        }
        finally
        {
            declined.setBoolean(ui, false);
            session.restoreSetup(asFound);
        }
    }

    /**
     * The declined-edit guard ends once a rebuild has carried the edit (AMS-B1).
     *
     * `setupEditDeclinedDuringRun` stops every fold of the running layout back into the setup, because the
     * running layout was built before the edit.  It was never cleared - so after the run, when the next setup
     * gesture rebuilds the railway from the setup (edit included) and puts the trains back where they stand,
     * every door went on refusing: the editor opened on pre-run placements, a home set from the menu went to a
     * pre-run square, and the exit saved no positions.  The next start put every train back where it stood before
     * the run, which is OB-183's consequence kept alive by the guard meant to protect an edit.
     *
     * Cleared only by a rebuild that REPLACED the running layout: `load` can decline (a confirmation refused, a
     * setup that will not build), and a declined load has carried nothing.
     */
    @Test
    public void testTheDeclinedEditGuardEndsWhenARebuildCarriesTheEdit() throws Exception
    {
        final java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");
        declined.setAccessible(true);
        try
        {
            declined.setBoolean(ui, true);
            final Object before = model.getAutoLayout();
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());
            settle();
            assertNotSame(model.getAutoLayout(), before,
                "precondition: the rebuild did not replace the running layout, so it carried nothing and the guard"
                + " is right to stay");
            assertFalse(declined.getBoolean(ui),
                "the running layout was rebuilt from the setup - the declined edit with it - and the guard is still"
                + " up, so no door folds the railway back for the rest of the session and the next start puts every"
                + " train back where it stood before the run (AMS-B1)");
        }
        finally
        {
            declined.setBoolean(ui, false);
        }
    }

    /** Every home the setup records, square by square, as a session holds it. */
    private static Map<String, String> homes(AutonomySession of)
    {
        Map<String, String> out = new java.util.TreeMap<>();
        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            Object home = of.getPointProperty(tile, "home");
            if (home != null) out.put(tile.toString(), String.valueOf(home));
        }
        return out;
    }

    /** Every placement the setup records, square by square. */
    private static Map<String, String> placements()
    {
        Map<String, String> out = new java.util.TreeMap<>();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            String name = session.getLocomotiveNameAt(tile);

            if (name != null) out.put(tile.toString(), name);
        }

        return out;
    }

    // ---------------------------------------------------------------- the fixture

    /** A fresh autonomy editor on the frozen railway's first page. */
    private static LayoutEditor opened() throws Exception
    {
        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);

            built[0].render();

            built[0].setAutonomyMode(session);
        });

        settle();

        assertTrue(built[0].isAutonomyMode(), "the editor did not open in autonomy mode");

        return built[0];
    }

    /** A plain track square on the page: one route, able to carry a direction, not a link. */
    private static TileKey aSquareWithOneRoute()
    {
        for (TileKey tile : session.getGraph().getTiles().keySet())
        {
            if (!PAGE.equals(tile.getPage())) continue;

            if (session.getRoutes(tile).size() != 1 || !session.canCarryDirection(tile)) continue;

            if (org.traincontrol.automationui.TilePorts.hasPortal(session.getGraph().getTiles().get(tile).getType()))
            {
                continue;
            }

            return tile;
        }

        fail("no plain track square on " + PAGE + " to change an arrow on");

        return null;
    }

    /** Every recorded direction on the page, by square and route, as a store holds it. */
    private static Map<String, String> directions(AutonomySession of)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (TileKey tile : session.getGraph().getTiles().keySet())
        {
            if (!PAGE.equals(tile.getPage())) continue;

            for (RouteId route : session.getRoutes(tile).keySet())
            {
                Object direction = of.getStore().getTileDirection(tile, route);

                if (direction != null) out.put(tile + "/" + route, String.valueOf(direction));
            }
        }

        return out;
    }

    /** The setup as the file holds it, read by a session of its own. */
    private static AutonomySession fromDisk() throws Exception
    {
        AutonomySession reread = new AutonomySession(sandbox.getFolder());

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String name : model.getLayoutList()) pages.add(model.getLayout(name));

        reread.open(pages);

        return reread;
    }

    /** A click on a square with no tool armed, through the panel's own door. */
    private static void click(final LayoutEditor editor, final TileKey square) throws Exception
    {
        final java.lang.reflect.Method cycle =
            editor.getAutonomyPanel().getClass().getDeclaredMethod("cycle", TileKey.class);

        cycle.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                cycle.invoke(editor.getAutonomyPanel(), square);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        // The rebuild of the running layout is posted, and so is anything it posts
        settle();
    }

    /** The Cancel button's action. */
    private static void cancel(final LayoutEditor editor) throws Exception
    {
        press(editor, "confirmExit");
    }

    /** The Save button's action. */
    private static void save(final LayoutEditor editor) throws Exception
    {
        final java.lang.reflect.Method pressed = LayoutEditor.class.getDeclaredMethod(
            "saveButtonActionPerformed", java.awt.event.ActionEvent.class);

        pressed.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                pressed.invoke(editor, new Object[] { null });
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        settle();
    }

    private static void press(final LayoutEditor editor, String method) throws Exception
    {
        final java.lang.reflect.Method pressed = LayoutEditor.class.getDeclaredMethod(method);

        pressed.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                pressed.invoke(editor);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        settle();
    }

    /** @param editor a window that may already have closed itself */
    private static void dispose(final LayoutEditor editor) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> editor.dispose());

        settle();
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    /**
     * Presses a button in whatever modal dialog comes up, from outside the event thread.
     *
     * A dialog put up inside an `invokeAndWait` runs its own event loop, so the button can be pressed
     * through `invokeLater` while the caller is still waiting.  Bounded: it gives up after a few seconds.
     */
    private static final class Answerer implements Runnable
    {
        private final String label;
        private volatile boolean running = true;
        private volatile boolean answered = false;
        private final Thread thread;

        private Answerer(String label)
        {
            this.label = label;
            this.thread = new Thread(this, "MT-406 dialog answerer");
            this.thread.setDaemon(true);
        }

        /** @param label the button to press, or null for the first one */
        static Answerer start(String label)
        {
            Answerer out = new Answerer(label);

            out.thread.start();

            return out;
        }

        boolean answered()
        {
            return answered;
        }

        void stop() throws InterruptedException
        {
            running = false;

            thread.join(2000);
        }

        @Override
        public void run()
        {
            long until = System.currentTimeMillis() + 15000;

            while (running && System.currentTimeMillis() < until)
            {
                for (Window window : Window.getWindows())
                {
                    if (!(window instanceof JDialog) || !window.isShowing()) continue;

                    final AbstractButton button = find(window);

                    if (button == null) continue;

                    answered = true;

                    SwingUtilities.invokeLater(() -> button.doClick());
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException stopped)
                {
                    return;
                }
            }
        }

        private AbstractButton find(java.awt.Container in)
        {
            for (java.awt.Component child : in.getComponents())
            {
                if (child instanceof AbstractButton
                    && (label == null || label.equals(((AbstractButton) child).getText())))
                {
                    return (AbstractButton) child;
                }

                if (child instanceof java.awt.Container)
                {
                    AbstractButton found = find((java.awt.Container) child);

                    if (found != null) return found;
                }
            }

            return null;
        }
    }
}
