import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.traincontrol.automation.Layout;
import org.traincontrol.gui.TrainControlUI;
import static org.traincontrol.gui.TrainControlUI.AUTONOMY_BLANK;
import static org.traincontrol.gui.TrainControlUI.AUTONOMY_SAMPLE;
import static org.traincontrol.gui.TrainControlUI.RESOURCE_PATH;

/**
 *
 */
public class testAutoLayout
{    
    public static MarklinControlStation model;
    
    public testAutoLayout()
    {
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
        mu_1_2_cs.setCentralStationMultiUnitLocomotives(locList12);
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
