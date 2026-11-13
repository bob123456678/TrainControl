package core;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;
import support.Reflect;
import support.Scenario;

/**
 * The same setup gives the same Return Home plan every time, on any machine at any load (OB-413): the search's budget
 * is route work, not time.
 *
 * His frozen railway with its own four trains standing as saved, and the homes the old-setups proof gives them in its
 * fourth round: an arrangement with a 4-move plan and an 8-move one.  The plan pinned below is the 4-move one, which
 * the build before also gave whenever its first search ran out of time.
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
     * The clock has no say: the planner reads none, so a slow or busy machine gets the plan a fast one finds, never
     * NO_PLAN_FOUND where a fast one finds a plan.
     *
     * The compiled planner, its nested and anonymous classes and lambdas included, names no clock: read from the class
     * files, so no spelling of the source hides one.
     *
     * MUTATION: put back a fifteen-second deadline read from `new java.util.Date().getTime()`, and this fails.
     */
    @Test(timeOut = 300000)
    public void testTheSamePlanWhateverTheClockSays() throws Exception
    {
        List<Class<?>> planner = new ArrayList<>();

        planner.add(HomeStaging.class);

        for (int i = 0; i < planner.size(); i++)
        {
            Class<?> c = planner.get(i);

            planner.addAll(java.util.Arrays.asList(c.getDeclaredClasses()));

            for (int n = 1; HomeStaging.class.getResource("/" + c.getName().replace('.', '/') + "$" + n + ".class") != null; n++)
            {
                planner.add(Class.forName(c.getName() + "$" + n));
            }
        }

        for (Class<?> c : planner)
        {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();

            try (java.io.InputStream in = HomeStaging.class.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class"))
            {
                assertNotNull(in, "precondition: no class file for " + c.getName());

                byte[] chunk = new byte[8192];

                for (int got; (got = in.read(chunk)) > 0; ) bytes.write(chunk, 0, got);
            }

            // The constant pool names every class and method the code calls, in plain ASCII
            String names = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);

            for (String clock : new String[] {"currentTimeMillis", "nanoTime", "java/util/Date", "java/util/Calendar",
                "java/time/", "LongSupplier"})
            {
                assertFalse(names.contains(clock), c.getName() + " reads the time (" + clock + "), so a slower machine can"
                    + " get a different plan");
            }
        }
    }

    /**
     * Seven trains on his railway get the 7-move plan 3.0.8 finds on its clock: the unit tracks time whatever the train
     * count, so the search from where they stand gets the share of the budget its time would have bought.
     *
     * MUTATION: count an edge as one unit plus a charge for each train with a length, in a 90 million budget, and this
     * answers NO_PLAN_FOUND.
     */
    @Test(timeOut = 300000)
    public void testSevenTrainsGetThePlanTheClockFound() throws Exception
    {
        model.parseAuto(built);

        Layout layout = model.getAutoLayout();

        String[][] standing = {{"BottomInnerOtherside", "BR 03 1022"}, {"ParkingTrack4", "Re4/4II 11229SBB"},
            {"TopR1ParkLong", "SBBC 421 378-1"}};

        for (String[] train : standing) layout.getPoint(train[0]).setLocomotive(model.getLocByName(train[1]));

        layout.clearHomeLocomotives();
        layout.setHomeLocomotive("RampDown (southbound)", "2-8-4 3505 SP");
        layout.setHomeLocomotive("BottomMainPost (northbound)", "SBBC 421 378-1");
        layout.setHomeLocomotive("ParkingTrack5", "EN57-947");
        layout.setHomeLocomotive("TopMainR1", "75 407 DB");
        layout.setHomeLocomotive("Tunnel (southbound)", "Re4/4II 11229SBB");
        layout.setHomeLocomotive("BottomMainB (eastbound)", "EN57-203");
        layout.setHomeLocomotive("BottomInnerOtherside", "BR 03 1022");

        assertEquals(layout.getHomeStations().size(), 7, "precondition: seven homes");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(movesOf(plan), "READY [EN57-203 -> BottomMainB (8), EN57-947 -> ParkingTrack5 (6),"
            + " SBBC 421 378-1 -> ParkingTrack6 (13), 2-8-4 3505 SP -> RampDown (4), 75 407 DB -> TopMainR1 (7),"
            + " SBBC 421 378-1 -> BottomMainPost (8), Re4/4II 11229SBB -> Tunnel (5)]", "seven trains");
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

        Field budget = Reflect.field(HomeStaging.class, "budget");
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
