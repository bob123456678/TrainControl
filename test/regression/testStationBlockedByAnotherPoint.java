package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.LinkedList;
import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * A station can be held back while another point is in use.
 *
 * FR-001, Adam: "similar to excluding locomotives, we should be able to exclude the autonomous
 * selection of a station when another (specified) point is occupied.  This is similar to how explicit
 * lock edges worked."
 *
 * And then, on how to build it: "add a lock edge ending with the requested S88 to be excluded.  that
 * will allow you to mostly reuse the existing model."
 *
 * So there is no new rule in the running model and nothing new in the running layout's vocabulary. The
 * setting is remembered against SQUARES in the setup, and the build turns it into LOCK EDGES: every
 * edge arriving at the held-back station gains, as a lock edge, every edge that ends at the square
 * being watched. From there the machinery that has always refused a path whose lock edges are held does
 * the work.
 *
 * **What that means, exactly**, because it is not word for word the request. `Edge.isLockHeld` asks
 * whether the track is HELD BY A ROUTE, not whether a train is standing at the sensor beyond it - and
 * its javadoc explains why that is deliberate: counting a parked train made a locomotive beside a
 * junction a permanent roadblock, and two of them could deadlock with no way out. So the station is
 * unavailable while something is running over the watched approach, and available again once that
 * train has arrived and its path has been released.
 *
 * This tests the BUILD, which is where the whole feature lives. The refusal itself is the existing lock
 * behaviour, covered by the path tests that have always covered it.
 *
 * @author Adam
 */
public class testStationBlockedByAnotherPoint
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-blocked").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * The edge into the held-back station gains a lock edge that ends at the watched square.
     */
    @Test
    public void testTheStationsApproachIsLockedAgainstTheWatchedPoint() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        org.json.JSONArray edges = built.getJSONArray("edges");

        boolean lockedAgainstTheYard = false;
        int intoThePlatform = 0;

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            if (!"Bahnsteig".equals(edge.optString("end", null))) continue;

            intoThePlatform++;

            org.json.JSONArray locks = edge.optJSONArray("lockedges");

            if (locks == null) continue;

            for (int at = 0; at < locks.length(); at++)
            {
                if ("Abstellgleis".equals(locks.getJSONObject(at).optString("end", null)))
                {
                    lockedAgainstTheYard = true;
                }
            }
        }

        assertTrue(intoThePlatform > 0,
            "no edge arrives at the platform, so nothing below tests anything");

        // And the same setting reaches the POINT as a name, which is the standing-train half
        org.json.JSONArray points = built.getJSONArray("points");

        boolean namedOnThePoint = false;

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            if (!"Bahnsteig".equals(point.optString("name", null))) continue;

            org.json.JSONArray watching = point.optJSONArray("blockedBy");

            if (watching == null) continue;

            for (int at = 0; at < watching.length(); at++)
            {
                if ("Abstellgleis".equals(watching.getString(at))) namedOnThePoint = true;
            }
        }

        assertTrue(namedOnThePoint,
            "the station carries no blockedBy naming the yard, so nothing holds it back while a train "
            + "is merely STANDING there - which is the half lock edges cannot express (FR-001)");

        assertTrue(lockedAgainstTheYard,
            "the approach to a station held back by the yard carries no lock edge ending at the yard, "
            + "so nothing stops autonomy choosing that station while the yard is in use (FR-001)");
    }

    /**
     * And with nothing set, nothing is added - the restriction costs nothing until it is asked for.
     */
    @Test
    public void testNoRestrictionAddsNoLocks() throws IOException
    {
        open();

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            if (!"Bahnsteig".equals(edge.optString("end", null))) continue;

            org.json.JSONArray locks = edge.optJSONArray("lockedges");

            if (locks == null) continue;

            for (int at = 0; at < locks.length(); at++)
            {
                assertNotEquals(locks.getJSONObject(at).optString("end", null), "Abstellgleis",
                    "a lock against the yard was emitted although nothing asked for one");
            }
        }
    }

    /**
     * A station never watches itself, which would make it a station nothing can be sent to.
     */
    @Test
    public void testAStationDoesNotWatchItself() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(platform));

        assertTrue(session.getStore().getBlockingPoints(platform).isEmpty(),
            "a station was recorded as held back by itself. Standing there already decides whether it "
            + "is free, so this makes it a station nothing can ever be sent to");
    }

    /**
     * The setting survives being written out and read back, which is what makes it a setting.
     */
    @Test
    public void testTheRestrictionSurvivesTheFile() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));
        session.save();

        AutonomySession reopened = new AutonomySession(layout);

        reopened.open(Arrays.asList(page()));

        assertEquals(reopened.getStore().getBlockingPoints(platform), Arrays.asList(yard),
            "the restriction did not survive the file, so it is lost on the next start");
    }

    /**
     * Deleting the watched square takes the restriction with it, rather than leaving one that can
     * never be satisfied and that the next tile drawn there would inherit.
     */
    @Test
    public void testDeletingTheWatchedSquareDropsTheRestriction() throws IOException
    {
        TileKey platform = new TileKey("main", 3, 1);
        TileKey yard = new TileKey("main", 3, 3);

        open();

        session.getStore().setBlockingPoints(platform, Arrays.asList(yard));

        session.getStore().reconcile(new java.util.LinkedHashSet<>(Arrays.asList(platform)));

        assertTrue(session.getStore().getBlockingPoints(platform).isEmpty(),
            "a restriction watching a square that no longer exists survived. It can never be "
            + "satisfied, and whatever is drawn at those coordinates next inherits it");
    }

    // ------------------------------------------------------------------------------------------

    private void open() throws IOException
    {
        session.open(Arrays.asList(page()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        session.setStation(new TileKey("main", 1, 1), true);
        session.setPointName(new TileKey("main", 1, 1), "Einfahrt");

        session.setStation(new TileKey("main", 3, 1), true);
        session.setPointName(new TileKey("main", 3, 1), "Bahnsteig");

        session.setStation(new TileKey("main", 3, 3), true);
        session.setPointName(new TileKey("main", 3, 3), "Abstellgleis");
    }

    /**
     * A run into a platform, and a yard reached from the same throat.
     */
    private LayoutDiagram page() throws IOException
    {
        // Sensors side by side, which is how the other fixtures in this suite build a run: a straight
        // needs an orientation to join anything, and adjacent feedbacks connect on their own.
        LayoutDiagram diagram = new LayoutDiagram("main", 10, 6, null, null);

        diagram.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 2, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        // A separate run for the yard.  It does not have to reach the platform: what the restriction
        // needs is an edge ENDING at the watched square, which is what gets locked against.
        diagram.addComponent(componentType.FEEDBACK, 1, 3, 0, 0, 8, 14, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 2, 3, 0, 0, 9, 15, accessoryDecoderType.MM2, null);
        diagram.addComponent(componentType.FEEDBACK, 3, 3, 0, 0, 10, 16, accessoryDecoderType.MM2, null);

        diagram.setPageId("1");

        return diagram;
    }

    /**
     * A train STANDING on the watched point takes the station out of autonomy's choices.
     *
     * FR-001's other half. The build emits this setting twice over, and the two answer different
     * questions on purpose: as LOCK EDGES, which hold the station back while a route is running over
     * the watched approach, and as the names below, which hold it back while a train is standing there.
     * `Edge.isLockHeld` cannot do the second - it asks about a reservation, deliberately, because
     * counting a parked train made a locomotive beside a junction a permanent roadblock and two could
     * deadlock. Adam asked for both.
     *
     * That reasoning does not carry over to this rule, which is why it is safe to ask the harder
     * question here: a lock edge is track a route must CROSS, and this is asked only of a path's
     * DESTINATION and only about squares somebody named. Nothing here can hold up a route that was not
     * going to that station anyway.
     */
    @Test
    public void testATrainStandingOnTheWatchedPointHoldsTheStationBack() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertTrue(layout.isPathClear(path, driven, false),
            "the path is refused before anything is standing anywhere, so nothing below tests anything");

        layout.getPoint("BK YARD").setLocomotive(other);

        assertFalse(layout.isPathClear(path, driven, false),
            "a train is standing on the point this station is held back by, and autonomy would still "
            + "send another one to the station (FR-001)");

        layout.getPoint("BK YARD").setLocomotive(null);

        assertTrue(layout.isPathClear(path, driven, false),
            "the station did not come back when the watched point cleared, so the restriction is a "
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
    public void testACopyOfTheWatchedSquareCountsAsOccupied() throws Exception
    {
        Layout layout = built();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive other = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);
        layout.getPoint("BK YARD TWIN").setLocomotive(other);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertFalse(layout.isPathClear(path, driven, false),
            "a train on the OTHER copy of the watched square did not count. The two are one piece of "
            + "track, so asking only the copy that carries the name answers clear with a train "
            + "standing there");
    }

    /**
     * And it survives being written out and read back, which is what makes it a setting.
     */
    @Test
    public void testTheRestrictionSurvivesTheConfiguration() throws Exception
    {
        Layout layout = built();

        Layout back = Layout.fromJSON(layout.toJSON(), model);

        assertNotNull(back, "the configuration did not parse: " + Layout.getLastError());

        assertEquals(back.getPoint("BK B").getBlockedBy(), Arrays.asList("BK YARD"),
            "the restriction did not survive the configuration, so it is lost on the next start");
    }

    /**
     * A point never watches itself, which would make the station impossible rather than restricted.
     */
    @Test
    public void testAPointDoesNotWatchItself() throws Exception
    {
        Layout layout = built("BK B");

        assertTrue(layout.getPoint("BK B").getBlockedBy().isEmpty(),
            "a station was recorded as held back by itself. Standing there already decides whether it "
            + "is free, so this makes it a station nothing can ever be sent to");
    }

    // ------------------------------------------------------------------------------------------

    private Layout built() throws Exception
    {
        return built("BK YARD");
    }

    /**
     * A run from A to B, with a yard that B is held back by.
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

    private static MarklinControlStation model;

    @org.testng.annotations.BeforeClass
    public static void setUpModel() throws Exception
    {
        model = MarklinControlStation.init(null, true, false, false, true);

        for (int address : new int[]{47441, 47442, 47443})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }
    }

    @org.testng.annotations.AfterClass
    public static void tearDownModel()
    {
        if (model != null) model.stop();
    }

    private void delete(File f)
    {
        if (f.isDirectory())
        {
            File[] kids = f.listFiles();

            if (kids != null) for (File kid : kids) delete(kid);
        }

        f.delete();
    }
}
