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
 * measures 6 with 3 of it past the switch, so a six-unit train stands across the points, and he rules
 * that acceptable because the train moves on.
 *
 * At a parking berth, a train is STAYING, and a berth is not worth a road: it must foul no track that
 * is not a way in or out of that square. On his railway a three-unit train at TunnelLongPark lies over
 * `BottomMainAPre -> RampDown` and `-> BottomCrossover`, which are the only roads to the lower level -
 * measured, 90 pairs of stations lose their connection - and that is what the berth half refuses.
 *
 * **The failure this class exists to catch is the relaxation leaking.** Both halves are about a train
 * standing on points, and a rule that stopped asking WHICH KIND OF SQUARE it is standing at would admit
 * the berth case along with the platform one. So the berth claims are not decoration; they are the
 * mutation target.
 *
 * **The lengths are Adam's hypothetical, set here rather than read.** He has not written them into the
 * layout - *"i didnt update any layout files with this info, but this should be the example motivating
 * the design"* - so the two squares of that run are measured in the sandbox at the figures he gave:
 * 3 either side of the switch at 14,12.
 *
 * MUTATION: drop the `isAutoDestination` test so the relaxation applies everywhere, and the berth claim
 * at length 3 fails. Remove the relaxation and the platform claim at 6 fails. Make the relaxation
 * unbounded - any length at a station - and the claim at 7 fails. Make the berth check always answer
 * null and the direct claim at the bottom fails.
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

    /** The two squares of that run Adam gave figures for */
    private static final TileKey BEFORE_THE_SWITCH = new TileKey("1 - Main", 13, 12);
    private static final TileKey THE_PLATFORM = new TileKey("1 - Main", 19, 12);

    /**
     * And the berth itself, which the snapshot does not measure either.
     *
     * `test/layouts/live-snapshot` carries NO lengths at all - that is deliberate, and OB-196 says why
     * - so every figure this class needs is set here. Two units is the berth Adam's own railway has at
     * TunnelLongPark, and it is what makes the berth half of the rule askable: a train of two fits
     * inside it and a train of three does not.
     */
    private static final TileKey THE_BERTH = new TileKey("1 - Main", 10, 10);

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

        // HIS FIGURES, in the copy: *"each segment that's currently 1 should actually be 3 on either
        // side of it."*
        session.setTileLength(BEFORE_THE_SWITCH, 3);
        session.setTileLength(THE_PLATFORM, 3);
        session.setTileLength(THE_BERTH, 2);

        session.rebuild();

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        // NAMED, not searched for.  Three runs reach TunnelLongPark and two reach BottomMainA, and
        // only one of each is the piece of railway Adam gave figures for - picking "the longest" got
        // an unmeasured one and the class then asserted things about a different approach.
        toThePlatform = approachFrom("BottomMainAPre (eastbound)", "BottomMainA (eastbound)");
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
     * Every claim below is about a particular shape of railway - an approach measuring 6 with 3 of it
     * past the points, and a berth with 2 - and if the snapshot or Adam's figures stop producing that
     * shape, the claims would pass or fail for reasons that have nothing to do with the rule.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheFixtureIsTheShapeTheRuleIsAbout() throws Exception
    {
        assertTrue(toThePlatform.getEnd().isAutoDestination(),
            toThePlatform.getEnd().getName() + " is not a station autonomy may choose, so the"
            + " relaxation this class is about does not apply to it");

        assertEquals(toThePlatform.getLength(), 6,
            "the approach to " + toThePlatform.getEnd().getName() + " measures "
            + toThePlatform.getLength() + " and Adam's example is 6");

        assertEquals(toThePlatform.getRoomAtTheEnd(), 3,
            "the room past the last switch on that approach is " + toThePlatform.getRoomAtTheEnd()
            + " and the example is 3 - a six-unit train has to stand across the points for any of this"
            + " to be the case he described");

        assertFalse(toTheBerth.getEnd().isAutoDestination(),
            toTheBerth.getEnd().getName() + " is a station autonomy may choose, so it is not the"
            + " parking berth half of the rule and the controls below prove nothing");

        assertEquals(toTheBerth.getRoomAtTheEnd(), 2,
            "the room past the last switch into " + toTheBerth.getEnd().getName() + " is "
            + toTheBerth.getRoomAtTheEnd() + ", not the 2 this class's lengths are chosen around");
    }

    /**
     * The claim: at a station autonomy may choose, a train that fits within its approach is allowed.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAPlatformTakesATrainAsLongAsItsWholeApproach() throws Exception
    {
        train.setTrainLength(6);

        assertNull(Layout.whyTooLongForThisRoute(justTheApproach(toThePlatform), train),
            "a six-unit train was refused " + toThePlatform.getEnd().getName() + ", whose approach"
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
        train.setTrainLength(3);

        assertNotNull(Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train),
            "a three-unit train was accepted at " + toTheBerth.getEnd().getName() + ", which has "
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
        train.setTrainLength(2);

        assertNull(Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train),
            "a two-unit train was refused " + toTheBerth.getEnd().getName() + ", which has "
            + toTheBerth.getRoomAtTheEnd() + " units past its last switch, so the claim above is"
            + " satisfied by a berth that takes nothing at all. Refused with: "
            + Layout.whyTooLongForThisRoute(justTheApproach(toTheBerth), train));
    }

    /**
     * The berth rule asked on its own, so it is not the room rule answering for it.
     *
     * On this railway the two agree at TunnelLongPark - a train that fits past the switch also fouls
     * nothing - so the claim above would pass with the berth check removed entirely. This asks the new
     * rule directly: at three units the train lies over `BottomMainAPre -> RampDown` and
     * `-> BottomCrossover`, which are somebody else's roads, and at two it lies over nothing at all.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheBerthRuleNamesTheRoadItWouldFoul() throws Exception
    {
        train.setTrainLength(3);

        String fouled = Layout.whyABerthCannotHoldIt(justTheApproach(toTheBerth), train);

        assertNotNull(fouled,
            "a three-unit train at " + toTheBerth.getEnd().getName() + " lies over the roads to the"
            + " lower level and the berth rule said nothing about it, so what refuses it above is the"
            + " room rule alone and this half of Adam's ruling is not being enforced");

        train.setTrainLength(2);

        assertNull(Layout.whyABerthCannotHoldIt(justTheApproach(toTheBerth), train),
            "a two-unit train at " + toTheBerth.getEnd().getName() + " fits inside the berth and the"
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
            // NOBODY HAS MEASURED THIS APPROACH, which is the state Adam's own railway is in for all
            // 41 of its non-station approaches.
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
