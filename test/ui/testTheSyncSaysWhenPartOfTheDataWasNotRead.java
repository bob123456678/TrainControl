package ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Reflect;
import support.Settle;
import support.StandInStation;
import support.StationRig;

/**
 * The sync says in plain words when part of what the Central Station sent could not be read and was left as it was
 * (FR-169): the dialog the Sync menu raises, and the notice a start-up raises.  The sync is made through the real window
 * against a stand-in CS3, and the questions the program asks are read as the user would see them.
 *
 * MUTATION: have TrainControlUI.doSync always pick "ui.infoSyncCompleteItemsAdded" for its text, and the sync with an
 * unreadable route list says "Sync complete. Items added: 0" and nothing more; drop the `view.showSyncPartial()` call
 * of MarklinControlStation.init, and the start-up says nothing.
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

                    assertEquals(plain, I18n.f("ui.infoSyncCompleteItemsAdded", "0"), "a sync that read everything says so");
                    assertEquals(partial, I18n.f("ui.infoSyncCompletePartial", "0"));
                    assertTrue(partial.contains("could not be read") && partial.contains("log"), "it says part was not read: " + partial);
                    assertTrue(partial.contains("Items added: 0"), "and still says what was added: " + partial);
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
     * The window lays out its demo diagram as it opens and syncs behind a spinner, posted from its start-up; a Sync
     * pressed meanwhile is rightly turned away as already running, so a claim starts once that sync has ended.
     */
    private static void startUpSyncDone(TrainControlUI ui) throws Exception
    {
        // The start-up's posted sync takes the flag before this runs, in the spinner's own event loop
        SwingUtilities.invokeAndWait(() -> { });

        AtomicBoolean inFlight = (AtomicBoolean) Reflect.field(TrainControlUI.class, "syncInFlight").get(ui);

        assertTrue(Settle.until(() -> !inFlight.get(), 60000), "precondition: the window's start-up sync never ended");
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
                assertEquals(answering.asked().get(1), I18n.f("ui.infoSyncCompleteItemsAdded", "0"),
                    "Retry synced again, and the station answered");

                // Close: no further sync
                dropping.set(true);
                ui.doSync(ui);
                assertTrue(answering.awaitAsked(3, 60000), "the second failure was not shown");
                assertTrue(answering.asked().get(2).startsWith(FAILED), answering.asked().get(2));

                int routesAsked = rig.station.requests("/app/api/automatics");

                assertFalse(answering.awaitAsked(4, 3000), "Close asked again: " + answering.asked());
                assertEquals(rig.station.requests("/app/api/automatics"), routesAsked, "Close synced again");
            }
        });
    }

    /**
     * A locomotive's own Sync (Locomotive Settings) over a station whose route list cannot be read says on that window
     * what the main Sync says: part was left as it was.
     *
     * MUTATION: leave out the `saidTheSyncWasPartial` call of `TrainControlUI.syncLocomotiveFrom`, and it says nothing.
     */
    @Test
    public void testALocomotiveSyncSaysWhenPartWasNotRead() throws Exception
    {
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();

        withAReplaceableStation(replaced, (rig, ui) ->
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync that imports the fixtures failed");

            org.traincontrol.marklin.MarklinLocomotive loc = rig.model.newDCCLocomotive("PS train", 1931);
            javax.swing.JFrame window = new javax.swing.JFrame();

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                // Everything read: nothing to say
                ui.syncLocomotiveFrom(loc, window, null);
                SwingUtilities.invokeAndWait(() -> { });
                assertFalse(answering.awaitAsked(1, 3000), "a sync that read everything said: " + answering.asked());

                replaced.put("/app/api/automatics", "<html>not json</html>".getBytes(StandardCharsets.UTF_8));
                ui.syncLocomotiveFrom(loc, window, null);
                assertTrue(answering.awaitAsked(1, 60000), "a sync that read part said nothing on the window");
                assertEquals(answering.asked().get(0), I18n.f("ui.infoSyncCompletePartial", "0"));
                assertSame(answering.questions().get(0).owner, window, "not on the window that asked");
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

            // Retry once the editor is gone
            try (Answering answering = Answering.start(message ->
            {
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
                assertFalse(answering.awaitAsked(2, 3000), "more was said: " + answering.asked());
            }
            finally
            {
                onItsWay.setBoolean(ui, false);
            }
        });
    }
}
