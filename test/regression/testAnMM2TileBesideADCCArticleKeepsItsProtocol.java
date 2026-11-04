package regression;

import java.io.File;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import support.StandInStation;

/**
 * An MM2 tile in a local copy of a CS3 layout keeps MM2 after a save, beside a DCC article at its address: one read
 * from the file, one drawn new and one re-addressed in the editor, one saved again while not on a CS3, and one copied
 * as the editor copies.  Every accessory tile writes its protocol, MM2 included, on any station (OB-402).
 *
 * The fixture `test/cs3_v261` is a CS3 plus (2.6.1) export: W 14 (DCC) and W 15 (MM2) are both address 25, W 11 (DCC)
 * and W 1R (MM2) both address 1, and the pages name only the address, so every tile at 25 and 1 reads as DCC.  The
 * editor's remedy is the tile's protocol, saved as `.prot`, MM2 included.
 *
 * MUTATION: write `.prot` only for a protocol other than MM2 (LayoutDiagramComponent.exportToCS2TextFormat), and
 * every MM2 tile at an address with two protocols reads back as DCC.
 *
 * @author Adam
 */
public class testAnMM2TileBesideADCCArticleKeepsItsProtocol
{
    // A copy of the fixture to save into; the sandbox also points the layout preference at it, which nothing here reads
    private static support.LayoutSandbox sandbox;

    // The control the parser asks whether the station is a CS3
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(new File("test/cs3_v261"));
        model = support.TestStation.simulated();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        StandInStation.tearDown(null, model, sandbox);
    }

    /** The sandbox's pages, read as a local copy is read, on a CS3 or not. */
    private static List<LayoutDiagram> read(boolean cs3) throws Exception
    {
        return read(cs3, sandbox.getFolder());
    }

    /** A folder's pages, read as a local copy is read, on a CS3 or not. */
    private static List<LayoutDiagram> read(boolean cs3, File folder) throws Exception
    {
        StandInStation.setCS3(model, cs3);
        CS2File parser = new CS2File("0.0.0.0", model);
        parser.setLayoutDataLoc("file:///" + folder.getAbsolutePath() + "/");
        return parser.parseLayout(parser.getMagList(true));
    }

    private static LayoutDiagram page(List<LayoutDiagram> pages, String name)
    {
        for (LayoutDiagram d : pages)
        {
            if (d.getName().equals(name))
            {
                return d;
            }
        }

        throw new AssertionError("no page " + name);
    }

    /**
     * W 15 at Main Layout (5,9) and W 1R at Startpackung (10,4), set to MM2 and saved, read back as MM2; the DCC
     * articles at their addresses and an ordinary MM2 turnout are unchanged.
     */
    @Test
    public void testTheMM2TilesReadBackAsMM2() throws Exception
    {
        List<LayoutDiagram> pages = read(true);

        assertEquals(page(pages, "Main Layout").getComponent(5, 9).getProtocol(), accessoryDecoderType.DCC,
            "the fixture's premise: W 15's tile reads as the DCC article's protocol");

        page(pages, "Main Layout").getComponent(5, 9).setProtocol(accessoryDecoderType.MM2);
        page(pages, "Main Layout").saveChanges(null, false);
        page(pages, "Startpackung").getComponent(10, 4).setProtocol(accessoryDecoderType.MM2);
        page(pages, "Startpackung").saveChanges(null, false);

        List<LayoutDiagram> again = read(true);

        assertEquals(page(again, "Main Layout").getComponent(5, 9).getProtocol(), accessoryDecoderType.MM2, "W 15");
        assertEquals(page(again, "Startpackung").getComponent(10, 4).getProtocol(), accessoryDecoderType.MM2, "W 1R");
        assertEquals(page(again, "Main Layout").getComponent(4, 7).getProtocol(), accessoryDecoderType.DCC, "W 14");
        assertEquals(page(again, "Main Layout").getComponent(16, 6).getProtocol(), accessoryDecoderType.DCC, "W 11");
        assertEquals(page(again, "Startpackung").getComponent(9, 8).getProtocol(), accessoryDecoderType.MM2, "W 2");
    }

    /**
     * A tile drawn new at 25, and W 2's tile re-addressed to 1, both MM2 as the editor's address popup sets them, read
     * back as MM2.
     */
    @Test
    public void testATileDrawnOrReaddressedInTheEditorReadsBackAsMM2() throws Exception
    {
        LayoutDiagram main = page(read(true), "Main Layout");
        int x = 0;

        while (main.getComponent(x, 0) != null)
        {
            x++;
        }

        // As the editor's palette makes a tile (LayoutEditor: protocol MM2), then the address popup sets 25, MM2
        main.addComponent(LayoutDiagramComponent.componentType.SWITCH_RIGHT, x, 0, 0, 0, 1, 2, accessoryDecoderType.MM2,
            null);
        main.getComponent(x, 0).setLogicalAddress(25, accessoryDecoderType.MM2, false);
        main.saveChanges(null, false);

        LayoutDiagram start = page(read(true), "Startpackung");
        start.getComponent(9, 8).setLogicalAddress(1, accessoryDecoderType.MM2, false);
        start.saveChanges(null, false);

        List<LayoutDiagram> again = read(true);

        assertEquals(page(again, "Main Layout").getComponent(x, 0).getProtocol(), accessoryDecoderType.MM2,
            "the new tile at 25");
        assertEquals(page(again, "Startpackung").getComponent(9, 8).getProtocol(), accessoryDecoderType.MM2,
            "W 2's tile, now at 1");
    }

    /**
     * The second square of the W 11 slip (Main Layout (17,6), address 1), set to MM2 on a CS3 and saved, then read and
     * saved while TrainControl does not take its station for a CS3 (it was not reached at start-up, or runs
     * simulated), keeps MM2.
     */
    @Test
    public void testAFixedTileSurvivesASaveOffline() throws Exception
    {
        fixTheSlipsSecondSquare();
        page(read(false), "Main Layout").saveChanges(null, false);

        assertEquals(page(read(true), "Main Layout").getComponent(17, 6).getProtocol(), accessoryDecoderType.MM2,
            "(17,6) after a save made while not on a CS3");
    }

    /** Sets (17,6) on Main Layout, the W 11 slip's second square at address 1, to MM2 on a CS3, and saves. */
    private static void fixTheSlipsSecondSquare() throws Exception
    {
        LayoutDiagram main = page(read(true), "Main Layout");
        main.getComponent(17, 6).setProtocol(accessoryDecoderType.MM2);
        main.saveChanges(null, false);
    }

    /**
     * The same square, fixed, copied as the editor copies a tile for an undo state, a move or a paste (all through the
     * copy constructor), and saved while not on a CS3, keeps MM2.
     */
    @Test
    public void testACopiedFixedTileSurvivesAnOfflineSave() throws Exception
    {
        fixTheSlipsSecondSquare();
        LayoutDiagram offline = page(read(false), "Main Layout");
        offline.addComponent(new LayoutDiagramComponent(offline.getComponent(17, 6)), 17, 6);
        offline.saveChanges(null, false);

        assertEquals(page(read(true), "Main Layout").getComponent(17, 6).getProtocol(), accessoryDecoderType.MM2,
            "the copy of (17,6) keeps the fix");
    }

    /**
     * On a CS2, read from the CS2 fixture `test/layout` and from the sandbox's pages: every addressed switch, signal and
     * uncoupler tile writes `.prot`, MM2 included; every other tile writes `.prot` only for a protocol other
     * than MM2, so track writes none; and the saved pages read back with every tile's protocol as before.
     *
     * MUTATION: write `.prot=MM2` on every tile (drop the accessory test from the `else if`), and a track tile writes it.
     */
    @Test
    public void testACS2SaveWritesEveryAccessorysProtocol() throws Exception
    {
        CS2File parser = new CS2File("0.0.0.0", model);
        parser.setLayoutDataLoc(getClass().getResource("/layout").toURI().toString());
        StandInStation.setCS3(model, false);
        List<LayoutDiagram> pages = parser.parseLayout(parser.getMagList(true));
        pages.addAll(read(false));
        java.util.Map<String, accessoryDecoderType> before = new java.util.HashMap<>();
        int mm2Accessories = 0;

        for (LayoutDiagram d : pages)
        {
            for (LayoutDiagramComponent c : d.getAll())
            {
                boolean accessory = c.getAddress() > 0 && (c.isSwitch() || c.isSignal() || c.isUncoupler());
                String where = d.getName() + " " + c.getX() + "," + c.getY() + " " + c.getType() + ": ";

                assertEquals(c.exportToCS2TextFormat().contains(" .prot=" + c.getProtocol() + "\n"),
                    accessory || c.getProtocol() != accessoryDecoderType.MM2, where + c.exportToCS2TextFormat());
                mm2Accessories += accessory && c.getProtocol() == accessoryDecoderType.MM2 ? 1 : 0;
                before.put(where, c.getProtocol());
            }
        }

        assertTrue(mm2Accessories > 0, "the fixtures have MM2 accessory tiles");

        for (LayoutDiagram d : read(false))
        {
            d.saveChanges(null, false);
        }

        for (LayoutDiagram d : read(false))
        {
            for (LayoutDiagramComponent c : d.getAll())
            {
                String where = d.getName() + " " + c.getX() + "," + c.getY() + " " + c.getType() + ": ";
                assertEquals(c.getProtocol(), before.get(where), where + "read back as before the save");
            }
        }
    }
}
