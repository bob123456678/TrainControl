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
 * A locomotive placed in the autonomy editor is still there when the editor closes.
 *
 * Adam, 2026-09-08, MT-337: *"placing locomotives via the editor does not seem to work at all -
 * nothing happens. it only works if placing via the track diagram."*
 *
 * **Two doors write a placement to two different places.** The track diagram places into the RUNNING
 * layout. The autonomy editor calls `AutonomySession.placeLocomotive`, which writes `loc` into the
 * setup. Closing the editor then does three things in this order:
 *
 * 1. `whereTheTrainsAre()` records every placement in the running layout - the ones from BEFORE the edit;
 * 2. the layout is rebuilt from the setup, so it now carries the edit;
 * 3. `putTheTrainsBack()` re-places everything it recorded in step 1, over the top of it.
 *
 * So the editor's placement is undone in memory, and `captureRunningLayout()` - which runs immediately
 * afterwards - writes the undone version back to the file. The diagram's door survives because its
 * placement is in the running layout, which is what step 1 reads.
 *
 * **The comment on the code that does it states the assumption that made it safe**: *"A placement is
 * not an inferred setting: nobody chose it in the editor, and the railway is the only place it lives."*
 * `placeLocomotive` is an editor door, so the second clause is false and the first follows it down.
 * That is the shape this codebase keeps producing - a rule lifted to a new site without the
 * precondition that made it true where it came from.
 *
 * **Why nothing caught it.** `putTheTrainsBack`'s only coverage was a source-shape guard in
 * `testEditorSurfaceRules`, which asserts that `whereTheTrainsAre()` is read before the load and
 * applied after it. That ordering is correct, is the fix for `OB-183`, and is entirely beside the
 * point: reading the order of two statements cannot say what they do to a train.
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
        // Before the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
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
     * A rebuild that already places the train leaves it where the setup put it.
     *
     * This is MT-337. The rebuilt layout holds `MOVED` at `SIDING` because the editor's write reached
     * the setup; the record from before the edit says `PLATFORM`. Putting it back is undoing the edit.
     *
     * MUTATION: remove the "already placed" test from `putTheTrainsBack` and this fails, reporting the
     * train back at PLATFORM.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheEditorsPlacementIsNotUndone() throws Exception
    {
        Layout built = new Layout(model);

        // A DESTINATION WITH A SENSOR, because only a destination can hold a train, and a destination
        // must have an s88.  The addresses are past the protocol range so they cannot collide with
        // the real railway's sixteen (OB-111).
        MarklinFeedback platformSensor = model.newFeedback(8394, null);
        MarklinFeedback sidingSensor = model.newFeedback(8395, null);

        model.setFeedbackState(platformSensor.getName(), false);
        model.setFeedbackState(sidingSensor.getName(), false);

        built.createPoint("PLATFORM", true, platformSensor.getName());
        built.createPoint("SIDING", true, sidingSensor.getName());

        // WHAT THE REBUILD PRODUCED. The setup carries the edit, so the train is at SIDING.
        built.moveLocomotive(MOVED, "SIDING", false);

        // WHAT THE RUNNING LAYOUT HELD BEFORE IT - where the train was when the editor opened.
        Map<String, String[]> standing = new LinkedHashMap<>();

        standing.put(MOVED, new String[]{"PLATFORM", null});

        // AND THE DOOR SAYS SO.  `AutonomyEditorPanel.placeLocomotive` names the train it has just
        // written into the setup, and the rebuild carries that name here - which is what tells this
        // case apart from the one below, where the same disagreement means the opposite thing.
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
     * A train the rebuild placed nowhere is still put back, which is what OB-183 asked for.
     *
     * The control on the test above: if the fix were "never put anything back", that test would pass
     * and this one would fail. A rebuild that carries no placement for a train has no opinion about it,
     * and the running railway's answer is the only one there is.
     *
     * MUTATION: make `putTheTrainsBack` return immediately and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainTheRebuildDroppedIsStillPutBack() throws Exception
    {
        Layout built = new Layout(model);

        // A DESTINATION WITH A SENSOR, because only a destination can hold a train, and a destination
        // must have an s88.  The addresses are past the protocol range so they cannot collide with
        // the real railway's sixteen (OB-111).
        MarklinFeedback platformSensor = model.newFeedback(8394, null);
        MarklinFeedback sidingSensor = model.newFeedback(8395, null);

        model.setFeedbackState(platformSensor.getName(), false);
        model.setFeedbackState(sidingSensor.getName(), false);

        built.createPoint("PLATFORM", true, platformSensor.getName());
        built.createPoint("SIDING", true, sidingSensor.getName());

        // The rebuild placed nothing: this is the OB-183 case, where the setup cannot say where the
        // trains are because they moved after it was written.
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
     * A train the rebuilt railway refuses to put back leaves no tail on the square (AMS-C2).
     *
     * `putTheTrainsBack` asked `moveLocomotive` and did not read its answer, then wrote the arrival side and the
     * road onto the square whether or not the train was standing on it.  `moveLocomotive` refuses a square that
     * is not a destination - a station demoted after a run, with the train still on it - so the square was left
     * with a tail and no train.  (The refusal itself is logged, by `moveLocomotive`.)
     *
     * @throws Exception on a failure to build the fixture
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

        TrainControlUI.putTheTrainsBack(built, standing, said::add);

        Point passing = built.getPoint("PASSING");

        assertNull(passing.getCurrentLocomotive(),
            "precondition: the railway stood a train on a square that is not a destination, so this is not the"
            + " refused case");

        assertNull(passing.getArrivedFrom(),
            "the train could not be put back on PASSING and the square was given its arrival side anyway - a tail"
            + " with no train, which the tail walk and the next capture both read (AMS-C2)");

        // Not asserted: that `said` hears of it.  `moveLocomotive` logs its own refusal to the model's log, which is
        // the log the running application hands in here, so a second line would say it twice.
    }

    /**
     * A placement NOBODY edited is a record, and a run outran it - so the railway wins.
     *
     * D2-A1 / W7-A1, and the case the pair above leaves out. The control beside it models the setup
     * having **no** entry for the moved train; OB-183 as it happens on the railway leaves a **stale**
     * entry, because the setup was captured while the train was still at PLATFORM and the run then
     * took it to SIDING. The rebuild therefore has an opinion - the wrong one - and "where the rebuild
     * put it wins" hands it the argument.
     *
     * **The door this reaches production through has no editor in it.** The track diagram viewer's
     * right-click autonomy menu is itself an `AutonomyEditorPanel`, and every gesture on it - a home,
     * a priority, a caption - ends in `setupChanged()` -> `rebuildRunningLayoutSoon()` ->
     * `rebuildRunningLayoutFromSetup(true)`. Nothing on that path captures the running layout, and
     * nothing captures when a run ends (`captureRunningLayout`: *"Stopping autonomy does not capture."*).
     * So the setup is stale about exactly the trains the run moved.
     *
     * Occupancy is `currentLoc` and `isPathClear` never consults the s88, so a train modeled at
     * PLATFORM while it stands at SIDING is a block the next dispatch believes is free.
     *
     * **What tells the two cases apart is provenance, and it is now supplied rather than assumed.**
     * The rebuild is told which trains the setup was just given a new placement for; those are the
     * edits and they win. This train is not among them, so the running railway's answer is the one
     * that survives.
     *
     * MUTATION: pass MOVED in the edited set and this fails, reporting the train back at PLATFORM -
     * which is the whole of the defect.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAStalePlacementDoesNotOverwriteTheRunningRailway() throws Exception
    {
        Layout built = new Layout(model);

        // A DESTINATION WITH A SENSOR, because only a destination can hold a train, and a destination
        // must have an s88.  The addresses are past the protocol range so they cannot collide with
        // the real railway's sixteen (OB-111).
        MarklinFeedback platformSensor = model.newFeedback(8394, null);
        MarklinFeedback sidingSensor = model.newFeedback(8395, null);

        model.setFeedbackState(platformSensor.getName(), false);
        model.setFeedbackState(sidingSensor.getName(), false);

        built.createPoint("PLATFORM", true, platformSensor.getName());
        built.createPoint("SIDING", true, sidingSensor.getName());

        // WHAT THE REBUILD PRODUCED, from a setup last captured before the run: the train back at the
        // square it set off from.  Nobody chose this - it is a record that has been overtaken.
        built.moveLocomotive(MOVED, "PLATFORM", false);

        // WHERE THE RUN ACTUALLY LEFT IT, which lives only in the running layout.
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
     * A train the last run left on a station is put back on it after the station is renamed (RSA4-A1).
     *
     * Where a train stands after a run lives on the running layout alone, and a rebuild from the diagram's setup menu
     * carries each train across by the name of the Point it stands on.  A station renamed there renamed that Point; the
     * rebuild found no Point by the name recorded and left the train where the setup last had it - where the run began -
     * so the railway held it where it was not, and the square it stood on read free.  Carried now by square and copy: the
     * name recorded is traced to the square it named, and to the copy of that square the build now calls something else.
     *
     * MUTATION: put the trains back by the names recorded alone, and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainOnARenamedStationIsPutBackOnIt() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = alphaBetaGamma("rsa4-a1", 8411);

        // PLACED AT ALPHA, as the setup has it
        session.placeLocomotive(new org.traincontrol.automationui.TileGraph.TileKey("main", 1, 1), MOVED);
        session.rebuild();

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout running = model.getAutoLayout();

            // THE RUN LEFT IT AT BETA, facing east - on the running layout alone
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

            // THE RENAME, from the diagram's setup menu, and the rebuild it makes
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
     * A train on a square that has since become two copies is put back on the copy facing its way (RSA4-A1): the one
     * copy of a station on a one-way line carries the station's plain name, and made two-way again the square is two
     * copies, each named for its heading - so the plain name recorded names no Point, and the train was left off the
     * railway.  A copy is its facing, so the train goes on the copy that faces the way it did.
     *
     * MUTATION: carry a name only to the same copy under the square's new name, and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainOnASquareSplitSinceIsPutBackFacingItsWay() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = alphaBetaGamma("rsa4-a1-split", 8414);

        org.traincontrol.automationui.TileGraph.TileKey west = new org.traincontrol.automationui.TileGraph.TileKey("main", 2, 1);
        org.traincontrol.automationui.TileGraph.TileKey east = new org.traincontrol.automationui.TileGraph.TileKey("main", 4, 1);

        org.traincontrol.automationui.TileGraph.RouteId straight = new org.traincontrol.automationui.TileGraph.RouteId(0, 0);

        // ONE WAY, EASTWARD, either side of Beta: its one copy, called Beta
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

            // BOTH WAYS AGAIN, from the diagram's setup menu, and the rebuild it makes: Beta is two copies
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
     * Alpha, Beta and Gamma, stations at 1,1, 3,1 and 5,1 of one line, on three sensors from the one given: a session of
     * its own, in a folder of its own.
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
     * A train on a station with no facing - one no track arrives at, with one way out - is put back on it after the
     * station is renamed (RSA4-A1): such a square is one copy that records no facing, so only its name, carried to the
     * square's new name, finds it again.
     *
     * MUTATION: carry a name only to the copy facing the same way, and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainOnAStationWithNoFacingIsPutBackAfterARename() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = alphaBetaGamma("rsa4-a1-nofacing", 8417);

        org.traincontrol.automationui.TileGraph.RouteId straight = new org.traincontrol.automationui.TileGraph.RouteId(0, 0);

        // ONE WAY, EASTWARD, away from Alpha: nothing arrives there, and it has one way out
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

            // THE RENAME, and the rebuild it makes
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
     * A station renamed to the name another station has keeps both trains the last run left on the two (RSA5-A1): the
     * name moves to the renamed square, the other square is called something else, and each train goes back on its own
     * square.
     *
     * The carry took a name the rebuild still gives as the same Point, and the rebuild gives it to the other square: the
     * train recorded there was stood on the renamed station, the train that stood on it was taken off the railway, and
     * the square the first really stands on read free.  A train is carried by the square it stood on and its facing.
     *
     * MUTATION: take a name the rebuild still gives as the same Point, whatever square it is on now, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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

            // THE RUN'S RESULT: one train on each, facing east - on the running layout alone
            assertTrue(running.moveLocomotive(MOVED, "Beta (eastbound)", false), "precondition: " + MOVED + " not stood");
            assertTrue(running.moveLocomotive(STAYER, "Gamma (eastbound)", false), "precondition: " + STAYER + " not stood");

            Map<String, String[]> standing = TrainControlUI.whereTheTrainsAre(running);

            // BETA RENAMED GAMMA, and the rebuild it makes: the name goes to main 3,1, main 5,1 becomes Gamma (2)
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
     * Stations in a row at 1,1, 3,1, 5,1 and on, one per name, on sensors from the one given: a session of its own, in a
     * folder of its own.
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
     * A train the last run left on a station, whose way a direction then takes, is recorded in the setup where it stands
     * and kept there through the fold (Adam, 2026-10-01, in place of RSA5-A2's refusal): the railway cannot stand it on a
     * copy facing another way, so a rebuild left it where the setup last had it - a square it had left - and a fold of
     * that railway took it off the setup altogether.
     *
     * And the rebuild from that setup stands it on no Point rather than on Beta's copy facing east, and the fold keeps it
     * facing west: the build's last fallback stood it on that copy, and the capture then wrote east over its facing.
     *
     * MUTATION: record nothing, and this fails; so does a fold that clears its square, and a build that stands it on a
     * copy facing another way.
     *
     * @throws Exception on a failure to build the fixture
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

            // THE RUN'S RESULT: on Beta, facing west - on the running layout alone
            assertTrue(running.moveLocomotive(MOVED, "Beta (westbound)", false), "precondition: " + MOVED
                + " not stood on Beta (westbound): " + namesOf(running));

            assertTrue(session.getLocomotiveNameAt(beta) == null, "precondition: the setup already has a train on Beta");

            // ONE WAY EAST EITHER SIDE OF BETA: no copy of it faces west - set, not refused
            java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> either = new java.util.LinkedHashSet<>();

            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 2, 1));
            either.add(new org.traincontrol.automationui.TileGraph.TileKey("main", 4, 1));

            session.setDirection(either, org.traincontrol.automationui.TileGraph.Direction.TOWARD_A);

            assertEquals(session.getLocomotiveNameAt(beta), MOVED, "the train the run left on Beta, whose way the change"
                + " took, is not recorded where it stands");

            assertEquals(session.getFacing(beta), org.traincontrol.automationui.TilePorts.Side.W, "the train recorded on"
                + " Beta is not recorded facing west, as it stands");

            // THE RAILWAY REBUILT FROM THE SETUP - which has no copy of Beta facing west - AND FOLDED BACK
            model.parseAuto(session.buildConfiguration());

            Layout rebuilt = model.getAutoLayout();

            for (org.traincontrol.automation.Point point : rebuilt.getPoints())
            {
                assertFalse(point.getCurrentLocomotive() != null && MOVED.equals(point.getCurrentLocomotive().getName()),
                    "the rebuild stood " + MOVED + " on " + point.getName() + ", though no copy of Beta faces west - a"
                    + " copy facing another way: the copy is the facing");
            }

            session.captureWhereTheTrainsStand(rebuilt.toJSON(rebuilt.getLastPointsReached()),
                session.getStore().getActiveConfiguration());

            assertEquals(session.getLocomotiveNameAt(beta), MOVED, "the fold of a railway that cannot stand the train"
                + " took it off the setup");

            assertEquals(session.getFacing(beta), org.traincontrol.automationui.TilePorts.Side.W, "the fold turned the"
                + " train on Beta round in the setup");
        }
        finally
        {
            session.setRunningLayoutSource(null);

            new Layout(model).makeCurrent();
        }
    }

    /**
     * A carry across a load after squares were moved puts each train back on its own station (RSA7-B3): while a declined
     * edit waits the trains are carried by the square the railway says each stands on, and after a move the railway said
     * the square as it was - so each train went on the station that had taken its old square.  A move moves the squares
     * the railway's Points say they are copies of, as it moves the setup's.
     *
     * MUTATION: move the setup's squares and not the railway's, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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

            // TWO COLUMNS INSERTED at the left, as the track editor moves the squares and tells the setup
            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey, org.traincontrol.automationui.TileGraph.TileKey>
                moves = new java.util.LinkedHashMap<>();

            for (int x = 7; x >= 1; x--)
            {
                moves.put(new org.traincontrol.automationui.TileGraph.TileKey("main", x, 1),
                    new org.traincontrol.automationui.TileGraph.TileKey("main", x + 2, 1));
            }

            session.moveTiles(moves);
            session.saveWithoutReconciling();

            // THE SAVE'S LOAD, a declined edit waiting: the trains carried across by where the railway says they stand
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
     * A carry across a load after a page was renamed puts each train back on its own station (RSA7-B3): the railway said
     * each train's square under the page's old name, which no square has, so every train on the page was left where the
     * setup last had it.  A rename moves the squares the railway's Points say they are copies of, as it moves the setup's.
     *
     * MUTATION: rename the setup's page and not the railway's squares, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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

            // THE PAGE RENAMED, as the track diagram's rename makes it
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
     * A move cancelled puts the railway's squares back with the setup's (RSA7-B3): a move moves the squares the railway's
     * Points say they are copies of, and a Cancel that put back the setup alone left the carry reading every train's
     * square as the move had made it, against the pages as they were - each train on the station that had its square.
     *
     * MUTATION: put the setup back and not the railway's squares, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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

            // THE EDITOR OPENED, two columns inserted, and Cancel
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

            // THE RESET'S LOAD, a declined edit waiting, over the pages as they were
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
     * Stations moved together in the track editor keep the timetable's legs through them (RSA8-B2): the carry every
     * build makes kept a name only on the square the build before gave it, and a move had not moved that memory - so
     * each leg was carried to the station now on its old square, one entry dropped and another sent on a journey nobody
     * recorded.  A move moves what the carry remembers with the squares.
     *
     * MUTATION: leave the carry's memory where the squares were, and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAMoveKeepsTheTimetableOnItsStations() throws Exception
    {
        Object[] fixture = fiveStationsWithATimetable("rsa8-b2-save", 8471);

        org.traincontrol.automationui.AutonomySession session = (org.traincontrol.automationui.AutonomySession) fixture[0];
        org.traincontrol.base.LayoutDiagram page = (org.traincontrol.base.LayoutDiagram) fixture[1];
        String before = (String) fixture[2];

        // TWO COLUMNS INSERTED, as the track editor moves the squares and tells the setup
        session.moveTiles(shiftRow(page, 2));

        assertEquals(legsOf(session), before, "a move of stations together carried the timetable's legs to the stations"
            + " now on their old squares (RSA8-B2)");
    }

    /**
     * The same after the track editor's Cancel (RSA8-B2): Cancel put the setup back and rebuilt over what the move had
     * made the carry remember, and carried every leg the other way.
     *
     * MUTATION: put back the setup and not what the carry remembers, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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

        // CANCEL: the setup put back as the editor opened it, rebuilt over the page as the editor left it
        assertTrue(session.restoreSetup(asOpened), "precondition: Cancel could not put the setup back");

        assertEquals(legsOf(session), before, "a move cancelled carried the timetable's legs to other stations (RSA8-B2)");
    }

    /**
     * The same after the track editor's Ctrl+Z (RSA8-B2): the undo put the page's setup back and rebuilt against what the
     * move had made the carry remember.  The undo point holds what the carry remembers and every stored leg, and the undo
     * puts both back.
     *
     * MUTATION: put back the page's setup alone at an undo, and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAnUndoneMoveKeepsTheTimetableOnItsStations() throws Exception
    {
        Object[] fixture = fiveStationsWithATimetable("rsa8-b2-undo", 8491);

        org.traincontrol.automationui.AutonomySession session = (org.traincontrol.automationui.AutonomySession) fixture[0];
        org.traincontrol.base.LayoutDiagram page = (org.traincontrol.base.LayoutDiagram) fixture[1];
        String before = (String) fixture[2];

        // THE UNDO POINT, the move, and Ctrl+Z: the tiles back, then the page's setup
        java.util.Map<String, Object> undoPoint = session.snapshotPage("main");

        session.moveTiles(shiftRow(page, 2));

        shiftRow(page, -2);
        session.restorePage("main", undoPoint);

        assertEquals(legsOf(session), before, "a move undone carried the timetable's legs to other stations (RSA8-B2)");
    }

    /**
     * A leg to a station with no name of its own names it as before once a move is undone (RSA8-B2): the move renamed
     * the names made from its square - "main 5,1" became "main 7,1" - and the undo did not name them back, so the leg
     * named whatever station then stood on the moved square.
     *
     * MUTATION: put back the page's setup alone at an undo, and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testAnUndoneMoveNamesAnUnnamedStationAsBefore() throws Exception
    {
        java.io.File folder = java.nio.file.Files.createTempDirectory("rsa8-b2-unnamed").toFile();

        folder.deleteOnExit();

        org.traincontrol.base.LayoutDiagram page = aRowPage("main", 0, 8501, 5, 13);

        // ALPHA AND BETA NAMED, the three after them not
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
     * A move undone puts the railway's squares back with the page's setup (RSA8-B3): Cancel did, and Ctrl+Z - the other
     * way back - did not, so while a declined edit waited the carry put each train on the station its moved square held.
     *
     * MUTATION: put back the page's setup alone at an undo, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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

            // THE UNDO POINT, the move, and Ctrl+Z
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

    /** The first built edge from a Point whose name begins so, to one whose name begins so. */
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

    /** The configuration in use's timetable, each entry's legs as "start -> end". */
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

    /** Every tile on row 1 moved so many columns, as the track editor's shift moves them in place; the moves. */
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
     * A page ticked out keeps its timetable entries through the railway's load and its fold (Adam, 2026-09-30: "Just
     * keep the entries, and if a path is run that contains a point on a disabled page, reject it with an error"): the
     * railway built without the page holds its entries as it read them, unrunnable, and the fold writes them back, so
     * ticked back in the page's entries run again.
     *
     * MUTATION: drop an entry the railway cannot build at the load, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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
            // OUT, and the railway built without the page
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

            // THE FOLD of that railway
            session.captureFromLayout(running.toJSON(running.getLastPointsReached()));

            assertEquals(legsOf(session), before, "the fold of the railway built without the page changed the timetable");

            // BACK IN
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
     * Start Timetable asks each locomotive to stand where its first entry the railway can run starts: an entry through a
     * page left out has no start on the railway, and asked of it the check refused the whole timetable.
     *
     * MUTATION: ask each locomotive's first entry whatever it is, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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

            // THE FIRST ENTRY'S TRAIN WHERE IT STARTS; the second's train, whose entry cannot run, nowhere
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
     * The editor's Cancel after two stations' names were swapped keeps the timetable on its stations (RSA9-B2): Cancel
     * put back the setup and the snapshot's legs, but put back what the carry of names remembers only where a move had
     * been made - so its rebuild found each name remembered on the other's square and carried every leg there.  What the
     * carry remembers is taken when the editor opens, as its snapshot is.
     *
     * MUTATION: put back what the carry remembers only after a move, and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testACancelAfterTwoNamesSwappedKeepsTheTimetable() throws Exception
    {
        Object[] fixture = fiveStationsWithATimetable("rsa9-b2-swap", 8561);

        org.traincontrol.automationui.AutonomySession session = (org.traincontrol.automationui.AutonomySession) fixture[0];
        String before = (String) fixture[2];

        // THE EDITOR OPENED: its snapshot, and its note
        org.json.JSONObject asOpened = session.snapshotSetup();

        session.beginEditSession();

        try
        {
            // THE NAMES OF BETA AND GAMMA SWAPPED, as three renames make it
            org.traincontrol.automationui.TileGraph.TileKey beta =
                new org.traincontrol.automationui.TileGraph.TileKey("main", 3, 1);
            org.traincontrol.automationui.TileGraph.TileKey gamma =
                new org.traincontrol.automationui.TileGraph.TileKey("main", 5, 1);

            session.setPointName(beta, "Tmp");
            session.setPointName(gamma, "Beta");
            session.setPointName(beta, "Gamma");

            assertFalse(legsOf(session).equals(before), "precondition: the legs did not follow the swapped names");

            // CANCEL
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
     * Start Timetable asks nothing of the entries past the one the run will stop at (RSA10-B2): the run stops at the
     * first entry it cannot run, so a train whose only entry comes after it never sets off - and asked where it stands,
     * the check refused the timetable, or sent the operator to stand a train where the run would never send it from.
     *
     * MUTATION: ask every entry the railway can run, past the stop too, and this fails.
     *
     * @throws Exception on a failure to build the fixture
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
            // A THIRD ENTRY, after the one through the page that will be left out: another train, on the page in play
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

            // THE FIRST ENTRY'S TRAIN WHERE IT STARTS; the third's nowhere - the run stops before it
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

    /** One line of stations, one per sensor from the first given, starting that many columns in: its page. */
    private static org.traincontrol.base.LayoutDiagram aRowPage(String name, int offset, int firstSensor, int count)
        throws Exception
    {
        return aRowPage(name, offset, firstSensor, count, 2 * count + 1 + offset);
    }

    /** The same on a page this many columns wide. */
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

    /** A session over this page in this folder, a configuration of this name, its stations named in order. */
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

    /** The next session over the setup in this folder, on this page, as a reset opens it. */
    private static org.traincontrol.automationui.AutonomySession sessionOver(java.io.File folder,
        org.traincontrol.base.LayoutDiagram page) throws Exception
    {
        org.traincontrol.automationui.AutonomySession next = new org.traincontrol.automationui.AutonomySession(folder);

        next.open(java.util.Arrays.asList(page));

        return next;
    }

    /** Whether the train stands on a Point of that square whose name begins so. */
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
}
