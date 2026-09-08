package regression;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinFeedback;

/**
 * Answering "no, do not keep the direction" turns the train round when it gets there.
 *
 * Adam, 2026-09-08, `OB-189`: *"I send EN57-203 from BottomMainA to BottomMainPost. I say No to keep
 * current direction, but it does not reverse on arrival."* And: *"add tests to confirm reversal
 * commands are emitted."*
 *
 * **Nothing in the suite watched the command.** Every reversal test up to here checked a decision -
 * `shouldReverseAt` returning true, `asksAbout` naming the right square, the prompt's polarity - and
 * the decision was reached correctly the whole time. What none of them followed was whether
 * `switchDirection()` then reached the locomotive. A chain of five correct answers ending in nothing
 * happening looks exactly like a chain with one wrong answer, from the outside.
 *
 * So these assert the DIRECTION OF THE TRAIN after the journey, which is the only thing the operator
 * can see, and they run the real `executePath` to get it.
 *
 * @author Adam
 */
public class testAReversalCommandIsEmitted
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static final String DRIVER = "OB189 driver";

    private static final int ADDRESS = 63;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        // DEBUG ON, because setSimulate refuses outside it - and simulation is the only way the
        // train ever arrives here.  The last argument is debug; testAutonomySimulationSanity does the
        // same for the same reason.
        model = init(null, true, false, false, true);
        model.stop();

        model.newMM2Locomotive(DRIVER, ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null)
        {
            try { model.deleteLoc(DRIVER); } catch (Exception ignored) { }
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * A policy that answers for one square, the way the prompt's does.
     *
     * Built here rather than through `ManualReversalPrompt.forJourney`, which puts a dialog on the
     * screen. The two clauses are copied from it exactly: the answer speaks for the square it was
     * asked about and no other, and `asksAbout` names that square so `shouldReverseAt` can tell a
     * turn the operator has a say over from a compulsory one.
     */
    private static Layout.ReversalPolicy answering(final boolean turn, final Point asked)
    {
        return new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return turn && at == asked;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == asked;
            }
        };
    }

    /**
     * Two points and the edge between them, the destination being one trains may turn at.
     *
     * @param tag distinguishes this fixture's names and sensors from the others in the class
     * @param from where the sensor addresses start
     * @return the layout, with the driver standing at START
     * @throws Exception on a failure to build it
     */
    private static Layout railway(String tag, int from) throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(from, null);
        MarklinFeedback end = model.newFeedback(from + 1, null);

        model.setFeedbackState(start.getName(), true);
        model.setFeedbackState(end.getName(), false);

        layout.createPoint(tag + "_START", true, start.getName());
        layout.createPoint(tag + "_END", true, end.getName());

        layout.getPoint(tag + "_END").setReversing(true);

        layout.createEdge(tag + "_START", tag + "_END");

        layout.getPoint(tag + "_START").setLocomotive(model.getLocByName(DRIVER));

        // WITHOUT THIS THE TRAIN NEVER ARRIVES.  `executePath` blocks on the destination sensor,
        // and nothing on this machine is going to trip it.
        layout.setSimulate(true);

        return layout;
    }

    /**
     * Three points in a line, the last being one trains may turn at.
     *
     * @param tag distinguishes this fixture's names and sensors
     * @param from where the sensor addresses start
     * @return the layout, with the driver standing at START
     * @throws Exception on a failure to build it
     */
    private static Layout longerRailway(String tag, int from) throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(from, null);
        MarklinFeedback middle = model.newFeedback(from + 1, null);
        MarklinFeedback end = model.newFeedback(from + 2, null);

        model.setFeedbackState(start.getName(), true);
        model.setFeedbackState(middle.getName(), false);
        model.setFeedbackState(end.getName(), false);

        layout.createPoint(tag + "_START", true, start.getName());
        layout.createPoint(tag + "_MIDDLE", true, middle.getName());
        layout.createPoint(tag + "_END", true, end.getName());

        // ONLY THE DESTINATION TURNS. The middle is an ordinary through square, so anything that
        // happens there is not a reversal anybody asked for.
        layout.getPoint(tag + "_END").setReversing(true);

        layout.createEdge(tag + "_START", tag + "_MIDDLE");
        layout.createEdge(tag + "_MIDDLE", tag + "_END");

        layout.getPoint(tag + "_START").setLocomotive(model.getLocByName(DRIVER));

        layout.setSimulate(true);

        return layout;
    }

    /**
     * Runs one leg and insists it actually arrived.
     *
     * A journey that never completes would leave the direction unchanged and read as the defect this
     * is looking for, so the timeout is a failure with its own message rather than a hang.
     *
     * @param layout the railway
     * @param path the leg
     * @param driver the train
     * @param answered what the operator said
     * @param which for the message
     * @throws Exception on a failure to run it
     */
    private static void run(Layout layout, List<Edge> path, Locomotive driver,
        Layout.ReversalPolicy answered, String which) throws Exception
    {
        ExecutorService watchdog = Executors.newSingleThreadExecutor();

        try
        {
            Future<Boolean> leg = watchdog.submit(
                () -> layout.executePath(path, driver, 20, null, answered));

            assertTrue(leg.get(30, TimeUnit.SECONDS),
                which + " reported failure rather than completing, so nothing below was exercised");
        }
        catch (TimeoutException wedged)
        {
            layout.stopLocomotives();

            fail(which + " never arrived, so whether it would have turned round is unknown");
        }
        finally
        {
            watchdog.shutdownNow();
        }
    }

    /**
     * The whole of OB-189: the answer was no, so the train is facing the other way at the end.
     *
     * MUTATION: delete the `switchDirection()` from the arrival block in `executePathInternal` and this
     * fails, while every existing reversal test stays green - which is the state the suite was in when
     * Adam reported it.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testAnsweringNoTurnsTheTrainRound() throws Exception
    {
        Layout layout = railway("REV", 8400);

        Locomotive driver = model.getLocByName(DRIVER);

        Point end = layout.getPoint("REV_END");

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge("REV_START", "REV_END"));

        assertNotNull(path.get(0), "the fixture produced no edge, so nothing below is exercised");

        boolean before = driver.goingForward();

        layout.runLocomotives();

        run(layout, path, driver, answering(true, end), "the answered journey");

        assertNotEquals(driver.goingForward(), before,
            "the train arrived at a may-reverse square it had been asked about, the answer was no -"
            + " do not keep the direction - and it is still facing the way it started. That is OB-189:"
            + " every decision on the way to this point was made correctly and no command was sent");
    }

    /**
     * The control: yes keeps the direction, so the test above is not passing on a turn that always
     * happens.
     *
     * Without this, a `switchDirection()` called unconditionally on every arrival would satisfy the
     * test above completely - and turning a train nobody asked to turn is the worse of the two faults,
     * because the operator is not expecting it.
     *
     * MUTATION: make `shouldReverse` return true regardless of the answer and this fails.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testAnsweringYesLeavesTheDirectionAlone() throws Exception
    {
        Layout layout = railway("KEEP", 8402);

        Locomotive driver = model.getLocByName(DRIVER);

        Point end = layout.getPoint("KEEP_END");

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge("KEEP_START", "KEEP_END"));

        boolean before = driver.goingForward();

        layout.runLocomotives();

        run(layout, path, driver, answering(false, end), "the kept-direction journey");

        assertEquals(driver.goingForward(), before,
            "the answer was yes - keep the direction - and the train turned round anyway. A dialog"
            + " whose answer is discarded is worse than no dialog");
    }

    /**
     * The same answer, on a journey of two legs - which is the shape Adam's was.
     *
     * `BottomMainA` to `BottomMainPost` is not one edge, and the single-leg test above passes, so if a
     * reversal is being applied twice and cancelling itself out, this is where it shows: the loop in
     * `executePathInternal` offers `path.get(i).getEnd()` to the mid-path reversal branch for every
     * edge INCLUDING THE LAST, and the arrival block below the loop then asks the same question of the
     * same square.
     *
     * MUTATION: this is the test for OB-189. If it passes, the double-turn theory is wrong and the
     * defect is elsewhere - which is worth knowing in one run rather than by argument.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testTwoLegsStillTurnTheTrainExactlyOnce() throws Exception
    {
        Layout layout = longerRailway("TWO", 8410);

        Locomotive driver = model.getLocByName(DRIVER);

        Point end = layout.getPoint("TWO_END");

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge("TWO_START", "TWO_MIDDLE"));
        path.add(layout.getEdge("TWO_MIDDLE", "TWO_END"));

        assertNotNull(path.get(0), "the fixture produced no first edge");
        assertNotNull(path.get(1), "the fixture produced no second edge");

        boolean before = driver.goingForward();

        layout.runLocomotives();

        run(layout, path, driver, answering(true, end), "the two-leg journey");

        assertNotEquals(driver.goingForward(), before,
            "the train ran two legs to a may-reverse square, was asked, and the answer was no - do not"
            + " keep the direction - and it is facing the way it started. Turning it twice leaves it"
            + " where it began, and to the operator that is indistinguishable from never turning at"
            + " all, which is what OB-189 reports");
    }

    /**
     * A journey that passes a reversing square on the way to the one that was answered.
     *
     * Adam, 2026-09-08, on OB-189 and which square the dialog named: *"it may have named
     * bottommainpost, but it could have been rampdown on some of the runs."* RampDown is on the way
     * to BottomMainPost, and it is a square trains turn at.
     *
     * **The answer is honoured; the compulsory turn on the way is what surprises.** Measured both ways
     * on 2026-09-08, same journey, same reversing square in the middle:
     *
     * - keep direction - started forward, ended BACKWARD
     * - reverse - started forward, ended FORWARD
     *
     * So the destination turn fires either way. `shouldReverseAt` treats a reversing square nobody was
     * asked about as a compulsory turn, which is right - a turning copy leaves only by the side the
     * train came in on, and not turning would drive it off its reserved path - and that flip happens
     * before the answered one. "No" therefore returns the train to the direction it set off in.
     *
     * **The answer is about how the train ARRIVES, and it reads as being about how it started.**
     *
     * **AND THIS FIXTURE IS NOT A SHAPE THE BUILDER EMITS.** It calls `setReversing(true)` on a square
     * a straight path runs through. `AutonomyBuilder` splits a may-turn square into a plain copy for
     * paths passing onwards and a turning copy for paths backing in, so a real journey only reaches a
     * reversing copy when it is backing in there - which is Adam's rule, already implemented by the
     * split: *"only reverse if intended at the end or when backing in somewhere per the path, don't
     * reverse if passing onwards."*
     *
     * So the cancelling pair of turns this shows cannot happen on a built railway, and the test earns
     * its place as the guard that the two answers still differ rather than as a report of a defect.
     * `testTheTurnRuleDoesNotChangeTheRealRailway` holds the measurement that settled it.
     *
     * MUTATION: make the middle square ordinary and this passes, which is exactly why the two-leg test
     * above did not catch it.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testTheAnswerStillChangesTheOutcomeThroughAReversingSquare() throws Exception
    {
        Layout layout = longerRailway("VIA", 8420);

        // THE DIFFERENCE FROM THE TEST ABOVE, and the whole of this fixture: the square in the middle
        // is one trains turn at. Every hand-built layout in this suite is a straight chain of ordinary
        // points, so nothing has ever run a journey through one.
        layout.getPoint("VIA_MIDDLE").setReversing(true);

        Locomotive driver = model.getLocByName(DRIVER);

        Point end = layout.getPoint("VIA_END");

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge("VIA_START", "VIA_MIDDLE"));
        path.add(layout.getEdge("VIA_MIDDLE", "VIA_END"));

        layout.runLocomotives();

        run(layout, path, driver, answering(true, end), "the reversed journey through a reversal");

        boolean afterNo = driver.goingForward();

        // THE SAME JOURNEY AGAIN, the other answer, on its own railway so nothing carries over.
        Layout second = longerRailway("VIA2", 8424);

        second.getPoint("VIA2_MIDDLE").setReversing(true);

        List<Edge> back = new ArrayList<>();

        back.add(second.getEdge("VIA2_START", "VIA2_MIDDLE"));
        back.add(second.getEdge("VIA2_MIDDLE", "VIA2_END"));

        second.runLocomotives();

        run(second, back, driver, answering(false, second.getPoint("VIA2_END")),
            "the kept-direction journey through a reversal");

        assertNotEquals(driver.goingForward(), afterNo,
            "the two answers produced the same final direction, so the dialog did nothing on a journey"
            + " that passes a reversing square. Measured on 2026-09-08 they differ - keep-direction"
            + " ends backward, reverse ends forward - because the compulsory turn at the square in the"
            + " middle flips the train once before the answered turn at the destination flips it"
            + " again. If this fails, the answer really is being discarded and OB-189 is a defect"
            + " rather than the surprise it turned out to be");
    }
}
