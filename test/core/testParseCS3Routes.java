package core;

import org.traincontrol.base.RouteCommand;
import java.util.ArrayList;
import java.util.List;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.file.CS2File.fetchURL;
import static org.traincontrol.marklin.file.CS2File.parseJSONArray;
import static org.traincontrol.marklin.file.CS2File.parseJSONObject;
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
    private final String tc_routes = getClass().getResource("/TC_routes.json").toURI().toString();
    private final String cs3_mags = getClass().getResource("/CS3_mags.json").toURI().toString();
    private final String cs3_automatics = getClass().getResource("/CS3_automatics.json").toURI().toString();
    private final String cs3_automatics_v260 = getClass().getResource("/CS3_automatics_v260.json").toURI().toString();
    private final String cs3_loks = getClass().getResource("/CS3_loks.json").toURI().toString();

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
        
        routesCS3 = parser.parseRoutesCS3(parseJSONObject(fetchURL(cs3_automatics)).getJSONArray("automatics"), parseJSONArray(fetchURL(cs3_mags)), parseJSONArray(fetchURL(cs3_loks)));           
    }
   
    /**
     * Ensures our saved routes match newly parsed routes
     * @throws java.lang.Exception
     */
    @Test
    public void testSameLength() throws Exception
    {   
        assertEquals(routesTC.size(), routesCS3.size());
    }
    
    /**
     * Test new format introduced in the new version of CS3
     * @throws java.lang.Exception
     */
    @Test
    public void testVersion260Format() throws Exception
    {   
        assertEquals(routesCS3, parser.parseRoutesCS3(parseJSONArray(fetchURL(cs3_automatics_v260)), parseJSONArray(fetchURL(cs3_mags)), parseJSONArray(fetchURL(cs3_loks))));
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

                    // TST-B20: Route.getRoute() returns the LIVE list, not a copy - removeAll() on it
                    // directly emptied the route it was diagnosing, corrupting routesTC (shared, and
                    // read by whatever runs after this in the same JVM) on the failure path this
                    // diagnostic exists for.  Copied here so the diff below is read-only towards
                    // otherRoute.
                    List<RouteCommand> rc = new ArrayList<>(otherRoute.getRoute());

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

    @AfterClass(alwaysRun = true)
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

    // ---------------------------------------------------------------------------------------------
    // Three-way turnouts in CS3 routes
    //
    // The shipped fixtures do not reach this code: CS3_mags.json holds exactly one dreiwegweiche
    // (id 8, address 5, mm) and not one of the 50 routes in CS3_automatics.json references it.  So the
    // routes below are built here and parsed against the real accessory list.
    // ---------------------------------------------------------------------------------------------

    /** The mag id of the fixture's only three-way, whose address is 5. */
    private static final int THREE_WAY_MAG_ID = 8;
    private static final int THREE_WAY_ADDRESS = 5;

    /**
     * A CS3 route naming the fixture's three-way at the given stellung, parsed against the real mags.
     */
    private MarklinRoute parseThreeWayRoute(String stellung, String name) throws Exception
    {
        org.json.JSONObject item = new org.json.JSONObject();

        item.put("typ", "mag");
        item.put("magnetartikel", THREE_WAY_MAG_ID);

        if (stellung != null) item.put("stellung", stellung);

        org.json.JSONObject route = new org.json.JSONObject();

        route.put("id", 9001);
        route.put("name", name);
        route.put("items", new org.json.JSONArray().put(item));

        List<MarklinRoute> parsed = parser.parseRoutesCS3(
            new org.json.JSONArray().put(route),
            parseJSONArray(fetchURL(cs3_mags)),
            parseJSONArray(fetchURL(cs3_loks)));

        assertEquals(parsed.size(), 1, "expected exactly one parsed route");

        return parsed.get(0);
    }

    /**
     * A three-way yields both of its drives, released before thrown, with the pair spaced apart.
     *
     * Two things are pinned, and they broke separately.  The order: a three-way is two drives on
     * consecutive addresses, and commanding the diverging one before the other is released puts both
     * blade sets over at once - a combination that routes nowhere.  The gap: route execution sleeps
     * SLEEP_INTERVAL plus the command's own delay, so without a delay on the first command the pair
     * fires DEFAULT_SLEEP_MS apart, closer together than the track diagram allows for the same turnout.
     */
    @Test
    public void testThreeWayRouteReleasesBeforeThrowing() throws Exception
    {
        MarklinRoute left = parseThreeWayRoute("0", "TW left");

        assertEquals(left.getRoute().size(), 2, "a three-way is two commands: " + left.getRoute());

        RouteCommand first = left.getRoute().get(0);
        RouteCommand second = left.getRoute().get(1);

        assertEquals(first.getAddress(), THREE_WAY_ADDRESS + 1, "the released drive comes first");
        assertFalse(first.getSetting(), "and it is released, not thrown");

        assertEquals(second.getAddress(), THREE_WAY_ADDRESS, "the thrown drive comes second");
        assertTrue(second.getSetting(), "and it is the one thrown");

        assertEquals(first.getDelay(), MarklinRoute.THREEWAY_ROUTE_DELAY_MS,
            "the gap has to sit on the FIRST command - execRoute sleeps after each one, so a delay on "
            + "the second would space this pair from whatever follows instead");

        // The other diverging position, which throws the other drive
        MarklinRoute right = parseThreeWayRoute("2", "TW right");

        assertEquals(right.getRoute().size(), 2, "also two commands: " + right.getRoute());
        assertEquals(right.getRoute().get(0).getAddress(), THREE_WAY_ADDRESS, "released first");
        assertFalse(right.getRoute().get(0).getSetting(), "and released, not thrown");
        assertTrue(right.getRoute().get(1).getSetting(), "the second is the thrown one");
        assertEquals(right.getRoute().get(0).getDelay(), MarklinRoute.THREEWAY_ROUTE_DELAY_MS);
    }

    /**
     * Neither drive is ever thrown at the same time as the other.
     *
     * Both-thrown is the state with no valid route through the turnout, so no stellung may produce it -
     * this asks the question of every position rather than of the two that happen to be interesting.
     */
    @Test
    public void testNoThreeWayPositionThrowsBothDrives() throws Exception
    {
        for (String stellung : new String[] { null, "0", "1", "2", "3" })
        {
            MarklinRoute r = parseThreeWayRoute(stellung, "TW " + stellung);

            int thrown = 0;

            for (RouteCommand rc : r.getRoute())
            {
                if (rc.getSetting()) thrown++;
            }

            assertTrue(thrown <= 1,
                "stellung " + stellung + " throws " + thrown + " drives at once: " + r.getRoute());
        }
    }

    /**
     * A route naming the same three-way twice spaces both of its pairs.
     *
     * This is why the delay is set on the command object rather than through
     * MarklinRoute.setDelay(address, ms): that searches the route for the address and returns at the
     * first match, so the second pair would have been left unspaced - and a route that sets a turnout,
     * runs a locomotive past it and sets it back is an ordinary thing to build.
     */
    @Test
    public void testEveryThreeWayPairInARouteIsSpaced() throws Exception
    {
        org.json.JSONArray items = new org.json.JSONArray();

        for (String stellung : new String[] { "0", "2" })
        {
            org.json.JSONObject item = new org.json.JSONObject();

            item.put("typ", "mag");
            item.put("magnetartikel", THREE_WAY_MAG_ID);
            item.put("stellung", stellung);

            items.put(item);
        }

        org.json.JSONObject route = new org.json.JSONObject();

        route.put("id", 9002);
        route.put("name", "TW twice");
        route.put("items", items);

        List<MarklinRoute> parsed = parser.parseRoutesCS3(
            new org.json.JSONArray().put(route),
            parseJSONArray(fetchURL(cs3_mags)),
            parseJSONArray(fetchURL(cs3_loks)));

        assertEquals(parsed.size(), 1);

        List<RouteCommand> commands = parsed.get(0).getRoute();

        assertEquals(commands.size(), 4, "two three-ways, two commands each: " + commands);

        assertEquals(commands.get(0).getDelay(), MarklinRoute.THREEWAY_ROUTE_DELAY_MS,
            "the first pair is spaced");
        assertEquals(commands.get(2).getDelay(), MarklinRoute.THREEWAY_ROUTE_DELAY_MS,
            "and so is the second - an address-keyed delay would have stopped at the first");
    }

    /**
     * An ordinary two-state accessory still yields exactly one command.
     *
     * The pair is emitted on the strength of the accessory's typ, so a plain turnout must be untouched
     * by all of the above - otherwise every route in the fixture would have grown a phantom command on
     * address + 1, which is a far worse bug than the one being fixed.
     */
    @Test
    public void testAnOrdinaryTurnoutStillYieldsOneCommand() throws Exception
    {
        org.json.JSONArray mags = parseJSONArray(fetchURL(cs3_mags));

        // -1 as the sentinel, not 0: the fixture's first plain turnout has id 0, and a > 0 check
        // rejected it
        int plainId = -1;

        for (int i = 0; i < mags.length(); i++)
        {
            org.json.JSONObject m = mags.getJSONObject(i);

            if ("linksweiche".equals(m.optString("typ")))
            {
                plainId = m.getInt("id");
                break;
            }
        }

        assertTrue(plainId >= 0, "precondition: the fixture must contain a plain turnout");

        org.json.JSONObject item = new org.json.JSONObject();

        item.put("typ", "mag");
        item.put("magnetartikel", plainId);
        item.put("stellung", "0");

        org.json.JSONObject route = new org.json.JSONObject();

        route.put("id", 9003);
        route.put("name", "plain turnout");
        route.put("items", new org.json.JSONArray().put(item));

        List<MarklinRoute> parsed = parser.parseRoutesCS3(
            new org.json.JSONArray().put(route), mags, parseJSONArray(fetchURL(cs3_loks)));

        assertEquals(parsed.size(), 1, "the route must parse, not be skipped");

        assertEquals(parsed.get(0).getRoute().size(), 1,
            "a two-state accessory is one command: " + parsed.get(0).getRoute());
    }

    /**
     * An item's own pause attaches after the three-way, not between its two drives.
     *
     * The CS records a pause per route item as "sekunde", meaning "wait this long before the next one".
     * It used to be applied by searching the route for the item's address, and that search returns at
     * the first match - so where the pair is emitted address-then-address+1, the pause landed on the
     * first of the two drives.  That did the wrong thing twice: it spaced the turnout's own two commands
     * by the operator's figure instead of the gap they need, and it never delayed anything before the
     * following item.
     */
    @Test
    public void testAnItemPauseGoesAfterTheThreeWayNotInsideIt() throws Exception
    {
        // stellung 2 emits address then address + 1, which is the order that used to be mishandled
        org.json.JSONObject item = new org.json.JSONObject();

        item.put("typ", "mag");
        item.put("magnetartikel", THREE_WAY_MAG_ID);
        item.put("stellung", "2");
        item.put("sekunde", 0.5);

        org.json.JSONObject route = new org.json.JSONObject();

        route.put("id", 9004);
        route.put("name", "TW paused");
        route.put("items", new org.json.JSONArray().put(item));

        List<MarklinRoute> parsed = parser.parseRoutesCS3(
            new org.json.JSONArray().put(route),
            parseJSONArray(fetchURL(cs3_mags)),
            parseJSONArray(fetchURL(cs3_loks)));

        assertEquals(parsed.size(), 1, "the route must parse, not be skipped");

        List<RouteCommand> commands = parsed.get(0).getRoute();

        assertEquals(commands.size(), 2, "still the two drives: " + commands);

        assertEquals(commands.get(0).getDelay(), MarklinRoute.THREEWAY_ROUTE_DELAY_MS,
            "the gap between the two drives has to survive the operator's pause");

        assertEquals(commands.get(1).getDelay(), 500,
            "and the pause belongs on the last command of the item, where it delays the next item");
    }
}
