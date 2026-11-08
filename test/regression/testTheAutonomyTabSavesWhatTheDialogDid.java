package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
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
import support.Components;

/**
 * Adam's rule for FR-048, "make sure behavior is unchanged" for autonomy configs and file reads: the station dialog
 * (`GraphLocAssign`) retires, and a train's length, reversible flag and functions set in the Autonomy tab of Locomotive
 * Settings leave the saved setup, and the train after a reload, as they are when the same values are written straight
 * onto the locomotive, which the dialog's OK was shown to do on b51f7e3a.  On the frozen copy of Adam's railway, with a train standing on BottomMainB.
 *
 * MUTATION: let `AutonomyCompanionStore.updateRecordInPoints` add a key the setup's record of the train lacks (drop
 * its `!stored.has(key)`), and the saved setup differs and this fails.
 */
public class testTheAutonomyTabSavesWhatTheDialogDid
{
    private static final String TRAIN = "AT standing train";

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
            model.newMM2Locomotive(TRAIN, 3778);

            made = true;
        }

        train = model.getLocByName(TRAIN);

        final TileKey square = support.StandingTrain.squareNamed(session, "BottomMainB");

        support.StandingTrain.standOn(model, session, TRAIN, square);

        // Its heading and tail, as a placement door records them: the dialog wrote them where they were missing
        SwingUtilities.invokeAndWait(() ->
        {
            session.setFacing(square, org.traincontrol.automationui.TilePorts.Side.E);
            session.setArrivedFrom(square, "W");
        });

        // A speed of its own: the dialog wrote the layout's default over a speed of 0
        train.setPreferredSpeed(40);
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

    /** The values each claim sets: length, reversible, departure function, arrival function. */
    private static final Object[] SET = { 7, true, 2, 1 };

    /**
     * The setup saved after the door's edit, file by file, and the train's values after a load that does not fold,
     * equal those of the same values written onto the locomotive and saved the same way.
     */
    @Test
    public void testTheDoorLeavesTheSetupAndTheTrainAsAPlainWriteDoes() throws Exception
    {
        final String configuration = ui.getActiveDiagramConfiguration();

        assertNotNull(configuration, "precondition: no setup is loaded");

        startFromTheSame();

        Components.onTheEventThread(() ->
        {
            train.setTrainLength((Integer) SET[0]);
            train.setReversible((Boolean) SET[1]);
            train.setDepartureFunc((Integer) SET[2]);
            train.setArrivalFunc((Integer) SET[3]);
            model.getAutoLayout().applyDefaultLocCallbacks(train);

            return null;
        });

        java.util.Map<String, String> plain = savedSetup();
        String plainTrain = afterAReload(configuration);

        startFromTheSame();

        throughTheDoor();

        java.util.Map<String, String> door = savedSetup();
        String doorTrain = afterAReload(configuration);

        assertEquals(plainTrain, "7 true 2 1 40", "precondition: a plain write of the values did not survive a reload");
        assertEquals(doorTrain, plainTrain, "the train's values after a reload differ from a plain write's");
        assertEquals(door, plain, "the setup saved after the door's edit differs from a plain write's");
    }

    /** The edit through the Autonomy tab's Save, opened as the station menus' Edit Locomotive opens it. */
    private static void throughTheDoor() throws Exception
    {
        assertTrue(support.SettingsWindow.open(ui, train, LocomotiveSettingsFrame.Tab.AUTONOMY, null), "the window did not open");
        assertNotNull(support.SettingsWindow.awaited(), "the window is not on screen");

        Components.onTheEventThread(() ->
        {
            support.SettingsWindow.part("autonomy.length", javax.swing.JSpinner.class).setValue(SET[0]);
            support.SettingsWindow.part("autonomy.reversible", javax.swing.JCheckBox.class).setSelected((Boolean) SET[1]);
            support.SettingsWindow.part("autonomy.departure", javax.swing.JComboBox.class).setSelectedIndex((Integer) SET[2] + 1);
            support.SettingsWindow.part("autonomy.arrival", javax.swing.JComboBox.class).setSelectedIndex((Integer) SET[3] + 1);

            return null;
        });

        support.SettingsWindow.press(support.SettingsWindow.save());

        assertTrue(Components.waitFor(() -> support.SettingsWindow.showing() == null, 8000), "Save did not close the window");
    }

    /** The train back at the starting values, and the setup saved, twice, so the next save writes only what changed. */
    private static void startFromTheSame() throws Exception
    {
        Components.onTheEventThread(() ->
        {
            train.setTrainLength(2);
            train.setReversible(false);
            train.setDepartureFunc(null);
            train.setArrivalFunc(null);
            train.setPreferredSpeed(40);

            return null;
        });

        savedSetup();
        savedSetup();
    }

    /** Saves the setup as a door does, and answers everything it keeps on disk, by file. */
    private static java.util.Map<String, String> savedSetup() throws Exception
    {
        Components.onTheEventThread(() -> session.save());

        return support.StandingTrain.setupFiles(fixture.sandbox.getFolder());
    }

    /** The train's length, reversible flag, functions and speed after a load of the setup that does not fold. */
    private static String afterAReload(String configuration) throws Exception
    {
        support.StandingTrain.reloadWithoutFolding(ui, configuration);

        return train.getTrainLength() + " " + train.isReversible() + " " + train.getDepartureFunc() + " "
            + train.getArrivalFunc() + " " + train.getPreferredSpeed();
    }

}
