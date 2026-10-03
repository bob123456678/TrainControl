package regression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import static regression.testNoSetupEditDuringARun.WAITING_FOR_THE_TRAINS;
import static regression.testNoSetupEditDuringARun.control;
import static regression.testTheImportDoorReadsAnOldFile.answeringYes;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import static regression.testTheImportDoorReadsAnOldFile.putTheFolderBack;

/**
 * The Auto tab waits for the trains at both ends of a run it did not see coming (RSA27-C1, RSA27-C2): a Start that starts
 * nothing gives it back, and a hand send greys it from its click.  The rule itself, and a whole run, are claimed in
 * `regression.testNoSetupEditDuringARun` (`testTheAutoTabIsGreyedWhileAutonomyIsBusy`,
 * `testARunGreysTheAutoTabAndItsStopGivesItBack`), whose helpers these share.
 *
 * Its own class because each of these drives a real run in its window, and that class already holds thirty-nine windows:
 * every window a test opens keeps its threads, and a class of many runs out of heap (2026-09-29).
 *
 * @author Adam
 */
public class testTheAutoTabWaitsForTheTrains
{

    /**
     * A Start that starts nothing gives Start and the Auto tab back (RSA27-C1): every train to run was skipped - here each
     * with no speed, as on a station out of service - and the railway cleared its own flag and only logged it, so Start
     * stayed greyed and Graceful Stop offered over nothing, and the Auto tab, which counts Graceful Stop offered as a run,
     * stayed greyed saying to wait for trains that were not moving.
     *
     * MUTATION: leave Start's buttons as they are when nothing started, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAStartThatStartsNothingGivesTheAutoTabBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        boolean echoWas = org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        final java.util.Map<org.traincontrol.base.Locomotive, Integer> speeds = new java.util.LinkedHashMap<>();

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            // THE POWER ON, which the gate asks
            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

            ui[0].getModel().go();

            long until = System.currentTimeMillis() + 5000;

            while (!ui[0].getModel().getPowerState() && System.currentTimeMillis() < until) Thread.sleep(50);

            assertTrue(ui[0].getModel().getPowerState(), "precondition: the power is not on");

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // EVERY TRAIN TO RUN SKIPPED, for want of a speed
            for (org.traincontrol.base.Locomotive loc : railway.getLocomotivesToRun())
            {
                speeds.put(loc, loc.getPreferredSpeed());

                loc.setPreferredSpeed(0);
            }

            assertFalse(speeds.isEmpty(), "precondition: no train is in the run list, so Start would be refused before it"
                + " dispatched");

            final java.util.Map<String, String> atRest = new java.util.LinkedHashMap<>();

            for (String name : WAITING_FOR_THE_TRAINS) atRest.put(name, control(ui[0], name).getToolTipText());

            // GRACEFUL STOP WATCHED, so its offer and its going are both seen however fast they come
            final javax.swing.JComponent stop = control(ui[0], "gracefulStop");

            final List<Object> offered = Collections.synchronizedList(new ArrayList<>());

            final java.beans.PropertyChangeListener watch = event -> offered.add(event.getNewValue());

            SwingUtilities.invokeAndWait(() -> stop.addPropertyChangeListener("enabled", watch));

            final java.lang.reflect.Method start =
                TrainControlUI.class.getDeclaredMethod("startAutonomyActionPerformed", java.awt.event.ActionEvent.class);

            start.setAccessible(true);

            answeringYes(() ->
            {
                try
                {
                    start.invoke(ui[0], new Object[] {null});
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            final javax.swing.JComponent startButton = control(ui[0], "startAutonomy");

            until = System.currentTimeMillis() + 10000;

            while (!(offered.contains(Boolean.TRUE) && !stop.isEnabled() && startButton.isEnabled())
                && System.currentTimeMillis() < until)
            {
                Thread.sleep(50);
            }

            assertTrue(offered.contains(Boolean.TRUE), "precondition: Start did not dispatch its run - Graceful Stop was"
                + " never offered");

            assertFalse(railway.isRunning(), "precondition: a train ran, though every train to run had no speed");

            assertFalse(stop.isEnabled(), "a Start that started nothing left Graceful Stop offered (RSA27-C1)");

            assertTrue(startButton.isEnabled(), "a Start that started nothing left Start greyed (RSA27-C1)");

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            for (String name : WAITING_FOR_THE_TRAINS)
            {
                javax.swing.JComponent c = control(ui[0], name);

                assertTrue(c.isEnabled(), name + " is still greyed after a Start that started nothing (RSA27-C1)");

                assertEquals(c.getToolTipText(), atRest.get(name), name + " did not get its own tooltip back");
            }

            SwingUtilities.invokeAndWait(() -> stop.removePropertyChangeListener("enabled", watch));
        }
        finally
        {
            for (java.util.Map.Entry<org.traincontrol.base.Locomotive, Integer> was : speeds.entrySet())
            {
                was.getKey().setPreferredSpeed(was.getValue());
            }

            if (ui[0] != null && ui[0].getModel().hasAutoLayout()) ui[0].getModel().getAutoLayout().stopLocomotives();

            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A hand send greys the Auto tab from its click (RSA27-C2), not once its train sets off: the dispatch sets the route
     * first - seconds on a long one - and the railway's first refresh came only then, so for those seconds the settings,
     * Execute Timetable and the capture toggle were offered and then refused.  Given back at the train's arrival.
     *
     * MUTATION: leave the Auto tab to the train's departure, or never count the hand send down, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAHandSendGreysTheAutoTabFromItsClick() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        boolean echoWas = org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            // THE POWER ON, which the gate asks
            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

            ui[0].getModel().go();

            long until = System.currentTimeMillis() + 5000;

            while (!ui[0].getModel().getPowerState() && System.currentTimeMillis() < until) Thread.sleep(50);

            assertTrue(ui[0].getModel().getPowerState(), "precondition: the power is not on");

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // THE LONGEST PATH ANY TRAIN MAY TAKE, so its route takes a while to set
            final java.lang.reflect.Method mayTake = TrainControlUI.class.getDeclaredMethod("whyThisPathCannotBeTaken",
                org.traincontrol.automation.Layout.class, List.class, org.traincontrol.base.Locomotive.class);

            mayTake.setAccessible(true);

            org.traincontrol.base.Locomotive train = null;

            List<org.traincontrol.automation.Edge> path = null;

            for (org.traincontrol.base.Locomotive loc : railway.getLocomotivesToRun())
            {
                for (List<org.traincontrol.automation.Edge> candidate : railway.getPossiblePaths(loc, true))
                {
                    if ((path == null || candidate.size() > path.size()) && mayTake.invoke(null, railway, candidate, loc) == null)
                    {
                        path = candidate;
                        train = loc;
                    }
                }
            }

            assertNotNull(path, "precondition: no train on the snapshot may take any path");

            final java.util.Map<String, String> atRest = new java.util.LinkedHashMap<>();

            for (String name : WAITING_FOR_THE_TRAINS) atRest.put(name, control(ui[0], name).getToolTipText());

            final java.lang.reflect.Method send = TrainControlUI.class.getDeclaredMethod("sendATrainByHand",
                org.traincontrol.automation.Layout.class, List.class, org.traincontrol.base.Locomotive.class,
                java.awt.Component.class);

            send.setAccessible(true);

            final List<org.traincontrol.automation.Edge> chosen = path;

            final org.traincontrol.base.Locomotive sent = train;

            final List<String> offeredAtTheClick = Collections.synchronizedList(new ArrayList<>());

            answeringYes(() ->
            {
                try
                {
                    send.invoke(ui[0], railway, chosen, sent, ui[0]);

                    // STRAIGHT AFTER THE CLICK, in the same event: before the route is set or the train sets off
                    for (String name : WAITING_FOR_THE_TRAINS)
                    {
                        if (control(ui[0], name).isEnabled()) offeredAtTheClick.add(name);
                    }
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            until = System.currentTimeMillis() + 5000;

            while (!railway.isRunning() && System.currentTimeMillis() < until) Thread.sleep(20);

            assertTrue(railway.isRunning(), "precondition: the hand send of " + sent.getName() + " did not set off");

            assertEquals(offeredAtTheClick, new ArrayList<String>(), "offered between a hand send's click and its train"
                + " setting off (RSA27-C2)");

            // AND GIVEN BACK at its arrival
            until = System.currentTimeMillis() + 180000;

            while (railway.isRunning() && System.currentTimeMillis() < until) Thread.sleep(100);

            assertFalse(railway.isRunning(), "precondition: " + sent.getName() + " did not arrive within three minutes");

            final javax.swing.JComponent first = control(ui[0], WAITING_FOR_THE_TRAINS[0]);

            until = System.currentTimeMillis() + 5000;

            while (!first.isEnabled() && System.currentTimeMillis() < until) Thread.sleep(50);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            for (String name : WAITING_FOR_THE_TRAINS)
            {
                javax.swing.JComponent c = control(ui[0], name);

                assertTrue(c.isEnabled(), name + " is still greyed after the hand-sent train arrived");

                assertEquals(c.getToolTipText(), atRest.get(name), name + " did not get its own tooltip back");
            }
        }
        finally
        {
            if (ui[0] != null && ui[0].getModel().hasAutoLayout()) ui[0].getModel().getAutoLayout().stopLocomotives();

            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }
}
