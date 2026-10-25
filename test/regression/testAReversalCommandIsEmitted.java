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
 * Answering "no, do not keep the direction" turns the train round when it gets there (OB-189: *"add tests to confirm
 * reversal commands are emitted."*).
 *
 * Every reversal test before this checked a decision (`shouldReverseAt` returning true, `asksAbout` naming the right
 * square, the prompt's polarity) and none followed whether `switchDirection()` then reached the locomotive: a chain of
 * five correct answers ending in nothing happening looks like a chain with one wrong answer. So these assert the
 * DIRECTION OF THE TRAIN after the journey, the only thing the operator can see, running the real `executePath`.
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
        // Before the model: init reads the layout preference and would otherwise open Adam's own railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        // DEBUG ON, because setSimulate refuses outside it, and simulation is the only way the train ever arrives here.
        model = support.TestStation.simulatedInDebug();
        model.stop();

        model.newMM2Locomotive(DRIVER, ADDRESS);

        // A train that can reverse, the one the operator is asked about: a new locomotive is not, and one that cannot
        // is not turned where its destination has a copy it can stand on facing on (MT-368), which is every claim here.
        model.getLocByName(DRIVER).setReversible(true);
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
     * A policy that answers for one square, the way the prompt's does, built here because
     * `ManualReversalPrompt.forJourney` puts a dialog on the screen. The answer speaks for the square it was asked
     * about and no other, and `asksAbout` names that square so `shouldReverseAt` can tell a turn the operator has a say
     * over from a compulsory one.
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
     * Two points and the edge between them, the destination being one trains may turn at; `tag` distinguishes this
     * fixture's names and sensors, and the driver stands at START.
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

        withAPlainCopy(layout, tag, end, tag + "_START");

        layout.getPoint(tag + "_START").setLocomotive(model.getLocByName(DRIVER));

        // Without this the train never arrives: `executePath` blocks on the destination sensor and nothing on this
        // machine trips it.
        layout.setSimulate(true);

        return layout;
    }

    /**
     * Makes the destination a square trains MAY turn at, as the build makes one a train can stand on facing on: its
     * turning copy and a plain copy beside it, one piece of track on one sensor, the plain one reached from the same
     * approach. A lone turning Point is the end of a line, where a train is turned whatever is answered, so "keep"
     * could not be asked about.
     */
    private static void withAPlainCopy(Layout layout, String tag, MarklinFeedback sensor, String approach)
        throws Exception
    {
        layout.createPoint(tag + "_END_PLAIN", true, sensor.getName());

        layout.getPoint(tag + "_END").setBlock(tag + "_ENDS");
        layout.getPoint(tag + "_END_PLAIN").setBlock(tag + "_ENDS");

        layout.createEdge(approach, tag + "_END_PLAIN");
    }

    /**
     * Three points in a line, the last being one trains may turn at; `tag` distinguishes this fixture's names and
     * sensors, and the driver stands at START.
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

        // Only the destination turns: the middle is an ordinary through square, so anything that happens there is not a
        // reversal anybody asked for.
        layout.getPoint(tag + "_END").setReversing(true);

        layout.createEdge(tag + "_START", tag + "_MIDDLE");
        layout.createEdge(tag + "_MIDDLE", tag + "_END");

        withAPlainCopy(layout, tag, end, tag + "_MIDDLE");

        layout.getPoint(tag + "_START").setLocomotive(model.getLocByName(DRIVER));

        layout.setSimulate(true);

        return layout;
    }

    /**
     * Runs one leg and insists it actually arrived: a journey that never completes would leave the direction unchanged
     * and read as the defect being looked for, so the timeout is a failure with its own message rather than a hang.
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
     * MUTATION: delete the `switchDirection()` from the arrival block in `executePathInternal` and this fails, while
     * every other reversal test stays green.
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
     * The control: yes keeps the direction, so the test above is not passing on a turn that always happens. A
     * `switchDirection()` called unconditionally on every arrival would satisfy that test, and turning a train nobody
     * asked to turn is the worse fault.
     *
     * MUTATION: make `shouldReverse` return true regardless of the answer and this fails.
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
     * The same answer, on a journey of two legs, the shape Adam's was. If a reversal is applied twice and cancels
     * itself out, this is where it shows: the loop in `executePathInternal` offers `path.get(i).getEnd()` to the
     * mid-path reversal branch for every edge INCLUDING THE LAST, and the arrival block below asks the same question of
     * the same square.
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
     * A journey that passes a reversing square on the way to the one that was answered (RampDown, on the way to
     * BottomMainPost). Measured both ways, same journey: keep direction started forward and ended BACKWARD; reverse
     * started forward and ended FORWARD. `shouldReverseAt` treats a reversing square nobody was asked about as a
     * compulsory turn, which is right (a turning copy leaves only by the side the train came in on), and that flip
     * happens before the answered one, so "No" returns the train to the direction it set off in.
     *
     * This fixture is NOT a shape the builder emits: `AutonomyBuilder` splits a may-turn square into a plain copy for
     * paths passing onwards and a turning copy for paths backing in, so a real journey only reaches a reversing copy
     * when backing in there. The canceling pair of turns cannot happen on a built railway; the test is the guard that
     * the two answers still differ, and `testTheTurnRuleDoesNotChangeTheRealRailway` holds the measurement that settled
     * it.
     *
     * MUTATION: make the middle square ordinary and this passes, which is why the two-leg test above did not catch it.
     */
    @Test
    public void testTheAnswerStillChangesTheOutcomeThroughAReversingSquare() throws Exception
    {
        Layout layout = longerRailway("VIA", 8420);

        // The difference from the test above, and the whole of this fixture: the square in the middle is one trains
        // turn at, which no other hand-built layout in this suite has.
        layout.getPoint("VIA_MIDDLE").setReversing(true);

        Locomotive driver = model.getLocByName(DRIVER);

        Point end = layout.getPoint("VIA_END");

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge("VIA_START", "VIA_MIDDLE"));
        path.add(layout.getEdge("VIA_MIDDLE", "VIA_END"));

        layout.runLocomotives();

        run(layout, path, driver, answering(true, end), "the reversed journey through a reversal");

        boolean afterNo = driver.goingForward();

        // The same journey again, the other answer, on its own railway so nothing carries over.
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
