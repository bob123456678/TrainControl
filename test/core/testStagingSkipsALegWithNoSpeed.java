package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A locomotive with no speed loses its own leg, not everybody else's.  `runLocomotives` skips a locomotive whose
 * preferred speed is outside 1 to 100 and every other one still starts; the timetable's dispatch loop never learned it:
 * executePath refuses at once, the retry loop reads that as a busy track, declares the entry stuck after three
 * attempts, and ends the run.  On a Return Home that abandons every remaining leg because one hand-placed train has no
 * speed (MT-233 closed the file half).
 *
 * Its own class because it needs debug mode (`setSimulate` refuses without it), which `testHomeStaging`, where the rest
 * of the staging tests live, inits without on purpose.
 *
 * @author Adam
 */
public class testStagingSkipsALegWithNoSpeed
{
    private static MarklinControlStation model;

    /**
     * Points the layout preference somewhere that is not Adam's railway (OB-111): `init` loads whatever layout it
     * names.
     */
    private static support.LayoutSandbox sandbox;

    /** Addresses of their own, so this class can run beside testHomeStaging's 81-and-up. */
    private static final String LOC_STUCK = "SG stuck";
    private static final String LOC_MOVING = "SG moving";

    private static final int STUCK_ADDRESS = 76;
    private static final int MOVING_ADDRESS = 77;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model, not just before a window (OB-111)
        sandbox = support.LayoutSandbox.open();

        // Debug mode last, because simulation requires it
        model = support.TestStation.simulatedInDebug();
        model.stop();

        model.newMM2Locomotive(LOC_STUCK, STUCK_ADDRESS);
        model.newMM2Locomotive(LOC_MOVING, MOVING_ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null)
        {
            if (model.getAutoLayout() != null) model.getAutoLayout().stopLocomotives();

            model.deleteLoc(LOC_STUCK);
            model.deleteLoc(LOC_MOVING);
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * The second entry runs even though the first cannot.  ON A DEADLINE: this drives a real dispatch loop, where a
     * regression is a hang rather than a failure.
     *
     * MUTATION: removing the skip restores the abandonment - executeTimetable returns
     * false and the second locomotive is still standing where it started.
     */
    @Test(timeOut = 180000)
    public void testAnEntryWithNoSpeedLosesItsOwnLegAndNotTheRun() throws Exception
    {
        Layout layout = load(ring(LOC_STUCK, null, LOC_MOVING));

        try
        {
            layout.setSimulate(true);
        }
        catch (Exception e)
        {
            throw new AssertionError("simulation could not be enabled: " + e.getMessage());
        }

        assertTrue(layout.isSimulate(), "simulation must be on before anything is asked to move");

        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        List<TimetablePath> entries = new ArrayList<>();

        entries.add(new TimetablePath(loc(LOC_STUCK), pathTo(layout, LOC_STUCK, "SG B"), 0L));
        entries.add(new TimetablePath(loc(LOC_MOVING), pathTo(layout, LOC_MOVING, "SG D"), 0L));

        layout.setTimetable(entries);

        // One train at a time, as a staging run is; setTimetable clears the flag, so this comes after it.
        layout.setTimetableSequential(true);

        assertTrue(layout.isTimetableSequential(),
            "the sequential flag did not survive being set after setTimetable, so this exercises the "
            + "parallel loop instead and waits three minutes to do it");

        loc(LOC_MOVING).setPreferredSpeed(35);
        loc(LOC_STUCK).setPreferredSpeed(0);

        // NOT A PRECONDITION ON THE TWO LINES ABOVE (the rule stated at testHomeStaging): what must be true is that the
        // two ENTRIES are different trains on different paths, or a skipped leg and a run leg are the same leg.
        assertNotSame(entries.get(0).getLoc(), entries.get(1).getLoc(),
            "precondition: both timetable entries name the same locomotive, so a skipped leg cannot be "
            + "told from a run one");

        assertNotEquals(entries.get(0).getPath(), entries.get(1).getPath(),
            "precondition: both entries run the same path, so this test cannot tell which leg the "
            + "staging run took");

        final boolean[] ranToTheEnd = new boolean[1];
        final Throwable[] thrown = new Throwable[1];

        Thread run = new Thread(() ->
        {
            try
            {
                ranToTheEnd[0] = layout.executeTimetable();
            }
            catch (Throwable bad)
            {
                thrown[0] = bad;
            }
        });

        run.start();
        run.join(120000);

        assertFalse(run.isAlive(),
            "the timetable did not finish in two minutes - the dispatch loop is stuck, which is the "
            + "shape a regression here takes (RC-B10)");

        if (thrown[0] != null) throw new RuntimeException(thrown[0]);

        assertNotNull(layout.getLocomotiveLocation(loc(LOC_MOVING)),
            "the second locomotive is on no square at all");

        assertEquals(layout.getLocomotiveLocation(loc(LOC_MOVING)).getName(), "SG D",
            "the second entry never ran: one locomotive without a speed ended the whole run, where "
            + "runLocomotives would have skipped it and kept everything else going");

        assertEquals(layout.getLocomotiveLocation(loc(LOC_STUCK)).getName(), "SG A",
            "the locomotive with no speed moved, which it must not - a skip is not a dispatch");

        assertTrue(ranToTheEnd[0],
            "the run reported itself abandoned, so the operator is shown the stopped-at-entry "
            + "dialog for a run that went on to the end without one leg");
    }

    // Fixtures

    /** Four stations in a ring, every edge in both directions, so the graph never refuses anything. */
    private static String ring(String locAtA, String locAtB, String locAtC)
    {
        return ("{'points': ["
            + station("SG A", 0, locAtA) + ","
            + station("SG B", 1, locAtB) + ","
            + station("SG C", 2, locAtC) + ","
            + station("SG D", 3, null)
            + "],'edges': ["
            + edge("SG A", "SG B") + "," + edge("SG B", "SG A") + ","
            + edge("SG B", "SG C") + "," + edge("SG C", "SG B") + ","
            + edge("SG C", "SG D") + "," + edge("SG D", "SG C") + ","
            + edge("SG D", "SG A") + "," + edge("SG A", "SG D")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 35}").replace('\'', '"');
    }

    private static String station(String name, int s88Offset, String loc)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (8990 + s88Offset)
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    private static String edge(String from, String to)
    {
        return "{'start': '" + from + "', 'end': '" + to + "'}";
    }

    private static Layout load(String config)
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertTrue(layout.isValid(),
            "precondition: the test graph must parse - " + Layout.getLastError());

        return layout;
    }

    private static MarklinLocomotive loc(String name)
    {
        return model.getLocByName(name);
    }

    /** The locomotive's own path to a named station, taken from the routes the runtime offers it. */
    private static List<Edge> pathTo(Layout layout, String locName, String to)
    {
        for (List<Edge> path : layout.getPossiblePaths(loc(locName), true))
        {
            if (path.get(path.size() - 1).getEnd().getName().equals(to)) return path;
        }

        fail("the fixture offers " + locName + " no route to " + to);

        return null;
    }
}
