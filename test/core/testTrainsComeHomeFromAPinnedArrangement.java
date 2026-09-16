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
 * Five trains are displaced to a FIXED arrangement and brought home, facing the way they set off.
 *
 * The pinned half of a pair Adam asked for on 2026-09-15: *"make a pinned version and keep the current version,
 * aiming for both to be true.  normal autonomy runs should always have a solution."*
 *
 * `core.testTrainsComeHomeToTheirPlatforms` is the other half.  It lets autonomy rearrange the railway for twenty
 * seconds and then sends everybody home, so it starts from a different arrangement on every run - which is the
 * realistic question and an ambiguous verdict: it failed once and passed once on identical code during the AMW round,
 * and a red from it cannot be told from chance without re-running.  This class asks the same question of an
 * arrangement that does not move, so a red here is always a regression.
 *
 * **THE ARRANGEMENT IS DERIVED, NOT TRANSCRIBED.**  Each train is displaced onto the ordinary copy of the NEXT
 * platform in the list, which is a fixed function of his frozen diagram rather than a list of copy names typed from a
 * failure message.  A square that has no copy a train may be sent to fails the fixture loudly instead of skipping,
 * for the reason the other class records: a test that absorbs its own failures reads as green while proving nothing.
 *
 * **Facing is the Point, not the square**, and the turning twin of an arrival counts as that arrival - both as the
 * other class has them, so the two make the same claim about the same railway.
 *
 * ON THE FROZEN COPY, never `cs2_sample_layout` (Adam, 2026-09-01: *"let's get the current diagram frozen in the
 * test"*).
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
            throw new SkipException("test/operator_layout is not here - this suite runs his stations");
        }

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

        if (layout == null || !layout.isValid())
        {
            throw new SkipException("his setup did not parse here: " + Layout.getLastError());
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

    /**
     * Everyone home from an arrangement that is the same on every run.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testEveryoneComesHomeFromTheSameArrangementEveryTime() throws Exception
    {
        place();

        assertEquals(STARTED_AT.size(), PLATFORMS.length, "not every train was placed, so this proves nothing");

        // DISPLACED BY HAND, ONE PLATFORM LEFT FREE, AND NO MOVE LANDING ON AN OCCUPIED SQUARE.
        //
        // Two earlier versions of this arrangement were wrong, and both in ways worth recording:
        //
        //  - all five shifted onto the next platform along is a five-way cycle with NO spare square, where every home
        //    is held by a train that must move first.  Return Home answered NO_PLAN_FOUND, which may well be right -
        //    shunting needs somewhere to shunt to - so pinning it would have asserted something his railway cannot do;
        //  - four shifted up the list with the fifth left at home puts the fourth train onto the fifth's platform,
        //    and `moveLocomotive` DISPLACES whoever is there: the fifth train was pushed off the railway altogether
        //    and the claim failed with it "nowhere".
        //
        // A rotation among five occupied platforms has NO collision-free order in either direction - walking up puts a
        // train on the next one's square, walking down puts it on the previous one's, and both are still occupied
        // because nothing has moved yet.  That is the closed cycle of the first version wearing a different hat.
        //
        // So one square is emptied first: train 0 goes somewhere that is nobody's home, and then trains 1 to 4 walk
        // DOWN onto the platform below each, every target having just been vacated.  Four trains end one platform
        // down, train 0 ends off the five altogether, the top platform is free, and no move displaces anybody - which
        // the assertion in the loop holds, so a future edit cannot silently push a train off the railway again.
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
     * His trains and homes off, five of ours on the platforms, each homed where it stands.
     *
     * The same sequence the live class uses, and for the same reasons recorded there: his locomotives are purged so the
     * question is about five trains rather than eight, and his home ASSIGNMENTS are cleared too - a square holds one
     * home, and a claim on a square another home already holds is refused silently, which once left two test trains
     * homeless and the plan free to abandon them.
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
     * An ordinary station outside the five platforms, so one square can be emptied before the rotation.
     *
     * Taken from the layout rather than named here: a list of square names typed into a test is a photograph of
     * somebody's diagram, and this one has to hold on his frozen copy whatever it contains.  Ordinary means what it
     * means elsewhere in this class - a destination, active, and not a terminus - and it must be nobody's home, or
     * emptying a square would fill another one.
     *
     * @return the first such station, or null when his setup has none
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
