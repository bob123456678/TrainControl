package core;

import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train that turns part way along comes to rest on the track after the turn.
 *
 * Adam, 2026-09-11, asked whether *"the track segments leading up to it"* means up to the berth or up
 * to the reversal: **"it can be either the reversal or the berth, depending on where switches are.
 * both need to be long enough."**
 *
 * `measuredRoomAtTheEndOf` stopped at the last SWITCH and at nothing else, so on a route with no switch
 * between the turn and the berth it summed the whole path. His own example, from `MON-C13`: a
 * `10 + 1 + 2` path admitted an eight-unit train into three units of room. The train turns after the
 * ten, backs in over the one and the two, and stands across track it does not fit on - where it blocks
 * everything through it while the model records it at the berth.
 *
 * **Both bounds, one walk.** Stopping at whichever is met first walking back gives the smaller sum, and
 * a train that fits in the smaller fits in the larger - so the single stop enforces both halves of the
 * ruling rather than checking them separately.
 *
 * **This is the one of the pair worth ruling on first**, in the words of the document that recorded it:
 * the other known-wrong case refuses trains that would fit, which is safe and annoying; this one
 * admitted trains that do not, which is neither.
 *
 * MUTATION: take the `isReversing()` stop out of the walk and the first test reads 13 where it should
 * read 3.
 *
 * @author Adam
 */
public class testTheRoomStopsAtAReversal
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    /** Adam's own figures, from the case he described. */
    private static final int BEFORE_THE_TURN = 10;
    private static final int AFTER_THE_TURN = 1 + 2;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);

        for (int sensor = 196; sensor <= 199; sensor++)
        {
            model.newFeedback(sensor, null);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The room is the track after the turn, not the whole run in.
     */
    @Test
    public void testARouteThatTurnsCountsOnlyWhatIsAfterTheTurn() throws Exception
    {
        Layout layout = aRunWithATurnInIt(true);

        Locomotive train = aTrainOf(8);

        Integer room = Layout.measuredRoomAtTheEndOf(theRoute(layout), train);

        assertNotNull(room, "the walk declined to judge a fully measured route");

        assertEquals(room.intValue(), AFTER_THE_TURN,
            "the room was measured as " + room + " over a route of "
            + (BEFORE_THE_TURN + AFTER_THE_TURN) + ", so the track BEFORE the turn was counted. A "
            + "train that reverses part way along comes to rest on what lies after the reversal only - "
            + "this is Adam's 10 + 1 + 2 case, which admitted an eight-unit train into three units of "
            + "room (MON-C13)");

        assertTrue(room < train.getTrainLength(),
            "control: the train has to be too long for the room after the turn, or this test would "
            + "pass on a railway where nothing was ever refused");
    }

    /**
     * And with nothing turning on it, the same route counts all of it.
     *
     * The control that matters: a stop at every point would refuse trains that fit, which is the
     * opposite defect and the one its sibling in `MON-C13` is about.
     */
    @Test
    public void testARouteWithNoTurnCountsTheWholeRunIn() throws Exception
    {
        Layout layout = aRunWithATurnInIt(false);

        Integer room = Layout.measuredRoomAtTheEndOf(theRoute(layout), aTrainOf(8));

        assertNotNull(room, "the walk declined to judge a fully measured route");

        assertEquals(room.intValue(), BEFORE_THE_TURN + AFTER_THE_TURN,
            "the same route with nothing reversing on it measured " + room + " rather than the whole "
            + "run in. The stop must be the reversal itself, not merely a point in the middle");
    }

    /**
     * Four points in a line, measured 10, 1, 2, with the middle one optionally a place trains turn.
     *
     * @param turns whether the point between the ten and the one reverses
     * @return the railway
     */
    private static Layout aRunWithATurnInIt(boolean turns) throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("REV_a", true, "196");
        layout.createPoint("REV_turn", true, "197");
        layout.createPoint("REV_c", true, "198");
        layout.createPoint("REV_berth", true, "199");

        layout.createEdge("REV_a", "REV_turn");
        layout.createEdge("REV_turn", "REV_c");
        layout.createEdge("REV_c", "REV_berth");

        layout.getEdge("REV_a", "REV_turn").setLength(BEFORE_THE_TURN);
        layout.getEdge("REV_turn", "REV_c").setLength(1);
        layout.getEdge("REV_c", "REV_berth").setLength(2);

        layout.getPoint("REV_turn").setReversing(turns);

        assertEquals(layout.getPoint("REV_turn").isReversing(), turns,
            "the fixture did not take: the middle point's reversing flag is not what this test set");

        return layout;
    }

    private static List<Edge> theRoute(Layout layout)
    {
        return Arrays.asList(
            layout.getEdge("REV_a", "REV_turn"),
            layout.getEdge("REV_turn", "REV_c"),
            layout.getEdge("REV_c", "REV_berth"));
    }

    /**
     * A locomotive of a stated length, which is what makes the walk answer at all.
     */
    private static Locomotive aTrainOf(int length) throws Exception
    {
        Locomotive train = model.getLocByName(model.getLocList().get(0));

        train.setTrainLength(length);

        assertEquals(train.getTrainLength().intValue(), length, "the train's length did not take");

        return train;
    }
}
