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
     * A name matching no point is DROPPED as the file is read, rather than kept and asked about.
     *
     * This used to be "a dangling name blocks nothing", which was true but only by accident: the rule
     * resolved the name on every path check and treated "no such point" as "not occupied". A Point
     * holds the points themselves now, so a name that resolves to nothing never becomes a restriction
     * at all - it is reported once, where the file is read, and then does not exist.
     *
     * The behaviour a user sees is the same, and that is deliberate: refusing the path, or the
     * configuration, would take a station out of service because the point it was paired with was
     * renamed. What changed is that the restriction is no longer carried around in a state where it
     * cannot mean anything.
     */
    @Test
    public void testAnUnresolvableRestrictionIsDroppedRatherThanCarried() throws Exception
    {
        Layout layout = built("Somewhere that was deleted");

        assertTrue(layout.getPoint("BK B").getBlockedBy().isEmpty(),
            "a restriction naming a point that does not exist was kept. It can never be satisfied or "
            + "cleared, and it is written back out on every save");

        Locomotive driven = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("BK A").setLocomotive(driven);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertTrue(layout.isPathClear(path, driven, false),
            "and the station is still usable - renaming a point should not quietly retire a platform");
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

        assertEquals(back.getPoint("BK B").getBlockedBy(), Arrays.asList(back.getPoint("BK YARD")),
            "the restriction did not survive the configuration, so it is lost on the next start");

        // The POINT of the reloaded layout, not merely something with the same name: the file holds a
        // name and parseAuto resolves it, so what comes back has to be this layout's own object.
        assertSame(back.getPoint("BK B").getBlockedBy().get(0), back.getPoint("BK YARD"),
            "the restriction came back as something other than the point it names");
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

    /**
     * The train leaving the watched point may still be sent to the station that point holds back.
     *
     * Adam, asked directly: "The condition should not apply to trains leaving - only departing."
     *
     * Without the exemption the one movement that clears the condition is the movement it forbids. A
     * locomotive standing in the yard could never be sent to the platform the yard holds back, and
     * while it sat there the platform was shut to everybody else too - so autonomy had no way out of
     * it at all, only a person driving the train off by hand.
     *
     * That is the same choice Edge.isOccupied makes, for the reason Edge.isLockHeld records: a train
     * parked next to a junction was a permanent roadblock for every route across it, and two of them
     * could deadlock with no way out for either.
     */
    @Test
    public void testATrainLeavingTheWatchedPointMayStillBeSentThere() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        Locomotive leaving = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("BK YARD").setLocomotive(leaving);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK YARD", "BK B"));

        assertTrue(layout.isPathClear(path, leaving, false),
            "the train standing on the watched point was refused the station that point holds back - "
            + "so the only movement that can clear the condition is the one it forbids, and the "
            + "station stays shut to everybody until somebody drives this train off by hand");
    }

    /**
     * And the rule still holds for everybody else, which is the half the exemption could have taken
     * with it: `standing == null || standing.equals(loc)` reduces to `true` if the second clause is
     * ever right about the wrong locomotive.
     */
    @Test
    public void testTheExemptionIsOnlyForTheTrainThatIsLeaving() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        Locomotive driven = model.getLocByName(model.getLocList().get(0));
        Locomotive standing = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("BK A").setLocomotive(driven);
        layout.getPoint("BK YARD").setLocomotive(standing);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK A", "BK B"));

        assertFalse(layout.isPathClear(path, driven, false),
            "somebody ELSE is standing on the watched point and the station was still offered - the "
            + "exemption has swallowed the rule it was carved out of");
    }

    /**
     * The exemption asks the BLOCK, not the square.
     *
     * The yard is reachable from two sides, modelled as two Points sharing one s88 - and a train
     * standing on the twin is standing on the same piece of track. getBlockLocomotive is what makes
     * that true, and it is the subtle half of the rule: testACopyOfTheWatchedSquareCountsAsOccupied
     * pins it for the train being held back, and this pins it for the train being let out.
     */
    @Test
    public void testLeavingTheOtherHalfOfTheWatchedBlockIsAlsoExempt() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        Locomotive leaving = model.getLocByName(model.getLocList().get(0));

        // The TWIN, not the point named in the restriction
        layout.getPoint("BK YARD TWIN").setLocomotive(leaving);

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("BK YARD", "BK B"));

        assertTrue(layout.isPathClear(path, leaving, false),
            "the train is standing on the other Point of the watched block - the same track - and was "
            + "treated as a different train, so it cannot leave the square it is on");
    }

    /**
     * The same run as built(), with a way OUT of the yard.
     *
     * built() has no edge leaving the yard, so the exemption has nothing to be asked about there: a
     * path can only be refused for its destination, and the yard was never a start.
     */
    private Layout builtWithAnApproachFromTheYard() throws Exception
    {
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"BK A\", \"station\": true, \"s88\": 47441},"
            + "  {\"name\": \"BK B\", \"station\": true, \"s88\": 47442,"
            + "   \"blockedBy\": [\"BK YARD\"]},"
            + "  {\"name\": \"BK YARD\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"},"
            + "  {\"name\": \"BK YARD TWIN\", \"station\": true, \"s88\": 47443, \"block\": \"yard\"}"
            + "],"
            + "\"edges\": ["
            + "  {\"start\": \"BK A\", \"end\": \"BK B\", \"length\": 1},"
            + "  {\"start\": \"BK YARD\", \"end\": \"BK B\", \"length\": 1}"
            + "],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        Layout layout = Layout.fromJSON(json, model);

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        layout.runLocomotives();

        return layout;
    }

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

    /**
     * Deleting a point takes it out of every list that was watching it.
     *
     * OB-080. `blockedBy` holds Points, so a deleted station stayed in the lists of the stations it
     * held back - a ghost blocker on a point nobody can see or clear.
     *
     * It fails closed, which is why nothing was reported: a station that will not be chosen is quieter
     * than one chosen wrongly. And it disappears across a save and load, because the list is written by
     * name and the name then resolves to nothing - so the symptom is a railway that behaves differently
     * before and after a restart, which is the hardest kind of fault to report.
     */
    @Test
    public void testDeletingAPointClearsItFromEveryBlockedByList() throws Exception
    {
        Layout layout = builtWithAnApproachFromTheYard();

        org.traincontrol.automation.Point watched = layout.getPoint("BK YARD");
        org.traincontrol.automation.Point station = layout.getPoint("BK B");

        assertTrue(station.getBlockedBy().contains(watched),
            "the fixture does not hold the station back, so nothing below tests anything");

        // BK YARD TWIN shares the block but has no edges, so it can be deleted cleanly
        layout.deletePoint("BK YARD TWIN");

        // and now the watched point itself, once nothing connects to it
        for (Edge e : new java.util.LinkedList<>(layout.getEdges()))
        {
            if (e.getStart().getName().equals("BK YARD") || e.getEnd().getName().equals("BK YARD"))
            {
                layout.deleteEdge(e.getStart().getName(), e.getEnd().getName());
            }
        }

        layout.deletePoint("BK YARD");

        assertTrue(station.getBlockedBy().isEmpty(),
            "the deleted point is still watching this station. Nothing stands on it and nothing can, "
            + "so the rule passes today - but the reference outlives the graph, and it vanishes across "
            + "a save and load, which makes the railway behave differently after a restart: "
            + station.getBlockedBy());
    }
}
