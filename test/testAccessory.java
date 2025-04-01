import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;

/**
 * Use testng 6.14.3
 */
public class testAccessory
{    
    public static MarklinControlStation model;
    
    public testAccessory()
    {
    }

    /**
     * Test accessory class functionality
     */
    @Test
    public void testAccessoryCreation()
    {   
        // Test with high numbers so the model deletes them automatically on restart
        model.newSignal(280, MarklinAccessory.accessoryDecoderType.MM2, true);
        
        MarklinAccessory signal1 = model.getAccessoryByName("Signal 280");
        MarklinAccessory signal2 = model.getAccessoryByAddress(280, MarklinAccessory.accessoryDecoderType.MM2);

        assertEquals(signal1.getName(), "Signal 280");
        assertEquals(signal1, signal2);
        assertEquals(signal1.getType(), MarklinAccessory.accessoryType.SIGNAL);
        assertEquals(signal1.isRed(), true);
        assertEquals(signal1.isGreen(), false);
        assertEquals(signal1.getDecoderType(), MarklinAccessory.accessoryDecoderType.MM2);

        // This should create a new signal in the DB
        model.newSignal(280, MarklinAccessory.accessoryDecoderType.DCC, false);
        signal1 = model.getAccessoryByName("Signal 280 DCC");

        assertTrue(model.getAccessoryByName("Signal 280 DCC") != model.getAccessoryByName("Signal 280"));
        assertEquals(signal1.getName(), "Signal 280 DCC");
        assertEquals(signal1.isGreen(), true);
        assertEquals(signal1.isRed(), false);
        assertEquals(signal1.isSignal(), true);
        assertEquals(signal1.isSwitch(), false);
        assertEquals(signal1.getDecoderType(), MarklinAccessory.accessoryDecoderType.DCC);

        // Test switching
        signal1.setSwitched(true);
        assertEquals(signal1.isSwitched(), true);
        signal1.setSwitched(false);
        assertEquals(signal1.isSwitched(), false);
        
        model.newSwitch(281, MarklinAccessory.accessoryDecoderType.MM2, false);
        MarklinAccessory switch1 = model.getAccessoryByAddress(281, MarklinAccessory.accessoryDecoderType.MM2);
        assertEquals(switch1.getType(), MarklinAccessory.accessoryType.SWITCH);
        assertEquals(switch1.isSwitched(), false);
        assertEquals(switch1.isTurned(), false);
        assertEquals(switch1.isStraight(), true);
        assertEquals(switch1.isSignal(), false);
        assertEquals(switch1.isSwitch(), true);
        
        // This switch should not exist
        MarklinAccessory createdAccessory = model.getAccessoryByName("Switch 3000");
        assertEquals(createdAccessory, null);
        
        // This will trigger the creation of the accessory
        model.getAccessoryState(3000, MarklinAccessory.accessoryDecoderType.DCC);
        createdAccessory = model.getAccessoryByName("Switch 3000 DCC");
        assertNotEquals(createdAccessory, null);
        assertEquals(createdAccessory.isSwitched(), false);
        assertEquals(createdAccessory.getType(), MarklinAccessory.accessoryType.SWITCH);
        
        // Non DCC version should not exist
        assertEquals(model.getAccessoryByName("Switch 3000"), null);
        
        assertFalse(createdAccessory.isValidAddress());
    }
    
    @Test
    public void testRouteCommand() throws Exception
    {   
        model.newSwitch(285, MarklinAccessory.accessoryDecoderType.MM2, false);
        MarklinAccessory createdAccessory = model.getAccessoryByAddress(285, MarklinAccessory.accessoryDecoderType.MM2);
        
        // Test copying an accessory setting
        RouteCommand rc = RouteCommand.fromLine(createdAccessory.toAccessorySettingString(), false);
        assertTrue(rc.isAccessory());
        assertFalse(rc.isAutonomyLightsOn());
        assertFalse(rc.isFunction());
        assertFalse(rc.isFunctionsOff());
        assertFalse(rc.isStop());
        assertFalse(rc.isLightsOn());
        assertFalse(rc.isLocomotive());
        assertEquals(rc.getAddress(), 285);
        assertFalse(rc.getSetting());
        
        // Test hypothetical setting
        RouteCommand rc2 = RouteCommand.fromLine(createdAccessory.toAccessorySettingString(true, MarklinAccessory.accessoryDecoderType.MM2.toString()), false);
        assertTrue(rc2.getSetting());
        
        // Test import and export
        String line = rc.toLine(createdAccessory);
        RouteCommand rc3 = RouteCommand.fromLine(line, false);
        assertEquals(rc, rc3);
        
        String line2 = rc2.toLine(createdAccessory);
        RouteCommand rc4 = RouteCommand.fromLine(line2, false);
        assertEquals(rc2, rc4);
    }
    
    @Test
    public void testSwitchingViaModel() throws Exception
    {  
        MarklinAccessory switch400 = model.getAccessoryByAddress(400, MarklinAccessory.accessoryDecoderType.DCC);
        
        model.setAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC, true);
        assertTrue(switch400.isSwitched());
        assertTrue(model.getAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC));

        model.setAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC, false);
        assertFalse(switch400.isSwitched());
        assertFalse(model.getAccessoryState(400, MarklinAccessory.accessoryDecoderType.DCC));
        
        MarklinAccessory switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        model.setAccessoryState(1, MarklinAccessory.accessoryDecoderType.MM2, true);
        assertTrue(switch1.isSwitched());
        
        switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        assertTrue(switch1.isSwitched());
        switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        System.out.println(switch1);

        // TODO - by name must differentiate between MM and DCC
        model.newSwitch(1, MarklinAccessory.accessoryDecoderType.MM2, true);

        switch1 = model.getAccessoryByName(switch1.isSwitch() ? "Switch 1" : "Signal 1");
        assertTrue(switch1.isSwitched());
        assertTrue(model.getAccessoryState(1, MarklinAccessory.accessoryDecoderType.MM2));
    }
    
    @Test
    public void testAddressValidation() throws Exception
    {  
        MarklinAccessory switchNeg = model.getAccessoryByAddress(-1, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch0 = model.getAccessoryByAddress(0, MarklinAccessory.accessoryDecoderType.MM2);
        
        // Model will reject switch creation for invalid addresses
        assertNull(switchNeg);
        assertNull(switch0);
        
        // nothing should happen for the same reason as above
        model.setAccessoryState(-1, MarklinAccessory.accessoryDecoderType.MM2, true);
        model.setAccessoryState(0, MarklinAccessory.accessoryDecoderType.MM2, true);
        
        // This will be allowed
        switch0 = model.newSwitch(0, MarklinAccessory.accessoryDecoderType.MM2, true);
        switchNeg = model.newSignal(-1, MarklinAccessory.accessoryDecoderType.MM2, true);
        
        assertNotNull(switchNeg);
        assertNotNull(switch0);

        MarklinAccessory switch1 = model.getAccessoryByAddress(1, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch255 = model.getAccessoryByAddress(255, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch256 = model.getAccessoryByAddress(256, MarklinAccessory.accessoryDecoderType.MM2);
        MarklinAccessory switch257 = model.getAccessoryByAddress(257, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch319 = model.getAccessoryByAddress(319, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch320 = model.getAccessoryByAddress(320, MarklinAccessory.accessoryDecoderType.DCC); 
        MarklinAccessory switch321 = model.getAccessoryByAddress(321, MarklinAccessory.accessoryDecoderType.DCC);

        MarklinAccessory switch2047 = model.getAccessoryByAddress(2047, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch2048 = model.getAccessoryByAddress(2048, MarklinAccessory.accessoryDecoderType.DCC);
        MarklinAccessory switch2049 = model.getAccessoryByAddress(2049, MarklinAccessory.accessoryDecoderType.DCC);

        assertFalse(switchNeg.isValidAddress());
        assertFalse(switch0.isValidAddress());
        assertTrue(switch1.isValidAddress());
        assertTrue(switch255.isValidAddress());
        assertTrue(switch256.isValidAddress());
        assertTrue(switch257.isValidAddress());
        assertTrue(MarklinAccessory.isValidDCCAddress(switch256.getAddress()));
        assertTrue(MarklinAccessory.isValidMM2Address(switch256.getAddress()));
        assertTrue(MarklinAccessory.isValidMM2Address(switch319.getAddress()));
        assertTrue(MarklinAccessory.isValidMM2Address(switch320.getAddress()));
        assertFalse(MarklinAccessory.isValidMM2Address(switch321.getAddress()));
        assertTrue(switch2047.isValidAddress());
        assertTrue(switch2048.isValidAddress());
        assertFalse(switch2049.isValidAddress());
        assertFalse(MarklinAccessory.isValidDCCAddress(switch2049.getAddress()));
        assertTrue(MarklinAccessory.isValidDCCAddress(switch2048.getAddress()));
    }
    
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testAccessory.model = init(null, true, false, false, false); 
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
