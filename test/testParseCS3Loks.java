import java.util.List;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.file.CS2File.parseJSONArray;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.file.CS2File.fetchURL;
import static org.traincontrol.marklin.file.CS2File.parseFile;

/**
 * Tests CS2 and CS3 locomotive parsing
 */
public class testParseCS3Loks
{   
    // Test files stored locally
    private final String cs3_loks = getClass().getResource("CS3_loks.json").toURI().toString();
    private final String cs2_loks = getClass().getResource("lokomotive.cs2").toURI().toString();
    private final String cs2_loks_from_cs3 = getClass().getResource("lokomotive_cs3.cs2").toURI().toString();
    
    public List<MarklinLocomotive> loksCS3;
    public List<MarklinLocomotive> loksCS2;
    public List<MarklinLocomotive> loksCS2_fromCS3;

    //public MarklinControlStation model;
    public CS2File parser;
            
    /**
     * Utility function to get a locomotive from the list
     * @param lst
     * @param name
     * @return 
     */
    private static MarklinLocomotive getLocByName(List<MarklinLocomotive> lst, String name)
    {
        if (lst != null)
        {
            for (MarklinLocomotive l : lst)
            {
                try
                {
                    if (l.getName().equals(name)) return l;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
                
        return null;
    }
    
    public testParseCS3Loks() throws Exception
    {
        parser = new CS2File(null, null);
        // model = init(null, true, false, false, false); 
        
        try
        {
            loksCS2 = parser.parseLocomotives(parseFile(fetchURL(cs2_loks)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        
        try
        {
            loksCS2_fromCS3 = parser.parseLocomotives(parseFile(fetchURL(cs2_loks_from_cs3)));  
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        
        try
        {
            loksCS3 = parser.parseLocomotivesCS3(parseJSONArray(fetchURL(cs3_loks)));  
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
   
    /**
     * Check basic state
     */
    @Test
    public void testLoaded()
    {   
        assertTrue(!loksCS2.isEmpty());
        assertTrue(!loksCS2_fromCS3.isEmpty());
        assertTrue(!loksCS3.isEmpty());
        assertEquals(loksCS3.size(), loksCS2_fromCS3.size());
    }
    
    @Test
    public void testCS2Locs()
    {
        // Locomotive direction is not downloaded
        List<MarklinLocomotive> db = loksCS2;

        assertEquals(getLocByName(db, "CE 6/8 14310").getAddress(), 68);
        assertEquals(getLocByName(db, "CE 6/8 14310").getDecoderType(), MarklinLocomotive.decoderType.MM2);

        assertEquals(getLocByName(db, "ABns").getAddress(), 3);
        assertEquals(getLocByName(db, "ABns").getDecoderType(), MarklinLocomotive.decoderType.DCC);
        
        assertEquals(getLocByName(db, "MY 1112").getAddress(), 12);
        assertEquals(getLocByName(db, "MY 1112").getDecoderType(), MarklinLocomotive.decoderType.MM2);
        
        assertEquals(getLocByName(db, "TGV POS").getAddress(), 5);
        assertEquals(getLocByName(db, "TGV POS").getDecoderType(), MarklinLocomotive.decoderType.MFX);
        
        assertEquals(getLocByName(db, "ICE 1").getAddress(), 27);
        assertEquals(getLocByName(db, "ICE 1").getDecoderType(), MarklinLocomotive.decoderType.MFX);
    }
    
    @Test
    public void testCS2LocsFromCS3()
    {
        List<MarklinLocomotive> db = loksCS2_fromCS3;
        
        // Edge case for address 1
        assertEquals(getLocByName(db, "ICE 3 406").getAddress(), 1);
        assertEquals(getLocByName(db, "ICE 3 406").getDecoderType(), MarklinLocomotive.decoderType.MM2);
        
        assertEquals(getLocByName(db, "02 0314-1 DDR").getAddress(), 39);
        assertEquals(getLocByName(db, "02 0314-1 DDR").getDecoderType(), MarklinLocomotive.decoderType.MFX);
        assertTrue(!getLocByName(db, "02 0314-1 DDR").getImageURL().isEmpty());
        
        assertEquals(getLocByName(db, "Test TC").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);  
        assertEquals(getLocByName(db, "Test TC").getAddress(), 1);  
        assertTrue(getLocByName(db, "Test TC").getCentralStationMultiUnitLocomotiveNames().containsKey("118 028-0 DB"));
    }
    
    @Test
    public void testCS3()
    {
        // System.out.println(loksCS3);
        List<MarklinLocomotive> db = loksCS3;

        assertEquals(getLocByName(db, "ICE 3 406").getAddress(), 1);
        assertEquals(getLocByName(db, "ICE 3 406").getDecoderType(), MarklinLocomotive.decoderType.MM2);
        
        assertEquals(getLocByName(db, "02 0314-1 DDR").getAddress(), 39);
        assertEquals(getLocByName(db, "02 0314-1 DDR").getDecoderType(), MarklinLocomotive.decoderType.MFX);
        assertTrue(!getLocByName(db, "02 0314-1 DDR").getImageURL().isEmpty());
                
        // Test multi unit
        assertEquals(getLocByName(db, "Test TC").getDecoderType(), MarklinLocomotive.decoderType.MULTI_UNIT);  
        assertEquals(getLocByName(db, "Test TC").getAddress(), 1);  
        assertTrue(getLocByName(db, "Test TC").getCentralStationMultiUnitLocomotiveNames().containsKey("118 028-0 DB"));
    }
        
    /**
     * These should be the same, except that the CS2 file will have a limited function count and different icons
     */
    @Test
    public void testBothCS3Equivalent()
    {
        for(MarklinLocomotive l1 : loksCS3)
        {
            for(MarklinLocomotive l2 : loksCS2_fromCS3)
            {
                if (l1.getName().equals(l2.getName()))
                {
                    assertEquals(l1.getAddress(), l2.getAddress());
                    assertEquals(l1.getDecoderType(), l2.getDecoderType());
                    assertEquals(l1.getImageURL().isEmpty(), l2.getImageURL().isEmpty());
                    assertEquals(l1.getNumF(), l2.getNumF());

                    for (int i = 0; i < l1.getNumF(); i++)
                    {
                        // Some types do differ between the two file formats
                        if (l1.getFunctionType(i) == 0)
                        {
                            assertEquals(l1.getFunctionType(i), l2.getFunctionType(i));
                            assertEquals(l1.getFunctionTriggerTypes()[i], l2.getFunctionTriggerTypes()[i]);
                        }  
                    }
                }
            }
        }
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
}
