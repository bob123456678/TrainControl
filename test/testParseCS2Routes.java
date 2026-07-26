import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Builds the parsed-file representation of a single route, bypassing the text reader so that the
     * token order inside one "item" group can be controlled exactly.  parseFile flattens each group
     * through a HashMap, so the order it produces is not the order the file is written in.
     */
    private Map<String, String> cs2Route(int id, String name, String item)
    {
        Map<String, String> m = new HashMap<>();
        m.put("_type", "fahrstrasse");
        m.put("id", Integer.toString(id));
        m.put("name", name);
        m.put("item", item);
        return m;
    }

    private MarklinRoute parseOne(Map<String, String> route) throws Exception
    {
        List<Map<String, String>> in = new ArrayList<>();
        in.add(route);

        List<MarklinRoute> out = parser.parseRoutes(in, new ArrayList<MarklinAccessory>());

        assertEquals(out.size(), 1, "expected exactly one parsed route");
        return out.get(0);
    }

    /**
     * A sensor condition is stored exactly once per group, with the state that "hi=" asked for.
     *
     * The condition used to be stored once per token FOLLOWING "kont=", so a group ending in "hi=0"
     * produced "sensor occupied AND sensor clear" - unsatisfiable, meaning the route could never fire.
     * Storing it after every token in the group has been read also makes the result independent of the
     * order those tokens arrive in.
     */
    @Test
    public void testConditionIsStoredOnceWithTheRequestedState() throws Exception
    {
        model.newFeedback(7701, null);

        MarklinRoute r = parseOne(cs2Route(9501, "Condition state",
            "{magnetartikel=1,stellung=1,kont=7701,hi=0}"));

        List<RouteCommand> conditions = NodeExpression.toList(r.getConditions());

        assertEquals(conditions.size(), 1, "the condition must be stored exactly once");
        assertEquals(conditions.get(0).getAddress(), 7701);
        assertFalse(conditions.get(0).getSetting(), "hi=0 asks for the sensor to be clear");

        // And it is satisfiable, which the contradictory pair never was
        model.setFeedbackState("7701", false);
        assertTrue(r.getConditions().evaluate(model), "met when the sensor is clear");

        model.setFeedbackState("7701", true);
        assertFalse(r.getConditions().evaluate(model), "not met when it is occupied");
    }

    /**
     * The same group with hi=1 stores one condition too - it used to be duplicated.
     */
    @Test
    public void testConditionIsNotDuplicated() throws Exception
    {
        model.newFeedback(7702, null);

        MarklinRoute r = parseOne(cs2Route(9502, "Condition duplicate",
            "{magnetartikel=2,stellung=1,kont=7702,hi=1}"));

        List<RouteCommand> conditions = NodeExpression.toList(r.getConditions());

        assertEquals(conditions.size(), 1, "the condition must not be stored twice");
        assertTrue(conditions.get(0).getSetting(), "hi=1 asks for the sensor to be occupied");

        model.setFeedbackState("7702", true);
        assertTrue(r.getConditions().evaluate(model));
    }

    /**
     * A group whose last token is "kont=", with no explicit state, expects the sensor occupied.  This
     * case always parsed correctly, since there was no trailing token to duplicate it.
     */
    @Test
    public void testTrailingKontIsParsedCorrectly() throws Exception
    {
        model.newFeedback(7703, null);

        MarklinRoute r = parseOne(cs2Route(9503, "Condition single",
            "{magnetartikel=3,stellung=1,kont=7703}"));

        assertEquals(NodeExpression.toList(r.getConditions()).size(), 1, "no trailing token, no duplicate");
    }

    /**
     * The same thing on the real fixture: route "yi 14i" declares two S88Flag groups - sensor 15
     * occupied, and sensor 3 clear - and both must come through with the state the file asked for.
     */
    @Test
    public void testConditionsOnTheRealRouteFile()
    {
        MarklinRoute r = this.getRoute("yi 14i", routes_mags);

        assertNotNull(r, "route 'yi 14i' should be present in the fixture");

        List<RouteCommand> conditions = NodeExpression.toList(r.getConditions());

        assertEquals(conditions.size(), 2, "exactly two conditions, with no duplicates");

        assertEquals(conditions.get(0).getAddress(), 15);
        assertTrue(conditions.get(0).getSetting(), "sensor 15 must be occupied");

        assertEquals(conditions.get(1).getAddress(), 3);
        assertFalse(conditions.get(1).getSetting(), "sensor 3 must be clear (hi=0)");
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
