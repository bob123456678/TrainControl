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
     * A route autonomy activates really is watching its sensor afterwards (Adam, 2026-09-10).
     *
     * **`testAutoRoute` above asserts the flag, and the flag is not the railway.** `enable()` sets a
     * boolean; `executeAutoRoute()` is what parks a thread on the s88. Remove the second call from
     * `applyAutonomyRouteActivations` and every assertion in `testAutoRoute` still passes while no
     * activated route ever fires again - the configuration would load, the log would say the route was
     * enabled, and the railway would do nothing.
     *
     * So this pulses the sensor and asks whether the turnout moved.
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
     * A route autonomy switches off really stops firing (Adam, 2026-09-10).
     *
     * The other half of the same gap: `testAutoRoute` asserts that every route outside the list reports
     * itself disabled, which `disable()` makes true by definition. What matters is that the parked
     * monitor honours it - it tests `enabled` after each feedback wait and returns - and that is what is
     * asserted here, with a control first so the test cannot pass because the route was never firing.
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
     * A route with no sensor cannot be activated, and is not reported as activated.
     *
     * `applyAutonomyRouteActivations` guards the enable on `hasS88()` and logs
     * `autolayoutErrorS88RequiredForAutoFire` instead - a route with no sensor has nothing to watch, so
     * enabling it would leave a route that claims to be automatic and can never fire.
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
     * An edge's arrival side survives Export JSON and Load JSON (S14-B3).
     *
     * `entrySide` is the side of the end point an edge arrives by. `AutonomyBuilder` writes it when it
     * traces the diagram, `Layout.fromJSON` reads it back, and the field's own javadoc says it *"travels
     * in the configuration"* - but `Edge.toJSON` never wrote it. So the program's own export was not a
     * configuration the program could reload: every edge came back with no arrival side.
     *
     * What that costs is arrival-side reasoning - which side of a station a train comes in on, and
     * therefore what counts as a reversal - on any layout that has been through Export JSON.
     *
     * Measured over the whole live configuration rather than one edge, because the defect is that NONE of
     * them carried it: a single-edge assertion would pass on a fixture that happened to have no sides.
     *
     * MUTATION: drop the `entrySide` line from `Edge.toJSON` and this fails, reporting 0 sides written of
     * however many the layout has.
     */
    @Test
    public void testAnEdgeKeepsItsArrivalSideThroughTheJSON() throws Exception
    {
        // THE BASELINE CONFIGURATION, not the sample layout.  entrySide is written by AutonomyBuilder
        // when it traces a track diagram, so the legacy sample has none and a test against it would
        // pass with nothing to preserve - which is what the floor below is for.
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
     * A railway can say which track non-atomic mode could release under a train (VD13-B2/B3, VD14-C6).
     *
     * This is the one question the atomic-routes gate asks, at all seven of its doors - the checkbox,
     * both load doors, and the five that dispatch a train.  It
     * is the hazard in the form the hazard takes: non-atomic mode releases an edge as soon as
     * `tailHasProvablyPassed` returns true, and that happens immediately when `pathIsUnmeasured` - when
     * NO edge on the path has a length.
     *
     * Nine claims about the count and one about its order, because the first version of this
     * counter got two of them wrong, the next two
     * versions were held by nothing at all, and the round after that found three of its own rules
     * dead in this very fixture (VD17-T4, T5, T10).
     *
     *  - **An unmeasured rail counts**, and a measured one does not.  (The control: a counter that
     *    answered "some" whatever the railway looked like would take the setting away from an operator
     *    who has measured everything, which is the failure Adam's standing rule is about.)
     *  - **A rail is counted ONCE, not once per direction.**  A rail is two `Edge` objects, so counting
     *    edges told him "2 pieces of track" about one piece.
     *  - **A rail into a point that is switched off is not counted**, because no path may reach it.
     *  - **Nor is a rail whose far end cannot reach a destination over unmeasured track**, because a
     *    path ends at a destination and so can never be unmeasured end to end.
     *  - **And the same rail DOES count once it can**, which is the half that says the narrowing is
     *    about paths rather than about rails: `UE_C -> UE_E` is counted the moment there is any
     *    unmeasured way on from `UE_E` to a destination, including the reverse direction of itself.
     *  - **The walk is a chain**, not one hop back from each destination: `UE_H -> UE_G -> UE_E ->
     *    UE_F` is unmeasured end to end, and the rail into `UE_G` counts as much as the one into the
     *    destination does.
     *  - **And it runs out of track rather than out of patience**: `UE_I` is one rail further back
     *    again, and a walk bounded at two levels stops before it (VD17-T5).
     *  - **A point that is switched off is not a way through**: the rail INTO `UE_J` is not counted,
     *    because no path may run through a point that is off - and until VD17-T4 that rule was dead
     *    in this fixture, since the only inactive point had no rail out of it.
     *  - **The answer is alphabetical**, which VD15-C2 fixed and nothing held until VD17-T10: the
     *    message names only the first three, and three arbitrary ones are no list to work down.
     *
     * **ONE-WAY EDGES ARE THE POINT OF THE FOURTH CLAIM, and it said so nowhere (VD16-T1).**
     * `createEdge` makes ONE directed edge, so `UE_C -> UE_E` with no reverse is a rail a train can
     * only be sent along, never back down - which is how facing is encoded on this railway and is why
     * nothing can be released onto it.  Adding the reverse makes `UE_E -> UE_C` a complete unmeasured
     * path to a destination, and the counter is RIGHT to count the rail then.  The fifth claim adds
     * that edge and asserts exactly that, so the property the fourth claim depends on is now written
     * down and checked rather than assumed.
     *
     * MUTATION: drop the de-duplication and the first claim goes red; replace the reachability walk
     * with the rule it replaced (`if (!edge.getEnd().isActive()) continue;`) and the fourth does -
     * measured 2026-09-22, and it was the only claim that moved; mark only the direct predecessors of
     * a destination instead of walking to exhaustion and the sixth does, at 3 against 4; bound the
     * walk at two levels and the seventh does, at 4 against 5; drop `!back.isActive()` from the walk
     * and the eighth does, at 7 against 6; delete `Collections.sort` and the last one does; return
     * nothing always and the first does.
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

        // AND AN UNMEASURED RAIL WITH MEASURED TRACK BETWEEN IT AND EVERY DESTINATION (VD15-T1).
        //
        // THIS IS THE CLAIM THE NARROWING HAS, and until it was written the narrowing had none: the
        // three above are all about rails whose far end IS an active destination, so replacing the
        // whole reachability walk with "every unmeasured edge" left them green.  A path ends at a
        // destination, and `tailHasProvablyPassed` hands an edge back only when NO edge on the path
        // has a length - so a rail that cannot be joined to a destination by unmeasured track alone
        // can never be part of an unmeasured path, and refusing the setting for it takes non-atomic
        // mode away from a railway where it is provably safe.
        layout.createPoint("UE_E", false, null);
        layout.createPoint("UE_F", true, "4");

        // ONE-WAY, AND THAT IS THE WHOLE POINT (VD16-T1).  `createEdge` makes one directed edge, so
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

        // AND THE WALK RUNS OUT OF TRACK, NOT OUT OF PATIENCE (VD17-T5).  The claim above is satisfied
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

        // AND A POINT THAT IS SWITCHED OFF IS NOT A WAY THROUGH (VD17-T4).  Until this claim the
        // walk's `isActive` test was dead in the fixture: the only inactive point had no rail out of
        // it, so the line could be deleted with every claim still green.  A path may not run through a
        // point that is off, so a rail whose only route to a destination passes through one can never
        // be part of an unmeasured path.
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

        // AND IN ALPHABETICAL ORDER (VD15-C2, held by nothing until VD17-T10).  `this.edges` is a
        // HashMap, the message names only the first three, and three arbitrary names that move between
        // runs of the same configuration are no list to work down.
        java.util.List<String> named = layout.unmeasuredTrackThatCouldBeReleased();

        java.util.List<String> sorted = new java.util.ArrayList<>(named);

        java.util.Collections.sort(sorted);

        assertEquals(named, sorted,
            "the rails come back in the graph's own order, which is a HashMap's and therefore none -"
            + " the refusal shows the first three of them, so on the same railway it would name a"
            + " different three from one run to the next.  " + named);
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
     * Where the trains have been survives a setup gesture (AUR-C3).
     *
     * **A session is longer than a `Layout`.**  `lastArrival` says in its own javadoc that it is kept
     * in memory because it describes this session's running - but `parseAuto` replaces the whole object,
     * and applying a diagram edit, placing a locomotive from the right-click menu or loading a
     * configuration all come through there.  `TrainControlUI` already carries placements, sides, roads
     * and pending turns across that rebuild; nothing carried this.
     *
     * So an operator running with Least Recently Visited, who stops to place a train and starts again,
     * got a rule that knew nothing: every station never visited, the choice random, and then worse than
     * random once the near ones began beating the far corner again.  That degraded state is the one the
     * preference's own javadoc was written to end.
     *
     * **And the carry has to be READABLE, not merely copied** (FXV-C5, second round).  The first version
     * moved `lastArrival` across as it stood, keyed by `recencyKeyOf` - the block, or failing that the unique
     * id.  Unique ids come from a global allocator that hands a new one to every Point built, so every entry
     * for a point with no block was dead weight the rebuilt railway could never match.  This test passed
     * anyway, because the map had been copied faithfully.  The carry is keyed by point NAME now, and this
     * assertion is about names, so a key the rule cannot use is a key this notices.
     *
     * **What this asserts, and what it does not.**  It asserts the state the rule reads - the map
     * `recencyOf` looks a destination up in - across a real `parseAuto`.  It does not drive `pickPath`
     * afterwards: a first draft built a three-point fixture, round-tripped it through JSON and asked
     * the rule to choose, and the parsed layout offered no route at all, so the claim rested on a
     * fixture rather than on the rebuild.  `testLeastRecentlyVisitedGoesWhereTrainsHaveNotBeen` above is
     * what says the map is read; this is what says it is still there to read.
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
     * Switching a protecting signal by hand asks only in the direction that removes protection
     * (MT-256).
     *
     * Adam, 2026-09-05: **"Could not run this.  create a test case for this - it requires activating
     * an autonomy path and creating a route that touches its signal."**
     *
     * The manual steps needed a running railway, a train standing at a protected platform, and a route
     * built to fight it. None of that is needed to ask the question the fix is about: `clearsProtection`
     * is the one rule all three doors consult - the diagram tile, the switch keyboard and the route -
     * and the direction is supplied by the caller because each knows it differently.
     *
     * **The aspect is half the rule, and leaving it out was `WK3-B1`.** Turning protection ON is doing
     * what the protection mechanism would do anyway, and refusing that was over-strictness the route
     * door had already had removed. So:
     *
     * - green, with a train standing there: asked;
     * - red, with a train standing there: never asked;
     * - either way with the platform empty: never asked.
     *
     * Those are steps 2, 3 and 4 of the manual test. Step 5 - a route setting the same signal green is
     * still refused - is `heldReason`, which asks the same method, and
     * `testARouteDoesNotThrowSwitchesUnderATrain` covers that door.
     *
     * MUTATION: drop `commandingGreen` from `clearsProtection` and the second assertion fails; drop
     * the occupancy half and the third does.
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
     * A square switched out of service refuses trains at every door but its own (Adam, 2026-09-06).
     *
     * **"In manual mode, inactive endpoints and intermediates should be refused as well.  Just not
     * inactive start points.  Inactive really means nothing can pass."**
     *
     * The intermediate rule was already unfenced and says why - a train crossing a point the operator
     * switched off is the one place nobody chose that point at all. The DESTINATION rule was fenced
     * behind `isAutoRunning`, so a hand-driven send could finish on a closed square while a Return
     * Home run could not, and the cross on that square meant two different things depending on who
     * asked.
     *
     * **The start stays exempt, and that is the whole exception:** a train standing on a square that
     * has been switched off is driven out by hand, which is what closing a square around a train is
     * for.
     *
     * **This test exists because the change had none.** Re-fencing the rule behind `isAutoRunning`
     * broke nothing across four classes and 242 tests - the walks were covered and the runtime rule,
     * which is the one that actually stops a train, was not.
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

        // AND THE DOOR, not only the rule.
        //
        // `isPathClear` is what refuses; `getPossiblePaths` is what the right-click menu and the
        // Locomotive commands tab OFFER.  It filters through `isPathClear`, so this should follow -
        // but "should follow" is how a rule comes to be tested while its call site is not, which has
        // produced two defects in this tree today alone.
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
     * One signal protecting TWO platforms stays red while either of them is claimed (RG5-C1).
     *
     * **Adam's repair depends on exactly this.** His 2.8.1 file reds Signals 63 and 64 on the edge
     * `TopMainR0 -> TopMainPost`, and the reason is not the junction:
     *
     * > "The route set them red to guarantee that the locked stations (TopMainR1 and TopMainR2)
     * > cannot accidentally have a train cross them.  In this case, we would want to set a guard for
     * > topmainR0 of each of those signals, too."
     *
     * So both signals get a second owner. 63 protects R1 **and** R0; 64 protects R2 **and** R0. The
     * model allows it, the pairing UI allows it - its only guard is "already on THIS station's list"
     * - and `refreshOneSignal` says it handles it. Nothing checked.
     *
     * **The failure this exists to catch is the release, not the claim.** A signal shows one aspect,
     * so two owners have to agree: the moment one platform empties, the obvious code sets the signal
     * green - and if the other is still occupied, the signal now lies about an occupied platform.
     * That is worse than never having paired it, because it reads as protection.
     *
     * The test that already stands next door is the other direction - two signals on one platform -
     * and it cannot see this.
     *
     * MUTATION: make `refreshOneSignal` ask only the point it was called for, rather than every point
     * that lists the signal, and the third assertion fails.
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

        assertEquals(loc.getSpeed(), 0,
            "the locomotive that failed is still under power.  Adam’s ruling is \"force a graceful "
            + "stop, alert the user, then unlock\", and stopLocomotives() only sets running = false - "
            + "the only thing that stops THIS train is setSpeed(0), and it used to run after the "
            + "release.  unlockPath is synchronized on the layout monitor, which "
            + "configureAndLockPath holds for seconds on a long path, so the train could keep "
            + "running on track the model had just declared free (VD10-B1)");
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

        // (A third assertion here read back the setter two lines above it, and would pass for as long
        // as a setter sets - removed rather than left as evidence it is not, VD11-C6.)
    }

    /**
     * A BERTH stays in the base list, and an excluded square leaves the menu (Adam, 2026-09-04/12).
     *
     * Two rulings on one gesture, given after seeing `BottomMainB` and `BottomMainC` move out of the
     * base list for `2-8-4 3505 SP`: *"We should still show the same number of base options (unless
     * unselectable)"* and *"if it excludes the loc, don't even include it in the list."*
     *
     * The first undid `VD11-C2`, which had the menu ask `isChoosableByAutonomy(end, locomotive)` on
     * the strength of a comment I had written promising that a terminus a non-reversible train could
     * not get out of belonged in More Destinations. Measured on `test/operator_layout` before changing
     * anything - the fixture emits each of those squares twice, once as a through arrival and once as
     * a reverse arrival that is a terminus, and it was the terminus copies that moved. Neither square
     * excludes any locomotive, so the exclusion clause was never involved.
     *
     * **NARROWED by his own ruling of 2026-09-12** (OB-205, MT-367), and this class read the older one
     * as wider than he ever made it: *"75 407 DB can go from Tunnel to BottomMainC manually, even
     * though it is not reversible and THIS IS NOT A PARKING BERTH (excluded from autonomy). it should
     * not be allowed to be chosen."* The 2026-09-01 ruling that moved the terminus rule out of
     * `isPathClear` said *"the operator asking for that BERTH by hand is no longer refused"* - the word
     * doing the work was berth, and the rule written from it did not carry it.
     *
     * So the first claim below is now about a berth: a terminus autonomy will not choose is still
     * offered to a train that cannot reverse. The claim beside it is the other half - an ORDINARY
     * terminus, one autonomy may choose, is not - and the two together are what keep either from being
     * satisfied by a rule that has simply stopped asking.
     *
     * MUTATION, stated exactly: dropping `isAutoDestination()` from `isOfferableToOperator`'s terminus
     * clause fails the first assertion; dropping the clause entirely fails the second; dropping the
     * exclusion clause fails the third; dropping `isActive` fails the fourth.
     *
     * **What this does NOT cover is the menu's choice of predicate** - the menu needs a window, and it
     * is package-private besides. That half is `MT-266`. What the rule having a name on `Layout` buys
     * is that the menu now holds one call rather than a copy of the rule.
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

            // AND THE HALF HE NARROWED IT TO, 2026-09-12: an ordinary terminus, one autonomy may
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
     * A failure during the LOCK PHASE leaves the train where it is standing, and leaves other trains'
     * claims alone (ACC-A1).
     *
     * **The release blocker.  This is the second fixture; the first one passed without reaching the
     * code it was about** (`OV2-B1`).  It put a `null` into an edge's `lockEdges` on the theory that
     * `Edge.setOccupied` would throw as it cascaded - and `isPathClear` walks the same list four
     * statements earlier, so the `NullPointerException` came out THERE, before `takingPath` was
     * claimed and before anything was locked.  A strictly weaker window, asserted with a javadoc that
     * told the next reader otherwise.
     *
     * The injection that does reach it: a config command mapped to a **null state on an accessory that
     * exists**.  `isPathClear`'s preview takes `configureEdge`'s `preConfigure != null` arm and
     * records the command without formatting it, so validation passes; the lock loop then calls
     * `configureEdge(e, null)`, which reaches `state.toString().toLowerCase()` - inside the try, after
     * `edgesLocked++`, after `setOccupied()` and after `reserve()`.  `handleMisconfiguredPath` then
     * genuinely runs.
     *
     * **The precondition is where the throw came from**, because that is the thing the first fixture
     * got wrong and no assertion about the outcome could have caught: a stack that never enters
     * `configureEdge` is not this failure.
     *
     * What the handler used to do over the top of that recovery: `unlockPath`'s `i == 0` clause
     * cleared the start reservation `handleMisconfiguredPath` had just written, so the train stood on
     * a square the model believed empty; and every never-taken edge was released, each cascading to
     * lock edges whose occupancy is a COUNT shared with other running dispatches.
     *
     * **Only the first of those two is asserted here, and the second is not testable at this door.**
     * Making a spurious release observable means giving the untaken edge a claim to lose - and any
     * claim on it, or on its lock edges, makes `isPathClear` refuse the path, so the lock loop is
     * never entered and nothing throws.  The fixture and the observation exclude each other.  Said
     * out loud rather than left as an unasserted sentence in a javadoc, which is how the first
     * version of this test came to claim more than it checked.
     *
     * MUTATION: removing the `hadItsPath` gate fails the second assertion.  Reading the flag after the
     * `activeLocomotives.remove` rather than before makes it always false, which is caught by
     * `testAFailedPathStopsTheRunAndGivesTheTrackBack` rather than here.
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

        // WHERE IT THREW, which is the whole of what the first fixture got wrong.
        java.io.StringWriter trace = new java.io.StringWriter();

        caught.printStackTrace(new java.io.PrintWriter(trace));

        // `configureEdge` ALONE DOES NOT SAY WHICH (FR3-C1).
        //
        // `configureAndLockPath` calls `configureEdge` twice over: once through `isPathClear`, which
        // previews the configuration, and once directly in the lock loop.  A stack carrying
        // `configureEdge` is therefore true of both the strong window and the weak one - the very
        // distinction the previous fixture got wrong.  `isPathClear` is what tells them apart: it is
        // in the preview stack and not in the lock loop’s.
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
}
