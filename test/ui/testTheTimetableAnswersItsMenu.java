package ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * The timetable's right-click menu and its Execute button, used as the operator uses them: a right-click on an entry's
 * row of the Timetable tab, an item of the menu that opens, and the real question answered.
 *
 * Each claim reads what the door did: the timetable in the model, the rows the tab shows, the setup saved in the
 * layout's folder, and the sentence the door said.  The entries are this class's own, two journeys of a locomotive it
 * adds, over track of the frozen railway.
 *
 * MUTATION: each claim names the line it rests on; see its javadoc.
 *
 * @author Adam
 */
public class testTheTimetableAnswersItsMenu
{
    private static final String TRAIN = "TT menu train";

    /** Where the run starts, a platform of the frozen railway, and where it ends, a way off through its switches. */
    private static final String PLATFORM = "BottomMainC";
    private static final String DESTINATION = "BottomInnerOtherside";

    private static final long PATIENCE_MS = 10000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the timetable tab needs a display");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStationAddress.withNoStation(() -> init(null, true, true, false, true));

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        for (int turn = 0; turn < 2; turn++) SwingUtilities.invokeAndWait(() -> { });

        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = ui.getAutonomySession() == null ? null
                : ui.getAutonomySession().getStore().getActiveConfiguration();

            if (active == null) throw new SkipException("no autonomy configuration on the snapshot");

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            assertTrue(Components.waitFor(() -> ui.getActiveDiagramConfiguration() != null, PATIENCE_MS),
                "precondition: the snapshot's configuration did not load");
        }

        if (model.getLocByName(TRAIN) == null) model.newMM2Locomotive(TRAIN, 66);

        model.getLocByName(TRAIN).setPreferredSpeed(30);
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

            if (model != null)
            {
                model.deleteLoc(TRAIN);
                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Change Delay on an entry, answered with a number of seconds, sets that entry's delay and no other.
     *
     * MUTATION: in `TrainControlUI.updateTimetableDelay`, `Long.parseLong(input) * 1000` to `Long.parseLong(input) * 100`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testChangeDelaySetsTheTypedDelayOnThatEntry() throws Exception
    {
        List<TimetablePath> entries = twoEntries(true);

        final long firstWas = entries.get(0).getSecondsToNext();

        try (Answering answering = Answering.start(message -> Answering.type("7")))
        {
            press(onRow(1), "timetable.ui.menuChangeDelay");

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Change Delay asked nothing");

            assertEquals(answering.asked().get(0), I18n.t("timetable.ui.enterDelaySecondsBeforeRouteExecutes"));

            assertTrue(Components.waitFor(() -> entries.get(1).getSecondsToNext() == 7000, PATIENCE_MS),
                "the second entry's delay is " + entries.get(1).getSecondsToNext() + " ms after 7 seconds were typed");
        }

        assertEquals(entries.get(0).getSecondsToNext(), firstWas, "Change Delay on the second entry changed the first");
    }

    /**
     * Delete Entry, answered Yes, takes that entry out of the timetable and off the tab, and leaves the other.
     *
     * MUTATION: in `TrainControlUI.deleteTimetableEntry`, take out `layout.removeTimetableEntry(row, (TimetablePath) shown);`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testDeleteEntryAnsweredYesRemovesThatEntry() throws Exception
    {
        List<TimetablePath> entries = twoEntries(true);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            press(onRow(1), "timetable.ui.menuDeleteEntry");

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Delete Entry asked nothing");

            assertEquals(answering.asked().get(0), I18n.f("timetable.ui.confirmRemoveEntryContinue", 1, entries.get(1)),
                "Delete Entry did not name the entry it removes");

            assertTrue(Components.waitFor(() -> railway().getTimetable().size() == 1, PATIENCE_MS),
                "Delete Entry answered Yes left " + railway().getTimetable().size() + " entries");
        }

        assertSame(railway().getTimetable().get(0), entries.get(0), "Delete Entry on the second entry removed the first");

        assertTrue(Components.waitFor(() -> rows() == 1, PATIENCE_MS), "the tab still shows " + rows() + " rows");
    }

    /**
     * Delete Entry, answered No, removes nothing.
     *
     * MUTATION: in `TrainControlUI.deleteTimetableEntry`, the `if (dialogResult != JOptionPane.YES_OPTION) return;`
     * after the question to `if (false) return;`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testDeleteEntryAnsweredNoRemovesNothing() throws Exception
    {
        List<TimetablePath> entries = twoEntries(true);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            press(onRow(0), "timetable.ui.menuDeleteEntry");

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Delete Entry asked nothing");

            // Its removal runs on a worker: given the time to, had it been sent
            Thread.sleep(1000);

            Components.settle();
        }

        assertEquals(railway().getTimetable(), entries, "Delete Entry answered No changed the timetable");
    }

    /**
     * Restart Timetable, answered Yes, marks every entry as not yet run, so the next run starts from the top.
     *
     * MUTATION: in `TrainControlUI.restartTimetable`, take out `this.getModel().getAutoLayout().resetTimetable();`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testRestartTimetableMarksEveryEntryUnrun() throws Exception
    {
        List<TimetablePath> entries = twoEntries(true);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            press(onRow(0), "timetable.ui.menuRestartTimetable");

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Restart Timetable asked nothing");

            assertEquals(answering.asked().get(0), I18n.t("timetable.ui.confirmRestartMarkEntriesUnvisited"));

            assertTrue(Components.waitFor(() -> !entries.get(0).isExecuted() && !entries.get(1).isExecuted(), PATIENCE_MS),
                "Restart Timetable answered Yes left an entry marked as run");
        }

        assertEquals(railway().getUnfinishedTimetablePathIndex(), 0, "the next run would not start from the first entry");
    }

    /**
     * Restart Timetable over a timetable nothing has run yet says it is already reset, and asks nothing.
     *
     * MUTATION: in `TrainControlUI.restartTimetable`, `getUnfinishedTimetablePathIndex() == 0` to `== 1`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testRestartOfAnUnrunTimetableSaysItIsAlreadyReset() throws Exception
    {
        twoEntries(false);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            press(onRow(0), "timetable.ui.menuRestartTimetable");

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Restart Timetable said nothing");

            Components.settle();

            assertEquals(answering.asked(), Arrays.asList(I18n.t("timetable.ui.infoTimetableAlreadyReset")),
                "Restart Timetable over an unrun timetable did not say it is already reset, or asked as well");
        }
    }

    /**
     * Clear Timetable, answered Yes, empties the timetable, the tab, and the timetable saved in the layout's setup.
     *
     * MUTATION: in `TrainControlUI.clearTimetable`, take out `keeping.clearTheTimetable();`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testClearTimetableAnsweredYesEmptiesItAndTheSavedSetup() throws Exception
    {
        twoEntries(true);

        assertTrue(savedEntries() > 0, "precondition: the setup in the layout's folder holds no timetable to clear");

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            press(onRow(0), "timetable.ui.menuClearTimetable");

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Clear Timetable asked nothing");

            assertEquals(answering.asked().get(0), I18n.t("timetable.ui.confirmRemoveAllEntries"));

            assertTrue(Components.waitFor(() -> railway().getTimetable().isEmpty(), PATIENCE_MS),
                "Clear Timetable answered Yes left " + railway().getTimetable().size() + " entries");

            assertTrue(Components.waitFor(() -> rows() == 0, PATIENCE_MS), "the tab still shows " + rows() + " rows");

            assertTrue(Components.waitFor(() -> savedEntries() == 0, PATIENCE_MS), "the setup saved in the layout's"
                + " folder still holds " + savedEntries() + " timetable entries, which come back at the next load");
        }
    }

    /**
     * Clear Timetable, answered No, changes nothing.
     *
     * MUTATION: in `TrainControlUI.clearTimetable`, the `if (dialogResult != JOptionPane.YES_OPTION) return;` after the
     * question to `if (false) return;`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testClearTimetableAnsweredNoChangesNothing() throws Exception
    {
        List<TimetablePath> entries = twoEntries(true);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            press(onRow(0), "timetable.ui.menuClearTimetable");

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Clear Timetable asked nothing");

            // Its clearing runs on a worker: given the time to, had it been sent
            Thread.sleep(1000);

            Components.settle();
        }

        assertEquals(railway().getTimetable(), entries, "Clear Timetable answered No changed the timetable");
    }

    /**
     * Execute Timetable with no entries says there are none, and the button can be pressed again.
     *
     * MUTATION: in `TrainControlUI.executeTimetableActionPerformed`, take out the `this.executeTimetable.setEnabled(true);`
     * after the no-entries message.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testExecuteTimetableWithNoEntriesSaysSo() throws Exception
    {
        railway().setTimetable(new ArrayList<>());

        final AbstractButton execute = executeButton();

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.select(execute);

            Components.press(execute);

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Execute Timetable said nothing");

            assertEquals(answering.asked().get(0), I18n.t("timetable.ui.errorNoEntriesCaptureCommandsFirst"));

            Components.settle();
        }

        assertTrue(Components.onTheEventThread(execute::isEnabled), "Execute Timetable is greyed after saying there is"
            + " nothing to run, and nothing gives it back");
    }

    /**
     * Execute Timetable, with the train of the first entry somewhere other than where that entry starts, says where to
     * put it, and starts nothing.
     *
     * MUTATION: in `TrainControlUI.executeTimetableActionPerformed`, `if (notThere != null)` to `if (false)`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testExecuteTimetableWithATrainAwayFromItsStartSaysWhereItMustGo() throws Exception
    {
        List<TimetablePath> entries = twoEntries(false);

        // Off the railway, wherever an earlier claim left it
        Point standing = railway().getLocomotiveLocation(model.getLocByName(TRAIN));

        if (standing != null) railway().moveLocomotive(null, standing.getName(), false);

        assertTrue(railway().getLocomotiveLocation(model.getLocByName(TRAIN)) == null,
            "precondition: the train stands on the railway, so it may be at its start");

        final String start = (String) Components.onTheEventThread(() -> timetableTable().getValueAt(0, 2));

        final String facing = entries.get(0).getStart().getCopyFacing();

        final String expected = facing == null
            ? I18n.f("timetable.ui.infoLocomotiveMustBeMovedToStart", TRAIN, start)
            : I18n.f("timetable.ui.infoLocomotiveMustBeMovedToStartFacing", TRAIN, start, I18n.t("autosetup.ui.facing"
                + facing));

        final AbstractButton execute = executeButton();

        // Yes to the warning about armed conditional routes, which the snapshot's routes may raise first; OK to the rest
        try (Answering answering = Answering.start(message ->
            message.equals(I18n.t("route.ui.confirmConditionalRoutesActiveProceed"))
                ? Answering.press(I18n.t("ui.yes")) : Answering.pressAt(0)))
        {
            Components.select(execute);

            Components.press(execute);

            assertTrue(Components.waitFor(() -> answering.asked().contains(expected), PATIENCE_MS),
                "Execute Timetable did not say where the train must be put: " + answering.asked());

            Components.settle();
        }

        assertTrue(railway().getActiveLocomotives().isEmpty(), "Execute Timetable sent a train after saying the first"
            + " one is not at its start: " + railway().getActiveLocomotives());

        assertTrue(Components.onTheEventThread(execute::isEnabled), "Execute Timetable is greyed after its refusal");
    }

    /**
     * Execute Timetable runs the timetable to its end on the simulated railway: the train arrives where its entry ends,
     * and every switch and signal on its way reads as its path set it.
     *
     * MUTATION: in `Layout`, where an edge's accessories are configured, `commanded = acc.setState(state);` to
     * `commanded = true;`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testExecuteTimetableSetsTheSwitchesOnItsWay() throws Exception
    {
        final List<Edge> way = runFromThePlatform();

        final Point end = way.get(way.size() - 1).getEnd();

        assertEquals(railway().getLocomotiveLocation(model.getLocByName(TRAIN)), end, "the train is not where its entry"
            + " ends");

        for (Map.Entry<String, Accessory.accessorySetting> c : settingsAlong(way).entrySet())
        {
            assertEquals(model.getAccessoryByName(c.getKey()).isSwitched(), Accessory.isThrow(c.getValue()), c.getKey()
                + " was not set " + c.getValue() + " by the timetable's run along " + railway().pathToString(way));
        }
    }

    /**
     * The journey a timetable runs is counted in the statistics: one more journey for the train, and a visit to where
     * it ended.
     *
     * MUTATION: in `Layout.executePath`, take out `loc.incrementNumPaths();`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testExecuteTimetableCountsTheJourneyInTheStatistics() throws Exception
    {
        final Locomotive train = model.getLocByName(TRAIN);

        final int journeys = train.getNumPaths();

        final List<Edge> way = runFromThePlatform();

        assertEquals((int) train.getNumPaths(), journeys + 1, "the statistics did not count the timetable's journey");

        assertTrue(railway().getVisitHistory().containsKey(way.get(way.size() - 1).getEnd().getName()), "the"
            + " statistics did not record the visit to where the journey ended");
    }

    /**
     * Puts the train on the platform, makes its timetable one journey from there to the far station, sets every
     * accessory on the way the other way round, and presses Execute Timetable; waits for the run to end.
     *
     * @return the journey's path
     * @throws Exception from the event thread
     */
    private static List<Edge> runFromThePlatform() throws Exception
    {
        final Layout railway = railway();

        assertTrue(railway.isSimulate(), "precondition: the frozen railway's configuration does not simulate");

        railway.setMinDelay(0);
        railway.setMaxDelay(0);

        assertTrue(railway.moveLocomotive(TRAIN, PLATFORM, false), "precondition: the train could not be put on "
            + PLATFORM);

        final List<Edge> way = railway.bfs(railway.getPoint(PLATFORM), railway.getPoint(DESTINATION), null);

        assertTrue(way != null && !way.isEmpty(), "precondition: no way from " + PLATFORM + " to " + DESTINATION);

        final Map<String, Accessory.accessorySetting> settings = settingsAlong(way);

        assertTrue(!settings.isEmpty(), "precondition: nothing on the way from " + PLATFORM + " to " + DESTINATION
            + " is set by the path, so there is nothing to see");

        for (Map.Entry<String, Accessory.accessorySetting> c : settings.entrySet())
        {
            model.getAccessoryByName(c.getKey()).setSwitched(!Accessory.isThrow(c.getValue()));
        }

        final TimetablePath entry = new TimetablePath(model.getLocByName(TRAIN), way, 0);

        railway.setTimetable(new ArrayList<>(Arrays.asList(entry)));

        if (!model.getPowerState()) model.go();

        assertTrue(model.waitForPowerState(true, PATIENCE_MS), "precondition: the power is not on");

        final AbstractButton execute = executeButton();

        try (Answering answering = Answering.start(message ->
            message.equals(I18n.t("route.ui.confirmConditionalRoutesActiveProceed"))
                ? Answering.press(I18n.t("ui.yes")) : Answering.pressAt(0)))
        {
            Components.select(execute);

            Components.press(execute);

            assertTrue(Components.waitFor(entry::isExecuted, 60000), "the timetable's entry did not run: "
                + answering.asked());

            assertTrue(Components.waitFor(() -> railway.getActiveLocomotives().isEmpty()
                && Components.onTheEventThread(execute::isEnabled), 60000), "the timetable did not finish");
        }

        return way;
    }

    /** What the path sets, accessory by accessory, in path order. */
    private static Map<String, Accessory.accessorySetting> settingsAlong(List<Edge> path)
    {
        Map<String, Accessory.accessorySetting> settings = new java.util.LinkedHashMap<>();

        for (Edge e : path) settings.putAll(e.getConfigCommands());

        return settings;
    }

    // ---------------------------------------------------------------- the fixture

    private static Layout railway()
    {
        return model.getAutoLayout();
    }

    /**
     * Gives the railway a timetable of two journeys of this class's train, and waits for the tab to show them.
     *
     * The tab redraws when the railway says a journey started or ended, which a timetable set from here does not;
     * so its redraw is asked for directly, as setup and not as anything under test.
     *
     * @param run whether the entries are marked as already run
     * @return the entries, in order
     * @throws Exception from the redraw
     */
    private static List<TimetablePath> twoEntries(boolean run) throws Exception
    {
        List<Edge> edges = new ArrayList<>(railway().getEdges());

        assertTrue(edges.size() >= 2, "precondition: the railway has no edges to make a timetable from");

        List<TimetablePath> entries = new ArrayList<>();

        for (int i = 0; i < 2; i++)
        {
            TimetablePath entry = new TimetablePath(model.getLocByName(TRAIN), Arrays.asList(edges.get(i)),
                run ? System.currentTimeMillis() - 60000 + i : 0);

            entry.setSecondsToNext(3000 * (i + 1));

            entries.add(entry);
        }

        railway().setTimetable(entries);

        java.lang.reflect.Method repaint = TrainControlUI.class.getDeclaredMethod("repaintTimetable");

        repaint.setAccessible(true);

        repaint.invoke(ui);

        assertTrue(Components.waitFor(() -> rows() == 2, PATIENCE_MS), "precondition: the tab shows " + rows()
            + " rows, not the two entries");

        return entries;
    }

    private static JTable timetableTable()
    {
        try
        {
            java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("timetable");

            field.setAccessible(true);

            return (JTable) field.get(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static int rows()
    {
        return Components.onTheEventThread(() -> timetableTable().getRowCount());
    }

    /**
     * Right-clicks a row of the timetable, with its tab in front, and gives back the menu that opened.
     */
    private static JPopupMenu onRow(int row) throws Exception
    {
        final JTable table = timetableTable();

        Components.select(table);

        Components.settle();

        final java.awt.Rectangle cell = Components.onTheEventThread(() -> table.getCellRect(row, 1, true));

        SwingUtilities.invokeLater(() -> table.dispatchEvent(new java.awt.event.MouseEvent(table,
            java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, cell.x + 4, (int) cell.getCenterY(),
            1, true, java.awt.event.MouseEvent.BUTTON3)));

        assertTrue(Components.waitFor(() -> Components.popupShowing() != null, PATIENCE_MS), "a right-click on row " + row
            + " of the timetable opened no menu");

        return Components.popupShowing();
    }

    /** Presses the item of a menu that says this. */
    private static void press(JPopupMenu menu, String key)
    {
        AbstractButton item = Components.withText(menu, I18n.t(key));

        assertNotNull(item, "the timetable's menu has no " + I18n.t(key));

        Components.press(item);
    }

    /** The Auto tab's Execute Timetable button, by its field, and saying what the operator reads on it. */
    private static AbstractButton executeButton() throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("executeTimetable");

        field.setAccessible(true);

        AbstractButton execute = (AbstractButton) field.get(ui);

        assertEquals(Components.onTheEventThread(execute::getText), I18n.t("ui.main.executeTimetable"),
            "the window's Execute Timetable does not say so");

        return execute;
    }

    /** How many timetable entries the active configuration in the layout's folder holds, read from the file. */
    private static int savedEntries()
    {
        try
        {
            String active = ui.getActiveDiagramConfiguration();

            File file = new File(sandbox.getFolder(), "config/autonomy/configuration-" + active + ".json");

            org.json.JSONObject configuration = new org.json.JSONObject(new String(
                java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));

            org.json.JSONObject globals = configuration.optJSONObject("globals");

            org.json.JSONArray timetable = globals == null ? null : globals.optJSONArray("timetable");

            return timetable == null ? 0 : timetable.length();
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
