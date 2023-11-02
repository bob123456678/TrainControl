/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/EmptyTestNGTest.java to edit this template
 */

import base.RouteCommand;
import static base.RouteCommand.commandType.TYPE_ACCESSORY;
import static base.RouteCommand.commandType.TYPE_STOP;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import marklin.MarklinControlStation;
import static marklin.MarklinControlStation.init;
import marklin.MarklinRoute;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author adamo
 */
public class testRoutes {
    
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

        Map<Integer, Boolean> conditionS88s = new HashMap<>(); // Generate random values for conditionS88s
        conditionS88s.put(random.nextInt(100), random.nextBoolean());
        
         List<RouteCommand> routeCommands = new ArrayList<>();

        // Populate the list with random RouteCommand objects
        for (int i = 0; i < random.nextInt(20); i++) {
            RouteCommand.commandType randomType = random.nextBoolean() ? TYPE_ACCESSORY : TYPE_STOP;

            switch (randomType) {
                case TYPE_ACCESSORY:
                    int address = random.nextInt(100);
                    boolean setting = random.nextBoolean();
                    RouteCommand accessoryCommand = RouteCommand.RouteCommandAccessory(address, setting);
                    routeCommands.add(accessoryCommand);
                    break;
                case TYPE_STOP:
                    RouteCommand stopCommand = RouteCommand.RouteCommandStop();
                    routeCommands.add(stopCommand);
                    break;
            }
        }
        MarklinRoute route = new MarklinRoute(model, name, id, routeCommands, s88, triggerType, enabled, conditionS88s);
        
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
    public void testJSONImport() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {   
        List<MarklinRoute> currentRoutes = new ArrayList<>(model.getRoutes());

        String json = model.exportRoutes();
        
        List<MarklinRoute> finalRoutes = model.parseRoutesFromJson(json);

        assert currentRoutes.equals(finalRoutes);
    }
    
    /**
     * Exporting route to JSON
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException 
     */
    @Test
    public void testJSONExportImport() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {   
        List<MarklinRoute> currentRoutes = new ArrayList<>(model.getRoutes());
        List<Integer> currentIds = new ArrayList<>();
        List<String> currentRouteNames = new ArrayList<>(model.getRouteList());
                
        for (String r : model.getRouteList())
        {
            currentIds.add(model.getRoute(r).getId());
        }
        
        List<MarklinRoute> newRoutes = new ArrayList();
        
        while (newRoutes.size() < (new Random()).nextInt(10)) 
        {
            MarklinRoute newRouteCandidate = generateRandomRoute();
            
            if (!currentIds.contains(newRouteCandidate.getId()) && !currentRouteNames.contains(newRouteCandidate.getName()))
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