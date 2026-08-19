import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.Edge;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import static org.traincontrol.gui.TrainControlUI.AUTONOMY_BLANK;
import static org.traincontrol.gui.TrainControlUI.AUTONOMY_SAMPLE;
import static org.traincontrol.gui.TrainControlUI.RESOURCE_PATH;
import org.traincontrol.marklin.MarklinRoute;

/**
 *
 */
public class testAutoLayout
{    
    public static MarklinControlStation model;
    
    public testAutoLayout()
    {
    }
    
    @Test
    public void testAutoRoute()
    { 
        Layout layout = model.getAutoLayout();
        
        MarklinRoute r = model.getRoute("Testcase Route 1");
        
        layout.setActivateRouteIDs(Collections.singletonList(r.getId()));
        layout.setActivateRoutes(false);
        
        assertEquals(r.isEnabled(), false);
        
        // This should not enable the route
        model.applyAutonomyRouteActivations();
        
        layout.setActivateRoutes(true);
        assertEquals(r.isEnabled(), false);
        
        // This should now enable the route
        model.applyAutonomyRouteActivations();
        
        assertEquals(r.isEnabled(), true);
        
        // And disable all other routes
        for (MarklinRoute otherRoute : model.getRoutes())
        {
            if (otherRoute.getId() == r.getId())
            {
                assertEquals(otherRoute.isEnabled(), true);
            }
            else
            {
                assertEquals(otherRoute.isEnabled(), false);
            }      
        }
    }
    
    /**
     * Test multi unit creation
     */
    @Test
    public void testMultiUnit()
    {         
        Layout layout = model.getAutoLayout();
        
        assertNotEquals(layout, null);
        
        System.out.println(layout.getPoints());

        // Fetch our locomotives
        MarklinLocomotive mu_1_2 = model.getLocByName("Test loc MU 1+2");
        MarklinLocomotive mu_3_2 = model.getLocByName("Test loc MU 3+2");

        MarklinLocomotive l1 = model.getLocByName("Test loc 1");
        MarklinLocomotive l1copy = model.getLocByName("Test loc 1 copy");
        
        MarklinLocomotive l2 = model.getLocByName("Test loc 2");
        MarklinLocomotive l3 = model.getLocByName("Test loc 3");
        MarklinLocomotive l4 = model.getLocByName("Test loc 4");
        MarklinLocomotive l5 = model.getLocByName("Test loc 5");

        MarklinLocomotive l1_dcc = model.getLocByName("Test loc 1 DCC");
        
        MarklinLocomotive mu_1_2_cs = model.getLocByName("Test loc MU CS");
        
        Map<String, Double> locList12 = new HashMap<String, Double>() {{ put(l1.getName(), 1.0); put(l2.getName(), -1.0); }};        
        Map<String, Double> locList2 = new HashMap<String, Double>() {{ put(l2.getName(), -1.0); }};
        Map<String, Double> locList1copy = new HashMap<String, Double>() {{ put(l1copy.getName(), 1.0); }};

        // Initialize multi-units
        mu_1_2_cs.setModelMultiUnitLocomotives(locList12);
        mu_1_2.preSetLinkedLocomotives(locList2);
        mu_3_2.preSetLinkedLocomotives(locList2);
        l5.preSetLinkedLocomotives(locList1copy);
        mu_1_2.setLinkedLocomotives();
        mu_3_2.setLinkedLocomotives();
        l5.setLinkedLocomotives();

        assertTrue(mu_1_2.getLinkedLocomotiveNames().containsKey(l2.getName()));
        assertTrue(mu_3_2.getLinkedLocomotiveNames().containsKey(l2.getName()));
        assertTrue(l5.getLinkedLocomotiveNames().containsKey(l1copy.getName()));

        // Place the locomotive on station 1
        layout.moveLocomotive(mu_1_2.getName(), "Station 1", true);
        
        assertEquals(layout.getLocomotiveLocation(mu_1_2), layout.getPoint("Station 1"));

        // Place the other mu on station 2.  Station 1 should be cleared.
        layout.moveLocomotive(mu_3_2.getName(), "Station 2", true);
        
        assertEquals(layout.getLocomotiveLocation(mu_1_2), null);
        assertEquals(layout.getLocomotiveLocation(mu_3_2), layout.getPoint("Station 2"));
        
        // Place loc 2 on station 1.  Station 2 should then be cleared
        layout.moveLocomotive(mu_3_2.getName(), "Station 2", true);
        layout.moveLocomotive(l2.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_3_2), null);
        assertEquals(layout.getLocomotiveLocation(l2), layout.getPoint("Station 1"));

        // Place loc 3 on station 1.  Station 2 should then be cleared
        layout.moveLocomotive(mu_3_2.getName(), "Station 2", true);
        layout.moveLocomotive(l3.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_3_2), null);
        assertEquals(layout.getLocomotiveLocation(l3), layout.getPoint("Station 1"));
        assertEquals(layout.getLocomotiveLocation(l2), null);

        // Place unrelated loc on station 2.  Station 1 should remain the same
        layout.moveLocomotive(l4.getName(), "Station 2", true);
        assertEquals(layout.getLocomotiveLocation(l3), layout.getPoint("Station 1"));
        assertEquals(layout.getLocomotiveLocation(l4), layout.getPoint("Station 2"));

        // Place MU 3_2 on another station.  l3 should be cleared from station 1
        layout.moveLocomotive(mu_3_2.getName(), "StationArrival", true);
        assertEquals(layout.getLocomotiveLocation(l3), null);
        assertEquals(layout.getLocomotiveLocation(mu_3_2), layout.getPoint("StationArrival"));
        
        // Place MU 1_2 on station 1.  MU3_2 should vanish.
        layout.moveLocomotive(mu_1_2.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_3_2), null);
        assertEquals(layout.getLocomotiveLocation(mu_1_2), layout.getPoint("Station 1"));
        assertEquals(layout.getLocomotiveLocation(l4), layout.getPoint("Station 2"));

        // Place dcc 1 on station 2.  MU 1_2 should stay put
        layout.moveLocomotive(l1_dcc.getName(), "Station 2", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2), layout.getPoint("Station 1"));
        assertEquals(layout.getLocomotiveLocation(l1_dcc), layout.getPoint("Station 2"));

        // Place locomotive 1 on station 1 and 1 dcc on station2, then add 1 copy to StationArrival and locomotive 1 should vanish
        layout.moveLocomotive(l1.getName(), "Station 1", true);
        layout.moveLocomotive(l1_dcc.getName(), "Station 2", true);
        assertEquals(layout.getLocomotiveLocation(l1_dcc), layout.getPoint("Station 2"));
        assertEquals(layout.getLocomotiveLocation(l1), layout.getPoint("Station 1"));
        layout.moveLocomotive(l1copy.getName(), "StationArrival", true);
        assertEquals(layout.getLocomotiveLocation(l1_dcc), layout.getPoint("Station 2"));
        assertEquals(layout.getLocomotiveLocation(l1), null);
        assertEquals(layout.getLocomotiveLocation(l1copy), layout.getPoint("StationArrival"));
        
        // l5 contains a locomotive with the same address as mu1_2 and l1. l5 should delete l1, and mu1_2 should delete l5
        layout.moveLocomotive(l1.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(l1), layout.getPoint("Station 1"));

        layout.moveLocomotive(l5.getName(), "Station 2", true);
        assertEquals(layout.getLocomotiveLocation(l1), null);
        assertEquals(layout.getLocomotiveLocation(l5), layout.getPoint("Station 2"));

        layout.moveLocomotive(mu_1_2.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2), layout.getPoint("Station 1"));
        assertEquals(layout.getLocomotiveLocation(l5), layout.getPoint(null));
        
        // Place CS MU, should overwrite the TC one
        layout.moveLocomotive(mu_1_2_cs.getName(), "Station 2", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2), layout.getPoint(null));
        assertEquals(layout.getLocomotiveLocation(mu_1_2_cs), layout.getPoint("Station 2"));

        // Vice versa
        layout.moveLocomotive(mu_1_2.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2_cs), layout.getPoint(null));
        assertEquals(layout.getLocomotiveLocation(mu_1_2), layout.getPoint("Station 1"));
        
        // Same with 3_2
        layout.moveLocomotive(mu_1_2_cs.getName(), "Station 2", true);
        layout.moveLocomotive(mu_3_2.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2_cs), layout.getPoint(null));
        assertEquals(layout.getLocomotiveLocation(mu_3_2), layout.getPoint("Station 1"));

        // Placing l1 should delete cs MU
        layout.moveLocomotive(mu_1_2_cs.getName(), "Station 2", true);
        layout.moveLocomotive(l1.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2_cs), layout.getPoint(null));
        assertEquals(layout.getLocomotiveLocation(l1), layout.getPoint("Station 1"));

        // Same for l2
        layout.moveLocomotive(mu_1_2_cs.getName(), "Station 2", true);
        layout.moveLocomotive(l2.getName(), "Station 1", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2_cs), layout.getPoint(null));
        assertEquals(layout.getLocomotiveLocation(l2), layout.getPoint("Station 1")); 
        
        // Should remove l2
        layout.moveLocomotive(mu_1_2_cs.getName(), "Station 2", true);
        assertEquals(layout.getLocomotiveLocation(mu_1_2_cs), layout.getPoint("Station 2"));
        assertEquals(layout.getLocomotiveLocation(l2), null);         
    }
    
    /**
     * Test connections
     * @throws java.lang.Exception
     */
    @Test
    public void testConnections() throws Exception
    {  
        Layout layout = model.getAutoLayout();
        
        assertTrue(layout.getNeighbors(layout.getPoint("Station 1")).get(0).getEnd().equals(layout.getPoint("Departure")));
        assertTrue(layout.getNeighbors(layout.getPoint("Station 2")).get(0).getEnd().equals(layout.getPoint("Departure")));
        assertTrue(layout.getNeighbors(layout.getPoint("Departure")).get(0).getEnd().equals(layout.getPoint("Main Track")));
        assertTrue(layout.getNeighbors(layout.getPoint("Main Track")).get(0).getEnd().equals(layout.getPoint("StationArrival")));
        assertEquals(layout.getNeighbors(layout.getPoint("StationArrival")).size(), 2);
        
        assertTrue(!layout.bfs(layout.getPoint("Station 1"), layout.getPoint("StationArrival"), null).isEmpty());
        assertTrue(!layout.bfs(layout.getPoint("Station 2"), layout.getPoint("StationArrival"), null).isEmpty());
        assertTrue(!layout.bfs(layout.getPoint("StationArrival"), layout.getPoint("Station 1"), null).isEmpty());
        assertTrue(!layout.bfs(layout.getPoint("StationArrival"), layout.getPoint("Station 2"), null).isEmpty());
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testAutoLayout.model = init(null, true, false, false, true); 
        model.stop();
        
        String s = 
            new BufferedReader(
                    new InputStreamReader(
                            TrainControlUI.class.getResource(RESOURCE_PATH + AUTONOMY_SAMPLE).openStream())
                    ).lines().collect(Collectors.joining("\n"));
        
        model.parseAuto(s);
        
        model.newMM2Locomotive("Test loc MU 1+2", 1);
        model.newMM2Locomotive("Test loc MU 3+2", 3);

        model.newMM2Locomotive("Test loc 1", 1);
        model.newMM2Locomotive("Test loc 1 copy", 1);

        model.newMM2Locomotive("Test loc 2", 2);
        model.newMM2Locomotive("Test loc 3", 3);
        model.newMM2Locomotive("Test loc 4", 4);
        model.newMM2Locomotive("Test loc 5", 5);

        model.newDCCLocomotive("Test loc 1 DCC", 1);
        
        model.newDCCLocomotive("Test loc MU CS", 2);
        model.changeLocAddress("Test loc MU CS", 100, MarklinLocomotive.decoderType.MULTI_UNIT);
        
        model.newRoute("Testcase Route 1", 
                Collections.singletonList(RouteCommand.RouteCommandStop()),
                2001, MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR, false, null);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        model.deleteLoc("Test loc MU 1+2");
        model.deleteLoc("Test loc MU 3+2");

        model.deleteLoc("Test loc 1");
        model.deleteLoc("Test loc 1 copy");
        
        model.deleteLoc("Test loc 2");
        model.deleteLoc("Test loc 3");
        model.deleteLoc("Test loc 4");
        model.deleteLoc("Test loc 5");

        model.deleteLoc("Test loc 1 DCC");
        
        model.deleteLoc("Test loc MU CS");
        
        model.deleteRoute("Testcase Route 1");
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
     * A locomotive cannot be in two places, and the model will not represent it.
     *
     * It used to be left to the callers.  moveLocomotive swept the graph but stopped at the FIRST copy
     * it found - written when a station was one Point, and a square has been several since - and
     * parseAuto placed straight from the file without looking at all.  So a locomotive could end up
     * standing on two copies of one platform: the diagram showed one of them, removing it cleared one
     * of them, and the next build produced a configuration fromJSON refused outright.
     *
     * Enforced in Point now, so no caller has to remember.  A Point with no layout behind it - which is
     * every Point built by hand - is untouched by this.
     */
    @Test
    public void testALocomotiveCanOnlyBeInOnePlace() throws Exception
    {
        Layout layout = new Layout(model);

        Point first = layout.createPoint("EX_First", true, "1");
        Point second = layout.createPoint("EX_Second", true, "2");
        Point third = layout.createPoint("EX_Third", true, "3");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        first.setLocomotive(loc);
        second.setLocomotive(loc);

        assertNull(first.getCurrentLocomotive(),
            "the locomotive is standing in two places, which nothing physical can do");

        assertEquals(second.getCurrentLocomotive(), loc);

        // and from a state that already had it twice - which is what an older configuration file, or a
        // build from before this rule, hands over
        first.setLocomotive(loc);

        assertEquals(first.getCurrentLocomotive(), loc);
        assertNull(second.getCurrentLocomotive());

        third.setLocomotive(loc);

        assertNull(first.getCurrentLocomotive(), "every other copy is cleared, not just the first");
        assertNull(second.getCurrentLocomotive());
        assertEquals(third.getCurrentLocomotive(), loc);
    }

    /**
     * Clearing a Point does not disturb anything else.
     *
     * The sweep only runs when a locomotive is being PUT somewhere.  Setting null is a removal, and a
     * removal that swept would be a removal that could take a different train off a different platform.
     */
    @Test
    public void testRemovingALocomotiveLeavesTheOthersAlone() throws Exception
    {
        Layout layout = new Layout(model);

        Point one = layout.createPoint("EX_One", true, "1");
        Point two = layout.createPoint("EX_Two", true, "2");

        Locomotive a = model.getLocByName(model.getLocList().get(0));
        Locomotive b = model.getLocByName(model.getLocList().get(1));

        one.setLocomotive(a);
        two.setLocomotive(b);

        one.setLocomotive(null);

        assertNull(one.getCurrentLocomotive());
        assertEquals(two.getCurrentLocomotive(), b, "a removal swept a platform it had no business at");
    }

    /**
     * Locking a path reserves every point along it, not only its destination.
     *
     * The reservation is what holds a junction the train has passed against a second train that could
     * reach it another way - that train reads the point's occupancy and its own path is refused.  When
     * placing a locomotive was made to sweep it off every other point, locking A->B->C swept each point
     * as the next was taken, so the train held C alone and B was free for anyone.  Reserving a path and
     * placing a train are different operations and no longer share an entry point.
     *
     * In simulate mode, so no accessory has to confirm and this runs without a screen.
     */
    @Test
    public void testLockingAPathReservesEveryPointOnIt() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RS_A", false, null);
        layout.createPoint("RS_B", true, "1");
        layout.createPoint("RS_C", true, "2");

        Edge ab = layout.createEdge("RS_A", "RS_B");
        Edge bc = layout.createEdge("RS_B", "RS_C");

        layout.setSimulate(true);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // where the train stands before it departs
        layout.getPoint("RS_A").setLocomotive(loc);

        boolean locked = layout.configureAndLockPath(java.util.Arrays.asList(ab, bc), loc);

        assertTrue(locked, "the clean path should lock in simulation");

        assertEquals(layout.getPoint("RS_A").getCurrentLocomotive(), loc,
            "the start was swept off its own reservation");

        assertEquals(layout.getPoint("RS_B").getCurrentLocomotive(), loc,
            "the junction the train must pass was left free for another train");

        assertEquals(layout.getPoint("RS_C").getCurrentLocomotive(), loc,
            "the destination was not reserved");
    }

    /**
     * Two trains cannot be routed onto one square, however many Points that square became.
     *
     * A square of the diagram is emitted as several Points - one per side a train can arrive by - and
     * they are the same piece of track.  Occupancy was recorded per Point, so the westbound copy of a
     * platform read free while a train stood on the eastbound one, and a second train could be given a
     * path onto it.  There is no version of that which is not a collision.
     *
     * The block is what ties the copies together.  Not the s88: genuinely different places share a
     * sensor on a real layout - a station, its approach guard and a reversing point can be three Points
     * on one feedback - so the sensor cannot say which Points are one square.
     */
    @Test
    public void testASecondTrainIsNotRoutedOntoAnOccupiedSquare() throws Exception
    {
        Layout layout = new Layout(model);

        // one square, two arrival-side copies, plus somewhere for each train to start
        layout.createPoint("BK_WestApproach", false, null);
        layout.createPoint("BK_EastApproach", false, null);

        Point eastbound = layout.createPoint("BK_Platform (eastbound)", true, "1");
        Point westbound = layout.createPoint("BK_Platform (westbound)", true, "2");

        // the copies are one piece of track - which only the builder can say, so it is said here
        eastbound.setBlock("main:5,5");
        westbound.setBlock("main:5,5");

        Edge toEast = layout.createEdge("BK_WestApproach", "BK_Platform (eastbound)");
        Edge toWest = layout.createEdge("BK_EastApproach", "BK_Platform (westbound)");

        Locomotive first = model.getLocByName(model.getLocList().get(0));
        Locomotive second = model.getLocByName(model.getLocList().get(1));

        // the first train is standing on the platform, on the eastbound copy
        eastbound.setLocomotive(first);

        assertEquals(westbound.getBlockLocomotive(), first,
            "the other copy of an occupied platform is not free - it is the same track");

        assertTrue(toWest.isOccupied(second),
            "a second train was offered the platform its twin is standing on");

        // and the copy the train is actually on is still occupied for anyone else
        assertTrue(toEast.isOccupied(second));

        // But a LOCK edge asks the narrower question.  A lock edge is track held clear so that two
        // routes cannot take one throat at once; it is not a claim on the platform beyond it, and a
        // train standing there is not in the way of a train merely using the throat.
        //
        // Asked of the whole square, a pair of converging platforms refused every route out of
        // either of them whenever either had a train on it - which is to say always, and which is
        // what made autonomy look dead: bfs found routes and every one was refused.
        assertFalse(toWest.isLockHeld(second),
            "a lock edge must not be blocked by a train standing on the far platform");

        assertTrue(toWest.isOccupied(second, true),
            "while running ONTO that track is still refused - the copies are one piece of rail");

        // And the same of the copy the train is ACTUALLY standing on, which is the half of this the
        // first fix left out.  The reason a lock edge does not care about the far platform is that the
        // sensor a train stands on is never the track a lock edge protects: reduction cuts an edge at
        // every sensor, so a Point's tile is an endpoint of its edges and appears in the path of none
        // of them.  That is as true of the copy the train is on as of its twin.
        //
        // Left in, it made any train parked next to a junction a permanent roadblock for every route
        // across that junction - and with two such trains, a deadlock neither could leave.
        assertFalse(toEast.isLockHeld(second),
            "a lock edge must not be blocked by a train standing at the point it leads to");

        // But a lock edge another route is HOLDING is refused, which is the whole mechanism.  Symmetric
        // locks are what makes the narrow question above safe, so this is the assertion that carries it.
        toEast.setOccupied();

        assertTrue(toEast.isLockHeld(second),
            "a lock edge held by another route must refuse this one");

        toEast.setUnoccupied();

        // while the train already there is not blocked by itself
        assertFalse(toEast.isOccupied(first));
        assertFalse(toWest.isOccupied(first));
    }

    /**
     * The route preference defaults to what every earlier version did, and nothing has to be set for
     * that to be true.
     *
     * The point of the default is that upgrading changes nothing.  A railway driven from a script has
     * no menu to look at, so the one thing this preference must never do is quietly re-route somebody
     * else's trains the moment they install a new build.
     */
    @Test
    public void testTheRoutePreferenceDefaultsToTheOldBehaviour()
    {
        assertEquals(Layout.getPathPreference(), Layout.PathPreference.RANDOM,
            "the default must be the behaviour existing layouts already have");
    }

    /**
     * Each rule measures a route by the thing it says it measures.
     *
     * Two routes to one place: a short one past two stations, and a longer way round past none.  Every
     * preference should pick a different winner, which is the only evidence that the setting does
     * anything at all - a comparator that always returns the same route is indistinguishable from no
     * comparator.
     *
     * The cost function is exercised through pickPath's own choice rather than called directly, so this
     * fails if the ranking is wired up wrongly as well as if it is computed wrongly.
     */
    @Test
    public void testEachRuleMeasuresWhatItSaysItMeasures() throws Exception
    {
        Layout.PathPreference was = Layout.getPathPreference();

        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(91, null);
        MarklinFeedback middleA = model.newFeedback(92, null);
        MarklinFeedback middleB = model.newFeedback(93, null);
        MarklinFeedback middleC = model.newFeedback(94, null);
        MarklinFeedback end = model.newFeedback(95, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{start, middleA, middleB, middleC, end})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        // The short way: two hops, and both intermediate squares are stations
        layout.createPoint("RP_Start", true, start.getName());
        layout.createPoint("RP_ViaStation", true, middleA.getName());
        layout.createPoint("RP_End", true, end.getName());

        // The long way: three hops, and neither intermediate square is a station
        layout.createPoint("RP_Plain1", false, middleB.getName());
        layout.createPoint("RP_Plain2", false, middleC.getName());

        // short route, 2 edges, 1 station passed, 100 long
        layout.createEdge("RP_Start", "RP_ViaStation").setLength(50);
        layout.createEdge("RP_ViaStation", "RP_End").setLength(50);

        // long route, 3 edges, 0 stations passed, 12 long
        layout.createEdge("RP_Start", "RP_Plain1").setLength(4);
        layout.createEdge("RP_Plain1", "RP_Plain2").setLength(4);
        layout.createEdge("RP_Plain2", "RP_End").setLength(4);

        // A station, but not one autonomy may send a train TO.
        //
        // Otherwise the fixture cannot tell the two rules apart: "go to RP_ViaStation" is itself a
        // route past no stations, so fewest-stations would pick it and the assertion below could not
        // say whether it had measured the route or simply chosen a nearer destination.
        layout.getPoint("RP_ViaStation").setAutoDestination(false);

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "RP_Start", false);

        try
        {
            Layout.setPathPreference(Layout.PathPreference.FEWEST_POINTS);

            assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "RP_ViaStation",
                "fewest sensors must take the two-hop route");

            Layout.setPathPreference(Layout.PathPreference.FEWEST_STATIONS);

            assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "RP_Plain1",
                "fewest stations must take the way round that passes none");

            Layout.setPathPreference(Layout.PathPreference.SHORTEST_LENGTH);

            assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "RP_Plain1",
                "shortest track must take the 12-long route over the 100-long one");
        }
        finally
        {
            Layout.setPathPreference(was);
        }
    }

    /**
     * The name of the point a route reaches first, which is what says which way it went.
     */
    private String nameOfSecondPoint(java.util.List<Edge> path)
    {
        assertNotNull(path, "no route was offered at all");

        return path.get(0).getEnd().getName();
    }

    /**
     * Everything parseAuto reads off a Point, toJSON writes back.
     *
     * The configuration JSON is the interchange format - exported, hand-edited in the advanced tab,
     * imported on another machine - so a field the reader understands and the writer omits is silently
     * lossy: the setup comes back missing something, and nothing anywhere says so.
     *
     * This has happened twice.  "block" was omitted, so an export and import undid the block-occupancy
     * rule and put the railway back to routing two trains onto one platform.  Then "protectingSignal"
     * was omitted the same way, and a setup round-tripped came back with every station-signal pairing
     * gone.  Both are asserted together, because the failure is not really about either field - it is
     * the two halves of the format drifting apart, and the next field added will drift the same way
     * unless something is watching.
     */
    @Test
    public void testEveryFieldParseAutoReadsIsAlsoWritten() throws Exception
    {
        Layout layout = model.getAutoLayout();

        Point point = layout.getPoints().iterator().next();

        String was = point.getBlock();
        String hadSignal = point.getProtectingSignal();

        try
        {
            point.setBlock("main:9,9");
            point.setProtectingSignal("Signal 12");

            org.json.JSONObject json = point.toJSON();

            assertEquals(json.optString("block", null), "main:9,9",
                "the block a square shares was not exported, so an import would undo the block rule");

            assertEquals(json.optString("protectingSignal", null), "Signal 12",
                "the protecting signal was not exported, so an import would lose every pairing");
        }
        finally
        {
            point.setBlock(was);
            point.setProtectingSignal(hadSignal);
        }
    }

    /**
     * A Point with no block is unchanged - which is every Point of a hand-written configuration.
     *
     * Points that share an s88 without sharing a square are ordinary on a real layout, and they must
     * not start blocking each other: that would refuse paths that have always been safe.
     */
    @Test
    public void testPointsThatMerelyShareASensorDoNotBlockEachOther() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("BK_Start", false, null);

        // two DIFFERENT places that happen to report on one feedback, as a real layout has
        Point guard = layout.createPoint("BK_ApproachGuard", true, "1");
        Point station = layout.createPoint("BK_Station", true, "1");

        Edge toStation = layout.createEdge("BK_Start", "BK_Station");

        Locomotive first = model.getLocByName(model.getLocList().get(0));
        Locomotive second = model.getLocByName(model.getLocList().get(1));

        guard.setLocomotive(first);

        assertNull(station.getBlockLocomotive(),
            "these are different places that share a sensor, not one square");

        assertFalse(toStation.isOccupied(second),
            "a shared sensor must not block a path the layout has always allowed");
    }

    /**
     * Somewhere to GO is not the same as somewhere to be sent.
     *
     * This is the fault behind "I place a train and it never moves".  A square is emitted as one Point
     * per arrival side, and placement picked among the copies at random, keeping any copy that had an
     * outgoing edge.  But a copy can have somewhere to go and nowhere to be DISPATCHED - everything it
     * reaches is a plain point, a reversing point or parking - and autonomy only ever sends a train to
     * a destination.  On the sample layout Tunnel (northbound) offers routes and Tunnel (southbound)
     * offers none, and placement could not tell them apart.
     */
    @Test
    public void testACopyWithNowhereToBeSentIsNotPlaceable() throws Exception
    {
        Layout layout = new Layout(model);

        // the two arrival copies of one platform
        Point northbound = layout.createPoint("RD_Platform (northbound)", true, "1");
        Point southbound = layout.createPoint("RD_Platform (southbound)", true, "2");

        // northbound leads on to a real station; southbound leads only to a plain point
        Point onward = layout.createPoint("RD_Onward", true, "3");
        Point deadEnd = layout.createPoint("RD_PlainPoint", false, null);

        layout.createEdge("RD_Platform (northbound)", "RD_Onward");
        layout.createEdge("RD_Platform (southbound)", "RD_PlainPoint");

        assertTrue(layout.canReachAnyDestination(northbound),
            "this copy reaches a station and a train placed here can be dispatched");

        assertFalse(layout.canReachAnyDestination(southbound),
            "this copy has an outgoing edge and nowhere to be SENT - a train here never moves");

        // and the plain point itself is a place with no destination beyond it
        assertFalse(layout.canReachAnyDestination(deadEnd));
    }

    /**
     * Parking does not count as somewhere to be sent.
     *
     * A berth is a station autonomy is told not to choose, so a copy whose only reachable station is a
     * berth is still a copy a train would sit on forever.
     */
    @Test
    public void testParkingDoesNotMakeACopyPlaceable() throws Exception
    {
        Layout layout = new Layout(model);

        Point from = layout.createPoint("RD_From", true, "1");
        Point berth = layout.createPoint("RD_Berth", true, "2");

        layout.createEdge("RD_From", "RD_Berth");

        assertTrue(layout.canReachAnyDestination(from), "precondition: a plain station counts");

        berth.setAutoDestination(false);

        assertFalse(layout.canReachAnyDestination(from),
            "a berth is somewhere autonomy will not send a train, so it is not somewhere to go");
    }

}
