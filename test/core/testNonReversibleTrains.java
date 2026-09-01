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
        model.newFeedback(172, null);
    }

    /**
     * A terminus is offered to a locomotive that cannot reverse, and never chosen for it.
     *
     * The long-standing rule, kept here because the round that changed everything around it is
     * exactly when a rule like this gets lost. A terminus is a place a train can only leave by
     * reversing, so sending one there that cannot is sending it somewhere it cannot leave.
     */
    @Test
    public void testATerminusIsOfferedByHandAndNotChosenByAutonomy() throws Exception
    {
        Layout layout = twoPointLayout(false, true);

        layout.getPoint("REV_end").setTerminus(true);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            // ALLOWED AT EXECUTION, as of Adam's ruling of 2026-09-01.
            //
            // "In manual operation, non reversing trains must be able to back into a terminus if the
            // graph makes that possible.  Otherwise we'd need a third kind of station."
            //
            // isPathClear is the tier EVERY door passes through, so refusing here refused the
            // right-click menu too. Measured on his own layout: 2-8-4 3505 SP is non-reversible,
            // stands at TopMainR2, and there is a five-edge route to TopMainR0Park - the graph makes
            // it possible and this said no.
            //
            // The rule has not gone; it has moved to where the reversing-station rule already lived,
            // on the doctrine written beside it: "Filtering at selection, never refusing at
            // execution." The half of this test that matters is now the one below.
            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "a locomotive that cannot reverse was refused a terminus at EXECUTION, which refuses "
                + "the operator asking for it by hand as well as autonomy");

            loc.setReversible(true);

            assertTrue(layout.isPathClear(pathAcross(layout), loc, false),
                "and one that can reverse must still be allowed there - a rule that refused "
                + "everybody would pass the line above and close the terminus");

            // AND THE HALF THAT KEEPS THE RULE: autonomy will not CHOOSE it.
            //
            // Asked through the explainer, which is the one list of standing reasons a station is
            // never picked - the same list pickPath's filter mirrors, and the one the "no available
            // paths" window prints.
            loc.setReversible(false);

            // ON THE GRAPH, because the explainer answers about a locomotive that is somewhere: with
            // nothing placed there are no paths to enumerate and it reports on nothing at all, which
            // would make the assertion below pass for the wrong reason.
            assertTrue(layout.moveLocomotive(loc.getName(), "REV_start", false),
                "could not place the locomotive, so the explainer has nothing to explain");

            String why = layout.explainDestinations(loc).get("REV_end");

            assertNotNull(why,
                "the explainer says nothing at all about the terminus, so autonomy has no recorded "
                + "reason for leaving it alone");

            assertTrue(why.toLowerCase().contains("reversible") || why.toLowerCase().contains("terminus"),
                "autonomy's reason for not choosing a terminus is not about reversing - it said: "
                + why);
        }
        finally
        {
            loc.setReversible(was);
            layout.moveLocomotive(null, "REV_start", true);
        }
    }

    // Where the reversing-point rule actually lives, so nobody looks for it here.
    //
    // "In full autonomy a train is only ever reversed at a terminus" is a rule about which routes
    // autonomy CHOOSES, not about which routes are legal - a hand-driven move and the staging planner
    // may both use a headshunt. It is therefore tested in
    // test/core/testLayoutPickPath.java's testFullAutonomyDoesNotDriveThroughAReversingPoint, beside
    // the other choosing rules, and a first attempt to put it here took the manual route and the
    // staging run out with it.
    //
    // This used to be a @Test method whose entire body was assertNotNull(testLayoutPickPath.class,
    // ...) - a compile-time-guaranteed non-null that asserted nothing and only occupied a slot in the
    // test count. A comment says the same thing without doing that.

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
     * ...but it may BACK INTO one, when the way there turns it round (Adam, 2026-08-31).
     *
     * His words, on MT-245: "trains should be allowed to back into terminuses if they are not
     * reversible (that's why we have the reversing point at feedback 2013)."
     *
     * The rule above is about a train that would have to reverse to LEAVE. A train that passes a
     * reversing point on the way arrives at the terminus already turned - it backs in - and leaves
     * forwards, so it never runs backwards out of anywhere and the objection does not apply.
     *
     * Measured on his own layout before this was changed: TunnelLeftPark is a terminus, and EN57-203
     * and EN57-947 are both non-reversible, so this one clause was refusing both the manual send and
     * the home. `isAutoDestination` is asked only by pickPath, never by getPossiblePaths or
     * isPathClear, so a non-automatic station was always manually selectable - this was the only thing
     * standing in the way of either.
     *
     * The escape is deliberately here and not in canRest: whether a train can be TURNED on the way is
     * a property of the route, and only a route can answer it.
     *
     * MUTATION: dropping the reversesAlongTheWay clause fails this; dropping the whole terminus rule
     * fails the method above, whose fixture has no reversing point.
     */
    @Test
    public void testATrainThatCannotReverseMayBackIntoATerminus() throws Exception
    {
        Layout layout = backingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertTrue(layout.getPoint("BACK_mid").isReversing(),
                "the fixture did not take: the middle point must be a reversing point");

            assertTrue(layout.getPoint("BACK_end").isTerminus(),
                "the fixture did not take: the far point must be a terminus");

            assertTrue(layout.isPathClear(pathThrough(layout), loc, false),
                "a train that cannot reverse was refused a terminus it would have BACKED into. The "
                + "reversing point on the way turns it, so it arrives running backwards and leaves "
                + "forwards - which is what the reversing point is for");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * Start, a reversing point, and a terminus beyond it.
     */
    private static Layout backingInLayout() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("BACK_start", true, "170");
        layout.createPoint("BACK_mid", true, "171");
        layout.createPoint("BACK_end", true, "172");

        layout.getPoint("BACK_mid").setReversing(true);
        layout.getPoint("BACK_end").setTerminus(true);

        layout.createEdge("BACK_start", "BACK_mid");
        layout.createEdge("BACK_mid", "BACK_end");

        return layout;
    }

    /**
     * The one path such a layout has, through the reversing point.
     */
    private static List<Edge> pathThrough(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("BACK_start", "BACK_mid"));
        path.add(layout.getEdge("BACK_mid", "BACK_end"));

        return path;
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
