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
 * station east past Signal 3, up to PostDeparture, west along the top through MainLine and down to PreArrival.
 *
 * @author Adam
 */
public class testTheSampleLayoutSaysOnlyWhatTrainsCanDo
{
    private static support.LayoutSandbox sandbox = null;

    private static MarklinControlStation model;

    private static AutonomySession session;

    private static File unpacked;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        try
        {
            sandbox = support.LayoutSandbox.open(unpackTheSample());

            model = MarklinControlStation.init(null, true, false, false, false);

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

        deleteQuietly(unpacked);
    }

    /**
     * Its two stations build, with Signal 3 as the exit guard of both.
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

            assertEquals(station.getProtectingSignals(), Collections.singletonList("Signal 3"), "precondition: Signal 3 is"
                + " not " + name + "'s exit guard");
        }
    }

    /**
     * Signal 3 is not said to guard either station on the way in: trains pass it only leaving them, as each one's own
     * exit guard.
     *
     * MUTATION: count the way in from where it leaves the station before - past that station's exit guard - and this
     * fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testSignal3IsNotSaidToGuardAWayIn() throws Exception
    {
        // THE CASE IS THERE: each station's way out commands Signal 3, and its way in starts with the other's way out
        Layout layout = built();

        for (String name : new String[] {"Station A", "Station B"})
        {
            boolean commanded = false;

            for (Edge edge : layout.getEdges())
            {
                if (edge.getStart().getName().equals(name) && edge.getConfigCommands().containsKey("Signal 3")) commanded = true;
            }

            assertTrue(commanded, "precondition: no route out of " + name + " sets Signal 3, so nothing below is about it");
        }

        List<String> said = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (AutonomyChecks.EXIT_GUARD_ON_THE_WAY_IN.equals(finding.getMessageKey())) said.add(finding.toString());
        }

        assertTrue(said.isEmpty(), "Signal 3 is said to guard a way in, and trains pass it only going out: " + said);
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
        unpacked = Files.createTempDirectory("tc-ob361-sample").toFile();

        String zip = TrainControlUI.RESOURCE_PATH + TrainControlUI.DEMO_LAYOUT_ZIP;

        try (InputStream in = TrainControlUI.class.getResourceAsStream(zip))
        {
            assertNotNull(in, "the shipped " + zip + " is not on the class path");

            try (ZipInputStream entries = new ZipInputStream(in))
            {
                for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry())
                {
                    Path to = unpacked.toPath().resolve(entry.getName()).normalize();

                    assertTrue(to.startsWith(unpacked.toPath()), "the sample's zip names a file outside it: " + entry);

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

        assertTrue(new File(unpacked, "config/autonomy/setup.json").isFile(), "the sample has no autonomy setup");

        return unpacked;
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
