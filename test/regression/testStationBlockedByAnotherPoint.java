package regression;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A station can be made unavailable to autonomy while another point is occupied.
 *
 * FR-001, Adam: "similar to excluding locomotives, we should be able to exclude the autonomous
 * selection of a station when another (specified) point is occupied.  This is similar to how explicit
 * lock edges worked."
 *
 * It is NOT a lock edge, and the difference is the design. A lock edge reserves TRACK: it is held for
 * the length of a path so that two routes cannot take one throat at once. This is about CHOOSING - it
 * stops autonomy picking a station to send a train to while a point somebody named is claimed. Nothing
 * is reserved, nothing is held, and a train already on its way is not turned round.
 *
 * Fenced behind auto running, like the other endpoint rules: a person dispatching by hand is looking at
 * the railway and has said what they want. That is the tiering the arrival restrictions follow, and the
 * one Adam settled in MT-078.
 *
 * @author Adam
 */
public class testStationBlockedByAnotherPoint
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);

        for (int address : new int[]{47441, 47442, 47443})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (model != null) model.stop();
    }

    /**
     * The rule itself: occupied blocker, no path; clear blocker, path.
     *
     * Both halves, because a rule that only ever refuses is indistinguishable from a path that never
     * worked - which is exactly how a restriction that retired a station for good would look.
     */
    @Test
    public void testAnOccupiedPointTakesItsStationOutOfAutonomysChoices() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertTrue(layout.isPathClear(path, driven, false),
            "the path is refused before anything blocks it, so nothing below tests anything");

        layout.getPoint("BK YARD").setLocomotive(other);

        assertFalse(layout.isPathClear(path, driven, false),
            "autonomy would still send a train to a station marked unavailable while another point "
            + "is occupied (FR-001)");

        layout.getPoint("BK YARD").setLocomotive(null);

        assertTrue(layout.isPathClear(path, driven, false),
            "the station did not come back when the blocking point cleared, so the restriction is a "
            + "one-way door rather than a condition");
    }

    /**
     * A hand-driven route is not affected, which is the tiering the arrival restrictions use.
     */
    @Test
    public void testAPersonMayStillSendATrainThere() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);
        layout.getPoint("BK YARD").setLocomotive(other);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        // Not auto running is what a hand dispatch looks like to this rule
        layout.stopLocomotives();

        assertTrue(layout.isPathClear(path, driven, false),
            "a route chosen by hand was refused by a restriction that exists to shape what AUTONOMY "
            + "chooses. A person looking at the railway has said what they want (FR-001)");
    }

    /**
     * A name matching no point blocks nothing, rather than taking the station out of service.
     */
    @Test
    public void testADanglingNameBlocksNothing() throws Exception
    {
        Layout layout = built("Somewhere that was deleted");

        Locomotive driven = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertTrue(layout.isPathClear(path, driven, false),
            "a station paired with a point that no longer exists is out of service for good. Refusing "
            + "is the worse answer: renaming a point should not quietly retire a platform");
    }

    /**
     * The whole BLOCK is asked, not only the copy carrying the name.
     */
    @Test
    public void testACopyOfTheBlockingSquareCountsAsOccupied() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);
        layout.getPoint("BK YARD TWIN").setLocomotive(other);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertFalse(layout.isPathClear(path, driven, false),
            "a train on the OTHER copy of the blocking square did not count. The two are one piece of "
            + "track, so asking only the copy that carries the name answers clear with a train "
            + "standing there");
    }

    /**
     * And it survives being written out and read back, which is what makes it a setting.
     */
    @Test
    public void testTheRestrictionSurvivesTheFile() throws Exception
    {
        Layout layout = built();

        Layout back = Layout.fromJSON(layout.toJSON(), model);

        assertNotNull(back, "the configuration did not parse: " + Layout.getLastError());

        assertEquals(back.getPoint("BK B").getBlockedBy(), Arrays.asList("BK YARD"),
            "the restriction did not survive the file, so it is lost on the next start");
    }

    /**
     * A point never blocks itself, which would make the station impossible rather than restricted.
     */
    @Test
    public void testAPointDoesNotBlockItself() throws Exception
    {
        Layout layout = built("BK B");

        assertTrue(layout.getPoint("BK B").getBlockedBy().isEmpty(),
            "a station was recorded as blocked by itself. Standing there already decides whether it "
            + "is free, so this makes it a station nothing can ever be sent to");
    }

    // ------------------------------------------------------------------------------------------

    private Layout built() throws Exception
    {
        return built("BK YARD");
    }

    /**
     * A run from A to B, with a yard that B is unavailable while it is occupied.
     *
     * The yard is two Points sharing a block, which is how a square reachable from two sides is
     * modelled - so the block half of the rule has something to be asked about.
     *
     * runLocomotives sets the auto-running flag and starts nothing here: it iterates the run list, and
     * this layout has none.
     */
    private Layout built(String blocker) throws Exception
    {
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"BK A\", \"station\": true, \"s88\": 47441},"
            + "  {\"name\": \"BK B\", \"station\": true, \"s88\": 47442,"
            + "   \"blockedBy\": [\"" + blocker + "\"]},"
            + "  {\"name\": \"BK YARD\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"},"
            + "  {\"name\": \"BK YARD TWIN\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"}"
            + "],"
            + "\"edges\": [{\"start\": \"BK A\", \"end\": \"BK B\", \"length\": 1}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        Layout layout = Layout.fromJSON(json, model);

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        layout.runLocomotives();

        return layout;
    }
}
