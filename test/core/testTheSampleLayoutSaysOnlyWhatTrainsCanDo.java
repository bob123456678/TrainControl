package core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The sample layout Create New Layout unpacks says nothing about moves no train can make (OB-361).
 *
 * Adam: *"In the new sample layout, there is a note saying Signal 3 guards station a (and b) on the way in, even though
 * trains can't arrive that way because switch 1 only allows one direction.  Signal 3 is set as the exit guard. Also, in
 * the viewer, there are excessive red arrows shown to the east of feedback 4, even though these aren't visible in the
 * editor. See the screenshot.  There is also a note about trains reaching MainLine from the west could not go on.
 * MainLine isn't a station, and it is impossible for trains to arrive from the west anyway, so both these conditions
 * should disqualify the notice."*
 *
 * The shipped `sample_layout.zip`, unpacked as Create New Layout unpacks it, in a sandbox.  Trains run from either
 * station east past their exit guard (Signal 3 when Adam wrote the above; the square at 9,5, renumbered since), up to
 * PostDeparture, west along the top through MainLine and down to PreArrival.
 *
 * @author Adam
 */
public class testTheSampleLayoutSaysOnlyWhatTrainsCanDo
{
    private static support.LayoutSandbox sandbox = null;

    private static MarklinControlStation model;

    private static AutonomySession session;

    /** Every copy of the sample unpacked here, deleted afterwards. */
    private static final List<File> unpacked = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        try
        {
            sandbox = support.LayoutSandbox.open(unpackTheSample());

            model = support.TestStationAddress.withNoStation(
                () -> MarklinControlStation.init(null, true, false, false, false));

            session = new AutonomySession(sandbox.getFolder());

            session.open(support.LayoutSandbox.wiredPages(model));
        }
        catch (Exception | Error failed)
        {
            if (sandbox != null) sandbox.close();

            sandbox = null;

            throw failed;
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();

        for (File folder : unpacked) deleteQuietly(folder);
    }

    /**
     * Its two stations build, with one exit guard for both (the signal at 9,5).
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheTwoStationsBuild() throws Exception
    {
        Layout layout = built();

        for (String name : new String[] {"Station A", "Station B"})
        {
            Point station = layout.getPoint(name);

            assertNotNull(station, name + " is not on the built railway: " + layout.getPoints());

            assertTrue(station.isDestination(), name + " is built as somewhere no train is sent");

            assertEquals(station.getProtectingSignals(), Collections.singletonList(exitGuard(layout)), "precondition: "
                + name + " has not the one exit guard both stations share");
        }
    }

    /**
     * The stations' exit guard (Adam's Signal 3) is not said to guard either station on the way in: trains pass it only
     * leaving them, as each one's own exit guard.
     *
     * MUTATION: count the way in from where it leaves the station before - past that station's exit guard - and this
     * fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testSignal3IsNotSaidToGuardAWayIn() throws Exception
    {
        // THE CASE IS THERE: each station's way out commands the guard, and its way in starts with the other's way out
        Layout layout = built();

        String guard = exitGuard(layout);

        for (String name : new String[] {"Station A", "Station B"})
        {
            boolean commanded = false;

            for (Edge edge : layout.getEdges())
            {
                if (edge.getStart().getName().equals(name) && edge.getConfigCommands().containsKey(guard)) commanded = true;
            }

            assertTrue(commanded, "precondition: no route out of " + name + " sets " + guard + ", so nothing below is about"
                + " it");
        }

        List<String> said = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (AutonomyChecks.EXIT_GUARD_ON_THE_WAY_IN.equals(finding.getMessageKey())) said.add(finding.toString());
        }

        assertTrue(said.isEmpty(), guard + " is said to guard a way in, and trains pass it only going out: " + said);
    }

    /** The one exit guard Station A has, which Station B shares: the signal at 9,5. */
    private static String exitGuard(Layout layout)
    {
        List<String> guards = layout.getPoint("Station A").getProtectingSignals();

        assertEquals(guards.size(), 1, "precondition: Station A has not one exit guard: " + guards);

        return guards.get(0);
    }

    /**
     * No arrival is said to be trapped: MainLine is no station, and no train reaches it from the west.
     *
     * MUTATION: give the notice at a square that is no station, or for a side no train arrives by, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testNoArrivalIsSaidToBeTrapped() throws Exception
    {
        // THE CASE IS THERE: MainLine's copy for trains from the west has no way out
        Point fromTheWest = built().getPoint("MainLine (eastbound)");

        assertNotNull(fromTheWest, "precondition: MainLine has no copy for trains arriving from the west");

        assertTrue(built().getNeighbors(fromTheWest).isEmpty(), "precondition: a train arriving at MainLine from the west"
            + " can go on, so nothing below is about one that cannot");

        List<String> said = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (AutonomyChecks.isArrivalTrapped(finding.getMessageKey())) said.add(finding.toString());
        }

        assertTrue(said.isEmpty(), "an arrival no train can make, at a square that is no station, is said to be trapped: "
            + said);
    }

    /**
     * The track diagram draws on every square the arrows the autonomy editor draws there: east of MainLine, one arrow for
     * the run up to PostDeparture, saying it runs one way.
     *
     * MUTATION: draw a run's squares that say otherwise than its first on the track diagram, or draw a run's first square
     * with its own direction rather than the run's, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testTheDiagramDrawsTheEditorsArrows() throws Exception
    {
        org.traincontrol.gui.AutonomyEditorPanel panel = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> { });

        panel.refresh();

        java.util.Map<TileKey, TileAnnotation> diagram =
            session.staticAnnotations(session.getGraph().getTiles().keySet(), true, false);

        List<String> differ = new ArrayList<>();

        for (TileKey tile : session.getGraph().getTiles().keySet())
        {
            List<TileAnnotation.Mark> drawn = diagram.get(tile) == null
                ? Collections.<TileAnnotation.Mark>emptyList() : diagram.get(tile).getMarks();

            List<TileAnnotation.Mark> edited = panel.annotationFor(tile).getMarks();

            if (!drawn.equals(edited)) differ.add(tile + ": diagram " + drawn + ", editor " + edited);
        }

        assertTrue(differ.isEmpty(), "the track diagram draws other arrows than the editor: " + differ);

        // EAST OF MAINLINE: one square of the run draws, and says one way
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        int drawing = 0;

        for (TileKey tile : new TileKey[] {square(7, 1), square(8, 1), square(9, 1), square(10, 1), square(10, 2),
            square(10, 3)})
        {
            TileAnnotation annotation = diagram.get(tile);

            if (annotation == null || annotation.getMarks().isEmpty()) continue;

            drawing++;

            marks.addAll(annotation.getMarks());
        }

        assertEquals(drawing, 1, "the run from MainLine to PostDeparture is not drawn once: " + marks);

        assertTrue(marks.size() == 1 && marks.get(0).getDirection() != Direction.BOTH, "the run from MainLine to"
            + " PostDeparture, which trains run one way, is not drawn one way: " + marks);
    }

    /**
     * The run east of MainLine is stored one way on all its squares, 10,3 included, and opening the sample has nothing to
     * bring into line (OB-361; Adam, on OB-321: *"Any disagreement should be corrected so that the viewer displays the
     * source of truth"*).  The sample shipped with 10,3 both ways until Adam's update of 2026-10-18; the correction itself
     * is claimed in `testAutonomyDiagramSession.testARunThatSaysTwoThingsIsBroughToWhatATrainFinds`.
     *
     * MUTATION: ship the sample with 10,3 both ways again, or have the opening bring a consistent run into line, and this
     * fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheRunEastOfMainLineIsStoredOneWay() throws Exception
    {
        File folder = unpackTheSample();

        AutonomySession first = openOn(folder);

        assertEquals(first.getStore().getTileDirection(square(10, 3), new RouteId(0, 0)), Direction.TOWARD_A, "10,3 is"
            + " not stored one way, toward the north, as the rest of its run is");

        for (TileKey square : first.runs().get(square(10, 3)).getTiles())
        {
            assertNotNull(first.getStore().getTileDirection(square, new RouteId(0, 0)), square + " of the run is not"
                + " stored one way");
        }

        assertTrue(first.getSquaresBroughtToTheirArrows().isEmpty(), "opening the sample brought a run into line, so it"
            + " ships disagreeing with itself: " + first.getSquaresBroughtToTheirArrows());

        AutonomySession again = openOn(folder);

        assertTrue(again.getSquaresBroughtToTheirArrows().isEmpty(), "opening the sample again brought a run into line: "
            + again.getSquaresBroughtToTheirArrows());

        assertEquals(again.getStore().getTileDirection(square(10, 3), new RouteId(0, 0)), Direction.TOWARD_A, "10,3 was"
            + " not saved one way");
    }

    /**
     * A click on one arrow changes that arrow alone: every other square's stored directions, and what it draws on the
     * track diagram and in the editor, are as they were (Adam, on OB-361's correction: *"if I click on an arrow, it should
     * only affect itself, not other tiles with arrows."*).  Every square that draws an arrow is clicked in turn; a run's
     * arrow is the whole run, whose squares are its arrow's.
     *
     * MUTATION: let a click, or the correction that follows it, touch another run, and this fails.
     *
     * @throws Exception from the panel
     */
    @Test
    public void testAClickOnOneArrowChangesThatArrowAlone() throws Exception
    {
        AutonomySession fresh = openOn(unpackTheSample());

        final org.traincontrol.gui.AutonomyEditorPanel[] panel = new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(fresh, null, () -> { }));

        int clicked = 0;

        for (TileKey arrow : new ArrayList<>(fresh.getGraph().getTiles().keySet()))
        {
            if (fresh.directionMarks(arrow).isEmpty() || fresh.followsItsRun(arrow)) continue;

            TileKey leader = fresh.runLeaders().get(arrow);

            java.util.Set<TileKey> itself = leader == null ? Collections.singleton(arrow)
                : new java.util.HashSet<>(fresh.runs().get(leader).getTiles());

            java.util.Map<TileKey, String> before = everyArrow(fresh, panel[0]);

            click(panel[0], arrow);

            java.util.Map<TileKey, String> after = everyArrow(fresh, panel[0]);

            boolean changed = false;

            for (TileKey square : before.keySet())
            {
                if (itself.contains(square))
                {
                    changed |= !before.get(square).equals(after.get(square));

                    continue;
                }

                assertEquals(after.get(square), before.get(square), "a click on the arrow at " + arrow + " changed "
                    + square + ", another arrow's square");
            }

            assertTrue(changed, "precondition: a click on the arrow at " + arrow + " changed nothing of its own");

            clicked++;
        }

        assertTrue(clicked >= 5, "precondition: only " + clicked + " arrows were clicked on the sample");
    }

    /**
     * The One-Way tool sets the runs of the two squares clicked, as well as the track between them, and leaves nothing for
     * the correction to bring into line (OB-361).  It used to set only the squares between: clicked from 10,3 to 5,1 on
     * the sample with no directions, it left 10,3 both ways in a run one way - the shipped sample exactly - and 5,1's run,
     * west of MainLine, both ways from end to end.
     *
     * MUTATION: leave the clicked squares' runs out again, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testTheOneWayToolSetsTheRunsOfTheSquaresClicked() throws Exception
    {
        File folder = unpackTheSample();

        File setup = new File(folder, "config/autonomy/setup.json");

        org.json.JSONObject written = new org.json.JSONObject(new String(Files.readAllBytes(setup.toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        written.put("tileDirections", new org.json.JSONObject());

        Files.write(setup.toPath(), written.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        AutonomySession fresh = openOn(folder);

        fresh.takeSquaresBroughtToTheirArrows();

        assertTrue(fresh.setOneWayRun(square(10, 3), square(5, 1)) > 0, "precondition: no track from 10,3 to 5,1");

        for (TileKey clicked : new TileKey[] {square(10, 3), square(5, 1)})
        {
            TileKey leader = fresh.runLeaders().get(clicked);

            assertNotNull(leader, "precondition: " + clicked + " is no plain track");

            for (TileKey square : fresh.runs().get(leader).getTiles())
            {
                assertNotNull(fresh.getStore().getTileDirection(square, new RouteId(0, 0)), square + ", on the run of "
                    + clicked + " clicked with the One-Way tool, was left both ways");
            }
        }

        assertTrue(fresh.takeSquaresBroughtToTheirArrows().isEmpty(), "the One-Way tool left a run for the correction to"
            + " bring into line");
    }

    /**
     * A square of a run built over in the track diagram editor - a straight dropped on it, as the sample's 10,3 was -
     * keeps its run's direction, and nothing is said: it is the edit, not a setup that disagreed (OB-361).  The editor's
     * drop clears what was set on the square with `forgetTiles`, after the tile is placed and turned (OB-360).
     *
     * MUTATION: say it as a correction, or leave the square both ways, and this fails.
     *
     * @throws Exception from the session
     */
    @Test
    public void testASquareBuiltOverKeepsItsRunsDirection() throws Exception
    {
        AutonomySession fresh = openOn(unpackTheSample());

        fresh.takeSquaresBroughtToTheirArrows();

        assertEquals(fresh.getStore().getTileDirection(square(10, 3), new RouteId(0, 0)), Direction.TOWARD_A,
            "precondition: 10,3 is not one way as the sample opens");

        fresh.forgetTiles(Collections.singletonList(square(10, 3)));

        assertEquals(fresh.getStore().getTileDirection(square(10, 3), new RouteId(0, 0)), Direction.TOWARD_A, "10,3,"
            + " built over, did not keep the direction of the run it is part of");

        assertTrue(fresh.takeSquaresBroughtToTheirArrows().isEmpty(), "a square built over taking its run's direction"
            + " was said as a correction");
    }

    /** Each square's stored directions and the marks it draws on the track diagram and in the editor, in one line. */
    private static java.util.Map<TileKey, String> everyArrow(AutonomySession on,
        org.traincontrol.gui.AutonomyEditorPanel panel)
    {
        java.util.Map<TileKey, String> out = new java.util.LinkedHashMap<>();

        java.util.Map<TileKey, TileAnnotation> diagram = on.staticAnnotations(on.getGraph().getTiles().keySet(), true, true);

        for (TileKey square : on.getGraph().getTiles().keySet())
        {
            StringBuilder line = new StringBuilder();

            for (RouteId road : on.getGraph().getRoutes(square).keySet())
            {
                line.append(road).append('=').append(on.getStore().getTileDirection(square, road)).append(' ');
            }

            line.append("| diagram ").append(diagram.get(square) == null ? "[]" : diagram.get(square).getMarks())
                .append(" | editor ").append(panel.annotationFor(square).getMarks());

            out.put(square, line.toString());
        }

        return out;
    }

    /** A click on a square in the editor, as the mouse gives it. */
    private static void click(final org.traincontrol.gui.AutonomyEditorPanel panel, final TileKey square) throws Exception
    {
        final java.lang.reflect.Method cycle =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("cycle", TileKey.class);

        cycle.setAccessible(true);

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                cycle.invoke(panel, square);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });
    }

    /** A session on a folder of the sample, opened on the model's pages. */
    private static AutonomySession openOn(File folder) throws Exception
    {
        AutonomySession opened = new AutonomySession(folder);

        opened.open(support.LayoutSandbox.wiredPages(model));

        return opened;
    }

    /**
     * Every sensor on the sample has an address of its own, and the setup check says nothing about a shared one (FR-139).
     * Station A and PostDeparture shared address 1 until Adam's update of 2026-10-18; the warning itself is claimed in
     * `testAutonomyDiagramSession.testASensorAddressOnTwoSquaresIsSaidOnce`.
     *
     * MUTATION: ship the sample with two sensors on one address again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testNoSensorAddressIsShared() throws Exception
    {
        List<AutonomyChecks.Finding> shared = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (AutonomyChecks.SENSOR_ADDRESS_SHARED.equals(finding.getMessageKey())) shared.add(finding);
        }

        assertTrue(shared.isEmpty(), "the sample has two sensors on one address: " + shared);
    }

    /** A square of the sample's only page. */
    private static TileKey square(int x, int y)
    {
        return new TileKey("Page 1", x, y);
    }

    /** The setup, built and parsed by the railway. */
    private static Layout built() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the sample's setup did not build");

        assertTrue(layout.isValid(), "the sample's setup builds invalid: " + Layout.getLastError());

        return layout;
    }

    /**
     * The shipped sample, unpacked into a folder of its own as Create New Layout unpacks it.
     *
     * @return the folder
     * @throws IOException if the zip cannot be read
     */
    private static File unpackTheSample() throws IOException
    {
        File into = Files.createTempDirectory("tc-ob361-sample").toFile();

        unpacked.add(into);

        String zip = TrainControlUI.RESOURCE_PATH + TrainControlUI.DEMO_LAYOUT_ZIP;

        try (InputStream in = TrainControlUI.class.getResourceAsStream(zip))
        {
            assertNotNull(in, "the shipped " + zip + " is not on the class path");

            try (ZipInputStream entries = new ZipInputStream(in))
            {
                for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry())
                {
                    Path to = into.toPath().resolve(entry.getName()).normalize();

                    assertTrue(to.startsWith(into.toPath()), "the sample's zip names a file outside it: " + entry);

                    if (entry.isDirectory())
                    {
                        Files.createDirectories(to);

                        continue;
                    }

                    Files.createDirectories(to.getParent());

                    Files.copy(entries, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        assertTrue(new File(into, "config/autonomy/setup.json").isFile(), "the sample has no autonomy setup");

        return into;
    }

    /** Deletes a folder and everything in it, quietly. */
    private static void deleteQuietly(File folder)
    {
        if (folder == null) return;

        File[] inside = folder.listFiles();

        for (int i = 0; inside != null && i < inside.length; i++) deleteQuietly(inside[i]);

        folder.delete();
    }
}
