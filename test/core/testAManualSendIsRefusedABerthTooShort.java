package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
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
 * Adam, triaging MT-262: "in the current setup, '75 407 DB' (length 4) is allowed to manually be sent from
 * bottommainpost to bottomlongpark, even though track segments between the current position and there are 1+1 = 2 and
 * there is no notice that can help state/debug this.  also, that path is not shown in the 'why not moving' view."  His
 * ruling was to refuse the send.
 *
 * The room rule was fenced off from most of the railway: `Layout.isPathClear` asked `measuredRoomToReverseInto` only
 * where the path ended somewhere the train would turn round (`ending.isTerminus() || ending.isReversing()`), so a plain
 * through platform was never judged on the track leading into it.  But a train always comes to rest with its head at
 * the destination sensor, so its tail extends back over the run in any kind of station; `edgesCoveredByStandingTrains`
 * models it that way and is not fenced on terminus (Adam's example is a THROUGH platform: "bottommainc should currently
 * be blocked since a train of length 4 is standing at bottommainb, which has a length of 1 leading up to its switch.").
 *
 * The second half is the explanation: `explainDestinations` asks `barredFromAutonomy` first and returns its answer, so
 * a berth marked "not an automatic destination" - a parking track, the place a person sends a train by hand - reported
 * "Set not to be chosen automatically." and nothing else.
 *
 * MUTATION: putting the `isTerminus() || isReversing()` fence back around the room rule in
 * `isPathClear` fails `testAThroughBerthTooShortIsRefused`; dropping the length arm from
 * `explainDestinations` fails `testTheWhyNotMovingViewSaysSoForAManualOnlyBerth`.
 */
public class testAManualSendIsRefusedABerthTooShort
{
    private static MarklinControlStation model;

    /**
     * A THROWAWAY COPY OF THE LAYOUT, opened before the model is built (OB-111): `MarklinControlStation.init` loads the
     * layout preference, which on Adam's machine is his real railway.
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUp() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDown() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /** The defect: a plain platform with two units of room admitted a four-unit train. */
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
     * The control: the same platform, a train that fits, is admitted; without it the refusal above passes on a rule
     * that refuses everything.
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

    /** And a terminus still refuses: the widening must not be a REPLACEMENT for the reversal rule. */
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
     * Adam: "there is no notice that can help state/debug this."  The two numbers tell the operator whether to shorten
     * the train, lengthen the berth, or look at what the graph thinks it measured.
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

        // ASSERTED AGAINST THE RENDERED MESSAGE, not the digits in it: `contains("2")` would be satisfied by the
        // locomotive's own name (Adam's are called things like "75 407 DB").
        assertEquals(said,
            org.traincontrol.util.I18n.f("autolayout.errorTrainTooLongForBerth",
                tight.loc.getName(), tight.berth.getName(), 2, 4),
            "the refusal is not the two-number message: " + said);

        // AND THE CONTROL: the room and the length are not interchangeable, so the assertion above pins which is which.
        assertNotEquals(said,
            org.traincontrol.util.I18n.f("autolayout.errorTrainTooLongForBerth",
                tight.loc.getName(), tight.berth.getName(), 4, 2),
            "the room found and the train's length render identically, so nothing above says which"
            + " number is which");
    }

    /**
     * The manual doors do not offer it, which is where Adam met this: the right-click menu and the locomotive panel
     * build their lists from `getPossiblePaths`, which vets every candidate through `isPathClear`.  Asked directly
     * rather than trusting that the two agree.
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
     * Adam: "that path is not shown in the 'why not moving' view."  A parking berth is spelled
     * `autoDestination: false`, and for those `explainDestinations` reported the standing bar and stopped.
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
     * The control for the window: a manual-only berth the train DOES fit in still reads as barred, so the length arm
     * must not swallow the standing bar.
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
     * An unmeasured run in is still not judged, which is Adam's "generally, allow it".  The widening changes WHERE the
     * rule applies, not what it does about missing measurements: refusing on absence of information would make every
     * through platform on an unmeasured layout unreachable.
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

    /**
     * The window's reason for a manual-only berth is the BERTH rule as well as the length rule.
     *
     * MT-262's ruling is that a physical refusal outranks a preference, and there are two: the train does not fit down
     * the route, and the train fits but its body would lie across a road something else uses.  `isPathClear` asks both,
     * and so does the staging planner; this branch asked only the first, so a berth the railway refuses on the second
     * was reported as "Set not to be chosen automatically.".
     *
     * The fixture separates the two: fifty units of room after the switch, so the length rule admits the train, and a
     * two-unit train whose body claims the neck of the berth, metal a second road shares.  The assertion is agreement
     * with `whyABerthCannotHoldIt` itself rather than a copy of its sentence, since a second spelling of a rule is this
     * project's most repeated defect.
     *
     * MUTATION: drop the berth arm from `whyNoRouteFitsTo` and the reason falls back to the standing bar.
     */
    @Test
    public void testTheWhyNotMovingViewNamesTheBerthRuleTooForAManualOnlyBerth() throws Exception
    {
        Fixture fouling = build(2, 50, false);

        fouling.berth.setAutoDestination(false);

        Edge run = fouling.path.get(fouling.path.size() - 1);

        // The berth and the neck behind it, in path order - the square arrived at is last, and its own measurement is
        // an allowance rather than rail to spend, so the two-unit train claims the neck as well.
        run.setPlaces(Arrays.asList("AMR_neck", "AMR_berth"), Arrays.asList(2, 1));

        // A SECOND ROAD OVER THE NECK.  It touches neither end of the berth's own square (a road in or out of the berth
        // is blocked by the train being there, so refusing on those would make every berth refuse itself).  The lock is
        // mutual, which is how the model tells shared metal from a one-directional FR-001 restriction.
        org.traincontrol.marklin.MarklinFeedback four = model.newFeedback(9720, null);
        org.traincontrol.marklin.MarklinFeedback five = model.newFeedback(9721, null);

        model.setFeedbackState(four.getName(), false);
        model.setFeedbackState(five.getName(), false);

        fouling.layout.createPoint("AMR_OTHER_A", false, four.getName());
        fouling.layout.createPoint("AMR_OTHER_B", false, five.getName());

        Edge otherRoad = fouling.layout.createEdge("AMR_OTHER_A", "AMR_OTHER_B");

        otherRoad.setLength(1);
        otherRoad.setPlaces(Arrays.asList("AMR_neck"), Arrays.asList(1));

        run.addLockEdge(otherRoad);
        otherRoad.addLockEdge(run);

        // THE PRECONDITIONS, so that neither half of this can pass for the other's reason.
        String foulsARoad = Layout.whyABerthCannotHoldIt(fouling.path, fouling.loc);

        assertNotNull(foulsARoad,
            "precondition: the berth rule does not refuse this berth at all, so the window has nothing to report and"
            + " this fixture shows nothing");

        assertNull(Layout.whyTooLongForThisRoute(fouling.path, fouling.loc),
            "precondition: the length rule refuses this route as well, so the window could report the right sentence"
            + " through the arm that was already there");

        String reason = fouling.layout.explainDestinations(fouling.loc).get(fouling.berth.getName());

        assertEquals(reason, foulsARoad,
            "the window's reason for a berth whose neck this train would lie across is \"" + reason + "\".  The"
            + " railway refuses the send on the berth rule and the operator is told about autonomy's preferences"
            + " instead (AMR-C2)");

        // AND THE BAR SURVIVES: a berth nothing physical refuses is still somewhere autonomy will not choose.
        Fixture plain = build(2, 50, false);

        plain.berth.setAutoDestination(false);

        assertEquals(plain.layout.explainDestinations(plain.loc).get(plain.berth.getName()),
            org.traincontrol.util.I18n.t("autolayout.why.notAutoDestination"),
            "a manual-only berth that nothing physical refuses no longer reads as barred, so the berth arm has"
            + " swallowed the standing bar");
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

    /** Whether the manual doors would list the berth as somewhere this train may go. */
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
     * A run into a berth with a switch part way along, and a measured stretch after it, as
     * `testATrainTooLongIsRefusedTheBerth` builds one.  Whether the berth is a terminus is a parameter, since the rule
     * used to be fenced behind it; `roomAfterTheSwitch` is the measured track between the switch and the berth, 0 for
     * unmeasured.
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

        // A PARKING BERTH (Can Be Chosen In Full Autonomy off), as MT-262's square was.  Since FR-087 a station
        // autonomy MAY choose takes a train the measured route in holds, so at one this 50-unit approach admits the
        // four-unit train; the room rule here is the berth's.  `regression.testAPassingTrainMayStandAcrossThePoints`
        // holds the station half.
        berth.setAutoDestination(false);

        // A long approach BEFORE the switch, which must not count at a berth: the binding measurement is the
        // stretch after the last switch, and a rule that adds this in admits everything.
        Edge approach = layout.createEdge(start.getName(), middle.getName());
        Edge run = layout.createEdge(middle.getName(), berth.getName());

        approach.setLength(50);
        run.setLength(roomAfterTheSwitch);

        // THE SWITCH, as the graph expresses it: `crossesASwitch` is `roomAtTheEnd != MIN_VALUE`, and roomAtTheEnd is
        // the stretch measured AFTER the switch.  NOT SET AT ALL for the unmeasured case: `roomAtTheEnd == 0` means
        // somebody measured no room and the rule refuses, while MIN_VALUE means no switch to measure from, and with the
        // run left at length 0 the walk declines to judge.
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
