import base.RouteCommand;
import java.util.ArrayList;
import java.util.List;
import marklin.MarklinControlStation;
import static marklin.MarklinControlStation.init;
import marklin.MarklinRoute;
import marklin.file.CS2File;
import static marklin.file.CS2File.fetchURL;
import static marklin.file.CS2File.parseJSONArray;
import static marklin.file.CS2File.parseJSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Compares CS2 and CS3 route parsing
 */
public class testParseCS3Routes
{   
    // Test files stored locally
    private final String tc_routes = getClass().getResource("TC_routes.json").toURI().toString();
    private final String cs3_mags = getClass().getResource("CS3_mags.json").toURI().toString();
    private final String cs3_automatics = getClass().getResource("CS3_automatics.json").toURI().toString();
    
    public MarklinControlStation model;
    public List<MarklinRoute> routesTC;
    public CS2File parser;
    public List<MarklinRoute> routesCS3;
            
    public testParseCS3Routes() throws Exception
    {
        parser = new CS2File(null, null);
        model = init(null, true, false, false, false); 
                
        // We assume the TrainControl routes are correct.  Also possible to read a CS2 file to compare
        // routesCS2 = parser.parseRoutes(parseFile(fetchURL(cs2_routes)));
        routesTC = model.parseRoutesFromJson(parseJSONObject(fetchURL(tc_routes)).toString()); 
        
        routesCS3 = parser.parseRoutesCS3(parseJSONObject(fetchURL(cs3_automatics)), parseJSONArray(fetchURL(cs3_mags)));           
    }
   
    /**
     *
     */
    @Test
    public void testSameLength()
    {   
        assertEquals(routesTC.size(), routesCS3.size());
    }
    
    /**
     * Checks if there are any extra routes in the CS2 DB
     */
    @Test
    public void testCS2()
    {           
        List<MarklinRoute> routesTCNot3 = new ArrayList<>(routesTC);
        routesTCNot3.removeAll(routesCS3);
                
        for (MarklinRoute newRoute : routesTCNot3)
        {
            boolean exists = false;
            
            for (MarklinRoute otherRoute : routesCS3)
            {
                if (otherRoute.getId() == newRoute.getId() && !otherRoute.equalsUnordered(newRoute))
                {
                    exists = true;
                }
            }
            
            if (!exists)
            {
                System.out.println("TC route missing in CS3 parsed data:");
                System.out.println(newRoute.toVerboseString()); 
                System.out.println("============");
            }
        }
        
        assertEquals(true, routesTCNot3.isEmpty());
    }
   
    
    @Test 
    public void testCS3()
    {
        List<MarklinRoute> routesCS3NotTC = new ArrayList<>(routesCS3);
        routesCS3NotTC.removeAll(routesTC);
        
        for (MarklinRoute newRoute : routesCS3NotTC)
        {
            System.out.println("CS3 Route:");
            System.out.println(newRoute.toVerboseString());
            
            for (MarklinRoute otherRoute : routesTC)
            {
                if (otherRoute.getId() == newRoute.getId() && !otherRoute.equalsUnordered(newRoute))
                {
                    System.out.println("Should be:");
                    System.out.println(otherRoute.toVerboseString());
                    List<RouteCommand> rc = otherRoute.getRoute();
                    
                    rc.removeAll(newRoute.getRoute());
                    
                    if (!rc.isEmpty())
                    {
                        System.out.println("!!! Correct values: " + rc.toString());
                    }
                }
            }
            
            System.out.println("============");
        }

        assertEquals(true, routesCS3NotTC.isEmpty());
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
