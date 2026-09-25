package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * A train is not sent round a loop into its own tail (OB-294).
 *
 * Adam, 2026-09-24: *"EN57-203 from bottomsecondary to lowerfront may run over its own tail.  make sure the model factors
 * in whether the train will clear the area before it crosses over.  right now, If I set the train length to 20, is still
 * allowed to go, even though it would likely hit its own tail."*  And on what must still run: *"the check should pass if
 * the train would be gone (i.e. if that one was only length 4, for example)."*
 *
 * His railway's case: a train that came down RampDown and west along row 11 stands at BottomSecondary with its tail
 * back along that row.  Every way to LowerFront leaves west, round by the tunnel, and comes back east along the same row
 * - over the track the tail was lying on.  Nothing asked, because a train never blocks itself (behaviour.md 5c): its own
 * tail is behind it, and pulling forward off it is how a train leaves a berth.  Coming back to it round a loop is not.
 *
 * The frozen railway, because the loop is his and no hand-drawn fixture has one; LowerFront's size is raised for the
 * test, as he raised it for his, so that the station's own limit is not what refuses a long train.
 *
 * MUTATION: leave the train's starting body out of the question, and the 20-unit claim fails.  Refuse at the gap
 * itself rather than past it, and the remedy claim fails.  Judge a train leaving over its own tail as though its body
 * were behind it, and the turned control fails.
 *
 * @author Adam
 */
public class testATrainDoesNotRunIntoItsOwnTail
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;

    private static final String OUR_TRAIN = "OB-294 train";

    private static Locomotive train;

    private static Point atBottomSecondary;
    private static Edge downRampDown;
    private static Edge intoRampDown;
    private static final List<Point> lowerFront = new ArrayList<>();
    private static final List<Integer> lowerFrontWas = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        // This class's own train, as the frozen railway's README asks - not one of his borrowed.
        train = model.newMM2Locomotive(OUR_TRAIN, 2294);

        assertNotNull(train, "could not create this class's train");

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));
        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }

        atBottomSecondary = layout.getPoint("BottomSecondary");

        assertNotNull(atBottomSecondary, "precondition: the frozen railway has no BottomSecondary");

        for (Edge in : layout.getNeighborsAndIncoming(atBottomSecondary))
        {
            if (in.getEnd() == atBottomSecondary && in.getStart().getName().startsWith("RampDown")) downRampDown = in;
        }

        assertNotNull(downRampDown, "precondition: no rail comes into BottomSecondary from RampDown");

        for (Edge in : layout.getNeighborsAndIncoming(downRampDown.getStart()))
        {
            if (in.getEnd() == downRampDown.getStart() && in.getStart().getName().startsWith("TopMainPost")) intoRampDown = in;
        }

        assertNotNull(intoRampDown, "precondition: no rail comes into RampDown from TopMainPost");

        for (Point point : layout.getPoints())
        {
            if (point.getName().startsWith("LowerFront (") && point.isDestination())
            {
                lowerFront.add(point);
                lowerFrontWas.add(point.getMaxTrainLength());

                // As Adam did for his own test: the station's size is not the question here.
                point.setMaxTrainLength(0);
            }
        }

        assertFalse(lowerFront.isEmpty(), "precondition: the frozen railway has no LowerFront");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            for (int i = 0; i < lowerFront.size(); i++) lowerFront.get(i).setMaxTrainLength(lowerFrontWas.get(i));

            if (model != null) model.deleteLoc(OUR_TRAIN);
        }
        catch (Exception alreadyGone)
        {
        }
        finally
        {
            try
            {
                if (model != null) model.stop();
            }
            finally
            {
                if (sandbox != null) sandbox.close();
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    public void clearTheRailway()
    {
        atBottomSecondary.setLocomotive(null);
        atBottomSecondary.setArrivedFrom(null);
        atBottomSecondary.setArrivedAlong(null);
    }

    /** The train at BottomSecondary as it arrives there: down RampDown and west along row 11, as long as given. */
    private static void standItAsArrived(int length)
    {
        train.setTrainLength(length);

        atBottomSecondary.setLocomotive(train);
        atBottomSecondary.setArrivedFrom(layout.entrySideOf(downRampDown, atBottomSecondary));
        atBottomSecondary.setArrivedAlong(Arrays.asList(intoRampDown, downRampDown));
    }

    /** Every route from where the train stands to LowerFront, with the railway's reason for each - null where clear. */
    private static Map<List<Edge>, String> routesToLowerFront() throws Exception
    {
        Map<List<Edge>, String> out = new java.util.LinkedHashMap<>();

        for (Point end : lowerFront) out.putAll(layout.debugPath(train, atBottomSecondary, end));

        assertFalse(out.isEmpty(), "precondition: no route from BottomSecondary to LowerFront on the frozen railway");

        return out;
    }

    /**
     * Whether the send doors offer LowerFront: the right-click menu and the Auto tab list `getPossiblePaths(train, true)`,
     * less what `isOfferableToOperator` keeps off them.
     */
    private static boolean offersLowerFront() throws Exception
    {
        for (List<Edge> path : layout.getPossiblePaths(train, true))
        {
            Point end = path.get(path.size() - 1).getEnd();

            if (lowerFront.contains(end) && layout.isOfferableToOperator(end, train)) return true;
        }

        return false;
    }

    /** The own-tail sentence, as a pattern: the gap it names is group 1, or null where the reason is another. */
    private static Integer gapNamedBy(String reason)
    {
        if (reason == null) return null;

        String template;

        try
        {
            template = I18n.t("autolayout.errorWouldMeetItsOwnTail");
        }
        catch (java.util.MissingResourceException none)
        {
            // Before the sentence exists nothing can be it.
            return null;
        }

        StringBuilder regex = new StringBuilder();

        Matcher slot = Pattern.compile("\\{(\\d)\\}").matcher(template);

        int from = 0;

        while (slot.find())
        {
            regex.append(Pattern.quote(template.substring(from, slot.start())));
            regex.append("2".equals(slot.group(1)) ? "(\\d+)" : ".*?");
            from = slot.end();
        }

        regex.append(Pattern.quote(template.substring(from)));

        if (!template.contains("{2}")) return null;

        Matcher m = Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(reason);

        // The sentence begins the reason; what is unmeasured on the way round may follow it (TDA-B1).
        return m.lookingAt() ? Integer.valueOf(m.group(1)) : null;
    }

    /**
     * Twenty units at BottomSecondary is refused every way to LowerFront, and the refusal says it is the train's own tail.
     */
    @Test
    public void testALongTrainIsNotSentRoundIntoItsOwnTail() throws Exception
    {
        standItAsArrived(20);

        assertTrue(layout.placesCoveredByStandingTrains().containsValue(train), "precondition: a 20-unit train at"
            + " BottomSecondary claims no track behind it, so there is no tail to run into");

        for (Map.Entry<List<Edge>, String> route : routesToLowerFront().entrySet())
        {
            assertNotNull(route.getValue(), "EN57-203's case: a 20-unit train at BottomSecondary, its tail back along row"
                + " 11, was cleared to LowerFront round by the tunnel and back east along row 11 over its own tail - Adam,"
                + " OB-294: \"it is collision currently possible, and preventable\".  Route: " + route.getKey());

            assertNotNull(gapNamedBy(route.getValue()), "the route to LowerFront was refused, but not for running into"
                + " its own tail, which is the thing to fix - it said: " + route.getValue());

            assertFalse(layout.isPathClear(route.getKey(), train, false), "debugPath and isPathClear disagree");
        }
    }

    /**
     * Four units at BottomSecondary is gone from row 11 before it comes back, and goes (Adam: "the check should pass if
     * the train would be gone").
     */
    @Test
    public void testAShortTrainIsGoneBeforeItComesBack() throws Exception
    {
        standItAsArrived(4);

        boolean anyClear = false;

        for (Map.Entry<List<Edge>, String> route : routesToLowerFront().entrySet())
        {
            assertNull(gapNamedBy(route.getValue()), "a 4-unit train at BottomSecondary was refused LowerFront for running"
                + " into its own tail - Adam: \"the check should pass if the train would be gone (i.e. if that one was only"
                + " length 4, for example)\".  It said: " + route.getValue());

            if (route.getValue() == null) anyClear = true;
        }

        assertTrue(anyClear, "control: a 4-unit train at BottomSecondary is refused every way to LowerFront for some other"
            + " reason, so the claim above is not about its tail");
    }

    /**
     * The refusal's remedy is true: a train as long as the gap it names goes, and one unit more does not.
     */
    @Test
    public void testTheRefusalNamesTheLongestTrainThatGoes() throws Exception
    {
        standItAsArrived(20);

        List<Edge> route = null;
        Integer gap = null;

        for (Map.Entry<List<Edge>, String> each : routesToLowerFront().entrySet())
        {
            Integer named = gapNamedBy(each.getValue());

            if (named != null && (gap == null || named < gap))
            {
                gap = named;
                route = each.getKey();
            }
        }

        assertNotNull(gap, "no route from BottomSecondary to LowerFront was refused for the train's own tail, so there is"
            + " no remedy to check");

        assertTrue(gap > 0 && gap < 20, "the gap named is " + gap + ", which a 20-unit train would not be refused for");

        // WHAT ADAM SEES AT 20 (MT-571): LowerFront not offered by the send doors, Why not Moving? giving the own-tail
        // sentence for it, and the refusal naming where the way round comes back.
        assertFalse(offersLowerFront(), "a 20-unit EN57-203 at BottomSecondary is still offered LowerFront (MT-571)");

        boolean told = false;

        for (Map.Entry<String, String> why : layout.explainDestinations(train, true).entrySet())
        {
            for (Point end : lowerFront)
            {
                if (end.getName().equals(why.getKey()) && gapNamedBy(why.getValue()) != null) told = true;
            }
        }

        assertTrue(told, "Why not Moving? does not give the own-tail sentence for LowerFront at 20 units (MT-571)");

        String tightest = routesToLowerFront().get(route);

        assertTrue(tightest.contains("BottomMainAPre") && tightest.contains("BottomCrossover"), "the refusal does not name"
            + " BottomMainAPre -> BottomCrossover, where the way round comes back to the train (MT-571): " + tightest);

        // THE FIGURE ITSELF, measured on the frozen railway (TDD-C4): from BottomSecondary west round by the tunnel and
        // back to row 11 at 14,11 - 11,8 and 11,7, 11,4 and 10,3, 7,4 and 7,5, 7,11 and 9,12, 12,12 are measured, one
        // unit each.  A claim that the sentence agrees with itself would pass a rule that moved every figure alike.
        assertEquals(gap.intValue(), 9, "the way round from BottomSecondary back to its own tail measures 9 on the frozen"
            + " railway, and the refusal names " + gap);

        clearTheRailway();
        standItAsArrived(gap);

        String atTheGap = null;

        for (Map.Entry<List<Edge>, String> each : routesToLowerFront().entrySet())
        {
            if (each.getKey().equals(route)) atTheGap = each.getValue();
        }

        assertNull(gapNamedBy(atTheGap), "the refusal said a train of " + gap + " units or shorter can take this route,"
            + " and a train of exactly " + gap + " is refused for its own tail: " + atTheGap);

        // AND AT THE FIGURE IT GOES (MT-571 step 4): some way to LowerFront is clear of every rule, and it is offered.
        boolean anyClear = false;

        for (Map.Entry<List<Edge>, String> each : routesToLowerFront().entrySet())
        {
            if (each.getValue() == null) anyClear = true;
        }

        assertTrue(anyClear, "at " + gap + " units every way to LowerFront is refused for some reason, so the train does"
            + " not go (MT-571)");

        assertTrue(offersLowerFront(), "at " + gap + " units LowerFront is not offered by the send doors (MT-571)");

        clearTheRailway();
        standItAsArrived(gap + 1);

        String pastTheGap = null;

        for (Map.Entry<List<Edge>, String> each : routesToLowerFront().entrySet())
        {
            if (each.getKey().equals(route)) pastTheGap = each.getValue();
        }

        assertNotNull(gapNamedBy(pastTheGap), "a train of " + (gap + 1) + " units - one more than the refusal allows - is"
            + " not refused for its own tail: " + pastTheGap);

        assertFalse(offersLowerFront(), "at " + (gap + 1) + " units LowerFront is still offered by the send doors"
            + " (MT-571)");

        // AND EVERY WAY THERE, which is what a person sees (TDA-C2): a destination is offered if any route to it is
        // clear, so a train one unit past the figure is kept off LowerFront only if every route there refuses it.
        for (Map.Entry<List<Edge>, String> each : routesToLowerFront().entrySet())
        {
            assertNotNull(gapNamedBy(each.getValue()), "a train of " + (gap + 1) + " units is not refused for its own tail"
                + " on one of the ways to LowerFront, so it is still offered there: " + each.getKey() + " - "
                + each.getValue());
        }
    }

    /**
     * A train turned at Tunnel, its tail to the south, leaves south over its own body - which moves with it - and is not
     * refused for it.
     *
     * The square OB-285 turns a train at, because BottomSecondary is come into from one side and left by the other only.
     * Three units, so the body reaches past the first measured square on the way out: a rule that judged the body as
     * though it were behind the train would find the way out re-entering it at two units, and refuse.
     */
    @Test
    public void testATrainLeavingOverItsOwnTailIsNotRefusedForIt() throws Exception
    {
        Point atTunnel = layout.getPoint("Tunnel (southbound)");

        assertNotNull(atTunnel, "precondition: the frozen railway has no southbound copy of Tunnel");

        Edge wayOut = null;

        for (Edge out : layout.getNeighbors(atTunnel))
        {
            if (out.getEnd().getName().startsWith("BottomMainAPre")) wayOut = out;
        }

        assertNotNull(wayOut, "precondition: no rail leaves Tunnel's southbound copy for BottomMainAPre");

        train.setTrainLength(3);

        atTunnel.setLocomotive(train);
        atTunnel.setArrivedFrom("S");

        try
        {
            Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

            int bodyOnTheWayOut = 0;

            for (String place : wayOut.getPlaceIds())
            {
                if (train.equals(claimed.get(place))) bodyOnTheWayOut++;
            }

            assertTrue(bodyOnTheWayOut > 4, "precondition: the turned train's body lies on only " + bodyOnTheWayOut
                + " squares of its way south, so this is not the case of a train leaving over a body that reaches past"
                + " the first measured square");

            boolean anyClear = false;
            int asked = 0;

            for (Point end : layout.getPoints())
            {
                if (!end.isDestination() || end.isSamePlaceAs(atTunnel)) continue;

                for (Map.Entry<List<Edge>, String> route : layout.debugPath(train, atTunnel, end).entrySet())
                {
                    if (route.getKey().get(0) != wayOut) continue;

                    asked++;

                    assertNull(gapNamedBy(route.getValue()), "a train turned at Tunnel, leaving south over its own tail,"
                        + " was refused for running into it - its body is ahead of it and moves with it: "
                        + route.getValue());

                    if (route.getValue() == null) anyClear = true;
                }
            }

            assertTrue(asked > 0, "precondition: no route from Tunnel starts south towards BottomMainAPre");

            assertTrue(anyClear, "control: every route south from Tunnel is refused for some other reason, so the claim"
                + " above is not about the train's own body");
        }
        finally
        {
            atTunnel.setLocomotive(null);
            atTunnel.setArrivedFrom(null);
            atTunnel.setArrivedAlong(null);
        }
    }

    /**
     * A turn on the way is not a return to the train's own tail: after it the body is ahead, and drives back over the
     * track it was lying on.
     *
     * Asked of the rule directly, because on the frozen railway the room rule refuses a train this long at RampDown,
     * its one turning square with routes through it - the track behind RampDown measures a single unit.  The rule is
     * asked by every door on its own terms, so what it says of a turn must hold whatever the room.
     *
     * MUTATION: judge the track after a turn against the track before it, and this fails.
     *
     * @throws Exception from the route search
     */
    @Test
    public void testATurnOnTheWayIsNotAReturnToTheTail() throws Exception
    {
        Point atTunnel = layout.getPoint("Tunnel (southbound)");

        assertNotNull(atTunnel, "precondition: the frozen railway has no southbound copy of Tunnel");

        Edge fromTunnelPre = null;

        for (Edge in : layout.getNeighborsAndIncoming(atTunnel))
        {
            if (in.getEnd() == atTunnel && in.getStart().getName().startsWith("TunnelPre")) fromTunnelPre = in;
        }

        assertNotNull(fromTunnelPre, "precondition: no rail comes into Tunnel's southbound copy from TunnelPre");

        train.setTrainLength(5);

        atTunnel.setLocomotive(train);
        atTunnel.setArrivedFrom(layout.entrySideOf(fromTunnelPre, atTunnel));
        atTunnel.setArrivedAlong(Arrays.asList(fromTunnelPre));

        try
        {
            int turning = 0;

            for (List<Edge> route : layout.debugPath(train, atTunnel, atBottomSecondary).keySet())
            {
                boolean turns = false;

                for (int i = 0; i + 1 < route.size(); i++)
                {
                    if (route.get(i).getEnd().isReversing() && route.get(i).getEnd().getName().startsWith("RampDown"))
                    {
                        turns = true;
                    }
                }

                if (!turns) continue;

                turning++;

                assertNull(layout.whyItWouldMeetItsOwnTail(route, train), "a 5-unit train turning at RampDown on its way"
                    + " from Tunnel to BottomSecondary was refused for running into its own tail - after the turn its"
                    + " body is ahead of it, and the track it drives back over is track it is leaving: " + route);
            }

            assertTrue(turning > 0, "precondition: no route from Tunnel to BottomSecondary turns at RampDown on the frozen"
                + " railway");
        }
        finally
        {
            atTunnel.setLocomotive(null);
            atTunnel.setArrivedFrom(null);
            atTunnel.setArrivedAlong(null);
        }
    }

    /**
     * Return Home plans no route the railway would refuse for the train's own tail.
     *
     * The planner re-implements the runtime's rules rather than asking `isPathClear`, which reads live sensors, so every
     * rule the runtime gains it has to gain too - or it plans a first move the railway refuses, and the plan stops half
     * way (OB-073).  Asked of its route search directly, as `testATurnedTrainIsNotSentIntoAnotherTail` asks its tail
     * check.
     *
     * MUTATION: leave the own-tail question out of the planner's search, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testReturnHomePlansNoRouteIntoItsOwnTail() throws Exception
    {
        java.lang.reflect.Method route = org.traincontrol.automation.HomeStaging.class.getDeclaredMethod("firstClearRoute",
            Map.class, java.util.Set.class, Locomotive.class, Point.class, Point.class);

        route.setAccessible(true);

        java.lang.reflect.Field startField = org.traincontrol.automation.HomeStaging.class.getDeclaredField("start");

        startField.setAccessible(true);

        // THE CONTROL: four units is gone in time, and the planner finds a way.
        standItAsArrived(4);

        org.traincontrol.automation.HomeStaging staging = org.traincontrol.automation.HomeStaging.snapshot(layout);

        Map<Point, Locomotive> start = (Map<Point, Locomotive>) startField.get(staging);

        boolean anyPlanned = false;

        for (Point end : lowerFront)
        {
            if (route.invoke(staging, start, new java.util.HashSet<String>(), train, atBottomSecondary, end) != null)
            {
                anyPlanned = true;
            }
        }

        assertTrue(anyPlanned, "control: Return Home finds no way for a 4-unit train from BottomSecondary to LowerFront,"
            + " so the claim below is not about the train's tail");

        clearTheRailway();

        // THE CASE: twenty units.
        standItAsArrived(20);

        staging = org.traincontrol.automation.HomeStaging.snapshot(layout);

        start = (Map<Point, Locomotive>) startField.get(staging);

        for (Point end : lowerFront)
        {
            List<Edge> planned = (List<Edge>) route.invoke(staging, start, new java.util.HashSet<String>(), train,
                atBottomSecondary, end);

            if (planned == null) continue;

            assertNull(layout.whyItWouldMeetItsOwnTail(planned, train), "Return Home planned a 20-unit train from"
                + " BottomSecondary to LowerFront round into its own tail, which the railway refuses at the first move: "
                + planned);
        }
    }

    /**
     * Return Home judges a train it has already moved by the road it moved it along, not by what the railway records
     * there (TDA-C3).
     *
     * The plan's second move of a train starts where its first left it, and the railway knows nothing of that yet: here
     * the plan has brought the train to BottomSecondary down RampDown, and BottomSecondary is empty on the railway.  Asked
     * from the railway's record the body is nothing, and the plan sends a twenty-unit train round into it - a move the
     * railway refuses once the first has been driven, and a plan that stops half way (OB-073).
     *
     * MUTATION: judge the moved train by the railway's record of its square, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testReturnHomeJudgesAMovedTrainByTheRoadItCameAlong() throws Exception
    {
        java.lang.reflect.Method route = org.traincontrol.automation.HomeStaging.class.getDeclaredMethod("firstClearRoute",
            Map.class, java.util.Set.class, Locomotive.class, Point.class, Point.class);

        route.setAccessible(true);

        java.lang.reflect.Field moved = org.traincontrol.automation.HomeStaging.class.getDeclaredField("movedAlong");

        moved.setAccessible(true);

        // Nothing on the railway at BottomSecondary: the train is there only in the plan.
        clearTheRailway();

        for (int length : new int[] {4, 20})
        {
            train.setTrainLength(length);

            org.traincontrol.automation.HomeStaging staging = org.traincontrol.automation.HomeStaging.snapshot(layout);

            Map<Locomotive, List<Edge>> movedAlong = new java.util.HashMap<>();

            movedAlong.put(train, Arrays.asList(intoRampDown, downRampDown));

            moved.set(staging, movedAlong);

            Map<Point, Locomotive> state = new java.util.LinkedHashMap<>();

            state.put(atBottomSecondary, train);

            boolean anyPlanned = false;

            for (Point end : lowerFront)
            {
                List<Edge> planned = (List<Edge>) route.invoke(staging, state, new java.util.HashSet<String>(), train,
                    atBottomSecondary, end);

                if (planned != null) anyPlanned = true;
            }

            if (length == 4)
            {
                assertTrue(anyPlanned, "control: Return Home plans no way for a 4-unit train it has moved to BottomSecondary"
                    + " on to LowerFront, so the claim below is not about the train's tail");
            }
            else
            {
                assertFalse(anyPlanned, "Return Home planned a 20-unit train it had moved to BottomSecondary, down RampDown,"
                    + " on round to LowerFront into its own tail - it judged the train by the railway's record of the"
                    + " square, where nothing stands (TDA-C3)");
            }
        }
    }
}
