package core;

import examples.BuildAGraph;
import examples.DriveALocomotive;
import examples.KnowYourStation;
import examples.ReactToEvents;
import examples.RunTrainsOnAGraph;
import examples.SimulatedStation;
import examples.SwitchesSignalsAndSensors;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * The API examples in test/examples run, against a station that is not there (FR-138).
 *
 * The examples teach the public Java API (AutomationAPI.md points to them), and a teaching example that no longer
 * compiles or no longer does what its words say is worse than none.  They compile with the tests, so a renamed method
 * breaks the build; this class runs each one on a simulated station and reads back what it claims to have done, so a
 * method that still exists but behaves differently fails here.
 *
 * Every example takes its station as a parameter, and none connects to a real Central Station: the only call to
 * `init` is in `SimulatedStation`, and it asks for a simulation.
 *
 * One station per JVM, because it binds a fixed network port, so the examples share one and use names, addresses and
 * sensors that do not meet.
 *
 * MUTATION: change a call in an example so it does something else (turn where it says straight, say), and the line it
 * reports no longer matches.
 */
public class testTheApiExamplesRun
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation station;

    /** The example classes, by source file. */
    private static final String[] EXAMPLES =
    {
        "KnowYourStation", "DriveALocomotive", "SwitchesSignalsAndSensors", "BuildAGraph", "RunTrainsOnAGraph",
        "ReactToEvents"
    };

    /**
     * Public methods of Layout, Edge, Locomotive and MarklinControlStation that nothing in the program calls and that
     * are kept for the people who write against the API (the list is in docs/refactor-3.0.3.md section 3).  Each must
     * be shown in an example, by the name of the method followed by an opening bracket.
     */
    private static final String[] KEPT_FOR_THE_API =
    {
        "hasOnlyReversingIncoming", "hasOnlyInactiveIncoming", "hasOnlyInactiveNeighbors", "hasOnlyReversingNeighbors",
        "getPointById", "getEdgeById", "getPathLength", "waitForS88Reached", "getPointsInActivePath",
        "clearConfigCommand", "clearLockEdges", "goForward", "goBackward", "getNumPaths", "getTypes", "getLastLatency",
        "getMarklinLocomotives"
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The layout preference points at a copy, because init reads it.
        sandbox = support.LayoutSandbox.open();

        station = support.TestStationAddress.withNoStation(() -> SimulatedStation.open());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Whether a report has a line that says exactly this, with a message that prints the report.
     */
    private static void assertSays(List<String> found, String line)
    {
        assertTrue(found.contains(line), "the example did not report \"" + line + "\"; it reported:\n"
            + String.join("\n", found));
    }

    /**
     * Whether a report has a line that begins like this.
     */
    private static void assertHasLine(List<String> found, String start)
    {
        for (String line : found)
        {
            if (line.startsWith(start)) return;
        }

        fail("the example reported no line beginning \"" + start + "\"; it reported:\n"
            + String.join("\n", found));
    }

    /**
     * The station tells what it holds, and a power switch it is asked to make is confirmed by the answer.
     *
     * @throws Exception from the example
     */
    @Test
    public void testKnowYourStation() throws Exception
    {
        station.newMM2Locomotive("Known locomotive", 63);

        List<String> found = KnowYourStation.run(station);

        assertSays(found, "This station is a simulation");

        assertSays(found, "Locomotive Known locomotive: address 63, MM2, 5 functions");

        assertSays(found, "Power off, confirmed by the station: true (now false)");

        assertSays(found, "Power on, confirmed by the station: true (now true)");

        assertTrue(station.getLastLatency() >= 0, "a latency is a number of milliseconds, never negative");

        station.deleteLoc("Known locomotive");
    }

    /**
     * Every direction, speed and function the example sets reads back as set, and it leaves the locomotive standing.
     *
     * @throws Exception from the example
     */
    @Test
    public void testDriveALocomotive() throws Exception
    {
        List<String> found = DriveALocomotive.run(station);

        assertSays(found, "after goBackward: going backward true, going forward false");

        assertSays(found, "after goForward: going backward false, going forward true");

        assertSays(found, "after turning round twice: going forward true");

        assertSays(found, "after setSpeed(40): speed 40");

        assertSays(found, "after stop: speed 0");

        assertSays(found, "light true, F1 false, F2 true of 5 functions");

        assertSays(found, "function 5 exists: false");

        assertSays(found, "after a pulse on F3: false");

        assertSays(found, "after lightsOff: light false");

        assertSays(found, "after functionsOff: F2 false");

        assertSays(found, "after applying the preferred setup: F2 true, speed 30");

        assertHasLine(found, "journeys run: 0, total run time: ");

        MarklinLocomotive loco = station.getLocByName(DriveALocomotive.NAME);

        assertEquals(loco.getSpeed(), 0, "the example leaves the locomotive standing");

        assertFalse(loco.getF(2), "and with its functions off");
    }

    /**
     * Switches and signals take the setting they are given, the sensor is read as set, and the stored route throws what
     * it holds and is gone again at the end.
     *
     * @throws Exception from the example
     */
    @Test(timeOut = 120000)
    public void testSwitchesSignalsAndSensors() throws Exception
    {
        boolean thereBefore = station.getAccessoryByAddressIfPresent(299, org.traincontrol.base.Accessory.accessoryDecoderType.MM2) != null;

        List<String> found = SwitchesSignalsAndSensors.run(station);

        assertSays(found, "after turn: turned true, straight false");

        assertSays(found, "after straight: turned false, straight true");

        assertSays(found, "after doSwitch: turned true");

        assertSays(found, "after red: red true, green false");

        assertSays(found, "after green: red false, green true");

        assertSays(found, "switch 5 turned: false");

        assertSays(found, "switch 5 found by address: Switch 5");

        assertHasLine(found, "switch 299 exists: " + thereBefore + ", its state ");

        assertSays(found, "sensor 900 known: true");

        assertSays(found, "sensor 900 occupied: true");

        assertSays(found, "sensor 900 occupied now: false");

        assertSays(found, "routes: true");

        assertSays(found, "after the route: switch 5 turned true, signal 6 red true");

        assertSays(found, "route still stored after deleteRoute: false");

        assertEquals(station.getAccessoryByAddressIfPresent(299, org.traincontrol.base.Accessory.accessoryDecoderType.MM2) != null,
            thereBefore, "asking about an accessory that is not there must not make it up");
    }

    /**
     * The graph answers what it was built to answer, a changed setting or lock is gone, and a saved graph loads again
     * the same.
     *
     * @throws Exception from the example
     */
    @Test
    public void testBuildAGraph() throws Exception
    {
        List<String> found = BuildAGraph.run(station);

        assertSays(found, "points: 4, edges: 5");

        assertSays(found, "point by id: Station 1");

        assertHasLine(found, "edge by name: Station 1");

        assertHasLine(found, "edge by id: Station 1");

        assertSays(found, "edge to an unknown point: null");

        assertSays(found, "leaving Pre Arrival: 2, arriving at Pre Arrival: 1");

        assertSays(found, "every way into Pre Arrival is from a reversing point: false");

        assertSays(found, "after making Main Track reversing: true, and every neighbour of Station 1 reversing: true");

        assertSays(found, "every way into Pre Arrival is from an inactive point: false");

        assertSays(found, "after switching Main Track off: true, and every neighbour of Station 1 inactive: true");

        assertHasLine(found, "shortest way: [Station 1 -> ");

        assertSays(found, "its length: 260");

        assertSays(found, "settings on Pre Arrival to Station 1: 2");

        assertSays(found, "after clearing Signal 1: 1");

        assertSays(found, "locks held by the way out of Station 1: 1");

        assertSays(found, "after removing one: 0");

        assertSays(found, "after clearing them all: 0");

        assertSays(found, "reloaded: valid true, points 4, edges 5");

        assertSays(found, "locks on the way out of Station 1 after reloading: 1");
    }

    /**
     * A train set off by the layout is seen leaving, passing its sensor and arriving, and stands at its destination.
     *
     * @throws Exception from the example
     */
    @Test(timeOut = 120000)
    public void testRunTrainsOnAGraph() throws Exception
    {
        List<String> found = RunTrainsOnAGraph.run(station);

        assertSays(found, "running: true");

        assertSays(found, "set off from Platform A for Platform B, over [Crossing, Platform A, Platform B]");

        assertSays(found, "passed sensor 922, sensors reached so far: [Crossing, Platform A], "
            + "last point reached: Platform A");

        assertSays(found, "moved on from sensor 922");

        assertSays(found, "arrived at Platform B, points reached: [Crossing, Platform A, Platform B]");

        assertSays(found, "the train arrived: true");

        assertSays(found, "still running after stop: false");

        assertSays(found, "the train stands at: Platform B");

        assertSays(found, "journeys run by the train: 1");

        assertSays(found, "the layout's callback heard of changes: true");
    }

    /**
     * A sensor makes a thread of the example's own act, a sensor starts the stored route, and the commands are counted.
     *
     * @throws Exception from the example
     */
    @Test(timeOut = 120000)
    public void testReactToEvents() throws Exception
    {
        List<String> found = ReactToEvents.run(station);

        assertSays(found, "sensor 930 known: true");

        assertSays(found, "a train passed sensor 930, switch 7 turned");

        assertSays(found, "a train came back to sensor 930, switch 7 straight again");

        assertSays(found, "the sensor went clear and occupied once more");

        assertSays(found, "switch 8 turned by the route: true");

        assertSays(found, "commands sent while this ran: true");

        assertNull(station.getRoute(ReactToEvents.ROUTE), "the example removes the route it stored");

        assertTrue(station.getAccessoryByName("Switch 7").isStraight(),
            "switch 7 was turned and then set straight again");
    }

    /**
     * Each example takes its station as a parameter, has a main to run by itself, and none opens a real station: the
     * only init in the folder is the simulation.
     *
     * @throws Exception from reading the sources
     */
    @Test
    public void testNoExampleReachesForARealStation() throws Exception
    {
        for (String name : EXAMPLES)
        {
            Class<?> example = Class.forName("examples." + name);

            Method run = null;

            for (Method each : example.getDeclaredMethods())
            {
                if (each.getName().equals("run")) run = each;
            }

            assertNotNull(run, name + " has no run method");

            assertTrue(Modifier.isPublic(run.getModifiers()) && Modifier.isStatic(run.getModifiers()),
                name + ".run must be public and static, so a program can call it with its own station");

            assertEquals(Arrays.asList(run.getParameterTypes()), Arrays.<Class<?>>asList(MarklinControlStation.class),
                name + ".run takes the station and nothing else");

            example.getDeclaredMethod("main", String[].class);
        }

        int inits = 0;

        for (File source : sources())
        {
            String code = code(source);

            Matcher matcher = Pattern.compile("\\binit\\s*\\(([^)]*)\\)").matcher(code);

            while (matcher.find())
            {
                inits++;

                assertEquals(source.getName(), "SimulatedStation.java",
                    source.getName() + " opens a station itself; only SimulatedStation may, and it simulates");

                assertTrue(matcher.group(1).replace(" ", "").startsWith("null,true,"),
                    "the station SimulatedStation opens must be a simulation: init(" + matcher.group(1) + ")");
            }
        }

        assertEquals(inits, 1, "exactly one init in the examples, the simulated one");
    }

    /**
     * The methods kept for the people who use the API are shown in the examples.
     *
     * A method nothing in the program calls survives only because someone may use it; this keeps the promise made when
     * it was kept (FR-138) from lapsing when an example is edited.
     *
     * @throws Exception from reading the sources
     */
    @Test
    public void testEveryKeptMethodIsShown() throws Exception
    {
        StringBuilder all = new StringBuilder();

        for (File source : sources()) all.append(code(source)).append('\n');

        for (String method : KEPT_FOR_THE_API)
        {
            assertTrue(Pattern.compile("\\." + method + "\\s*\\(").matcher(all).find(),
                "no example calls " + method + "; it is kept for API users and must be shown (FR-138)");
        }
    }

    /**
     * @return the example sources
     */
    private static File[] sources()
    {
        File[] files = new File("test/examples").listFiles((dir, name) -> name.endsWith(".java"));

        assertNotNull(files, "the examples folder is not where this expects it");

        assertEquals(files.length, EXAMPLES.length + 1, "an example is not listed in this test, or one is missing");

        return files;
    }

    /**
     * @param source a Java file
     * @return its code, without comments and text in quotes
     * @throws Exception when it cannot be read
     */
    private static String code(File source) throws Exception
    {
        String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ").replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}
