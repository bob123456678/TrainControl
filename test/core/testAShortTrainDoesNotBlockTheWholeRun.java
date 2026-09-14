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
 * **This test states the geometry it depends on rather than hoping for it.** The checked-in snapshot
 * measures nothing at all, so nothing is covered on it and there would be no defect to see; the tile
 * behind TunnelLongPark is measured here, which is what makes the tail reach into that edge.
 *
 * @author Adam
 */
public class testAShortTrainDoesNotBlockTheWholeRun
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    /** The square the short train stands on, and the one nothing should stop a train reaching. */
    private static final TileKey PARK = new TileKey("1 - Main", 10, 9);
    private static final TileKey BEHIND_PARK = new TileKey("1 - Main", 10, 10);

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

        // THE ONE MEASURED TILE THIS NEEDS.  The snapshot measures nothing, so a tail reaches nowhere and
        // there is no coverage to be wrong about; two units behind the park is what lets a train stand
        // inside that edge without filling it.
        session.setTileLength(BEHIND_PARK, 2);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

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
        Point from = pointNamed("Tunnel");
        Point to = pointNamed("BottomMainA");

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
            + " below would pass on any code. The tile behind it is measured "
            + session.getStore().getTileLength(BEHIND_PARK) + " units; covered: " + covered.keySet());

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
        Point from = pointNamed("Tunnel");
        Point to = pointNamed("BottomMainA");

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
     * A Point whose base name is this, whichever copy comes first.
     */
    private static Point pointNamed(String base)
    {
        Point found = null;

        for (Point point : layout.getPoints())
        {
            if (!point.getName().startsWith(base)) continue;

            // A destination copy for the far end, so the path has somewhere to finish.
            if (found == null || (!found.isDestination() && point.isDestination())) found = point;
        }

        if (found == null) throw new SkipException("the snapshot has no Point called " + base);

        return found;
    }
}
