package core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A station closed to arrivals on every side cannot be sent to by the right-click menu or by Return Home (OB-411):
 * the setup check's "driving one there yourself" is the only way left.  This pins existing behavior; the control is
 * the same railway with the station open.
 *
 * MUTATION: dropping `end.isDestination()` from `Layout.getPossiblePaths` and the same test at the top of `Layout.bfs`
 * fails the first claim; dropping `isDestination()` from `HomeStaging.canRest` and from the station list built in
 * `HomeStaging.snapshot` fails the second.  Each rule is held twice, so one drop alone stays green.
 */
public class testAStationClosedOnEverySideCannotBeSentTo
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static final String LOC = "OB411 alpha";

    private static int sensors = 3300;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();

        model.stop();

        model.newMM2Locomotive(LOC, 93);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null) model.deleteLoc(LOC);

        if (sandbox != null) sandbox.close();
    }

    /** Whether any offered route from the train's square ends at a copy of Beta. */
    private static boolean sendsToBeta(Layout rail, Locomotive loc)
    {
        for (List<Edge> path : rail.getPossiblePaths(loc, false))
        {
            if (path.get(path.size() - 1).getEnd().getName().startsWith("OB411Beta")) return true;
        }

        return false;
    }

    /** Alpha - Beta - Gamma with the train on Alpha and homed on Beta; Beta closed on both sides or open. */
    private static Layout build(String tag, boolean closed) throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("ob411-" + tag).toFile();

        LayoutDiagram page = new LayoutDiagram("main", 7, 3, null, null);

        int a = sensors;

        sensors += 10;

        for (int i = 0; i < 3; i++) model.newFeedback(a + i, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, a, a, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, a + 1, a + 1, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 5, 1, 0, 0, a + 2, a + 2, accessoryDecoderType.MM2, null);
        page.setPageId("1");

        AutonomySession s = new AutonomySession(folder);

        s.open(Arrays.asList(page));
        s.initialize(tag);

        TileKey alpha = new TileKey("main", 1, 1), beta = new TileKey("main", 3, 1), gamma = new TileKey("main", 5, 1);

        for (TileKey t : Arrays.asList(alpha, beta, gamma)) s.setStation(t, true);

        s.setPointName(alpha, "OB411Alpha");
        s.setPointName(beta, "OB411Beta");
        s.setPointName(gamma, "OB411Gamma");

        s.setPointProperty(alpha, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);
        s.setPointProperty(gamma, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);

        s.setHome(beta, LOC);

        s.placeLocomotive(alpha, LOC);

        if (closed) s.setBarredArrivals(beta, new LinkedHashSet<>(s.arrivalSides(beta)));

        s.rebuild();

        model.parseAuto(s.buildConfiguration());

        return model.getAutoLayout();
    }

    /** The right-click menu offers no route to the closed station; the open one is offered. */
    @Test
    public void testTheSendMenuOffersNoRouteToAStationClosedOnEverySide() throws Exception
    {
        Locomotive loc = model.getLocByName(LOC);

        Layout open = build("open", false);

        assertTrue(sendsToBeta(open, loc),
            "precondition: with Beta open the menu offers no route to it, so the closed case proves nothing");

        Layout closed = build("closed", true);

        for (Point p : closed.getPoints())
        {
            if (p.getName().startsWith("OB411Beta")) assertFalse(p.isDestination(), p.getName() + " is still a destination");
        }

        assertFalse(sendsToBeta(closed, loc),
            "the menu offers a route to a station closed to arrivals on every side, so a train can be sent there"
            + " and the setup check's wording is wrong again");
    }

    /** Return Home does not take the train to the closed station. */
    @Test
    public void testReturnHomeDoesNotReachAStationClosedOnEverySide() throws Exception
    {
        Layout open = build("homeopen", false);

        HomeStaging.Plan control = open.planReturnToHome();

        assertEquals(control.getOutcome(), HomeStaging.Outcome.READY,
            "precondition: with Beta open the train is not planned home, so the closed case proves nothing");

        Layout closed = build("homeclosed", true);

        HomeStaging.Plan plan = closed.planReturnToHome();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "Return Home planned a move onto a station closed to arrivals on every side");
    }
}
