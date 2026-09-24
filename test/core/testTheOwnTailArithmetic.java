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
 * The own-tail rule's arithmetic, on routes built by hand (OB-294; TDA-B1, TDA-C1, TDD-B1, TDD-C3).
 *
 * `Layout.whyItWouldMeetItsOwnTail` asked with a body and a route written out square by square, so each claim is about
 * the arithmetic alone.  The body lies behind the head on X (the standing square), A and B, measured 1, 2 and 1: A
 * begins 1 unit behind the head and B 3.
 *
 * MUTATION: judge a return whose way round has nothing measured on it, and the first claim fails; refuse at the first
 * return rather than the tightest, and the second does; leave the unmeasured squares out of the sentence, and the third.
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

    /** The rule, asked of this route for a train of this length. */
    private static String ask(Edge route, int length) throws Exception
    {
        Method rule = Layout.class.getDeclaredMethod("whyItWouldMeetItsOwnTail", List.class, Locomotive.class, Map.class);

        rule.setAccessible(true);

        train.setTrainLength(length);

        return (String) rule.invoke(null, Arrays.asList(route), train, BODY);
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
     * Where squares on the way round have no length, the refusal says how many, and that measuring them is the way past.
     *
     * @throws Exception from the rule
     */
    @Test
    public void testAPartlyMeasuredWayRoundSaysSo() throws Exception
    {
        String said = ask(edge(Arrays.asList("OT:Q", "OT:R", "OT:S", "OT:B"), Arrays.asList(1, 0, 0, 0)), 20);

        assertNotNull(said, "precondition: a twenty-unit train is not refused a way round of 1 measured square");

        String note;

        try
        {
            note = I18n.f("autolayout.errorOwnTailPartlyUnmeasured", 2);
        }
        catch (java.util.MissingResourceException none)
        {
            note = null;
        }

        assertTrue(note != null && said.contains(note), "the refusal does not say that two squares on the way round have"
            + " no length, so the only way past it names is a shorter train: " + said);
    }
}
