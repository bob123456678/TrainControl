package core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Return Home brings a train back facing the way it was homed, not merely onto the square (Adam, 2026-09-23).
 *
 * *"yes, it should accomplish the facing.  but it's also reasonable to expect that the input facings are ones realistic
 * on the layout.  we shouldn't allow an impossible facing to be saved."*  And his rule of 2026-08-31 for what a home is:
 * *"the home should just be the logical point, and the direction is wherever the locomotive was facing when it started
 * moving."*
 *
 * **What this morning's run showed.**  With BottomMainA's two plain copies both stations, the pinned arrangement homed a
 * train on the westbound copy, and Return Home brought it back on the EASTBOUND one and called it home: the planner took
 * any copy of the home square as home (MT-165).  A copy is a facing (section 3), so that train came home turned round.
 *
 * **The fixture:** the frozen operator layout with BottomMainA's east bar lifted, as it stood at `2958fcf3` - Adam's
 * follow-up edit of the same day barred arrivals from the east there again, which leaves the square one placeable copy
 * and the facing no choice.  One train, homed on the westbound copy by the running diagram's own door, moved away by
 * hand, and sent home.
 *
 * @author Adam
 */
public class testATrainComesHomeFacingTheWayItWasHomed
{
    private static final String OUR_TRAIN = "facing home probe";

    private static final long SETTLE_TIMEOUT_MS = 180000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Layout layout;
    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory()) throw new SkipException("test/operator_layout is not here");

        sandbox = support.LayoutSandbox.open(frozen);

        model = init(null, true, false, false, true);
        model.stop();

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        session = new AutonomySession(sandbox.getFolder());

        session.open(pages);

        // BOTH PLAIN COPIES OF BOTTOMMAINA PLACEABLE - see the class comment.
        TileKey mainA = null;

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if ("BottomMainA".equals(session.getStore().getPointName(key))) mainA = key;
        }

        assertNotNull(mainA, "his railway has no BottomMainA");

        session.setBarredArrivals(mainA, java.util.Collections.<org.traincontrol.automationui.TilePorts.Side>emptySet());

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null || !layout.isValid()) throw new SkipException("his setup did not parse: " + Layout.getLastError());

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        // NOTHING OF HIS STANDING OR HOMED, so the plan is about this train alone.
        for (Point p : new ArrayList<>(layout.getPoints()))
        {
            if (p.getCurrentLocomotive() != null) layout.moveLocomotive(null, p.getName(), true);
        }

        layout.clearHomeLocomotives();

        model.newMM2Locomotive(OUR_TRAIN, 2399);

        Locomotive train = model.getLocByName(OUR_TRAIN);

        assertNotNull(train, "could not create this class's train");

        train.setReversible(true);
        train.setPreferredSpeed(35);
        train.setTrainLength(0);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (layout != null) layout.stopLocomotives();
        }
        catch (Exception ignored) { }

        try
        {
            if (model != null) model.deleteLoc(OUR_TRAIN);
        }
        catch (Exception ignored) { }

        if (sandbox != null) sandbox.close();
    }

    /**
     * Homed westbound on BottomMainA, moved to RampDown, sent home: it stands westbound.
     *
     * MUTATION: `HomeStaging.atHome` answering by square alone (MT-165) brings it home eastbound.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testItComesHomeOnTheCopyItWasHomedOn() throws Exception
    {
        Point westbound = layout.getPoint("BottomMainA (westbound)");
        Point eastbound = layout.getPoint("BottomMainA (eastbound)");

        assertTrue(westbound != null && westbound.isDestination() && eastbound != null && eastbound.isDestination(),
            "BottomMainA does not have two copies a train may stand on here, so the facing is not a choice and this"
            + " claim cannot fail");

        assertTrue(layout.moveLocomotive(OUR_TRAIN, westbound.getName(), false), "could not stand the train westbound");

        // HOMED THROUGH THE RUNNING DIAGRAM'S DOOR, on the copy it stands on - the facing it has.
        layout.setHomeLocomotive(westbound.getName(), OUR_TRAIN);

        Point away = null;

        for (Point p : layout.getPoints())
        {
            if (p.getName().startsWith("RampDown") && p.isDestination() && p.getCurrentLocomotive() == null) away = p;
        }

        assertNotNull(away, "no copy of RampDown a train may stand on");

        assertTrue(layout.moveLocomotive(OUR_TRAIN, away.getName(), false), "could not move the train to " + away.getName());

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "no way home from " + away.getName() + " (" + plan.getOutcome() + ")");

        layout.loadReturnToHomeTimetable();

        assertTrue(layout.executeTimetable(), "the plan was accepted but a move gave up on the way");

        awaitStopped();

        Point now = layout.getLocomotiveLocation(model.getLocByName(OUR_TRAIN));

        assertNotNull(now, "the train is nowhere after Return Home");

        assertEquals(now.getCopyFacing(), westbound.getCopyFacing(),
            "a train homed facing west on BottomMainA came home on " + now.getName() + ".  Adam, 2026-09-23: \"it should"
            + " accomplish the facing\" - a copy is a facing, and the other copy of the home square is the train turned"
            + " round");
    }

    /**
     * A train standing turned round on its home square - on the turning copy of the arrival it was homed in - is not home
     * (TDY-B1, AUT-B2).
     *
     * A turning copy points back at the side it came in by, so it faces the other way from its plain twin.  Return Home
     * counted the twin as home because the two share an arrival, and on a square trains may turn at, a train left turned
     * round there was reported already home.  On his railway that is BottomMainB, EN57-947's home.
     *
     * MUTATION: `HomeStaging.atHome` accepting the turning twin again fails this.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainTurnedRoundOnItsHomeIsNotHome() throws Exception
    {
        // A PLAIN COPY WITH A TURNING TWIN, both places a train may stand - BottomMainB's where it has one.
        Point plain = null;
        Point twin = null;

        for (Point candidate : layout.getPoints())
        {
            String name = candidate.getName();

            if (!name.endsWith(")") || name.endsWith(", reverse)") || !candidate.isDestination()) continue;

            Point turned = layout.getPoint(name.substring(0, name.length() - 1) + ", reverse)");

            if (turned == null || !turned.isDestination()) continue;

            if (plain == null || name.startsWith("BottomMainB")) { plain = candidate; twin = turned; }
        }

        assertNotNull(plain, "precondition: his railway has no square with a plain copy and a turning twin a train may stand"
            + " on, so being turned round on one's home cannot happen here");

        try
        {
            assertTrue(layout.moveLocomotive(OUR_TRAIN, plain.getName(), false), "could not stand the train on " + plain.getName());

            layout.setHomeLocomotive(plain.getName(), OUR_TRAIN);

            assertTrue(plain.isHomeFacingFixed(), "precondition: the home was not held to the copy it was set on");

            assertTrue(layout.moveLocomotive(OUR_TRAIN, twin.getName(), false), "could not stand the train on " + twin.getName());

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertTrue(plan.getOutcome() != HomeStaging.Outcome.ALREADY_HOME, "a train homed on " + plain.getName() + " and"
                + " standing on " + twin.getName() + " - turned round, facing the other way - is reported already home."
                + "  Adam, 2026-09-23: \"it should accomplish the facing\"");
        }
        finally
        {
            layout.clearHomeLocomotives();

            for (Point p : new ArrayList<>(layout.getPoints()))
            {
                if (p.getCurrentLocomotive() != null && OUR_TRAIN.equals(p.getCurrentLocomotive().getName()))
                {
                    layout.moveLocomotive(null, p.getName(), true);
                }
            }
        }
    }

    /**
     * A home is set facing the way the train faces ON THE RAILWAY, not the way the setup last said (TDY-C2).
     *
     * Adam's rule for the facing: *"Direction it is facing when home is set."*  It was read from the setup's FACING,
     * which names the heading a train set off with until a capture writes the arrival back - so after a run that turned
     * the train at this square, the home was saved facing the way it no longer faces.  `facingOf` was given the
     * running-layout-first reading for exactly this staleness (CONF-B1); the home door is its sibling.
     *
     * MUTATION: read the setup's facing alone again and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAHomeTakesTheFacingTheTrainHasOnTheRailway() throws Exception
    {
        Point plain = layout.getPoint("BottomMainB (eastbound)");
        Point twin = layout.getPoint("BottomMainB (eastbound, reverse)");

        assertTrue(plain != null && plain.isDestination() && twin != null && twin.isDestination(),
            "precondition: BottomMainB has no plain copy and turning twin a train may stand on");

        TileKey square = session.getStationIndex().squareOf(plain.getName());

        assertNotNull(square, "precondition: BottomMainB is not a square of the setup");

        String plainFacing = session.facingsFor(square).get(plain.getName()).name();
        String twinFacing = session.facingsFor(square).get(twin.getName()).name();

        assertTrue(!plainFacing.equals(twinFacing), "precondition: the twin faces the way its plain copy does");

        session.setRunningLayoutSource(() -> layout);

        try
        {
            // THE SETUP SAYS IT STANDS HERE FACING EAST...
            session.placeLocomotive(square, OUR_TRAIN);
            session.setFacing(square, org.traincontrol.automationui.TilePorts.Side.valueOf(plainFacing));

            // ...AND ON THE RAILWAY IT HAS TURNED, uncaptured.
            assertTrue(layout.moveLocomotive(OUR_TRAIN, twin.getName(), false), "could not stand the train on " + twin.getName());

            session.setHome(square, OUR_TRAIN);

            assertEquals(session.getPointProperty(square, org.traincontrol.automationui.AutonomyBuilder.HOME_FACING),
                twinFacing, "the train stands on " + twin.getName() + ", facing " + twinFacing + ", and its home there was"
                + " set facing the way the setup last said.  Adam: \"Direction it is facing when home is set\"");
        }
        finally
        {
            session.setHome(square, null);
            session.placeLocomotive(square, null);
            session.setRunningLayoutSource(null);

            layout.clearHomeLocomotives();

            for (Point p : new ArrayList<>(layout.getPoints()))
            {
                if (p.getCurrentLocomotive() != null && OUR_TRAIN.equals(p.getCurrentLocomotive().getName()))
                {
                    layout.moveLocomotive(null, p.getName(), true);
                }
            }
        }
    }

    /**
     * A home held to a facing the train cannot stand in there is IMPOSSIBLE, with the reason - not a search that
     * exhausts and says "maybe" (TDY-C1).
     *
     * The pre-check that turns an unreachable home into a proof asked whether the train could reach and stand on ANY
     * copy of the home square.  Once a home is held to a facing, only the copies facing that way will do: here the train
     * is excluded from the westbound copy it was homed on, the eastbound copy would still take it, and the pre-check
     * passed on the eastbound copy's word - leaving the search to spend its budget and answer NO_PLAN_FOUND.
     *
     * MUTATION: have the pre-check look at every copy again and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAHomeHeldToAFacingItCannotStandInIsImpossible() throws Exception
    {
        Point westbound = layout.getPoint("BottomMainA (westbound)");
        Point eastbound = layout.getPoint("BottomMainA (eastbound)");

        assertTrue(westbound != null && westbound.isDestination() && eastbound != null && eastbound.isDestination(),
            "precondition: BottomMainA does not have two copies a train may stand on here");

        Locomotive train = model.getLocByName(OUR_TRAIN);

        try
        {
            assertTrue(layout.moveLocomotive(OUR_TRAIN, westbound.getName(), false), "could not stand the train westbound");

            layout.setHomeLocomotive(westbound.getName(), OUR_TRAIN);

            Point away = null;

            for (Point p : layout.getPoints())
            {
                if (p.getName().startsWith("RampDown") && p.isDestination() && p.getCurrentLocomotive() == null) away = p;
            }

            assertNotNull(away, "no copy of RampDown a train may stand on");

            assertTrue(layout.moveLocomotive(OUR_TRAIN, away.getName(), false), "could not move the train away");

            // THE HOME'S OWN COPY WILL NOT TAKE IT; the other arrival's would.
            westbound.getExcludedLocs().add(train);

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE, "a train homed facing west on BottomMainA and"
                + " excluded from the westbound copy can never come home facing west, and Return Home answered "
                + plan.getOutcome() + " - the pre-check asked about the eastbound copy, which would take it");
        }
        finally
        {
            westbound.getExcludedLocs().remove(train);

            layout.clearHomeLocomotives();

            for (Point p : new ArrayList<>(layout.getPoints()))
            {
                if (p.getCurrentLocomotive() != null && OUR_TRAIN.equals(p.getCurrentLocomotive().getName()))
                {
                    layout.moveLocomotive(null, p.getName(), true);
                }
            }
        }
    }

    private static void awaitStopped() throws Exception
    {
        long until = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;

        while (System.currentTimeMillis() < until)
        {
            if (!layout.isRunning()) return;

            Thread.sleep(250);
        }

        fail("the railway was still running " + (SETTLE_TIMEOUT_MS / 1000) + " seconds after being asked to stop");
    }
}
