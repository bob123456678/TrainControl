package ui;

import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.DiagramMonitorDriver;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.StationCaption;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The locomotive on a square no track leads out of faces a way its track runs, not east (OB-348).
 *
 * Adam, 2026-10-14: *"on tunnelleftpark, if there is no way out (both arrows at 8,7 red), the icon is rotated to the
 * east, even though the only allowed directions are n/s."*
 *
 * The icon of a standing train turns to the facing of the copy it stands on, or to a single copy's only way out
 * (`DiagramMonitorDriver.facingsOf`).  A square no track leads out of is emitted whole with neither, so the icon was
 * given no facing and drawn as its picture is drawn - east - on track that runs north and south.  The caption beside it
 * fell back to the setup's facing and showed south.
 *
 * At TunnelLeftPark on the frozen `live-snapshot`, with both arrows of 8,7 below it red, through the real window's
 * diagram monitor.
 *
 * And on the validator's findings (V348-C1, V348-C2), Adam, 2026-10-14: *"The icon can guess in that case, but it must be
 * N/S or a N/S track, or E/W on E/W, or angled on a curved track."*  The facing for such a square is asked of the setup
 * whenever the captions are, so a facing a door writes reaches the icon without a rebuild; and a recorded facing the track
 * does not run is shown by none of the icon, the caption and the Is Facing menu.
 *
 * MUTATION: take the setup's facing out of `DiagramMonitorDriver.recordedFacingsChanged` and the first claim fails; return
 * null from `AutonomySession.facingWhereNoCopySays` where nothing is recorded and the second does; ask the facings only at
 * a bind and the third does; let the caption read the record as it stands, or the menu add it, and the fourth does.
 *
 * @author Adam
 */
public class testATrainWithNoWayOutFacesAlongItsTrack
{
    private static final String PAGE = "1 - Main";

    /** TunnelLeftPark's square, and the square below it whose arrows are both made red. */
    private static final TileKey PLATFORM = new TileKey(PAGE, 8, 6);
    private static final TileKey BELOW = new TileKey(PAGE, 8, 7);

    private static final String OUR_TRAIN = "OB-348 icon probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static MarklinLocomotive train;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the diagram monitor belongs to a window, and a window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration on the snapshot");
        }

        if (ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            settle();
        }

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: no configuration is loaded");

        train = model.newMM2Locomotive(OUR_TRAIN, 2398);

        assertNotNull(train, "could not create this class's train");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null)
            {
                model.deleteLoc(OUR_TRAIN);

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * With the setup's facing south, as Adam's is, the locomotive faces south.
     *
     * @throws Exception from the window
     */
    @Test
    public void testItFacesTheWayTheSetupRecords() throws Exception
    {
        TileOverlay shown = standItThereAndLook(Side.S);

        assertEquals(shown.getFacing(), Side.S, "the locomotive on TunnelLeftPark, which no track leads out of, does not"
            + " face the way the setup records and the caption shows - Adam, OB-348: \"the icon is rotated to the east,"
            + " even though the only allowed directions are n/s\"");
    }

    /**
     * With nothing recorded, it still faces a way its track runs.
     *
     * @throws Exception from the window
     */
    @Test
    public void testWithNothingRecordedItFacesAlongTheTrack() throws Exception
    {
        TileOverlay shown = standItThereAndLook(null);

        assertTrue(shown.getFacing() == Side.N || shown.getFacing() == Side.S, "the locomotive on TunnelLeftPark, with"
            + " no facing recorded and no track leading out, faces " + shown.getFacing() + " - drawn east, across track"
            + " that runs north and south (OB-348)");
    }

    /**
     * A facing written after the diagram was bound reaches the icon at the refresh every door ends with, with no rebuild
     * (V348-C2).
     *
     * @throws Exception from the window
     */
    @Test
    public void testAFacingWrittenLaterReachesTheIcon() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        Point copy = null;

        try
        {
            copy = standItThere(Side.S);

            assertEquals(look(Side.S).getFacing(), Side.S, "precondition: the icon does not face the facing recorded");

            // AS A DOOR LEAVES IT: the record written, then the refresh every door ends with - and no rebuild
            session.setFacing(PLATFORM, null);

            SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

            assertEquals(look(Side.N).getFacing(), Side.N, "the locomotive on TunnelLeftPark still faces the facing recorded"
                + " before a door cleared it, until some later rebuild turns it round (V348-C2)");
        }
        finally
        {
            putBack(asFound, copy);
        }
    }

    /**
     * A recorded facing the track does not run is shown by none of the three: the icon lies along the track, the caption
     * has no east arrow, and the Is Facing menu is greyed (V348-C1).
     *
     * @throws Exception from the window
     */
    @Test
    public void testAFacingTheTrackDoesNotRunIsShownNowhere() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();
        final LayoutEditor[] editor = new LayoutEditor[1];

        Point copy = null;

        try
        {
            copy = standItThere(Side.E);

            TileOverlay shown = look(Side.N);

            assertTrue(shown.getFacing() == Side.N || shown.getFacing() == Side.S, "the locomotive on TunnelLeftPark, with"
                + " east recorded on track that runs north and south, faces " + shown.getFacing() + " (OB-348)");

            SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

            settle();

            boolean captioned = false;

            for (javax.swing.JLabel caption : ui.getLayoutStations(PLATFORM))
            {
                final String[] text = new String[1];

                SwingUtilities.invokeAndWait(() -> text[0] = caption.getText());

                if (text[0] == null || !text[0].contains(OUR_TRAIN.substring(0, 3))) continue;

                captioned = true;

                assertFalse(text[0].contains(StationCaption.ARROW_E), "the caption on TunnelLeftPark points east, on track"
                    + " that runs north and south, while the icon lies along it (V348-C1): " + text[0]);
            }

            assertTrue(captioned, "precondition: no caption on TunnelLeftPark names the train");

            final org.traincontrol.base.LayoutDiagram page = model.getLayout(PLATFORM.getPage());

            assertNotNull(page, "precondition: no page " + PLATFORM.getPage());

            SwingUtilities.invokeAndWait(() ->
            {
                editor[0] = new LayoutEditor(page, 30, ui, 0);
                editor[0].render();
                editor[0].setAutonomyMode(session);
            });

            settle();

            final AutonomyEditorPanel panel = editor[0].getAutonomyPanel();
            final javax.swing.JMenu[] menu = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> menu[0] = panel.buildFacingMenu(PLATFORM));

            assertNotNull(menu[0], "precondition: no Is Facing menu for the train on TunnelLeftPark");

            assertFalse(menu[0].isEnabled(), "the Is Facing menu on TunnelLeftPark, which no track leads out of, is open"
                + " with east ticked, on track that runs north and south, while the icon lies along it (V348-C1)");
        }
        finally
        {
            if (editor[0] != null)
            {
                final LayoutEditor closing = editor[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            putBack(asFound, copy);
        }
    }

    /**
     * On a square that does have ways out, where the railway does not hold the setup's train, a recorded facing the track
     * does not run is ticked in the Is Facing menu no more than the caption shows it (V348B-C3), and once the railway is
     * built from that setup - which cannot stand the train, so the diagram draws it on no Point - its icon lies along the
     * track (V348C-C1).
     *
     * MUTATION: let the menu read the record as it stands again (OB-177's tick) and this fails; draw a train on no Point
     * facing its record as it stands, and this fails at the icon.
     *
     * @throws Exception from the window
     */
    @Test
    public void testOffTheTrackTheMenuAndTheCaptionAgreeWhereThereIsAWayOut() throws Exception
    {
        final TileKey tunnel = new TileKey(PAGE, 7, 7);
        final org.json.JSONObject asFound = session.snapshotSetup();
        final LayoutEditor[] editor = new LayoutEditor[1];

        try
        {
            assertFalse(session.facingChoices(tunnel).isEmpty(), "precondition: Tunnel has no way out on this snapshot");

            assertFalse(session.getRoutes(tunnel).values().iterator().next().touches(Side.E), "precondition: Tunnel's"
                + " track runs east");

            // NO TRAIN THERE ON THE RAILWAY, and this class's in the setup, recorded facing east
            Layout running = model.getAutoLayout();

            for (String name : session.getStationIndex().pointNamesAt(tunnel))
            {
                Point copy = running.getPoint(name);

                if (copy != null) copy.setLocomotive(null);
            }

            session.placeLocomotive(tunnel, OUR_TRAIN);
            session.setFacing(tunnel, Side.E);

            // THE RAILWAY BUILT FROM THAT SETUP, which cannot stand the train: no copy of Tunnel faces east
            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();

            assertTrue(session.trainsOnNoPoint().containsKey(tunnel), "precondition: the railway stands the train at Tunnel"
                + " on a Point, so the diagram does not draw it as a train on no Point");

            final DiagramMonitorDriver driver = ui.getDiagramMonitorDriver();

            SwingUtilities.invokeAndWait(() ->
            {
                driver.bind(session);
                driver.setEnabled(true);
                driver.start();
            });

            TileOverlay shown = null;

            for (int tries = 0; tries < 50 && (shown == null || !shown.hasTrain()); tries++)
            {
                settle();

                shown = ui.getDiagramTileRegistry().overlayAt(tunnel);

                if (shown == null || !shown.hasTrain()) Thread.sleep(100);
            }

            assertTrue(shown != null && shown.hasTrain(), "precondition: the diagram shows no train at Tunnel: " + shown);

            assertTrue(shown.getFacing() == Side.N || shown.getFacing() == Side.S, "the locomotive at Tunnel, which the"
                + " railway cannot stand with east recorded, faces " + shown.getFacing() + " on track that runs north and"
                + " south - Adam: \"it must be N/S or a N/S track\" (V348C-C1)");

            final org.traincontrol.base.LayoutDiagram page = model.getLayout(PAGE);

            SwingUtilities.invokeAndWait(() ->
            {
                editor[0] = new LayoutEditor(page, 30, ui, 0);
                editor[0].render();
                editor[0].setAutonomyMode(session);
            });

            settle();

            final String[] caption = new String[1];
            final javax.swing.JMenu[] menu = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() ->
            {
                caption[0] = ui.autonomyCaptionTextAt(tunnel);
                menu[0] = editor[0].getAutonomyPanel().buildFacingMenu(tunnel);
            });

            assertNotNull(menu[0], "precondition: no Is Facing menu for the train placed at Tunnel");

            assertTrue(caption[0] == null || !caption[0].contains(StationCaption.ARROW_E), "precondition: the caption at"
                + " Tunnel points east, on track that runs north and south: " + caption[0]);

            String east = I18n.t("autosetup.ui.facingE");

            for (java.awt.Component item : menu[0].getMenuComponents())
            {
                if (!(item instanceof javax.swing.JRadioButtonMenuItem)) continue;

                javax.swing.JRadioButtonMenuItem radio = (javax.swing.JRadioButtonMenuItem) item;

                assertFalse(radio.isSelected() && east.equals(radio.getText()), "the Is Facing menu at Tunnel ticks east,"
                    + " on track that runs north and south, where the caption shows no arrow (V348B-C3)");
            }
        }
        finally
        {
            if (editor[0] != null)
            {
                final LayoutEditor closing = editor[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            session.restoreSetup(asFound);

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();
        }
    }

    /**
     * Makes both arrows of 8,7 red, records the facing given on TunnelLeftPark, stands this class's train there and
     * returns what the diagram shows on the square - the setup put back afterwards.
     *
     * @param recorded the facing to record, or null for none
     * @return the square's overlay, showing the train
     * @throws Exception from the window
     */
    private static TileOverlay standItThereAndLook(Side recorded) throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        Point copy = null;

        try
        {
            copy = standItThere(recorded);

            return look(null);
        }
        finally
        {
            putBack(asFound, copy);
        }
    }

    /**
     * Makes both arrows of 8,7 red, records the facing given on TunnelLeftPark, stands this class's train there, and binds
     * the diagram as a rebuild does.
     *
     * @param recorded the facing to record, or null for none
     * @return the copy the train stands on
     * @throws Exception from the window
     */
    private static Point standItThere(Side recorded) throws Exception
    {
        if (!session.getRoutes(PLATFORM).values().iterator().next().touches(Side.N)
            || session.getRoutes(PLATFORM).values().iterator().next().touches(Side.E))
        {
            throw new SkipException("TunnelLeftPark does not run north to south on this snapshot");
        }

        // BOTH ARROWS AT 8,7 RED
        for (RouteId road : session.getRoutes(BELOW).keySet()) session.setDirection(BELOW, road, Direction.NONE);

        session.setFacing(PLATFORM, recorded);

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        settle();

        assertTrue(session.facingChoices(PLATFORM).isEmpty(), "precondition: TunnelLeftPark still has a way out: "
            + session.facingChoices(PLATFORM));

        assertEquals(session.getFacing(PLATFORM), recorded, "precondition: the setup does not record the facing given");

        Layout running = model.getAutoLayout();

        List<String> names = session.getStationIndex().pointNamesAt(PLATFORM);

        assertEquals(names.size(), 1, "precondition: TunnelLeftPark is not one Point: " + names);

        Point copy = running.getPoint(names.get(0));

        assertNotNull(copy, "precondition: the railway has no TunnelLeftPark");

        for (Point point : running.getPoints())
        {
            if (point.getCurrentLocomotive() != null && point.isSamePlaceAs(copy)) point.setLocomotive(null);
        }

        copy.setLocomotive(train);

        final DiagramMonitorDriver driver = ui.getDiagramMonitorDriver();

        SwingUtilities.invokeAndWait(() ->
        {
            driver.bind(session);
            driver.setEnabled(true);
            driver.start();
        });

        return copy;
    }

    /**
     * What the diagram shows on TunnelLeftPark, once it shows the train - facing the way given, where one is - or what it
     * shows after five seconds.
     *
     * @param facing the facing to wait for, or null for any
     * @return the square's overlay, showing the train
     * @throws Exception from the window
     */
    private static TileOverlay look(Side facing) throws Exception
    {
        TileOverlay seen = null;

        for (int tries = 0; tries < 50; tries++)
        {
            settle();

            seen = ui.getDiagramTileRegistry().overlayAt(PLATFORM);

            if (seen != null && seen.hasTrain() && (facing == null || seen.getFacing() == facing)) break;

            Thread.sleep(100);
        }

        assertTrue(seen != null && seen.hasTrain() && seen.isParked(), "precondition: the diagram shows no train"
            + " standing on TunnelLeftPark: " + seen);

        return seen;
    }

    /**
     * Takes the train off and puts the setup back as it was found.
     *
     * @param asFound the setup as found
     * @param copy the copy the train stood on, or null
     * @throws Exception from the window
     */
    private static void putBack(org.json.JSONObject asFound, Point copy) throws Exception
    {
        if (copy != null) copy.setLocomotive(null);

        session.restoreSetup(asFound);

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        settle();
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
