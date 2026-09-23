package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * A train is only ever stood on a copy of its square it can be started from - whatever facing the setup holds (GUI-B1).
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
 * MUTATION: have `placementCopy` or `moveOntoFacingCopy` take the first copy facing that way again, and its claim fails.
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
     * A facing only a barred copy holds does not put the train on that copy at the next build.
     *
     * @throws Exception from the build
     */
    @Test
    public void testTheBuildNeverStandsATrainOnACopyItCannotStartFrom() throws Exception
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

        assertTrue(standing.isDestination(), "the setup holds a facing only " + westbound + " holds, and the build stood"
            + " the train on " + standing.getName() + " - no station, so autonomy will not start it from there");
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

    private static Point standingOn(Layout layout)
    {
        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null && PROBE.equals(p.getCurrentLocomotive().getName())) return p;
        }

        return null;
    }
}
