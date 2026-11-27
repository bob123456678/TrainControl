package ui;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsWindow;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.SettingsWindow;

/**
 * The Autonomy tab of Locomotive Settings (FR-048): train length, reversible, the departure and arrival functions and
 * Active, written at Save.  The setup of an old file keeps its own copy of a standing train, which a load copies back
 * onto the locomotive, so a Save reaches that copy too.  A rename the setup's file cannot take is said where it was
 * made, at Save and at a CSV import.  On the frozen copy of Adam's railway, with a train standing on BottomMainB.
 */
public class testTheLocomotiveSettingsAutonomyTab
{
    private static final String TRAIN = "AU standing train";

    private static support.SettingsFixture fixture;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Locomotive train;
    private static TileKey square;
    private static boolean made;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111).
        fixture = support.SettingsFixture.open(support.Scenario.folderFor("live-snapshot"));

        model = fixture.model;
        ui = fixture.ui;

        model.setNetworkCommState(false);

        Components.settle();

        session = ui.getAutonomySession();

        if (session == null) throw new AssertionError("no autonomy setup on this railway");

        if (model.getLocByName(TRAIN) == null)
        {
            model.newMFXLocomotive(TRAIN, 0xCE9);

            made = true;
        }

        train = model.getLocByName(TRAIN);
        square = support.StandingTrain.squareNamed(session, "BottomMainB");

        support.StandingTrain.standOn(model, session, TRAIN, square);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (made && fixture != null) SwingUtilities.invokeAndWait(() -> model.deleteLoc(TRAIN));

            if (fixture != null) fixture.finish();
        }
        finally
        {
            if (fixture != null) fixture.closeSandbox();
        }
    }

    /** The train with these values, and the setup's record of it, in the old format, holding them too. */
    private static void startFrom(int length, boolean reversible, Integer departure, Integer arrival) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            train.setTrainLength(length);
            train.setReversible(reversible);
            train.setDepartureFunc(departure);
            train.setArrivalFunc(arrival);

            org.json.JSONObject record = new org.json.JSONObject().put("name", TRAIN).put("trainLength", length)
                .put("reversible", reversible);

            if (departure != null) record.put("departureFunc", departure);
            if (arrival != null) record.put("arrivalFunc", arrival);

            session.setPointProperty(square, "loc", record);

            return null;
        });
    }

    private static void openTheTab() throws Exception
    {
        fixture.quiet();

        assertTrue(SettingsWindow.open(ui, train, LocomotiveSettingsWindow.Tab.AUTONOMY, null), "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
        assertEquals(SettingsWindow.tabShowing(), LocomotiveSettingsWindow.Tab.AUTONOMY.ordinal(), "the Autonomy tab is not showing");
    }

    /** Sets the tab's fields as a click would; a function of -1 is None. */
    private static void setTheTab(int length, boolean reversible, int departure, int arrival, Boolean active)
    {
        Components.onTheEventThread(() ->
        {
            SettingsWindow.part("autonomy.length", JSpinner.class).setValue(length);
            SettingsWindow.part("autonomy.reversible", JCheckBox.class).setSelected(reversible);
            SettingsWindow.part("autonomy.departure", JComboBox.class).setSelectedIndex(departure + 1);
            SettingsWindow.part("autonomy.arrival", JComboBox.class).setSelectedIndex(arrival + 1);

            if (active != null) SettingsWindow.part("autonomy.active", JCheckBox.class).setSelected(active);

            return null;
        });
    }

    private static void save() throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save is gray: "
                + Components.onTheEventThread(SettingsWindow.part("settings.strip", JLabel.class)::getText));

            SettingsWindow.press(SettingsWindow.save());

            assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, 8000), "Save did not close the window: "
                + answering.asked());
        }
    }

    private static String values()
    {
        return train.getTrainLength() + " " + train.isReversible() + " " + train.getDepartureFunc() + " " + train.getArrivalFunc();
    }

    /**
     * Each field saved in the tab is the train's after a load that does not fold, though the setup holds its own copy
     * of the train, and that copy holds the saved values: a function set to None leaves it, as a train with none is
     * written.
     *
     * MUTATION: leave `main.recordInTheSetup` out of `AutonomyTab.apply`, and the load puts the old values back and
     * this fails.
     */
    @Test
    public void testEachFieldReachesTheSetupAndSurvivesAReload() throws Exception
    {
        final String configuration = ui.getActiveDiagramConfiguration();

        startFrom(2, false, 1, 2);

        openTheTab();
        setTheTab(6, true, 3, -1, null);
        save();

        assertEquals(values(), "6 true 3 null", "Save did not write the tab onto the train");

        org.json.JSONObject record = Components.onTheEventThread(() -> (org.json.JSONObject) session.getPointProperty(square, "loc"));

        assertEquals(record.optInt("trainLength", -1), 6, "the setup's copy kept the old length: " + record);
        assertEquals(record.optBoolean("reversible", false), true, "the setup's copy kept the old reversible: " + record);
        assertEquals(record.optInt("departureFunc", -1), 3, "the setup's copy kept the old departure function: " + record);
        assertFalse(record.has("arrivalFunc"), "the setup's copy kept an arrival function set to None: " + record);

        support.StandingTrain.reloadWithoutFolding(ui, configuration);

        assertEquals(values(), "6 true 3 null", "a load put the old setup's copy over what was saved in the tab");
    }

    private static void chooseDecoder(Locomotive.decoderType type)
    {
        Components.onTheEventThread(() ->
        {
            SettingsWindow.part("settings.decoder", JComboBox.class).setSelectedItem(type);

            return null;
        });
    }

    /**
     * A function chosen for departure comes back when a decoder type that has it is chosen again, and the tab is not
     * dirty for the round trip.
     *
     * MUTATION: have `AutonomyTab.showCount` fill the boxes from `function(departure)` rather than the wanted value, and
     * this fails.
     */
    @Test
    public void testASmallerDecoderDoesNotForgetTheDepartureFunction() throws Exception
    {
        startFrom(2, false, 30, null);

        openTheTab();

        JComboBox<?> departure = SettingsWindow.part("autonomy.departure", JComboBox.class);

        SettingsWindow.selectTab(0);
        chooseDecoder(Locomotive.decoderType.DCC);
        assertEquals((int) Components.onTheEventThread(departure::getSelectedIndex), 0, "F30 is shown under DCC's 29 functions");

        chooseDecoder(Locomotive.decoderType.MFX);
        assertEquals((int) Components.onTheEventThread(departure::getSelectedIndex), 31, "F30 did not come back with MFX");
        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "the round trip left the window dirty");

        SettingsWindow.closeIfOpen();
    }

    /**
     * A departure function the chosen decoder type does not have is not kept on the train, and not recorded in the
     * setup either.
     *
     * MUTATION: record `function(departure)` instead of `loc.getDepartureFunc()` in `AutonomyTab.apply` and drop
     * `autonomyTab.showCount` from the decoder listener, and the setup keeps F20.
     */
    @Test
    public void testADepartureFunctionTheDecoderLacksIsNotRecorded() throws Exception
    {
        startFrom(2, false, 20, null);

        try
        {
            openTheTab();
            SettingsWindow.selectTab(0);
            chooseDecoder(Locomotive.decoderType.MM2);
            SettingsWindow.type("settings.address", "20");
            save();

            assertEquals(train.getDepartureFunc(), null, "MM2 kept a departure function it does not have");

            org.json.JSONObject record = Components.onTheEventThread(() -> (org.json.JSONObject) session.getPointProperty(square, "loc"));

            assertFalse(record.has("departureFunc"), "the setup recorded a function MM2 does not have: " + record);
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                try
                {
                    model.changeLocAddress(TRAIN, 0xCE9, Locomotive.decoderType.MFX);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }

                return null;
            });
        }
    }

    /**
     * Unticking Active pauses the train in the setup as the station menu's Is Active does, and a load keeps it paused;
     * ticking it again sets it going.
     *
     * MUTATION: leave `setLocomotiveActive` out of `AutonomyTab.apply`, and this fails.
     */
    @Test
    public void testActiveIsSavedAndSurvivesAReload() throws Exception
    {
        final String configuration = ui.getActiveDiagramConfiguration();

        startFrom(3, false, null, null);

        try
        {
            openTheTab();
            setTheTab(3, false, -1, -1, false);
            save();

            assertTrue(train.isAutonomyPaused(), "unticking Active did not pause the train");
            assertTrue(Components.onTheEventThread(() -> session.getPausedLocomotives().contains(TRAIN)),
                "unticking Active did not reach the setup");

            support.StandingTrain.reloadWithoutFolding(ui, configuration);

            assertTrue(model.getLocByName(TRAIN).isAutonomyPaused(), "a load set going a train paused in the tab");

            openTheTab();

            assertFalse(Components.onTheEventThread(SettingsWindow.part("autonomy.active", JCheckBox.class)::isSelected),
                "the tab reads going for a paused train");

            setTheTab(3, false, -1, -1, true);
            save();

            assertFalse(train.isAutonomyPaused(), "ticking Active did not set the train going");
        }
        finally
        {
            ui.setLocomotiveActive(session, train, true);
        }
    }

    /**
     * Nothing is written until Save: Cancel leaves the train and the setup as they were.
     *
     * MUTATION: have the tab's length spinner write the train's length as it changes, and this fails.
     */
    @Test
    public void testCancelWritesNothing() throws Exception
    {
        startFrom(4, false, null, null);

        String before = Components.onTheEventThread(() -> session.snapshotSetup().toString());

        openTheTab();
        setTheTab(9, true, 2, 2, false);

        assertTrue(SettingsWindow.save().isEnabled(), "Save is gray with the tab changed");

        SettingsWindow.cancelDiscarding();

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, 8000), "Cancel did not close the window");
        assertEquals(values(), "4 false null null", "Cancel wrote the tab onto the train");
        assertFalse(train.isAutonomyPaused(), "Cancel paused the train");
        assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before, "Cancel wrote the setup");
    }

    /**
     * The tab opens locked as the others do, while the train moves with the power on and while autonomy runs: the strip
     * says why, and Save stays gray with the tab's changes kept.
     *
     * MUTATION: drop `!blocked` from `LocomotiveSettingsWindow.changed`, and Save lights and this fails.
     */
    @Test
    public void testItOpensLockedWhileTheTrainMovesOrAutonomyRuns() throws Exception
    {
        startFrom(5, false, null, null);

        try
        {
            SettingsWindow.power(ui, model, true);
            train.setSpeed(40);

            openTheTab();
            setTheTab(8, true, -1, -1, null);

            assertLocked(I18n.f("loc.ui.settingsMovingStrip", TRAIN), "moving");
        }
        finally
        {
            train.setSpeed(0);
            SettingsWindow.power(ui, model, false);
        }

        try
        {
            Components.onTheEventThread(() ->
            {
                model.getAutoLayout().setStagingInProgress(true);

                return null;
            });

            openTheTab();
            setTheTab(8, true, -1, -1, null);

            assertLocked(I18n.t("loc.ui.settingsAutonomyStrip"), "running");
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                model.getAutoLayout().setStagingInProgress(false);

                return null;
            });

            fixture.quiet();
        }

        assertEquals(values(), "5 false null null", "a locked tab wrote the train");
    }

    /**
     * A running route holds the setup without locking the window: the tab's Save is refused and says so, and neither
     * the train nor the setup's copy of it changes, so no setup edit is made during a run.
     *
     * MUTATION: take `refuseWhileARunHoldsTheSetup` out of `LocomotiveSettingsWindow.write`, and this fails.
     */
    @Test
    public void testASaveWaitsForARunningRoute() throws Exception
    {
        startFrom(5, false, null, null);

        try
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, true);

            openTheTab();
            setTheTab(8, true, -1, -1, null);

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                SettingsWindow.press(SettingsWindow.save());

                assertTrue(answering.awaitAsked(1, 8000), "Save during a route said nothing");
            }

            assertNotNull(SettingsWindow.showing(), "a refused Save closed the window");
            assertEquals(values(), "5 false null null", "Save during a route wrote the train");
            assertEquals(Components.onTheEventThread(() -> ((org.json.JSONObject) session.getPointProperty(square, "loc"))
                .optInt("trainLength", -1)), Integer.valueOf(5), "Save during a route wrote the setup");
        }
        finally
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, false);
            fixture.quiet();
        }
    }

    /**
     * A number typed into Train Length, or into the preset speed on Basic Settings, lights Save as it is typed, as a
     * text box does, without Enter or leaving the box.
     *
     * MUTATION: have `typedAtOnce` leave the spinner as it was, and this fails.
     */
    @Test
    public void testATypedNumberLightsSaveAsItIsTyped() throws Exception
    {
        startFrom(5, false, null, null);

        try
        {
            openTheTab();

            assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "precondition: Save is lit unchanged");

            typeInto("autonomy.length", "9");

            assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "a typed train length left Save gray");

            SettingsWindow.closeIfOpen();

            fixture.quiet();

            assertTrue(SettingsWindow.open(ui, train, LocomotiveSettingsWindow.Tab.LOCOMOTIVE, null), "the window did not open");
            assertNotNull(SettingsWindow.awaited(), "the window is not on screen");

            int speed = (Integer) Components.onTheEventThread(SettingsWindow.part("settings.speed", JSpinner.class)::getValue);

            typeInto("settings.speed", Integer.toString(speed == 40 ? 41 : 40));

            assertTrue(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "a typed preset speed left Save gray");
        }
        finally
        {
            SettingsWindow.closeIfOpen();
        }
    }

    /** Types into a spinner's box as the keyboard does, leaving the focus where it is. */
    private static void typeInto(String name, String text)
    {
        Components.onTheEventThread(() ->
        {
            ((JSpinner.DefaultEditor) SettingsWindow.part(name, JSpinner.class).getEditor()).getTextField().setText(text);

            return null;
        });

        Components.settle();
    }

    private static void assertLocked(String strip, String why)
    {
        JLabel shown = SettingsWindow.part("settings.strip", JLabel.class);

        assertTrue(Components.onTheEventThread(shown::isVisible), "no strip while " + why);
        assertEquals(Components.onTheEventThread(shown::getText), strip, "the strip while " + why);
        assertFalse(Components.onTheEventThread(SettingsWindow.save()::isEnabled), "Save is live while " + why);
        assertEquals(Components.onTheEventThread(SettingsWindow.part("autonomy.length", JSpinner.class)::getValue), 8,
            "the typing was lost while " + why);
    }

    /**
     * A rename at Save that the setup cannot write, because its configuration file is held, is said over the window,
     * naming the configuration.  Retry while the file is still held says it again and leaves the file as it was; Retry
     * once it is let go writes the new name.
     *
     * MUTATION: in `AutonomyLifecycle.repairAutonomyLocomotive`, only log the failed save again; or leave the write
     * out of `sayAfterRenames`' Retry; and this fails.
     */
    @Test
    public void testARenameTheHeldSetupCannotSaveIsSaidAndRetried() throws Exception
    {
        final String was = "AU held name", name = "AU held renamed";
        final String configuration = session.getStore().getActiveConfiguration();
        final java.io.File file = configurationFile(configuration);
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));
        final Locomotive loc = model.newMFXLocomotive(was, 0xCEA);
        final String line = I18n.f("loc.ui.renameSetupNotUpdated", configuration, name);
        final java.util.concurrent.atomic.AtomicInteger said = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.List<byte[]> whileHeld = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.io.FileInputStream held = null;

        try
        {
            excludedHere(new org.json.JSONArray().put(was), true);

            final byte[] before = java.nio.file.Files.readAllBytes(file.toPath());
            final java.io.FileInputStream holding = held = new java.io.FileInputStream(file);

            assertTrue(new String(before, java.nio.charset.StandardCharsets.UTF_8).contains(was),
                "precondition: the configuration does not name the train");

            fixture.quiet();

            try (Answering answering = Answering.start(message ->
            {
                if (!message.contains(line)) return null;

                // The second time, the file is let go before Retry
                if (said.incrementAndGet() == 2)
                {
                    try
                    {
                        whileHeld.add(java.nio.file.Files.readAllBytes(file.toPath()));
                        holding.close();
                    }
                    catch (java.io.IOException e)
                    {
                        throw new java.io.UncheckedIOException(e);
                    }
                }

                return Answering.press(I18n.t("loc.ui.renameSetupRetry"));
            }))
            {
                renameAtSave(loc, name, answering);
                assertEquals(answering.asked(), java.util.Arrays.asList(line, line), "the held configuration was not"
                    + " said once at Save and once at the Retry that met it still held");
            }

            assertEquals(whileHeld.get(0), before, "the held configuration was written");

            String after = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);

            assertTrue(after.contains(name) && !after.contains(was), "Retry did not write the new name once the file"
                + " was let go");
        }
        finally
        {
            if (held != null) held.close();

            excludedHere(excluded, false);

            deleteAll(was, name);
        }
    }

    /**
     * A CSV import's Merge whose rename the setup cannot take, with no setup built and its configuration file locked,
     * is said in the import's finishing message, naming the configuration, and the file is left as it was.  Closing
     * the message forgets it: the next import that renames says nothing of it.
     *
     * MUTATION: in `AutonomyLifecycle.repairAutonomyLocomotive`, only log the no-session repair's failure again; or
     * leave out the forgetting in `sayAfterRenames`; and this fails.
     */
    @Test
    public void testAnImportsRenameTheLockedSetupCannotTakeIsSaidAtTheEnd() throws Exception
    {
        final String was = "AU import here", name = "AU import", was2 = "AU import two here", name2 = "AU import two";
        final String configuration = session.getStore().getActiveConfiguration();
        final java.io.File file = configurationFile(configuration);
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));
        final String line = I18n.f("loc.ui.renameSetupNotUpdated", configuration, name);
        final String key = I18n.t("loc.ui.renameSetupNotUpdated");
        final String start = key.substring(key.indexOf("{1}") + 3, key.indexOf("{0}"));
        final int address = freeAddress(13000), address2 = freeAddress(address - 1);
        final java.io.File csv = rows(name, address), csv2 = rows(name2, address2);

        model.newMFXLocomotive(was, address);
        model.newMFXLocomotive(was2, address2);

        try
        {
            excludedHere(new org.json.JSONArray().put(was), true);

            final byte[] before = java.nio.file.Files.readAllBytes(file.toPath());
            java.util.List<String> said;

            fixture.quiet();

            try (java.io.RandomAccessFile held = new java.io.RandomAccessFile(file, "rw");
                java.nio.channels.FileLock lock = held.getChannel().lock())
            {
                // As before autonomy is first opened; with the file locked, none can be built meanwhile
                setupBuilt(null);
                said = importThroughTheMenu(csv, start);
            }

            assertTrue(said.stream().anyMatch(message -> message.contains(line)), "the finishing message does not say"
                + " the configuration kept the old name: " + said);
            assertEquals(java.nio.file.Files.readAllBytes(file.toPath()), before, "the locked configuration was written");

            setupBuilt(session);

            java.util.List<String> next = importThroughTheMenu(csv2, start);

            assertNotNull(model.getLocByName(name2), "precondition: the second import's Merge did not rename");
            assertFalse(next.stream().anyMatch(message -> message.contains(start)), "a closed message's configuration"
                + " was said again at the next import: " + next);
        }
        finally
        {
            setupBuilt(session);

            excludedHere(excluded, false);

            csv.delete();
            csv2.delete();

            deleteAll(was, name, was2, name2);
        }
    }

    /**
     * A record of an unwritten rename that no door said, as a rename the model makes for itself leaves, is not said by
     * the next door's own rename: neither at a Locomotive Settings Save nor at a CSV import's end.
     *
     * MUTATION: leave out `forgetUnrepaired()` in `LocomotiveActions.writeNameAndAddress` or in
     * `LocomotiveCsvImport`; each fails its own assertion.
     */
    @Test
    public void testARecordNoDoorSaidIsNotSaidAtTheNextDoor() throws Exception
    {
        final String stray = "AU stray", strayTo = "AU stray renamed", was = "AU next", name = "AU next renamed",
            was2 = "AU next import here", name2 = "AU next import";
        final java.io.File file = new java.io.File(ui.localLayoutPath(), "config/autonomy/configuration-"
            + session.getStore().getActiveConfiguration() + ".json");
        final String key = I18n.t("loc.ui.renameSetupNotUpdated");
        final String start = key.substring(key.indexOf("{1}") + 3, key.indexOf("{0}"));
        final int address2 = freeAddress(12000);
        final java.io.File csv = rows(name2, address2);

        model.newMFXLocomotive(stray, 0xCEB);
        model.newMFXLocomotive(was, 0xCEC);
        model.newMFXLocomotive(was2, address2);

        try
        {
            leftUnsaid(stray, strayTo, file);
            fixture.quiet();

            try (Answering answering = Answering.start(message -> message.contains(start) ? Answering.pressAt(1) : null))
            {
                renameAtSave(model.getLocByName(was), name, answering);
                Components.settle();

                assertNotNull(model.getLocByName(name), "precondition: Save did not rename");
                assertFalse(answering.asked().stream().anyMatch(message -> message.contains(start)), "a record left"
                    + " elsewhere was said at Save: " + answering.asked());
            }

            leftUnsaid(strayTo, stray, file);

            java.util.List<String> said = importThroughTheMenu(csv, start);

            assertNotNull(model.getLocByName(name2), "precondition: the import's Merge did not rename");
            assertFalse(said.stream().anyMatch(message -> message.contains(start)), "a record left elsewhere was said"
                + " at the import's end: " + said);
        }
        finally
        {
            csv.delete();

            for (String n : new String[] {stray, strayTo, was, name, was2, name2})
            {
                if (model.getLocByName(n) != null) model.deleteLoc(n);
            }
        }
    }

    /**
     * A deletion and a rename onto the deleted name that the held setup cannot save, with the session open: Retry once
     * the file is let go writes what the session holds, the renamed locomotive kept, not the deletion done again over
     * it.
     *
     * MUTATION: in `AutonomyLifecycle.sayAfterRenames`, Retry by doing the door's repairs again over the session's
     * store before writing it, and this fails.
     */
    @Test
    public void testARetryAfterADeletionAndARenameOntoItKeepsTheRenamed() throws Exception
    {
        final String doomed = "AU held doomed", was = "AU held onto";
        final String configuration = session.getStore().getActiveConfiguration();
        final String line = I18n.f("loc.ui.renameSetupNotUpdated", configuration, doomed);
        model.newMFXLocomotive(doomed, 0xCEB);
        model.newMFXLocomotive(was, 0xCEC);
        final java.io.File file = configurationFile(configuration);
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));

        try
        {
            fixture.quiet();
            support.Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class, "forgetUnrepaired")
                .invoke(ui.autonomyLifecycle());
            excludedHere(new org.json.JSONArray().put(doomed).put(was), true);
            try (java.io.FileInputStream held = new java.io.FileInputStream(file))
            {
                Components.onTheEventThread(() ->
                {
                    model.deleteLoc(doomed);
                    model.renameLoc(was, doomed);
                    return null;
                });
            }

            java.lang.reflect.Method say = support.Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class,
                "sayAfterRenames", java.awt.Component.class, String.class, java.util.List.class, String.class);
            try (Answering answering = Answering.start(m -> m.contains(line)
                ? Answering.press(I18n.t("loc.ui.renameSetupRetry")) : null))
            {
                SwingUtilities.invokeLater(() ->
                {
                    try
                    {
                        say.invoke(ui.autonomyLifecycle(), ui, null, java.util.Collections.emptyList(), null);
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new IllegalStateException(e);
                    }
                });
                assertTrue(answering.awaitAsked(1, 15000), "the deletion and rename were not said");
                support.AskingWindows.awaitAnswered();
                Components.settle();
                assertEquals(answering.asked(), java.util.Arrays.asList(line), "not said once, naming the rename");
            }

            String after = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(after.split("\"" + doomed + "\"", -1).length == 2 && !after.contains(was), "Retry did not keep"
                + " the renamed locomotive alone: " + after);
        }
        finally
        {
            excludedHere(excluded, false);
            deleteAll(doomed, was);
        }
    }

    /**
     * Two configurations with no setup built, the second's file held: a deletion and a rename onto the deleted name
     * reach the first and miss the second.  Retry while it is still held says it again; Retry once it is let go gives
     * each file only what it lacked: the first keeps the renamed locomotive, the second takes the deletion, then the
     * rename; and a setup built from the files afterwards and saved keeps them so.
     *
     * MUTATION: in `AutonomyCompanionStore.load`, apply every file's pending repairs to every configuration; or, in
     * `save`, keep a written file's; or, in `AutonomyLifecycle.stillLacking`, keep every record; and this fails.
     */
    @Test
    public void testARetryGivesEachConfigurationOnlyWhatItsFileLacked() throws Exception
    {
        final String doomed = "AU two doomed", was = "AU two onto";
        final String line = I18n.f("loc.ui.renameSetupNotUpdated", "Zzz", doomed);
        final java.io.File file = configurationFile(session.getStore().getActiveConfiguration()),
            second = configurationFile("Zzz"), setupFile = new java.io.File(ui.localLayoutPath(),
            "config/autonomy/setup.json");
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));
        final byte[] setup = java.nio.file.Files.readAllBytes(setupFile.toPath());
        java.io.FileInputStream held = null;
        model.newMFXLocomotive(doomed, 0xCEB);
        model.newMFXLocomotive(was, 0xCEC);

        try
        {
            fixture.quiet();
            excludedHere(new org.json.JSONArray().put(doomed).put(was), true);
            org.json.JSONObject copy = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(
                file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            copy.put("name", "Zzz");
            java.nio.file.Files.write(second.toPath(), copy.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));

            held = new java.io.FileInputStream(second);
            setupBuilt(null);
            Components.onTheEventThread(() ->
            {
                support.Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class, "forgetUnrepaired")
                    .invoke(ui.autonomyLifecycle());
                model.deleteLoc(doomed);
                model.renameLoc(was, doomed);
                return null;
            });

            // Retry with no setup built (a showing dialog may build one) meets Zzz still held
            Components.onTheEventThread(() ->
            {
                support.Reflect.set(TrainControlUI.class, ui, "autonomySession", null);
                return support.Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class, "writeAgain")
                    .invoke(ui.autonomyLifecycle());
            });
            assertEquals(new org.traincontrol.automationui.AutonomyCompanionStore(new java.io.File(ui.localLayoutPath()))
                .lacking((org.traincontrol.automationui.AutonomyCompanionStore.Lacking) support.Reflect.get(
                org.traincontrol.gui.AutonomyLifecycle.class, ui.autonomyLifecycle(), "lacking"), java.util.Arrays.asList(
                session.getStore().getActiveConfiguration(), "Zzz")), java.util.Arrays.asList("Zzz"),
                "a Retry through a bare store did not keep Zzz as left");
            assertEquals(retried(line, held), java.util.Arrays.asList(line, line), "not said once, naming Zzz and the"
                + " rename, and again at the Retry that met it still held");
            Components.onTheEventThread(() ->
            {
                support.Reflect.set(TrainControlUI.class, ui, "autonomySession", null);
                ui.getAutonomySession().getStore().save();
                return null;
            });

            for (java.io.File f : new java.io.File[] {file, second})
            {
                String after = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(after.split("\"" + doomed + "\"", -1).length == 2 && !after.contains(was), "after Retry "
                    + f.getName() + " does not keep the renamed locomotive alone: " + after);
            }
        }
        finally
        {
            if (held != null) held.close();
            setupBuilt(session);
            second.delete();
            java.nio.file.Files.write(setupFile.toPath(), setup);
            excludedHere(excluded, true);
            deleteAll(doomed, was);
        }
    }

    /**
     * A rename the held setup cannot save, then the setup built again from its files before Retry, as a layout reload
     * does: the new setup reads the rename its file lacks, and Retry once the file is let go writes the new name.
     *
     * MUTATION: in `AutonomyLifecycle.getAutonomySession`, leave out `shareLacking`, and this fails.
     */
    @Test
    public void testARetryAfterTheSetupIsBuiltAgainWritesTheRename() throws Exception
    {
        final String was = "AU again was", name = "AU again renamed";
        final String configuration = session.getStore().getActiveConfiguration();
        final String line = I18n.f("loc.ui.renameSetupNotUpdated", configuration, name);
        final java.io.File file = configurationFile(configuration);
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));
        model.newMFXLocomotive(was, 0xCED);

        try
        {
            fixture.quiet();
            excludedHere(new org.json.JSONArray().put(was), true);

            try (java.io.FileInputStream held = new java.io.FileInputStream(file))
            {
                Components.onTheEventThread(() ->
                {
                    support.Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class, "forgetUnrepaired")
                        .invoke(ui.autonomyLifecycle());
                    model.renameLoc(was, name);
                    return null;
                });
            }

            setupBuilt(null);
            assertNotNull(Components.onTheEventThread(ui::getAutonomySession), "precondition: no setup was built");
            assertEquals(retried(line, null), java.util.Arrays.asList(line), "not said once, naming the rename");

            String after = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(after.contains(name) && !after.contains(was), "Retry over the setup built again did not write"
                + " the new name: " + after);
        }
        finally
        {
            setupBuilt(session);
            excludedHere(excluded, true);
            deleteAll(was, name);
        }
    }

    /**
     * A rename the held setup cannot save while the autonomy editor is open, the file let go, then the editor canceled
     * over an edit of its own: Cancel puts back the setup as the editor opened it and saves, with the new name.
     *
     * MUTATION: in `AutonomyLifecycle.repairAutonomyLocomotive`, follow the rename into the editor only when the save
     * worked, and this fails.
     */
    @Test
    public void testCancelingTheEditorAfterAnUnsavedRenameKeepsTheNewName() throws Exception
    {
        final String was = "AU cancel was", name = "AU cancel renamed";
        final java.io.File file = configurationFile(session.getStore().getActiveConfiguration());
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));
        final java.lang.reflect.Field open = support.Reflect.field(TrainControlUI.class, "openEditor");
        final Object before = open.get(ui);
        final LayoutEditor[] editor = new LayoutEditor[1];
        model.newMFXLocomotive(was, freeAddress(11000));

        try
        {
            fixture.quiet();
            excludedHere(new org.json.JSONArray().put(was), true);
            SwingUtilities.invokeAndWait(() ->
            {
                editor[0] = new LayoutEditor(model.getLayout("1 - Main"), 30, ui, 0);
                editor[0].render();
                editor[0].setAutonomyMode(session);
            });
            Components.settle();
            assertTrue(editor[0].isDisplayable() && editor[0].isAutonomyMode(), "precondition: no autonomy editor");
            open.set(ui, editor[0]);
            leftUnsaid(was, name, file);

            // An edit of the editor's own, so Cancel asks and puts its snapshot back
            excludedHere(new org.json.JSONArray().put(name).put(TRAIN), false);

            final String confirm = I18n.t("autosetup.ui.confirmExitWithoutSaving");
            final java.util.List<String> asked;
            try (Answering answering = Answering.start(m -> m.contains(confirm)
                ? Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[0])) : Answering.pressAt(0)))
            {
                Components.onTheEventThread(() -> support.Reflect.method(LayoutEditor.class, "confirmExit").invoke(editor[0]));
                Components.settle();
                asked = answering.asked();
            }

            String after = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(asked.stream().anyMatch(m -> m.contains(confirm)), "precondition: Cancel did not ask: " + asked);
            assertTrue(after.contains(name) && !after.contains(was), "Cancel wrote the old name back: " + after + asked);
        }
        finally
        {
            open.set(ui, before);
            if (editor[0] != null) SwingUtilities.invokeAndWait(editor[0]::dispose);
            excludedHere(excluded, true);
            deleteAll(was, name);
        }
    }

    /**
     * A rename the held setup cannot save, then, the file let go, a second rename whose save writes it whole: the door
     * says nothing, as the file no longer lacks the first.
     *
     * MUTATION: in `AutonomyLifecycle.sayAfterRenames`, show every record taken, not `stillLacking(taken)`, and this
     * fails.
     */
    @Test
    public void testARenameItsFileWasWrittenWithSinceIsNotSaid() throws Exception
    {
        final String a = "AU since one", b = "AU since one renamed", c = "AU since two", d = "AU since two renamed";
        final java.io.File file = configurationFile(session.getStore().getActiveConfiguration());
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));
        final String key = I18n.t("loc.ui.renameSetupNotUpdated");
        final String start = key.substring(key.indexOf("{1}") + 3, key.indexOf("{0}"));
        model.newMFXLocomotive(a, freeAddress(11000));
        model.newMFXLocomotive(c, freeAddress(11000));

        try
        {
            fixture.quiet();
            excludedHere(new org.json.JSONArray().put(a).put(c), true);
            leftUnsaid(a, b, file);
            Components.onTheEventThread(() -> model.renameLoc(c, d));

            String now = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(now.contains(b) && now.contains(d) && !now.contains("\"" + a + "\""), "precondition: the second"
                + " rename's save did not write both: " + now);

            try (Answering answering = Answering.start(m -> m.contains(start) ? Answering.pressAt(1) : null))
            {
                Components.onTheEventThread(() -> support.Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class,
                    "sayAfterRenames", java.awt.Component.class, String.class, java.util.List.class, String.class)
                    .invoke(ui.autonomyLifecycle(), ui, null, java.util.Collections.emptyList(), null));
                Components.settle();
                assertEquals(answering.asked(), java.util.Collections.emptyList(), "a rename its file was written with"
                    + " since was said");
            }
        }
        finally
        {
            excludedHere(excluded, true);
            deleteAll(a, b, c, d);
        }
    }

    /**
     * A rename at Save while the layout folder cannot be reached, with no setup built and with one, is said over the
     * window like a held file, naming the configuration; Retry once the folder is back writes the new name, which the
     * setup built keeps in memory too.
     *
     * MUTATION: in `AutonomyCompanionStore.repairOnDisk`, return false for every setup that does not exist; or in
     * `AutonomyLifecycle.repairAutonomyLocomotive`, return for every session whose setup does not exist; and this fails.
     */
    @Test
    public void testARenameWhileTheLayoutFolderIsAwayIsSaidAndRetried() throws Exception
    {
        final String configuration = session.getStore().getActiveConfiguration();
        final java.io.File file = configurationFile(configuration);
        final java.io.File layout = new java.io.File(ui.localLayoutPath());
        final java.io.File away = new java.io.File(layout.getPath() + "-away");
        final Object excluded = Components.onTheEventThread(() -> session.getPointProperty(square, "excludedLocs"));

        for (boolean built : new boolean[] {false, true})
        {
            final String was = "AU away was " + built, name = "AU away renamed " + built;
            final String line = I18n.f("loc.ui.renameSetupNotUpdated", configuration, name);
            final Locomotive loc = model.newMFXLocomotive(was, built ? 0xCF2 : 0xCF1);

            try
            {
                excludedHere(new org.json.JSONArray().put(was), true);
                if (!built) setupBuilt(null);
                fixture.quiet();
                assertTrue(layout.renameTo(away), "precondition: the layout folder could not be taken away");

                try (Answering answering = Answering.start(m ->
                {
                    if (!m.contains(line)) return null;

                    // Back before Retry
                    away.renameTo(layout);
                    return Answering.press(I18n.t("loc.ui.renameSetupRetry"));
                }))
                {
                    renameAtSave(loc, name, answering);
                    assertEquals(answering.asked(), java.util.Arrays.asList(line), "the rename was not said once, with"
                        + " the setup built: " + built);
                }
                finally
                {
                    if (away.isDirectory()) away.renameTo(layout);
                }

                String after = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(after.contains(name) && !after.contains(was), "Retry did not write the new name, with the"
                    + " setup built: " + built);
                if (built) assertTrue(String.valueOf(Components.onTheEventThread(() -> session.getPointProperty(square,
                    "excludedLocs"))).contains(name), "the setup built kept the old name in memory");
            }
            finally
            {
                setupBuilt(session);
                excludedHere(excluded, true);
                deleteAll(was, name);
            }
        }
    }

    /**
     * Says what renames left through `sayAfterRenames`, pressing Retry at `line`, letting `held` go (null: none) as it
     * is said the second time; what was said.
     */
    private static java.util.List<String> retried(String line, java.io.Closeable held) throws Exception
    {
        final java.util.concurrent.atomic.AtomicInteger said = new java.util.concurrent.atomic.AtomicInteger();

        try (java.io.Closeable holding = held; Answering answering = Answering.start(m ->
        {
            if (!m.contains(line)) return null;

            if (held != null && said.incrementAndGet() == 2)
            {
                try
                {
                    held.close();
                }
                catch (java.io.IOException e)
                {
                    throw new java.io.UncheckedIOException(e);
                }
            }

            return Answering.press(I18n.t("loc.ui.renameSetupRetry"));
        }))
        {
            java.lang.reflect.Method say = support.Reflect.method(org.traincontrol.gui.AutonomyLifecycle.class,
                "sayAfterRenames", java.awt.Component.class, String.class, java.util.List.class, String.class);
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    say.invoke(ui.autonomyLifecycle(), ui, null, java.util.Collections.emptyList(), null);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });
            assertTrue(answering.awaitAsked(held == null ? 1 : 2, 15000), "the renames were not said: "
                + answering.asked());
            support.AskingWindows.awaitAnswered();
            Components.settle();

            return answering.asked();
        }
    }

    /** Sets the square's excluded locomotives on the event thread, and saves the setup when `save`. */
    private static void excludedHere(Object value, boolean save)
    {
        Components.onTheEventThread(() ->
        {
            session.setPointProperty(square, "excludedLocs", value);
            if (save) session.getStore().save();
            return null;
        });
    }

    /** Deletes those of these locomotives that exist. */
    private static void deleteAll(String... names)
    {
        for (String n : names) if (model.getLocByName(n) != null) model.deleteLoc(n);
    }

    /** The file this configuration of the railway is kept in. */
    private static java.io.File configurationFile(String configuration)
    {
        return new java.io.File(ui.localLayoutPath(), "config/autonomy/configuration-" + configuration + ".json");
    }

    /** Renames `loc` to `name` through Locomotive Settings' Save, which closes the window; `answering` answers it. */
    private static void renameAtSave(Locomotive loc, String name, Answering answering) throws Exception
    {
        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsWindow.Tab.values()[0], null),
            "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
        SettingsWindow.type("settings.name", name);
        SettingsWindow.press(SettingsWindow.save());
        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, 8000), "Save did not close the"
            + " window: " + answering.asked());
    }

    /** Renames through the model alone, as no door does, while `file` is held: a record that no door has said. */
    private static void leftUnsaid(String from, String to, java.io.File file) throws Exception
    {
        try (java.io.FileInputStream held = new java.io.FileInputStream(file))
        {
            Components.onTheEventThread(() ->
            {
                model.renameLoc(from, to);
                return null;
            });
        }

        assertFalse(((java.util.Collection<?>) support.Reflect.get(org.traincontrol.gui.AutonomyLifecycle.class,
            ui.autonomyLifecycle(), "unrepaired")).isEmpty(), "precondition: the held rename left no record");
    }

    /** Puts `built` in the window as the setup it has built, null as none, as a user's first opening would. */
    private static void setupBuilt(AutonomySession built)
    {
        Components.onTheEventThread(() ->
        {
            support.Reflect.set(TrainControlUI.class, ui, "autonomySession", built);
            return null;
        });
    }

    /** Imports `file` through Locomotives > Import from CSV, Import pressed and the finishing message closed; what was said. */
    private static java.util.List<String> importThroughTheMenu(java.io.File file, String unrepaired) throws Exception
    {
        try (Answering answering = Answering.start(message -> message.contains(unrepaired) ? Answering.pressAt(1) : null))
        {
            answering.choosing(file);

            javax.swing.JMenuItem door = (javax.swing.JMenuItem) support.Reflect.get(TrainControlUI.class, ui,
                "importLocsFromCSVMenuItem");

            SwingUtilities.invokeLater(door::doClick);

            assertTrue(answering.awaitAsked(2, 30000), "the import did not finish: " + answering.asked());

            Components.settle();

            return answering.asked();
        }
    }

    /** A file of one mfx locomotive row, as Export to CSV writes it. */
    private static java.io.File rows(String name, int address) throws Exception
    {
        java.io.File file = java.io.File.createTempFile("tc-locs", ".csv");

        java.nio.file.Files.write(file.toPath(), ("Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,"
            + "Start Year,End Year,RailwayName,Notes\n" + name + ",,MFX," + address + ",0,0:00:00,0,0,,\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return file;
    }

    /** An address no locomotive holds, counting down from `from`. */
    private static int freeAddress(int from)
    {
        java.util.Set<Integer> held = new java.util.HashSet<>();

        for (Locomotive l : model.getLocomotives()) held.add(l.getAddress());

        int address = from;

        while (held.contains(address)) address--;

        return address;
    }
}
