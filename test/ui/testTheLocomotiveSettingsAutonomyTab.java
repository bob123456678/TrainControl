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
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.SettingsWindow;

/**
 * The Autonomy tab of Locomotive Settings (FR-048): train length, reversible, the departure and arrival functions and
 * Active, written at Save.  The setup of an old file keeps its own copy of a standing train, which a load copies back
 * onto the locomotive, so a Save reaches that copy too.  On the frozen copy of Adam's railway, with a train standing on
 * BottomMainB.
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

        assertTrue(SettingsWindow.open(ui, train, LocomotiveSettingsFrame.Tab.AUTONOMY, null), "the window did not open");
        assertNotNull(SettingsWindow.awaited(), "the window is not on screen");
        assertEquals(SettingsWindow.tabShowing(), LocomotiveSettingsFrame.Tab.AUTONOMY.ordinal(), "the Autonomy tab is not showing");
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

        try (Answering answering = Answering.start(message -> Answering.pressAt(1)))
        {
            SettingsWindow.press(SettingsWindow.cancel());
        }

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, 8000), "Cancel did not close the window");
        assertEquals(values(), "4 false null null", "Cancel wrote the tab onto the train");
        assertFalse(train.isAutonomyPaused(), "Cancel paused the train");
        assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before, "Cancel wrote the setup");
    }

    /**
     * The tab opens locked as the others do, while the train moves with the power on and while autonomy runs: the strip
     * says why, and Save stays gray with the tab's changes kept.
     *
     * MUTATION: drop `!blocked` from `LocomotiveSettingsFrame.changed`, and Save lights and this fails.
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
     * MUTATION: take `refuseWhileARunHoldsTheSetup` out of `LocomotiveSettingsFrame.write`, and this fails.
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

            assertTrue(SettingsWindow.open(ui, train, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "the window did not open");
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

}
