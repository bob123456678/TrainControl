package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * A timetable played and saved (FR-213), on `single-switch`, simulated: the gap before an entry, entries in order
 * (one at a time or not), an entry that cannot run, one bad entry in a saved timetable, and the turn flag through a
 * save.  Two legs for one train: WestEnd to the copy of Approach it arrives on, then on to MainPlatform.
 *
 * @author Adam
 */
public class testATimetableAtRunTime
{
    private static support.Scenario scenario;
    private static MarklinControlStation model;
    private static Locomotive train;

    private static final List<String> LOGGED = Collections.synchronizedList(new ArrayList<String>());
    private static final java.util.logging.Handler tap = support.LogTap.messagesInto(LOGGED);

    private static JSONObject pristine;

    private static final java.util.logging.Logger STATION_LOG
        = java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        STATION_LOG.addHandler(tap);

        scenario = support.Scenario.openSimulated("single-switch");
        model = scenario.getModel();
        model.stop();
        train = scenario.newTrain("Timetable train", 2301);
        pristine = scenario.getSession().snapshotSetup();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        STATION_LOG.removeHandler(tap);

        if (scenario != null) scenario.close();
    }

    @BeforeMethod
    public void aFreshSetup() throws Exception
    {
        LOGGED.clear();

        scenario.getSession().restoreSetup(new JSONObject(pristine.toString()));
    }

    /** The name of the Approach copy a train from WestEnd arrives on, by the rail. */
    private static String middle() throws Exception
    {
        Layout railway = scenario.buildSimulated();

        for (Edge e : railway.getNeighbors(railway.getPoint("WestEnd"))) return e.getEnd().getName();

        throw new AssertionError("WestEnd leads nowhere");
    }

    private static JSONObject entry(String loc, long gapMillis, String... stations)
    {
        JSONArray path = new JSONArray();

        for (int i = 0; i + 1 < stations.length; i++)
        {
            path.put(new JSONObject().put("start", stations[i]).put("end", stations[i + 1]));
        }

        return new JSONObject().put("loc", loc).put("executionTime", 0L).put("secondsToNext", gapMillis)
            .put("path", path);
    }

    /** The railway built from a setup holding exactly these entries as its timetable, the log cleared first. */
    private static Layout saved(JSONObject... entries) throws Exception
    {
        scenario.getSession().setGlobal("timetable", new JSONArray(java.util.Arrays.asList(entries)));

        LOGGED.clear();

        return scenario.buildSimulated();
    }

    /** The two legs as a saved timetable, the second held back `gap` milliseconds. */
    private static Layout twoLegs(long gap) throws Exception
    {
        String middle = middle();

        scenario.getSession().setGlobal("timetable", new JSONArray()
            .put(entry(train.getName(), 0, "WestEnd", middle))
            .put(entry(train.getName(), gap, middle, "MainPlatform")));

        Layout railway = scenario.buildSimulated();

        assertEquals(railway.getTimetable().size(), 2, "the timetable did not load: " + LOGGED);

        railway.getPoint("WestEnd").setLocomotive(train);

        return railway;
    }

    private static void play(Layout railway) throws Exception
    {
        assertTrue(railway.executeTimetable(), "the timetable stopped: " + railway.whyTheTimetableStopped());

        assertTrue(support.Components.waitFor(() -> !railway.isRunning(), 60000), "the timetable did not finish");
    }

    private static String starting(TimetablePath entry)
    {
        return I18n.f("autolayout.infoStartingTimetableRoute", entry.toString());
    }

    /**
     * An entry's gap holds it back from the start of the one before, and no entry starts before the one it follows.
     *
     * MUTATION: drop the `getSecondsToNext` wait in `Layout.executeTimetableInternal` and this fails: the second leg
     * starts as soon as the first has set off.
     */
    @Test(timeOut = 120000)
    public void testAnEntrysGapIsKept() throws Exception
    {
        Layout railway = twoLegs(3000);

        play(railway);

        List<TimetablePath> table = railway.getTimetable();

        assertEquals(railway.getLocomotiveLocation(train).getName(), "MainPlatform", "the train did not get there");

        long between = table.get(1).getExecutionTime() - table.get(0).getExecutionTime();

        assertTrue(between >= 2800, "the second entry started " + between + " ms after the first, not after its gap");
        assertTrue(between < 20000, "the second entry waited far beyond its gap: " + between + " ms");
    }

    /**
     * Played in order whichever way it is run; run one at a time, the second entry does not start until the first
     * train has arrived.
     *
     * MUTATION: drop the `timetableSequential` wait in `Layout.executeTimetableInternal` and the sequential half fails.
     */
    @Test(timeOut = 120000)
    public void testEntriesPlayInOrderAndOneAtATimeWhenAsked() throws Exception
    {
        for (boolean sequential : new boolean[] {false, true})
        {
            LOGGED.clear();

            scenario.getSession().restoreSetup(new JSONObject(pristine.toString()));

            Layout railway = twoLegs(0);

            railway.setTimetableSequential(sequential);

            play(railway);

            List<TimetablePath> table = railway.getTimetable();

            int first = LOGGED.indexOf(starting(table.get(0)));
            int second = LOGGED.indexOf(starting(table.get(1)));

            assertTrue(first >= 0 && second > first, "sequential " + sequential + ": the entries did not start in order: "
                + LOGGED);

            assertEquals(railway.getLocomotiveLocation(train).getName(), "MainPlatform",
                "sequential " + sequential + ": the train did not get to the end");

            assertTrue(table.get(0).getExecutionTime() <= table.get(1).getExecutionTime(),
                "sequential " + sequential + ": the second entry went first");

            if (sequential)
            {
                int arrived = LOGGED.indexOf(I18n.f("autolayout.infoTimetablePathFinished"));

                assertTrue(arrived >= 0 && arrived < second,
                    "the second entry started before the first train had arrived: " + LOGGED);
            }
        }
    }

    /**
     * An entry that never gets its track stops a timetable that is not run one at a time, and says so.
     *
     * MUTATION: take the `errorTimetableEntryStuck` line out of `Layout.executeTimetableInternal` and this fails.
     */
    @Test(timeOut = 60000)
    public void testAnEntryThatCannotRunStopsTheRunAndSaysSo() throws Exception
    {
        Layout railway = twoLegs(0);

        // Nothing stands at WestEnd, so the first entry is refused for as long as it is asked
        railway.getPoint("WestEnd").setLocomotive(null);

        long was = Layout.TIMETABLE_STUCK_MS;

        Layout.TIMETABLE_STUCK_MS = 1500;

        try
        {
            assertFalse(railway.executeTimetable(), "a timetable that could not run reported success");

            assertTrue(support.Components.waitFor(() -> !railway.isRunning(), 30000), "the timetable did not stop");
        }
        finally
        {
            Layout.TIMETABLE_STUCK_MS = was;

            railway.stopLocomotives();
        }

        String stuck = I18n.f("autolayout.errorTimetableEntryStuck", railway.getTimetable().get(0).toString());

        assertEquals(Collections.frequency(LOGGED, stuck), 1, "the run did not say once that the entry was stuck: "
            + LOGGED);
    }

    /**
     * A saved timetable with an entry for a train that is gone loads the rest and says so once.  An unknown station
     * is not that: the entry is kept as written, to be refused when it is run.
     *
     * MUTATION: let the `catch` in `Layout.parseAuto`'s timetable loop rethrow and this fails.
     */
    @Test
    public void testOneBadEntryDoesNotLoseTheOthers() throws Exception
    {
        String middle = middle();

        Layout railway = saved(entry(train.getName(), 0, "WestEnd", middle),
            entry("No such train", 0, "WestEnd", middle),
            entry(train.getName(), 0, "Nowhere", "MainPlatform"),
            entry(train.getName(), 0, middle, "MainPlatform"));

        assertEquals(railway.getTimetable().size(), 3, "the good entries and the unknown station's did not all load");

        assertEquals(railway.getTimetable().get(0).getEndName(), middle, "the first good entry is not first");
        assertEquals(railway.getTimetable().get(2).getEndName(), "MainPlatform", "the second good entry is not last");

        String warning = I18n.f("autolayout.warnTimetableEntry",
            I18n.f("autolayout.errorLocomotiveDoesNotExist", "No such train"));

        assertEquals(Collections.frequency(LOGGED, warning), 1, "the bad entry was not warned about once: " + LOGGED);
    }

    /**
     * The turn the arrival was told, recorded by hand, and its absence, survive the railway's own file.
     *
     * MUTATION: drop the `turnAtTheEnd` write in `TimetablePath.toJSON` or its read in `fromJSON` and this fails.
     */
    @Test
    public void testTheTurnAtTheEndSurvivesASave() throws Exception
    {
        String middle = middle();

        Layout railway = saved(entry(train.getName(), 0, "WestEnd", middle).put("turnAtTheEnd", true),
            entry(train.getName(), 0, middle, "MainPlatform").put("turnAtTheEnd", false),
            entry(train.getName(), 0, "MainPlatform", middle));

        assertEquals(railway.getTimetable().size(), 3, "the timetable did not load: " + LOGGED);

        model.parseAuto(railway.toJSON());

        List<TimetablePath> reopened = model.getAutoLayout().getTimetable();

        assertEquals(reopened.size(), 3, "the saved timetable lost entries");

        assertEquals(reopened.get(0).getTurnAtTheEnd(), Boolean.TRUE, "a turn was lost");
        assertEquals(reopened.get(1).getTurnAtTheEnd(), Boolean.FALSE, "a no-turn was lost");
        assertNull(reopened.get(2).getTurnAtTheEnd(), "an entry with no answer was given one");
    }
}
