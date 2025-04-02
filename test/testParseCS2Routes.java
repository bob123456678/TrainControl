import org.traincontrol.base.RouteCommand;
import java.util.ArrayList;
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
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.MarklinAccessory;

/**
 * Compares CS2 and CS3 route parsing
 */
public class testParseCS2Routes
{   
    // Test files stored locally
    private final String cs2_mags = getClass().getResource("magnetartikel.cs2").toURI().toString();
    private final String cs2_routes = getClass().getResource("fahrstrassen.cs2").toURI().toString();
    
    public MarklinControlStation model;
    public List<MarklinRoute> routes_mags;
    public List<MarklinRoute> routes_nomags;
    public List<MarklinAccessory> accs;

    public CS2File parser;
            
    public testParseCS2Routes() throws Exception
    {
        parser = new CS2File(null, null);
        model = init(null, true, false, false, false); 
                        
        accs = parser.parseMags(
            CS2File.parseFile(CS2File.fetchURL(cs2_mags))
        );
        
        // Correctly parsed routes
        routes_mags = parser.parseRoutes(
            CS2File.parseFile(CS2File.fetchURL(cs2_routes)),
            accs
        );
        
        // Routes with accessories
        routes_nomags = parser.parseRoutes(
            CS2File.parseFile(CS2File.fetchURL(cs2_routes)),
            new ArrayList<>()
        );
    }
    
    private MarklinRoute getRoute(String routeName, List<MarklinRoute> input)
    {        
        for (MarklinRoute r : input)
        {
            if (r.getName().equals(routeName))
            {
                return r;
            }
        }
        
        return null;
    }
    
    private MarklinAccessory getAcc(int address, List<MarklinAccessory> input)
    {        
        for (MarklinAccessory a : input)
        {
            if (a.getAddress() == address)
            {
                return a;
            }
        }
        
        return null;
    }
   
    /**
     * Checks if there are any extra routes in the CS2 DB
     */
    @Test
    public void testNumRoutes()
    {           
        assertEquals(81, routes_nomags.size());
        assertEquals(81, routes_mags.size());
    }
    
    @Test
    public void testNumAccs()
    {           
        assertEquals(127, accs.size());
    }
    
    @Test
    public void testDCCRoute()
    {
        MarklinRoute r = this.getRoute("D1 dcc tst", routes_mags);

        for (RouteCommand rc: r.getRoute())
        {
            if (rc.getAddress() == 119)
            {
                assertEquals(rc.getProtocol(), Accessory.accessoryDecoderType.MM2);
            }
            else if (rc.getAddress() == 121)
            {
                assertEquals(rc.getProtocol(), Accessory.accessoryDecoderType.DCC);
            }
        }
    }
    
    public void testDCCRouteNoMags()
    {
        MarklinRoute r = this.getRoute("D1 dcc tst", routes_nomags);
        
        for (RouteCommand rc: r.getRoute())
        {
            assertEquals(rc.getProtocol(), Accessory.accessoryDecoderType.MM2);
        }
    }
    
     public void testMM2RouteMags()
    {
        MarklinRoute r = this.getRoute("D1 dcc tst", routes_mags);
        
        for (RouteCommand rc: r.getRoute())
        {
            assertEquals(rc.getProtocol(), Accessory.accessoryDecoderType.MM2);
        }
    }
    
    public void testDCCAcc()
    {
        MarklinAccessory a = this.getAcc(121, accs);
        
        assertEquals(a.getDecoderType(), Accessory.accessoryDecoderType.DCC);
    }
    
    public void testMM2Acc()
    {
        MarklinAccessory a = this.getAcc(118, accs);
        
        assertEquals(a.getDecoderType(), Accessory.accessoryDecoderType.DCC);
    }
    
    public void testMM2Acc1()
    {
        MarklinAccessory a = this.getAcc(117, accs);
        
        assertEquals(a.getDecoderType(), Accessory.accessoryDecoderType.DCC);
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
