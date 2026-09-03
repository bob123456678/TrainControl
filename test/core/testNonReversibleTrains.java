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
        model.newFeedback(173, null);
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
     * A train too long for the berth is not backed over the switch (Adam, 2026-09-01).
     *
     * "if there is a switch right next to the station and the train is longer than the length of the
     * station track plus switch track, do we have guards against backing over it?"  There were none.
     * `validateTrainLength` is the only length rule in the model and it compares the train against a
     * MAXIMUM SOMEBODY TYPED on the station, not against the track that is actually there - and it is
     * skipped entirely when that maximum is zero, which is most squares on a real layout.
     *
     * So a train reversing into a berth shorter than itself stood across the switch behind it, and
     * nothing objected. This refuses the path instead.
     *
     * **Only where the answer is knowable.** The rule reads the lengths that have been recorded, and
     * unmeasured track is not a short berth - it is an unknown one. A layout that records no lengths
     * at all is unaffected; a layout that records some gets a notice in the editor asking for the ones
     * that matter, which is the other half of what he asked for.
     *
     * MUTATION: dropping the reversal test refuses nothing.
     *
     * The second mutation this used to name - "comparing against the whole path rather than the track
     * at the reversal" - was two things wrong (TS3-C3).  This fixture cannot tell them apart: its path
     * is two edges of 2 and 3 against a train of 10, so 5 and 3 both refuse.  And since Adam's ruling
     * of 2026-09-01 the whole run in IS the rule - `measuredRoomToReverseInto` sums every segment - so
     * it described the shipped code rather than a mutation.  What covers that distinction properly is
     * `testTheRoomIsEverySegmentLeadingUpToTheReversal`, two methods down, on a three-segment fixture.
     */
    @Test
    public void testATrainTooLongForTheBerthIsNotBackedOverTheSwitch() throws Exception
    {
        Layout layout = backingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();
        Integer wasLength = loc.getTrainLength();

        try
        {
            loc.setReversible(false);

            // The berth and its approach, measured; the train longer than both together.
            layout.getEdge("BACK_mid", "BACK_end").setLength(3);
            layout.getEdge("BACK_start", "BACK_mid").setLength(2);

            loc.setTrainLength(10);

            assertFalse(layout.isPathClear(pathThrough(layout), loc, false),
                "a train ten long was sent to reverse into a berth of three with an approach of two, "
                + "so it would stand across the switch behind it and nothing objected");

            // And the same railway with a train that fits.
            loc.setTrainLength(4);

            assertTrue(layout.isPathClear(pathThrough(layout), loc, false),
                "a train that fits in the berth and its approach was refused, so the rule refuses "
                + "more than it was asked to");

            // AND THE CONTROL: with nothing measured anywhere the rule cannot know, and says nothing.
            layout.getEdge("BACK_mid", "BACK_end").setLength(0);
            layout.getEdge("BACK_start", "BACK_mid").setLength(0);

            loc.setTrainLength(10);

            assertTrue(layout.isPathClear(pathThrough(layout), loc, false),
                "a layout that records no track lengths was refused a path on the strength of lengths "
                + "it does not have - unmeasured track is unknown, not short");
        }
        finally
        {
            loc.setReversible(was);
            loc.setTrainLength(wasLength);
        }
    }

    /**
     * With no switch on the route, the room is the WHOLE run in (Adam, 2026-09-01).
     *
     * "Do you sum the track segments leading up to it?  if they are long enough, then we are good.  if
     * segments < train length, then we can't reverse over the switch."
     *
     * **NARROWED on 2026-09-02, and this test is the half that survived.**  He ruled that the
     * measurement is "between the switch and the station", so the whole-route sum now applies only
     * where the route crosses no switch at all - which is this fixture, whose edges are hand-built and
     * carry no switch.  `testTheRoomIsMeasuredFromTheLastSwitch` is the other half.
     *
     * The first version of the guard added the last two edges, reading "the station track plus switch
     * track" as a count of segments rather than as an example of them - which is stricter than his
     * rule everywhere the run in is longer than that, and refuses trains that fit.
     *
     * Three segments is the shortest fixture that can tell the two apart: 2 + 3 + 4 is nine, the last
     * two are seven, and a train of eight fits under his rule and not under the first one.
     *
     * MUTATION: summing only the last two edges fails the first assertion.
     */
    @Test
    public void testTheRoomIsEverySegmentLeadingUpToTheReversal() throws Exception
    {
        Layout layout = longerBackingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();
        Integer wasLength = loc.getTrainLength();

        try
        {
            loc.setReversible(false);

            layout.getEdge("LONG_a", "LONG_b").setLength(2);
            layout.getEdge("LONG_b", "LONG_mid").setLength(3);
            layout.getEdge("LONG_mid", "LONG_end").setLength(4);

            loc.setTrainLength(8);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "a train of eight was refused a run in of nine, so the room is being measured over "
                + "part of the approach rather than all of it");

            // And ten does not fit in nine.
            loc.setTrainLength(10);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "a train of ten was accepted into a run in of nine, so nothing is being measured");

            // A SEGMENT NOBODY HAS MEASURED makes the total unknowable, and it is not judged.
            layout.getEdge("LONG_b", "LONG_mid").setLength(0);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "a path with an unmeasured segment was refused on a total that cannot be worked out - "
                + "an unknown length is not a zero one, and the editor's notice is what asks for it");
        }
        finally
        {
            loc.setReversible(was);
            loc.setTrainLength(wasLength);
        }
    }

    /**
     * With a switch on the route, the room is only the track after it (Adam, 2026-09-02).
     *
     * He was asked whether a train longer than berth-plus-switch may still come to rest across the
     * switch behind its berth when the run in as a whole is long enough, and answered: *"it depends on
     * the direction.  if the train crosses the fork through the base, then the track after the switch
     * has to be long enough to accommodate it.  in other words, between the switch and the station,
     * the length must be >= length of the train."*  Asked which crossings that covers, since on a
     * simple turnout every route touches the toe: *"for the switches, for simplicity, let's use any
     * direction, that way we are guaranteed to be safe."*
     *
     * So the binding constraint is the LAST switch before the destination, whichever way the route
     * crosses it.  A train longer than what is left beyond it stands on the points, blocking every
     * route through them, while the model records it only at the berth.
     *
     * **The same fixture as the test above, and that is the point.**  Nine units of run in, four of
     * them after the switch.  A train of eight passed the old rule and fails this one; nothing about
     * the layout changed except that the last edge now says where its switch is.
     *
     * MUTATION this catches: summing the whole path again - the first assertion passes a train of
     * eight into four units of room.  Also: counting the switch tile itself, which would make the
     * third assertion accept a train that does not fit.
     */
    @Test
    public void testTheRoomIsMeasuredFromTheLastSwitch() throws Exception
    {
        Layout layout = longerBackingInLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();
        Integer wasLength = loc.getTrainLength();

        try
        {
            loc.setReversible(false);

            layout.getEdge("LONG_a", "LONG_b").setLength(2);
            layout.getEdge("LONG_b", "LONG_mid").setLength(3);
            layout.getEdge("LONG_mid", "LONG_end").setLength(4);

            // The last edge crosses a switch, and four of its units lie beyond it.  This is what the
            // reducer records from the diagram; here it is set by hand, because this test is about
            // what the guard does with it.
            layout.getEdge("LONG_mid", "LONG_end").setRoomAtTheEnd(4);

            loc.setTrainLength(8);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "a train of eight was let into four units of track beyond the switch because the "
                + "whole nine-unit run in was counted - which is the rule Adam narrowed: \"between "
                + "the switch and the station, the length must be >= length of the train\"");

            loc.setTrainLength(4);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "a train of four was refused four units of room, so the measurement is short of the "
                + "stretch it is meant to be");

            loc.setTrainLength(5);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "a train of five was accepted into four units of room");

            // BOUNDED BUT UNMEASURED is not the same as unbounded, and it is not zero either.
            layout.getEdge("LONG_mid", "LONG_end").setRoomAtTheEnd(-1);

            assertTrue(layout.isPathClear(longPath(layout), loc, false),
                "a route whose stretch beyond the switch is unmeasured was judged anyway - an unknown "
                + "length is not a short one, and the editor's notice is what asks for it");

            // AND AN EARLIER UNMEASURED EDGE NO LONGER MATTERS, which is a widening rather than a
            // narrowing: the guard counts only the edges it actually uses.
            layout.getEdge("LONG_mid", "LONG_end").setRoomAtTheEnd(4);
            layout.getEdge("LONG_a", "LONG_b").setLength(0);

            loc.setTrainLength(5);

            assertFalse(layout.isPathClear(longPath(layout), loc, false),
                "an unmeasured edge at the far end of the route made the room unknowable, though "
                + "nothing beyond the last switch depends on it");
        }
        finally
        {
            loc.setReversible(was);
            loc.setTrainLength(wasLength);
        }
    }

    /**
     * Four points, so the run in to the terminus is three segments long.
     */
    private static Layout longerBackingInLayout() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("LONG_a", true, "170");
        layout.createPoint("LONG_b", true, "171");
        layout.createPoint("LONG_mid", true, "172");
        layout.createPoint("LONG_end", true, "173");

        layout.getPoint("LONG_mid").setReversing(true);
        layout.getPoint("LONG_end").setTerminus(true);

        layout.createEdge("LONG_a", "LONG_b");
        layout.createEdge("LONG_b", "LONG_mid");
        layout.createEdge("LONG_mid", "LONG_end");

        return layout;
    }

    /**
     * The one path such a layout has.
     */
    private static List<Edge> longPath(Layout layout)
    {
        List<Edge> path = new LinkedList<>();

        path.add(layout.getEdge("LONG_a", "LONG_b"));
        path.add(layout.getEdge("LONG_b", "LONG_mid"));
        path.add(layout.getEdge("LONG_mid", "LONG_end"));

        return path;
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
