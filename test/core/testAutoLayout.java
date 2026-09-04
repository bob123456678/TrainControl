package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
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
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.base.Accessory;
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

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null) model.deleteLoc("Test loc MU 1+2");
        if (model != null) model.deleteLoc("Test loc MU 3+2");

        if (model != null) model.deleteLoc("Test loc 1");
        if (model != null) model.deleteLoc("Test loc 1 copy");
        
        if (model != null) model.deleteLoc("Test loc 2");
        if (model != null) model.deleteLoc("Test loc 3");
        if (model != null) model.deleteLoc("Test loc 4");
        if (model != null) model.deleteLoc("Test loc 5");

        if (model != null) model.deleteLoc("Test loc 1 DCC");
        
        if (model != null) model.deleteLoc("Test loc MU CS");
        
        if (model != null) model.deleteRoute("Testcase Route 1");
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
        assertEquals(new Layout(model).getPathPreference(), Layout.PathPreference.RANDOM,
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
        // No save and restore: the rule belongs to this Layout, so it cannot reach another test.
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

        layout.setPathPreference(Layout.PathPreference.FEWEST_POINTS);

        assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "RP_ViaStation",
            "fewest sensors must take the two-hop route");

        layout.setPathPreference(Layout.PathPreference.FEWEST_STATIONS);

        assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "RP_Plain1",
            "fewest stations must take the way round that passes none");

        layout.setPathPreference(Layout.PathPreference.SHORTEST_LENGTH);

        assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "RP_Plain1",
            "shortest track must take the 12-long route over the 100-long one");
    }

    /**
     * "Fewest sensors" counts SENSORS, not hops of the running graph.
     *
     * On a derived graph a square is several Points - one per arrival side - and they share a block.
     * Counting hops therefore counts the model's own structure: two routes crossing exactly the same
     * physical s88s can come out with different numbers, and the route that "wins" wins for a reason
     * nothing on the diagram shows.
     *
     * The fixture makes that concrete.  One way round goes through two Points that are two copies of a
     * single square; the other goes through two genuinely different squares.  By hops they are equal.
     * By sensors the first is one shorter, and that is the one it must take.
     */
    @Test
    public void testFewestSensorsCountsSensorsAndNotGraphHops() throws Exception
    {
        // No save and restore: the rule belongs to this Layout, so it cannot reach another test.
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(81, null);
        MarklinFeedback shared = model.newFeedback(82, null);
        MarklinFeedback plainA = model.newFeedback(83, null);
        MarklinFeedback plainB = model.newFeedback(84, null);
        MarklinFeedback end = model.newFeedback(85, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{start, shared, plainA, plainB, end})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("SC_Start", true, start.getName());
        layout.createPoint("SC_End", true, end.getName());

        // Two copies of ONE square, the way the builder emits an arrival-side split: different Points,
        // same block
        layout.createPoint("SC_Split1", false, shared.getName());
        layout.createPoint("SC_Split2", false, shared.getName());

        layout.getPoint("SC_Split1").setBlock("SC_SharedSquare");
        layout.getPoint("SC_Split2").setBlock("SC_SharedSquare");

        // Two genuinely different squares
        layout.createPoint("SC_PlainA", false, plainA.getName());
        layout.createPoint("SC_PlainB", false, plainB.getName());

        layout.getPoint("SC_PlainA").setBlock("SC_SquareA");
        layout.getPoint("SC_PlainB").setBlock("SC_SquareB");

        // Both ways are THREE hops
        layout.createEdge("SC_Start", "SC_Split1");
        layout.createEdge("SC_Split1", "SC_Split2");
        layout.createEdge("SC_Split2", "SC_End");

        layout.createEdge("SC_Start", "SC_PlainA");
        layout.createEdge("SC_PlainA", "SC_PlainB");
        layout.createEdge("SC_PlainB", "SC_End");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "SC_Start", false);

        layout.setPathPreference(Layout.PathPreference.FEWEST_POINTS);

        assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "SC_Split1",
            "the two routes are the same length in hops, so a hop count cannot tell them apart - "
            + "but one crosses ONE sensor and the other crosses two, and fewest-sensors has to "
            + "take the one that crosses one");
    }

    /**
     * "Least recently visited" sends trains where they have not been.
     *
     * The rule an operator reaches for first, and the one every other rule here cannot express: none
     * of the others knows or cares where trains have already been, so a layout with a favourite loop
     * can leave its far corner untouched all evening.
     */
    @Test
    public void testLeastRecentlyVisitedGoesWhereTrainsHaveNotBeen() throws Exception
    {
        // No save and restore: the rule belongs to this Layout, so it cannot reach another test.
        Layout layout = new Layout(model);

        MarklinFeedback start = model.newFeedback(86, null);
        MarklinFeedback nearby = model.newFeedback(87, null);
        MarklinFeedback faraway = model.newFeedback(88, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{start, nearby, faraway})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        layout.createPoint("LR_Start", true, start.getName());
        layout.createPoint("LR_Nearby", true, nearby.getName());
        layout.createPoint("LR_Faraway", true, faraway.getName());

        layout.createEdge("LR_Start", "LR_Nearby");
        layout.createEdge("LR_Start", "LR_Faraway");

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "LR_Start", false);

        layout.setPathPreference(Layout.PathPreference.LEAST_RECENTLY_VISITED);

        // One of them has just had a train.  The other has never had one.
        layout.noteArrivalForTest("LR_Nearby");

        assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "LR_Faraway",
            "a station that has just had a train was chosen over one that has never had one, so "
            + "the rule is not ranking by where trains have been at all");

        // And now the other way round, so this cannot be passing by luck of the ordering
        Thread.sleep(1100);

        layout.noteArrivalForTest("LR_Faraway");

        assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "LR_Nearby",
            "with the far station now the more recently visited, the choice has to swap - a rule "
            + "that always picks the same one is indistinguishable from no rule");
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
     * An accessory this layout already has, borrowed rather than created.
     *
     * newSignal adds to the live accessory database, and that database is the user's real one - these
     * tests run against the installed data, not a fixture.  Two invented signals therefore ended up
     * persisted in it, which no test has the right to do: the suite had come back byte-identical on
     * every previous run and stopped doing so.
     *
     * Nothing about these tests needs a signal in particular.  refreshProtectingSignal resolves the
     * pairing by NAME and calls setState on whatever it finds, so any accessory the layout already
     * carries exercises the same path - and leaves the database exactly as it was found.
     *
     * @param which how many to skip, so two tests can each have one of their own
     */
    private MarklinAccessory borrowedAccessory(int which)
    {
        int seen = 0;

        for (int address = 1; address <= 256; address++)
        {
            MarklinAccessory found = model.getAccessoryByAddressIfPresent(
                address, Accessory.accessoryDecoderType.MM2);

            if (found == null) continue;

            if (seen++ == which) return found;
        }

        fail("this layout has no accessories to borrow, so the signal tests cannot run without "
            + "adding one - which is what they exist to avoid");

        return null;
    }

    /**
     * A protecting signal follows the PLATFORM, not one copy of it.
     *
     * The memo that stops a redundant command used to live on the Point while "claimed" was a fact
     * about the whole square.  A refresh on one copy that saw the square claimed through ANOTHER copy
     * wrote true into its own memo while standing empty, and nothing wrote false back - so the next
     * real arrival there matched its stale memo, sent nothing, and left the signal GREEN with a train
     * standing at the platform.
     *
     * The sequence below is that exact poisoning: claim through the west copy, refresh the east one,
     * release the west, then arrive properly on the east.
     */
    @Test
    public void testAProtectingSignalIsRedWheneverThePlatformIsHeld() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback fb = model.newFeedback(101, null);
        model.setFeedbackState(fb.getName(), false);

        MarklinAccessory signal = borrowedAccessory(0);

        layout.createPoint("SIG_east", true, fb.getName());
        layout.createPoint("SIG_west", true, fb.getName());

        // Signals are only thrown while trains are being RUN - see refreshProtectingSignal.  Placing
        // one by hand is how a railway is arranged before a run, and driving real ironwork from that
        // is what the guard exists to stop.
        running(layout);

        for (String name : new String[]{"SIG_east", "SIG_west"})
        {
            layout.getPoint(name).setBlock("main:5,5");
            layout.getPoint(name).setProtectingSignal(signal.getName());
        }

        MarklinLocomotive first = model.getLocByName(model.getLocList().get(0));
        MarklinLocomotive second = model.getLocByName(model.getLocList().get(1));

        // a train claims the platform through the west copy
        layout.getPoint("SIG_west").setLocomotive(first);

        assertTrue(signal.isRed(), "a claimed platform must show red");

        // the east copy is written to while the platform is still claimed through the west one.
        // Every occupancy change refreshes the signal, so this is the ordinary door, not a back one.
        layout.getPoint("SIG_east").setLocomotive(second);
        layout.getPoint("SIG_east").setLocomotive(null);

        assertTrue(signal.isRed(), "still claimed - the other copy holds a train");

        // the west copy releases, and the platform is genuinely empty
        layout.getPoint("SIG_west").setLocomotive(null);

        assertTrue(signal.isGreen(), "an empty platform must show green");

        // and now a train really does arrive on the east copy
        layout.getPoint("SIG_east").setLocomotive(first);

        assertTrue(signal.isRed(),
            "the signal was left GREEN with a train standing at the platform");
    }

    /**
     * Two signals, one platform: BOTH follow it.
     *
     * A platform reachable from two directions needs a signal on each approach, and they say the same
     * thing about the same platform - so a train standing there has to put both to red and leaving has
     * to release both.  The failure this guards against is quiet in the worst way: the approach whose
     * signal was dropped stays green with a train in the platform, and only that approach.
     */
    @Test
    public void testEverySignalGuardingAPlatformIsThrown() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback fb = model.newFeedback(104, null);
        model.setFeedbackState(fb.getName(), false);

        MarklinAccessory north = borrowedAccessory(0);
        MarklinAccessory south = borrowedAccessory(1);

        layout.createPoint("BOTH_ENDS", true, fb.getName());

        running(layout);

        layout.getPoint("BOTH_ENDS").setProtectingSignals(
            Arrays.asList(north.getName(), south.getName()));

        assertEquals(layout.getPoint("BOTH_ENDS").getProtectingSignals().size(), 2,
            "a platform could not be given two signals at all");

        MarklinLocomotive train = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("BOTH_ENDS").setLocomotive(train);

        assertTrue(north.isRed(), "the first signal stayed green with a train in the platform");
        assertTrue(south.isRed(), "the second signal stayed green with a train in the platform, so "
            + "that approach is unprotected and nothing on the diagram says so");

        layout.getPoint("BOTH_ENDS").setLocomotive(null);

        assertTrue(north.isGreen(), "the first signal was left red on an empty platform");
        assertTrue(south.isGreen(), "the second signal was left red on an empty platform");
    }

    /**
     * A configuration carrying a list of signals is read back as that list.
     *
     * parseAuto has always taken a bare string, and a file written before this feature still holds one.
     * Both shapes therefore have to arrive as the same thing - a list - or a railway upgraded to this
     * version comes back with every platform unprotected and nothing saying so.
     */
    @Test
    public void testBothShapesOfProtectingSignalAreRead() throws Exception
    {
        Layout layout = new Layout(model);

        // The s88 is written as a NUMBER, and the run-wide delays are present, because parseAuto
        // invalidates the WHOLE layout over either - and an invalidated layout answers null for every
        // point in it, which reads exactly like the signal having been dropped
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"OLD_SHAPE\", \"station\": true, \"s88\": 106,"
            + "   \"protectingSignal\": \"Signal 12\"},"
            + "  {\"name\": \"NEW_SHAPE\", \"station\": true, \"s88\": 107,"
            + "   \"protectingSignal\": [\"Signal 12\", \"Signal 14\"]}"
            + "],"
            + "\"edges\": [{\"start\": \"OLD_SHAPE\", \"end\": \"NEW_SHAPE\", \"length\": 1}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        Layout parsed = Layout.fromJSON(json, model);

        assertEquals(parsed.getPoint("OLD_SHAPE").getProtectingSignals(),
            Arrays.asList("Signal 12"),
            "a pairing written as a bare string was not read, which would unprotect every platform "
            + "on a railway set up before this version");

        assertEquals(parsed.getPoint("NEW_SHAPE").getProtectingSignals(),
            Arrays.asList("Signal 12", "Signal 14"), "a list of signals did not survive the file");

        // and what is read is written back out the same way round
        org.json.JSONObject back = parsed.getPoint("NEW_SHAPE").toJSON();

        assertTrue(back.get("protectingSignal") instanceof org.json.JSONArray,
            "two signals were exported as something an import would read as one");

        assertTrue(parsed.getPoint("OLD_SHAPE").toJSON().get("protectingSignal") instanceof String,
            "one signal was exported as a list, which the version before this one cannot read");
    }

    /**
     * Arranging the railway does not throw its signals.
     *
     * Every occupancy change refreshes the protecting signals, and placing a train by hand is an
     * occupancy change - so setting a layout up before a run drove real ironwork: cutting a locomotive
     * off a platform with Control+X threw its signals on the spot.  Adam found it doing exactly that.
     * Nobody asked for the railway to be commanded while they were still deciding what it should look
     * like.
     *
     * The wait for the run to start is not a loss of protection: the memo of what each signal was last
     * told is cleared when a run begins, so the first arrival commands them for real.
     */
    @Test
    public void testPlacingATrainByHandDoesNotThrowItsSignals() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback fb = model.newFeedback(106, null);
        model.setFeedbackState(fb.getName(), false);

        MarklinAccessory signal = borrowedAccessory(0);

        layout.createPoint("ARRANGING", true, fb.getName());
        layout.getPoint("ARRANGING").setProtectingSignal(signal.getName());

        // Whatever it happens to be showing now is what it must still be showing afterwards
        boolean before = signal.isRed();

        MarklinLocomotive train = model.getLocByName(model.getLocList().get(0));

        // Nothing is running: this is somebody setting their railway up
        layout.getPoint("ARRANGING").setLocomotive(train);

        assertEquals(signal.isRed(), before,
            "putting a train on a platform by hand threw its protecting signal.  The railway was "
            + "being arranged, not run, and real ironwork moved");

        layout.getPoint("ARRANGING").setLocomotive(null);

        assertEquals(signal.isRed(), before, "and taking it off again threw it back");

        // A train put on the platform while nothing was running, and left there
        layout.getPoint("ARRANGING").setLocomotive(train);

        assertEquals(signal.isRed(), before, "still arranging, still not throwing anything");

        // And now the run begins.  No occupancy CHANGE is coming for this train - it was already
        // standing there - so the signal is only protected if starting a run asks every signal again.
        running(layout);
        layout.refreshAllProtectingSignals();

        assertTrue(signal.isRed(),
            "the run started with a train already standing at the platform and its signal stayed "
            + "clear.  Nothing calls the refresh for a square whose occupancy has not changed, so "
            + "forgetting the memo is not enough - every signal has to be asked again");
    }

    /**
     * Makes a layout report itself as running, without dispatching anything.
     *
     * isRunning() is true while any locomotive is active, so an entry in that map is enough - and it
     * is the same map executePath writes, under the same lock.  Calling runLocomotives would set real
     * trains off.
     */
    private static void running(Layout layout)
    {
        synchronized (layout.getActiveLocomotives())
        {
            layout.getActiveLocomotives().put(
                model.getLocByName(model.getLocList().get(0)), new java.util.ArrayList<>());
        }
    }

    /**
     * One signal, two platforms: red while EITHER is occupied.
     *
     * The pairing menu lets two stations pick the same signal, and a signal can only show one aspect.
     * Asking the square rather than the signal made the second platform going free turn it green while
     * a train still stood at the first.
     */
    @Test
    public void testASignalSharedByTwoStationsStaysRedWhileEitherIsHeld() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback one = model.newFeedback(102, null);
        MarklinFeedback two = model.newFeedback(103, null);
        model.setFeedbackState(one.getName(), false);
        model.setFeedbackState(two.getName(), false);

        MarklinAccessory signal = borrowedAccessory(1);

        layout.createPoint("SHARE_A", true, one.getName());
        layout.createPoint("SHARE_B", true, two.getName());

        running(layout);

        layout.getPoint("SHARE_A").setProtectingSignal(signal.getName());
        layout.getPoint("SHARE_B").setProtectingSignal(signal.getName());

        MarklinLocomotive first = model.getLocByName(model.getLocList().get(0));
        MarklinLocomotive second = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("SHARE_A").setLocomotive(first);
        layout.getPoint("SHARE_B").setLocomotive(second);

        assertTrue(signal.isRed(), "both platforms held");

        layout.getPoint("SHARE_B").setLocomotive(null);

        assertTrue(signal.isRed(),
            "one platform is still occupied, so the signal it protects cannot be green");

        layout.getPoint("SHARE_A").setLocomotive(null);

        assertTrue(signal.isGreen(), "and green once both are clear");
    }

    /**
     * Everything comes back from a configuration that already had two trains on one square.
     *
     * Hand placement was closed, but a FILE was the other door - one written by a version before the
     * rule existed, hand-edited, or brought from another machine.  fromJSON checks for a duplicate
     * locomotive and never for a duplicate square, so the state was reinstated on every load and the
     * fix that was supposed to end it never touched anything already saved.
     */
    @Test
    public void testAConfigurationWithTwoTrainsOnOneSquareIsRepairedOnLoad() throws Exception
    {
        String first = model.getLocList().get(0);
        String second = model.getLocList().get(1);

        String json = "{"
            + "\"points\": ["
            + "  {\"name\":\"LOAD_east\",\"station\":true,\"s88\":110,\"block\":\"main:6,6\","
            + "   \"loc\":{\"name\":\"" + first + "\"}},"
            + "  {\"name\":\"LOAD_west\",\"station\":true,\"s88\":110,\"block\":\"main:6,6\","
            + "   \"loc\":{\"name\":\"" + second + "\"}},"
            + "  {\"name\":\"LOAD_far\",\"station\":true,\"s88\":111}"
            + "],"
            + "\"edges\": ["
            + "  {\"start\":\"LOAD_east\",\"end\":\"LOAD_far\",\"length\":1}"
            + "],"
            + "\"minDelay\":1,\"maxDelay\":2,\"defaultLocSpeed\":35}";

        // parseAuto replaces the model's layout wholesale, and every test in this class shares the one
        // loaded in setUpClass - so this puts it back, or everything after it runs against a railway
        // with three points on it.
        try
        {
            model.parseAuto(json);

            Layout loaded = model.getAutoLayout();

            assertTrue(loaded.isValid(),
                "the configuration must still load - refusing it leaves a railway nobody can use: "
                    + loaded.getInvalidReason());

            int standing = 0;

            for (String name : new String[]{"LOAD_east", "LOAD_west"})
            {
                if (loaded.getPoint(name).getCurrentLocomotive() != null) standing++;
            }

            assertEquals(standing, 1,
                "a file holding two trains on one square put them both back on load");
        }
        finally
        {
            reloadSampleLayout();
        }
    }

    /**
     * Puts back the layout setUpClass loaded, for a test that had to replace it.
     */
    private static void reloadSampleLayout() throws Exception
    {
        model.parseAuto(new BufferedReader(new InputStreamReader(
            TrainControlUI.class.getResource(RESOURCE_PATH + AUTONOMY_SAMPLE).openStream()))
            .lines().collect(Collectors.joining("\n")));
    }

    /**
     * Every key parseAuto reads off a Point, toJSON writes back - checked by asking the reader.
     *
     * This began as two assertions, on `block` and `protectingSignal`, wearing the name of an
     * invariant.  Both had been omitted from the writer in turn while the reader understood them, and
     * the javadoc claimed "the next field added will drift the same way unless something is watching" -
     * while watching exactly two.  A test that names the fields it knows about cannot catch the field
     * nobody thought of, which is the entire failure mode.
     *
     * So the source of truth is the READER.  Every key parseAuto looks for on a point is listed here,
     * and the test fails if any of them is absent from what toJSON produced.  Adding a key to the
     * reader without adding it to the writer now fails here rather than silently losing a setting on
     * the next export; adding one to both means adding it to this list, which is the point at which
     * somebody has to think about it.
     */
    @Test
    public void testEveryKeyParseAutoReadsIsAlsoWritten() throws Exception
    {
        Layout layout = model.getAutoLayout();

        Point point = layout.getPoints().iterator().next();

        String hadBlock = point.getBlock();
        String hadSignal = point.getProtectingSignal();
        org.traincontrol.base.Locomotive hadHome = point.getHomeLoc();
        boolean wasActive = point.isActive();
        java.util.List<Point> hadBlockedBy = new java.util.ArrayList<>(point.getBlockedBy());
        java.util.Set<Locomotive> hadExcludedLocs = new java.util.HashSet<>(point.getExcludedLocs());

        // TST-B20: these four used to be set below and never put back, leaving this point of the
        // shared sample layout non-dispatchable and speed-scaled for every test that ran after this
        // one in the same JVM.
        Integer hadMaxTrainLength = point.getMaxTrainLength();
        int hadPriority = point.getPriority();
        double hadSpeedMultiplier = point.getSpeedMultiplier();
        boolean hadAutoDestination = point.isAutoDestination();

        // A second, unrelated point of the same real layout, purely to be named as a blocker - never
        // touched itself, so nothing about it needs restoring.
        Point blocker = null;

        for (Point candidate : layout.getPoints())
        {
            if (candidate != point) { blocker = candidate; break; }
        }

        assertNotNull(blocker,
            "precondition: needed a second point of the same layout to name as a blocker");

        try
        {
            // Everything optional set to a non-default, so nothing is omitted for being absent
            point.setBlock("main:9,9");
            point.setProtectingSignal("Signal 12");
            point.setHomeLoc(model.getLocByName("Test loc 1"));
            point.setMaxTrainLength(7);
            point.setPriority(3);
            point.setSpeedMultiplier(0.75);
            point.setAutoDestination(false);
            point.setExcludedLocs(new java.util.HashSet<>(
                Arrays.asList(model.getLocByName("Test loc 2"))));
            point.setBlockedBy(Arrays.asList(blocker));

            // Not the default, deliberately.  The format omits a field that holds its default value -
            // "active" is written only when false, and parseAuto defaults it to true - so a test that
            // demanded every key be present would be asserting something the format does not promise.
            // What it DOES promise is that a value somebody set is written, and that is what is asked
            // here: every field is moved off its default first.
            point.setActive(false);

            org.json.JSONObject json = point.toJSON();

            // The keys parseAuto looks for on a point.  Kept in the reader's order so the two can be
            // compared by eye.  "terminus", "reversing" and "loc" are checked separately below: the
            // first two are mutually exclusive with each other (Point.setTerminus/setReversing each
            // refuse the other), and "loc" needs a Point actually attached to a live layout to matter,
            // which this shared sample-layout Point already is for everything else here.
            String[] readsOffAPoint =
            {
                "name", "station", "s88", "x", "y",
                "block", "protectingSignal", "home", "maxTrainLength",
                "active", "autoDestination", "priority", "speedMultiplier",
                "blockedBy", "excludedLocs"
            };

            for (String key : readsOffAPoint)
            {
                assertTrue(json.has(key),
                    "parseAuto reads \"" + key + "\" off a point and toJSON did not write it, so a "
                        + "setup exported and imported comes back without it: " + json.toString());
            }
        }
        finally
        {
            point.setBlock(hadBlock);
            point.setProtectingSignal(hadSignal);
            point.setHomeLoc(hadHome);
            point.setActive(wasActive);
            point.setBlockedBy(hadBlockedBy);
            point.setExcludedLocs(hadExcludedLocs);
            point.setMaxTrainLength(hadMaxTrainLength);
            point.setPriority(hadPriority);
            point.setSpeedMultiplier(hadSpeedMultiplier);
            point.setAutoDestination(hadAutoDestination);
        }

        // "terminus", "reversing" and "loc" - checked on a standalone Point rather than the shared
        // sample layout's, so setLocomotive here cannot reach into MarklinControlStation.clearLocomotiveExcept
        // and move a locomotive the rest of this class's tests depend on (Point.setLocomotive only does
        // that when the Point has a layout attached; a hand-built one does not).
        //
        // MUTATION each of the three assertions below catches on its own: delete the corresponding
        // `jsonObj.put(...)` write in Point.toJSON (Point.java:941 terminus, :946 reversing, :1031 loc)
        // - the terminus flag, the reversing flag, or the locomotive standing on a point is then
        // silently dropped by export, exactly as TST-A1 describes for the fields above.
        Point standalone = new Point("Terminus check", true, "999");

        standalone.setTerminus(true);
        assertTrue(standalone.toJSON().has("terminus"),
            "parseAuto reads \"terminus\" off a point and toJSON did not write it");
        standalone.setTerminus(false);

        standalone.setReversing(true);
        assertTrue(standalone.toJSON().has("reversing"),
            "parseAuto reads \"reversing\" off a point and toJSON did not write it");
        standalone.setReversing(false);

        standalone.setLocomotive(model.getLocByName("Test loc 1"));
        assertTrue(standalone.toJSON().has("loc"),
            "parseAuto reads \"loc\" off a point and toJSON did not write it");
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


    /**
     * A failure part way along a path stops the run, says so, and gives the track back.
     *
     * **Adam's ruling, 2026-09-03**, on the last item of the release backlog: *"force a graceful stop,
     * alert the user, then unlock."*
     *
     * The `RuntimeException` handler removes the locomotive from `activeLocomotives`,
     * `locomotiveMilestones` and `clearedEdges` - and those are exactly the maps `getActiveAccs` reads
     * to know which accessories a route must not throw. It then deliberately left the path LOCKED, so
     * the track was held by nobody, with no thread watching it, and **its route protection gone at the
     * same instant**. Only a graph reload recovered it.
     *
     * Leaving it locked was the safe half of a choice whose other half was never made: the protection
     * went anyway. The ruling closes it the other way - stop everything, tell the operator, and let
     * the track go, so that what the model believes and what it protects agree again. The stop is
     * what makes releasing safe: `running` is false, so autonomy dispatches nothing new.
     *
     * **The operator is told to look**, because this is the one case where the model frees track a
     * train may physically be standing on. That sentence is in the message, in all eight languages.
     *
     * MUTATION: removing the `unlockPath` call from the handler fails the occupancy assertion; removing
     * `stopLocomotives()` fails the one below it.
     */
    @Test
    public void testAFailedPathStopsTheRunAndGivesTheTrackBack() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback from = model.newFeedback(140, null);
        MarklinFeedback to = model.newFeedback(141, null);

        model.setFeedbackState(from.getName(), true);
        model.setFeedbackState(to.getName(), false);

        layout.createPoint("FAIL_FROM", true, from.getName());
        layout.createPoint("FAIL_TO", true, to.getName());
        layout.createEdge("FAIL_FROM", "FAIL_TO");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("FAIL_FROM").setLocomotive(loc);

        java.util.List<Edge> path = new java.util.ArrayList<>();

        path.add(layout.getEdge("FAIL_FROM", "FAIL_TO"));

        assertNotNull(path.get(0), "the fixture produced no edge, so nothing below is exercised");

        // The throw, fifty lines after the path has been locked.
        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            throw new RuntimeException("deliberate mid-path failure");
        });

        layout.runLocomotives();

        try
        {
            layout.executePath(path, loc, 20, null);

            fail("executePath swallowed the failure, so the handler under test never ran");
        }
        catch (RuntimeException expected)
        {
            // The handler does not swallow it - executeTimetable's retry loop depends on that.
        }
        finally
        {
            loc.setCallback(Layout.CB_ROUTE_START, null);
        }

        assertFalse(layout.isRunning(),
            "the run did not stop itself.  A locomotive is somewhere on a path with nothing tracking "
            + "it and every other train still going, which is the state RC-A11 exists to end");

        for (Edge e : path)
        {
            assertFalse(e.isOccupied(loc),
                "the abandoned path is still locked: " + e.getName() + ".  The handler had already "
                + "removed this locomotive from activeLocomotives, locomotiveMilestones and "
                + "clearedEdges - which is what getActiveAccs reads - so the track was held by "
                + "nobody and protected by nothing at the same time, until a graph reload.  Adam's "
                + "ruling: stop, alert, then unlock");
        }

        assertFalse(layout.getActiveLocomotives().containsKey(loc),
            "the locomotive is still registered as active, so isRunning() stays true for the rest of "
            + "the session and every guard built on it stands down");
    }

    /**
     * A failure does not release an edge the tail had already given up (VD10-A1).
     *
     * `unlockPath`'s non-atomic branch reads `clearedEdges` to know which edges the tail released as it
     * passed them, so that it does not release them again. The comment at that lookup says what a
     * second release costs: *"the second release would take away a claim somebody else made in
     * between"* - the edge comes free under a train that locked it after the tail went by, and its
     * lock edges with it.
     *
     * **The ordering is the whole of it.** The ordinary ending calls `unlockPath` and clears the map
     * after it. The failure handler added on 2026-09-03 cleared the map fifty lines BEFORE calling
     * `unlockPath`, so the lookup was always null and every early-released edge was released twice.
     * `atomicRoutes` is `false` on the operator's own configuration, so this is the live branch.
     *
     * **Seeded, not driven.** Getting a real tail to release an edge early needs a train in motion;
     * what is under test is whether the map is still populated when `unlockPath` runs, and seeding it
     * asks exactly that and nothing else.
     *
     * MUTATION: moving `clearedEdges.remove(loc)` back above the release fails this.
     */
    @Test
    public void testAFailureDoesNotReleaseAnEdgeTheTailAlreadyGaveUp() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setAtomicRoutes(false);

        MarklinFeedback from = model.newFeedback(150, null);
        MarklinFeedback mid = model.newFeedback(151, null);
        MarklinFeedback to = model.newFeedback(152, null);

        model.setFeedbackState(from.getName(), true);
        model.setFeedbackState(mid.getName(), false);
        model.setFeedbackState(to.getName(), false);

        layout.createPoint("VD10_FROM", true, from.getName());
        layout.createPoint("VD10_MID", true, mid.getName());
        layout.createPoint("VD10_TO", true, to.getName());

        org.traincontrol.automation.Edge first = layout.createEdge("VD10_FROM", "VD10_MID");
        org.traincontrol.automation.Edge second = layout.createEdge("VD10_MID", "VD10_TO");

        // The throat: `first` names `second` as a lock edge, so releasing `first` releases it too.
        layout.getEdge("VD10_FROM", "VD10_MID").addLockEdge(second);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("VD10_FROM").setLocomotive(loc);

        java.util.List<org.traincontrol.automation.Edge> path = new java.util.ArrayList<>();

        path.add(first);

        // And the tail having already given `first` up, which is what clearedEdges records.
        java.lang.reflect.Field cleared = Layout.class.getDeclaredField("clearedEdges");

        cleared.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Map<Locomotive, java.util.Set<org.traincontrol.automation.Edge>> map =
            (java.util.Map<Locomotive, java.util.Set<org.traincontrol.automation.Edge>>)
                cleared.get(layout);


        // The claim is made IN BETWEEN - after the path is locked, before it fails - because that is
        // the sequence the lookup exists for.  A throat claimed beforehand simply refuses the lock and
        // the handler never runs.
        // Read at the MOMENT OF FAILURE, not before the run: the claim being protected is one made
        // after the path was locked, so a baseline taken beforehand is a different number.
        final int[] atTheMoment = { -1 };
        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            // SEEDED HERE, not before the dispatch: executePathInternal installs its own clearedEdges
            // entry for this locomotive when the run starts, which overwrites anything put there
            // earlier.  By this callback the path is locked and that entry exists, which is exactly
            // the moment a real tail would be adding to it.
            java.util.Set<org.traincontrol.automation.Edge> given = map.get(loc);

            if (given == null) { given = new java.util.HashSet<>(); map.put(loc, given); }

            given.add(first);

            // And the claim another train makes in between.
            second.setOccupied();

            try
            {
                java.lang.reflect.Field held =
                    org.traincontrol.automation.Edge.class.getDeclaredField("occupancy");

                held.setAccessible(true);

                atTheMoment[0] = held.getInt(second);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }

            throw new RuntimeException("deliberate mid-path failure");
        });




        layout.runLocomotives();

        try
        {
            layout.executePath(path, loc, 20, null);

            fail("executePath swallowed the failure, so the handler under test never ran");
        }
        catch (RuntimeException expected)
        {
            // expected
        }
        finally
        {
            loc.setCallback(Layout.CB_ROUTE_START, null);
        }

        // THE COUNT, not the boolean.  `release()` floors at zero and occupancy is a COUNT now, so a
        // throat held by two claims and released once is still "occupied" - which is why asserting
        // isOccupied passed against both orderings and proved nothing (found by mutation).
        java.lang.reflect.Field occupancy =
            org.traincontrol.automation.Edge.class.getDeclaredField("occupancy");

        occupancy.setAccessible(true);

        assertTrue(atTheMoment[0] > 0, "the callback never ran, so nothing below is exercised");

        assertEquals(occupancy.getInt(second), atTheMoment[0],
            "the failure released a throat this locomotive had already given up, taking away the "
            + "claim another train made after the tail went by.  unlockPath reads clearedEdges to "
            + "know which edges not to release twice, and the handler had emptied that map fifty "
            + "lines earlier - so the lookup was null and every early-released edge went again, "
            + "lock edges included.  The ordinary ending clears the map AFTER the release, which is "
            + "where this one clears it now (VD10-A1)");
    }

    /**
     * The diagram menu's split: what autonomy would choose, and what it never would (FR-058).
     *
     * Adam, 2026-09-03: *"show only active stations that can be chosen in full autonomy.  add a menu
     * called More Destinations and in there, list the points that cannot be chosen in full autonomy
     * but are still valid.  the current setup lists both in one flat list, which truncates active
     * stations, which I don't like"*.
     *
     * **The cap is why it matters.** The top level shows twelve and then an ellipsis, so a parking
     * track autonomy will never pick used to cost a line an ordinary platform wanted.
     *
     * The menu itself needs a window, so what is asserted here is the predicate the menu splits on -
     * `isChoosableByAutonomy`, which is also what the "no available paths" window and the diagram's
     * caption rule ask. If that answer is right, the two lists are right; if it moves, this fails
     * before the menu does.
     *
     * MUTATION: making `isChoosableByAutonomy` ignore `isAutoDestination` puts the parking track in
     * the top-level list and fails this.
     */
    @Test
    public void testTheDiagramMenuSplitsOnWhatAutonomyWouldChoose() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback ordinary = model.newFeedback(160, null);
        MarklinFeedback parking = model.newFeedback(161, null);
        MarklinFeedback shut = model.newFeedback(162, null);

        model.setFeedbackState(ordinary.getName(), false);
        model.setFeedbackState(parking.getName(), false);
        model.setFeedbackState(shut.getName(), false);

        Point plain = layout.createPoint("FR058_PLAIN", true, ordinary.getName());
        Point park = layout.createPoint("FR058_PARK", true, parking.getName());
        Point off = layout.createPoint("FR058_OFF", true, shut.getName());

        // A station autonomy may pick.
        assertTrue(layout.isChoosableByAutonomy(plain),
            "an ordinary active station is not choosable, so the fixture says nothing");

        // One the operator has marked as not an automatic destination - valid to send a train to by
        // hand, never picked on its own.  This is the case that used to eat a line of the cap.
        park.setAutoDestination(false);

        assertFalse(layout.isChoosableByAutonomy(park),
            "a square marked as not an automatic destination is still being offered to autonomy, so "
            + "the diagram menu would keep it in the capped top-level list (FR-058)");

        // And one switched off, which stays off the menu altogether - Adam's earlier ruling, which
        // FR-058 does not change: "make the inactive stations disappear from the track diagram menu".
        off.setActive(false);

        assertFalse(layout.isChoosableByAutonomy(off),
            "a switched-off square is being offered to autonomy");

        assertFalse(off.isActive(),
            "the switched-off square is the one the menu drops before the split, so this is the "
            + "property the drop reads");
    }
}
