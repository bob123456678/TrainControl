package core;

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
     * A locomotive that cannot reverse is still refused a terminus.
     *
     * The long-standing rule, kept here because the round that changed everything around it is
     * exactly when a rule like this gets lost. A terminus is a place a train can only leave by
     * reversing, so sending one there that cannot is sending it somewhere it cannot leave.
     */
    @Test
    public void testATerminusIsRefusedToATrainThatCannotReverse() throws Exception
    {
        Layout layout = twoPointLayout(false, true);

        layout.getPoint("REV_end").setTerminus(true);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertFalse(layout.isPathClear(pathAcross(layout), loc, false),
                "a train that cannot reverse was sent to a terminus, which is a place it cannot "
                + "leave");

            loc.setReversible(true);

            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "and one that can reverse must still be allowed there - a rule that refused "
                + "everybody would pass the line above and close the terminus");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * Where the reversing-point rule actually lives, so nobody looks for it here.
     *
     * "In full autonomy a train is only ever reversed at a terminus" is a rule about which routes
     * autonomy CHOOSES, not about which routes are legal - a hand-driven move and the staging planner
     * may both use a headshunt. It is therefore tested in testLayoutPickPath, beside the other
     * choosing rules, and a first attempt to put it here took the manual route and the staging run
     * out with it.
     */
    @Test
    public void testTheReversingRuleIsTestedWhereItLives()
    {
        // Nothing to assert: this is a signpost, and the compiler is what keeps it from rotting into
        // a reference to a class that no longer exists.
        assertNotNull(testLayoutPickPath.class,
            "the reversing-point rule is tested in testLayoutPickPath");
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
