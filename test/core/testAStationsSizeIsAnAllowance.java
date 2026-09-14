package core;

import java.util.Map;
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
 * What a station measures is how much train it may HOLD, not track that absorbs the train standing on it.
 *
 * Adam, 2026-09-13: **"if the segment length is shorter, more should be blocked.  The station size is
 * an allowance, not a length."**
 *
 * **The ruling settles a disagreement between two walks**, filed as PRW-B4. Both work out where a
 * standing train's body lies, and they charged different squares for it:
 *
 *   - `Layout.walkStandingTrains` - the GUARD, which decides what the railway refuses - claimed the
 *     square the train stands on AND spent its length before walking back. So a train at a
 *     generously-sized platform had its whole body absorbed by the platform and blocked nothing behind
 *     it, however long it was.
 *   - `AutonomySession.walkBackFrom` - the PICTURE, the orange line and the grey - never charged that
 *     square at all.
 *
 * A repair that made the picture match the guard was tried on 2026-09-12 and reverted the same day: it
 * left a train shorter than its own square with nothing drawn behind it, which failed two claims
 * Adam had already validated. His ruling says why that was the wrong direction - the picture had it
 * right, and the guard was spending an allowance as though it were rail.
 *
 * **What this class pins is the ruling itself**, on the frozen copy of his railway: a platform measured
 * far larger than the train standing at it does not stop the train's body reaching the track behind.
 * The room rule still reads that same number as a capacity - `whyTooLongForThisRoute` asks whether the
 * train FITS - and nothing here changes that; the two questions simply stop sharing an answer.
 *
 * Its own fixture rather than a claim added to `testAShortTrainDoesNotBlockTheWholeRun`, whose figures
 * this has to change: measuring the park differently and rebuilding under that class's other claims
 * broke one of them, which is `new-field-needs-the-copy-constructor` in its fixture form.
 *
 * @author Adam
 */
public class testAStationsSizeIsAnAllowance
{
    /** The station whose declared size is the allowance, and the measured tile behind it. */
    private static final TileKey PARK = new TileKey("1 - Main", 10, 9);
    private static final TileKey BEHIND_PARK = new TileKey("1 - Main", 10, 10);

    /**
     * A second station, chosen because the walk really does take a SECOND hop behind it.
     *
     * The park above does not: the run into it ends at BottomMainA, where three roads meet, and the
     * fork rule stops the tail there however long the train is.  So nothing standing at the park can
     * say anything about the HOP budget, which only matters when there is another edge to reach.
     * TopMainR1's approach runs back through TopMainR1Pre to exactly one neighbour.
     */
    private static final TileKey TWO_HOPS = new TileKey("1 - Main", 5, 4);
    private static final TileKey BEHIND_TWO_HOPS = new TileKey("1 - Main", 5, 5);
    private static final TileKey BEYOND_TWO_HOPS = new TileKey("1 - Main", 5, 9);

    /** A berth measured on an approach that is measured NOWHERE else - the arrangement of SVX-B1. */
    private static final TileKey LONE_BERTH = new TileKey("1 - Main", 7, 7);

    /**
     * A berth where BOTH directions of the rail behind it report the same way in.
     *
     * The interesting square for the claim below, and the reason it is a different one: at the park
     * and at TopMainR1 only the arriving copy answers to the recorded side, so the walk cannot pick
     * the other.
     */
    private static final TileKey TWO_COPIES = new TileKey("1 - Main", 4, 5);
    private static final TileKey BEHIND_TWO_COPIES = new TileKey("1 - Main", 4, 4);

    /** Bigger than any train this class stands there, which is the whole point of the fixture. */
    private static final int ALLOWANCE = 10;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    private static Locomotive standing;
    private static Integer standingLengthWas;

    /**
     * Opens the frozen railway, measures the park generously and the tile behind it, and builds.
     *
     * @throws Exception when the railway cannot be read
     */
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        // THE TWO MEASUREMENTS THIS NEEDS. The snapshot measures nothing, so no tail reaches anywhere
        // and there would be nothing to be wrong about. The park is measured LARGER than the train -
        // that is the allowance - and the tile behind it is measured so that a claim on it is
        // something the walk had to reach rather than something it fell into.
        session.setTileLength(PARK, ALLOWANCE);
        session.setTileLength(BEHIND_PARK, 2);

        // AND THE SECOND STATION, arranged so the two budgets can be told apart: the berth is the
        // allowance, the tile behind it is two, and one tile on the edge BEYOND that is measured so
        // the walk is allowed to say anything about it at all.
        session.setTileLength(TWO_HOPS, ALLOWANCE);
        session.setTileLength(BEHIND_TWO_HOPS, 2);
        session.setTileLength(BEYOND_TWO_HOPS, 1);

        // AND A BERTH WITH NOTHING MEASURED BEHIND IT, which is what `Automation.md` produces first
        // because it tells him to measure the station.
        session.setTileLength(LONE_BERTH, ALLOWANCE);

        session.setTileLength(TWO_COPIES, ALLOWANCE);
        session.setTileLength(BEHIND_TWO_COPIES, 2);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        if (model.getLocList().isEmpty()) throw new SkipException("this fixture has no locomotives");

        standing = model.getLocByName(model.getLocList().get(0));

        standingLengthWas = standing.getTrainLength();
    }

    /**
     * Puts the train's length back and the layout preference with it.
     */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (standing != null) standing.setTrainLength(standingLengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The fixture can tell the two answers apart.
     *
     * Without this the claim below could pass on a railway where the park measures nothing, where
     * every rule agrees and there is no ruling to test.
     */
    @Test
    public void testTheParkIsBiggerThanTheTrain()
    {
        Point park = park();

        assertTrue(session.getStore().getTileLength(PARK) > 2,
            "the park measures " + session.getStore().getTileLength(PARK) + ", which is not bigger"
            + " than the two-unit train below - so the old rule and Adam's give the same answer here"
            + " and the claim after this cannot fail");

        assertFalse(edgeInto(park) == null,
            "no run arrives at " + park.getName() + ", so there is no track behind it to claim");

        assertNotNull(layout.entrySideOf(edgeInto(park), park),
            "the run into " + park.getName() + " does not say which side it comes in by, so a train"
            + " stood there has no recorded arrival and the tail walk stops at the fork before it"
            + " claims anything - the claim below would then fail for a reason about this fixture");

        assertTrue(edgeInto(park).getPlaceIds().contains(BEHIND_PARK.toString()),
            "the run into " + park.getName() + " does not carry " + BEHIND_PARK + " among its places,"
            + " so the square this claim looks for is not on the way in and the fixture is about a"
            + " railway that has moved: " + edgeInto(park).getPlaceIds());
    }

    /**
     * A train standing at a station bigger than itself still lies over the track behind it.
     *
     * MUTATION, run: restoring the standing square's `left -= span` in `Layout.walkStandingTrains`
     * fails this, claiming nothing behind the park.
     */
    @Test(dependsOnMethods = "testTheParkIsBiggerThanTheTrain")
    public void testTheAllowanceDoesNotAbsorbTheTrain()
    {
        Point park = park();

        park.setLocomotive(standing);

        // AND WHICH WAY IT CAME IN, as a train that really arrived would have recorded.
        //
        // The walk stops at a fork unless the standing square says which way the tail lies - "one way
        // means the tail certainly lies there; several means the graph cannot say which" - and this
        // park is reached by more than one road. Without it the walk claims nothing at all and this
        // claim fails for a reason about the fixture.
        park.setArrivedFrom(layout.entrySideOf(edgeInto(park), park));

        standing.setTrainLength(2);

        Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

        assertTrue(claimed.containsKey(BEHIND_PARK.toString()),
            "a two-unit train stands at " + park.getName() + ", which is measured at " + ALLOWANCE
            + ", and the railway claims nothing on " + BEHIND_PARK + " behind it. Adam, 2026-09-13:"
            + " \"the station size is an allowance, not a length\" - so the " + ALLOWANCE + " does not"
            + " absorb the two, and the body lies back over the track behind. Claimed: "
            + claimed.keySet());

        assertTrue(claimed.containsKey(PARK.toString()),
            "the square the train is standing on is not claimed at all, which is the other half of the"
            + " rule: an allowance that does not absorb the train still holds it. Claimed: "
            + claimed.keySet());
    }

    /**
     * The allowance is skipped ONCE, and the tail still reaches the edge beyond it.
     *
     * **SVV-C3, and why it had to be pinned rather than noted.**  Two budgets walk a standing train's
     * body: the PLACES budget, which claims square by square, and the HOP budget, which spends a whole
     * edge at a time to decide whether to walk back another one.  Adam's allowance ruling took the
     * standing square out of the first and SEV-B3 took it out of the second, and nothing could tell
     * either from the line it replaced.
     *
     * That is the PERMITTING side of the rule between two trains.  If the hop budget over-spends, the
     * walk stops early, the tail's last units are claimed by nobody, and another train is cleared over
     * metal this one is lying on.
     *
     * **Not at the park above.**  The first version of this claim stood a long train there and came
     * back red for a reason about the railway: the run into it ends at BottomMainA, where three roads
     * meet, and the fork rule stops the tail at the first hop whatever the budget says.  TopMainR1 was
     * found by walking every point and asking which ones have a second hop to reach.
     *
     * **The arrangement:** the berth measures {@value #ALLOWANCE}, the tile behind it 2, one tile on
     * the edge beyond is measured 1, and the train is 5.  Under the ruling the allowance is not track,
     * so the first hop costs 2 and 3 units are still to place - enough to reach the next edge.  Spend
     * the allowance as well and the first hop costs 12, which leaves nothing and stops the walk.
     *
     * MUTATION, run 2026-09-13: with `remaining -= segment.getLength()` the claimed places stop at the
     * first approach - `[5,4 5,5 5,6 5,7 5,8]` against `[... 5,9 5,10 4,10 3,10 2,10]` here.
     *
     * @throws Exception from the railway
     */
    @Test(dependsOnMethods = "testTheParkIsBiggerThanTheTrain")
    public void testTheTailReachesPastTheFirstEdgeBehindIt()
    {
        // The park's own claim leaves a train standing there, and TestNG does not order siblings.
        park().setLocomotive(null);

        Point berth = onlyPointWithApproachThrough(TWO_HOPS, BEHIND_TWO_HOPS);

        Edge approach = edgeIntoVia(berth, BEHIND_TWO_HOPS);

        int was = standing.getTrainLength();

        try
        {
            berth.setLocomotive(standing);

            berth.setArrivedFrom(layout.entrySideOf(approach, berth));

            standing.setTrainLength(5);

            Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

            java.util.Set<String> beyond = new java.util.LinkedHashSet<>(claimed.keySet());

            beyond.removeAll(approach.getPlaceIds());

            assertFalse(beyond.isEmpty(),
                "a five-unit train stands at " + berth.getName() + ", whose berth is measured "
                + ALLOWANCE + " and whose approach measures 2, and the railway claims nothing past"
                + " that approach. The " + ALLOWANCE + " is an allowance and not track (Adam,"
                + " 2026-09-13), so 5 units are spent behind the berth: 2 on the first tile and 3"
                + " still to place, which reaches the edge beyond. If the hop budget spends the"
                + " allowance as well it spends 12, the walk stops here, and the last units of a train"
                + " are claimed by nobody - which is what clears a second train over them. Claimed: "
                + claimed.keySet());
        }
        finally
        {
            standing.setTrainLength(was);

            berth.setLocomotive(null);
        }
    }

    /**
     * A berth measured on an approach measured nowhere else blocks nothing behind it.
     *
     * **SVX-B1.**  The measurement rule - "nothing can be said about an unmeasured segment" - reads
     * the edge's whole length, and `GraphReducer` builds that as the path PLUS the square the edge
     * arrives at.  On the first hop that square is the berth, so a berth measured on an otherwise
     * unmeasured approach passed the test with nothing spendable behind it, and the walk then claimed
     * every place on the way in.
     *
     * The pair was worse than either half: `whyABerthCannotHoldIt` got the matching bound a round
     * earlier, so the berth rule ACCEPTED such a train and this guard then blocked every road behind
     * it.  And it is the arrangement `Automation.md` produces first, because it tells him to measure
     * the station.
     *
     * MUTATION, run 2026-09-13: dropping `spendableAllowance` from the measurement test claims the
     * whole twelve-tile approach here instead of nothing.
     *
     * @throws Exception from the railway
     */
    @Test(dependsOnMethods = "testTheParkIsBiggerThanTheTrain")
    public void testAMeasuredBerthOnAnUnmeasuredApproachBlocksNothing()
    {
        park().setLocomotive(null);

        Point berth = null;

        Edge approach = null;

        for (Point candidate : layout.getPoints())
        {
            TileKey square = session.getStationIndex().squareOf(candidate.getName());

            if (square == null || !square.equals(LONE_BERTH)) continue;

            for (Edge edge : layout.getEdges())
            {
                if (edge.getEnd() != candidate) continue;

                if (layout.entrySideOf(edge, candidate) == null) continue;

                if (measuredPlaces(edge) != 1) continue;

                berth = candidate;

                approach = edge;
            }
        }

        if (berth == null)
        {
            throw new SkipException("no point on " + LONE_BERTH + " is reached by an approach whose"
                + " only measured place is the berth itself, so this railway cannot show SVX-B1");
        }

        int was = standing.getTrainLength();

        try
        {
            berth.setLocomotive(standing);

            berth.setArrivedFrom(layout.entrySideOf(approach, berth));

            standing.setTrainLength(5);

            Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

            java.util.Set<String> onTheApproach = new java.util.LinkedHashSet<>(approach.getPlaceIds());

            onTheApproach.retainAll(claimed.keySet());

            assertTrue(onTheApproach.isEmpty(),
                "a five-unit train stands at " + berth.getName() + ", whose berth is measured "
                + ALLOWANCE + " and whose approach is measured nowhere at all, and the railway has"
                + " blocked " + onTheApproach + " behind it. The berth's measurement is an allowance"
                + " and not track, so there is nothing measured behind this train and nothing can be"
                + " said about where its body lies - which is what the measurement rule is for. The"
                + " berth rule already reads it that way, and the two disagreeing is worse than"
                + " either: it accepts the train here and this blocks every road behind it.");
        }
        finally
        {
            standing.setTrainLength(was);

            berth.setLocomotive(null);
        }
    }

    /**
     * The square the train stands on is claimed wherever it stands, not only at some stations.
     *
     * **SVZ-B1: the other half of the ruling, and it was true at only some berths.**  `testTheAllowanceDoesNotAbsorbTheTrain`
     * asks for the standing square among the claims - an allowance that does not absorb the train
     * still holds it - and it passes at the park.  At TopMainR0Park the same train claims the track
     * behind and NOT the square it is standing on.
     *
     * A piece of rail is two `Edge` objects, one per direction, and both can report the same way in at
     * the square they meet.  `getNeighborsAndIncoming` sorts the outgoing ones first, so the first hop
     * there is the copy running AWAY from the berth - and an edge's places are the path plus the
     * square it ARRIVES at, so that copy's places are the track behind and never the berth.  Which
     * answer a station got depended on whether a copy pointing the other way happened to exist.
     *
     * The train arrived along the copy that ends here; that is what `arrivedFrom` records, and it is
     * the one whose places describe where a tail lies.  So the first hop prefers it.
     *
     * MUTATION, run 2026-09-13: this claim was written against the unfixed walk and failed there,
     * claiming `[4,4 4,3]` - the two tiles behind - with the berth itself missing.
     */
    @Test(dependsOnMethods = "testTheParkIsBiggerThanTheTrain")
    public void testTheSquareUnderTheTrainIsClaimedWhereverItStands()
    {
        park().setLocomotive(null);

        Point berth = onlyPointWithApproachThrough(TWO_COPIES, BEHIND_TWO_COPIES);

        Edge approach = edgeIntoVia(berth, BEHIND_TWO_COPIES);

        int was = standing.getTrainLength();

        try
        {
            berth.setLocomotive(standing);

            berth.setArrivedFrom(layout.entrySideOf(approach, berth));

            standing.setTrainLength(5);

            Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

            assertTrue(claimed.containsKey(TWO_COPIES.toString()),
                "a train stands on " + TWO_COPIES + " and the railway does not claim that square at"
                + " all - only the track behind it. The same claim at the park passes, and the only"
                + " difference is that this berth has a copy of its rail pointing the other way which"
                + " answers to the same side and sorts first. A square with a train on it that nobody"
                + " has claimed is a square another train can be routed over. Claimed: "
                + claimed.keySet());

            assertTrue(claimed.containsKey(BEHIND_TWO_COPIES.toString()),
                "the tile behind " + berth.getName() + " is not claimed either, so the walk did not"
                + " get past the berth and this claim is not testing what it says. Claimed: "
                + claimed.keySet());
        }
        finally
        {
            standing.setTrainLength(was);

            berth.setLocomotive(null);
        }
    }

    /**
     * And the allowance is still what the room rule reads, which is the question it IS for.
     *
     * The ruling separates two uses of one number; it does not retire either. A train longer than the
     * station's allowance still does not fit there.
     */
    @Test(dependsOnMethods = "testTheParkIsBiggerThanTheTrain")
    public void testTheAllowanceIsStillWhatDecidesWhetherItFits()
    {
        Point park = park();

        park.setLocomotive(null);

        standing.setTrainLength(ALLOWANCE + 5);

        java.util.List<org.traincontrol.automation.Edge> route =
            new java.util.ArrayList<>(java.util.Arrays.asList(edgeInto(park)));

        assertFalse(Layout.whyTooLongForThisRoute(route, standing) == null,
            "a train five units longer than everything measured on the way in to " + park.getName()
            + " was not refused. The station's size is an allowance - that is what Adam's ruling calls"
            + " it - and an allowance is exactly what says whether a train fits.");
    }

    /** How many of an edge's places carry a measurement at all. */
    private static int measuredPlaces(Edge edge)
    {
        int measured = 0;

        for (Integer span : edge.getPlaceLengths())
        {
            if (span != null && span > 0) measured++;
        }

        return measured;
    }

    /**
     * The one Point on a square whose way in runs over another named square.
     *
     * A station square carries a running Point per direction - `TopMainR1` has a northbound and a
     * southbound copy - and they are approached from opposite ends. Naming the tile the tail is
     * expected to lie on picks the copy this class means without depending on the builder's names.
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
                throw new SkipException("two copies on " + square + " are reached over " + via
                    + ", so this railway no longer says which one the claim is about");
            }

            found = candidate;
        }

        if (found == null)
        {
            throw new SkipException("nothing on " + square + " is reached over " + via + " any more,"
                + " so this test is about a railway that has moved");
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

    /** The park's running Point, or a skip when the snapshot has moved. */
    private static Point park()
    {
        String name = session.getStationIndex().nameOf(PARK);

        if (name == null || !"TunnelLongPark".equals(name))
        {
            throw new SkipException("the snapshot no longer calls " + PARK + " TunnelLongPark (it is "
                + name + "), so this test is about a railway that has moved");
        }

        for (Point point : layout.getPoints())
        {
            TileKey square = session.getStationIndex().squareOf(point.getName());

            if (square != null && square.equals(PARK)) return point;
        }

        throw new SkipException("no running Point stands on " + PARK);
    }

    /** The run a train arrives at the park along - the one whose places the tail is spent over. */
    private static org.traincontrol.automation.Edge edgeInto(Point park)
    {
        for (org.traincontrol.automation.Edge edge : layout.getEdges())
        {
            if (edge.getEnd() == park && edge.getPlaceIds().contains(BEHIND_PARK.toString()))
            {
                return edge;
            }
        }

        for (org.traincontrol.automation.Edge edge : layout.getEdges())
        {
            if (edge.getEnd() == park) return edge;
        }

        return null;
    }
}
