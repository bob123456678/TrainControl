package ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Keys;
import support.Reflect;
import support.Settle;
import support.Teardown;

/**
 * Start Autonomous Operation and its stops, pressed as the user presses them, and what each leaves (FR-136 Phase 6).
 *
 * The button's worker has refusals of its own after the gate: a route armed to run on a sensor (asked), no train to run,
 * and a railway made invalid since it was loaded.  Each answer, and each refusal, leaves autonomy stopped with Start
 * offered again.  A run that does start is simulated, so its trains move, and is stopped gracefully - from the button and
 * from the track diagram's right-click menu - until the railway says it is idle, with no track held.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
 *
 * @author Adam
 */
public class testStartingAutonomyFromTheWindow
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static org.json.JSONObject asOpened;

    /** The route a claim arms, deleted after it. */
    private static final String ARMED = "FR-136 armed route";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.FrozenEditor frozen = support.FrozenEditor.window("live-snapshot");

        sandbox = frozen.sandbox;
        model = frozen.model;
        ui = frozen.ui;

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is nothing to start");
        }

        asOpened = Components.onTheEventThread(() -> session.snapshotSetup());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null && model.hasAutoLayout()) Settle.gracefully(model.getAutoLayout(), Settle.PATIENCE_MS);
        }
        finally
        {
            Teardown.close(sandbox, model, ui);
        }
    }

    /** The setup as it opened and the railway built from it, simulated, with Start offered. */
    @BeforeMethod
    public void atRest() throws Exception
    {
        if (asOpened == null) throw new SkipException("the class did not set up");

        Components.onTheEventThread(() ->
        {
            session.restoreSetup(asOpened);

            ui.rebuildRunningLayoutFromSetup(false, new java.util.LinkedHashSet<>(
                session.placementsAutonomyWillWrite().values()));

            return null;
        });

        Components.settle();

        Settle.simulate(model.getAutoLayout());

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "precondition: Start is not offered at rest: "
            + start().getToolTipText());
    }

    @AfterMethod(alwaysRun = true)
    public void stopAndTidy() throws Exception
    {
        if (model.hasAutoLayout()) Settle.gracefully(model.getAutoLayout(), Settle.PATIENCE_MS);

        // The next test's `atRest` asserts Start is offered
        if (model.getRoute(ARMED) != null) model.deleteRoute(ARMED);
    }

    /**
     * A route armed to run on a sensor makes Start ask first; No starts nothing, and Start is offered again.
     *
     * MUTATION: read the No as a Yes, and the trains start.
     *
     * First of the class: once answered Yes, the window does not ask again.
     */
    @Test(priority = -2)
    public void testAnArmedRouteAnsweredNoStartsNothing() throws Exception
    {
        arm();

        List<String> asked = press(start(), message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1])), 1);

        assertEquals(asked, java.util.Collections.singletonList(I18n.t("route.ui.confirmConditionalRoutesActiveProceed")),
            "Start did not ask about the armed route, or asked more");

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again after No");

        assertFalse(model.getAutoLayout().isAutoRunning(), "autonomy started after No");

        assertFalse(gracefulStop().isEnabled(), "Graceful Stop is offered after No, with nothing running");
    }

    /**
     * The same question answered Yes starts the run: autonomy runs, a train sets off, and Graceful Stop is offered in
     * Start's place.
     *
     * MUTATION: have the Yes return as No does, and nothing starts.
     */
    @Test(priority = -1)
    public void testAnArmedRouteAnsweredYesStartsTheRun() throws Exception
    {
        arm();

        press(start(), message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0])), 1);

        Layout layout = model.getAutoLayout();

        assertTrue(Settle.until(() -> layout.isAutoRunning(), 30000), "autonomy did not start after Yes");

        assertTrue(Settle.until(() -> !layout.getActiveLocomotives().isEmpty(), Settle.PATIENCE_MS),
            "autonomy started and no train set off");

        assertTrue(Settle.until(() -> gracefulStop().isEnabled() && !start().isEnabled(), 30000), "Graceful Stop is not"
            + " offered in Start's place while the run goes");
    }

    /**
     * Start with no train on the railway says so, starts nothing, and is offered again.
     *
     * MUTATION: drop the return after the sentence, and this fails.
     */
    @Test
    public void testStartWithNoTrainSaysSo() throws Exception
    {
        model.getAutoLayout().getLocomotivesToRun().clear();

        List<String> asked = press(start(), message -> null, 1);

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again");

        assertEquals(said(asked), java.util.Collections.singletonList(I18n.t("autolayout.ui.infoPleaseAddLocomotivesToGraph")),
            "Start with no train to run did not say so, or said more");

        assertFalse(model.getAutoLayout().isAutoRunning(), "autonomy started with no train to run");
    }

    /**
     * Start on a railway made invalid since it was loaded says to load it again, and starts nothing.
     *
     * MUTATION: drop the sentence, and this fails.
     */
    @Test
    public void testStartOnAnInvalidRailwaySaysToLoadItAgain() throws Exception
    {
        model.getAutoLayout().invalidate();

        List<String> asked = press(start(), message -> null, 1);

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again");

        assertEquals(said(asked), java.util.Collections.singletonList(
            I18n.t("autolayout.ui.errorLayoutStateInvalidRevalidateJson")), "Start on an invalid railway did not say so,"
            + " or said more");

        assertFalse(model.getAutoLayout().isAutoRunning(), "autonomy started on an invalid railway");
    }

    /**
     * Start, then Graceful Stop: every train finishes its journey and the railway comes to rest with no track held, and
     * Start is offered again.
     *
     * MUTATION: have Graceful Stop's worker skip `stopLocomotives`, and the run never comes to rest.
     */
    @Test
    public void testGracefulStopLetsTheRunComeToRest() throws Exception
    {
        Layout layout = startTheRun(() -> press(start(), message -> null));

        press(gracefulStop(), message -> null);

        assertRestsWithNothingHeld(layout, "Graceful Stop");
    }

    /**
     * The track diagram's right-click menu starts the run, and its Gracefully Stop Autonomy brings it to rest.
     *
     * MUTATION: have the menu's Gracefully Stop Autonomy do nothing, and the run never comes to rest.
     */
    @Test
    public void testTheDiagramsMenuStartsAndStopsTheRun() throws Exception
    {
        runAndStopFromTheDiagram();
    }

    /**
     * Once Gracefully Stop Autonomy from the diagram's menu has brought every train in, a station's menu is the menu at
     * rest in every group, not half of it (OB-449): Start live, the train's Remove and Autonomy Setup.
     *
     * MUTATION: leave Graceful Stop offered once pressed (drop `this.gracefulStop.setEnabled(false)` in
     * `gracefulStopActionPerformed`), and the menu keeps Start live but loses Remove and Autonomy Setup.
     */
    @Test
    public void testAfterAGracefulStopTheDiagramsMenuIsAtRest() throws Exception
    {
        Layout layout = runAndStopFromTheDiagram();

        // Where a train stands now, on the railway: the run has moved them
        TileKey standing = null;

        for (Point each : layout.getPoints())
        {
            if (standing == null && each.getCurrentLocomotive() != null && each.isDestination())
            {
                standing = session.getStationIndex().squareOf(each);
            }
        }

        assertNotNull(standing, "precondition: no train stands at a station after the run");

        java.util.List<String> live = new ArrayList<>();

        for (AbstractButton item : Components.all(diagramItem(standing, I18n.t("autosetup.ui.menuStationSettings"))
            .getParent(), AbstractButton.class, b -> b.isEnabled())) live.add(item.getText());

        String remove = I18n.f("layout.ui.menuRemoveLocomotive", "#").split("#")[0];

        assertTrue(live.containsAll(java.util.Arrays.asList(I18n.t("autosetup.ui.menuAutonomySetup"),
            I18n.t("autolayout.ui.menuStartAutonomy"))) && live.stream().anyMatch(text -> text.startsWith(remove)),
            "after the trains came in, the menu on " + standing + " is not the menu at rest: " + live);
    }

    // ---------------------------------------------------------------- a page window through a session reset (OB-519)

    /**
     * A page window left open through Stop Using Autonomy shows the next run's path: the reset must not let go of the
     * window's squares, which nothing draws again to register them (OB-519).
     *
     * MUTATION: in `DiagramTileRegistry.reset`, `tiles.clear()` again, and the page window shows no path.
     */
    @Test
    public void testAPageWindowShowsTheNextRunAfterStopUsingAutonomy() throws Exception
    {
        throughAPageWindow(page ->
        {
            press(gracefulStop(), message -> null);

            assertTrue(Settle.idle(model.getAutoLayout(), Settle.PATIENCE_MS), "precondition: the first run did not rest");

            stopUsingAutonomy();

            loadAgain();

            startTheRun(() -> press(start(), message -> null));

            assertTrue(Settle.until(() -> squaresShowing(page, true) > 0, 30000), "a page window left open through Stop"
                + " Using Autonomy shows no train's path in the next run (OB-519)");
        });
    }

    /**
     * Stop Using Autonomy while a path is lit leaves nothing of the run in a page window: no path, no train.
     *
     * MUTATION: in `DiagramTileRegistry.reset`, forget the labels without blanking them, and the path stays.
     */
    @Test
    public void testStopUsingAutonomyMidRunEmptiesAPageWindow() throws Exception
    {
        throughAPageWindow(page ->
        {
            stopUsingAutonomy();

            assertTrue(Settle.until(() -> squaresShowing(page, false) == 0, 10000), "a page window still shows "
                + squaresShowing(page, false) + " squares of a run after Stop Using Autonomy (OB-519)");
        });
    }

    /**
     * A page window open while the pages are read again after a diagram edit, which resets the session, shows the next
     * run's path.
     *
     * MUTATION: with `tiles.clear()` in `DiagramTileRegistry.reset`, leave out both `this.updatePopups(true)` in
     * `TrainControlUI.layoutRefreshCompleteInternal` and `refreshPopupsWhosePagesWereReadAgain()` in `repaintLayout`, and
     * the page window shows no path.
     */
    @Test
    public void testAPageWindowShowsTheRunAfterThePagesAreReadAgain() throws Exception
    {
        throughAPageWindow(page ->
        {
            press(gracefulStop(), message -> null);

            assertTrue(Settle.idle(model.getAutoLayout(), Settle.PATIENCE_MS), "precondition: the first run did not rest");

            final java.util.concurrent.atomic.AtomicInteger underWay =
                (java.util.concurrent.atomic.AtomicInteger) Reflect.get(TrainControlUI.class, ui, "refreshesUnderWay");

            javax.swing.SwingUtilities.invokeAndWait(ui::layoutEditingCompleteFromTheEditor);

            assertTrue(Components.waitFor(() -> underWay.get() == 0, 30000), "precondition: the pages were not read again");

            Settle.theDiagram(ui);

            Settle.simulate(model.getAutoLayout());

            startTheRun(() -> press(start(), message -> null));

            assertTrue(Settle.until(() -> squaresShowing(page, true) > 0, 30000), "a page window open while the pages"
                + " were read again shows no train's path in the next run (OB-519)");
        });
    }

    /**
     * Stop Using Autonomy while a train heads for a station leaves no caption orange, in a page window or the main
     * window, and none once the setup is loaded again with nothing running (OB-519).
     *
     * MUTATION: in `AutonomyLifecycle.resetAutonomySession`, leave out the captions' reset, and the destination stays
     * orange.
     */
    @Test
    public void testStopUsingAutonomyMidRunLeavesNoCaptionOrange() throws Exception
    {
        throughAPageWindow(page ->
        {
            assertTrue(Settle.until(() -> !orangeCaptions().isEmpty(), 30000), "precondition: no caption shows a"
                + " destination during the run");

            stopUsingAutonomy();

            Components.settle();

            assertTrue(orangeCaptions().isEmpty(), "captions are left orange after Stop Using Autonomy: " + orangeCaptions());

            loadAgain();

            assertTrue(orangeCaptions().isEmpty(), "captions are orange with the setup loaded again and nothing running: "
                + orangeCaptions());
        });
    }

    private interface InAPageWindow
    {
        void run(org.traincontrol.gui.LayoutPopupWindow page) throws Exception;
    }

    /**
     * Opens a page window, starts the run and waits until the window shows a path, then hands over; puts the setup back
     * loaded and closes the window afterwards.
     */
    private static void throughAPageWindow(InAPageWindow claim) throws Exception
    {
        org.traincontrol.gui.LayoutPopupWindow page = null;

        try
        {
            final org.traincontrol.gui.LayoutPopupWindow opened = page = support.AskingWindows.openThePage(ui);

            startTheRun(() -> press(start(), message -> null));

            assertTrue(Settle.until(() -> squaresShowing(opened, true) > 0, 30000), "precondition: the page window shows"
                + " no train's path during the run");

            claim.run(opened);
        }
        finally
        {
            support.AskingWindows.closeThePage(page);

            if (ui.getActiveDiagramConfiguration() == null) loadAgain();
        }
    }

    /** Stop Using Autonomy, as the Autonomy menu's item does it, answering Yes to stopping the trains. */
    private static void stopUsingAutonomy() throws Exception
    {
        try (Answering answering = Answering.start(message ->
            Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0]))))
        {
            javax.swing.SwingUtilities.invokeLater(() -> ui.autonomyLifecycle().unloadAutonomy());

            assertTrue(Settle.until(() -> ui.getActiveDiagramConfiguration() == null, 30000), "precondition: Stop Using"
                + " Autonomy left a setup loaded: " + answering.asked());

            Components.settle();
        }
    }

    /** Loads the setup the class opened with, as choosing it in the Autonomy menu does, simulated. */
    private static void loadAgain() throws Exception
    {
        final String name = session.getStore().getActiveConfiguration();

        javax.swing.SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(name, false));

        assertTrue(Settle.until(() -> name.equals(ui.getActiveDiagramConfiguration()), 30000), "precondition: " + name
            + " did not load");

        Components.settle();

        Settle.simulate(model.getAutoLayout());
    }

    /** How many of a window's squares show a path ahead (`ahead`), or anything of a run at all. */
    private static int squaresShowing(java.awt.Window window, boolean ahead)
    {
        return Components.onTheEventThread(() ->
        {
            int count = 0;

            for (org.traincontrol.gui.LayoutLabel square
                : Components.all(window, org.traincontrol.gui.LayoutLabel.class, label -> true))
            {
                org.traincontrol.automationui.TileOverlay shown = (org.traincontrol.automationui.TileOverlay)
                    Reflect.get(org.traincontrol.gui.LayoutLabel.class, square, "autonomyOverlay");

                if (shown != null && (!ahead || shown.getSegments().stream().anyMatch(each ->
                    each.getState() == org.traincontrol.automationui.TileOverlay.State.ACTIVE))) count++;
            }

            return count;
        });
    }

    /** The captions on screen drawn as a destination (a caption with no text draws nothing), with their windows. */
    private static List<String> orangeCaptions()
    {
        return Components.onTheEventThread(() ->
        {
            List<String> out = new ArrayList<>();

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!window.isShowing()) continue;

                for (javax.swing.JLabel caption : Components.all(window, javax.swing.JLabel.class, label ->
                    label.isShowing() && label.getText() != null && !label.getText().isEmpty()
                    && org.traincontrol.gui.widgets.StationCaption.isDestination(label.getBackground())))
                {
                    out.add(caption.getText() + " in " + window.getClass().getSimpleName());
                }
            }

            return out;
        });
    }

    // ---------------------------------------------------------------- the keys (FR-200)

    /**
     * Control+G starts the run as Start does: autonomy runs and a train sets off.
     *
     * MUTATION: drop the "start autonomous operation" row from `TrainControlUI.KeyShortcuts`, and nothing starts.
     */
    @Test
    public void testControlGStartsTheRun() throws Exception
    {
        startTheRun(() -> control(KeyEvent.VK_G, message -> null));
    }

    /**
     * Control+Q brings a run to rest as Graceful Stop does.
     *
     * MUTATION: drop the "stop autonomy gracefully" row from `TrainControlUI.KeyShortcuts`, and the run never comes to
     * rest.
     */
    @Test
    public void testControlQLetsTheRunComeToRest() throws Exception
    {
        Layout layout = startTheRun(() -> press(start(), message -> null));

        control(KeyEvent.VK_Q, message -> null);

        assertRestsWithNothingHeld(layout, "Control+Q");
    }

    /**
     * Control+G with the power off starts nothing and says why on screen, once, as Start does: in the log alone, Adam
     * saw no effect (MT-1048).
     *
     * MUTATION: pass `false` for `shown` at the row's second `refusedByKey`, and the refusal is only logged.
     */
    @Test
    public void testControlGWithThePowerOffSaysWhyOnScreen() throws Exception
    {
        support.SettingsWindow.power(ui, model, false);

        try
        {
            assertEquals(control(KeyEvent.VK_G, message -> null), java.util.Collections.singletonList(
                I18n.t("autolayout.ui.powerOnToStart")), "Control+G with the power off did not say why on screen, once");

            assertFalse(model.getAutoLayout().isAutoRunning(), "Control+G started the run with the power off");
        }
        finally
        {
            support.SettingsWindow.power(ui, model, true);
        }
    }

    /**
     * Control+G over a setup with an error, here a station without a name, starts nothing and says why on screen, once.
     *
     * MUTATION: pass `false` for `shown` where the row refuses with `whyAutonomyWillNotStart`, and the refusal is only
     * logged.
     */
    @Test
    public void testControlGOverASetupWithErrorsSaysWhyOnScreen() throws Exception
    {
        final TileKey station = Components.onTheEventThread(() ->
        {
            for (TileKey each : session.getStore().getNamedTiles())
            {
                if (session.getStore().isStation(each)) return each;
            }

            return null;
        });

        assertNotNull(station, "precondition: no named station");

        Components.onTheEventThread(() ->
        {
            session.getStore().setPointName(station, "");
            session.rebuild();

            return null;
        });

        Components.settle();

        assertTrue(Components.onTheEventThread(() -> ui.autonomyHasErrors()), "precondition: the setup has no error");

        String why = Components.onTheEventThread(() -> ui.whyAutonomyWillNotStart());

        assertEquals(control(KeyEvent.VK_G, message -> null), java.util.Collections.singletonList(why),
            "Control+G over a setup with errors did not say why on screen, once");
        assertFalse(model.getAutoLayout().isAutoRunning(), "Control+G started the run over a setup with errors");
    }

    /**
     * Control+G held with the power off says why once: repeats of Control+G and Control+H that reach the key table while
     * its message is up show nothing more.
     *
     * MUTATION: drop `ui.keysAsking > 0 ||` from the "start autonomous operation" row, and the repeat says it again.
     */
    @Test
    public void testControlGHeldSaysWhyOnce() throws Exception
    {
        support.SettingsWindow.power(ui, model, false);

        try
        {
            offerReturnHome();

            final java.awt.Component keyboard = field("KeyboardTab");

            final java.util.concurrent.atomic.AtomicBoolean repeated = new java.util.concurrent.atomic.AtomicBoolean();

            List<String> asked = control(KeyEvent.VK_G, message ->
            {
                if (repeated.compareAndSet(false, true)) repeat(keyboard, KeyEvent.VK_G, KeyEvent.VK_H);

                return null;
            });

            assertEquals(asked, java.util.Collections.singletonList(I18n.t("autolayout.ui.powerOnToStart")),
                "Control+G held said more over its own message");
        }
        finally
        {
            support.SettingsWindow.power(ui, model, true);
        }
    }

    /**
     * Control+G on a page window with the power off says why over that window, once: repeats on the page and on the
     * main window show nothing more.
     *
     * MUTATION: `this` for `dialogParent()` in `refusedByKey`, and the message hangs off the main window.
     */
    @Test
    public void testControlGOnAPageWindowSaysWhyOverItOnce() throws Exception
    {
        support.SettingsWindow.power(ui, model, false);

        org.traincontrol.gui.LayoutPopupWindow page = null;

        try
        {
            final org.traincontrol.gui.LayoutPopupWindow opened = page = support.AskingWindows.openThePage(ui);

            final java.awt.Component keyboard = field("KeyboardTab");

            final java.util.concurrent.atomic.AtomicBoolean repeated = new java.util.concurrent.atomic.AtomicBoolean();

            try (Answering answering = Answering.start(message ->
            {
                if (repeated.compareAndSet(false, true))
                {
                    repeat(opened, KeyEvent.VK_G, KeyEvent.VK_H);
                    repeat(keyboard, KeyEvent.VK_G);
                }

                return null;
            }))
            {
                repeat(opened, KeyEvent.VK_G);

                answering.awaitAsked(2, 3000);

                Components.settle();

                assertEquals(answering.asked(), java.util.Collections.singletonList(I18n.t("autolayout.ui.powerOnToStart")),
                    "Control+G on a page window did not say why once");

                support.AskingWindows.assertSaid(answering.questions().get(0), opened, I18n.t("autolayout.ui.powerOnToStart"));
            }
        }
        finally
        {
            support.AskingWindows.closeThePage(page);

            support.SettingsWindow.power(ui, model, true);
        }
    }

    /**
     * Control+H with the power off and Return Home offered (the power does not gray it) asks nothing and says why on
     * screen, as the button does.
     *
     * MUTATION: pass `false` for `shown` at `returnHomeByKey`'s second `refusedByKey`, and the refusal is only logged.
     */
    @Test
    public void testControlHWithThePowerOffSaysWhyOnScreen() throws Exception
    {
        support.SettingsWindow.power(ui, model, false);

        try
        {
            List<Boolean> grayed = offerReturnHome();

            assertEquals(control(KeyEvent.VK_H, message -> null), java.util.Collections.singletonList(
                I18n.t("autolayout.ui.powerOnToStart")), "Control+H with the power off did not say why on screen, once");

            assertEquals(grayed, new ArrayList<Boolean>(), "Control+H with the power off set off for home");
        }
        finally
        {
            support.SettingsWindow.power(ui, model, true);
        }
    }

    /**
     * Control+G while Return Home plans, while a train is sent by hand, or during a run, presses nothing, asks nothing and
     * says in the log that trains are running: the diagram's menu offers no Start then.
     *
     * MUTATION: drop the `isAutonomyBusy` question from the "start autonomous operation" row, and Start is pressed.
     */
    @Test
    public void testControlGWhileTrainsAreBusyOnlySaysSo() throws Exception
    {
        java.lang.reflect.Field planning = Reflect.field(TrainControlUI.class, "stagingFlowActive");

        java.util.concurrent.atomic.AtomicInteger sending = (java.util.concurrent.atomic.AtomicInteger) Reflect.get(
            Layout.class, model.getAutoLayout(), "locomotiveThreads");

        for (String busy : new String[] {"Return Home planning", "a hand send", "a run"})
        {
            if (busy.startsWith("Return")) planning.set(ui, true);
            else if (busy.startsWith("a hand")) sending.incrementAndGet();
            else startTheRun(() -> press(start(), message -> null));

            boolean offered = start().isEnabled();

            // The log drops a line that repeats the one before it
            model.log("FR-200 " + busy);

            try (Answering answering = Answering.start(message -> null))
            {
                List<String> said = logged(() -> { control(KeyEvent.VK_G); return null; });

                // Start's worker says its refusals a moment later
                answering.awaitAsked(1, 3000);

                assertEquals(answering.asked(), new ArrayList<String>(), "Control+G during " + busy + " asked something");

                assertTrue(said.contains(running()), "Control+G during " + busy + " did not say why: " + said);

                assertEquals(start().isEnabled(), offered, "Control+G during " + busy + " pressed Start");
            }
            finally
            {
                if (busy.startsWith("Return")) planning.set(ui, false);
                else if (busy.startsWith("a hand")) sending.decrementAndGet();
            }
        }
    }

    /**
     * Control+H where Return Home is not offered says the button's reason in the log in plain words, not its tooltip's
     * markup.  Grayed through the door Return Home's triage uses, with every train home: the button, offered first,
     * ends grayed with the reason as its tooltip.
     *
     * MUTATION: have `returnHomeByKey` log `whyReturnHomeIsNotOffered()`, the tooltip, and the line is HTML; drop
     * `setEnabled(false)` in `ReturnHomeController.disable`, and the button stays offered.
     */
    @Test
    public void testControlHNotOfferedSaysWhyInPlainWords() throws Exception
    {
        offerReturnHome();

        Components.settle();

        final String home = (String) Reflect.method(home().getClass(), "describeStagingOutcome",
            org.traincontrol.automation.HomeStaging.Outcome.class, List.class).invoke(home(),
            org.traincontrol.automation.HomeStaging.Outcome.ALREADY_HOME, null);

        final java.lang.reflect.Method gray = Reflect.method(TrainControlUI.class, "disableReturnHome", String.class);

        Components.onTheEventThread(() -> gray.invoke(ui, home));

        final JButton button = field("returnHomeButton");

        assertFalse(button.isEnabled(), "Return Home refused left the button offered");

        assertTrue(button.getToolTipText().contains(home), "the grayed button does not give the reason: "
            + button.getToolTipText());

        List<String> asked = new ArrayList<>();

        List<String> said = logged(() -> asked.addAll(control(KeyEvent.VK_H, message -> null)));

        assertEquals(asked, new ArrayList<String>(), "Control+H, not offered, asked something");

        assertTrue(said.contains(home), "Control+H did not log the reason in plain words: " + said);
    }

    /**
     * Control+H asks before sending every train home, which the button does not (D65): No sends nothing, and Yes goes
     * through Return Home's door, which grays the button as it sets off.
     *
     * MUTATION: have `returnHomeByKey` call `requestReturnToHome` without asking, and No sends the trains home.
     */
    @Test
    public void testControlHAsksFirst() throws Exception
    {
        List<Boolean> grayed = offerReturnHome();

        assertEquals(control(KeyEvent.VK_H, message -> Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1]))),
            java.util.Collections.singletonList(I18n.t("autolayout.ui.confirmReturnToHome")),
            "Control+H did not ask first, or asked more");

        assertEquals(grayed, new ArrayList<Boolean>(), "Control+H answered No set off for home");

        try (Answering answering = Answering.start(message -> null))
        {
            control(KeyEvent.VK_H);

            assertEquals(answering.asked().get(0), I18n.t("autolayout.ui.confirmReturnToHome"),
                "Control+H did not ask first");

            assertFalse(grayed.isEmpty(), "Control+H answered Yes did not set off for home: " + answering.asked());

            // Until the trip ends, with its answers given here
            assertTrue(Settle.until(() -> !(Boolean) field("stagingFlowActive"), Settle.PATIENCE_MS),
                "the trip home did not end");

            Components.settle();
        }
    }

    /**
     * Control+H pressed again while its question is up asks nothing more: a held key repeats.
     *
     * MUTATION: drop `keysAsking > 0` from `returnHomeByKey`'s first return, and the repeat asks over the question.
     */
    @Test
    public void testControlHHeldAsksOnce() throws Exception
    {
        offerReturnHome();

        final java.awt.Component keyboard = field("KeyboardTab");

        final java.util.concurrent.atomic.AtomicBoolean repeated = new java.util.concurrent.atomic.AtomicBoolean();

        List<String> asked = control(KeyEvent.VK_H, message ->
        {
            // The repeat, delivered while the question is up and before it is answered
            if (repeated.compareAndSet(false, true)) javax.swing.SwingUtilities.invokeLater(() -> keyboard.dispatchEvent(
                new KeyEvent(keyboard, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK,
                    KeyEvent.VK_H, 'H')));

            return Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1]));
        });

        assertEquals(asked, java.util.Collections.singletonList(I18n.t("autolayout.ui.confirmReturnToHome")),
            "Control+H held asked again over its own question");
    }

    /**
     * Control+H during a run asks nothing and says in the log that trains are running.
     *
     * MUTATION: have `returnHomeByKey` ask its question without its refusals first, and it asks during the run.
     */
    @Test
    public void testControlHDuringARunOnlySaysSo() throws Exception
    {
        startTheRun(() -> press(start(), message -> null));

        List<String> asked = new ArrayList<>();

        List<String> said = logged(() -> asked.addAll(control(KeyEvent.VK_H, message -> null)));

        assertEquals(asked, new ArrayList<String>(), "Control+H during the run asked something");

        assertTrue(said.contains(running()), "Control+H during the run did not say the trains are running: " + said);
    }

    /**
     * The diagram's Start, Gracefully Stop and Return Home items show the table's keys, and do not act on them
     * themselves: the table already does.
     *
     * MUTATION: drop the `showShortcut` of the Start item in `DiagramAutonomyContextMenu`.
     */
    @Test
    public void testTheDiagramsItemsShowTheirKeys() throws Exception
    {
        TileKey square = aStationWithATrain();

        String[][] rows = {{"autolayout.ui.menuStartAutonomy", "start autonomous operation"},
            {"autolayout.ui.menuReturnToHome", "return locomotives home"}};

        for (String[] row : rows) assertKey(diagramItem(square, I18n.t(row[0])), row[1]);

        startTheRun(() -> press(start(), message -> null));

        assertKey(diagramItem(square, I18n.t("autolayout.ui.menuStopAutonomyGracefully")), "stop autonomy gracefully");
    }

    private static void assertKey(AbstractButton item, String row)
    {
        javax.swing.KeyStroke keys = TrainControlUI.KeyShortcuts.named(row).stroke();

        assertEquals(((javax.swing.JMenuItem) item).getAccelerator(), keys, item.getText() + " does not show " + row);

        assertEquals(item.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(keys), "none",
            item.getText() + " acts on its key itself");
    }

    /** Presses Control and a key on the window, answering by the rule, and gives what it asked. */
    private static List<String> control(int key, java.util.function.Function<String, Answering.Answer> rule)
        throws Exception
    {
        try (Answering answering = Answering.start(rule))
        {
            control(key);

            Components.settle();

            return answering.asked();
        }
    }

    /** The press itself, with the window on screen: the focus manager drops a key for a window that is not showing. */
    private static void control(int key) throws Exception
    {
        onScreen(() ->
        {
            Keys.press(field("KeyboardTab"), key, InputEvent.CTRL_DOWN_MASK);

            return null;
        });
    }

    /** Control and this key pressed on a component. */
    private static KeyEvent controlAnd(java.awt.Component on, int key)
    {
        return new KeyEvent(on, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, key, (char) key);
    }

    /**
     * Control and these keys as a held key's repeats reach the component's own listeners, posted, so a repeat sent while
     * a message is up lands before it is answered: past the modal message, which keeps one posted through the queue.
     */
    private static void repeat(java.awt.Component on, int... keys)
    {
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            for (int key : keys) for (java.awt.event.KeyListener each : on.getKeyListeners()) each.keyPressed(controlAnd(on, key));
        });
    }

    /** The window's Return Home controller, which words its outcomes. */
    private static Object home() throws Exception
    {
        return Reflect.get(TrainControlUI.class, ui, "returnHomeController");
    }

    /** The sentence for trains running, as Return Home's button says it. */
    private static String running() throws Exception
    {
        return (String) Reflect.method(home().getClass(), "describeStagingOutcome",
            org.traincontrol.automation.HomeStaging.Outcome.class, List.class).invoke(home(),
            org.traincontrol.automation.HomeStaging.Outcome.LOCOMOTIVES_RUNNING, null);
    }

    /** What the window logs while this runs. */
    private static List<String> logged(java.util.concurrent.Callable<?> doThis) throws Exception
    {
        List<String> said = java.util.Collections.synchronizedList(new ArrayList<>());

        java.util.logging.Handler tap = support.LogTap.messagesInto(said);

        java.util.logging.Logger at = java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());

        at.addHandler(tap);

        try
        {
            doThis.call();
        }
        finally
        {
            at.removeHandler(tap);
        }

        return said;
    }

    /**
     * Return Home offered, as the button offers it once its triage has answered: on the frozen railway every train may
     * be home already.  Gives the times the button is grayed from now on.
     */
    private static List<Boolean> offerReturnHome() throws Exception
    {
        assertTrue((Boolean) Reflect.method(TrainControlUI.class, "awaitReturnHomeTriage", long.class).invoke(ui, 30000L),
            "precondition: the Return Home triage did not settle");

        final JButton button = field("returnHomeButton");

        final List<Boolean> grayed = java.util.Collections.synchronizedList(new ArrayList<>());

        Components.onTheEventThread(() ->
        {
            button.setEnabled(true);

            button.addPropertyChangeListener("enabled", e -> { if (Boolean.FALSE.equals(e.getNewValue())) grayed.add(true); });

            return null;
        });

        return grayed;
    }

    // ---------------------------------------------------------------- doors

    /**
     * MT-1001 steps 1 and 2: with Toggle specified routes ticked and only R1 listed, R1 switched off and R2 on by hand,
     * Start Autonomous Operation switches R1 on again and R2 off, as their right-click menus read.
     *
     * MUTATION: leave the `applyAutonomyRouteActivations()` call out of `Layout.runLocomotives` and this fails.
     */
    @Test
    public void testStartHoldsToTheRouteListOverTheHandSwitches() throws Exception
    {
        onScreen(true);
    }

    /**
     * MT-1001 step 5: with Toggle specified routes unticked, Start leaves the hand switches as they were.
     *
     * MUTATION: in `MarklinControlStation.applyAutonomyRouteActivations`, drop the `isActivateRoutes()` check and this
     * fails.
     */
    @Test
    public void testStartLeavesTheHandSwitchesWithToggleUnticked() throws Exception
    {
        onScreen(false);
    }

    /**
     * Start above the track diagram ends at the diagram's right edge, to the pixel on screen (OB-473).
     *
     * MUTATION: give `AutonomyOverlayToggle`'s border a right inset of 4 again, and this fails.
     */
    @Test
    public void testStartEndsAtTheDiagramsRightEdge() throws Exception
    {
        java.awt.Component diagram = field("LayoutArea");

        onScreen(() ->
        {
            AbstractButton copy = Components.onTheEventThread(() ->
            {
                ((javax.swing.JTabbedPane) field("KeyboardTab")).setSelectedComponent(field("layoutPanel"));

                return Components.withText(field("autonomyOverlayToggle"), I18n.t("ui.main.startAutonomy"));
            });

            assertNotNull(copy, "precondition: the strip above the diagram offers no Start");

            assertTrue(Settle.until(copy::isShowing, 30000), "precondition: the strip's Start is not on screen");

            int[] rights = Components.onTheEventThread(() -> new int[] {
                copy.getLocationOnScreen().x + copy.getWidth(), diagram.getLocationOnScreen().x + diagram.getWidth()});

            assertEquals(rights[0], rights[1], "Start does not end at the track diagram's right edge");

            return null;
        });
    }

    /**
     * MT-1001's check with Start as the run's door, the window shown for it: the route list's right-click menu only
     * opens on a window on screen.
     */
    private static void onScreen(boolean toggled) throws Exception
    {
        onScreen(() ->
        {
            support.RouteListDoors.HandSwitched.runAndRead(ui, toggled, "Start", () -> startAnsweredYes());

            return null;
        });
    }

    /** Runs the work with the window on screen, hidden again after: this class's window is otherwise never shown. */
    private static void onScreen(java.util.concurrent.Callable<?> work) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            ui.setVisible(true);

            return null;
        });

        try
        {
            work.call();
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                ui.setVisible(false);

                return null;
            });
        }
    }

    /** Presses Start, answers its question Yes, and waits for the run; `stopAndTidy` stops it. */
    private static Object startAnsweredYes()
    {
        try (Answering answering = Answering.start(message ->
            Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0]))))
        {
            Components.press(start());

            assertTrue(Settle.until(() -> model.getAutoLayout().isAutoRunning(), 30000), "autonomy did not start: "
                + answering.asked());
        }

        return null;
    }

    private interface Pressing
    {
        List<String> press() throws Exception;
    }

    /** Starts the run and stops it gracefully from the diagram's menu, and waits until it rests with nothing held. */
    private static Layout runAndStopFromTheDiagram() throws Exception
    {
        final TileKey square = aStationWithATrain();

        Layout layout = startTheRun(() -> press(diagramItem(square, I18n.t("autolayout.ui.menuStartAutonomy")),
            message -> null));

        press(diagramItem(square, I18n.t("autolayout.ui.menuStopAutonomyGracefully")), message -> null);

        assertRestsWithNothingHeld(layout, "the diagram's Gracefully Stop Autonomy");

        return layout;
    }

    /** Starts the run through a door, and waits until autonomy runs with a train under way. */
    private static Layout startTheRun(Pressing door) throws Exception
    {
        Layout layout = model.getAutoLayout();

        List<String> asked = door.press();

        assertTrue(Settle.until(() -> layout.isAutoRunning(), 30000), "autonomy did not start: " + asked);

        assertTrue(Settle.until(() -> !layout.getActiveLocomotives().isEmpty(), Settle.PATIENCE_MS),
            "autonomy started and no train set off: " + asked);

        return layout;
    }

    private static void assertRestsWithNothingHeld(Layout layout, String how)
    {
        assertTrue(Settle.idle(layout, Settle.PATIENCE_MS), "the run did not come to rest after " + how + ": trains under"
            + " way " + layout.getActiveLocomotives().keySet());

        assertFalse(layout.isAutoRunning(), "autonomy still runs after " + how);

        List<String> held = new ArrayList<>();

        for (Edge e : layout.getEdges())
        {
            if (e.isLockHeld(null)) held.add(e.getName());
        }

        assertEquals(held, new ArrayList<String>(), "track is still held after " + how);

        assertTrue(Settle.until(() -> start().isEnabled(), 30000), "Start is not offered again after " + how);
    }

    /** A route armed to run when a sensor is occupied, which Start asks about. */
    private static void arm() throws Exception
    {
        assertTrue(model.newRoute(ARMED, new ArrayList<>(), 0,
            org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null), "precondition: no route made");

        ((org.traincontrol.marklin.MarklinRoute) model.getRoute(ARMED)).enable();
    }

    private static JButton start()
    {
        return field("startAutonomy");
    }

    private static JButton gracefulStop()
    {
        return field("gracefulStop");
    }

    /** The window's component of this field name, as the type the caller asks for. */
    @SuppressWarnings("unchecked")
    private static <T> T field(String name)
    {
        try
        {
            return (T) Reflect.get(TrainControlUI.class, ui, name);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("the window has no " + name, e);
        }
    }

    /** A square with a train standing on it, on any page. */
    private static TileKey aStationWithATrain()
    {
        for (TileKey tile : session.placementsAutonomyWillWrite().keySet()) return tile;

        throw new AssertionError("precondition: no train stands on the frozen railway");
    }

    /**
     * An item of the track diagram's right-click menu on a square, the menu built as a right-click builds it: the
     * railway asked first, then the menu made from the answer.
     */
    private static AbstractButton diagramItem(final TileKey square, String text) throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.DiagramAutonomyContextMenu");

        final java.lang.reflect.Method gather =
            Reflect.method(menuClass, "gatherPathOptions", TrainControlUI.class, Point.class);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
            TileKey.class, TileKey.class, Class.forName("org.traincontrol.gui.DiagramAutonomyContextMenu$PathOptions"));

        make.setAccessible(true);

        final Point standing = Components.onTheEventThread(() -> ui.stationCaptions().getAutonomyPointForTile(square));

        final Object options = gather.invoke(null, ui, standing);

        javax.swing.JPopupMenu menu = Components.onTheEventThread(
            () -> (javax.swing.JPopupMenu) make.newInstance(ui, square, square, options));

        AbstractButton item = Components.withText(menu, text);

        assertNotNull(item, "precondition: the diagram's menu on " + square + " has no \"" + text + "\"");

        return item;
    }

    /**
     * Presses a button as a click does, answering each question by the rule, and returns what was asked once the click's
     * own work, and the worker it starts, have had their say.
     */
    private static List<String> press(final AbstractButton button,
        java.util.function.Function<String, Answering.Answer> rule) throws Exception
    {
        return press(button, rule, 0);
    }

    /** The same, waiting for this many questions, which the worker posts once it has decided. */
    private static List<String> press(final AbstractButton button,
        java.util.function.Function<String, Answering.Answer> rule, int questions) throws Exception
    {
        try (Answering answering = Answering.start(rule))
        {
            Components.press(button);

            Components.settle();

            answering.awaitAsked(questions, 30000);

            Components.settle();

            return answering.asked();
        }
    }

    private static List<String> said(List<String> asked)
    {
        return new ArrayList<>(asked);
    }
}
