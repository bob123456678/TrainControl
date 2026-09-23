package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A train may overhang the points at a station, and may not at a parking berth (Adam, 2026-09-12).
 *
 * *"it would not fit just past 14,12. 14,12 would be blocked and the 6 units would be between
 * bottommainapre and bottommaina. so we need a clear rule to govern that this is OK, or simply make a
 * rule that parking berths cant block any other edges, but not make that check for active stations."*
 *
 * **The two halves, and why the pair of them is the rule rather than either one.**
 *
 * At a station autonomy may choose, a train is PASSING: it fits if it fits past the last switch, as
 * before, **or** within its own approach. The second is the relaxation - on his example the approach
 * measured 6 with 3 of it past the switch, so a six-unit train stood across the points, and he ruled
 * that acceptable because the train moves on.
 *
 * At a parking berth, a train is STAYING, and a berth is not worth a road: it must foul no track that
 * is not a way in or out of that square. A train too long for TunnelLongPark lies back over
 * `BottomMainAPre -> RampDown` and `-> BottomCrossover`, which are the only roads to the lower level,
 * and that is what the berth half refuses.
 *
 * **The failure this class exists to catch is the relaxation leaking.** Both halves are about a train
 * standing on points, and a rule that stopped asking WHICH KIND OF SQUARE it is standing at would admit
 * the berth case along with the platform one. So the berth claims are not decoration; they are the
 * mutation target.
 *
 * **ON HIS MEASURED RAILWAY (refrozen 2026-09-23 from `e36df979`), not on figures set here.** Until
 * then the snapshot measured almost nothing, so this class wrote Adam's hypothetical into it - *"i didnt
 * update any layout files with this info, but this should be the example motivating the design"* - 3
 * either side of the switch at 14,12.  His own measurements do not have that shape there (the eastbound
 * approach to BottomMainA measures 5 with 4 past the switch, and BottomMainA holds 5, so no train the
 * station takes stands across those points).  They have it at Tunnel: the approach from TunnelPre
 * measures 4 with 2 past the switch, so a three- or four-unit train stands across the points of a
 * platform that holds five.  That is the platform this class asks about now, and the berth is still
 * TunnelLongPark, which measures 3 past its switch on an approach of 7.
 *
 * **One setting of his is taken off, and it is the berth's capacity.**  He gave every berth a maximum
 * equal to the room past its switch, so on his railway the capacity rule refuses an overhanging train at
 * a berth before the berth rule is asked - and a claim that the relaxation has not leaked would pass
 * with the leak in place.  The capacity rule is `Point.validateTrainLength`, asked first in
 * `whyTooLongForThisRoute`, and it is tested where it is the subject.
 *
 * MUTATION: drop the `isAutoDestination` test so the relaxation applies everywhere, and the berth claim
 * at one unit past the room fails. Remove the relaxation and the platform claim at the approach's length
 * fails. Make the relaxation unbounded - any length at a station - and the claim one unit past the
 * approach fails. Make the berth check always answer null and the direct claim at the bottom fails.
 *
 * @author Adam
 */
public class testABerthAndAPlatformJudgeAnOverhangDifferently
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;
    private static Locomotive train;
    private static Integer lengthWas;

    /** TunnelLongPark, whose capacity is taken off so the berth rule is what answers */
    private static final TileKey THE_BERTH = new TileKey("1 - Main", 10, 9);

    /** The approach to a station autonomy may choose, and the approach to a parking berth */
    private static Edge toThePlatform;
    private static Edge toTheBerth;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        // HIS MEASUREMENTS, AND NOT THE BERTH'S CAPACITY.  He set it equal to the room past the switch,
        // so it refuses every overhang there first and the berth rule is never reached - see the class
        // comment.  Nothing else is changed.
        assertEquals(session.getStationIndex().nameOf(THE_BERTH), "TunnelLongPark",
            "the square this class clears the capacity of is no longer TunnelLongPark");

        session.setPointProperty(THE_BERTH, "maxTrainLength", null);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        // NAMED, not searched for.  Several runs reach TunnelLongPark and BottomMainA, and only one of
        // each is the piece of railway this class is about - picking "the longest" once got an
        // unmeasured one and the class then asserted things about a different approach.
        toThePlatform = approachFrom("TunnelPre", "Tunnel (southbound)");
        toTheBerth = approachFrom("BottomMainA (westbound)", "TunnelLongPark");

        List<String> names = model.getLocList();

        if (names.isEmpty()) throw new SkipException("this fixture has no locomotives");

        train = model.getLocByName(names.get(0));

        lengthWas = train.getTrainLength();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (train != null) train.setTrainLength(lengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The geometry this class rests on, stated rather than hoped for.
     *
     * Every claim below is about a particular shape of railway - a platform approach measuring 4 with 2
     * of it past the points, at a platform that holds more than the approach, and a berth with 3 past
     * its switch on a longer approach - and if the snapshot stops producing that shape, the claims
     * would pass or fail for reasons that have nothing to do with the rule.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheFixtureIsTheShapeTheRuleIsAbout() throws Exception
    {
        assertTrue(toThePlatform.getEnd().isDestination() && toThePlatform.getEnd().isAutoDestination(),
            toThePlatform.getEnd().getName() + " is not a station autonomy may choose, so the"
            + " relaxation this class is about does not apply to it");

        assertEquals(toThePlatform.getLength(), 4,
            "the approach to " + toThePlatform.getEnd().getName() + " measures "
            + toThePlatform.getLength() + " and Adam's measurement of 2026-09-23 is 4");

        assertEquals(toThePlatform.getRoomAtTheEnd(), 2,
            "the room past the last switch on that approach is " + toThePlatform.getRoomAtTheEnd()
            + " and his measurement is 2 - a train longer than that has to stand across the points for"
            + " any of this to be the case he described");

        // AND THE PLATFORM HOLDS MORE THAN ITS APPROACH, so the claim one unit past the approach is
        // answered by the route bound rather than by the capacity he typed on the platform.
        assertTrue(toThePlatform.getEnd().getMaxTrainLength() == null
            || toThePlatform.getEnd().getMaxTrainLength() == 0
            || toThePlatform.getEnd().getMaxTrainLength() > toThePlatform.getLength(),
            toThePlatform.getEnd().getName() + " holds " + toThePlatform.getEnd().getMaxTrainLength()
            + ", no more than its approach, so the capacity rule refuses the longer train before the"
            + " relaxation's bound is asked");

        assertTrue(toTheBerth.getEnd().isDestination(),
            toTheBerth.getEnd().getName() + " is not a destination, so no train stops there and the berth half"
            + " is about nothing");

        assertFalse(toTheBerth.getEnd().isAutoDestination(),
            toTheBerth.getEnd().getName() + " is a station autonomy may choose, so it is not the"
            + " parking berth half of the rule and the controls below prove nothing");

        assertEquals(toTheBerth.getRoomAtTheEnd(), 3,
            "the room past the last switch into " + toTheBerth.getEnd().getName() + " is "
            + toTheBerth.getRoomAtTheEnd() + ", not the 3 his measurements give");

        assertTrue(toTheBerth.getLength() > toTheBerth.getRoomAtTheEnd() + 1,
            "the approach to " + toTheBerth.getEnd().getName() + " measures " + toTheBerth.getLength()
            + ", so a train one unit longer than the berth's room would not fit it either and the"
            + " relaxation, if it leaked, would still refuse it");

        assertTrue(toTheBerth.getEnd().validateTrainLength(trainOf(toTheBerth.getLength())),
            "the berth's capacity is still in place, so it refuses the overhang before the berth rule"
            + " is asked and every berth claim below passes with the relaxation leaked");
    }

    /**
     * The class's train at the given length, for asking the capacity rule on its own.
     */
    private static Locomotive trainOf(int units)
    {
        train.setTrainLength(units);

        return train;
    }

    /**
     * The claim: at a station autonomy may choose, a train that fits within its approach is allowed.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAPlatformTakesATrainAsLongAsItsWholeApproach() throws Exception
    {
        train.setTrainLength(toThePlatform.getLength());

        assertNull(Layout.whyTooLongForThisRoute(justTheApproach(toThePlatform), train),
            "a " + toThePlatform.getLength() + "-unit train was refused " + toThePlatform.getEnd().getName()
            + ", which has " + toThePlatform.getRoomAtTheEnd() + " units past its switch and whose approach"
            + " measures " + toThePlatform.getLength() + ". Adam, 2026-09-12: \"the 6 units would be"
            + " between bottommainapre and bottommaina... we need a clear rule to govern that this is"
            + " OK\". Refused with: "
            + Layout.whyTooLongForThisRoute(justTheApproach(toThePlatform), train));
    }

    /**
     * And it is bounded: a train longer than the measured route in - here the approach alone - is still refused.
     *
     * Without this the relaxation is "no check at all at a station", which would let a fifty-unit train
     * stop at a platform and lie back over track nothing has measured.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAPlatformStillRefusesATrainLongerThanItsApproach() throws Exception
    {
        train.setTrainLength(toThePlatform.getLength() + 1);

        assertNotNull(Layout.whyTooLongForThisRoute(justTheApproach(toThePlatform), train),
            "a train one unit longer than the measured route in (here the whole approach) was accepted at "
            + toThePlatform.getEnd().getName() + ", so the relaxation is unbounded and a train of any"
            + " length may stop at a station");
    }

    /**
     * The berth half, and the mutation target: a parking berth does NOT get the relaxation.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAParkingBerthStillRefusesAnOverhang() throws Exception
    {
        train.setTrainLength(toTheBerth.getRoomAtTheEnd() + 1);

        assertNotNull(Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train),
            "a " + train.getTrainLength() + "-unit train was accepted at " + toTheBerth.getEnd().getName()
            + ", whose approach measures " + toTheBerth.getLength() + " and which has "
            + toTheBerth.getRoomAtTheEnd() + " units past its last switch. Adam: \"make a rule that"
            + " parking berths cant block any other edges, but not make that check for active"
            + " stations\" - the relaxation has leaked from the platforms to the berths");
    }

    /**
     * And the berth is not simply refusing everything.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAParkingBerthStillTakesATrainThatFits() throws Exception
    {
        train.setTrainLength(toTheBerth.getRoomAtTheEnd());

        assertNull(Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train),
            "a " + train.getTrainLength() + "-unit train was refused " + toTheBerth.getEnd().getName() + ", which has "
            + toTheBerth.getRoomAtTheEnd() + " units past its last switch, so the claim above is"
            + " satisfied by a berth that takes nothing at all. Refused with: "
            + Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train));
    }

    /**
     * The berth rule asked on its own, so it is not the room rule answering for it.
     *
     * On this railway the two agree at TunnelLongPark - a train that fits past the switch also fouls
     * nothing - so the claim above would pass with the berth check removed entirely. This asks the new
     * rule directly: one unit longer than the berth, the train lies back over somebody else's road, and
     * at the berth's own room it lies over nothing at all.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheBerthRuleNamesTheRoadItWouldFoul() throws Exception
    {
        int room = toTheBerth.getRoomAtTheEnd();

        train.setTrainLength(room + 1);

        String fouled = Layout.whyABerthCannotHoldIt(justTheApproach(toTheBerth), train);

        assertNotNull(fouled,
            "a " + (room + 1) + "-unit train at " + toTheBerth.getEnd().getName() + " lies back past the"
            + " switch and the berth rule said nothing about it, so what refuses it above is the room"
            + " rule alone and this half of Adam's ruling is not being enforced");

        train.setTrainLength(room);

        assertNull(Layout.whyABerthCannotHoldIt(justTheApproach(toTheBerth), train),
            "a " + room + "-unit train at " + toTheBerth.getEnd().getName() + " fits inside the berth and the"
            + " berth rule refused it anyway, which would make every berth on the layout unusable:"
            + " " + Layout.whyABerthCannotHoldIt(justTheApproach(toTheBerth), train));
    }

    /**
     * A berth whose approach nobody has measured refuses nothing.
     *
     * **The rule has to be able to say "I do not know"** (PRW-B1).  The claim walk spends the train's
     * length backwards over the approach's places and stops when it runs out; where every place
     * measures zero it never runs out, so it claims the WHOLE approach and refuses the berth against
     * any road sharing any part of it.  That refusal is invented: nothing has been measured, so
     * nothing is known about whether the train would foul anything.
     *
     * It is the room rule's own answer at the other end of the same question - `walkStandingTrains`
     * claims nothing on an unmeasured segment and declines to judge - and Adam's, twice: *"a stretch
     * is only indeterminate when ALL of it is zero"* (MT-364), and the standing preference this
     * codebase records as `guards-need-a-way-past`, that he would rather have no check than an
     * over-strict one.
     *
     * **Measured on the frozen snapshot before this was written: all 41 non-station approaches are
     * wholly unmeasured, and 26 of them refuse a three-unit train.**  His railway has three measured
     * tiles on it, so in practice every train with a length was refused every parking berth, by hand
     * and through Return Home.
     *
     * Partial measurement is still judged, which is the other half of the MT-364 ruling and is what
     * the claim below it asserts: one measured place is enough to know something, and the walk uses
     * what it has.
     *
     * @throws Exception from the railway
     */
    /**
     * One measured square behind an unmeasured piece closes the berth, and the refusal now says why
     * (RTX-C2, AUR-C2).
     *
     * **This is the ordinary intermediate state of Mass Assign Lengths.**  MAL-B1's ruling gives every switch
     * on a page one length in a step of its own, and MT-455 tells the operator to reach that prompt by
     * skipping a piece - so switches measured with pieces still at zero is not an odd state, it is the state
     * the tool leaves between sittings.  A berth behind such a piece then has an approach where `anyMeasured`
     * is true on the switch alone: the walk claims every place, spends nothing on the unmeasured ones, and
     * refuses the berth to a one-unit train as readily as to a nine-unit one.
     *
     * Ruled behaviour on both sides and the refusing direction, so this pins it rather than changing it -
     * Adam, on the half-measured case: *"we want clear warnings to the user if so, then it's fine."*  What
     * was missing is that the refusal named a road and a train length and never the unmeasured piece, so the
     * operator had nothing pointing at what to measure.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAMeasuredSquareBehindAnUnmeasuredPieceSaysWhyItRefuses() throws Exception
    {
        Edge approach = toTheBerth;

        List<Integer> was = new ArrayList<>(approach.getPlaceLengths());

        assertTrue(was.size() >= 3,
            "precondition: this approach has fewer than three places, so there is no room for a measured"
            + " square with an unmeasured piece behind it");

        try
        {
            // ONE SQUARE MEASURED, the rest at zero - switches measured, pieces skipped.
            List<Integer> spans = new ArrayList<>();

            for (int i = 0; i < was.size(); i++) spans.add(i == 0 ? 1 : 0);

            approach.setPlaces(approach.getPlaceIds(), spans);

            train.setTrainLength(1);

            String refused = Layout.whyABerthCannotHoldIt(justTheApproach(approach), train);

            assertNotNull(refused,
                "one square of the approach is measured and the rest are not, and the berth was offered to"
                + " a one-unit train.  The walk claims a place before it spends on it and an unmeasured"
                + " place spends nothing, so the whole approach is claimed - this is the refusing side of"
                + " the rounding, and it is what the operator meets between sittings of Mass Assign"
                + " Lengths");

            assertTrue(refused.contains(org.traincontrol.util.I18n.f("autolayout.errorBerthApproachPartlyUnmeasured",
                was.size() - 2)) || refused.contains(org.traincontrol.util.I18n.f("autolayout.errorBerthApproachPartlyUnmeasured",
                was.size() - 1)),
                "the refusal names the road and the train's length and says nothing about the unmeasured"
                + " piece that caused it, so there is nothing to point the operator at what to measure."
                + "  Said: " + refused);

            // AND MEASURING THE PIECE CLEARS IT, which is the other half and what the sentence promises.
            List<Integer> measured = new ArrayList<>();

            for (int i = 0; i < was.size(); i++) measured.add(3);

            approach.setPlaces(approach.getPlaceIds(), measured);

            assertNull(Layout.whyABerthCannotHoldIt(justTheApproach(approach), train),
                "with every square of the approach measured at three, a one-unit train is still refused the"
                + " berth - so the refusal above was not about the unmeasured piece after all, and the"
                + " sentence this adds would be pointing the operator at the wrong thing.  Said: "
                + Layout.whyABerthCannotHoldIt(justTheApproach(approach), train));
        }
        finally
        {
            approach.setPlaces(approach.getPlaceIds(), was);
            train.setTrainLength(0);
        }
    }

    @Test
    public void testAnUnmeasuredApproachRefusesNothing() throws Exception
    {
        Edge approach = toTheBerth;

        List<Integer> spans = approach.getPlaceLengths();

        assertFalse(spans.isEmpty(),
            "precondition: this approach carries no places at all, so it is not the case PRW-B1 is"
            + " about - the walk has nothing to over-claim");

        List<Integer> was = new ArrayList<>(spans);

        try
        {
            // NOBODY HAS MEASURED THIS APPROACH, which is the state Adam's own railway was in for all
            // 41 of its non-station approaches until he measured it, and every railway is in first.
            List<Integer> none = new ArrayList<>();

            for (int i = 0; i < was.size(); i++) none.add(0);

            approach.setPlaces(approach.getPlaceIds(), none);

            train.setTrainLength(3);

            assertNull(Layout.whyABerthCannotHoldIt(justTheApproach(approach), train),
                "no tile on the approach to " + approach.getEnd().getName() + " has been measured,"
                + " and the berth rule refused a three-unit train anyway. The claim walk spends the"
                + " train's length over the places and stops when it runs out; against all zeroes it"
                + " never runs out, claims the whole approach, and then refuses on any road that"
                + " shares any of it. Nothing is known here, so nothing can be refused - which is the"
                + " answer the room rule gives at the other end of the same question. Said: "
                + Layout.whyABerthCannotHoldIt(justTheApproach(approach), train));

            // AND ONE MEASUREMENT IS ENOUGH TO JUDGE AGAIN, which is the other half of the ruling.
            //
            // NOT THE LAST PLACE (SVV-B2).  The last place is the BERTH ITSELF, and since Adam's
            // allowance ruling the walk does not spend it - so measuring that one left the walk with
            // nothing to spend, claiming the whole approach, and this claim passed because of the
            // over-claim rather than because of the arithmetic it names.  It would have gone red
            // against the repair for SVV-B1, which is a guard arguing for the defect it guards.
            //
            // The place BEFORE it is track the train really does lie back over, which is what "one
            // measurement is something to reason from" means.
            assertTrue(none.size() >= 2,
                "this approach carries one place, so there is no square behind the berth to measure"
                + " and the half of the ruling below cannot be exercised here");

            List<Integer> some = new ArrayList<>(none);

            some.set(some.size() - 2, 1);

            approach.setPlaces(approach.getPlaceIds(), some);

            assertNotNull(Layout.whyABerthCannotHoldIt(justTheApproach(approach), train),
                "the tile behind the berth measures 1 and the train is 3, so the rule can see track"
                + " this train hangs back over - and it said nothing. A stretch is indeterminate only"
                + " when ALL of it is zero (Adam, MT-364); one measurement is something to reason"
                + " from, and declining to judge here would be the over-correction");

            // AND THE BERTH'S OWN MEASUREMENT IS NOT ONE OF THEM (SVV-B1, and SVX-C5 for keeping it).
            //
            // The arrangement above cannot tell SVV-B1's repair from what it replaced: with `size-2`
            // measured, "has anything been measured?" is true under both the old bound and the new
            // one.  This is the arrangement that moves - the LAST place measured and nothing else -
            // and it is the one Adam's manual produces first, because it tells him to measure the
            // station.
            //
            // The last place is the berth itself, and since the allowance ruling its measurement says
            // how much train the station may HOLD, not track the body lies back over.  So there is
            // still nothing measured behind this train and still nothing to reason from.
            //
            // MUTATION, run 2026-09-13: restoring `n < spans.size()` in the `anyMeasured` loop fails
            // this and leaves the claim above passing, which is how the pair was worse than either.
            List<Integer> berthOnly = new ArrayList<>(none);

            berthOnly.set(berthOnly.size() - 1, 5);

            approach.setPlaces(approach.getPlaceIds(), berthOnly);

            assertNull(Layout.whyABerthCannotHoldIt(justTheApproach(approach), train),
                "the berth itself measures 5 and nothing behind it is measured, and the rule refused"
                + " the train anyway. A station's measurement is an allowance - how much train it may"
                + " hold (Adam, 2026-09-13) - so measuring it says nothing about where this train's"
                + " back end is. Reading it as track brings PRW-B1's invented refusal back for every"
                + " berth somebody has measured, which is the first thing the manual asks them to do."
                + " Said: " + Layout.whyABerthCannotHoldIt(justTheApproach(approach), train));
        }
        finally
        {
            approach.setPlaces(approach.getPlaceIds(), was);
        }
    }

    /**
     * The last edge on its own, which is the route as far as the square being judged.
     */
    private static List<Edge> justTheApproach(Edge approach)
    {
        return new ArrayList<>(Arrays.asList(approach));
    }

    /**
     * The one approach this class's figures are about, by name.
     */
    private static Edge approachFrom(String from, String to)
    {
        Edge found = layout.getEdge(from, to);

        if (found == null)
        {
            throw new SkipException("the snapshot no longer runs " + from + " -> " + to
                + ", so it is about a railway that has moved");
        }

        return found;
    }
}
