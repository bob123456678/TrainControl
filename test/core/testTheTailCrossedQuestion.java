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

    // RESTORED (MFR-C4): these seven claims were dropped when OB-226, OB-227 and FR-088 rewrote this class on
    // 2026-09-15, and the rules they hold - TLR-B1, TLR-B2, TLV-B1, TLV-B2/TLR-B3, TLV-B3, TLW-B1, TLW-C4 - are still
    // in the code.

    /**
     * One road with a crossed sensor is enough to ask, when the other road has none (TLR-B1).
     *
     * A -> J measures three and C -> J five.  Five units leave four past J: A is crossed with one left, C is not
     * (five, since OB-226 offers a sensor exactly the train's length back).
     * "A" claims A -> J; "Not known" stops at J - different track, so the question has to be put.
     */
    @Test
    public void testOneCrossedRoadIsEnoughToAsk() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2340, true);

        layout.getEdge("TQ_C", "TQ_J").setLength(5);

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
     * A fork right behind the platform: the answer picks the rail the tail lies on, and the walk follows it (TLV-B1).
     *
     * K -> S and L -> S both come in from the west.  The walk's first hop chose between them by side alone and took
     * whichever it met first, so answering L covered K -> S and left L -> S - where the train stands - open.
     */
    @Test
    public void testTheAnswerPicksTheRailRightBehindThePlatform() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        layout.createPoint("TQ_K", true, model.newFeedback(2360, null).getName());
        layout.createPoint("TQ_L", true, model.newFeedback(2361, null).getName());
        layout.createPoint("TQ_S", true, model.newFeedback(2362, null).getName());

        layout.createEdge("TQ_K", "TQ_S");
        layout.createEdge("TQ_L", "TQ_S");

        layout.getEdge("TQ_K", "TQ_S").setLength(3);
        layout.getEdge("TQ_L", "TQ_S").setLength(3);
        layout.getEdge("TQ_K", "TQ_S").setEntrySide("W");
        layout.getEdge("TQ_L", "TQ_S").setEntrySide("W");

        Point s = layout.getPoint("TQ_S");

        assertTrue(layout.moveLocomotive(loc.getName(), "TQ_S", false), "could not stand the train at TQ_S");

        s.setArrivedFrom("W");
        loc.setTrainLength(5);

        assertTrue(TailCrossedPrompt.wouldAsk(layout, s, "W", 5), "precondition: the fork behind the platform is not asked about");

        TailCrossedPrompt.answerForTests("TQ_L");

        List<Edge> road = TailCrossedPrompt.askAfterPlacement(layout, s, "W", 5, loc.getName(), null, null).getRoad();

        assertNotNull(road, "precondition: the answer TQ_L gave no road");

        s.setArrivedAlong(road);

        Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(layout.getEdge("TQ_L", "TQ_S")),
            "TQ_L was answered and the rail from it, right behind the platform, is not covered - the walk's first hop"
            + " picked by side alone. Covered: " + covered.keySet());

        assertFalse(covered.containsKey(layout.getEdge("TQ_K", "TQ_S")),
            "the tail was claimed along K -> S, which the answer did not name. Covered: " + covered.keySet());
    }

    /**
     * A train with no road still follows the only road back past a square it reaches by two names (TLV-B2, TLR-B3).
     *
     * The fork rule counted the junction's neighbours by name, so a lane copy and a turning copy of one square read as
     * two roads and the tail stopped at J - and with the question rightly not put there (TLR-B2), nothing could ever
     * extend it.  Adam's rule is to stop where the track SPLITS, and two copies of one square are not a split.
     */
    @Test
    public void testATrainWithNoRoadFollowsOneRoadUnderTwoNames() throws Exception
    {
        Layout layout = twoCopiesBehindAJunction(2370);

        Point s = layout.getPoint("TQ_S");

        assertTrue(layout.moveLocomotive(loc.getName(), "TQ_S", false), "could not stand the train at TQ_S");

        s.setArrivedFrom("W");
        loc.setTrainLength(5);

        assertNull(s.getArrivedAlong(), "precondition: the placed train has a road");

        Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(layout.getEdge("TQ_J", "TQ_S")), "precondition: the walk did not start");

        assertTrue(covered.containsKey(layout.getEdge("TQ_A", "TQ_J")) || covered.containsKey(layout.getEdge("TQ_A (reverse)", "TQ_J")),
            "five units at TQ_S reach four past the junction, whose only road back is to one square under two names, and"
            + " the tail stopped at the junction. Covered: " + covered.keySet());
    }

    /**
     * Two roads that part and meet again before the sensor are told apart in the list (TLV-B3).
     *
     * Behind S: M, then K or L, meeting again at N, then A.  "A (via N)" named both roads to A.
     */
    @Test
    public void testRoadsThatMeetAgainAreToldApart() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "TQ_S", "TQ_M", "TQ_K", "TQ_L", "TQ_N", "TQ_A" };

        for (int i = 0; i < names.length; i++)
        {
            layout.createPoint(names[i], true, model.newFeedback(2380 + i, null).getName());
        }

        String[][] rails = { { "TQ_M", "TQ_S" }, { "TQ_K", "TQ_M" }, { "TQ_L", "TQ_M" }, { "TQ_N", "TQ_K" }, { "TQ_N", "TQ_L" },
            { "TQ_A", "TQ_N" } };

        for (String[] rail : rails)
        {
            layout.createEdge(rail[0], rail[1]);
            layout.getEdge(rail[0], rail[1]).setLength(1);
        }

        layout.getEdge("TQ_M", "TQ_S").setEntrySide("W");

        List<TailCrossedPrompt.Choice> choices =
            TailCrossedPrompt.choicesFor(layout, layout.getPoint("TQ_S"), "W", 10, null);

        List<String> labels = new ArrayList<>();

        for (TailCrossedPrompt.Choice choice : choices) labels.add(choice.getLabel());

        assertTrue(farthestOf(choices).contains("TQ_A"), "precondition: TQ_A is not offered: " + farthestOf(choices));

        assertEquals(new java.util.LinkedHashSet<>(labels).size(), labels.size(),
            "two different roads read the same in the list, so choosing one is a guess: " + labels);
    }

    /**
     * Two ends of one square reached from one junction are two roads, and the tail still stops there (TLW-B1).
     *
     * A balloon: from J one rail reaches square A's west end and a loop reaches its east end.  The builder splits A by
     * arrival side, so those are two copies with one block - different track.  Counted by block they were one road,
     * so a train with no road ran on past J down whichever rail sorted first, and the question was not put.
     */
    @Test
    public void testTwoEndsOfOneSquareAreTwoRoads() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        layout.createPoint("TQ_A (westbound)", true, model.newFeedback(2390, null).getName());
        layout.createPoint("TQ_A (eastbound)", true, model.newFeedback(2391, null).getName());
        layout.createPoint("TQ_J", false, model.newFeedback(2392, null).getName());
        layout.createPoint("TQ_S", true, model.newFeedback(2393, null).getName());

        layout.getPoint("TQ_A (westbound)").setBlock("TQ_A-square");
        layout.getPoint("TQ_A (eastbound)").setBlock("TQ_A-square");

        layout.createEdge("TQ_A (westbound)", "TQ_J");
        layout.createEdge("TQ_A (eastbound)", "TQ_J");
        layout.createEdge("TQ_J", "TQ_S");

        layout.getEdge("TQ_A (westbound)", "TQ_J").setLength(3);
        layout.getEdge("TQ_A (eastbound)", "TQ_J").setLength(3);
        layout.getEdge("TQ_J", "TQ_S").setLength(1);
        layout.getEdge("TQ_J", "TQ_S").setEntrySide("W");

        Point s = layout.getPoint("TQ_S");

        assertTrue(layout.moveLocomotive(loc.getName(), "TQ_S", false), "could not stand the train at TQ_S");

        s.setArrivedFrom("W");
        loc.setTrainLength(5);

        Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

        assertTrue(covered.containsKey(layout.getEdge("TQ_J", "TQ_S")), "precondition: the walk did not start");

        assertFalse(covered.containsKey(layout.getEdge("TQ_A (westbound)", "TQ_J"))
            || covered.containsKey(layout.getEdge("TQ_A (eastbound)", "TQ_J")),
            "a train with no road ran on past a junction whose two rails reach opposite ends of one square - different"
            + " track, where Adam's rule is to stop. Covered: " + covered.keySet());

        assertTrue(TailCrossedPrompt.wouldAsk(layout, s, "W", 5),
            "the tail can have crossed either end of TQ_A, which are different track, and the question is not put");
    }

    /**
     * The list starts on the road the train already has, so OK does not throw it away (TLW-C4).
     */
    @Test
    public void testTheRecordedRoadIsTheOneOfferedFirst() throws Exception
    {
        Layout layout = aPlatformBehindAJunction(2400, true);
        Point s = layout.getPoint("TQ_S");

        List<TailCrossedPrompt.Choice> choices = TailCrossedPrompt.choicesFor(layout, s, "W", 5, null);

        int c = farthestOf(choices).indexOf("TQ_C");

        assertTrue(c >= 0, "precondition: TQ_C is not offered");

        assertEquals(TailCrossedPrompt.preselectedIndex(choices, choices.get(c).getRoad()), c,
            "the list does not start on the road the train already has, so OK with nothing moved erases it");

        assertEquals(TailCrossedPrompt.preselectedIndex(choices, null), -1,
            "a train with no road and TWO sensors nearest its back, TQ_A and TQ_C, has one chosen for it - FR-088 starts"
            + " the list on one only where exactly one qualifies");
    }

    /** A -> J and A (reverse) -> J, one square under two names, then J -> S. */
    private static Layout twoCopiesBehindAJunction(int s88) throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        layout.createPoint("TQ_A", true, model.newFeedback(s88, null).getName());
        layout.createPoint("TQ_A (reverse)", true, model.newFeedback(s88 + 1, null).getName());
        layout.createPoint("TQ_J", false, model.newFeedback(s88 + 2, null).getName());
        layout.createPoint("TQ_S", true, model.newFeedback(s88 + 3, null).getName());

        layout.getPoint("TQ_A").setBlock("TQ_A-square");
        layout.getPoint("TQ_A (reverse)").setBlock("TQ_A-square");

        layout.createEdge("TQ_A", "TQ_J");
        layout.createEdge("TQ_A (reverse)", "TQ_J");
        layout.createEdge("TQ_J", "TQ_S");

        layout.getEdge("TQ_A", "TQ_J").setLength(3);
        layout.getEdge("TQ_A (reverse)", "TQ_J").setLength(3);
        layout.getEdge("TQ_J", "TQ_S").setLength(1);
        layout.getEdge("TQ_J", "TQ_S").setEntrySide("W");

        return layout;
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

    // ------------------------------------------------------------------------------------------------ OB-276

    /**
     * Two copies of one square leaving by the same rail are ONE road back, offered once and no reason to ask (OB-276).
     *
     * Adam, 2026-09-23: *"when pasting 75 407 DB on bottomsecondary, the tail question lists rampdown twice in the
     * list."*  Measured on his railway, the two were RampDown's southbound lane and its northbound TURNING copy - a train
     * that came in from the south and turned - both leaving south over the same places.  Keyed by lane name they were
     * two roads.
     *
     * Built on the diagram, because the copies are the builder's: X - R - Q - A in a line, R a square trains may turn
     * at, so R is emitted as a plain copy and a turning one for each side, and two of them leave east over the same
     * rail towards Q.  Every square measured at one unit: a three-unit train at Q has its tail across R, which is the
     * first hop; a five-unit train at A has it across Q and R, which is the same two copies one hop further back.
     *
     * Before the fix: R offered twice (its two names), and `wouldAsk` true - a junction counted where there is one rail.
     *
     * @throws Exception from the build
     */
    @Test
    public void testTwoCopiesLeavingByOneRailAreOneRoadBack() throws Exception
    {
        Layout layout = aTurnRoundSquareBehindAPlatform();

        // THE FIRST HOP: a train at Q, whose tail reaches R one rail back.
        Point at = pointWithSensor(layout, 3304);

        assertNotNull(at, "precondition: the platform Q was not built as a Point");

        java.util.Set<String> copiesOfR = new java.util.TreeSet<>();

        for (Point p : layout.getPoints()) if ("3302".equals(p.getS88())) copiesOfR.add(p.getName());

        assertTrue(copiesOfR.size() >= 3,
            "precondition: R was not split into plain and turning copies, so there is nothing here to tell apart - " + copiesOfR);

        List<TailCrossedPrompt.Choice> choices = TailCrossedPrompt.choicesFor(layout, at, "W", 3, null);

        int offersOfR = 0;

        for (TailCrossedPrompt.Choice choice : choices) if ("3302".equals(choice.getFarthest().getS88())) offersOfR++;

        assertEquals(offersOfR, 1, "R is offered " + offersOfR + " times - one piece of metal read as several roads back: "
            + farthestOf(choices));

        assertFalse(TailCrossedPrompt.isATurningCopyForTests(choices.get(0).getFarthest()),
            "of two copies leaving by the same rail the turning copy was kept, so the road recorded is the turn's rather than"
            + " the lane's: " + farthestOf(choices));

        assertFalse(TailCrossedPrompt.wouldAsk(layout, at, "W", 3),
            "the question is put where the only two roads back are the same rail, so every answer describes the same track");

        // AND FURTHER BACK: a train at A, whose tail passes Q and reaches R - where the same two copies arrive at Q.
        Point further = pointWithSensor(layout, 3303);

        assertNotNull(further, "precondition: the platform A was not built as a Point");

        List<TailCrossedPrompt.Choice> behindQ = TailCrossedPrompt.choicesFor(layout, further, "W", 5, null);

        int deeperOffersOfR = 0;

        for (TailCrossedPrompt.Choice choice : behindQ) if ("3302".equals(choice.getFarthest().getS88())) deeperOffersOfR++;

        assertEquals(deeperOffersOfR, 1, "R is offered " + deeperOffersOfR + " times from two rails back - the same rail"
            + " counted as two roads past the first hop: " + farthestOf(behindQ));
    }

    private static Point pointWithSensor(Layout layout, int s88)
    {
        for (Point p : layout.getPoints()) if (String.valueOf(s88).equals(p.getS88())) return p;

        return null;
    }

    /**
     * 1,1 sensor X (3301) - 2,1 - 3,1 sensor R (3302), trains may turn - 4,1 - 5,1 sensor Q (3304) - 6,1 - 7,1 sensor A
     * (3303).  Every square measured at one unit.
     */
    private static Layout aTurnRoundSquareBehindAPlatform() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("tc-tail-one-rail").toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = new org.traincontrol.base.LayoutDiagram("main", 10, 4, null, null);

        org.traincontrol.base.LayoutDiagramComponent.componentType feedback =
            org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK;
        org.traincontrol.base.LayoutDiagramComponent.componentType straight =
            org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT;
        org.traincontrol.base.Accessory.accessoryDecoderType mm2 = org.traincontrol.base.Accessory.accessoryDecoderType.MM2;

        page.addComponent(feedback, 1, 1, 0, 0, 3301, 3301, mm2, null);
        page.addComponent(straight, 2, 1, 0, 0, 0, 0, mm2, null);
        page.addComponent(feedback, 3, 1, 0, 0, 3302, 3302, mm2, null);
        page.addComponent(straight, 4, 1, 0, 0, 0, 0, mm2, null);
        page.addComponent(feedback, 5, 1, 0, 0, 3304, 3304, mm2, null);
        page.addComponent(straight, 6, 1, 0, 0, 0, 0, mm2, null);
        page.addComponent(feedback, 7, 1, 0, 0, 3303, 3303, mm2, null);

        page.setPageId("1");

        org.traincontrol.automationui.AutonomySession session = new org.traincontrol.automationui.AutonomySession(folder);

        session.open(java.util.Arrays.asList(page));
        session.initialize("Tail");

        org.traincontrol.automationui.TileGraph.TileKey r = new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);

        session.setStation(r, true);
        session.setStation(new org.traincontrol.automationui.TileGraph.TileKey("main", 5, 1), true);
        session.setStation(new org.traincontrol.automationui.TileGraph.TileKey("main", 7, 1), true);
        session.setStation(new org.traincontrol.automationui.TileGraph.TileKey("main", 1, 1), true);
        session.setPointProperty(r, "canReverse", true);

        for (int x = 1; x <= 7; x++) session.setTileLength(new org.traincontrol.automationui.TileGraph.TileKey("main", x, 1), 1);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        return model.getAutoLayout();
    }

    private static List<String> farthestOf(List<TailCrossedPrompt.Choice> choices)
    {
        List<String> names = new ArrayList<>();

        for (TailCrossedPrompt.Choice choice : choices) names.add(choice.getFarthest().getName());

        return names;
    }
}
