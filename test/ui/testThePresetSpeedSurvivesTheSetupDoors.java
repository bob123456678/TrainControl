package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
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
 * The preset speed saved in the Locomotive Settings window (FR-048) is the speed autonomy runs the train at, and the
 * setup stores a standing train by its name alone: so a speed saved with a train standing on a station survives every
 * door that reloads the setup, as Alt+U's and the station dialog's own write of the same number does.  On the frozen
 * copy of Adam's railway, with a train standing on BottomMainB.
 *
 * MUTATION: make `Layout.parseAuto` apply the default speed whatever the train holds (`if (l.getPreferredSpeed() == 0)`
 * becomes `if (true)`), and the reload claims fail.
 */
public class testThePresetSpeedSurvivesTheSetupDoors
{
    private static final String TRAIN = "PS standing train";

    private static support.SettingsFixture fixture;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Locomotive train;
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
            model.newMM2Locomotive(TRAIN, 3777);

            made = true;
        }

        train = model.getLocByName(TRAIN);

        final TileKey square = squareNamed("BottomMainB");

        support.StandingTrain.standOn(model, session, TRAIN, square);
    }

    /** The window's Save rewrites the run's saved data, which the next classes in the lane read. */
    private static final support.SavedData FOUND = support.SavedData.take();

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
            try
            {
                if (fixture != null) fixture.closeSandbox();
            }
            finally
            {
                FOUND.putBack();
            }
        }
    }

    /**
     * A speed saved in the window with the train standing is still the train's after each door that reloads the
     * setup: the editor's fold and a load, a load that folds first, a load that does not, the rebuild from the setup,
     * and the exit's fold followed by a load.  Each door is shown to have replaced the railway.
     */
    @Test
    public void testASpeedSavedInTheWindowSurvivesEveryReload() throws Exception
    {
        final String configuration = ui.getActiveDiagramConfiguration();

        assertNotNull(configuration, "precondition: no setup is loaded");

        List<Runnable> doors = new ArrayList<>();
        List<String> names = new ArrayList<>();

        names.add("the fold the editor's door makes, then a load");
        doors.add(() ->
        {
            ui.captureRunningLayout();
            ui.getAutonomyViewerPanel().load(configuration, false, false);
        });

        names.add("a load that folds the running layout first");
        doors.add(() -> ui.getAutonomyViewerPanel().load(configuration, false, true));

        names.add("a load that does not fold");
        doors.add(() -> ui.getAutonomyViewerPanel().load(configuration, false, false));

        names.add("the rebuild from the setup");
        doors.add(() -> ui.rebuildRunningLayoutFromSetup());

        names.add("the exit's fold, then a load");
        doors.add(() ->
        {
            ui.saveState(false, true);
            ui.getAutonomyViewerPanel().load(configuration, false, true);
        });

        int speed = 31;

        for (int i = 0; i < doors.size(); i++)
        {
            speed += 7;

            final int saved = speed;

            train.setPreferredSpeed(20);

            saveInTheWindow(saved);

            assertEquals(train.getPreferredSpeed(), saved, "precondition: Save did not write the preset speed");

            final Layout before = model.getAutoLayout();
            final Runnable door = doors.get(i);

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
            {
                SwingUtilities.invokeAndWait(door);

                Components.settle();
                Components.settle();
            }

            assertNotSame(model.getAutoLayout(), before, "precondition: " + names.get(i) + " did not replace the railway");

            assertEquals(train.getPreferredSpeed(), saved, names.get(i) + " undid the speed saved in the window");
        }
    }

    /**
     * Alt+U's Save Current Speed and a plain `setPreferredSpeed` on the locomotive, which is all the retired station
     * dialog did with the speed, survive the same load, so the window is no different.
     */
    @Test
    public void testTheOtherDoorsSpeedsSurviveTheSameLoad() throws Exception
    {
        final String configuration = ui.getActiveDiagramConfiguration();

        train.setPreferredSpeed(58);

        reload(configuration);

        assertEquals(train.getPreferredSpeed(), 58, "a speed written straight to the locomotive, as the station dialog does, was undone by a load");

        train.setSpeed(47);

        ui.savePreferredSpeed(train);

        assertTrue(Components.waitFor(() -> train.getPreferredSpeed() == 47, 8000), "Alt+U did not keep the speed");

        reload(configuration);

        assertEquals(train.getPreferredSpeed(), 47, "a speed saved with Alt+U was undone by a load");

        train.setSpeed(0);
    }

    /**
     * An old-format setup keeps the whole standing train, speed included, in the store until a fold rewrites it to the
     * name.  Untouched, a load still reads that speed; a speed saved in the window wins over the stale copy at a load
     * that does not fold and at the rebuild from the setup.
     *
     * MUTATION: leave `ui.recordPresetSpeedInTheSetup` out of `LocomotiveSettingsFrame.write`, and this fails.
     */
    @Test
    public void testAnOldSetupsStoredSpeedDoesNotUndoTheWindowsSave() throws Exception
    {
        final String configuration = ui.getActiveDiagramConfiguration();
        final TileKey square = squareNamed("BottomMainB");

        SwingUtilities.invokeAndWait(() -> session.setPointProperty(square, "loc",
            new org.json.JSONObject().put("name", TRAIN).put("speed", 20)));

        // What a file read does is unchanged: the stored speed is the train's after a load that does not fold
        train.setPreferredSpeed(50);

        reloadWithoutFolding(configuration);

        assertEquals(train.getPreferredSpeed(), 20, "an untouched old setup no longer loads the speed it stores");

        saveInTheWindow(66);

        reloadWithoutFolding(configuration);

        assertEquals(train.getPreferredSpeed(), 66, "a load without a fold put the old setup's speed over the one saved in the window");

        saveInTheWindow(71);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            Components.settle();
            Components.settle();
        }

        assertEquals(train.getPreferredSpeed(), 71, "the rebuild from the setup put the old setup's speed over the one saved in the window");
    }

    private static final String COPY = "PS copy";

    /** Everything the setup keeps on disk, by file. */
    private static java.util.Map<String, String> setupFiles() throws Exception
    {
        return support.StandingTrain.setupFiles(fixture.sandbox.getFolder());
    }

    /** The old-format setup the claims start from: the train stands with a speed of 20 on its square. */
    private static String oldSetupStoringSpeed20() throws Exception
    {
        storeEverywhere(squareNamed("BottomMainB"), new org.json.JSONObject().put("name", TRAIN).put("speed", 20));

        return ui.getActiveDiagramConfiguration();
    }

    /** The square's record of the train, set to this, in every configuration there is. */
    private static void storeEverywhere(TileKey square, org.json.JSONObject record) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> session.setPointProperty(square, "loc", record));
    }

    /**
     * A second configuration that is a copy of the active one keeps the window's speed too: loaded without a fold, it
     * does not bring the stale one back.
     *
     * MUTATION: walk only the active configuration in `AutonomySession.updateStoredRecord`, and this fails.
     */
    @Test
    public void testAnotherConfigurationDoesNotBringTheStaleSpeedBack() throws Exception
    {
        final String original = ui.getActiveDiagramConfiguration();
        final TileKey square = squareNamed("BottomMainB");

        storeEverywhere(square, new org.json.JSONObject().put("name", TRAIN).put("speed", 20));

        session.getStore().createConfiguration(COPY, original);

        try
        {
            saveInTheWindow(66);

            reloadWithoutFolding(COPY);

            assertEquals(train.getPreferredSpeed(), 66, "the copy configuration put the stale speed over the one saved in the window");
        }
        finally
        {
            reloadWithoutFolding(original);

            session.getStore().deleteConfiguration(COPY);
        }
    }

    /**
     * A train on an excluded page keeps the window's speed too: the page comes back and a load reads 66, not 20.
     *
     * MUTATION: skip the points of excluded pages in `AutonomySession.updateStoredRecord`, and this fails.
     */
    @Test
    public void testATrainOnAnExcludedPageDoesNotBringTheStaleSpeedBack() throws Exception
    {
        final String configuration = oldSetupStoringSpeed20();
        final TileKey square = squareNamed("BottomMainB");

        SwingUtilities.invokeAndWait(() -> session.setPageExcluded(square.getPage(), true));

        try
        {
            saveInTheWindow(66);
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> session.setPageExcluded(square.getPage(), false));
        }

        reloadWithoutFolding(configuration);

        assertEquals(train.getPreferredSpeed(), 66, "the excluded page's stale speed came back");
    }

    /**
     * A Save whose stored speed already equals the setup's does not rewrite the setup, and a setup that stores no
     * speed is left byte for byte as it was, in every configuration.
     *
     * MUTATION: drop the equal-value test in `AutonomySession.updateStoredRecord`, and this fails.
     */
    @Test
    public void testASetupIsNotRewrittenForNothing() throws Exception
    {
        final String original = ui.getActiveDiagramConfiguration();
        final TileKey square = squareNamed("BottomMainB");

        // Stores no speed: nothing to change, in either configuration
        storeEverywhere(square, new org.json.JSONObject().put("name", TRAIN));

        session.getStore().createConfiguration(COPY, original);

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    session.saveWithoutReconciling();
                }
                catch (java.io.IOException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            java.util.Map<String, String> before = setupFiles();

            saveInTheWindow(66);

            assertEquals(setupFiles(), before, "a setup that stores no speed was rewritten");

            // Stores this speed already
            storeEverywhere(square, new org.json.JSONObject().put("name", TRAIN).put("speed", 40));

            assertTrue(Components.onTheEventThread(() -> session.updateStoredSpeed(TRAIN, 41)), "a different speed was not stored");
            assertFalse(Components.onTheEventThread(() -> session.updateStoredSpeed(TRAIN, 41)), "the same speed counted as a change");
        }
        finally
        {
            session.getStore().deleteConfiguration(COPY);
        }
    }

    /**
     * A saved 0 removes the stored speed and a load then gives the layout's default speed, which is what a train with no
     * speed has always been given on a load.
     *
     * MUTATION: write the speed 0 into the stored record instead of removing it, and this fails.
     */
    @Test
    public void testASavedZeroLoadsTheDefaultSpeed() throws Exception
    {
        final String configuration = oldSetupStoringSpeed20();
        final TileKey square = squareNamed("BottomMainB");

        saveInTheWindow(0);

        assertTrue(Components.onTheEventThread(() -> !((org.json.JSONObject) session.getPointProperty(square, "loc")).has("speed")),
            "a saved 0 left a speed in the setup");

        reloadWithoutFolding(configuration);

        assertEquals(train.getPreferredSpeed(), model.getAutoLayout().getDefaultLocSpeed(), "a saved 0 did not load the default speed");
    }

    private static void reloadWithoutFolding(String configuration) throws Exception
    {
        support.StandingTrain.reloadWithoutFolding(ui, configuration);
    }

    private static void reload(String configuration) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(configuration, false, true));

            Components.settle();
            Components.settle();
        }
    }

    private static void saveInTheWindow(int speed) throws Exception
    {
        // Something else to start from, so that the Save has a change to write
        train.setPreferredSpeed(speed == 1 ? 2 : 1);

        assertTrue(SettingsWindow.open(ui, train, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null), "the window did not open");
        assertNotNull(SettingsWindow.awaited());

        Components.onTheEventThread(() ->
        {
            SettingsWindow.part("settings.speed", JSpinner.class).setValue(speed);

            return null;
        });

        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() == null, 8000), "Save did not close the window");
    }

    private static TileKey squareNamed(String name)
    {
        return support.StandingTrain.squareNamed(session, name);
    }
}
