package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train is refused a berth it does not fit in, whether or not it has to back into it.
 *
 * Adam, 2026-09-05, triaging MT-262: **"in the current setup, '75 407 DB' (length 4) is allowed to
 * manually be sent from bottommainpost to bottomlongpark, even though track segments between the
 * current position and there are 1+1 = 2 and there is no notice that can help state/debug this.
 * also, that path is not shown in the 'why not moving' view."**  His ruling on what to do about it
 * was to refuse the send.
 *
 * **What was wrong.**  The room rule existed and was fenced off from most of the railway.
 * `Layout.isPathClear` asked `measuredRoomToReverseInto` only where the path ended somewhere the
 * train would turn round - `ending.isTerminus() || ending.isReversing()` - so a plain through
 * platform was never judged on the track leading into it at all.  Measured on Adam's own railway on
 * 2026-09-08, with `75 407 DB` set to four units and standing at BottomMainPost: `BottomMainA
 * (eastbound)` was offered with one measured unit of room behind it, and `measuredRoomToReverseInto`
 * answered null because that platform is not a terminus.
 *
 * **Why the fence was wrong.**  A train in this model always comes to rest with its head at the
 * destination sensor, so its tail extends back over the run in whatever kind of station that is.
 * `edgesCoveredByStandingTrains` has always modelled it that way and is not fenced on terminus -
 * Adam's own example for it is a THROUGH platform: *"bottommainc should currently be blocked since a
 * train of length 4 is standing at bottommainb, which has a length of 1 leading up to its switch."*
 * So the railway already knew such a train lies across the switch behind it; nothing stopped one
 * being sent there to do it.
 *
 * **The second half is the explanation.**  `explainDestinations` asks `barredFromAutonomy` first and
 * returns its answer, so a berth marked "not an automatic destination" - which is what a parking
 * track is, and exactly the sort of place a person sends a train by hand - reported *"Set not to be
 * chosen automatically."* and nothing else.  The one class of destination the operator reaches for by
 * hand was the one class whose hand-driven refusal the window could never show.
 *
 * MUTATION: putting the `isTerminus() || isReversing()` fence back around the room rule in
 * `isPathClear` fails `testAThroughBerthTooShortIsRefused`; dropping the length arm from
 * `explainDestinations` fails `testTheWhyNotMovingViewSaysSoForAManualOnlyBerth`.
 */
public class testAManualSendIsRefusedABerthTooShort
{
    private static MarklinControlStation model;

    /**
     * A THROWAWAY COPY OF THE LAYOUT, opened before the model is built (OB-111).
     *
     * `MarklinControlStation.init` reads the layout preference and loads whatever it names, which on
     * Adam's machine is his real railway.  This class builds its own graphs and has no business
     * reading that one.
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUp() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDown() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The defect: a plain platform with two units of room admitted a four-unit train.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAThroughBerthTooShortIsRefused() throws Exception
    {
        Fixture tight = build(4, 2, false);

        assertFalse(tight.layout.isPathClear(tight.path, tight.loc, false),
            "a four-unit train was admitted to a platform with two units of track behind it, because"
            + " that platform is not a terminus. It comes to rest lying two units across the switch"
            + " behind it, which is the state Adam's own covered-track rule already refuses to route"
            + " anything else over");
    }

    /**
     * The control: the same platform, a train that fits, is admitted.
     *
     * Without it the refusal above passes on a rule that refuses everything, which is the failure
     * mode this file's older sibling was written to avoid.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAThroughBerthWithRoomIsAdmitted() throws Exception
    {
        Fixture roomy = build(2, 2, false);

        assertTrue(roomy.layout.isPathClear(roomy.path, roomy.loc, false),
            "a two-unit train was refused two units of room at a through platform. Exactly-fits is"
            + " admitted - otherwise every platform measured to the train that lives in it becomes"
            + " unusable - and if this refuses then the test above says nothing about lengths");
    }

    /**
     * And a terminus still refuses, which is the behaviour that already existed.
     *
     * The widening must not be a REPLACEMENT for the reversal rule: a fix that moved the refusal from
     * one kind of berth to the other would leave this file green and the railway no better off.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATerminusTooShortIsStillRefused() throws Exception
    {
        Fixture tight = build(4, 2, true);

        assertFalse(tight.layout.isPathClear(tight.path, tight.loc, false),
            "a terminus with two units of room admitted a four-unit train, so the reversal rule that"
            + " has existed since 2026-09-02 has been lost");
    }

    /**
     * The refusal names the berth, the room found and the train's length.
     *
     * Adam: *"there is no notice that can help state/debug this."*  A refusal that says only "too
     * long" leaves the operator with nothing to measure: the two numbers are what tell him whether to
     * shorten the train, lengthen the berth, or go and look at what the graph thinks it measured.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheRefusalNamesTheBerthAndBothNumbers() throws Exception
    {
        Fixture tight = build(4, 2, false);

        assertFalse(tight.layout.isPathClear(tight.path, tight.loc, false),
            "the path was not refused at all, so there is no message to read");

        String said = Layout.getLastError();

        assertNotNull(said, "nothing was recorded about why the path was refused");

        assertTrue(said.contains(tight.berth.getName()),
            "the refusal does not name the berth: " + said);

        // ASSERTED AGAINST THE RENDERED MESSAGE, not against the digits in it.
        //
        // `contains("2")` would be satisfied by the locomotive's own name - this fixture takes
        // whatever locomotive the database hands it, and Adam's are called things like "75 407 DB".
        // Rendering the message with the two numbers in their places says the same thing and cannot
        // be passed by an accident of naming.
        assertEquals(said,
            org.traincontrol.util.I18n.f("autolayout.errorTrainTooLongForBerth",
                tight.loc.getName(), tight.berth.getName(), 2, 4),
            "the refusal is not the two-number message: " + said);

        // AND THE CONTROL FOR THAT: the room and the length are not interchangeable. Passed the other
        // way round the message reads differently, so the assertion above is pinning which number is
        // which rather than merely that both appear.
        assertNotEquals(said,
            org.traincontrol.util.I18n.f("autolayout.errorTrainTooLongForBerth",
                tight.loc.getName(), tight.berth.getName(), 4, 2),
            "the room found and the train's length render identically, so nothing above says which"
            + " number is which");
    }

    /**
     * The manual doors do not offer it, which is where Adam met this.
     *
     * The right-click menu and the locomotive panel both build their lists from `getPossiblePaths`,
     * which vets every candidate through `isPathClear` - so this is what "manually selectable" means
     * in his report, and it is worth asking directly rather than trusting that the two agree.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheManualDoorsDoNotOfferIt() throws Exception
    {
        Fixture roomy = build(2, 2, false);

        assertTrue(offersTheBerth(roomy),
            "a train that fits is not offered the berth at all, so the refusal below means only that"
            + " this door offers nothing to anybody");

        Fixture tight = build(4, 2, false);

        assertFalse(offersTheBerth(tight),
            "a four-unit train is still listed as able to go to a platform with two units of room."
            + " That is Adam's sentence exactly: it \"is allowed to manually be sent\" there");
    }

    /**
     * The why-not-moving view says so, even for a berth autonomy would never choose.
     *
     * Adam: *"that path is not shown in the 'why not moving' view."*  A parking berth is spelled
     * `autoDestination: false`, and for those `explainDestinations` reported the standing bar and
     * stopped - so the destinations a person actually sends trains to by hand were the ones it could
     * not explain a hand-driven refusal for.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheWhyNotMovingViewSaysSoForAManualOnlyBerth() throws Exception
    {
        Fixture tight = build(4, 2, false);

        tight.berth.setAutoDestination(false);

        String reason = tight.layout.explainDestinations(tight.loc).get(tight.berth.getName());

        assertNotNull(reason,
            "the window reports nothing at all about a berth this train cannot reach, so it reads as"
            + " somewhere the train could go");

        assertEquals(reason,
            org.traincontrol.util.I18n.f("autolayout.errorTrainTooLongForBerth",
                tight.loc.getName(), tight.berth.getName(), 2, 4),
            "the window's reason for a manual-only berth a four-unit train does not fit in is \""
            + reason + "\". That is a statement about autonomy, and the operator is asking why his"
            + " own send will not work");
    }

    /**
     * The control for the window: a manual-only berth the train DOES fit in still reads as barred.
     *
     * The length arm must not swallow the standing bar. A parking berth a train fits in is still
     * somewhere autonomy will not choose, and that is what the window has always said about it.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAManualOnlyBerthThatFitsStillReadsAsBarred() throws Exception
    {
        Fixture roomy = build(2, 2, false);

        roomy.berth.setAutoDestination(false);

        String reason = roomy.layout.explainDestinations(roomy.loc).get(roomy.berth.getName());

        assertEquals(reason, org.traincontrol.util.I18n.t("autolayout.why.notAutoDestination"),
            "a berth marked as not an automatic destination, which this train fits in, is reported as"
            + " \"" + reason + "\". The standing bar is what the window has always said about it, and"
            + " the length arm must not swallow it");
    }

    /**
     * An unmeasured run in is still not judged, which is Adam's "generally, allow it".
     *
     * The widening changes WHERE the rule applies, not what it does about missing measurements. A
     * railway nobody has measured must stay usable, and refusing on absence of information would make
     * every through platform on such a layout unreachable - which is most of every layout.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAnUnmeasuredThroughBerthIsNotJudged() throws Exception
    {
        Fixture unknown = build(9, 0, false);

        assertTrue(unknown.layout.isPathClear(unknown.path, unknown.loc, false),
            "a nine-unit train was refused a through platform whose run in nobody has measured. There"
            + " is no evidence either way, and refusing on none of it makes every unmeasured layout"
            + " unusable - Adam: \"generally, allow it\"");
    }

    // ---------------------------------------------------------------- the fixture

    /** One built railway, so the rule, the door and the window can be asked about the same one. */
    private static final class Fixture
    {
        private Layout layout;
        private Locomotive loc;
        private List<Edge> path;
        private Point berth;
    }

    /**
     * Whether the manual doors would list the berth as somewhere this train may go.
     *
     * @param fixture the railway
     * @return true when the berth is among the offered destinations
     */
    private boolean offersTheBerth(Fixture fixture)
    {
        for (List<Edge> candidate : fixture.layout.getPossiblePaths(fixture.loc, true))
        {
            if (candidate.isEmpty()) continue;

            if (fixture.berth.equals(candidate.get(candidate.size() - 1).getEnd())) return true;
        }

        return false;
    }

    private static int addresses = 1200;

    /**
     * A run into a berth with a switch part way along, and a measured stretch after it.
     *
     * The shape is `testATrainTooLongIsRefusedTheBerth`'s, with one thing added: whether the berth is
     * a terminus. That flag is the whole subject here - the rule used to be fenced behind it - so it
     * is a parameter rather than a fixed part of the fixture.
     *
     * @param trainLength how long the train is
     * @param roomAfterTheSwitch the measured track between the switch and the berth, 0 for unmeasured
     * @param terminus whether the berth is the end of the line
     * @return the built railway
     * @throws Exception on a failure to build
     */
    private Fixture build(int trainLength, int roomAfterTheSwitch, boolean terminus) throws Exception
    {
        Layout layout = new Layout(model);

        String suffix = "_" + (addresses++);

        org.traincontrol.marklin.MarklinFeedback one = model.newFeedback(addresses++, null);
        org.traincontrol.marklin.MarklinFeedback two = model.newFeedback(addresses++, null);
        org.traincontrol.marklin.MarklinFeedback three = model.newFeedback(addresses++, null);

        model.setFeedbackState(one.getName(), false);
        model.setFeedbackState(two.getName(), false);
        model.setFeedbackState(three.getName(), false);

        layout.createPoint("START" + suffix, true, one.getName());
        layout.createPoint("MIDDLE" + suffix, false, two.getName());
        layout.createPoint("BERTH" + suffix, true, three.getName());

        Point start = layout.getPoint("START" + suffix);
        Point middle = layout.getPoint("MIDDLE" + suffix);
        Point berth = layout.getPoint("BERTH" + suffix);

        berth.setTerminus(terminus);

        // A long approach BEFORE the switch, which must not count: the binding measurement is the
        // stretch after the last switch, and a rule that adds this in admits everything.
        Edge approach = layout.createEdge(start.getName(), middle.getName());
        Edge run = layout.createEdge(middle.getName(), berth.getName());

        approach.setLength(50);
        run.setLength(roomAfterTheSwitch);

        // THE SWITCH, as the graph expresses it. `crossesASwitch` is `roomAtTheEnd != MIN_VALUE`, and
        // roomAtTheEnd is the stretch measured AFTER the switch - so this says both "there is a switch
        // here" and "this much track lies beyond it".
        //
        // NOT SET AT ALL for the unmeasured case, which is a different thing from a measured zero.
        // `roomAtTheEnd == 0` means somebody measured no room after the switch and the rule refuses
        // on it; MIN_VALUE means there is no switch here to measure from, and with the run itself
        // left at length 0 the walk has nothing to count and declines to judge.
        if (roomAfterTheSwitch > 0) run.setRoomAtTheEnd(roomAfterTheSwitch);

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
