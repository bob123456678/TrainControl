package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import support.StandInStation;

/**
 * FR-175: a layout read from the Central Station has links whose number is the page's ID.  A page that states no ID is
 * numbered in the order it appears, and two that share one are told apart in memory, by name, the same way at every
 * sync.  There is no real station sample with two links between pages whose IDs and names sort differently: the
 * station here is a stand-in serving the pages of `test/cs3_v261` with an arrow added.
 */
public class testALinkOnAStationGoesByPageId
{
    private support.LayoutSandbox sandbox;
    private MarklinControlStation model;
    private StandInStation station;
    private volatile String index;
    private volatile String startpackung;

    @BeforeMethod
    public void standIn() throws Exception
    {
        sandbox = support.LayoutSandbox.open(new File("test/cs3_v261"));

        model = support.TestStation.simulated();

        File config = new File("test/cs3_v261/config");

        // Startpackung is first in the file, then Main, Something, Main Layout; an arrow on it with the number 2
        startpackung = new String(Files.readAllBytes(new File(config, "gleisbilder/Startpackung.cs2").toPath()),
            StandardCharsets.UTF_8) + "\nelement\n .id=0x101\n .typ=pfeil\n .drehung=1\n .artikel=2\n";

        station = new StandInStation((path, n) ->
        {
            try
            {
                switch (path)
                {
                    case "/config/geraet.vrs": return "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8);
                    case "/config/gleisbild.cs2": return index.getBytes(StandardCharsets.UTF_8);
                    case "/config/fahrstrassen.cs2": return Files.readAllBytes(new File("test/fahrstrassen.cs2").toPath());
                    case "/config/lokomotive.cs2": return Files.readAllBytes(new File("test/lokomotive.cs2").toPath());
                    case "/config/gleisbilder/Startpackung.cs2": return startpackung.getBytes(StandardCharsets.UTF_8);
                    default:
                        if (path.startsWith("/config/"))
                        {
                            File file = new File(config, path.substring("/config/".length()));

                            return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
                        }

                        return null;
                }
            }
            catch (java.io.IOException e)
            {
                throw new IllegalStateException(e);
            }
        });

        // The layout is the station's, not the sandbox folder
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
    {
        StandInStation.tearDown(station, model, sandbox);
    }

    private static String page(int id, String name, boolean withId)
    {
        return "seite\n" + (withId ? " .id=" + id + "\n" : "") + " .name=" + name + "\n";
    }

    private Map<String, String> links() throws Exception
    {
        // Emptied, as switching to the station's layout does, so the sync reads the station's pages
        model.clearLayouts();

        station.sync(model);

        assertTrue(station.requests("/config/gleisbilder/Startpackung.cs2") > 0, "the pages did not come from the station: "
            + station.asked());

        Map<String, String> out = new LinkedHashMap<>();

        for (String name : model.getLayoutList())
        {
            for (LayoutDiagramComponent tile : model.getLayout(name).getAll())
            {
                if (tile.isLink()) out.put(name, tile.getLinkPageName() == null ? "gone" : tile.getLinkPageName());
            }
        }

        return out;
    }

    /**
     * A station page that states no ID is numbered in the order it appears, and a link's number is that ID: the arrow
     * with the number 2 on the first page goes to the second page in the file, not to the third in name order.
     *
     * MUTATION: `CS2File.parseLayoutIndex` gives an absent ID 0, or a station's link is read as a place in the
     * name-sorted list, and the arrow goes to Something.
     */
    @Test
    public void testPagesWithNoIdAreNumberedInOrderAndALinkGoesByThatNumber() throws Exception
    {
        index = "[gleisbild]\nversion\n .major=1\ngroesse\n" + page(0, "Startpackung", false) + page(0, "Main", false)
            + page(0, "Something", false) + page(0, "Main Layout", false);

        assertEquals(links().get("Startpackung"), "Main", "a station link does not go to the page numbered so");
    }

    /**
     * Two station pages that share an ID are told apart in memory: the first by name keeps it, the other takes a fresh
     * one, and a link to the shared ID goes to the keeper, the same at every sync.
     *
     * MUTATION: the dedupe in `parseLayoutIndex` removed, and the link goes to whichever page the lookup finds last.
     */
    @Test
    public void testTwoStationPagesSharingAnIdAreToldApartByName() throws Exception
    {
        // Startpackung and Main share ID 2; Main is first by name
        index = "[gleisbild]\nversion\n .major=1\ngroesse\n" + page(2, "Startpackung", true) + page(2, "Main", true)
            + page(3, "Something", true) + page(4, "Main Layout", true);

        Map<String, String> first = links();

        assertEquals(first.get("Startpackung"), "Main", "the link to the shared ID does not go to the page first by name");

        assertEquals(links(), first, "a second sync resolved the shared ID differently");

        assertEquals(model.getPageIdOf("Main"), 2, "the page first by name lost the shared ID");

        assertTrue(model.getPageIdOf("Startpackung") > 4, "the other page did not take a fresh ID");

        assertNotNull(model.getLayout("Startpackung"));
    }
}
