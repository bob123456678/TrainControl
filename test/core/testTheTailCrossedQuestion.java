package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The farthest sensor the tail of a hand-placed train has crossed: which sensors are offered, when the question is
 * put, and that the road it names is the one the tail then covers.
 *
 * Adam, 2026-09-14: *"the prompt should ask the user to pick from a list and select the farthest sensor the tail of
 * the train recently crossed."*
 *
 * **Each claim builds its own graph on sensors of its own** (Adam, 2026-09-14: *"Make sure tests for the different
 * scenarios use independent track diagrams"*).  The shape is the junction behind a platform: A -> J -> S with C -> J,
 * J a junction, every edge measured, the platform's approach one unit.  The train stands at S having come in from J's
 * side.
 *
 * @author Adam
 */
public class testTheTailCrossedQuestion
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Locomotive loc;
    private static Integer lengthWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();
        model = MarklinControlStation.init(null, true, false, false, false);
        loc = model.getLocByName(model.getLocList().get(0));
        lengthWas = loc.getTrainLength();
    }

    @AfterMethod(alwaysRun = true)
    public void putTheDialogBack()
    {
        TailCrossedPrompt.answerForTests(null);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (loc != null) loc.setTrainLength(lengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A train long enough to have crossed sensors on both roads past J is offered J, A and C, and is asked.
     *
     * Five units: one on the approach leaves four past J, so the tail crossed J; three more on either road leave one
     * past A or past C, so it crossed whichever of those it came by.
     */
    @Test
    public void testEachSensorOnEachRoadBackIsOffered() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2300, true);
        Point s = layout.getPoint("TQ_S");

        List<TailCrossedPrompt.Choice> choices = TailCrossedPrompt.choicesFor(layout, s, "W", 5, null);

        assertEquals(farthestOf(choices), java.util.Arrays.asList("TQ_J", "TQ_A", "TQ_C"),
            "a five-unit train at TQ_S can have crossed TQ_J and then TQ_A or TQ_C - the list offers something else");

        assertTrue(TailCrossedPrompt.wouldAsk(layout, s, "W", 5),
            "the tail can have crossed a sensor on either road back from the junction, which is exactly where the answer"
            + " changes which track is blocked - and the question is not put");
    }

    /**
     * Three units reach past J and cross neither A nor C: only J is offered, and nothing is asked.
     *
     * Every answer would be J, which cannot say which road the last two units lie on - so the walk stops at J, as it
     * always has, and a question would change nothing.
     */
    @Test
    public void testATailThatCrossesNoSensorPastTheJunctionIsNotAsked() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2310, true);
        Point s = layout.getPoint("TQ_S");

        assertEquals(farthestOf(TailCrossedPrompt.choicesFor(layout, s, "W", 3, null)), java.util.Arrays.asList("TQ_J"),
            "a three-unit train crosses TQ_J and no sensor beyond it");

        assertFalse(TailCrossedPrompt.wouldAsk(layout, s, "W", 3),
            "the question is put for a tail that crossed no sensor past the junction, where every answer is TQ_J");

        assertFalse(TailCrossedPrompt.wouldAsk(layout, s, "W", 1),
            "the question is put for a train no longer than its own approach");

        assertFalse(TailCrossedPrompt.wouldAsk(layout, s, null, 5),
            "the question is put with no arrival side, where there is no first road back to start from");
    }

    /**
     * The road the answer names is the one the tail covers: C chosen, C -> J claimed and A -> J not.
     */
    @Test
    public void testTheChosenRoadIsTheOneTheTailCovers() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2320, true);
        Point s = layout.getPoint("TQ_S");

        assertTrue(layout.moveLocomotive(loc.getName(), "TQ_S", false), "could not stand the train at TQ_S");

        s.setArrivedFrom("W");
        loc.setTrainLength(5);

        TailCrossedPrompt.answerForTests("TQ_C");

        List<Edge> road = TailCrossedPrompt.askAfterPlacement(layout, s, "W", 5, loc.getName(), null, null).getRoad();

        assertNotNull(road, "the answer TQ_C gave no road");

        s.setArrivedAlong(road);

        Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(layout.getEdge("TQ_C", "TQ_J")),
            "TQ_C was chosen as the farthest sensor the tail crossed, and the road from it, C -> J, is not covered."
            + " Covered: " + covered.keySet());

        assertFalse(covered.containsKey(layout.getEdge("TQ_A", "TQ_J")),
            "the tail was claimed along A -> J, a road the answer did not name. Covered: " + covered.keySet());

        TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);

        assertNull(TailCrossedPrompt.askAfterPlacement(layout, s, "W", 5, loc.getName(), null, null).getRoad(),
            "Not Known gave a road");
    }

    /**
     * Only a road a train can DRIVE in on is offered (OB-227).
     *
     * Adam, MT-435, 2026-09-15: *"the blocked orange path crosses switch 99 and switch 100 instead of going to
     * bottomsecondary, which would require the train to reverse in.  that isn't a realistic path."*  Here the rails out of
     * J run AWAY from the platform to A and C, so no train can have come from A or C to S without turning round: nothing
     * past J is offered.  This replaces a claim of 2026-09-14 that such a road was followed - a reading of "a tail is not
     * directional" that the road it lies on does not share.
     */
    @Test
    public void testOnlyARoadATrainCanDriveInOnIsOffered() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2330, false);
        Point s = layout.getPoint("TQ_S");

        assertEquals(farthestOf(TailCrossedPrompt.choicesFor(layout, s, "W", 5, null)), java.util.Arrays.asList("TQ_J"),
            "the rails out of TQ_J run away from the platform, so a train can only have come from TQ_J itself - and the"
            + " list offers sensors it would have had to reverse from");

        assertFalse(TailCrossedPrompt.wouldAsk(layout, s, "W", 5),
            "the question is put where only one road can be driven in on");
    }

    /**
     * A sensor exactly the train's length back is one its tail has crossed (OB-226).
     *
     * Adam, MT-435, 2026-09-15: *"When 75 407 DB is set to length 3, only BottomMainAPre is offered (2 away from the
     * station), but Tunnel should also be offered since it is 3 away."*  Here: one unit to J, three more to A and C - a
     * four-unit train reaches exactly to A and to C.
     */
    @Test
    public void testASensorExactlyTheTrainsLengthBackIsOffered() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2410, true);
        Point s = layout.getPoint("TQ_S");

        List<String> offered = farthestOf(TailCrossedPrompt.choicesFor(layout, s, "W", 4, null));

        assertTrue(offered.contains("TQ_A") && offered.contains("TQ_C"),
            "a four-unit train at TQ_S reaches exactly four units back, to TQ_A and TQ_C, and they are not offered: " + offered);

        assertFalse(farthestOf(TailCrossedPrompt.choicesFor(layout, s, "W", 3, null)).contains("TQ_A"),
            "a three-unit train is offered TQ_A, four units back");
    }

    /**
     * With no recorded road, the list starts on the sensor nearest the back when there is exactly one (FR-088).
     *
     * Adam, MT-435, 2026-09-15: *"The closest sensor to the back should be the default selection in the length window,
     * so the user can just click OK if appropriate."*  And, asked what to do where there are several: nearest the back
     * if there is one, nothing chosen otherwise.  One unit to J, then A -> J three and C -> J five: five units cross A
     * and not C, so A is the one sensor nearest the back.  With both at three, A and C both are, and nothing is chosen.
     */
    @Test
    public void testTheListStartsOnTheOneSensorNearestTheBack() throws Exception
    {
        Layout oneAtTheBack = aPlatformBehindAJunction(2420, true);

        oneAtTheBack.getEdge("TQ_C", "TQ_J").setLength(5);

        List<TailCrossedPrompt.Choice> choices =
            TailCrossedPrompt.choicesFor(oneAtTheBack, oneAtTheBack.getPoint("TQ_S"), "W", 5, null);

        int a = farthestOf(choices).indexOf("TQ_A");

        assertTrue(a >= 0, "precondition: TQ_A is not offered: " + farthestOf(choices));

        assertEquals(TailCrossedPrompt.preselectedIndex(choices, null), a,
            "TQ_A is the only sensor nearest the back of a five-unit train, and the list does not start on it");

        Layout two = aPlatformBehindAJunction(2430, true);

        List<TailCrossedPrompt.Choice> both = TailCrossedPrompt.choicesFor(two, two.getPoint("TQ_S"), "W", 5, null);

        assertEquals(TailCrossedPrompt.preselectedIndex(both, null), -1,
            "TQ_A and TQ_C are both nearest the back, and the list chose one of them: " + farthestOf(both));
    }

    /**
     * A -> J -> S, with C joining at J.
     *
     * @param s88 the first of four sensor numbers this fixture uses
     * @param towardsTheJunction true for rails A -> J and C -> J; false for J -> A and J -> C, laid the other way
     * @return the graph
     */
    private static Layout aPlatformBehindAJunction(int s88, boolean towardsTheJunction) throws Exception
    {
        Layout built = new Layout(model);

        built.createPoint("TQ_A", true, model.newFeedback(s88, null).getName());
        built.createPoint("TQ_C", true, model.newFeedback(s88 + 1, null).getName());
        built.createPoint("TQ_J", false, model.newFeedback(s88 + 2, null).getName());
        built.createPoint("TQ_S", true, model.newFeedback(s88 + 3, null).getName());

        if (towardsTheJunction)
        {
            built.createEdge("TQ_A", "TQ_J");
            built.createEdge("TQ_C", "TQ_J");
            built.getEdge("TQ_A", "TQ_J").setLength(3);
            built.getEdge("TQ_C", "TQ_J").setLength(3);
        }
        else
        {
            built.createEdge("TQ_J", "TQ_A");
            built.createEdge("TQ_J", "TQ_C");
            built.getEdge("TQ_J", "TQ_A").setLength(3);
            built.getEdge("TQ_J", "TQ_C").setLength(3);
        }

        built.createEdge("TQ_J", "TQ_S");
        built.getEdge("TQ_J", "TQ_S").setLength(1);
        built.getEdge("TQ_J", "TQ_S").setEntrySide("W");

        return built;
    }

    private static List<String> farthestOf(List<TailCrossedPrompt.Choice> choices)
    {
        List<String> names = new ArrayList<>();

        for (TailCrossedPrompt.Choice choice : choices) names.add(choice.getFarthest().getName());

        return names;
    }
}
