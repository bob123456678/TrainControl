package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
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

        SwingUtilities.invokeAndWait(() ->
        {
            Layout railway = model.getAutoLayout();

            railway.getPoints().stream().filter(p -> p.getCurrentLocomotive() != null)
                .forEach(p -> railway.moveLocomotive(null, p.getName(), true));

            // On the first copy of the square that will take it
            session.facingsFor(square).keySet().stream().map(railway::getPoint).filter(p -> p != null).findFirst()
                .ifPresent(p -> railway.moveLocomotive(TRAIN, p.getName(), false));

            session.placeLocomotive(square, TRAIN);
        });

        assertNotNull(model.getAutoLayout().getLocomotiveLocation(train), "precondition: the train could not be stood on BottomMainB");
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
     * Alt+U's Save Current Speed and the station dialog's write (a plain `setPreferredSpeed` on the locomotive, which
     * is all `GraphLocAssign.commitChanges` does with the speed) survive the same load, so the window is no different.
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
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new AssertionError("this railway has no " + name);
    }
}
