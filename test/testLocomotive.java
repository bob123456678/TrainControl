import org.traincontrol.base.Locomotive;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.traincontrol.marklin.MarklinLocomotive.getMaxNumF;

/**
 *
 */
public class testLocomotive
{    
    public static MarklinControlStation model;
    public static MarklinLocomotive l;
    public static MarklinLocomotive l2;
    public static MarklinLocomotive l3;
    
    public testLocomotive()
    {
    }

    /**
     * Test locomotive class functionality
     */
    @Test
    public void testLocomotiveConstructor()
    {   
        assertEquals(10, l.isFunctionTimed(4));
        assertEquals(128, l.getFunctionType(0));
        assertEquals(true, l.isFunctionPulse(0));
        assertEquals(10, l.getFunctionType(1));
        assertEquals(false, l.isFunctionPulse(1));
        assertEquals(240, l.getFunctionType(2));
        assertEquals(true, l.isFunctionPulse(2));
        // TODO - simulate CS3
        assertEquals(112, l.sanitizeFIconIndex(l.getFunctionType(2)));
        assertEquals(0, l.sanitizeFIconIndex(l.getFunctionType(3)));
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
       
    /**
     * Test locomotive address validation
     */
    @Test
    public void testLocomotiveAddressRanges()
    {  
        List<Integer> invalidMM2Addresses = Arrays.asList(0, -1, 81, 82, 99, 100);
        List<Integer> validMM2Addresses = Arrays.asList(1, 2, 50, 79, 80);
        
        for (int i : invalidMM2Addresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MM2, i), false);
        }
        
        for (int i : validMM2Addresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MM2, i), true);
        }
        
        List<Integer> invalidDCCAddresses = Arrays.asList(0, -1, 2049, 2050, 3000);
        List<Integer> validDCCAddresses = Arrays.asList(1, 2, 50, 79, 80, 1000, 2048, 2047);
        
        for (int i : invalidDCCAddresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.DCC, i), false);
        }
        
        for (int i : validDCCAddresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.DCC, i), true);
        }    
        
        List<Integer> invalidMFXAddresses = Arrays.asList(0, -1, 100000);
        List<Integer> validMFXAddresses = Arrays.asList(1, 2, 10, 100, 1000);
        
        for (int i : invalidMFXAddresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MFX, i), false);
        }
        
        for (int i : validMFXAddresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MFX, i), true);
        } 
        
        List<Integer> invalidMUAddresses = Arrays.asList(0, -1, 100000, 5121, 5122);
        List<Integer> validMUAddresses = Arrays.asList(1, 2, 10, 100, 1000, 5119, 5120);
        
        for (int i : invalidMUAddresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MULTI_UNIT, i), false);
        }
        
        for (int i : validMUAddresses)
        {
            assertEquals(MarklinLocomotive.validateNewAddress(MarklinLocomotive.decoderType.MULTI_UNIT, i), true);
        } 
    }
    
    /**
     * Test locomotive changes
     */
    @Test
    public void testLocomotiveChanges()
    {            
        l.setAddress(11, MarklinLocomotive.decoderType.DCC);
        assertEquals(l.getFunctionTriggerTypes().length, getMaxNumF(MarklinLocomotive.decoderType.DCC));
        assertEquals(l.getFunctionState().length, getMaxNumF(MarklinLocomotive.decoderType.DCC));
        assertEquals(l.getFunctionTypes().length, getMaxNumF(MarklinLocomotive.decoderType.DCC));
        assertEquals(l.getAddress(), 11);
        assertEquals(l.getDecoderType(), MarklinLocomotive.decoderType.DCC);
        
        l.setAddress(12, MarklinLocomotive.decoderType.MFX);
        assertEquals(l.getFunctionTriggerTypes().length, getMaxNumF(MarklinLocomotive.decoderType.MFX));
        assertEquals(l.getFunctionState().length, getMaxNumF(MarklinLocomotive.decoderType.MFX));
        assertEquals(l.getFunctionTypes().length, getMaxNumF(MarklinLocomotive.decoderType.MFX));
        assertEquals(l.getAddress(), 12);
        assertEquals(l.getDecoderType(), MarklinLocomotive.decoderType.MFX);

        l.setAddress(13, MarklinLocomotive.decoderType.MM2);
        assertEquals(l.getFunctionTriggerTypes().length, getMaxNumF(MarklinLocomotive.decoderType.MM2));
        assertEquals(l.getFunctionState().length, getMaxNumF(MarklinLocomotive.decoderType.MM2));
        assertEquals(l.getFunctionTypes().length, getMaxNumF(MarklinLocomotive.decoderType.MM2));
        assertEquals(l.getAddress(), 13);
        assertEquals(l.getDecoderType(), MarklinLocomotive.decoderType.MM2);
        
        l.setAddress(80, MarklinLocomotive.decoderType.MM2);
        assertEquals(l.getAddress(), 80);
        
        l.setAddress(81, MarklinLocomotive.decoderType.MM2);
        assertEquals(l.getAddress(), 80);
        
        l.rename("New loc");
        assertEquals("New loc", l.getName());

        l.rename("Test Loc");
        assertEquals("Test Loc", l.getName());
    }
    
    /**
     * Test locomotive changes
     */
    @Test
    public void testCopyFunctions()
    {       
        // This will expand the array to ensure the copy correctly ignores the extra values
        l.setAddress(80, MarklinLocomotive.decoderType.MFX);

        l2.setFunctionTypes(l.getFunctionTypes(), l.getFunctionTriggerTypes());
        
        l2.setFunctionState(l.getPreferredFunctions());
        l2.savePreferredFunctions();
        
        l2.setFunctionState(l.getFunctionState());
        
        // These should all differ because the first loc is MFX and has more functions
        assertNotEquals(l.getFunctionState(), l2.getFunctionState());
        assertNotEquals(l.getFunctionTriggerTypes(), l2.getFunctionTriggerTypes());
        assertNotEquals(l.getFunctionTypes(), l2.getFunctionTypes());
        assertNotEquals(l.getPreferredFunctions(), l2.getPreferredFunctions());

        // This will shrink the array back
        l.setAddress(80, MarklinLocomotive.decoderType.MM2);

        // These now should all be identical
        assertEquals(l.getFunctionState(), l2.getFunctionState());
        assertEquals(l.getFunctionTriggerTypes(), l2.getFunctionTriggerTypes());
        assertEquals(l.getFunctionTypes(), l2.getFunctionTypes());
        assertEquals(l.getPreferredFunctions(), l2.getPreferredFunctions());
    }
    
    /**
     * Test multi unit creation
     */
    @Test
    public void testMultiUnit()
    { 
        MarklinLocomotive l3 = model.getLocByName("Test loc 3");
        MarklinLocomotive l4 = model.getLocByName("Test loc 4");
        MarklinLocomotive l5 = model.getLocByName("Test loc 5");
        MarklinLocomotive l6 = model.getLocByName("Test loc 6");
        MarklinLocomotive l7 = model.getLocByName("Test loc 7");
        MarklinLocomotive l8 = model.getLocByName("Test loc 8");
        MarklinLocomotive l88 = model.getLocByName("Test loc 88");

        
        Map<String, Double> locList = new HashMap<String, Double>() {{ put(l4.getName(), 1.0); put(l6.getName(), -1.1); }};
        Map<String, Double> locListB = new HashMap<String, Double>() {{ put(l4.getName(), 1.2); }};

        Map<String, Double> locList2 = new HashMap<String, Double>() {{ put(l3.getName(), 1.0); put(l5.getName(), 1.0); }};
        Map<String, Double> locList3 = new HashMap<String, Double>() {{ put(l7.getName(), -1.0); }};

        Map<String, Double> locList88 = new HashMap<String, Double>() {{ put(l88.getName(), -1.0); }};
        
        // Normal process of assigning a multi unit
        assertTrue(l3.getLinkedLocomotiveNames().isEmpty());
        l3.preSetLinkedLocomotives(locList);
        assertTrue(l3.getLinkedLocomotiveNames().isEmpty());
        l3.setLinkedLocomotives();
        assertFalse(l3.getLinkedLocomotiveNames().isEmpty());
        assertTrue(l3.getLinkedLocomotiveNames().containsKey(l4.getName()));
        assertTrue(l3.getLinkedLocomotiveNames().containsKey(l6.getName()));
        assertEquals(l3.getLinkedLocomotiveNames().size(), 2);
        assertEquals(l3.getLinkedLocomotiveNames().get(l4.getName()), 1.0);
        assertEquals(l3.getLinkedLocomotiveNames().get(l6.getName()), -1.1);
        
        // Trim the list
        l3.preSetLinkedLocomotives(locListB);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().get(l4.getName()), 1.2);
        assertEquals(l3.getLinkedLocomotiveNames().size(), 1);

        // Expand the list
        l3.preSetLinkedLocomotives(locList);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().size(), 2);
        
        // Changing the address should re-validate the state
        
        // No change - address does not conflict
        try
        {
            model.changeLocAddress(l4.getName(), l3.getAddress() - 10, l3.getDecoderType());
        }
        catch (Exception e) {}
        assertEquals(l3.getLinkedLocomotiveNames().size(), 2);
        
        // Should be removed - address does conflict
        try
        {
            model.changeLocAddress(l4.getName(), l3.getAddress(), l3.getDecoderType());
        }
        catch (Exception e) {}
        assertEquals(l3.getLinkedLocomotiveNames().size(), 1);
                
        // Cannot add an existing multi-unit or itself
        assertTrue(l5.getLinkedLocomotiveNames().isEmpty());
        l5.preSetLinkedLocomotives(locList2);
        assertTrue(l5.getLinkedLocomotiveNames().isEmpty());
        l5.setLinkedLocomotives();
        assertTrue(l5.getLinkedLocomotiveNames().isEmpty());

        // Cannot add multi-unit as part of the chain
        assertTrue(l4.getLinkedLocomotiveNames().isEmpty());
        l4.preSetLinkedLocomotives(locList3);
        assertTrue(l4.getLinkedLocomotiveNames().isEmpty());
        
        // Cannot add to a multi-unit defined in the Central station
        l7.preSetLinkedLocomotives(locList2);
        l7.setLinkedLocomotives();
        assertTrue(l7.getLinkedLocomotiveNames().isEmpty());

        // Cannot change the decoder type to MU if there are linked locomotives
        assertEquals(l3.getDecoderType(), MarklinLocomotive.decoderType.MM2);

        try
        {
            model.changeLocAddress(l3.getName(), 0, MarklinLocomotive.decoderType.MULTI_UNIT);
        }
        catch (Exception e) {}
        
        assertEquals(l3.getDecoderType(), MarklinLocomotive.decoderType.MM2);
        
        // Cannot add loc with same address
        l8.preSetLinkedLocomotives(locList88);
        assertTrue(l8.getLinkedLocomotiveNames().isEmpty());

        l8.setLinkedLocomotives();
        assertTrue(l8.getLinkedLocomotiveNames().isEmpty());
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testLocomotive.model = init(null, true, false, false, false); 
        model.stop();
        
        l = new MarklinLocomotive(model, 80, MarklinLocomotive.decoderType.MM2, "Test Loc",
                MarklinLocomotive.locDirection.DIR_FORWARD,
                new boolean[] {false,false,true,false,false}, // function state
                new int[] {128,10,240,241,0}, // function types
                new int[] {Locomotive.FUNCTION_PULSE,Locomotive.FUNCTION_TOGGLE,Locomotive.FUNCTION_PULSE,Locomotive.FUNCTION_PULSE,10},
                new boolean[] {true,true,false,true,false}, // preferred functions
                99,// preferred speed
                2, //departure F
                3, //arival F
                true, //reversible
                4, // length
                new HashMap<>() // total runtime
        );
        
        l2 = new MarklinLocomotive(model, 80, MarklinLocomotive.decoderType.MM2, "Test Loc",
                MarklinLocomotive.locDirection.DIR_FORWARD,
                new boolean[] {false,true,true,false,true}, // function state
                new int[] {128,10,240,241,0}, // function types
                new int[] {Locomotive.FUNCTION_TOGGLE,Locomotive.FUNCTION_PULSE,Locomotive.FUNCTION_PULSE,Locomotive.FUNCTION_TOGGLE,6},
                new boolean[] {false,true,true,true,true}, // preferred functions
                99,// preferred speed
                2, //departure F
                3, //arival F
                true, //reversible
                4, // length
                new HashMap<>() // total runtime
        );
        
        model.newMM2Locomotive("Test loc 3", 80);
        model.newMM2Locomotive("Test loc 4", 79);
        model.newMFXLocomotive("Test loc 5", 78);
        model.newDCCLocomotive("Test loc 6", 77);

        model.newDCCLocomotive("Test loc 7", 76);
        model.getLocByName("Test loc 7").setAddress(76, MarklinLocomotive.decoderType.MULTI_UNIT);
        
        model.newDCCLocomotive("Test loc 8", 75);
        model.newDCCLocomotive("Test loc 88", 75);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        model.deleteLoc("Test loc 3");
        model.deleteLoc("Test loc 4");
        model.deleteLoc("Test loc 5");
        model.deleteLoc("Test loc 6");
        model.deleteLoc("Test loc 7");
        model.deleteLoc("Test loc 8");
        model.deleteLoc("Test loc 88");
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
