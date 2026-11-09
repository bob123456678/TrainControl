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
import static regression.testTheImportDoorReadsAnOldFile.onItsOwnWindow;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import support.Reflect;

/**
 * The Auto tab waits for the trains at both ends of a run it did not see coming: a Start that starts nothing gives it
 * back, and a hand send grays it from its click.  The rule itself, and a whole run, are claimed in
 * `regression.testNoSetupEditDuringARun`, whose helpers these share.  Its own class: each test drives a real run in
 * its window, and every window keeps its threads, so a class of many runs out of heap.
 *
 * @author Adam
 */
public class testTheAutoTabWaitsForTheTrains
{

    /**
     * A Start that starts nothing gives Start and the Auto tab back: every train to run was skipped (here each with no
     * speed), and the railway must clear its own flag, or Start stays grayed and the Auto tab says to wait for trains
     * that are not moving.
     *
     * MUTATION: leave Start's buttons as they are when nothing started, or say nothing, and this fails.
     */
    @Test
    public void testAStartThatStartsNothingGivesTheAutoTabBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        boolean echoWas = org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        final java.util.Map<org.traincontrol.base.Locomotive, Integer> speeds = new java.util.LinkedHashMap<>();

        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        onItsOwnWindow("live-snapshot", ui ->
        {
            // THE POWER ON, which the gate asks
            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

            ui.getModel().go();

            long until = System.currentTimeMillis() + 5000;

            while (!ui.getModel().getPowerState() && System.currentTimeMillis() < until) Thread.sleep(50);

            assertTrue(ui.getModel().getPowerState(), "precondition: the power is not on");

            final org.traincontrol.automation.Layout railway = ui.getModel().getAutoLayout();

            // EVERY TRAIN TO RUN SKIPPED, for want of a speed
            for (org.traincontrol.base.Locomotive loc : railway.getLocomotivesToRun())
            {
                speeds.put(loc, loc.getPreferredSpeed());

                loc.setPreferredSpeed(0);
            }

            assertFalse(speeds.isEmpty(), "precondition: no train is in the run list, so Start would be refused before it"
                + " dispatched");

            final java.util.Map<String, String> atRest = new java.util.LinkedHashMap<>();

            for (String name : WAITING_FOR_THE_TRAINS) atRest.put(name, control(ui, name).getToolTipText());

            // GRACEFUL STOP WATCHED, so its offer and its going are both seen however fast they come
            final javax.swing.JComponent stop = control(ui, "gracefulStop");

            final List<Object> offered = Collections.synchronizedList(new ArrayList<>());

            final java.beans.PropertyChangeListener watch = event -> offered.add(event.getNewValue());

            SwingUtilities.invokeAndWait(() -> stop.addPropertyChangeListener("enabled", watch));

            final java.lang.reflect.Method start =
                Reflect.method(TrainControlUI.class, "startAutonomyActionPerformed", java.awt.event.ActionEvent.class);

            final List<String> answered = answeringYes(() ->
            {
                try
                {
                    start.invoke(ui, new Object[] {null});
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            final javax.swing.JComponent startButton = control(ui, "startAutonomy");

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
                javax.swing.JComponent c = control(ui, name);

                assertTrue(c.isEnabled(), name + " is still greyed after a Start that started nothing (RSA27-C1)");

                assertEquals(c.getToolTipText(), atRest.get(name), name + " did not get its own tooltip back");
            }

            SwingUtilities.invokeAndWait(() -> stop.removePropertyChangeListener("enabled", watch));

            final String nothing = I18n.t("autolayout.ui.errorNothingStarted");

            until = System.currentTimeMillis() + 5000;

            // Start's own answerer may close the message before the watcher above begins, so both lists count
            while (!said.contains(nothing) && !answered.contains(nothing) && System.currentTimeMillis() < until)
            {
                Thread.sleep(100);
            }

            assertTrue(said.contains(nothing) || answered.contains(nothing), "a Start that started nothing said nothing"
                + " - only the log said why: " + said + " " + answered);
        }, ui ->
        {
            going.set(false);

            for (java.util.Map.Entry<org.traincontrol.base.Locomotive, Integer> was : speeds.entrySet())
            {
                was.getKey().setPreferredSpeed(was.getValue());
            }

            if (ui != null && ui.getModel().hasAutoLayout()) ui.getModel().getAutoLayout().stopLocomotives();

            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;
        });
    }

    /**
     * A hand send grays the Auto tab from its click, not once its train sets off: the dispatch sets the route first
     * (seconds on a long one), so the settings would be offered and then refused.  Given back at the train's arrival.
     *
     * MUTATION: leave the Auto tab to the train's departure, or never count the hand send down, and this fails.
     */
    @Test
    public void testAHandSendGreysTheAutoTabFromItsClick() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        final TrainControlUI[] ui = new TrainControlUI[1];

        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        boolean echoWas = org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        support.FastSimulation unpaced = null;

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            ui[0] = sandboxed.hold(openTheWindow());

            // THE POWER ON, which the gate asks
            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

            ui[0].getModel().go();

            long until = System.currentTimeMillis() + 5000;

            while (!ui[0].getModel().getPowerState() && System.currentTimeMillis() < until) Thread.sleep(50);

            assertTrue(ui[0].getModel().getPowerState(), "precondition: the power is not on");

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // UNPACED: the claim is about the click and the arrival, not the seconds the simulation waits at each sensor
            unpaced = support.FastSimulation.unpace(railway);

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
                org.traincontrol.automation.Layout.class, List.class, org.traincontrol.base.Locomotive.class);

            send.setAccessible(true);

            final List<org.traincontrol.automation.Edge> chosen = path;

            final org.traincontrol.base.Locomotive sent = train;

            final List<String> offeredAtTheClick = Collections.synchronizedList(new ArrayList<>());

            answeringYes(() ->
            {
                try
                {
                    send.invoke(ui[0], railway, chosen, sent);

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

            if (unpaced != null) unpaced.close();

            org.traincontrol.marklin.MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            sandboxed.close();
        }
    }

    /**
     * The Auto tab's Edit Autonomy Paths in Track Diagram opens the autonomy editor, whichever editor was used last:
     * the Edit button's code opens the editor used last, the track diagram's after it.
     *
     * MUTATION: send the button to the Edit button's code, and this fails.
     */
    @Test
    public void testTheSettingsTabOpensTheAutonomyEditor() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        onItsOwnWindow("live-snapshot", ui ->
        {
            // THE TRACK EDITOR, USED LAST
            answeringYes(() -> ui.openLayoutEditor(null, Boolean.FALSE, null, true));

            org.traincontrol.gui.layouteditor.LayoutEditor first = theEditor(ui);

            assertNotNull(first, "precondition: the track editor did not open");

            assertFalse(first.isAutonomyMode(), "precondition: the editor opened was not the track editor");

            closeTheEditor(ui);

            // THE SETTINGS TAB'S BUTTON
            final java.lang.reflect.Method press = Reflect.method(TrainControlUI.class,
                "editAutonomyFromSettingsActionPerformed", java.awt.event.ActionEvent.class);

            answeringYes(() ->
            {
                try
                {
                    press.invoke(ui, new Object[] {null});
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            org.traincontrol.gui.layouteditor.LayoutEditor opened = theEditor(ui);

            assertNotNull(opened, "Edit Autonomy Paths in Track Diagram opened no editor");

            assertTrue(opened.isAutonomyMode(), "Edit Autonomy Paths in Track Diagram opened the track editor, the one used"
                + " last, not the autonomy editor");

            closeTheEditor(ui);
        });
    }

    /**
     * An editor stays one editor through its own switches, and is counted open until it closes.
     *
     * Each switch hands over through the main window's teardown, which gives Edit and Edit Autonomy Paths back until
     * the posted arrival grays them again; a press in between opens a second editor, and closing the newer would let
     * Start run trains with the other still open.  So open is asked of the editor windows.  Watched through Autonomy
     * Setup to Track Diagram (both buttons pressed in the gap), back, and a page switch.
     *
     * MUTATION: ask the window's flag, not its editor windows, and this fails.
     */
    @Test
    public void testAnEditorStaysOneThroughItsSwitches() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        onItsOwnWindow("live-snapshot", ui ->
        {
            // EITHER BUTTON OFFERED WHILE AN EDITOR WINDOW IS THERE
            final List<String> offered = Collections.synchronizedList(new ArrayList<String>());

            final String[] during = {"opening"};

            java.beans.PropertyChangeListener watch = e ->
            {
                try
                {
                    if (Boolean.TRUE.equals(e.getNewValue()) && editorWindows(ui) > 0)
                    {
                        offered.add(((javax.swing.AbstractButton) e.getSource()).getText() + " during " + during[0]);
                    }
                }
                catch (ReflectiveOperationException failed)
                {
                    offered.add("the watch failed: " + failed);
                }
            };

            final javax.swing.AbstractButton edit = (javax.swing.AbstractButton) control(ui, "editLayoutButton");
            final javax.swing.AbstractButton paths = (javax.swing.AbstractButton) control(ui, "editAutonomyFromSettings");

            edit.addPropertyChangeListener("enabled", watch);
            paths.addPropertyChangeListener("enabled", watch);

            answeringYes(() -> ui.openLayoutEditor(null, Boolean.TRUE, null, true));

            final org.traincontrol.gui.layouteditor.LayoutEditor editor = theEditor(ui);

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

            waitForTheSwitch(ui, editor);

            assertEquals(editorWindows(ui), 1, "Edit or Edit Autonomy Paths pressed while the autonomy editor handed over to"
                + " Track Diagram opened a second editor (RSA28-B1)");

            assertFalse(editor.isAutonomyMode(), "precondition: the editor did not arrive at Track Diagram");

            // TRACK DIAGRAM TO AUTONOMY SETUP
            during[0] = "the switch to Autonomy Setup";

            final javax.swing.AbstractButton setup = button(editor.getRootPane(), I18n.t("layout.ui.sidebarAutonomy"));

            assertNotNull(setup, "precondition: no Autonomy Setup in the editor's sidebar");

            answeringYes(() -> setup.doClick());

            waitForTheSwitch(ui, editor);

            assertTrue(editor.isAutonomyMode(), "precondition: the editor did not arrive at Autonomy Setup");

            // A PAGE SWITCH
            during[0] = "a page switch";

            final java.lang.reflect.Method step = Reflect.method(editor.getClass(), "stepPage", int.class);

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

            waitForTheSwitch(ui, editor);

            assertEquals(editorWindows(ui), 1, "precondition: the switches left " + editorWindows(ui) + " editors");

            // THE GATE SHUT WHILE IT IS OPEN
            assertTrue(ui.isLayoutEditorOpen(), "the gate does not see the editor that is open (RSA28-B1)");

            assertEquals(offered, new ArrayList<String>(), "Edit or Edit Autonomy Paths was offered while an editor was open"
                + " (RSA28-B1)");

            // AND THE CLOSE - the window's own - GIVES BOTH BACK
            during[0] = "the close";

            final java.lang.reflect.Method close = Reflect.method(editor.getClass(), "confirmExit");

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

            waitForTheSwitch(ui, editor);

            assertEquals(editorWindows(ui), 0, "the editor did not close");

            assertTrue(edit.isEnabled() && paths.isEnabled(), "Edit or Edit Autonomy Paths is still greyed once the editor"
                + " closed");

            // AND A CLOSE THAT GIVES EDIT BACK BEFORE ITS WINDOW GOES - a switch to a page that has gone, a build that
            // failed: asked again once the window has gone
            during[0] = "an editor given up on";

            answeringYes(() -> ui.openLayoutEditor(null, Boolean.FALSE, null, true));

            final org.traincontrol.gui.layouteditor.LayoutEditor again = theEditor(ui);

            assertNotNull(again, "precondition: the track editor did not open again");

            final java.lang.reflect.Method abandon = Reflect.method(again.getClass(), "confirmExitWithoutAsking");

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

            assertEquals(editorWindows(ui), 0, "precondition: the editor given up on is still there");

            assertTrue(edit.isEnabled() && paths.isEnabled(), "Edit or Edit Autonomy Paths is still greyed once an editor"
                + " that gave them back before its window went has gone (RSA28-B1)");

            assertEquals(offered, new ArrayList<String>(), "Edit or Edit Autonomy Paths was offered while an editor was open"
                + " (RSA28-B1)");
        });
    }

    /**
     * A Save and Continue at a switch leaves nothing to ask at a close in the switch's gap: the save must take the undo
     * point again, or the close asks *"throw away everything changed since you last saved"*.
     *
     * MUTATION: keep the old undo point at a Save and Continue, and this fails.
     */
    @Test
    public void testASaveAtASwitchLeavesNothingToAskAtTheClose() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        onItsOwnWindow("live-snapshot", ui ->
        {
            answeringYes(() -> ui.openLayoutEditor(null, Boolean.TRUE, null, true));

            final org.traincontrol.gui.layouteditor.LayoutEditor editor = theEditor(ui);

            assertNotNull(editor, "precondition: the autonomy editor did not open");

            assertTrue(editor.isAutonomyMode(), "precondition: the editor opened was not the autonomy editor");

            // AN EDIT: a station's longest train
            final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

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

            final java.lang.reflect.Method unsaved = Reflect.method(editor.getClass(), "hasUnsavedAutonomyWork");

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

            // TRACK DIAGRAM, SAVE AND CONTINUE (the question's first answer), AND THE CLOSE IN THE SWITCH'S GAP
            List<String> asked = clickAndCloseInTheSwitchsGap(track, editor);

            assertFalse(asked.contains(I18n.t("autosetup.ui.confirmExitWithoutSaving")), "a close straight after Save and"
                + " Continue asked to throw away everything changed since the last save (RSA28-C1): " + asked);

            assertEquals(asked.size(), 1, "the close asked something after Save and Continue, or the switch asked nothing: "
                + asked);

            assertEquals(asked.get(0), I18n.t("layout.ui.confirmSwitchWithUnsavedWork"), "precondition: the switch's question"
                + " was not the first");

            waitForTheSwitch(ui, editor);

            Object kept = session.getPointProperty(edited, "maxTrainLength");

            assertTrue(kept instanceof Number && ((Number) kept).intValue() == length, "the saved edit was lost: " + kept);

            assertEquals(editorWindows(ui), 0, "the editor did not close");
        });
    }

    /**
     * An editor that fails to build leaves no frame behind: the main window counts every editor frame, so one left by a
     * build that threw keeps Edit grayed for the session.  Driven through the real door with a page the model does not
     * have, chosen in the page list with its listeners off.
     *
     * MUTATION: let the build's failure leave its frame, and this fails.
     */
    @Test
    public void testAnEditorThatFailsToBuildLeavesNoFrame() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        onItsOwnWindow("live-snapshot", ui ->
        {
            // A PAGE THE MODEL DOES NOT HAVE, chosen with the list's listeners off
            openTheEditorOnAPageChosenWithTheListenersOff(ui, "No such page (RSA29-C1)");

            assertEquals(editorWindows(ui), 0, "an editor that failed to build left its frame (RSA29-C1)");

            assertFalse(ui.isLayoutEditorOpen(), "the gate counts an editor that failed to build (RSA29-C1)");

            assertTrue(control(ui, "editLayoutButton").isEnabled()
                && control(ui, "editAutonomyFromSettings").isEnabled(), "Edit or Edit Autonomy Paths is greyed after an"
                + " editor failed to build (RSA29-C1)");
        });
    }

    /**
     * An editor whose build raises an Error, rather than an exception, gives Edit back and opens the gate:
     * `openLayoutEditor`'s catch must take an Error too.  Driven with a page whose first use raises an
     * `AssertionError`.
     *
     * MUTATION: catch only exceptions there again, and this fails.
     */
    @Test
    public void testAnEditorWhoseBuildRaisesAnErrorGivesEditBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        final TrainControlUI[] ui = new TrainControlUI[1];

        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> pages = null;

        final String failing = "Fails to build (RSA30-C6)";

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            ui[0] = sandboxed.hold(openTheWindow());

            // A PAGE WHOSE FIRST USE RAISES AN ERROR
            java.lang.reflect.Field db =
                Reflect.field(org.traincontrol.marklin.MarklinControlStation.class, "layoutDB");

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

            openTheEditorOnAPageChosenWithTheListenersOff(ui[0], failing);

            java.lang.reflect.Field onItsWay = Reflect.field(TrainControlUI.class, "editorOnItsWay");

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

            sandboxed.close();
        }
    }

    /**
     * An editor whose posted build (the drawing, after the frame is made) raises an Error gives Edit back and opens
     * the gate: `render`'s catch must take Errors too.  Driven with a page whose size raises an `AssertionError`.
     *
     * MUTATION: let `render`'s catch take exceptions only again, and this fails.
     */
    @Test
    public void testAnEditorWhoseDrawingRaisesAnErrorGivesEditBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        final TrainControlUI[] ui = new TrainControlUI[1];

        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> pages = null;

        final String failing = "Fails to draw (RSA31-C3)";

        // THE OPERATOR WANTS THE MAIN WINDOW ON TOP, put back as it was in the finally
        final String onTopKey = TrainControlUI.ONTOP_SETTING_PREF;
        final String onTopWas = TrainControlUI.getPrefs().get(onTopKey, null);

        try
        {
            TrainControlUI.getPrefs().putBoolean(onTopKey, true);

            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            ui[0] = sandboxed.hold(openTheWindow());

            pages = thePages(ui[0]);

            pages.add(aPageThatFailsToDraw(failing, ui[0]), failing, failing);

            openTheEditorOnAPageChosenWithTheListenersOff(ui[0], failing);

            assertEquals(editorWindows(ui[0]), 0, "an editor whose drawing raised an Error left its frame, counted as open"
                + " (RSA31-C3)");

            assertFalse(ui[0].isLayoutEditorOpen(), "the gate counts an editor whose drawing raised an Error (RSA31-C3)");

            assertTrue(control(ui[0], "editLayoutButton").isEnabled()
                && control(ui[0], "editAutonomyFromSettings").isEnabled(), "Edit or Edit Autonomy Paths is greyed after an"
                + " editor's drawing raised an Error (RSA31-C3)");

            // AND THE MAIN WINDOW BACK ON TOP: opening took the setting off, and the window that failed to
            // draw is the only one that can put it back as it goes
            assertTrue(ui[0].isAlwaysOnTop(), "after an editor failed to draw, the main window is not put back on top as"
                + " the operator's setting asks");
        }
        finally
        {
            if (onTopWas == null) TrainControlUI.getPrefs().remove(onTopKey);
            else TrainControlUI.getPrefs().put(onTopKey, onTopWas);

            if (pages != null) pages.delete(failing);

            sandboxed.close();
        }
    }

    /**
     * A page switch that raises an Error gives the window up, as one that throws an exception does: `arriveAt` must
     * catch more than `RuntimeException`, or the window is left half switched and counted as an open editor.
     *
     * MUTATION: let `arriveAt`'s catch take exceptions only again, and this fails.
     */
    @Test
    public void testAPageSwitchThatRaisesAnErrorGivesTheWindowUp() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        final TrainControlUI[] ui = new TrainControlUI[1];

        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> pages = null;

        final String failing = "Fails to draw on a switch (RSA31-C3)";

        try
        {
            sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

            ui[0] = sandboxed.hold(openTheWindow());

            answeringYes(() -> ui[0].openLayoutEditor(null, Boolean.FALSE, null, true));

            final org.traincontrol.gui.layouteditor.LayoutEditor editor = theEditor(ui[0]);

            assertNotNull(editor, "precondition: the track editor did not open");

            pages = thePages(ui[0]);

            pages.add(aPageThatFailsToDraw(failing, ui[0]), failing, failing);

            final java.lang.reflect.Method arrive = Reflect.method(org.traincontrol.gui.layouteditor.LayoutEditor.class,
                "arriveAt", String.class, boolean.class);

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

            sandboxed.close();
        }
    }

    /** The model's pages. */
    @SuppressWarnings("unchecked")
    private static org.traincontrol.base.RemoteDeviceCollection<org.traincontrol.base.LayoutDiagram, String> thePages(
        TrainControlUI ui) throws ReflectiveOperationException
    {
        java.lang.reflect.Field db = Reflect.field(org.traincontrol.marklin.MarklinControlStation.class, "layoutDB");

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
     * A Save and Continue at the track editor's switch leaves nothing to ask at a close in the switch's gap: the save
     * must clear the undo history as the arrival does, or the close asks *"Are you sure you want to close the editor
     * without saving changes?"*.  A tile is turned through `rotate`, then Autonomy Setup, Save and Continue.
     *
     * MUTATION: keep the undo history at a Save and Continue, and this fails.
     */
    @Test
    public void testATrackSaveAtASwitchLeavesNothingToAskAtTheClose() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        onItsOwnWindow("live-snapshot", ui ->
        {
            answeringYes(() -> ui.openLayoutEditor(null, Boolean.FALSE, null, true));

            final org.traincontrol.gui.layouteditor.LayoutEditor editor = theEditor(ui);

            assertNotNull(editor, "precondition: the track editor did not open");

            assertFalse(editor.isAutonomyMode(), "precondition: the editor opened was not the track editor");

            // A TILE TURNED through the editor's own rotate: a station on the page shown
            java.lang.reflect.Field shown =
                Reflect.field(org.traincontrol.gui.layouteditor.LayoutEditor.class, "layout");

            final org.traincontrol.base.LayoutDiagram page = (org.traincontrol.base.LayoutDiagram) shown.get(editor);

            final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

            org.traincontrol.automationui.TileGraph.TileKey square = null;

            for (org.traincontrol.automationui.TileGraph.TileKey tile : session.getReducer().getPoints().keySet())
            {
                if (square == null && page.getName().equals(tile.getPage())
                    && page.getComponent(tile.getX(), tile.getY()) != null) square = tile;
            }

            assertNotNull(square, "precondition: no sensor on " + page.getName());

            final org.traincontrol.automationui.TileGraph.TileKey turned = square;

            final int before = page.getComponent(turned.getX(), turned.getY()).getOrientation();

            java.lang.reflect.Field gridField =
                Reflect.field(org.traincontrol.gui.layouteditor.LayoutEditor.class, "grid");

            final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(editor);

            SwingUtilities.invokeAndWait(() -> editor.rotate(grid.getValueAt(turned.getX(), turned.getY())));

            assertTrue(editor.canUndo(), "precondition: the turn is not in the editor's undo history");

            final javax.swing.AbstractButton setup = button(editor.getRootPane(), I18n.t("layout.ui.sidebarAutonomy"));

            assertNotNull(setup, "precondition: no Autonomy Setup in the editor's sidebar");

            // AUTONOMY SETUP, SAVE AND CONTINUE (the question's first answer), AND THE CLOSE IN THE SWITCH'S GAP
            List<String> asked = clickAndCloseInTheSwitchsGap(setup, editor);

            assertFalse(asked.contains(I18n.t("layout.ui.confirmExitWithoutSaving")), "a close straight after the track"
                + " editor's Save and Continue asked to close without saving (RSA29-C2): " + asked);

            assertEquals(asked.size(), 1, "the close asked something after Save and Continue, or the switch asked nothing: "
                + asked);

            assertEquals(asked.get(0), I18n.t("layout.ui.confirmSwitchWithUnsavedWork"), "precondition: the switch's question"
                + " was not the first");

            waitForTheSwitch(ui, editor);

            assertEquals(editorWindows(ui), 0, "the editor did not close");

            org.traincontrol.base.LayoutDiagram reread = ui.getModel().getLayout(turned.getPage());

            assertTrue(reread != null && reread.getComponent(turned.getX(), turned.getY()) != null
                && reread.getComponent(turned.getX(), turned.getY()).getOrientation() != before, "the saved turn was lost");
        });
    }

    /**
     * @param ui the window
     * @return its editor windows that are still there
     * @throws ReflectiveOperationException reading an editor's window
     */
    private static int editorWindows(TrainControlUI ui) throws ReflectiveOperationException
    {
        java.lang.reflect.Field parent = Reflect.field(org.traincontrol.gui.layouteditor.LayoutEditor.class, "parent");

        int count = 0;

        for (java.awt.Frame frame : java.awt.Frame.getFrames())
        {
            if (frame instanceof org.traincontrol.gui.layouteditor.LayoutEditor && frame.isDisplayable() && parent.get(frame) == ui) count++;
        }

        return count;
    }

    /** Chooses `page` in the page list with its listeners off, presses Edit, and puts the list back as it was. */
    private static void openTheEditorOnAPageChosenWithTheListenersOff(final TrainControlUI ui, final String page)
        throws Exception
    {
        final javax.swing.JComboBox list = (javax.swing.JComboBox) control(ui, "LayoutList");

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

            ((javax.swing.DefaultComboBoxModel) list.getModel()).addElement(page);
            list.setSelectedItem(page);
        });

        try
        {
            answeringYes(() -> ui.openLayoutEditor(null, Boolean.FALSE, null, true));

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(1000);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                ((javax.swing.DefaultComboBoxModel) list.getModel()).removeElement(page);
                list.setSelectedItem(was[0]);

                for (java.awt.event.ActionListener l : actions[0]) list.addActionListener(l);
                for (java.awt.event.ItemListener l : items[0]) list.addItemListener(l);
            });
        }
    }

    /** Presses `button` and closes `editor` in one answerer run, the close landing in the switch's gap; what was asked. */
    private static List<String> clickAndCloseInTheSwitchsGap(final javax.swing.AbstractButton button,
        final org.traincontrol.gui.layouteditor.LayoutEditor editor) throws Exception
    {
        final java.lang.reflect.Method close = Reflect.method(editor.getClass(), "confirmExit");

        return answeringYes(() ->
        {
            button.doClick();

            try
            {
                close.invoke(editor);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * Waits until an editor's switch has arrived, or not, and the refresh behind it is done.
     *
     * @param ui the window
     * @param editor the editor
     */
    private static void waitForTheSwitch(TrainControlUI ui, org.traincontrol.gui.layouteditor.LayoutEditor editor) throws Exception
    {
        java.lang.reflect.Field latch =
            Reflect.field(org.traincontrol.gui.layouteditor.LayoutEditor.class, "changingPage");

        java.lang.reflect.Field refreshes = Reflect.field(TrainControlUI.class, "refreshesUnderWay");

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
     * (OB-313).  Asked at the checkbox size a 100% display gives: the look and feel's own 12-point Segoe UI.
     *
     * MUTATION: hold the button to the checkbox again, and this fails.
     */
    @Test
    public void testTheStripsButtonsAreAsTallAsThePagesOwn() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        onItsOwnWindow("live-snapshot", ui ->
        {
            final org.traincontrol.gui.AutonomyOverlayToggle strip =
                (org.traincontrol.gui.AutonomyOverlayToggle) control(ui, "autonomyOverlayToggle");

            assertNotNull(strip, "precondition: the track diagram has no strip");

            final javax.swing.JComponent edit = control(ui, "editLayoutButton");

            java.lang.reflect.Field showField = Reflect.field(org.traincontrol.gui.AutonomyOverlayToggle.class, "show");
            final javax.swing.JCheckBox show = (javax.swing.JCheckBox) showField.get(strip);

            java.lang.reflect.Field runField = Reflect.field(org.traincontrol.gui.AutonomyOverlayToggle.class, "run");
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
        });
    }

    /**
     * The banner's Load is in Start's bold, like the strip's Fix it, even when it is styled before Start is bound
     * (OB-391).  A strip with no window, its Start in the window's own font.
     *
     * MUTATION: style the banner's button in `show.getFont()` before Start is bound in `styleAsRunButton`, and this
     * fails.
     */
    @Test
    public void testTheFixItButtonsAreInTheBannersFont() throws Exception
    {
        final java.awt.Font[] fonts = new java.awt.Font[3];
        final String[] said = new String[2];

        SwingUtilities.invokeAndWait(() ->
        {
            org.traincontrol.gui.AutonomyOverlayToggle strip = new org.traincontrol.gui.AutonomyOverlayToggle(null);

            // Styled first, as at start-up, before a layout binds Start
            javax.swing.JButton load = new javax.swing.JButton(I18n.t("autosetup.ui.btnLoadConfiguration"));
            strip.styleAsRunButton(load);

            javax.swing.JButton start = new javax.swing.JButton("Start");
            start.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));

            // Nothing running, so Stop is grayed and the strip stands in for Start
            javax.swing.JButton stop = new javax.swing.JButton("Stop");
            stop.setEnabled(false);

            strip.setLoaded(true);
            strip.bindRunButtons(start, stop);

            javax.swing.JButton run = runOf(strip);

            fonts[2] = run.getFont();
            said[1] = run.getText();

            strip.setFindings(1, 0, 1, 0, null);

            fonts[0] = load.getFont();
            fonts[1] = run.getFont();
            said[0] = run.getText();
        });

        assertEquals(said[0], I18n.t("autosetup.ui.btnFixSetup"), "precondition: with an error the strip does not offer"
            + " Fix it");

        assertEquals(fonts[0], new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12), "the banner's button, styled"
            + " before Start was bound, is not in Start's bold: " + fonts[0]);

        assertEquals(fonts[1], fonts[0], "the strip's Fix it is set in " + fonts[1] + ", the banner's button in "
            + fonts[0]);

        assertEquals(fonts[2], new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12), "the strip's " + said[1] + " no"
            + " longer takes Start's own font");
    }

    /** The strip's own button, the one that stands in for Start, Stop or Fix it. */
    private static javax.swing.JButton runOf(org.traincontrol.gui.AutonomyOverlayToggle strip)
    {
        try
        {
            return (javax.swing.JButton) Reflect.get(org.traincontrol.gui.AutonomyOverlayToggle.class, strip, "run");
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    /**
     * The turning mark turns about its own center (MT-650): every frame, at the sizes the two buttons use and at 100%
     * to 200% scaling, where the ink sits, turned back by the frame's angle, must be the same each time.
     *
     * MUTATION: draw the arc with `drawArc` on whole pixels again, and this fails.
     */
    @Test
    public void testTheTurningMarkTurnsAboutItsOwnCentre() throws Exception
    {
        Class<?> arcClass = Class.forName("org.traincontrol.gui.TrainControlUI$TurningArc");

        java.lang.reflect.Constructor<?> make = arcClass.getDeclaredConstructor(int.class);
        make.setAccessible(true);

        java.lang.reflect.Method advance = Reflect.method(arcClass, "advance");

        java.lang.reflect.Field angle = Reflect.field(arcClass, "angle");

        List<String> unsteady = new ArrayList<>();

        double worst = 0;

        for (int size : new int[] {11, 12, 13, 14, 16, 18})
        {
            for (double scale : new double[] {1.0, 1.25, 1.5, 2.0})
            {
                javax.swing.Icon arc = (javax.swing.Icon) make.newInstance(size);

                int side = (int) Math.ceil((size + 2) * scale) + 2;

                // The icon's own center, in the picture's pixels: painted at (1, 1), scaled
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

                    // Where the ink sits from the center, turned back by the frame's angle (counterclockwise, y up)
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
     * Closing the editor while it switches to Track Diagram gives Edit and Edit Autonomy Paths back (MT-649): the
     * switch's own work is posted and arrives after a close that lands in between, and a closed window is not arrived
     * at.
     *
     * MUTATION: drop the closed-window return from `LayoutEditor.arriveAt`, and this fails.
     */
    @Test
    public void testClosingTheEditorMidSwitchGivesTheButtonsBack() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        onItsOwnWindow("live-snapshot", ui ->
        {
            answeringYes(() -> ui.openLayoutEditor(null, Boolean.TRUE, null, true));

            final org.traincontrol.gui.layouteditor.LayoutEditor editor = theEditor(ui);

            assertNotNull(editor, "precondition: the autonomy editor did not open");

            assertTrue(editor.isAutonomyMode(), "precondition: the editor opened was not the autonomy editor");

            final javax.swing.AbstractButton track = button(editor.getRootPane(), I18n.t("layout.ui.sidebarTrack"));

            assertNotNull(track, "precondition: no Track Diagram in the editor's sidebar");

            // TRACK DIAGRAM, AND THE CLOSE BEFORE THE SWITCH'S POSTED WORK HAS RUN
            clickAndCloseInTheSwitchsGap(track, editor);

            // Until the switch has arrived, or not: its latch comes down first thing either way
            java.lang.reflect.Field latch = Reflect.field(editor.getClass(), "changingPage");

            long until = System.currentTimeMillis() + 30000;

            while (latch.getBoolean(editor) && System.currentTimeMillis() < until) Thread.sleep(100);

            assertFalse(latch.getBoolean(editor), "precondition: the switch never finished");

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(1000);

            for (int i = 0; i < 10; i++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(editor.isDisplayable(), "the editor closed mid-switch was put back together after it closed"
                + " (MT-649)");

            assertTrue(control(ui, "editLayoutButton").isEnabled(), "Edit is greyed after the editor was closed while"
                + " it switched to Track Diagram (MT-649)");

            assertTrue(control(ui, "editAutonomyFromSettings").isEnabled(), "Edit Autonomy Paths in Track Diagram is"
                + " greyed after the editor was closed while it switched to Track Diagram (MT-649)");
        });
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
     */
    private static org.traincontrol.gui.layouteditor.LayoutEditor theEditor(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field open = Reflect.field(TrainControlUI.class, "openEditor");

        long until = System.currentTimeMillis() + 20000;

        while (System.currentTimeMillis() < until)
        {
            SwingUtilities.invokeAndWait(() -> { });

            org.traincontrol.gui.layouteditor.LayoutEditor editor = (org.traincontrol.gui.layouteditor.LayoutEditor) open.get(ui);

            if (editor != null && editor.isDisplayable()) return editor;

            Thread.sleep(100);
        }

        return null;
    }
}
