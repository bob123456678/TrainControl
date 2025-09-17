import org.traincontrol.base.Locomotive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinLocomotive.decoderType;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author adamo
 */
public class testLocDB
{   
    public static MarklinControlStation model;
    
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

        assert model.getLocByName(locName) != null;   
        assert model.getLocList().size() == numLocs + 1;
        assert model.getLocByName(locName).getAddress() == address;
        assert model.getLocByName(locName).getName().equals(locName);
        assert model.getLocList().contains(locName);
        assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MFX, address), true);
        
        assertFalse(model.getLocByName(locName).hasLinkedLocomotives());
        
        model.deleteLoc(locName);
     
        assert model.getLocByName(locName) == null;
        assert model.getLocList().size() == numLocs;
        assert !model.getLocList().contains(locName); 
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
                
        assert model.getLocByName(locName) == null;
        assert model.getLocByName(locName2) != null;
        assert locName2.equals(model.getLocByName(locName2).getName());
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
}
