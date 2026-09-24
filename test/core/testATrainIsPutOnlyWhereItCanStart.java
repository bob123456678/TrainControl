package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train is stood on a copy of its square facing the way the setup records, and among those on one it can be started
 * from (GUI-B1, TDY2-A1, GUI2-A1, AUT2-A1, GUI2-B1).
 *
 * **The copy IS the direction.**  Nothing else in the running layout says which way a train will move: the runtime never
 * commands an absolute direction, it only reverses at a turning square.  So a train stood on a copy facing the other way
 * from the way it really points is dispatched along a route locked one way while its decoder drives it the other, over
 * track nothing reserved.  GUI-B1's first repair did exactly that where only a copy trains may not arrive at faces the
 * recorded way - the build, the throttle's direction-follow and the Facing menu all stood the train on a copy facing the
 * other way, and the log said the direction had been followed.  Where no copy trains may arrive at faces the recorded
 * way, the train now stands on one that faces it anyway: autonomy will not start it there, and says so - *"It is
 * standing on {0}, which is not a station"* - which is the truth.
 *
 * GUI-B1's own case stands: among the copies facing the recorded way, one trains may arrive at is chosen first.
 *
 * Adam, 2026-09-23, on OB-270: *"we shouldn't allow an impossible facing to be saved."*  That was built into the paste.
 * But a facing only a barred copy holds could still reach the setup - through the editor's Place door and the Place
 * Locomotive dialog, or from any setup saved before - and two things then acted on it without asking whether trains may
 * arrive at that copy: the build, which put the train on the copy facing that way, and the Facing door and the idle
 * drain after a turn, which move a train onto "the copy that faces X".  The train then stood on a copy that is not a
 * station, and autonomy would not start it: *"It is standing on {0}, which is not a station."*
 *
 * **On the frozen railway, as he has it**: BottomMainA with arrivals from the east barred, so its westbound copy is no
 * station; BottomMainPost, which trains may turn at and which has arrivals from the north barred.
 *
 * MUTATION: have `placementCopy` or `moveOntoFacingCopy` take the first copy facing that way again, and its claim fails;
 * let either fall back to a copy trains may arrive at before one facing the recorded way, and the facing claims fail;
 * return copy zero with no facing recorded, and the no-facing claim fails.
 *
 * @author Adam
 */
public class testATrainIsPutOnlyWhereItCanStart
{
    private static final String PROBE = "start copy probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static List<LayoutDiagram> pages;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);
        model.stop();

        pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        assertNotNull(model.newMM2Locomotive(PROBE, 2396), "could not create this class's train");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null) model.deleteLoc(PROBE);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A facing only a copy trains may not arrive at holds still stands the train on a copy facing that way (AUT2-A1).
     *
     * @throws Exception from the build
     */
    @Test
    public void testTheBuildKeepsTheFacingTheSetupRecords() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        Layout before = build(session);

        String westbound = copyFacing(session, mainA, Side.W);

        assertNotNull(westbound, "precondition: BottomMainA has no copy facing west");
        assertFalse(before.getPoint(westbound).isDestination(), "precondition: " + westbound + " is a station here, so"
            + " the facing west is a possible one and nothing below is about a barred copy");

        // THE FACING ONLY THAT COPY HOLDS, as a door or an older setup leaves it.
        session.placeLocomotive(mainA, PROBE);
        session.setFacing(mainA, Side.W);

        Point standing = standingOn(build(session));

        assertNotNull(standing, "the build put the train nowhere");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "the setup records the train at"
            + " BottomMainA facing west, and the build stood it on " + standing.getName() + ", which faces "
            + session.facingsFor(mainA).get(standing.getName()) + " - turned round in the model, so its next route is"
            + " locked one way while its decoder drives it the other (AUT2-A1)");
    }

    /**
     * A train placed with no facing recorded is stood on a copy it can be started from (GUI2-B1).
     *
     * The build returned copy zero when the setup held no facing, and copies are emitted N, E, S, W - so at BottomMainA
     * and BottomMainPost copy zero is the one trains may not arrive at.  A door that places a train and records no facing
     * (several copies it could stand on, and nothing saying which way it points) is ordinary.
     *
     * @throws Exception from the build
     */
    @Test
    public void testATrainWithNoFacingIsPutWhereItCanStart() throws Exception
    {
        for (String name : new String[] {"BottomMainA", "BottomMainPost"})
        {
            AutonomySession session = session();

            TileKey square = square(session, name);

            session.placeLocomotive(square, PROBE);
            session.setFacing(square, null);

            Point standing = standingOn(build(session));

            assertNotNull(standing, "the build put the train at " + name + " nowhere");

            assertTrue(standing.isDestination(), "placed at " + name + " with no facing recorded, the train was stood"
                + " on " + standing.getName() + " - no station, so autonomy will not start it from there, and nothing"
                + " said which way it points to make that the truth (GUI2-B1)");
        }
    }

    /**
     * A train reversed on the throttle at a square whose other facing only a barred copy holds is moved onto a copy
     * facing its new way (TDY2-A1, GUI2-A1).
     *
     * Adam: *"a locomotive direction command WILL update the direction on the graph if it does not match"*.  The flip is a
     * physical fact - the decoder has already reversed - so the copy has to follow it, whether or not autonomy can start
     * the train from there.
     *
     * @throws Exception from the build
     */
    @Test
    public void testAReversalOnTheThrottleIsFollowed() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        Point eastbound = stoodFacing(session, running, mainA, Side.E);

        session.placeLocomotive(mainA, PROBE);
        session.setFacing(mainA, Side.E);

        assertEquals(session.flipFacing(PROBE, running), mainA, "precondition: the direction-follow did not act on the"
            + " train at BottomMainA");

        assertEquals(session.getFacing(mainA), Side.W, "precondition: the flip did not record the train facing west");

        Point standing = standingOn(running);

        assertNotNull(standing, "the flip took the train off the railway");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "reversed on the throttle at"
            + " BottomMainA, the train was recorded facing west and left on " + standing.getName() + " - facing "
            + session.facingsFor(mainA).get(standing.getName()) + " in the model, so its next route is locked that way"
            + " while its decoder drives it west (TDY2-A1)");

        assertTrue(eastbound != standing, "precondition: the train did not move at all");
    }

    /**
     * And the Facing menu, which is how the operator tells the record which way the train really points (GUI2-A1).
     *
     * @throws Exception from the build
     */
    @Test
    public void testTheFacingMenuMovesTheTrainOntoACopyFacingThatWay() throws Exception
    {
        AutonomySession session = session();

        TileKey mainA = square(session, "BottomMainA");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        stoodFacing(session, running, mainA, Side.E);

        session.placeLocomotive(mainA, PROBE);

        session.setFacingAndMove(mainA, Side.W);

        Point standing = standingOn(running);

        assertNotNull(standing, "the Facing menu took the train off the railway");

        assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "told the train at BottomMainA faces"
            + " west, the Facing menu recorded west and left it on " + standing.getName() + ", facing "
            + session.facingsFor(mainA).get(standing.getName()) + " in the model (GUI2-A1)");
    }

    /**
     * The Facing door moves a train onto a copy facing that way that it can be started from, or nowhere.
     *
     * @throws Exception from the build
     */
    @Test
    public void testTheFacingDoorNeverMovesATrainOntoACopyItCannotStartFrom() throws Exception
    {
        AutonomySession session = session();

        TileKey post = square(session, "BottomMainPost");

        final Layout running = build(session);

        session.setRunningLayoutSource(() -> running);

        // THE PREMISE: the first copy facing south is one no train may arrive at.
        String firstFacingSouth = copyFacing(session, post, Side.S);

        assertNotNull(firstFacingSouth, "precondition: BottomMainPost has no copy facing south");
        assertFalse(running.getPoint(firstFacingSouth).isDestination(), "precondition: " + firstFacingSouth + " is a"
            + " station, so the first copy facing south is a good one and this cannot fail");

        // A TRAIN THAT CAME IN FROM THE SOUTH AND TURNED - standing on the turning copy, facing south.
        Point turned = running.getPoint("BottomMainPost (northbound, reverse)");

        assertTrue(turned != null && turned.isDestination(), "precondition: no turning copy of BottomMainPost a train may"
            + " stand on");

        assertTrue(running.moveLocomotive(PROBE, turned.getName(), false), "could not stand the train on " + turned.getName());

        session.placeLocomotive(post, PROBE);

        session.setFacingAndMove(post, Side.S);

        Point standing = standingOn(running);

        assertNotNull(standing, "the facing door took the train off the railway");

        assertTrue(standing.isDestination(), "told the train at BottomMainPost faces south, the facing door moved it onto "
            + standing.getName() + " - no station, so autonomy will not start it from there");
    }

    // ---------------------------------------------------------------------------------------------

    private static AutonomySession session() throws Exception
    {
        AutonomySession session = new AutonomySession(sandbox.getFolder());

        session.open(pages);

        return session;
    }

    private static Layout build(AutonomySession session) throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout built = model.getAutoLayout();

        assertNotNull(built, "the setup did not build: " + Layout.getLastError());

        return built;
    }

    private static TileKey square(AutonomySession session, String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new AssertionError("his railway has no " + name);
    }

    /** The first copy of the square, in the build's order, facing this way. */
    private static String copyFacing(AutonomySession session, TileKey square, Side facing)
    {
        for (Map.Entry<String, Side> copy : session.facingsFor(square).entrySet())
        {
            if (copy.getValue() == facing) return copy.getKey();
        }

        return null;
    }

    /** Stands the train on a copy of the square facing this way that it can start from, and says which. */
    private static Point stoodFacing(AutonomySession session, Layout running, TileKey square, Side facing)
    {
        for (Map.Entry<String, Side> copy : session.facingsFor(square).entrySet())
        {
            Point point = running.getPoint(copy.getKey());

            if (copy.getValue() != facing || point == null || !point.isDestination()) continue;

            assertTrue(running.moveLocomotive(PROBE, point.getName(), false), "could not stand the train on "
                + point.getName());

            return point;
        }

        throw new AssertionError("precondition: no copy of the square faces " + facing + " and is a station");
    }

    private static Point standingOn(Layout layout)
    {
        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null && PROBE.equals(p.getCurrentLocomotive().getName())) return p;
        }

        return null;
    }
}
