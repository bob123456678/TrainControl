package core;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The own-tail rule's arithmetic, on routes built by hand (OB-294; TDA-B1, TDA-C1, TDD-B1, TDD-C3; TDA2-C1, TDA2-C3,
 * TDD3-C1).
 *
 * `Layout.whyItWouldMeetItsOwnTail` asked with a body and a route written out square by square, so each claim is about
 * the arithmetic alone.  The body lies behind the head on X (the standing square), A and B, measured 1, 2 and 1: A
 * begins 1 unit behind the head and B 3.
 *
 * MUTATION: judge a return whose way round has nothing measured on it, and `testAWayRoundWithNothingMeasuredIsNotJudged`
 * fails; refuse at the first return rather than the tightest, and `testTheRefusalNamesTheTightestReturn` does; leave
 * the unmeasured stretches out of the sentence, and `testAPartlyMeasuredWayRoundSaysSo`; stop timing the route's own
 * places (R2b), or time them from the near end, and `testARouteThatComesBackOverItselfIsJudged` or
 * `testARouteIsTimedFromTheFarEndOfAPlace`; count neither the stretch the train comes back in (R2c) nor the one it left
 * a place in (R2d), and `testTheStretchTheTrainComesBackInIsCounted`.
 *
 * @author Adam
 */
public class testTheOwnTailArithmetic
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Layout layout;
    private static Locomotive train;
    private static int addresses = 2700;

    private static final String TRAIN = "Own-tail arithmetic";

    /** The body: each place behind the head, and how far behind the head it begins. */
    private static final Map<String, Integer> BODY = new LinkedHashMap<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);

        train = model.newMM2Locomotive(TRAIN, 2300);

        assertNotNull(train, "could not create this class's train");

        layout = new Layout(model);

        BODY.put("OT:X", 0);
        BODY.put("OT:A", 1);
        BODY.put("OT:B", 3);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null) model.deleteLoc(TRAIN);
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
            catch (Exception stopping)
            {
            }
            finally
            {
                if (sandbox != null) sandbox.close();
            }
        }
    }

    /** One edge over these places with these lengths, between two new Points. */
    private static Edge edge(List<String> places, List<Integer> lengths) throws Exception
    {
        String from = "OT_P" + (addresses++);
        String to = "OT_P" + (addresses++);

        for (String name : new String[] {from, to})
        {
            org.traincontrol.marklin.MarklinFeedback sensor = model.newFeedback(addresses++, null);

            model.setFeedbackState(sensor.getName(), false);

            layout.createPoint(name, false, sensor.getName());
        }

        Edge e = layout.createEdge(from, to);

        e.setPlaces(places, lengths);

        return e;
    }

    /** The rule, asked of this one-edge route for a train of this length. */
    private static String ask(Edge route, int length) throws Exception
    {
        return ask(Arrays.asList(route), length);
    }

    /** The rule, asked of this route for a train of this length. */
    private static String ask(List<Edge> route, int length) throws Exception
    {
        return ask(route, length, BODY);
    }

    /** The rule, asked of this route for a train of this length whose body lies on these places. */
    private static String ask(List<Edge> route, int length, Map<String, Integer> body) throws Exception
    {
        Method rule = Layout.class.getDeclaredMethod("whyItWouldMeetItsOwnTail", List.class, Locomotive.class, Map.class);

        rule.setAccessible(true);

        train.setTrainLength(length);

        return (String) rule.invoke(null, route, train, body);
    }

    /**
     * A route that comes back over its own earlier track - a reversing loop, out and back through one switch - is judged
     * like a return to the body: the head may come back to a place the route ran over only once the tail has left it
     * (TDA2-C3, TDD2-C2).  With no body at all, so nothing but the route's own timing can refuse it.
     *
     * Out over Q (2) and the switch, round the loop - L1 (3), L2 (3) - and back through the switch to Q: six units from
     * leaving the switch to coming back to it.
     *
     * MUTATION: stop timing the route's own places, and this fails.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testARouteThatComesBackOverItselfIsJudged() throws Exception
    {
        List<Edge> loop = Arrays.asList(edge(Arrays.asList("OT:Q2", "OT:SW", "OT:L1"), Arrays.asList(2, 0, 3)),
            edge(Arrays.asList("OT:L2", "OT:SW", "OT:Q2"), Arrays.asList(3, 0, 2)));

        Map<String, Integer> none = new LinkedHashMap<>();

        String seven = ask(loop, 7, none);

        assertNotNull(seven, "a seven-unit train was cleared round a loop of six back through the switch it left by - its"
            + " head meets its own tail at the switch");

        assertTrue(seven.contains(" 6 "), "the refusal did not name the loop, 6: " + seven);

        assertNull(ask(loop, 6, none), "a six-unit train - clear of the switch as its head comes back to it - was refused");
    }

    /**
     * A place the route ran over is free once the tail passes its FAR end, the end the head left by (TDD3-C1): timed from
     * the near end, a return to a measured square was allowed that square's length more than there is.  One edge over Q
     * (1), D (2), E (3) and back to D, nothing behind the head: from leaving D to coming back to it the head runs E's 3.
     *
     * MUTATION: time a route's own place from where the head reached it, and this fails - the figure is 5, and a
     * four-unit train goes.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testARouteIsTimedFromTheFarEndOfAPlace() throws Exception
    {
        List<Edge> route = Arrays.asList(edge(Arrays.asList("OT:Q5", "OT:D5", "OT:E5", "OT:D5"),
            Arrays.asList(1, 2, 3, 0)));

        Map<String, Integer> none = new LinkedHashMap<>();

        String four = ask(route, 4, none);

        assertNotNull(four, "a four-unit train was cleared back onto D, 3 units after its head left it - its head meets"
            + " its own tail there");

        assertTrue(four.contains(" 3 "), "the refusal did not name 3, the track from leaving D to coming back: " + four);

        assertNull(ask(route, 3, none), "a three-unit train - clear of D as its head comes back - was refused");
    }

    /**
     * The note counts what Mass Assign Lengths would ask for on the way round - the pieces the build marks on each edge's
     * places - once each, and nothing where it would ask for nothing (OB-297, ADA-C1).
     *
     * Out over X, the switch (3) and Y, and back onto the body at B.  With X and Y in two pieces still to measure, the note
     * says two; in one piece, one; with nothing to measure, nothing - whatever the places themselves measure.
     *
     * MUTATION: count the runtime's own pieces instead of the marks, or count a piece once per place, and this fails.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testTheNoteCountsWhatMassAssignAsksFor() throws Exception
    {
        Edge out = edge(Arrays.asList("OT:X7", "OT:SW7", "OT:Y7"), Arrays.asList(0, 3, 0));
        Edge back = edge(Arrays.asList("OT:B7"), Arrays.asList(0));

        Method mark = Edge.class.getMethod("setPiecesToMeasure", Map.class);

        Map<String, String> asked = new LinkedHashMap<>();

        asked.put("OT:X7", "piece 1");
        asked.put("OT:Y7", "piece 2");

        mark.invoke(out, asked);
        mark.invoke(back, new LinkedHashMap<String, String>());

        Map<String, Integer> body = new LinkedHashMap<>();

        body.put("OT:B7", 0);

        String said = ask(Arrays.asList(out, back), 20, body);

        assertNotNull(said, "precondition: a twenty-unit train is not refused a way round of 3 onto its own body");

        assertTrue(said.contains(I18n.f("autolayout.errorOwnTailPartlyUnmeasured", 2)), "two pieces Mass Assign would ask"
            + " for lie on the way round, and the note does not say two: " + said);

        // ONE PIECE ACROSS THE SWITCH'S TWO SIDES is one thing to measure.
        asked.put("OT:Y7", "piece 1");
        mark.invoke(out, asked);

        said = ask(Arrays.asList(out, back), 20, body);

        assertTrue(said != null && said.contains(I18n.f("autolayout.errorOwnTailPartlyUnmeasured", 1)), "one piece is"
            + " counted once per place: " + said);

        // NOTHING TO MEASURE, and the note says nothing, though X and Y measure 0.
        mark.invoke(out, new LinkedHashMap<String, String>());

        said = ask(Arrays.asList(out, back), 20, body);

        assertNotNull(said, "precondition: the refusal went with the marks");

        for (int n = 1; n <= 5; n++)
        {
            assertFalse(said.contains(I18n.f("autolayout.errorOwnTailPartlyUnmeasured", n)), "Mass Assign would ask for"
                + " nothing on the way round, and the note asks for " + n + ": " + said);
        }
    }

    /**
     * The stretch the train comes back in counts among those with no length (TDA2-C1, TDD2-C3): the note named only the
     * stretches wholly between leaving and coming back, and a way round whose last stretch had nothing measured said
     * nothing of it.
     *
     * MUTATION: count only the stretches wholly between, and this fails.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testTheStretchTheTrainComesBackInIsCounted() throws Exception
    {
        String said = ask(Arrays.asList(edge(Arrays.asList("OT:Q3"), Arrays.asList(1)),
            edge(Arrays.asList("OT:R3", "OT:B"), Arrays.asList(0, 0))), 20);

        assertNotNull(said, "precondition: a twenty-unit train is not refused a way round of 1 onto its own body");

        String note;

        try
        {
            note = I18n.f("autolayout.errorOwnTailPartlyUnmeasured", 1);
        }
        catch (java.util.MissingResourceException none)
        {
            note = null;
        }

        assertTrue(note != null && said.contains(note), "the way round comes back to the body in a stretch with nothing"
            + " measured on it, and the refusal does not say so: " + said);

        // AND THE STRETCH IT LEFT A PLACE IN, where it runs on in it: out of P and on over U, neither measured, round by M
        // and back to P.
        String left = ask(Arrays.asList(edge(Arrays.asList("OT:P4", "OT:U4"), Arrays.asList(0, 0)),
            edge(Arrays.asList("OT:M4"), Arrays.asList(5)), edge(Arrays.asList("OT:P4"), Arrays.asList(0))), 20,
            new LinkedHashMap<String, Integer>());

        assertNotNull(left, "precondition: a twenty-unit train is not refused a way round of 5 back to where it set out");

        assertTrue(note != null && left.contains(note), "the way round runs on, in the stretch it left P by, over track"
            + " with nothing measured on it, and the refusal does not say so: " + left);
    }

    /**
     * A way round with no measured square on it is not judged - *"only apply if lengths are specified"* - however much
     * of the body in front of the place it comes back to is measured (TDA-B1, TDD-B1).
     *
     * @throws Exception from the rule
     */
    @Test
    public void testAWayRoundWithNothingMeasuredIsNotJudged() throws Exception
    {
        // THE CONTROL: one measured square on the way round, and the same return is judged.
        assertNotNull(ask(edge(Arrays.asList("OT:Q", "OT:R", "OT:B", "OT:A"), Arrays.asList(1, 0, 0, 1)), 6),
            "control: a six-unit train is not refused a way round of one measured square back onto its own body, so the"
            + " claim below is not about the measurement");

        assertNull(ask(edge(Arrays.asList("OT:Q", "OT:R", "OT:B", "OT:A"), Arrays.asList(0, 0, 0, 1)), 6),
            "a way round with nothing measured on it was judged by the body in front of the square it comes back to - the"
            + " loop may be thirty units long.  The rule's own words: a return with nothing measured on the way round is"
            + " not judged");
    }

    /**
     * The figure named is the tightest return on the route, so the remedy it gives is true (TDA-C1, TDD-C3).
     *
     * The route comes up behind the body: to B after 5 measured units - 8 from where B begins to be left - and then to
     * A, whose way round is 6.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testTheRefusalNamesTheTightestReturn() throws Exception
    {
        Edge route = edge(Arrays.asList("OT:Q", "OT:B", "OT:A"), Arrays.asList(5, 0, 2));

        String atTwenty = ask(route, 20);

        assertNotNull(atTwenty, "precondition: a twenty-unit train is not refused a way round of 5 onto its own body");

        assertTrue(atTwenty.contains(" 6 "), "the refusal did not name the tightest return, 6 - it said: " + atTwenty);

        assertNotNull(ask(route, 8), "an eight-unit train was cleared, though it comes back onto A with only 6 units run"
            + " - the first return, onto B, allows 8 and was the only one asked");

        assertNull(ask(route, 6), "a six-unit train - the length the refusal names - was refused");
    }

    /**
     * Where a stretch of the way round has nothing measured on it, the refusal says how many, and that measuring them is
     * the way past - and a stretch whose length is stored on one of its squares is not one of them.
     *
     * The grain is the edge, sensor to sensor: on a fully measured railway many squares carry no length of their own, a
     * short piece drawn over several squares having fewer units than squares, and counting squares told Adam's that 21
     * of its squares on the way round had none.  Coarser than the pieces lengths are given in (TDA2-C1): an edge
     * measured only at its switch is not counted.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testAPartlyMeasuredWayRoundSaysSo() throws Exception
    {
        // THE CONTROL: every stretch measured, one of them on one square of two - nothing to measure.
        String measured = ask(Arrays.asList(edge(Arrays.asList("OT:Q"), Arrays.asList(1)),
            edge(Arrays.asList("OT:R", "OT:S"), Arrays.asList(0, 1)), edge(Arrays.asList("OT:B"), Arrays.asList(0))), 20);

        assertNotNull(measured, "precondition: a twenty-unit train is not refused a measured way round of 2");

        String said = ask(Arrays.asList(edge(Arrays.asList("OT:Q"), Arrays.asList(1)),
            edge(Arrays.asList("OT:R", "OT:S"), Arrays.asList(0, 0)), edge(Arrays.asList("OT:B"), Arrays.asList(0))), 20);

        assertNotNull(said, "precondition: a twenty-unit train is not refused a way round of 1 measured square");

        String note;

        try
        {
            note = I18n.f("autolayout.errorOwnTailPartlyUnmeasured", 1);
        }
        catch (java.util.MissingResourceException none)
        {
            note = null;
        }

        assertTrue(note != null && said.contains(note), "the refusal does not say that one stretch of the way round has"
            + " no length, so the only way past it names is a shorter train: " + said);

        assertFalse(measured.contains(I18n.f("autolayout.errorOwnTailPartlyUnmeasured", 1).substring(2)),
            "a way round whose every stretch is measured was said to have one with no length: " + measured);
    }
}
