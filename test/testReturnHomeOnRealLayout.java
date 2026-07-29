import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * "Return to home" against a real layout, driven the way an operator drives it.
 *
 * The other staging tests use small hand-built graphs where every case is constructed deliberately.
 * Those are precise but they share a weakness: the graph was written by the same person as the planner,
 * so it only contains the situations that person thought of.  Every defect this feature actually shipped
 * with came from the opposite direction - a rule of the real layout that the planner did not model:
 * sensors shared between a platform and its bypass, edges that lock other edges, and a path that
 * commands one signal two different ways.  None of them appear in a four-station ring.
 *
 * So this suite runs the operator's own configuration and asserts the property that matters: after
 * autonomy has run and been stopped at an arbitrary moment, every locomotive can get home again.
 * Stopping at a random moment is the point - it is what produces arrangements nobody would think to
 * write down, which is exactly where the planner kept being wrong.
 *
 * The failing case is checked too, and checked properly: a sensor is forced occupied on the way in, the
 * plan is required to fail, and then the sensor is released and the plan is required to succeed again.
 * Without that second half the test would pass just as happily if planning were broken outright.
 *
 * Skipped, not failed, when the configuration is absent - it is the author's layout, not a fixture that
 * ships with the project.
 */
public class testReturnHomeOnRealLayout
{
    private static MarklinControlStation model;

    /** The operator's configuration, read once. */
    private static String config;

    /** How many run-and-recover cycles.  Each one drives real paths, so this is minutes, not seconds. */
    private static final int ROUNDS = 3;

    /** How long autonomy is allowed to run before the graceful stop, in seconds. */
    private static final int RUN_MIN_SECONDS = 4;
    private static final int RUN_MAX_SECONDS = 9;

    /** Long enough for any single path to finish; short enough that a hang fails instead of hanging. */
    private static final long SETTLE_TIMEOUT_MS = 240000;

    private static final Random RANDOM = new Random();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The last argument is debug mode, which simulation requires - without it setSimulate throws
        // and this suite would have no way to move a train without moving a real one.
        model = init(null, true, false, false, true);
        model.stop();

        File file = new File("test/autonomy.json");

        if (!file.exists()) file = new File("autonomy.json");

        if (!file.exists())
        {
            throw new SkipException(
                "No autonomy.json in test/ or the project root - this suite runs the operator's own layout"
            );
        }

        config = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (model != null && model.getAutoLayout() != null)
        {
            model.getAutoLayout().stopLocomotives();
        }
    }

    /**
     * Parses the configuration and makes it run quickly and without hardware.
     */
    private static Layout load() throws Exception
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the configuration produced no graph");

        assertTrue(layout.isValid(),
            "the configuration must parse against this database - " + Layout.getLastError()
            + ".  A locomotive named in the file but missing from the database will do this.");

        // Nothing may reach the track, whatever the file says.  If simulation cannot be turned on the
        // suite must not run at all: it drives locomotives for minutes on end, and doing that to real
        // hardware because a precondition quietly failed is not a test failure worth having.
        try
        {
            layout.setSimulate(true);
        }
        catch (Exception e)
        {
            throw new SkipException("refusing to run - simulation could not be enabled: " + e.getMessage());
        }

        assertTrue(layout.isSimulate(), "simulation must be on before anything is asked to move");

        // The file's delays are there to make trains look real; here they only make the suite slow
        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        assertFalse(layout.getLocomotivesToRun().isEmpty(),
            "the configuration must place at least one locomotive for this suite to mean anything");

        return layout;
    }

    /**
     * Blocks until nothing is moving, or fails - a run that never settles is a defect, not a slow test.
     */
    private static void awaitStopped(Layout layout) throws Exception
    {
        long deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;

        while (layout.isRunning())
        {
            if (System.currentTimeMillis() > deadline)
            {
                fail("locomotives were still running " + (SETTLE_TIMEOUT_MS / 1000)
                    + "s after the graceful stop");
            }

            Thread.sleep(250);
        }
    }

    /**
     * Where everything currently stands, for failure messages worth reading.
     */
    private static String describe(Layout layout)
    {
        List<String> out = new ArrayList<>();

        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() == null) continue;

            Point home = layout.getHomeStation(p.getCurrentLocomotive());

            out.add(p.getCurrentLocomotive().getName() + "@" + p.getName()
                + (home == null ? " (no home)" : home.equals(p) ? "" : " (home " + home.getName() + ")"));
        }

        return out.toString();
    }

    /**
     * Runs autonomy, stops it at an arbitrary moment, and requires everyone to get home again.
     *
     * The arrangement at the moment of the stop is not chosen and not repeatable, which is the whole
     * value: it is how the planner met the shared sensors, the lock edges and the conflicting signal
     * commands that a hand-written fixture never produced.
     */
    @Test
    public void testEveryoneCanGetHomeAfterAutonomyIsStoppedAtRandom() throws Exception
    {
        Layout layout = load();

        // Not necessarily already home.  This assertion used to read ALREADY_HOME, which held only while
        // a home was always the station a locomotive was standing on when the file loaded.  An
        // assignment says where a locomotive *belongs*, which need not be where the file left it - so
        // the operator’s own layout legitimately loads with trains away from home, and requiring
        // otherwise made the feature working look like a test failure.
        //
        // What does have to hold before the rounds begin is that the loaded state is one the planner can
        // work from, or a failure below would belong to the file rather than to the random stop.
        HomeStaging.Outcome loaded = layout.triageReturnToHome();

        if (loaded != HomeStaging.Outcome.ALREADY_HOME)
        {
            assertNull(loaded, "precondition: the loaded graph offers no way home at all - got " + loaded
                + " with " + describe(layout));

            assertTrue(layout.planReturnToHome().isPossible(),
                "precondition: the loaded graph cannot be returned home before any autonomy has run - "
                + describe(layout));
        }

        for (int round = 1; round <= ROUNDS; round++)
        {
            int seconds = RUN_MIN_SECONDS + RANDOM.nextInt(RUN_MAX_SECONDS - RUN_MIN_SECONDS + 1);

            layout.runLocomotives();
            Thread.sleep(seconds * 1000L);

            // The graceful stop: trains finish the path they are on rather than halting mid-section
            layout.stopLocomotives();
            awaitStopped(layout);

            String arrangement = describe(layout);

            HomeStaging.Outcome trivial = layout.triageReturnToHome();

            if (trivial == HomeStaging.Outcome.ALREADY_HOME)
            {
                // Everything happened to finish where it started; nothing to prove this round
                continue;
            }

            assertNull(trivial,
                "round " + round + ": nothing should have removed the homes - got " + trivial
                + " with " + arrangement);

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertTrue(plan.isPossible(),
                "round " + round + ": no way home from " + arrangement
                + " (outcome " + plan.getOutcome() + ", blocked " + plan.getBlocked() + ")");

            layout.loadReturnToHomeTimetable();

            assertTrue(layout.executeTimetable(),
                "round " + round + ": the plan was accepted but a move gave up on the way, from "
                + arrangement);

            awaitStopped(layout);

            assertEquals(layout.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME,
                "round " + round + ": the run finished but not everyone is home - " + describe(layout)
                + ", started from " + arrangement);
        }
    }

    /**
     * A sensor held occupied on the way in makes the plan fail - and releasing it makes it work.
     *
     * The second half is what gives the first half meaning.  A planner that refused everything would
     * satisfy "this must fail" perfectly, so the same arrangement is required to succeed once the
     * obstruction is gone.  That pins the refusal to the sensor rather than to the planner being broken.
     */
    @Test
    public void testASensorHeldOccupiedOnTheWayInMakesTheReturnImpossible() throws Exception
    {
        Layout layout = load();

        // Displace something so that there is a journey to block in the first place
        Locomotive displaced = null;
        Point home = null;
        Point elsewhere = null;

        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() == null) continue;
            if (layout.getHomeStation(p.getCurrentLocomotive()) == null) continue;

            for (Point free : layout.getPoints())
            {
                if (free.getCurrentLocomotive() != null || !free.isDestination() || !free.isActive()) continue;
                if (free.equals(p) || free.getS88() == null) continue;

                if (layout.moveLocomotive(p.getCurrentLocomotive().getName(), free.getName(), false))
                {
                    displaced = free.getCurrentLocomotive();
                    home = layout.getHomeStation(displaced);
                    elsewhere = free;
                    break;
                }
            }

            if (displaced != null) break;
        }

        if (displaced == null)
        {
            throw new SkipException("could not displace any locomotive on this layout, so nothing to block");
        }

        assertNotNull(home.getS88(), "precondition: the home station must have a sensor to hold occupied");

        assertTrue(layout.planReturnToHome().isPossible(),
            displaced.getName() + " should be able to get from " + elsewhere.getName()
            + " back to " + home.getName() + " before anything is blocked");

        // Hold the home station's own sensor occupied.  Nothing the planner can move accounts for it, so
        // it stays blocked for the whole plan - the way an unmodelled obstruction on the track behaves.
        model.setFeedbackState(home.getS88(), true);

        try
        {
            HomeStaging.Plan blockedPlan = layout.planReturnToHome();

            assertFalse(blockedPlan.isPossible(),
                "sensor " + home.getS88() + " is occupied, so nothing can enter " + home.getName()
                + " - the plan must not claim otherwise (got " + blockedPlan.getOutcome() + ")");
        }
        finally
        {
            model.setFeedbackState(home.getS88(), false);
        }

        // The control: same arrangement, obstruction gone, plan works again
        assertTrue(layout.planReturnToHome().isPossible(),
            "releasing sensor " + home.getS88() + " must make the same journey possible again - if not,"
            + " the refusal above proved nothing about the sensor");
    }
}
