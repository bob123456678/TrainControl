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

        List<Edge> road = TailCrossedPrompt.askAfterPlacement(layout, s, "W", 5, loc.getName(), null, null);

        assertNotNull(road, "the answer TQ_C gave no road");

        s.setArrivedAlong(road);

        Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(layout.getEdge("TQ_C", "TQ_J")),
            "TQ_C was chosen as the farthest sensor the tail crossed, and the road from it, C -> J, is not covered."
            + " Covered: " + covered.keySet());

        assertFalse(covered.containsKey(layout.getEdge("TQ_A", "TQ_J")),
            "the tail was claimed along A -> J, a road the answer did not name. Covered: " + covered.keySet());

        TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);

        assertNull(TailCrossedPrompt.askAfterPlacement(layout, s, "W", 5, loc.getName(), null, null),
            "Not Known gave a road");
    }

    /**
     * A road laid against one-way rails is still followed: the tail lies across a rail whichever way traffic runs.
     *
     * Here the rails out of J run towards A and C, so the road the answer names is made of J -> C.  The tail walk used
     * to accept only an edge ENDING at the square it had reached, which is how a road a train drove is written.
     */
    @Test
    public void testARoadAlongRailsLaidTheOtherWayIsFollowed() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2330, false);
        Point s = layout.getPoint("TQ_S");

        assertTrue(layout.moveLocomotive(loc.getName(), "TQ_S", false), "could not stand the train at TQ_S");

        s.setArrivedFrom("W");
        loc.setTrainLength(5);

        TailCrossedPrompt.answerForTests("TQ_C");

        List<Edge> road = TailCrossedPrompt.askAfterPlacement(layout, s, "W", 5, loc.getName(), null, null);

        assertNotNull(road, "precondition: the answer TQ_C gave no road on the one-way junction");

        s.setArrivedAlong(road);

        Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(layout.getEdge("TQ_J", "TQ_C")),
            "the road to TQ_C runs along the rail J -> C, laid the other way, and the tail walk did not follow it past"
            + " the junction. Covered: " + covered.keySet());

        assertFalse(covered.containsKey(layout.getEdge("TQ_J", "TQ_A")),
            "the tail was claimed along J -> A, a road the answer did not name. Covered: " + covered.keySet());
    }

    /**
     * One road with a crossed sensor is enough to ask, when the other road has none (TLR-B1).
     *
     * A -> J measures three and C -> J four.  Five units leave four past J: A is crossed with one left, C is not.
     * "A" claims A -> J; "Not known" stops at J - different track, so the question has to be put.
     */
    @Test
    public void testOneCrossedRoadIsEnoughToAsk() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2340, true);

        layout.getEdge("TQ_C", "TQ_J").setLength(4);

        Point s = layout.getPoint("TQ_S");

        assertEquals(farthestOf(TailCrossedPrompt.choicesFor(layout, s, "W", 5, null)), java.util.Arrays.asList("TQ_J", "TQ_A"),
            "precondition: five units cross TQ_J and TQ_A and not TQ_C");

        assertTrue(TailCrossedPrompt.wouldAsk(layout, s, "W", 5),
            "the tail crossed TQ_A on one road back from the junction and no sensor on the other, so TQ_A and Not Known"
            + " block different track - and the question is not put");
    }

    /**
     * Two copies of one square are one road, not two (TLR-B2).
     *
     * A square a train may turn at is split into a lane copy and a turning copy (`AutonomyBuilder`), the same metal
     * under two names.  Counted by name they were two roads back from J, so the question was put on plain track and
     * the list offered the same sensor twice.
     */
    @Test
    public void testTwoCopiesOfOneSquareAreOneRoad() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("TQ_A", true, model.newFeedback(2350, null).getName());
        layout.createPoint("TQ_A (reverse)", true, model.newFeedback(2351, null).getName());
        layout.createPoint("TQ_J", false, model.newFeedback(2352, null).getName());
        layout.createPoint("TQ_S", true, model.newFeedback(2353, null).getName());

        layout.getPoint("TQ_A").setBlock("TQ_A-square");
        layout.getPoint("TQ_A (reverse)").setBlock("TQ_A-square");

        layout.createEdge("TQ_A", "TQ_J");
        layout.createEdge("TQ_A (reverse)", "TQ_J");
        layout.createEdge("TQ_J", "TQ_S");

        layout.getEdge("TQ_A", "TQ_J").setLength(3);
        layout.getEdge("TQ_A (reverse)", "TQ_J").setLength(3);
        layout.getEdge("TQ_J", "TQ_S").setLength(1);
        layout.getEdge("TQ_J", "TQ_S").setEntrySide("W");

        Point s = layout.getPoint("TQ_S");

        List<TailCrossedPrompt.Choice> choices = TailCrossedPrompt.choicesFor(layout, s, "W", 5, null);

        assertEquals(choices.size(), 2, "one road back past the junction, to one square, offers TQ_J and that square once -"
            + " the list offered " + farthestOf(choices));

        assertFalse(TailCrossedPrompt.wouldAsk(layout, s, "W", 5),
            "the question is put where the only road back past the junction reaches one square by two names");
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
