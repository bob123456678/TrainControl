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
import org.traincontrol.gui.autonomy.DiagramMonitorDriver;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.widgets.StationCaption;
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
 * And on the validator's findings, Adam, 2026-10-14: *"The icon can guess in that case, but it must be
 * N/S or a N/S track, or E/W on E/W, or angled on a curved track."*  The facing for such a square is asked of the setup
 * whenever the captions are, so a facing a door writes reaches the icon without a rebuild; and a recorded facing the track
 * does not run is shown by none of the icon, the caption and the Is Facing menu.
 *
 * And a train can be put there (OB-350), Adam: *"the track viewer doesn't allow autonomy locomotive placements
 * on stations that have no way in (option greyed out), but the autonomy editor does.  they should be consistent, and
 * allowed."*  The track diagram's Place and Control+V stand the train where the editor's Place does, recording the same
 * facing.
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
        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no autonomy configuration on the snapshot");
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
     * A facing written after the diagram was bound reaches the icon at the refresh every door ends with, with no rebuild.
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
     * has no east arrow, and the Is Facing menu is grayed.
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
     * does not run is ticked in the Is Facing menu no more than the caption shows it, and once the railway is
     * built from that setup - which cannot stand the train, so the diagram draws it on no Point - its icon lies along the
     * track.
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
     * The same on every square of the snapshot that offers one facing, for every way its track does not run.
     */
    @Test
    public void testEverySquareWithOneWayOutGuessesIt()
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        int asked = 0;

        try
        {
            for (TileKey square : new java.util.ArrayList<>(session.getStationIndex().squares()))
            {
                List<Side> offered = session.facingChoices(square);

                if (offered.size() != 1) continue;

                java.util.Set<Side> track = new java.util.HashSet<>();

                for (org.traincontrol.automationui.TilePorts.Route route : session.getRoutes(square).values())
                {
                    track.add(route.getA());
                    track.add(route.getB());
                }

                for (Side off : Side.values())
                {
                    if (track.contains(off)) continue;

                    session.setFacing(square, off);

                    asked++;

                    assertEquals(session.facingWhereNoCopySays(square), offered.get(0), "a train recorded facing " + off
                        + " at " + square + ", whose track does not run that way and whose one way out is "
                        + offered.get(0) + ", is drawn facing elsewhere (V348D-C1)");
                }
            }
        }
        finally
        {
            session.restoreSetup(asFound);
        }

        assertTrue(asked > 0, "precondition: no square of the snapshot offers one facing");
    }

    /**
     * On a square with one way out, a train the railway cannot stand - its recorded facing off the track - faces that way
     * out, which the Is Facing menu ticks.
     *
     * MUTATION: guess the track's first way before the facing the square offers, and this fails at TunnelRightPark, whose
     * one way out is south on track that runs from the north.
     *
     * @throws Exception from the window
     */
    @Test
    public void testOffTheTrackWithOneWayOutItFacesThatWay() throws Exception
    {
        final TileKey square = new TileKey(PAGE, 10, 6);
        final org.json.JSONObject asFound = session.snapshotSetup();
        final LayoutEditor[] editor = new LayoutEditor[1];

        try
        {
            List<Side> offered = session.facingChoices(square);

            assertEquals(offered.size(), 1, "TunnelRightPark does not offer one facing here: " + offered);

            assertFalse(session.getRoutes(square).values().iterator().next().touches(Side.E), "precondition:"
                + " TunnelRightPark's track runs east");

            Layout running = model.getAutoLayout();

            for (String name : session.getStationIndex().pointNamesAt(square))
            {
                Point copy = running.getPoint(name);

                if (copy != null) copy.setLocomotive(null);
            }

            session.placeLocomotive(square, OUR_TRAIN);
            session.setFacing(square, Side.E);

            SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

            settle();

            assertTrue(session.trainsOnNoPoint().containsKey(square), "precondition: the railway stands the train at"
                + " TunnelRightPark on a Point");

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

                shown = ui.getDiagramTileRegistry().overlayAt(square);

                if (shown == null || !shown.hasTrain()) Thread.sleep(100);
            }

            assertTrue(shown != null && shown.hasTrain(), "precondition: no train shown at TunnelRightPark: " + shown);

            final org.traincontrol.base.LayoutDiagram page = model.getLayout(PAGE);

            SwingUtilities.invokeAndWait(() ->
            {
                editor[0] = new LayoutEditor(page, 30, ui, 0);
                editor[0].render();
                editor[0].setAutonomyMode(session);
            });

            settle();

            final javax.swing.JMenu[] menu = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> menu[0] = editor[0].getAutonomyPanel().buildFacingMenu(square));

            String ticked = null;

            for (java.awt.Component item : menu[0] == null ? new java.awt.Component[0] : menu[0].getMenuComponents())
            {
                if (item instanceof javax.swing.JRadioButtonMenuItem && ((javax.swing.JRadioButtonMenuItem) item).isSelected())
                {
                    ticked = ((javax.swing.JRadioButtonMenuItem) item).getText();
                }
            }

            assertEquals(ticked, I18n.t("autosetup.ui.facing" + offered.get(0).name()), "precondition: the Is Facing"
                + " menu does not tick TunnelRightPark's one way out");

            assertEquals(shown.getFacing(), offered.get(0), "the locomotive at TunnelRightPark, which the railway cannot"
                + " stand with east recorded, faces away from the one way out the Is Facing menu ticks (V348D-C1)");
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
     * On TunnelLeftPark with no way out, the track diagram's Place is offered and stands the train where the editor's
     * Place does, with the same facing recorded (OB-350).
     *
     * Adam, OB-350: *"the track viewer doesn't allow autonomy locomotive placements on stations that have no way in
     * (option greyed out), but the autonomy editor does.  they should be consistent, and allowed."*
     *
     * MUTATION: gray the diagram's Place where no copy can leave again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsPlaceStandsItWhereTheEditorsDoes() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            final Object[] byTheEditor = placedByTheEditor();

            closeItOff();

            placeByTheDiagram();

            assertEquals(standingOn(), byTheEditor[0], "the track diagram's Place stood the train on another Point of"
                + " TunnelLeftPark, which no track leads out of, than the editor's Place does (OB-350)");

            assertEquals(session.getFacing(PLATFORM), byTheEditor[1], "the track diagram's Place recorded another facing"
                + " on TunnelLeftPark than the editor's Place does (OB-350)");
        }
        finally
        {
            liftIt();

            putBack(asFound, null);
        }
    }

    /**
     * And Control+V there stands it where the editor's Place does, with the same facing recorded (OB-350).
     *
     * MUTATION: refuse the paste where no copy can leave again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testThePasteStandsItWhereTheEditorsPlaceDoes() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            final Object[] byTheEditor = placedByTheEditor();

            closeItOff();

            makeItTheActiveTrain();

            final java.lang.reflect.Method door = TrainControlUI.class.getDeclaredMethod("locomotiveGestureOnDiagram",
                int.class, boolean.class);

            door.setAccessible(true);

            final Object[] handled = new Object[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui.setHoveredDiagramTile(PLATFORM.getPage(), PLATFORM.getX(), PLATFORM.getY());

                    handled[0] = door.invoke(ui, java.awt.event.KeyEvent.VK_V, true);
                }
                catch (ReflectiveOperationException refused)
                {
                    handled[0] = refused;
                }
            });

            settle();

            assertEquals(handled[0], Boolean.TRUE, "Control+V over TunnelLeftPark was not taken");

            assertEquals(standingOn(), byTheEditor[0], "Control+V stood the train on another Point of TunnelLeftPark,"
                + " which no track leads out of, than the editor's Place does (OB-350)");

            assertEquals(session.getFacing(PLATFORM), byTheEditor[1], "Control+V recorded another facing on"
                + " TunnelLeftPark than the editor's Place does (OB-350)");
        }
        finally
        {
            liftIt();

            putBack(asFound, null);
        }
    }

    /**
     * And the track diagram's Place there is saved: read back from disk, the setup stands the train on TunnelLeftPark,
     * where no copy gives a facing (OB-373).  Unsaved, the train is gone at the next configuration load.
     *
     * MUTATION: save only where a copy gives a facing (put `facing != null &&` back in `placeFacing`), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheDiagramsPlaceThereIsSaved() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        try
        {
            closeItOff();

            // ON DISK WITHOUT IT, so that only the click's own save can put it there: the paste's claim saves it there
            session.save();

            assertNotEquals(placedOnDisk(), OUR_TRAIN, "precondition: the setup on disk already stands the train on"
                + " TunnelLeftPark");

            placeByTheDiagram();

            assertEquals(session.getLocomotiveNameAt(PLATFORM), OUR_TRAIN, "precondition: the track diagram's Place did"
                + " not put the train on TunnelLeftPark in the setup");

            assertEquals(placedOnDisk(), OUR_TRAIN, "the track diagram's Place on TunnelLeftPark, where no copy gives a"
                + " facing, was not saved: the setup read back from disk does not stand the train there, so it is gone at"
                + " the next configuration load (OB-373)");
        }
        finally
        {
            liftIt();

            putBack(asFound, null);

            session.save();
        }
    }

    /**
     * And a save that fails there is logged with debug mode off, as the paste and the locomotive dialog log theirs
     * (OB-373).  A setup file made read-only is the failure.  Read from the station's logger, which every log line
     * passes: this class's window is not the station's view.
     *
     * MUTATION: log the failure in `placeFacing` only in debug mode again, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAFailedSaveOfTheDiagramsPlaceIsLogged() throws Exception
    {
        final org.json.JSONObject asFound = session.snapshotSetup();

        final boolean wasDebug = model.isDebug();

        final java.io.File setup = new java.io.File(sandbox.getFolder(), "config" + java.io.File.separator + "autonomy"
            + java.io.File.separator + "setup.json");

        final List<String> logged = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        final java.util.logging.Handler tap = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                if (record != null && record.getMessage() != null) logged.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        final java.util.logging.Logger log = java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());

        try
        {
            closeItOff();

            assertTrue(setup.isFile(), "precondition: no setup file at " + setup);

            model.debug(false);

            // A FILE THAT CANNOT BE WRITTEN
            assertTrue(setup.setReadOnly(), "precondition: the setup file would not be made read-only");

            log.addHandler(tap);

            placeByTheDiagram();

            settle();

            String said;

            synchronized (logged) { said = String.join("\n", logged); }

            assertTrue(said.contains(setup.getName()), "the track diagram's Place on TunnelLeftPark could not save the"
                + " setup, and with debug mode off the log does not say so, while the paste and the locomotive dialog"
                + " log a failed save (OB-373); the log reads: " + said);
        }
        finally
        {
            log.removeHandler(tap);

            setup.setWritable(true);

            model.debug(wasDebug);

            liftIt();

            putBack(asFound, null);
        }
    }

    /** The train the setup on disk stands on TunnelLeftPark, read from the file as a configuration load reads it. */
    private static String placedOnDisk() throws Exception
    {
        AutonomySession read = new AutonomySession(sandbox.getFolder());

        read.getStore().load();

        assertNotNull(read.getStore().getActiveConfiguration(), "precondition: the setup on disk has no configuration");

        return read.getLocomotiveNameAt(PLATFORM);
    }

    /**
     * Closes TunnelLeftPark off, then puts this class's train there by the editor's Place and builds the railway from the
     * setup, as closing the editor does.
     *
     * @return the Point the train stands on, and the facing recorded
     * @throws Exception from the window
     */
    private static Object[] placedByTheEditor() throws Exception
    {
        closeItOff();

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, PAGE, () -> { });

        panel.setRunningLayoutSource(() -> model.getAutoLayout());
        panel.setLayoutSource(() -> model.getAutoLayout());
        panel.setLocomotiveNames(() -> model.getLocList());

        final java.lang.reflect.Method place = AutonomyEditorPanel.class.getDeclaredMethod("placeLocomotive",
            TileKey.class);

        place.setAccessible(true);

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                place.invoke(panel, PLATFORM);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        final javax.swing.JDialog asked = awaitDialogStarting(I18n.t("autosetup.ui.menuAddToAutonomy"));

        final javax.swing.JList<?> list = find(asked.getContentPane(), javax.swing.JList.class);
        final javax.swing.JOptionPane pane = find(asked.getContentPane(), javax.swing.JOptionPane.class);

        assertTrue(list != null && pane != null, "precondition: the editor's Place asks no list of locomotives");

        SwingUtilities.invokeAndWait(() ->
        {
            list.setSelectedValue(OUR_TRAIN, true);

            assertEquals(list.getSelectedValue(), OUR_TRAIN, "precondition: the editor's Place does not offer the train");

            pane.setValue(Integer.valueOf(javax.swing.JOptionPane.OK_OPTION));
        });

        for (long end = System.currentTimeMillis() + 10000; !OUR_TRAIN.equals(session.getLocomotiveNameAt(PLATFORM))
            && System.currentTimeMillis() < end; ) Thread.sleep(50);

        assertEquals(session.getLocomotiveNameAt(PLATFORM), OUR_TRAIN, "precondition: the editor's Place did not put the"
            + " train on TunnelLeftPark");

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        settle();

        String on = standingOn();

        assertNotNull(on, "precondition: the railway built from the editor's Place stands the train on no Point");

        return new Object[] {on, session.getFacing(PLATFORM)};
    }

    /**
     * Makes both arrows of 8,7 red, records TunnelLeftPark facing south, takes this class's train off the railway and the
     * setup, and builds the railway from the setup - so a door that records no facing is told apart from one that leaves
     * the record standing.
     *
     * @throws Exception from the window
     */
    private static void closeItOff() throws Exception
    {
        if (!session.getRoutes(PLATFORM).values().iterator().next().touches(Side.N))
        {
            throw new AssertionError("TunnelLeftPark does not run north to south on this snapshot");
        }

        liftIt();

        // NOTHING STANDING THERE, in the setup or on the railway: the snapshot has a train on it
        SwingUtilities.invokeAndWait(() ->
        {
            for (String name : session.getStationIndex().pointNamesAt(PLATFORM))
            {
                Point copy = model.getAutoLayout().getPoint(name);

                if (copy != null) copy.setLocomotive(null);
            }
        });

        session.placeLocomotive(PLATFORM, null);

        for (RouteId road : session.getRoutes(BELOW).keySet()) session.setDirection(BELOW, road, Direction.NONE);

        session.setFacing(PLATFORM, Side.S);

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        settle();

        assertTrue(session.facingChoices(PLATFORM).isEmpty(), "precondition: TunnelLeftPark still has a way out: "
            + session.facingChoices(PLATFORM));

        assertNull(session.getLocomotiveNameAt(PLATFORM), "precondition: the setup still places a train on"
            + " TunnelLeftPark");

        assertNull(standingOn(), "precondition: the train still stands on TunnelLeftPark");

        assertEquals(session.getFacing(PLATFORM), Side.S, "precondition: the setup does not record TunnelLeftPark facing"
            + " south");

        // EVERY QUESTION A PLACEMENT MAY ASK, answered so nothing waits for a person
        List<Side> sides = session.unbarredArrivalSides(PLATFORM);

        org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(sides == null || sides.isEmpty() ? "N" : sides.get(0).name());
        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);
    }

    /** Right-clicks TunnelLeftPark on the track diagram and chooses Place, with this class's train the active one. */
    private static void placeByTheDiagram() throws Exception
    {
        makeItTheActiveTrain();

        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final java.lang.reflect.Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class,
            Point.class);

        gather.setAccessible(true);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
            TileKey.class, TileKey.class, Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

        make.setAccessible(true);

        final Point[] standing = new Point[1];

        SwingUtilities.invokeAndWait(() -> standing[0] = ui.getAutonomyPointForTile(PLATFORM));

        final Object options = gather.invoke(null, ui, standing[0]);

        final javax.swing.JMenuItem[] place = new javax.swing.JMenuItem[1];

        final String label = I18n.f("layout.ui.menuPlaceLocomotive", OUR_TRAIN);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                place[0] = itemNamed((javax.swing.JPopupMenu) make.newInstance(ui, PLATFORM, PLATFORM, options), label);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        assertNotNull(place[0], "the right-click menu on TunnelLeftPark has no " + label + " item");

        assertTrue(place[0].isEnabled(), "the right-click menu on TunnelLeftPark, which no track leads out of, greys "
            + label + " while the editor's Place puts the train there - Adam, OB-350: \"they should be consistent, and"
            + " allowed.\" (" + place[0].getToolTipText() + ")");

        SwingUtilities.invokeLater(place[0]::doClick);

        for (long end = System.currentTimeMillis() + 10000; standingOn() == null && System.currentTimeMillis() < end; )
        {
            Thread.sleep(50);
        }

        settle();

        assertNotNull(standingOn(), "the right-click Place did not put the train on TunnelLeftPark");
    }

    /** Makes this class's train the active locomotive, as the window makes one. */
    private static void makeItTheActiveTrain() throws Exception
    {
        // NOT AGAIN: mapping the active train to its button a second time takes it off
        if (ui.getActiveLoc() == null || !OUR_TRAIN.equals(ui.getActiveLoc().getName())) ui.mapLocToCurrentButton(OUR_TRAIN);

        for (long end = System.currentTimeMillis() + 10000; (ui.getActiveLoc() == null
            || !OUR_TRAIN.equals(ui.getActiveLoc().getName())) && System.currentTimeMillis() < end; ) settle();

        assertTrue(ui.getActiveLoc() != null && OUR_TRAIN.equals(ui.getActiveLoc().getName()), "precondition: "
            + OUR_TRAIN + " could not be made the active locomotive");
    }

    /** The Point of TunnelLeftPark this class's train stands on, or null. */
    private static String standingOn()
    {
        Point at = model.getAutoLayout().getLocomotiveLocation(train);

        return at != null && session.getStationIndex().pointNamesAt(PLATFORM).contains(at.getName()) ? at.getName() : null;
    }

    /** Takes this class's train off the railway, wherever it stands, and puts the questions' dialogs back. */
    private static void liftIt() throws Exception
    {
        org.traincontrol.gui.autonomy.ArrivalSidePrompt.answerForTests(null);
        org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

        SwingUtilities.invokeAndWait(() ->
        {
            for (Point point : model.getAutoLayout().getPoints())
            {
                if (point.getCurrentLocomotive() == train) point.setLocomotive(null);
            }
        });
    }

    private static javax.swing.JDialog awaitDialogStarting(String title) throws Exception
    {
        for (long end = System.currentTimeMillis() + 15000; System.currentTimeMillis() < end; )
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && String.valueOf(((javax.swing.JDialog) window).getTitle()).startsWith(title))
                {
                    return (javax.swing.JDialog) window;
                }
            }

            Thread.sleep(50);
        }

        fail("no dialog titled \"" + title + "...\" appeared");

        return null;
    }

    private static javax.swing.JMenuItem itemNamed(java.awt.Container menu, String text)
    {
        java.awt.Component[] children = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component child : children)
        {
            if (child instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) child).getText()))
            {
                return (javax.swing.JMenuItem) child;
            }

            if (child instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = itemNamed((javax.swing.JMenu) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static <T> T find(java.awt.Container container, Class<T> type)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof java.awt.Container)
            {
                T found = find((java.awt.Container) child, type);

                if (found != null) return found;
            }
        }

        return null;
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
            throw new AssertionError("TunnelLeftPark does not run north to south on this snapshot");
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
