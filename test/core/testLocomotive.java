package core;

import org.traincontrol.base.Locomotive;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
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
    public void testLocomotiveConstructor() throws InterruptedException
    {   
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;
        
        assertEquals(10, l.isFunctionTimed(4));
        assertEquals(128, l.getFunctionType(0));
        assertEquals(true, l.isFunctionPulse(0));
        assertEquals(10, l.getFunctionType(1));
        assertEquals(false, l.isFunctionPulse(1));
        assertEquals(240, l.getFunctionType(2));
        assertEquals(true, l.isFunctionPulse(2));
        
        if (model.isCS3() || !model.getNetworkCommState())
        {
            assertEquals(240, l.sanitizeFIconIndex(l.getFunctionType(2)));
            assertEquals(241, l.sanitizeFIconIndex(l.getFunctionType(3))); 
        }
        else
        {
            assertEquals(112, l.sanitizeFIconIndex(l.getFunctionType(2)));
            assertEquals(0, l.sanitizeFIconIndex(l.getFunctionType(3)));  
        }   

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

        // assertTrue rather than a bare assert: a Java assert only executes when the JVM is started
        // with -ea, so it silently checks nothing under any runner that does not enable assertions,
        // and it reports no message when it does fire
        assertTrue(l.getTotalRuntime() > 0, "runtime must accumulate once the locomotive has run");
        assertEquals(Locomotive.getDate(System.currentTimeMillis()), l.getOperatingDate(true));

        l.functionsOff();
        assertEquals(false, l.getF(3));
        assertEquals(false, l.getF(0));

        l.lightsOn();
        assertEquals(true, l.getF(0));
        l.lightsOff();
        assertEquals(false, l.getF(0));
        
        // Switches
        l.setAccessoryState(1, Accessory.accessoryDecoderType.MM2, true);
        l.delay(250);
        
        assertTrue(model.getAccessoryState(1, Accessory.accessoryDecoderType.MM2));
        
        l.setAccessoryState(1, Accessory.accessoryDecoderType.MM2, false);
        l.delay(250);
        
        assertFalse(model.getAccessoryState(1, Accessory.accessoryDecoderType.MM2));
        
        l.setAccessoryState(1, Accessory.accessoryDecoderType.DCC, true);
        l.delay(250);

        assertFalse(model.getAccessoryState(1, Accessory.accessoryDecoderType.MM2));
        assertTrue(model.getAccessoryState(1, Accessory.accessoryDecoderType.DCC));

        l.setAccessoryState(1, Accessory.accessoryDecoderType.DCC, false);
        l.delay(250);
        
        assertFalse(model.getAccessoryState(1, Accessory.accessoryDecoderType.DCC));
        
        model.setAccessoryState(100, Accessory.accessoryDecoderType.MM2, true);
        l.delay(250);
        
        assertEquals(l.getSpeed(), 0);
        l.waitForSpeedAtOrAbove(0);
        
        new Thread(() ->
        {
            l.delay(250);
            
            model.setFeedbackState("1001", true);
            
            l.delay(250);
            
            model.setFeedbackState("1001", false);
            
            l.delay(250);
            
            model.setAccessoryState(100, Accessory.accessoryDecoderType.MM2, false);
            
            l.delay(250);
            
            l.setSpeed(1);
            
            l.delay(250);
            
            l.setSpeed(0);

        }).start();
        
        l.waitForAccessoryState(100, Accessory.accessoryDecoderType.MM2, true);
        assertTrue(model.getAccessoryState(100, Accessory.accessoryDecoderType.MM2));

        assertFalse(model.getFeedbackState("1001"));
        l.waitForOccupiedFeedback("1001");
        assertTrue(model.getFeedbackState("1001"));
        
        l.waitForClearFeedback("1001");
        assertFalse(model.getFeedbackState("1001"));
        
        l.waitForAccessoryState(100, Accessory.accessoryDecoderType.MM2, false);
        assertFalse(model.getAccessoryState(100, Accessory.accessoryDecoderType.MM2));
        
        l.waitForSpeedAtOrAbove(1);
        assertEquals(l.getSpeed(), 1);

        l.waitForSpeedBelow(1);
        assertEquals(l.getSpeed(), 0);
        
        // Test power events
        model.go();
        model.waitForPowerState(true);
        assertTrue(model.getPowerState());

        new Thread(() ->
        {
            l.delay(250);
            
            model.stop();

        }).start();
        
        model.waitForPowerState(false);
        
        assertFalse(model.getPowerState());
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
     * Test command propagation
     * @throws java.lang.InterruptedException
     */
    @Test
    public void testMultiUnitCommands() throws InterruptedException
    {
        // This allows us to test without a central station
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;
        
        MarklinLocomotive l_mu = model.getLocByName("Test loc MU");
        MarklinLocomotive l1 = model.getLocByName("Test loc child 1");
        MarklinLocomotive l2 = model.getLocByName("Test loc child 2");
        MarklinLocomotive l3 = model.getLocByName("Test loc child 3");

        Map<String, Double> locList = new HashMap<String, Double>() {{ put(l1.getName(), 1.0); put(l2.getName(), -2.0); put(l3.getName(), 0.8); }};
        l_mu.preSetLinkedLocomotives(locList);
        l_mu.setLinkedLocomotives();
         
        assertEquals(l_mu.getLinkedLocomotiveNames().size(), 3);
        
        // Sanity check
        l_mu.setDirection(Locomotive.locDirection.DIR_FORWARD);
        l_mu.setSpeed(10);
        l_mu.setF(0, false);
        l_mu.setF(1, true);
        
        Thread.sleep(1000);
        
        // Speeds should be adjusted
        assertEquals(l_mu.getSpeed(), 10);
        assertEquals(l1.getSpeed(), 10);
        assertEquals(l2.getSpeed(), 20);
        assertEquals(l3.getSpeed(), 8);
        
        // MU parameters unchanged
        assertEquals(Locomotive.locDirection.DIR_FORWARD, l_mu.getDirection());
        assertEquals(false, l_mu.getF(0));
        assertEquals(true, l_mu.getF(1));

        // Linked locomotive parameters matching
        assertEquals(l1.getDirection(), l_mu.getDirection());
        assertNotEquals(l2.getDirection(), l_mu.getDirection());
        assertEquals(l3.getDirection(), l_mu.getDirection());
        assertEquals(false, l1.getF(0));
        assertEquals(true, l1.getF(1));
        assertEquals(false, l2.getF(0));
        assertEquals(true, l2.getF(1));       
        assertEquals(false, l3.getF(0));
        assertEquals(true, l3.getF(1));       
        
        l_mu.stop();
        Thread.sleep(1000);

        assertEquals(l_mu.getSpeed(), 0);
        assertEquals(l1.getSpeed(), 0);
        assertEquals(l2.getSpeed(), 0);
        assertEquals(l3.getSpeed(), 0);
        
        l_mu.functionsOff();
        Thread.sleep(1000);

        assertEquals(false, l_mu.getF(0));
        assertEquals(false, l_mu.getF(1));
        assertEquals(false, l1.getF(0));
        assertEquals(false, l1.getF(1));
        assertEquals(false, l2.getF(0));
        assertEquals(false, l2.getF(1));       
        assertEquals(false, l3.getF(0));
        assertEquals(false, l3.getF(1));    
        
        l_mu.switchDirection();
        Thread.sleep(1000);

        assertEquals(Locomotive.locDirection.DIR_BACKWARD, l_mu.getDirection());

        assertEquals(l1.getDirection(), l_mu.getDirection());
        assertNotEquals(l2.getDirection(), l_mu.getDirection());
        assertEquals(l3.getDirection(), l_mu.getDirection());        
    }
    
    /**
     * Test multi unit creation
     */
    @Test
    public void testMultiUnitCreation()
    { 
        MarklinLocomotive l3 = model.getLocByName("Test loc 3");
        MarklinLocomotive l4 = model.getLocByName("Test loc 4");
        MarklinLocomotive l5 = model.getLocByName("Test loc 5");
        MarklinLocomotive l6 = model.getLocByName("Test loc 6");
        MarklinLocomotive l7 = model.getLocByName("Test loc 7");
        MarklinLocomotive l8 = model.getLocByName("Test loc 8");
        MarklinLocomotive l88 = model.getLocByName("Test loc 88");
        MarklinLocomotive l888 = model.getLocByName("Test loc 888");
        MarklinLocomotive l9 = model.getLocByName("Test loc 9");

        Map<String, Double> locList = new HashMap<String, Double>() {{ put(l4.getName(), 1.0); put(l6.getName(), -1.1); }};
        Map<String, Double> locListShorter = new HashMap<String, Double>() {{ put(l4.getName(), 1.2); }};
        Map<String, Double> locListDupe = new HashMap<String, Double>() {{ put(l4.getName(), 1.2); put(l4.getName(), 1.3); }};
        Map<String, Double> locListInvalidRange = new HashMap<String, Double>() {{ put(l4.getName(), 1.2); put(l6.getName(), 2.1); }};
        Map<String, Double> locListInvalidRange2 = new HashMap<String, Double>() {{ put(l4.getName(), 1.2); put(l6.getName(), 0.0); }};
        Map<String, Double> locListInvalidRange3 = new HashMap<String, Double>() {{ put(l4.getName(), 1.2); put(l6.getName(), -2.1); }};

        Map<String, Double> locList2 = new HashMap<String, Double>() {{ put(l3.getName(), 1.0); put(l5.getName(), 1.0); }};
        Map<String, Double> locList3 = new HashMap<String, Double>() {{ put(l7.getName(), -1.0); }};

        Map<String, Double> locList88 = new HashMap<String, Double>() {{ put(l88.getName(), -1.0); }};
        Map<String, Double> locList888 = new HashMap<String, Double>() {{ put(l88.getName(), -1.0); put(l888.getName(), -1.0); }};

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
        l3.preSetLinkedLocomotives(locListShorter);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().get(l4.getName()), 1.2);
        assertEquals(l3.getLinkedLocomotiveNames().size(), 1);
        
        // Attempt to add a duplicate
        l3.preSetLinkedLocomotives(locListDupe);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().size(), 1);
        
        // Attempt to add invalid addresses
        l3.preSetLinkedLocomotives(locListInvalidRange);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().get(l4.getName()), 1.2);
        assertEquals(l3.getLinkedLocomotiveNames().size(), 1);
        
        l3.preSetLinkedLocomotives(locListInvalidRange2);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().get(l4.getName()), 1.2);
        assertEquals(l3.getLinkedLocomotiveNames().size(), 1);
        
        l3.preSetLinkedLocomotives(locListInvalidRange3);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().get(l4.getName()), 1.2);
        assertEquals(l3.getLinkedLocomotiveNames().size(), 1);

        // Expand the list
        l3.preSetLinkedLocomotives(locList);
        l3.setLinkedLocomotives();
        assertEquals(l3.getLinkedLocomotiveNames().get(l4.getName()), 1.0);
        assertEquals(l3.getLinkedLocomotiveNames().get(l6.getName()), -1.1);
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
        
        // Cannot have two children with the same address
        l9.preSetLinkedLocomotives(locList888);
        l9.setLinkedLocomotives();
        assertEquals(l9.getLinkedLocomotiveNames().size(), 1);
        
        // Change address to match one of the child locomotives
        try
        {
            model.changeLocAddress("Test loc 88", 73, MarklinLocomotive.decoderType.DCC);
        }
        catch (Exception e) {}
        
        l9.preSetLinkedLocomotives(locList888);
        l9.setLinkedLocomotives();
        assertEquals(l9.getLinkedLocomotiveNames().size(), 2);
        
        try
        {
            model.changeLocAddress("Test loc 88", 75, MarklinLocomotive.decoderType.DCC);
        }
        catch (Exception e) {}
        
        assertEquals(l9.getLinkedLocomotiveNames().size(), 1);
    }
    
    /**
     * A UID identifies both the decoder type and the address, and addressFromUID has to recover both -
     * it names the locomotive in the log when a command arrives for one we have no record of.
     *
     * The bases are not ordered by value: DCC (0xc000) sits above MFX (0x4000), so testing MFX first
     * matched every DCC and multi-unit UID too and reported them all as MFX addresses.  Each type is
     * checked here at both ends of its address range, since the MFX and multi-unit ranges meet exactly.
     */
    @Test
    public void testAddressFromUID()
    {
        assertEquals(MarklinLocomotive.addressFromUID(5), "MM2 5");
        assertEquals(MarklinLocomotive.addressFromUID(MarklinLocomotive.MM2_MAX_ADDR), "MM2 80");

        // Multi-unit tops out exactly where MFX begins
        assertEquals(MarklinLocomotive.addressFromUID(MarklinLocomotive.MULTI_UNIT_BASE + 1), "MULTI_UNIT 1");
        assertEquals(MarklinLocomotive.addressFromUID(
            MarklinLocomotive.MULTI_UNIT_BASE + MarklinLocomotive.MULTI_UNIT_MAX_ADDR), "MULTI_UNIT 5120");

        assertEquals(MarklinLocomotive.addressFromUID(MarklinLocomotive.MFX_BASE + 1), "MFX 1");
        assertEquals(MarklinLocomotive.addressFromUID(
            MarklinLocomotive.MFX_BASE + MarklinLocomotive.MFX_MAX_ADDR), "MFX 16383");

        // Previously unreachable: these came out as MFX addresses
        assertEquals(MarklinLocomotive.addressFromUID(MarklinLocomotive.DCC_BASE + 1), "DCC 1");
        assertEquals(MarklinLocomotive.addressFromUID(
            MarklinLocomotive.DCC_BASE + MarklinLocomotive.DCC_MAX_ADDR), "DCC 2048");
    }

    /**
     * getF rejects an out-of-range index at both ends.  It used to test only fNumber < numF, so a
     * negative index reached the array and threw ArrayIndexOutOfBoundsException - validF, the sibling
     * check, has always had both bounds.
     */
    @Test
    public void testGetFRejectsOutOfRangeIndexes()
    {
        assertFalse(l.getF(-1), "a negative function number must return false, not throw");
        assertFalse(l.getF(-100));
        assertFalse(l.getF(l.getNumF()), "numF is one past the last valid function");
        assertFalse(l.getF(Integer.MIN_VALUE));
        assertFalse(l.getF(Integer.MAX_VALUE));

        // In-range indexes are covered by testLocFunctions; asserting a specific one here would
        // couple this test to whatever order TestNG happens to run the mutating tests in
    }

    /**
     * Locomotives sharing an address are all reported, whatever their decoder type - MFX included.
     *
     * MFX is deliberately not exempt.  The same physical locomotive can be duplicated in the UI for
     * convenience, or left behind by a stale sync, and both entries then drive the same decoder -
     * exactly what the operator needs to be told about.  This test exists because MFX was once
     * filtered out here on the incorrect reasoning that its mfxuid makes its address unique.
     */
    @Test
    public void testDuplicateAddressesIncludeMFX() throws Exception
    {
        // Deliberately does not hunt for an unused address.  The MM2 range is only 1-80 and a populated
        // database can occupy all of it, so there may be no free one.  It does not matter: the question
        // is whether each of these three is reported, not who else happens to share the address - so the
        // assertions below ask about membership rather than the size of the group.
        int address = Locomotive.MM2_MAX_ADDR;

        MarklinLocomotive mm2 = model.newMM2Locomotive("C5 MM2", address);
        MarklinLocomotive dcc = model.newDCCLocomotive("C5 DCC", address);
        MarklinLocomotive mfx = model.newMFXLocomotive("C5 MFX", address);

        try
        {
            Set<Locomotive> reported = model.getDuplicateLocAddresses().get(address);

            assertNotNull(reported, "locomotives sharing an address must be reported");
            assertTrue(reported.contains(mm2), "MM2 shares this address");
            assertTrue(reported.contains(dcc), "DCC shares this address");
            assertTrue(reported.contains(mfx), "MFX is not exempt - see the comment above");
        }
        finally
        {
            model.deleteLoc("C5 MM2");
            model.deleteLoc("C5 DCC");
            model.deleteLoc("C5 MFX");
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testLocomotive.model = init(null, true, false, false, true);
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
        model.newDCCLocomotive("Test loc 888", 75);
        model.newDCCLocomotive("Test loc 9", 74);
        
        model.newMFXLocomotive("Test loc MU", 4);
        model.newMFXLocomotive("Test loc child 1", 1);
        model.newMM2Locomotive("Test loc child 2", 2);
        model.newMFXLocomotive("Test loc child 3", 3);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        model.deleteLoc("Test loc 3");
        model.deleteLoc("Test loc 4");
        model.deleteLoc("Test loc 4a");
        model.deleteLoc("Test loc 5");
        model.deleteLoc("Test loc 6");
        model.deleteLoc("Test loc 7");
        model.deleteLoc("Test loc 8");
        model.deleteLoc("Test loc 88");
        model.deleteLoc("Test loc 888");
        model.deleteLoc("Test loc 9");

        model.deleteLoc("Test loc MU");
        model.deleteLoc("Test loc child 1");
        model.deleteLoc("Test loc child 2");

        // TST-B20: testMultiUnitCommands creates "Test loc child 3" (:720) and nothing ever deleted it -
        // left in the restored DB image for whatever ran next in this JVM.
        model.deleteLoc("Test loc child 3");

        // TST-B20: testLocomotiveConstructor (:41) and testMultiUnitCommands (:359) both set this true
        // with no restore.  Left true, testAutoLayoutRace.testWaitingForPowerGivesUp can have its GO
        // echoed back by the simulated-packet branch and waitForPowerState(true, 400) return true - a
        // false failure/pass in a class that never touched this flag itself.
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = false;
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
     * UC-B1: converting a decoder type downward must not leave a stale arrival/departure function.
     *
     * setAddress resizes the function arrays and shrinks numF, but never revisits arrivalFunc or
     * departureFunc - so an MFX locomotive with arrival function 20, converted to MM2 (5 functions),
     * keeps the 20.  GraphLocAssign then calls setSelectedIndex(21) against a 6-entry combo model and
     * the assignment dialog - the only place the value could be repaired - throws before it opens.
     *
     * The invariant: after a decoder change, both functions are either cleared or within numF.
     */
    @Test
    public void testDecoderConversionClampsArrivalAndDepartureFunctions() throws Exception
    {
        MarklinLocomotive loc = model.newMFXLocomotive("UC B1 conv", 1000);

        try
        {
            loc.setArrivalFunc(20);
            loc.setDepartureFunc(25);

            assertEquals(loc.getArrivalFunc(), Integer.valueOf(20), "precondition: valid for MFX");
            assertEquals(loc.getDepartureFunc(), Integer.valueOf(25), "precondition: valid for MFX");

            assertTrue(loc.setAddress(75, MarklinLocomotive.decoderType.MM2),
                "precondition: the conversion itself must succeed");

            assertTrue(loc.getArrivalFunc() == null || loc.getArrivalFunc() < loc.getNumF(),
                "arrival function " + loc.getArrivalFunc() + " is out of range for numF "
                + loc.getNumF() + " - the assignment dialog crashes on exactly this");

            assertTrue(loc.getDepartureFunc() == null || loc.getDepartureFunc() < loc.getNumF(),
                "departure function " + loc.getDepartureFunc() + " is out of range for numF "
                + loc.getNumF());
        }
        finally
        {
            model.deleteLoc("UC B1 conv");
        }
    }

    /**
     * UC-B1, the other unguarded writer: the full-state constructor restores arrival/departure
     * without validation, so a stale value survives restarts via the locomotive database.  The
     * setter refuses out-of-range values; the constructor must not be the way around it.
     */
    @Test
    public void testFullStateConstructorClampsArrivalAndDepartureFunctions()
    {
        MarklinLocomotive restored = new MarklinLocomotive(model, 80,
            MarklinLocomotive.decoderType.MM2, "UC B1 restored",
            MarklinLocomotive.locDirection.DIR_FORWARD,
            new boolean[] {false, false, false, false, false},
            new int[] {128, 10, 240, 241, 0},
            new int[] {Locomotive.FUNCTION_PULSE, Locomotive.FUNCTION_TOGGLE,
                Locomotive.FUNCTION_PULSE, Locomotive.FUNCTION_PULSE, 10},
            new boolean[] {false, false, false, false, false},
            50,
            25,  // departure function - out of range for 5 MM2 functions
            20,  // arrival function - out of range for 5 MM2 functions
            false, 4, new HashMap<>());

        assertTrue(restored.getArrivalFunc() == null || restored.getArrivalFunc() < restored.getNumF(),
            "restored arrival function " + restored.getArrivalFunc()
            + " is out of range for numF " + restored.getNumF());

        assertTrue(restored.getDepartureFunc() == null || restored.getDepartureFunc() < restored.getNumF(),
            "restored departure function " + restored.getDepartureFunc()
            + " is out of range for numF " + restored.getNumF());
    }

    /**
     * UC-C12: clearing the local icon must not wipe the Central Station image URL.
     *
     * setLocalImageURL assigns imageURL first and copies it to localImageURL, so clearing the local
     * override also destroys the CS-provided image.  The caller compensates with an immediate
     * syncWithCS2() - which restores it only when connected; offline the locomotive shows no image
     * for the rest of the session.
     */
    @Test
    public void testClearingTheLocalIconKeepsTheCentralStationImage() throws Exception
    {
        MarklinLocomotive loc = model.newMM2Locomotive("UC C12 icon", 60);

        try
        {
            loc.setImageURL("http://cs/loc60.png");
            loc.setLocalImageURL("file:///custom/icon.png");

            assertEquals(loc.getLocalImageURL(), "file:///custom/icon.png",
                "precondition: the local override is stored");

            loc.setLocalImageURL(null);

            assertNull(loc.getLocalImageURL(), "the local override is cleared");

            assertEquals(loc.getImageURL(), "http://cs/loc60.png",
                "the Central Station image must survive clearing the local override - offline "
                + "there is no syncWithCS2 to bring it back");

            // UC-C17, the restore ordering: after a restart the override exists FIRST (restored from
            // the locomotive database) and the CS image arrives second, from a sync that now adopts
            // it unconditionally - the old guard skipped adopting while an override existed, which
            // starved the fallback and reopened the same offline gap one restart later.  The sync
            // guard itself has no headless seam, so this pins the Locomotive-level ordering it
            // relies on.
            loc.setImageURL(null);
            loc.setLocalImageURL("file:///restored/icon.png");

            loc.setImageURL("http://cs/loc60.png");

            assertEquals(loc.getImageURL(), "file:///restored/icon.png",
                "the override wins while it is set, even when the CS image arrives afterwards");

            loc.setLocalImageURL(null);

            assertEquals(loc.getImageURL(), "http://cs/loc60.png",
                "and clearing it after the restart reveals the adopted CS image");
        }
        finally
        {
            model.deleteLoc("UC C12 icon");
        }
    }

    /**
     * A second power-on while a train is already running does not throw away what it has run.
     *
     * Runtime is only credited at the next stop, so the clock is a single start timestamp - and
     * notifyOfPowerStateChange reset it on every power-on it was told about, transitioning or not.
     * receiveMessage calls it for EVERY locomotive on EVERY system GO, so pressing Go on the Central
     * Station while trains were running - or clicking a diagram accessory, which turns power on first
     * - silently discarded everything since the real start.
     *
     * Timed rather than mocked because the credit is computed from the wall clock; the margins are
     * wide enough that only the defect can fail it.  Before the fix the second GO restarted the clock
     * and the credit was the last 50ms, not the whole 350.
     */
    @Test
    public void testARedundantPowerOnKeepsTheRunningTime() throws Exception
    {
        MarklinLocomotive timed = new MarklinLocomotive(model, 91,
            MarklinLocomotive.decoderType.MM2, "Runtime Test Loc");

        // A known starting state: nothing running, power off, so the first GO below is a transition
        timed.setSpeed(0);
        timed.notifyOfPowerStateChange(false);

        long before = timed.getTotalRuntime();

        timed.setSpeed(50);
        timed.notifyOfPowerStateChange(true);

        Thread.sleep(300);

        // The Central Station saying GO again, which changes nothing and used to cost everything
        timed.notifyOfPowerStateChange(true);

        Thread.sleep(50);

        timed.notifyOfPowerStateChange(false);

        long credited = timed.getTotalRuntime() - before;

        assertTrue(credited >= 250,
            "only " + credited + "ms was credited of about 350 run: the second power-on restarted "
                + "the clock and threw away everything before it");

        timed.setSpeed(0);
    }

    /**
     * An address with exactly one locomotive on it is not in the duplicate list - the fact the
     * "check for duplicates" dialog's bug (and its fix) both turn on.
     *
     * TST-B14: this used to close with `assertTrue(all.containsKey(single), ...)`, labelled as the
     * substantive check - but `single` was obtained by iterating `all.entrySet()` in the first place,
     * so that assertion could never fail; it is true by construction, not by anything about the
     * database.  Removed rather than kept as padding.
     *
     * What is left cannot, by itself, catch AddLocomotive.java's checkDuplicatesActionPerformed being
     * reverted to consult getDuplicateLocAddresses instead of getLocAddresses (the actual regression
     * this class is named for) - that logic is private, inline in a Swing action handler inside a
     * JDialog, and exercising it means instantiating AddLocomotive, which is GUI-dialog testing outside
     * test/core's scope here (see TST-B22: no test file anywhere in test/ mentions AddLocomotive).  What
     * this DOES verify, with a control proving the check can detect a presence rather than only its
     * absence, is the contract the fix depends on: getDuplicateLocAddresses() excludes an address with
     * exactly one locomotive, and includes one that genuinely has more than one.
     */
    @Test
    public void testAnAddressWithOneLocomotiveIsNotReportedFree() throws Exception
    {
        java.util.Map<Integer, java.util.Set<org.traincontrol.base.Locomotive>> all =
            model.getLocAddresses();

        java.util.Map<Integer, java.util.Set<org.traincontrol.base.Locomotive>> duplicates =
            model.getDuplicateLocAddresses();

        Integer single = null;
        Integer doubled = null;

        for (java.util.Map.Entry<Integer, java.util.Set<org.traincontrol.base.Locomotive>> entry
                : all.entrySet())
        {
            if (entry.getValue().size() == 1 && single == null)
            {
                single = entry.getKey();
            }
            else if (entry.getValue().size() > 1 && doubled == null)
            {
                doubled = entry.getKey();
            }
        }

        assertNotNull(single,
            "no address in this database has exactly one locomotive, so this test proves nothing");

        assertFalse(duplicates.containsKey(single),
            "an address with one locomotive is in the duplicate list - which the dialog would then "
            + "report as free if it were ever reverted to ask getDuplicateLocAddresses");

        // Control: proves the absence check above is not vacuously true because getDuplicateLocAddresses
        // never reports anything at all - a mutation emptying it outright would trip this, not that one.
        if (doubled != null)
        {
            assertTrue(duplicates.containsKey(doubled),
                "an address with more than one locomotive should be in the duplicate list");
        }
    }
}
