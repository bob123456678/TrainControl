package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train longer than the track it is being sent into is refused it.
 *
 * Adam, 2026-09-06: **"BR 75 407 DB shouldn't be able to go from bottommainpost to tunnellongpark
 * because the track length from bottommainpost to tunnellongpark is 2, and the train is too long.  It
 * would have to advance further forward before being eligible to go in.  Make a test that turns red
 * that first creates a train with a certain length, then makes the required segments too short or
 * other combinations (try with =, >, <, and < by 1)."**
 *
 * **Four cases, because the interesting failure is at the boundary.**  A rule that refuses a train
 * twice too long and admits one half the size can still be wrong about the two cases either side of
 * exactly-fits, and those are the ones a real railway meets: a berth measured to the train that lives
 * in it, and a train one unit too long for it.  `=` must be admitted - a train that exactly fits, fits
 * - and `room - 1` must be refused.
 *
 * The room is measured from the LAST SWITCH, not from the start of the path: a train that fits between
 * that switch and the berth fits between any earlier switch and the berth too, and one that does not
 * comes to rest standing on the switch.  So the fixture puts a switch part way along and the answer
 * depends only on what lies after it.
 */
public class testATrainTooLongIsRefusedTheBerth
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUp() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * A train the same length as the room it is sent into is admitted.
     */
    @Test
    public void testATrainThatExactlyFitsIsAdmitted() throws Exception
    {
        assertTrue(admitted(4, 4),
            "a train of 4 was refused 4 units of room. A berth measured to the train that lives in it"
            + " is the ordinary case, and refusing it means every correctly measured berth is unusable");
    }

    /**
     * One unit longer than the room, which is the case that matters most.
     */
    @Test
    public void testATrainOneUnitTooLongIsRefused() throws Exception
    {
        assertFalse(admitted(5, 4),
            "a train of 5 was admitted into 4 units of room. Adam's case exactly: it comes to rest"
            + " protruding one unit past the switch, which is where the next train through would hit"
            + " it");
    }

    /**
     * Comfortably too long.
     */
    @Test
    public void testATrainMuchTooLongIsRefused() throws Exception
    {
        assertFalse(admitted(9, 2),
            "a train of 9 was admitted into 2 units of room - which is Adam's own example, a train"
            + " sent from BottomMainPost into TunnelLongPark with 2 units of track leading in");
    }

    /**
     * And comfortably short, which must still be allowed - the control for all three above.
     */
    @Test
    public void testATrainThatFitsEasilyIsAdmitted() throws Exception
    {
        assertTrue(admitted(1, 4),
            "a train of 1 was refused 4 units of room, so this rule refuses everything and the three"
            + " refusals above prove nothing about lengths");
    }

    // ---------------------------------------------------------------- the fixture

    /**
     * Builds a run into a terminus with a switch part way along, and asks whether the path is clear.
     *
     * @param trainLength how long the train is
     * @param roomAfterTheSwitch how much measured track lies between the switch and the berth
     * @return whether isPathClear allowed it
     * @throws Exception on a failure to build
     */
    private boolean admitted(int trainLength, int roomAfterTheSwitch) throws Exception
    {
        Layout layout = new Layout(model);

        String suffix = "_" + trainLength + "_" + roomAfterTheSwitch;

        org.traincontrol.marklin.MarklinFeedback one =
            model.newFeedback(400 + trainLength * 10 + roomAfterTheSwitch, null);

        org.traincontrol.marklin.MarklinFeedback two =
            model.newFeedback(401 + trainLength * 10 + roomAfterTheSwitch, null);

        org.traincontrol.marklin.MarklinFeedback three =
            model.newFeedback(402 + trainLength * 10 + roomAfterTheSwitch, null);

        model.setFeedbackState(one.getName(), false);
        model.setFeedbackState(two.getName(), false);
        model.setFeedbackState(three.getName(), false);

        layout.createPoint("START" + suffix, true, one.getName());
        layout.createPoint("MIDDLE" + suffix, false, two.getName());
        layout.createPoint("BERTH" + suffix, true, three.getName());

        Point start = layout.getPoint("START" + suffix);
        Point middle = layout.getPoint("MIDDLE" + suffix);
        Point berth = layout.getPoint("BERTH" + suffix);

        // The berth is the end of the line, which is what brings the room rule into play at all.
        berth.setTerminus(true);

        // A long approach BEFORE the switch, which must not count: the binding measurement is what
        // lies after the last switch, and a permissive rule that adds this in admits everything.
        Edge approach = layout.createEdge(start.getName(), middle.getName());
        Edge run = layout.createEdge(middle.getName(), berth.getName());

        approach.setLength(50);
        run.setLength(roomAfterTheSwitch);

        // THE SWITCH, as the graph expresses it.  `crossesASwitch` is `roomAtTheEnd != MIN_VALUE`, and
        // roomAtTheEnd is the stretch measured AFTER the switch - so this is both "there is a switch
        // here" and "this much track lies beyond it", which is the quantity the rule is about.
        run.setRoomAtTheEnd(roomAfterTheSwitch);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        loc.setTrainLength(trainLength);

        start.setLocomotive(loc);

        List<Edge> path = new ArrayList<>();
        path.add(approach);
        path.add(run);

        return layout.isPathClear(path, loc, false);
    }
}
