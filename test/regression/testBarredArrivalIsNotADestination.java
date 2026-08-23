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
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * A station with one arrival side barred does not offer that side as somewhere to stop.
 *
 * MT-078, answered 2026-08-22: "Not always honored. In manual operation, I was able to send a train
 * from Tunnel to BottomMainA. BottomMainA had barred arrivals from the west."
 *
 * The rule lives in the BUILD, not in the search. A square that trains can reach from two sides is
 * emitted as two Points - that is how the model records which way a train is facing, since it knows
 * only which Point a locomotive stands on - and barring a side makes THAT copy a non-station. Every
 * destination list, manual and automatic alike, filters on `isDestination()`, so a copy that is not a
 * station cannot be the end of a path.
 *
 * This pins the build half, which is the half that decides. It is worth having either way: if the copy
 * is a destination, every list downstream is right to offer it and the defect is here; if it is not,
 * then a train that arrived at the station arrived at the OTHER copy, which is legal - and the two
 * copies share a base name, so a report naming the station cannot tell them apart.
 *
 * @author Adam
 */
public class testBarredArrivalIsNotADestination
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-barred").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * Bar one side; that copy stops being a place a train may be sent.
     */
    @Test
    public void testTheBarredCopyIsNotAStation() throws IOException
    {
        TileKey platform = twoEndedStation();

        java.util.List<TilePorts.Side> sides = session.arrivalSides(platform);

        assertEquals(sides.size(), 2,
            "the fixture must be reachable from two sides or this test proves nothing - got " + sides);

        TilePorts.Side barred = sides.get(0);

        session.setBarredArrivals(platform,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(barred)));

        org.json.JSONObject built =
            new org.json.JSONObject(session.buildConfigurationForInspection());

        int stops = 0, doesNot = 0;

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            if (!platform.toString().equals(point.optString("block", null))
                && !String.valueOf(point.optString("name", "")).contains("Bahnsteig")) continue;

            if (point.optBoolean("station", false)) stops++;
            else doesNot++;
        }

        assertTrue(doesNot >= 1,
            "barring one arrival side left every copy of the platform still a station, so the "
            + "restriction changes nothing and a train can still be sent in from the barred side - "
            + "which is what MT-078 reports");

        assertTrue(stops >= 1,
            "barring ONE side left no copy able to stop, so the station has become unreachable "
            + "rather than restricted - the opposite failure, and the worse one");
    }

    /**
     * And un-barring it puts it back, so the setting is a switch rather than a one-way door.
     */
    @Test
    public void testUnbarringRestoresIt() throws IOException
    {
        TileKey platform = twoEndedStation();

        java.util.List<TilePorts.Side> sides = session.arrivalSides(platform);

        TilePorts.Side side = sides.get(0);

        session.setBarredArrivals(platform,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(side)));

        assertTrue(session.getBarredArrivals(platform).contains(side), "the bar did not take");

        session.setBarredArrivals(platform, new java.util.LinkedHashSet<TilePorts.Side>());

        assertFalse(session.getBarredArrivals(platform).contains(side),
            "un-ticking the side left it barred, so the restriction cannot be undone from the menu "
            + "that set it");
    }

    // ------------------------------------------------------------------------------------------

    /**
     * A platform in the middle of a run, so trains can reach it from either end.
     */
    private TileKey twoEndedStation() throws IOException
    {
        session.open(Arrays.asList(runOfTrack()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey platform = new TileKey("main", 2, 1);

        session.setPointName(platform, "Bahnsteig");
        session.getStore().setStation(platform, true);

        return platform;
    }

    private LayoutDiagram runOfTrack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 10, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 2, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 7, 13, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
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
