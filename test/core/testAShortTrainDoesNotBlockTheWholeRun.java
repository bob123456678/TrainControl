package core;

import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A one-unit train does not make a station on the far side of the run unreachable (Adam, OB-207).
 *
 * *"75 407 DB cannot go from Tunnel to BottomMainA even though it should be able to"*, at a train length
 * of one.
 *
 * **The mechanism, measured on his own railway before this was written.** A standing train covers whole
 * EDGES, and `Layout.isPathClear` then sweeps SHARED METAL: for every edge of a candidate path it asks
 * whether any edge sharing tiles with it is covered, through `getLockEdges()`. EN57-203 stood at
 * TunnelLongPark and covered `BottomMainA (westbound) -> TunnelLongPark`, twelve tiles across three
 * switches; the path `Tunnel -> BottomMainAPre -> BottomMainA` shares the last of those tiles with it, so
 * the path was refused - by a train lying inside the FIRST tile of that edge, nowhere near the shared
 * metal.
 *
 * So the refusal is right in principle and wrong in extent, and the extent is the whole of the bug: the
 * runtime cannot say which part of an edge a train is on, because `Edge` holds a length and no places.
 *
 * **On his measured railway** (the snapshot refrozen 2026-09-23).  Until then it measured nothing, so
 * the tile behind TunnelLongPark was measured here to give the tail something to reach; now his own
 * measurements are what the train stands on - 2 on TunnelLongPark's square, 1 behind it - and nothing
 * is set by this class.  A one-unit train lies on its own square (OB-278), which is the first place of
 * the run into the park and nowhere near the metal the path shares.
 *
 * @author Adam
 */
public class testAShortTrainDoesNotBlockTheWholeRun
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    /** The square the short train stands on. */
    private static final TileKey PARK = new TileKey("1 - Main", 10, 9);

    private static Locomotive standing;
    private static Locomotive travelling;

    private static Integer standingLengthWas;
    private static Integer travellingLengthWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        // NOTHING OF HIS STANDING ABOUT.  The snapshot refrozen 2026-09-23 has 75 407 DB at BottomMainA, the end of the
        // very journey this class asks about - so the path was refused because its destination was occupied, which is
        // true and is not the tail this class is about.
        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }

        List<String> names = model.getLocList();

        if (names.size() < 2) throw new SkipException("this fixture has fewer than two locomotives");

        standing = model.getLocByName(names.get(0));
        travelling = model.getLocByName(names.get(1));

        standingLengthWas = standing.getTrainLength();
        travellingLengthWas = travelling.getTrainLength();

        standing.setTrainLength(1);
        travelling.setTrainLength(1);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (standing != null) standing.setTrainLength(standingLengthWas);

            if (travelling != null) travelling.setTrainLength(travellingLengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The claim: a one-unit train parked at the far end of a long run leaves the run usable.
     *
     * MUTATION: this is the defect itself - before the locations reached the runtime, the shared-metal
     * sweep refused the path over track the train is nowhere near.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAOneUnitTrainLeavesTheRestOfTheRunUsable() throws Exception
    {
        Point park = pointAt(PARK, "TunnelLongPark");
        Point[] journey = tunnelToBottomMainA();
        Point from = journey[0];
        Point to = journey[1];

        List<Edge> path = layout.bfs(from, to, new java.util.ArrayList<List<Edge>>());

        assertNotNull(path, "no path from " + from.getName() + " to " + to.getName() + " at all, so the"
            + " fixture cannot show this");

        // THE RUN THIS TEST IS ABOUT: one that touches the park and SHARES METAL with that path, which
        // is the relation `isPathClear` sweeps. Three runs reach this square and only some of them lie
        // over the same rails as the way in to BottomMainA; picking the longest, or the first, tests a
        // different railway.
        Edge shared = null;

        for (Edge candidate : layout.getNeighborsAndIncoming(park))
        {
            for (Edge leg : path)
            {
                if (candidate.getLockEdges().contains(leg)) shared = candidate;
            }
        }

        if (shared == null)
        {
            throw new SkipException("no run into " + park.getName() + " shares metal with the way to "
                + to.getName() + " on this fixture, so the sweep this test is about cannot fire");
        }

        assertFalse(path.contains(shared),
            "the path runs over that edge itself, so refusing it is right and this test is about the"
            + " wrong pair of squares");

        // THE TRAIN IS ON THE PARK, told which way it came in.  The fork rule - one way back or nothing
        // - refuses to guess where three runs meet, so without the side the train covers nothing at all.
        park.setLocomotive(standing);

        park.setArrivedFrom(layout.entrySideOf(shared, park));

        java.util.Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        Edge behind = null;

        for (java.util.Map.Entry<Edge, Locomotive> each : covered.entrySet())
        {
            // Either direction of the same rail: a tail fouls it whichever way traffic runs, and the
            // walk records the direction it found.
            if (each.getKey() == shared || (each.getKey().getStart() == shared.getEnd()
                && each.getKey().getEnd() == shared.getStart()))
            {
                behind = each.getKey();
            }
        }

        assertNotNull(behind, "the one-unit train at " + park.getName() + " does not cover the run that"
            + " shares metal with the path, so the sweep this test is about cannot fire and the claim"
            + " below would pass on any code. Covered: " + covered.keySet());

        assertTrue(behind.getLength() > standing.getTrainLength(),
            "the covered edge measures " + behind.getLength() + " and the train is "
            + standing.getTrainLength() + " long, so the train fills it and there is no part of it left"
            + " for another train to use - which is not the case this test is about");

        // AND THAT THE FINER ANSWER IS THE ONE BEING USED.
        //
        // Both edges fall back to the whole-edge sweep when the build did not describe them in places,
        // and under that fallback the claim below is the defect and the control that follows passes for
        // a reason that has nothing to do with this change. Say so here rather than let either be
        // answered by the code this replaces.
        assertFalse(behind.getPlaceIds().isEmpty(),
            "the covered run carries no places, so the narrowing this test is about cannot fire");

        for (Edge leg : path)
        {
            assertFalse(leg.getPlaceIds().isEmpty(),
                "the path leg " + leg.getName() + " carries no places, so the sweep falls back to whole"
                + " edges and neither claim in this class means anything");
        }

        java.util.Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

        int lyingOn = 0;

        for (String place : behind.getPlaceIds())
        {
            if (standing.equals(claimed.get(place))) lyingOn++;
        }

        assertTrue(lyingOn > 0 && lyingOn < behind.getPlaceIds().size(),
            "the one-unit train lies on " + lyingOn + " of the covered run's " + behind.getPlaceIds().size()
            + " places. This test is about a train on PART of a run: nought would mean the walk stops"
            + " before reaching it, and all of them would mean the finer answer is no finer than the"
            + " whole-edge one it replaces");

        assertTrue(layout.isPathClear(path, travelling, false),
            "a one-unit train parked at " + park.getName() + " made " + to.getName() + " unreachable."
            + " It lies in the first tile of a " + behind.getLength() + "-unit edge; the path shares only"
            + " the far end of that edge with it, and `isPathClear`'s shared-metal sweep refused the"
            + " whole thing because a covered edge is covered whole. Adam, OB-207: \"75 407 DB cannot go"
            + " from Tunnel to BottomMainA even though it should be able to\". Refused with: "
            + Layout.getLastError());
    }

    /**
     * And the control: a train long enough to reach the shared metal still blocks it.
     *
     * Without this the claim above is satisfied by a railway that has stopped refusing anything, which is
     * the direction this change could most easily go wrong in - it is a collision guard.
     *
     * @throws Exception from the railway
     */
    @Test(dependsOnMethods = "testAOneUnitTrainLeavesTheRestOfTheRunUsable")
    public void testATrainLongEnoughToReachItStillBlocksIt() throws Exception
    {
        Point park = pointAt(PARK, "TunnelLongPark");
        Point[] journey = tunnelToBottomMainA();
        Point from = journey[0];
        Point to = journey[1];

        park.setLocomotive(standing);

        List<Edge> path = layout.bfs(from, to, new java.util.ArrayList<List<Edge>>());

        assertNotNull(path, "no path to check");

        for (Edge leg : path)
        {
            assertFalse(leg.getPlaceIds().isEmpty(),
                "the path leg " + leg.getName() + " carries no places, so this control is answered by the"
                + " whole-edge fallback and would pass with the narrowing removed");
        }

        // Long enough to lie across the whole run, so it really is on the shared metal.
        standing.setTrainLength(50);

        try
        {
            assertFalse(layout.isPathClear(path, travelling, false),
                "a fifty-unit train parked at " + park.getName() + " does NOT block " + to.getName()
                + ", so the sweep has stopped refusing anything and the claim above means nothing");
        }
        finally
        {
            standing.setTrainLength(1);
        }
    }

    /**
     * The Point standing for a square, by the name the setup gives it.
     */
    private static Point pointAt(TileKey square, String expected)
    {
        String name = session.getStationIndex().nameOf(square);

        if (name == null || !expected.equals(name))
        {
            throw new SkipException("the snapshot no longer calls " + square + " " + expected
                + " (it is " + name + "), so this test is about a railway that has moved");
        }

        for (Point point : layout.getPoints())
        {
            if (session.getStationIndex().squareOf(point.getName()) != null
                && session.getStationIndex().squareOf(point.getName()).equals(square))
            {
                return point;
            }
        }

        throw new SkipException("no running Point stands on " + square);
    }

    /**
     * Adam's journey, Tunnel to BottomMainA, as the pair of copies a route joins.
     *
     * By route rather than by name.  This took the first Point whose name STARTED with "Tunnel" - which TunnelLongPark
     * and TunnelLeftPark do too - and the first destination copy of BottomMainA, and on the railway refrozen 2026-09-23
     * that was a copy no route from Tunnel reaches.  His journey arrives on the eastbound copy.
     *
     * @return the copy of Tunnel it starts from and the copy of BottomMainA it ends at
     * @throws Exception from the search
     */
    private static Point[] tunnelToBottomMainA() throws Exception
    {
        for (Point from : layout.getPoints())
        {
            if (!from.getName().equals("Tunnel") && !from.getName().startsWith("Tunnel (")) continue;

            for (Point to : layout.getPoints())
            {
                if (!to.getName().equals("BottomMainA") && !to.getName().startsWith("BottomMainA (")) continue;

                if (!to.isDestination() || to.getName().endsWith(", reverse)")) continue;

                if (layout.bfs(from, to, new java.util.ArrayList<List<Edge>>()) != null) return new Point[] {from, to};
            }
        }

        throw new AssertionError("no route joins a copy of Tunnel to a plain copy of BottomMainA a train may stop at, so"
            + " the journey Adam reported (OB-207) is not on this railway");
    }
}
