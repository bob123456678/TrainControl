package regression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
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
 * Cancel in the track diagram editor puts back everything the edit took, not most of it.
 *
 * MT-072, answered 2026-08-22: "stations stay, locomotives are removed and no longer shown in the
 * labels" - and the 18 August answer to the same test said the same thing about labels.
 *
 * Cancel is two undos that have to agree. The DIAGRAM is undone by re-reading the pages from disk;
 * the SETUP is undone by restoring a snapshot taken when the window opened, because every gesture
 * that moves track writes the setup as it goes and there is nothing else to undo it with.
 *
 * The two halves are stored differently, and that is what makes this worth a test rather than a read:
 * a station lives in the shared half, in setup.json, and a locomotive PLACEMENT lives inside a
 * configuration file, under that Point. A snapshot that covers one and not the other looks completely
 * correct until somebody deletes a square that has both on it.
 *
 * This drives the session directly - the same forgetTiles the editor calls when a square is deleted -
 * so it needs no display and no railway.
 *
 * @author Adam
 */
public class testCancelRestoresPlacements
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-cancel-restore").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * The whole of MT-072: delete a square that carries settings, cancel, and get all of it back.
     */
    @Test
    public void testCancelPutsBackTheStationAndTheLocomotive() throws IOException
    {
        TileKey sensor = furnished();

        // What the window remembers when it opens, and what Cancel restores
        org.json.JSONObject asOpened = session.snapshotSetup();

        // The editor deleting that square
        session.forgetTiles(Collections.singletonList(sensor));

        assertNull(session.getLocomotiveNameAt(sensor),
            "the delete did not take the placement, so this test is not exercising the case");

        // Cancel
        assertTrue(session.restoreSetup(asOpened), "the restore did not reach the file");

        assertTrue(session.getStore().isStation(sensor),
            "the station did not come back - this half has always worked, so a failure here means "
            + "the restore did not run at all");

        assertEquals(session.getLocomotiveNameAt(sensor), "BR 218",
            "the STATION came back and the LOCOMOTIVE did not, which is exactly what MT-072 "
            + "reports: the two halves of a square are stored in different files and only one of "
            + "them is being put back");

        assertEquals(session.getStore().getPointName(sensor), "Bahnhof",
            "the name did not come back");
    }

    /**
     * And what Cancel restores survives a reload, which is the half the 18 August answer checked.
     *
     * "Confirmed the labels stay gone after reload" - so an in-memory restore that never reaches the
     * configuration file would pass the test above and fail the railway.
     */
    @Test
    public void testTheRestoreReachesTheFiles() throws IOException
    {
        TileKey sensor = furnished();

        org.json.JSONObject asOpened = session.snapshotSetup();

        session.forgetTiles(Collections.singletonList(sensor));

        session.restoreSetup(asOpened);

        // A second session over the same folder reads what is actually on disk
        AutonomySession reloaded = new AutonomySession(layout);

        reloaded.open(Arrays.asList(pageOnDisk()));

        assertTrue(reloaded.getStore().isStation(sensor), "the station is not in the files");

        assertEquals(reloaded.getLocomotiveNameAt(sensor), "BR 218",
            "the placement is not in the files, so it comes back until the next reload and then "
            + "goes - which is what was reported on 18 August");
    }

    // ------------------------------------------------------------------------------------------

    /**
     * A sensor square with everything on it a square can carry: a name, a station, a locomotive.
     */
    private TileKey furnished() throws IOException
    {
        session.open(Arrays.asList(pageOnDisk()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Bahnhof");
        session.getStore().setStation(sensor, true);
        session.placeLocomotive(sensor, "BR 218");

        assertEquals(session.getLocomotiveNameAt(sensor), "BR 218", "the fixture did not take");

        return sensor;
    }

    private LayoutDiagram pageOnDisk() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4, null, null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

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
