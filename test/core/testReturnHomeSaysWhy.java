package core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A Return Home plan that fails says, in the log, why each train cannot get home.
 *
 * Adam, FR-078, 2026-09-13: *"if a return home plan fails, state the reason why the layout doesn't allow a
 * locomotive to go to its home in the log (length, blocked, etc.)"*.  And on MT-346, the same evening:
 * *"seems OK, just needs to be easier for the user to debug"*.
 *
 * **Before this the plan named the trains and nothing else.**  Each check that puts a train on the
 * unreachable list is a different fact with a different remedy - a length to measure, a switch to open,
 * a station to switch back on - and the dialog could only say "these trains cannot reach their homes".
 *
 * **Asked of the words, not of a flag:** each claim finds the sentence for the rule that actually refused
 * and checks it carries the numbers or names the operator would act on, so a plan that logged a reason
 * but the wrong one fails here.  And the log is read as well as the plan, because a reason kept in the
 * plan and never written is what the request was about.
 *
 * @author Adam
 */
public class testReturnHomeSaysWhy
{
    private static MarklinControlStation model;

    /** Clear of testHomeStaging's 8890s. */
    private static final int S88_BASE = 8860;

    private static final String LOC = "RHW alpha";

    private static final List<String> logged = java.util.Collections.synchronizedList(new ArrayList<String>());

    private static final Handler listening = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            if (record != null && record.getMessage() != null) logged.add(record.getMessage());
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();

        model.newMM2Locomotive(LOC, 91);

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(listening);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(listening);

        if (model != null) model.deleteLoc(LOC);
    }

    /**
     * A home too short for the train says so, with both lengths.
     */
    @Test
    public void testAHomeTooShortForTheTrainSaysSoWithBothLengths()
    {
        Layout layout = load(json("{'points': ["
            + station("RHW A", 0, LOC) + ","
            + station("RHW B", 1, null) + ","
            + "{'name': 'RHW D', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'maxTrainLength': 4, 'home': '" + LOC + "'}"
            + "],'edges': ["
            + "{'start': 'RHW A', 'end': 'RHW D', 'length': 20},"
            + "{'start': 'RHW D', 'end': 'RHW A', 'length': 20},"
            + "{'start': 'RHW A', 'end': 'RHW B', 'length': 20},"
            + "{'start': 'RHW B', 'end': 'RHW A', 'length': 20}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        MarklinLocomotive loc = model.getLocByName(LOC);

        Integer lengthWas = loc.getTrainLength();

        try
        {
            loc.setTrainLength(6);

            logged.clear();

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
                "precondition: a six-unit train homed on a platform limited to four is not refused, so"
                + " there is no failure here to explain");

            List<String> why = plan.getReasons().get(loc);

            assertNotNull(why, "the plan refused " + LOC + " and kept no reason for it: "
                + plan.getReasons() + ". Adam, FR-078: \"state the reason why the layout doesn't allow a"
                + " locomotive to go to its home in the log (length, blocked, etc.)\"");

            assertTrue(anyMentions(why, "RHW D", "6", "4"),
                "the reason given for a six-unit train refused a four-unit home does not name the home and"
                + " both lengths, so it does not say what to change: " + why);

            assertTrue(anyLoggedMentions(LOC, "RHW D", "6", "4"),
                "the length reason was kept in the plan and never written to the log, which is where Adam"
                + " asked for it. Logged: " + logged);
        }
        finally
        {
            loc.setTrainLength(lengthWas);
        }
    }

    /**
     * A home with no way in says there is no route, and names both ends.
     */
    @Test
    public void testAHomeWithNoWayInSaysThereIsNoRoute()
    {
        Layout layout = load(json("{'points': ["
            + station("RHW A", 0, LOC) + ","
            + station("RHW B", 1, null) + ","
            + "{'name': 'RHW E', 'station': true, 's88': " + (S88_BASE + 4)
            + ", 'home': '" + LOC + "'}"
            + "],'edges': ["
            + edge("RHW A", "RHW B") + "," + edge("RHW B", "RHW A") + ","
            // Out of the home, and nothing back in: the station exists and the train can never reach it.
            + edge("RHW E", "RHW B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        Locomotive loc = model.getLocByName(LOC);

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "precondition: a home with no edge into it is not refused");

        List<String> why = plan.getReasons().get(loc);

        assertNotNull(why, "the plan refused a home nothing leads to and kept no reason: "
            + plan.getReasons());

        assertTrue(anyMentions(why, "RHW A", "RHW E"),
            "the reason for a home no edge leads into does not name where the train stands and where it"
            + " is trying to go: " + why);

        assertTrue(!anyMentions(why, "RHW E", "4"),
            "a home with no length limit was described as too short, which is a sentence about a rule"
            + " that did not refuse: " + why);
    }

    /**
     * The control: a plan that succeeds carries no reasons, so a reason is never noise.
     */
    @Test
    public void testAPlanThatSucceedsGivesNoReasons()
    {
        Layout layout = load(json("{'points': ["
            + station("RHW A", 0, LOC) + ","
            + station("RHW B", 1, null) + ","
            + "{'name': 'RHW F', 'station': true, 's88': " + (S88_BASE + 5)
            + ", 'home': '" + LOC + "'}"
            + "],'edges': ["
            + edge("RHW A", "RHW F") + "," + edge("RHW F", "RHW A") + ","
            + edge("RHW A", "RHW B") + "," + edge("RHW B", "RHW A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "precondition: a train with a clear run to an empty home was not planned");

        assertTrue(plan.getReasons().isEmpty(),
            "a plan that succeeded still carries reasons, so the log would explain a failure that did not"
            + " happen: " + plan.getReasons());
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private static boolean anyMentions(List<String> sentences, String... all)
    {
        for (String sentence : sentences)
        {
            boolean every = true;

            for (String part : all) every &= sentence.contains(part);

            if (every) return true;
        }

        return false;
    }

    private static boolean anyLoggedMentions(String... all)
    {
        synchronized (logged)
        {
            return anyMentions(new ArrayList<>(logged), all);
        }
    }

    private static Layout load(String config)
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertTrue(layout.isValid(), "precondition: the test graph must parse - " + Layout.getLastError());

        return layout;
    }

    private static String json(String s)
    {
        return s.replace('\'', '"');
    }

    private static String station(String name, int s88Offset, String loc)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (S88_BASE + s88Offset)
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    private static String edge(String from, String to)
    {
        return "{'start': '" + from + "', 'end': '" + to + "'}";
    }
}
