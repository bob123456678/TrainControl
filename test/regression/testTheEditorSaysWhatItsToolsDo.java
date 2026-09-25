package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor says what its items do, and shows where its tools can be used (Adam, 2026-09-24: OB-293, FR-102).
 *
 * @author Adam
 */
public class testTheEditorSaysWhatItsToolsDo
{
    private File folder;
    private AutonomySession session;

    private static final TileKey WITH_A_TRAIN = new TileKey("main", 3, 1);
    private static final TileKey EMPTY = new TileKey("main", 1, 1);

    @BeforeMethod
    public void setUp() throws IOException
    {
        folder = Files.createTempDirectory("tc-editor-tools").toFile();
        session = new AutonomySession(folder);

        // 1,1 station - 2,1 - 3,1 station, a train standing at 3,1.
        LayoutDiagram page = new LayoutDiagram("main", 6, 3, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        session.open(Arrays.asList(page));
        session.initialize("Tools");
        session.setStation(EMPTY, true);
        session.setStation(WITH_A_TRAIN, true);
        session.placeLocomotive(WITH_A_TRAIN, "FR-102 train");
        session.rebuild();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
    {
        delete(folder);
    }

    /**
     * The exit and entry guard items on a station's menu say what the two guards do (OB-293).
     *
     * Adam, 2026-09-24: *"add brief tooltips on what entry guards and exit guards are to their items in the autonomy
     * right click menu"*.
     *
     * MUTATION: leave either tooltip out, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheGuardItemsSayWhatTheGuardsDo() throws Exception
    {
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            menu[0] = panel.buildTileMenu(EMPTY, session.getGraph().getTiles().get(EMPTY)));

        for (String key : new String[] {"autosetup.ui.menuPairSignal", "autosetup.ui.menuPairEntrySignal"})
        {
            javax.swing.JMenuItem item = find(menu[0], I18n.t(key));

            assertNotNull(item, "precondition: a station's menu has no " + I18n.t(key));

            String tip = item.getToolTipText();

            assertTrue(tip != null && !tip.replaceAll("<[^>]*>", "").trim().isEmpty(), I18n.t(key) + " has no tooltip -"
                + " Adam, OB-293: \"add brief tooltips on what entry guards and exit guards are\"");
        }
    }

    /**
     * With Why Not Moving armed and nothing drawn yet, the squares with trains are outlined (FR-102).
     *
     * Adam, 2026-09-24: *"when the button is pressed an nothing is drawn yet, highlight stations w/ trains on the editor
     * diagram so it's clear what the user can click on"*.
     *
     * MUTATION: outline nothing while the tool waits, or every station, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testWhyNotMovingOutlinesTheTrains() throws Exception
    {
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

        assertFalse(panel.annotationFor(WITH_A_TRAIN).isSelected(), "precondition: the train's square is outlined before"
            + " the tool is armed");

        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("whyButton");

        field.setAccessible(true);

        final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field.get(panel);

        javax.swing.SwingUtilities.invokeAndWait(() -> why.doClick());

        assertTrue(why.isSelected(), "precondition: Why Not Moving did not arm");

        assertTrue(panel.annotationFor(WITH_A_TRAIN).isSelected(), "Why Not Moving is waiting for a click and the square"
            + " with a train on it is not outlined - Adam, FR-102: \"highlight stations w/ trains on the editor diagram so"
            + " it's clear what the user can click on\"");

        assertFalse(panel.annotationFor(EMPTY).isSelected(), "a station with no train on it is outlined, and there is"
            + " nothing there to ask about");
    }

    /**
     * Where a railway is running, the outline follows the trains on it, not the setup's placements - and once a square
     * is clicked the outlines go (FR-102; TDU-C11, TDD-C9).
     *
     * The javadoc of `whyWaitsOn`: *"The train as the running railway has it where there is one - a run moves trains the
     * setup has not been told about - and as the setup places it otherwise."*  The claim above builds the panel with no
     * railway, so only the second arm was asked.
     *
     * MUTATION: read the setup's placements while a railway runs, or keep the outlines after the click, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testWhyNotMovingFollowsTheRunningRailway() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        org.traincontrol.marklin.MarklinControlStation model = null;

        try
        {
            // A SANDBOX FIRST, so the model does not load the machine's own railway (OB-111).
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, false);

            org.traincontrol.base.Locomotive moved = model.newMM2Locomotive("FR-102 moved", 2301);

            assertNotNull(moved, "could not create this test's train");

            model.parseAuto(session.buildConfiguration());

            final org.traincontrol.automation.Layout railway = model.getAutoLayout();

            assertNotNull(railway, "precondition: the fixture built no railway");

            // ON THE RAILWAY THE TRAIN STANDS AT 1,1, where the setup has none; the setup's train at 3,1 is not there.
            for (org.traincontrol.automation.Point point : railway.getPoints()) point.setLocomotive(null);

            org.traincontrol.automation.Point atEmpty = null;

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (EMPTY.equals(session.getStationIndex().squareOf(point))) atEmpty = point;
            }

            assertNotNull(atEmpty, "precondition: the railway has no Point at 1,1");

            atEmpty.setLocomotive(moved);

            final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, "main", () -> { });

            panel.setRunningLayoutSource(() -> railway);
            panel.setLayoutSource(() -> railway);

            java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("whyButton");

            field.setAccessible(true);

            final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field.get(panel);

            javax.swing.SwingUtilities.invokeAndWait(() -> why.doClick());

            assertTrue(panel.annotationFor(EMPTY).isSelected(), "a railway is running with a train at 1,1 and Why Not"
                + " Moving does not outline it - it read the setup, which has none there");

            assertFalse(panel.annotationFor(WITH_A_TRAIN).isSelected(), "a railway is running with no train at 3,1 and"
                + " Why Not Moving outlines it - it read the setup's placement, not the railway");

            // AND ONCE A SQUARE IS CLICKED, THE OUTLINES GO: a second train on the railway at 3,1 is outlined while the
            // tool waits, and not once 1,1 has been asked about.
            org.traincontrol.base.Locomotive second = model.newMM2Locomotive("FR-102 second", 2302);

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (WITH_A_TRAIN.equals(session.getStationIndex().squareOf(point))) point.setLocomotive(second);
            }

            // Asked of the waiting outline itself: once a square is asked about, the answer draws outlines of its own.
            java.lang.reflect.Method waitsOn = AutonomyEditorPanel.class.getDeclaredMethod("whyWaitsOn", TileKey.class);

            waitsOn.setAccessible(true);

            assertTrue((Boolean) waitsOn.invoke(panel, WITH_A_TRAIN), "precondition: the second train at 3,1 is not"
                + " outlined as somewhere to click while Why Not Moving waits");

            javax.swing.SwingUtilities.invokeAndWait(() ->
                panel.tileClicked(EMPTY, session.getGraph().getTiles().get(EMPTY), false));

            assertFalse((Boolean) waitsOn.invoke(panel, WITH_A_TRAIN), "Why Not Moving was asked about 1,1 and still"
                + " outlines the train at 3,1 as somewhere to click");
        }
        finally
        {
            try
            {
                if (model != null)
                {
                    model.deleteLoc("FR-102 moved");
                    model.deleteLoc("FR-102 second");
                }
            }
            finally
            {
                if (sandbox != null) sandbox.close();
            }
        }
    }

    /** The menu item with this text, anywhere in the menu. */
    private static javax.swing.JMenuItem find(java.awt.Container container, String text)
    {
        for (java.awt.Component c : container instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) container).getMenuComponents() : container.getComponents())
        {
            if (c instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) c).getText()))
            {
                return (javax.swing.JMenuItem) c;
            }

            if (c instanceof java.awt.Container)
            {
                javax.swing.JMenuItem inner = find((java.awt.Container) c, text);

                if (inner != null) return inner;
            }
        }

        return null;
    }

    private static void delete(File file)
    {
        if (file == null) return;

        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children) delete(child);
        }

        file.delete();
    }
}
