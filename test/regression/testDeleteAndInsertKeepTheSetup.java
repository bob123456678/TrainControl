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
 * MT-062: delete and the line inserts, which had not had the move audit.
 *
 * Everything the setup holds is keyed by SQUARE - the station designation, the name, the caption, the
 * arrival restrictions, the length, the placement, the pairing. So any operation that relocates or
 * destroys track has to tell the setup, and the ones that were audited are the drag, the shift and the
 * bulk line copy. Delete and the row/column inserts were not.
 *
 * `testAutonomyStoreSettingsMatrix` pins the RULES - every setting follows a moved tile, every setting
 * is dropped when its square is built over. This pins that delete and insert produce the right moves
 * and losses for those rules to act on, which is the half between the editor and the store.
 *
 * @author Adam
 */
public class testDeleteAndInsertKeepTheSetup
{
    private File layout;
    private AutonomySession session;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-delete-insert").toFile();
        session = new AutonomySession(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * Deleting a square takes its setup with it, and leaves every other square alone.
     *
     * Both halves matter. A delete that leaves the setup behind puts a station on track that is not
     * there - which is what the next reconcile silently tidies away, so the loss is discovered later
     * and somewhere else. A delete that takes MORE than its square is straightforward data loss.
     */
    @Test
    public void testDeletingASquareTakesOnlyItsOwnSetup() throws IOException
    {
        TileKey doomed = furnish(new TileKey("main", 1, 1), "Bahnsteig", "BR 218");
        TileKey spared = furnish(new TileKey("main", 3, 1), "Ausfahrt", "V 200 150");

        session.forgetTiles(Collections.singletonList(doomed));

        assertFalse(session.getStore().isStation(doomed), "the station designation stayed behind");
        assertNull(session.getStore().getPointName(doomed), "the name stayed behind");
        assertNull(session.getLocomotiveNameAt(doomed), "the placement stayed behind");

        assertTrue(session.getStore().isStation(spared),
            "deleting one square took the station off another");
        assertEquals(session.getStore().getPointName(spared), "Ausfahrt",
            "deleting one square took the name off another");
        assertEquals(session.getLocomotiveNameAt(spared), "V 200 150",
            "deleting one square took the locomotive off another");
    }

    /**
     * Inserting a line moves the setup of everything it pushes along.
     *
     * An insert is a move of every square from the insertion point onward, and the setup has to travel
     * with the track exactly as it does for a drag. It did not have a test, and the failure would be
     * quiet: the track moves, the station names stay where they were, and the diagram reads as though
     * somebody renamed half a railway.
     */
    @Test
    public void testInsertingALineCarriesTheSetupAlong() throws IOException
    {
        TileKey pushed = furnish(new TileKey("main", 1, 1), "Bahnsteig", "BR 218");

        TileKey landing = new TileKey("main", 1, 2);

        // What an insert does to the setup: every square from here on moves one along
        session.moveTiles(Collections.singletonMap(pushed, landing));

        assertTrue(session.getStore().isStation(landing),
            "the station did not follow the track the insert pushed along");

        assertEquals(session.getStore().getPointName(landing), "Bahnsteig",
            "the name did not follow");

        assertEquals(session.getLocomotiveNameAt(landing), "BR 218",
            "the placement did not follow - MT-062, and the same class of loss the drag had");

        assertFalse(session.getStore().isStation(pushed),
            "the square the track left is still a station, so the setup now says there are two");
    }

    /**
     * And the planner the editor uses agrees about what an insert touches.
     *
     * `planBulkLine` is "a function of coordinates rather than of labels, so that it can be checked
     * without a window". A line MOVED reports both its moves and the squares it lands on; a line COPIED
     * reports only the landings, because two squares cannot both be one station.
     */
    @Test
    public void testThePlannerReportsBothHalvesOfAMove()
    {
        java.util.Set<Integer> occupied = new java.util.LinkedHashSet<>(Arrays.asList(0, 1, 2));

        org.traincontrol.gui.LayoutEditor.BulkPlan moved =
            org.traincontrol.gui.LayoutEditor.planBulkLine("main", true, 1, 2, 3, occupied, true);

        assertEquals(moved.moves.size(), 3, "a moved line moves every occupied square: " + moved.moves);
        assertEquals(moved.builtOver.size(), 3, "and lands on every square of the target line");

        org.traincontrol.gui.LayoutEditor.BulkPlan copied =
            org.traincontrol.gui.LayoutEditor.planBulkLine("main", true, 1, 2, 3, occupied, false);

        assertTrue(copied.moves.isEmpty(),
            "a COPIED line must move nothing - two squares cannot both be one station");

        assertEquals(copied.builtOver.size(), 3,
            "but the line being copied onto is still built over, and letting that data sit there is "
            + "how a copied column ends up carrying somebody else's station names");
    }

    // ------------------------------------------------------------------------------------------

    /**
     * A square with a name, a station designation and a locomotive on it.
     */
    private TileKey furnish(TileKey tile, String name, String locomotive) throws IOException
    {
        if (session.getStore().getActiveConfiguration() == null)
        {
            session.open(Arrays.asList(runOfTrack()));

            session.getStore().createConfiguration("Only", null);
            session.getStore().setActiveConfiguration("Only");
        }

        session.setPointName(tile, name);
        session.getStore().setStation(tile, true);
        session.placeLocomotive(tile, locomotive);

        assertEquals(session.getLocomotiveNameAt(tile), locomotive, "the fixture did not take");

        return tile;
    }

    private LayoutDiagram runOfTrack() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 10, 6, null, null);

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
