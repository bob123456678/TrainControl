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
import org.traincontrol.gui.DiagramMonitorDriver;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
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
 * MUTATION: take the setup's facing out of `DiagramMonitorDriver.facingsOf` and the first claim fails; return null from
 * `AutonomySession.facingWhereNoCopySays` where nothing is recorded and the second does.
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

            copy = running.getPoint(names.get(0));

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

            TileOverlay seen = null;

            for (int tries = 0; tries < 50; tries++)
            {
                settle();

                seen = ui.getDiagramTileRegistry().overlayAt(PLATFORM);

                if (seen != null && seen.hasTrain()) break;

                Thread.sleep(100);
            }

            assertTrue(seen != null && seen.hasTrain() && seen.isParked(), "precondition: the diagram shows no train"
                + " standing on TunnelLeftPark: " + seen);

            return seen;
        }
        finally
        {
            if (copy != null) copy.setLocomotive(null);

            session.restoreSetup(asFound);

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();
        }
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
