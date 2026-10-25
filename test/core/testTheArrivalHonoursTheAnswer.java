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
 * The arrival once read `arrived.isTerminus() || shouldReverseAt(...)`, and `AutonomyBuilder` emits the turning copy of
 * a MAY-turn square with `terminus: true`, so the dialog's answer was collected and thrown away. The decision is now
 * `Layout.turnsOnArrival`, and this class asks it what it DOES, not what its source says.
 *
 * MUTATION: restore the `||` and the first claim fails; drop the `asksAbout` branch and it fails too; make the branch
 * answer `true` and the "keep direction" claim fails.
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
                // Independent of the ANSWER, which is the contract `ReversalPolicy` spells out.
                return at == askedAbout;
            }
        };
    }

    /** A bounded wait rather than a sleep: the railway's timings are its own. */
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
        // BEFORE the model: `init` reads the machine-global layout preference and would otherwise open Adam's real
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();

        layout = new Layout(model);

        MarklinFeedback a = model.newFeedback(2210, null);
        MarklinFeedback b = model.newFeedback(2211, null);
        MarklinFeedback c = model.newFeedback(2212, null);

        // A MAY-TURN SQUARE AS THE BUILDER EMITS ONE: the turning copy carries the terminus flag, which is the whole of
        // the confusion.
        layout.createPoint("MT368_MAY", true, a.getName());
        layout.createPoint("MT368_TERMINUS", true, b.getName());
        layout.createPoint("MT368_PLAIN", true, c.getName());

        layout.getPoint("MT368_MAY").setTerminus(true);
        layout.getPoint("MT368_TERMINUS").setTerminus(true);

        // A square as the builder really emits one: both copies, tied by a block. A may-turn square is routinely
        // reached on its TURNING copy, and where the train is left standing needs the other copy to exist.
        layout.createPoint("MT368_ARRIVE", true, model.newFeedback(2213, null).getName());
        layout.createPoint("MT368_TURNCOPY", true, model.newFeedback(2214, null).getName());
        layout.createPoint("MT368_PLAINCOPY", true, model.newFeedback(2215, null).getName());

        // One square, two copies.
        layout.getPoint("MT368_TURNCOPY").setBlock("MT368_BLOCK");
        layout.getPoint("MT368_PLAINCOPY").setBlock("MT368_BLOCK");

        // The turning copy carries the terminus flag, exactly as the builder writes it.
        layout.getPoint("MT368_TURNCOPY").setTerminus(true);

        layout.createEdge("MT368_ARRIVE", "MT368_TURNCOPY");

        // The arriving edge knows which side it comes in by: a hand-built Point has no compass geometry to fall back
        // on, and without a side the tail has nothing to carry over.
        layout.getEdge("MT368_ARRIVE", "MT368_TURNCOPY").setEntrySide("W");

        // The plain copy is reached by the same approach, as the builder emits them.
        layout.createEdge("MT368_ARRIVE", "MT368_PLAINCOPY");

        // A second plain copy, for the other arrival side: with one, "not a turning copy" and "the copy this approach
        // reaches" are the same Point and a claim cannot tell the rule from the accident. A train that came in by
        // MT368_ARRIVE must not be stood on it.
        layout.createPoint("MT368_OTHERWAY", true, model.newFeedback(2216, null).getName());
        layout.createPoint("MT368_OTHERAPPROACH", true, model.newFeedback(2217, null).getName());

        layout.getPoint("MT368_OTHERWAY").setBlock("MT368_BLOCK");

        layout.createEdge("MT368_OTHERAPPROACH", "MT368_OTHERWAY");

        loc = model.getLocByName(model.getLocList().get(0));
    }

    /** Makes the class's railway current again: a claim that builds a railway of its own retires every other one. */
    @AfterMethod(alwaysRun = true)
    public void theClassRailwayIsCurrentAgain()
    {
        if (layout != null) layout.makeCurrent();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        // The Points belong to a Layout this class made; there is nothing on the real railway to put back.
        if (sandbox != null) sandbox.close();
    }

    /** "Keep direction" at a may-turn square keeps the direction. */
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
     * The path offered to a may-turn square routinely ends on the TURNING copy (`StationIndex.distinctDestinations`
     * keeps the first path per square), so a train that declines the turn would face one way while the Point it stands
     * on says the other, and its next dispatch would read the wrong outgoing edges. The mirror case, a train that DOES
     * turn, is re-stood by `AutonomySession.faceTheWayItCameIn`.
     *
     * Driven through `executePath` rather than by asking the rule, so the call site is covered.
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

        // On another thread, with the sensor fired by hand: `executePath` blocks until the destination's feedback goes
        // on, and there is no Central Station in this class.
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

            // On its way the railway already knows the copy it will stand on (OB-314), not the turning copy its path
            // ends on.
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
     * It is stood on the copy ITS OWN approach reaches, not merely one that is not a turning copy; asserted in the same
     * arrival as the claim above.
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
     * And its tail comes with it, so the track behind stays blocked: `Point.setLocomotive` clears `arrivedFrom` on
     * every change of occupant, so the side is carried over by hand, as `AutonomySession.moveOntoFacingCopy` does.
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
     * And the route it drove comes with it, so its tail follows that route past a junction (*"Follow its last route"*,
     * MT-335): it is read before the move, which clears it.
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
     * A train that cannot reverse is not turned at a square where turning is optional (MT-368).
     *
     * `ManualReversalPrompt.KEEP_DIRECTION.asksAbout` answers FALSE, "no opinion", so `turnsOnArrival` would fall
     * through to `arrived.isTerminus()`, TRUE at the turning copy of a may-turn square; suppressing the prompt alone
     * would turn every such train. A COMPULSORY turn is untouched (MT-245): `hasAWayThrough` tells the two apart.
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
                org.traincontrol.gui.autonomy.ManualReversalPrompt.KEEP_DIRECTION),
                "a locomotive that cannot reverse arrived at a square where turning is OPTIONAL - it"
                + " has a plain copy of its own - and was turned anyway. Adam, MT-368: \"I get the"
                + " prompt, but I shouldn't because the train is not reversible.\" KEEP_DIRECTION's"
                + " asksAbout answers false, so the policy branch is skipped and the terminus flag on"
                + " the turning copy decides - which is the flag a may-turn square shares with a real"
                + " terminus.");

            assertFalse(layout.turnsOnArrival(may, loc, answering(true, may)),
                "the same train was turned because a POLICY said to. It cannot run backwards; that is"
                + " not a preference anybody may overrule at a square it can simply drive through.");

            // A plan is not an operator: `ALWAYS_REVERSE` is what `executePath`'s four-argument form hands in
            // (autonomy, the staging planner), and declining the turn there would abandon Return Home half-staged.
            assertTrue(layout.turnsOnArrival(may, loc, org.traincontrol.automation.Layout.ALWAYS_REVERSE),
                "a plan said turn here and the train was not turned, because it cannot reverse. That"
                + " rule is for the hand-driven doors: ALWAYS_REVERSE is a plan speaking, and it has"
                + " already routed the next leg from the copy it expects the train to be on.");

            // No policy at all is read as the railway deciding, not a door asking: the fence reads `reversals != null
            // && reversals != ALWAYS_REVERSE`, and `shouldReverseAt` reads null the same way. Nothing in `src/` hands
            // in null today, so this pins the reading, not a live path.
            //
            // MUTATION: deleting `reversals != null &&` fails this claim.
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
     * And it still backs into a compulsory terminus, which is how it gets there at all (MT-245, and
     * `testATrainThatCannotReverseMayBackIntoATerminus`).
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
                org.traincontrol.gui.autonomy.ManualReversalPrompt.KEEP_DIRECTION),
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

    /** And "turn round" at the same square turns it, so the answer is read in both directions. */
    @Test
    public void testTurnRoundIsHonouredAtTheSameSquare() throws Exception
    {
        Point may = layout.getPoint("MT368_MAY");

        assertTrue(layout.turnsOnArrival(may, loc, answering(true, may)),
            "the operator said TURN ROUND at a square trains may turn at and the train was not"
            + " turned, so the answer is ignored in both directions rather than honoured");
    }

    /**
     * The control: a compulsory terminus still turns whatever is said, since `asksAbout` is the reversible squares
     * MINUS the compulsory ones (MT-245).
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

    /** And an ordinary destination is not turned at all. */
    @Test
    public void testAnOrdinaryDestinationIsNotTurned() throws Exception
    {
        Point plain = layout.getPoint("MT368_PLAIN");

        Point may = layout.getPoint("MT368_MAY");

        assertFalse(layout.turnsOnArrival(plain, loc, answering(true, may)),
            "an ordinary through platform turned the train round. Nothing about it says to: it is not"
            + " a terminus, it does not reverse, and the door was not asked about it");
    }

    /** And autonomy, which hands over no policy, still turns where the flag says. */
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
     * A train arriving on a railway retired on its way is not turned: the milestones' check stops such a train, but
     * Unload could land in the pause between the last check and the turn, leaving the train facing the other way from
     * the folded record. The railway is retired from the arrival's own callback, between the last check and the turn.
     *
     * MUTATION: turn a train at its destination without asking whether its railway is still the current one, and this
     * fails.
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

        // Retired from the arrival's callback, after the last milestone's check and before the turn.
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
     * A train that kept its direction is stood on the plain copy without the square ever reading empty (the platform's
     * exit-guard signal follows the square and was commanded GREEN then RED over the standing train): it is put on the
     * plain copy, with the side and road it came in by, before it is taken off the turning one.
     *
     * MUTATION: take the train off the turning copy before putting it on the plain one, and this fails.
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
     * A train sent by hand to a may-turn square and told to keep its direction is drawn facing the way it will stand
     * from the moment it stops, not the turning copy's way until its run is done (MT-701). The caption's arrow asks
     * `copyItWillStandOn` (OB-314), and the icon must too (OB-317). The route end is held open with the route-end
     * callback, where the re-stand delay sits.
     *
     * MUTATION: take the copy it will stand on out of `DiagramMonitor.markTrain`, and this fails.
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
     * faces on from the way it came: the end of a line marked "Trains May Change Direction Here", or a may-turn square
     * one of whose ways in has no way on. Such a square is a compulsory turn in all but its flag, and so for a train
     * that cannot reverse (MT-245): the model and the decoder agree, which keeps the next journey on its path.
     *
     * Two shapes, each sent with the answer keep: no other copy at all, and a plain copy the train's approach cannot
     * reach (the second with a train that cannot reverse as well).
     *
     * MUTATION: take the compulsory turn out of `Layout.turnsOnArrival`, and this fails.
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

    /** Sends a train with the answer keep, asked about the destination, and asserts it was turned there. */
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
     * could not be honored there, and still is where one does. Asked of `ManualReversalPrompt.destinationAskedAbout`,
     * because asking puts up a modal dialog.
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

        assertEquals(org.traincontrol.gui.autonomy.ManualReversalPrompt.destinationAskedAbout(everywhere, rail,
            java.util.Arrays.asList(rail.getEdge("NAS", "NAT"))), null, "the operator is asked whether to keep the"
            + " direction at a square no copy of which faces on from the train's approach - an answer the arrival cannot"
            + " honour (RSA49-A1)");

        assertEquals(org.traincontrol.gui.autonomy.ManualReversalPrompt.destinationAskedAbout(everywhere, rail,
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
     * A timetable recorded by hand through a may-turn square plays back what was decided there: the entry keeps the
     * answer the arrival was given and is played back with it, so playback does not turn a train the recording did not
     * (the next entry starts on the plain copy).
     *
     * MUTATION: record no answer, or play every entry back as autonomy again, and this fails.
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

        // Playback drives an entry at the train's preferred speed, and one with none is skipped
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
                org.traincontrol.gui.autonomy.ManualReversalPrompt.KEEP_DIRECTION, c);

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

    /** Sends a train by hand along a path with the answer given, and plays its arrival sensor. */
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
     * A train that cannot reverse is not offered by hand the turning copy of a may-turn square when no way into that
     * copy has a copy facing on (MT-367): the rule is asked of the copy's ways in, not of the square alone.
     *
     * MUTATION: ask the square alone again, and this fails.
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
