package core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Return Home finds a plan on a crowded railway where one exists - the shortest where the time allows, and some plan
 * where it does not (OB-230).
 *
 * Adam, deferring this until his railway was measured: *"I want to do the heuristic, but I think this needs to be
 * deferred until I deliver you the fully measured layout."*  Measured on it (2026-09-24, `test/operator_layout`), four
 * trains come home in a second or two, and six or more answered NO_PLAN_FOUND at the fifteen-second budget - and at
 * ten times the budget.  Counted: fifteen seconds examined 3,852 arrangements and spent 14.4 of them in 643,129 route
 * searches, some thirty-three onward moves an arrangement.  A search for the SHORTEST plan cannot get through that
 * breadth with any estimate that never overstates the moves left; weighting the estimate found a ten-move plan for
 * the arrangement below in under five seconds.  So the budget is shared: the first half looks for the shortest plan,
 * as it always has, and the second looks for any plan from the railway as it stands.
 *
 * The search's clock is stepped a fixed amount a read, so its budget is a count of arrangements rather than of seconds
 * on whatever machine runs this - the same answer every time.
 *
 * No lengths, as in `core.testTrainsComeHomeFromAPinnedArrangement` (Adam, 2026-09-15: *"i recommend relaxing
 * restrictions (track/station lengths) in the layout used for the A* tests"*): this is about the search, not the room
 * rules.
 *
 * @author Adam
 */
public class testReturnHomeFindsAPlanOnAFullRailway
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Layout layout;

    private static final int FIRST_ADDRESS = 2461;

    /** Milliseconds the search's clock moves each time it is read: fifteen seconds is 3,750 reads. */
    private static final long STEP = 4;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory()) throw new SkipException("test/operator_layout is not here - this suite runs his stations");

        sandbox = support.LayoutSandbox.open(frozen);

        model = init(null, true, false, false, true);
        model.stop();

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        org.traincontrol.automationui.AutonomySession session =
            new org.traincontrol.automationui.AutonomySession(sandbox.getFolder());

        session.open(pages);

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null || !layout.isValid()) throw new SkipException("his setup did not parse here: " + Layout.getLastError());

        for (int i = 0; i < 6; i++) model.newMM2Locomotive(name(i), FIRST_ADDRESS + i);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null)
        {
            for (int i = 0; i < 6; i++)
            {
                try { model.deleteLoc(name(i)); } catch (Exception ignored) { }
            }

            model.stop();
        }

        if (sandbox != null) sandbox.close();
    }

    private static String name(int i)
    {
        return "full railway probe " + i;
    }

    /**
     * Six trains, one of them already home, the others each standing on somebody else's platform - an arrangement a run
     * leaves.  NO_PLAN_FOUND before OB-230, whatever the budget; a plan now, and one that brings everybody home.
     *
     * MUTATION: search only for the shortest plan, for the whole budget, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testSixTrainsOnAFullRailwayComeHome() throws Exception
    {
        String[] homes = {"RampDown (southbound)", "BottomMainPost (northbound)", "TopMainR2", "TopMainR1",
            "Tunnel (southbound)", "LowerParkingOuter"};
        String[] standing = {"BottomMainPost (northbound)", "TopMainR2", "BottomInner (northbound)",
            "RampDown (southbound)", "TopMainR1", "LowerParkingOuter"};

        HomeStaging.Plan plan = plan(homes, standing);

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY, "six trains on his measured railway, each with a way"
            + " home, answered " + plan.getOutcome() + " - the search spent its whole budget looking for the shortest"
            + " plan through some thirty-three onward moves an arrangement (OB-230)");

        assertEverybodyHome(plan, homes, standing);
    }

    /**
     * An arrangement the shortest-plan search solves in its half of the budget still gets the shortest plan (OB-230).
     *
     * Four trains each moved one platform along: six moves, as before OB-230.  The any-plan half is reached only when
     * the first half finds nothing, so where a shortest plan is found it is the one given.
     *
     * MUTATION: search for any plan first, and this fails on the number of moves.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAnEasyArrangementStillGetsTheShortestPlan() throws Exception
    {
        String[] homes = {"RampDown (southbound)", "BottomMainPost (northbound)", "TopMainR2", "TopMainR1"};
        String[] standing = {"Tunnel (southbound)", "RampDown (southbound)", "BottomMainPost (northbound)", "TopMainR2"};

        HomeStaging.Plan plan = plan(homes, standing);

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY, "precondition: four trains one platform along have"
            + " no plan");

        assertEquals(plan.getMoves().size(), 6, "four trains one platform along were brought home in "
            + plan.getMoves().size() + " moves, where the shortest plan is six: " + plan.getMoves());

        assertEverybodyHome(plan, homes, standing);
    }

    private static HomeStaging.Plan plan(String[] homes, String[] standing) throws Exception
    {
        for (Point p : new ArrayList<>(layout.getPoints()))
        {
            if (p.getCurrentLocomotive() != null) layout.moveLocomotive(null, p.getName(), true);
        }

        layout.clearHomeLocomotives();

        for (int i = 0; i < homes.length; i++)
        {
            Locomotive loc = model.getLocByName(name(i));

            loc.setTrainLength(0);

            // A MIXTURE, as the pinned class has it: the ones that cannot reverse turn only on the way home.
            loc.setReversible(i % 2 == 1);

            assertNotNull(layout.getPoint(homes[i]), "precondition: his frozen railway has no " + homes[i]);

            assertTrue(layout.moveLocomotive(name(i), homes[i], false), "could not place " + name(i) + " on " + homes[i]);

            layout.setHomeLocomotive(homes[i], name(i));
        }

        for (Point p : new ArrayList<>(layout.getPoints()))
        {
            if (p.getCurrentLocomotive() != null) layout.moveLocomotive(null, p.getName(), true);
        }

        for (int i = 0; i < standing.length; i++)
        {
            assertTrue(layout.moveLocomotive(name(i), standing[i], false), "could not stand " + name(i) + " on "
                + standing[i]);
        }

        HomeStaging staging = HomeStaging.snapshot(layout);

        final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();

        java.lang.reflect.Field clock = HomeStaging.class.getDeclaredField("clock");

        clock.setAccessible(true);
        clock.set(staging, (java.util.function.LongSupplier) () -> now.addAndGet(STEP));

        return staging.plan();
    }

    /**
     * Where the plan's moves leave each train, and that it is home.
     */
    private static void assertEverybodyHome(HomeStaging.Plan plan, String[] homes, String[] standing)
    {
        Map<String, Point> at = new LinkedHashMap<>();

        for (int i = 0; i < standing.length; i++) at.put(name(i), layout.getPoint(standing[i]));

        for (HomeStaging.Move move : plan.getMoves())
        {
            assertEquals(move.getPath().get(0).getStart().isSamePlaceAs(at.get(move.getLocomotive().getName())), true,
                "a move starts somewhere the train is not: " + move);

            at.put(move.getLocomotive().getName(), move.getEnd());
        }

        for (int i = 0; i < homes.length; i++)
        {
            assertTrue(at.get(name(i)).isSamePlaceAs(layout.getPoint(homes[i])), name(i) + " is left on "
                + at.get(name(i)).getName() + ", not at its home " + homes[i] + ": " + plan.getMoves());
        }
    }
}
