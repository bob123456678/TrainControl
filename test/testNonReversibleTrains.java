import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.StationIndex;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train that cannot reverse is only turned round where nobody would call that service.
 *
 * Both of these come from Adam watching his own layout run. He saw two non-reversible trains sent to
 * an ordinary platform and turned round there, through a reversing point - and he saw the platform's
 * caption showing one train twice, facing both ways at once.
 *
 * The two are not the same fault, but they share a cause worth stating: a square is several Points,
 * one per side a train can arrive by, and a rule written about one of them is not a rule about the
 * square. The reversal rule had been written about the terminus flag and not about the other way a
 * layout says "turn round here"; the caption had been written about Points and not about trains.
 */
public class testNonReversibleTrains
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        // The sensors these points stand on.  A destination Point insists on a feedback that exists,
        // which is the model refusing to describe a platform with no way of knowing a train is there.
        model.newFeedback(170, null);
        model.newFeedback(171, null);
    }

    /**
     * A locomotive that cannot reverse is refused a path that ends where it would be turned round.
     *
     * What Adam saw: two trains with a locomotive at one end only, sent to an ordinary platform and
     * reversed there because the route used a reversing point. That is a train running backwards in
     * service, which is the whole of what marking it non-reversible is meant to prevent.
     *
     * The rule already existed for a terminus and simply had not been written about a reversing point,
     * even though arriving at either turns the train round - the reversal at the end of a path fires
     * on both.
     */
    @Test
    public void testAReversingStationIsRefusedToANonReversibleTrain() throws Exception
    {
        Layout layout = twoPointLayout(true, true);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertFalse(layout.isPathClear(pathAcross(layout), loc, false),
                "a train that cannot reverse was allowed to finish at a station that turns it round.  "
                + "It is not the reversing that is wrong - it is doing it at a platform, in service, "
                + "to a train with a locomotive at one end only");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * The same station, out of full autonomy, is allowed: that is a parking spot.
     *
     * The distinction Adam asked for. A place autonomy may not choose as a destination is somewhere a
     * train is PUT rather than somewhere it calls - a parking road, a shed, a staging siding - and
     * turning one round there is unremarkable. Without this the staging planner could no longer bring
     * such a train home, which would trade one bug for another.
     */
    @Test
    public void testAParkingSpotIsAllowed() throws Exception
    {
        Layout layout = twoPointLayout(true, false);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "a train that cannot reverse was refused a PARKING spot.  Somewhere out of full "
                + "autonomy is exactly where it is supposed to be turned, and refusing it leaves the "
                + "staging planner no way to bring such a train home");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * And a train that can reverse is still allowed the ordinary platform.
     *
     * A rule that refused everybody would pass the first test and stop the railway, which is the same
     * bug wearing the other hat.
     */
    @Test
    public void testAReversibleTrainIsStillAllowed() throws Exception
    {
        Layout layout = twoPointLayout(true, true);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "a reversible train must still be able to use a reversing station - that is what they "
                + "are for");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * One locomotive on several copies of a square is one train in the caption, not several.
     *
     * Locking a path RESERVES every point along it for that train, deliberately without taking it off
     * anywhere else - that is how a junction is held against a second train. Where a path runs through
     * two copies of one square, the train really is on both, and the caption showed it twice with a
     * different arrow each time: "[BR &lt; |BR &gt;]", one train apparently facing both ways.
     */
    @Test
    public void testOneTrainIsNotShownTwice() throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // Two copies of one platform, both holding the same train.  Points built by hand have no
        // layout behind them and so do not sweep, which is what makes this state constructible here -
        // and it is the state a locked path produces in life.
        Point east = new Point("PLATFORM (eastbound)", false, null);
        Point west = new Point("PLATFORM (westbound)", false, null);

        east.setLocomotive(loc);
        west.setLocomotive(loc);

        List<Point> both = new LinkedList<>();
        both.add(east);
        both.add(west);

        assertEquals(StationIndex.oneEntryPerLocomotive(both).size(), 1,
            "the same train was listed once per copy of the platform it had reserved, so one "
            + "locomotive appeared on the diagram as two trains facing opposite ways");
    }

    /**
     * Two different trains on one platform are still both shown.
     *
     * This is a real thing on a real layout - two trains sent to one platform from opposite ends, each
     * arriving on the copy facing its own way - and the caption exists to say so. A fix that collapsed
     * them would hide a train.
     */
    @Test
    public void testTwoTrainsAreStillBothShown() throws Exception
    {
        assertTrue(model.getLocList().size() >= 2, "this test needs two locomotives");

        Point east = new Point("PLATFORM (eastbound)", false, null);
        Point west = new Point("PLATFORM (westbound)", false, null);

        east.setLocomotive(model.getLocByName(model.getLocList().get(0)));
        west.setLocomotive(model.getLocByName(model.getLocList().get(1)));

        List<Point> both = new LinkedList<>();
        both.add(east);
        both.add(west);

        assertEquals(StationIndex.oneEntryPerLocomotive(both).size(), 2,
            "two different trains on one platform must both be shown - dropping one puts a train on "
            + "the layout that is not on the diagram");
    }

    /**
     * Two points and one edge between them, with the far end set up as asked.
     *
     * @param reversing whether arriving at the far end turns the train round
     * @param auto whether autonomy may choose the far end as a destination
     */
    private static Layout twoPointLayout(boolean reversing, boolean auto) throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("REV_start", true, "170");
        layout.createPoint("REV_end", true, "171");

        Point end = layout.getPoint("REV_end");

        end.setReversing(reversing);
        end.setAutoDestination(auto);

        layout.createEdge("REV_start", "REV_end");

        return layout;
    }

    /**
     * The one path such a layout has.
     */
    private static List<Edge> pathAcross(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("REV_start", "REV_end"));

        return path;
    }
}
