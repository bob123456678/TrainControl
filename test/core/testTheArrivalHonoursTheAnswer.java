package core;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;

/**
 * A train turned at the end of a journey is turned because the answer said so (Adam, MT-368).
 *
 * *"when asked if it should keep direction, I said yes, but it still got reversed at the 'may change
 * direction' station bottommainb.  ran it twice with the same behavior.  'no' also reverses it, so it is
 * just always getting reversed."*
 *
 * **The defect was an `||`.**  The arrival read
 * `arrived.isTerminus() || shouldReverseAt(arrived, arrived, loc, reversals)`, and `AutonomyBuilder`
 * emits the turning copy of a MAY-turn square with `terminus: true` - so the left operand was true, the
 * right was never evaluated, and the dialog's answer was collected and thrown away.
 *
 * **This is the same flag confusion `OB-205` claims 2 and 3 named**, at a third site. Two were repaired
 * that evening - `ManualReversalPrompt.forJourney` and `Layout.shouldReverseAt` - and this one was not,
 * which is `fix-one-site-sweep-the-siblings` exactly.
 *
 * **And why it passed the tests, which is the part worth keeping.**  The only test of that site was
 * `core.testNonReversibleTrains.testTheRunAsksThatRule`, which reads `Layout.java` as a STRING and
 * asserts the statement verbatim - `||` and all. A guard that requires an expression cannot fail on what
 * that expression does, and this one pinned the bug in place: correcting the line broke the test, so the
 * guard argued for the defect. The comment beside the code said as much, asking that the line not be
 * changed in shape because the guard would not recognise it. Code and guard were each citing the other,
 * and neither was reading the railway.
 *
 * So the decision has a name now - `Layout.turnsOnArrival` - and this class asks it what it DOES.
 *
 * MUTATION: restore the `||` and the first claim fails; drop the `asksAbout` branch and it fails too;
 * make the branch answer `true` and the "keep direction" claim fails.
 *
 * @author Adam
 */
public class testTheArrivalHonoursTheAnswer
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Layout layout;
    private static Locomotive loc;

    /** The operator's answer, as the two doors hand it over: a constant, decided before departure. */
    private static Layout.ReversalPolicy answering(final boolean turn, final Point askedAbout)
    {
        return new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return turn;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                // Independent of the ANSWER, which is the contract `ReversalPolicy` spells out and
                // which cost a day when it was broken.
                return at == askedAbout;
            }
        };
    }

    /**
     * Waits for something to become true, or gives up.
     *
     * A bounded wait rather than a sleep: the railway's timings are its own and a fixed pause is
     * either too short on a busy machine or wasted on a quiet one.
     *
     * @param until the condition
     * @param ceilingMs how long to wait before answering false
     * @return whether it came true
     */
    private static boolean waitFor(java.util.function.BooleanSupplier until, long ceilingMs)
    {
        long deadline = System.currentTimeMillis() + ceilingMs;

        while (System.currentTimeMillis() < deadline)
        {
            if (until.getAsBoolean()) return true;

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return until.getAsBoolean();
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference and would otherwise
        // open Adam's real railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        layout = new Layout(model);

        MarklinFeedback a = model.newFeedback(2210, null);
        MarklinFeedback b = model.newFeedback(2211, null);
        MarklinFeedback c = model.newFeedback(2212, null);

        // A MAY-TURN SQUARE AS THE BUILDER EMITS ONE: a destination whose turning copy carries the
        // terminus flag.  That flag is the whole of the confusion, so the fixture has to carry it.
        layout.createPoint("MT368_MAY", true, a.getName());
        layout.createPoint("MT368_TERMINUS", true, b.getName());
        layout.createPoint("MT368_PLAIN", true, c.getName());

        layout.getPoint("MT368_MAY").setTerminus(true);
        layout.getPoint("MT368_TERMINUS").setTerminus(true);

        // A SQUARE AS THE BUILDER REALLY EMITS ONE: both copies, tied by a block (PRW-A1).
        //
        // The claims above ask the DECISION and the fixture above is enough for that.  What they
        // cannot see is where the train is left standing afterwards, and that needs the other copy to
        // exist: `AutonomyBuilder` emits a may-turn square as a plain copy per arrival side PLUS a
        // turning copy per arrival side, tied together by `block`, and the two face opposite ways.
        //
        // Measured on the frozen snapshot before this was written: the path the menu offers to
        // BottomMainC ends on `BottomMainC (eastbound, reverse)` - the TURNING copy - for both of
        // Adam's reversible trains, five runs out of five.  So the copy a train arrives on at a
        // may-turn square is routinely the one that expects it to turn.
        layout.createPoint("MT368_ARRIVE", true, model.newFeedback(2213, null).getName());
        layout.createPoint("MT368_TURNCOPY", true, model.newFeedback(2214, null).getName());
        layout.createPoint("MT368_PLAINCOPY", true, model.newFeedback(2215, null).getName());

        // One square, two copies.
        layout.getPoint("MT368_TURNCOPY").setBlock("MT368_BLOCK");
        layout.getPoint("MT368_PLAINCOPY").setBlock("MT368_BLOCK");

        // The turning copy carries the terminus flag, exactly as the builder writes it.
        layout.getPoint("MT368_TURNCOPY").setTerminus(true);

        layout.createEdge("MT368_ARRIVE", "MT368_TURNCOPY");

        // AND THE ARRIVING EDGE KNOWS WHICH SIDE IT COMES IN BY.
        //
        // `entrySideOf` prefers the side the BUILD wrote on the edge and falls back to compass
        // geometry, which a hand-built Point has none of - so without this the arrival records no
        // side at all, and the claim that the tail is carried over has no tail to be carried.
        layout.getEdge("MT368_ARRIVE", "MT368_TURNCOPY").setEntrySide("W");

        // AND THE PLAIN COPY IS REACHED BY THE SAME APPROACH, which is how the builder emits them:
        // both copies of one arrival side hang off the same edge, and which of the two a path ends on
        // is what PRW-A1 is about.
        layout.createEdge("MT368_ARRIVE", "MT368_PLAINCOPY");

        // AND A SECOND PLAIN COPY, FOR THE OTHER ARRIVAL SIDE (PRV-C4).
        //
        // A split square has a plain copy per arrival SIDE - four Points on Adam's biggest squares -
        // and only one of them is the copy THIS train could have driven onto.  With one plain copy in
        // the fixture, "the sibling that is not a turning copy" and "the sibling this approach
        // reaches" are the same Point, and a claim cannot tell the rule from the accident: deleting
        // the approach test left every claim green.
        //
        // This one is the same square - same block, not a turning copy - and is reached from a
        // DIFFERENT approach.  A train that came in by MT368_ARRIVE must not be stood on it.
        layout.createPoint("MT368_OTHERWAY", true, model.newFeedback(2216, null).getName());
        layout.createPoint("MT368_OTHERAPPROACH", true, model.newFeedback(2217, null).getName());

        layout.getPoint("MT368_OTHERWAY").setBlock("MT368_BLOCK");

        layout.createEdge("MT368_OTHERAPPROACH", "MT368_OTHERWAY");

        loc = model.getLocByName(model.getLocList().get(0));
    }

    /**
     * The class's railway made current again after each claim: a claim that builds a railway of its own retires every
     * other one (`Layout`'s constructor counts a new version), and the claims after it that send a train on this one
     * found it retired - the run refused, and "the train never set off".
     */
    @AfterMethod(alwaysRun = true)
    public void theClassRailwayIsCurrentAgain()
    {
        if (layout != null) layout.makeCurrent();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        // The Points belong to a Layout this class made, so there is nothing on the real railway to
        // put back; the feedbacks are the suite's usual spare addresses.
        if (sandbox != null) sandbox.close();
    }

    /**
     * The claim: "keep direction" at a may-turn square keeps the direction.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testKeepDirectionIsHonouredAtAMayTurnSquare() throws Exception
    {
        Point may = layout.getPoint("MT368_MAY");

        assertTrue(may.isTerminus(),
            "precondition: the fixture's may-turn square does not carry the terminus flag, so it is"
            + " not the case MT-368 is about - the flag is the whole confusion");

        assertFalse(layout.turnsOnArrival(may, loc, answering(false, may)),
            "the operator said KEEP DIRECTION at a square trains may turn at, and the train was turned"
            + " anyway. Adam, MT-368: \"I said yes, but it still got reversed... 'no' also reverses"
            + " it, so it is just always getting reversed\". The arrival read"
            + " `arrived.isTerminus() || shouldReverseAt(...)`, and the turning copy of a may-turn"
            + " square carries terminus:true - so the answer was never read");
    }

    /**
     * A train that declines the turn is not left standing on the copy that expected it to turn.
     *
     * **The consequence the boolean claim above cannot see** (PRW-A1).  Answering the question is half
     * the job; the other half is that the graph agrees with the train afterwards.
     *
     * A may-turn square is two Points per arrival side - the plain copy, facing the way a train that
     * drove in is pointing, and the turning copy, facing the other way - and the path offered to such
     * a square routinely ends on the TURNING one: measured on the frozen snapshot,
     * `BottomMainC (eastbound, reverse)` for both of Adam's reversible trains, five runs out of five,
     * because `StationIndex.distinctDestinations` keeps the first path per square and the enumeration
     * that produced it walks the shuffling `getNeighbors`.
     *
     * So a train that arrives there and declines the turn faces one way while the Point it is standing
     * on says the other. Nothing on the railway is wrong yet - the train is where it should be - but
     * the graph is, and the next dispatch reads that Point's outgoing edges, which are the edges for a
     * train pointing the other way. It drives off its own route.
     *
     * **This became reachable when the answer started being honoured.**  Before MT-368 the arrival
     * turned every train at a may-turn square whatever the operator said, and a turned train agrees
     * with the turning copy - so the copy was right for the wrong reason. Repairing the decision is
     * what exposed this, which is `a-fix-can-be-worse-than-the-defect` arriving as a second half
     * rather than as a worse first one.
     *
     * The mirror case already has its repair: a train that DOES turn is re-stood by
     * `AutonomySession.faceTheWayItCameIn`, which writes the side it came in by and calls
     * `moveOntoFacingCopy`. This direction had none.
     *
     * Driven through `executePath` rather than by asking the rule, because
     * `extracted-rule-moves-the-bug-to-the-call` is how the last two defects in this method arrived:
     * a claim about a helper leaves the call site as the only uncovered part, and the call site is
     * where both of them were.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testDecliningTheTurnDoesNotLeaveItOnTheTurningCopy() throws Exception
    {
        Point arrive = layout.getPoint("MT368_ARRIVE");
        Point turning = layout.getPoint("MT368_TURNCOPY");
        Point plain = layout.getPoint("MT368_PLAINCOPY");

        assertTrue(turning.isSamePlaceAs(plain),
            "precondition: the fixture's two copies are not one square, so there is nothing for the"
            + " train to be re-stood on and this claim cannot fail");

        assertTrue(turning.isTerminus(),
            "precondition: the turning copy does not carry the terminus flag, so it is not the case"
            + " PRW-A1 is about");

        assertFalse(plain.isTerminus(),
            "precondition: the plain copy carries the terminus flag too, so the two are"
            + " indistinguishable and nothing below could pick between them");

        model.setFeedbackState(arrive.getS88(), true);
        model.setFeedbackState(turning.getS88(), false);
        model.setFeedbackState(plain.getS88(), false);

        arrive.setLocomotive(loc);

        final java.util.List<org.traincontrol.automation.Edge> path =
            new java.util.ArrayList<>();

        path.add(layout.getEdge("MT368_ARRIVE", "MT368_TURNCOPY"));

        assertNotNull(path.get(0), "the fixture produced no edge, so nothing below is exercised");

        layout.runLocomotives();

        // ON ANOTHER THREAD, WITH THE SENSOR FIRED BY HAND, which is how the railway is driven here.
        //
        // `executePath` blocks until the train arrives, and arriving means the destination's feedback
        // goes on - there is no Central Station and no packet simulation in this class. So the run
        // goes on a daemon thread and this one plays the railway: wait for the train to be under way,
        // then occupy the destination and clear the square it left.
        final Layout running = layout;
        final Locomotive driver = loc;

        Thread run = new Thread(() -> running.executePath(path, driver, 20, null,
            answering(false, turning)));

        run.setDaemon(true);
        run.start();

        try
        {
            assertTrue(waitFor(() -> running.isRunning() && driver.getSpeed() > 0, 15000),
                "the train never set off, so no arrival happened and this claim tested nothing");

            // AND ON ITS WAY THE RAILWAY ALREADY KNOWS WHERE IT WILL STAND (OB-314): the copy it will be stood on, not
            // the turning copy its path ends on - so the label does not show it arriving the other way round
            org.testng.Assert.assertSame(running.copyItWillStandOn(driver), plain, "on its way, the railway says the train will stand on "
                + running.copyItWillStandOn(driver) + " - the copy its path ends on - though KEEP DIRECTION puts it on the"
                + " plain copy (OB-314)");

            model.setFeedbackState(turning.getS88(), true);
            model.setFeedbackState(arrive.getS88(), false);

            assertTrue(waitFor(() -> !run.isAlive(), 30000),
                "the run never finished after the destination's sensor went on, so the arrival this"
                + " claim is about was never reached");
        }
        finally
        {
            layout.stopLocomotives();

            run.interrupt();
        }

        assertTrue(plain.getCurrentLocomotive() == loc,
            "the operator said KEEP DIRECTION, the train was correctly not turned, and it was left"
            + " standing on " + (turning.getCurrentLocomotive() == loc
                ? "the TURNING copy" : "neither copy")
            + " of that square. The turning copy faces the way a train that HAS turned points, so the"
            + " graph now disagrees with the train by 180 degrees, and the next dispatch reads that"
            + " copy's outgoing edges - the ones for a train pointing the other way. A train that"
            + " turns is re-stood by faceTheWayItCameIn; one that declines had no such repair.");
    }

    /**
     * It is stood on the copy ITS OWN approach reaches, not merely on one that is not a turning copy.
     *
     * **PRV-C4: without this the rule and an accident of the fixture are indistinguishable.**  A split
     * square has a plain copy per arrival side, and only the one reachable from where the train came
     * is the copy it could have driven onto.  Picking by "not a turning copy" alone would put an
     * eastbound train on the westbound copy half the time - the same defect facing the other way -
     * and with a single plain copy in the fixture, deleting the approach test changed nothing.
     *
     * Asserted as part of the same arrival as the claim above rather than by driving a second one:
     * what is being checked is which of two candidates was chosen, and both were candidates then.
     */
    @Test(dependsOnMethods = "testDecliningTheTurnDoesNotLeaveItOnTheTurningCopy")
    public void testItIsStoodOnTheCopyItsOwnApproachReaches()
    {
        Point other = layout.getPoint("MT368_OTHERWAY");

        assertTrue(other.isSamePlaceAs(layout.getPoint("MT368_PLAINCOPY")),
            "precondition: the second plain copy is not the same square, so it was never a candidate"
            + " and this claim cannot fail");

        assertFalse(other.isTerminus() || other.isReversing(),
            "precondition: the second copy is a turning copy, so the flags alone would have excluded"
            + " it and the approach test is not what is being checked");

        assertTrue(other.getCurrentLocomotive() != loc,
            "the train came in by MT368_ARRIVE and was stood on MT368_OTHERWAY, which is the copy of"
            + " that square belonging to the OTHER arrival side. Not a turning copy, and not one this"
            + " train could have driven onto either - the sibling has to be chosen by the approach,"
            + " not by the flags alone");
    }

    /**
     * And its tail comes with it, so the track behind it is still blocked.
     *
     * **PRV-C3: deleting `setArrivedFrom` passed every claim**, and the tail walk then silently stops
     * blocking the track behind the train - a switch this train is lying across offered to the next
     * route.
     *
     * `Point.setLocomotive` clears `arrivedFrom` on every change of occupant, because "a different
     * occupant did not come in that way". This is the same train being re-stood on a sibling copy of
     * one square, whose carriages have not moved - behaviour.md 4 - so the side has to be carried
     * over by hand, exactly as `AutonomySession.moveOntoFacingCopy` does for the turning case.
     */
    @Test(dependsOnMethods = "testDecliningTheTurnDoesNotLeaveItOnTheTurningCopy")
    public void testTheTailComesWithIt()
    {
        Point plain = layout.getPoint("MT368_PLAINCOPY");

        assertEquals(plain.getCurrentLocomotive(), loc,
            "precondition: the train is not on the plain copy, so there is no tail to check");

        assertEquals(plain.getArrivedFrom(), "W",
            "the train was re-stood on " + plain.getName() + " and arrived from "
            + plain.getArrivedFrom() + " rather than from W, which is the side the arriving edge"
            + " carries. Which side a train came in by is what the tail walk reads to block the track"
            + " behind it, and setLocomotive clears it on every change of occupant - so a re-stand"
            + " that does not carry it over leaves the switch this train is lying across free for the"
            + " next route.");
    }

    /**
     * And the route it drove comes with it, not only the side (TDR-B1).
     *
     * A train that was DRIVEN somewhere remembers the route it arrived along, and past a junction its tail
     * follows that route rather than stopping at the fork (Adam, MT-333/MT-335, 2026-09-13: *"Follow its
     * last route"*).  `standOnTheCopyItDidNotTurnOn` carried the side over, read before the move - and
     * read the route AFTER it, when moving the train off the turning copy had already cleared it.  So
     * every train that declined a turn at a may-turn square lost its route, and its tail stopped at the
     * first junction again.
     */
    @Test(dependsOnMethods = "testDecliningTheTurnDoesNotLeaveItOnTheTurningCopy")
    public void testTheRouteComesWithIt()
    {
        Point plain = layout.getPoint("MT368_PLAINCOPY");

        assertEquals(plain.getCurrentLocomotive(), loc,
            "precondition: the train is not on the plain copy, so there is no route to check");

        java.util.List<org.traincontrol.automation.Edge> along = plain.getArrivedAlong();

        assertNotNull(along,
            "the train was driven to the may-turn square, declined the turn, and was re-stood on "
            + plain.getName() + " WITHOUT the route it drove - so its tail stops at the first junction"
            + " behind it instead of following the road it came in on. Adam, MT-335: \"Follow its last"
            + " route\" (TDR-B1)");

        assertTrue(!along.isEmpty() && along.get(along.size() - 1).getEnd().isSamePlaceAs(plain),
            "the route carried onto " + plain.getName() + " does not end at that square: " + along);
    }

    /**
     * A train that cannot reverse is not turned at a square where turning is optional.
     *
     * Adam, MT-368, 2026-09-13: **"I get the prompt, but I shouldn't because the train is not
     * reversible and the station is selectable in full autonomy."**
     *
     * **The half that decides**, and the reason suppressing the prompt alone would have been worse.
     * `ManualReversalPrompt.KEEP_DIRECTION.asksAbout` answers FALSE - "no opinion" rather than "no" -
     * so `turnsOnArrival` skips the policy branch and reads `arrived.isTerminus()`, which is TRUE at
     * the turning copy of a may-turn square. A train nobody asked about would be turned every time,
     * which is MT-368's original defect arriving by a new route.
     *
     * A COMPULSORY turn is untouched: `hasAWayThrough` is what tells the two apart, and backing into a
     * terminus is how a non-reversible train gets there at all (MT-245).
     */
    @Test
    public void testATrainThatCannotReverseIsNotTurnedWhereItNeedNot()
    {
        Point may = layout.getPoint("MT368_TURNCOPY");

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertFalse(layout.turnsOnArrival(may, loc,
                org.traincontrol.gui.ManualReversalPrompt.KEEP_DIRECTION),
                "a locomotive that cannot reverse arrived at a square where turning is OPTIONAL - it"
                + " has a plain copy of its own - and was turned anyway. Adam, MT-368: \"I get the"
                + " prompt, but I shouldn't because the train is not reversible.\" KEEP_DIRECTION's"
                + " asksAbout answers false, so the policy branch is skipped and the terminus flag on"
                + " the turning copy decides - which is the flag a may-turn square shares with a real"
                + " terminus.");

            assertFalse(layout.turnsOnArrival(may, loc, answering(true, may)),
                "the same train was turned because a POLICY said to. It cannot run backwards; that is"
                + " not a preference anybody may overrule at a square it can simply drive through.");

            // AND A PLAN IS NOT AN OPERATOR (SEV-B2).
            //
            // `ALWAYS_REVERSE` is what `executePath`'s four-argument form hands in, which is autonomy
            // and the staging planner. A plan that routed this train to a turning copy searched the
            // next leg from that copy's edges; declining the turn re-stands the train on the plain
            // copy, the next leg is refused as not starting where the train is, and Return Home is
            // abandoned half-staged. The rule is about what to do when NOBODY has decided.
            assertTrue(layout.turnsOnArrival(may, loc, org.traincontrol.automation.Layout.ALWAYS_REVERSE),
                "a plan said turn here and the train was not turned, because it cannot reverse. That"
                + " rule is for the hand-driven doors: ALWAYS_REVERSE is a plan speaking, and it has"
                + " already routed the next leg from the copy it expects the train to be on.");

            // AND NO POLICY AT ALL IS READ AS THE RAILWAY DECIDING, NOT AS A DOOR ASKING (SVX-C3).
            //
            // The fence reads `reversals != null && reversals != ALWAYS_REVERSE`, and the null half
            // was unpinned: every caller under `test/` handing in null used a REVERSIBLE train, so
            // deleting `reversals != null &&` passed the whole class.
            //
            // Null is not a door that decided nothing - it is a caller with no policy object at all,
            // and `shouldReverseAt` has always read it that way in its own first line
            // (`reversals == null ||`). This fence is for the HAND-DRIVEN doors, where Adam's ruling
            // was that a train which cannot reverse is not asked and not turned; a caller that hands
            // in nothing gets the railway's own answer, exactly as `ALWAYS_REVERSE` does.
            //
            // Nothing in `src/` hands in null today: autonomy's `executePath(path, loc, speed, ttp)`
            // supplies `ALWAYS_REVERSE` and both manual doors supply the prompt. So this pins the
            // reading rather than a live path, which is the reason to write it down rather than leave
            // the conjunct looking like an oversight.
            //
            // MUTATION, run 2026-09-13: deleting `reversals != null &&` fails this claim.
            assertTrue(layout.turnsOnArrival(may, loc, null),
                "with no reversal policy at all, a train that cannot reverse was NOT turned at a"
                + " may-turn square. Null means no caller has an opinion, and the railway then"
                + " decides - the same answer ALWAYS_REVERSE gets. Reading it as \"a door that said"
                + " no\" would apply the hand-driven rule to callers that never asked anybody, and"
                + " would strand a plan the way SEV-B2 did.");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * And it still backs into a compulsory terminus, which is how it gets there at all.
     *
     * The other side of the rule above, and the one that would break if it were written as "never
     * turn a train that cannot reverse". Adam's ruling on MT-245, and
     * `testATrainThatCannotReverseMayBackIntoATerminus` in its own class.
     */
    @Test
    public void testItStillBacksIntoACompulsoryTerminus()
    {
        Point terminus = layout.getPoint("MT368_TERMINUS");

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertTrue(layout.turnsOnArrival(terminus, loc,
                org.traincontrol.gui.ManualReversalPrompt.KEEP_DIRECTION),
                "a locomotive that cannot reverse arrived at a compulsory terminus and was NOT turned."
                + " Backing in is how it gets there - the reversal is part of the journey rather than"
                + " a preference - so the rule that spares it at a may-turn square must not reach"
                + " here. hasAWayThrough is what separates the two.");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * And "turn round" at the same square turns it, so the answer is being read rather than ignored
     * in the other direction.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTurnRoundIsHonouredAtTheSameSquare() throws Exception
    {
        Point may = layout.getPoint("MT368_MAY");

        assertTrue(layout.turnsOnArrival(may, loc, answering(true, may)),
            "the operator said TURN ROUND at a square trains may turn at and the train was not"
            + " turned, so the answer is ignored in both directions rather than honoured");
    }

    /**
     * The control that keeps the fix honest: a compulsory terminus still turns, whatever is said.
     *
     * A door does not ask about one - `asksAbout` is the reversible squares MINUS the compulsory ones
     * - and the turn there is how the train gets in and how it leaves again (MT-245).
     *
     * @throws Exception from the railway
     */
    @Test
    public void testACompulsoryTerminusStillTurns() throws Exception
    {
        Point terminus = layout.getPoint("MT368_TERMINUS");

        Point may = layout.getPoint("MT368_MAY");

        assertTrue(layout.turnsOnArrival(terminus, loc, answering(false, may)),
            "a train arriving at a real terminus was not turned, so it can never leave again. The"
            + " door does not ask about a compulsory turn, and MT-245 turns on that: \"a train that"
            + " cannot reverse may back into a terminus\"");
    }

    /**
     * And an ordinary destination is not turned at all.
     *
     * Without this the claims above are satisfied by a rule that turns everything a policy is handed
     * for, which is the direction this change could most easily go wrong in.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAnOrdinaryDestinationIsNotTurned() throws Exception
    {
        Point plain = layout.getPoint("MT368_PLAIN");

        Point may = layout.getPoint("MT368_MAY");

        assertFalse(layout.turnsOnArrival(plain, loc, answering(true, may)),
            "an ordinary through platform turned the train round. Nothing about it says to: it is not"
            + " a terminus, it does not reverse, and the door was not asked about it");
    }

    /**
     * And autonomy, which hands over no policy, still turns where the flag says.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAutonomyStillTurnsAtATerminus() throws Exception
    {
        assertTrue(layout.turnsOnArrival(layout.getPoint("MT368_TERMINUS"), loc, null),
            "autonomy no longer turns a train at a terminus, so a run would leave one facing the"
            + " buffers");

        assertFalse(layout.turnsOnArrival(layout.getPoint("MT368_PLAIN"), loc, null),
            "autonomy turns a train at an ordinary platform");
    }

    /**
     * A train arriving on a railway retired on its way is not turned (RLV12-C5).  The milestones' check stops a train
     * whose railway was retired - Unload, a reload - and the arrival after the last of them went on to turn the locomotive
     * at a terminus, on a railway nothing reads any more, after the pause before the turn in which Unload could come.
     * The fold at Unload wrote the train as it was; turned afterwards, it faces the other way from that record.
     *
     * The railway is retired from the arrival's own callback, after the last check and before the turn.
     *
     * MUTATION: turn a train at its destination without asking whether its railway is still the current one, and this
     * fails.
     *
     * @throws Exception from the railway or reflection
     */
    @Test
    public void testATrainArrivingOnARailwayRetiredOnTheWayIsNotTurned() throws Exception
    {
        Point from = layout.createPoint("RLV12_FROM", true, model.newFeedback(2218, null).getName());
        Point end = layout.createPoint("RLV12_END", true, model.newFeedback(2219, null).getName());

        end.setTerminus(true);

        layout.createEdge("RLV12_FROM", "RLV12_END");

        final java.util.List<org.traincontrol.automation.Edge> path =
            java.util.Arrays.asList(layout.getEdge("RLV12_FROM", "RLV12_END"));

        assertNotNull(path.get(0), "the fixture produced no edge, so nothing below is exercised");

        assertTrue(layout.turnsOnArrival(end, loc, null), "precondition: a train arriving at the terminus is not turned");

        model.setFeedbackState(from.getS88(), true);
        model.setFeedbackState(end.getS88(), false);

        from.setLocomotive(loc);

        final boolean forward = loc.goingForward();

        final java.lang.reflect.Field version = Layout.class.getDeclaredField("version");

        version.setAccessible(true);

        final int current = version.getInt(layout);

        final int minWas = layout.getMinDelay();
        final int maxWas = layout.getMaxDelay();

        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        // RETIRED FROM THE ARRIVAL'S CALLBACK, after the last milestone's check and before the turn
        layout.setCallback("rlv12-retire", (edges, train, underWay) ->
        {
            java.util.List<Point> reached = layout.getReachedMilestones(train);

            if (Boolean.TRUE.equals(underWay) && train == loc && reached != null && reached.contains(end))
            {
                try
                {
                    version.setInt(layout, current - 1000);
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }
            }

            return null;
        });

        layout.runLocomotives();

        final Layout running = layout;
        final Locomotive driver = loc;

        Thread run = new Thread(() -> running.executePath(path, driver, 20, null), "arriving on a retired railway");

        run.setDaemon(true);
        run.start();

        try
        {
            assertTrue(waitFor(() -> running.isRunning() && driver.getSpeed() > 0, 15000),
                "the train never set off, so no arrival happened and this claim tested nothing");

            model.setFeedbackState(end.getS88(), true);
            model.setFeedbackState(from.getS88(), false);

            assertTrue(waitFor(() -> !run.isAlive(), 30000), "the run never finished after the destination's sensor went"
                + " on, so the arrival this claim is about was never reached");

            assertTrue(version.getInt(layout) != current, "precondition: the arrival's callback never retired the"
                + " railway, so this claim tested nothing");
        }
        finally
        {
            // Put down, as a callback is: the map holds no nulls
            layout.setCallback("rlv12-retire", (edges, train, underWay) -> null);

            version.setInt(layout, current);

            layout.stopLocomotives();

            run.interrupt();

            layout.setMinDelay(minWas);
            layout.setMaxDelay(maxWas);
        }

        assertEquals(loc.goingForward(), forward, "a train arriving at a terminus on a railway retired on its way was"
            + " turned - after Unload wrote where it stood and which way it faced (RLV12-C5)");

        assertEquals(layout.turnedOnArrivalAt(loc.getName()), null, "a turn on a retired railway was recorded as owed"
            + " (RLV12-C5)");
    }

    /**
     * A train that kept its direction is stood on the plain copy without the square ever reading empty (RSA-C3).  The
     * re-stand after a declined turn took the train off the turning copy and then put it on the plain one, so for a moment
     * the square held nobody and the train was nowhere - and the platform's exit-guard signal, which follows the square,
     * was commanded GREEN and then RED again over the standing train.  It is put on the plain copy, with the side and the
     * road it came in by, before it is taken off the turning one.
     *
     * MUTATION: take the train off the turning copy before putting it on the plain one, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheReStandNeverShowsTheSquareEmpty() throws Exception
    {
        MarklinFeedback ms = model.newFeedback(1961, null);
        MarklinFeedback mm = model.newFeedback(1962, null);

        model.setFeedbackState(ms.getName(), false);
        model.setFeedbackState(mm.getName(), false);

        final org.traincontrol.base.Accessory guard = model.newSignal(293,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2, false);

        final Locomotive x = model.getLocByName(model.getLocList().get(6));

        final Integer lengthWas = x.getTrainLength();

        final Layout rail = new Layout(model);

        rail.createPoint("RSMS", true, ms.getName());
        rail.createPoint("RSMT", true, mm.getName());
        rail.createPoint("RSMP", true, mm.getName());
        rail.getPoint("RSMT").setTerminus(true);
        rail.getPoint("RSMT").setBlock("RSM");
        rail.getPoint("RSMP").setBlock("RSM");
        rail.getPoint("RSMT").setProtectingSignal(guard.getName());
        rail.getPoint("RSMP").setProtectingSignal(guard.getName());
        rail.createEdge("RSMS", "RSMT");
        rail.createEdge("RSMS", "RSMP");

        // The side the train comes in by, as the builder records it, so the arrival has one to carry across
        rail.getEdge("RSMS", "RSMT").setEntrySide("W");
        rail.makeCurrent();

        final Point turning = rail.getPoint("RSMT");
        final Point plain = rail.getPoint("RSMP");

        // THE OPERATOR SAID KEEP THE DIRECTION: asked about the turning copy, and no
        final Layout.ReversalPolicy keep = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive loc, Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == turning;
            }
        };

        final java.util.concurrent.atomic.AtomicBoolean arriving = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.List<String> atTheArrival = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // Every signal command sent once the train reaches its sensor, with who stands on the square as it goes out
        model.setSentMessageObserver(m ->
        {
            if (!arriving.get() || m == null || m.getCommand() == null
                || m.getCommand() != org.traincontrol.marklin.udp.CS2Message.CMD_ACC_SWITCH) return;

            byte[] data = m.getData();

            atTheArrival.add((data != null && data.length > 4 && data[4] == 0 ? "RED" : "GREEN") + " with "
                + (turning.getCurrentLocomotive() == null ? "nobody" : "the train") + " on the turning copy and "
                + (plain.getCurrentLocomotive() == null ? "nobody" : "the train") + " on the plain one");
        });

        Thread journey = null;

        try
        {
            x.setSpeed(0);
            x.setTrainLength(2);

            rail.getPoint("RSMS").setLocomotive(x);

            journey = new Thread(() -> rail.executePath(java.util.Arrays.asList(rail.getEdge("RSMS", "RSMT")), x, 30, null,
                keep), "restand-claim");

            journey.setDaemon(true);
            journey.start();

            long until = System.currentTimeMillis() + 10000;

            while (!(x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x)) && System.currentTimeMillis() < until)
            {
                Thread.sleep(20);
            }

            assertTrue(rail.getActiveLocomotives().containsKey(x), "precondition: the train was not sent to the turning"
                + " copy: " + Layout.getLastError());

            // It arrives, and is stood on the plain copy
            arriving.set(true);

            model.setFeedbackState(mm.getName(), true);

            journey.join(10000);

            assertEquals(plain.getCurrentLocomotive(), x, "precondition: the train was not stood on the plain copy, so"
                + " the re-stand never ran");

            assertFalse(atTheArrival.stream().anyMatch(command -> command.startsWith("GREEN")), "the re-stand commanded"
                + " the platform's exit guard GREEN over the standing train, the square reading empty for a moment"
                + " (RSA-C3): " + atTheArrival);

            assertEquals(plain.getArrivedFrom(), "W", "the plain copy was given the train without the side it came in by,"
                + " so its tail is nowhere (RSA-C3)");
        }
        finally
        {
            model.setSentMessageObserver(null);
            model.setFeedbackState(mm.getName(), false);
            model.setFeedbackState(ms.getName(), false);

            if (journey != null) journey.join(5000);

            x.setSpeed(0);
            x.setTrainLength(lengthWas);
        }
    }

    /**
     * A train sent by hand to a square it may turn at, and told to keep its direction, is drawn facing the way it will
     * stand from the moment it stops - not the turning copy's way until its run is done (Adam, 2026-10-07, on MT-701:
     * *"the locomotive icon direction doesn't always match the arrow (arrow is correct) when arriving at a may reverse
     * station ... the icon at BottomMainB initially faces west and then flips to the correct east only after arrival"*).
     *
     * The path ends on the square's TURNING copy, and the train is stood on the plain copy only once the run's end has
     * run - a second or two later, with the default delay.  The caption's arrow asks `copyItWillStandOn` (OB-314); the
     * icon read the copy the train held (OB-317), so it faced the turning copy's way, west, until the re-stand.  The end
     * is held open here with the route-end callback, which is where that delay sits.
     *
     * MUTATION: take the copy it will stand on out of `DiagramMonitor.markTrain`, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheIconFacesTheWayTheTrainWillStand() throws Exception
    {
        MarklinFeedback ms = model.newFeedback(1971, null);
        MarklinFeedback mm = model.newFeedback(1972, null);

        model.setFeedbackState(ms.getName(), false);
        model.setFeedbackState(mm.getName(), false);

        final Locomotive x = model.getLocByName(model.getLocList().get(6));

        final Integer lengthWas = x.getTrainLength();

        final Layout rail = new Layout(model);

        rail.createPoint("ICFS", true, ms.getName());
        rail.createPoint("ICFT", true, mm.getName());
        rail.createPoint("ICFP", true, mm.getName());
        rail.getPoint("ICFT").setTerminus(true);
        rail.getPoint("ICFT").setBlock("ICF");
        rail.getPoint("ICFP").setBlock("ICF");
        rail.createEdge("ICFS", "ICFT");
        rail.createEdge("ICFS", "ICFP");
        rail.getEdge("ICFS", "ICFT").setEntrySide("W");
        rail.makeCurrent();

        final Point turning = rail.getPoint("ICFT");
        final Point plain = rail.getPoint("ICFP");

        // THE OPERATOR SAID KEEP THE DIRECTION: asked about the turning copy, and no
        final Layout.ReversalPolicy keep = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive loc, Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == turning;
            }
        };

        // The diagram: the start on one square, both copies of the destination on another - the turning copy facing back
        // the way trains come in, the plain one onward
        final org.traincontrol.automationui.TileGraph.TileKey square =
            new org.traincontrol.automationui.TileGraph.TileKey("main", 5, 1);

        java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> tiles = new java.util.LinkedHashMap<>();

        tiles.put("ICFS", new org.traincontrol.automationui.TileGraph.TileKey("main", 1, 1));
        tiles.put("ICFT", square);
        tiles.put("ICFP", square);

        java.util.Map<String, org.traincontrol.automationui.TilePorts.Side> facings = new java.util.LinkedHashMap<>();

        facings.put("ICFS", org.traincontrol.automationui.TilePorts.Side.E);
        facings.put("ICFT", org.traincontrol.automationui.TilePorts.Side.W);
        facings.put("ICFP", org.traincontrol.automationui.TilePorts.Side.E);

        final org.traincontrol.automationui.DiagramMonitor monitor = new org.traincontrol.automationui.DiagramMonitor(
            () -> rail, new java.util.LinkedHashMap<String, org.traincontrol.automationui.GraphReducer.ReducedEdge>(), tiles,
            overlays -> { });

        monitor.setFacings(facings);

        // THE ROUTE'S END HELD OPEN, the train stopped on the turning copy
        final java.util.function.Consumer<Locomotive> wasEnd = x.hasCallback(Layout.CB_ROUTE_END)
            ? x.getCallback(Layout.CB_ROUTE_END) : null;

        final java.util.concurrent.CountDownLatch atTheEnd = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch letGo = new java.util.concurrent.CountDownLatch(1);

        Thread journey = null;

        try
        {
            x.setCallback(Layout.CB_ROUTE_END, l ->
            {
                atTheEnd.countDown();

                try
                {
                    letGo.await(10, java.util.concurrent.TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            });

            x.setSpeed(0);
            x.setTrainLength(2);

            rail.getPoint("ICFS").setLocomotive(x);

            journey = new Thread(() -> rail.executePath(java.util.Arrays.asList(rail.getEdge("ICFS", "ICFT")), x, 30, null,
                keep), "icon-facing-claim");

            journey.setDaemon(true);
            journey.start();

            long until = System.currentTimeMillis() + 10000;

            while (!(x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x)) && System.currentTimeMillis() < until)
            {
                Thread.sleep(20);
            }

            assertTrue(rail.getActiveLocomotives().containsKey(x), "precondition: the train was not sent to the turning"
                + " copy: " + Layout.getLastError());

            model.setFeedbackState(mm.getName(), true);

            assertTrue(atTheEnd.await(10, java.util.concurrent.TimeUnit.SECONDS), "precondition: the run never reached its"
                + " end");

            assertEquals(turning.getCurrentLocomotive(), x, "precondition: the train is not on the turning copy while its"
                + " run ends, so there is nothing here to draw the wrong way");

            assertEquals(x.getSpeed(), 0, "precondition: the train has not stopped");

            monitor.refresh();

            org.traincontrol.automationui.TileOverlay stopped = monitor.getPublished().get(square);

            assertTrue(stopped != null && stopped.hasTrain() && stopped.isParked(), "precondition: the stopped train is"
                + " not drawn standing on its square: " + monitor.getPublished());

            assertEquals(stopped.getFacing(), org.traincontrol.automationui.TilePorts.Side.E, "the stopped train's icon"
                + " faces the turning copy's way while its run ends, though it kept its direction and the arrow says the"
                + " plain copy's (Adam, 2026-10-07, on MT-701)");

            // AND ONCE IT IS STOOD ON THE PLAIN COPY: the same way, no flip
            letGo.countDown();

            journey.join(10000);

            assertEquals(plain.getCurrentLocomotive(), x, "precondition: the train was not stood on the plain copy");

            monitor.refresh();

            org.traincontrol.automationui.TileOverlay standing = monitor.getPublished().get(square);

            assertTrue(standing != null && standing.isParked(), "the standing train is not drawn: "
                + monitor.getPublished());

            assertEquals(standing.getFacing(), org.traincontrol.automationui.TilePorts.Side.E, "the train stood on the"
                + " plain copy faces the other way");
        }
        finally
        {
            letGo.countDown();

            // No way to remove a callback: an absent one is put back as one that does nothing
            x.setCallback(Layout.CB_ROUTE_END, wasEnd != null ? wasEnd : l -> { });

            model.setFeedbackState(mm.getName(), false);
            model.setFeedbackState(ms.getName(), false);

            if (journey != null) journey.join(5000);

            x.setSpeed(0);
            x.setTrainLength(lengthWas);
        }
    }

    /**
     * A train told to keep its direction at a square it may turn at is turned all the same where no copy of that square
     * faces on from the way it came (RSA49-A1): the end of a line marked "Trains May Change Direction Here", or a may-turn
     * square one of whose ways in has no way on.
     *
     * Kept, the train stood on the turning copy - the only copy the build makes for that approach, facing back out - with
     * its decoder still driving it forward: its next journey set the route back the way it came and drove it into the end
     * of the line, over track nothing held.  REG6-A1 took the question away from squares marked "must"; a square marked
     * "may" where nothing faces on is a compulsory turn in all but its flag.  So is it for a train that cannot reverse,
     * which a dead end turns already (SVV-C2, Adam's MT-245 ruling): the model and the decoder agree, which is what keeps
     * the next journey on its path.
     *
     * Two shapes - no other copy at all, and a plain copy the train's approach cannot reach - each sent with the answer
     * keep, and the second with a train that cannot reverse as well.
     *
     * MUTATION: take the compulsory turn out of `Layout.turnsOnArrival`, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testKeepingTheDirectionWhereNoCopyFacesOnStillTurnsTheTrain() throws Exception
    {
        MarklinFeedback s1 = model.newFeedback(1981, null);
        MarklinFeedback t1 = model.newFeedback(1982, null);
        MarklinFeedback s2 = model.newFeedback(1983, null);
        MarklinFeedback t2 = model.newFeedback(1984, null);
        MarklinFeedback q2 = model.newFeedback(1985, null);

        for (MarklinFeedback f : new MarklinFeedback[] {s1, t1, s2, t2, q2}) model.setFeedbackState(f.getName(), false);

        final Locomotive x = model.getLocByName(model.getLocList().get(6));

        final Integer lengthWas = x.getTrainLength();
        final boolean reversibleWas = x.isReversible();

        final Layout rail = new Layout(model);

        // THE END OF A LINE: one copy, the turning one
        rail.createPoint("NCF1S", true, s1.getName());
        rail.createPoint("NCF1T", true, t1.getName());
        rail.getPoint("NCF1T").setTerminus(true);
        rail.getPoint("NCF1T").setBlock("NCF1");
        rail.createEdge("NCF1S", "NCF1T");
        rail.getEdge("NCF1S", "NCF1T").setEntrySide("W");

        // A PLAIN COPY, but only for trains from the other way
        rail.createPoint("NCF2S", true, s2.getName());
        rail.createPoint("NCF2Q", true, q2.getName());
        rail.createPoint("NCF2T", true, t2.getName());
        rail.createPoint("NCF2P", true, t2.getName());
        rail.getPoint("NCF2T").setTerminus(true);
        rail.getPoint("NCF2T").setBlock("NCF2");
        rail.getPoint("NCF2P").setBlock("NCF2");
        rail.createEdge("NCF2S", "NCF2T");
        rail.createEdge("NCF2Q", "NCF2P");
        rail.getEdge("NCF2S", "NCF2T").setEntrySide("W");
        rail.getEdge("NCF2Q", "NCF2P").setEntrySide("E");
        rail.makeCurrent();

        try
        {
            x.setTrainLength(2);

            assertTurnedKeeping(rail, x, "NCF1S", "NCF1T", t1, "the end of a line marked may");

            assertTurnedKeeping(rail, x, "NCF2S", "NCF2T", t2, "a may-turn square whose plain copy this approach cannot"
                + " reach");

            x.setReversible(false);

            assertTurnedKeeping(rail, x, "NCF2S", "NCF2T", t2, "the same, with a train that cannot reverse");
        }
        finally
        {
            for (MarklinFeedback f : new MarklinFeedback[] {s1, t1, s2, t2, q2}) model.setFeedbackState(f.getName(), false);

            x.setSpeed(0);
            x.setTrainLength(lengthWas);
            x.setReversible(reversibleWas);
        }
    }

    /**
     * Sends a train from one Point to the next with the answer keep, asked about the destination, and asserts it was
     * turned there.
     */
    private static void assertTurnedKeeping(Layout rail, Locomotive x, String from, String to, MarklinFeedback arrive,
        String shape) throws Exception
    {
        final Point end = rail.getPoint(to);

        model.setFeedbackState(arrive.getName(), false);

        x.setSpeed(0);

        rail.getPoint(from).setLocomotive(x);

        final boolean forward = x.goingForward();

        final Layout.ReversalPolicy keep = answering(false, end);

        final java.util.List<org.traincontrol.automation.Edge> path = java.util.Arrays.asList(rail.getEdge(from, to));

        Thread journey = new Thread(() -> rail.executePath(path, x, 30, null, keep), "no-copy-faces-on-claim");

        journey.setDaemon(true);
        journey.start();

        assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
            "precondition (" + shape + "): the train was not sent: " + Layout.getLastError());

        model.setFeedbackState(arrive.getName(), true);

        journey.join(15000);

        assertFalse(journey.isAlive(), "precondition (" + shape + "): the journey did not end");

        assertEquals(end.getCurrentLocomotive(), x, "precondition (" + shape + "): the train is not on the copy it was"
            + " sent to");

        assertEquals(x.goingForward(), !forward, shape + ": the train was kept facing on where no copy of the square faces"
            + " on from its approach, so it stands on the copy facing back out with its decoder still driving it forward,"
            + " and its next journey drives it into the end of the line (RSA49-A1)");

        model.setFeedbackState(arrive.getName(), false);
    }

    /**
     * The operator is not asked about a destination where no copy faces on from the train's approach, since the answer
     * could not be honoured there (RSA49-A1) - and still is where one does.
     *
     * Asked of `ManualReversalPrompt.destinationAskedAbout`, the part of the question that decides whether to ask, because
     * asking puts a modal dialog up.
     *
     * MUTATION: ask about such a destination again, and this fails.
     */
    @Test
    public void testNobodyIsAskedWhereTheAnswerCouldNotBeHonoured() throws Exception
    {
        MarklinFeedback s = model.newFeedback(1991, null);
        MarklinFeedback t = model.newFeedback(1992, null);
        MarklinFeedback q = model.newFeedback(1993, null);

        final Layout rail = new Layout(model);

        rail.createPoint("NAS", true, s.getName());
        rail.createPoint("NAQ", true, q.getName());
        rail.createPoint("NAT", true, t.getName());
        rail.createPoint("NAP", true, t.getName());
        rail.getPoint("NAT").setTerminus(true);
        rail.getPoint("NAT").setBlock("NA");
        rail.getPoint("NAP").setBlock("NA");
        rail.createEdge("NAS", "NAT");
        rail.createEdge("NAQ", "NAT");
        rail.createEdge("NAQ", "NAP");

        // The setup's answer: every square is one the operator may be asked about
        final Layout.ReversalPolicy everywhere = new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return false;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at != null;
            }
        };

        assertEquals(org.traincontrol.gui.ManualReversalPrompt.destinationAskedAbout(everywhere, rail,
            java.util.Arrays.asList(rail.getEdge("NAS", "NAT"))), null, "the operator is asked whether to keep the"
            + " direction at a square no copy of which faces on from the train's approach - an answer the arrival cannot"
            + " honour (RSA49-A1)");

        assertEquals(org.traincontrol.gui.ManualReversalPrompt.destinationAskedAbout(everywhere, rail,
            java.util.Arrays.asList(rail.getEdge("NAQ", "NAT"))), rail.getPoint("NAT"), "the operator is no longer asked"
            + " at a may-turn square whose plain copy the train's approach reaches");

        assertTrue(rail.noCopyFacesOnFrom(rail.getPoint("NAT"), rail.getPoint("NAS")), "a turning copy with no plain copy"
            + " this approach reaches was not found to have none");

        assertFalse(rail.noCopyFacesOnFrom(rail.getPoint("NAT"), rail.getPoint("NAQ")), "a turning copy whose plain copy"
            + " this approach reaches was found to have none");

        assertFalse(rail.noCopyFacesOnFrom(rail.getPoint("NAP"), rail.getPoint("NAQ")), "a plain copy was found to be one"
            + " nothing faces on from");
    }

    /**
     * A timetable recorded by hand through a square trains may turn at plays back what was decided there (RSA49-B1).
     *
     * The operator kept the direction, the hand path ended on the square's turning copy, and the train was stood on the
     * plain copy; the next entry was recorded from there.  Playback ran every entry as autonomy does, turning wherever the
     * route ends on a turning copy - so it turned the train where the recording had not, and the next entry, which starts
     * on the plain copy, never started: three minutes later the run stopped saying the track never became free.  The
     * entry now keeps the answer the arrival was given, and is played back with it.
     *
     * MUTATION: record no answer, or play every entry back as autonomy again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATimetableRecordedByHandPlaysBackTheAnswer() throws Exception
    {
        MarklinFeedback s = model.newFeedback(2001, null);
        MarklinFeedback m = model.newFeedback(2002, null);
        MarklinFeedback c = model.newFeedback(2003, null);

        for (MarklinFeedback f : new MarklinFeedback[] {s, m, c}) model.setFeedbackState(f.getName(), false);

        final Locomotive x = model.getLocByName(model.getLocList().get(6));

        final Integer lengthWas = x.getTrainLength();

        // Playback drives an entry at the train's preferred speed, and one with none is skipped (SG-A5)
        final int speedWas = x.getPreferredSpeed();

        final Layout rail = new Layout(model);

        rail.setMaxDelay(1);
        rail.setMinDelay(1);

        rail.createPoint("TBS", true, s.getName());
        rail.createPoint("TBT", true, m.getName());
        rail.createPoint("TBP", true, m.getName());
        rail.createPoint("TBC", true, c.getName());
        rail.getPoint("TBT").setTerminus(true);
        rail.getPoint("TBT").setBlock("TB");
        rail.getPoint("TBP").setBlock("TB");
        rail.createEdge("TBS", "TBT");
        rail.createEdge("TBS", "TBP");
        rail.createEdge("TBP", "TBC");
        rail.getEdge("TBS", "TBT").setEntrySide("W");
        rail.makeCurrent();

        final Point turning = rail.getPoint("TBT");
        final Point plain = rail.getPoint("TBP");
        final Point onward = rail.getPoint("TBC");

        final long stuckWas = Layout.TIMETABLE_STUCK_MS;

        try
        {
            x.setSpeed(0);
            x.setTrainLength(2);
            x.setPreferredSpeed(30);

            rail.getPoint("TBS").setLocomotive(x);

            final boolean forward = x.goingForward();

            // RECORDED BY HAND: kept at the turning copy and stood on the plain one, then on from there
            rail.setTimetableCapture(true);

            sendByHand(rail, x, java.util.Arrays.asList(rail.getEdge("TBS", "TBT")), answering(false, turning), m);

            assertEquals(plain.getCurrentLocomotive(), x, "precondition: the kept train was not stood on the plain copy");

            sendByHand(rail, x, java.util.Arrays.asList(rail.getEdge("TBP", "TBC")),
                org.traincontrol.gui.ManualReversalPrompt.KEEP_DIRECTION, c);

            assertEquals(onward.getCurrentLocomotive(), x, "precondition: the second send did not arrive");

            rail.setTimetableCapture(false);

            assertEquals(rail.getTimetable().size(), 2, "precondition: the two sends were not recorded: "
                + rail.getTimetable());

            assertEquals(rail.getTimetable().get(0).getTurnAtTheEnd(), Boolean.FALSE, "the first entry does not keep the"
                + " answer its arrival was given, so playback cannot give it again (RSA49-B1)");

            // AND IT IS SAVED WITH IT: the timetable is written to the configuration and read back at the next start
            assertEquals(org.traincontrol.automation.TimetablePath.fromJSON(rail.getTimetable().get(0).toJSON().toString(),
                model, rail).getTurnAtTheEnd(), Boolean.FALSE, "the answer is lost when the timetable is saved and read"
                + " back (RSA49-B1)");

            // BACK TO THE START, facing as it did when the recording began, and played back
            x.setSpeed(0);

            rail.getPoint("TBS").setLocomotive(x);

            if (x.goingForward() != forward) x.switchDirection();

            for (org.traincontrol.automation.TimetablePath entry : rail.getTimetable())
            {
                entry.setSecondsToNext(0);
                entry.setExecutionTime(0);
            }

            Layout.TIMETABLE_STUCK_MS = 8000;

            Thread runner = new Thread(rail::executeTimetable, "recorded-answer-playback");

            runner.setDaemon(true);
            runner.start();

            // THE FIRST ENTRY: to the square, and kept as recorded
            assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                "precondition: the first entry did not start: " + Layout.getLastError());

            model.setFeedbackState(m.getName(), true);

            assertTrue(waitFor(() -> plain.getCurrentLocomotive() == x || (turning.getCurrentLocomotive() == x
                && !rail.getActiveLocomotives().containsKey(x)), 15000), "precondition: the first entry did not end");

            model.setFeedbackState(m.getName(), false);

            assertEquals(x.goingForward(), forward, "playback turned the train where the recording kept its direction"
                + " (RSA49-B1)");

            assertEquals(plain.getCurrentLocomotive(), x, "playback left the train on the turning copy, where the recording"
                + " had it stood on the plain one, so the next entry cannot start (RSA49-B1)");

            // THE SECOND ENTRY, from the plain copy, as recorded
            assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                "the second entry, recorded from the plain copy, never started (RSA49-B1): " + Layout.getLastError());

            model.setFeedbackState(c.getName(), true);

            assertTrue(waitFor(() -> onward.getCurrentLocomotive() == x && !rail.getActiveLocomotives().containsKey(x),
                15000), "the second entry did not arrive");
        }
        finally
        {
            Layout.TIMETABLE_STUCK_MS = stuckWas;

            rail.stopLocomotives();
            rail.setTimetableCapture(false);

            for (MarklinFeedback f : new MarklinFeedback[] {s, m, c}) model.setFeedbackState(f.getName(), false);

            x.setSpeed(0);
            x.setTrainLength(lengthWas);
            x.setPreferredSpeed(speedWas);
        }
    }

    /**
     * Sends a train by hand along a path with the answer given, and plays its arrival sensor.
     */
    private static void sendByHand(Layout rail, Locomotive x, java.util.List<org.traincontrol.automation.Edge> path,
        Layout.ReversalPolicy answer, MarklinFeedback arrive) throws Exception
    {
        Thread journey = new Thread(() -> rail.executePath(path, x, 30, null, answer), "recorded-by-hand");

        journey.setDaemon(true);
        journey.start();

        assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
            "precondition: the hand send did not start: " + Layout.getLastError());

        model.setFeedbackState(arrive.getName(), true);

        journey.join(15000);

        assertFalse(journey.isAlive(), "precondition: the hand send did not end");

        model.setFeedbackState(arrive.getName(), false);
    }

    /**
     * A train that cannot reverse is not offered by hand the turning copy of a may-turn square when no way into that copy
     * has a copy facing on (RSA49-A1): it would be turned there and leave backwards, which is what Adam's MT-367 ruling
     * keeps it from being sent to - the rule asked of the copy's ways in, where it was asked of the square alone, and the
     * square has a way through for trains from the other side.
     *
     * MUTATION: ask the square alone again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainThatCannotReverseIsNotOfferedATurnItCannotAvoid() throws Exception
    {
        MarklinFeedback s = model.newFeedback(2011, null);
        MarklinFeedback t = model.newFeedback(2012, null);
        MarklinFeedback q = model.newFeedback(2013, null);

        final Locomotive x = model.getLocByName(model.getLocList().get(6));

        final boolean reversibleWas = x.isReversible();

        final Layout rail = new Layout(model);

        rail.createPoint("NOS", true, s.getName());
        rail.createPoint("NOQ", true, q.getName());
        rail.createPoint("NOT", true, t.getName());
        rail.createPoint("NOP", true, t.getName());
        rail.getPoint("NOT").setTerminus(true);
        rail.getPoint("NOT").setBlock("NO");
        rail.getPoint("NOP").setBlock("NO");
        rail.createEdge("NOS", "NOT");
        rail.createEdge("NOQ", "NOP");

        try
        {
            x.setReversible(false);

            assertTrue(rail.hasAWayThrough(rail.getPoint("NOT")), "precondition: the square has no way through, so the"
                + " square's own rule would bar it and this asks nothing new");

            assertFalse(rail.isOfferableToOperator(rail.getPoint("NOT"), x), "a train that cannot reverse is offered the"
                + " turning copy of a may-turn square whose only way in has no copy facing on - it would be turned there"
                + " and leave backwards (RSA49-A1, MT-367)");

            assertTrue(rail.isOfferableToOperator(rail.getPoint("NOP"), x), "the plain copy, which it can drive through,"
                + " is not offered to it");

            x.setReversible(true);

            assertTrue(rail.isOfferableToOperator(rail.getPoint("NOT"), x), "a train that can reverse is not offered it");
        }
        finally
        {
            x.setReversible(reversibleWas);
        }
    }
}
