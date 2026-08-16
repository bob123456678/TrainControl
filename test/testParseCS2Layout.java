import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.file.CS2File;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.traincontrol.base.Accessory.accessoryDecoderType.DCC;
import static org.traincontrol.base.Accessory.accessoryDecoderType.MM2;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;

/**
 * Compares CS2 and CS3 route parsing
 */
public class testParseCS2Layout
{   
    // Test files stored locally
    private final String cs2_layout = getClass().getResource("layout").toURI().toString();
    private final String cs2_mags = getClass().getResource("layout/config/magnetartikel.cs2").toURI().toString();

    public MarklinControlStation model;
    public List<LayoutDiagram> layouts;
    public List<LayoutDiagram> layouts_nomags;

    public List<MarklinAccessory> accs;
    public List<MarklinAccessory> manualAccs;

    public CS2File parser;
            
    public testParseCS2Layout() throws Exception
    {
        model = init(null, true, false, false, false); 

        parser = new CS2File(cs2_layout, model);
                 
        parser.setLayoutDataLoc(cs2_layout);

        // Parse accessories with built-in logic
        accs = parser.getMagList(true);
        
        // Prase accessories manually
        manualAccs = parser.parseMags(
            CS2File.parseFile(CS2File.fetchURL(cs2_mags))
        );
        
        // Parse routes with and without the accessory database file
        layouts = parser.parseLayout(accs);
        
        layouts_nomags = parser.parseLayout(new LinkedList<>());
    }
    
    /**
     * The page id from gleisbild.cs2 reaches the parsed layout.
     *
     * Autonomy stores its setup against this id rather than the page name, because a name is what a user
     * renames and an id is not.  Nothing else in TrainControl reads it, so nothing else would notice if
     * parsing stopped carrying it - hence a test.
     */
    @Test
    public void testPageIdIsParsed()
    {
        assertEquals(layouts.size(), 1);
        assertEquals(layouts.get(0).getPageId(), "1",
            "the id in gleisbild.cs2 should reach the layout");
    }

    /**
     * Reading the index must not change which pages come back, or in what order.
     *
     * parseLayoutList was rewritten to keep the page id alongside the name.  An index keyed by name
     * would have been the obvious shape and is wrong: two pages may share a name, and collapsing them
     * would silently drop one from every caller - including the download that writes the files back
     * out.  So the list is one entry per page, in file order, duplicates and all.
     */
    @Test
    public void testEveryPageInTheIndexComesBackInFileOrder() throws Exception
    {
        List<String> fromIndex = new ArrayList<>();

        for (Map<String, String> entry : CS2File.parseFile(CS2File.fetchURL(
            CS2File.getLayoutMasterURL(cs2_layout))))
        {
            if ("seite".equals(entry.get("_type")) && entry.get("name") != null)
            {
                fromIndex.add(entry.get("name"));
            }
        }

        assertEquals(layouts.size(), fromIndex.size(),
            "one layout per page in the index, including any sharing a name");

        for (int i = 0; i < fromIndex.size(); i++)
        {
            assertEquals(layouts.get(i).getName(), fromIndex.get(i),
                "page " + i + " should keep its position in the file");
        }
    }

    /**
     * The page list a user sees is sorted, and stays sorted.
     *
     * File order is what parsing returns; lexicographic order is what the layout selector shows.  The
     * two are different on purpose, and the parsing change must not have quietly swapped one for the
     * other.
     */
    @Test
    public void testTheLayoutListIsStillSortedLexicographically()
    {
        List<String> shown = model.getLayoutList();

        List<String> sorted = new ArrayList<>(shown);
        java.util.Collections.sort(sorted);

        assertEquals(shown, sorted, "the layout selector should list pages in lexicographic order");
    }

    /**
     * A parsed layout still writes back out as valid CS2 text.
     *
     * The page id lives in the index file rather than in a page, so exporting a page must be unchanged
     * by carrying it - and this is the path a diagram edit saves through.
     */
    @Test
    public void testAParsedLayoutStillExportsAsCS2Text() throws Exception
    {
        String exported = layouts.get(0).exportToCS2TextFormat();

        assertNotNull(exported);
        assertTrue(exported.contains("[gleisbildseite]"),
            "an exported page should carry the CS2 header: "
                + exported.substring(0, Math.min(200, exported.length())));

        // every component that went in comes back out
        int elements = 0;
        int index = exported.indexOf("element");

        while (index >= 0)
        {
            elements++;
            index = exported.indexOf("element", index + 1);
        }

        assertEquals(elements, layouts.get(0).getAll().size(),
            "every tile should be written back");
    }

    /**
     * Checks the number of layout pages
     */
    @Test
    public void testNumLayouts()
    {           
        assertEquals(1, layouts.size());
        assertEquals(1, layouts_nomags.size());
    }
    
    /**
     * Verify that the DCC accessories were detected as such
     */
    @Test
    public void testDCCAcc()
    {           
        int valid = 0;
        
        for (LayoutDiagram l : layouts)
        {
            for (LayoutDiagramComponent c : l.getAll())
            {
                if (c.getAddress() == 65)
                {
                    assertEquals(c.getProtocol(), DCC);
                    valid++;
                }
                
                if (c.getAddress() == 67)
                {
                    assertEquals(c.getProtocol(), DCC);
                    valid++;
                }
                
                if (c.getAddress() == 68)
                {
                    assertEquals(c.getProtocol(), DCC);
                    valid++;
                }
                
                if (c.getAddress() == 21)
                {
                    assertEquals(c.getProtocol(), MM2);
                    valid++;
                }
                
                if (c.getAddress() == 54)
                {
                    assertEquals(c.getProtocol(), MM2);
                    valid++;
                }
            }
        }
        
        assertEquals(5, valid);
    }
    
    @Test
    public void testDCCAccNoMags()
    {           
        int valid = 0;
        
        for (LayoutDiagram l : layouts_nomags)
        {
            for (LayoutDiagramComponent c : l.getAll())
            {
                if (c.getAddress() == 65)
                {
                    assertEquals(c.getProtocol(), MM2);
                    valid++;
                }
                
                if (c.getAddress() == 67)
                {
                    assertEquals(c.getProtocol(), MM2);
                    valid++;
                }
                
                if (c.getAddress() == 68)
                {
                    assertEquals(c.getProtocol(), MM2);
                    valid++;
                }
                
                if (c.getAddress() == 21)
                {
                    assertEquals(c.getProtocol(), MM2);
                    valid++;
                }
                
                if (c.getAddress() == 54)
                {
                    assertEquals(c.getProtocol(), MM2);
                    valid++;
                }
            }
        }
        
        assertEquals(5, valid);
    }
    
    @Test
    public void testAccDB()
    {
        assertEquals(accs, manualAccs);
        assertEquals(accs.size(), 127);
    }
   
     
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception
    {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception
    {
    }

    /**
     * Importing a three-way never records both of its drives as thrown.
     *
     * A three-way is two solenoid drives, and they have exactly three combinations the turnout can
     * hold: straight with both released, left with the first thrown, right with the second - the cycle
     * execSwitching walks.  The importer seeded the first drive from state != 1, which makes state 2
     * both drives thrown.  That is none of the three, and the sample layout shipped with this
     * repository contains it: element 0x203 of "1 - Main.cs2" is a dreiwegweiche with zustand=2.
     */
    @Test
    public void testAThreeWayNeverImportsWithBothDrivesThrown() throws Exception
    {
        for (int state = 0; state <= 2; state++)
        {
            LayoutDiagramComponent c = new LayoutDiagramComponent(
                LayoutDiagramComponent.componentType.SWITCH_THREE, 0, 0, 0, state, 36, 36, MM2);

            assertFalse(c.getPrimaryDriveState() && c.getSecondaryDriveState(),
                "state " + state + " records both drives thrown, which is not a position a three-way has");
        }

        // state 2 is "right": the second drive over, the first released
        LayoutDiagramComponent right = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.SWITCH_THREE, 0, 0, 0, 2, 36, 36, MM2);

        assertFalse(right.getPrimaryDriveState(), "the first drive is released at right");
        assertTrue(right.getSecondaryDriveState(), "and the second is the one thrown");

        // the other two, unchanged
        LayoutDiagramComponent left = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.SWITCH_THREE, 0, 0, 0, 0, 36, 36, MM2);

        assertTrue(left.getPrimaryDriveState(), "state 0 is left");
        assertFalse(left.getSecondaryDriveState());

        LayoutDiagramComponent straight = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.SWITCH_THREE, 0, 0, 0, 1, 36, 36, MM2);

        assertFalse(straight.getPrimaryDriveState(), "state 1 is straight - both released");
        assertFalse(straight.getSecondaryDriveState());
    }

    /**
     * An ordinary turnout is untouched by the above: it has one drive and state 0 means thrown.
     */
    @Test
    public void testAnOrdinaryTurnoutStillSeedsFromStateOne() throws Exception
    {
        LayoutDiagramComponent thrown = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.SWITCH_LEFT, 0, 0, 0, 0, 10, 10, MM2);

        LayoutDiagramComponent released = new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.SWITCH_LEFT, 0, 0, 0, 1, 10, 10, MM2);

        assertTrue(thrown.getPrimaryDriveState());
        assertFalse(released.getPrimaryDriveState());
    }

    /**
     * RS-C2: a page name carrying a path separator must not decide where the file is written.
     *
     * downloadCS2Layout writes each page to new File(layoutsDir, layoutName + ".cs2") with layoutName
     * taken verbatim from the fetched index.  The REMOTE url is built through sanitizeURL for exactly
     * this reason; the local write is not guarded at all.  A page named "Sub/Page" - which a Central
     * Station will happily fetch, because the separator is legal in a url path - resolves locally to a
     * subdirectory that does not exist in the destination, so Files.newBufferedWriter throws and the
     * download stops half done, leaving a partial layout folder the next sync reads as authoritative.
     *
     * The fixture uses a separator rather than a Windows-illegal character on purpose: the source file
     * has to EXIST for the fetch to succeed, so "Yard / West" cannot be reproduced end to end - the
     * same constraint that makes the defect possible.  A separator reproduces the identical unguarded
     * join with filenames that are legal on both sides.
     *
     * The accessory file at the end of downloadCS2Layout is fetched from http://IP/... regardless of
     * the data path, so it cannot succeed against a local fixture.  That step runs strictly AFTER the
     * page loop, so the assertions below are unaffected; the throw is caught and identified rather
     * than swallowed, so a failure in the page loop cannot hide inside it.
     */
    @Test
    public void testAPageNameCarryingAPathSeparatorStaysInsideTheLayoutFolder() throws Exception
    {
        String source = getClass().getResource("layout_subpage").toURI().toString();

        CS2File downloader = new CS2File(source, model);

        downloader.setLayoutDataLoc(source);

        Path destination = Files.createTempDirectory("tc-rsc2");

        try
        {
            try
            {
                downloader.downloadCS2Layout(destination.toFile());
            }
            catch (Exception e)
            {
                // Expected: the accessory file is fetched over http and there is no station here.
                // Anything thrown from the page loop instead would surface as a failed assertion
                // below, which is the point of asserting on the files rather than on this call.
                assertTrue(String.valueOf(e).contains("magnetartikel")
                        || String.valueOf(e).toLowerCase().contains("http")
                        || e instanceof java.net.MalformedURLException
                        || e instanceof java.net.UnknownHostException,
                    "the only tolerated failure is the accessory fetch: " + e);
            }

            File pages = new File(new File(destination.toFile(), "config"), "gleisbilder");

            assertTrue(new File(pages, "Main.cs2").exists(),
                "precondition: the ordinary page must download - it is listed before the awkward one, "
                    + "so if this is missing the run failed before reaching the case under test");

            // Fix-shape agnostic: sanitising the name to a flat file and creating the subdirectory are
            // both acceptable, and both keep every written page inside the layouts folder.
            List<String> written = new ArrayList<>();

            if (pages.exists())
            {
                java.nio.file.Files.walk(pages.toPath())
                    .filter(java.nio.file.Files::isRegularFile)
                    .forEach(p -> written.add(pages.toPath().relativize(p).toString()));
            }

            assertEquals(written.size(), 2,
                "both pages must be written, and both inside the layouts folder - found: " + written);

            File strayDir = new File(destination.toFile(), "config");

            for (File stray : strayDir.listFiles())
            {
                assertTrue(stray.isDirectory() || "gleisbild.cs2".equals(stray.getName())
                        || "magnetartikel.cs2".equals(stray.getName()),
                    "nothing may be written into config/ except the index and the accessory file, "
                        + "but found " + stray.getName());
            }
        }
        finally
        {
            java.nio.file.Files.walk(destination)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
