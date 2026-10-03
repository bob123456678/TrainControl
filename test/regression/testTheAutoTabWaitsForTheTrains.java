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
