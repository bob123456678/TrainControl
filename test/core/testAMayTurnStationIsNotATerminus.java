package core;

import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A train that cannot reverse may still be sent to a station trains MAY turn at (Adam, MT-367).
 *
 * *"EN57-947 is not allowed to go to BottomMainB, even though it 'may' reverse (is not a terminus). It
 * is correctly barred from BottomMainC, a terminus."*
 *
 * **This is OB-205 claim 1 reading the flag that OB-205 claims 2 and 3 say cannot be read.** Those two
 * are one cause: *"`AutonomyBuilder` emits the turning copy of a MAY-turn square with `terminus: true`,
 * so `isTerminus()` cannot tell it from a compulsory terminus"*, and their fix was to ask the DOOR
 * rather than the flag. Claim 1 went in the same evening asking exactly that flag, so a square trains
 * may turn at became a square a non-reversible train could not be sent to at all.
 *
 * **The question is about the SQUARE, not about this copy of it.** Measured on Adam's own railway, as
 * refrozen on 2026-09-23:
 *
 * <pre>
 *   BottomMainB   (eastbound, reverse) terminus     (eastbound) plain
 *   BottomMainC   one copy, a terminus - he made it a compulsory turn, and it is emitted with no plain copy
 * </pre>
 *
 * BottomMainB has a copy a train can stand at without turning, so a non-reversible train is not
 * stranded there. BottomMainC has none: every way to be on that square turns the train. That is what
 * "a terminus it could not leave" means, and it is answerable at runtime because `Point.getBlock`
 * knows which copies are one piece of track.
 *
 * **A first attempt at the discriminator was wrong and the probe caught it**: "some sibling is not a
 * terminus" is true of BottomMainC too, because its westbound copy is a REVERSING point rather than a
 * terminus. A way through has to be neither.
 *
 * MUTATION: drop the way-through clause and the BottomMainB claim fails; drop the whole terminus rule
 * and the BottomMainC claim fails; drop `isAutoDestination` and the berth claim fails.
 *
 * @author Adam
 */
public class testAMayTurnStationIsNotATerminus
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static Layout layout;
    private static Locomotive train;
    private static Boolean wasReversible;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null) throw new SkipException("the snapshot did not build");

        List<String> names = model.getLocList();

        if (names.isEmpty()) throw new SkipException("this fixture has no locomotives");

        train = model.getLocByName(names.get(0));

        wasReversible = train.isReversible();

        // Borrowed from the real database and given back - the suite runs against the live LocDB.
        train.setReversible(false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (train != null && wasReversible != null) train.setReversible(wasReversible);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The claim: a square trains MAY turn at is still offered to a train that cannot turn.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAMayTurnSquareIsStillOffered() throws Exception
    {
        Point turning = named("BottomMainB (eastbound, reverse)");

        assertTrue(turning.isTerminus(),
            "precondition: " + turning.getName() + " is not emitted as a terminus, so it is not the"
            + " case MT-367 is about - the whole point is that the flag cannot tell a MAY-turn copy"
            + " from a compulsory one");

        assertTrue(aWayThroughExists(turning),
            "precondition: no copy of " + turning.getName() + " lets a train stand there without"
            + " turning, so this square really is one a non-reversible train could not leave and the"
            + " claim below would be wrong");

        assertTrue(layout.isOfferableToOperator(turning, train),
            train.getName() + " cannot be sent to " + turning.getName() + " even though the square has"
            + " a way through. Adam, MT-367: \"EN57-947 is not allowed to go to BottomMainB, even"
            + " though it 'may' reverse (is not a terminus)\" - OB-205 claim 1 asks isTerminus(), which"
            + " claims 2 and 3 of the same issue say cannot tell the two apart");
    }

    /**
     * And the control: a square every way in turns the train at is still refused.
     *
     * **The compulsory case is his railway's own now.**  Until the refreeze of 2026-09-23 the snapshot
     * predated his making BottomMainC a compulsory turn, so this closed its plain copies to build the
     * case.  The snapshot now has it as he runs it - one copy, a terminus, no way through - so nothing is
     * arranged, and the precondition says so.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testACompulsoryTerminusIsStillRefused() throws Exception
    {
        Point terminus = named("BottomMainC");

        assertTrue(terminus.isTerminus() && !aWayThroughExists(terminus),
            "precondition: BottomMainC is not a compulsory turn on this fixture any more (terminus="
            + terminus.isTerminus() + "), so it is not the square Adam said is correctly barred");

        assertFalse(layout.isOfferableToOperator(terminus, train),
            train.getName() + " cannot reverse and is still offered " + terminus.getName()
            + ", where every way to be on that square turns it. Adam: \"It is correctly barred"
            + " from BottomMainC, a terminus\" - and it must stay barred");
    }

    /**
     * The half of a compulsory turn that is spelled `reversing` is refused too.
     *
     * Adam, MT-367, 2026-09-13: **"still offered it for 2-8-4 3505."**
     *
     * **A turning copy carries whichever of two words the builder chose for it** -
     * `stops ? "terminus" : "reversing"`, decided by whether a train may stop there - and this rule
     * read only the first. Measured on his railway before this was written:
     *
     *     BottomMainC (eastbound, reverse)   terminus=true    offerable=false   <- refused
     *     BottomMainC (westbound, reverse)   reversing=true   offerable=TRUE    <- the leak
     *
     * One compulsory-turn station, half refused and half offered, to the same locomotive. To a train
     * that cannot reverse the two words say one thing: you must turn round here.
     *
     * **The spelling is arranged; the square is his.**  BottomMainC builds to one copy now, spelled
     * `terminus`, so it is re-spelled `reversing` here for the length of the claim - the case the report
     * was about, on the square it was about.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheReversingCopyOfACompulsoryTurnIsRefusedToo() throws Exception
    {
        Point reversing = named("BottomMainC");

        assertTrue(reversing.isAutoDestination(),
            "precondition: that copy is not one autonomy may choose, so the berth exemption applies"
            + " and refusing it is not what this claim is about");

        // THE SPELLING HAS TO BE ARRANGED.  What the claim is about is the COMPULSORY case with a copy
        // spelled `reversing` rather than `terminus`; his BottomMainC is compulsory and spelled
        // `terminus`.  Any plain sibling is closed too, so the claim holds if the square ever grows one.
        List<Point> closed = new java.util.ArrayList<>();

        boolean wasTerminus = false;

        try
        {
            for (Point sibling : layout.getPoints())
            {
                if (!reversing.isSamePlaceAs(sibling)) continue;

                if (sibling == reversing || sibling.isTerminus() || sibling.isReversing()) continue;

                sibling.setTerminus(true);

                closed.add(sibling);
            }

            // A Point refuses to be both - "Terminus stations cannot be set as reversing" - and in
            // this snapshot that copy IS a terminus, which is the other spelling of the same thing.
            // Cleared first, and put back in the finally.
            wasTerminus = reversing.isTerminus();

            if (wasTerminus) reversing.setTerminus(false);

            reversing.setReversing(true);

            assertTrue(reversing.isReversing(),
                "the copy under test could not be made a reversing point, so the spelling this claim"
                + " is about is not present");

            assertFalse(reversing.isTerminus(),
                "that copy carries the terminus flag too, so the old rule would already have refused"
                + " it and this claim cannot fail");

            assertFalse(aWayThroughExists(reversing),
                "closing every plain copy left a way through anyway, so this is still a MAY-turn"
                + " square and being offered would be correct: " + closed);

            assertFalse(layout.isOfferableToOperator(reversing, train),
                train.getName() + " cannot reverse and is still offered " + reversing.getName()
                + ". Every way to be on that square turns the train round; that this copy is spelled"
                + " \"reversing\" rather than \"terminus\" is the builder's choice about whether a"
                + " train may STOP there, and says nothing to a locomotive that cannot back out.");
        }
        finally
        {
            reversing.setReversing(false);

            if (wasTerminus) reversing.setTerminus(true);

            for (Point sibling : closed) sibling.setTerminus(false);
        }
    }

    /**
     * And the exemption OB-205 was careful to keep: a parking berth is still offered.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAParkingBerthIsStillOffered() throws Exception
    {
        Point berth = named("TunnelLongPark");

        assertTrue(berth.isTerminus() && !berth.isAutoDestination(),
            "precondition: " + berth.getName() + " is not a terminus autonomy leaves alone, so it is"
            + " not the berth this exemption is about");

        assertTrue(layout.isOfferableToOperator(berth, train),
            "a parking berth was refused to a train that cannot reverse. Adam's ruling of 2026-09-01"
            + " is that \"the operator asking for that BERTH by hand is no longer refused\", and"
            + " OB-205 narrowed the rule around that case rather than over it");
    }

    /**
     * And with the train able to reverse, the compulsory terminus comes back.
     *
     * Without this, every claim above is satisfied by a rule that refuses on something other than the
     * locomotive - the square being switched off, say - and never looks at the train at all.
     *
     * @throws Exception from the railway
     */
    @Test(dependsOnMethods = "testACompulsoryTerminusIsStillRefused")
    public void testAReversibleTrainIsOfferedTheTerminus() throws Exception
    {
        Point terminus = named("BottomMainC");

        try
        {
            train.setReversible(true);

            assertTrue(layout.isOfferableToOperator(terminus, train),
                "a train that CAN reverse is refused " + terminus.getName() + ", so the rule is not"
                + " about the locomotive at all and the refusal above says nothing about reversing");
        }
        finally
        {
            train.setReversible(false);
        }
    }

    /**
     * Whether any copy of this square lets a train stand there without being turned.
     *
     * Worked out here from the Points rather than asked of the rule, so the preconditions above are a
     * statement about the railway and not a restatement of the answer.
     */
    private static boolean aWayThroughExists(Point square)
    {
        for (Point sibling : layout.getPoints())
        {
            if (sibling == square || !square.isSamePlaceAs(sibling)) continue;

            if (!sibling.isTerminus() && !sibling.isReversing()) return true;
        }

        return false;
    }

    private static Point named(String name)
    {
        for (Point p : layout.getPoints())
        {
            if (name.equals(p.getName())) return p;
        }

        throw new SkipException("the snapshot has no Point called " + name
            + ", so it is about a railway that has moved");
    }
}
