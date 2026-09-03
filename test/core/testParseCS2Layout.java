package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
    private final String cs2_layout = getClass().getResource("/layout").toURI().toString();
    private final String cs2_mags = getClass().getResource("/layout/config/magnetartikel.cs2").toURI().toString();

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
     *
     * Driven against three names added directly to the model's layout database in a scrambled order,
     * rather than against whatever model.getLayoutList() already returns.  That used to compare the
     * list with a sorted copy of itself - trivially true at 0 or 1 pages, and the list it compared came
     * from LAYOUT_OVERRIDE_PATH_PREF, a machine preference, rather than from this test's own fixture,
     * so it was really asserting nothing about production sorting.  RemoteDeviceCollection stores names
     * in a plain HashMap, whose iteration order is neither insertion order nor alphabetical, so three
     * names added as Zulu, Alpha, Mike coming back as Alpha, Mike, Zulu is real evidence that
     * getLayoutList()'s own Collections.sort ran, not a coincidence of insertion order.
     */
    @Test
    public void testTheLayoutListIsStillSortedLexicographically() throws Exception
    {
        java.lang.reflect.Field field = MarklinControlStation.class.getDeclaredField("layoutDB");
        field.setAccessible(true);

        @SuppressWarnings("unchecked")
        org.traincontrol.base.RemoteDeviceCollection<LayoutDiagram, String> layoutDB =
            (org.traincontrol.base.RemoteDeviceCollection<LayoutDiagram, String>) field.get(model);

        // Deliberately out of both insertion order and alphabetical order.
        String[] scrambled = {"TSTC13_Zulu", "TSTC13_Alpha", "TSTC13_Mike"};

        try
        {
            for (String name : scrambled)
            {
                layoutDB.add(new LayoutDiagram(name, 1, 1, "test://" + name, model), name, name);
            }

            List<String> shown = model.getLayoutList();

            assertFalse(shown.isEmpty(),
                "the layout list must not be empty - three known pages were just added to it");

            int alpha = shown.indexOf("TSTC13_Alpha");
            int mike = shown.indexOf("TSTC13_Mike");
            int zulu = shown.indexOf("TSTC13_Zulu");

            assertTrue(alpha >= 0 && mike >= 0 && zulu >= 0,
                "all three known layout names must come back from getLayoutList(): " + shown);

            assertTrue(alpha < mike && mike < zulu,
                "three names added in scrambled order (Zulu, Alpha, Mike) must come back sorted "
                + "(Alpha, Mike, Zulu) - they did not: " + shown);

            List<String> sorted = new ArrayList<>(shown);
            java.util.Collections.sort(sorted);

            assertEquals(shown, sorted, "the layout selector should list pages in lexicographic order");
        }
        finally
        {
            for (String name : scrambled)
            {
                layoutDB.delete(name);
            }
        }
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

    @AfterClass(alwaysRun = true)
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
        String source = getClass().getResource("/layout_subpage").toURI().toString();

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

    /**
     * A component TrainControl has never heard of survives being saved.
     *
     * Saving regenerates the whole page from the model, and an element whose type the parser cannot map
     * never entered the model - so writing the file deleted it.  That is not a save the user asked for:
     * naming one station writes its page out, and renaming a point writes out every page showing that
     * name.  A diagram with one unrecognised element on it lost that element the first time somebody
     * typed a station name.
     */
    @Test
    public void testAnUnrecognisedElementSurvivesASave() throws Exception
    {
        String original =
            "[gleisbildseite]\n"
            + "version\n"
            + " .major=1\n"
            + "element\n"
            + " .id=0x101\n"
            + " .typ=gerade\n"
            + " .artikel=-1\n"
            + "element\n"
            + " .id=0x202\n"
            + " .typ=weltraumbahnhof\n"
            + " .artikel=7\n"
            + " .drehung=2\n";

        String written = roundTrip(original);

        assertTrue(written.contains("weltraumbahnhof"),
            "an element this program cannot model is still the user's, and must come back:\n" + written);

        assertTrue(written.contains("0x202"), "and at the square it was on:\n" + written);
        assertTrue(written.contains(".artikel=7"), "with what it said about itself:\n" + written);
        assertTrue(written.contains(".drehung=2"), "all of it:\n" + written);

        // and the ordinary component is still there too, which is what says the export still works
        assertTrue(written.contains("gerade"), "the modelled component is still written:\n" + written);
    }

    /**
     * A key on a component the model DOES understand, but has no field for, survives too.
     *
     * The same loss one level down: the component is kept, and everything the file said about it that
     * this program has no variable for is dropped on the way out.
     */
    @Test
    public void testAnUnknownKeyOnAKnownComponentSurvivesASave() throws Exception
    {
        String original =
            "[gleisbildseite]\n"
            + "version\n"
            + " .major=1\n"
            + "element\n"
            + " .id=0x101\n"
            + " .typ=gerade\n"
            + " .artikel=-1\n"
            + " .sonderfarbe=blau\n";

        String written = roundTrip(original);

        assertTrue(written.contains("sonderfarbe"),
            "a key this program does not read is still a key it may not delete:\n" + written);

        assertTrue(written.contains("blau"), "with its value:\n" + written);
    }

    /**
     * Writes a page, parses it the way the application does, exports it again, and hands back what was
     * written.  Nothing is asserted here - each test says what it expects to survive.
     */
    private String roundTrip(String contents) throws Exception
    {
        return parsePage(contents).exportToCS2TextFormat();
    }

    /**
     * Parses one fixture page exactly as the program parses a real layout.
     *
     * Separate from roundTrip because a save that corrupts what it wrote is only visible on the way
     * back IN: the exported text can look perfectly reasonable and still mean something else.
     */
    private LayoutDiagram parsePage(String contents) throws Exception
    {
        File folder = Files.createTempDirectory("tc-layout").toFile();

        File config = new File(folder, "config");
        File pages = new File(config, "gleisbilder");

        assertTrue(pages.mkdirs(), "could not create " + pages);

        Files.write(new File(pages, "Test.cs2").toPath(), contents.getBytes(StandardCharsets.UTF_8));

        // The index lives beside the gleisbilder folder, not inside it - config/gleisbild.cs2 is what
        // getLayoutMasterURL asks for, and the pages are config/gleisbilder/<name>.cs2.
        Files.write(new File(config, "gleisbild.cs2").toPath(),
            ("[gleisbild]\nversion\n .major=1\ngroesse\nseite\n .id=1\n .name=Test\n")
                .getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

        org.traincontrol.marklin.file.CS2File parser =
            new org.traincontrol.marklin.file.CS2File(url, null);

        parser.setLayoutDataLoc(url);

        List<LayoutDiagram> parsed =
            parser.parseLayout(new java.util.LinkedList<org.traincontrol.marklin.MarklinAccessory>());

        assertFalse(parsed.isEmpty(), "the fixture page did not parse");

        return parsed.get(0);
    }

    /**
     * The one component of a single-element fixture, found without assuming how an id maps to a square.
     */
    private LayoutDiagramComponent onlyComponent(LayoutDiagram page)
    {
        List<LayoutDiagramComponent> all = new ArrayList<>();

        for (LayoutDiagramComponent component : page.getAll())
        {
            if (component != null) all.add(component);
        }

        assertEquals(all.size(), 1, "the fixture should hold exactly one element");

        return all.get(0);
    }

    /**
     * A signal keeps the exact type and rotation the file gave it.
     *
     * The type mapping is many-to-one - fifteen signal words all become SIGNAL - while the export wrote
     * one canonical word back, so every variant the file distinguished collapsed the first time a page
     * was saved.  Worse for a semaphore: the parser turns any type whose word contains "_f_" by a
     * quarter to correct the artwork, and "signal" does not contain it, so the correction was baked into
     * the file and the signal came back turned a step further on every save.
     *
     * Autonomy saved pages unasked during its migration, so this was not a theoretical round trip.
     */
    @Test
    public void testASignalKeepsItsExactTypeAndRotation() throws Exception
    {
        String original =
            "[gleisbildseite]\n"
            + "version\n"
            + " .major=1\n"
            + "element\n"
            + " .id=0x101\n"
            + " .typ=signal_f_hp01\n"
            + " .drehung=1\n"
            + " .artikel=12\n";

        String written = roundTrip(original);

        assertTrue(written.contains("signal_f_hp01"),
            "the file said which kind of signal, and only the file knows:\n" + written);

        assertTrue(written.contains(".drehung=1"),
            "the rotation must come back as it went in, or it creeps a quarter every save:\n" + written);
    }

    /**
     * An element carrying a CS2 array comes back as an array, not as one line with braces in it.
     *
     * The parser folds a key and its " ..sub=value" lines into a single map entry holding
     * "{a=b}|{c=d}".  Writing that back as one " .key=..." line keeps the text and loses the shape - a
     * parser would read it as a scalar that happens to contain braces.  The whole reason unmodelled
     * content is kept is that a later firmware\u2019s file should survive a round trip, and this is the
     * shape most likely to BE a later firmware\u2019s.
     */
    @Test
    public void testAnArrayInAnUnmodelledElementKeepsItsShape() throws Exception
    {
        String original =
            "[gleisbildseite]\n"
            + "version\n"
            + " .major=1\n"
            + "element\n"
            + " .id=0x202\n"
            + " .typ=weltraumbahnhof\n"
            + " .artikel=7\n"
            + " .plan\n"
            + " ..gleis=3\n"
            + " ..bahnsteig=b\n";

        String written = roundTrip(original);

        assertTrue(written.contains(" ..gleis=3"),
            "an array entry has to come back as an array entry:\n" + written);

        assertTrue(written.contains(" ..bahnsteig=b"), "both of them:\n" + written);

        assertFalse(written.contains("{gleis"),
            "and not as the brace form the parser folds them into:\n" + written);
    }

    /**
     * A semaphore the file gave no rotation at all still means the same thing after a save.
     *
     * The parser turns any type whose word contains "_f_" back by a quarter, to correct artwork the CS2
     * draws rotated.  A file with no ".drehung" means rotation 0, so such a signal parses to
     * orientation 3 - and the export wrote that 3 back out, under the very word that triggers the
     * correction, so the next load read it as 2.  A quarter turn lost to pressing Save, on the diagram
     * and on the Central Station both.
     *
     * Asserting on the re-parsed orientation rather than on the text is deliberate: the number in the
     * file is only correct relative to the word beside it, and a test that reads one without the other
     * cannot tell a right answer from a wrong one.
     */
    @Test
    public void testASemaphoreWithNoRotationDoesNotDriftOnSave() throws Exception
    {
        String original =
            "[gleisbildseite]\n"
            + "version\n"
            + " .major=1\n"
            + "element\n"
            + " .id=0x101\n"
            + " .typ=signal_f_hp01\n"
            + " .artikel=12\n";

        int meant = onlyComponent(parsePage(original)).getOrientation();

        String written = parsePage(original).exportToCS2TextFormat();

        assertEquals(onlyComponent(parsePage(written)).getOrientation(), meant,
            "saving turned the signal a quarter:\n" + written);
    }

    /**
     * A semaphore the user turns in the editor comes back turned the way they left it.
     *
     * Same asymmetry as the test above, reached from the other side.  Once the orientation no longer
     * matches the file's number the export falls back to writing the CORRECTED orientation - still
     * under the preserved "_f_" word - so the correction is applied a second time on the next load.
     *
     * Before the type word was preserved this case worked, at the price of collapsing the word to a
     * canonical "signal": the canonical word contains no "_f_", so nothing corrected it on the way
     * back.  Preserving the word without accounting for the correction traded a known loss for a
     * quieter one.
     */
    @Test
    public void testATurnedSemaphoreComesBackTurnedTheSameWay() throws Exception
    {
        String original =
            "[gleisbildseite]\n"
            + "version\n"
            + " .major=1\n"
            + "element\n"
            + " .id=0x101\n"
            + " .typ=signal_f_hp01\n"
            + " .drehung=1\n"
            + " .artikel=12\n";

        LayoutDiagram page = parsePage(original);

        onlyComponent(page).setOrientation(2);

        String written = page.exportToCS2TextFormat();

        assertEquals(onlyComponent(parsePage(written)).getOrientation(), 2,
            "the signal was left at 2 and did not come back at 2:\n" + written);
    }

    /**
     * A copied component writes the same file text as the one it was copied from.
     *
     * The copy constructor took the modelled fields and the label, which is everything the editor
     * needs to DRAW a tile and not everything the file needs to survive one.  It carried over neither
     * the original type word, nor the original rotation, nor the keys this program cannot model - so
     * every copy was a component with the verbatim record stripped out.
     *
     * That matters because the editor snapshots through this constructor: deepCopyLayout builds undo
     * states with it, and undo then replaces every component on the page with a stripped copy.  Place
     * a tile, press undo, press Save, and every unmodelled key on a page that has never touched
     * autonomy is gone - from a gesture nobody would think of as destructive.
     */
    @Test
    public void testACopiedComponentStillKnowsWhatTheFileSaid() throws Exception
    {
        String original =
            "[gleisbildseite]\n"
            + "version\n"
            + " .major=1\n"
            + "element\n"
            + " .id=0x101\n"
            + " .typ=signal_f_hp01\n"
            + " .drehung=1\n"
            + " .artikel=12\n"
            + " .weltraumbahnhof=42\n";

        LayoutDiagramComponent parsed = onlyComponent(parsePage(original));

        LayoutDiagramComponent copy = new LayoutDiagramComponent(parsed);

        assertEquals(copy.exportToCS2TextFormat(), parsed.exportToCS2TextFormat(),
            "a copy of a component does not write what the original wrote, so copying it loses "
                + "whatever the file said that this program cannot model");
    }

    /**
     * Renaming a page to the same letters in different case keeps the page.
     *
     * saveChanges writes the new filename and then deletes the old one, which is correct only while
     * they are two files.  On Windows and macOS "Main" and "MAIN" are one file: the writer reopened
     * and rewrote the original, and the delete then removed the only copy.  The UI does not catch it
     * either - its duplicate check is a case-sensitive list lookup, so "MAIN" does not collide with
     * "Main" and the rename proceeds.  Renaming is offered only for local layouts, so nothing on the
     * Central Station could put the page back.
     *
     * Counting the files rather than asking for one by name is what makes this meaningful on both
     * kinds of filesystem: where the two names are distinct the old file must be gone, and where they
     * are the same file it must still be there.  Either way exactly one page survives, with content.
     */
    @Test
    public void testACaseOnlyRenameDoesNotDeleteThePage() throws Exception
    {
        File folder = Files.createTempDirectory("tc-rename").toFile();

        File config = new File(folder, "config");
        File pages = new File(config, "gleisbilder");

        assertTrue(pages.mkdirs(), "could not create " + pages);

        Files.write(new File(pages, "Main.cs2").toPath(),
            ("[gleisbildseite]\nversion\n .major=1\nelement\n .id=0x101\n .typ=weiche\n .artikel=8\n")
                .getBytes(StandardCharsets.UTF_8));

        Files.write(new File(config, "gleisbild.cs2").toPath(),
            ("[gleisbild]\nversion\n .major=1\ngroesse\nseite\n .id=1\n .name=Main\n")
                .getBytes(StandardCharsets.UTF_8));

        String url = "file:///" + folder.getAbsolutePath().replace('\\', '/') + "/";

        CS2File parser = new CS2File(url, null);
        parser.setLayoutDataLoc(url);

        List<LayoutDiagram> parsed = parser.parseLayout(new LinkedList<MarklinAccessory>());

        assertFalse(parsed.isEmpty(), "the fixture page did not parse");

        parsed.get(0).saveChanges("MAIN", false);

        // PAGES, not files.  A page is found by the name the index gives it, so anything in this
        // folder that is not a .cs2 - the backup saveChanges keeps of a file it is about to rewrite -
        // is not a page and cannot become one.
        File[] left = pages.listFiles((dir, name) -> name.endsWith(".cs2"));

        assertEquals(left.length, 1,
            "a case-only rename should leave exactly one page: " + java.util.Arrays.toString(left));

        String kept = new String(Files.readAllBytes(left[0].toPath()), StandardCharsets.UTF_8);

        assertTrue(kept.contains("weiche"),
            "the surviving file is not the page that was renamed:\n" + kept);
    }
}
