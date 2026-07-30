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

    /**
     * A fractional pause keeps its fraction.
     *
     * "sekunde" was read as Float.valueOf(text).intValue() * 1000 - truncated to whole seconds before
     * being scaled - so the operator's 2.3s became 2.0s.  Route "zyA01/02" in the shipped file carries
     * exactly that value, so the repository's own data lost 300ms on every import.
     */
    @Test
    public void testAFractionalPauseKeepsItsFraction()
    {
        MarklinRoute r = this.getRoute("zyA01/02", routes_mags);

        assertNotNull(r, "route 'zyA01/02' should be present in the fixture");

        RouteCommand paused = null;

        for (RouteCommand rc : r.getRoute())
        {
            if (rc.hasAddress() && rc.getAddress() == 8)
            {
                paused = rc;
            }
        }

        assertNotNull(paused, "the fixture route sets accessory 8");
        assertEquals(paused.getDelay(), 2300, "sekunde=2.3 is 2300ms - the fraction was truncated away");
    }

    /**
     * And a pause under one second survives at all.
     *
     * Truncation turned 0.5 into 0, and the delay > 0 guards then skipped setting any delay - so the
     * shortest pauses, the ones that exist to space accessories, were the ones lost completely.
     */
    @Test
    public void testASubSecondPauseIsNotLostEntirely() throws Exception
    {
        MarklinRoute r = parseOne(cs2Route(9504, "Half second", "{magnetartikel=4,stellung=1,sekunde=0.5}"));

        assertEquals(r.getRoute().size(), 1, "one command: " + r.getRoute());
        assertEquals(r.getRoute().get(0).getDelay(), 500, "0.5s is 500ms, not no pause at all");
    }

    /**
     * An item's pause attaches to that item's own command.
     *
     * It used to be applied with setDelay(address, ms), which searches the route and returns at the
     * first match.  A route that sets a turnout and later sets it back names one address twice, so the
     * second item's pause overwrote the first item's and left its own command with none.
     */
    @Test
    public void testAPauseLandsOnItsOwnItemNotAnEarlierOne() throws Exception
    {
        MarklinRoute r = parseOne(cs2Route(9505, "There and back",
            "{magnetartikel=3,stellung=1,sekunde=1}|{magnetartikel=3,stellung=0,sekunde=2}"));

        List<RouteCommand> commands = r.getRoute();

        assertEquals(commands.size(), 2, "one command per item: " + commands);

        assertEquals(commands.get(0).getAddress(), 3);
        assertEquals(commands.get(1).getAddress(), 3);

        assertEquals(commands.get(0).getDelay(), 1000, "the first item's own pause");
        assertEquals(commands.get(1).getDelay(), 2000,
            "and the second item's - the address search matched the first command both times, so this "
            + "pause overwrote the one above and this command was left with none");
    }
}
