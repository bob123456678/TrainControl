import java.util.LinkedList;
import java.util.List;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;
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
import org.traincontrol.marklin.MarklinLayout;
import org.traincontrol.marklin.MarklinLayoutComponent;

/**
 * Compares CS2 and CS3 route parsing
 */
public class testParseCS2Layout
{   
    // Test files stored locally
    private final String cs2_layout = getClass().getResource("layout").toURI().toString();
    private final String cs2_mags = getClass().getResource("layout/config/magnetartikel.cs2").toURI().toString();

    public MarklinControlStation model;
    public List<MarklinLayout> layouts;
    public List<MarklinLayout> layouts_nomags;

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
        
        for (MarklinLayout l : layouts)
        {
            for (MarklinLayoutComponent c : l.getAll())
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
        
        for (MarklinLayout l : layouts_nomags)
        {
            for (MarklinLayoutComponent c : l.getAll())
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
}
