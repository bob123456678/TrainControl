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
        String name = "Route: " + random.nextInt(100);
        int id = random.nextInt(1000);
        int s88 = random.nextInt(10000);
        boolean enabled = random.nextBoolean();

        // Randomly select s88Triggers value
        MarklinRoute.s88Triggers triggerType = (random.nextBoolean()) ? MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED : MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR;

        List<RouteCommand> conditions = new ArrayList<>();
        for (int i = 0; i < random.nextInt(4); i++)
        {
            conditions.add(RouteCommand.RouteCommandFeedback(random.nextInt(100), random.nextBoolean()));
        }
        //Map<Integer, Boolean> conditionS88s = new HashMap<>(); // Generate random values for conditionS88s
        //conditionS88s.put(random.nextInt(100), random.nextBoolean());
                
        List<RouteCommand> routeCommands = new ArrayList<>();

        // Populate the list with random RouteCommand objects
        for (int i = 0; i < random.nextInt(20); i++)
        {    
            RouteCommand.commandType[] types = new RouteCommand.commandType[]{TYPE_ACCESSORY, TYPE_STOP, TYPE_FUNCTION, TYPE_LOCOMOTIVE};
            RouteCommand.commandType randomType = types[random.nextInt(4)];
            
            switch (randomType) {
                case TYPE_ACCESSORY:
                    int address = random.nextInt(100);
                    boolean setting = random.nextBoolean();
                    RouteCommand accessoryCommand = RouteCommand.RouteCommandAccessory(address, setting);
                    
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
            RouteCommand accessoryCommand = RouteCommand.RouteCommandAccessory(address, setting);
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
        MarklinAccessory accessory1 = model.getAccessoryByAddress(60);
        accessory1.setSwitched(true);
        MarklinAccessory accessory2 = model.getAccessoryByAddress(55);
        accessory2.setSwitched(false);

        // Generate command strings
        String command1 = RouteCommand.RouteCommandAccessory(60, true).toLine(model.getAccessoryByAddress(60));
        String command2 = RouteCommand.RouteCommandFeedback(10, true).toLine(null);
        String command3 = RouteCommand.RouteCommandFeedback(6, false).toLine(null);
        String command4 = RouteCommand.RouteCommandAccessory(55, false).toLine(model.getAccessoryByAddress(55));
        String commandOpposite2 = RouteCommand.RouteCommandFeedback(10, false).toLine(null); // False feedback
        String commandOpposite3 = RouteCommand.RouteCommandFeedback(6, true).toLine(null); // False feedback
        String commandOpposite1 = RouteCommand.RouteCommandAccessory(60, false).toLine(model.getAccessoryByAddress(60));
        String commandOpposite4 = RouteCommand.RouteCommandAccessory(55, true).toLine(model.getAccessoryByAddress(55));

        // Test 1: (Switch 60,turn Feedback 10,1) OR Feedback 11,1
        String expr1 = "(" + command1 + "\n" + command2 + ")\nOR\n" + commandOpposite2;
        NodeExpression node1 = NodeExpression.fromTextRepresentation(expr1, model);
        assertTrue(node1.evaluate(model));

        // Test 2: (Switch 60,turn Feedback 10,1)
        String expr2 = "(" + command1 + "\n" + command2 + ")";
        NodeExpression node2 = NodeExpression.fromTextRepresentation(expr2, model);
        assertTrue(node2.evaluate(model));

        // Test 3: Switch 60,turn AND Feedback 6,0
        String expr3 = command1 + "\n" + command3;
        NodeExpression node3 = NodeExpression.fromTextRepresentation(expr3, model);
        assertTrue(node3.evaluate(model));

        // Test 4: Switch 60,turn OR (Feedback 6,0 AND Switch 55,straight)
        String expr4 = command1 + "\nOR\n(" + command3 + "\n" + command4 + ")";
        NodeExpression node4 = NodeExpression.fromTextRepresentation(expr4, model);
        assertTrue(node4.evaluate(model));

        // Test 5: (Switch 60,turn AND Feedback 10,1 AND Feedback 6,0 AND Switch 55,straight) should be false
        String expr5 = "(" + command1 + "\n" + command2 + " " + command3 + "\n" + command4 + ")";
        NodeExpression node5 = NodeExpression.fromTextRepresentation(expr5, model);
        assertFalse(node5.evaluate(model));

        // Test 6: (Switch 60,turn) OR (Feedback 10,1 AND Switch 55,straight) should be true
        String expr6 = "(" + command1 + ")\nOR\n(" + command2 + "\n" + command4 + ")";
        NodeExpression node6 = NodeExpression.fromTextRepresentation(expr6, model);
        assertTrue(node6.evaluate(model));

        // Test 7: (Feedback 10,1 AND Switch 55,straight) OR (Feedback 6,0) should be true
        String expr7 = "(" + command2 + "\n" + command4 + ")\nOR\n(" + commandOpposite3 + ")";
        NodeExpression node7 = NodeExpression.fromTextRepresentation(expr7, model);
        assertTrue(node7.evaluate(model));

        // Test 7a: (Feedback 10,1 AND Switch 55,turn) OR (Feedback 6,0) should be false
        String expr7a = "(" + command2 + "\n" + commandOpposite4 + ")\nOR\n(" + commandOpposite3 + ")";
        NodeExpression node7a = NodeExpression.fromTextRepresentation(expr7a, model);
        assertFalse(node7a.evaluate(model));
        
        // Test 8: Feedback 10,1 AND (Feedback 6,0 OR Switch 55,straight) should be false
        String expr8 = "(" + command2 + ")\n \n(" + command3 + "\nOR\n" + command4 + ")";
        NodeExpression node8 = NodeExpression.fromTextRepresentation(expr8, model);
        assertFalse(node8.evaluate(model));

        // Test 9: Switch 60,turn AND Switch 60,straight should be false
        String expr9 = command1 + "\n" + commandOpposite1;
        NodeExpression node9 = NodeExpression.fromTextRepresentation(expr9, model);
        assertFalse(node9.evaluate(model));
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
        StringBuilder sb = new StringBuilder();
        int numCommands = RANDOM.nextInt(MAX_NUM_COMMANDS) + 1;
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
                String command = RouteCommand.RouteCommandAccessory(address, setting).toLine(model.getAccessoryByAddress(address));
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
        }

        if (useParens)
        {
            sb.append(")");
        }

        if (useOr && RANDOM.nextBoolean())
        {
            sb.append("\nOR\n");
            sb.append(generateRandomExpression());
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

        assert currentRoutes.equals(finalRoutes);
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
            MarklinAccessory.accessoryType t = MarklinAccessory.stringToAccessoryType(" blah ");
        }
        catch(Exception e)
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
        
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SWITCH, 1, true), "Switch 1,turn");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SWITCH, 3, false), "Switch 3,straight");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SIGNAL, 2, false), "Signal 2,green");
        assertEquals(MarklinAccessory.toAccessorySettingString(Accessory.accessoryType.SIGNAL, 4, true), "Signal 4,red");
        
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
        
        while (newRoutes.size() < (new Random()).nextInt(10) + 1) 
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

        // Routes in JSON should equal routes in 
        assert model.getRoutes().equals(finalRoutes);
        assert !model.getRoutes().equals(currentRoutes);
        
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
                assertEquals(rc, RouteCommand.fromLine(rc.toLine(null)));
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
