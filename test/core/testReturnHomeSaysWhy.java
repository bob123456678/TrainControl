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
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    /** Clear of testHomeStaging's 8890s. */
    private static final int S88_BASE = 8860;

    private static final String LOC = "RHW alpha";

    /** A second train, for the claims that need two homes. */
    private static final String OTHER = "RHW bravo";

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
        // BEFORE the model: `init` reads the machine-global layout preference and would otherwise open
        // Adam's real railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
        model.stop();

        model.newMM2Locomotive(LOC, 91);

        model.newMM2Locomotive(OTHER, 92);

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(listening);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(listening);

        if (model != null) model.deleteLoc(LOC);

        if (model != null) model.deleteLoc(OTHER);

        if (sandbox != null) sandbox.close();
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

    /**
     * A home square emitted as two copies, one of which is not a destination, still says "too short"
     * (TDR-B3).
     *
     * A platform whose arrival from one side is barred is built as a destination copy and a copy that is
     * not one, in one block.  `Point.validateTrainLength` answers yes for a copy that is not a destination,
     * so asking "is ANY copy long enough" was answered by the copy no train can stop at - the length
     * sentence was suppressed, and the operator was told to check the connections instead.
     */
    @Test
    public void testAHomeWithABarredCopyStillSaysItIsTooShort()
    {
        Layout layout = load(json("{'points': ["
            + station("RHW A", 0, LOC) + ","
            + "{'name': 'RHW D', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'block': 'RHW-D', 'maxTrainLength': 4, 'home': '" + LOC + "'},"
            + "{'name': 'RHW D (barred)', 'station': false, 's88': " + (S88_BASE + 3)
            + ", 'block': 'RHW-D'}"
            + "],'edges': ["
            + "{'start': 'RHW A', 'end': 'RHW D', 'length': 20},"
            + "{'start': 'RHW D', 'end': 'RHW A', 'length': 20},"
            + "{'start': 'RHW D (barred)', 'end': 'RHW A', 'length': 20}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertTrue(layout.getPoint("RHW D").isSamePlaceAs(layout.getPoint("RHW D (barred)")),
            "precondition: the two copies are not one square, so this is the single-copy case again");

        MarklinLocomotive loc = model.getLocByName(LOC);

        Integer lengthWas = loc.getTrainLength();

        try
        {
            loc.setTrainLength(6);

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
                "precondition: a six-unit train homed on a four-unit platform is not refused");

            List<String> why = plan.getReasons().get(loc);

            assertNotNull(why, "no reason was kept: " + plan.getReasons());

            assertTrue(anyMentions(why, "RHW D", "6", "4"),
                "a six-unit train refused a four-unit home whose square also has a copy trains cannot stop"
                + " at was told something other than that the home is too short: " + why
                + ". The copy that is not a destination answered the length question (TDR-B3)");
        }
        finally
        {
            loc.setTrainLength(lengthWas);
        }
    }

    /**
     * A home switched out of service says so.
     */
    @Test
    public void testAHomeOutOfServiceSaysSo()
    {
        HomeStaging.Plan plan = load(aHomeWith(", 'active': false")).planReturnToHome();

        assertSays(plan, model.getLocByName(LOC), "autolayout.whyHomeOutOfService", "RHW H");
    }

    /**
     * A home that excludes the train says so.
     */
    @Test
    public void testAHomeThatExcludesTheTrainSaysSo()
    {
        HomeStaging.Plan plan = load(aHomeWith(", 'excludedLocs': ['" + LOC + "']")).planReturnToHome();

        assertSays(plan, model.getLocByName(LOC), "autolayout.whyHomeExcludesIt", "RHW H");
    }

    /**
     * A train standing on a square switched out of service says it cannot start from there.
     */
    @Test
    public void testAStartOutOfServiceSaysSo()
    {
        HomeStaging.Plan plan = load(json("{'points': ["
            + "{'name': 'RHW A', 'station': true, 's88': " + S88_BASE + ", 'active': false,"
            + " 'loc': {'name': '" + LOC + "'}},"
            + "{'name': 'RHW H', 'station': true, 's88': " + (S88_BASE + 7) + ", 'home': '" + LOC + "'}"
            + "],'edges': [" + edge("RHW A", "RHW H") + "," + edge("RHW H", "RHW A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome();

        assertSays(plan, model.getLocByName(LOC), "autolayout.whyHomeStartOutOfService", "RHW A");
    }

    /**
     * Two homes on one detection section say which two.
     */
    @Test
    public void testTwoHomesOnOneSectionSayWhichTwo()
    {
        HomeStaging.Plan plan = load(json("{'points': ["
            + station("RHW A", 0, LOC) + ","
            + station("RHW B", 1, OTHER) + ","
            + "{'name': 'RHW G', 'station': true, 's88': " + (S88_BASE + 6) + ", 'home': '" + LOC + "'},"
            + "{'name': 'RHW J', 'station': true, 's88': " + (S88_BASE + 6) + ", 'home': '" + OTHER + "'}"
            + "],'edges': ["
            + edge("RHW A", "RHW G") + "," + edge("RHW G", "RHW A") + ","
            + edge("RHW B", "RHW J") + "," + edge("RHW J", "RHW B") + ","
            + edge("RHW A", "RHW B") + "," + edge("RHW B", "RHW A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome();

        assertSays(plan, model.getLocByName(LOC), "autolayout.whyHomeSharesASection", "RHW G", OTHER, "RHW J");
    }

    /**
     * Where the search only ran out of room, the home another train stands on is named, and nothing is
     * claimed to be impossible.
     */
    @Test
    public void testAnOccupiedHomeIsNamedWhenNoArrangementIsFound()
    {
        // Two trains on a line with no way past each other, each standing on the other's home.
        HomeStaging.Plan plan = load(json("{'points': ["
            + "{'name': 'RHW A', 'station': true, 's88': " + S88_BASE + ", 'loc': {'name': '" + OTHER + "'},"
            + " 'home': '" + LOC + "'},"
            + "{'name': 'RHW B', 'station': true, 's88': " + (S88_BASE + 1) + ", 'loc': {'name': '" + LOC + "'},"
            + " 'home': '" + OTHER + "'}"
            + "],'edges': [" + edge("RHW A", "RHW B") + "," + edge("RHW B", "RHW A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.NO_PLAN_FOUND,
            "precondition: two trains swapping places on a line with no passing loop was not a search that"
            + " ran out, so the sentence under test is not the one that would be said");

        assertSays(plan, model.getLocByName(LOC), "autolayout.whyHomeOccupied", "RHW A", OTHER);
    }

    /**
     * The reasons name squares, not the direction copies the builder made of them (TDR-C8).
     *
     * Adam's ruling that the copy machinery is masked from the user covers the log as much as a dialog, and
     * a home is stood on as one of its copies - so "its home X (eastbound, reverse) is switched out of
     * service" was what the log said.
     */
    @Test
    public void testTheReasonsNameSquaresNotCopies()
    {
        HomeStaging.Plan plan = load(json("{'points': ["
            + "{'name': 'RHW A (westbound)', 'station': true, 's88': " + S88_BASE
            + ", 'loc': {'name': '" + LOC + "'}},"
            + "{'name': 'RHW K (eastbound, reverse)', 'station': true, 's88': " + (S88_BASE + 8)
            + ", 'active': false, 'home': '" + LOC + "'}"
            + "],'edges': [" + edge("RHW A (westbound)", "RHW K (eastbound, reverse)") + ","
            + edge("RHW K (eastbound, reverse)", "RHW A (westbound)")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome();

        List<String> why = plan.getReasons().get(model.getLocByName(LOC));

        assertNotNull(why, "precondition: the plan (" + plan.getOutcome() + ") kept no reason: " + plan.getReasons());

        assertTrue(anyMentions(why, "RHW K"), "precondition: no reason names the home: " + why);

        for (String sentence : why)
        {
            assertTrue(!sentence.contains("eastbound") && !sentence.contains("westbound")
                && !sentence.contains("reverse"),
                "a Return Home reason names the builder's copy of a square, heading and all: \"" + sentence
                + "\" (TDR-C8)");
        }
    }

    /**
     * A planned move names the square it goes to, not the builder's copy of it (TDR-C9).
     *
     * The "planned" lines are logged in the same block as the reasons, and TDR-C8 masked the reasons only.
     */
    @Test
    public void testAPlannedMoveNamesTheSquare()
    {
        HomeStaging.Plan plan = load(json("{'points': ["
            + "{'name': 'RHW A (westbound)', 'station': true, 's88': " + S88_BASE
            + ", 'loc': {'name': '" + LOC + "'}},"
            + "{'name': 'RHW P (eastbound, reverse)', 'station': true, 's88': " + (S88_BASE + 9)
            + ", 'home': '" + LOC + "'}"
            + "],'edges': [" + edge("RHW A (westbound)", "RHW P (eastbound, reverse)") + ","
            + edge("RHW P (eastbound, reverse)", "RHW A (westbound)")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "precondition: a clear run to an empty home was not planned: " + plan.getReasons());

        assertTrue(!plan.getMoves().isEmpty(), "precondition: the plan has no moves to name");

        for (HomeStaging.Move move : plan.getMoves())
        {
            String line = move.toString();

            assertTrue(line.contains("RHW P"), "precondition: the planned move does not name the home: " + line);

            assertTrue(!line.contains("eastbound") && !line.contains("reverse"),
                "the planned move, which the log shows as the plan, names the builder's copy of the home: \""
                + line + "\" (TDR-C9)");
        }
    }

    /**
     * Every reason a plan gives names squares, across every sentence the refusals can reach (TDR-C12).
     *
     * `testTheReasonsNameSquaresNotCopies` reached one sentence, so reverting any of the other sites that
     * name a square left it green.  Each fixture here is named the way the builder names copies, and each
     * is checked for the sentence it must produce, so a site is only counted as covered when its own
     * sentence was actually said.
     *
     * Not reached: "the start is not a station", "the home is not a station" and "no arrangement found" -
     * the loader or the search decides those before a reason is written, on these small railways - and the
     * fallback at the end of `whyNotHome`, which a refused train with no other reason would need.
     */
    @Test
    public void testEveryReachableReasonNamesSquaresNotCopies()
    {
        Locomotive loc = model.getLocByName(LOC);

        // A start switched out of service.
        assertSaysWithoutHeadings(load(json("{'points': ["
            + "{'name': 'RHW A (westbound)', 'station': true, 's88': " + S88_BASE + ", 'active': false,"
            + " 'loc': {'name': '" + LOC + "'}},"
            + "{'name': 'RHW H (eastbound)', 'station': true, 's88': " + (S88_BASE + 7) + ", 'home': '" + LOC + "'}"
            + "],'edges': [" + edge("RHW A (westbound)", "RHW H (eastbound)") + "," + edge("RHW H (eastbound)", "RHW A (westbound)")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome(),
            loc, "autolayout.whyHomeStartOutOfService", "RHW A");

        // A home that excludes the train.
        assertSaysWithoutHeadings(load(copyNamedHome(", 'excludedLocs': ['" + LOC + "']")).planReturnToHome(),
            loc, "autolayout.whyHomeExcludesIt", "RHW H");

        // A home out of service.
        assertSaysWithoutHeadings(load(copyNamedHome(", 'active': false")).planReturnToHome(),
            loc, "autolayout.whyHomeOutOfService", "RHW H");

        // A home too short for the train.
        Integer lengthWas = loc.getTrainLength();

        try
        {
            loc.setTrainLength(6);

            assertSaysWithoutHeadings(load(copyNamedHome(", 'maxTrainLength': 2")).planReturnToHome(),
                loc, "autolayout.whyHomeTooShort", "RHW H");
        }
        finally
        {
            loc.setTrainLength(lengthWas);
        }

        // A home with no way in.
        assertSaysWithoutHeadings(load(json("{'points': ["
            + "{'name': 'RHW A (westbound)', 'station': true, 's88': " + S88_BASE + ", 'loc': {'name': '" + LOC + "'}},"
            + "{'name': 'RHW B (northbound)', 'station': true, 's88': " + (S88_BASE + 1) + "},"
            + "{'name': 'RHW E (southbound)', 'station': true, 's88': " + (S88_BASE + 4) + ", 'home': '" + LOC + "'}"
            + "],'edges': [" + edge("RHW A (westbound)", "RHW B (northbound)") + "," + edge("RHW B (northbound)", "RHW A (westbound)") + ","
            + edge("RHW E (southbound)", "RHW B (northbound)")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome(),
            loc, "autolayout.whyHomeNoRoute", "RHW A", "RHW E");

        // Two homes on one detection section.
        assertSaysWithoutHeadings(load(json("{'points': ["
            + "{'name': 'RHW A (westbound)', 'station': true, 's88': " + S88_BASE + ", 'loc': {'name': '" + LOC + "'}},"
            + "{'name': 'RHW B (northbound)', 'station': true, 's88': " + (S88_BASE + 1) + ", 'loc': {'name': '" + OTHER + "'}},"
            + "{'name': 'RHW G (eastbound)', 'station': true, 's88': " + (S88_BASE + 6) + ", 'home': '" + LOC + "'},"
            + "{'name': 'RHW J (southbound)', 'station': true, 's88': " + (S88_BASE + 6) + ", 'home': '" + OTHER + "'}"
            + "],'edges': ["
            + edge("RHW A (westbound)", "RHW G (eastbound)") + "," + edge("RHW G (eastbound)", "RHW A (westbound)") + ","
            + edge("RHW B (northbound)", "RHW J (southbound)") + "," + edge("RHW J (southbound)", "RHW B (northbound)") + ","
            + edge("RHW A (westbound)", "RHW B (northbound)") + "," + edge("RHW B (northbound)", "RHW A (westbound)")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome(),
            loc, "autolayout.whyHomeSharesASection", "RHW G", "RHW J");

        // A home another train stands on, where the search runs out.
        assertSaysWithoutHeadings(load(json("{'points': ["
            + "{'name': 'RHW A (westbound)', 'station': true, 's88': " + S88_BASE + ", 'loc': {'name': '" + OTHER + "'},"
            + " 'home': '" + LOC + "'},"
            + "{'name': 'RHW B (eastbound)', 'station': true, 's88': " + (S88_BASE + 1) + ", 'loc': {'name': '" + LOC + "'},"
            + " 'home': '" + OTHER + "'}"
            + "],'edges': [" + edge("RHW A (westbound)", "RHW B (eastbound)") + "," + edge("RHW B (eastbound)", "RHW A (westbound)")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}")).planReturnToHome(),
            loc, "autolayout.whyHomeOccupied", "RHW A");
    }

    /** A copy-named start with a train on it, and a copy-named home carrying the given extra JSON. */
    private static String copyNamedHome(String extra)
    {
        return json("{'points': ["
            + "{'name': 'RHW A (westbound)', 'station': true, 's88': " + S88_BASE + ", 'loc': {'name': '" + LOC + "'}},"
            + "{'name': 'RHW H (eastbound, reverse)', 'station': true, 's88': " + (S88_BASE + 7)
            + ", 'home': '" + LOC + "'" + extra + "}"
            + "],'edges': [" + edge("RHW A (westbound)", "RHW H (eastbound, reverse)") + ","
            + edge("RHW H (eastbound, reverse)", "RHW A (westbound)")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /** The sentence for this key was said, naming these squares, and no sentence carries a heading. */
    private static void assertSaysWithoutHeadings(HomeStaging.Plan plan, Locomotive loc, String key, String... parts)
    {
        assertSays(plan, loc, key, parts);

        for (String sentence : plan.getReasons().get(loc))
        {
            for (String heading : new String[] {"northbound", "southbound", "eastbound", "westbound"})
            {
                assertTrue(!sentence.contains("(" + heading),
                    "a Return Home reason (" + key + ") names the builder's copy of a square: \"" + sentence
                    + "\" (TDR-C12)");
            }
        }
    }

    private static String aHomeWith(String extra)
    {
        return json("{'points': ["
            + station("RHW A", 0, LOC) + ","
            + "{'name': 'RHW H', 'station': true, 's88': " + (S88_BASE + 7) + ", 'home': '" + LOC + "'" + extra + "}"
            + "],'edges': [" + edge("RHW A", "RHW H") + "," + edge("RHW H", "RHW A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The plan kept, for this train, a sentence that is the given key's own wording and names every part.
     */
    private static void assertSays(HomeStaging.Plan plan, Locomotive loc, String key, String... parts)
    {
        List<String> why = plan.getReasons().get(loc);

        assertNotNull(why, "the plan (" + plan.getOutcome() + ") kept no reason for " + loc.getName()
            + ": " + plan.getReasons());

        // The key's own fixed words, so the sentence is shown to be THIS rule and not another naming the
        // same squares.
        String fixed = org.traincontrol.util.I18n.t(key).replaceAll("\\{\\d\\}", "\u0000");

        String longest = "";

        for (String piece : fixed.split("\u0000"))
        {
            if (piece.trim().length() > longest.length()) longest = piece.trim();
        }

        boolean found = false;

        for (String sentence : why)
        {
            boolean every = sentence.contains(longest);

            for (String part : parts) every &= sentence.contains(part);

            found |= every;
        }

        assertTrue(found, "no sentence for " + loc.getName() + " says \"" + longest + "\" and names "
            + java.util.Arrays.toString(parts) + ": " + why);
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
