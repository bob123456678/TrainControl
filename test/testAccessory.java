import java.lang.reflect.Field;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.base.RemoteDeviceCollection;
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
    
    private static final MarklinAccessory.accessoryDecoderType MM2 =
        MarklinAccessory.accessoryDecoderType.MM2;

    /**
     * Empties one logical MM2 address, so a test can start from the state a fresh installation is in.
     * A real database is not clean - the keyboard registers an accessory at every address the operator
     * has ever scrolled past - so there is no reliably unused address to pick.
     *
     * Reaches accDB by reflection because there is no accessory delete on the model.  In-memory only;
     * nothing here calls saveState.
     */
    @SuppressWarnings("unchecked")
    private static RemoteDeviceCollection<MarklinAccessory, Integer> accDb() throws Exception
    {
        Field accDbField = MarklinControlStation.class.getDeclaredField("accDB");
        accDbField.setAccessible(true);

        return (RemoteDeviceCollection<MarklinAccessory, Integer>) accDbField.get(model);
    }

    private static void clearAccessoryAddress(int logicalAddress) throws Exception
    {
        // A Switch and a Signal at one address share a UID, so both names have to go
        accDb().delete("Switch " + logicalAddress);
        accDb().delete("Signal " + logicalAddress);

        assertNull(model.getAccessoryByName("Switch " + logicalAddress));
        assertNull(model.getAccessoryByName("Signal " + logicalAddress));
    }

    /**
     * Re-creating an accessory as the other type must not leave the old name behind.
     *
     * A switch and a signal at one address share a database id, so re-registering under the other
     * name used to leave the first one in the name map forever - listed by getItemNames, and still
     * resolving, to the new device.  This happens for real when a track diagram tile is changed from
     * a switch to a signal and syncLayouts re-creates the accessory.
     */
    @Test
    public void testChangingAccessoryTypeDoesNotStrandTheOldName() throws Exception
    {
        clearAccessoryAddress(285);

        model.newSwitch(285, MM2, false);
        assertTrue(accDb().getItemNames().contains("Switch 285"));

        // The layout now says this address is a signal
        MarklinAccessory signal = model.newSignal(285, MM2, false);

        assertEquals(signal.getType(), MarklinAccessory.accessoryType.SIGNAL);
        assertTrue(accDb().getItemNames().contains("Signal 285"));

        assertFalse(accDb().getItemNames().contains("Switch 285"),
            "the superseded name must not linger in the database");

        // Existing references to the old name still work, via the accessory type fallback
        assertEquals(model.getAccessoryByName("Switch 285"), signal);
    }

    /**
     * A signal and a switch at the same address are the same physical decoder, and the database is
     * keyed by address and protocol - so either name must find the one entry.  Autonomy edges and
     * routes refer to accessories by name, and previously a command naming the other type simply did
     * not resolve, leaving that accessory silently uncommanded.
     */
    @Test
    public void testSignalAndSwitchNamesResolveToTheSameDecoder()
    {
        MarklinAccessory signal = model.newSignal(281, MM2, false);

        assertEquals(model.getAccessoryByName("Signal 281"), signal);
        assertEquals(model.getAccessoryByName("Switch 281"), signal,
            "the switch name at the same address must resolve to the same decoder");
    }

    /**
     * Only the type prefix is interchangeable.  The address and any protocol suffix must still match
     * exactly, and a name that matches nothing under either type resolves to nothing.
     */
    @Test
    public void testOnlyTheAccessoryTypePrefixIsInterchangeable()
    {
        MarklinAccessory dcc = model.newSwitch(284, MarklinAccessory.accessoryDecoderType.DCC, false);

        // The protocol suffix is part of the identity - MM2 284 and DCC 284 are different decoders
        assertEquals(model.getAccessoryByName("Signal 284 DCC"), dcc);
        assertNotEquals(model.getAccessoryByName("Signal 284"), dcc,
            "the MM2 name must not resolve to the DCC accessory at the same address");

        assertNull(model.getAccessoryByName("Switch 99999"));
        assertNull(model.getAccessoryByName("Not an accessory"));
    }

    /**
     * An edge configuration command naming a signal at an address that already holds a switch must be
     * accepted - they are the same decoder.  It used to be rejected as an address conflict, which left
     * the autonomy edge referring to a name that never resolved.
     */
    @Test
    public void testSignalCommandResolvesToAnExistingSwitch() throws Exception
    {
        model.newSwitch(282, MM2, false);
        assertNotNull(model.getAccessoryByName("Switch 282"));

        Edge.validateConfigCommand("Signal 282", "red", model);

        assertEquals(model.getAccessoryByName("Signal 282"), model.getAccessoryByName("Switch 282"),
            "the command should resolve to the decoder already at that address");
    }

    /**
     * And at a genuinely unused address the accessory is created, as the requested type, with no
     * spurious extra accessory left behind at a neighbouring address.
     */
    @Test
    public void testSignalIsCreatedAtAnUnusedAddress() throws Exception
    {
        clearAccessoryAddress(283);
        clearAccessoryAddress(282);

        Edge.validateConfigCommand("Signal 283", "red", model);

        MarklinAccessory created = model.getAccessoryByName("Signal 283");

        assertNotNull(created, "the signal should have been created");
        assertEquals(created.getType(), MarklinAccessory.accessoryType.SIGNAL);

        assertNull(model.getAccessoryByName("Signal 282"),
            "creating an accessory must not register one at the address below it");
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
        assertFalse(rc.isLocomotiveSpeed());
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
