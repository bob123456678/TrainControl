package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * Adam's rule for FR-048, "make sure behavior is unchanged" for autonomy configs and file reads: the station dialog
 * (`GraphLocAssign`) retires, and a train's length, reversible flag and functions set through its successor leave the
 * saved setup, and the train after a reload, as they are when the same values are written straight onto the
 * locomotive.  On the frozen copy of Adam's railway, with a train standing on BottomMainB.
 *
 * MUTATION: have the door also write the train's length into the setup's record of it, and this fails.
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

        final TileKey square = squareNamed("BottomMainB");

        SwingUtilities.invokeAndWait(() ->
        {
            Layout railway = model.getAutoLayout();

            railway.getPoints().stream().filter(p -> p.getCurrentLocomotive() != null)
                .forEach(p -> railway.moveLocomotive(null, p.getName(), true));

            session.facingsFor(square).keySet().stream().map(railway::getPoint).filter(p -> p != null).findFirst()
                .ifPresent(p -> railway.moveLocomotive(TRAIN, p.getName(), false));

            session.placeLocomotive(square, TRAIN);

            // Its heading and tail, as a placement door records them: the dialog wrote them where they were missing
            session.setFacing(square, org.traincontrol.automationui.TilePorts.Side.E);
            session.setArrivedFrom(square, "W");
        });

        assertNotNull(model.getAutoLayout().getLocomotiveLocation(train), "precondition: the train could not be stood on BottomMainB");

        // A speed of its own: the dialog wrote the layout's default over a speed of 0
        train.setPreferredSpeed(40);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

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

    /** The edit through the station dialog's OK, as both station menus commit it. */
    private static void throughTheDoor() throws Exception
    {
        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        Components.onTheEventThread(() ->
        {
            org.traincontrol.automation.Point at = model.getAutoLayout().getLocomotiveLocation(train);
            org.traincontrol.gui.GraphLocAssign edit = new org.traincontrol.gui.GraphLocAssign(ui, at, session,
                model.getAutoLayout());

            field(edit, "trainLength", javax.swing.JComboBox.class).setSelectedItem(String.valueOf(SET[0]));
            field(edit, "reversible", javax.swing.JCheckBox.class).setSelected((Boolean) SET[1]);
            field(edit, "departureFunc", javax.swing.JComboBox.class).setSelectedItem(String.valueOf(SET[2]));
            field(edit, "arrivalFunc", javax.swing.JComboBox.class).setSelectedItem(String.valueOf(SET[3]));

            org.traincontrol.gui.GraphLocAssign.commitAndRecord(edit);

            return null;
        });

        Components.settle();
    }

    private static <T> T field(Object on, String name, Class<T> type) throws Exception
    {
        java.lang.reflect.Field f = on.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return type.cast(f.get(on));
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

        java.util.Map<String, String> out = new java.util.TreeMap<>();

        java.nio.file.Path root = new java.io.File(fixture.sandbox.getFolder(), "config" + java.io.File.separator + "autonomy").toPath();

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root))
        {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) walk.filter(java.nio.file.Files::isRegularFile)::iterator)
            {
                out.put(root.relativize(file).toString(), new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        return out;
    }

    /** The train's length, reversible flag, functions and speed after a load of the setup that does not fold. */
    private static String afterAReload(String configuration) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(configuration, false, false));

            Components.settle();
            Components.settle();
        }

        return train.getTrainLength() + " " + train.isReversible() + " " + train.getDepartureFunc() + " "
            + train.getArrivalFunc() + " " + train.getPreferredSpeed();
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
