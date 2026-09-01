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
 * Five trains on the operator's own stations go out, come back, and face the way they set off.
 *
 * Adam asked for exactly this arrangement: "place a non reversing train on TunnelLeftPark,
 * TunnelRightPark, and TunnelCenterPark.  Place a normal train at BottomMainA and a reversing train
 * facing west on BottomMainC.  Then run for a while, send home and ensure they are where they started,
 * facing the same direction as when they started."
 *
 * **Why the arrangement is the point.** The three Tunnel berths are parking terminuses - a train can
 * only leave one by reversing - and the trains standing in them cannot reverse, so each of them can
 * only get home by BACKING IN past a reversing point. That is the rule of 2026-08-31, and this is it
 * on the real railway rather than on a three-point fixture. BottomMainA and BottomMainC are main-line
 * platforms drawn as more than one graph Point, so they are the case where "the home is the square"
 * and "facing the same direction" are two different claims.
 *
 * **Facing is the Point, not the square.** A square that trains can enter from two directions is
 * emitted as one Point per arrival side, and which copy a train stands on IS which way it is facing -
 * the model has no separate direction. So "facing the same direction as when they started" is asserted
 * as the same Point, and on a split platform that is strictly stronger than being home.
 *
 * **THIS TEST FAILS TODAY, and that is what it is for.** Return Home answers NO_PLAN_FOUND for this
 * arrangement on his railway. Three causes were eliminated by experiment before it was written up:
 *
 *   - it is NOT the backing-in rule added on 2026-08-31 - disabling `mustBackIn` gives the same
 *     answer;
 *   - it is NOT the search budget - raising SEARCH_BUDGET_MS from 15s to 90s gives the same answer;
 *   - it is NOT the fifth train standing on a copy trains cannot be SENT to - putting all five on
 *     destination copies gives the same answer.
 *
 * So it is structural, and it wants somebody who knows what the plan ought to be. Until then this
 * class is deliberately NOT registered in build.xml: a permanently red battery would cost more than
 * this test earns, and the finding is written up in the tracker instead of being shouted every run.
 *
 * **It runs against a COPY of his layout.** `cs2_sample_layout` is his real railway and is not
 * recoverable; `LayoutSandbox.open(folder)` copies it somewhere temporary and points the layout
 * preference at the copy, so the original is only ever read.
 *
 * @author Adam
 */
public class testTheParkingBerthsGetTheirTrainsBack
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static Layout layout;

    /** Addresses of its own, high enough not to collide with anything on the real database. */
    private static final int FIRST_ADDRESS = 2101;

    /** How long autonomy is allowed to rearrange the railway before everyone is sent home. */
    private static final int RUN_SECONDS = 20;

    private static final long SETTLE_TIMEOUT_MS = 180000;

    /** Where each train was put, which is also which way it was facing. */
    private static final Map<String, String> STARTED_AT = new LinkedHashMap<>();

    private static final String[] BERTHS = {"TunnelLeftPark", "TunnelRightPark", "TunnelCenterPark"};

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File live = new File("cs2_sample_layout");

        if (!live.isDirectory())
        {
            throw new SkipException("cs2_sample_layout is not here - this suite runs his own stations");
        }

        // The COPY, before the model, because init reads the layout preference.
        sandbox = support.LayoutSandbox.open(live);

        // Debug mode, which simulation requires.
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

    @AfterClass
    public static void tearDownClass()
    {
        try
        {
            if (layout != null) layout.stopLocomotives();
        }
        catch (Exception ignored) { }

        if (model != null)
        {
            for (int i = 0; i < 5; i++)
            {
                try { model.deleteLoc(name(i)); } catch (Exception ignored) { }
            }
        }

        if (sandbox != null) sandbox.close();
    }

    private static String name(int i)
    {
        return "PB test loc " + i;
    }

    /**
     * The arrangement Adam asked for, and everyone back on the square they left, facing as they left.
     */
    @Test
    public void testEveryoneComesBackFacingTheWayTheySetOff() throws Exception
    {
        place();

        assertEquals(STARTED_AT.size(), 5, "not every train was placed, so this proves nothing");

        // A floor on the fixture: the berths must really be terminuses, or the backing-in rule this
        // arrangement was chosen for is not being exercised at all.
        int berths = 0;

        for (String berth : BERTHS)
        {
            Point p = layout.getPoint(berth);

            if (p != null && p.isTerminus()) berths++;
        }

        assertEquals(berths, BERTHS.length,
            "the parking berths are not terminuses on this setup, so nothing here has to back in");

        // Run for a while, then let the trains finish the paths they are on.
        layout.runLocomotives();

        Thread.sleep(RUN_SECONDS * 1000L);

        layout.stopLocomotives();

        awaitStopped();

        String arrangement = describe();

        // Then send everybody home.
        HomeStaging.Outcome trivial = layout.triageReturnToHome();

        if (trivial != HomeStaging.Outcome.ALREADY_HOME)
        {
            assertNull(trivial,
                "the homes went away while the railway ran - got " + trivial + " from " + arrangement);

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertTrue(plan.isPossible(),
                "no way home from " + arrangement + " (outcome " + plan.getOutcome()
                + ", blocked " + plan.getBlocked() + "). The three berths are terminuses and their "
                + "trains cannot reverse, so each of them has to back in past a reversing point");

            layout.loadReturnToHomeTimetable();

            assertTrue(layout.executeTimetable(),
                "the plan was accepted but a move gave up on the way, from " + arrangement);

            awaitStopped();
        }

        // AND THE WHOLE POINT: the same Point, not merely the same square.
        List<String> wrong = new ArrayList<>();

        for (Map.Entry<String, String> e : STARTED_AT.entrySet())
        {
            Locomotive loc = model.getLocByName(e.getKey());

            Point now = layout.getLocomotiveLocation(loc);

            String where = now == null ? "nowhere" : now.getName();

            if (!e.getValue().equals(where))
            {
                wrong.add(e.getKey() + " set off from " + e.getValue() + " and is at " + where);
            }
        }

        assertEquals(wrong.toString(), "[]",
            "trains did not come back to where they started, facing the way they started. A square a "
            + "train can enter from two directions is emitted as one Point per arrival side, so the "
            + "Point is the facing - coming back on the other copy is coming back the wrong way round. "
            + "Ran from " + arrangement);
    }

    /**
     * Five locomotives of its own, placed where Adam asked for them.
     */
    private static void place() throws Exception
    {
        String[] where = new String[]
        {
            BERTHS[0], BERTHS[1], BERTHS[2], "BottomMainA", "BottomMainC"
        };

        for (int i = 0; i < where.length; i++)
        {
            model.newMM2Locomotive(name(i), FIRST_ADDRESS + i);

            Locomotive loc = model.getLocByName(name(i));

            assertNotNull(loc, "could not create " + name(i));

            // The three in the berths cannot reverse; the one on BottomMainA is an ordinary train; the
            // one on BottomMainC is a reversing train, which is what Adam asked for.
            loc.setReversible(i >= 3);

            loc.setPreferredSpeed(35);

            Point target = pointFor(where[i], null);

            assertNotNull(target, "no point named " + where[i] + " on his setup");

            assertTrue(layout.moveLocomotive(name(i), target.getName(), false),
                "could not place " + name(i) + " on " + target.getName());

            // THE ONE FACING WEST, which cannot be reached through the placement door.
            //
            // Adam asked for "a reversing train facing west on BottomMainC". Both westbound copies of
            // that platform are NOT destinations, because his own setup bars arrivals from the east
            // there - so moveLocomotive refuses them, and a train can only be SENT to it facing east.
            //
            // Standing there facing west is a real state of his railway even so, and it is the state
            // he asked about, so the train is put on that copy directly. Whether Return Home can ever
            // restore it is exactly the question this test exists to answer.
            if (i == 4)
            {
                Point west = facingCopy(where[i], "west");

                assertNotNull(west, "BottomMainC has no westbound copy at all on his setup");

                west.setLocomotive(loc);

                target = west;
            }

            System.out.println("PLACING " + name(i) + " reversible=" + loc.isReversible()
                + " on " + target.getName() + " (destination=" + target.isDestination() + ")");

            STARTED_AT.put(name(i), target.getName());
        }
    }

    /**
     * Any copy of a square facing a given way, whether or not trains may be SENT there.
     */
    private static Point facingCopy(String station, String facing)
    {
        for (Point p : layout.getPoints())
        {
            if (!p.getName().startsWith(station + " (")) continue;

            if (p.getName().toLowerCase().contains(facing)) return p;
        }

        return null;
    }

    /**
     * The Point for a station, choosing the copy that faces a given way where the square has copies.
     *
     * A square trains can enter from two directions is emitted as one Point per arrival side, and the
     * builder puts the direction in the name - "BottomMainC (westbound)". Where a square has only one
     * copy the direction is not modelled and the name is bare.
     */
    private static Point pointFor(String station, String facing)
    {
        // Only the copies a train may actually be SENT to.
        //
        // A square trains can enter from two directions is emitted as one Point per arrival side, and
        // not every copy is a destination - "BottomMainA (westbound) is not a station" is what the
        // model says about the other kind. Placing is refused there, so the choice has to be made
        // among the copies that are stations.
        List<Point> stations = new ArrayList<>();
        List<String> all = new ArrayList<>();

        for (Point p : layout.getPoints())
        {
            if (!p.getName().equals(station) && !p.getName().startsWith(station + " (")) continue;

            all.add(p.getName() + (p.isDestination() ? "" : " [not a station]"));

            if (p.isDestination()) stations.add(p);
        }

        assertFalse(stations.isEmpty(),
            "no copy of " + station + " is a station on his setup, so nothing can be placed there. "
            + "Copies found: " + all);

        if (facing == null) return stations.get(0);

        for (Point p : stations)
        {
            if (p.getName().toLowerCase().contains(facing)) return p;
        }

        // FAILED, not skipped.  A skip here would hide the one thing this arrangement was chosen to
        // exercise, and read as green while asking nothing.
        fail(station + " has no copy facing " + facing + " that a train can be placed on. Copies "
            + "found: " + all);

        return null;
    }

    /**
     * Waits for every driving thread to finish, so the arrangement being asserted is a settled one.
     */
    private static void awaitStopped() throws Exception
    {
        long until = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;

        while (System.currentTimeMillis() < until)
        {
            if (!layout.isRunning()) return;

            Thread.sleep(250);
        }

        fail("the railway was still running " + (SETTLE_TIMEOUT_MS / 1000) + " seconds after being "
            + "asked to stop, so nothing below could be trusted");
    }

    /**
     * Where everybody is, for a failure message somebody can act on.
     */
    private static String describe()
    {
        StringBuilder out = new StringBuilder();

        for (String who : STARTED_AT.keySet())
        {
            Locomotive loc = model.getLocByName(who);

            Point at = loc == null ? null : layout.getLocomotiveLocation(loc);

            out.append(out.length() == 0 ? "" : ", ")
               .append(who).append(" at ").append(at == null ? "nowhere" : at.getName());
        }

        return out.toString();
    }
}
