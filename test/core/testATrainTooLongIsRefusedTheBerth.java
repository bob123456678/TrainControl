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

    /**
     * And it is not OFFERED, which is the sentence Adam actually wrote.
     *
     * **"BottomMainPost -> TunnelLongPark is manually selectable on the current layout.  It is a train
     * that has been staged at BottomMainPost by the user, ready to be parked next.  It should stop
     * being selectable while the track lengths are too short."**
     *
     * A rule can be right and a menu still wrong.  The four cases above ask `isPathClear` directly;
     * this asks `getPossiblePaths`, which is what the right-click menu lists and what the staging
     * planner reads.  They are only the same question because `getPossiblePaths` calls `isPathClear`
     * on every candidate - and that is worth pinning, because a menu built from any other source would
     * offer a berth the railway then refuses on the first move.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATooLongTrainIsNotEvenOfferedTheBerth() throws Exception
    {
        // BUILT AND ASKED ONE AT A TIME, because every fixture shares the model's single Locomotive
        // object - so building the second one reset the first one's length, and the "fits" case was
        // being evaluated at the "does not fit" length.  The first version of this test failed on its
        // own control for that reason, which is the control doing its job.
        Fixture fits = build(4, 4);

        assertTrue(offersTheBerth(fits),
            "a train that fits is not offered the berth at all, so the refusal below means only that"
            + " this menu offers nothing to anybody");

        Fixture doesNot = build(5, 4);

        assertFalse(offersTheBerth(doesNot),
            "a train too long for the berth is still listed as somewhere it can be sent. The rule"
            + " refuses it on the first move, so the operator is offered a destination the railway"
            + " will turn down - which is Adam's case: a train staged ready to be parked, offered a"
            + " berth it does not fit");
    }

    /**
     * Measured track still refuses, even when there is unmeasured track further back.
     *
     * Adam, 2026-09-06, ruling on what an unmeasured run-in should do: **"Generally, allow it.  But:
     * the example I gave you from bottommainpost to tunnellongpark has measured segments before (that
     * prevent it), but none after (which would allow it had that prevention not been there).  Make
     * sure you are actually enforcing this."**
     *
     * **Two rules, and the second is the one that was missing.**  Generally: a run-in nobody has
     * measured is not judged, so the train is allowed - the railway does not refuse what it cannot
     * work out.  But an unmeasured segment must not WIPE OUT a refusal the measured ones have already
     * established.  The walk returned `null` the moment it met unmeasured track, discarding everything
     * it had already counted, and that is what let his case through.
     *
     * The doctrine is the same one he gave for the protrusion walk on the same day: *"only segments
     * with a positive length are determinate."*  Unmeasured track contributes no room.  It just does
     * not follow that it contributes no ANSWER.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testMeasuredTrackStillRefusesWhenTheRestIsUnmeasured() throws Exception
    {
        assertFalse(admittedWithUnmeasuredApproach(4, 2),
            "a train of 4 was let into 2 units of measured berth because the track FURTHER BACK is"
            + " unmeasured. The two units already prove it does not fit, and an unmeasured segment"
            + " behind them cannot unprove it - that is Adam's BottomMainPost case exactly");
    }

    /**
     * And it still admits what the measured part does allow.
     */
    @Test
    public void testMeasuredTrackStillAdmitsWhatFits() throws Exception
    {
        assertTrue(admittedWithUnmeasuredApproach(2, 2),
            "a train of 2 was refused 2 measured units because of unmeasured track behind them, so"
            + " this rule now refuses on absence of information rather than on evidence");
    }

    /**
     * A run-in with nothing measured at all is not judged - "generally, allow it".
     */
    @Test
    public void testAnEntirelyUnmeasuredRunInIsNotJudged() throws Exception
    {
        assertTrue(admittedWithUnmeasuredApproach(9, 0),
            "a long train was refused a berth nobody has measured. There is no evidence either way"
            + " here, and refusing on no evidence would make every unmeasured layout unusable -"
            + " Adam: \"generally, allow it\"");
    }

    /**
     * A station refuses a train longer than it accepts - which nothing had ever checked.
     *
     * Adam, 2026-09-07, annotating the room rule: **"the train should be refused any destination it
     * does not fit in, i.e. where the accepted length > train length."**
     *
     * **This rule already existed, and I nearly shipped a second copy of it.** `Point.validateTrainLength`
     * has enforced it all along, called from `isPathClear`; a grep for `getMaxTrainLength()` missed it
     * because it reads the field directly. A duplicate rule was written, tested, and only caught when
     * the mutation that should have reddened this test did not - the refusal was coming from the copy
     * that was already there. `DR-B3` in miniature, committed by the person who had just removed one.
     *
     * So this test now covers the EXISTING rule, which had no direct test of its own.
     *
     * A different question from the room rule beside it, as he also noted: that one measures the track
     * LEADING IN, this one is the station's own limit. A train can clear the approach and not the
     * platform, or the other way about, and either refuses.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAStationRefusesATrainLongerThanItAccepts() throws Exception
    {
        assertFalse(fitsTheStation(9, 3),
            "a nine-unit train was accepted by a station that takes three. maxTrainLength has been"
            + " editable and unenforced since it was added");

        assertTrue(fitsTheStation(3, 3),
            "a three-unit train was refused a station that takes three. Exactly-fits is admitted, as"
            + " it is for the room rule - otherwise every platform measured to its train is unusable");

        assertTrue(fitsTheStation(9, 0),
            "a station with no limit recorded refused a train. Zero is how every square starts and"
            + " what the editor writes when the field is cleared, so it is no limit rather than a"
            + " limit of zero - refusing on it would make every unmeasured platform unusable");
    }

    /**
     * A plain run into a station with a stated capacity.
     *
     * @param trainLength how long the train is
     * @param stationTakes the station maxTrainLength, 0 for unset
     * @return whether the path was allowed
     * @throws Exception on a failure to build
     */
    private boolean fitsTheStation(int trainLength, int stationTakes) throws Exception
    {
        Layout layout = new Layout(model);

        String tag = "_cap" + (addresses++);

        org.traincontrol.marklin.MarklinFeedback a = model.newFeedback(800 + addresses++, null);
        org.traincontrol.marklin.MarklinFeedback b = model.newFeedback(800 + addresses++, null);

        model.setFeedbackState(a.getName(), false);
        model.setFeedbackState(b.getName(), false);

        layout.createPoint("CAPSTART" + tag, true, a.getName());
        layout.createPoint("CAPEND" + tag, true, b.getName());

        Point start = layout.getPoint("CAPSTART" + tag);
        Point end = layout.getPoint("CAPEND" + tag);

        end.setMaxTrainLength(stationTakes);

        Edge run = layout.createEdge(start.getName(), end.getName());

        run.setLength(50);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        loc.setTrainLength(trainLength);

        start.setLocomotive(loc);

        List<Edge> path = new ArrayList<>();

        path.add(run);

        return layout.isPathClear(path, loc, false);
    }

    /**
     * A terminus that is not a station is still judged on TRACK length, just not on station capacity.
     *
     * Adam, 2026-09-07: **"the terminus that isn't a destination should fail on the track length
     * check - the station length can safely be ignored."**
     *
     * Two rules, two gates, and they are not the same gate.  `validateTrainLength` asks about the
     * STATION'S stated capacity and returns true for anything that is not a destination - there is no
     * capacity to exceed on a square nobody calls a station.  `measuredRoomToReverseInto` asks about
     * the TRACK leading in and gates on terminus-or-reversing alone, with no destination requirement.
     *
     * So a train can still be refused a dead end it does not physically fit into, whether or not that
     * dead end has ever been named a station.  Already true; pinned so it stays true, because the two
     * gates look similar enough that one would be "tidied" into the other.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testATerminusThatIsNotAStationIsStillJudgedOnTrackLength() throws Exception
    {
        Fixture tight = build(9, 2);

        // Not a station at all - so the capacity rule has nothing to say about it.
        tight.berth.setDestination(false);

        // THE STATE HE IS ASKING ABOUT CANNOT EXIST, which is a better answer than either rule.
        //
        // `setDestination(false)` clears the terminus flag - "reset terminus status" - so a terminus
        // that is not a station is not a thing the model can hold. And a non-destination is never the
        // END of a path anyway: getPossiblePaths requires isDestination of every candidate.
        //
        // So the track-length rule not firing there costs nothing, because nothing is ever sent there
        // to be judged. Worth pinning rather than answering, because the question is a reasonable one
        // and the reason it does not arise lives in a setter three files away.
        assertFalse(tight.berth.isTerminus(),
            "a square can now be a terminus while not being a station. That combination used to be"
            + " impossible - setDestination(false) cleared the terminus flag - and the track-length"
            + " rule gates on terminus-or-reversing, so it would now be judging squares nothing can"
            + " be sent to, or missing ones it should judge (Adam, 2026-09-07)");

        assertTrue(tight.berth.validateTrainLength(tight.loc),
            "the station capacity rule is judging a square that is not a station. There is no stated"
            + " capacity to exceed, and Adam: \"the station length can safely be ignored\"");
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
    /**
     * One built railway, so the rule and the menu can be asked the same question about it.
     */
    private static final class Fixture
    {
        private Layout layout;
        private Locomotive loc;
        private List<Edge> path;
        private Point berth;
    }

    /**
     * Whether the menu lists the berth as somewhere this train may go.
     *
     * @param fixture the railway
     * @return true when the berth is among the offered destinations
     */
    private boolean offersTheBerth(Fixture fixture)
    {
        List<List<Edge>> offered = fixture.layout.getPossiblePaths(fixture.loc, true);

        if (offered == null) return false;

        for (List<Edge> candidate : offered)
        {
            if (candidate.isEmpty()) continue;

            if (fixture.berth.equals(candidate.get(candidate.size() - 1).getEnd())) return true;
        }

        return false;
    }

    private boolean admitted(int trainLength, int roomAfterTheSwitch) throws Exception
    {
        Fixture fixture = build(trainLength, roomAfterTheSwitch);

        return fixture.layout.isPathClear(fixture.path, fixture.loc, false);
    }

    /**
     * A run-in whose last segment is measured and whose approach is NOT, with no switch between.
     *
     * @param trainLength how long the train is
     * @param measuredAtTheBerth the measured units immediately in front of the berth, 0 for none
     * @return whether isPathClear allowed it
     * @throws Exception on a failure to build
     */
    private boolean admittedWithUnmeasuredApproach(int trainLength, int measuredAtTheBerth)
        throws Exception
    {
        Layout layout = new Layout(model);

        String tag = "_u" + trainLength + "_" + measuredAtTheBerth;

        org.traincontrol.marklin.MarklinFeedback a = model.newFeedback(600 + addresses++, null);
        org.traincontrol.marklin.MarklinFeedback b = model.newFeedback(600 + addresses++, null);
        org.traincontrol.marklin.MarklinFeedback c = model.newFeedback(600 + addresses++, null);

        model.setFeedbackState(a.getName(), false);
        model.setFeedbackState(b.getName(), false);
        model.setFeedbackState(c.getName(), false);

        layout.createPoint("USTART" + tag, true, a.getName());
        layout.createPoint("UMID" + tag, false, b.getName());
        layout.createPoint("UBERTH" + tag, true, c.getName());

        Point start = layout.getPoint("USTART" + tag);
        Point mid = layout.getPoint("UMID" + tag);
        Point berth = layout.getPoint("UBERTH" + tag);

        berth.setTerminus(true);

        Edge approach = layout.createEdge(start.getName(), mid.getName());
        Edge run = layout.createEdge(mid.getName(), berth.getName());

        // UNMEASURED, which is the whole point of this fixture.
        approach.setLength(0);

        run.setLength(measuredAtTheBerth);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        loc.setTrainLength(trainLength);

        start.setLocomotive(loc);

        List<Edge> path = new ArrayList<>();

        path.add(approach);
        path.add(run);

        return layout.isPathClear(path, loc, false);
    }

    private static int addresses = 900;

    private Fixture build(int trainLength, int roomAfterTheSwitch) throws Exception
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

        Fixture fixture = new Fixture();

        fixture.layout = layout;
        fixture.loc = loc;
        fixture.berth = berth;
        fixture.path = new ArrayList<>();

        fixture.path.add(approach);
        fixture.path.add(run);

        return fixture;
    }
}
