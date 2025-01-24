import org.traincontrol.base.RouteCommand;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_ACCESSORY;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FUNCTION;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LOCOMOTIVE;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_STOP;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
