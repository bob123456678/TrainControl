package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
 * page 0, as on a CS2, unless the index states none, when they are numbered in file order from 0; two that share one
 * are told apart in memory, by name, the same way at every sync.  The station here is a stand-in serving the pages of
 * `test/cs3_v261` with an arrow added, or the structure of a genuine CS2 export.
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
                        String served = path.startsWith("/config/gleisbilder/") && path.endsWith(".cs2")
                            ? genuine.get(path.substring("/config/gleisbilder/".length(), path.length() - 4)) : null;

                        if (served != null) return served.getBytes(StandardCharsets.UTF_8);

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
     * A station index that states no page ID numbers its pages in the order they appear, from 0, and a link's number
     * is that ID: the arrow with the number 1 on the first page goes to the second page in the file, not to the second
     * in name order.
     *
     * MUTATION: `CS2File.parseLayoutIndex` numbers them from 1 (`position` for `position - 1`), and the arrow goes to
     * the page it is on; or a station's link is read as a place in the name-sorted list, and it goes to Main Layout.
     */
    @Test
    public void testPagesWithNoIdAreNumberedInOrderAndALinkGoesByThatNumber() throws Exception
    {
        startpackung = startpackung.replace(" .artikel=2\n", " .artikel=1\n");

        index = "[gleisbild]\nversion\n .major=1\ngroesse\n" + page(0, "Startpackung", false) + page(0, "Main", false)
            + page(0, "Something", false) + page(0, "Main Layout", false);

        assertEquals(links().get("Startpackung"), "Main", "a station link does not go to the page numbered so");

        assertEquals(model.getPageIdOf("Startpackung"), 0, "the first page in the file is not page 0");
    }

    /**
     * The structure of a genuine CS2 export: its first page states no ID, which on a CS2 means 0, and the others 1 to
     * 3; arrows with no number go to page 0, and one with the number 2 to page 2.  Each arrow goes where the CS2 sends
     * it, and every page keeps the ID the CS2 gives it.
     *
     * MUTATION: `parseLayoutIndex` numbers a station page that states no ID by its place in the file (`position`), and
     * the first page loses ID 0, so the arrows to it go nowhere.
     */
    @Test
    public void testAGenuineExportsFirstPageIsPageZero() throws Exception
    {
        servingTheGenuineStructure();

        Map<String, String> expected = new java.util.TreeMap<>();

        for (String at : new String[] {"0 Stations|10,9", "0 Stations|15,10"}) expected.put(at, "2 Yard");

        for (String at : new String[] {"1 Goods|0,3", "1 Goods|14,7", "2 Yard|22,6", "2 Yard|16,7", "2 Yard|6,16"})
        {
            expected.put(at, "0 Stations");
        }

        assertEquals(everyLink(), expected, "the arrows do not go where the CS2 sends them");

        for (int id = 0; id < 4; id++)
        {
            assertEquals(model.getPageIdOf(GENUINE_NAMES[id]), id, "the page " + GENUINE_NAMES[id] + " lost its ID");
        }
    }

    /**
     * B2: a downloaded copy of the station's layout is read as a local layout, and its arrows go exactly where the
     * station's do: for a genuine export, for an index that states no IDs, and for one where two pages share an ID.
     *
     * MUTATION: `downloadCS2Layout` copies the pages as they are (no `.zielseite`, no mark), and on the copy an arrow
     * goes by its place in the name-sorted list.
     */
    @Test
    public void testADownloadedCopysArrowsGoWhereTheStationsDo() throws Exception
    {
        List<String> problems = new ArrayList<>();

        String withArrow = startpackung;

        for (int each = 0; each < 3; each++)
        {
            if (each == 0)
            {
                servingTheGenuineStructure();
            }
            else
            {
                startpackung = withArrow.replace(" .artikel=2\n", " .artikel=" + (each == 1 ? 1 : 2) + "\n");

                index = "[gleisbild]\nversion\n .major=1\ngroesse\n" + (each == 1
                    ? page(0, "Startpackung", false) + page(0, "Main", false) + page(0, "Something", false)
                        + page(0, "Main Layout", false)
                    : page(2, "Startpackung", true) + page(2, "Main", true) + page(3, "Something", true)
                        + page(4, "Main Layout", true));
            }

            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            Map<String, String> onTheStation = everyLink();

            File copy = Files.createTempDirectory("tc-download").toFile();

            try
            {
                String was = support.TestStationAddress.get();

                try
                {
                    support.TestStationAddress.set(station.address());

                    model.downloadLayout(copy);
                }
                catch (Exception afterThePages)
                {
                    // Only the files after the pages may be missing from the stand-in
                }
                finally
                {
                    support.TestStationAddress.set(was);
                }

                TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, copy.getAbsolutePath());

                Map<String, String> onTheCopy = everyLink();

                assertTrue(model.getLayout(model.getLayoutList().get(0)).getUrl().startsWith("file:"),
                    "precondition: the copy was not read from the folder");

                if (!onTheCopy.equals(onTheStation)) problems.add(each + ": the copy " + onTheCopy + ", the station "
                    + onTheStation);

                assertFalse(onTheStation.isEmpty(), "precondition: the station has no arrows");
            }
            finally
            {
                TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

                deleteTree(copy);
            }
        }

        assertEquals(problems.toString(), "[]", "a downloaded copy's arrows go elsewhere than the station's");
    }

    private static final String[] GENUINE_NAMES = {"0 Stations", "1 Goods", "2 Yard", "3 Sensors"};

    /**
     * Serves the pages, IDs and arrows of a genuine CS2 export (the owner's backup), under other names: an index that
     * opens with `zuletztBenutzt`, a first page with no ID, and arrows with no number or the number 2.
     */
    private void servingTheGenuineStructure()
    {
        index = "[gleisbild]\nversion\n .major=1\ngroesse\nzuletztBenutzt\n .name=0 Stations\nseite\n .name=0 Stations\n"
            + " .xoffset=3\n .yoffset=4\nseite\n .id=1\n .name=1 Goods\nseite\n .id=2\n .name=2 Yard\n .xoffset=6\n"
            + " .yoffset=6\nseite\n .id=3\n .name=3 Sensors\n";

        genuine.put("0 Stations", "[gleisbildseite]\nversion\n .major=1\nelement\n .id=0x302\n .typ=prellbock\n .drehung=2\n"
            + " .artikel=-1\nelement\n .id=0x90a\n .typ=pfeil\n .drehung=2\n .artikel=2\nelement\n .id=0xa0f\n .typ=pfeil\n"
            + " .artikel=2\n");
        genuine.put("1 Goods", "[gleisbildseite]\npage=1\nversion\n .major=1\nelement\n .id=0x10300\n .typ=pfeil\n"
            + " .drehung=2\nelement\n .id=0x1070e\n .typ=pfeil\n .drehung=3\n");
        genuine.put("2 Yard", "[gleisbildseite]\npage=2\nversion\n .major=1\nelement\n .id=0x20616\n .typ=pfeil\n"
            + " .drehung=2\nelement\n .id=0x20710\n .typ=pfeil\nelement\n .id=0x21006\n .typ=pfeil\n .drehung=2\n");
        genuine.put("3 Sensors", "[gleisbildseite]\npage=3\nversion\n .major=1\nelement\n .id=0x30101\n .typ=s88kontakt\n"
            + " .artikel=1\n");
    }

    /** The genuine structure's page files by name, served in place of the sample's when set. */
    private final Map<String, String> genuine = new java.util.concurrent.ConcurrentHashMap<>();

    /** Every arrow, "page|x,y" to where it goes, after a sync from wherever the preference says. */
    private Map<String, String> everyLink() throws Exception
    {
        model.clearLayouts();

        station.sync(model);

        Map<String, String> out = new java.util.TreeMap<>();

        for (String name : model.getLayoutList())
        {
            for (LayoutDiagramComponent tile : model.getLayout(name).getAll())
            {
                if (tile.isLink()) out.put(name + "|" + tile.getX() + "," + tile.getY(),
                    tile.getLinkPageName() == null ? "gone" : tile.getLinkPageName());
            }
        }

        return out;
    }

    private static void deleteTree(File file)
    {
        File[] inside = file.listFiles();

        if (inside != null) for (File each : inside) deleteTree(each);

        file.delete();
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
