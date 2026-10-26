package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.util.I18n;

/**
 * A dispatch that fails says the right thing about restarting (MT-327, MT-328).
 *
 * Adam, on both entries: "I don't know how to trigger this error condition. Seems like something to test offline."
 * Reaching this sentence by hand means making a real dispatch fail part way, with a decoder that does not answer or an
 * accessory that has gone missing.
 *
 * Two doors, two sentences: `executePath` has three callers (autonomy, the commands panel, the diagram's right-click
 * menu); the last two work with autonomy stopped, and the old "Autonomy has stopped itself so the railway can be parked
 * and started again" sent the operator to press Start over a hand dispatch.
 *
 * The trap the entries name: "If both cases say the same thing, the flag that chooses between them is being read after
 * the stop has already cleared it."  `stopLocomotives()` clears `running` and the handler calls it before it logs, so
 * `wasRunning` has to be captured first.
 *
 * MUTATION: move the `final boolean wasRunning = this.running;` below `stopLocomotives()` and the running case fails
 * while the hand-dispatch case stays green.
 *
 * @author Adam
 */
public class testAFailedDispatchSaysWhichKindItWas
{
    private static MarklinControlStation model;

    /**
     * A copy of a fixture, so `init` does not load whatever railway this machine happens to have: nothing here reads
     * the diagram, but `init` points at the layout preference regardless.
     * `regression.testSwitchingToACentralStationLayout` ratchets the number of classes that skip this.
     */
    private static support.LayoutSandbox sandbox;

    /** Everything the model logged while a claim was running */
    private static final List<String> LOGGED = Collections.synchronizedList(new ArrayList<String>());

    private static java.util.logging.Handler tap;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = support.TestStation.simulatedInDebug();

        // THE LOG, READ AT ITS OWN LOGGER: `MarklinControlStation.log(String)` ends in `log.info(message)`, so no hook
        // is added to the program for the sake of the test.
        tap = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                if (record != null && record.getMessage() != null) LOGGED.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (tap != null)
            {
                java.util.logging.Logger.getLogger(MarklinControlStation.class.getName())
                    .removeHandler(tap);
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * MT-327: with autonomy NOT running, the message says there is nothing to restart.
     */
    @Test
    public void testAHandDispatchIsNotToldToPressStart() throws Exception
    {
        Locomotive loc = failADispatch("HD_FROM", 1440, "HD_TO", 1441, false);

        String handDispatch = I18n.f("autolayout.errorHandDispatchFailed", loc.getName());
        String autonomyStopped = I18n.f("autolayout.errorRunStoppedByFailure", loc.getName());

        assertTrue(LOGGED.contains(handDispatch),
            "a hand dispatch failed with autonomy stopped and the log did not say so. Expected: \""
            + handDispatch + "\". What was logged: " + tail());

        assertFalse(LOGGED.contains(autonomyStopped),
            "a hand dispatch failed with autonomy stopped and the log said autonomy had stopped"
            + " ITSELF and could be started again - which sends the operator to press Start over"
            + " something that was never running");
    }

    /**
     * MT-328: with autonomy running, the old sentence is the right one and is still said: a message that always says
     * "nothing to restart" would pass the claim above.
     */
    @Test(dependsOnMethods = "testAHandDispatchIsNotToldToPressStart")
    public void testARunThatStopsItselfStillSaysToStartItAgain() throws Exception
    {
        Locomotive loc = failADispatch("RUN_FROM", 1442, "RUN_TO", 1443, true);

        String handDispatch = I18n.f("autolayout.errorHandDispatchFailed", loc.getName());
        String autonomyStopped = I18n.f("autolayout.errorRunStoppedByFailure", loc.getName());

        assertTrue(LOGGED.contains(autonomyStopped),
            "a path failed while autonomy WAS running and the log did not say the run had stopped"
            + " itself. Expected: \"" + autonomyStopped + "\". What was logged: " + tail()
            + ". If this says the hand-dispatch sentence instead, `wasRunning` is being read after"
            + " stopLocomotives() has already cleared it");

        assertFalse(LOGGED.contains(handDispatch),
            "a run that stopped itself was described as a hand dispatch with nothing to restart");
    }

    /**
     * Builds a two-point railway, makes the dispatch throw part way, and returns the locomotive.
     */
    private static Locomotive failADispatch(String fromName, int fromSensor, String toName,
        int toSensor, boolean running) throws Exception
    {
        LOGGED.clear();

        Layout layout = new Layout(model);

        MarklinFeedback from = model.newFeedback(fromSensor, null);
        MarklinFeedback to = model.newFeedback(toSensor, null);

        model.setFeedbackState(from.getName(), true);
        model.setFeedbackState(to.getName(), false);

        layout.createPoint(fromName, true, from.getName());
        layout.createPoint(toName, true, to.getName());
        layout.createEdge(fromName, toName);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint(fromName).setLocomotive(loc);

        List<Edge> path = new ArrayList<>();

        path.add(layout.getEdge(fromName, toName));

        assertNotNull(path.get(0), "the fixture produced no edge, so nothing below is exercised");

        // The throw, after the path has been locked - as in `testAFailedPathStopsTheRunAndGivesTheTrackBack`.
        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            throw new RuntimeException("deliberate mid-path failure");
        });

        if (running) layout.runLocomotives();

        try
        {
            layout.executePath(path, loc, 20, null);

            fail("executePath swallowed the failure, so the handler that writes the message never ran");
        }
        catch (RuntimeException expected)
        {
            // Not swallowed - executeTimetable's retry loop depends on that.
        }
        finally
        {
            loc.setCallback(Layout.CB_ROUTE_START, null);
        }

        return loc;
    }

    /** The last few lines, for a failure message somebody can act on */
    private static List<String> tail()
    {
        synchronized (LOGGED)
        {
            return new ArrayList<>(LOGGED.subList(Math.max(0, LOGGED.size() - 6), LOGGED.size()));
        }
    }
}
