package core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
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
 * Five trains are displaced to a FIXED arrangement and brought home, facing the way they set off.
 *
 * The pinned half of a pair: Adam, *"make a pinned version and keep the current version, aiming for both to be true.
 * normal autonomy runs should always have a solution."*  `core.testTrainsComeHomeToTheirPlatforms` is the other half:
 * it lets autonomy rearrange the railway for twenty seconds, so it starts from a different arrangement on every run
 * and a red from it cannot be told from chance without re-running.  This class asks the same question of an
 * arrangement that does not move, so a red here is always a regression.
 *
 * The arrangement is derived, not transcribed: each train is displaced onto the ordinary copy of the NEXT platform in
 * the list, a fixed function of his frozen diagram.  A square with no copy a train may be sent to fails the fixture
 * loudly instead of skipping.  Facing is the Point, not the square, and the turning twin of an arrival counts as that
 * arrival, as in the other class.
 *
 * ON THE FROZEN COPY, never `cs2_sample_layout` (Adam: *"let's get the current diagram frozen in the test"*).
 *
 * @author Adam
 */
public class testTrainsComeHomeFromAPinnedArrangement
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Layout layout;

    /** Addresses of its own, clear of the live class's 2101 upward. */
    private static final int FIRST_ADDRESS = 2111;

    private static final long SETTLE_TIMEOUT_MS = 180000;

    /** Where each train was homed, which is also which way it was facing when it set off. */
    private static final Map<String, String> STARTED_AT = new LinkedHashMap<>();

    /** The same five ordinary platforms the live class uses. */
    private static final String[] PLATFORMS =
    {
        "BottomMainA", "TopMainR1", "TopMainR2", "Tunnel", "LowerFront"
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory())
        {
            throw new AssertionError("test/operator_layout is not here - this suite runs his stations");
        }

        sandbox = support.LayoutSandbox.open(frozen);

        model = support.TestStation.simulatedInDebug();
        model.stop();

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        org.traincontrol.automationui.AutonomySession session =
            new org.traincontrol.automationui.AutonomySession(sandbox.getFolder());

        session.open(pages);

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null || !layout.isValid())
        {
            throw new AssertionError("his setup did not parse here: " + Layout.getLastError());
        }

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (layout != null) layout.stopLocomotives();
        }
        catch (Exception ignored) { }

        if (model != null)
        {
            for (int i = 0; i < PLATFORMS.length; i++)
            {
                try { model.deleteLoc(name(i)); } catch (Exception ignored) { }
            }
        }

        if (sandbox != null) sandbox.close();
    }

    private static String name(int i)
    {
        return "PA test loc " + i;
    }

    /** Everyone home from an arrangement that is the same on every run. */
    @Test
    public void testEveryoneComesHomeFromTheSameArrangementEveryTime() throws Exception
    {
        place();

        assertEquals(STARTED_AT.size(), PLATFORMS.length, "not every train was placed, so this proves nothing");

        // Displaced by hand, one platform left free, and no move landing on an occupied square.  A rotation among five
        // occupied platforms has NO collision-free order in either direction, and shifting all five onto the next
        // platform is a closed cycle with no spare square (Return Home answered NO_PLAN_FOUND, which may well be right:
        // shunting needs somewhere to shunt to).  Four shifted up with the fifth left at home puts the fourth onto the
        // fifth's platform, and `moveLocomotive` DISPLACES whoever is there.  So train 0 goes somewhere that is
        // nobody's home, then trains 1 to 4 walk DOWN onto the platform below each, every target having just been
        // vacated: four end one platform down, the top platform is free, and no move displaces anybody, which the
        // assertion in the loop holds.
        List<String> displacedTo = new ArrayList<>();

        Point elsewhere = aStationThatIsNobodysHome();

        assertNotNull(elsewhere,
            "his setup has no ordinary station outside the five platforms for the first train to stand on, so this"
            + " arrangement cannot be built");

        assertTrue(layout.moveLocomotive(name(0), elsewhere.getName(), false),
            "could not displace " + name(0) + " onto " + elsewhere.getName());

        displacedTo.add(name(0) + " at " + elsewhere.getName());

        for (int i = 1; i < PLATFORMS.length; i++)
        {
            Point to = ordinaryCopy(PLATFORMS[i - 1]);

            assertNotNull(to, "no copy of " + PLATFORMS[i - 1] + " accepts a train");

            assertNull(to.getCurrentLocomotive(),
                "fixture: " + to.getName() + " still holds a train, so displacing " + name(i)
                + " onto it would push that one off the railway");

            assertTrue(layout.moveLocomotive(name(i), to.getName(), false),
                "could not displace " + name(i) + " onto " + to.getName());

            displacedTo.add(name(i) + " at " + to.getName());
        }

        // EVERY TRAIN IS AWAY FROM HOME, or the claim below is satisfied by doing nothing.
        assertNotEquals(layout.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME,
            "precondition: the displaced arrangement counts as already home, so nothing would be planned: "
            + displacedTo);

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(),
            "no way home from the pinned arrangement " + displacedTo + " (outcome " + plan.getOutcome()
            + ", blocked " + plan.getBlocked() + ").  This arrangement does not change between runs, so this is a"
            + " regression rather than an unlucky scatter");

        List<String> planned = new ArrayList<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            planned.add(move.getLocomotive().getName() + " -> " + move.getEnd().getName());
        }

        layout.loadReturnToHomeTimetable();

        assertTrue(layout.executeTimetable(),
            "the plan was accepted but a move gave up on the way, from " + displacedTo + "\nplan was: " + planned);

        awaitStopped();

        List<String> wrong = new ArrayList<>();

        for (Map.Entry<String, String> e : STARTED_AT.entrySet())
        {
            Locomotive loc = model.getLocByName(e.getKey());

            Point now = layout.getLocomotiveLocation(loc);

            String where = now == null ? "nowhere" : now.getName();

            if (!arrival(e.getValue()).equals(arrival(where)))
            {
                wrong.add(e.getKey() + " set off from " + e.getValue() + " and is at " + where);
            }
        }

        assertEquals(wrong.toString(), "[]",
            "trains did not come back to where they started, facing the way they started.  The Point is the facing, and"
            + " the turning copy of an arrival counts as that arrival.  Displaced to " + displacedTo
            + "\nplan was: " + planned);
    }

    /**
     * His trains and homes off, five of ours on the platforms, each homed where it stands: his locomotives are purged
     * so the question is about five trains, and his home ASSIGNMENTS cleared, as a claim on a square another home holds
     * is refused silently.
     */
    private static void place() throws Exception
    {
        int lifted = 0;

        for (Point p : new ArrayList<>(layout.getPoints()))
        {
            if (p.getCurrentLocomotive() == null) continue;

            layout.moveLocomotive(null, p.getName(), true);

            lifted++;
        }

        layout.clearHomeLocomotives();

        System.out.println("LIFTED " + lifted + " of his own trains, and cleared his homes - "
            + layout.getHomeStations().size() + " left");

        for (int i = 0; i < PLATFORMS.length; i++)
        {
            model.newMM2Locomotive(name(i), FIRST_ADDRESS + i);

            Locomotive loc = model.getLocByName(name(i));

            assertNotNull(loc, "could not create " + name(i));

            // A MIXTURE, as the live class has it: the first three cannot reverse, the last two can, so the planner
            // brings both kinds home over the same railway.
            loc.setReversible(i >= 3);

            loc.setPreferredSpeed(35);

            // And no length, deliberately (Adam: *"i recommend relaxing restrictions (track/station lengths) in the
            // layout used for the A* tests"*): the same arrangement has no plan with three-unit trains and an
            // eight-move plan with none, because the room rules decline to judge a train of no length.  This class is
            // about whether the SEARCH can work out an order; the length rules are tested in
            // `core.testTheRoomRuleCensusOnTheRealLayout` and the berth classes.
            loc.setTrainLength(0);

            Point home = ordinaryCopy(PLATFORMS[i]);

            assertNotNull(home, "no copy of " + PLATFORMS[i] + " accepts a train on his setup");

            assertTrue(layout.moveLocomotive(name(i), home.getName(), false),
                "could not place " + name(i) + " on " + home.getName());

            assertTrue(home.isDestination() && home.isActive() && !home.isTerminus(),
                PLATFORMS[i] + " is not an ordinary platform on this setup (" + home.getName() + " terminus="
                + home.isTerminus() + " active=" + home.isActive() + "), which turns this into the parking-berth"
                + " question the live class was moved away from");

            STARTED_AT.put(name(i), home.getName());
        }

        // The homes are where they stand, claimed by the placement itself.
        for (Map.Entry<String, String> e : STARTED_AT.entrySet())
        {
            layout.setHomeLocomotive(e.getValue(), e.getKey());
        }

        assertEquals(layout.getHomeStations().size(), PLATFORMS.length,
            "precondition: not every train kept a home of its own, so the planner is under no obligation to bring the"
            + " others back - which is what made the live class read as abandoning a train");
    }

    /**
     * An ordinary station outside the five platforms, so one square can be emptied before the rotation, taken from the
     * layout rather than named: a destination, active, not a terminus, and nobody's home; null when his setup has none.
     */
    private static Point aStationThatIsNobodysHome()
    {
        List<String> theFive = java.util.Arrays.asList(PLATFORMS);

        for (Point p : layout.getPoints())
        {
            if (!p.isDestination() || !p.isActive() || p.isTerminus()) continue;

            if (p.getCurrentLocomotive() != null) continue;

            // Not one of the five, by square rather than by copy: "Tunnel (southbound)" is Tunnel.
            String square = p.getName().contains(" (")
                ? p.getName().substring(0, p.getName().indexOf(" (")) : p.getName();

            if (theFive.contains(square)) continue;

            if (p.getHomeLoc() != null) continue;

            return p;
        }

        return null;
    }

    /** A copy's name with the turning twin folded onto the plain one, exactly as the live class folds it. */
    private static String arrival(String point)
    {
        String turning = ", reverse)";

        if (!point.endsWith(turning)) return point;

        return point.substring(0, point.length() - turning.length()) + ")";
    }

    /** The copy of a square a train may be sent to and does not have to reverse at, ordinary preferred. */
    private static Point ordinaryCopy(String station)
    {
        Point reversing = null;

        for (Point copy : layout.getPoints())
        {
            boolean sameSquare = copy.getName().equals(station) || copy.getName().startsWith(station + " (");

            if (!sameSquare || !copy.isDestination() || !copy.isActive()) continue;

            if (!copy.isTerminus()) return copy;

            if (reversing == null) reversing = copy;
        }

        return reversing;
    }

    private static void awaitStopped() throws Exception
    {
        long until = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;

        while (System.currentTimeMillis() < until)
        {
            if (!layout.isRunning()) return;

            Thread.sleep(250);
        }

        fail("the railway was still running " + (SETTLE_TIMEOUT_MS / 1000) + " seconds after being asked to stop, so"
            + " nothing below could be trusted");
    }
}
