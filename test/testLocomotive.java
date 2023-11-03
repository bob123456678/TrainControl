/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/EmptyTestNGTest.java to edit this template
 */

import base.Locomotive;
import java.util.HashMap;
import marklin.MarklinControlStation;
import static marklin.MarklinControlStation.init;
import marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 */
public class testLocomotive {
    
    public static MarklinControlStation model;
    public static MarklinLocomotive l;
    
    public testLocomotive()
    {
    }

    /**
     * Test locomotive class functionality
     */
    @Test
    public void testLocomotiveConstructor()
    {   
        l = new MarklinLocomotive(model, 80, MarklinLocomotive.decoderType.MM2, "Test Loc",
                MarklinLocomotive.locDirection.DIR_FORWARD,
                new boolean[] {false,false,true,false,false}, // function state
                new int[] {128,10,240,241,0}, // function types
                new boolean[] {true,true,false,true,false}, // preferred functions
                99,// preferred speed
                2, //departure F
                3, //arival F
                true, //reversible
                4, // length
                new HashMap<>() // total runtime
        );
        
        assertEquals(128, l.getFunctionType(0));
        assertEquals(true, l.isFunctionPulse(0));
        assertEquals(10, l.getFunctionType(1));
        assertEquals(false, l.isFunctionPulse(1));
        assertEquals(240, l.getFunctionType(2));
        assertEquals(true, l.isFunctionPulse(2));
        assertEquals(112, MarklinLocomotive.sanitizeFIconIndex(l.getFunctionType(2)));
        assertEquals(0, MarklinLocomotive.sanitizeFIconIndex(l.getFunctionType(3)));
        assertEquals(0, l.getFunctionType(4));

        assertEquals(true, l.isReversible());
        assertEquals((long) 4, (long) l.getTrainLength());
        assertEquals(0, l.getTotalRuntime());
        assertEquals((long) 3, (long) l.getArrivalFunc());
        assertEquals((long) 2, (long) l.getDepartureFunc());
        assertEquals((long) 99, (long) l.getPreferredSpeed());
        
        assertEquals(false, l.getF(0));
        assertEquals(false, l.getF(1));
        assertEquals(true, l.getF(2));
        assertEquals(false, l.getF(3));
        assertEquals(false, l.getF(4));

        l.setF(0, true);
        assertEquals(true, l.getF(0));

        l.applyPreferredFunctions();
                
        assertEquals(true, l.getF(0));
        assertEquals(true, l.getF(1));
        assertEquals(false, l.getF(2));
        assertEquals(true, l.getF(3));
        assertEquals(false, l.getF(4));
        
        l.setDepartureFunc(100);
        assertEquals((long) 2, (long) l.getDepartureFunc());
        
        l.applyPreferredSpeed();
        assertEquals((long) 99, (long) l.getSpeed());

        l.setSpeed(50);
        assertEquals((long) 50, (long) l.getSpeed());

        l.stop();
        assertEquals((long) 0, (long) l.getSpeed());

        assertEquals(MarklinLocomotive.locDirection.DIR_FORWARD, l.getDirection());
        
        l.switchDirection();
        assertEquals(MarklinLocomotive.locDirection.DIR_BACKWARD, l.getDirection());

        l.switchDirection();
        assertEquals(MarklinLocomotive.locDirection.DIR_FORWARD, l.getDirection());
        
        assertEquals(MarklinLocomotive.decoderType.MM2, l.getDecoderType());
        
        assertEquals("Test Loc", l.getName());
        
        assertEquals(80, l.getAddress());
        
        l.setSpeed(10).delay(50).setSpeed(0);
        assert l.getTotalRuntime() > 0;
        assertEquals(Locomotive.getDate(System.currentTimeMillis()), l.getOperatingDate(true));

        l.functionsOff();
        assertEquals(false, l.getF(3));
        assertEquals(false, l.getF(0));

        l.lightsOn();
        assertEquals(true, l.getF(0));
        l.lightsOff();
        assertEquals(false, l.getF(0));
    }
        
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testLocomotive.model = init(null, true, false, false, false); 
        model.stop();
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
