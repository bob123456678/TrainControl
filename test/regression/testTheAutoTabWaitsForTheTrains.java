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
import static regression.testNoSetupEditDuringARun.closingEveryQuestion;
import static regression.testTheImportDoorReadsAnOldFile.closeTheEditor;
import org.traincontrol.util.I18n;
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
     * And it says so (Adam, 2026-10-02: "the start says nothing"): the log alone said why.
     *
     * MUTATION: leave Start's buttons as they are when nothing started, or say nothing, and this fails.
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

        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

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

            // WHAT IT SAYS, read and closed - started once Start's own questions are answered
            final List<String> said = Collections.synchronizedList(new ArrayList<String>());

            closingEveryQuestion(said, new ArrayList<String>(), going);

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

            final String nothing = I18n.t("autolayout.ui.errorNothingStarted");

            until = System.currentTimeMillis() + 5000;

            while (!said.contains(nothing) && System.currentTimeMillis() < until) Thread.sleep(100);

            assertTrue(said.contains(nothing), "a Start that started nothing said nothing - only the log said why: " + said);
        }
        finally
        {
            going.set(false);

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

                    // and Start turning, as it does whenever anything runs (OB-309)
                    if (!ui[0].isShowingSomethingRuns()) offeredAtTheClick.add("no turning mark on Start (OB-309)");
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

    /**
     * The Auto tab's Edit Autonomy Paths in Track Diagram opens the autonomy editor, whichever editor was used last (Adam,
     * 2026-10-02): it ran the Edit button's code, which opens the editor used last - the track diagram's, after it.
     *
     * MUTATION: send the button to the Edit button's code, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheSettingsTabOpensTheAutonomyEditor() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            // THE TRACK EDITOR, USED LAST
            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

            org.traincontrol.gui.LayoutEditor first = theEditor(ui[0]);

            assertNotNull(first, "precondition: the track editor did not open");

            assertFalse(first.isAutonomyMode(), "precondition: the editor opened was not the track editor");

            closeTheEditor(ui[0]);

            // THE SETTINGS TAB'S BUTTON
            final java.lang.reflect.Method press = TrainControlUI.class.getDeclaredMethod(
                "editAutonomyFromSettingsActionPerformed", java.awt.event.ActionEvent.class);

            press.setAccessible(true);

            answeringYes(() ->
            {
                try
                {
                    press.invoke(ui[0], new Object[] {null});
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            org.traincontrol.gui.LayoutEditor opened = theEditor(ui[0]);

            assertNotNull(opened, "Edit Autonomy Paths in Track Diagram opened no editor");

            assertTrue(opened.isAutonomyMode(), "Edit Autonomy Paths in Track Diagram opened the track editor, the one used"
                + " last, not the autonomy editor");

            closeTheEditor(ui[0]);
        }
        finally
        {
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
     * An editor stays one editor through its own switches, and is counted open until it closes (RSA28-B1).
     *
     * Each switch hands over through the main window's own teardown - `autonomyEditorClosed` for the autonomy editor,
     * the refresh's `layoutRefreshCompleteInternal` for the track editor - which gave Edit and Edit Autonomy Paths back,
     * and the switch's posted arrival greyed them again.  A press in between opened a second editor; close the newer
     * and the gate, which asked only the newest, let Start run trains with the other still open.  Open is asked of the
     * editor windows now, so neither button is offered while one is there.
     *
     * Watched through Autonomy Setup to Track Diagram (with both buttons pressed in the hand-over's gap, the order
     * RSA28's M14 found), back to Autonomy Setup, and a page switch; then the close gives both back.
     *
     * MUTATION: ask the window's flag, not its editor windows, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEditorStaysOneThroughItsSwitches() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            // EITHER BUTTON OFFERED WHILE AN EDITOR WINDOW IS THERE
            final List<String> offered = Collections.synchronizedList(new ArrayList<String>());

            final String[] during = {"opening"};

            java.beans.PropertyChangeListener watch = e ->
            {
                try
                {
                    if (Boolean.TRUE.equals(e.getNewValue()) && editorWindows(ui[0]) > 0)
                    {
                        offered.add(((javax.swing.AbstractButton) e.getSource()).getText() + " during " + during[0]);
                    }
                }
                catch (ReflectiveOperationException failed)
                {
                    offered.add("the watch failed: " + failed);
                }
            };

            final javax.swing.AbstractButton edit = (javax.swing.AbstractButton) control(ui[0], "editLayoutButton");
            final javax.swing.AbstractButton paths = (javax.swing.AbstractButton) control(ui[0], "editAutonomyFromSettings");

            edit.addPropertyChangeListener("enabled", watch);
            paths.addPropertyChangeListener("enabled", watch);

            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.TRUE, null, true));

            final org.traincontrol.gui.LayoutEditor editor = theEditor(ui[0]);

            assertNotNull(editor, "precondition: the autonomy editor did not open");

            assertTrue(editor.isAutonomyMode(), "precondition: the editor opened was not the autonomy editor");

            // AUTONOMY SETUP TO TRACK DIAGRAM, both buttons pressed in the hand-over's gap
            during[0] = "the switch to Track Diagram";

            final javax.swing.AbstractButton track = button(editor.getRootPane(), I18n.t("layout.ui.sidebarTrack"));

            assertNotNull(track, "precondition: no Track Diagram in the editor's sidebar");

            answeringYes(() ->
            {
                track.doClick();

                SwingUtilities.invokeLater(() ->
                {
                    edit.doClick();
                    paths.doClick();
                });
            });

            waitForTheSwitch(ui[0], editor);

            assertEquals(editorWindows(ui[0]), 1, "Edit or Edit Autonomy Paths pressed while the autonomy editor handed over to"
                + " Track Diagram opened a second editor (RSA28-B1)");

            assertFalse(editor.isAutonomyMode(), "precondition: the editor did not arrive at Track Diagram");

            // TRACK DIAGRAM TO AUTONOMY SETUP
            during[0] = "the switch to Autonomy Setup";

            final javax.swing.AbstractButton setup = button(editor.getRootPane(), I18n.t("layout.ui.sidebarAutonomy"));

            assertNotNull(setup, "precondition: no Autonomy Setup in the editor's sidebar");

            answeringYes(() -> setup.doClick());

            waitForTheSwitch(ui[0], editor);

            assertTrue(editor.isAutonomyMode(), "precondition: the editor did not arrive at Autonomy Setup");

            // A PAGE SWITCH
            during[0] = "a page switch";

            final java.lang.reflect.Method step = editor.getClass().getDeclaredMethod("stepPage", int.class);

            step.setAccessible(true);

            answeringYes(() ->
            {
                try
                {
                    step.invoke(editor, 1);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            waitForTheSwitch(ui[0], editor);

            assertEquals(editorWindows(ui[0]), 1, "precondition: the switches left " + editorWindows(ui[0]) + " editors");

            // THE GATE SHUT WHILE IT IS OPEN
            assertTrue(ui[0].isLayoutEditorOpen(), "the gate does not see the editor that is open (RSA28-B1)");

            assertEquals(offered, new ArrayList<String>(), "Edit or Edit Autonomy Paths was offered while an editor was open"
                + " (RSA28-B1)");

            // AND THE CLOSE - the window's own - GIVES BOTH BACK
            during[0] = "the close";

            final java.lang.reflect.Method close = editor.getClass().getDeclaredMethod("confirmExit");

            close.setAccessible(true);

            answeringYes(() ->
            {
                try
                {
                    close.invoke(editor);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            waitForTheSwitch(ui[0], editor);

            assertEquals(editorWindows(ui[0]), 0, "the editor did not close");

            assertTrue(edit.isEnabled() && paths.isEnabled(), "Edit or Edit Autonomy Paths is still greyed once the editor"
                + " closed");

            // AND A CLOSE THAT GIVES EDIT BACK BEFORE ITS WINDOW GOES - a switch to a page that has gone, a build that
            // failed: asked again once the window has gone
            during[0] = "an editor given up on";

            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

            final org.traincontrol.gui.LayoutEditor again = theEditor(ui[0]);

            assertNotNull(again, "precondition: the track editor did not open again");

            final java.lang.reflect.Method abandon = again.getClass().getDeclaredMethod("confirmExitWithoutAsking");

            abandon.setAccessible(true);

            answeringYes(() ->
            {
                try
                {
                    abandon.invoke(again);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertEquals(editorWindows(ui[0]), 0, "precondition: the editor given up on is still there");

            assertTrue(edit.isEnabled() && paths.isEnabled(), "Edit or Edit Autonomy Paths is still greyed once an editor"
                + " that gave them back before its window went has gone (RSA28-B1)");

            assertEquals(offered, new ArrayList<String>(), "Edit or Edit Autonomy Paths was offered while an editor was open"
                + " (RSA28-B1)");
        }
        finally
        {
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
     * A Save and Continue at a switch leaves nothing to ask at a close in the switch's gap (RSA28-C1).
     *
     * The save kept the undo point the window opened with, so until the switch arrived the window still thought it had
     * unsaved work: a close then asked to *"throw away everything changed since you last saved"* - the opposite of what had
     * just been done - with the window changing mode beneath the question.  The save takes the undo point again now.
     *
     * MUTATION: keep the old undo point at a Save and Continue, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testASaveAtASwitchLeavesNothingToAskAtTheClose() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.TRUE, null, true));

            final org.traincontrol.gui.LayoutEditor editor = theEditor(ui[0]);

            assertNotNull(editor, "precondition: the autonomy editor did not open");

            assertTrue(editor.isAutonomyMode(), "precondition: the editor opened was not the autonomy editor");

            // AN EDIT: a station's longest train
            final org.traincontrol.automationui.AutonomySession session = ui[0].getAutonomySession();

            org.traincontrol.automationui.TileGraph.TileKey station = null;

            for (org.traincontrol.automationui.TileGraph.TileKey tile : session.getReducer().getPoints().keySet())
            {
                if (station == null && session.getStore().isStation(tile)) station = tile;
            }

            assertNotNull(station, "precondition: no station in the setup");

            final org.traincontrol.automationui.TileGraph.TileKey edited = station;

            final Object was = session.getPointProperty(edited, "maxTrainLength");

            final int length = was instanceof Number && ((Number) was).intValue() == 7 ? 6 : 7;

            SwingUtilities.invokeAndWait(() -> session.setPointProperty(edited, "maxTrainLength", length));

            final java.lang.reflect.Method unsaved = editor.getClass().getDeclaredMethod("hasUnsavedAutonomyWork");

            unsaved.setAccessible(true);

            final boolean[] waiting = new boolean[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    waiting[0] = (Boolean) unsaved.invoke(editor);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(waiting[0], "precondition: the edit is not unsaved work");

            final javax.swing.AbstractButton track = button(editor.getRootPane(), I18n.t("layout.ui.sidebarTrack"));

            final java.lang.reflect.Method close = editor.getClass().getDeclaredMethod("confirmExit");

            close.setAccessible(true);

            // TRACK DIAGRAM, SAVE AND CONTINUE (the question's first answer), AND THE CLOSE IN THE SWITCH'S GAP
            List<String> asked = answeringYes(() ->
            {
                track.doClick();

                try
                {
                    close.invoke(editor);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertFalse(asked.contains(I18n.t("autosetup.ui.confirmExitWithoutSaving")), "a close straight after Save and"
                + " Continue asked to throw away everything changed since the last save (RSA28-C1): " + asked);

            assertEquals(asked.size(), 1, "the close asked something after Save and Continue, or the switch asked nothing: "
                + asked);

            assertEquals(asked.get(0), I18n.t("layout.ui.confirmSwitchWithUnsavedWork"), "precondition: the switch's question"
                + " was not the first");

            waitForTheSwitch(ui[0], editor);

            Object kept = session.getPointProperty(edited, "maxTrainLength");

            assertTrue(kept instanceof Number && ((Number) kept).intValue() == length, "the saved edit was lost: " + kept);

            assertEquals(editorWindows(ui[0]), 0, "the editor did not close");
        }
        finally
        {
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
     * An editor that fails to build leaves no frame behind (RSA29-C1).
     *
     * `initComponents` makes the frame before the rest of the build runs, and a build that threw after it - a page gone
     * in a reload of the pages, the one route found - left the frame there, never shown.  The main window counts every
     * editor frame of its own (RSA28-B1), so Edit and Edit Autonomy Paths stayed greyed and the gate said "Close the editor
     * first" for the session, with no editor on screen.  The build takes its frame with it now.
     *
     * Driven through the real door with a page the model does not have, chosen in the page list with its listeners off.
     *
     * MUTATION: let the build's failure leave its frame, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEditorThatFailsToBuildLeavesNoFrame() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final javax.swing.JComboBox list = (javax.swing.JComboBox) control(ui[0], "LayoutList");

            final Object[] was = new Object[1];
            final java.awt.event.ActionListener[][] actions = new java.awt.event.ActionListener[1][];
            final java.awt.event.ItemListener[][] items = new java.awt.event.ItemListener[1][];

            final String missing = "No such page (RSA29-C1)";

            // A PAGE THE MODEL DOES NOT HAVE, chosen with the list's listeners off
            SwingUtilities.invokeAndWait(() ->
            {
                was[0] = list.getSelectedItem();
                actions[0] = list.getActionListeners();
                items[0] = list.getItemListeners();

                for (java.awt.event.ActionListener l : actions[0]) list.removeActionListener(l);
                for (java.awt.event.ItemListener l : items[0]) list.removeItemListener(l);

                ((javax.swing.DefaultComboBoxModel) list.getModel()).addElement(missing);
                list.setSelectedItem(missing);
            });

            try
            {
                answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                Thread.sleep(1000);

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
            }
            finally
            {
                SwingUtilities.invokeAndWait(() ->
                {
                    ((javax.swing.DefaultComboBoxModel) list.getModel()).removeElement(missing);
                    list.setSelectedItem(was[0]);

                    for (java.awt.event.ActionListener l : actions[0]) list.addActionListener(l);
                    for (java.awt.event.ItemListener l : items[0]) list.addItemListener(l);
                });
            }

            assertEquals(editorWindows(ui[0]), 0, "an editor that failed to build left its frame (RSA29-C1)");

            assertFalse(ui[0].isLayoutEditorOpen(), "the gate counts an editor that failed to build (RSA29-C1)");

            assertTrue(control(ui[0], "editLayoutButton").isEnabled()
                && control(ui[0], "editAutonomyFromSettings").isEnabled(), "Edit or Edit Autonomy Paths is greyed after an"
                + " editor failed to build (RSA29-C1)");
        }
        finally
        {
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
     * An editor whose build raises an Error, rather than an exception, gives Edit back and opens the gate (RSA30-C6).
     *
     * RSA29-C1's fix disposes the frame and throws on, and `openLayoutEditor`'s catch gives Edit back - but it took only
     * `Exception`, so an Error went past it: Edit stayed greyed, and every train was refused "Close the editor first" for
     * the session, with no editor anywhere.
     *
     * Driven through the real door with a page the model has, whose first use raises an `AssertionError`, chosen in the
     * page list with its listeners off.
     *
     * MUTATION: catch only exceptions there again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEditorWhoseBuildRaisesAnErrorGivesEditBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> pages = null;

        final String failing = "Fails to build (RSA30-C6)";

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            // A PAGE WHOSE FIRST USE RAISES AN ERROR
            java.lang.reflect.Field db = org.traincontrol.marklin.MarklinControlStation.class.getDeclaredField("layoutDB");

            db.setAccessible(true);

            @SuppressWarnings("unchecked")
            org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> held =
                (org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String>) db.get(ui[0].getModel());

            pages = held;

            pages.add(new org.traincontrol.base.LayoutDiagram(failing, 12, 8, null, ui[0].getModel())
            {
                @Override
                public boolean getShowAddress()
                {
                    throw new AssertionError("RSA30-C6: an Error in the editor's build");
                }
            }, failing, failing);

            final javax.swing.JComboBox list = (javax.swing.JComboBox) control(ui[0], "LayoutList");

            final Object[] was = new Object[1];
            final java.awt.event.ActionListener[][] actions = new java.awt.event.ActionListener[1][];
            final java.awt.event.ItemListener[][] items = new java.awt.event.ItemListener[1][];

            SwingUtilities.invokeAndWait(() ->
            {
                was[0] = list.getSelectedItem();
                actions[0] = list.getActionListeners();
                items[0] = list.getItemListeners();

                for (java.awt.event.ActionListener l : actions[0]) list.removeActionListener(l);
                for (java.awt.event.ItemListener l : items[0]) list.removeItemListener(l);

                ((javax.swing.DefaultComboBoxModel) list.getModel()).addElement(failing);
                list.setSelectedItem(failing);
            });

            try
            {
                answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                Thread.sleep(1000);

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
            }
            finally
            {
                SwingUtilities.invokeAndWait(() ->
                {
                    ((javax.swing.DefaultComboBoxModel) list.getModel()).removeElement(failing);
                    list.setSelectedItem(was[0]);

                    for (java.awt.event.ActionListener l : actions[0]) list.addActionListener(l);
                    for (java.awt.event.ItemListener l : items[0]) list.addItemListener(l);
                });
            }

            java.lang.reflect.Field onItsWay = TrainControlUI.class.getDeclaredField("editorOnItsWay");

            onItsWay.setAccessible(true);

            assertEquals(editorWindows(ui[0]), 0, "an editor whose build raised an Error left its frame");

            assertFalse(onItsWay.getBoolean(ui[0]), "an editor whose build raised an Error is still on its way, so every"
                + " train is refused 'Close the editor first' (RSA30-C6)");

            assertTrue(control(ui[0], "editLayoutButton").isEnabled()
                && control(ui[0], "editAutonomyFromSettings").isEnabled(), "Edit or Edit Autonomy Paths is greyed after an"
                + " editor's build raised an Error (RSA30-C6)");
        }
        finally
        {
            if (pages != null) pages.delete(failing);

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
     * An editor whose posted build - the drawing, after the frame is made - raises an Error gives Edit back and opens the
     * gate (RSA31-C3).
     *
     * RSA30-C6 took Errors in `openLayoutEditor`'s catch, which covers the construction; the drawing runs later, in
     * `render`'s posted body, whose catch took only `RuntimeException` - so the hidden frame stayed counted, Edit and Edit
     * Autonomy Paths stayed greyed, and every train was refused "Close the editor first" for the session.
     *
     * Driven through the real door with a page whose size, asked by the drawing, raises an `AssertionError`.
     *
     * MUTATION: let `render`'s catch take exceptions only again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEditorWhoseDrawingRaisesAnErrorGivesEditBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> pages = null;

        final String failing = "Fails to draw (RSA31-C3)";

        // THE OPERATOR WANTS THE MAIN WINDOW ON TOP, put back as it was in the finally
        final String onTopKey = TrainControlUI.ONTOP_SETTING_PREF;
        final String onTopWas = TrainControlUI.getPrefs().get(onTopKey, null);

        try
        {
            TrainControlUI.getPrefs().putBoolean(onTopKey, true);

            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            pages = thePages(ui[0]);

            pages.add(aPageThatFailsToDraw(failing, ui[0]), failing, failing);

            final javax.swing.JComboBox list = (javax.swing.JComboBox) control(ui[0], "LayoutList");

            final Object[] was = new Object[1];
            final java.awt.event.ActionListener[][] actions = new java.awt.event.ActionListener[1][];
            final java.awt.event.ItemListener[][] items = new java.awt.event.ItemListener[1][];

            SwingUtilities.invokeAndWait(() ->
            {
                was[0] = list.getSelectedItem();
                actions[0] = list.getActionListeners();
                items[0] = list.getItemListeners();

                for (java.awt.event.ActionListener l : actions[0]) list.removeActionListener(l);
                for (java.awt.event.ItemListener l : items[0]) list.removeItemListener(l);

                ((javax.swing.DefaultComboBoxModel) list.getModel()).addElement(failing);
                list.setSelectedItem(failing);
            });

            try
            {
                answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

                Thread.sleep(1000);

                for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
            }
            finally
            {
                SwingUtilities.invokeAndWait(() ->
                {
                    ((javax.swing.DefaultComboBoxModel) list.getModel()).removeElement(failing);
                    list.setSelectedItem(was[0]);

                    for (java.awt.event.ActionListener l : actions[0]) list.addActionListener(l);
                    for (java.awt.event.ItemListener l : items[0]) list.addItemListener(l);
                });
            }

            assertEquals(editorWindows(ui[0]), 0, "an editor whose drawing raised an Error left its frame, counted as open"
                + " (RSA31-C3)");

            assertFalse(ui[0].isLayoutEditorOpen(), "the gate counts an editor whose drawing raised an Error (RSA31-C3)");

            assertTrue(control(ui[0], "editLayoutButton").isEnabled()
                && control(ui[0], "editAutonomyFromSettings").isEnabled(), "Edit or Edit Autonomy Paths is greyed after an"
                + " editor's drawing raised an Error (RSA31-C3)");

            // AND THE MAIN WINDOW BACK ON TOP (round 80): opening took the setting off, and the window that failed to
            // draw is the only one that can put it back as it goes
            assertTrue(ui[0].isAlwaysOnTop(), "after an editor failed to draw, the main window is not put back on top as"
                + " the operator's setting asks");
        }
        finally
        {
            if (onTopWas == null) TrainControlUI.getPrefs().remove(onTopKey);
            else TrainControlUI.getPrefs().put(onTopKey, onTopWas);

            if (pages != null) pages.delete(failing);

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
     * A page switch that raises an Error gives the window up, as one that throws an exception does (RSA31-C3's sibling).
     *
     * `arriveAt`'s catch took only `RuntimeException`, so an Error left the window half switched - showing one page and
     * wired to another - and counted as an open editor for the session.
     *
     * The track editor opened on a page, then switched to a page whose size, asked by the drawing, raises an
     * `AssertionError`.
     *
     * MUTATION: let `arriveAt`'s catch take exceptions only again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAPageSwitchThatRaisesAnErrorGivesTheWindowUp() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> pages = null;

        final String failing = "Fails to draw on a switch (RSA31-C3)";

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

            final org.traincontrol.gui.LayoutEditor editor = theEditor(ui[0]);

            assertNotNull(editor, "precondition: the track editor did not open");

            pages = thePages(ui[0]);

            pages.add(aPageThatFailsToDraw(failing, ui[0]), failing, failing);

            final java.lang.reflect.Method arrive = org.traincontrol.gui.LayoutEditor.class.getDeclaredMethod("arriveAt",
                String.class, boolean.class);

            arrive.setAccessible(true);

            final Throwable[] thrown = new Throwable[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    arrive.invoke(editor, failing, false);
                }
                catch (java.lang.reflect.InvocationTargetException e)
                {
                    thrown[0] = e.getCause();
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(500);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(thrown[0] instanceof AssertionError, "precondition: the switch did not raise the page's Error: "
                + thrown[0]);

            assertEquals(editorWindows(ui[0]), 0, "a page switch that raised an Error left its window, half switched and"
                + " counted as open (RSA31-C3)");

            assertTrue(control(ui[0], "editLayoutButton").isEnabled(), "Edit is greyed after a page switch raised an Error"
                + " (RSA31-C3)");
        }
        finally
        {
            if (pages != null) pages.delete(failing);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The model's pages. */
    @SuppressWarnings("unchecked")
    private static org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> thePages(
        TrainControlUI ui) throws ReflectiveOperationException
    {
        java.lang.reflect.Field db = org.traincontrol.marklin.MarklinControlStation.class.getDeclaredField("layoutDB");

        db.setAccessible(true);

        return (org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String>) db.get(ui.getModel());
    }

    /** A page whose size, asked by the editor's drawing, raises an Error - asked by anything else, answers. */
    private static org.traincontrol.base.LayoutDiagram aPageThatFailsToDraw(String name, TrainControlUI ui)
    {
        return new org.traincontrol.base.LayoutDiagram(name, 12, 8, null, ui.getModel())
        {
            @Override
            public int getSx()
            {
                for (StackTraceElement step : Thread.currentThread().getStackTrace())
                {
                    if ("drawGrid".equals(step.getMethodName())) throw new AssertionError("an Error in the drawing");
                }

                return super.getSx();
            }
        };
    }

    /**
     * A Save and Continue at the track editor's switch leaves nothing to ask at a close in the switch's gap (RSA29-C2).
     *
     * RSA28-C1's other half: the track editor asks a close about its undo history, which a save kept until the switch
     * arrived - so a close then asked *"Are you sure you want to close the editor without saving changes?"* straight after
     * the save.  The save clears it now, as the arrival does.
     *
     * A tile turned through the editor's own `rotate`, then Autonomy Setup, Save and Continue, and the close in the gap.
     *
     * MUTATION: keep the undo history at a Save and Continue, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATrackSaveAtASwitchLeavesNothingToAskAtTheClose() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

            final org.traincontrol.gui.LayoutEditor editor = theEditor(ui[0]);

            assertNotNull(editor, "precondition: the track editor did not open");

            assertFalse(editor.isAutonomyMode(), "precondition: the editor opened was not the track editor");

            // A TILE TURNED through the editor's own rotate: a station on the page shown
            java.lang.reflect.Field shown = org.traincontrol.gui.LayoutEditor.class.getDeclaredField("layout");

            shown.setAccessible(true);

            final org.traincontrol.base.LayoutDiagram page = (org.traincontrol.base.LayoutDiagram) shown.get(editor);

            final org.traincontrol.automationui.AutonomySession session = ui[0].getAutonomySession();

            org.traincontrol.automationui.TileGraph.TileKey square = null;

            for (org.traincontrol.automationui.TileGraph.TileKey tile : session.getReducer().getPoints().keySet())
            {
                if (square == null && page.getName().equals(tile.getPage())
                    && page.getComponent(tile.getX(), tile.getY()) != null) square = tile;
            }

            assertNotNull(square, "precondition: no sensor on " + page.getName());

            final org.traincontrol.automationui.TileGraph.TileKey turned = square;

            final int before = page.getComponent(turned.getX(), turned.getY()).getOrientation();

            java.lang.reflect.Field gridField = org.traincontrol.gui.LayoutEditor.class.getDeclaredField("grid");

            gridField.setAccessible(true);

            final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(editor);

            SwingUtilities.invokeAndWait(() -> editor.rotate(grid.getValueAt(turned.getX(), turned.getY())));

            assertTrue(editor.canUndo(), "precondition: the turn is not in the editor's undo history");

            final javax.swing.AbstractButton setup = button(editor.getRootPane(), I18n.t("layout.ui.sidebarAutonomy"));

            assertNotNull(setup, "precondition: no Autonomy Setup in the editor's sidebar");

            final java.lang.reflect.Method close = editor.getClass().getDeclaredMethod("confirmExit");

            close.setAccessible(true);

            // AUTONOMY SETUP, SAVE AND CONTINUE (the question's first answer), AND THE CLOSE IN THE SWITCH'S GAP
            List<String> asked = answeringYes(() ->
            {
                setup.doClick();

                try
                {
                    close.invoke(editor);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertFalse(asked.contains(I18n.t("layout.ui.confirmExitWithoutSaving")), "a close straight after the track"
                + " editor's Save and Continue asked to close without saving (RSA29-C2): " + asked);

            assertEquals(asked.size(), 1, "the close asked something after Save and Continue, or the switch asked nothing: "
                + asked);

            assertEquals(asked.get(0), I18n.t("layout.ui.confirmSwitchWithUnsavedWork"), "precondition: the switch's question"
                + " was not the first");

            waitForTheSwitch(ui[0], editor);

            assertEquals(editorWindows(ui[0]), 0, "the editor did not close");

            org.traincontrol.base.LayoutDiagram reread = ui[0].getModel().getLayout(turned.getPage());

            assertTrue(reread != null && reread.getComponent(turned.getX(), turned.getY()) != null
                && reread.getComponent(turned.getX(), turned.getY()).getOrientation() != before, "the saved turn was lost");
        }
        finally
        {
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
     * @param ui the window
     * @return its editor windows that are still there
     * @throws ReflectiveOperationException reading an editor's window
     */
    private static int editorWindows(TrainControlUI ui) throws ReflectiveOperationException
    {
        java.lang.reflect.Field parent = org.traincontrol.gui.LayoutEditor.class.getDeclaredField("parent");

        parent.setAccessible(true);

        int count = 0;

        for (java.awt.Frame frame : java.awt.Frame.getFrames())
        {
            if (frame instanceof org.traincontrol.gui.LayoutEditor && frame.isDisplayable() && parent.get(frame) == ui) count++;
        }

        return count;
    }

    /**
     * Waits until an editor's switch has arrived, or not, and the refresh behind it is done.
     *
     * @param ui the window
     * @param editor the editor
     * @throws Exception from the event thread
     */
    private static void waitForTheSwitch(TrainControlUI ui, org.traincontrol.gui.LayoutEditor editor) throws Exception
    {
        java.lang.reflect.Field latch = org.traincontrol.gui.LayoutEditor.class.getDeclaredField("changingPage");

        latch.setAccessible(true);

        java.lang.reflect.Field refreshes = TrainControlUI.class.getDeclaredField("refreshesUnderWay");

        refreshes.setAccessible(true);

        long until = System.currentTimeMillis() + 30000;

        while ((latch.getBoolean(editor) || ((java.util.concurrent.atomic.AtomicInteger) refreshes.get(ui)).get() > 0)
            && System.currentTimeMillis() < until)
        {
            Thread.sleep(100);
        }

        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

        Thread.sleep(2000);

        for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * The strip above the track diagram holds its buttons to the page's own button height, at every display scaling
     * (OB-313).
     *
     * Adam, 2026-10-03: *"The issue was on lower display scaling monitors, where the button looked too narrow
     * vertically."*  The strip's Start (and the banner's Load) were held to its checkbox's height less three pixels, with
     * no padding above or below.  The checkbox's font is the one that follows the display's scaling; the page's own
     * buttons - Edit, Small, Large - are a fixed 24.  At 125% the two agreed; at 100% the checkbox is shorter and the
     * strip's button came out a few pixels skinnier than the buttons beside it.
     *
     * Asked at the checkbox size a 100% display gives: the look and feel's own 12-point Segoe UI.
     *
     * MUTATION: hold the button to the checkbox again, and this fails.
     *
     * @throws Exception from the window or reflection
     */
    @Test
    public void testTheStripsButtonsAreAsTallAsThePagesOwn() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final org.traincontrol.gui.AutonomyOverlayToggle strip =
                (org.traincontrol.gui.AutonomyOverlayToggle) control(ui[0], "autonomyOverlayToggle");

            assertNotNull(strip, "precondition: the track diagram has no strip");

            final javax.swing.JComponent edit = control(ui[0], "editLayoutButton");

            java.lang.reflect.Field showField = org.traincontrol.gui.AutonomyOverlayToggle.class.getDeclaredField("show");
            showField.setAccessible(true);
            final javax.swing.JCheckBox show = (javax.swing.JCheckBox) showField.get(strip);

            java.lang.reflect.Field runField = org.traincontrol.gui.AutonomyOverlayToggle.class.getDeclaredField("run");
            runField.setAccessible(true);
            final javax.swing.JButton run = (javax.swing.JButton) runField.get(strip);

            final int[] heights = new int[4];
            final java.awt.Insets[] padding = new java.awt.Insets[2];

            SwingUtilities.invokeAndWait(() ->
            {
                // A 100% DISPLAY's checkbox
                show.setFont(new javax.swing.plaf.FontUIResource("Segoe UI", java.awt.Font.PLAIN, 12));

                strip.syncRun();

                javax.swing.JButton load = new javax.swing.JButton("Load");
                strip.styleAsRunButton(load);

                heights[0] = edit.getPreferredSize().height;
                heights[1] = run.getPreferredSize().height;
                heights[2] = load.getPreferredSize().height;
                heights[3] = show.getPreferredSize().height;

                padding[0] = run.getMargin();
                padding[1] = load.getMargin();
            });

            assertTrue(run.isVisible(), "precondition: the strip shows no button");

            assertEquals(heights[1], heights[0], "at a 100% display's checkbox (" + heights[3] + " px) the strip's button is "
                + heights[1] + " px tall and the page's Edit " + heights[0] + " - too narrow vertically (OB-313)");

            assertEquals(heights[2], heights[0], "the banner's button, styled as the strip's, is " + heights[2] + " px tall"
                + " and the page's Edit " + heights[0] + " (OB-313)");

            assertTrue(padding[0].top >= 2 && padding[0].bottom >= 2 && padding[1].top >= 2 && padding[1].bottom >= 2,
                "the strip's buttons have no padding above and below their text: " + padding[0] + ", " + padding[1]);
        }
        finally
        {
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
     * The turning mark turns about its own centre (MT-650).
     *
     * Adam, 2026-10-02: *"the spinner isn't perfectly centered around itself- looks a little unsmooth (1-2 px off)."*
     * Start's mark is Return Home's (FR-077), and it was drawn on whole pixels with Java's default stroke normalisation,
     * which moves an outline onto the pixel grid differently at each angle - so the ring's centre stepped about as it
     * turned, the more so on a scaled display.  Every frame, at the sizes the two buttons use and at 100% to 200%
     * scaling: where the ink sits, turned back by the frame's angle, must be the same each time.
     *
     * MUTATION: draw the arc with `drawArc` on whole pixels again, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testTheTurningMarkTurnsAboutItsOwnCentre() throws Exception
    {
        Class<?> arcClass = Class.forName("org.traincontrol.gui.TrainControlUI$TurningArc");

        java.lang.reflect.Constructor<?> make = arcClass.getDeclaredConstructor(int.class);
        make.setAccessible(true);

        java.lang.reflect.Method advance = arcClass.getDeclaredMethod("advance");
        advance.setAccessible(true);

        java.lang.reflect.Field angle = arcClass.getDeclaredField("angle");
        angle.setAccessible(true);

        List<String> unsteady = new ArrayList<>();

        double worst = 0;

        for (int size : new int[] {11, 12, 13, 14, 16, 18})
        {
            for (double scale : new double[] {1.0, 1.25, 1.5, 2.0})
            {
                javax.swing.Icon arc = (javax.swing.Icon) make.newInstance(size);

                int side = (int) Math.ceil((size + 2) * scale) + 2;

                // The icon's own centre, in the picture's pixels: painted at (1, 1), scaled
                double cx = scale * (1 + size / 2.0), cy = scale * (1 + size / 2.0);

                List<double[]> turnedBack = new ArrayList<>();

                for (int frame = 0; frame < 12; frame++)
                {
                    java.awt.image.BufferedImage picture =
                        new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);

                    java.awt.Graphics2D g = picture.createGraphics();

                    g.scale(scale, scale);

                    arc.paintIcon(null, g, 1, 1);

                    g.dispose();

                    double ink = 0, sx = 0, sy = 0;

                    for (int y = 0; y < side; y++)
                    {
                        for (int x = 0; x < side; x++)
                        {
                            double a = (picture.getRGB(x, y) >>> 24) / 255.0;

                            ink += a;
                            sx += a * (x + 0.5);
                            sy += a * (y + 0.5);
                        }
                    }

                    assertTrue(ink > 0, "the turning mark drew nothing at size " + size + ", scale " + scale);

                    // Where the ink sits from the centre, turned back by the frame's angle (counterclockwise, y up)
                    double vx = sx / ink - cx, vy = -(sy / ink - cy);

                    double r = Math.toRadians(angle.getInt(arc));

                    turnedBack.add(new double[] {vx * Math.cos(r) + vy * Math.sin(r), -vx * Math.sin(r) + vy * Math.cos(r)});

                    advance.invoke(arc);
                }

                double mx = 0, my = 0;

                for (double[] u : turnedBack)
                {
                    mx += u[0] / turnedBack.size();
                    my += u[1] / turnedBack.size();
                }

                double spread = 0;

                for (double[] u : turnedBack) spread = Math.max(spread, Math.hypot(u[0] - mx, u[1] - my));

                worst = Math.max(worst, spread);

                if (spread > 0.3)
                {
                    unsteady.add(String.format(java.util.Locale.ROOT, "size %d at %.0f%%: %.2f px", size, scale * 100,
                        spread));
                }
            }
        }

        assertEquals(unsteady, new ArrayList<String>(), "the turning mark does not turn about one centre - it steps by"
            + " up to the pixels named as it turns (MT-650; worst " + String.format(java.util.Locale.ROOT, "%.2f", worst)
            + " px)");
    }

    /**
     * Closing the editor while it switches to Track Diagram gives Edit and Edit Autonomy Paths back (MT-649).
     *
     * Adam, 2026-10-02: *"The edit autonomy paths button is greyed out, so this test is moot".*  The switch's own work is
     * posted, and arrives after a close that lands in between - at a closed window, which it put back together out of
     * sight, greying both buttons for the session.  The close wins now: a closed window is not arrived at.
     *
     * MUTATION: drop the closed-window return from `LayoutEditor.arriveAt`, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testClosingTheEditorMidSwitchGivesTheButtonsBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.TRUE, null, true));

            final org.traincontrol.gui.LayoutEditor editor = theEditor(ui[0]);

            assertNotNull(editor, "precondition: the autonomy editor did not open");

            assertTrue(editor.isAutonomyMode(), "precondition: the editor opened was not the autonomy editor");

            final javax.swing.AbstractButton track = button(editor.getRootPane(), I18n.t("layout.ui.sidebarTrack"));

            assertNotNull(track, "precondition: no Track Diagram in the editor's sidebar");

            final java.lang.reflect.Method close = editor.getClass().getDeclaredMethod("confirmExit");

            close.setAccessible(true);

            // TRACK DIAGRAM, AND THE CLOSE BEFORE THE SWITCH'S POSTED WORK HAS RUN
            answeringYes(() ->
            {
                track.doClick();

                try
                {
                    close.invoke(editor);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            // Until the switch has arrived, or not: its latch comes down first thing either way
            java.lang.reflect.Field latch = editor.getClass().getDeclaredField("changingPage");

            latch.setAccessible(true);

            long until = System.currentTimeMillis() + 30000;

            while (latch.getBoolean(editor) && System.currentTimeMillis() < until) Thread.sleep(100);

            assertFalse(latch.getBoolean(editor), "precondition: the switch never finished");

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(1000);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(editor.isDisplayable(), "the editor closed mid-switch was put back together after it closed"
                + " (MT-649)");

            assertTrue(control(ui[0], "editLayoutButton").isEnabled(), "Edit is greyed after the editor was closed while"
                + " it switched to Track Diagram (MT-649)");

            assertTrue(control(ui[0], "editAutonomyFromSettings").isEnabled(), "Edit Autonomy Paths in Track Diagram is"
                + " greyed after the editor was closed while it switched to Track Diagram (MT-649)");
        }
        finally
        {
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
     * @param in where to look
     * @param text the button's text
     * @return the first button saying it, or null
     */
    private static javax.swing.AbstractButton button(java.awt.Container in, String text)
    {
        for (java.awt.Component c : in.getComponents())
        {
            if (c instanceof javax.swing.AbstractButton && text.equals(((javax.swing.AbstractButton) c).getText()))
            {
                return (javax.swing.AbstractButton) c;
            }

            if (c instanceof java.awt.Container)
            {
                javax.swing.AbstractButton found = button((java.awt.Container) c, text);

                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * @param ui the window
     * @return its editor once it is showing, within twenty seconds, or null
     * @throws Exception from the event thread
     */
    private static org.traincontrol.gui.LayoutEditor theEditor(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field open = TrainControlUI.class.getDeclaredField("openEditor");

        open.setAccessible(true);

        long until = System.currentTimeMillis() + 20000;

        while (System.currentTimeMillis() < until)
        {
            SwingUtilities.invokeAndWait(() -> { });

            org.traincontrol.gui.LayoutEditor editor = (org.traincontrol.gui.LayoutEditor) open.get(ui);

            if (editor != null && editor.isDisplayable()) return editor;

            Thread.sleep(100);
        }

        return null;
    }
}
