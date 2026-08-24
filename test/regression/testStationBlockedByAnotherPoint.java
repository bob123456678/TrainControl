package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
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
