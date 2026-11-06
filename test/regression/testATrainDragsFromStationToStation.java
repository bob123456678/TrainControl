package regression;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Mouse;

/**
 * FR-160: a train drawn on a station in the autonomy editor can be dragged to another station.  It lands through the
 * door the editor's Add to Autonomy picker places through (`putLocomotiveOn`), so the setup it leaves is the picker's.
 * Every claim drives real mouse events at the editor's squares on the frozen railway and puts the setup back.
 *
 * @author Adam
 */
public class testATrainDragsFromStationToStation
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutDiagram page;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        // The locomotive list arrives with the first tiles; a configuration loaded before it forgets its trains.
        final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        settle();

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration, so there is no editor to drag in");
        }

        page = model.getLayout(PAGE);

        // A configuration loaded, as on Adam's railway: the trains are drawn from the running layout it builds.
        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            settle();
        }

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: no configuration is loaded");

        editor = openEditor();
    }

    /**
     * An autonomy editor on the page, shown: the registry hands trains only to squares in a visible window.  The
     * running layout is rebuilt so the squares are given what it holds.
     */
    private static LayoutEditor openEditor() throws Exception
    {
        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);

            built[0].render();

            built[0].setAutonomyMode(session);

            built[0].setVisible(true);
        });

        settle();

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        settle();

        return built[0];
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (editor != null)
            {
                final LayoutEditor closing = editor;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

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
     * A train pressed, carried across and let go on another station lands there and leaves its old square; the square
     * it left and the one it reached say so in the setup.
     *
     * MUTATION: dropping the `dropTrain` call from `LayoutEditorTrainDrag.release` leaves the train where it was.
     */
    @Test(timeOut = 120000)
    public void testADraggedTrainLandsOnTheStation() throws Exception
    {
        Fixture f = fixture(false);

        String train = session.getLocomotiveNameAt(f.from);

        org.json.JSONObject asFound = session.snapshotSetup();

        Map<String, String> expected = placements();

        try
        {
            Mouse.drag(f.fromLabel, f.toLabel);
            settle();

            assertEquals(session.getLocomotiveNameAt(f.to), train,
                "the train dragged from " + f.from + " is not on the station it was let go on, " + f.to);

            assertNull(session.getLocomotiveNameAt(f.from), "the train is on its old square as well as the new one");

            expected.remove(f.from.toString());
            expected.put(f.to.toString(), train);

            assertEquals(placements(), expected, "another train moved with it");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * What the drag leaves in the setup is what the picker's door leaves for the same move, facing, road and the rest,
     * onto an empty station and onto one that holds a train (which the door replaces).
     *
     * MUTATION: having `dropTrain` write `session.placeLocomotive` and `placementChanged` itself, without
     * `setFacing`, leaves the facing the picker records out.
     */
    @Test(timeOut = 180000)
    public void testItLeavesTheSetupThePickersDoorLeaves() throws Exception
    {
        for (boolean occupied : new boolean[] { false, true })
        {
            Fixture f = fixture(occupied);

            String train = session.getLocomotiveNameAt(f.from);

            org.json.JSONObject asFound = session.snapshotSetup();

            try
            {
                Mouse.drag(f.fromLabel, f.toLabel);
                settle();

                org.json.JSONObject dragged = session.snapshotSetup();

                putBack(asFound);

                placeByThePicker(f, train);

                settle();

                assertNull(session.getLocomotiveNameAt(f.from), "precondition: the picker did not move the train");

                assertTrue(dragged.similar(session.snapshotSetup()),
                    "dragging " + train + " from " + f.from + " to " + (occupied ? "an occupied" : "an empty")
                    + " station leaves a different setup from placing it there by the picker:\n" + dragged
                    + "\nagainst\n" + session.snapshotSetup());
            }
            finally
            {
                putBack(asFound);
            }
        }
    }

    /**
     * While carrying, the square under the pointer wears green where the drop would take and red where it would
     * refuse, asked of the rule the drop uses.
     *
     * MUTATION: marking every square allowed (`allowed = true` in `LayoutEditorTrainDrag.move`) turns the plain track
     * square green.
     */
    @Test(timeOut = 120000)
    public void testTheTargetSquareReadsBeforeRelease() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

            carryTo(f.fromLabel, f.toLabel);

            assertEquals(markOf(f.toLabel), new Color(0, 150, 0), "a station that takes the train is not marked green (canDropTrain "
                + editor.getAutonomyPanel().canDropTrain(f.from, f.to) + ", run holds the setup "
                + editor.getAutonomyPanel().setupIsHeldByARun() + ")");

            assertNotNull(ghost(), "nothing follows the pointer while the train is carried");

            carryTo(f.fromLabel, f.trackLabel);

            assertEquals(markOf(f.trackLabel), Color.RED, "a square that is no station is not marked as refusing");

            carryTo(f.fromLabel, f.toLabel);

            Mouse.release(f.fromLabel, MouseEvent.BUTTON1);
            settle();

            assertNull(ghost(), "the carried name stayed on screen after the release");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * Let go on plain track, back on the square it came from, or outside the diagram: nothing changes and no dialog
     * says anything.
     *
     * MUTATION: dropping `isStation(to)` from `canDropTrain` puts the train on the plain track square.
     */
    @Test(timeOut = 120000)
    public void testARefusedReleaseChangesAndSaysNothing() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        try (Answering answering = Answering.start(message -> null))
        {
            Mouse.drag(f.fromLabel, f.trackLabel);
            settle();

            assertUnchanged(asFound, "a release on plain track moved the train");

            Mouse.drag(f.fromLabel, f.fromLabel);
            settle();

            assertUnchanged(asFound, "a release on its own square changed the setup");

            Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

            Point outside = new Point(-50, -50);

            post(f.fromLabel, MouseEvent.MOUSE_DRAGGED, outside, InputEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON);
            post(f.fromLabel, MouseEvent.MOUSE_RELEASED, outside, 0, 1, MouseEvent.BUTTON1);
            settle();

            assertUnchanged(asFound, "a release outside the diagram changed the setup");

            assertEquals(answering.asked().size(), 0, "a refused release said something: " + answering.asked());

            assertNull(ghost(), "the carried name stayed on screen");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * Escape while carrying puts the train back: the release that follows moves nothing, and the editor stays open.
     *
     * MUTATION: having `LayoutEditorTrainDrag.cancel` answer false leaves the drag standing, so the release lands.
     */
    @Test(timeOut = 120000)
    public void testEscapeDropsTheTrain() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

            carryTo(f.fromLabel, f.toLabel);

            support.Keys.press(editor, KeyEvent.VK_ESCAPE);
            settle();

            assertNull(ghost(), "Escape left the carried name on screen");

            Point there = pointOn(f.toLabel, f.fromLabel);

            post(f.fromLabel, MouseEvent.MOUSE_RELEASED, there, 0, 1, MouseEvent.BUTTON1);
            settle();

            assertUnchanged(asFound, "the train moved after Escape");

            assertTrue(editor.isDisplayable(), "Escape while carrying closed the editor");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * A plain click on the train, and a press that only jitters, are not drags: no name is lifted and nothing moves,
     * and a drag still works afterwards.
     *
     * MUTATION: letting a drag begin at once (`pressedAt.distance(at) < 0`) lifts the name on a two-pixel jitter.
     */
    @Test(timeOut = 120000)
    public void testAClickOrAJitterIsNotADrag() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        Map<String, String> expectedPlacements = placements();

        try
        {
            Mouse.click(f.fromLabel);
            settle();

            // A click on a station does what it always did to the square (cycles its arrow); only the train must stay.
            asFound = session.snapshotSetup();

            assertEquals(placements(), expectedPlacements, "a click on the train moved a train");

            Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

            Point at = center(f.fromLabel);

            post(f.fromLabel, MouseEvent.MOUSE_DRAGGED, new Point(at.x + 2, at.y + 1),
                InputEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON);

            assertNull(ghost(), "a two-pixel jitter lifted the train");

            post(f.fromLabel, MouseEvent.MOUSE_RELEASED, new Point(at.x + 2, at.y + 1), 0, 1, MouseEvent.BUTTON1);
            settle();

            assertUnchanged(asFound, "a jitter changed the setup");

            Mouse.drag(f.fromLabel, f.toLabel);
            settle();

            assertNotNull(session.getLocomotiveNameAt(f.to), "after a click and a jitter, a real drag no longer works");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * While a run holds the setup the train is not offered: pressed and carried, nothing moves.
     *
     * MUTATION: dropping the `setupIsHeldByARun` tests from `AutonomyEditorPanel.canDropTrain` and
     * `LayoutEditorTrainDrag.press` lets the train go.
     */
    @Test(timeOut = 120000)
    public void testNothingDragsWhileARunHoldsTheSetup() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        final java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        staging.setAccessible(true);

        try
        {
            staging.setBoolean(ui, true);

            assertTrue(ui.aRunHoldsTheSetup(), "precondition: the staging flag does not hold the setup");

            Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

            carryTo(f.fromLabel, f.toLabel);

            Mouse.release(f.fromLabel, MouseEvent.BUTTON1);
            settle();

            assertUnchanged(asFound, "a train moved while a run holds the setup");
        }
        finally
        {
            staging.setBoolean(ui, false);

            putBack(asFound);
        }
    }

    /**
     * Cancel does with a dragged train what it does with one placed by the picker (trains are working state, which
     * Cancel leaves where they were put), and a diagram scrolled away from its corner still lands the train.
     *
     * MUTATION: none of its own: the drag rides the door the picker uses.  Taking the scroll offset out of the square
     * lookup (`LayoutGrid.tileUnder`) fails the scrolled landing.
     */
    @Test(timeOut = 240000)
    public void testCancelTreatsADraggedTrainAsAPlacedOne() throws Exception
    {
        fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            org.json.JSONObject byDrag = movedThenCanceled(asFound, true);
            org.json.JSONObject byPicker = movedThenCanceled(asFound, false);

            if (!byDrag.similar(byPicker))
            {
                java.util.List<String> differ = new java.util.ArrayList<>();

                collectDifferences("", byPicker, byDrag, differ);

                fail("Cancel leaves a dragged train differently from a placed one: " + differ);
            }
        }
        finally
        {
            putBack(asFound);
        }
    }

    /** Opens a fresh editor on the setup as found, moves a train by drag or by the picker, and Cancels. */
    private static org.json.JSONObject movedThenCanceled(org.json.JSONObject asFound, boolean byDrag) throws Exception
    {
        putBack(asFound);

        SwingUtilities.invokeAndWait(() -> editor.dispose());

        editor = openEditor();

        Fixture f = fixture(false);

        String train = session.getLocomotiveNameAt(f.from);

        if (byDrag)
        {
            scrollTheDiagram();

            Mouse.drag(f.fromLabel, f.toLabel);
            settle();
        }
        else
        {
            placeByThePicker(f, train);
        }

        assertEquals(session.getLocomotiveNameAt(f.to), train, "precondition: the train did not reach " + f.to
            + (byDrag ? " (diagram scrolled)" : ""));

        try (Answering answering = Answering.start(
            message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0]))))
        {
            final java.lang.reflect.Method cancel = LayoutEditor.class.getDeclaredMethod("confirmExit");

            cancel.setAccessible(true);

            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    cancel.invoke(editor);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });

            for (int wait = 0; wait < 100 && editor.isDisplayable(); wait++)
            {
                Thread.sleep(50);
            }
        }

        settle();

        assertFalse(editor.isDisplayable(), "precondition: Cancel did not close the editor");

        return session.snapshotSetup();
    }

    /** Moves the train as the menu's Add to Autonomy does: the picker asked which train, and answered. */
    private static void placeByThePicker(Fixture f, String train) throws Exception
    {
        try (Answering answering = Answering.start(
            message -> Answering.type(I18n.f("autosetup.ui.locomotiveElsewhere", train))))
        {
            final java.lang.reflect.Method pick = editor.getAutonomyPanel().getClass()
                .getDeclaredMethod("placeLocomotive", TileKey.class);

            pick.setAccessible(true);

            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    pick.invoke(editor.getAutonomyPanel(), f.to);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });

            assertTrue(answering.awaitAsked(1, 20000), "the picker never asked which train to place");
        }

        settle();
    }

    // ------------------------------------------------------------------------------------------ the fixture

    /** A train's square and a station to take it, with the labels the mouse is aimed at. */
    private static final class Fixture
    {
        TileKey from;
        TileKey to;
        TileKey track;
        LayoutLabel fromLabel;
        LayoutLabel toLabel;
        LayoutLabel trackLabel;
    }

    /**
     * Finds a square whose train is drawn, a station to move it to (holding a train when `occupied`), and a plain
     * track square; waits for the drawing.  Re-opens the editor if an earlier claim closed it.
     */
    private static Fixture fixture(boolean occupied) throws Exception
    {
        if (!editor.isDisplayable())
        {
            editor = openEditor();
        }

        putTwoTrainsOnStations();

        showTheTrains();

        long giveUp = System.currentTimeMillis() + 20000;

        while (true)
        {
            final Fixture[] found = new Fixture[1];

            SwingUtilities.invokeAndWait(() -> found[0] = look(occupied));

            if (found[0] != null) return found[0];

            if (System.currentTimeMillis() > giveUp)
            {
                final String[] why = new String[1];

                SwingUtilities.invokeAndWait(() -> why[0] = diagnose());

                fail("precondition: no drawn train, " + (occupied ? "occupied" : "empty")
                    + " station and plain track square on " + PAGE + ": " + why[0]);
            }

            Thread.sleep(100);
        }
    }

    /**
     * The frozen railway has no train standing on this page, so two are put on stations through the editor's own
     * placement door, once; the claims then drag them.
     */
    private static void putTwoTrainsOnStations() throws Exception
    {
        final java.util.List<TileKey> stations = new java.util.ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                LayoutGrid grid = gridOf();

                for (TileKey tile : session.getReducer().getPoints().keySet())
                {
                    if (!PAGE.equals(tile.getPage()) || !session.getStore().isStation(tile)) continue;

                    LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

                    if (label != null && !label.isSpacer() && label.getParent() != null
                        && session.getLocomotiveNameAt(tile) == null)
                    {
                        stations.add(tile);
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        int onPage = 0;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getLocomotiveNameAt(tile) != null) onPage++;
        }

        if (onPage >= 2) return;

        final Object panel = editor.getAutonomyPanel();

        final java.lang.reflect.Method door = panel.getClass().getDeclaredMethod("putLocomotiveOn", TileKey.class,
            String.class);

        door.setAccessible(true);

        // The simulated station reads its locomotive list a moment after the window attaches to it.
        for (int wait = 0; wait < 150 && model.getLocList().isEmpty(); wait++) Thread.sleep(100);

        final java.util.List<String> roster = model.getLocList();

        java.util.List<String> free = new java.util.ArrayList<>(roster);

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            String standing = session.getLocomotiveNameAt(tile);

            if (standing != null) free.remove(standing);
        }

        assertTrue(free.size() >= 2 - onPage && stations.size() >= 2,
            "precondition: the railway lacks two free locomotives (" + free.size() + " of " + roster.size()
            + ") or two empty stations (" + stations.size() + ") on " + PAGE);

        for (int i = 0; onPage < 2; i++, onPage++)
        {
            final TileKey at = stations.get(i);
            final String name = free.get(i);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    door.invoke(panel, at, name);
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });

            settle();
        }
    }

    /**
     * Show Trains is a remembered view setting, which may be off on the machine running this; the box is ticked
     * without the click, so the setting itself is left as it was.
     */
    private static void showTheTrains() throws Exception
    {
        final Object panel = editor.getAutonomyPanel();

        final java.lang.reflect.Field box = panel.getClass().getDeclaredField("showTrains");

        box.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ((javax.swing.JCheckBox) box.get(panel)).setSelected(true);

                editor.getAutonomyPanel().refresh();
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        settle();
    }

    /** What the page holds, for a fixture that found nothing. */
    private static String diagnose()
    {
        StringBuilder out = new StringBuilder();

        try
        {
            LayoutGrid grid = gridOf();

            int placed = 0, labelled = 0, drawn = 0, stations = 0, emptyStations = 0, tracks = 0;

            for (TileKey tile : session.getReducer().getPoints().keySet())
            {
                if (!PAGE.equals(tile.getPage())) continue;

                LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

                boolean station = session.getStore().isStation(tile);
                boolean train = session.getLocomotiveNameAt(tile) != null;

                if (station) stations++;
                if (station && !train) emptyStations++;

                if (train) placed++;

                if (label != null && !label.isSpacer())
                {
                    labelled++;

                    if (train && label.trainDrawnAt(center(label))) drawn++;
                }
            }

            for (int x = 0; grid.getValueAt(x, 0) != null; x++)
            {
                for (int y = 0; grid.getValueAt(x, y) != null; y++)
                {
                    LayoutLabel label = grid.getValueAt(x, y);

                    if (!label.isSpacer() && label.getComponent() != null) tracks++;
                }
            }

            out.append("showing trains ").append(editor.getAutonomyPanel().isShowingTrains()).append(", points ").append(session.getReducer().getPoints().size()).append(", placed ").append(placed)
                .append(", labelled ").append(labelled).append(", train drawn at the middle ").append(drawn)
                .append(", stations ").append(stations).append(" (empty ").append(emptyStations)
                .append("), drawn squares ").append(tracks);
        }
        catch (Exception e)
        {
            out.append(e);
        }

        return out.toString();
    }

    private static Fixture look(boolean occupied)
    {
        try
        {
            LayoutGrid grid = gridOf();

            Fixture f = new Fixture();

            for (TileKey tile : session.getReducer().getPoints().keySet())
            {
                if (!PAGE.equals(tile.getPage())) continue;

                LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

                if (label == null || label.isSpacer() || label.getParent() == null) continue;

                String standing = session.getLocomotiveNameAt(tile);

                if (f.from == null && standing != null && label.trainDrawnAt(center(label)))
                {
                    f.from = tile;
                    f.fromLabel = label;
                }
            }

            if (f.from == null) return null;

            for (TileKey tile : session.getReducer().getPoints().keySet())
            {
                if (!PAGE.equals(tile.getPage()) || tile.equals(f.from) || f.to != null) continue;

                LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

                if (label == null || label.isSpacer() || label.getParent() == null) continue;

                boolean holds = session.getLocomotiveNameAt(tile) != null;

                if (session.getStore().isStation(tile) && holds == occupied)
                {
                    f.to = tile;
                    f.toLabel = label;
                }
            }

            for (int x = 0; f.track == null && grid.getValueAt(x, 0) != null; x++)
            {
                for (int y = 0; grid.getValueAt(x, y) != null; y++)
                {
                    LayoutLabel label = grid.getValueAt(x, y);

                    TileKey tile = new TileKey(PAGE, x, y);

                    if (label.isSpacer() || label.getComponent() == null || session.getStore().isStation(tile)
                        || session.getLocomotiveNameAt(tile) != null)
                    {
                        continue;
                    }

                    f.track = tile;
                    f.trackLabel = label;

                    break;
                }
            }

            return f.to == null || f.track == null ? null : f;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static LayoutGrid gridOf() throws Exception
    {
        java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("grid");

        field.setAccessible(true);

        return (LayoutGrid) field.get(editor);
    }

    /** The middle of a square, where Mouse presses. */
    private static Point center(java.awt.Component on)
    {
        return new Point(Math.max(1, on.getWidth() / 2), Math.max(1, on.getHeight() / 2));
    }

    /** The middle of `target` as `from` sees it. */
    private static Point pointOn(LayoutLabel target, LayoutLabel from) throws Exception
    {
        final Point[] out = new Point[1];

        SwingUtilities.invokeAndWait(() ->
            out[0] = SwingUtilities.convertPoint(target, center(target), from));

        return out[0];
    }

    /** Moves the held pointer to the middle of another square, reported to the square it was pressed on. */
    private static void carryTo(LayoutLabel pressed, LayoutLabel over) throws Exception
    {
        post(pressed, MouseEvent.MOUSE_DRAGGED, pointOn(over, pressed), InputEvent.BUTTON1_DOWN_MASK, 0,
            MouseEvent.NOBUTTON);
    }

    private static void post(final java.awt.Component on, final int id, final Point at, final int modifiers,
        final int count, final int button) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            Point screen = new Point(at);

            if (on.isShowing()) SwingUtilities.convertPointToScreen(screen, on);

            on.dispatchEvent(new MouseEvent(on, id, System.currentTimeMillis(), modifiers, at.x, at.y, screen.x,
                screen.y, count, false, button));
        });
    }

    /** The picture following the pointer, or null. */
    private static Object ghost() throws Exception
    {
        java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("captionGhost");

        field.setAccessible(true);

        return field.get(editor);
    }

    /** The color of the outline a square wears, or null. */
    private static Color markOf(LayoutLabel label) throws Exception
    {
        final Color[] out = new Color[1];

        SwingUtilities.invokeAndWait(() -> out[0] = colorIn(label.getBorder()));

        return out[0];
    }

    private static Color colorIn(Border border)
    {
        if (border instanceof javax.swing.border.LineBorder) return ((javax.swing.border.LineBorder) border).getLineColor();

        if (border instanceof javax.swing.border.CompoundBorder)
        {
            javax.swing.border.CompoundBorder both = (javax.swing.border.CompoundBorder) border;

            Color outer = colorIn(both.getOutsideBorder());

            return outer != null ? outer : colorIn(both.getInsideBorder());
        }

        return null;
    }

    /** Scrolls the diagram away from its corner, shrinking the window first if everything fits. */
    private static void scrollTheDiagram() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("jScrollPane1");

                field.setAccessible(true);

                javax.swing.JScrollPane pane = (javax.swing.JScrollPane) field.get(editor);

                javax.swing.JViewport view = pane.getViewport();

                view.setViewPosition(new Point(
                    Math.max(0, view.getViewSize().width - view.getExtentSize().width),
                    Math.max(0, view.getViewSize().height - view.getExtentSize().height)));
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        settle();
    }

    /** Every placement the setup records, by square. */
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

    /** The setup as found, and the running layout built from it again. */
    private static void putBack(org.json.JSONObject asFound) throws Exception
    {
        session.restoreSetup(asFound);

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        settle();
    }

    /** Fails, naming the paths that differ, when the setup is not as it was found. */
    private static void assertUnchanged(org.json.JSONObject asFound, String message)
    {
        org.json.JSONObject now = session.snapshotSetup();

        if (asFound.similar(now)) return;

        java.util.List<String> differ = new java.util.ArrayList<>();

        collectDifferences("", asFound, now, differ);

        fail(message + ": " + differ.subList(0, Math.min(8, differ.size())) + " of " + differ.size());
    }

    private static void collectDifferences(String path, Object was, Object now, java.util.List<String> into)
    {
        if (was instanceof org.json.JSONObject && now instanceof org.json.JSONObject)
        {
            java.util.Set<String> keys = new java.util.TreeSet<>(((org.json.JSONObject) was).keySet());

            keys.addAll(((org.json.JSONObject) now).keySet());

            for (String key : keys)
            {
                collectDifferences(path + "/" + key, ((org.json.JSONObject) was).opt(key),
                    ((org.json.JSONObject) now).opt(key), into);
            }

            return;
        }

        if (was == null ? now != null : !(now != null && was.toString().equals(now.toString())))
        {
            into.add(path + " " + was + " -> " + now);
        }
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
