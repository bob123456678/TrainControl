import org.traincontrol.base.RouteCommand;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_ACCESSORY;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FUNCTION;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LOCOMOTIVE;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_STOP;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import org.traincontrol.marklin.MarklinAccessory;
import static org.traincontrol.base.Accessory.accessoryDecoderType.DCC;
import static org.traincontrol.base.Accessory.accessoryDecoderType.MM2;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_AUTONOMY_LIGHTS_ON;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FUNCTIONS_OFF;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LIGHTS_ON;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_ROUTE;

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
            RouteCommand.commandType[] types = new RouteCommand.commandType[]{TYPE_ACCESSORY, TYPE_STOP, TYPE_FUNCTION, TYPE_LOCOMOTIVE, 
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
                    
                    RouteCommand locCommand = RouteCommand.RouteCommandLocomotive(locName, speed);
                    
                    if (random.nextBoolean())
                    {
                        locCommand.setDelay(random.nextInt(1000));
                    }
                    
                    routeCommands.add(locCommand);
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
        String expr1 = "(" + command1 + "\n" + command2 + ")\nOR\n" + commandOpposite2;
        NodeExpression node1 = NodeExpression.fromTextRepresentation(expr1, model);
        assertTrue(node1.evaluate(model));

        // Test 2: (Switch 60,turn Feedback 10,1)
        String expr2 = "(" + command1 + "\n" + command2 + ")";
        NodeExpression node2 = NodeExpression.fromTextRepresentation(expr2, model);
        assertTrue(node2.evaluate(model));

        // Test 3: Switch 60,turn Feedback 6,0
        String expr3 = command1 + "\n" + command3;
        NodeExpression node3 = NodeExpression.fromTextRepresentation(expr3, model);
        assertTrue(node3.evaluate(model));

        // Test 4: Switch 60,turn OR (Feedback 6,0 Switch 55,straight)
        String expr4 = command1 + "\nOR\n(" + command3 + "\n" + command4 + ")";
        NodeExpression node4 = NodeExpression.fromTextRepresentation(expr4, model);
        assertTrue(node4.evaluate(model));

        // Test 5: (Switch 60,turn Feedback 10,1 Feedback 6,0 Switch 55,straight) should be false
        String expr5 = "(" + command1 + "\n" + command2 + " " + command3 + "\n" + command4 + ")";
        NodeExpression node5 = NodeExpression.fromTextRepresentation(expr5, model);
        assertFalse(node5.evaluate(model));

        // Test 6: (Switch 60,turn) OR (Feedback 10,1 Switch 55,straight) should be true
        String expr6 = "(" + command1 + ")\nOR\n(" + command2 + "\n" + command4 + ")";
        NodeExpression node6 = NodeExpression.fromTextRepresentation(expr6, model);
        assertTrue(node6.evaluate(model));

        // Test 7: (Feedback 10,1 Switch 55,straight) OR (Feedback 6,0) should be true
        String expr7 = "(" + command2 + "\n" + command4 + ")\nOR\n(" + commandOpposite3 + ")";
        NodeExpression node7 = NodeExpression.fromTextRepresentation(expr7, model);
        assertTrue(node7.evaluate(model));

        // Test 7a: (Feedback 10,1 Switch 55,turn) OR (Feedback 6,0) should be false
        String expr7a = "(" + command2 + "\n" + commandOpposite4 + ")\nOR\n(" + commandOpposite3 + ")";
        NodeExpression node7a = NodeExpression.fromTextRepresentation(expr7a, model);
        assertFalse(node7a.evaluate(model));

        // Test 8: Feedback 10,1 (Feedback 6,0 OR Switch 55,straight) should be true
        String expr8 = command2 + "\n(" + command3 + "\nOR\n" + command4 + ")";
        NodeExpression node8 = NodeExpression.fromTextRepresentation(expr8, model);
        assertTrue(node8.evaluate(model));

        // Test 8a: Feedback 10,0 (Feedback 6,1 OR Switch 55,turn) should be false
        String expr8a = commandOpposite2 + "\n(" + commandOpposite3 + "\nOR\n" + commandOpposite4 + ")";
        NodeExpression node8a = NodeExpression.fromTextRepresentation(expr8a, model);
        assertFalse(node8a.evaluate(model));

        // Test 9: Switch 60,turn Switch 60,straight should be false
        String expr9 = command1 + "\n" + commandOpposite1;
        NodeExpression node9 = NodeExpression.fromTextRepresentation(expr9, model);
        assertFalse(node9.evaluate(model));

        // Test 10: (Feedback 10,1 Feedback 4,1) OR Switch 55,straight should be true
        String expr10 = "(" + command2 + "\n" + command5 + ")\nOR\n" + command4;
        NodeExpression node10 = NodeExpression.fromTextRepresentation(expr10, model);
        assertTrue(node10.evaluate(model));

        // Test 11: (Feedback 10,1 Feedback 4,1) (Switch 60,straight) should be false
        String expr11 = "(" + command2 + "\n" + command5 + ")\n" + commandOpposite1;
        NodeExpression node11 = NodeExpression.fromTextRepresentation(expr11, model);
        assertFalse(node11.evaluate(model));

        // Test 12: (Switch 50,turn) (Switch 55,straight) should be true
        String expr12 = "(" + command6 + ")\n" + command4;
        NodeExpression node12 = NodeExpression.fromTextRepresentation(expr12, model);
        assertTrue(node12.evaluate(model));

        // Test 13: (Switch 50,turn) (Switch 50,straight) should be false
        String expr13 = "(" + command6 + ")\n" + commandOpposite6;
        NodeExpression node13 = NodeExpression.fromTextRepresentation(expr13, model);
        assertFalse(node13.evaluate(model));

        // Test 14: Feedback 10,1 AND (Switch 50,turn OR Switch 55,straight) should be true
        String expr14 = command2 + "\n(" + command6 + "\nOR\n" + command4 + ")";
        NodeExpression node14 = NodeExpression.fromTextRepresentation(expr14, model);
        assertTrue(node14.evaluate(model));

        // Test 15: Feedback 10,1 AND (Switch 50,straight OR Switch 55,turn) should be false
        String expr15 = command2 + "\n(" + commandOpposite6 + "\nOR\n" + commandOpposite4 + ")";
        NodeExpression node15 = NodeExpression.fromTextRepresentation(expr15, model);
        assertFalse(node15.evaluate(model));

        // Test 16: (Switch 50,turn Feedback 4,1) AND (Switch 60,turn Feedback 10,1) should be true
        String expr16 = "(" + command6 + "\n" + command5 + ")\n(" + command1 + "\n" + command2 + ")";
        NodeExpression node16 = NodeExpression.fromTextRepresentation(expr16, model);
        assertTrue(node16.evaluate(model));

        // Test 17: (Switch 50,turn Feedback 4,0) AND (Switch 60,turn Feedback 10,1) should be false
        String expr17 = "(" + command6 + "\n" + commandOpposite3 + ")\n(" + command1 + "\n" + command2 + ")";
        NodeExpression node17 = NodeExpression.fromTextRepresentation(expr17, model);
        assertFalse(node17.evaluate(model));

        // Test 18: (Switch 50,turn) AND (Switch 65,turn) should be true
        String expr18 = "(" + command6 + ")\n" + command7;
        NodeExpression node18 = NodeExpression.fromTextRepresentation(expr18, model);
        assertTrue(node18.evaluate(model));
    }

    @Test
    public void testExpressions() throws Exception 
    {
        // Generate random expressions and verify consistency
        for (int i = 0; i < 20; i++)
        {
            String randomExpr = generateRandomExpression();
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

            if (i < numCommands - 1)
            {
                sb.append("\n");
            }

            // 20% likelihood of generating a nested expression
            if (remainingCommands > 1 && RANDOM.nextInt(100) < 20)
            {
                sb.append(generateRandomExpression(remainingCommands - numCommands));
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
        
        assert newRoute.equals(model.getRoute(newRoute.getName()));
        
        model.deleteRoute(newRoute.getName());
        
        assert currentRouteNames.equals(model.getRouteList());
        
        List<MarklinRoute> finalRoutes = new ArrayList<>(model.getRoutes());

        assert finalRoutes.equals(currentRoutes);
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
        
        while (newRoutes.size() < (new Random()).nextInt(20) + 1) 
        {
            MarklinRoute newRouteCandidate = generateRandomRoute();
            
            if (!currentIds.contains(newRouteCandidate.getId()) && !currentRouteNames.contains(newRouteCandidate.getName())
                    && model.getRoute(newRouteCandidate.getName()) == null
            )
            {
                newRoutes.add(newRouteCandidate);
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
        assert model.getRoutes().equals(finalRoutes);
        
        for (MarklinRoute r : newRoutes)
        {
            // All routes will be disabled because importRoutes first deletes all existing routes
            model.getRoute(r.getName()).disable();
            
            assert r.equals(model.getRoute(r.getName()));
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
            assert null == model.getRoute(r.getName());
        }   
                
        assert model.getRouteList().equals(currentRouteNames);
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
}
