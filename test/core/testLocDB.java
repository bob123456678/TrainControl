package core;

import org.traincontrol.base.Locomotive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive.decoderType;

/**
 *
 * @author adamo
 */
public class testLocDB
{   
    public static MarklinControlStation model;
    
    /**
     * The locomotive CSV is produced without a window (AC2-C3).
     *
     * **A method that needed the one thing a headless session does not have.** Nine of its ten
     * columns come from the locomotive; the tenth, button mappings, is a fact about the main window,
     * and it was read through `this.view` with no check. `view` is null whenever the model was built
     * with `showUI` false - which is exactly how the documented programmatic API starts, and how this
     * very test class starts. So an API user asking for the CSV got a bare NullPointerException
     * instead of the nine columns that were never in doubt.
     *
     * Every neighbouring method in that class already asks whether there is a view. This one did not,
     * which is this codebase's recurring shape rather than an isolated slip.
     *
     * **Empty, not refused.** A button mapping is a fact about a window; a session with no window has
     * none. That is the answer, not an error.
     *
     * MUTATION: take the null check out and this throws rather than failing an assertion - which is
     * the defect exactly, so the throw is the evidence.
     */
    @Test
    public void testTheLocomotiveCsvNeedsNoWindow()
    {
        assertNull(model.getGUI(),
            "precondition: this class starts the model with showUI false, so there must be no view - "
            + "if that ever changes this test stops asking anything");

        String csv = model.exportLocsToCSV();

        assertNotNull(csv, "no CSV came back at all");

        // THE HEADER, which is the part that proves the method ran rather than that a locomotive
        // happened to be present.
        assertTrue(csv.startsWith("Name,ButtonMappings"),
            "the CSV does not begin with its own header row: " + csv.substring(0,
                Math.min(80, csv.length())));

        // AND A NAMED LOCOMOTIVE IN IT, so the row loop is actually reached - a header-only CSV
        // would pass the assertion above without ever touching the line that used to throw.
        //
        // By name rather than by counting lines: `escapeCsv` quotes a notes field that contains a
        // newline, and a quoted newline is a legal CSV row that spans two lines.  Counting them said
        // 197 rows for 173 locomotives, and the twenty-four extra were notes, not a
        // defect - the count was the wrong question, asked confidently.
        assertFalse(model.getLocList().isEmpty(),
            "precondition: the database has to hold a locomotive, or the row loop never runs and "
            + "the line that used to throw is never reached");

        final String first = model.getLocList().get(0);

        assertTrue(csv.contains(org.traincontrol.util.Util.escapeCsv(first)),
            "the CSV does not contain \"" + first + "\", so the per-locomotive loop - which is "
            + "where the view was read - was never reached");
    }

    public testLocDB()
    {
    }

    /**
     * Adding and deleting a locomotive from the database
     */
    @Test
    public void testAddAndDeleteLoc()
    {   
        int numLocs = model.getLocList().size();
        String locName = "New locomotive test";
        int address = 10;
        
        model.newMFXLocomotive(locName, address);

        assertNotNull(model.getLocByName(locName), "the new locomotive must be in the database");
        assertEquals(model.getLocList().size(), numLocs + 1, "the list must have grown by one");
        assertEquals(model.getLocByName(locName).getAddress(), address);
        assertEquals(model.getLocByName(locName).getName(), locName);
        assertTrue(model.getLocList().contains(locName), "the new locomotive must be listed");
        assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MFX, address), true);
        
        assertFalse(model.getLocByName(locName).hasLinkedLocomotives());
        
        model.deleteLoc(locName);
     
        assertNull(model.getLocByName(locName), "the locomotive must be gone after deletion");
        assertEquals(model.getLocList().size(), numLocs, "the list must be back to its original size");
        assertFalse(model.getLocList().contains(locName), "the deleted locomotive must not be listed");
    }
    
    /**
     * Adding and deleting a locomotive from the database
     */
    @Test
    public void testChangeAddress() throws Exception
    {   
        String locName = "New locomotive test 2";
        int address = 12;
        int newAddress = 14;
        
        model.newMFXLocomotive(locName, address);

        assertEquals(model.getLocList().contains(locName), true);
        
        MarklinLocomotive loc = model.getLocByName(locName);
        
        assertNotEquals(loc, null);
        
        int currentAddress = loc.getAddress();
        
        assertEquals(currentAddress, address);
        
        model.changeLocAddress(locName, address, loc.getDecoderType());
        
        assertEquals(model.getLocList().contains(locName), true);

        model.changeLocAddress(locName, address, decoderType.MM2);
        
        assertEquals(model.getLocByName(locName).getDecoderType(), decoderType.MM2);
        assertEquals(model.getLocByName(locName).getAddress(), address);

        model.changeLocAddress(locName, newAddress, decoderType.MFX);
        
        assertEquals(model.getLocByName(locName).getDecoderType(), decoderType.MFX);
        
        MarklinLocomotive locAgain = model.getLocByName(locName);
        
        assertEquals(locAgain, loc);
        assertEquals(locAgain.getAddress(), newAddress);

        assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MFX, address), true);
        assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MFX, newAddress), true);

        model.deleteLoc(locName);
    }
    
    /**
     * Adding and deleting a locomotive from the database
     */
    @Test
    public void testRenameLoc()
    {   
        String locName = "New locomotive test 2";
        String locName2 = "New locomotive test 2 copy";
        
        model.newMFXLocomotive(locName, 20);
        model.deleteLoc(locName2);

        model.renameLoc(locName, locName2);
                
        assertNull(model.getLocByName(locName), "the old name must not resolve after a rename");
        assertNotNull(model.getLocByName(locName2), "the new name must resolve after a rename");
        assertEquals(model.getLocByName(locName2).getName(), locName2);
    }
    
    @Test
    public void testYearRangeMatching()
    {
        // Target locomotive
        String targetName = "TargetLoc";
        model.newMFXLocomotive(targetName, 20);
        Locomotive target = model.getLocByName(targetName);
        target.setStructuredNotes(1950, 1970, "PKP", "Target notes");

        // Candidate: full overlap
        String fullOverlapName = "FullOverlap";
        model.newMFXLocomotive(fullOverlapName, 21);
        Locomotive fullOverlap = model.getLocByName(fullOverlapName);
        fullOverlap.setStructuredNotes(1960, 1965, "PKP", "Full overlap");

        // Candidate: partial overlap
        String partialOverlapName = "PartialOverlap";
        model.newMFXLocomotive(partialOverlapName, 22);
        Locomotive partialOverlap = model.getLocByName(partialOverlapName);
        partialOverlap.setStructuredNotes(1965, 1980, "PKP", "Partial overlap");

        // Candidate: no overlap
        String noOverlapName = "NoOverlap";
        model.newMFXLocomotive(noOverlapName, 23);
        Locomotive noOverlap = model.getLocByName(noOverlapName);
        noOverlap.setStructuredNotes(1980, 1990, "PKP", "No overlap");

        // Candidate: no end year
        String noEndName = "NoEndYear";
        model.newMFXLocomotive(noEndName, 24);
        Locomotive noEnd = model.getLocByName(noEndName);
        noEnd.setStructuredNotes(1960, 0, "PKP", "No end year");

        // Candidate: no start year
        String noStartName = "NoStartYear";
        model.newMFXLocomotive(noStartName, 25);
        Locomotive noStart = model.getLocByName(noStartName);
        noStart.setStructuredNotes(0, 1970, "PKP", "No start year");

        // Candidate: both years missing
        String noYearsName = "NoYears";
        model.newMFXLocomotive(noYearsName, 26);
        Locomotive noYears = model.getLocByName(noYearsName);
        noYears.setStructuredNotes(0, 0, "PKP", "No years");

        // Candidate: wrong railway
        String wrongRailwayName = "WrongRailway";
        model.newMFXLocomotive(wrongRailwayName, 27);
        Locomotive wrongRailway = model.getLocByName(wrongRailwayName);
        wrongRailway.setStructuredNotes(1960, 1970, "DB", "Wrong railway");

        List<String> railroads = Arrays.asList("PKP");
        List<Locomotive> allLocs = new ArrayList<>(model.getLocomotives());

        List<Locomotive> result = Locomotive.findSimilarLocomotives(target, 10, railroads, allLocs, true);
        List<String> names = result.stream().map(Locomotive::getName).collect(Collectors.toList());

        assertEquals(true, names.contains(fullOverlapName));
        assertEquals(true, names.contains(partialOverlapName));
        assertEquals(true, names.contains(noEndName));
        assertEquals(false, names.contains(noStartName));
        assertEquals(false, names.contains(noYearsName));
        assertEquals(false, names.contains(noOverlapName));
        assertEquals(false, names.contains(wrongRailwayName));

        // Cleanup
        model.deleteLoc(targetName);
        model.deleteLoc(fullOverlapName);
        model.deleteLoc(partialOverlapName);
        model.deleteLoc(noOverlapName);
        model.deleteLoc(noEndName);
        model.deleteLoc(noStartName);
        model.deleteLoc(noYearsName);
        model.deleteLoc(wrongRailwayName);
    }
    
    /**
     * Test the lists of locomotives
     */
    @Test
    public void testLocList()
    {   
        String locName = "New locomotive test 2";
        
        model.newMFXLocomotive(locName, 20);
        
        List<String> locNames = model.getLocList();
        
        List<String> locNames2 = new ArrayList<>();
        
        for (Locomotive l : model.getLocomotives())
        {
            locNames2.add(l.getName());
        }
               
        assertEquals(locNames.containsAll(locNames2), true);
        assertEquals(locNames2.containsAll(locNames), true);
        assertEquals(locNames2.size(), locNames.size());
        
        model.deleteLoc(locName);
        
        assertEquals(model.getLocByName(locName), null);
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testLocDB.model = init(null, true, false, false, false); 
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        // TST-B20: testRenameLoc creates "New locomotive test 2" (MFX 20) and renames it to "New
        // locomotive test 2 copy" - nothing ever deleted either name, so one of them was left in the
        // restored DB image for whatever ran next in this JVM.
        if (model != null) model.deleteLoc("New locomotive test 2");
        if (model != null) model.deleteLoc("New locomotive test 2 copy");
    }

    @BeforeMethod
    public void setUpMethod() throws Exception
    {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception
    {
    }
}
