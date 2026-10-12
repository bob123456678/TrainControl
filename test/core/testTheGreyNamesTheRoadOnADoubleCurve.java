package core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A tail across a double curve greys the road it lies on, not the whole square (OB-280, MT-475 step 4).
 *
 * A double curve carries two separate tracks, and routing refuses only the one a standing train covers - so the grey,
 * which is what routing refuses, has to name that road.  Nothing claimed it: `ui.testTheGreyAppearsAtIdleToo` compares
 * squares only, and its fixture never chooses a double curve, so emptying the road set left every claim green and the
 * diagram then greyed the whole square - both tracks - for a tail on one (the round-4 mutation run, G1).  Adam, 2026-09-24:
 * *"Add the double curve coverage test."*
 *
 * On his frozen railway: ParkingTrack7, `2 - Bottom:16,5`, is reached from the west only (its east end is a buffer),
 * through the double curve at `15,5`, whose E-S road (route 0#1) runs to it and whose N-W road (0#0) runs from
 * ParkingTrack8 to the switch at `14,5`.  A two-unit train standing in ParkingTrack7 lies on its own square and on the
 * E-S road; the N-W road is free.  The three squares are measured one each, so the claim does not rest on how the
 * snapshot happens to be measured.
 *
 * MUTATION: name no road for a claimed place on a double curve (`routesBlockedByStandingTrains`), and this fails.
 *
 * @author Adam
 */
public class testTheGreyNamesTheRoadOnADoubleCurve
{
    private static final String PAGE = "2 - Bottom";

    private static final TileKey BERTH = new TileKey(PAGE, 16, 5);
    private static final TileKey CURVE = new TileKey(PAGE, 15, 5);
    private static final TileKey BELOW = new TileKey(PAGE, 15, 6);

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;
    private static Locomotive train;
    private static Integer lengthWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        for (TileKey tile : new TileKey[] {BERTH, CURVE, BELOW}) session.setTileLength(tile, 1);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }

        List<String> names = model.getLocList();

        if (names.isEmpty()) throw new SkipException("this fixture has no locomotive");

        train = model.getLocByName(names.get(0));

        lengthWas = train.getTrainLength();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (train != null) train.setTrainLength(lengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Two units in ParkingTrack7 grey the double curve's E-S road and leave its N-W road alone.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testATailOnOneRoadOfADoubleCurveGreysThatRoadOnly() throws Exception
    {
        List<String> copies = session.getStationIndex().pointNamesAt(BERTH);

        assertFalse(copies.isEmpty(), "precondition: ParkingTrack7 is not a Point on this setup");

        Point berth = layout.getPoint(copies.get(0));

        assertNotNull(berth, "precondition: " + copies.get(0) + " is not on the running layout");

        train.setTrainLength(2);

        assertTrue(layout.moveLocomotive(train.getName(), berth.getName(), false), "could not stand the train in "
            + berth.getName());

        berth.setArrivedFrom("W");

        Map<TileKey, Set<RouteId>> greyed = session.routesBlockedByStandingTrains(layout);

        assertTrue(greyed.containsKey(CURVE), "precondition: a two-unit train in ParkingTrack7 does not reach the double"
            + " curve at " + CURVE + " - greyed: " + greyed.keySet());

        assertFalse(greyed.get(CURVE).isEmpty(), "the tail on the double curve at " + CURVE + " names no road, so the"
            + " diagram greys the whole square - both tracks - for a train on one (OB-280, G1)");

        assertEquals(greyed.get(CURVE), Collections.singleton(new RouteId(0, 1)), "the grey on the double curve at "
            + CURVE + " is not the E-S road the tail lies on");
    }
}
