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
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Mouse;
import support.Reflect;

/**
 * FR-160: a train drawn on a station in the autonomy editor can be dragged to another station.  It lands through the
 * window's paste of a train, so the setup it leaves is a paste's.
 * Every claim drives real mouse events at the editor's squares on the frozen railway and puts the setup back.
 *
 * @author Adam
 */
public class testATrainDragsFromStationToStation
{
    private static final String PAGE = "1 - Main";

    private static LayoutEditor editor;
    private static LayoutDiagram page;
    private static AutonomySession session;
    private static TrainControlUI ui;
    private static MarklinControlStation model;
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        needADisplay();

        openTheRailway();

        attachTheWindow();

        waitForTheTiles();

        session = ui.getAutonomySession();

        assertNotNull(session.getStore().getActiveConfiguration(), "no autonomy configuration to drag in");

        page = model.getLayout(PAGE);

        loadTheConfiguration();

        editor = openEditor();
    }

    private static void needADisplay()
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the autonomy editor needs a display");
    }

    private static void openTheRailway() throws Exception
    {
        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            model = support.TestStation.simulatedInDebug();
        }
        catch (Exception | Error failed)
        {
            closeTheRailway();

            throw failed;
        }
    }

    private static void attachTheWindow() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    /** The locomotive list arrives with the first tiles; a configuration loaded before it forgets its trains. */
    private static void waitForTheTiles() throws Exception
    {
        final java.util.concurrent.CountDownLatch arrived = new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(arrived::countDown);

        arrived.await(30, java.util.concurrent.TimeUnit.SECONDS);

        Components.drain(8);
    }

    /** Loaded as on Adam's railway: the trains are drawn from the running layout it builds. */
    private static void loadTheConfiguration() throws Exception
    {
        final String active = session.getStore().getActiveConfiguration();

        if (ui.getActiveDiagramConfiguration() == null)
        {
            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            Components.drain(8);
        }

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: no configuration is loaded");
    }

    /**
     * An autonomy editor on the page, shown: the registry hands trains only to squares in a visible window.  The
     * running layout is rebuilt so the squares are given what it holds.
     */
    private static LayoutEditor openEditor() throws Exception
    {
        final java.util.concurrent.atomic.AtomicReference<LayoutEditor> made = new java.util.concurrent.atomic.AtomicReference<>();

        onTheEventThread(() -> made.set(shown(new LayoutEditor(page, 30, ui, 0))));

        Components.drain(8);

        onTheEventThread(ui::rebuildRunningLayoutFromSetup);

        Components.drain(8);

        return made.get();
    }

    private static LayoutEditor shown(LayoutEditor window)
    {
        window.render();
        window.setAutonomyMode(session);
        window.setVisible(true);

        return window;
    }

    /** A step that may throw. */
    private interface Step
    {
        void run() throws Exception;
    }

    private static void onTheEventThread(final Step step) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> attempt(step));
    }

    private static void later(final Step step)
    {
        SwingUtilities.invokeLater(() -> attempt(step));
    }

    private static void attempt(Step step)
    {
        try
        {
            step.run();
        }
        catch (Exception failed)
        {
            throw new RuntimeException(failed);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            closeTheWindows();
        }
        finally
        {
            closeTheRailway();
        }
    }

    private static void closeTheWindows() throws Exception
    {
        for (final java.awt.Window open : new java.awt.Window[] {editor, ui})
        {
            if (open != null) SwingUtilities.invokeAndWait(open::dispose);
        }
    }

    private static void closeTheRailway()
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
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
            Components.drain(8);

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
     * A dragged train ends as a real Control+X, Control+V of it ends (FR-160): the facing, the tail's side and road,
     * the copy it stands on and every other train, on several trains and squares, onto squares a paste asks nothing at.
     *
     * MUTATION: having `AutonomyEditorPanel.dropTrain` place through `placeLocomotive` (the picker's door) leaves the
     * facing and the tail unrecorded where the paste records them.
     */
    @Test(timeOut = 600000)
    public void testADragLeavesWhatAPasteLeaves() throws Exception
    {
        fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        java.util.List<TileKey[]> cases = casesToCompare();

        SwingUtilities.invokeAndWait(() -> editor.dispose());

        java.util.Map<String, String> pasted = new java.util.TreeMap<>();
        java.util.Set<String> asked = new java.util.HashSet<>();
        java.util.List<String> left = new java.util.ArrayList<>();
        java.util.List<String> unstable = new java.util.ArrayList<>();

        TailCrossedPrompt.answerForTests(TailCrossedPrompt.DISMISSED);

        try (Answering answering = Answering.start(message -> null))
        {
            for (TileKey[] each : cases)
            {
                putBack(asFound);

                int before = answering.asked().size();

                String standing = session.getLocomotiveNameAt(each[0]);

                realPaste(each[0], each[1]);

                String first = outcome(each[1]);

                // Twice: a case the paste itself does not repeat is no reference to hold the drag to
                putBack(asFound);

                realPaste(each[0], each[1]);

                pasted.put(key(each), outcome(each[1]));

                if (!first.equals(pasted.get(key(each)))) unstable.add(key(each) + " " + first + " / " + pasted.get(key(each)));

                // A paste that asked, or whose cut found no train to take (it then puts down the selected one), is
                // not a move to compare
                boolean wasAsked = answering.asked().size() > before;

                if (wasAsked || !standing.equals(session.getLocomotiveNameAt(each[1])))
                {
                    asked.add(key(each));

                    left.add(key(each) + (wasAsked ? " asked " + answering.asked().subList(before, answering.asked().size())
                        : " put down " + session.getLocomotiveNameAt(each[1]) + " not " + standing));
                }
            }
        }
        finally
        {
            TailCrossedPrompt.answerForTests(null);

            putBack(asFound);
        }

        editor = openEditor();

        // A new editor reads the remembered Show Trains, and a train not drawn cannot be pressed to drag
        showTheTrains();

        int compared = 0;
        int tailed = 0;

        try
        {
            for (TileKey[] each : cases)
            {
                if (asked.contains(key(each)) || unstable.toString().contains(key(each))) continue;

                putBack(asFound);

                dragTrain(each[0], each[1]);

                assertEquals(outcome(each[1]), pasted.get(key(each)),
                    "dragging " + key(each) + " leaves what a real paste does not");

                compared++;

                if (session.getArrivedFrom(each[1]) != null) tailed++;
            }
        }
        finally
        {
            putBack(asFound);
        }

        assertTrue(compared >= 5, "precondition: only " + compared + " of " + cases.size()
            + " moves are ones a paste asks nothing at and repeats, so too few were compared: " + left + unstable);

        assertTrue(tailed > 0, "precondition: no paste recorded the side the tail came in by, so the tail is not compared");
    }

    /**
     * The train a drag displaces takes its own settings with it, and the dragged one keeps its own (FR-160): the
     * placement door starts a different train from nothing, and the same train from what it had.
     *
     * MUTATION: dropping the `name.equals(nameOfPlacedLocomotive(existing))` test in `AutonomySession.placeLocomotive`
     * gives the dragged train the displaced one's length, reversibility and speed.
     */
    @Test(timeOut = 180000)
    public void testADisplacedTrainsSettingsDoNotPassOn() throws Exception
    {
        Fixture f = fixture(true);

        String dragged = session.getLocomotiveNameAt(f.from);
        String displaced = session.getLocomotiveNameAt(f.to);

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            session.setPointProperty(f.from, "loc", settings(dragged, 7, true, 61));
            session.setPointProperty(f.to, "loc", settings(displaced, 3, false, 33));

            onTheEventThread(ui::rebuildRunningLayoutFromSetup);
            Components.drain(8);

            Mouse.drag(f.fromLabel, f.toLabel);
            Components.drain(8);

            org.json.JSONObject now = (org.json.JSONObject) session.getPointProperty(f.to, "loc");

            assertEquals(now.optString("name"), dragged, "precondition: the train did not land");
            assertEquals(now.optInt("trainLength", -1), 7, "the dragged train took the displaced train's length");
            assertTrue(now.optBoolean("reversible"), "the dragged train took the displaced train's reversibility");
            assertEquals(now.optInt("speed", -1), 61, "the dragged train took the displaced train's speed");

            // The same train put down again keeps what it had
            session.setPointProperty(f.to, "loc", settings(dragged, 9, true, 70).put("odd", 1));
            session.placeLocomotive(f.to, dragged);

            org.json.JSONObject kept = (org.json.JSONObject) session.getPointProperty(f.to, "loc");

            assertEquals(kept.optInt("trainLength", -1), 9, "placing the same train again dropped its settings");
            assertEquals(kept.optInt("odd", -1), 1, "placing the same train again dropped a setting it had");
        }
        finally
        {
            putBack(asFound);
        }
    }

    private static org.json.JSONObject settings(String name, int length, boolean reversible, int speed)
    {
        return new org.json.JSONObject().put("name", name).put("trainLength", length).put("reversible", reversible)
            .put("speed", speed);
    }

    /**
     * A train pressed while the picture is between a wipe and the next one is still the train: the press does not fall
     * to a station caption (the intermittent red of `testARefusedReleaseChangesAndSaysNothing`).
     *
     * MUTATION: having `LayoutEditorTrainDrag.press` ask `trainDrawnUnder` instead of `trainStandingUnder` lets the
     * press go to the caption drag, so the train stays and a caption may move.
     */
    @Test(timeOut = 120000)
    public void testAPressOnATrainWhoseOverlayIsWipedStillDragsIt() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        String train = session.getLocomotiveNameAt(f.from);

        try
        {
            onTheEventThread(() -> f.fromLabel.setAutonomyOverlay(null));

            assertTrue(onTheEventThreadValue(f.fromLabel::trainPictureIsMissing), "precondition: the picture is not missing");

            Mouse.drag(f.fromLabel, f.toLabel);
            Components.drain(8);

            assertEquals(session.getLocomotiveNameAt(f.to), train, "a press on a train between pictures did not drag it");

            assertEquals(asFound.getJSONObject("shared").optJSONObject("captions").toString(),
                session.snapshotSetup().getJSONObject("shared").optJSONObject("captions").toString(),
                "the press fell to the station caption and moved it");
        }
        finally
        {
            putBack(asFound);

            // The square's picture was taken away by hand and the monitor does not repaint an unchanged one: a fresh
            // editor takes the published pictures
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * With a tool armed, or a tail pick waiting for its click, a press on a train starts no drag and the pending pick
     * still points at its square.
     *
     * MUTATION: dropping `panel.isAToolArmed()` from `LayoutEditorTrainDrag.press` lets the train be lifted.
     */
    @Test(timeOut = 120000)
    public void testAnArmedToolOwnsThePressOnATrain() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        final Object panel = editor.getAutonomyPanel();

        final java.lang.reflect.Field tool = panel.getClass().getDeclaredField("tool");
        final java.lang.reflect.Field tail = panel.getClass().getDeclaredField("tailFor");

        tool.setAccessible(true);
        tail.setAccessible(true);

        try
        {
            onTheEventThread(() -> tool.set(panel, AutonomyEditorPanel.Tool.TEST));

            Mouse.drag(f.fromLabel, f.toLabel);
            Components.drain(8);

            assertUnchanged(asFound, "a train was dragged with a tool armed");

            onTheEventThread(() ->
            {
                tool.set(panel, AutonomyEditorPanel.Tool.NONE);
                tail.set(panel, f.from);
            });

            Mouse.drag(f.fromLabel, f.toLabel);
            Components.drain(8);

            assertUnchanged(asFound, "a train was dragged with a tail pick waiting");

            assertEquals(editor.getAutonomyPanel().tailPickFor(), f.from, "the pending tail pick no longer points at its square");
        }
        finally
        {
            onTheEventThread(() ->
            {
                tool.set(panel, AutonomyEditorPanel.Tool.NONE);
                tail.set(panel, null);
            });

            putBack(asFound);
        }
    }

    /**
     * A right press while a caption is being dragged puts the carried name down at once (FR-035).
     *
     * MUTATION: removing the two `hideCaptionGhost`/`clearCaptionDropTarget` lines from the press branch of
     * `LayoutGrid.dragCaption` leaves the carried name on screen.
     */
    @Test(timeOut = 120000)
    public void testARightPressMidCaptionDragPutsTheNameDown() throws Exception
    {
        Fixture f = fixture(false);

        LayoutLabel captioned = null;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getCaptionTarget(tile) != null
                && session.getLocomotiveNameAt(tile) == null && labelOf(tile) != null)
            {
                captioned = labelOf(tile);

                break;
            }
        }

        assertNotNull(captioned, "precondition: no station caption on " + PAGE + " away from a train");

        Mouse.press(captioned, MouseEvent.BUTTON1);

        // A name carried, as the first drag event lifts it
        onTheEventThread(() -> editor.showCaptionGhost(
            new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB), 0, 0));

        assertNotNull(ghost(), "precondition: no name is being carried");

        post(captioned, MouseEvent.MOUSE_PRESSED, center(captioned), InputEvent.BUTTON3_DOWN_MASK, 1, MouseEvent.BUTTON3);

        assertNull(ghost(), "a right press mid-drag left the carried caption on screen");

        post(captioned, MouseEvent.MOUSE_RELEASED, center(captioned), 0, 1, MouseEvent.BUTTON3);
        post(captioned, MouseEvent.MOUSE_RELEASED, center(captioned), 0, 1, MouseEvent.BUTTON1);
        Components.drain(8);
    }

    /**
     * What the train brings stays with it (FR-160): a timetable entry of the train is not lost and a paused train stays
     * paused.  Losing a timetable is grade B.
     *
     * MUTATION: clear the timetable in `AutonomyEditorPanel.dropTrain` once the train has landed, and this fails.
     */
    @Test(timeOut = 240000)
    public void testATrainKeepsItsTimetableAndItsPause() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        String train = session.getLocomotiveNameAt(f.from);

        try
        {
            session.setLocomotivePaused(train, true);

            // The snapshot's one entry is another train's: the dragged train is given a copy of it
            org.json.JSONArray all = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

            assertTrue(all.length() > 0, "precondition: the snapshot has no timetable entry to copy");

            all.put(new org.json.JSONObject(all.getJSONObject(0).toString()).put("loc", train));
            session.setGlobal("timetable", all);

            org.json.JSONArray timetable = timetableOf(train);

            assertTrue(timetable.length() > 0, "precondition: the train has no timetable entry");

            Mouse.drag(f.fromLabel, f.toLabel);
            Components.drain(8);

            assertEquals(session.getLocomotiveNameAt(f.to), train, "precondition: the train did not land");
            assertTrue(session.getPausedLocomotives().contains(train), "the drag un-paused the train");
            assertEquals(timetableOf(train).toString(), timetable.toString(), "the drag changed the train's timetable");
        }
        finally
        {
            session.setLocomotivePaused(train, false);

            putBack(asFound);
        }
    }

    /**
     * A train that heads a multi-unit goes where a paste would put it, and what a paste does to the member standing
     * elsewhere is what the drag does.
     *
     * MUTATION: as `testADragLeavesWhatAPasteLeaves`.
     */
    @Test(timeOut = 300000)
    public void testAMultiUnitIsTreatedAsAPasteTreatsIt() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        String head = session.getLocomotiveNameAt(f.from);
        String member = null;

        for (String each : session.placementsAutonomyWillWrite().values())
        {
            if (!each.equals(head)) member = each;
        }

        assertNotNull(member, "precondition: no second train stands on the railway");

        org.traincontrol.base.Locomotive lead = model.getLocByName(head);

        java.util.Map<String, Double> consist = new java.util.HashMap<>();

        consist.put(member, 1.0);

        lead.setModelMultiUnitLocomotives(consist);

        try
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());

            // Every question a paste would put is answered: the tail's is asked on the diagram, which waits for a click
            TailCrossedPrompt.answerForTests(TailCrossedPrompt.DISMISSED);

            try (Answering answering = Answering.start(message -> null))
            {
                realPaste(f.from, f.to);
            }
            finally
            {
                TailCrossedPrompt.answerForTests(null);
            }

            String pasted = outcome(f.to);

            putBack(asFound);

            editor = openEditor();

            fixture(false);

            dragTrain(f.from, f.to);

            assertEquals(outcome(f.to), pasted, "a multi-unit head dragged is not left as a paste leaves it");
        }
        finally
        {
            lead.setModelMultiUnitLocomotives(new java.util.HashMap<String, Double>());

            putBack(asFound);
        }
    }

    /**
     * Closing the editor with a train carried puts it down where it stood: the release that follows moves nothing.
     *
     * MUTATION: removing `trainDrag.abandon()` from `LayoutEditor.dispose` lets the release land.
     */
    @Test(timeOut = 120000)
    public void testClosingTheEditorMidDragLeavesTheTrain() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            liftAndCarry(f);

            assertNotNull(ghost(), "precondition: the train was not being carried");

            final LayoutEditor closing = editor;

            SwingUtilities.invokeAndWait(closing::dispose);

            post(f.fromLabel, MouseEvent.MOUSE_RELEASED, pointOn(f.toLabel, f.fromLabel), 0, 1, MouseEvent.BUTTON1);
            Components.drain(8);

            assertUnchanged(asFound, "a train carried when the editor closed landed");
        }
        finally
        {
            putBack(asFound);
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
            liftAndCarry(f);

            assertEquals(markOf(f.toLabel), new Color(0, 150, 0), "a station that takes the train is not marked green (canDropTrain "
                + editor.getAutonomyPanel().canDropTrain(f.from, f.to) + ", run holds the setup "
                + editor.getAutonomyPanel().setupIsHeldByARun() + ")");

            assertNotNull(ghost(), "nothing follows the pointer while the train is carried");

            carryTo(f.fromLabel, f.trackLabel);

            assertEquals(markOf(f.trackLabel), Color.RED, "a square that is no station is not marked as refusing");

            carryTo(f.fromLabel, f.toLabel);

            Mouse.release(f.fromLabel, MouseEvent.BUTTON1);
            Components.drain(8);

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
            Components.drain(8);

            assertUnchanged(asFound, "a release on plain track moved the train");

            Mouse.drag(f.fromLabel, f.fromLabel);
            Components.drain(8);

            assertUnchanged(asFound, "a release on its own square changed the setup");

            Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

            Point outside = new Point(-50, -50);

            post(f.fromLabel, MouseEvent.MOUSE_DRAGGED, outside, InputEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON);
            post(f.fromLabel, MouseEvent.MOUSE_RELEASED, outside, 0, 1, MouseEvent.BUTTON1);
            Components.drain(8);

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
            liftAndCarry(f);

            support.Keys.press(editor, KeyEvent.VK_ESCAPE);
            Components.drain(8);

            assertNull(ghost(), "Escape left the carried name on screen");

            Point there = pointOn(f.toLabel, f.fromLabel);

            post(f.fromLabel, MouseEvent.MOUSE_RELEASED, there, 0, 1, MouseEvent.BUTTON1);
            Components.drain(8);

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
            Components.drain(8);

            // A click on a station does what it always did to the square (cycles its arrow); only the train must stay.
            asFound = session.snapshotSetup();

            assertEquals(placements(), expectedPlacements, "a click on the train moved a train");

            Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

            Point at = center(f.fromLabel);

            post(f.fromLabel, MouseEvent.MOUSE_DRAGGED, new Point(at.x + 2, at.y + 1),
                InputEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON);

            assertNull(ghost(), "a two-pixel jitter lifted the train");

            post(f.fromLabel, MouseEvent.MOUSE_RELEASED, new Point(at.x + 2, at.y + 1), 0, 1, MouseEvent.BUTTON1);
            Components.drain(8);

            assertUnchanged(asFound, "a jitter changed the setup");

            Mouse.drag(f.fromLabel, f.toLabel);
            Components.drain(8);

            assertNotNull(session.getLocomotiveNameAt(f.to), "after a click and a jitter, a real drag no longer works");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * While a run holds the setup the train is not offered, and one already carried is not let go: pressed with a run
     * holding the setup, or with the run beginning mid-drag, nothing moves.
     *
     * MUTATION: dropping the `setupIsHeldByARun` test from `AutonomyEditorPanel.canDropTrain` leaves the station
     * reading green with a run holding the setup.
     */
    @Test(timeOut = 120000)
    public void testNothingDragsWhileARunHoldsTheSetup() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        final java.lang.reflect.Field staging = Reflect.field(TrainControlUI.class, "stagingFlowActive");

        try
        {
            // A run beginning while the train is carried
            liftAndCarry(f);

            assertNotNull(ghost(), "precondition: the train was not being carried before the run began");

            staging.setBoolean(ui, true);

            assertTrue(ui.aRunHoldsTheSetup(), "precondition: the staging flag does not hold the setup");

            carryTo(f.fromLabel, f.toLabel);

            assertEquals(markOf(f.toLabel), Color.RED, "with a run holding the setup, the station still reads as taking the train");

            Mouse.release(f.fromLabel, MouseEvent.BUTTON1);
            Components.drain(8);

            assertUnchanged(asFound, "a train carried when a run began landed");

            // A run already holding the setup: the train is still drawn there, and is not picked up
            final boolean[] drawn = new boolean[1];

            SwingUtilities.invokeAndWait(() -> drawn[0] = f.fromLabel.trainDrawnAt(center(f.fromLabel)));

            assertTrue(drawn[0], "precondition: the train is no longer drawn under the press, so nothing is tested");

            liftAndCarry(f);

            Mouse.release(f.fromLabel, MouseEvent.BUTTON1);
            Components.drain(8);

            assertUnchanged(asFound, "a train moved while a run holds the setup");
        }
        finally
        {
            staging.setBoolean(ui, false);

            putBack(asFound);
        }
    }

    /**
     * A train dragged onto another train's square, then Cancel or Discard on a page or mode switch, puts both back where
     * they were in the setup, the file and the railway, with their facing and tail (FR-160): the railway alone lost the
     * displaced train, and a rebuild let the railway's places win over the restored setup.
     *
     * MUTATION: passing no trains from `LayoutEditor.discardAutonomyWork` to the rebuild (as before) loses the
     * displaced train from all three stores.
     */
    @Test(timeOut = 400000)
    public void testCancelAndDiscardPutBothTrainsBackWhenTheTargetIsOccupied() throws Exception
    {
        fixture(true);

        org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            for (boolean bySwitch : new boolean[] {false, true})
            {
                putBack(asFound);

                SwingUtilities.invokeAndWait(() -> editor.dispose());

                editor = openEditor();

                Fixture f = fixture(true);

                String dragged = session.getLocomotiveNameAt(f.from);

                String before = threeStores(f);

                Mouse.drag(f.fromLabel, f.toLabel);
                Components.drain(8);

                assertEquals(session.getLocomotiveNameAt(f.to), dragged, "precondition: the train did not land");

                if (bySwitch) discardBySwitch();
                else cancelTheEditor();

                assertEquals(threeStores(f), before, (bySwitch ? "a Discard on a switch" : "Cancel")
                    + " did not put both trains back");
            }
        }
        finally
        {
            putBack(asFound);
        }
    }

    /** The setup, the file and the railway on two squares: each train, its facing and its tail. */
    private static String threeStores(Fixture f) throws Exception
    {
        return storesOn(f.from, f.to);
    }

    /** The same on any squares. */
    private static String storesOn(TileKey... squares) throws Exception
    {
        AutonomySession onDisk = new AutonomySession(sandbox.getFolder());

        java.util.List<LayoutDiagram> pages = new java.util.ArrayList<>();

        for (String name : model.getLayoutList()) pages.add(model.getLayout(name));

        onDisk.open(pages);

        StringBuilder out = new StringBuilder();

        for (AutonomySession each : new AutonomySession[] {session, onDisk})
        {
            out.append(each == session ? "setup " : "file ").append(each.placementsAutonomyWillWrite());

            for (TileKey tile : squares)
            {
                for (String property : new String[] {"facing", "arrivedFrom", "arrivedAlong"})
                {
                    out.append(' ').append(each.getPointProperty(tile, property));
                }
            }

            out.append('\n');
        }

        for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() != null) out.append(point.getCurrentLocomotive().getName()).append('@')
                .append(point.getName()).append(' ');
        }

        return out.toString();
    }

    /** The question a switch asks about unsaved work, answered Discard. */
    private static void discardBySwitch() throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("layout.ui.switchDiscard"))))
        {
            final java.lang.reflect.Method settle = Reflect.method(LayoutEditor.class, "settleUnsavedWork");

            onTheEventThread(() -> assertTrue((Boolean) settle.invoke(editor), "the switch did not go ahead"));

            assertEquals(answering.asked().size(), 1, "the switch did not ask about the unsaved work");
        }

        Components.drain(8);
    }

    /**
     * A green mark is true (FR-160): where the railway cannot take the train on that square (a square built since the
     * railway was, a train the roster lacks) or autonomy ignores the page, the mark is red and the release changes
     * nothing.
     *
     * MUTATION: dropping the copy-names loop from `TrainControlUI.canTrainLandOn` leaves the stale square green;
     * dropping the roster test leaves the unknown train green; dropping `isIgnored` from `canDropTrain` leaves the
     * excluded page green.
     */
    @Test(timeOut = 240000)
    public void testAMarkIsRedWhereTheRailwayCannotTakeTheTrain() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        String train = session.getLocomotiveNameAt(f.from);

        try
        {
            // A square changed since the railway was built: its copies have other names now
            session.setPointProperty(f.to, AutonomyBuilder.CAN_REVERSE, true);

            assertFalse(editor.getAutonomyPanel().canDropTrain(f.from, f.to), "a square the railway has not been rebuilt for is offered");

            org.json.JSONObject changed = session.snapshotSetup();

            Mouse.drag(f.fromLabel, f.toLabel);
            Components.drain(8);

            assertUnchanged(changed, "a train was dropped on a square the railway is stale for");

            putBack(asFound);

            // A train the roster does not know
            session.setPointProperty(f.from, "loc", settings("No Such Train", 5, false, 40));

            assertFalse(editor.getAutonomyPanel().canDropTrain(f.from, f.to), "a train the roster lacks is offered");

            putBack(asFound);

            // A page autonomy ignores
            session.setPageExcluded(PAGE, true);

            assertFalse(editor.getAutonomyPanel().canDropTrain(f.from, f.to), "a square on an ignored page is offered");

            assertEquals(session.getLocomotiveNameAt(f.from), train, "precondition");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * Where the paste would ask, the drag asks nothing and the mark tells the truth (FR-160): on a station trains may
     * turn at, a green mark means the train lands; and a drag handed a square that is no station meets no dialog.
     *
     * MUTATION: removing `!dropping` from the tail-side check in `DiagramLocGestures` stops a paste there with the
     * mark green; removing `if (dropping) return true;` before `errorTrainOnlyAtStation` shows that dialog; refusing
     * every may-turn square in `canDropTrain` grays the mark.
     */
    @Test(timeOut = 240000)
    public void testAStationTrainsMayTurnAtTakesTheDragAndNoDialogIsMet() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        String train = session.getLocomotiveNameAt(f.from);

        try (Answering answering = Answering.start(message -> null))
        {
            session.setPointProperty(f.to, AutonomyBuilder.CAN_REVERSE, true);

            onTheEventThread(ui::rebuildRunningLayoutFromSetup);
            Components.drain(8);

            Fixture again = fixture(false);

            boolean green = editor.getAutonomyPanel().canDropTrain(again.from, again.to);

            assertTrue(green, "the mark is not green on a station trains may turn at");

            dragTrain(again.from, again.to);

            assertEquals(session.getLocomotiveNameAt(again.to), train, "the mark was green and the train did not land");

            // A square the railway has a Point for that is no station
            TileKey notAStation = null;

            for (TileKey tile : session.getReducer().getPoints().keySet())
            {
                if (PAGE.equals(tile.getPage()) && !session.getStore().isStation(tile) && ui.getAutonomyPointForTile(tile) != null)
                {
                    notAStation = tile;
                }
            }

            assertNotNull(notAStation, "precondition: every square the railway has a Point for is a station");

            assertFalse(ui.dropTrainByPaste(train, notAStation), "a train was dropped on a square that is no station");

            assertEquals(answering.asked().size(), 0, "a drag met a dialog: " + answering.asked());
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * Cancel throws away every drag of the session, in the setup, the file and the railway, with facing and tail
     * (FR-160): the dragged train goes back whatever the target held, a chain of drops leaves no train lost, and a
     * dragged train the railway moved on afterwards goes back too.
     *
     * MUTATION: adding the dragged name only for an occupied target (`displaced != null` in
     * `AutonomyEditorPanel.dropTrain`) loses a train in the chain and leaves the empty drags moved.
     */
    @Test(timeOut = 400000)
    public void testCancelUndoesEveryDragOfTheSession() throws Exception
    {
        org.json.JSONObject asFound = began();

        try
        {
            java.util.List<TileKey> trains = trainSquares();
            java.util.List<TileKey> empty = emptyStations();

            assertTrue(trains.size() >= 3 && empty.size() >= 3, "precondition: three trains and three empty stations are needed");

            TileKey a = trains.get(0), b = trains.get(1), c = trains.get(2);

            // Two empty drops, then the railway moves the first train on alone
            reopenedFrom(asFound);

            String before = storesOn(a, b, c, empty.get(0), empty.get(1), empty.get(2));

            scrollTheDiagram();

            dragTrain(a, empty.get(0));
            dragTrain(b, empty.get(1));

            assertNotNull(session.getLocomotiveNameAt(empty.get(1)), "precondition: the second drop did not land");

            moveOnTheRailwayAlone(session.getLocomotiveNameAt(empty.get(0)), empty.get(2));

            cancelTheEditor();

            assertEquals(storesOn(a, b, c, empty.get(0), empty.get(1), empty.get(2)), before,
                "Cancel left something of two empty drops");

            // A chain: a onto b, then c onto the square a left
            reopenedFrom(asFound);

            dragTrain(a, b);
            dragTrain(c, a);


            cancelTheEditor();

            assertEquals(storesOn(a, b, c, empty.get(0), empty.get(1), empty.get(2)), before, "Cancel lost a train in a chain of drops");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * A shut station edited in the same session comes back with the trains; what the railway moved on its own and
     * nobody dragged keeps its place; and a Save makes the drags the new starting point, which a later Cancel keeps.
     *
     * MUTATION: none of its own: a Save retakes the undo point, which the restored setup then follows, so the saved
     * drag stays whatever the Cancel's list holds; passing every train in the list would put the railway's own move back.
     */
    @Test(timeOut = 400000)
    public void testCancelKeepsWhatWasSavedAndWhatTheRailwayMovedAlone() throws Exception
    {
        org.json.JSONObject asFound = began();

        try
        {
            java.util.List<TileKey> trains = trainSquares();
            java.util.List<TileKey> empty = emptyStations();

            TileKey a = trains.get(0), b = trains.get(1);

            // A shut station edited after a drag
            reopenedFrom(asFound);

            Object activeBefore = session.getPointProperty(empty.get(1), "active");

            String before = storesOn(a, b, empty.get(0), empty.get(1));

            dragTrain(a, empty.get(0));

            session.setPointProperty(empty.get(1), "active", false);

            cancelTheEditor();

            assertEquals(storesOn(a, b, empty.get(0), empty.get(1)), before, "a drag with a shut station was not all undone");
            assertEquals(session.getPointProperty(empty.get(1), "active"), activeBefore, "the shut station stayed shut");

            // A train the railway moved alone, and a drag that was never of it
            reopenedFrom(asFound);

            String mover = session.getLocomotiveNameAt(b);

            moveOnTheRailwayAlone(mover, empty.get(2));

            dragTrain(a, empty.get(0));

            cancelTheEditor();

            assertEquals(railwaySquareOf(mover), empty.get(2), "Cancel moved a train nobody dragged");

            // Save, then Cancel: the saved drag stays
            reopenedFrom(asFound);

            String saved = session.getLocomotiveNameAt(a);

            dragTrain(a, empty.get(0));

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("layout.ui.switchSave"))))
            {
                final java.lang.reflect.Method settleWork = Reflect.method(LayoutEditor.class, "settleUnsavedWork");

                onTheEventThread(() -> settleWork.invoke(editor));
            }

            dragTrain(b, empty.get(1));

            cancelTheEditor();

            assertEquals(session.getLocomotiveNameAt(empty.get(0)), saved, "Cancel undid a drag that was saved");
            assertNull(session.getLocomotiveNameAt(empty.get(1)), "Cancel kept a drag made after the save");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * A train placed from the train window's list and a drag, in either order, are both undone by Cancel, in the
     * setup, the file and the railway (MT-914): placed on an empty station then a drag, or a drag then a placement on
     * the square the drag left.
     *
     * MUTATION: put `if (named.size() > 1)` back before `putBackOnDiscard.addAll(named)` in
     * `AutonomyEditorPanel.placeLocomotive`, and the placed train stays in both orders and this fails.
     */
    @Test(timeOut = 400000)
    public void testCancelUndoesAPlacementAndADragInEitherOrder() throws Exception
    {
        org.json.JSONObject asFound = began();

        TailCrossedPrompt.answerForTests(TailCrossedPrompt.DISMISSED);

        try
        {
            java.util.List<TileKey> trains = trainSquares();
            java.util.List<TileKey> empty = emptyStations();

            assertTrue(!trains.isEmpty() && empty.size() >= 2, "precondition: a train and two empty stations are needed");

            TileKey a = trains.get(0);

            String placed = support.StandingTrain.standingNowhere(model, session);

            for (boolean placeFirst : new boolean[] {true, false})
            {
                reopenedFrom(asFound);

                String before = storesOn(a, empty.get(0), empty.get(1));

                TileKey placedOn = placeFirst ? empty.get(1) : a;

                if (placeFirst) placeFromTheList(placed, placedOn);

                dragTrain(a, empty.get(0));

                assertNotNull(session.getLocomotiveNameAt(empty.get(0)), "precondition: the drag did not land");

                if (!placeFirst) placeFromTheList(placed, placedOn);

                assertEquals(session.getLocomotiveNameAt(placedOn), placed, "precondition: the list did not place " + placed);

                cancelTheEditor();

                assertEquals(storesOn(a, empty.get(0), empty.get(1)), before,
                    (placeFirst ? "placed, then dragged" : "dragged, then placed") + ": Cancel did not undo both");
            }
        }
        finally
        {
            TailCrossedPrompt.answerForTests(null);

            putBack(asFound);
        }
    }

    /** Puts a train on a square through the editor's train window, as the user picks it from the list. */
    private static void placeFromTheList(String train, TileKey square) throws Exception
    {
        support.StationDoors.placeFromTheList(editor.getAutonomyPanel(), square, train);

        Components.drain(8);
    }

    /**
     * A sensor the railway has, made a station in the setup with no rebuild, is no destination yet: the mark is red.
     *
     * MUTATION: dropping the destination question from `TrainControlUI.canTrainLandOn` leaves it green with nothing
     * landing.
     */
    @Test(timeOut = 120000)
    public void testASquareMadeAStationSinceTheBuildIsRed() throws Exception
    {
        Fixture f = fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        TileKey sensor = new TileKey(PAGE, 0, 3);

        try
        {
            assertFalse(session.getStore().isStation(sensor), "precondition: 1 - Main:0,3 is already a station");

            session.setStation(sensor, true);

            assertFalse(editor.getAutonomyPanel().canDropTrain(f.from, sensor),
                "a square made a station, not yet a destination on the railway, is offered");
        }
        finally
        {
            putBack(asFound);
        }
    }

    /**
     * With every station able to turn trains and a train placed in the setup, a drag onto 1,10 or 7,7 records the side
     * and road (`arrivedFrom`, `arrivedAlong`) a real paste onto the same square records (FR-160); the facing too where
     * the paste asks nothing, as a drag takes the rule's answer where a paste asks.
     *
     * MUTATION: dropping the side a drag works out (`if (dropping) ui.tailAtTheLanding = null;` after
     * `ArrivalSidePrompt.forPlacement` in `DiagramLocGestures`) leaves the side the paste records unrecorded.
     */
    @Test(timeOut = 300000)
    public void testADragRecordsTheRoadAPasteRecordsWhereEveryStationMayTurn() throws Exception
    {
        fixture(false);

        org.json.JSONObject asFound = session.snapshotSetup();

        TileKey where = new TileKey(PAGE, 9, 6);

        assertTrue(session.getStore().isStation(where) && session.getLocomotiveNameAt(where) == null, "precondition: 1 - Main:9,6 is not an empty station");

        String lone = "02 0314-1 DDR";

        assertTrue(model.getLocList().contains(lone) && !session.placementsAutonomyWillWrite().containsValue(lone),
            "precondition: " + lone + " is not a train that stands in the roster only");

        try
        {
            for (TileKey to : new TileKey[] {new TileKey(PAGE, 1, 10), new TileKey(PAGE, 7, 7)})
            {
                standOnATurningPage(asFound, where, lone);

                SwingUtilities.invokeAndWait(() -> editor.dispose());

                boolean pasteAsked;

                TailCrossedPrompt.answerForTests(TailCrossedPrompt.DISMISSED);

                try (Answering answering = Answering.start(message -> null))
                {
                    realPaste(where, to);

                    pasteAsked = !answering.asked().isEmpty();
                }
                finally
                {
                    TailCrossedPrompt.answerForTests(null);
                }

                assertEquals(session.getLocomotiveNameAt(to), lone, "precondition: the paste did not land on " + to);

                String pasted = pasteAsked ? sideAndRoad(to) : outcome(to);

                editor = openEditor();

                standOnATurningPage(asFound, where, lone);

                fixture(false);

                assertTrue(editor.getAutonomyPanel().canDropTrain(where, to), "precondition: " + to + " does not take the drag");

                dragTrain(where, to);

                assertEquals(session.getLocomotiveNameAt(to), lone, "precondition: the train did not land on " + to);

                assertEquals(pasteAsked ? sideAndRoad(to) : outcome(to), pasted,
                    "the drag onto " + to + " records what a real paste does not (paste asked: " + pasteAsked + ")");
            }
        }
        finally
        {
            putBack(asFound);
        }
    }

    /** The setup as found, every station on the page able to turn trains, and the train placed on one of them. */
    private static void standOnATurningPage(org.json.JSONObject asFound, TileKey where, String train) throws Exception
    {
        putBack(asFound);

        for (TileKey station : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(station.getPage()) && session.getStore().isStation(station))
            {
                session.setPointProperty(station, AutonomyBuilder.CAN_REVERSE, true);
            }
        }

        final java.util.Set<String> all = new java.util.HashSet<>(session.placementsAutonomyWillWrite().values());

        onTheEventThread(() -> ui.rebuildRunningLayoutFromSetup(false, all));
        Components.drain(8);

        session.setPointProperty(where, "loc", settings(train, 5, false, 40));

        // A placement door's redraw, which writing the setup directly skips: without it the train is not drawn
        final Object panel = editor.getAutonomyPanel();
        final java.lang.reflect.Method placed = Reflect.method(panel.getClass(), "placementChanged", java.util.Collection.class);

        onTheEventThread(() -> placed.invoke(panel, java.util.Collections.singletonList(train)));
        Components.drain(8);
    }

    /** The side a placement records the train came in by, and the road. */
    private static String sideAndRoad(TileKey to)
    {
        return "arrivedFrom=" + session.getPointProperty(to, "arrivedFrom") + " arrivedAlong=" + session.getPointProperty(to, "arrivedAlong");
    }

    /** The editor ready, and the setup as it stands. */
    private static org.json.JSONObject began() throws Exception
    {
        fixture(false);

        return session.snapshotSetup();
    }

    private static java.util.List<TileKey> trainSquares()
    {
        return new java.util.ArrayList<>(session.placementsAutonomyWillWrite().keySet());
    }

    /** Opens a fresh editor on the setup as found. */
    private static void reopenedFrom(org.json.JSONObject asFound) throws Exception
    {
        putBack(asFound);

        SwingUtilities.invokeAndWait(() -> editor.dispose());

        editor = openEditor();

        fixture(false);
    }

    /** The stations on the page with no train, in a fixed order, with a drawn square. */
    private static java.util.List<TileKey> emptyStations() throws Exception
    {
        java.util.List<TileKey> empty = new java.util.ArrayList<>();

        for (TileKey[] each : casesToCompare()) if (!empty.contains(each[1])) empty.add(each[1]);

        return empty;
    }

    /** The railway moves a train on its own, as a run does: the setup is told nothing. */
    private static void moveOnTheRailwayAlone(String train, TileKey to) throws Exception
    {
        final String point = session.getStationIndex().pointNamesAt(to).iterator().next();

        onTheEventThread(() -> assertTrue(model.getAutoLayout().moveLocomotive(train, point, false, true),
            "precondition: the railway would not move " + train + " to " + to));
    }

    private static TileKey railwaySquareOf(String train)
    {
        for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() != null && train.equals(point.getCurrentLocomotive().getName()))
            {
                return session.getStationIndex().squareOf(point);
            }
        }

        return null;
    }

    private static void cancelTheEditor() throws Exception
    {
        try (Answering answering = Answering.start(
            message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0]))))
        {
            final java.lang.reflect.Method cancel = Reflect.method(LayoutEditor.class, "confirmExit");

            later(() -> cancel.invoke(editor));

            support.Components.waitFor(() -> !editor.isDisplayable(), 5000);
        }

        Components.drain(8);

        assertFalse(editor.isDisplayable(), "precondition: Cancel did not close the editor");
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

        needTwoTrains();

        showTheTrains();

        final Fixture[] found = new Fixture[1];

        support.Components.waitFor(() -> support.Components.onTheEventThread(() -> (found[0] = look(occupied)) != null), 20000);

        if (found[0] != null) return found[0];

        final String[] why = new String[1];

        SwingUtilities.invokeAndWait(() -> why[0] = diagnose());

        fail("precondition: no drawn train, " + (occupied ? "occupied" : "empty")
            + " station and plain track square on " + PAGE + ": " + why[0]);

        return null;
    }

    /** The frozen railway stands trains on this page once the locomotive list has arrived; two are needed. */
    private static void needTwoTrains()
    {
        int standing = 0;

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getLocomotiveNameAt(tile) != null) standing++;
        }

        assertTrue(standing >= 2, "precondition: " + standing + " trains stand on " + PAGE + ", and two are needed");
    }

    /**
     * Show Trains is a remembered view setting, which may be off on the machine running this; the box is ticked
     * without the click, so the setting itself is left as it was.
     */
    private static void showTheTrains() throws Exception
    {
        final Object panel = editor.getAutonomyPanel();

        final java.lang.reflect.Field box = Reflect.field(panel.getClass(), "showTrains");

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

        Components.drain(8);
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

                LayoutLabel label = drawnLabel(grid, tile);

                if (label == null) continue;

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

                LayoutLabel label = drawnLabel(grid, tile);

                if (label == null) continue;

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

    /** The square's label if it is really on the page, else null. */
    private static LayoutLabel drawnLabel(LayoutGrid grid, TileKey tile)
    {
        LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

        return label == null || label.isSpacer() || label.getParent() == null ? null : label;
    }

    private static LayoutGrid gridOf() throws Exception
    {
        return (LayoutGrid) Reflect.get(LayoutEditor.class, editor, "grid");
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
        return Reflect.get(LayoutEditor.class, editor, "captionGhost");
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
                java.lang.reflect.Field field = Reflect.field(LayoutEditor.class, "jScrollPane1");

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

        Components.drain(8);
    }

    /** Every placement the setup records, by square. */
    private static Map<String, String> placements()
    {
        Map<String, String> out = new java.util.TreeMap<>();

        for (Map.Entry<TileKey, String> standing : session.placementsAutonomyWillWrite().entrySet())
        {
            out.put(standing.getKey().toString(), standing.getValue());
        }

        return out;
    }

    /**
     * The setup as found, and the railway made to agree with it: a rebuild lets the railway's places win unless the
     * trains are named, so every train placed before or after is.
     */
    private static void putBack(org.json.JSONObject asFound) throws Exception
    {
        final java.util.Set<String> trains = new java.util.HashSet<>(session.placementsAutonomyWillWrite().values());

        session.restoreSetup(asFound);

        trains.addAll(session.placementsAutonomyWillWrite().values());

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup(false, trains));

        Components.drain(8);

        for (java.util.Map.Entry<TileKey, String> placed : session.placementsAutonomyWillWrite().entrySet())
        {
            TileKey standing = null;

            for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
            {
                if (point.getCurrentLocomotive() != null && placed.getValue().equals(point.getCurrentLocomotive().getName()))
                {
                    standing = session.getStationIndex().squareOf(point);
                }
            }

            assertEquals(standing, placed.getKey(), "precondition: after putting the setup back, the railway has "
                + placed.getValue() + " elsewhere than the setup says");
        }
    }

    /** Presses the train and carries it over the target station, the button still down. */
    private static void liftAndCarry(Fixture f) throws Exception
    {
        Mouse.press(f.fromLabel, MouseEvent.BUTTON1);

        carryTo(f.fromLabel, f.toLabel);
    }

    private static String key(TileKey[] move)
    {
        return move[0] + ">" + move[1];
    }

    private static LayoutLabel labelOf(TileKey tile) throws Exception
    {
        return onTheEventThreadValue(() -> drawnLabel(gridOf(), tile));
    }

    private static <T> T onTheEventThreadValue(final java.util.concurrent.Callable<T> read) throws Exception
    {
        final Object[] out = new Object[1];

        onTheEventThread(() -> out[0] = read.call());

        @SuppressWarnings("unchecked")
        T value = (T) out[0];

        return value;
    }

    /** Drags the train on one square to another with the mouse, once the first is drawn. */
    private static void dragTrain(TileKey from, TileKey to) throws Exception
    {
        LayoutLabel start = labelOf(from);

        assertTrue(support.Components.waitFor(() -> support.Components.onTheEventThread(() -> start.trainDrawnAt(center(start))), 10000),
            "precondition: no train is drawn on " + from + "; is Show Trains off?");

        Mouse.drag(start, labelOf(to));
        Components.drain(8);
    }

    /** The trains on the page, each with some empty stations, as moves to compare. */
    private static java.util.List<TileKey[]> casesToCompare() throws Exception
    {
        java.util.List<TileKey> trains = new java.util.ArrayList<>(session.placementsAutonomyWillWrite().keySet());
        java.util.List<TileKey> empty = new java.util.ArrayList<>();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage()) && session.getStore().isStation(tile)
                && session.getLocomotiveNameAt(tile) == null && labelOf(tile) != null) empty.add(tile);
        }

        java.util.Collections.sort(empty, (a, b) -> a.toString().compareTo(b.toString()));

        java.util.List<TileKey[]> cases = new java.util.ArrayList<>();

        for (TileKey from : trains)
        {
            for (int i = 0; i < empty.size(); i += 3) cases.add(new TileKey[] {from, empty.get(i)});
        }

        return cases;
    }

    /** A real Control+X on one square and Control+V on another, through the main window's key door. */
    private static void realPaste(final TileKey from, final TileKey to) throws Exception
    {
        final java.lang.reflect.Method gesture =
            Reflect.method(TrainControlUI.class, "locomotiveGestureOnDiagram", int.class, boolean.class);

        // The train is also the one selected in the main window, which is what a paste puts down if its cut took none
        final java.lang.reflect.Field selected = Reflect.field(TrainControlUI.class, "activeLoc");

        final org.traincontrol.base.Locomotive train = model.getLocByName(session.getLocomotiveNameAt(from));

        onTheEventThread(() ->
        {
            selected.set(ui, train);
            ui.setHoveredDiagramTile(PAGE, from.getX(), from.getY());
            gesture.invoke(ui, KeyEvent.VK_X, true);
            ui.setHoveredDiagramTile(PAGE, to.getX(), to.getY());
            gesture.invoke(ui, KeyEvent.VK_V, true);
        });

        Components.drain(8);
    }

    /** What a placement left on a square: facing, tail side and road, every train's place, and the copy it stands on. */
    private static String outcome(TileKey to)
    {
        StringBuilder out = new StringBuilder();

        for (String property : new String[] {"facing", "arrivedFrom", "arrivedAlong"})
        {
            out.append(property).append('=').append(session.getPointProperty(to, property)).append(' ');
        }

        out.append(placements());

        String train = session.getLocomotiveNameAt(to);

        for (org.traincontrol.automation.Point point : model.getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() != null && point.getCurrentLocomotive().getName().equals(train))
            {
                out.append(" on ").append(point.getName()).append(" from ").append(point.getArrivedFrom());
            }
        }

        return out.toString();
    }

    private static org.json.JSONArray timetableOf(String train)
    {
        org.json.JSONObject globals = session.snapshotSetup().getJSONObject("configurations")
            .getJSONObject(session.getStore().getActiveConfiguration()).optJSONObject("globals");

        org.json.JSONArray all = globals == null ? null : globals.optJSONArray("timetable");

        org.json.JSONArray mine = new org.json.JSONArray();

        for (int i = 0; all != null && i < all.length(); i++)
        {
            if (all.get(i).toString().contains(train)) mine.put(all.get(i));
        }

        return mine;
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
}
