package ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A timetable entry the railway changed alone while the autonomy editor was open stays on its squares through the
 * editor's renames, Cancel, Save and the next load (OB-345).  Every claim reads the entry by the squares it ends up on:
 * on the railway the load built, in the setup, and in the file a next start reads.  On `single-switch`, whose entry from
 * WestEnd through Approach to MainPlatform has a split square in the middle and a plain one at each end.
 *
 * @author Adam
 */
public class testATimetableEntryKeepsItsSquaresThroughRenames
{
    private static final String PAGE = "1 - Junction";

    private static final String TRAIN = "OB-345 train";

    private static final TileKey WEST_END = onThePage(1);
    private static final TileKey APPROACH = onThePage(3);
    private static final TileKey MAIN = onThePage(7);

    /** The plain track between WestEnd and Approach, whose arrow can take the entry's first leg away. */
    private static final TileKey BETWEEN = onThePage(2);

    private static final List<TileKey> THE_WHOLE_WAY = Arrays.asList(WEST_END, APPROACH, MAIN);

    private static TileKey onThePage(int x)
    {
        return new TileKey(PAGE, x, 3);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor is a window, and the window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = support.TestStation.simulatedWithWindowInDebug();

        settle();

        ui = (TrainControlUI) model.getGUI();

        session = ui.getAutonomySession();

        model.setNetworkCommState(false);

        model.newMM2Locomotive(TRAIN, 3451);

        theConfigurationIsLoaded();

        asFound = session.snapshotSetup();
    }

    /** A gesture's rebuild and its fold run only with a configuration loaded. */
    private static void theConfigurationIsLoaded() throws Exception
    {
        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            settle();
        }

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: no configuration is loaded");

        assertNotNull(railway(), "precondition: the scenario built no railway");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null) SwingUtilities.invokeAndWait(() -> model.deleteLoc(TRAIN));
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @BeforeMethod
    public void nothingInTheTimetable()
    {
        assertTrue(railway().getTimetable().isEmpty(), "precondition: the last claim left a timetable on the railway");
    }

    /** The setup and the railway put back, whatever the claim got to. */
    @AfterMethod(alwaysRun = true)
    public void putItBack() throws Exception
    {
        if (open != null) cancel();

        SwingUtilities.invokeAndWait(() ->
        {
            session.restoreSetup(asFound);

            ui.rebuildRunningLayoutFromSetup();
        });

        settle();
    }

    /**
     * The chain in one edit: the name one station gave up taken by another, and Cancel.  The entry the railway changed
     * comes back on the squares it ran over, under the names the setup opened with.
     *
     * MUTATION: in `restoreSetup`, leave `legsBySquare` false, so the legs are read by the memory as the edit began, and
     * this fails.
     */
    @Test
    public void testAChainOfRenamesInOneEditThenCancel() throws Exception
    {
        openTheEditor();

        theRailwayChangesItsTimetable();

        rename(MAIN, "Delta one");
        rename(WEST_END, "MainPlatform");

        cancel();

        assertEquals(session.getStore().getPointName(WEST_END), "WestEnd", "precondition: Cancel did not undo the rename");

        assertTheEntryRunsTheWholeWay("the editor's Cancel");

        theNextLoad();

        assertTheEntryRunsTheWholeWay("the load after the editor's Cancel");
    }

    /**
     * The chain across two edits: a name a station had in an earlier, saved edit of this session, given to another
     * station in the next edit, and Cancel.
     *
     * MUTATION: in `restoreSetup`, leave `legsBySquare` false, so the legs are read by the memory as the edit began, and
     * this fails.
     */
    @Test
    public void testAChainOfRenamesAcrossASavedEditThenCancel() throws Exception
    {
        openTheEditor();

        rename(MAIN, "Delta two");

        save();

        openTheEditor();

        theRailwayChangesItsTimetable();

        rename(WEST_END, "MainPlatform");

        cancel();

        assertEquals(session.getStore().getPointName(MAIN), "Delta two", "precondition: Cancel undid the edit saved earlier");
        assertEquals(session.getStore().getPointName(WEST_END), "WestEnd", "precondition: Cancel did not undo the rename");

        assertTheEntryRunsTheWholeWay("the editor's Cancel of the edit after a saved one");

        theNextLoad();

        assertTheEntryRunsTheWholeWay("the load after that Cancel");
    }

    /**
     * Cancel's own fold: the railway's timetable changes after the chain's last rebuild, in that build's names, so the
     * rebuild behind Cancel is the first to take it.
     *
     * MUTATION: in `inTheNamesNow`, return the railway's timetable as it is, and this fails.
     */
    @Test
    public void testAChainOfRenamesThenATimetableChangeThenCancel() throws Exception
    {
        openTheEditor();

        rename(MAIN, "Delta three");
        rename(WEST_END, "MainPlatform");

        theRailwayChangesItsTimetable();

        cancel();

        assertTheEntryRunsTheWholeWay("the fold behind the editor's Cancel");

        theNextLoad();

        assertTheEntryRunsTheWholeWay("the load after that Cancel");
    }

    /**
     * One rename giving a station the name another has, at the in-editor fold, with no Cancel: the railway's leg still
     * means the square its own Point stands on, and Save keeps it there.  The Name item refuses a name in use, so the
     * name is written behind that door, as a file or an import can bring one.
     *
     * MUTATION: in `inTheNamesNow`, return the railway's timetable as it is, and this fails.
     */
    @Test
    public void testARenameToANameAnotherStationHasThenSave() throws Exception
    {
        openTheEditor();

        theRailwayChangesItsTimetable();

        renamesInOneGesture(new TileKey[] {WEST_END}, new String[] {"MainPlatform"});

        assertTheEntryIsOnTheRailwayTheWholeWay("the in-editor fold of a rename to a name another station has");

        save();

        assertTheEntryRunsTheWholeWay("the editor's Save after that");

        theNextLoad();

        assertTheEntryRunsTheWholeWay("the load after that Save");
    }

    /**
     * The form the Name item lets through: the chain made before the railway has caught up with its first rename, as
     * when a run or a refused build holds the rebuild back.  The fold then meets a name that has moved to another square.
     *
     * MUTATION: in `inTheNamesNow`, return the railway's timetable as it is, and this fails.
     */
    @Test
    public void testAChainTheRailwayHasNotCaughtUpWithThenSave() throws Exception
    {
        openTheEditor();

        theRailwayChangesItsTimetable();

        renamesInOneGesture(new TileKey[] {MAIN, WEST_END}, new String[] {"Delta four", "MainPlatform"});

        assertTheEntryIsOnTheRailwayTheWholeWay("the in-editor fold of a chain the railway had not caught up with");

        save();

        assertTheEntryRunsTheWholeWay("the editor's Save after that");

        theNextLoad();

        assertTheEntryRunsTheWholeWay("the load after that Save");
    }

    /**
     * No chain: a plain rename and Cancel, as before.  The entry keeps its squares and the setup its names.
     *
     * MUTATION: in `putBackWhatTheRailwayChangedAlone`, leave the timetable out of what Cancel puts back, and this fails.
     */
    @Test
    public void testAPlainRenameThenCancel() throws Exception
    {
        openTheEditor();

        theRailwayChangesItsTimetable();

        rename(MAIN, "Delta five");

        cancel();

        assertEquals(session.getStore().getPointName(MAIN), "MainPlatform", "precondition: Cancel did not undo the rename");

        assertTheEntryRunsTheWholeWay("a plain rename and the editor's Cancel");

        assertEquals(lastLegOf(storedTimetable(session))[1], "MainPlatform", "the entry does not end under the name the"
            + " setup opened with");

        theNextLoad();

        assertTheEntryRunsTheWholeWay("the load after that Cancel");
    }

    /**
     * No chain: a plain rename and Save, as before.  The entry keeps its squares and takes the new name.
     *
     * MUTATION: in `carryTheNamesAcross`, leave the in-use configuration's legs uncarried, and this fails.
     */
    @Test
    public void testAPlainRenameThenSave() throws Exception
    {
        openTheEditor();

        theRailwayChangesItsTimetable();

        rename(MAIN, "Delta six");

        save();

        assertTheEntryRunsTheWholeWay("a plain rename and the editor's Save");

        assertEquals(lastLegOf(storedTimetable(session))[1], "Delta six", "the entry does not end under the new name");

        theNextLoad();

        assertTheEntryRunsTheWholeWay("the load after that Save");
    }

    /**
     * A leg with no road left is still refused, through the same chain: the arrow closes the entry's first leg, so the
     * load keeps the entry as written, to be refused when it is run, and neither drops it nor runs it some other way.
     *
     * MUTATION: in `TimetablePath.fromJSON`, throw where the path cannot be built, so the load drops the entry, and this
     * fails.
     */
    @Test
    public void testALegWithNoRoadLeftIsStillRefused() throws Exception
    {
        openTheEditor();

        theRailwayChangesItsTimetable();

        rename(MAIN, "Delta seven");
        rename(WEST_END, "MainPlatform");

        closeTheRoadBetween();

        save();

        theNextLoad();

        List<TimetablePath> table = railway().getTimetable();

        assertEquals(table.size(), 1, "the load dropped the entry whose road was closed, where it should keep it to refuse"
            + " it when it is run");

        assertNotNull(table.get(0).whyNotRunnable(), "the entry whose first leg has no road left runs: "
            + table.get(0).toJSON());

        String[] first = firstLegOf(storedTimetable(session));

        assertEquals(session.tileForPointName(first[0]), WEST_END, "the entry's first leg no longer starts on the square it"
            + " did: " + Arrays.toString(first));
    }

    /**
     * A shift of the track and Cancel: the track editor moves the squares and the carry's memory, so that memory no
     * longer names the squares of the setup Cancel restores, and reading the legs by it would write them under the
     * wrong names.  The fold is the one a railway newer than the setup makes while the track editor is open.
     *
     * MUTATION: in `restoreSetup`, drop `!squareMovedInTheEdit`, and this fails.
     */
    @Test
    public void testAShiftOfTheTrackThenCancel() throws Exception
    {
        final LayoutEditor[] editor = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new LayoutEditor(model.getLayout(PAGE), 30, ui, 0);

            editor[0].render();
        });

        open = editor[0];

        settle();

        final java.lang.reflect.Method newer = TrainControlUI.class.getDeclaredMethod("setupNewerThanTheRailway", boolean.class);

        newer.setAccessible(true);

        try
        {
            newer.invoke(ui, true);

            theRailwayChangesItsTimetable();

            SwingUtilities.invokeAndWait(() -> ui.captureRunningLayout());

            assertStoredTheWholeWay(session, "the fold, before the shift");

            final java.lang.reflect.Method drawGrid = LayoutEditor.class.getDeclaredMethod("drawGrid");
            final java.lang.reflect.Field hovered = LayoutEditor.class.getDeclaredField("lastHoveredX");

            drawGrid.setAccessible(true);
            hovered.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    drawGrid.invoke(editor[0]);

                    hovered.setInt(editor[0], 2);

                    editor[0].shiftRight();
                }
                catch (ReflectiveOperationException failed)
                {
                    throw new IllegalStateException(failed);
                }
            });

            settle();

            assertEquals(session.getStore().getPointName(new TileKey(PAGE, 8, 3)), "MainPlatform",
                "precondition: the shift did not carry the station");

            cancel();

            session = ui.getAutonomySession();

            assertEquals(session.getStore().getPointName(MAIN), "MainPlatform", "precondition: Cancel did not undo the shift");

            assertStoredTheWholeWay(session, "the shift and Cancel, in the setup");

            assertEquals(lastLegOf(storedTimetable(session))[1], "MainPlatform", "the entry does not end under the name the"
                + " setup opened with");

            newer.invoke(ui, false);

            theNextLoad();

            assertTheEntryRunsTheWholeWay("the load after the shift and Cancel");
        }
        finally
        {
            newer.invoke(ui, false);
        }
    }

    // ----------------------------------------------------------- what the claims do

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static org.json.JSONObject asFound;
    private static java.awt.Window open;

    private static Layout railway()
    {
        return model.getAutoLayoutIfLoaded() instanceof Layout ? (Layout) model.getAutoLayoutIfLoaded() : null;
    }

    private static void theRailwayChangesItsTimetable() throws Exception
    {
        final Locomotive train = model.getLocByName(TRAIN);

        SwingUtilities.invokeAndWait(() ->
        {
            Layout layout = railway();

            Edge in = null;
            Edge on = null;

            for (Edge first : layout.getEdges())
            {
                if (!on(first.getStart().getSquare(), WEST_END) || !on(first.getEnd().getSquare(), APPROACH)) continue;

                for (Edge second : layout.getEdges())
                {
                    if (second.getStart() == first.getEnd() && on(second.getEnd().getSquare(), MAIN))
                    {
                        in = first;
                        on = second;
                    }
                }
            }

            assertNotNull(in, "precondition: the railway has no way from WestEnd through Approach to MainPlatform");

            layout.setTimetable(new ArrayList<>(Arrays.asList(new TimetablePath(train, Arrays.asList(in, on), 1000L))));
        });
    }

    private static boolean on(String square, TileKey tile)
    {
        return tile.toString().equals(square);
    }

    private static void openTheEditor() throws Exception
    {
        SwingUtilities.invokeLater(() -> ui.openAutonomyEditorOnPage(PAGE));

        support.Components.waitFor(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window.isShowing() && window instanceof LayoutEditor) open = window;
            }

            return open != null;
        }, 60000);

        assertNotNull(open, "the autonomy editor did not open on " + PAGE);

        settle();
    }

    private static AutonomyEditorPanel panel()
    {
        return ((LayoutEditor) open).getAutonomyPanel();
    }

    /** A rename through the editor's Name item, whose dialog is answered, and the rebuild it asks for. */
    private static void rename(final TileKey square, final String name) throws Exception
    {
        final java.lang.reflect.Method prompt = AutonomyEditorPanel.class.getDeclaredMethod("promptName", TileKey.class);

        prompt.setAccessible(true);

        try (support.Answering answering = support.Answering.start(asked -> support.Answering.type(name)))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    prompt.invoke(panel(), square);
                }
                catch (ReflectiveOperationException failed)
                {
                    throw new IllegalStateException(failed);
                }
            });

            assertTrue(answering.awaitAsked(1, 15000), "the Name item asked nothing");
        }

        settle();

        assertEquals(session.getStore().getPointName(square), name, "precondition: the rename did not take");
    }

    /** Names written together, behind the Name item, with the one rebuild a gesture asks for after them. */
    private static void renamesInOneGesture(final TileKey[] squares, final String[] names) throws Exception
    {
        final java.lang.reflect.Method changed = AutonomyEditorPanel.class.getDeclaredMethod("setupChanged");

        changed.setAccessible(true);

        final AutonomyEditorPanel panel = panel();

        SwingUtilities.invokeAndWait(() ->
        {
            for (int i = 0; i < squares.length; i++) session.setPointName(squares[i], names[i]);

            try
            {
                changed.invoke(panel);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        settle();
    }

    /** The arrow through the track before Approach turned to run away from it, through the editor's own door. */
    private static void closeTheRoadBetween() throws Exception
    {
        final java.lang.reflect.Method arrow = AutonomyEditorPanel.class.getDeclaredMethod("setAllBranches", TileKey.class,
            Direction.class);

        arrow.setAccessible(true);

        final AutonomyEditorPanel panel = panel();

        final java.util.Set<String> before = edgesOf(session);

        for (Direction direction : Arrays.asList(Direction.TOWARD_A, Direction.TOWARD_B))
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    arrow.invoke(panel, BETWEEN, direction);
                }
                catch (ReflectiveOperationException failed)
                {
                    throw new IllegalStateException(failed);
                }
            });

            settle();

            if (!hasARoadFromWestEndToApproach()) return;
        }

        fail("precondition: neither arrow closes the road from WestEnd to Approach.  Edges were " + before + ", now "
            + edgesOf(session));
    }

    private static boolean hasARoadFromWestEndToApproach()
    {
        org.json.JSONArray edges = new org.json.JSONObject(session.buildConfiguration()).getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            if (WEST_END.equals(session.tileForPointName(edge.getString("start")))
                && APPROACH.equals(session.tileForPointName(edge.getString("end")))) return true;
        }

        return false;
    }

    private static java.util.Set<String> edgesOf(AutonomySession of)
    {
        java.util.Set<String> out = new java.util.TreeSet<>();

        org.json.JSONArray edges = new org.json.JSONObject(of.buildConfiguration()).getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            out.add(edges.getJSONObject(i).getString("start") + " -> " + edges.getJSONObject(i).getString("end"));
        }

        return out;
    }

    /** The editor's Cancel: the window closed, and its question answered yes. */
    private static void cancel() throws Exception
    {
        final java.awt.Window editor = open;

        open = null;

        // LATER, NOT AND-WAIT: closing asks whether to leave without saving, and a question asked inside invokeAndWait
        // waits for an answer this thread can then never give.
        try (support.Answering answering = support.Answering.start(asked -> support.Answering.pressAt(0)))
        {
            SwingUtilities.invokeLater(() -> editor.dispatchEvent(
                new java.awt.event.WindowEvent(editor, java.awt.event.WindowEvent.WINDOW_CLOSING)));

            untilItHasGone(editor);
        }

        settle();
    }

    /** The editor's Save button. */
    private static void save() throws Exception
    {
        final java.awt.Window editor = open;

        open = null;

        final java.lang.reflect.Field button = LayoutEditor.class.getDeclaredField("saveButton");

        button.setAccessible(true);

        final javax.swing.AbstractButton save = (javax.swing.AbstractButton) button.get(editor);

        SwingUtilities.invokeLater(save::doClick);

        untilItHasGone(editor);

        settle();
    }

    private static void untilItHasGone(java.awt.Window editor) throws Exception
    {
        support.Components.waitFor(() -> !editor.isDisplayable() && ui.whyLayoutCannotBeEdited() == null, 30000);

        assertFalse(editor.isDisplayable(), "the editor did not close");
    }

    /** A real load of the configuration, with the fold before it, as the Autonomy tab's Load makes it. */
    private static void theNextLoad() throws Exception
    {
        final String active = ui.getActiveDiagramConfiguration();

        SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

        settle();
    }

    /** Pumps the event queue and gives a coalesced rebuild and the cards after it their turn. */
    private static void settle() throws Exception
    {
        support.Components.settle();
    }

    // ----------------------------------------------------------- what the claims read

    private static void assertTheEntryIsOnTheRailwayTheWholeWay(String after) throws Exception
    {
        List<TimetablePath> table = railway().getTimetable();

        assertEquals(table.size(), 1, "after " + after + ", the railway has not the one entry it was given");

        TimetablePath entry = table.get(0);

        assertNull(entry.whyNotRunnable(), "after " + after + ", the railway refuses the entry, " + entry.toJSON());

        List<TileKey> squares = new ArrayList<>();

        squares.add(org.traincontrol.automationui.AutonomyCompanionStore.parseTileKey(
            entry.getPath().get(0).getStart().getSquare()));

        for (Edge leg : entry.getPath())
        {
            squares.add(org.traincontrol.automationui.AutonomyCompanionStore.parseTileKey(leg.getEnd().getSquare()));
        }

        assertEquals(squares, THE_WHOLE_WAY, "after " + after + ", the railway's entry runs on the wrong squares");
    }

    /** On the railway, in the setup, and in the file a next start reads, by the squares each names. */
    private static void assertTheEntryRunsTheWholeWay(String after) throws Exception
    {
        assertTheEntryIsOnTheRailwayTheWholeWay(after);

        assertStoredTheWholeWay(session, after + ", in the setup");

        AutonomySession reread = new AutonomySession(sandbox.getFolder());

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String name : model.getLayoutList()) pages.add(model.getLayout(name));

        reread.open(pages);

        assertStoredTheWholeWay(reread, after + ", in the file");
    }

    private static void assertStoredTheWholeWay(AutonomySession of, String after)
    {
        org.json.JSONArray table = storedTimetable(of);

        assertEquals(table.length(), 1, "after " + after + ", not the one entry: " + table);

        org.json.JSONArray path = table.getJSONObject(0).getJSONArray("path");

        List<TileKey> squares = new ArrayList<>();

        squares.add(of.tileForPointName(path.getJSONObject(0).getString("start")));

        for (int i = 0; i < path.length(); i++) squares.add(of.tileForPointName(path.getJSONObject(i).getString("end")));

        assertEquals(squares, THE_WHOLE_WAY, "after " + after + ", the entry names the wrong squares: " + path);

        for (int i = 0; i < path.length(); i++)
        {
            String leg = path.getJSONObject(i).getString("start") + " -> " + path.getJSONObject(i).getString("end");

            assertTrue(edgesOf(of).contains(leg), "after " + after + ", the leg " + leg + " is no edge of the build");
        }
    }

    private static org.json.JSONArray storedTimetable(AutonomySession of)
    {
        return new org.json.JSONArray(String.valueOf(of.getGlobal("timetable")));
    }

    private static String[] firstLegOf(org.json.JSONArray table)
    {
        org.json.JSONObject leg = table.getJSONObject(0).getJSONArray("path").getJSONObject(0);

        return new String[] {leg.getString("start"), leg.getString("end")};
    }

    private static String[] lastLegOf(org.json.JSONArray table)
    {
        org.json.JSONArray path = table.getJSONObject(0).getJSONArray("path");

        org.json.JSONObject leg = path.getJSONObject(path.length() - 1);

        return new String[] {leg.getString("start"), leg.getString("end")};
    }
}
