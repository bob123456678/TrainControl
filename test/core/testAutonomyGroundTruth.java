package core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The paths a real, hand-authored railway offers, pinned.
 *
 * Everything else about the graph is tested against fixtures built for the test - three tiles and a
 * switch, arranged to make one point.  Those catch what they were written for and nothing else, and the
 * faults this project keeps finding are the ones nobody thought to build a fixture for: a passing loop
 * that emits two edges of the same name, a station reachable from one end and not the other, a square
 * split into copies that then disagree about where they are.
 *
 * So this suite uses ground truth instead of a fixture.  test/autonomy.json is the operator's own
 * v2.8.1 configuration - 91 points, 121 edges, 44 stations, written by hand over a long time against a
 * railway that exists.  Which stations can reach which is a FACT about it, independent of how any of
 * this code works, and it is checked in beside the file it describes.
 *
 * What this catches: any change that quietly adds or removes a route.  A path-finder that starts
 * refusing a move it used to allow, or allowing one it used to refuse, changes this file - and a
 * changed line here is either a bug or a deliberate decision somebody has to write down.
 *
 * What it does not catch: whether the ANSWERS are right.  They are what this code said on the day they
 * were pinned; the value is that they stop changing by accident.  A wrong answer here is a wrong answer
 * that has to be argued with rather than one that slips through.
 *
 * @author Adam
 */
public class testAutonomyGroundTruth
{
    private static MarklinControlStation model;

    private static String config;

    /**
     * Where the expected answer lives.  Beside the configuration it describes, because the two are only
     * meaningful together - regenerating one without the other is how a pinned file stops being ground
     * truth and becomes a copy of the bug.
     */
    private static final String EXPECTED = "test/autonomy_formats/v2_8_1-station-paths.txt";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
        model.stop();

        // Resolved as a classpath resource, not a CWD-relative File.  test/autonomy.json used to be
        // opened with new File("test/autonomy.json"), which only exists when the process is launched
        // from the project root - anywhere else, this threw a SkipException and the whole class (1,399
        // pinned station pairs) went quietly green with nothing checked.  testAutonomySimulationSanity's
        // loadSanityFixture() resolves its fixture the same classpath-resource way; this follows suit,
        // and a missing resource is now a hard, loud failure instead of a silent skip.
        URL resource = testAutonomyGroundTruth.class.getResource("/autonomy.json");

        assertNotNull(resource,
            "autonomy.json was not found on the classpath - it is expected to be present as a "
            + "classpath resource (mirrored from test/autonomy.json) regardless of the working "
            + "directory the test process was launched from");

        try (InputStream in = resource.openStream())
        {
            config = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Every station pair the graph can route between, exactly as it could when this was pinned.
     */
    @Test
    public void testTheStationPathsHaveNotChanged() throws Exception
    {
        List<String> reachable = reachableStationPairs();

        File expected = new File(EXPECTED);

        if (!expected.exists())
        {
            Files.write(expected.toPath(), String.join("\n", reachable).getBytes(StandardCharsets.UTF_8));

            fail("no pinned answer existed, so one was written to " + EXPECTED
                + " - read it, satisfy yourself that it describes the railway, and commit it");
        }

        List<String> pinned = Files.readAllLines(expected.toPath(), StandardCharsets.UTF_8);

        pinned.removeIf(line -> line.trim().isEmpty() || line.startsWith("#"));

        // Reported as the difference rather than as two lists.  91 points make a long file, and "these
        // three routes appeared" is the sentence somebody can act on.
        List<String> appeared = new ArrayList<>(reachable);
        appeared.removeAll(pinned);

        List<String> vanished = new ArrayList<>(pinned);
        vanished.removeAll(reachable);

        assertTrue(appeared.isEmpty() && vanished.isEmpty(),
            "the routes this railway offers have changed."
            + "\n  now possible and was not: " + appeared
            + "\n  was possible and is not: " + vanished
            + "\nIf that is deliberate, regenerate " + EXPECTED + " and say why in the commit.");
    }

    /**
     * The count on its own, so a wholesale collapse is reported as one line rather than as hundreds.
     *
     * A graph that loses its edges fails the test above with an unreadable list; this one says what
     * happened.  Both are wanted: the difference tells you which route, the count tells you it was not
     * a route, it was the railway.
     */
    @Test
    public void testTheRailwayStillHasItsStationsAndEdges() throws Exception
    {
        Layout layout = load();

        int stations = 0;

        for (Point point : layout.getPoints())
        {
            if (point.isDestination()) stations++;
        }

        assertEquals(stations, 44, "the configuration describes 44 stations");

        assertEquals(layout.getEdges().size(), 121, "the configuration describes 121 edges");

        assertEquals(layout.getPoints().size(), 91, "the configuration describes 91 points");
    }

    /**
     * Loads the configuration, refusing to let anything reach the track.
     */
    private static Layout load() throws Exception
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration produced no graph");

        assertTrue(layout.isValid(),
            "the configuration must parse against this database - " + Layout.getLastError());

        layout.setSimulate(true);

        assertTrue(layout.isSimulate(), "simulation must be on before anything else happens");

        return layout;
    }

    /**
     * Which station can reach which, as sorted "from -> to" lines.
     *
     * bfs rather than getPossiblePaths, deliberately: getPossiblePaths asks what a particular
     * LOCOMOTIVE may do from where it is standing, which brings in occupancy, exclusions and lengths.
     * This is a question about the railway rather than about a train, and it has to stay answerable
     * when nothing is placed.
     */
    private static List<String> reachableStationPairs() throws Exception
    {
        Layout layout = load();

        List<Point> stations = new ArrayList<>();

        for (Point point : layout.getPoints())
        {
            if (point.isDestination()) stations.add(point);
        }

        // Nothing standing anywhere, so occupancy cannot make a route look impossible
        for (Point point : layout.getPoints())
        {
            point.setLocomotive(null);
        }

        List<String> pairs = new ArrayList<>();

        for (Point from : stations)
        {
            for (Point to : stations)
            {
                if (from == to) continue;

                List<Edge> path = layout.bfs(from, to, null);

                if (path != null && !path.isEmpty())
                {
                    pairs.add(from.getName() + " -> " + to.getName());
                }
            }
        }

        Collections.sort(pairs);

        return pairs;
    }
}
