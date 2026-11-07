package core;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;
import support.Scenario;

/**
 * The same setup gives the same Return Home plan every time, on any machine at any load (OB-413): the search's budget
 * is route work, not time.
 *
 * His frozen railway with its own four trains standing as saved, and the homes the old-setups proof gives them in its
 * fourth round: the arrangement where the search used to pick 4 moves or 8 by how fast it ran.  The plan pinned below
 * is the 4-move one, which the build before also gave whenever its first search ran out of time.
 *
 * @author Adam
 */
public class testReturnHomeIsTheSameEveryTime
{
    private static Scenario sandbox;
    private static MarklinControlStation model;
    private static String built;

    /** The plan, move by move, as the log shows it, with the number of legs each move drives. */
    private static final String PINNED = "READY [2-8-4 3505 SP -> BottomSecondary (5), EN57-947 -> TopMainR1 (11),"
        + " EN57-203 -> Tunnel (7), 75 407 DB -> BottomMainPost (1)]";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = Scenario.openSimulated(new File("test/operator_layout"));
        model = sandbox.getModel();
        model.stop();
        built = sandbox.getSession().buildConfiguration();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /** The railway as saved, with the four homes. */
    private static Layout railway() throws Exception
    {
        model.parseAuto(built);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "his setup did not parse here: " + Layout.getLastError());

        assertEquals(layout.getPoint("TopMainR0Park").getCurrentLocomotive().getName(), "EN57-203",
            "precondition: his railway no longer stands EN57-203 on TopMainR0Park as saved");

        layout.clearHomeLocomotives();
        layout.setHomeLocomotive("BottomSecondary", "2-8-4 3505 SP");
        layout.setHomeLocomotive("BottomMainPost (northbound, reverse)", "75 407 DB");
        layout.setHomeLocomotive("Tunnel (southbound)", "EN57-203");
        layout.setHomeLocomotive("TopMainR1", "EN57-947");

        return layout;
    }

    private static String movesOf(HomeStaging.Plan plan)
    {
        List<String> moves = new ArrayList<>();

        for (HomeStaging.Move move : plan.getMoves()) moves.add(move + " (" + move.getPath().size() + ")");

        return plan.getOutcome() + " " + moves;
    }

    /** A plan with the search's clock stepped this far a read, where the planner still has a clock. */
    private static HomeStaging.Plan planWithAClockStepping(long step) throws Exception
    {
        HomeStaging staging = HomeStaging.snapshot(railway());

        Field clock = null;

        for (Field each : HomeStaging.class.getDeclaredFields()) if (each.getName().equals("clock")) clock = each;

        if (clock != null)
        {
            long[] now = {1000};

            clock.setAccessible(true);
            clock.set(staging, (java.util.function.LongSupplier) () -> now[0] += step);
        }

        return staging.plan();
    }

    /**
     * Ten replans in this JVM give one plan, and it is the pinned one, which no other JVM can then differ from.
     *
     * MUTATION: search from the greedy pass's arrangement before the railway as it stands, as 3.0.8 did, and this fails
     * on the pin: 8 moves.
     */
    @Test(timeOut = 300000)
    public void testTheSamePlanEveryReplan() throws Exception
    {
        List<String> plans = new ArrayList<>();

        for (int i = 0; i < 10; i++) plans.add(movesOf(HomeStaging.snapshot(railway()).plan()));

        for (String plan : plans) assertEquals(plan, PINNED, "ten replans of one setup gave " + plans);
    }

    /**
     * The clock has no say: the planner reads none, and where one could be stepped, any rate gives the pinned plan.
     *
     * MUTATION: put back a fifteen-second deadline read through a `clock` field, and this fails on both counts.
     */
    @Test(timeOut = 300000)
    public void testTheSamePlanWhateverTheClockSays() throws Exception
    {
        String source = new String(Files.readAllBytes(new File("src/org/traincontrol/automation/HomeStaging.java").toPath()),
            StandardCharsets.UTF_8);

        for (String clock : new String[] {"currentTimeMillis", "nanoTime", "LongSupplier", "Instant.now"})
        {
            assertFalse(source.contains(clock), "the planner reads the time (" + clock + "), so a slower machine can get a"
                + " different plan");
        }

        for (long step : new long[] {0, 1, 100, 300})
        {
            assertEquals(movesOf(planWithAClockStepping(step)), PINNED, "with the clock stepped " + step + " ms a read");
        }
    }

    /**
     * A machine slow enough to spend 700 ms or more a step still gets the plan a fast one finds, not NO_PLAN_FOUND.
     *
     * MUTATION: put back a fifteen-second deadline read through a `clock` field, and this answers NO_PLAN_FOUND.
     */
    @Test(timeOut = 300000)
    public void testASlowMachineIsNotRefused() throws Exception
    {
        for (long step : new long[] {700, 3000})
        {
            HomeStaging.Plan plan = planWithAClockStepping(step);

            assertEquals(movesOf(plan), PINNED, "a machine taking " + step + " ms a step was answered differently");
        }
    }

    /**
     * A search stopped by its budget says so, and one that ended because the railway had nothing left to try does not.
     *
     * MUTATION: leave out `ranOut = true` where the search stops at its share, and this fails.
     */
    @Test(timeOut = 300000)
    public void testABudgetRunOutSaysSo() throws Exception
    {
        HomeStaging staging = HomeStaging.snapshot(railway());

        Field budget = HomeStaging.class.getDeclaredField("budget");

        budget.setAccessible(true);
        budget.setLong(staging, 2000);

        HomeStaging.Plan starved = staging.plan();

        assertEquals(starved.getOutcome(), HomeStaging.Outcome.NO_PLAN_FOUND, "precondition: 2000 units of route work"
            + " found " + movesOf(starved));

        assertTrue(starved.ranOutOfSearch(), "the search stopped at its budget and the plan does not say so");

        HomeStaging.Plan whole = HomeStaging.snapshot(railway()).plan();

        assertEquals(movesOf(whole), PINNED, "precondition: the whole budget");

        assertFalse(whole.ranOutOfSearch(), "the search finished in its first share, yet the plan says it ran out");
    }
}
