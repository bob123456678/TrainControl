package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * The station's size is an allowance; the squares a train stands on are track, its own square included (OB-278).
 *
 * Adam, 2026-09-13: **"if the segment length is shorter, more should be blocked.  The station size is an allowance,
 * not a length."**  And on 2026-09-23, when a two-unit train was refused TunnelLongPark: **"the 2 length tile with the
 * s88 consumes 2 units of the train"** - asked whether that holds at every station, *"Everywhere"*, and *"This
 * shouldn't be a major ruling reversal."*
 *
 * **What the first ruling meant, and how it was misread.**  "The station size" is the station's MAXIMUM TRAIN LENGTH -
 * the number typed on the station, which says how much train it may HOLD and is asked by `whyTooLongForThisRoute`.
 * It was read instead as the length MEASURED on the station's own square, and from 2026-09-13 to 2026-09-23 every walk
 * that works out where a standing train's body lies left that square's measurement unspent: the whole train was put on
 * the track BEHIND the square it stands on.  On his measured railway, where each berth's length is written on its
 * sensor square, that put the tail over the switch behind almost every berth - TunnelLongPark (2 on its square, 1
 * behind, maximum 3) took a one-unit train and nothing longer, TunnelCenterPark and TopR1ParkShort took nothing at all.
 *
 * **The rule now**: every measured square a train lies over is spent, the square it stands on first.  A train no longer
 * than that square blocks nothing behind it.  Three walks read it and must agree - `Layout.walkStandingTrains` (what
 * the railway blocks), `Layout.whyABerthCannotHoldIt` (whether a berth can take a train) and
 * `AutonomySession.walkBackFrom` (the orange line) - and this class asks each of them.
 *
 * On a sandbox copy of the frozen `live-snapshot`, which carries his measurements; where he has not measured the
 * squares a claim needs, they are set here and say so.  The train is this class's own.
 *
 * @author Adam
 */
public class testAStationsSizeIsAnAllowance
{
    /** TunnelLongPark, measured 2 on its own square, and the square behind it, measured 1 - both his. */
    private static final TileKey PARK = new TileKey("1 - Main", 10, 9);
    private static final TileKey BEHIND_PARK = new TileKey("1 - Main", 10, 10);

    /** The switch behind the park, which the road to BottomMainAPre runs over - unmeasured on his railway. */
    private static final TileKey SWITCH_BEHIND_PARK = new TileKey("1 - Main", 10, 11);

    /**
     * A second station, chosen because the walk really does take a SECOND hop behind it.
     *
     * The park above does not: the run into it ends at BottomMainA, where three roads meet, and the fork rule stops the
     * tail there however long the train is.  TopMainR1's approach runs back through TopMainR1Pre to exactly one
     * neighbour.  He has measured the three squares behind it at 1 each and left the station and TopMainR1Pre at
     * nothing, so those two are measured here.
     */
    private static final TileKey TWO_HOPS = new TileKey("1 - Main", 5, 4);
    private static final TileKey BEHIND_TWO_HOPS = new TileKey("1 - Main", 5, 5);
    private static final TileKey BEYOND_TWO_HOPS = new TileKey("1 - Main", 5, 9);

    /**
     * A berth where BOTH directions of the rail behind it report the same way in - TopMainR0Park.  Unmeasured on his
     * railway, so measured here.
     */
    private static final TileKey TWO_COPIES = new TileKey("1 - Main", 4, 5);
    private static final TileKey BEHIND_TWO_COPIES = new TileKey("1 - Main", 4, 4);

    private static final String OUR_TRAIN = "allowance probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    private static MarklinLocomotive standing;

    /**
     * Opens the frozen railway with his measurements, adds the few the second-hop and two-copy claims need, and builds.
     *
     * @throws Exception when the railway cannot be read
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        standing = model.newMM2Locomotive(OUR_TRAIN, 2393);

        assertNotNull(standing, "could not create this class's train");

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        // NOT HIS: TopMainR1 and TopMainR1Pre, which he left unmeasured, so the walk has a second edge to reach.
        session.setTileLength(TWO_HOPS, 2);
        session.setTileLength(BEYOND_TWO_HOPS, 1);

        // NOR THESE: TopMainR0Park and the square behind it, unmeasured on his railway.
        session.setTileLength(TWO_COPIES, 1);
        session.setTileLength(BEHIND_TWO_COPIES, 2);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        // Nothing of his standing about, so every claim is about this class's train alone.
        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }
    }

    /**
     * Takes this class's train away and puts the layout preference back.
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null) model.deleteLoc(OUR_TRAIN);
        }
        catch (Exception alreadyGone)
        {
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Every claim puts the train down where it wants it, so none is left standing for the next.
     */
    @AfterMethod(alwaysRun = true)
    public void liftTheTrain()
    {
        if (layout == null) return;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) point.setLocomotive(null);
        }
    }

    /**
     * The fixture is the shape the claims are about: his measurements at TunnelLongPark, and the road behind it.
     */
    @Test
    public void testTheParkIsMeasuredTheWayHeMeasuredIt()
    {
        Point park = park();

        assertEquals(session.getStore().getTileLength(PARK), 2,
            "TunnelLongPark's own square is not measured 2, which is his measurement and what this class is about");

        assertEquals(session.getStore().getTileLength(BEHIND_PARK), 1,
            "the square behind TunnelLongPark is not measured 1, his measurement");

        assertEquals(park.getMaxTrainLength(), Integer.valueOf(3),
            "TunnelLongPark does not hold 3, which is the size he gave it");

        assertNotNull(edgeInto(park), "no run arrives at " + park.getName() + " over " + BEHIND_PARK);

        assertTrue(edgeInto(park).getPlaceIds().contains(SWITCH_BEHIND_PARK.toString()),
            "the run into " + park.getName() + " does not cross " + SWITCH_BEHIND_PARK + ", so the switch the claims"
            + " below keep the tail off is not on it: " + edgeInto(park).getPlaceIds());

        assertNotNull(layout.entrySideOf(edgeInto(park), park),
            "the run into " + park.getName() + " does not say which side it comes in by, so a train stood there has"
            + " no recorded arrival and the tail walk stops at the fork before it claims anything");
    }

    /**
     * The square with the sensor consumes its own length of the train (OB-278).
     *
     * Adam: *"the 2 length tile with the s88 consumes 2 units of the train."*  So a two-unit train at TunnelLongPark
     * lies on that square and nowhere else, and a three-unit one reaches the square behind and stops short of the
     * switch.
     *
     * MUTATION: leaving the standing square unspent in `Layout.walkOneTail` - the reading of 2026-09-13 - claims
     * 10,10 for the two-unit train and the switch for the three-unit one.
     */
    @Test(dependsOnMethods = "testTheParkIsMeasuredTheWayHeMeasuredIt")
    public void testTheSquareWithTheSensorConsumesItsLength()
    {
        Map<String, Locomotive> atTwo = standAtThePark(2);

        assertTrue(atTwo.containsKey(PARK.toString()),
            "a train stands on " + PARK + " and the railway does not claim that square: " + atTwo.keySet());

        assertFalse(atTwo.containsKey(BEHIND_PARK.toString()),
            "a two-unit train at TunnelLongPark, whose own square measures 2, is claimed on " + BEHIND_PARK
            + " behind it.  Adam, 2026-09-23: \"the 2 length tile with the s88 consumes 2 units of the train\" - the"
            + " train fits on its own square.  Claimed: " + atTwo.keySet());

        Map<String, Locomotive> atThree = standAtThePark(3);

        assertTrue(atThree.containsKey(BEHIND_PARK.toString()),
            "a three-unit train at TunnelLongPark is one unit longer than its square, and " + BEHIND_PARK
            + " behind it is not claimed - so the body is being put nowhere.  Claimed: " + atThree.keySet());

        assertFalse(atThree.containsKey(SWITCH_BEHIND_PARK.toString()),
            "a three-unit train at TunnelLongPark (2 on its square, 1 behind) is claimed on the switch at "
            + SWITCH_BEHIND_PARK + ", which the road from BottomMainA to BottomMainAPre runs over - so it blocks that"
            + " road though it fits the berth exactly.  Claimed: " + atThree.keySet());
    }

    /**
     * The berth rule takes what the berth measures (OB-278).
     *
     * Adam, 2026-09-23: *"75 407 DB can't go to TunnelLongPark from BottomMainPost unless it is length 1, whereas up to
     * length 3 should be allowed (both measured and max on the station)."*  The berth rule refused two and three units
     * for standing across the road from BottomMainA to BottomMainAPre, because it put the whole train behind the
     * berth's own square.
     *
     * MUTATION: leaving the berth's own span unspent in `Layout.whyABerthCannotHoldIt` refuses two units here.
     */
    @Test(dependsOnMethods = "testTheParkIsMeasuredTheWayHeMeasuredIt")
    public void testTheBerthTakesWhatItMeasures()
    {
        Edge approach = edgeInto(park());

        for (int units = 1; units <= 3; units++)
        {
            standing.setTrainLength(units);

            assertNull(Layout.whyABerthCannotHoldIt(Arrays.asList(approach), standing),
                "a " + units + "-unit train was refused TunnelLongPark, which measures 2 on its own square and 1 behind"
                + " it.  Adam: \"up to length 3 should be allowed\".  Refused: "
                + Layout.whyABerthCannotHoldIt(Arrays.asList(approach), standing));
        }

        standing.setTrainLength(4);

        assertNotNull(Layout.whyABerthCannotHoldIt(Arrays.asList(approach), standing),
            "a four-unit train was accepted by the berth rule at TunnelLongPark, whose squares measure 3 before the"
            + " switch - its tail lies on the road to BottomMainAPre, and the berth rule no longer refuses anything");
    }

    /**
     * And the journey he reported is offered: BottomMainPost to TunnelLongPark, at one, two and three units.
     *
     * By hand, as he sent it, and from the copy of BottomMainPost a route to TunnelLongPark leaves from.  Four units is
     * refused, by the size he gave the station.
     *
     * @throws Exception from the railway
     */
    @Test(dependsOnMethods = "testTheParkIsMeasuredTheWayHeMeasuredIt")
    public void testBottomMainPostToTunnelLongParkIsOfferedUpToThree() throws Exception
    {
        Point park = park();

        Point from = null;

        for (Point copy : layout.getPoints())
        {
            String name = copy.getName();

            if (!name.equals("BottomMainPost") && !name.startsWith("BottomMainPost (")) continue;

            if (!copy.isDestination()) continue;

            if (layout.bfs(copy, park, new ArrayList<List<Edge>>()) != null) from = copy;
        }

        assertNotNull(from, "no copy of BottomMainPost a train may stand on has a route to TunnelLongPark, so the"
            + " journey Adam reported is not on this railway");

        try
        {
            for (int units = 1; units <= 4; units++)
            {
                standing.setTrainLength(units);

                assertTrue(layout.moveLocomotive(OUR_TRAIN, from.getName(), false), "could not stand the train at "
                    + from.getName());

                Map<String, String> reasons = layout.explainDestinations(standing, true);

                assertTrue(reasons.containsKey(park.getName()),
                    "TunnelLongPark is not among the stations explained for a train at " + from.getName() + ": "
                    + reasons.keySet());

                if (units <= 3)
                {
                    assertNull(reasons.get(park.getName()),
                        "a " + units + "-unit train at " + from.getName() + " is refused TunnelLongPark.  Adam,"
                        + " 2026-09-23: \"up to length 3 should be allowed (both measured and max on the station)\"."
                        + "  Refused: " + reasons.get(park.getName()));
                }
                else
                {
                    assertNotNull(reasons.get(park.getName()),
                        "a four-unit train at " + from.getName() + " is offered TunnelLongPark, which holds 3");
                }

                layout.moveLocomotive(null, from.getName(), true);
            }
        }
        finally
        {
            layout.moveLocomotive(null, from.getName(), true);
        }
    }

    /**
     * The orange line stops where the train does (OB-278).
     *
     * The picture is the third walk, and it has to agree with the other two: a train that fits on its own square is
     * drawn there and nowhere behind it.
     *
     * MUTATION: leaving the standing square unspent in `AutonomySession.walkBackFrom` draws 10,10 behind a two-unit
     * train at TunnelLongPark.
     */
    @Test(dependsOnMethods = "testTheParkIsMeasuredTheWayHeMeasuredIt")
    public void testTheOrangeStopsWhereTheTrainDoes()
    {
        standAtThePark(2);

        java.util.Set<TileKey> drawn = session.tilesCoveredByStandingTrains(layout);

        assertTrue(drawn.contains(PARK),
            "a two-unit train stands at TunnelLongPark and its own square is not drawn orange: " + drawn);

        assertFalse(drawn.contains(BEHIND_PARK),
            "a two-unit train stands at TunnelLongPark, whose own square measures 2, and the orange is drawn on "
            + BEHIND_PARK + " behind it - the picture says the train is somewhere it is not: " + drawn);

        standAtThePark(3);

        drawn = session.tilesCoveredByStandingTrains(layout);

        assertTrue(drawn.contains(BEHIND_PARK),
            "a three-unit train at TunnelLongPark reaches " + BEHIND_PARK + " and it is not drawn: " + drawn);

        assertFalse(drawn.contains(SWITCH_BEHIND_PARK),
            "a three-unit train at TunnelLongPark fits before the switch and the orange is drawn on it: " + drawn);
    }

    /**
     * The tail reaches past the first edge behind it when the train is longer than that edge (SVV-C3).
     *
     * **Two budgets walk a standing train's body**: the PLACES budget, which claims square by square, and the HOP
     * budget, which decides whether to walk back another edge.  If the hop budget over-spends, the walk stops early,
     * the tail's last units are claimed by nobody, and another train is cleared over metal this one is lying on - the
     * permitting side.
     *
     * **The arrangement:** TopMainR1 measured 2 here, his three squares of 1 behind it, TopMainR1Pre measured 1 here.
     * The first edge holds 5; a seven-unit train has 2 left to place, which reaches the edge beyond.
     *
     * MUTATION: charging the first edge twice - `remaining -= chargedHere + segment.getLength()` - stops the walk at
     * the first approach.
     */
    @Test
    public void testTheTailReachesPastTheFirstEdgeBehindIt()
    {
        Point berth = onlyPointWithApproachThrough(TWO_HOPS, BEHIND_TWO_HOPS);

        Edge approach = edgeIntoVia(berth, BEHIND_TWO_HOPS);

        int first = 0;

        for (Integer span : approach.getPlaceLengths()) first += span == null ? 0 : Math.max(0, span);

        assertEquals(first, 5, "the first edge behind " + berth.getName() + " measures " + first + ", not the 5 this"
            + " claim's train length is chosen around: " + approach.getPlaceIds() + " " + approach.getPlaceLengths());

        berth.setLocomotive(standing);

        berth.setArrivedFrom(layout.entrySideOf(approach, berth));

        standing.setTrainLength(first + 2);

        Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

        java.util.Set<String> beyond = new java.util.LinkedHashSet<>(claimed.keySet());

        beyond.removeAll(approach.getPlaceIds());

        assertTrue(beyond.contains(BEYOND_TWO_HOPS.toString()),
            "a " + (first + 2) + "-unit train stands at " + berth.getName() + ", whose first edge back measures "
            + first + ", and the railway does not claim " + BEYOND_TWO_HOPS + " on the edge beyond - the last units of"
            + " the train are claimed by nobody, which is what clears a second train over them.  Claimed: "
            + claimed.keySet());
    }

    /**
     * The square the train stands on is claimed wherever it stands, not only at some stations (SVZ-B1).
     *
     * A piece of rail is two `Edge` objects, one per direction, and both can report the same way in at the square they
     * meet.  `getNeighborsAndIncoming` sorts the outgoing ones first, and an edge's places are the path plus the square
     * it ARRIVES at - so taking the outgoing copy first claimed the track behind and never the berth.  The train
     * arrived along the copy that ends here, and the first hop prefers it.
     *
     * TopMainR0Park measured 1 here and the square behind it 2; the train is 2, so it lies on both.
     *
     * MUTATION, run 2026-09-13: written against the unfixed walk, it claimed the two tiles behind with the berth
     * itself missing.
     */
    @Test
    public void testTheSquareUnderTheTrainIsClaimedWhereverItStands()
    {
        Point berth = onlyPointWithApproachThrough(TWO_COPIES, BEHIND_TWO_COPIES);

        Edge approach = edgeIntoVia(berth, BEHIND_TWO_COPIES);

        berth.setLocomotive(standing);

        berth.setArrivedFrom(layout.entrySideOf(approach, berth));

        standing.setTrainLength(2);

        Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

        assertTrue(claimed.containsKey(TWO_COPIES.toString()),
            "a train stands on " + TWO_COPIES + " and the railway does not claim that square at all.  A square with a"
            + " train on it that nobody has claimed is a square another train can be routed over.  Claimed: "
            + claimed.keySet());

        assertTrue(claimed.containsKey(BEHIND_TWO_COPIES.toString()),
            "a two-unit train on a square measured 1 does not reach " + BEHIND_TWO_COPIES + " behind it, so the walk"
            + " did not get past the berth and this claim is not testing what it says.  Claimed: " + claimed.keySet());
    }

    /**
     * A berth measured on an approach measured nowhere else takes a train that fits on it, and blocks nothing behind.
     *
     * SVX-B1's arrangement - the one `Automation.md` produces first, because it tells him to measure the station.
     * Until OB-278 the berth's own measurement was not spent, so the approach counted as unmeasured and the walk
     * claimed nothing at all, not even the square the train is on.
     *
     * The approach into TunnelLongPark with every square but the berth's taken to 0 for the length of this claim.
     */
    @Test(dependsOnMethods = "testTheParkIsMeasuredTheWayHeMeasuredIt")
    public void testAMeasuredBerthOnAnUnmeasuredApproachHoldsWhatFits()
    {
        Edge approach = edgeInto(park());

        List<Integer> was = new ArrayList<>(approach.getPlaceLengths());

        try
        {
            List<Integer> berthOnly = new ArrayList<>();

            for (int i = 0; i < was.size(); i++) berthOnly.add(i == was.size() - 1 ? was.get(i) : 0);

            approach.setPlaces(approach.getPlaceIds(), berthOnly);

            Map<String, Locomotive> claimed = standAtThePark(2);

            java.util.Set<String> behind = new java.util.LinkedHashSet<>(approach.getPlaceIds());

            behind.remove(PARK.toString());

            behind.retainAll(claimed.keySet());

            assertTrue(claimed.containsKey(PARK.toString()),
                "a two-unit train on a berth measured 2 is not claimed on its own square: " + claimed.keySet());

            assertTrue(behind.isEmpty(),
                "a two-unit train fits on a berth measured 2, and the railway blocks " + behind + " behind it");

            standing.setTrainLength(2);

            assertNull(Layout.whyABerthCannotHoldIt(Arrays.asList(approach), standing),
                "a two-unit train was refused a berth measured 2 with nothing measured behind it: "
                + Layout.whyABerthCannotHoldIt(Arrays.asList(approach), standing));
        }
        finally
        {
            approach.setPlaces(approach.getPlaceIds(), was);
        }
    }

    /**
     * And the station's size - its maximum train length - is what decides whether a train fits.
     *
     * That is the allowance Adam's ruling names.  TunnelLongPark holds 3.
     */
    @Test(dependsOnMethods = "testTheParkIsMeasuredTheWayHeMeasuredIt")
    public void testTheStationsSizeDecidesWhetherItFits()
    {
        Point park = park();

        List<Edge> route = new ArrayList<>(Arrays.asList(edgeInto(park)));

        standing.setTrainLength(3);

        assertTrue(park.validateTrainLength(standing), "TunnelLongPark, which holds 3, refused a three-unit train");

        standing.setTrainLength(4);

        assertFalse(park.validateTrainLength(standing), "TunnelLongPark, which holds 3, took a four-unit train");

        assertNotNull(Layout.whyTooLongForThisRoute(route, standing),
            "a four-unit train was not refused TunnelLongPark, which holds 3");
    }

    /**
     * Stands this class's train at the park as a train that drove in would stand, and reads the claims.
     */
    private static Map<String, Locomotive> standAtThePark(int units)
    {
        Point park = park();

        park.setLocomotive(standing);

        // WHICH WAY IT CAME IN, as a train that really arrived would have recorded - the park is reached by more than
        // one road and the walk stops at the fork without it.
        park.setArrivedFrom(layout.entrySideOf(edgeInto(park), park));

        standing.setTrainLength(units);

        return layout.placesCoveredByStandingTrains();
    }

    /**
     * The one Point on a square whose way in runs over another named square.
     *
     * @param square the station square the train stands on
     * @param via a square the way in runs over
     * @return the copy reached that way
     */
    private static Point onlyPointWithApproachThrough(TileKey square, TileKey via)
    {
        Point found = null;

        for (Point candidate : layout.getPoints())
        {
            TileKey at = session.getStationIndex().squareOf(candidate.getName());

            if (at == null || !at.equals(square)) continue;

            if (edgeIntoVia(candidate, via) == null) continue;

            if (found != null)
            {
                throw new AssertionError("two copies on " + square + " are reached over " + via
                    + ", so this railway no longer says which one the claim is about");
            }

            found = candidate;
        }

        if (found == null)
        {
            throw new AssertionError("nothing on " + square + " is reached over " + via + " any more, so this claim is"
                + " about a railway that has moved");
        }

        return found;
    }

    /** The run into a point that passes over a named square, or null. */
    private static Edge edgeIntoVia(Point point, TileKey via)
    {
        for (Edge edge : layout.getEdges())
        {
            if (edge.getEnd() == point && edge.getPlaceIds().contains(via.toString())
                && layout.entrySideOf(edge, point) != null)
            {
                return edge;
            }
        }

        return null;
    }

    /** The park's running Point. */
    private static Point park()
    {
        assertEquals(session.getStationIndex().nameOf(PARK), "TunnelLongPark",
            "the snapshot no longer calls " + PARK + " TunnelLongPark, so this class is about a railway that has moved");

        for (Point point : layout.getPoints())
        {
            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square != null && square.equals(PARK)) return point;
        }

        throw new AssertionError("no running Point stands on " + PARK);
    }

    /** The run a train arrives at the park along - the one over the square behind it. */
    private static Edge edgeInto(Point park)
    {
        return edgeIntoVia(park, BEHIND_PARK);
    }
}
