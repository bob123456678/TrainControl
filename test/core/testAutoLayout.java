package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

        // A SECOND ROUTE, ENABLED, which autonomy has to switch off.  The test configuration holds only the route
        // above, so without it the loop at the end would have nothing to disable and could not fail.
        model.newRoute("AR switched off by autonomy", 9823,
            Collections.singletonList(RouteCommand.RouteCommandStop()), 8873,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

        try
        {
            MarklinRoute other = model.getRoute("AR switched off by autonomy");

            other.enable();

            assertEquals(other.isEnabled(), true);

            // This should not enable the route
            model.applyAutonomyRouteActivations();

            layout.setActivateRoutes(true);
            assertEquals(r.isEnabled(), false);

            // This should now enable the route
            model.applyAutonomyRouteActivations();

            assertEquals(r.isEnabled(), true);

            // And disable all other routes
            int others = 0;

            for (MarklinRoute otherRoute : model.getRoutes())
            {
                if (otherRoute.getId() == r.getId())
                {
                    assertEquals(otherRoute.isEnabled(), true);
                }
                else
                {
                    assertEquals(otherRoute.isEnabled(), false);

                    others++;
                }
            }

            assertTrue(others > 0, "there is no other route, so nothing was disabled");
        }
        finally
        {
            model.deleteRoute("AR switched off by autonomy");
        }
    }
    
    /**
     * A route autonomy activates really is watching its sensor afterwards.
     *
     * `testAutoRoute` asserts the flag, and the flag is not the railway: `enable()` sets a boolean,
     * `executeAutoRoute()` is what parks a thread on the s88.  Remove the second call from
     * `applyAutonomyRouteActivations` and every `testAutoRoute` assertion passes while no activated route ever fires
     * again.  So this pulses the sensor and asks whether the turnout moved.
     *
     * MUTATION: delete `r.executeAutoRoute();` from `applyAutonomyRouteActivations` and this fails while
     * `testAutoRoute` stays green.
     */
    @Test
    public void testAnActivatedRouteIsArmedAndNotJustFlagged() throws Exception
    {
        final int sensor = 8871;
        final int turnout = 295;
        final int routeId = 9821;

        model.newFeedback(sensor, null);
        model.setFeedbackState(String.valueOf(sensor), false);

        MarklinAccessory acc = model.getAccessoryByAddress(turnout, MarklinAccessory.accessoryDecoderType.MM2);

        acc.setSwitched(false);

        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandAccessory(turnout, Accessory.accessoryDecoderType.MM2, true));

        // Built disabled, so nothing is armed until autonomy says so - which is the sequence under test.
        model.newRoute("AR arm probe", routeId, commands, sensor,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        try
        {
            MarklinRoute route = model.getRoute("AR arm probe");

            assertFalse(route.isEnabled(), "precondition: the route starts disarmed");

            Layout layout = model.getAutoLayout();

            layout.setActivateRouteIDs(Collections.singletonList(routeId));
            layout.setActivateRoutes(true);

            model.applyAutonomyRouteActivations();

            assertTrue(route.isEnabled(), "precondition: autonomy reports the route as enabled");

            pulseFeedback(String.valueOf(sensor));

            assertTrue(acc.isSwitched(),
                "autonomy enabled the route and the sensor then fired, but the turnout did not move - "
                + "so the route carries the flag and nothing is watching the railway.  enable() sets a "
                + "boolean; executeAutoRoute() is what parks the monitor");
        }
        finally
        {
            model.deleteRoute("AR arm probe");
        }
    }

    /**
     * A route autonomy switches off really stops firing.  The other half of the same gap: `testAutoRoute` asserts that
     * routes outside the list report themselves disabled, which `disable()` makes true by definition; what matters is
     * that the parked monitor honors it (it tests `enabled` after each feedback wait), asserted with a control first.
     *
     * MUTATION: take the `r.disable()` out of the non-listed branch of `applyAutonomyRouteActivations`
     * and this fails.
     */
    @Test
    public void testADeactivatedRouteStopsFiring() throws Exception
    {
        final int sensor = 8872;
        final int turnout = 297;
        final int routeId = 9822;

        model.newFeedback(sensor, null);
        model.setFeedbackState(String.valueOf(sensor), false);

        MarklinAccessory acc = model.getAccessoryByAddress(turnout, MarklinAccessory.accessoryDecoderType.MM2);

        acc.setSwitched(false);

        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandAccessory(turnout, Accessory.accessoryDecoderType.MM2, true));

        model.newRoute("AR disarm probe", routeId, commands, sensor,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

        try
        {
            MarklinRoute route = model.getRoute("AR disarm probe");

            route.enable();
            route.executeAutoRoute();

            Thread.sleep(600);

            // THE CONTROL: it fires while it is armed, so the assertion below is about the disarming.
            pulseFeedback(String.valueOf(sensor));

            assertTrue(acc.isSwitched(),
                "control: the route did not fire even while armed, so this test cannot tell a disarmed "
                + "route from a broken fixture");

            acc.setSwitched(false);

            // Autonomy now loads a configuration that does not list this route.
            Layout layout = model.getAutoLayout();

            layout.setActivateRouteIDs(Collections.singletonList(routeId + 1));
            layout.setActivateRoutes(true);

            model.applyAutonomyRouteActivations();

            assertFalse(route.isEnabled(), "precondition: autonomy reports the route as disabled");

            pulseFeedback(String.valueOf(sensor));

            assertFalse(acc.isSwitched(),
                "the route was switched off by autonomy and still threw its turnout when the sensor "
                + "fired.  Two systems command this railway and the operator was told this one had "
                + "stopped");
        }
        finally
        {
            model.deleteRoute("AR disarm probe");
        }
    }

    /**
     * A route with no sensor cannot be activated, and is not reported as activated: `applyAutonomyRouteActivations`
     * guards the enable on `hasS88()` and logs `autolayoutErrorS88RequiredForAutoFire`, since a route with nothing to
     * watch would claim to be automatic and never fire.
     */
    @Test
    public void testAListedRouteWithNoSensorIsNotActivated() throws Exception
    {
        final int routeId = 9823;

        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandAccessory(299, Accessory.accessoryDecoderType.MM2, true));

        model.newRoute("AR sensorless probe", routeId, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        try
        {
            MarklinRoute route = model.getRoute("AR sensorless probe");

            assertFalse(route.hasS88(), "precondition: the route has no sensor");

            Layout layout = model.getAutoLayout();

            layout.setActivateRouteIDs(Collections.singletonList(routeId));
            layout.setActivateRoutes(true);

            model.applyAutonomyRouteActivations();

            assertFalse(route.isEnabled(),
                "a route with no sensor was marked as automatically firing, which it can never do");
        }
        finally
        {
            model.deleteRoute("AR sensorless probe");
        }
    }

    /**
     * Drives a sensor clear then occupied, holding each state past FEEDBACK_DURATION_THRESHOLD, then
     * allows time for the route body to run.  The same shape testRoutes uses.
     */
    private static void pulseFeedback(String feedbackName) throws InterruptedException
    {
        model.setFeedbackState(feedbackName, false);
        Thread.sleep(400);
        model.setFeedbackState(feedbackName, true);
        Thread.sleep(1500);
    }

    /**
     * An edge's arrival side survives Export JSON and Load JSON.
     *
     * `entrySide` is the side of the end point an edge arrives by; `AutonomyBuilder` writes it, `Layout.fromJSON` reads
     * it back and the field's javadoc says it *"travels in the configuration"*, so `Edge.toJSON` has to write it too,
     * or arrival-side reasoning (and so what counts as a reversal) fails on any layout that has been through Export
     * JSON.  Measured over the whole live configuration, because the defect is that NONE of them carried it.
     *
     * MUTATION: drop the `entrySide` line from `Edge.toJSON` and this fails, reporting 0 sides written of
     * however many the layout has.
     */
    @Test
    public void testAnEdgeKeepsItsArrivalSideThroughTheJSON() throws Exception
    {
        // THE BASELINE CONFIGURATION, not the sample layout: entrySide is written by AutonomyBuilder, so the legacy
        // sample has none and the test would pass with nothing to preserve (hence the floor below).
        String file = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("baseline.dir", "test/baseline"),
                "configuration.json")), java.nio.charset.StandardCharsets.UTF_8);

        Layout layout = Layout.fromJSON(file, model);

        assertNotNull(layout, "precondition: the baseline configuration could not be read");

        int sides = 0;

        for (Edge e : layout.getEdges())
        {
            if (e.getEntrySide() != null) sides++;
        }

        assertTrue(sides > 0,
            "precondition: no edge in the baseline configuration has an arrival side, so this test "
            + "would pass vacuously.  AutonomyBuilder is what writes them");

        // The program's own export, read back by the program's own reader.
        Layout back = Layout.fromJSON(layout.toJSON().toString(), model);

        assertNotNull(back, "the exported configuration could not be read back at all");

        int sidesBack = 0;

        for (Edge e : back.getEdges())
        {
            if (e.getEntrySide() != null) sidesBack++;
        }

        assertEquals(sidesBack, sides,
            "the layout has " + sides + " edges with an arrival side and the export brought back "
            + sidesBack + ".  Edge.toJSON did not write entrySide, which Layout.fromJSON reads and the "
            + "field's javadoc says travels in the configuration - so Export JSON then Load JSON lost "
            + "the arrival side of every edge (S14-B3)");
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
        testAutoLayout.model = support.TestStation.simulatedInDebug(); 
        model.stop();
        
        String s = readTheSample();
        
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
     * A locomotive cannot be in two places, and the model will not represent it.  Setting a locomotive on a Point
     * clears every other copy of it, so no caller has to remember: a locomotive standing on two copies of one platform
     * would show on one in the diagram, be cleared from only one by removing it, and make the next build produce a
     * configuration fromJSON refuses.  A Point with no layout behind it (every Point built by hand) is untouched.
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
     * Track answered 0 on purpose is measured track of no length (Adam: *"0 lengths count as measures, so non-atomic
     * should be allowed"*): to the Atomic Routes gate and the release escape it stands for, and to every other length
     * rule.
     *
     * The gate and the escape ask one question, or the gate lets through a railway the escape then releases under a
     * train.  The route in and the room walk count on past an answered 0, adding nothing, where a stretch nobody
     * answered still ends them; the walk that claims a standing train's tail walks on over it too
     * (`testAnAnsweredZeroIsNotMissing.testAStretchAnsweredZeroIsMeasuredTrackOfNoLength`).
     *
     * MUTATION: count answered track as unmeasured at the gate, at the escape, in the route in or in the room walk, and
     * this fails.
     *
     * @throws Exception from the model
     */
    @Test
    public void testTrackAnsweredZeroIsMeasuredTrack() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("AZ_C", true, "5");
        layout.createPoint("AZ_A", true, "6");
        layout.createPoint("AZ_B", true, "7");

        Edge ca = layout.createEdge("AZ_C", "AZ_A");
        Edge ab = layout.createEdge("AZ_A", "AZ_B");
        Edge ba = layout.createEdge("AZ_B", "AZ_A");

        ca.setPlaces(java.util.Arrays.asList("AZ:1"), java.util.Arrays.asList(3));
        ca.setLength(3);

        for (Edge adjacent : new Edge[] {ab, ba})
        {
            adjacent.setPlaces(java.util.Arrays.asList("AZ:2", "AZ:3"), java.util.Arrays.asList(0, 0));
        }

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 1, "CONTROL: the rail between AZ_A and AZ_B"
            + " has no length and nobody answered it, so it is the one piece a non-atomic run could release under a"
            + " train - counted: " + layout.unmeasuredTrackThatCouldBeReleased());

        // ANSWERED 0 ON PURPOSE - his adjacent tracks.
        ab.setAnsweredPlaces(java.util.Arrays.asList("AZ:2", "AZ:3"));
        ba.setAnsweredPlaces(java.util.Arrays.asList("AZ:2", "AZ:3"));

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 0, "the rail was answered 0 on purpose - Adam,"
            + " 2026-09-24, TDU-C6: \"0 lengths count as measures, so non-atomic should be allowed\" - and Atomic Routes"
            + " is still held on for it: " + layout.unmeasuredTrackThatCouldBeReleased());

        // THE ESCAPE AGREES, or the gate lets through a railway the runtime then releases under a train.
        java.lang.reflect.Method unmeasured = Layout.class.getDeclaredMethod("pathIsUnmeasured", List.class);

        unmeasured.setAccessible(true);

        assertEquals(unmeasured.invoke(null, java.util.Arrays.asList(ab)), Boolean.FALSE, "a path over track answered 0"
            + " is still taken for one with nothing measured, so `tailHasProvablyPassed` hands its edges back at once -"
            + " the gate and the escape have to ask one question");

        // AND THE ROUTE IN COUNTS ON PAST IT, adding nothing: 3, then 0, then 2.  A leg nobody answered still ends it.
        layout.createPoint("AZ_P", true, "8");

        Edge bp = layout.createEdge("AZ_B", "AZ_P");

        bp.setPlaces(java.util.Arrays.asList("AZ:4", "AZ:5"), java.util.Arrays.asList(1, 1));
        bp.setLength(2);

        java.util.List<Edge> road = java.util.Arrays.asList(ca, ab, bp);

        assertEquals(Layout.measuredRouteIn(road), 5, "the route in stops at a leg answered 0, so a train the 5 measured"
            + " end to end holds is refused - Adam, 2026-09-25: \"allow trains in ... if the total track lengths allow\"");

        org.traincontrol.marklin.MarklinLocomotive loc = model.getLocByName("Test loc 1");

        assertNotNull(loc, "precondition: this class's Test loc 1 is gone");

        Integer was = loc.getTrainLength();

        try
        {
            loc.setTrainLength(4);

            // THE ROOM WALK TOO: no switch on this road, so the room is the whole of it, over the 0.
            assertEquals(Layout.measuredRoomAtTheEndOf(road, loc), Integer.valueOf(5), "the room walk stops at a leg"
                + " answered 0 as if nobody had measured it");

            // AND A ROOM OF 0 IS A ROOM, not an unknown: the leg alone, answered 0, holds nothing.
            assertEquals(Layout.measuredRoomAtTheEndOf(java.util.Arrays.asList(ab), loc), Integer.valueOf(0), "a leg"
                + " answered 0 is read as a room nobody measured, so nothing is judged there");

            // AND WHERE THE WALK STOPS AT A LEG NOBODY MEASURED, the answered 0 it counted first is still a room of 0.
            layout.createPoint("AZ_D", true, "9");

            Edge da = layout.createEdge("AZ_D", "AZ_A");

            da.setPlaces(java.util.Arrays.asList("AZ:6"), java.util.Arrays.asList(0));

            assertEquals(Layout.measuredRoomAtTheEndOf(java.util.Arrays.asList(da, ab), loc), Integer.valueOf(0), "the room"
                + " walk counted a leg answered 0, stopped at one nobody measured, and called the room unknown");

            // A LEG NOBODY ANSWERED STILL ENDS BOTH.
            ab.setAnsweredPlaces(java.util.Collections.<String>emptyList());

            assertEquals(Layout.measuredRouteIn(road), 2, "CONTROL: the route in counts on past a leg nobody answered");

            assertEquals(Layout.measuredRoomAtTheEndOf(road, loc), Integer.valueOf(2), "CONTROL: the room walk counts on"
                + " past a leg nobody answered");
        }
        finally
        {
            loc.setTrainLength(was);
        }
    }

    /**
     * Where two roads part behind a standing train, a rail answered 0 is covered as far as the tail reaches, like a
     * measured one: the claim up to where the rails part, and the tail question's reach along a rail.  Two rails into
     * RP_P, answered 0 throughout, sharing their last two squares.
     *
     * MUTATION: have either decline a rail answered 0, as both declined a rail with no length, and this fails.
     *
     * @throws Exception from the model or the reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAnAnsweredRailIsCoveredWhereTheRoadsPart() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RP_X", true, "5");
        layout.createPoint("RP_Y", true, "6");
        layout.createPoint("RP_P", true, "7");

        Edge xp = layout.createEdge("RP_X", "RP_P");
        Edge yp = layout.createEdge("RP_Y", "RP_P");

        xp.setPlaces(java.util.Arrays.asList("RP:x", "RP:s", "RP:p"), java.util.Arrays.asList(0, 0, 0));
        yp.setPlaces(java.util.Arrays.asList("RP:y", "RP:s", "RP:p"), java.util.Arrays.asList(0, 0, 0));

        xp.setAnsweredPlaces(java.util.Arrays.asList("RP:x", "RP:s", "RP:p"));
        yp.setAnsweredPlaces(java.util.Arrays.asList("RP:y", "RP:s", "RP:p"));

        org.traincontrol.marklin.MarklinLocomotive loc = model.getLocByName("Test loc 1");

        assertNotNull(loc, "precondition: this class's Test loc 1 is gone");

        Integer was = loc.getTrainLength();

        try
        {
            loc.setTrainLength(3);

            java.lang.reflect.Method part = Layout.class.getDeclaredMethod("claimUpToWhereTheRailsPart", List.class,
                org.traincontrol.base.Locomotive.class, java.util.Map.class, java.util.Set.class, int.class,
                java.util.Map.class);

            part.setAccessible(true);

            java.util.Map<String, org.traincontrol.base.Locomotive> places = new java.util.LinkedHashMap<>();

            Object parted = part.invoke(null, java.util.Arrays.asList(xp, yp), loc, places,
                new java.util.HashSet<String>(), 3, null);

            assertEquals(parted, Boolean.TRUE, "two rails answered 0 were declined as rails nobody measured, so the"
                + " squares they share behind the train are left to the ordinary walk's guess of one road");

            assertEquals(places.keySet(), new java.util.LinkedHashSet<>(java.util.Arrays.asList("RP:p", "RP:s")), "the"
                + " claim up to where the rails part does not claim the two squares they share: " + places.keySet());

            java.lang.reflect.Method reach = org.traincontrol.gui.TailCrossedPrompt.class.getDeclaredMethod("reachOf",
                Edge.class, int.class);

            reach.setAccessible(true);

            java.util.Set<String> covered = (java.util.Set<String>) reach.invoke(null, xp, 3);

            assertEquals(covered, new java.util.LinkedHashSet<>(java.util.Arrays.asList("RP:p", "RP:s", "RP:x")), "the"
                + " tail question reads a rail answered 0 as reaching nothing, where a tail of 3 lies over all of it: "
                + covered);
        }
        finally
        {
            loc.setTrainLength(was);
        }
    }

    /**
     * A railway can say which track non-atomic mode could release under a train.
     *
     * The one question the atomic-routes gate asks, at all seven of its doors: non-atomic mode releases an edge as soon
     * as `tailHasProvablyPassed` returns true, immediately when `pathIsUnmeasured` (NO edge on the path has a length).
     * The claims are commented where each is asserted.
     *
     * MUTATION: drop the de-duplication and the first claim goes red; replace the reachability walk with the rule it
     * replaced and the fourth does; mark only the direct predecessors of a destination and the sixth does; bound the
     * walk at two levels and the seventh does; drop `!back.isActive()` and the eighth does; delete `Collections.sort`
     * and the last does.
     *
     * @throws Exception from the model
     */
    @Test
    public void testARailwayCountsItsUnmeasuredDrivableTrack() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("UE_A", false, null);
        layout.createPoint("UE_B", true, "1");
        layout.createPoint("UE_C", true, "2");
        layout.createPoint("UE_D", true, "3");

        Edge ab = layout.createEdge("UE_A", "UE_B");
        Edge bc = layout.createEdge("UE_B", "UE_C");
        Edge cb = layout.createEdge("UE_C", "UE_B");
        Edge cd = layout.createEdge("UE_C", "UE_D");

        ab.setLength(3);
        cd.setLength(2);

        // BOTH DIRECTIONS OF ONE RAIL, neither measured: one piece of track, not two.
        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 1,
            "UE_B and UE_C are joined by one rail written as two edges, and neither has a length, so"
            + " this railway has ONE unmeasured piece of track - it counts "
            + layout.unmeasuredTrackThatCouldBeReleased() + ".  Telling the operator 2 about one piece is a number"
            + " that means nothing to him");

        // THE CONTROL: measured end to end, so nothing is in the way of running non-atomic.
        bc.setLength(1);
        cb.setLength(1);

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 0,
            "every rail has a length and the railway still reports " + layout.unmeasuredTrackThatCouldBeReleased()
            + " without one - so a setup that is measured end to end would have non-atomic mode taken"
            + " away from it at every load");

        // AND TRACK NOTHING CAN BE DRIVEN ONTO: the far end switched off, the length taken away.
        cd.setLength(0);
        layout.getPoint("UE_D").setActive(false);

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 0,
            "the rail into UE_D has no length, but UE_D is switched off - `isPathClear` refuses a path"
            + " whose destination or intermediate point is inactive, so no train can ever be driven"
            + " over it and it must not hold the setting back.  Counted: "
            + layout.unmeasuredTrackThatCouldBeReleased());

        // AND AN UNMEASURED RAIL WITH MEASURED TRACK BETWEEN IT AND EVERY DESTINATION.  The claim the narrowing has:
        // the three above are about rails whose far end IS an active destination, so replacing the reachability walk
        // with "every unmeasured edge" would leave them green.  A rail that cannot be joined to a destination by
        // unmeasured track alone can never be part of an unmeasured path, and refusing the setting for it takes
        // non-atomic mode away from a railway where it is provably safe.
        layout.createPoint("UE_E", false, null);
        layout.createPoint("UE_F", true, "4");

        // ONE-WAY, AND THAT IS THE WHOLE POINT.  `createEdge` makes one directed edge, so
        // a train can be sent UE_C -> UE_E and can never come back: there is no unmeasured way from
        // UE_E to any destination, and the fifth claim below adds the reverse to show the difference.
        Edge ce = layout.createEdge("UE_C", "UE_E");
        Edge ef = layout.createEdge("UE_E", "UE_F");

        ef.setLength(4);

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 0,
            "UE_C - UE_E has no length, but UE_E is not a destination, the rail is one-way, and the"
            + " only way on from UE_E is measured - so no path over that rail can be an unmeasured"
            + " one and no train can be released onto it.  Counted: "
            + layout.unmeasuredTrackThatCouldBeReleased());

        // AND IT COUNTS THE MOMENT THERE IS AN UNMEASURED WAY ON, including the reverse of itself.
        // UE_E -> UE_C is a complete unmeasured path to the destination UE_C, so the rail really
        // could be handed back and the counter is right to say so.
        Edge ec = layout.createEdge("UE_E", "UE_C");

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 1,
            "with the reverse direction added and unmeasured, UE_E -> UE_C is itself a path with no"
            + " measurement anywhere on it, ending at the destination UE_C - so the rail CAN be"
            + " released under a train and has to be counted.  Counted: "
            + layout.unmeasuredTrackThatCouldBeReleased());

        ec.setLength(5);

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 0,
            "the way back from UE_E is measured again, so the rail is once more one a train can only"
            + " be sent along.  Counted: " + layout.unmeasuredTrackThatCouldBeReleased());

        // AND THE WALK IS A CHAIN, not one hop back from each destination.  UE_H reaches a
        // destination only through UE_G, which reaches one only through UE_E: three marks deep, and
        // the rail into UE_G is counted only if the walk got that far.
        layout.createPoint("UE_G", false, null);
        layout.createPoint("UE_H", false, null);

        Edge ge = layout.createEdge("UE_G", "UE_E");
        Edge hg = layout.createEdge("UE_H", "UE_G");

        ef.setLength(0);

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 4,
            "UE_H -> UE_G -> UE_E -> UE_F has no measurement anywhere on it and ends at a"
            + " destination, so every one of its rails would be handed back under the train, and so"
            + " would UE_C - UE_E.  Counted: " + layout.unmeasuredTrackThatCouldBeReleased()
            + " - a walk that marks only the direct predecessors of a destination finds three of"
            + " them, and one that asks whether a rail's far end IS a destination finds one");

        // AND THE WALK RUNS OUT OF TRACK, NOT OUT OF PATIENCE.  The claim above is satisfied
        // by a walk bounded at two levels, because nothing lies behind UE_H: marking it or not changes
        // no count.  One more rail behind it, and only a walk that goes on until there is nothing left
        // to mark finds five.
        layout.createPoint("UE_I", false, null);

        layout.createEdge("UE_I", "UE_H");

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 5,
            "UE_I - UE_H is four unmeasured rails from the destination UE_F, and a train on it would"
            + " be handed its own track back like any other.  Counted: "
            + layout.unmeasuredTrackThatCouldBeReleased() + " - a walk that stops two marks deep"
            + " finds four");

        // AND A POINT THAT IS SWITCHED OFF IS NOT A WAY THROUGH.  The walk's `isActive` test needs an inactive point
        // with a rail out of it, or the line could be deleted with every claim still green: a rail whose only route to
        // a destination passes through one can never be part of an unmeasured path.
        layout.createPoint("UE_J", false, null);
        layout.createPoint("UE_K", false, null);

        layout.getPoint("UE_J").setActive(false);

        layout.createEdge("UE_J", "UE_E");
        layout.createEdge("UE_K", "UE_J");

        assertEquals(layout.unmeasuredTrackThatCouldBeReleased().size(), 6,
            "UE_J - UE_E ends at a square the walk has marked, so it counts; UE_K - UE_J does not,"
            + " because UE_J is switched off and no path may run through it - a train cannot be"
            + " driven from UE_K to any destination over unmeasured track.  Counted: "
            + layout.unmeasuredTrackThatCouldBeReleased() + " - seven means the walk marks squares"
            + " that are off, and a railway is refused non-atomic mode over track nothing can reach");

        // AND IN ALPHABETICAL ORDER: `this.edges` is a HashMap, the message names only the first three, and three
        // arbitrary names that move between runs are no list to work down.
        java.util.List<String> named = layout.unmeasuredTrackThatCouldBeReleased();

        java.util.List<String> sorted = new java.util.ArrayList<>(named);

        java.util.Collections.sort(sorted);

        assertEquals(named, sorted,
            "the rails come back in the graph's own order, which is a HashMap's and therefore none -"
            + " the refusal shows the first three of them, so on the same railway it would name a"
            + " different three from one run to the next.  " + named);
    }

    /**
     * Locking a path reserves every point along it, not only its destination.  The reservation is what holds a junction
     * the train has passed against a second train that could reach it another way.  Reserving a path and placing a
     * train are different operations and do not share an entry point: a sweep that took a locomotive off every other
     * point would leave a train holding C alone, with B free for anyone.  In simulate mode, so no accessory has to
     * confirm.
     */
    @Test
    public void testLockingAPathReservesEveryPointOnIt() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RS_A", false, null);
        // Sensors of its own: 1 and 2 are the live layout's, whose saved occupancy can refuse the path
        model.newFeedback(9901, null);
        model.newFeedback(9902, null);
        layout.createPoint("RS_B", true, "9901");
        layout.createPoint("RS_C", true, "9902");

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
     * A square is emitted as several Points, one per side a train can arrive by, all the same piece of track, so
     * occupancy is read across all the copies or the westbound copy would read free while a train stood on the
     * eastbound one.  The block ties the copies together, not the s88: different places share a sensor on a real layout
     * (a station, its approach guard and a reversing point), so the sensor cannot say which Points are one square.
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

        // But a LOCK edge asks the narrower question: it is track held clear so that two routes cannot take one throat
        // at once, not a claim on the platform beyond it.  Asked of the whole square, a pair of converging platforms
        // would refuse every route out of either of them whenever either had a train on it, which is always.
        assertFalse(toWest.isLockHeld(second),
            "a lock edge must not be blocked by a train standing on the far platform");

        assertTrue(toWest.isOccupied(second, true),
            "while running ONTO that track is still refused - the copies are one piece of rail");

        // And the same of the copy the train is ACTUALLY standing on: the sensor a train stands on is never the track a
        // lock edge protects, since reduction cuts an edge at every sensor and a Point's tile appears in the path of
        // none of them.  Were it asked, any train parked next to a junction would be a permanent roadblock for every
        // route across it, and with two such trains a deadlock neither could leave.
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
     * The route preference defaults to what every earlier version did, and nothing has to be set for that to be true: a
     * railway driven from a script has no menu to look at, so upgrading must never quietly re-route somebody's trains.
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
     * Two routes to one place: a short one past two stations, and a longer way round past none.  Every preference
     * should pick a different winner, the only evidence that the setting does anything.  The cost function is exercised
     * through pickPath's own choice, so this fails if the ranking is wired up wrongly as well as computed wrongly.
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

        // A station, but not one autonomy may send a train TO: otherwise "go to RP_ViaStation" is itself a route past
        // no stations, so fewest-stations would pick it and the assertion could not say whether it had measured the
        // route.
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
     * of the others knows or cares where trains have already been, so a layout with a favorite loop
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
     * Where the trains have been survives a setup gesture.
     *
     * `lastArrival` is kept in memory, but `parseAuto` replaces the whole `Layout`, and a diagram edit, placing a
     * locomotive or loading a configuration all come through there.  `TrainControlUI` carries placements, sides, roads
     * and pending turns across that rebuild and this history goes with them: an operator running with Least Recently
     * Visited who stops to place a train must not get a rule that knows nothing.  The carry has to be READABLE, not
     * merely copied: keyed by point NAME, not by `recencyKeyOf` (unique ids are re-allocated for every Point built, so
     * such an entry is dead weight).  It asserts the state `recencyOf` reads across a real `parseAuto`, not `pickPath`
     * afterwards; `testLeastRecentlyVisitedGoesWhereTrainsHaveNotBeen` says the map is read, this that it is still
     * there to read.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testTheVisitHistorySurvivesASetupRebuild() throws Exception
    {
        Layout before = model.getAutoLayout();

        assertNotNull(before, "precondition: no autonomy layout is loaded at all");

        // THE SHARED SETUP IS PUT BACK.  This is the only test here that goes through parseAuto, which
        // replaces the layout every other test in this class reads.
        String was = before.toJSON().toString();

        // AND ITS VISITS, which the re-parse below carries forward: put back as they were, not with this test's arrival in
        // them (OB-267)
        java.util.Map<String, Long> visitsWere = new java.util.HashMap<>(before.getVisitHistory());

        try
        {
            String visited = before.getPoints().iterator().next().getName();

            before.noteArrivalForTest(visited);

            java.util.Map<String, Long> history = before.getVisitHistory();

            assertFalse(history.isEmpty(),
                "precondition: noting an arrival at " + visited + " recorded nothing, so there is no"
                + " history for a rebuild to lose");

            // THE GESTURE.  This is what applying a diagram edit or placing a locomotive does.
            model.parseAuto(was);

            Layout rebuilt = model.getAutoLayout();

            assertNotSame(rebuilt, before, "precondition: parseAuto did not actually build a new layout");

            assertEquals(rebuilt.getVisitHistory(), history,
                "after a setup rebuild the layout no longer knows a train has just been to " + visited
                + ", so every station reads as never visited and Least Recently Visited chooses at"
                + " random - the degraded state that rule was written to end (AUR-C3)");
        }
        finally
        {
            model.parseAuto(was);

            Layout restored = model.getAutoLayout();

            java.lang.reflect.Field visits = Layout.class.getDeclaredField("lastArrival");

            visits.setAccessible(true);

            ((java.util.Map<?, ?>) visits.get(restored)).clear();

            restored.restoreVisitHistory(visitsWere);
        }
    }

    /** The name of the point a route reaches first, which is what says which way it went. */
    private String nameOfSecondPoint(java.util.List<Edge> path)
    {
        assertNotNull(path, "no route was offered at all");

        return path.get(0).getEnd().getName();
    }

    /**
     * An accessory this layout already has, borrowed rather than created: newSignal adds to the live accessory
     * database, which is the user's real one, and a test has no right to persist an invented signal in it.
     * refreshProtectingSignal resolves the pairing by NAME and calls setState on whatever it finds, so any accessory
     * the layout carries exercises the same path and leaves the database as it was found.
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
     * The memo that stops a redundant command belongs to the whole square, because "claimed" is a fact about the whole
     * square.  Kept per Point, a refresh on one copy that saw the square claimed through ANOTHER copy would write true
     * into its own memo while standing empty and nothing would write false back, so the next real arrival there would
     * send nothing and leave the signal GREEN with a train standing at the platform.  The sequence below is that
     * poisoning.
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
     * Two signals, one platform: BOTH follow it.  A platform reachable from two directions needs a signal on each
     * approach; a train standing there has to put both to red and leaving has to release both.  The failure is quiet in
     * the worst way: the approach whose signal was dropped stays green with a train in the platform.
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
     * Switching a protecting signal by hand asks only in the direction that removes protection (MT-256).
     *
     * The question needs no running railway, standing train or fighting route: `clearsProtection` is the one rule all
     * three doors consult (the diagram tile, the switch keyboard and the route), and the caller supplies the direction.
     * The aspect is half the rule: turning protection ON is what the protection mechanism would do anyway, and refusing
     * it was over-strictness the route door had already had removed.  So green with a train standing there is asked;
     * red there never; either way with the platform empty never.  Those are steps 2, 3 and 4 of the manual test; step 5
     * (a route setting the same signal green is still refused) is `heldReason`, covered by
     * `testARouteDoesNotThrowSwitchesUnderATrain`.
     *
     * MUTATION: drop `commandingGreen` from `clearsProtection` and the second assertion fails; drop the occupancy half
     * and the third does.
     */
    @Test
    public void testSwitchingAProtectingSignalByHandAsksOnlyOneWay() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback platform = model.newFeedback(210, null);

        model.setFeedbackState(platform.getName(), false);

        MarklinAccessory signal = borrowedAccessory(0);

        layout.createPoint("MT256_PLATFORM", true, platform.getName());

        running(layout);

        layout.getPoint("MT256_PLATFORM").setProtectingSignals(
            java.util.Arrays.asList(signal.getName()));

        MarklinLocomotive train = model.getLocByName(model.getLocList().get(0));

        // THE CONTROL, and step 4 of the manual test: with the platform EMPTY, neither direction is a
        // question.  Without this, "asked when green" passes on a rule that asks about everything.
        assertFalse(layout.clearsProtection(signal, true),
            "control: with no train at the platform, turning its signal green was still a question - "
            + "so the rule is not about occupancy at all");

        assertFalse(layout.clearsProtection(signal, false),
            "control: with no train at the platform, turning its signal red was a question");

        layout.getPoint("MT256_PLATFORM").setLocomotive(train);

        try
        {
            // Step 3: green, with a train standing there.
            assertTrue(layout.clearsProtection(signal, true),
                "turning a protecting signal GREEN with a train standing at the platform it protects "
                + "was not a question.  That is the command that takes protection off, and it is the "
                + "one the operator has to be asked about (MT-256)");

            // Step 2: red, with the same train standing there.
            assertFalse(layout.clearsProtection(signal, false),
                "turning a protecting signal RED was a question.  That is doing what the protection "
                + "mechanism would do anyway - the over-strictness WK3-B1 removed from the route door "
                + "and this rule must not put back");

            // AND AN ACCESSORY THAT PROTECTS NOTHING is never a question, whichever way it is sent.
            assertFalse(layout.clearsProtection(borrowedAccessory(1), true),
                "an accessory that protects no platform was treated as one, so every hand-switched "
                + "turnout on the railway would raise a dialog");
        }
        finally
        {
            layout.getPoint("MT256_PLATFORM").setLocomotive(null);
        }

        // And it stops asking the moment the train leaves.
        assertFalse(layout.clearsProtection(signal, true),
            "the train left and the signal is still protected, so the question outlives the train it "
            + "was about");
    }
    /**
     * A square switched out of service refuses trains at every door but its own (Adam).
     *
     * **"In manual mode, inactive endpoints and intermediates should be refused as well.  Just not
     * inactive start points.  Inactive really means nothing can pass."**
     *
     * The rule is the same for intermediates and destinations, in manual mode as in autonomy: a destination fenced
     * behind `isAutoRunning` would let a hand-driven send finish on a closed square that a Return Home run refuses.
     * The start stays exempt, the whole exception: a train standing on a switched-off square is driven out by hand.
     * The runtime rule is what stops a train, so it is asserted as well as the walks.
     *
     * MUTATION: put `&& this.isAutoRunning()` back on the destination rule and the second assertion
     * fails; refuse the start too and the third does.
     */
    @Test
    public void testAClosedSquareRefusesTrainsExceptAsAStart() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback one = model.newFeedback(220, null);
        MarklinFeedback two = model.newFeedback(221, null);
        MarklinFeedback three = model.newFeedback(222, null);

        for (MarklinFeedback sensor : new MarklinFeedback[] {one, two, three})
        {
            model.setFeedbackState(sensor.getName(), false);
        }

        layout.createPoint("SHUT_A", true, one.getName());
        layout.createPoint("SHUT_B", true, two.getName());
        layout.createPoint("SHUT_C", true, three.getName());

        layout.createEdge("SHUT_A", "SHUT_B");
        layout.createEdge("SHUT_B", "SHUT_C");

        MarklinLocomotive train = model.getLocByName(model.getLocList().get(0));

        java.util.List<Edge> toB = new java.util.ArrayList<>();
        toB.add(layout.getEdge("SHUT_A", "SHUT_B"));

        java.util.List<Edge> toC = new java.util.ArrayList<>();
        toC.add(layout.getEdge("SHUT_A", "SHUT_B"));
        toC.add(layout.getEdge("SHUT_B", "SHUT_C"));

        layout.getPoint("SHUT_A").setLocomotive(train);

        // NOT running: this is the manual tier, which is exactly what the ruling is about.
        assertFalse(layout.isAutoRunning(),
            "precondition: autonomy must NOT be running, or every rule below is the autonomy one and "
            + "the test says nothing about the manual tier");

        // THE CONTROL: with everything in service the path is clear, so a refusal below is about the
        // closure and not about a fixture that never worked.
        assertTrue(layout.isPathClear(toC, train, false),
            "control: the path must be clear with every square in service, or nothing below is "
            + "measuring what closing one does");

        layout.getPoint("SHUT_C").setActive(false);

        assertFalse(layout.isPathClear(toC, train, false),
            "a hand-driven send was allowed to FINISH on a square switched out of service.  Inactive "
            + "means nothing can pass, and the destination rule was fenced behind isAutoRunning so "
            + "the same square refused a Return Home run and accepted this one");

        layout.getPoint("SHUT_C").setActive(true);
        layout.getPoint("SHUT_B").setActive(false);

        assertFalse(layout.isPathClear(toC, train, false),
            "a hand-driven send was allowed to pass THROUGH a square switched out of service");

        // AND THE START, which is the one case Adam kept.
        layout.getPoint("SHUT_B").setActive(true);
        layout.getPoint("SHUT_A").setActive(false);

        assertTrue(layout.isPathClear(toB, train, false),
            "a train standing on a square that has been switched off can no longer be driven off it.  "
            + "That is how a train is held in place, and it is the whole of the exception: \"just not "
            + "inactive start points\"");

        layout.getPoint("SHUT_A").setActive(true);

        // AND THE DOOR, not only the rule: `isPathClear` refuses, `getPossiblePaths` is what the right-click menu and
        // the Locomotive commands tab OFFER, and "should follow" is how a rule comes to be tested while its call site
        // is not.
        assertTrue(offers(layout, train, "SHUT_C"),
            "control: SHUT_C must be offered with every square in service, or the assertion below "
            + "passes on a door that offers nothing at all");

        layout.getPoint("SHUT_C").setActive(false);

        assertFalse(offers(layout, train, "SHUT_C"),
            "a square switched out of service is still offered as somewhere to send a train by hand.  "
            + "isPathClear refuses it now, so the operator is offered a destination the railway will "
            + "refuse - which is the shape testTheCheckerAgreesWithTheBuild exists to stop");

        layout.getPoint("SHUT_C").setActive(true);
    }

    /**
     * Whether the manual destination doors would offer this point for this locomotive.
     *
     * @param layout the railway
     * @param loc the train
     * @param point the destination
     * @return true when a path to it is offered
     */
    private static boolean offers(Layout layout, MarklinLocomotive loc, String point)
    {
        for (java.util.List<Edge> path : layout.getPossiblePaths(loc, false))
        {
            if (path.get(path.size() - 1).getEnd().getName().equals(point)) return true;
        }

        return false;
    }
    /**
     * One signal protecting TWO platforms stays red while either of them is claimed.
     *
     * One signal can have a second owner: 63 protects R1 **and** R0; 64 protects R2 **and** R0, the shape of Adam's
     * 2.8.1 file, which reds Signals 63 and 64 on the edge `TopMainR0 -> TopMainPost`.  The model and the pairing UI
     * allow it.  The failure to catch is the release, not the claim: the moment one platform empties the obvious code
     * sets the signal green, and if the other is still occupied the signal lies about an occupied platform, worse than
     * never having paired it because it reads as protection.  The test next door is the other direction (two signals on
     * one platform) and cannot see this.
     *
     * MUTATION: make `refreshOneSignal` ask only the point it was called for, rather than every point that lists the
     * signal, and the third assertion fails.
     */
    @Test
    public void testOneSignalProtectingTwoPlatforms() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback first = model.newFeedback(105, null);
        MarklinFeedback second = model.newFeedback(106, null);

        model.setFeedbackState(first.getName(), false);
        model.setFeedbackState(second.getName(), false);

        MarklinAccessory shared = borrowedAccessory(0);

        layout.createPoint("PLATFORM_ONE", true, first.getName());
        layout.createPoint("PLATFORM_TWO", true, second.getName());

        running(layout);

        // The same signal on both, which is what pairing 63 to R1 and to R0 produces.
        layout.getPoint("PLATFORM_ONE").setProtectingSignals(Arrays.asList(shared.getName()));
        layout.getPoint("PLATFORM_TWO").setProtectingSignals(Arrays.asList(shared.getName()));

        MarklinLocomotive one = model.getLocByName(model.getLocList().get(0));
        MarklinLocomotive two = model.getLocByName(model.getLocList().get(1));

        assertNotSame(one, two, "precondition: two different locomotives are needed, or both "
            + "platforms are claimed by the same one and the release below is not a release");

        // THE CONTROL: green while both stand empty, so the assertions below are about occupancy
        // and not about a signal that was red from the start.
        assertTrue(shared.isGreen(),
            "precondition: the signal must start green, or nothing here shows it turning red");

        layout.getPoint("PLATFORM_ONE").setLocomotive(one);

        assertTrue(shared.isRed(),
            "a shared signal stayed green with a train in the first platform it protects");

        layout.getPoint("PLATFORM_TWO").setLocomotive(two);

        assertTrue(shared.isRed(), "and with both claimed");

        // THE RELEASE, which is the whole test.
        layout.getPoint("PLATFORM_ONE").setLocomotive(null);

        assertTrue(shared.isRed(),
            "one platform emptied and the shared signal went green while the OTHER platform it "
            + "protects still holds a train.  A signal shows one aspect, so two owners have to agree "
            + "- and this is worse than never pairing it, because a green signal in front of an "
            + "occupied platform reads as protection (RG5-C1).  Adam is pairing 63 and 64 to "
            + "TopMainR0 on top of R1 and R2, which is exactly this shape");

        // And only when the last of them lets go.
        layout.getPoint("PLATFORM_TWO").setLocomotive(null);

        assertTrue(shared.isGreen(),
            "both platforms are empty and the shared signal was left red, so it never comes back");
    }
    /**
     * A configuration carrying a list of signals is read back as that list.  parseAuto has always taken a bare string,
     * and a file written before this feature still holds one; both shapes must arrive as a list, or an upgraded railway
     * comes back with every platform unprotected and nothing saying so.
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
     * Every occupancy change refreshes the protecting signals, but setting a layout up before a run must not drive real
     * ironwork: cutting a locomotive off a platform with Control+X must not throw its signals on the spot.  The wait
     * for the run to start is not a loss of protection: the memo of what each signal was last told is cleared when a
     * run begins, so the first arrival commands them for real.
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
     * Makes a layout report itself as running, without dispatching anything: isRunning() is true while any locomotive
     * is active, so an entry in the map executePath writes (under the same lock) is enough.
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
     * Hand placement is closed, but a FILE is the other door - one written by a version before the rule existed,
     * hand-edited, or brought from another machine.  fromJSON checks for a duplicate locomotive and not for a
     * duplicate square, so load itself has to repair a square holding two trains.
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
        model.parseAuto(readTheSample());
    }

    /**
     * The sample autonomy layout this class runs on.  A test fixture, not part of the program: the application never
     * read it, so it moved out of the jar's resources (it was `TrainControlUI.AUTONOMY_SAMPLE`).
     *
     * @return the file's text
     */
    private static String readTheSample() throws Exception
    {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(SAMPLE_AUTONOMY)),
            java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Where the sample autonomy layout is, from the project root the tests run in. */
    private static final String SAMPLE_AUTONOMY = "test/sample_autonomy.json";

    /**
     * Every key parseAuto reads off a Point, toJSON writes back - checked by asking the reader.
     *
     * A test that names the fields it knows about cannot catch the field nobody thought of.  So the source of truth is
     * the READER: every key parseAuto looks for on a point is listed here, and the test fails if any is absent from
     * what toJSON produced; adding a key to both means adding it to this list, which is when somebody has to think
     * about it.
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
        boolean hadAllowOnly = point.isAllowOnlyListed();

        // These four are put back in the finally below, or this point of the shared sample layout would stay
        // non-dispatchable and speed-scaled for every test that runs after this one in the same JVM.
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
            point.setAllowOnlyListed(true);
            point.setBlockedBy(Arrays.asList(blocker));

            // Not the default, deliberately: the format omits a field that holds its default ("active" is written only
            // when false), so demanding every key would assert something it does not promise.  What it DOES promise is
            // that a value somebody set is written, so every field is moved off its default first.
            point.setActive(false);

            org.json.JSONObject json = point.toJSON();

            // The keys parseAuto looks for on a point, in the reader's order so the two compare by eye.  "terminus",
            // "reversing" and "loc" are checked separately below: the first two are mutually exclusive (each setter
            // refuses the other), and "loc" needs a Point attached to a live layout, which this shared sample Point
            // already is.
            String[] readsOffAPoint =
            {
                "name", "station", "s88", "x", "y",
                "block", "protectingSignal", "home", "maxTrainLength",
                "active", "autoDestination", "priority", "speedMultiplier",
                "blockedBy", "excludedLocs", "excludedLocsMode"
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
            point.setAllowOnlyListed(hadAllowOnly);
            point.setMaxTrainLength(hadMaxTrainLength);
            point.setPriority(hadPriority);
            point.setSpeedMultiplier(hadSpeedMultiplier);
            point.setAutoDestination(hadAutoDestination);
        }

        // "terminus", "reversing" and "loc" - checked on a standalone Point rather than the shared sample layout's, so
        // setLocomotive cannot reach into MarklinControlStation.clearLocomotiveExcept and move a locomotive the rest of
        // this class depends on (it only does that when the Point has a layout attached).
        //
        // MUTATION each of the three assertions below catches on its own: delete the corresponding `jsonObj.put(...)`
        // write in Point.toJSON (terminus, reversing, loc), and export silently drops that flag or locomotive.
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
     * Somewhere to GO is not the same as somewhere to be sent.  A square is emitted as one Point per arrival side, and
     * a copy can have somewhere to go and nowhere to be DISPATCHED (everything it reaches is a plain point, a reversing
     * point or parking), while autonomy only ever sends a train to a destination.  On the sample layout Tunnel
     * (northbound) offers routes and Tunnel (southbound) offers none, so placement has to tell them apart.
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
     * **The rule is Adam's**: *"force a graceful stop, alert the user, then unlock."*
     *
     * The `RuntimeException` handler removes the locomotive from the maps `getActiveAccs` reads to know which
     * accessories a route must not throw, so its route protection is gone at that instant and leaving the path locked
     * would leave the track held by nobody.  So the handler stops everything (which makes releasing safe: `running` is
     * false, so autonomy dispatches nothing new), tells the operator to look, because this is the one case where the
     * model frees track a train may physically be standing on, and lets the track go.  That sentence is in all eight
     * languages.
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

        // MOVING BEFORE THE FAULT.  The callback throws ahead of anything that gives the train a speed, so a locomotive
        // standing at 0 reads 0 afterwards whether or not the handler stopped it.
        loc.setSpeed(30);

        assertEquals(loc.getSpeed(), 30, "precondition: the locomotive must be moving before the fault");

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

        assertEquals(loc.getSpeed(), 0,
            "the locomotive that failed is still under power.  Adam’s ruling is \"force a graceful "
            + "stop, alert the user, then unlock\", and stopLocomotives() only sets running = false - "
            + "the only thing that stops THIS train is setSpeed(0), and it used to run after the "
            + "release.  unlockPath is synchronized on the layout monitor, which "
            + "configureAndLockPath holds for seconds on a long path, so the train could keep "
            + "running on track the model had just declared free (VD10-B1)");
    }

    /**
     * A failure does not release an edge the tail had already given up.
     *
     * `unlockPath` reads `releasedEarly` to know which edges the tail released as it passed them, so as not to release
     * them again; the comment at that lookup says what a second release costs: *"the second release would take away a
     * claim somebody else made in between"*.  The ordinary ending calls `unlockPath` and clears the map after it; the
     * failure handler must do the same, or the lookup is null and every early-released edge is released twice
     * (`atomicRoutes` is `false` on the operator's own configuration, so this is the live branch).  Seeded, not driven:
     * a real tail needs a train in motion, and what is under test is whether the map is still populated.
     *
     * MUTATION: moving `releasedEarly.remove(loc)` back above the release fails this.
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


        // The claim is made IN BETWEEN, after the path is locked and before it fails, because that is the sequence the
        // lookup exists for (a throat claimed beforehand refuses the lock and the handler never runs).  Read at the
        // MOMENT OF FAILURE, since a baseline taken beforehand is a different number.
        final int[] atTheMoment = { -1 };
        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            // SEEDED HERE, not before the dispatch: executePathInternal installs its own clearedEdges entry when the
            // run starts, overwriting anything put there earlier; by this callback that entry exists, as when a real
            // tail adds.
            java.util.Set<org.traincontrol.automation.Edge> given = map.get(loc);

            if (given == null) { given = new java.util.HashSet<>(); map.put(loc, given); }

            given.add(first);

            // AND AS RELEASED, which is what the unlock reads to know it was given back: the cleared set is kept
            // in atomic mode too, where nothing in it is released, so it alone no longer says so.
            try
            {
                java.lang.reflect.Field releasedField = Layout.class.getDeclaredField("releasedEarly");

                releasedField.setAccessible(true);

                @SuppressWarnings("unchecked")
                java.util.Map<Locomotive, java.util.Set<org.traincontrol.automation.Edge>> released =
                    (java.util.Map<Locomotive, java.util.Set<org.traincontrol.automation.Edge>>) releasedField.get(layout);

                released.computeIfAbsent(loc, k -> new java.util.HashSet<>()).add(first);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }

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

        // THE COUNT, not the boolean.  `release()` floors at zero and occupancy is a COUNT, so a throat held by two
        // claims and released once is still "occupied", and asserting isOccupied would pass against both orderings.
        java.lang.reflect.Field occupancy =
            org.traincontrol.automation.Edge.class.getDeclaredField("occupancy");

        occupancy.setAccessible(true);

        assertTrue(atTheMoment[0] > 0, "the callback never ran, so nothing below is exercised");

        assertEquals(occupancy.getInt(second), atTheMoment[0],
            "the failure released a throat this locomotive had already given up, taking away the "
            + "claim another train made after the tail went by.  unlockPath reads releasedEarly to "
            + "know which edges not to release twice, and the handler had emptied that map fifty "
            + "lines earlier - so the lookup was null and every early-released edge went again, "
            + "lock edges included.  The ordinary ending clears the map AFTER the release, which is "
            + "where this one clears it now (VD10-A1)");
    }

    /**
     * The diagram menu's split: what autonomy would choose, and what it never would (FR-058).
     *
     * The top-level menu shows only the active stations that can be chosen in full autonomy; More Destinations lists
     * the points that cannot be but are still valid.  The cap matters: the top level shows twelve and then an ellipsis,
     * so a parking track autonomy never picks would cost a line an ordinary platform wants.  The menu needs a window,
     * so the predicate it splits on, `isChoosableByAutonomy` (which the "no available paths" window and the caption
     * rule ask too), is asserted.
     *
     * MUTATION: making `isChoosableByAutonomy` ignore `isAutoDestination` puts the parking track in the top-level list
     * and fails this.
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
        // hand, never picked on its own.  This is the case that would eat a line of the cap.
        park.setAutoDestination(false);

        assertFalse(layout.isChoosableByAutonomy(park),
            "a square marked as not an automatic destination is still being offered to autonomy, so "
            + "the diagram menu would keep it in the capped top-level list (FR-058)");

        // And one switched off, which stays off the menu altogether - Adam's earlier ruling, which
        // FR-058 does not change: "make the inactive stations disappear from the track diagram menu".
        off.setActive(false);

        assertFalse(layout.isChoosableByAutonomy(off),
            "a switched-off square is being offered to autonomy");

        // (No assertion reads back the setter two lines above: it would pass for as long as a setter sets.)
    }

    /**
     * A BERTH stays in the base list, and an excluded square leaves the menu (Adam).
     *
     * Two rulings on one gesture: *"We should still show the same number of base options (unless unselectable)"* and
     * *"if it excludes the loc, don't even include it in the list."*  The menu asks `isOfferableToOperator` and not
     * `isChoosableByAutonomy(end, locomotive)`, which would move a terminus out of the base list.
     *
     * The terminus rule is about a berth (OB-205, MT-367): *"75 407 DB can go from Tunnel to BottomMainC manually, even
     * though it is not reversible and THIS IS NOT A PARKING BERTH (excluded from autonomy). it should not be allowed to
     * be chosen."*  So a berth is offered to a train that cannot reverse, and an ORDINARY terminus is not.
     *
     * MUTATION: dropping `isAutoDestination()` from `isOfferableToOperator`'s terminus clause fails the first
     * assertion; dropping the clause entirely fails the second; dropping the exclusion clause fails the third; dropping
     * `isActive` fails the fourth.  The menu's choice of predicate needs a window: that half is MT-266.
     */
    @Test
    public void testATerminusIsOfferedByHandAndAnExcludedSquareIsNotOfferedAtAll() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback end = model.newFeedback(170, null);

        model.setFeedbackState(end.getName(), false);

        Point terminus = layout.createPoint("VD11_TERMINUS", true, end.getName());

        terminus.setTerminus(true);

        Locomotive plain = model.getLocByName(model.getLocList().get(0));

        // Borrowed from the real database and given back - the suite runs against the live LocDB, and
        // a locomotive left non-reversible would follow this test into every class after it.
        boolean wasReversible = plain.isReversible();

        try
        {
            plain.setReversible(false);

            // THE RULING: a train that cannot turn round may still be backed into a BERTH by hand -
            // somewhere autonomy will never send it, which is what that exemption is for.
            terminus.setAutoDestination(false);

            assertTrue(layout.isOfferableToOperator(terminus, plain),
                "a parking berth is being kept off the menu for a non-reversible train. Putting a train"
                + " away by hand is exactly what Adam's 2026-09-01 exemption is for - \"the operator"
                + " asking for that BERTH by hand is no longer refused\" - and it survived the"
                + " narrowing of 2026-09-12");

            // AND THE HALF HE NARROWED IT TO (OB-205): an ordinary terminus, one autonomy may
            // choose, is not offered to a train that could not get out of it again.
            terminus.setAutoDestination(true);

            assertFalse(layout.isOfferableToOperator(terminus, plain),
                "an ordinary terminus is still offered to a train that cannot reverse. Adam, OB-205:"
                + " \"75 407 DB can go from Tunnel to BottomMainC manually, even though it is not"
                + " reversible and this is not a parking berth (excluded from autonomy). it should not"
                + " be allowed to be chosen\"");

            terminus.setAutoDestination(false);

            // AND THE OTHER RULING: an exclusion is not a demotion, it is a removal.
            MarklinFeedback barred = model.newFeedback(171, null);

            model.setFeedbackState(barred.getName(), false);

            Point excluded = layout.createPoint("VD11_EXCLUDED", true, barred.getName());

            assertTrue(layout.isOfferableToOperator(excluded, plain),
                "an ordinary station is already off the menu, so the exclusion below proves nothing");

            excluded.getExcludedLocs().add(plain);

            assertFalse(layout.isOfferableToOperator(excluded, plain),
                "a station that excludes this train is still on its menu - Adam: 'if it excludes the loc, "
                + "don't even include it in the list'");

            // And the earlier ruling this shares a method with, which is the one it must not break.
            excluded.setActive(false);

            assertFalse(layout.isOfferableToOperator(excluded, null),
                "a switched-off square is still on the menu, which is the 2026-09-01 ruling");
        }
        finally
        {
            plain.setReversible(wasReversible);
        }
    }
    /**
     * A failure during the LOCK PHASE leaves the train where it is standing, and leaves other trains' claims alone.
     *
     * The fixture has to reach the lock loop: a `null` in an edge's `lockEdges` throws earlier, in `isPathClear`, a
     * strictly weaker window.  So a config command is mapped to a null state on an accessory that exists: the preview
     * records it without formatting, and the lock loop's `configureEdge(e, null)` throws inside the try, after
     * `edgesLocked++`, `setOccupied()` and `reserve()`, so `handleMisconfiguredPath` genuinely runs.
     *
     * The handler must not unlock over that recovery: `unlockPath`'s `i == 0` clause would clear the start reservation
     * just written, and every never-taken edge would be released, cascading to lock edges whose occupancy is a COUNT
     * shared with other dispatches.  Only the first is asserted: any claim that makes the second observable also makes
     * `isPathClear` refuse the path.
     *
     * MUTATION: removing the `hadItsPath` gate fails the second assertion.
     */
    @Test
    public void testALockPhaseFailureLeavesTheTrainWhereItStands() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback from = model.newFeedback(180, null);
        MarklinFeedback mid = model.newFeedback(181, null);
        MarklinFeedback to = model.newFeedback(182, null);

        model.setFeedbackState(from.getName(), false);
        model.setFeedbackState(mid.getName(), false);
        model.setFeedbackState(to.getName(), false);

        layout.createPoint("LOCKFAIL_FROM", true, from.getName());
        layout.createPoint("LOCKFAIL_MID", true, mid.getName());
        layout.createPoint("LOCKFAIL_TO", true, to.getName());

        layout.createEdge("LOCKFAIL_FROM", "LOCKFAIL_MID");
        layout.createEdge("LOCKFAIL_MID", "LOCKFAIL_TO");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        Point start = layout.getPoint("LOCKFAIL_FROM");

        start.setLocomotive(loc);

        java.util.List<Edge> path = new java.util.ArrayList<>();

        path.add(layout.getEdge("LOCKFAIL_FROM", "LOCKFAIL_MID"));
        path.add(layout.getEdge("LOCKFAIL_MID", "LOCKFAIL_TO"));

        assertNotNull(path.get(0), "the fixture produced no first edge");
        assertNotNull(path.get(1), "the fixture produced no second edge");

        // An accessory that EXISTS, commanded to a null state.  The preview formats nothing and
        // passes; the lock loop formats it and throws.
        MarklinAccessory real = model.newSwitch(190,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2, false);

        assertNotNull(model.getAccessoryByName(real.getName()),
            "the accessory has to exist, or configureEdge refuses in the preview and the lock loop is "
            + "never reached at all");

        path.get(0).addConfigCommand(real.getName(), null);


        layout.runLocomotives();

        RuntimeException caught = null;

        try
        {
            layout.executePath(path, loc, 20, null);

            fail("the lock loop did not throw, so the handler under test never ran");
        }
        catch (RuntimeException expected)
        {
            caught = expected;
        }

        // WHERE IT THREW.
        java.io.StringWriter trace = new java.io.StringWriter();

        caught.printStackTrace(new java.io.PrintWriter(trace));

        // `configureEdge` ALONE DOES NOT SAY WHICH: `configureAndLockPath` calls it twice over, through `isPathClear`'s
        // preview and directly in the lock loop, so a stack carrying it fits both the strong window and the weak one.
        // `isPathClear` tells them apart: it is in the preview stack and not in the lock loop's.
        assertTrue(trace.toString().contains("configureEdge"),
            "the failure did not come out of configureEdge at all, so this is not the window ACC-A1 "
            + "is about.  Stack was:" + trace);

        assertFalse(trace.toString().contains("isPathClear"),
            "the failure came out of the PREVIEW rather than the lock loop - isPathClear is in the "
            + "stack, so nothing had been locked and handleMisconfiguredPath had nothing to recover. "
            + "That is the strictly weaker window the second fixture was rejected for.  Stack was:"
            + trace);

        assertFalse(layout.isRunning(),
            "the run did not stop itself, so this is not the state the handler leaves behind");

        assertEquals(start.getCurrentLocomotive(), loc,
            "the failed locomotive was erased from the model.  configureAndLockPath had released what "
            + "it took and re-reserved the train on the point it never left; the handler then "
            + "unlocked the WHOLE path over the top of that, and unlockPath's i == 0 clause cleared "
            + "the start.  The train stands on a square the model believes is empty, so pickPath can "
            + "route another train into it (ACC-A1)");

    }

    /**
     * Autonomy does not send a reversible train to a terminus it could not leave again at its length.
     *
     * A may-turn square's turning copy is a terminus whose one way out is back the way the train came.  On Adam's
     * railway BottomMainB admits a train of 4 and every station its turning copy leads to is shorter, so a reversible
     * train of 4 that autonomy turned there never moved again, and Return Home found no plan for the whole fleet.  A
     * train that cannot reverse is already kept out of a terminus; this is the same rule for one that can, at its
     * length.  The terminus is first by priority and leads only to a station of 2: a train of 4 is sent to the other
     * station, a train of 2 to the terminus, and the Why Not Moving? reasons say the same.
     *
     * MUTATION: drop the new clause from pickPath's terminus filter and the first assertion fails; have it refuse every
     * terminus and the third does; drop it from the yield probe and the last does.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAReversibleTrainIsNotSentToATerminusItCouldNotLeave() throws Exception
    {
        Layout layout = new Layout(model);

        String[] names = {"TL_Start", "TL_Turn", "TL_Short", "TL_Long"};
        int[] sensors = {8964, 8965, 8966, 8967};

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback fb = model.newFeedback(sensors[i], null);

            model.setFeedbackState(fb.getName(), false);

            layout.createPoint(names[i], true, fb.getName());
        }

        Point turn = layout.getPoint("TL_Turn");

        turn.setTerminus(true);
        turn.setMaxTrainLength(4);

        // THE FIRST CHOICE: bands are settled in priority order, so a train that may go there is sent there
        turn.setPriority(5);

        layout.getPoint("TL_Short").setMaxTrainLength(2);
        layout.getPoint("TL_Long").setMaxTrainLength(9);

        layout.createEdge("TL_Start", "TL_Turn");
        layout.createEdge("TL_Turn", "TL_Short");
        layout.createEdge("TL_Start", "TL_Long");

        layout.setPathPreference(Layout.PathPreference.FEWEST_POINTS);

        org.traincontrol.marklin.MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        // Borrowed from the real database and given back
        boolean wasReversible = loc.isReversible();
        Integer wasLength = loc.getTrainLength();

        try
        {
            loc.setReversible(true);
            loc.setTrainLength(4);

            assertTrue(layout.moveLocomotive(loc.getName(), "TL_Start", false), "precondition: the train is not placed");

            assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "TL_Long", "a reversible train of 4 was sent to a"
                + " terminus that leads only to a station of 2 - it could never leave it again (E2E-B1, BottomMainB)");

            assertNotNull(layout.explainDestinations(loc).get("TL_Turn"), "Why Not Moving? says the train of 4 may go"
                + " to the terminus that autonomy will not send it to");

            // THE CONTROL: a train of 2 fits the station beyond, so it can leave the terminus and is sent there first
            loc.setTrainLength(2);

            assertEquals(nameOfSecondPoint(layout.pickPath(loc)), "TL_Turn", "a reversible train of 2, which fits the"
                + " station beyond the terminus, was not sent to it - the rule refuses a terminus it could leave");

            assertNull(layout.explainDestinations(loc).get("TL_Turn"), "Why Not Moving? gives a reason against a"
                + " terminus the train of 2 is sent to");

            // AND THE YIELD PROBE AGREES - pickPath's mirror, which decides whether the other trains wait for this one.
            // With the other station switched off, the terminus is all there is for a train of 4, and a train autonomy
            // will send nowhere is not one to wait for.
            loc.setTrainLength(4);

            layout.getPoint("TL_Long").setActive(false);

            assertNull(layout.pickPath(loc), "precondition: autonomy found somewhere to send the train of 4");

            java.lang.reflect.Method probe = Layout.class.getDeclaredMethod("hasAutonomousDestination", Locomotive.class);

            probe.setAccessible(true);

            assertFalse((Boolean) probe.invoke(layout, loc), "the yield probe says autonomy has somewhere to send the"
                + " train of 4, whose only station is a terminus it could not leave - the other trains would stop and wait"
                + " for a train that is never sent");
        }
        finally
        {
            loc.setReversible(wasReversible);
            loc.setTrainLength(wasLength);
        }
    }

    /**
     * A clear route is found although a shorter one that throws a switch both ways reaches a square on it first.
     *
     * The route search must not mark a square visited the first time any route reaches it: a shorter route that sets a
     * switch one way and then the other (refused for conflicting commands) would hide a clear route reaching the same
     * square with the switch untouched, and the station beyond would be offered on some openings of the menu and not
     * others.  Here the short way RV_S - RV_X - RV_E sets the switch STRAIGHT then TURN, the long way RV_S - RV_Y -
     * RV_W - RV_X - RV_E only TURN.  Asked twelve times, because the answer depends on the order squares are taken in.
     *
     * MUTATION: key the search on the square alone again and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAClearRouteIsNotHiddenByAConflictingOne() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinAccessory sw = borrowedAccessory(0);

        String[] names = {"RV_S", "RV_X", "RV_Y", "RV_W", "RV_E"};
        boolean[] stations = {true, false, false, false, true};
        int[] sensors = {8970, 8971, 8972, 8973, 8974};

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback fb = model.newFeedback(sensors[i], null);

            model.setFeedbackState(fb.getName(), false);

            layout.createPoint(names[i], stations[i], fb.getName());
        }

        layout.createEdge("RV_S", "RV_X").addConfigCommand(sw.getName(), Accessory.accessorySetting.STRAIGHT);
        layout.createEdge("RV_X", "RV_E").addConfigCommand(sw.getName(), Accessory.accessorySetting.TURN);
        layout.createEdge("RV_S", "RV_Y");
        layout.createEdge("RV_Y", "RV_W");
        layout.createEdge("RV_W", "RV_X");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "RV_S", false), "precondition: the train is not placed");

        for (int ask = 1; ask <= 12; ask++)
        {
            List<Edge> found = null;

            for (List<Edge> path : layout.getPossiblePaths(loc, false))
            {
                if ("RV_E".equals(path.get(path.size() - 1).getEnd().getName())) found = path;
            }

            assertNotNull(found, "on ask " + ask + " RV_E was not offered: the only clear way there, by RV_Y and RV_W, was"
                + " hidden by the shorter way through RV_X, which throws the switch both ways (RSA59-B1)");

            assertEquals(found.size(), 4, "RV_E was offered by a way that is not the clear one: " + found);
        }

        assertNull(layout.explainDestinations(loc, true).get("RV_E"), "Why Not Moving? (by hand) gives a reason against"
            + " RV_E, which the clear way reaches: " + layout.explainDestinations(loc, true).get("RV_E"));

        layout.moveLocomotive(null, "RV_S", true);
    }

    /**
     * Two routes of the same length to a station are both offered, every time.
     *
     * The route search marks a square when it is taken OFF the queue, not when it is put on, so a square two routes
     * reach at the same distance is followed on from by both.  Marked when put on the queue, the second way to EQ_X is
     * dropped whenever the first is taken first, and the menu offers one route to EQ_E on some openings and two on
     * others.  Here EQ_S - EQ_A - EQ_X - EQ_E and EQ_S - EQ_B - EQ_X - EQ_E, no switches; asked twelve times, because
     * which way is taken first depends on the order `getNeighbors` shuffles into.
     *
     * MUTATION: mark a square when it is put on the queue and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTwoRoutesOfTheSameLengthAreBothOffered() throws Exception
    {
        Layout layout = new Layout(model);

        String[] names = {"EQ_S", "EQ_A", "EQ_B", "EQ_X", "EQ_E"};
        boolean[] stations = {true, false, false, false, true};
        int[] sensors = {8975, 8976, 8977, 8978, 8979};

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback fb = model.newFeedback(sensors[i], null);

            model.setFeedbackState(fb.getName(), false);

            layout.createPoint(names[i], stations[i], fb.getName());
        }

        layout.createEdge("EQ_S", "EQ_A");
        layout.createEdge("EQ_S", "EQ_B");
        layout.createEdge("EQ_A", "EQ_X");
        layout.createEdge("EQ_B", "EQ_X");
        layout.createEdge("EQ_X", "EQ_E");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "EQ_S", false), "precondition: the train is not placed");

        try
        {
            for (int ask = 1; ask <= 12; ask++)
            {
                java.util.Set<String> via = new java.util.TreeSet<>();

                for (List<Edge> path : layout.getPossiblePaths(loc, false))
                {
                    if ("EQ_E".equals(path.get(path.size() - 1).getEnd().getName())) via.add(path.get(0).getEnd().getName());
                }

                assertEquals(via, new java.util.TreeSet<>(java.util.Arrays.asList("EQ_A", "EQ_B")), "on ask " + ask
                    + " EQ_E was offered by way of " + via + " only - the other route of the same length was dropped");
            }
        }
        finally
        {
            layout.moveLocomotive(null, "EQ_S", true);
        }
    }

    /**
     * Where the only route to a station throws a switch both ways, Why Not Moving? still says so.
     *
     * The search keeps a route whose switch commands disagree out of the way of every route whose commands do not, but
     * still follows it and returns it on reaching the station, because that is how Why Not Moving? learns the reason.
     * Dropped instead, the station would be explained as if no route led there at all.  Here RC_S - RC_X sets the
     * switch STRAIGHT, RC_X - RC_Y sets it TURN, and RC_Y - RC_E is the only way on: the conflict arises a square
     * BEFORE the station, so the route has to be followed on from RC_Y in conflict (on the last edge the claim could
     * not fail).
     *
     * MUTATION: drop a route the moment its commands disagree and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAConflictThatIsAllThereIsIsStillTheReason() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinAccessory sw = borrowedAccessory(0);

        String[] names = {"RC_S", "RC_X", "RC_Y", "RC_E"};
        boolean[] stations = {true, false, false, true};
        int[] sensors = {8980, 8981, 8983, 8982};

        for (int i = 0; i < names.length; i++)
        {
            MarklinFeedback fb = model.newFeedback(sensors[i], null);

            model.setFeedbackState(fb.getName(), false);

            layout.createPoint(names[i], stations[i], fb.getName());
        }

        layout.createEdge("RC_S", "RC_X").addConfigCommand(sw.getName(), Accessory.accessorySetting.STRAIGHT);
        layout.createEdge("RC_X", "RC_Y").addConfigCommand(sw.getName(), Accessory.accessorySetting.TURN);
        layout.createEdge("RC_Y", "RC_E");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "RC_S", false), "precondition: the train is not placed");

        try
        {
            String why = layout.explainDestinations(loc, true).get("RC_E");

            assertNotNull(why, "RC_E, which only a route throwing " + sw.getName() + " both ways reaches, has no reason"
                + " against it");

            assertTrue(why.contains(sw.getName()), "RC_E is explained without the switch whose commands disagree: " + why);
        }
        finally
        {
            layout.moveLocomotive(null, "RC_S", true);
        }
    }
}
