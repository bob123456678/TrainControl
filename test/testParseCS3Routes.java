import base.RouteCommand;
import static base.RouteCommand.commandType.TYPE_ACCESSORY;
import static base.RouteCommand.commandType.TYPE_FUNCTION;
import static base.RouteCommand.commandType.TYPE_LOCOMOTIVE;
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
    public static String pathprefix = "";
    private static String cs2_routes = "File:///" + pathprefix + "CS2_fahrstrassen.json";
    private static String cs3_mags = "File:///" + pathprefix + "CS3_mags.json";
    private static String cs3_automatics = "File:///" + pathprefix + "CS3_automatics.json";
    
    public MarklinControlStation model;
    public List<MarklinRoute> routesCS2;
    public CS2File parser;
    public List<MarklinRoute> routesCS3;
            
    public testParseCS3Routes() throws Exception
    {
        parser = new CS2File(null, null);
        model = init(null, true, false, false, false); 
        
        // We assume this data is correct
        // routesCS2 = parser.parseRoutes(parseFile(fetchURL(cs2_routes)));
        routesCS2 = model.parseRoutesFromJson(parseJSONObject(fetchURL(cs2_routes)).toString()); 
        
        routesCS3 = parser.parseRoutesCS3(parseJSONObject(fetchURL(cs3_automatics)), parseJSONArray(fetchURL(cs3_mags)));   
    }
   
    /**
     *
     */
    @Test
    public void testSameLength()
    {   
        assertEquals(routesCS2.size(), routesCS3.size());
    }
    
    /**
     * Checks if there are any extra routes in the CS2 DB
     */
    @Test
    public void testCS2()
    {           
        List<MarklinRoute> routesCS2Not3 = new ArrayList<>(routesCS2);
        routesCS2Not3.removeAll(routesCS3);
                
        for (MarklinRoute newRoute : routesCS2Not3)
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
                System.out.println("CS2 route not in CS3 file:");
                System.out.println(newRoute.toVerboseString()); 
                System.out.println("============");
            }
        }
        
        assertEquals(true, routesCS2Not3.isEmpty());
    }
   
    
    @Test 
    public void testCS3()
    {
        List<MarklinRoute> routesCS3Not2 = new ArrayList<>(routesCS3);
        routesCS3Not2.removeAll(routesCS2);
        
        for (MarklinRoute newRoute : routesCS3Not2)
        {
            System.out.println("CS3 Route:");
            System.out.println(newRoute.toVerboseString());
            
            for (MarklinRoute otherRoute : routesCS2)
            {
                if (otherRoute.getId() == newRoute.getId() && !otherRoute.equalsUnordered(newRoute))
                {
                    System.out.println("Should be:");
                    System.out.println(otherRoute.toVerboseString());
                }
            }
            
            System.out.println("============");
        }

        assertEquals(true, routesCS3Not2.isEmpty());
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
