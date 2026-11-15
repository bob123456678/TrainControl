package ui;

import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Components;
import support.Reflect;
import support.SettingsWindow;

/**
 * The Sync button on a locomotive's Basic Settings (FR-182): it replaced the key menu's Sync w/ Central Station, runs
 * at once, is gray with its reason when no Central Station is connected, stays lit while the window is locked, is not
 * in Add, and refreshes the window from the locomotive without wiping typing.  And the yellow strip the shared-address
 * triangle opens, stacked under the lock strip.
 *
 * The station is never reached: the address is pointed at nowhere, so the sync fails at once, and the claims read
 * what the window does with the locomotive afterwards.
 */
public class testTheLocomotiveSettingsSync extends support.SettingsClass
{
    private static final String NAME = "SY train";
    private static final String TWIN = "SY twin";
    private static final long WAIT = 10000;

    private static boolean stationWasUnset;
    private static MarklinLocomotive loc;

    @Override
    protected String[] made()
    {
        return new String[] { NAME, TWIN };
    }

    @org.testng.annotations.BeforeClass(dependsOnMethods = "openTheRailway")
    public void pointAtNowhere() throws Exception
    {
        stationWasUnset = support.TestStationAddress.get() == null;

        if (stationWasUnset) support.TestStationAddress.set(support.TestStationAddress.NOWHERE);
    }

    @org.testng.annotations.AfterClass(alwaysRun = true, dependsOnMethods = "closeTheRailway")
    public void putTheAddressBack() throws Exception
    {
        if (stationWasUnset) support.TestStationAddress.set(null);
    }

    @BeforeMethod
    public void lay() throws Exception
    {
        fixture.quiet();

        connected(false);

        fixture.forget(NAME, TWIN);

        SettingsWindow.power(ui, fixture.model, false);

        loc = fixture.model.newDCCLocomotive(NAME, 1931);

        fixture.show(loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE);
    }

    @AfterMethod(alwaysRun = true)
    public void tidy() throws Exception
    {
        loc.setSpeed(0);

        SettingsWindow.power(ui, fixture.model, false);

        connected(false);

        fixture.quiet();
    }

    /** Makes the model read as having a real Central Station on the wire, or as a simulation again. */
    private static void connected(boolean on) throws Exception
    {
        Reflect.set(org.traincontrol.marklin.MarklinControlStation.class, fixture.model, "simulation", !on);

        fixture.model.setNetworkCommState(on);
    }

    private static java.awt.Rectangle inWindow(java.awt.Container pane, java.awt.Component c)
    {
        return Components.onTheEventThread(() -> javax.swing.SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), pane));
    }

    private static JButton sync()
    {
        return SettingsWindow.part("settings.sync", JButton.class);
    }

    /** Lets the window's once-a-second look at the rule and the station go by. */
    private static void awaitLook(java.util.function.BooleanSupplier until) throws Exception
    {
        assertTrue(Components.waitFor(until, WAIT), "the window never caught up");
    }

    /**
     * The button is on a locomotive's Basic Settings, directly under the picture at its left edge with its (i) beside
     * it, and not in Add; the key menu has no Sync.  Gray with its reason as the tooltip while no Central Station is
     * connected, lit when one is.
     *
     * MUTATION: leave `refreshSync` out of `LocomotiveSettingsFrame.refresh`, or enable the button whatever the station,
     * or move `syncRow` out of `pictureColumn`, and this fails.
     */
    @Test
    public void testTheButtonIsGrayWithItsReasonUntilAStationIsConnected() throws Exception
    {
        JButton button = sync();

        assertNotNull(button, "Basic Settings has no Sync button");
        assertEquals(button.getText(), I18n.t("loc.ui.menuSyncCentralStation"));
        assertEquals(SettingsWindow.tabShowing(), 0, "the button is not on the first tab");

        java.awt.Container pane = SettingsWindow.showing().getContentPane();
        java.awt.Rectangle at = inWindow(pane, button);
        java.awt.Rectangle picture = inWindow(pane, SettingsWindow.part("settings.picture", JLabel.class));

        assertEquals(at.x, picture.x, "the button does not start at the picture's left edge");
        assertTrue(at.y >= picture.y + picture.height && at.y - (picture.y + picture.height) <= 12,
            "the button is not directly under the picture: " + at + " under " + picture);
        assertNotNull(Components.find(SettingsWindow.showing().getContentPane(), JLabel.class,
            l -> org.traincontrol.gui.InfoMark.icon() == l.getIcon() && l.getToolTipText() != null
                && l.getToolTipText().contains(I18n.t("loc.ui.settingsSyncHint"))), "the button has no (i)");

        assertFalse(Components.onTheEventThread(button::isEnabled), "the button is lit in a simulation");
        assertEquals(Components.onTheEventThread(button::getToolTipText), I18n.t("loc.ui.settingsSyncNoStation"),
            "the gray button does not say why");

        connected(true);
        awaitLook(() -> Components.onTheEventThread(button::isEnabled));
        assertNull(Components.onTheEventThread(button::getToolTipText), "the lit button still shows the reason");

        connected(false);
        awaitLook(() -> !Components.onTheEventThread(button::isEnabled));
    }

    /**
     * Add has no Sync button: there is no locomotive to sync yet.
     *
     * MUTATION: add the button to Add's layout in `LocomotiveTab`, and this fails.
     */
    @Test
    public void testAddHasNoSyncButton() throws Exception
    {
        fixture.quiet();

        assertTrue(Components.onTheEventThread(ui::openAddLocomotive), "Add did not open");
        assertNotNull(SettingsWindow.awaited(), "Add is not on screen");
        assertNull(sync(), "Add shows a Sync button");
    }

    /**
     * It stays lit while the moving rule locks the window: it only reads from the station.
     *
     * MUTATION: gray the button in `LocomotiveTab.setBlocked`, and this fails.
     */
    @Test
    public void testTheButtonStaysLitWhileTheWindowIsLocked() throws Exception
    {
        connected(true);

        SettingsWindow.power(ui, fixture.model, true);
        loc.setSpeed(40);

        Components.onTheEventThread(() ->
        {
            ui.repaintLoc(true, null);

            return null;
        });

        JLabel strip = SettingsWindow.part("settings.strip", JLabel.class);

        awaitLook(() -> Components.onTheEventThread(strip::isVisible));
        assertTrue(Components.onTheEventThread(sync()::isEnabled), "the locked window grayed Sync");
        assertFalse(Components.onTheEventThread(SettingsWindow.part("settings.trash", JButton.class)::isEnabled),
            "precondition: the window is not locked");
    }

    /**
     * Pressing it syncs this locomotive at once (the log says so) and the window then shows what the locomotive now
     * holds: an untouched address field, the Functions tab rebuilt.  Nothing waits for Save.
     *
     * MUTATION: leave `afterSync` out of `LocomotiveSettingsFrame.syncNow`'s call, or `takeLocomotive` empty, and this
     * fails.
     */
    @Test
    public void testPressingSyncSyncsAndRefreshesTheWindow() throws Exception
    {
        connected(true);

        List<String> logged = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.logging.Handler tap = support.LogTap.messagesInto(logged);
        java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).addHandler(tap);

        try
        {
            JButton button = sync();

            awaitLook(() -> Components.onTheEventThread(button::isEnabled));

            JTable before = SettingsWindow.part("functions.table", JTable.class);

            // What a sync does to a locomotive: a new address
            assertTrue(loc.setAddress(loc.getAddress() + 1, decoderType.DCC), "precondition: the address was refused");

            assertFalse(SettingsWindow.text("settings.address").equals(Integer.toString(loc.getAddress())),
                "precondition: the window already shows it");

            Components.press(button);

            awaitLook(() -> SettingsWindow.text("settings.address").equals(Integer.toString(loc.getAddress())));
            assertTrue(Components.waitFor(() -> logged.stream().anyMatch(m -> m.contains(I18n.f("loc.syncing", NAME))), WAIT),
                "the press did not ask for this locomotive's sync");
            assertNotSame(SettingsWindow.part("functions.table", JTable.class), before,
                "the Functions tab was not rebuilt from the locomotive");
            assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled),
                "a sync left Save lit: the refresh counts as typing");
        }
        finally
        {
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName())
                .removeHandler(tap);
        }
    }

    /**
     * Sync over typing not saved asks first, as Close does: Cancel syncs nothing and keeps the typing, the Notes
     * included, which only TrainControl holds.
     *
     * MUTATION: drop the `settleUnsaved` from `LocomotiveSettingsFrame.syncNow`, and this fails.
     */
    @Test
    public void testSyncAsksBeforeItDropsTheTyping() throws Exception
    {
        connected(true);

        JButton button = sync();

        awaitLook(() -> Components.onTheEventThread(button::isEnabled));

        SettingsWindow.type("settings.name", "SY typed name");

        Components.onTheEventThread(() ->
        {
            SettingsWindow.part("notes.text", javax.swing.JTextArea.class).setText("SY typed notes");

            return null;
        });

        int address = loc.getAddress();

        assertTrue(loc.setAddress(address + 1, decoderType.DCC), "precondition: the address was refused");

        LocomotiveSettingsFrame.unsavedAnswerForTest = 2;

        try
        {
            Components.press(sync());

            Components.drain(2);

            assertFalse(Components.waitFor(() -> SettingsWindow.text("settings.address").equals(Integer.toString(loc.getAddress())),
                2000), "Cancel on the question went on to sync");
            assertEquals(SettingsWindow.text("settings.name"), "SY typed name", "the sync dropped the typed name");
            assertEquals(Components.onTheEventThread(SettingsWindow.part("notes.text", javax.swing.JTextArea.class)::getText),
                "SY typed notes", "the sync dropped the typed notes");
        }
        finally
        {
            LocomotiveSettingsFrame.unsavedAnswerForTest = null;
        }
    }

    /**
     * A Sync that fails (the station is nowhere) says so on this window and offers Retry, which syncs again; one pressed
     * while another sync runs says that one is running, on this window too.
     *
     * MUTATION: in `TrainControlUI.syncLocomotiveFrom`, leave out the `saidTheSyncDidNotRun` call, and the button says
     * nothing.
     */
    @Test(timeOut = 120000)
    public void testASyncThatFailsSaysSoOnTheWindowAndRetries() throws Exception
    {
        connected(true);

        JButton button = sync();

        awaitLook(() -> Components.onTheEventThread(button::isEnabled));

        LocomotiveSettingsFrame window = SettingsWindow.showing();
        String failed = I18n.f("ui.syncFailedRetry", "").split("\n")[0];
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();

        java.util.concurrent.atomic.AtomicBoolean inFlight = (java.util.concurrent.atomic.AtomicBoolean)
            Reflect.field(org.traincontrol.gui.TrainControlUI.class, "syncInFlight").get(ui);

        // Retry for the first failure, Close for the second; one answerer, since a dialog just answered is still
        // showing for a moment and a new answerer would count it again
        try (support.Answering answering = support.Answering.start(message -> support.Answering.pressAt(
            message.startsWith(failed) && failures.incrementAndGet() > 1 ? 1 : 0)))
        {
            Components.press(button);

            assertTrue(answering.awaitAsked(2, 30000), "the failed Sync, then its Retry, said: " + answering.asked());

            for (support.Answering.Question q : answering.questions().subList(0, 2))
            {
                assertTrue(q.message.startsWith(failed), "not the failed-sync popup: " + q.message);
                org.testng.Assert.assertSame(q.owner, window, "not on the Settings window");
            }

            assertFalse(answering.awaitAsked(3, 3000), "Close synced again: " + answering.asked());

            inFlight.set(true);

            Components.press(button);

            assertTrue(answering.awaitAsked(3, 30000), "a Sync while another runs said nothing");
            assertEquals(answering.asked().get(2), I18n.t("ui.infoSyncAlreadyRunning"));
            org.testng.Assert.assertSame(answering.questions().get(2).owner, window, "not on the Settings window");
        }
        finally
        {
            inFlight.set(false);
        }
    }

    /**
     * A Sync pressed while another sync is still writing says so and leaves the window alone: reloading it then would
     * show half of the other sync's data as the answer.
     *
     * MUTATION: in `TrainControlUI.syncLocomotiveFrom`, run `after` whatever `synced` is, and the window reloads.
     */
    @Test(timeOut = 120000)
    public void testASyncWhileAnotherRunsDoesNotReloadTheWindow() throws Exception
    {
        connected(true);

        java.util.concurrent.atomic.AtomicBoolean inFlight = (java.util.concurrent.atomic.AtomicBoolean)
            Reflect.field(org.traincontrol.gui.TrainControlUI.class, "syncInFlight").get(ui);
        java.util.concurrent.atomic.AtomicInteger reloads = new java.util.concurrent.atomic.AtomicInteger();

        try (support.Answering answering = support.Answering.start(message -> support.Answering.pressAt(0)))
        {
            inFlight.set(true);
            ui.syncLocomotiveFrom(loc, SettingsWindow.showing(), reloads::incrementAndGet);

            assertTrue(answering.awaitAsked(1, 30000), "a Sync while another runs said nothing");
            assertEquals(answering.asked().get(0), I18n.t("ui.infoSyncAlreadyRunning"));
            assertEquals(reloads.get(), 0, "the window reloaded while the other sync was still writing");
        }
        finally
        {
            inFlight.set(false);
        }
    }

    /**
     * The pre-arrival speed slider opens at the percent the reduction was saved at, for figures whose double times 100
     * falls just short (0.57 is 56.99...), and a click on it writes the same figure back.
     *
     * MUTATION: in `AutonomySettingsTab.loadAutoLayoutSettings`, cast the reduction times 100 to int again instead of
     * rounding, and 0.57 opens as 56 and a click saves 0.56.
     */
    @Test(timeOut = 120000)
    public void testThePreArrivalSliderOpensAndSavesAsItWasSet() throws Exception
    {
        boolean hadOne = fixture.model.hasAutoLayout();
        org.traincontrol.automation.Layout auto = fixture.model.getAutoLayout();
        double was = auto.getPreArrivalSpeedReduction();
        Object tab = Reflect.get(org.traincontrol.gui.TrainControlUI.class, ui, "autonomySettingsTab");
        javax.swing.JSlider slider = (javax.swing.JSlider) Reflect.get(org.traincontrol.gui.TrainControlUI.class, ui,
            "preArrivalSpeedReduction");
        java.lang.reflect.Method load = Reflect.method(tab.getClass(), "loadAutoLayoutSettings");
        java.lang.reflect.Method release = Reflect.method(tab.getClass(), "preArrivalSpeedReductionMouseReleased",
            java.awt.event.MouseEvent.class);

        try
        {
            for (double set : new double[] { 0.29, 0.57, 0.58 })
            {
                auto.setPreArrivalSpeedReduction(set);

                int shown = Components.onTheEventThread(() ->
                {
                    load.invoke(tab);

                    return slider.getValue();
                });

                assertEquals(shown, (int) Math.round(set * 100), "a reduction saved at " + set + " opened as " + shown + "%");

                Components.onTheEventThread(() ->
                {
                    release.invoke(tab, (Object) null);

                    return null;
                });

                assertEquals(auto.getPreArrivalSpeedReduction(), set, 1e-9, "a click on the slider changed " + set);
            }
        }
        finally
        {
            auto.setPreArrivalSpeedReduction(was);

            if (!hadOne) fixture.model.clearAutoLayout();
        }
    }

    /**
     * A sync reloads the whole window from the locomotive and overwrites what was typed, when Discard is the answer to
     * the question: the typed name and the edited Functions table are replaced by the synced values, Save goes gray,
     * and the tab selected and the window's place stay.
     *
     * MUTATION: keep dirty fields and tabs in `LocomotiveSettingsFrame.afterSync` (rebuild nothing), and this fails.
     */
    @Test
    public void testSyncReloadsTheWholeWindowOverTheTyping() throws Exception
    {
        LocomotiveSettingsFrame.unsavedAnswerForTest = 1;

        try
        {
            syncOverTheTyping();
        }
        finally
        {
            LocomotiveSettingsFrame.unsavedAnswerForTest = null;
        }
    }

    private void syncOverTheTyping() throws Exception
    {
        connected(true);

        JButton button = sync();

        awaitLook(() -> Components.onTheEventThread(button::isEnabled));

        SettingsWindow.type("settings.name", "SY typed name");
        SettingsWindow.pickIcon(0, (loc.getFunctionType(0) + 7) % 100 + 1);

        JTable before = SettingsWindow.part("functions.table", JTable.class);

        assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "precondition: nothing is typed");
        assertTrue(loc.setAddress(loc.getAddress() + 1, decoderType.DCC), "precondition: the address was refused");

        SettingsWindow.selectTab(0);

        java.awt.Point where = Components.onTheEventThread(() -> SettingsWindow.showing().getLocation());

        Components.press(sync());

        awaitLook(() -> SettingsWindow.text("settings.address").equals(Integer.toString(loc.getAddress())));

        assertEquals(SettingsWindow.text("settings.name"), NAME, "the typed name survived the sync");
        assertNotSame(SettingsWindow.part("functions.table", JTable.class), before, "the edited Functions tab survived");
        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save is lit after a reload");
        assertEquals(SettingsWindow.tabShowing(), 0, "the sync changed the tab shown");
        assertEquals(Components.onTheEventThread(() -> SettingsWindow.showing().getLocation()), where,
            "the sync moved the window");
    }

    /**
     * Another locomotive at the address and decoder: the triangle's strip stacks under the lock strip and neither
     * hides the other, in a locked window.
     *
     * MUTATION: put the shared strip where the lock strip is, in the `top` panel of `LocomotiveSettingsFrame`, and this
     * fails.
     */
    @Test
    public void testTheSharedStripStacksUnderTheLockStrip() throws Exception
    {
        fixture.model.newDCCLocomotive(TWIN, 1931);

        SettingsWindow.power(ui, fixture.model, true);
        loc.setSpeed(40);

        Components.onTheEventThread(() ->
        {
            ui.repaintLoc(true, null);

            return null;
        });

        JLabel lock = SettingsWindow.part("settings.strip", JLabel.class);
        JPanel shared = SettingsWindow.part("settings.sharedStrip", JPanel.class);

        awaitLook(() -> Components.onTheEventThread(lock::isVisible));

        // The window was built before the twin existed: a keystroke asks again
        SettingsWindow.type("settings.address", "1931");
        Components.settle();

        support.Mouse.click(SettingsWindow.part("settings.sameAddress", JLabel.class));
        Components.settle();

        assertTrue(Components.onTheEventThread(shared::isVisible), "the triangle opened nothing in a locked window");
        assertTrue(Components.onTheEventThread(lock::isVisible), "the shared strip hid the lock strip");
        assertTrue(Components.onTheEventThread(lock::getText).contains(loc.getName()), "the lock strip lost its message");
        assertTrue(Components.onTheEventThread(() -> lock.getHeight() > 0 && shared.getY() >= lock.getY() + lock.getHeight()),
            "the strips are not stacked");
        assertTrue(Components.onTheEventThread(() -> shared.getY() + shared.getHeight()
            <= SettingsWindow.showing().tabsForTest().getY()), "the strips overlap the tabs");
    }

    /**
     * Everything in the window that is clicked as an icon shows the hand cursor: the trash can, the picture's marks, the
     * Functions tab's undo arrows, the strip's close button, and the duplicate triangle once it shows.  The (i) marks,
     * which only explain, keep the arrow.
     *
     * MUTATION: drop `trash.setCursor` from `LocomotiveTab`, or the cursor from `revertButton` in
     * `LocomotiveFunctionsTab`, or the triangle's cursor in `warnAboutTheSameAddress`, and this fails.
     */
    @Test
    public void testClickableIconsShowTheHandCursor() throws Exception
    {
        fixture.model.newDCCLocomotive(TWIN, 1931);

        SettingsWindow.type("settings.address", "1931");
        Components.settle();

        for (String name : new String[] { "settings.trash", "settings.wrench", "settings.crop", "settings.revert",
            "settings.sameAddress", "functions.resetAll", "functions.clearPreset", "settings.sharedClose" })
        {
            java.awt.Component c = SettingsWindow.part(name, java.awt.Component.class);

            assertNotNull(c, "no " + name);
            assertEquals((int) Components.onTheEventThread(() -> c.getCursor().getType()), java.awt.Cursor.HAND_CURSOR,
                name + " does not show the hand cursor");
        }

        java.awt.Component info = Components.find(SettingsWindow.showing().getContentPane(), JLabel.class,
            l -> org.traincontrol.gui.InfoMark.icon() == l.getIcon());

        assertNotNull(info, "no (i)");
        assertEquals((int) Components.onTheEventThread(() -> info.getCursor().getType()), java.awt.Cursor.DEFAULT_CURSOR,
            "an (i) shows a hand");
    }

    /**
     * A locomotive's own Basic Settings: the address's (i) ends where the dropdown does, there is no Check Duplicates
     * button, and a preset speed of 0 is the way to clear a preset: the (i) says so, and Save with 0 leaves none.
     *
     * MUTATION: put the (i) back right of the fields, restore the button, or drop the 0 sentence from the Preset Speed
     * (i) text, and this fails.
     */
    @Test
    public void testAnEditedLocomotiveHasNoCheckButtonAndPresetZeroClears() throws Exception
    {
        assertNull(SettingsWindow.part("settings.checkDuplicates", JButton.class), "Check Duplicates is still a button");

        int overhang = SettingsWindow.addressHintOverhang();

        assertTrue(Math.abs(overhang) <= 1, "the (i) ends " + overhang + " px from where the dropdown ends");

        java.awt.Container pane = SettingsWindow.showing().getContentPane();

        String wrapped = org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.wrapped(I18n.t("loc.ui.settingsPresetSpeedHint"));

        assertNotNull(Components.find(pane, JLabel.class, l -> wrapped.equals(l.getToolTipText())),
            "the Preset Speed (i) is missing");
        assertTrue(I18n.t("loc.ui.settingsPresetSpeedHint").contains(" 0 "), "the Preset Speed (i) does not say what 0 does");

        loc.setPreferredSpeed(30);
        fixture.quiet();
        fixture.show(loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE);

        javax.swing.JSpinner speed = SettingsWindow.part("settings.speed", javax.swing.JSpinner.class);

        assertEquals(Components.onTheEventThread(speed::getValue), 30, "the window does not show the preset");

        Components.onTheEventThread(() ->
        {
            speed.setValue(0);

            return null;
        });

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> loc.getPreferredSpeed() == 0, WAIT), "Save with 0 did not clear the preset");
    }
}
