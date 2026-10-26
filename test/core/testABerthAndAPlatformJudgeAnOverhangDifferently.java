package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
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
 * A train may overhang the points at a station, and may not at a parking berth (Adam: "we need a clear rule to
 * govern that this is OK, or simply make a rule that parking berths cant block any other edges, but not make that
 * check for active stations").
 *
 * At a station autonomy may choose, a train is PASSING: it fits if it fits past the last switch, as before, or
 * within its own approach.  At a parking berth, a train is STAYING, and a berth is not worth a road: it must foul no
 * track that is not a way in or out of that square.  The failure this class exists to catch is the relaxation
 * leaking: a rule that stopped asking WHICH KIND OF SQUARE it is standing at would admit the berth case along with
 * the platform one, so the berth claims are the mutation target.
 *
 * The figures are his measured railway (the snapshot refrozen from `e36df979`), not figures set here.  The platform
 * is Tunnel: the approach from TunnelPre measures 4 with 2 past the switch, at a platform that holds five.  The berth
 * is TunnelLongPark, 3 past its switch on an approach of 7.  One setting of his is taken off, the berth's capacity:
 * he gave every berth a maximum equal to the room past its switch, so `Point.validateTrainLength` (asked first in
 * `whyTooLongForThisRoute`, and tested where it is the subject) refuses an overhang before the berth rule is asked.
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

    /** Why the set-up found no fixture for these claims, or null; asked by each claim, so it fails rather than skips. */
    private static String unbuilt;

    /** Fails the claim when the set-up found no fixture: a skip or an assert in `setUpClass` reads as skipped tests. */
    private static void assertBuilt()
    {
        if (unbuilt != null) throw new AssertionError(unbuilt);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulated();

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        // HIS MEASUREMENTS, AND NOT THE BERTH'S CAPACITY: the capacity refuses every overhang first, so the berth rule
        // would never be reached.  Nothing else is changed.
        assertEquals(session.getStationIndex().nameOf(THE_BERTH), "TunnelLongPark",
            "the square this class clears the capacity of is no longer TunnelLongPark");

        session.setPointProperty(THE_BERTH, "maxTrainLength", null);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null)
        {
            unbuilt = "the snapshot did not build";
            return;
        }

        // NAMED, not searched for: several runs reach TunnelLongPark and BottomMainA, and only one of each is the piece
        // of railway this class is about.
        toThePlatform = approachFrom("TunnelPre", "Tunnel (southbound)");
        toTheBerth = approachFrom("BottomMainA (westbound)", "TunnelLongPark");

        if (unbuilt != null) return;

        List<String> names = model.getLocList();

        if (names.isEmpty())
        {
            unbuilt = "this fixture has no locomotives";
            return;
        }

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
     * The geometry this class rests on, stated rather than hoped for: a platform approach measuring 4 with 2 past the
     * points at a platform that holds more than the approach, and a berth with 3 past its switch on a longer approach.
     * If the snapshot stops producing that shape the claims pass or fail for reasons that have nothing to do with the
     * rule.
     */
    @Test
    public void testTheFixtureIsTheShapeTheRuleIsAbout() throws Exception
    {
        assertBuilt();

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

        // AND THE PLATFORM HOLDS MORE THAN ITS APPROACH, so the claim one unit past the approach is answered by the
        // route bound rather than by the capacity he typed on the platform.
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

    /** The class's train at the given length, for asking the capacity rule on its own. */
    private static Locomotive trainOf(int units)
    {
        train.setTrainLength(units);

        return train;
    }

    /** The claim: at a station autonomy may choose, a train that fits within its approach is allowed. */
    @Test
    public void testAPlatformTakesATrainAsLongAsItsWholeApproach() throws Exception
    {
        assertBuilt();

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
     * And it is bounded: a train longer than the measured route in (here the approach alone) is still refused; without
     * this the relaxation is "no check at all at a station".
     */
    @Test
    public void testAPlatformStillRefusesATrainLongerThanItsApproach() throws Exception
    {
        assertBuilt();

        train.setTrainLength(toThePlatform.getLength() + 1);

        assertNotNull(Layout.whyTooLongForThisRoute(justTheApproach(toThePlatform), train),
            "a train one unit longer than the measured route in (here the whole approach) was accepted at "
            + toThePlatform.getEnd().getName() + ", so the relaxation is unbounded and a train of any"
            + " length may stop at a station");
    }

    /** The berth half, and the mutation target: a parking berth does NOT get the relaxation. */
    @Test
    public void testAParkingBerthStillRefusesAnOverhang() throws Exception
    {
        assertBuilt();

        train.setTrainLength(toTheBerth.getRoomAtTheEnd() + 1);

        assertNotNull(Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train),
            "a " + train.getTrainLength() + "-unit train was accepted at " + toTheBerth.getEnd().getName()
            + ", whose approach measures " + toTheBerth.getLength() + " and which has "
            + toTheBerth.getRoomAtTheEnd() + " units past its last switch. Adam: \"make a rule that"
            + " parking berths cant block any other edges, but not make that check for active"
            + " stations\" - the relaxation has leaked from the platforms to the berths");
    }

    /** And the berth is not simply refusing everything. */
    @Test
    public void testAParkingBerthStillTakesATrainThatFits() throws Exception
    {
        assertBuilt();

        train.setTrainLength(toTheBerth.getRoomAtTheEnd());

        assertNull(Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train),
            "a " + train.getTrainLength() + "-unit train was refused " + toTheBerth.getEnd().getName() + ", which has "
            + toTheBerth.getRoomAtTheEnd() + " units past its last switch, so the claim above is"
            + " satisfied by a berth that takes nothing at all. Refused with: "
            + Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train));
    }

    /**
     * The berth rule asked on its own, so it is not the room rule answering for it: at TunnelLongPark a train that fits
     * past the switch also fouls nothing, so the claim above would pass with the berth check removed.  One unit longer
     * than the berth, the train lies back over somebody else's road; at the berth's own room, over nothing.
     */
    @Test
    public void testTheBerthRuleNamesTheRoadItWouldFoul() throws Exception
    {
        assertBuilt();

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
     * One measured square behind an unmeasured piece closes the berth, and the refusal now says why.  This is the
     * ordinary intermediate state of Mass Assign Lengths (MT-455 has the operator skip a piece): `anyMeasured` is true
     * on the switch alone, so the walk claims every place and refuses the berth to a one-unit train as readily as to a
     * nine-unit one.  Ruled behavior (Adam: "we want clear warnings to the user if so, then it's fine"), so this pins
     * it; what was missing is that the refusal never named the unmeasured piece.
     */
    @Test
    public void testAMeasuredSquareBehindAnUnmeasuredPieceSaysWhyItRefuses() throws Exception
    {
        assertBuilt();

        Edge approach = toTheBerth;

        List<Integer> was = new ArrayList<>(approach.getPlaceLengths());

        // AND NOTHING ON IT ANSWERED 0 (FR-136): an answered square is not counted as missing, and which of his he has
        // answered is his working state.
        List<String> answered = new ArrayList<>();

        for (String id : approach.getPlaceIds())
        {
            if (approach.isPlaceAnswered(id)) answered.add(id);
        }

        assertTrue(was.size() >= 3,
            "precondition: this approach has fewer than three places, so there is no room for a measured"
            + " square with an unmeasured piece behind it");

        try
        {
            approach.setAnsweredPlaces(null);

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
            approach.setAnsweredPlaces(answered);
            train.setTrainLength(0);
        }
    }

    /**
     * A berth whose approach nobody has measured refuses nothing: the rule has to be able to say "I do not know".
     * Where every place measures zero the claim walk never runs out and claims the WHOLE approach, an invented refusal
     * (MT-364: "a stretch is only indeterminate when ALL of it is zero"; Adam would rather have no check than an
     * over-strict one).  On the frozen snapshot all 41 non-station approaches are wholly unmeasured and 26 refused a
     * three-unit train.  Partial measurement is still judged, which the claim above asserts.
     */
    @Test
    public void testAnUnmeasuredApproachRefusesNothing() throws Exception
    {
        assertBuilt();

        Edge approach = toTheBerth;

        List<Integer> spans = approach.getPlaceLengths();

        assertFalse(spans.isEmpty(),
            "precondition: this approach carries no places at all, so it is not the case PRW-B1 is"
            + " about - the walk has nothing to over-claim");

        List<Integer> was = new ArrayList<>(spans);

        try
        {
            // NOBODY HAS MEASURED THIS APPROACH, the state every railway is in first.
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

            // AND ONE MEASUREMENT IS ENOUGH TO JUDGE AGAIN, the other half of the ruling.  NOT THE LAST PLACE: that is
            // the BERTH ITSELF, which the walk does not spend since the allowance ruling, so measuring it left the walk
            // claiming the whole approach and the claim passed because of the over-claim.  The place BEFORE it is track
            // the train really lies back over.
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

            // AND THE BERTH'S OWN MEASUREMENT IS NOT ONE OF THEM.  With `size-2` measured, "has anything been
            // measured?" is true under both the old bound and the new one; this arrangement (the LAST place measured
            // and nothing else) is the one that moves.  The last place is the berth itself, and its measurement says
            // how much train the station may HOLD, not track the body lies back over.
            //
            // MUTATION: restoring `n < spans.size()` in the `anyMeasured` loop fails this and leaves the claim above
            // passing.
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

    /** The last edge on its own, which is the route as far as the square being judged. */
    private static List<Edge> justTheApproach(Edge approach)
    {
        return new ArrayList<>(Arrays.asList(approach));
    }

    /**
     * The one approach this class's figures are about, by name; one the railway does not run is what `unbuilt` says.
     */
    private static Edge approachFrom(String from, String to)
    {
        Edge found = layout.getEdge(from, to);

        if (found == null && unbuilt == null)
        {
            unbuilt = "the snapshot no longer runs " + from + " -> " + to + ", so it is about a railway that has moved";
        }

        return found;
    }
}
