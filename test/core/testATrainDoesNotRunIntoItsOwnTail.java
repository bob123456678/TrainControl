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

        return m.matches() ? Integer.valueOf(m.group(1)) : null;
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

        clearTheRailway();
        standItAsArrived(gap);

        String atTheGap = null;

        for (Map.Entry<List<Edge>, String> each : routesToLowerFront().entrySet())
        {
            if (each.getKey().equals(route)) atTheGap = each.getValue();
        }

        assertNull(gapNamedBy(atTheGap), "the refusal said a train of " + gap + " units or shorter can take this route,"
            + " and a train of exactly " + gap + " is refused for its own tail: " + atTheGap);

        clearTheRailway();
        standItAsArrived(gap + 1);

        String pastTheGap = null;

        for (Map.Entry<List<Edge>, String> each : routesToLowerFront().entrySet())
        {
            if (each.getKey().equals(route)) pastTheGap = each.getValue();
        }

        assertNotNull(gapNamedBy(pastTheGap), "a train of " + (gap + 1) + " units - one more than the refusal allows - is"
            + " not refused for its own tail: " + pastTheGap);
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
}
