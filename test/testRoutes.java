import java.util.LinkedList;
import java.util.Collections;
import org.traincontrol.base.RouteCommand;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_ACCESSORY;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FUNCTION;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LOCOMOTIVE;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_STOP;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_AUTONOMY_LIGHTS_ON;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FUNCTIONS_OFF;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LIGHTS_ON;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LOCOMOTIVE_DIRECTION;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_ROUTE;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.json.JSONArray;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.Route;
import org.traincontrol.marklin.MarklinAccessory;
import static org.traincontrol.base.Accessory.accessoryDecoderType.DCC;
import static org.traincontrol.base.Accessory.accessoryDecoderType.MM2;
import org.traincontrol.base.Locomotive;
import static org.traincontrol.base.Locomotive.locDirection.DIR_BACKWARD;
import static org.traincontrol.base.Locomotive.locDirection.DIR_FORWARD;

/**
 *
 * @author adam
 */
public class testRoutes
{   
    public static MarklinControlStation model;
        
    private static final int MAX_NUM_COMMANDS = 10;
    private static final Random RANDOM = new Random();
    
    public testRoutes()
    {
    }

    public static MarklinRoute generateRandomRoute()
    {
        // Generate random values for parameters
        Random random = new Random();
        String name = "Route: " + random.nextInt(1000);
        int id = random.nextInt(1000);
        int s88 = random.nextInt(10000);
        boolean enabled = random.nextBoolean();
        
        MarklinAccessory.accessoryDecoderType[] protocols = new MarklinAccessory.accessoryDecoderType[]{MM2, DCC};

        // Randomly select s88Triggers value
        MarklinRoute.s88Triggers triggerType = (random.nextBoolean()) ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR;

        List<RouteCommand> conditions = new ArrayList<>();
        
        for (int i = 0; i < random.nextInt(10); i++)
        {
            switch (random.nextInt(3))
            {
                case 0:
                    conditions.add(RouteCommand.RouteCommandFeedback(random.nextInt(100), random.nextBoolean()));
                    break;
                case 1:
                    conditions.add(RouteCommand.RouteCommandAccessory(random.nextInt(100), protocols[random.nextInt(2)], random.nextInt(2) == 1));
                    break;
                case 2:
                    conditions.add(RouteCommand.RouteCommandAutoLocomotive(model.getLocList().get(random.nextInt(model.getLocList().size())), random.nextInt(4000)));
                    break;
            }
        }
           
        List<RouteCommand> routeCommands = new ArrayList<>();

        // Populate the list with random RouteCommand objects
        for (int i = 0; i < random.nextInt(40); i++)
        {    
            RouteCommand.commandType[] types = new RouteCommand.commandType[]{TYPE_ACCESSORY, TYPE_STOP, TYPE_FUNCTION, TYPE_LOCOMOTIVE, TYPE_LOCOMOTIVE_DIRECTION,
                 TYPE_AUTONOMY_LIGHTS_ON, TYPE_FUNCTIONS_OFF, TYPE_LIGHTS_ON, TYPE_ROUTE
            };
            RouteCommand.commandType randomType = types[random.nextInt(8)];
            
            MarklinAccessory.accessoryDecoderType randomProtocol = protocols[random.nextInt(2)];
            
            switch (randomType)
            {
                case TYPE_AUTONOMY_LIGHTS_ON:
                    routeCommands.add(RouteCommand.RouteCommandAutonomyLightsOn());
                    break;
                    
                case TYPE_FUNCTIONS_OFF:
                    routeCommands.add(RouteCommand.RouteCommandFunctionsOff());
                    break;
                    
                case TYPE_LIGHTS_ON:
                    routeCommands.add(RouteCommand.RouteCommandLightsOn());
                    break;
                    
                case TYPE_ROUTE:
                    String selectedRoute = model.getRouteList().get(random.nextInt(model.getRouteList().size()));
                    String routeName = model.getRoute(selectedRoute).getName();
                    routeCommands.add(RouteCommand.RouteCommandRoute(routeName));
                    break;
                
                case TYPE_ACCESSORY:
                    int address = random.nextInt(100) + 1;
                    boolean setting = random.nextBoolean();
                    RouteCommand accessoryCommand = RouteCommand.RouteCommandAccessory(address, randomProtocol, setting);
                    
                    if (random.nextBoolean())
                    {
                        accessoryCommand.setDelay(random.nextInt(1000));
                    }
                    
                    routeCommands.add(accessoryCommand);                    
                    break;
                
                case TYPE_STOP:
                    RouteCommand stopCommand = RouteCommand.RouteCommandStop();
                    routeCommands.add(stopCommand);
                    break;
                    
                case TYPE_LOCOMOTIVE:
                    String locName = model.getLocList().get(random.nextInt(model.getLocList().size()));
                    int speed = random.nextInt(101);
                    
                    RouteCommand locCommand = RouteCommand.RouteCommandLocomotiveSpeed(locName, speed);
                    
                    if (random.nextBoolean())
                    {
                        locCommand.setDelay(random.nextInt(1000));
                    }
                    
                    routeCommands.add(locCommand);
                    break;
                    
                case TYPE_LOCOMOTIVE_DIRECTION:
                    String locNameForDirection = model.getLocList().get(random.nextInt(model.getLocList().size()));
                    int direction = random.nextInt(2);
                    
                    RouteCommand locCommandDirection = RouteCommand.RouteCommandLocomotiveDirection(locNameForDirection, direction == 0 ? DIR_FORWARD : DIR_BACKWARD);
                    
                    if (random.nextBoolean())
                    {
                        locCommandDirection.setDelay(random.nextInt(1000));
                    }
                    
                    routeCommands.add(locCommandDirection);
                    break;
                    
                case TYPE_FUNCTION:
                    String flocName = model.getLocList().get(random.nextInt(model.getLocList().size()));
                    boolean state = random.nextBoolean();
                    int function = random.nextInt(33);
                    
                    RouteCommand funcCommand = RouteCommand.RouteCommandFunction(flocName, function, state);
                    
                    if (random.nextBoolean())
                    {
                        funcCommand.setDelay(random.nextInt(1000));
                    }
                    
                    routeCommands.add(funcCommand);
                    break;
            }
        }
        
        for (int i = 0; i < random.nextInt(20); i++)
        {
            int address = random.nextInt(100);
            boolean setting = random.nextBoolean();
            
            MarklinAccessory.accessoryDecoderType randomProtocol = protocols[random.nextInt(2)];
            
            RouteCommand accessoryCommand = RouteCommand.RouteCommandAccessory(address, randomProtocol, setting);
            conditions.add(accessoryCommand);
        }
        
        MarklinRoute route = new MarklinRoute(model, name, id, routeCommands, s88, triggerType, enabled, NodeExpression.fromList(conditions));
        
        return route;
    }
    
    @Test
    public void testLocomotiveRenameInRouteCommand()
    {
        // Step 1: Get two locomotive names from the model
        List<String> locList = model.getLocList();
        assertTrue(locList.size() >= 2);

        String originalName = locList.get(0);
        String testName = originalName + "_test";
        String untouchedName = locList.get(1); // this one won't be renamed

        // Step 2: Create multiple locomotive commands
        int speed = 42;
        int functionNumber = 3;
        boolean functionSetting = true;
        int s88Address = 1234;
        Locomotive.locDirection direction = Locomotive.locDirection.DIR_FORWARD;

        int untouchedSpeed = 88;

        List<RouteCommand> routeCommands = new ArrayList<>();
        routeCommands.add(RouteCommand.RouteCommandLocomotiveSpeed(originalName, speed));
        routeCommands.add(RouteCommand.RouteCommandFunction(originalName, functionNumber, functionSetting));
        routeCommands.add(RouteCommand.RouteCommandAutoLocomotive(originalName, s88Address));
        routeCommands.add(RouteCommand.RouteCommandLocomotiveDirection(originalName, direction));

        // Add a command for the untouched locomotive
        routeCommands.add(RouteCommand.RouteCommandLocomotiveSpeed(untouchedName, untouchedSpeed));

        // Step 3: Create a route with these commands
        MarklinRoute route = new MarklinRoute(
            model,
            "TestRoute999",
            999,
            routeCommands,
            0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            true,
            NodeExpression.fromList(new ArrayList<>())
        );

        model.newRoute(route);

        // Step 4: Rename the locomotive
        model.renameLoc(originalName, testName);

        // Step 5: Verify renamed commands reflect new name and retain values
        List<RouteCommand> renamedCommands = route.getRoute();

        assertEquals(testName, renamedCommands.get(0).getName());
        assertEquals(speed, renamedCommands.get(0).getSpeed());

        assertEquals(testName, renamedCommands.get(1).getName());
        assertEquals(functionNumber, renamedCommands.get(1).getFunction());
        assertEquals(functionSetting, renamedCommands.get(1).getSetting());

        assertEquals(testName, renamedCommands.get(2).getName());
        assertEquals(s88Address, renamedCommands.get(2).getAddress());

        assertEquals(testName, renamedCommands.get(3).getName());
        assertEquals(direction, renamedCommands.get(3).getDirection());

        // Verify untouched locomotive command remains unchanged
        assertEquals(untouchedName, renamedCommands.get(4).getName());
        assertEquals(untouchedSpeed, renamedCommands.get(4).getSpeed());

        // Step 6: Rename back to original name
        model.renameLoc(testName, originalName);

        // Step 7: Verify restored commands reflect original name and retain values
        List<RouteCommand> restoredCommands = route.getRoute();

        assertEquals(originalName, restoredCommands.get(0).getName());
        assertEquals(speed, restoredCommands.get(0).getSpeed());

        assertEquals(originalName, restoredCommands.get(1).getName());
        assertEquals(functionNumber, restoredCommands.get(1).getFunction());
        assertEquals(functionSetting, restoredCommands.get(1).getSetting());

        assertEquals(originalName, restoredCommands.get(2).getName());
        assertEquals(s88Address, restoredCommands.get(2).getAddress());

        assertEquals(originalName, restoredCommands.get(3).getName());
        assertEquals(direction, restoredCommands.get(3).getDirection());

        // Verify untouched locomotive command still remains unchanged
        assertEquals(untouchedName, restoredCommands.get(4).getName());
        assertEquals(untouchedSpeed, restoredCommands.get(4).getSpeed());

        model.deleteRoute(route.getName());
    }

    @Test
    public void testNodeExpressionEvaluation() throws Exception 
    {
        // Initialize model and set states
        model.setFeedbackState("10", true);
        model.setFeedbackState("6", false);
        model.setFeedbackState("4", true);
        MarklinAccessory accessory1 = model.getAccessoryByAddress(60, MarklinAccessory.accessoryDecoderType.MM2);
        accessory1.setSwitched(true);
        MarklinAccessory accessory2 = model.getAccessoryByAddress(55, MarklinAccessory.accessoryDecoderType.MM2);
        accessory2.setSwitched(false);
        MarklinAccessory accessory3 = model.getAccessoryByAddress(50, MarklinAccessory.accessoryDecoderType.MM2);
        accessory3.setSwitched(true);
        MarklinAccessory accessory4 = model.getAccessoryByAddress(65, MarklinAccessory.accessoryDecoderType.MM2);
        accessory4.setSwitched(true);

        // Generate command strings
        String command1 = RouteCommand.RouteCommandAccessory(60, MM2, true).toLine(model.getAccessoryByAddress(60, MarklinAccessory.accessoryDecoderType.MM2));
        String command2 = RouteCommand.RouteCommandFeedback(10, true).toLine(null);
        String command3 = RouteCommand.RouteCommandFeedback(6, false).toLine(null);
        String command4 = RouteCommand.RouteCommandAccessory(55, MM2, false).toLine(model.getAccessoryByAddress(55, MarklinAccessory.accessoryDecoderType.MM2));
        String command5 = RouteCommand.RouteCommandFeedback(4, true).toLine(null);
        String command6 = RouteCommand.RouteCommandAccessory(50, MM2, true).toLine(model.getAccessoryByAddress(50, MarklinAccessory.accessoryDecoderType.MM2));
        String command7 = RouteCommand.RouteCommandAccessory(65, MM2, true).toLine(model.getAccessoryByAddress(65, MarklinAccessory.accessoryDecoderType.MM2));
        String commandOpposite1 = RouteCommand.RouteCommandAccessory(60, MM2, false).toLine(model.getAccessoryByAddress(60, MarklinAccessory.accessoryDecoderType.MM2));
        String commandOpposite2 = RouteCommand.RouteCommandFeedback(10, false).toLine(null); // False feedback
        String commandOpposite3 = RouteCommand.RouteCommandFeedback(6, true).toLine(null); // False feedback
        String commandOpposite4 = RouteCommand.RouteCommandAccessory(55, MM2, true).toLine(model.getAccessoryByAddress(55, MarklinAccessory.accessoryDecoderType.MM2));
        String commandOpposite6 = RouteCommand.RouteCommandAccessory(50, MM2, false).toLine(model.getAccessoryByAddress(50, MarklinAccessory.accessoryDecoderType.MM2));

        // Test 1: (Switch 60,turn Feedback 10,1) OR Feedback 10,0
        String expr1 = "(" + command1 + "\nAND\n" + command2 + ")\nOR\n" + commandOpposite2;
        NodeExpression node1 = NodeExpression.fromTextRepresentation(expr1, model);
        assertTrue(node1.evaluate(model));

        // Test 2: (Switch 60,turn Feedback 10,1)
        String expr2 = "(" + command1 + "\nAND\n" + command2 + ")";
        NodeExpression node2 = NodeExpression.fromTextRepresentation(expr2, model);
        assertTrue(node2.evaluate(model));

        // Test 3: Switch 60,turn Feedback 6,0
        String expr3 = command1 + "\nAND\n" + command3;
        NodeExpression node3 = NodeExpression.fromTextRepresentation(expr3, model);
        assertTrue(node3.evaluate(model));

        // Test 4: Switch 60,turn OR (Feedback 6,0 Switch 55,straight)
        String expr4 = command1 + "\nOR\n(" + command3 + "\nAND\n" + command4 + ")";
        NodeExpression node4 = NodeExpression.fromTextRepresentation(expr4, model);
        assertTrue(node4.evaluate(model));

        // Test 5: (Switch 60,turn Feedback 10,1 Feedback 6,0 Switch 55,straight) should be true
        String expr5 = "(" + command1 + " AND " + command2 + " AND " + command3 + " AND " + command4 + ")";
        NodeExpression node5 = NodeExpression.fromTextRepresentation(expr5, model);
        assertTrue(node5.evaluate(model));

        // Test 6: (Switch 60,turn) OR (Feedback 10,1 Switch 55,straight) should be true
        String expr6 = "(" + command1 + ")\nOR\n(" + command2 + "\nAND\n" + command4 + ")";
        NodeExpression node6 = NodeExpression.fromTextRepresentation(expr6, model);
        assertTrue(node6.evaluate(model));

        // Test 7: (Feedback 10,1 Switch 55,straight) OR (Feedback 6,0) should be true
        String expr7 = "(" + command2 + "\nAND\n" + command4 + ")\nOR\n(" + commandOpposite3 + ")";
        NodeExpression node7 = NodeExpression.fromTextRepresentation(expr7, model);
        assertTrue(node7.evaluate(model));

        // Test 7a: (Feedback 10,1 Switch 55,turn) OR (Feedback 6,0) should be false
        String expr7a = "(" + command2 + "\nAND\n" + commandOpposite4 + ")\nOR\n(" + commandOpposite3 + ")";
        NodeExpression node7a = NodeExpression.fromTextRepresentation(expr7a, model);
        assertFalse(node7a.evaluate(model));

        // Test 8: Feedback 10,1 (Feedback 6,0 OR Switch 55,straight) should be true
        String expr8 = command2 + "\nAND \n(" + command3 + "\nOR\n" + command4 + ")";
        NodeExpression node8 = NodeExpression.fromTextRepresentation(expr8, model);
        assertTrue(node8.evaluate(model));

        // Test 8a: Feedback 10,0 (Feedback 6,1 OR Switch 55,turn) should be false
        String expr8a = commandOpposite2 + " \n AND \n(" + commandOpposite3 + "\nOR\n" + commandOpposite4 + ")";
        NodeExpression node8a = NodeExpression.fromTextRepresentation(expr8a, model);
        assertFalse(node8a.evaluate(model));

        // Test 9: Switch 60,turn Switch 60,straight should be false
        String expr9 = command1 + " AND " + commandOpposite1;
        NodeExpression node9 = NodeExpression.fromTextRepresentation(expr9, model);
        assertFalse(node9.evaluate(model));

        // Test 10: (Feedback 10,1 Feedback 4,1) OR Switch 55,straight should be true
        String expr10 = "(" + command2 + " AND " + command5 + ")\nOR\n" + command4;
        NodeExpression node10 = NodeExpression.fromTextRepresentation(expr10, model);
        assertTrue(node10.evaluate(model));

        // Test 11: (Feedback 10,1 Feedback 4,1) (Switch 60,straight) should be false
        String expr11 = "(" + command2 + " AND \n" + command5 + ") AND \n" + commandOpposite1;
        NodeExpression node11 = NodeExpression.fromTextRepresentation(expr11, model);
        assertFalse(node11.evaluate(model));

        // Test 12: (Switch 50,turn) (Switch 55,straight) should be true
        String expr12 = "(" + command6 + ") AND \n" + command4;
        NodeExpression node12 = NodeExpression.fromTextRepresentation(expr12, model);
        assertTrue(node12.evaluate(model));

        // Test 13: (Switch 50,turn) (Switch 50,straight) should be false
        String expr13 = "(" + command6 + ") AND \n" + commandOpposite6;
        NodeExpression node13 = NodeExpression.fromTextRepresentation(expr13, model);
        assertFalse(node13.evaluate(model));

        // Test 14: Feedback 10,1 AND (Switch 50,turn OR Switch 55,straight) should be true
        String expr14 = command2 + " AND \n(" + command6 + "\nOR\n" + command4 + ")";
        NodeExpression node14 = NodeExpression.fromTextRepresentation(expr14, model);
        assertTrue(node14.evaluate(model));

        // Test 15: Feedback 10,1 AND (Switch 50,straight OR Switch 55,turn) should be false
        String expr15 = command2 + " AND \n(" + commandOpposite6 + "\nOR\n" + commandOpposite4 + ")";
        NodeExpression node15 = NodeExpression.fromTextRepresentation(expr15, model);
        assertFalse(node15.evaluate(model));

        // Test 16: (Switch 50,turn Feedback 4,1) AND (Switch 60,turn Feedback 10,1) should be true
        String expr16 = "(" + command6 + " AND \n" + command5 + ") AND \n(" + command1 + " AND \n" + command2 + ")";
        NodeExpression node16 = NodeExpression.fromTextRepresentation(expr16, model);
        assertTrue(node16.evaluate(model));

        // Test 17: (Switch 50,turn Feedback 4,0) AND (Switch 60,turn Feedback 10,1) should be false
        String expr17 = "(" + command6 + " AND \n" + commandOpposite3 + ") AND \n(" + command1 + " AND \n" + command2 + ")";
        NodeExpression node17 = NodeExpression.fromTextRepresentation(expr17, model);
        assertFalse(node17.evaluate(model));

        // Test 18: (Switch 50,turn) AND (Switch 65,turn) should be true
        String expr18 = "(" + command6 + ") AND \n" + command7;
        NodeExpression node18 = NodeExpression.fromTextRepresentation(expr18, model);
        assertTrue(node18.evaluate(model));
    }

    /**
     * A minimal autonomy configuration with a single station, so that autoloc conditions have a graph
     * to resolve against.
     */
    private static String stationOnlyAutonomy(String pointName, int s88)
    {
        return "{"
            + "\"points\": [ {\"name\": \"" + pointName + "\", \"station\": true, \"s88\": " + s88 + "} ],"
            + "\"edges\": [],"
            + "\"minDelay\": 0,"
            + "\"maxDelay\": 0,"
            + "\"defaultLocSpeed\": 30"
            + "}";
    }

    /**
     * Drives a sensor clear then occupied, holding each state well past
     * Locomotive.FEEDBACK_DURATION_THRESHOLD, then allows time for the route body to run.
     */
    private static void pulseFeedback(String feedbackName) throws InterruptedException
    {
        model.setFeedbackState(feedbackName, false);
        Thread.sleep(400);
        model.setFeedbackState(feedbackName, true);
        Thread.sleep(1500);
    }

    /**
     * An autoloc condition names a locomotive and the sensor it is expected to be at.  A locomotive
     * that is not on the autonomy graph at all simply does not satisfy that condition - it must
     * evaluate to false rather than failing.
     */
    @Test
    public void testAutoLocomotiveConditionWithUnplacedLocomotive() throws Exception
    {
        model.parseAuto(stationOnlyAutonomy("A4R_Unplaced", 8801));

        String locName = model.getLocList().get(0);

        assertNull(model.getAutoLayout().getLocomotiveLocation(model.getLocByName(locName)),
            "precondition: the locomotive is not on the graph");

        assertFalse(Route.evaluate(RouteCommand.RouteCommandAutoLocomotive(locName, 8801), model),
            "an unplaced locomotive cannot be at the named sensor");
    }

    /**
     * Control: placed at the named sensor, the same condition is satisfied.
     */
    @Test
    public void testAutoLocomotiveConditionWithPlacedLocomotive() throws Exception
    {
        model.parseAuto(stationOnlyAutonomy("A4R_Placed", 8802));

        String locName = model.getLocList().get(0);

        assertTrue(model.getAutoLayout().moveLocomotive(locName, "A4R_Placed", false),
            "precondition: the locomotive is placed at the station");

        assertTrue(Route.evaluate(RouteCommand.RouteCommandAutoLocomotive(locName, 8802), model),
            "a locomotive standing at the named sensor satisfies the condition");
    }

    /**
     * Control: placed somewhere else, the condition is not satisfied - and still does not fail.
     */
    @Test
    public void testAutoLocomotiveConditionAtADifferentSensor() throws Exception
    {
        model.parseAuto(stationOnlyAutonomy("A4R_Elsewhere", 8803));

        String locName = model.getLocList().get(0);

        assertTrue(model.getAutoLayout().moveLocomotive(locName, "A4R_Elsewhere", false));

        assertFalse(Route.evaluate(RouteCommand.RouteCommandAutoLocomotive(locName, 8804), model),
            "the locomotive is at 8803, not 8804");
    }

    /**
     * The operational consequence, end to end.
     *
     * executeAutoRoute evaluates a route's conditions inside a bare Thread with no exception handler.
     * If evaluating a condition fails, that thread dies and the route stops watching its sensor for the
     * rest of the session - while still reporting itself as enabled.  A condition that is merely not
     * satisfied must instead be retried on the next trigger.
     */
    @Test
    public void testUnsatisfiedAutoLocConditionDoesNotKillTheRouteMonitor() throws Exception
    {
        model.parseAuto(stationOnlyAutonomy("A4R_Monitor", 8811));

        model.newFeedback(8812, null);
        model.setFeedbackState("8812", false);

        MarklinAccessory observable = model.newSwitch(286, MM2, false);
        assertFalse(observable.isSwitched(), "precondition: the observed switch starts straight");

        String locName = model.getLocList().get(0);

        assertNull(model.getAutoLayout().getLocomotiveLocation(model.getLocByName(locName)),
            "precondition: the locomotive is not on the graph, so the condition is unsatisfied");

        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandAccessory(286, MM2, true));

        List<RouteCommand> conditions = new ArrayList<>();
        conditions.add(RouteCommand.RouteCommandAutoLocomotive(locName, 8811));

        MarklinRoute route = new MarklinRoute(model, "A4 monitor survival route", 9801, commands, 8812,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, NodeExpression.fromList(conditions));

        try
        {
            // Let the monitor thread reach its blocking wait
            Thread.sleep(600);

            // First trigger: the condition is not satisfied, so the route must not fire
            pulseFeedback("8812");

            assertFalse(observable.isSwitched(),
                "the route must not fire while its condition is unsatisfied");

            // Now satisfy the condition
            assertTrue(model.getAutoLayout().moveLocomotive(locName, "A4R_Monitor", false));

            assertTrue(Route.evaluate(RouteCommand.RouteCommandAutoLocomotive(locName, 8811), model),
                "the condition is now satisfiable");

            assertTrue(route.isEnabled(), "the route still reports itself as enabled");

            // Second trigger: a route that is still watching its sensor must now fire
            pulseFeedback("8812");

            assertTrue(observable.isSwitched(),
                "the route must still be monitoring its sensor after an unsatisfied condition");
        }
        finally
        {
            route.disable();
        }
    }

    /**
     * A route may only ever have one monitor thread.
     *
     * disable() just clears a flag; the thread stays parked in its feedback wait until the sensor next
     * fires.  Re-enabling before that happens used to start a second monitor, and the route then fired
     * once per monitor on every trigger.  applyAutonomyRouteActivations produces exactly this sequence
     * when one autonomy configuration omits the route and the next one includes it.
     */
    @Test
    public void testDisableAndReEnableDoesNotStartASecondMonitor() throws Exception
    {
        model.newFeedback(8821, null);
        model.setFeedbackState("8821", false);

        List<RouteCommand> commands = new ArrayList<>();
        commands.add(RouteCommand.RouteCommandAccessory(287, MM2, true));

        MarklinRoute route = new MarklinRoute(model, "A6 single monitor route", 9802, commands, 8821,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null);

        try
        {
            // Let the monitor started by the constructor reach its blocking wait
            Thread.sleep(600);

            route.disable();
            route.enable();

            assertFalse(route.executeAutoRoute(),
                "the parked monitor is still alive, so a second one must not be started");
        }
        finally
        {
            route.disable();
        }
    }

    @Test
    public void testExpressions() throws Exception
    {
        // Generate random expressions and verify consistency
        for (int i = 0; i < 20; i++)
        {
            String randomExpr = generateRandomExpression();
            System.out.println("---");
            System.out.println(randomExpr);
            System.out.println("---");
            NodeExpression node = NodeExpression.fromTextRepresentation(randomExpr, model);
            String textRepresentation = NodeExpression.toTextRepresentation(node, model);            
            NodeExpression parsedNode = NodeExpression.fromTextRepresentation(textRepresentation, model);
            assertEquals(node, parsedNode);
        }
    }

    private String generateRandomExpression() throws Exception
    {
        return generateRandomExpression(RANDOM.nextInt(MAX_NUM_COMMANDS) + 1);
    }

    private String generateRandomExpression(int remainingCommands) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        int numCommands = remainingCommands > 1 ? RANDOM.nextInt(remainingCommands - 1) + 1 : 1;
        boolean useParens = RANDOM.nextBoolean();
        boolean useOr = RANDOM.nextBoolean();
        boolean useAnd = RANDOM.nextBoolean();

        if (useParens)
        {
            sb.append("(");
        }

        for (int i = 0; i < numCommands; i++)
        {
            if (RANDOM.nextBoolean())
            {
                int address = 50 + RANDOM.nextInt(10);
                boolean setting = RANDOM.nextBoolean();
                String command = RouteCommand.RouteCommandAccessory(address, MM2, setting).toLine(model.getAccessoryByAddress(address, MarklinAccessory.accessoryDecoderType.MM2));
                sb.append(command);
            }
            else
            {
                int address = 5 + RANDOM.nextInt(5);
                boolean setting = RANDOM.nextBoolean();
                String command = RouteCommand.RouteCommandFeedback(address, setting).toLine(null);
                sb.append(command);
            }

            // 20% likelihood of generating a nested expression
            if (remainingCommands > 1 && RANDOM.nextInt(100) < 20)
            {
                sb.append(" AND ");
                sb.append(generateRandomExpression(remainingCommands - numCommands));
            }
            
            if (i < numCommands - 1)
            {
                sb.append(" AND ");
            }
        }

        if (useParens)
        {
            sb.append(")");
        }

        if (useOr && RANDOM.nextBoolean())
        {
            sb.append("\nOR\n");
            sb.append(generateRandomExpression(remainingCommands - numCommands));
        }
        else if (useAnd && RANDOM.nextBoolean())
        {
            sb.append("\nAND\n");
            sb.append(generateRandomExpression(remainingCommands - numCommands));
        }

        return sb.toString();
    }

    /**
     * Adding and removing a route from the database
     */
    @Test
    public void testAddRemoveRoute()
    {   
        List<MarklinRoute> currentRoutes = new ArrayList<>(model.getRoutes());
        List<Integer> currentIds = new ArrayList<>();
        List<String> currentRouteNames = new ArrayList<>(model.getRouteList());
        
        for (String r : model.getRouteList())
        {
            currentIds.add(model.getRoute(r).getId());
        }
        
        MarklinRoute newRoute = null;
        
        while (newRoute == null) 
        {
            MarklinRoute newRouteCandidate = generateRandomRoute();
            
            if (!currentIds.contains(newRouteCandidate.getId()) && !currentRouteNames.contains(newRouteCandidate.getName()))
            {
                newRoute = newRouteCandidate;
                break;
            }
        }
        
        System.out.println(newRoute.toVerboseString());
        model.newRoute(newRoute);
        
        assertEquals(model.getRoute(newRoute.getName()), newRoute,
            "the route must come back out of the model as it went in");

        model.deleteRoute(newRoute.getName());

        assertEquals(model.getRouteList(), currentRouteNames,
            "deleting the route must restore the original list");

        assertNull(model.getRoute(newRoute.getName()), "the route must be gone after deletion");
        
        List<MarklinRoute> finalRoutes = new ArrayList<>(model.getRoutes());

        assertTrue(new HashSet<>(finalRoutes).equals(new HashSet<>(currentRoutes)));
    }

    /**
     * Setting a delay by address must work on a route that also contains locomotive commands.
     *
     * MarklinRoute.setDelay walks every command looking for a matching address, but getAddress() is
     * only meaningful for accessory and feedback commands - it parses the ADDRESS entry of the command
     * config, which locomotive commands do not have.  A locomotive command appearing before the target
     * accessory therefore aborts the whole call.
     *
     * This is the shared root cause behind the CS3 import failure below; it is exercised here directly
     * because it is protocol-independent.
     */
    @Test
    public void testSetDelayOnRouteContainingLocomotiveCommands()
    {
        List<RouteCommand> commands = new ArrayList<>();

        // The locomotive does not need to exist - only the command's shape matters here
        commands.add(RouteCommand.RouteCommandLocomotiveSpeed("Delay Test Loc", 40));
        commands.add(RouteCommand.RouteCommandLocomotiveDirection("Delay Test Loc", DIR_FORWARD));
        commands.add(RouteCommand.RouteCommandAccessory(5, MM2, true));

        MarklinRoute route = new MarklinRoute(model, "Mixed delay route", 9701, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        route.setDelay(5, 1500);

        assertEquals(route.getRoute().get(2).getDelay(), 1500,
            "the delay should have been applied to the accessory at address 5");

        assertEquals(route.getRoute().get(0).getDelay(), 0,
            "the locomotive speed command should be untouched");

        assertEquals(route.getRoute().get(1).getDelay(), 0,
            "the locomotive direction command should be untouched");
    }

    /**
     * A CS3 route that starts a locomotive and then throws a switch after a delay must import intact.
     *
     * The item order is what the CS3 editor produces when the operator sets a speed and then schedules
     * an accessory a couple of seconds later.  parseRoutesCS3 appends the speed command first, so the
     * subsequent setDelay call for the accessory walks over it - see the test above.  The resulting
     * exception is swallowed by the per-route handler, and the entire route is dropped from the import
     * with only a generic "could not be parsed" message.
     *
     * Note the reverse ordering (accessory first) imports fine, as does the same route without a
     * delay, so nothing but the ordering plus the delay is required to lose the route.
     */
    @Test
    public void testCS3RouteWithLocomotiveCommandThenDelayedAccessory()
    {
        CS2File parser = new CS2File(null, model);

        JSONArray locs = new JSONArray(
            "[{\"internname\": \"loc_mixed\", \"name\": \"CS3 Mixed Route Loc\"}]");

        JSONArray mags = new JSONArray(
            "[{\"id\": 1, \"address\": 7, \"prot\": \"mm\", \"typ\": \"linksweiche\", \"states\": 2}]");

        JSONArray routes = new JSONArray(
            "[{\"id\": \"9702\", \"name\": \"Mixed CS3 route\", \"items\": ["
          + "  {\"typ\": \"speed\", \"lok\": \"loc_mixed\", \"wert\": 500, \"key\": \"000\"},"
          + "  {\"typ\": \"mag\", \"magnetartikel\": \"1\", \"stellung\": \"1\", \"sekunde\": 2, \"key\": \"001\"}"
          + "]}]");

        List<MarklinRoute> parsed = parser.parseRoutesCS3(routes, mags, locs);

        assertEquals(parsed.size(), 1,
            "a route mixing a locomotive command with a delayed accessory must not be dropped");

        MarklinRoute route = parsed.get(0);

        assertEquals(route.getRoute().size(), 2,
            "both the speed command and the accessory should be present");

        assertTrue(route.getRoute().get(0).isLocomotiveSpeed(),
            "the speed command should come first");

        assertTrue(route.getRoute().get(1).isAccessory(),
            "the accessory should come second");

        assertEquals(route.getRoute().get(1).getDelay(), 2000,
            "the accessory's 2 second delay should have survived the import");
    }

    /**
     * Adding and removing a route from the database
     * @throws java.lang.IllegalAccessException
     * @throws java.lang.NoSuchFieldException
     */
    @Test
    public void testJSONImport() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, Exception
    {   
        List<MarklinRoute> currentRoutes = new ArrayList<>(model.getRoutes());

        String json = model.exportRoutes();
        
        List<MarklinRoute> finalRoutes = model.parseRoutesFromJson(json);
                
        for (MarklinRoute current : currentRoutes)
        {
            for (MarklinRoute finalr : finalRoutes)
            {
                if (finalr.getName().equals(current.getName()) && !current.equals(finalr))
                {
                    System.out.println("EXPECTED: ");
                    System.out.println(current);
                    System.out.println("GOT: ");
                    System.out.println(finalr);
                    System.out.println("=========================");
                }
            }  
        }

        assertTrue(currentRoutes.equals(finalRoutes));
    }
    
    @Test
    public void testConstants() throws Exception
    {
        assertEquals(MarklinAccessory.stringToAccessoryType("Switch"), MarklinAccessory.accessoryType.SWITCH);
        assertEquals(MarklinAccessory.stringToAccessoryType("switch"), MarklinAccessory.accessoryType.SWITCH);
        assertEquals(MarklinAccessory.stringToAccessoryType("SWITCH"), MarklinAccessory.accessoryType.SWITCH);
        assertEquals(MarklinAccessory.stringToAccessoryType(" SwITCH "), MarklinAccessory.accessoryType.SWITCH);
        
        boolean excepted = false;
        
        try
        {
            MarklinAccessory.stringToAccessoryType(" blah ");
        }
        catch (Exception e)
        {
            excepted = true;
        }
        
        assertEquals(excepted, true);
        
        assertEquals(MarklinAccessory.stringToAccessoryType("Signal"), MarklinAccessory.accessoryType.SIGNAL);
        assertEquals(MarklinAccessory.stringToAccessoryType("signal"), MarklinAccessory.accessoryType.SIGNAL);
        assertEquals(MarklinAccessory.stringToAccessoryType("SIGNAL"), MarklinAccessory.accessoryType.SIGNAL);
        assertEquals(MarklinAccessory.stringToAccessoryType(" SiGNAL "), MarklinAccessory.accessoryType.SIGNAL);
        
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("turn"), true);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("red"), true);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("TURN"), true);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("RED"), true);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting(" Turn"), true);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting(" Red"), true);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("green"), false);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("straight"), false);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("GREEN"), false);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("STRAIGHT"), false);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("Green "), false);
        assertEquals(MarklinAccessory.stringAccessorySettingToSetting("Straight "), false);
        
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SWITCH, 1, MM2.toString(), true), "Switch 1,turn");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SWITCH, 3, MM2.toString(), false), "Switch 3,straight");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SIGNAL, 2, MM2.toString(), false), "Signal 2,green");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SIGNAL, 4, MM2.toString(), true), "Signal 4,red");
        
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SWITCH, 1, DCC.toString(), false), "Switch 1 DCC,straight");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SWITCH, 3, DCC.toString(), true), "Switch 3 DCC,turn");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SIGNAL, 5, DCC.toString(), false), "Signal 5 DCC,green");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SIGNAL, 6, DCC.toString(), true), "Signal 6 DCC,red");
        
        
        assertEquals(MarklinAccessory.accessoryTypeToPrettyString(Accessory.accessoryType.SWITCH), "Switch");
        assertEquals(MarklinAccessory.accessoryTypeToPrettyString(Accessory.accessoryType.SIGNAL), "Signal");
        
        assertEquals(MarklinAccessory.switchedToAccessorySetting(true, Accessory.accessoryType.SWITCH), Accessory.accessorySetting.TURN);
        assertEquals(MarklinAccessory.switchedToAccessorySetting(false, Accessory.accessoryType.SWITCH), Accessory.accessorySetting.STRAIGHT);
        assertEquals(MarklinAccessory.switchedToAccessorySetting(true, Accessory.accessoryType.SIGNAL), Accessory.accessorySetting.RED);
        assertEquals(MarklinAccessory.switchedToAccessorySetting(false, Accessory.accessoryType.SIGNAL), Accessory.accessorySetting.GREEN);
    }
    
    /**
     * Exporting route to JSON
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException 
     */
    @Test
    public void testJSONExportImport() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, Exception
    {   
        List<MarklinRoute> currentRoutes = new ArrayList<>(model.getRoutes());
        List<Integer> currentIds = new ArrayList<>();
        List<String> currentRouteNames = new ArrayList<>(model.getRouteList());
                
        for (String r : model.getRouteList())
        {
            currentIds.add(model.getRoute(r).getId());
        }
        
        List<MarklinRoute> newRoutes = new ArrayList();
        
        while (newRoutes.size() < (new Random()).nextInt(40) + 1) 
        {
            MarklinRoute newRouteCandidate = generateRandomRoute();
            
            if (!currentIds.contains(newRouteCandidate.getId()) && !currentRouteNames.contains(newRouteCandidate.getName())
                    && model.getRoute(newRouteCandidate.getName()) == null
            )
            {
                newRoutes.add(newRouteCandidate);

                // The id has to join the ones already in use, or this loop only ever checks the
                // candidate against routes that existed before it started.  Ids are drawn from
                // nextInt(1000), so two generated routes collided a few percent of the time - and the
                // route database is keyed by id, so the second silently evicted the first from it.  The
                // evicted route was then missing from the export, missing again after the re-import,
                // and getRoute(name) returned null for a route this list still expects to exist.
                //
                // Names needed no such fix: getRoute(name) below sees routes added earlier in this loop.
                currentIds.add(newRouteCandidate.getId());

                model.newRoute(newRouteCandidate);
            }
        }
        
        String json = model.exportRoutes();
        
        List<MarklinRoute> finalRoutes = model.parseRoutesFromJson(json);

        // Routes in JSON should equal routes in model
        assertTrue(model.getRoutes().equals(finalRoutes));
        assertTrue(!model.getRoutes().equals(currentRoutes));
        
        // Actually import the routes into the model
        model.importRoutes(json);
        assertEquals(model.getRoutes(), finalRoutes, "importing must reproduce the exported routes");

        for (MarklinRoute r : newRoutes)
        {
            // All routes will be disabled because importRoutes first deletes all existing routes
            model.getRoute(r.getName()).disable();

            assertEquals(model.getRoute(r.getName()), r,
                "each imported route must match the one that was exported");
        }
        
        // Line export
        for (MarklinRoute r : newRoutes)
        {
            for (RouteCommand rc : r.getRoute())
            {
                MarklinAccessory a = null;
                if (rc.isAccessory())
                {
                    a = model.getAccessoryByAddress(rc.getAddress(), 
                            MarklinAccessory.determineAccessoryDecoderType(rc.getAccessoryType()));
                }
                
                RouteCommand rc2 = RouteCommand.fromLine(rc.toLine(a), false);
                                
                assertEquals(rc, rc2);
            }
        }
        
        // Cleanup
        for (MarklinRoute r : newRoutes)
        {
            model.deleteRoute(r.getName());
        }
        
        for (MarklinRoute r : newRoutes)
        {
            assertNull(model.getRoute(r.getName()), "route " + r.getName() + " must be gone");
        }

        assertEquals(model.getRouteList(), currentRouteNames,
            "the route list must be back to its original state");
    }
        
    /**
     * A route read from an autonomy file that omits "triggerType" gets the documented
     * CLEAR_THEN_OCCUPIED default.
     *
     * fromJSON used to leave the field null, and the monitor's "== CLEAR_THEN_OCCUPIED" test then fell
     * through to waiting for occupied-then-clear - so the route fired on the opposite edge of the sensor
     * from the one the file meant, for the whole session.
     *
     * "auto" is false so that constructing the route does not start a live monitor thread.
     */
    @Test
    public void testRouteFromJSONDefaultsToClearThenOccupied()
    {
        org.json.JSONObject json = new org.json.JSONObject();
        json.put("name", "C13 default trigger type");
        json.put("id", 9601);
        json.put("s88", 88);
        json.put("auto", false);

        assertEquals(MarklinRoute.fromJSON(json, model).getTriggerType(),
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
            "an absent triggerType must mean clear-then-occupied, not the opposite edge");

        // An explicit value is still honoured
        json.put("triggerType", MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR.toString());

        assertEquals(MarklinRoute.fromJSON(json, model).getTriggerType(),
            MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR);
    }

    /**
     * A malformed route line is reported as a readable error rather than an unchecked exception.
     *
     * Every branch of the parser splits user-entered text on commas and calls Integer.parseInt on the
     * pieces, so a truncated line or a non-numeric address escaped as ArrayIndexOutOfBoundsException or
     * NumberFormatException.  Those bypassed the friendly error.invalidLine message the parser produces
     * in its other branches, and reached the route editor as a raw stack trace instead of a message.
     */
    @Test
    public void testMalformedRouteLinesReportAReadableError()
    {
        String[] malformed = {
            "Switch abc,turn",      // address does not match the accessory pattern
            "Switch 5",             // no setting at all
            "locdir,MyLoc",         // truncated before the direction
            "locdir",               // truncated before everything
            "locspeed,MyLoc,abc",   // non-numeric speed
            "locfunc,MyLoc,3",      // truncated before the state
            "autoloc,MyLoc",        // truncated before the s88
            "Switch 5,turn,abc"     // non-numeric delay
        };

        for (String line : malformed)
        {
            try
            {
                RouteCommand.fromLine(line, false);

                fail("expected a readable error for: " + line);
            }
            catch (Exception e)
            {
                assertFalse(e instanceof RuntimeException,
                    line + " threw an unchecked " + e.getClass().getSimpleName()
                    + " instead of a readable error");

                assertNotNull(e.getMessage(), line + " produced no message");
            }
        }
    }

    /**
     * The conversion above must not swallow lines that are actually valid.
     */
    @Test
    public void testValidRouteLinesStillParse() throws Exception
    {
        assertNotNull(RouteCommand.fromLine("Switch 5,turn", false));
        assertNotNull(RouteCommand.fromLine("Switch 5,turn,200", false));
        assertNotNull(RouteCommand.fromLine("5,1", false));
        assertNotNull(RouteCommand.fromLine("locspeed,MyLoc,50", false));
        assertNotNull(RouteCommand.fromLine("locdir,MyLoc,forward", false));
        assertNotNull(RouteCommand.fromLine("locfunc,MyLoc,3,1", false));
        assertNotNull(RouteCommand.fromLine("autoloc,MyLoc,14", false));
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testRoutes.model = init(null, true, false, false, false);
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
     * A route nothing else is using, so these tests cannot collide with the database they run against.
     */
    private static MarklinRoute unusedRoute()
    {
        MarklinRoute candidate = generateRandomRoute();

        while (model.getRoute(candidate.getName()) != null || model.getRoute(candidate.getId()) != null)
        {
            candidate = generateRandomRoute();
        }

        return candidate;
    }

    /**
     * editRoute refuses what it cannot do, says so in its return value, and damages nothing.
     *
     * It edits by delete-then-re-add, so a rename onto a name another route already holds used to
     * delete the original and then decline to add it back - losing the route. The check moved into the
     * model precisely so it would not depend on one dialog; this pins both halves of that: the boolean
     * is false, and both routes are still there afterwards.
     */
    @Test
    public void testEditRouteRefusesWithoutLosingTheRoute() throws Exception
    {
        MarklinRoute first = unusedRoute();
        MarklinRoute second = unusedRoute();

        while (second.getName().equals(first.getName()) || second.getId() == first.getId())
        {
            second = unusedRoute();
        }

        assertTrue(model.newRoute(first), "precondition: the first route must be added");
        assertTrue(model.newRoute(second), "precondition: the second route must be added");

        final String firstName = first.getName();
        final String secondName = second.getName();

        try
        {
            assertFalse(model.editRoute("no route is called this", "anything", new ArrayList<>(), 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
                "editing a route that does not exist must be refused rather than reported as done");

            assertFalse(model.editRoute(firstName, secondName, first.getRoute(), first.getS88(),
                first.getTriggerType(), false, null),
                "renaming onto a name another route already holds must be refused");

            assertNotNull(model.getRoute(firstName),
                "the refusal has to come before the delete - the route being renamed must survive it");
            assertNotNull(model.getRoute(secondName),
                "and so must the route whose name it collided with");
        }
        finally
        {
            model.deleteRoute(firstName);
            model.deleteRoute(secondName);
        }
    }

    /**
     * And it still succeeds, so the test above is not passing because everything is refused.
     */
    @Test
    public void testEditRouteSucceedsWhenNothingIsInTheWay() throws Exception
    {
        MarklinRoute route = unusedRoute();

        assertTrue(model.newRoute(route), "precondition: the route must be added");

        final String original = route.getName();
        final String renamed = original + " renamed";

        try
        {
            assertTrue(model.editRoute(original, renamed, route.getRoute(), route.getS88(),
                route.getTriggerType(), false, null),
                "an edit with nothing in its way must report success");

            assertNotNull(model.getRoute(renamed), "and the new name must resolve");
            assertNull(model.getRoute(original), "while the old one must not");
        }
        finally
        {
            model.deleteRoute(renamed);
            model.deleteRoute(original);
        }
    }

    /**
     * UC-C6: executing an unknown route name must be a no-op, not an NPE.
     *
     * execRoute is routeDB.getByName(name).execRoute(false) - null dereference.  The UI passes
     * names from live lists, but the programmatic API reaches this directly, and
     * ProgrammaticControlExample literally calls execRoute("SomeRoute").  getLocAddress twenty
     * lines up was fixed for exactly this shape.
     */
    @Test
    public void testExecutingAnUnknownRouteNameIsANoOp()
    {
        try
        {
            model.execRoute("UC-C6 no such route");
        }
        catch (NullPointerException e)
        {
            fail("an unknown route name must log and return, not NPE");
        }
    }

    /**
     * Deleting a route on the activation list must work whatever list the caller handed over.
     *
     * Layout.setActivateRouteIDs stores the caller's list verbatim, and deleteRoute later mutates it
     * with remove().  testAutoLayout passes Collections.singletonList - immutable - and its teardown
     * has thrown UnsupportedOperationException on every run since 2025-11-29, invisibly (a TestNG
     * configuration error, not a failing test), leaving "Testcase Route 1" undeleted in the session.
     *
     * The model must not take ownership of a caller's list without copying it.
     */
    @Test
    public void testDeletingAnActivationListedRouteSurvivesAnImmutableList() throws Exception
    {
        MarklinRoute r = new MarklinRoute(model, "UC immutable activation", 46901,
            new LinkedList<>(), 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            NodeExpression.fromList(new ArrayList<>()));

        model.newRoute(r);

        try
        {
            model.getAutoLayout().setActivateRouteIDs(Collections.singletonList(r.getId()));

            try
            {
                model.deleteRoute("UC immutable activation");
            }
            catch (UnsupportedOperationException e)
            {
                fail("deleteRoute mutated the caller's own list - the model must copy what it is "
                    + "handed, not take ownership of it");
            }

            assertNull(model.getRoute("UC immutable activation"),
                "and the route is actually gone, not stranded by the failed removal");
        }
        finally
        {
            // Leave nothing behind for later tests in this class, whichever way it went
            model.getAutoLayout().setActivateRouteIDs(new LinkedList<>());

            if (model.getRoute("UC immutable activation") != null)
            {
                model.getAutoLayout().getActivateRouteIDs().clear();
                model.deleteRoute("UC immutable activation");
            }
        }
    }

    /**
     * A delay of zero is the same command as no delay at all.
     *
     * setDelay(0) used to store DELAY=0 in the config map, but toLine only emits positive delays -
     * so a command built with an explicit zero stopped equalling its own line round trip.  The
     * randomized JSON export test draws setDelay(random.nextInt(1000)) behind a coin flip, which is
     * a 1-in-2000 flake per direction command; one seed finally hit it.
     */
    @Test
    public void testAZeroDelayIsTheSameAsNoDelay() throws Exception
    {
        RouteCommand explicit = RouteCommand.RouteCommandLocomotiveDirection("Zero Delay Loc", DIR_FORWARD);
        explicit.setDelay(0);

        RouteCommand bare = RouteCommand.RouteCommandLocomotiveDirection("Zero Delay Loc", DIR_FORWARD);

        assertEquals(explicit, bare, "an explicit zero delay is the same command as none");

        assertEquals(RouteCommand.fromLine(explicit.toLine(null), false), explicit,
            "and it survives its own line round trip");

        // A positive delay still round-trips with its value
        explicit.setDelay(250);

        assertEquals(explicit.getDelay(), 250);
        assertEquals(RouteCommand.fromLine(explicit.toLine(null), false), explicit);

        // And setting it back to zero clears it again
        explicit.setDelay(0);

        assertEquals(explicit, bare, "zero clears a previously set delay");
    }
}
