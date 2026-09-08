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
     * nothing captures when a run ends (`captureRunningLayout`: *"Stopping autonomy does not capture;
     * nothing else does either"*). So the setup is stale about exactly the trains the run moved.
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
}
