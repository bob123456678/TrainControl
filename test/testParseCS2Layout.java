import java.util.LinkedList;
import java.util.List;
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
}
