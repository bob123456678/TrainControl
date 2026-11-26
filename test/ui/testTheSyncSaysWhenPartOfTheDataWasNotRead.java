package ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Reflect;
import support.Settle;
import support.StandInStation;
import support.StationRig;

/**
 * The sync says in plain words when part of what the Central Station sent could not be read and was left as it was
 * (FR-169): the dialog the Sync menu raises, and the notice a start-up raises.  The sync is made through the real window
 * against a stand-in CS3, and the questions the program asks are read as the user would see them.
 *
 * MUTATION: have ManualSync.saySynced always pass `partial` false, and the sync with an unreadable route list
 * says everything matches; drop the `view.showSyncPartial()` call of MarklinControlStation.init, and the start-up says
 * nothing; show a partial read as information in ManualSync.saySynced, and it is not a warning.
 *
 * @author Adam
 */
public class testTheSyncSaysWhenPartOfTheDataWasNotRead
{
    private static final String INFO = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    @Test
    public void testTheDialogAndTheStartUpNoticeSayPartWasLeftAsItWas() throws Exception
    {
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);

        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-says").toFile(),
            (path, n) -> replaced.containsKey(path) ? replaced.get(path) : base.apply(path, n), true))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();
            assertNotNull(ui, "the window");

            try
            {
                startUpSyncDone(ui);

                // One answerer for the whole claim: a dialog just answered is still showing for a moment, and a new answerer
                // would count it again
                try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
                {
                    assertTrue(rig.model.syncWithCS2() >= 0, "the sync that imports the fixtures failed");

                    ui.doSync(ui);
                    assertTrue(answering.awaitAsked(1, 60000), "a sync that read everything asked nothing");

                    // The routes answered as a web page: nothing is read of them, and what is held is left
                    replaced.put("/app/api/automatics", "<html>not json</html>".getBytes(StandardCharsets.UTF_8));
                    ui.doSync(ui);
                    assertTrue(answering.awaitAsked(2, 60000), "the sync that read part asked nothing");
                    replaced.clear();

                    ui.showSyncPartial();
                    assertTrue(answering.awaitAsked(3, 60000), "the start-up notice asked nothing");

                    String plain = answering.asked().get(0);
                    String partial = answering.asked().get(1);
                    String notice = answering.asked().get(2);

                    assertEquals(plain, I18n.t("ui.infoSyncNothingDiffers"), "a sync that read everything says so");
                    assertEquals(answering.questions().get(0).type, JOptionPane.INFORMATION_MESSAGE,
                        "all read, not information");
                    assertEquals(answering.questions().get(1).type, JOptionPane.WARNING_MESSAGE,
                        "part read, not a warning");
                    assertEquals(answering.questions().get(0).title, I18n.t("sync.ui.title"), "the message's title");
                    assertEquals(partial, I18n.t("ui.infoSyncComplete") + "\n\n" + I18n.t("ui.infoSyncPartial"));
                    assertTrue(partial.contains("could not be read") && partial.contains("log"), "it says part was not read: " + partial);
                    assertFalse(partial.contains(I18n.t("ui.infoSyncNothingDiffers")), "and never that all matches: "
                        + partial);
                    assertEquals(notice, I18n.t("ui.startupSyncPartial"));
                    assertTrue(notice.contains("could not be read") && notice.contains("sync again"), "start-up says so: " + notice);
                }
            }
            finally
            {
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }

    /**
     * A start whose layout folder will not read, with the station answering, keeps the folder: the preference still
     * names it, the station's layout stands in as the layout in use for the session, the start says so once by the
     * folder's name, the exit keeps the folder's page windows remembered, and the next start reads the folder once it
     * is back (FR-245).
     *
     * MUTATION: in `MarklinControlStation.syncLayoutsFromConfiguredSource`, empty the preference again at a start; or
     * leave out the `view.showLayoutFolderHeld` call of `MarklinControlStation.init`; or `!layoutFolderHeld()` in
     * `TrainControlUI.saveLayoutTitles`; each fails its own assertion.
     */
    @Test(timeOut = 240000)
    @SuppressWarnings("unchecked")
    public void testAStartWhoseLayoutFolderWillNotReadKeepsItAndSaysSo() throws Exception
    {
        final java.io.File root = Files.createTempDirectory("tc-start-unread").toFile();
        final java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();
        final boolean rememberWas = prefs.getBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, true);
        String titles = null;

        // An empty folder: its index cannot be read
        try (Answering answering = Answering.start(message -> Answering.pressAt(0));
            StationRig rig = new StationRig(root, StandInStation.cs3("260", INFO), true, true,
                new java.io.File(root, "none")))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();
            String folder = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            String said = I18n.f("layout.ui.warnFolderUnreadAtStart", folder);

            try
            {
                startUpSyncDone(ui);
                assertFalse(folder.isEmpty(), "the start let the layout folder go");
                assertTrue(Components.waitFor(() -> answering.asked().contains(said), 30000), "the start did not say"
                    + " the folder could not be read: " + answering.asked());
                Components.settle();
                assertEquals(answering.asked().stream().filter(said::equals).count(), 1L, "not said once");
                assertEquals(answering.questions().get(answering.asked().indexOf(said)).type,
                    JOptionPane.WARNING_MESSAGE, "not as a warning");
                assertTrue(rig.model.getLayoutList().contains("Startpackung") && ui.isRemoteLayout(),
                    "the station's layout does not stand in: " + rig.model.getLayoutList());

                // The exit
                titles = TrainControlUI.LAYOUT_TITLES_PREF + "_" + ((java.util.Map<String, Integer>) Reflect.get(
                    TrainControlUI.class, ui, "layoutSizes")).values().iterator().next();
                prefs.putBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, true);
                prefs.put(titles, "1 - Main");
                SwingUtilities.invokeAndWait(() -> ui.saveState(false));

                assertEquals(prefs.get(titles, null), "1 - Main", "the exit forgot the folder's page windows");
                assertEquals(prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""), folder, "the exit let the folder"
                    + " go");

                // The next start, the folder back
                support.Trees.copyForTheRun(new java.io.File("test/test_layout"), new java.io.File(folder));
                rig.model.clearLayouts();

                assertTrue(rig.model.syncWithCS2() >= 0, "the next start's sync failed");
                assertTrue(rig.model.getLayoutList().contains("1 - Main") && !ui.isRemoteLayout(), "the folder back,"
                    + " the next start did not read it: " + rig.model.getLayoutList());
            }
            finally
            {
                prefs.putBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, rememberWas);
                if (titles != null) prefs.remove(titles);
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }

    /**
     * While a start holds an unreadable layout folder, Switch to Central Station Layout is offered and lets the folder
     * go, as for any local folder: the preference is emptied and nothing is held, so the next start says nothing
     * (FR-245).
     *
     * MUTATION: in `TrainControlUI.repaintPathLabel`, keep the switch gray while the folder is held; or leave out the
     * `layoutFolderHeld = null` of `MarklinControlStation.syncLayoutsFromConfiguredSource`; each fails its assertion.
     */
    @Test(timeOut = 240000)
    public void testSwitchToTheStationsLayoutLetsAHeldFolderGo() throws Exception
    {
        final java.io.File root = Files.createTempDirectory("tc-start-held").toFile();
        final java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();
        final java.lang.reflect.Field simulation = Reflect.field(org.traincontrol.marklin.MarklinControlStation.class,
            "simulation");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0));
            StationRig rig = new StationRig(root, StandInStation.cs3("260", INFO), true, true,
                new java.io.File(root, "none")))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();
            javax.swing.JMenuItem door = (javax.swing.JMenuItem) Reflect.field(TrainControlUI.class,
                "switchCSLayoutMenuItem").get(ui);
            String folder = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            try
            {
                startUpSyncDone(ui);
                assertEquals(rig.model.getLayoutFolderHeld(), folder, "precondition: the start held no folder");
                ui.repaintPathLabel();
                Components.settle();
                assertEquals(Components.onTheEventThread(door::getToolTipText), I18n.t(
                    "ui.main.toolbar.tooltip.centralStationNotConnected"), "gray in a simulation, it says the wrong why");

                // A station on the wire, which the stand-in is and a simulation is not
                simulation.setBoolean(rig.model, false);
                rig.model.setNetworkCommState(true);
                ui.repaintPathLabel();
                Components.settle();
                assertTrue(Components.onTheEventThread(door::isEnabled), "the switch is gray while the folder is"
                    + " held: " + door.getToolTipText());

                SwingUtilities.invokeAndWait(door::doClick);
                assertTrue(Settle.until(() -> prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "").isEmpty(), 30000),
                    "the switch kept the folder");
                assertTrue(syncEnded(ui), "the switch's sync did not end; log " + logTail(rig));
                assertTrue(Settle.until(() -> rig.model.getLayoutList().contains("Startpackung"), 30000),
                    "the station's layout is not shown: " + rig.model.getLayoutList());
                assertNull(rig.model.getLayoutFolderHeld(), "the folder is still held, so the next start says so again");
                assertTrue(ui.isRemoteLayout(), "the station's layout is not the one in use");
            }
            finally
            {
                simulation.setBoolean(rig.model, true);
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }

    /**
     * A start with no station whose layout folder will not read keeps the folder: nobody there takes the default No,
     * and the question, as the start asks it, names the folder and starts on No, which keeps it too (FR-245).
     *
     * MUTATION: in `TrainControlUI.createAndApplyEmptyLayout`, take Yes unattended (`proceed = true`), or ask the old
     * question starting on Yes; each fails its assertion.
     */
    @Test(timeOut = 240000)
    public void testAStartWithNoStationNamesTheUnreadFolderAndKeepsIt() throws Exception
    {
        final java.io.File root = Files.createTempDirectory("tc-start-alone").toFile();
        final java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();
        final String[] no = {null};

        try (Answering answering = Answering.start(message -> Answering.pressAt(message.equals(no[0]) ? 1 : 0));
            StationRig rig = new StationRig(root, StandInStation.cs3("260", INFO), true, false,
                new java.io.File(root, "none")))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();
            String folder = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            try
            {
                startUpSyncDone(ui);
                Components.settle();
                assertFalse(folder.isEmpty(), "precondition: no layout folder set");
                assertEquals(prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""), folder, "the start let the folder"
                    + " go for a demo layout; said " + answering.asked());

                // Somebody there is asked, and answers No
                no[0] = I18n.f("layout.ui.confirmDemoLayoutFolderUnread", folder,
                    new java.io.File(TrainControlUI.DEMO_LAYOUT_OUTPUT_PATH).getAbsolutePath());
                int before = answering.asked().size();
                TrainControlUI.setUnattended(false);
                SwingUtilities.invokeLater(() -> ui.createAndApplyEmptyLayout(TrainControlUI.DEMO_LAYOUT_OUTPUT_PATH,
                    true));
                assertTrue(answering.awaitAsked(before + 1, 30000), "nothing was asked");
                assertEquals(answering.asked().get(before), no[0], "the question does not name the folder");
                assertEquals(answering.questions().get(before).startsOn, String.valueOf(TrainControlUI.YES_NO_OPTS[1]),
                    "the question does not start on No");
                Components.settle();
                assertEquals(prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""), folder, "No let the folder go");
            }
            finally
            {
                TrainControlUI.setUnattended(true);
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }

    /**
     * The window lays out its demo diagram as it opens and syncs behind a spinner, posted from its start-up; a Sync
     * pressed meanwhile is rightly turned away as already running, so a claim starts once that sync has ended.
     */
    private static void startUpSyncDone(TrainControlUI ui) throws Exception
    {
        assertTrue(syncEnded(ui), "precondition: the window's start-up sync never ended");
    }

    /** True once the sync a press posted has ended; a wait of fixed length would let a slow sync outlive its claim. */
    static boolean syncEnded(TrainControlUI ui) throws Exception
    {
        // A posted sync takes the flag before this runs, in the spinner's own event loop
        SwingUtilities.invokeAndWait(() -> { });

        AtomicBoolean inFlight = (AtomicBoolean) Reflect.field(TrainControlUI.class, "syncInFlight").get(ui);

        if (!Settle.until(() -> !inFlight.get(), 60000)) return false;

        // The flag clears inside the event that ran the sync: the rest of that event, then what it posted (the start-up's
        // diagram refresh, which says "no track diagram" if a claim's sync has the layouts cleared when it runs)
        Components.drain(2);

        return true;
    }

    /** The last lines the model logged, for a failure's message. */
    private static String logTail(StationRig rig)
    {
        java.util.List<String> log = rig.takeLog();

        return log.subList(Math.max(0, log.size() - 5), log.size()).toString();
    }

    private static final AtomicBoolean deviceDropping = new AtomicBoolean();

    private static final AtomicBoolean everyFileDropping = new AtomicBoolean();

    /** What a claim does with the window, once its start-up sync has ended. */
    private interface OnTheWindow
    {
        void run(StationRig rig, TrainControlUI ui) throws Exception;
    }

    /** Runs `claim` on the window of a stand-in CS3 that drops its route list while `dropping` is set. */
    private static void withAWindowWhoseRoutesDrop(AtomicBoolean dropping, OnTheWindow claim) throws Exception
    {
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);

        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-retry").toFile(), (path, n) ->
        {
            if (dropping.get() && "/app/api/automatics".equals(path)) throw new IllegalStateException("dropped");

            if (deviceDropping.get() && "/config/geraet.vrs".equals(path)) throw new IllegalStateException("dropped");

            if (everyFileDropping.get()) throw new IllegalStateException("dropped");

            return base.apply(path, n);
        }, true))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();
            assertNotNull(ui, "the window");

            try
            {
                startUpSyncDone(ui);
                claim.run(rig, ui);
            }
            finally
            {
                // A sync still running when the window goes would show its popup to the next claim
                syncEnded(ui);
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }

    /** The first line of the failed-Sync popup. */
    private static final String FAILED = I18n.f("ui.syncFailedRetry", "").split("\n")[0];

    /**
     * A Sync that fails says why on the main window and offers Retry, which syncs again at once, and Close, which does
     * not.
     *
     * MUTATION: have TrainControlUI.doSync show "ui.errorSyncFailedSeeLog" alone on a failure, and no Retry is offered.
     */
    @Test
    public void testAFailedSyncSaysWhyAndRetries() throws Exception
    {
        AtomicBoolean dropping = new AtomicBoolean();

        withAWindowWhoseRoutesDrop(dropping, (rig, ui) ->
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync that imports the fixtures failed");

            // Retry for the first failure, with the station well again; Close for the second; OK for the rest
            AtomicInteger failures = new AtomicInteger();

            try (Answering answering = Answering.start(message ->
            {
                if (!message.startsWith(FAILED)) return Answering.pressAt(0);

                if (failures.incrementAndGet() > 1) return Answering.pressAt(1);

                dropping.set(false);
                return Answering.pressAt(0);
            }))
            {
                dropping.set(true);
                ui.doSync(ui);
                assertTrue(answering.awaitAsked(2, 60000), "the failure and the retry's result: " + answering.asked());

                Answering.Question failure = answering.questions().get(0);

                assertTrue(failure.message.startsWith(FAILED), "the failure says so: " + failure.message);
                assertTrue(failure.message.length() > I18n.f("ui.syncFailedRetry", "").length(), "and why: "
                    + failure.message);
                assertEquals(failure.buttons, java.util.Arrays.asList(I18n.t("ui.syncFailedRetryButton"),
                    I18n.t("ui.syncFailedCloseButton")));
                assertEquals(failure.title, I18n.t("ui.syncFailedTitle"));
                assertSame(failure.owner, ui, "on the main window");
                assertEquals(answering.asked().get(1), I18n.t("ui.infoSyncNothingDiffers"),
                    "Retry synced again, and the station answered");

                // Close: no further sync
                dropping.set(true);
                ui.doSync(ui);
                assertTrue(answering.awaitAsked(3, 60000), "the second failure was not shown");
                assertTrue(answering.asked().get(2).startsWith(FAILED), answering.asked().get(2));

                int routesAsked = rig.station.requests("/app/api/automatics");

                assertTrue(syncEnded(ui), "the sync did not end; log " + logTail(rig));
                assertFalse(answering.awaitAsked(4, 3000), "Close asked again: " + answering.asked());
                assertEquals(rig.station.requests("/app/api/automatics"), routesAsked, "Close synced again");
            }
        });
    }

    /**
     * A locomotive's own Sync (Locomotive Settings) says on that window what the main Sync says: that all matches, or,
     * over a station whose route list cannot be read, that part was left as it was.
     *
     * MUTATION: leave out the `saySynced` call of `TrainControlUI.syncLocomotiveFrom`, and it says nothing.
     */
    @Test
    public void testALocomotiveSyncSaysWhenPartWasNotRead() throws Exception
    {
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();

        withAReplaceableStation(replaced, (rig, ui) ->
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync that imports the fixtures failed");

            org.traincontrol.marklin.MarklinLocomotive loc = rig.model.newDCCLocomotive("PS train", 1931);
            org.traincontrol.gui.TrainControlUI.installLookAndFeel();
            javax.swing.JFrame window = new javax.swing.JFrame();

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                // Waited for by `after`: the sync starts only once a worker has read the station
                CountDownLatch first = new CountDownLatch(1);
                CountDownLatch second = new CountDownLatch(1);

                // Everything read: everything matches
                ui.syncLocomotiveFrom(loc, window, first::countDown);
                assertTrue(first.await(60, TimeUnit.SECONDS), "the first sync did not end; log " + logTail(rig));
                assertTrue(answering.awaitAsked(1, 60000), "a sync that read everything said nothing");
                assertEquals(answering.asked().get(0), I18n.t("ui.infoSyncNothingDiffers"));
                assertEquals(answering.questions().get(0).type, JOptionPane.INFORMATION_MESSAGE,
                    "all read, not information");

                replaced.put("/app/api/automatics", "<html>not json</html>".getBytes(StandardCharsets.UTF_8));
                ui.syncLocomotiveFrom(loc, window, second::countDown);
                assertTrue(second.await(60, TimeUnit.SECONDS), "the second sync did not end; log " + logTail(rig));
                assertTrue(answering.awaitAsked(2, 60000), "a sync that read part said nothing on the window; log "
                    + logTail(rig));
                assertEquals(answering.asked().get(1), I18n.t("ui.infoSyncComplete") + "\n\n"
                    + I18n.t("ui.infoSyncPartial"));
                assertSame(answering.questions().get(1).owner, window, "not on the window that asked");
                assertEquals(answering.questions().get(1).type, JOptionPane.WARNING_MESSAGE,
                    "part read, not a warning");
            }
            finally
            {
                SwingUtilities.invokeAndWait(window::dispose);
            }
        });
    }

    /** Runs `claim` on the window of a stand-in CS3 whose answers are the ones in `replaced`, once its start-up sync ended. */
    private static void withAReplaceableStation(Map<String, byte[]> replaced, OnTheWindow claim) throws Exception
    {
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);

        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-loc").toFile(),
            (path, n) -> replaced.containsKey(path) ? replaced.get(path) : base.apply(path, n), true))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();

            try
            {
                startUpSyncDone(ui);
                claim.run(rig, ui);
            }
            finally
            {
                // A sync still running when the window goes would show its popup to the next claim
                syncEnded(ui);
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }

    /**
     * A Sync pressed in the locomotive selector that fails says so on the selector, the window that asked, not on the
     * main window behind it.
     *
     * MUTATION: have TrainControlUI.choseFirst hang the popup off the main window again, and it is the failure's owner.
     */
    @Test
    public void testASyncThatFailsFromTheSelectorSaysSoOnTheSelector() throws Exception
    {
        AtomicBoolean dropping = new AtomicBoolean();

        withAWindowWhoseRoutesDrop(dropping, (rig, ui) ->
        {
            java.awt.Window selector = (java.awt.Window) Reflect.field(TrainControlUI.class, "selector").get(ui);
            javax.swing.JButton sync = (javax.swing.JButton) Reflect.field(selector.getClass(), "SyncWithCS")
                .get(selector);

            // Close for the failure; OK for anything else
            try (Answering answering = Answering.start(message -> Answering.pressAt(message.startsWith(FAILED) ? 1 : 0)))
            {
                dropping.set(true);
                SwingUtilities.invokeAndWait(sync::doClick);
                assertTrue(answering.awaitAsked(1, 60000), "the failure was not shown");

                Answering.Question failure = answering.questions().get(0);

                assertTrue(failure.message.startsWith(FAILED), "the failure says so: " + failure.message);
                assertSame(failure.owner, selector, "on the selector, whose Sync was pressed");
            }
        });
    }

    /** The start-up's own sync, made by the application's init against a station whose routes cannot be read, raises the notice. */
    @Test
    public void testTheStartUpSaysSoToo() throws Exception
    {
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            try (StationRig rig = new StationRig(Files.createTempDirectory("tc-startup-says").toFile(),
                (path, n) -> "/app/api/automatics".equals(path) ? "<html>not json</html>".getBytes(StandardCharsets.UTF_8)
                    : base.apply(path, n), true, true))
            {
                TrainControlUI ui = (TrainControlUI) rig.model.getGUI();

                try
                {
                    assertTrue(answering.awaitAsked(1, 60000), "start-up asked nothing");
                    assertEquals(answering.asked().get(0), I18n.t("ui.startupSyncPartial"));
                }
                finally
                {
                    SwingUtilities.invokeAndWait(ui::dispose);
                }
            }
        }
    }

    /**
     * The failed-Sync popup gives one plain sentence for what went wrong, never the raw exception, a URL or a byte count.
     *
     * MUTATION: put `failure.getClass().getSimpleName() + ": " + failure.getMessage()` back in
     * `MarklinControlStation.describe`, and the popup names an exception.
     */
    @Test
    public void testAFailedSyncSaysWhatWentWrongInOnePlainSentence() throws Exception
    {
        AtomicBoolean dropping = new AtomicBoolean();

        withAWindowWhoseRoutesDrop(dropping, (rig, ui) ->
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync that imports the fixtures failed");

            try (Answering answering = Answering.start(message -> Answering.pressAt(1)))
            {
                dropping.set(true);
                ui.doSync(ui);
                assertTrue(answering.awaitAsked(1, 60000), "the failure was not shown");

                assertEquals(answering.asked().get(0), I18n.f("ui.syncFailedRetry", I18n.t("sync.failedInterrupted")),
                    "a connection dropped part way says so in plain words");
            }
        });
    }

    /**
     * "Switch to the Central Station's layout" with the device file failing once, after the type was learned, keeps the
     * station's diagram and offers the same Retry popup as Sync.
     *
     * MUTATION: withhold the diagram whenever detection failed, whatever was learned before (drop `typeLearned` from
     * `MarklinControlStation.requireTheTypeKnown`), and the Layout tab is empty; drop the `offerSyncRetry` call of
     * `switchCSLayoutMenuItemActionPerformed`, and the popup is not shown.
     */
    @Test
    public void testTheSwitchToTheStationsLayoutKeepsTheDiagramAndOffersRetry() throws Exception
    {
        AtomicBoolean dropping = new AtomicBoolean();

        withAWindowWhoseRoutesDrop(dropping, (rig, ui) ->
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync that imports the fixtures failed");
            assertFalse(rig.model.getLayoutList().isEmpty(), "precondition: the station's diagram is loaded");

            javax.swing.JMenuItem door = (javax.swing.JMenuItem) Reflect.field(TrainControlUI.class,
                "switchCSLayoutMenuItem").get(ui);

            try (Answering answering = Answering.start(message -> Answering.pressAt(1)))
            {
                deviceDropping.set(true);
                SwingUtilities.invokeAndWait(() -> { door.setEnabled(true); door.doClick(); });
                assertTrue(answering.awaitAsked(1, 60000), "the failure was not shown");
                deviceDropping.set(false);

                assertTrue(answering.asked().get(0).startsWith(FAILED), answering.asked().get(0));
                assertFalse(rig.model.getLayoutList().isEmpty(), "the station's diagram is gone though its type was known");
            }
        });
    }

    /** The page box's item count, on the event thread. */
    private static int pagesListed(TrainControlUI ui) throws Exception
    {
        javax.swing.JComboBox<?> pages = (javax.swing.JComboBox<?>) Reflect.field(TrainControlUI.class, "LayoutList").get(ui);

        return support.Components.onTheEventThread(pages::getItemCount);
    }

    /**
     * Retry after a failed "Switch to the Central Station's layout" that read no pages, once the station answers, fills
     * the page box with the station's pages; the failure is the only thing said, with no "no layout" message stacked on
     * it.
     *
     * MUTATION: have the `retry` of `TrainControlUI.fetchTheStationsPages` run `doSync(this)`, and the page box stays
     * empty.
     */
    @Test
    public void testRetryAfterAFailedSwitchToTheStationsLayoutListsThePages() throws Exception
    {
        withAWindowWhoseRoutesDrop(new AtomicBoolean(), (rig, ui) ->
        {
            javax.swing.JMenuItem door = (javax.swing.JMenuItem) Reflect.field(TrainControlUI.class,
                "switchCSLayoutMenuItem").get(ui);

            // Retry once the station answers again
            try (Answering answering = Answering.start(message ->
            {
                if (message.startsWith(FAILED)) everyFileDropping.set(false);

                return Answering.pressAt(0);
            }))
            {
                everyFileDropping.set(true);
                SwingUtilities.invokeAndWait(() -> { door.setEnabled(true); door.doClick(); });
                assertTrue(answering.awaitAsked(1, 60000), "the failure was not shown");
                assertTrue(answering.asked().get(0).startsWith(FAILED), answering.asked().get(0));

                assertTrue(Settle.until(() -> !rig.model.getLayoutList().isEmpty(), 60000), "Retry read no pages");
                assertTrue(Settle.until(() -> { try { return pagesListed(ui) > 0; } catch (Exception e) { return false; } },
                    20000), "Retry left the page box empty; said " + answering.asked());
                assertEquals(pagesListed(ui), rig.model.getLayoutList().size(), "the page box lists other pages");
                assertTrue(syncEnded(ui), "Retry's sync did not end; log " + logTail(rig));
                assertFalse(answering.awaitAsked(2, 3000), "more than the failure was said: " + answering.asked());
            }
            finally
            {
                everyFileDropping.set(false);
            }
        });
    }

    /**
     * The switch's sync refused because an editor is on its way offers the same Retry popup, naming the editor, and
     * says nothing more; Retry, the editor gone, syncs.
     *
     * MUTATION: in `TrainControlUI.fetchTheStationsPages`, leave out the `offerSyncRetry` for `refused`, and nothing is
     * said.
     */
    @Test
    public void testASwitchSyncRefusedForAnEditorOffersRetry() throws Exception
    {
        withAWindowWhoseRoutesDrop(new AtomicBoolean(), (rig, ui) ->
        {
            java.lang.reflect.Field onItsWay = Reflect.field(TrainControlUI.class, "editorOnItsWay");
            java.lang.reflect.Method tail = Reflect.method(TrainControlUI.class, "fetchTheStationsPages");
            int syncs = rig.station.requests("/config/geraet.vrs");
            String retry = I18n.f("ui.syncFailedRetry", I18n.t("loc.ui.errorEditorOpenNoLinkOrSync"));

            // Retry once the editor is gone, on that popup only; Close on any other
            try (Answering answering = Answering.start(message ->
            {
                if (!message.equals(retry)) return Answering.pressAt(1);

                try
                {
                    onItsWay.setBoolean(ui, false);
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }

                return Answering.pressAt(0);
            }))
            {
                support.Components.onTheEventThread(() ->
                {
                    onItsWay.setBoolean(ui, true);

                    return tail.invoke(ui);
                });

                assertTrue(answering.awaitAsked(1, 60000), "the refused sync said nothing");
                assertEquals(answering.asked().get(0), I18n.f("ui.syncFailedRetry",
                    I18n.t("loc.ui.errorEditorOpenNoLinkOrSync")), "not the Retry popup naming the editor");
                assertTrue(Settle.until(() -> rig.station.requests("/config/geraet.vrs") > syncs, 60000),
                    "Retry did not sync");
                assertTrue(syncEnded(ui), "Retry's sync did not end; log " + logTail(rig));
                assertFalse(answering.awaitAsked(2, 3000), "more was said: " + answering.asked() + "; log " + logTail(rig));
            }
            finally
            {
                onItsWay.setBoolean(ui, false);
            }
        });
    }
}
