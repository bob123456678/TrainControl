package regression;

import java.util.LinkedHashMap;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A locomotive placed in the autonomy editor is still there when the editor closes (MT-337): *"placing locomotives via
 * the editor does not seem to work at all - nothing happens. it only works if placing via the track diagram."*
 *
 * The track diagram places into the RUNNING layout; the autonomy editor calls `AutonomySession.placeLocomotive`, which
 * writes `loc` into the setup.  Closing the editor then (1) `whereTheTrainsAre()` records every placement in the
 * running layout, from BEFORE the edit; (2) rebuilds the layout from the setup, so it carries the edit; (3)
 * `putTheTrainsBack()` re-places everything recorded in step 1 over the top of it, and `captureRunningLayout()` writes
 * the undone version back.  Reading the order of those statements, as `testEditorSurfaceRules` does, cannot say what
 * they do to a train.
 *
 * @author Adam
 */
public class testAnEditedPlacementSurvivesTheRebuild
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    /** The train the operator re-places in the editor. */
    private static final String MOVED = "OB189 mover";

    /** The train nothing touches, which must still be put back. */
    private static final String STAYER = "OB189 stayer";

    private static final int MOVED_ADDRESS = 61;

    private static final int STAYER_ADDRESS = 62;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before the model: init reads the layout preference and would otherwise open Adam's own railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();
        model.stop();

        model.newMM2Locomotive(MOVED, MOVED_ADDRESS);
        model.newMM2Locomotive(STAYER, STAYER_ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null)
        {
            try { model.deleteLoc(MOVED); } catch (Exception ignored) { }
            try { model.deleteLoc(STAYER); } catch (Exception ignored) { }
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * A rebuild that already places the train leaves it where the setup put it (MT-337): the rebuilt layout holds
     * `MOVED` at `SIDING` because the editor's write reached the setup, and putting it back to `PLATFORM` would undo
     * the edit.
     *
     * MUTATION: remove the "already placed" test from `putTheTrainsBack` and this fails, reporting the train back at
     * PLATFORM.
     */
    @Test
    public void testTheEditorsPlacementIsNotUndone() throws Exception
    {
        Layout built = new Layout(model);

        // A destination with a sensor, because only a destination can hold a train and it must have an s88.  The
        // addresses are past the protocol range so they cannot collide with the real railway's sixteen (OB-111).
        MarklinFeedback platformSensor = model.newFeedback(8394, null);
        MarklinFeedback sidingSensor = model.newFeedback(8395, null);

        model.setFeedbackState(platformSensor.getName(), false);
        model.setFeedbackState(sidingSensor.getName(), false);

        built.createPoint("PLATFORM", true, platformSensor.getName());
        built.createPoint("SIDING", true, sidingSensor.getName());

        built.moveLocomotive(MOVED, "SIDING", false);

        Map<String, String[]> standing = new LinkedHashMap<>();

        standing.put(MOVED, new String[]{"PLATFORM", null});

        // The door names the train it has just written into the setup, which tells this case apart from the one below.
        TrainControlUI.putTheTrainsBack(built, standing, null,
            java.util.Collections.singleton(MOVED));

        Point platform = built.getPoint("PLATFORM");
        Point siding = built.getPoint("SIDING");

        assertNull(platform.getCurrentLocomotive(),
            "the rebuild placed " + MOVED + " at SIDING because that is what the setup says after the"
            + " edit, and putting it back at PLATFORM undoes the operator's placement - which is"
            + " exactly what MT-337 reported: placing from the editor appears to do nothing");

        assertNotNull(siding.getCurrentLocomotive(),
            MOVED + " is on no square at all. Putting the trains back moved it off the square the"
            + " rebuild placed it on and did not put it anywhere");

        assertEquals(siding.getCurrentLocomotive().getName(), MOVED,
            "SIDING is holding the wrong locomotive");
    }

    /**
     * A train the rebuild placed nowhere is still put back (OB-183).  The control on the test above: if the fix were
     * "never put anything back", that test would pass and this one would fail.
     *
     * MUTATION: make `putTheTrainsBack` return immediately and this fails.
     */
    @Test
    public void testATrainTheRebuildDroppedIsStillPutBack() throws Exception
    {
        Layout built = new Layout(model);

        MarklinFeedback platformSensor = model.newFeedback(8394, null);
        MarklinFeedback sidingSensor = model.newFeedback(8395, null);

        model.setFeedbackState(platformSensor.getName(), false);
        model.setFeedbackState(sidingSensor.getName(), false);

        built.createPoint("PLATFORM", true, platformSensor.getName());
        built.createPoint("SIDING", true, sidingSensor.getName());

        // The rebuild placed nothing: the setup cannot say where the trains are because they moved after it was
        // written.
        Map<String, String[]> standing = new LinkedHashMap<>();

        standing.put(STAYER, new String[]{"PLATFORM", "north"});

        TrainControlUI.putTheTrainsBack(built, standing, null);

        Point platform = built.getPoint("PLATFORM");

        assertNotNull(platform.getCurrentLocomotive(),
            STAYER + " was standing at PLATFORM and the rebuild has no placement for it, so nothing"
            + " else knows where it is. Dropping it here is OB-183, which this restore exists to fix");

        assertEquals(platform.getCurrentLocomotive().getName(), STAYER,
            "PLATFORM is holding the wrong locomotive");

        assertEquals(platform.getArrivedFrom(), "north",
            "the arrival side did not go back with the train. `Point.setLocomotive` clears it when the"
            + " occupant changes, which is right for a different train arriving and wrong for the same"
            + " one being put back - without this the tail blocking switches itself off on every"
            + " rebuild, which is OB-183 wearing different clothes");
    }

    /**
     * A train the rebuilt railway refuses to put back leaves no tail on the square, and is said.  A station demoted
     * after a run, with the train still on it, is put back on its square, tail and all; what still refuses is the
     * railway running.
     *
     * MUTATION: refuse a square that is no station in the put-back, and this fails.
     */
    @Test
    public void testATrainThatCannotBePutBackLeavesNoTailBehind() throws Exception
    {
        Layout built = new Layout(model);

        MarklinFeedback platformSensor = model.newFeedback(8394, null);
        MarklinFeedback passingSensor = model.newFeedback(8396, null);

        model.setFeedbackState(platformSensor.getName(), false);
        model.setFeedbackState(passingSensor.getName(), false);

        built.createPoint("PLATFORM", true, platformSensor.getName());
        built.createPoint("PASSING", false, passingSensor.getName());

        Map<String, String[]> standing = new LinkedHashMap<>();

        standing.put(STAYER, new String[]{"PASSING", "north"});

        java.util.List<String> said = new java.util.ArrayList<>();

        java.util.Set<String> notBack = TrainControlUI.putTheTrainsBack(built, standing, said::add);

        Point passing = built.getPoint("PASSING");

        assertTrue(passing.getCurrentLocomotive() != null && STAYER.equals(passing.getCurrentLocomotive().getName()),
            STAYER + " was not put back on PASSING, which is no station but where it stands - left off the railway, the"
            + " square read free (RSA17-A1): " + notBack);

        assertEquals(passing.getArrivedFrom(), "north", "the arrival side did not go back with the train");

        Layout running = new Layout(model);

        MarklinFeedback otherSensor = model.newFeedback(8397, null);

        model.setFeedbackState(otherSensor.getName(), false);

        running.createPoint("OTHER", true, otherSensor.getName());

        java.lang.reflect.Field runningFlag = Layout.class.getDeclaredField("running");

        runningFlag.setAccessible(true);
        runningFlag.setBoolean(running, true);

        try
        {
            Map<String, String[]> there = new LinkedHashMap<>();

            there.put(STAYER, new String[]{"OTHER", "north"});

            java.util.Set<String> refused = TrainControlUI.putTheTrainsBack(running, there, said::add);

            Point other = running.getPoint("OTHER");

            assertNull(other.getCurrentLocomotive(), "precondition: the railway put a train back while it ran");

            assertNull(other.getArrivedFrom(), "the train could not be put back on OTHER and the square was given its"
                + " arrival side anyway - a tail with no train, which the tail walk and the next capture both read"
                + " (AMS-C2)");

            assertTrue(refused.contains(STAYER), "a train the put-back refused is not said (RSA17-A1): " + refused);
        }
        finally
        {
            runningFlag.setBoolean(running, false);
        }
    }

    /**
     * A placement NOBODY edited is a record, and a run outran it - so the railway wins.  The setup holds a stale entry
     * (captured while the train was at PLATFORM; the run took it to SIDING), so the rebuild has an opinion, the wrong
     * one.  The door this reaches production through has no editor in it: the diagram viewer's right-click autonomy
     * menu is an `AutonomyEditorPanel` whose gestures end in `rebuildRunningLayoutFromSetup(true)`, and nothing on that
     * path captures the running layout (`captureRunningLayout`: *"Stopping autonomy does not capture."*).  A train
     * modeled at PLATFORM while it stands at SIDING is a block the next dispatch believes is free.  What tells the
     * cases apart is provenance: the rebuild is told which trains the setup was just given a new placement for, and
     * this train is not among them.
     *
     * MUTATION: pass MOVED in the edited set and this fails, reporting the train back at PLATFORM.
     */
    @Test
    public void testAStalePlacementDoesNotOverwriteTheRunningRailway() throws Exception
    {
        Layout built = new Layout(model);

        MarklinFeedback platformSensor = model.newFeedback(8394, null);
        MarklinFeedback sidingSensor = model.newFeedback(8395, null);

        model.setFeedbackState(platformSensor.getName(), false);
        model.setFeedbackState(sidingSensor.getName(), false);

        built.createPoint("PLATFORM", true, platformSensor.getName());
        built.createPoint("SIDING", true, sidingSensor.getName());

        // From a setup last captured before the run: nobody chose this, it is a record that has been overtaken.
        built.moveLocomotive(MOVED, "PLATFORM", false);

        Map<String, String[]> standing = new LinkedHashMap<>();

        standing.put(MOVED, new String[]{"SIDING", "north"});

        TrainControlUI.putTheTrainsBack(built, standing, null);

        Point platform = built.getPoint("PLATFORM");
        Point siding = built.getPoint("SIDING");

        assertNotNull(siding.getCurrentLocomotive(),
            MOVED + " is not at SIDING, where the run left it.  A setup nobody captured since the run"
            + " says PLATFORM, and letting that stale record win is the teleport Adam reported as"
            + " OB-183 - reached now through the viewer's right-click menu, which never captures");

        assertEquals(siding.getCurrentLocomotive().getName(), MOVED,
            "SIDING is holding the wrong locomotive");

        assertNull(platform.getCurrentLocomotive(),
            MOVED + " is modeled at PLATFORM while it is standing at SIDING.  Occupancy is currentLoc"
            + " and isPathClear never consults the s88, so the next dispatch can route another train"
            + " into a block that is physically occupied, and can dispatch this one from a square it"
            + " is not on");

        assertEquals(siding.getArrivedFrom(), "north",
            "the arrival side did not go back with the train, so the track behind it stops being"
            + " blocked by its tail");
    }

    /**
     * A train the last run left on a station is put back on it after the station is renamed.  Where a train stands
     * after a run lives on the running layout alone, and a rebuild from the diagram's setup menu carries each train by
     * the name of the Point it stands on, so it is carried by square and copy: the name recorded is traced to the
     * square it named.
     *
     * MUTATION: put the trains back by the names recorded alone, and this fails.
     */
    @Test
    public void testATrainOnARenamedStationIsPutBackOnIt() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = alphaBetaGamma("rsa4-a1", 8411);

        session.placeLocomotive(new org.traincontrol.automationui.TileGraph.TileKey("main", 1, 1), MOVED);
        session.rebuild();

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            String atBeta = null;

            for (Point p : running.getPoints())
            {
                if (p.getName().startsWith("Beta") && p.getName().contains("eastbound") && !p.getName().contains("reverse"))
                {
                    atBeta = p.getName();
                }
            }

            assertNotNull(atBeta, "precondition: Beta has no copy facing east");

            assertTrue(running.moveLocomotive(MOVED, atBeta, false), "precondition: the train could not be stood at "
                + atBeta);

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            session.setPointName(new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1), "Bravo");

            model.parseAuto(session.buildConfiguration());

            Layout built = model.getAutoLayout();

            TrainControlUI.putTheTrainsBack(built, standing, null, null, session::pointNamedNow);

            String renamed = "Bravo" + atBeta.substring("Beta".length());

            assertNotNull(built.getPoint(renamed), "precondition: no Point " + renamed + " after the rename");

            assertTrue(built.getPoint(renamed).getCurrentLocomotive() != null
                && MOVED.equals(built.getPoint(renamed).getCurrentLocomotive().getName()), MOVED + " is not back on "
                + renamed + ", where the run left it, after Beta was renamed Bravo (RSA4-A1)");

            for (Point p : built.getPoints())
            {
                if (p.getName().startsWith("Alpha"))
                {
                    assertTrue(p.getCurrentLocomotive() == null || !MOVED.equals(p.getCurrentLocomotive().getName()),
                        MOVED + " is held at " + p.getName() + ", where the setup last had it and the run began, while it"
                        + " stands at " + renamed + " - which reads free (RSA4-A1)");
                }
            }
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * A train on a square that has since become two copies is put back on the copy facing its way: the one copy of a
     * station on a one-way line carries the plain name, and made two-way again the square is two copies, each named for
     * its heading.  A copy is its facing, so the train goes on the copy that faces the way it did.
     *
     * MUTATION: carry a name only to the same copy under the square's new name, and this fails.
     */
    @Test
    public void testATrainOnASquareSplitSinceIsPutBackFacingItsWay() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = alphaBetaGamma("rsa4-a1-split", 8414);

        org.traincontrol.automationui.TileGraph.TileKey west = new org.traincontrol.automationui.TileGraph.TileKey("main", 2, 1);
        org.traincontrol.automationui.TileGraph.TileKey east = new org.traincontrol.automationui.TileGraph.TileKey("main", 4, 1);

        org.traincontrol.automationui.TileGraph.RouteId straight = new org.traincontrol.automationui.TileGraph.RouteId(0, 0);

        session.setDirection(west, straight, org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);
        session.setDirection(east, straight, org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);
        session.rebuild();

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            assertNotNull(running.getPoint("Beta"), "precondition: Beta is not one copy on a one-way line: "
                + namesOf(running));

            boolean eastward = false;

            for (org.traincontrol.automation.Edge e : running.getEdges())
            {
                if (e.getStart().getName().startsWith("Alpha") && "Beta".equals(e.getEnd().getName())) eastward = true;
            }

            assertTrue(eastward, "precondition: the line does not run east into Beta: " + namesOf(running));

            assertTrue(running.moveLocomotive(MOVED, "Beta", false), "precondition: the train could not be stood at Beta");

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            session.setDirection(west, straight, org.traincontrol.automationui.TileGraph.Direction.BOTH);
            session.setDirection(east, straight, org.traincontrol.automationui.TileGraph.Direction.BOTH);
            session.rebuild();

            model.parseAuto(session.buildConfiguration());

            Layout built = model.getAutoLayout();

            assertNotNull(built.getPoint("Beta (eastbound)"), "precondition: Beta did not split: " + namesOf(built));

            TrainControlUI.putTheTrainsBack(built, standing, null, null, session::pointNamedNow);

            Point back = built.getPoint("Beta (eastbound)");

            assertTrue(back.getCurrentLocomotive() != null && MOVED.equals(back.getCurrentLocomotive().getName()), MOVED
                + ", facing east at Beta, is not on Beta (eastbound) once Beta was made two copies (RSA4-A1): "
                + namesOf(built));
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * Alpha, Beta and Gamma, stations at 1,1, 3,1 and 5,1 of one line, on three sensors from the one given: a session
     * in a folder of its own.
     */
    private static org.traincontrol.automationui.AutonomySession alphaBetaGamma(String folderName, int firstSensor)
        throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory(folderName).toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = new org.traincontrol.base.LayoutDiagram("main", 7, 3, null, null);

        for (int i = 0; i < 3; i++)
        {
            page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK, 1 + 2 * i, 1, 0, 0,
                firstSensor + i, firstSensor + i, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

            if (i < 2)
            {
                page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT, 2 + 2 * i, 1, 0, 0,
                    0, 0, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);
            }
        }

        page.setPageId("1");

        org.traincontrol.automationui.AutonomySession session = new org.traincontrol.automationui.AutonomySession(folder);

        session.open(java.util.Arrays.asList(page));
        session.initialize(folderName);

        String[] names = {"Alpha", "Beta", "Gamma"};

        for (int i = 0; i < 3; i++)
        {
            org.traincontrol.automationui.TileGraph.TileKey square =
                new org.traincontrol.automationui.TileGraph.TileKey("main", 1 + 2 * i, 1);

            session.setStation(square, true);
            session.setPointName(square, names[i]);
        }

        return session;
    }

    private static String namesOf(Layout layout)
    {
        java.util.List<String> names = new java.util.ArrayList<>();

        for (Point p : layout.getPoints()) names.add(p.getName());

        java.util.Collections.sort(names);

        return names.toString();
    }

    /**
     * A train on a station with no facing (no track arrives at it, one way out) is put back on it after the station is
     * renamed: such a square is one copy that records no facing, so only its name, carried to the square's new name,
     * finds it.
     *
     * MUTATION: carry a name only to the copy facing the same way, and this fails.
     */
    @Test
    public void testATrainOnAStationWithNoFacingIsPutBackAfterARename() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = alphaBetaGamma("rsa4-a1-nofacing", 8417);

        org.traincontrol.automationui.TileGraph.RouteId straight = new org.traincontrol.automationui.TileGraph.RouteId(0, 0);

        for (int x : new int[] {2, 4})
        {
            session.setDirection(new org.traincontrol.automationui.TileGraph.TileKey("main", x, 1), straight,
                org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);
        }

        session.rebuild();

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            assertNotNull(running.getPoint("Alpha"), "precondition: Alpha is not one copy: " + namesOf(running));

            assertTrue(running.moveLocomotive(STAYER, "Alpha", false), "precondition: the train could not be stood at"
                + " Alpha");

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            session.setPointName(new org.traincontrol.automationui.TileGraph.TileKey("main", 1, 1), "Alfa");

            model.parseAuto(session.buildConfiguration());

            Layout built = model.getAutoLayout();

            assertNotNull(built.getPoint("Alfa"), "precondition: no Point Alfa after the rename: " + namesOf(built));

            TrainControlUI.putTheTrainsBack(built, standing, null, null, session::pointNamedNow);

            Point back = built.getPoint("Alfa");

            assertTrue(back.getCurrentLocomotive() != null && STAYER.equals(back.getCurrentLocomotive().getName()), STAYER
                + ", left at Alpha by the run, is not on it once Alpha was renamed Alfa (RSA4-A1): " + namesOf(built));
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * A station renamed to the name another station has keeps both trains the last run left on the two: the name moves
     * to the renamed square, the other is called something else, and each train goes back on its own square, carried by
     * the square it stood on and its facing.
     *
     * MUTATION: take a name the rebuild still gives as the same Point, whatever square it is on now, and this fails.
     */
    @Test
    public void testARenameToAnotherStationsNameKeepsBothTrains() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("rsa5-a1", 8421, "Alpha", "Beta", "Gamma", "Delta");

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            assertNotNull(running.getPoint("Beta (eastbound)"), "precondition: no Beta (eastbound): " + namesOf(running));
            assertNotNull(running.getPoint("Gamma (eastbound)"), "precondition: no Gamma (eastbound): " + namesOf(running));

            assertTrue(running.moveLocomotive(MOVED, "Beta (eastbound)", false), "precondition: " + MOVED + " not stood");
            assertTrue(running.moveLocomotive(STAYER, "Gamma (eastbound)", false), "precondition: " + STAYER + " not stood");

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            session.setPointName(new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1), "Gamma");

            model.parseAuto(session.buildConfiguration());

            Layout built = model.getAutoLayout();

            assertNotNull(built.getPoint("Gamma (2) (eastbound)"), "precondition: main 5,1 is not Gamma (2): "
                + namesOf(built));

            TrainControlUI.putTheTrainsBack(built, standing, null, null, session::pointNamedNow);

            Point renamed = built.getPoint("Gamma (eastbound)");
            Point other = built.getPoint("Gamma (2) (eastbound)");

            assertTrue(renamed.getCurrentLocomotive() != null && MOVED.equals(renamed.getCurrentLocomotive().getName()),
                MOVED + ", left on Beta, is not on it once Beta was renamed Gamma (RSA5-A1): " + standingOn(built));

            assertTrue(other.getCurrentLocomotive() != null && STAYER.equals(other.getCurrentLocomotive().getName()),
                STAYER + ", left on the station that was Gamma, is not on it once another station took the name - moved"
                + " onto the renamed one, or off the railway (RSA5-A1): " + standingOn(built));
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * Stations in a row at 1,1, 3,1, 5,1 and on, one per name, on sensors from the one given: a session in a folder of
     * its own.
     */
    private static org.traincontrol.automationui.AutonomySession aRow(String folderName, int firstSensor,
        String... names) throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory(folderName).toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = new org.traincontrol.base.LayoutDiagram("main", 2 * names.length + 1,
            3, null, null);

        for (int i = 0; i < names.length; i++)
        {
            page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK, 1 + 2 * i, 1, 0, 0,
                firstSensor + i, firstSensor + i, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

            if (i + 1 < names.length)
            {
                page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT, 2 + 2 * i, 1, 0, 0,
                    0, 0, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);
            }
        }

        page.setPageId("1");

        org.traincontrol.automationui.AutonomySession session = new org.traincontrol.automationui.AutonomySession(folder);

        session.open(java.util.Arrays.asList(page));
        session.initialize(folderName);

        for (int i = 0; i < names.length; i++)
        {
            org.traincontrol.automationui.TileGraph.TileKey square =
                new org.traincontrol.automationui.TileGraph.TileKey("main", 1 + 2 * i, 1);

            session.setStation(square, true);
            session.setPointName(square, names[i]);
        }

        return session;
    }

    private static String standingOn(Layout layout)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null) out.add(p.getCurrentLocomotive().getName() + " on " + p.getName());
        }

        return out.toString();
    }

    /**
     * A train the last run left on a station, whose way a direction then takes, is recorded in the setup where it
     * stands and kept there through the fold: the railway cannot stand it on a copy facing another way.  The rebuild
     * stands it on no Point rather than on Beta's copy facing east, and the fold keeps it facing west.
     *
     * MUTATION: record nothing, and this fails; so does a fold that clears its square, and a build that stands it on a
     * copy facing another way.
     */
    @Test
    public void testATrainTheRunLeftIsRecordedWhereItStandsWhenItsWayGoes() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("r34-railway", 8471, "Alpha", "Beta", "Gamma");

        org.traincontrol.automationui.TileGraph.TileKey beta =
            new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Beta (westbound)", false), "precondition: " + MOVED
                + " not stood on Beta (westbound): " + namesOf(running));

            running.getPoint("Beta (westbound)").setArrivedFrom("E");

            // Nothing arrives at Beta on this row, so one of its two edges: the record must carry the road the railway
            // has.
            java.util.List<org.traincontrol.automation.Edge> road = new java.util.ArrayList<>();

            for (org.traincontrol.automation.Edge edge : running.getEdges())
            {
                if (road.isEmpty() && edge.getEnd() != null) road.add(edge);
            }

            assertFalse(road.isEmpty(), "precondition: the railway has no edge to give Beta as its road: "
                + running.getEdges().size() + " edges, " + namesOf(running));

            running.getPoint("Beta (westbound)").setArrivedAlong(road);

            String roadNames = Layout.namesOfRoad(road);

            assertTrue(session.getLocomotiveNameAt(beta) == null, "precondition: the setup already has a train on Beta");

            java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> either = new java.util.LinkedHashSet<>();

            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 2, 1));
            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 4, 1));

            session.setDirection(either, org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);

            assertEquals(session.getLocomotiveNameAt(beta), MOVED, "the train the run left on Beta, whose way the change"
                + " took, is not recorded where it stands");

            assertEquals(session.getFacing(beta), org.traincontrol.automationui.TilePorts.Side.W, "the train recorded on"
                + " Beta is not recorded facing west, as it stands");

            assertEquals(session.getPointProperty(beta, "arrivedFrom"), "E", "the train recorded on Beta is recorded"
                + " without the side it came in by");

            assertTrue(sameRoad(session.getPointProperty(beta, "arrivedAlong"), roadNames), "the train recorded on Beta is"
                + " recorded without the road it came in along: " + session.getPointProperty(beta, "arrivedAlong")
                + ", came along " + roadNames);

            model.parseAuto(session.buildConfiguration());

            Layout rebuilt = model.getAutoLayout();

            for (org.traincontrol.automation.Point point : rebuilt.getPoints())
            {
                assertFalse(point.getCurrentLocomotive() != null && MOVED.equals(point.getCurrentLocomotive().getName()),
                    "the rebuild stood " + MOVED + " on " + point.getName() + ", though no copy of Beta faces west - a"
                    + " copy facing another way: the copy is the facing");
            }

            session.captureWhatTheRailwayOwns(rebuilt.toJSON(rebuilt.getLastPointsReached()),
                session.getStore().getActiveConfiguration(), null);

            assertEquals(session.getLocomotiveNameAt(beta), MOVED, "the fold of a railway that cannot stand the train"
                + " took it off the setup");

            assertEquals(session.getFacing(beta), org.traincontrol.automationui.TilePorts.Side.W, "the fold turned the"
                + " train on Beta round in the setup");

            assertEquals(session.trainsOnNoPoint().get(beta), org.traincontrol.automationui.TilePorts.Side.W, "the train"
                + " on no Point is not listed, facing west, for the diagram to draw on Beta: " + session.trainsOnNoPoint());

            // Asked of the setup's list, since the railway does not have the train.
            assertFalse(session.pausedTrainsOnNoPoint().contains(beta), "control: the train on no Point is listed as"
                + " paused before anybody paused it");

            session.setLocomotivePaused(MOVED, true);

            try
            {
                assertTrue(session.pausedTrainsOnNoPoint().contains(beta), "a paused train on no Point is not listed for"
                    + " the diagram to draw grey (RSA60-C5): " + session.pausedTrainsOnNoPoint());
            }
            finally
            {
                session.setLocomotivePaused(MOVED, false);
            }
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * A carry across a load after squares were moved puts each train back on its own station: while a declined edit
     * waits the trains are carried by the square the railway says each stands on, so a move moves the squares the
     * railway's Points say they are copies of, as it moves the setup's.
     *
     * MUTATION: move the setup's squares and not the railway's, and this fails.
     */
    @Test
    public void testACarryAfterAMoveKeepsEachTrainOnItsStation() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("rsa7-b3-move").toFile();

        folder.deleteOnExit();

        org.traincontrol.automationui.AutonomySession session = sessionOn(folder, aRowPage("main", 0, 8441, 4), "B3 move",
            "Alpha", "Beta", "Gamma", "Delta");

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma (eastbound)", false), "precondition: " + MOVED + " not stood"
                + " on Gamma: " + namesOf(running));

            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey>
                moves = new java.util.LinkedHashMap<>();

            for (int x = 7; x >= 1; x--)
            {
                moves.put(new org.traincontrol.automationui.TileGraph.TileKey("main", x, 1),
                    new org.traincontrol.automationui.TileGraph.TileKey("main", x + 2, 1));
            }

            session.moveTiles(moves);
            session.saveWithoutReconciling();

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            org.traincontrol.automationui.AutonomySession next = sessionOver(folder, aRowPage("main", 2, 8441, 4));

            model.parseAuto(next.buildConfiguration());

            Layout built = model.getAutoLayout();

            TrainControlUI.putTheTrainsBack(built, standing, null, null, next::pointNamedNow);

            assertTrue(stoodOn(built, MOVED, "main:7,1", "Gamma"), MOVED + ", left on Gamma, is not on it after Gamma's"
                + " square was moved two columns (RSA7-B3): " + standingOn(built));
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * A carry across a load after a page was renamed puts each train back on its own station: a rename moves the
     * squares the railway's Points say they are copies of, as it moves the setup's.
     *
     * MUTATION: rename the setup's page and not the railway's squares, and this fails.
     */
    @Test
    public void testACarryAfterAPageRenameKeepsEachTrainOnItsStation() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("rsa7-b3-rename").toFile();

        folder.deleteOnExit();

        org.traincontrol.automationui.AutonomySession session = sessionOn(folder, aRowPage("main", 0, 8451, 4),
            "B3 rename", "Alpha", "Beta", "Gamma", "Delta");

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma (eastbound)", false), "precondition: " + MOVED + " not stood"
                + " on Gamma: " + namesOf(running));

            session.renamePage("main", "yard");
            session.saveWithoutReconciling();

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            org.traincontrol.automationui.AutonomySession next = sessionOver(folder, aRowPage("yard", 0, 8451, 4));

            model.parseAuto(next.buildConfiguration());

            Layout built = model.getAutoLayout();

            TrainControlUI.putTheTrainsBack(built, standing, null, null, next::pointNamedNow);

            assertTrue(stoodOn(built, MOVED, "yard:5,1", "Gamma"), MOVED + ", left on Gamma, is not on it after its page"
                + " was renamed (RSA7-B3): " + standingOn(built));
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * A move canceled puts the railway's squares back with the setup's, or the carry reads every train's square as the
     * move had made it, against the pages as they were.
     *
     * MUTATION: put the setup back and not the railway's squares, and this fails.
     */
    @Test
    public void testACancelledMovePutsTheRailwaysSquaresBack() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("rsa7-b3-cancel").toFile();

        folder.deleteOnExit();

        org.traincontrol.automationui.AutonomySession session = sessionOn(folder, aRowPage("main", 0, 8461, 4),
            "B3 cancel", "Alpha", "Beta", "Gamma", "Delta");

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma (eastbound)", false), "precondition: " + MOVED + " not stood"
                + " on Gamma: " + namesOf(running));

            org.json.JSONObject asOpened = session.snapshotSetup();

            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey>
                moves = new java.util.LinkedHashMap<>();

            for (int x = 7; x >= 1; x--)
            {
                moves.put(new org.traincontrol.automationui.TileGraph.TileKey("main", x, 1),
                    new org.traincontrol.automationui.TileGraph.TileKey("main", x + 2, 1));
            }

            session.moveTiles(moves);

            assertTrue(stoodOn(running, MOVED, "main:7,1", "Gamma"), "precondition: the move did not move the railway's"
                + " squares: " + standingOn(running));

            assertTrue(session.restoreSetup(asOpened), "precondition: Cancel could not put the setup back");

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            org.traincontrol.automationui.AutonomySession next = sessionOver(folder, aRowPage("main", 0, 8461, 4));

            model.parseAuto(next.buildConfiguration());

            Layout built = model.getAutoLayout();

            TrainControlUI.putTheTrainsBack(built, standing, null, null, next::pointNamedNow);

            assertTrue(stoodOn(built, MOVED, "main:5,1", "Gamma"), MOVED + ", left on Gamma, is not on it after a move"
                + " was cancelled (RSA7-B3): " + standingOn(built));
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * Stations moved together in the track editor keep the timetable's legs through them: a move moves what the carry
     * remembers with the squares, or each leg is carried to the station now on its old square.
     *
     * MUTATION: leave the carry's memory where the squares were, and this fails.
     */
    @Test
    public void testAMoveKeepsTheTimetableOnItsStations() throws Exception
    {
        Object[] fixture = fiveStationsWithATimetable("rsa8-b2-save", 8471);

        org.traincontrol.automationui.AutonomySession session = (org.traincontrol.automationui.AutonomySession) fixture[0];
        org.traincontrol.base.LayoutDiagram page = (org.traincontrol.base.LayoutDiagram) fixture[1];
        String before = (String) fixture[2];

        session.moveTiles(shiftRow(page, 2));

        assertEquals(legsOf(session), before, "a move of stations together carried the timetable's legs to the stations"
            + " now on their old squares (RSA8-B2)");
    }

    /**
     * The same after the track editor's Cancel, which must put back what the move made the carry remember.
     *
     * MUTATION: put back the setup and not what the carry remembers, and this fails.
     */
    @Test
    public void testACancelledMoveKeepsTheTimetableOnItsStations() throws Exception
    {
        Object[] fixture = fiveStationsWithATimetable("rsa8-b2-cancel", 8481);

        org.traincontrol.automationui.AutonomySession session = (org.traincontrol.automationui.AutonomySession) fixture[0];
        org.traincontrol.base.LayoutDiagram page = (org.traincontrol.base.LayoutDiagram) fixture[1];
        String before = (String) fixture[2];

        org.json.JSONObject asOpened = session.snapshotSetup();

        session.moveTiles(shiftRow(page, 2));

        assertTrue(session.restoreSetup(asOpened), "precondition: Cancel could not put the setup back");

        assertEquals(legsOf(session), before, "a move cancelled carried the timetable's legs to other stations (RSA8-B2)");
    }

    /**
     * The same after the track editor's Ctrl+Z: the undo point holds what the carry remembers and every stored leg, and
     * the undo puts both back.
     *
     * MUTATION: put back the page's setup alone at an undo, and this fails.
     */
    @Test
    public void testAnUndoneMoveKeepsTheTimetableOnItsStations() throws Exception
    {
        Object[] fixture = fiveStationsWithATimetable("rsa8-b2-undo", 8491);

        org.traincontrol.automationui.AutonomySession session = (org.traincontrol.automationui.AutonomySession) fixture[0];
        org.traincontrol.base.LayoutDiagram page = (org.traincontrol.base.LayoutDiagram) fixture[1];
        String before = (String) fixture[2];

        java.util.Map<String, Object> undoPoint = session.snapshotPage("main");

        session.moveTiles(shiftRow(page, 2));

        shiftRow(page, -2);
        session.restorePage("main", undoPoint);

        assertEquals(legsOf(session), before, "a move undone carried the timetable's legs to other stations (RSA8-B2)");
    }

    /**
     * A leg to a station with no name of its own names it as before once a move is undone: the move renamed the names
     * made from its square ("main 5,1" became "main 7,1"), and the undo must name them back.
     *
     * MUTATION: put back the page's setup alone at an undo, and this fails.
     */
    @Test
    public void testAnUndoneMoveNamesAnUnnamedStationAsBefore() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("rsa8-b2-unnamed").toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = aRowPage("main", 0, 8501, 5, 13);

        org.traincontrol.automationui.AutonomySession session = sessionOn(folder, page, "B2 unnamed", "Alpha", "Beta");

        for (int x = 5; x <= 9; x += 2)
        {
            session.setStation(new org.traincontrol.automationui.TileGraph.TileKey("main", x, 1), true);
        }

        session.save();

        String[] leg = edgeFrom(new org.json.JSONObject(session.buildConfiguration()), "Beta", "main 5,1");

        assertNotNull(leg, "precondition: no edge from Beta to the unnamed station after it");

        session.setGlobal("timetable", new org.json.JSONArray().put(new org.json.JSONObject().put("loc", MOVED)
            .put("executionTime", 1L).put("secondsToNext", 5L)
            .put("path", new org.json.JSONArray().put(new org.json.JSONObject().put("start", leg[0]).put("end", leg[1])))));

        String before = legsOf(session);

        java.util.Map<String, Object> undoPoint = session.snapshotPage("main");

        session.moveTiles(shiftRow(page, 2));

        assertTrue(legsOf(session).contains("main 7,1"), "precondition: the move did not rename the unnamed station's"
            + " leg: " + legsOf(session));

        shiftRow(page, -2);
        session.restorePage("main", undoPoint);

        assertEquals(legsOf(session), before, "a move undone left the leg naming the unnamed station by its moved square"
            + " (RSA8-B2)");
    }

    /**
     * A move undone puts the railway's squares back with the page's setup, as Cancel does, or while a declined edit
     * waits the carry puts each train on the station its moved square held.
     *
     * MUTATION: put back the page's setup alone at an undo, and this fails.
     */
    @Test
    public void testAnUndoneMovePutsTheRailwaysSquaresBack() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("rsa8-b3-undo").toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = aRowPage("main", 0, 8511, 4, 11);

        org.traincontrol.automationui.AutonomySession session = sessionOn(folder, page, "B3 undo", "Alpha", "Beta",
            "Gamma", "Delta");

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma (eastbound)", false), "precondition: " + MOVED + " not stood"
                + " on Gamma: " + namesOf(running));

            java.util.Map<String, Object> undoPoint = session.snapshotPage("main");

            session.moveTiles(shiftRow(page, 2));

            assertTrue(stoodOn(running, MOVED, "main:7,1", "Gamma"), "precondition: the move did not move the railway's"
                + " squares: " + standingOn(running));

            shiftRow(page, -2);
            session.restorePage("main", undoPoint);

            assertTrue(stoodOn(running, MOVED, "main:5,1", "Gamma"), "a move undone left the railway's squares moved, so"
                + " the carry puts each train on the station its moved square holds (RSA8-B3): " + standingOn(running));
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * Alpha to Epsilon, two squares apart on one row of a page wide enough to move them, a timetable of Beta to Gamma
     * eastward and Delta to Gamma westward: the session, the page, and the legs as `legsOf` writes them.
     */
    private static Object[] fiveStationsWithATimetable(String folderName, int firstSensor) throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory(folderName).toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = aRowPage("main", 0, firstSensor, 5, 13);

        org.traincontrol.automationui.AutonomySession session = sessionOn(folder, page, folderName, "Alpha", "Beta",
            "Gamma", "Delta", "Epsilon");

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        String[] x = edgeFrom(built, "Beta (eastbound)", "Gamma (eastbound)");
        String[] y = edgeFrom(built, "Delta (westbound)", "Gamma (westbound)");

        assertTrue(x != null && y != null, "precondition: the two legs are not built: " + x + ", " + y);

        org.json.JSONArray table = new org.json.JSONArray();

        for (String[] leg : new String[][] {x, y})
        {
            table.put(new org.json.JSONObject().put("loc", MOVED).put("executionTime", 1L).put("secondsToNext", 5L)
                .put("path", new org.json.JSONArray().put(new org.json.JSONObject().put("start", leg[0])
                .put("end", leg[1]))));
        }

        session.setGlobal("timetable", table);

        return new Object[] {session, page, legsOf(session)};
    }

    private static String[] edgeFrom(org.json.JSONObject built, String from, String to)
    {
        for (Object o : built.getJSONArray("edges"))
        {
            org.json.JSONObject e = (org.json.JSONObject) o;

            if (e.getString("start").startsWith(from) && e.getString("end").startsWith(to))
            {
                return new String[] {e.getString("start"), e.getString("end")};
            }
        }

        return null;
    }

    private static String legsOf(org.traincontrol.automationui.AutonomySession session)
    {
        StringBuilder out = new StringBuilder();

        org.json.JSONArray table = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

        for (int i = 0; i < table.length(); i++)
        {
            for (Object p : table.getJSONObject(i).getJSONArray("path"))
            {
                org.json.JSONObject leg = (org.json.JSONObject) p;

                out.append(out.length() == 0 ? "" : "; ").append(leg.getString("start")).append(" -> ")
                    .append(leg.getString("end"));
            }
        }

        return out.toString();
    }

    private static java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
        org.traincontrol.automationui.TileGraph.TileKey> shiftRow(org.traincontrol.base.LayoutDiagram page, int by)
        throws Exception
    {
        java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey>
            moves = new java.util.LinkedHashMap<>();

        java.util.List<org.traincontrol.base.LayoutDiagramComponent> all = new java.util.ArrayList<>();

        for (int x = 0; x < page.getSx(); x++)
        {
            org.traincontrol.base.LayoutDiagramComponent c = page.getComponent(x, 1);

            if (c != null) all.add(c);
        }

        for (org.traincontrol.base.LayoutDiagramComponent c : all) page.addComponent(null, c.getX(), 1);

        for (org.traincontrol.base.LayoutDiagramComponent c : all)
        {
            int x = c.getX();

            c.setX(x + by);
            page.addComponent(c, x + by, 1);

            moves.put(new org.traincontrol.automationui.TileGraph.TileKey(page.getName(), x, 1),
                new org.traincontrol.automationui.TileGraph.TileKey(page.getName(), x + by, 1));
        }

        return moves;
    }

    /**
     * A page ticked out keeps its timetable entries through the railway's load and its fold (*"Just keep the entries,
     * and if a path is run that contains a point on a disabled page, reject it with an error"*): the railway built
     * without the page holds its entries as it read them, unrunnable, and the fold writes them back.
     *
     * MUTATION: drop an entry the railway cannot build at the load, and this fails.
     */
    @Test
    public void testAPageOutKeepsItsEntriesThroughTheLoadAndTheFold() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("keep-the-entries").toFile();

        folder.deleteOnExit();

        org.traincontrol.automationui.AutonomySession session = twoPagesWithATimetable(folder, "keep", 8541, null);

        String before = legsOf(session);

        try
        {
            session.setPageExcluded("second", true);
            session.rebuild();

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            assertEquals(running.getTimetable().size(), 2, "the railway built without the page dropped its entry, which"
                + " the next fold erases: " + running.getTimetable());

            assertTrue(running.getTimetable().get(0).isRunnable(), "precondition: the entry on the page in play reads as"
                + " unrunnable");

            assertFalse(running.getTimetable().get(1).isRunnable(), "the entry through the page left out reads as"
                + " runnable");

            session.captureFromLayout(running.toJSON(running.getLastPointsReached()));

            assertEquals(legsOf(session), before, "the fold of the railway built without the page changed the timetable");

            session.setPageExcluded("second", false);
            session.rebuild();

            model.parseAuto(session.buildConfiguration());

            java.util.List<org.traincontrol.automation.TimetablePath> back = model.getAutoLayout().getTimetable();

            assertTrue(back.size() == 2 && back.get(0).isRunnable() && back.get(1).isRunnable(), "the page's entry does"
                + " not run once the page is back: " + back);
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * Start Timetable asks each locomotive to stand where its first entry the railway can run starts: an entry through
     * a page left out has no start on the railway.
     *
     * MUTATION: ask each locomotive's first entry whatever it is, and this fails.
     */
    @Test
    public void testStartTimetableAsksOnlyEntriesTheRailwayCanRun() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("start-the-timetable").toFile();

        folder.deleteOnExit();

        String other = null;

        for (String name : model.getLocList())
        {
            if (!MOVED.equals(name) && other == null) other = name;
        }

        assertNotNull(other, "precondition: no second locomotive in the database");

        org.traincontrol.automationui.AutonomySession session = twoPagesWithATimetable(folder, "start", 8551, other);

        try
        {
            session.setPageExcluded("second", true);
            session.rebuild();

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            assertEquals(running.getTimetable().size(), 2, "precondition: the railway did not keep the page's entry");

            org.traincontrol.automation.TimetablePath first = running.getTimetable().get(0);

            assertTrue(running.moveLocomotive(MOVED, first.getStart().getName(), false), "precondition: " + MOVED
                + " not stood where its entry starts");

            org.traincontrol.automation.TimetablePath notThere = TrainControlUI.aTrainNotAtItsStart(running);

            assertNull(notThere, "Start Timetable refused the timetable over an entry the railway cannot run, whose"
                + " train has nowhere on the railway to stand: " + notThere);
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    /**
     * Two pages of three stations each, Alpha to Gamma on "main" and Delta to Zeta on "second", and a timetable of Beta
     * to Gamma for `MOVED` and Epsilon to Zeta for this locomotive (MOVED where null): the session, saved.
     */
    private static org.traincontrol.automationui.AutonomySession twoPagesWithATimetable(java.io.File folder,
        String configuration, int firstSensor, String secondTrain) throws Exception
    {
        org.traincontrol.base.LayoutDiagram main = aRowPage("main", 0, firstSensor, 3);
        org.traincontrol.base.LayoutDiagram second = aRowPage("second", 0, firstSensor + 5, 3);

        second.setPageId("2");

        org.traincontrol.automationui.AutonomySession session = new org.traincontrol.automationui.AutonomySession(folder);

        session.open(java.util.Arrays.asList(main, second));
        session.initialize(configuration);

        String[] names = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta"};

        for (int i = 0; i < 6; i++)
        {
            org.traincontrol.automationui.TileGraph.TileKey square =
                new org.traincontrol.automationui.TileGraph.TileKey(i < 3 ? "main" : "second", 1 + 2 * (i % 3), 1);

            session.setStation(square, true);
            session.setPointName(square, names[i]);
        }

        org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

        String[] x = edgeFrom(built, "Beta (eastbound)", "Gamma");
        String[] y = edgeFrom(built, "Epsilon (eastbound)", "Zeta");

        assertTrue(x != null && y != null, "precondition: the two legs are not built");

        org.json.JSONArray table = new org.json.JSONArray();

        String[][] legs = {x, y};
        String[] trains = {MOVED, secondTrain == null ? MOVED : secondTrain};

        for (int i = 0; i < 2; i++)
        {
            table.put(new org.json.JSONObject().put("loc", trains[i]).put("executionTime", 0L).put("secondsToNext", 0L)
                .put("path", new org.json.JSONArray().put(new org.json.JSONObject().put("start", legs[i][0])
                .put("end", legs[i][1]))));
        }

        session.setGlobal("timetable", table);
        session.save();

        return session;
    }

    /**
     * The editor's Cancel after two stations' names were swapped keeps the timetable on its stations: what the carry of
     * names remembers is taken when the editor opens, as its snapshot is, and put back whether or not a move was made.
     *
     * MUTATION: put back what the carry remembers only after a move, and this fails.
     */
    @Test
    public void testACancelAfterTwoNamesSwappedKeepsTheTimetable() throws Exception
    {
        Object[] fixture = fiveStationsWithATimetable("rsa9-b2-swap", 8561);

        org.traincontrol.automationui.AutonomySession session = (org.traincontrol.automationui.AutonomySession) fixture[0];
        String before = (String) fixture[2];

        org.json.JSONObject asOpened = session.snapshotSetup();

        session.beginEditSession();

        try
        {
            org.traincontrol.automationui.TileGraph.TileKey beta =
                new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);
            org.traincontrol.automationui.TileGraph.TileKey gamma =
                new org.traincontrol.automationui.TileGraph.TileKey("main", 5, 1);

            session.setPointName(beta, "Tmp");
            session.setPointName(gamma, "Beta");
            session.setPointName(beta, "Gamma");

            assertFalse(legsOf(session).equals(before), "precondition: the legs did not follow the swapped names");

            assertTrue(session.restoreSetup(asOpened), "precondition: Cancel could not put the setup back");

            assertEquals(legsOf(session), before, "a Cancel after two names were swapped carried the timetable's legs to"
                + " the other stations (RSA9-B2)");
        }
        finally
        {
            session.endEditSession();
        }
    }

    /**
     * Start Timetable asks nothing of the entries past the one the run will stop at: a train whose only entry comes
     * after it never sets off, so asking where it stands would refuse the timetable.
     *
     * MUTATION: ask every entry the railway can run, past the stop too, and this fails.
     */
    @Test
    public void testStartTimetableAsksNothingPastTheEntryTheRunStopsAt() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("start-past-the-stop").toFile();

        folder.deleteOnExit();

        String other = null;

        for (String name : model.getLocList())
        {
            if (!MOVED.equals(name) && other == null) other = name;
        }

        assertNotNull(other, "precondition: no second locomotive in the database");

        org.traincontrol.automationui.AutonomySession session = twoPagesWithATimetable(folder, "past the stop", 8571, null);

        try
        {
            org.json.JSONObject built = new org.json.JSONObject(session.buildConfiguration());

            String[] z = edgeFrom(built, "Beta (westbound)", "Alpha");

            assertNotNull(z, "precondition: no edge from Beta westward to Alpha");

            org.json.JSONArray table = new org.json.JSONArray(String.valueOf(session.getGlobal("timetable")));

            table.put(new org.json.JSONObject().put("loc", other).put("executionTime", 0L).put("secondsToNext", 0L)
                .put("path", new org.json.JSONArray().put(new org.json.JSONObject().put("start", z[0]).put("end", z[1]))));

            session.setGlobal("timetable", table);

            session.setPageExcluded("second", true);
            session.rebuild();

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            assertEquals(running.getTimetable().size(), 3, "precondition: the railway did not keep the three entries");

            assertFalse(running.getTimetable().get(1).isRunnable(), "precondition: the second entry can run");

            assertTrue(running.moveLocomotive(MOVED, running.getTimetable().get(0).getStart().getName(), false),
                "precondition: " + MOVED + " not stood where its entry starts");

            org.traincontrol.automation.TimetablePath notThere = TrainControlUI.aTrainNotAtItsStart(running);

            assertNull(notThere, "Start Timetable asked where a train stands of an entry past the one the run stops at"
                + " (RSA10-B2): " + notThere);
        }
        finally
        {
            new Layout(model).makeCurrent();
        }
    }

    private static org.traincontrol.base.LayoutDiagram aRowPage(String name, int offset, int firstSensor, int count)
        throws Exception
    {
        return aRowPage(name, offset, firstSensor, count, 2 * count + 1 + offset);
    }

    private static org.traincontrol.base.LayoutDiagram aRowPage(String name, int offset, int firstSensor, int count,
        int width) throws Exception
    {
        org.traincontrol.base.LayoutDiagram page = new org.traincontrol.base.LayoutDiagram(name, width, 3, null, null);

        for (int i = 0; i < count; i++)
        {
            page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK, offset + 1 + 2 * i, 1,
                0, 0, firstSensor + i, firstSensor + i, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

            if (i + 1 < count)
            {
                page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT, offset + 2 + 2 * i,
                    1, 0, 0, 0, 0, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);
            }
        }

        page.setPageId("1");

        return page;
    }

    private static org.traincontrol.automationui.AutonomySession sessionOn(java.io.File folder,
        org.traincontrol.base.LayoutDiagram page, String configuration, String... names) throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = new org.traincontrol.automationui.AutonomySession(folder);

        session.open(java.util.Arrays.asList(page));
        session.initialize(configuration);

        for (int i = 0; i < names.length; i++)
        {
            org.traincontrol.automationui.TileGraph.TileKey square =
                new org.traincontrol.automationui.TileGraph.TileKey(page.getName(), 1 + 2 * i, 1);

            session.setStation(square, true);
            session.setPointName(square, names[i]);
        }

        session.save();

        return session;
    }

    private static org.traincontrol.automationui.AutonomySession sessionOver(java.io.File folder,
        org.traincontrol.base.LayoutDiagram page) throws Exception
    {
        org.traincontrol.automationui.AutonomySession next = new org.traincontrol.automationui.AutonomySession(folder);

        next.open(java.util.Arrays.asList(page));

        return next;
    }

    private static boolean stoodOn(Layout layout, String train, String square, String name)
    {
        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null && train.equals(p.getCurrentLocomotive().getName()))
            {
                return square.equals(p.getSquare()) && p.getName().startsWith(name);
            }
        }

        return false;
    }

    /**
     * A train the last run left on a station that is then marked one trains only pass through stands there still after
     * the rebuild: the put-back must not refuse a copy that is no station.  The train IS there.
     *
     * MUTATION: refuse a copy that is no station in the put-back, and this fails.
     */
    @Test
    public void testATrainOnAStationMadePassThroughIsPutBackOnIt() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("r35-passthrough", 8481, "Alpha", "Beta", "Gamma");

        org.traincontrol.automationui.TileGraph.TileKey beta = new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Beta (westbound)", false), "precondition: " + MOVED
                + " not stood on Beta (westbound): " + namesOf(running));

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            session.setStation(beta, false);

            model.parseAuto(session.buildConfiguration());

            Layout rebuilt = model.getAutoLayout();

            java.util.Set<String> notBack = TrainControlUI.putTheTrainsBack(rebuilt, standing, line -> { }, null,
                session::pointNamedNow);

            Point on = null;

            for (Point point : rebuilt.getPoints())
            {
                if (point.getCurrentLocomotive() != null && MOVED.equals(point.getCurrentLocomotive().getName())) on = point;
            }

            assertTrue(on != null && "main:3,1".equals(on.getSquare()), MOVED + " is not on Beta after Beta was made a"
                + " station trains only pass through, though it stands there - the square reads free: " + standingOn(rebuilt)
                + "; not put back: " + notBack);
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * A train the last run turned round on a station, whose turning is then taken away, is recorded where it stands and
     * reported, not left where the setup last had it: no copy of the square faces its way any more.
     *
     * MUTATION: put nothing on the list of trains not put back for a copy the rebuild has not got, and this fails.
     */
    @Test
    public void testATrainWhoseTurnIsTakenAwayIsRecordedWhereItStands() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("r35-turn", 8491, "Alpha", "Beta", "Gamma");

        org.traincontrol.automationui.TileGraph.TileKey beta = new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);

        try
        {
            java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> either = new java.util.LinkedHashSet<>();

            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 2, 1));
            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 4, 1));

            session.setDirection(either, org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);
            session.setPointProperty(beta, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE, Boolean.TRUE);

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            String turned = null;

            for (Point point : running.getPoints())
            {
                if ("main:3,1".equals(point.getSquare()) && point.getName().contains(", reverse)")) turned = point.getName();
            }

            assertNotNull(turned, "precondition: Beta has no turning copy: " + namesOf(running));

            assertTrue(running.moveLocomotive(MOVED, turned, false, true), "precondition: " + MOVED + " not stood on "
                + turned);

            running.getPoint(turned).setArrivedFrom("W");

            java.util.List<org.traincontrol.automation.Edge> road = new java.util.ArrayList<>();

            for (org.traincontrol.automation.Edge edge : running.getEdges())
            {
                if (road.isEmpty() && edge.getEnd() != null && turned.equals(edge.getEnd().getName())) road.add(edge);
            }

            assertFalse(road.isEmpty(), "precondition: no edge ends on " + turned);

            running.getPoint(turned).setArrivedAlong(road);

            String roadNames = Layout.namesOfRoad(road);

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            session.setPointProperty(beta, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE, null);

            model.parseAuto(session.buildConfiguration());

            Layout rebuilt = model.getAutoLayout();

            java.util.Set<String> notBack = TrainControlUI.putTheTrainsBack(rebuilt, standing, line -> { }, null,
                session::pointNamedNow);

            assertTrue(notBack.contains(MOVED), MOVED + " could not be put back - no copy of Beta faces its way - and the"
                + " put-back did not say so: " + notBack + ", " + standingOn(rebuilt));

            TrainControlUI.recordTheTrainsNotPutBack(rebuilt, standing, notBack, session, line -> { });

            assertEquals(session.getLocomotiveNameAt(beta), MOVED, MOVED + " is not recorded on Beta, where it stands");

            String facing = standing.get(MOVED)[4];

            assertEquals(String.valueOf(session.getFacing(beta)), facing, MOVED + " is not recorded facing the way it"
                + " stands");

            assertEquals(session.getPointProperty(beta, "arrivedFrom"), "W", MOVED + " is recorded without the side it"
                + " came in by");

            assertTrue(sameRoad(session.getPointProperty(beta, "arrivedAlong"), roadNames), MOVED + " is recorded without"
                + " the road it came in along: " + session.getPointProperty(beta, "arrivedAlong") + ", came along "
                + roadNames);

            boolean reported = false;

            for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
            {
                if (org.traincontrol.automationui.AutonomyChecks.FACING_IMPOSSIBLE.equals(finding.getMessageKey())
                    && beta.equals(finding.getTile())
                    && finding.getSeverity() == org.traincontrol.automationui.AutonomyChecks.Severity.ERROR)
                {
                    reported = true;
                }
            }

            assertTrue(reported, "the train recorded on Beta, which no copy of it can hold, is not reported as an error: "
                + session.check());
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * A setup record the last run has outrun raises no error: the railway has the train on another square, and the
     * railway wins.  The train the railway has nowhere, recorded where it cannot stand, is listed for the diagram to
     * draw there.
     *
     * MUTATION: ask the setup alone for the error, and this fails.
     */
    @Test
    public void testARecordTheRunOutranRaisesNoError() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("r35-stale", 8501, "Alpha", "Beta", "Gamma");

        org.traincontrol.automationui.TileGraph.TileKey beta = new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);

        try
        {
            session.placeLocomotive(beta, MOVED);
            session.setFacing(beta, org.traincontrol.automationui.TilePorts.Side.W);

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma", false), "precondition: " + MOVED
                + " not stood on Gamma: " + namesOf(running));

            java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> either = new java.util.LinkedHashSet<>();

            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 2, 1));
            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 4, 1));

            session.setDirection(either, org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);

            for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
            {
                assertFalse(org.traincontrol.automationui.AutonomyChecks.FACING_IMPOSSIBLE.equals(finding.getMessageKey())
                    && beta.equals(finding.getTile()), "an error about Beta, where the setup's record says " + MOVED
                    + " stands, while the railway has it on Gamma: " + finding);
            }

            assertFalse(session.trainsOnNoPoint().containsKey(beta), "a record the run outran is listed as a train on no"
                + " Point");
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * A train of the railway's beside a link that is switched off, where no copy then faces its way, is recorded where
     * it stands: `setPortalDisabled` writes it, as a direction does.
     *
     * MUTATION: take the record out of `setPortalDisabled`, and this fails.
     */
    @Test
    public void testATrainBesideALinkSwitchedOffIsRecordedWhereItStands() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("r35-link").toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = new org.traincontrol.base.LayoutDiagram("main", 7, 3, null, null);

        org.traincontrol.base.LayoutDiagramComponent.componentType[] row = {
            org.traincontrol.base.LayoutDiagramComponent.componentType.LINK,
            org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK,
            org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT,
            org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK,
            org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT,
            org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK,
            org.traincontrol.base.LayoutDiagramComponent.componentType.LINK};

        int sensor = 8511;

        for (int x = 0; x < row.length; x++)
        {
            boolean feedback = row[x] == org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK;

            page.addComponent(row[x], x, 1, x == 0 ? 2 : 0, 0, feedback ? sensor : 0, feedback ? sensor++ : 0,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);
        }

        page.setPageId("1");

        org.traincontrol.automationui.AutonomySession session = new org.traincontrol.automationui.AutonomySession(folder);

        session.open(java.util.Arrays.asList(page));
        session.initialize("r35-link");

        String[] names = {"Alpha", "Beta", "Gamma"};

        for (int i = 0; i < 3; i++)
        {
            session.setStation(new org.traincontrol.automationui.TileGraph.TileKey("main", 1 + 2 * i, 1), true);
            session.setPointName(new org.traincontrol.automationui.TileGraph.TileKey("main", 1 + 2 * i, 1), names[i]);
        }

        org.traincontrol.automationui.TileGraph.TileKey east = new org.traincontrol.automationui.TileGraph.TileKey("main", 6, 1);
        org.traincontrol.automationui.TileGraph.TileKey gamma = new org.traincontrol.automationui.TileGraph.TileKey("main", 5, 1);

        session.pairPortals(new org.traincontrol.automationui.TileGraph.TileKey("main", 0, 1), east);

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma (westbound)", false), "precondition: " + MOVED
                + " not stood on Gamma (westbound): " + namesOf(running));

            assertTrue(session.getLocomotiveNameAt(gamma) == null, "precondition: the setup already has a train on Gamma");

            session.setPortalDisabled(east, true);

            assertTrue(session.getStore().isPortalDisabled(east), "precondition: the link was not switched off");

            assertEquals(session.getLocomotiveNameAt(gamma), MOVED, "the train beside the link switched off, which no copy"
                + " of Gamma faces the way of now, is not recorded where it stands");

            assertEquals(session.getFacing(gamma), org.traincontrol.automationui.TilePorts.Side.W, "the train recorded on Gamma is not recorded facing"
                + " west, as it stands");
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * A setup record the last run outran raises no error where the railway has the train on the same square facing the
     * other way: a run turned it and brought it back, and the setup's facing is the one it set off with.
     *
     * MUTATION: ask the setup's facing for a train the railway has on the same square, and this fails.
     */
    @Test
    public void testARecordTheRunTurnedRaisesNoError() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("r36-turned", 8521, "Alpha", "Beta", "Gamma");

        org.traincontrol.automationui.TileGraph.TileKey beta = new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);

        try
        {
            session.placeLocomotive(beta, MOVED);
            session.setFacing(beta, org.traincontrol.automationui.TilePorts.Side.E);

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Beta (westbound)", false), "precondition: " + MOVED
                + " not stood on Beta (westbound): " + namesOf(running));

            java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> either = new java.util.LinkedHashSet<>();

            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 2, 1));
            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 4, 1));

            session.setDirection(either, org.traincontrol.automationui.TileGraph.Direction.TOWARD_B);

            for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
            {
                assertFalse(org.traincontrol.automationui.AutonomyChecks.FACING_IMPOSSIBLE.equals(finding.getMessageKey())
                    && beta.equals(finding.getTile()), "an error about the facing the setup recorded for " + MOVED
                    + " on Beta, while the railway has it there facing west: " + finding);
            }
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * Home All Trains Where They Stand homes each train where the railway has it, not where the setup last had it: from
     * the track diagram's menu, which folds nothing first.
     *
     * MUTATION: walk the setup's placements alone, and this fails.
     */
    @Test
    public void testHomeAllHomesTheTrainsWhereTheyStand() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("r36-home", 8531, "Alpha", "Beta", "Gamma", "Delta");

        org.traincontrol.automationui.TileGraph.TileKey beta = new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);
        org.traincontrol.automationui.TileGraph.TileKey gamma = new org.traincontrol.automationui.TileGraph.TileKey("main", 5, 1);

        try
        {
            session.placeLocomotive(beta, MOVED);

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma (eastbound)", false), "precondition: " + MOVED
                + " not stood on Gamma (eastbound): " + namesOf(running));

            session.homeEveryPlacedTrain();

            assertEquals(session.getPointProperty(gamma, "home"), MOVED, MOVED + " was not homed on Gamma, where it stands");

            assertNull(session.getPointProperty(beta, "home"), MOVED + " was homed on Beta, where it set off from");
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * The no-length notice, on the square a train stands on after a run, names that train and not the one the setup
     * recorded there.
     *
     * MUTATION: name the setup's train, and this fails.
     */
    @Test
    public void testTheNoLengthNoticeNamesTheTrainThatStandsThere() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = aRow("r37-length", 8541, "Alpha", "Beta", "Gamma", "Delta");

        org.traincontrol.automationui.TileGraph.TileKey beta = new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);
        org.traincontrol.automationui.TileGraph.TileKey gamma = new org.traincontrol.automationui.TileGraph.TileKey("main", 5, 1);

        try
        {
            session.placeLocomotive(beta, MOVED);
            session.setTrainLengthSource(name -> 0);

            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            session.setRunningLayoutSource(() -> model.getAutoLayout());

            assertTrue(running.moveLocomotive(MOVED, "Gamma (eastbound)", false), "precondition: " + MOVED
                + " not stood on Gamma (eastbound): " + namesOf(running));

            String named = null;

            for (org.traincontrol.automationui.AutonomyChecks.Finding finding : session.check())
            {
                if (org.traincontrol.automationui.AutonomyChecks.NO_TRAIN_LENGTH.equals(finding.getMessageKey())
                    && gamma.equals(finding.getTile()))
                {
                    named = finding.getSubject();
                }
            }

            assertEquals(named, MOVED, "the no-length notice on Gamma, where " + MOVED + " stands, does not name it");
        }
        finally
        {
            session.setRunningLayoutSource(null);
            session.setTrainLengthSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * Whether a recorded road is the road a train came in along: the same steps between the same squares' Points, by
     * name before any heading.
     */
    private static boolean sameRoad(Object recorded, String came)
    {
        if (recorded == null || came == null) return false;

        org.json.JSONArray was = new org.json.JSONArray(came);
        org.json.JSONArray now = new org.json.JSONArray(recorded.toString());

        if (was.length() != now.length() || now.length() == 0) return false;

        for (int i = 0; i < now.length(); i++)
        {
            for (int end = 0; end < 2; end++)
            {
                if (!base(was.getJSONArray(i).getString(end)).equals(base(now.getJSONArray(i).getString(end)))) return false;
            }
        }

        return true;
    }

    private static String base(String name)
    {
        int heading = name.indexOf(" (");

        return heading < 0 ? name : name.substring(0, heading);
    }
}
